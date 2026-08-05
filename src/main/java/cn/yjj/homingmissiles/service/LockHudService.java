package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.util.HudFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregates guidance state into native client HUD widgets and spatial warning audio.
 * Shooter telemetry intentionally contains only occupied outbound channels: never a
 * target identity, bearing, range, speed, or target count.
 */
public final class LockHudService {
    private final SettingsManager settingsManager;

    private long serviceTick;
    private final Map<UUID, ShooterState> shooterStates = new HashMap<>();
    private final Map<UUID, TargetThreat> targetThreats = new HashMap<>();
    private final Map<UUID, Long> nextWarningTick = new HashMap<>();
    private final Map<UUID, BossBar> shooterBars = new HashMap<>();
    private final Map<UUID, BossBar> targetBars = new HashMap<>();

    public LockHudService(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void beginTick(long serviceTick) {
        this.serviceTick = serviceTick;
        shooterStates.clear();
        targetThreats.clear();
    }

    public void reportOutbound(Player shooter) {
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        shooterStates.compute(shooter.getUniqueId(), (id, existing) -> existing == null
                ? new ShooterState(shooter, 1)
                : new ShooterState(shooter, existing.active + 1));
    }

    public void reportLock(Player target, Location arrowLocation, double distance) {
        if (target == null || !target.isOnline()) {
            return;
        }

        UUID targetId = target.getUniqueId();
        TargetThreat existing = targetThreats.get(targetId);
        if (existing == null) {
            targetThreats.put(targetId, new TargetThreat(
                    target, 1, distance,
                    arrowLocation.getX(), arrowLocation.getY(), arrowLocation.getZ()));
            return;
        }

        if (distance < existing.distance) {
            targetThreats.put(targetId, new TargetThreat(
                    target, existing.count + 1, distance,
                    arrowLocation.getX(), arrowLocation.getY(), arrowLocation.getZ()));
        } else {
            targetThreats.put(targetId, new TargetThreat(
                    target, existing.count + 1, existing.distance,
                    existing.arrowX, existing.arrowY, existing.arrowZ));
        }
    }

    public void endTick() {
        PluginSettings settings = settingsManager.current();
        if (!settings.hudEnabled()) {
            clearBars(shooterBars);
            clearBars(targetBars);
            nextWarningTick.clear();
            return;
        }

        Set<UUID> activeShooters = renderShooterHud(settings);
        Set<UUID> threatened = renderThreatHudAndAudio(settings);
        removeStaleBars(shooterBars, activeShooters);
        removeStaleBars(targetBars, threatened);
        pruneWarnings(threatened);
    }

    public void forgetPlayer(UUID playerId) {
        nextWarningTick.remove(playerId);
        removeBar(shooterBars, playerId);
        removeBar(targetBars, playerId);
    }

    public void shutdown() {
        clearBars(shooterBars);
        clearBars(targetBars);
        shooterStates.clear();
        targetThreats.clear();
        nextWarningTick.clear();
    }

    private Set<UUID> renderShooterHud(PluginSettings settings) {
        if (!settings.shooterBossBar()) {
            clearBars(shooterBars);
            return Set.of();
        }

        Set<UUID> active = new HashSet<>();
        for (Map.Entry<UUID, ShooterState> entry : shooterStates.entrySet()) {
            ShooterState state = entry.getValue();
            if (!state.player.isOnline()) {
                continue;
            }
            active.add(entry.getKey());
            BossBar bar = shooterBars.computeIfAbsent(entry.getKey(), ignored ->
                    Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID));
            attach(bar, state.player);
            int visibleCount = Math.min(state.active, settings.maxTrackedPerPlayer());
            bar.setTitle("§b◈  数据链  §f" + HudFormat.hardpoints(visibleCount, settings.maxTrackedPerPlayer()));
            bar.setProgress(HudFormat.ratio(visibleCount, settings.maxTrackedPerPlayer()));
            bar.setVisible(true);
        }
        return active;
    }

    private Set<UUID> renderThreatHudAndAudio(PluginSettings settings) {
        Set<UUID> threatened = new HashSet<>();
        for (Map.Entry<UUID, TargetThreat> entry : targetThreats.entrySet()) {
            TargetThreat threat = entry.getValue();
            Player target = threat.target;
            if (!target.isOnline()) {
                continue;
            }
            threatened.add(entry.getKey());

            if (settings.targetBossBar()) {
                BossBar bar = targetBars.computeIfAbsent(entry.getKey(), ignored ->
                        Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_20));
                attach(bar, target);
                bar.setTitle(threat.count > 1 ? "§4⚠  多枚导弹来袭  ×" + threat.count : "§4⚠  导弹来袭");
                bar.setProgress(HudFormat.proximity(threat.distance, settings.trackingRange()));
                bar.setVisible(true);
            }

            if (settings.warningAudio()) {
                playSpatialWarning(target, threat, settings);
            }
        }
        if (!settings.targetBossBar()) {
            clearBars(targetBars);
        }
        return threatened;
    }

    private void playSpatialWarning(Player target, TargetThreat threat, PluginSettings settings) {
        UUID id = target.getUniqueId();
        if (serviceTick < nextWarningTick.getOrDefault(id, 0L)) {
            return;
        }

        double proximity = HudFormat.proximity(threat.distance, settings.trackingRange());
        int interval = HudFormat.warningInterval(
                threat.distance,
                settings.trackingRange(),
                settings.warningMinIntervalTicks(),
                settings.warningMaxIntervalTicks());

        Location eye = target.getEyeLocation();
        Vector towardThreat = new Vector(
                threat.arrowX - eye.getX(),
                threat.arrowY - eye.getY(),
                threat.arrowZ - eye.getZ());
        Location cue = target.getEyeLocation();
        if (towardThreat.lengthSquared() > 1.0E-8) {
            cue.add(towardThreat.normalize().multiply(4.0));
        }

        float sensorVolume = (float) (0.35 + proximity * 0.45);
        float sensorPitch = (float) (0.62 + proximity * 0.68);
        target.playSound(cue, Sound.BLOCK_SCULK_SENSOR_CLICKING, sensorVolume, sensorPitch);
        if (proximity >= 0.65) {
            target.playSound(cue, Sound.ENTITY_WARDEN_HEARTBEAT,
                    (float) (0.25 + proximity * 0.35),
                    (float) (0.82 + proximity * 0.28));
        }
        nextWarningTick.put(id, serviceTick + interval);
    }

    private static void attach(BossBar bar, Player player) {
        if (!bar.getPlayers().contains(player)) {
            bar.addPlayer(player);
        }
    }

    private static void removeStaleBars(Map<UUID, BossBar> bars, Set<UUID> active) {
        Iterator<Map.Entry<UUID, BossBar>> iterator = bars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, BossBar> entry = iterator.next();
            if (active.contains(entry.getKey())) {
                continue;
            }
            entry.getValue().removeAll();
            iterator.remove();
        }
    }

    private static void removeBar(Map<UUID, BossBar> bars, UUID playerId) {
        BossBar bar = bars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private static void clearBars(Map<UUID, BossBar> bars) {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    private void pruneWarnings(Set<UUID> threatened) {
        nextWarningTick.keySet().removeIf(id -> !threatened.contains(id));
    }

    private static final class ShooterState {
        final Player player;
        final int active;

        ShooterState(Player player, int active) {
            this.player = player;
            this.active = active;
        }
    }

    private static final class TargetThreat {
        final Player target;
        final int count;
        final double distance;
        final double arrowX;
        final double arrowY;
        final double arrowZ;

        TargetThreat(Player target, int count, double distance,
                     double arrowX, double arrowY, double arrowZ) {
            this.target = target;
            this.count = count;
            this.distance = distance;
            this.arrowX = arrowX;
            this.arrowY = arrowY;
            this.arrowZ = arrowZ;
        }
    }
}
