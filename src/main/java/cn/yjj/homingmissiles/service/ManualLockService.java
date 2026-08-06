package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Owns the draw-to-acquire state machine. A completed lock is consumed by one shot. */
public final class ManualLockService {
    private static final double EPSILON = 1.0E-8;
    private static final double HUD_SMOOTHING = 0.38;

    private final SettingsManager settingsManager;
    private final HomingBowFactory bowFactory;
    private final LockHudService lockHud;
    private final Map<UUID, AimState> states = new HashMap<>();

    private long serviceTick;

    public ManualLockService(SettingsManager settingsManager,
                             HomingBowFactory bowFactory,
                             LockHudService lockHud) {
        this.settingsManager = settingsManager;
        this.bowFactory = bowFactory;
        this.lockHud = lockHud;
    }

    public void tick(Collection<? extends Player> onlinePlayers, long serviceTick) {
        this.serviceTick = serviceTick;
        PluginSettings settings = settingsManager.current();

        for (Player shooter : onlinePlayers) {
            UUID shooterId = shooter.getUniqueId();
            if (!isDrawingHomingBow(shooter, settings)) {
                AimState stale = states.get(shooterId);
                if (stale != null && serviceTick - stale.lastDrawingTick > 2L) {
                    states.remove(shooterId);
                    lockHud.clearAim(shooterId);
                }
                continue;
            }

            AimState state = states.computeIfAbsent(shooterId, ignored -> new AimState(shooter));
            state.shooter = shooter;
            state.lastDrawingTick = serviceTick;
            updateState(state, settings);
            lockHud.reportAim(shooter,
                    state.target != null,
                    (double) state.progressTicks / settings.manualLockDurationTicks(),
                    state.locked,
                    state.screenX,
                    state.screenY);
        }
    }

    public Player lockedTarget(Player shooter) {
        AimState state = states.get(shooter.getUniqueId());
        if (!isConsumable(state)) {
            return null;
        }
        return state.target;
    }

    public Player consumeLockedTarget(Player shooter) {
        UUID shooterId = shooter.getUniqueId();
        AimState state = states.get(shooterId);
        if (!isConsumable(state)) {
            return null;
        }
        states.remove(shooterId);
        lockHud.clearAim(shooterId);
        return state.target;
    }

    public void forgetPlayer(UUID playerId) {
        states.remove(playerId);
        lockHud.clearAim(playerId);
    }

    public void clear() {
        for (UUID playerId : states.keySet()) {
            lockHud.clearAim(playerId);
        }
        states.clear();
    }

    private boolean isDrawingHomingBow(Player shooter, PluginSettings settings) {
        return shooter.isOnline()
                && shooter.hasPermission("homingmissiles.use")
                && !settings.disabledWorlds().contains(shooter.getWorld().getName().toLowerCase(Locale.ROOT))
                && shooter.isHandRaised()
                && shooter.hasActiveItem()
                && bowFactory.isHomingBow(shooter.getActiveItem());
    }

    private void updateState(AimState state, PluginSettings settings) {
        AimCandidate candidate;
        if (state.locked && state.target != null) {
            candidate = evaluate(state.shooter, state.target, settings.manualLockBreakConeDegrees(), settings);
        } else {
            candidate = state.target == null
                    ? null
                    : evaluate(state.shooter, state.target, settings.manualLockConeDegrees(), settings);
            if (candidate == null) {
                candidate = selectBestCandidate(state.shooter, settings);
            }
        }

        if (candidate == null) {
            handleLostTarget(state, settings);
            return;
        }

        boolean sameTarget = state.target != null
                && state.target.getUniqueId().equals(candidate.player.getUniqueId());
        if (!sameTarget) {
            state.target = candidate.player;
            state.progressTicks = 0;
            state.lostTicks = 0;
            state.locked = false;
            state.screenX = candidate.screenX;
            state.screenY = candidate.screenY;
        } else {
            state.lostTicks = 0;
            state.screenX = lerp(state.screenX, candidate.screenX, HUD_SMOOTHING);
            state.screenY = lerp(state.screenY, candidate.screenY, HUD_SMOOTHING);
        }

        state.lastVisibleTick = serviceTick;
        if (!state.locked) {
            state.progressTicks = Math.min(settings.manualLockDurationTicks(), state.progressTicks + 1);
            if (state.progressTicks >= settings.manualLockDurationTicks()) {
                state.locked = true;
                lockHud.playManualLockCue(state.shooter);
            }
        }
    }

    private void handleLostTarget(AimState state, PluginSettings settings) {
        if (state.target != null && state.lostTicks < settings.manualLockGraceTicks()) {
            state.lostTicks++;
            if (!state.locked) {
                state.progressTicks = Math.max(0, state.progressTicks - 1);
            }
            return;
        }
        state.target = null;
        state.progressTicks = 0;
        state.lostTicks = 0;
        state.locked = false;
        state.screenX = 0.0;
        state.screenY = 0.0;
    }

    private AimCandidate selectBestCandidate(Player shooter, PluginSettings settings) {
        AimCandidate best = null;
        for (Player candidate : shooter.getWorld().getPlayers()) {
            AimCandidate evaluated = evaluate(shooter, candidate, settings.manualLockConeDegrees(), settings);
            if (evaluated == null) {
                continue;
            }
            if (best == null || evaluated.angleDegrees < best.angleDegrees
                    || (evaluated.angleDegrees == best.angleDegrees && evaluated.distance < best.distance)) {
                best = evaluated;
            }
        }
        return best;
    }

    private AimCandidate evaluate(Player shooter, Player candidate, double coneDegrees,
                                  PluginSettings settings) {
        if (!isEligibleTarget(shooter, candidate, settings)) {
            return null;
        }

        Location eye = shooter.getEyeLocation();
        Location targetEye = candidate.getEyeLocation();
        Vector offset = targetEye.toVector().subtract(eye.toVector());
        double distanceSquared = offset.lengthSquared();
        if (distanceSquared < EPSILON || distanceSquared > settings.trackingRange() * settings.trackingRange()) {
            return null;
        }

        double distance = Math.sqrt(distanceSquared);
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < EPSILON) {
            return null;
        }
        double dot = clamp(direction.normalize().dot(offset.clone().normalize()), -1.0, 1.0);
        double angle = Math.toDegrees(Math.acos(dot));
        if (angle > coneDegrees) {
            return null;
        }

        double absoluteYaw = Math.toDegrees(Math.atan2(-offset.getX(), offset.getZ()));
        double horizontal = Math.hypot(offset.getX(), offset.getZ());
        double absolutePitch = -Math.toDegrees(Math.atan2(offset.getY(), horizontal));
        double screenX = clamp(-wrapDegrees(absoluteYaw - eye.getYaw()) / coneDegrees, -1.0, 1.0);
        double screenY = clamp((absolutePitch - eye.getPitch()) / coneDegrees, -1.0, 1.0);
        return new AimCandidate(candidate, angle, distance, screenX, screenY);
    }

    private static boolean isEligibleTarget(Player shooter, Player candidate, PluginSettings settings) {
        if (candidate == null || candidate.getUniqueId().equals(shooter.getUniqueId())
                || !candidate.isOnline() || candidate.isDead()) {
            return false;
        }
        World world = shooter.getWorld();
        if (!candidate.getWorld().equals(world)
                || candidate.hasPermission("homingmissiles.target.exempt")) {
            return false;
        }
        GameMode mode = candidate.getGameMode();
        if (mode == GameMode.SPECTATOR && !settings.targetSpectator()) {
            return false;
        }
        if (mode == GameMode.CREATIVE && !settings.targetCreative()) {
            return false;
        }
        if (settings.respectVanish() && !shooter.canSee(candidate)) {
            return false;
        }
        return shooter.hasLineOfSight(candidate);
    }

    private boolean isConsumable(AimState state) {
        if (state == null || !state.locked || state.target == null || !state.target.isOnline()) {
            return false;
        }
        PluginSettings settings = settingsManager.current();
        return serviceTick - state.lastVisibleTick <= settings.manualLockGraceTicks()
                && isEligibleTarget(state.shooter, state.target, settings);
    }

    private static double wrapDegrees(double value) {
        return ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    private static double lerp(double current, double target, double factor) {
        return current + (target - current) * factor;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class AimState {
        Player shooter;
        Player target;
        int progressTicks;
        int lostTicks;
        boolean locked;
        double screenX;
        double screenY;
        long lastDrawingTick;
        long lastVisibleTick;

        AimState(Player shooter) {
            this.shooter = shooter;
        }
    }

    private record AimCandidate(Player player, double angleDegrees, double distance,
                                double screenX, double screenY) {
    }
}
