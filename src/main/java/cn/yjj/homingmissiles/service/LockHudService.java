package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.util.HudFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregates guidance state into a resource-pack-backed pixel HMD. The ActionBar
 * carries one private-use glyph whose pixels come from homingmissiles:hud; it is
 * not a textual telemetry line. BossBars are used only when the pack is not ready.
 * Shooter symbology exposes channel occupancy only, never target telemetry.
 */
public final class LockHudService {
    public static final UUID HUD_PACK_ID = UUID.fromString("4b388bf5-7ba1-4d5e-97e8-fb1f38d3a300");
    private static final Key HUD_FONT = Key.key("homingmissiles", "hud");
    private static final String SOUND_LOCK_CONFIRM = "homingmissiles:hud.lock_confirm";
    private static final String SOUND_MISSILE_WARNING = "homingmissiles:hud.missile_warning";
    private static final String SOUND_MISSILE_CRITICAL = "homingmissiles:hud.missile_critical";
    private static final String SOUND_LAUNCH = "homingmissiles:hud.launch";

    private final SettingsManager settingsManager;

    private long serviceTick;
    private final Map<UUID, ShooterState> shooterStates = new HashMap<>();
    private final Map<UUID, TargetThreat> targetThreats = new HashMap<>();
    private final Map<UUID, AimDisplay> aimDisplays = new HashMap<>();
    private final Map<UUID, TelemetryState> telemetryStates = new HashMap<>();
    private final Map<UUID, Long> nextWarningTick = new HashMap<>();
    private final Map<UUID, BossBar> shooterBars = new HashMap<>();
    private final Map<UUID, BossBar> targetBars = new HashMap<>();
    private final Map<UUID, BossBar> aimBars = new HashMap<>();
    private final Map<UUID, Player> overlayPlayers = new HashMap<>();
    private final Set<UUID> packReady = new HashSet<>();

    public LockHudService(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public void beginTick(long serviceTick) {
        this.serviceTick = serviceTick;
        shooterStates.clear();
        targetThreats.clear();
        aimDisplays.clear();
    }

    public void reportAim(Player shooter, boolean targetPresent, double progress,
                          boolean locked, double screenX, double screenY) {
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        aimDisplays.put(shooter.getUniqueId(), new AimDisplay(
                shooter, targetPresent, clamp(progress, 0.0, 1.0), locked,
                clamp(screenX, -1.0, 1.0), clamp(screenY, -1.0, 1.0)));
    }

    public void clearAim(UUID shooterId) {
        aimDisplays.remove(shooterId);
        removeBar(aimBars, shooterId);
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
            clearOverlays();
            clearBars(shooterBars);
            clearBars(targetBars);
            clearBars(aimBars);
            nextWarningTick.clear();
            return;
        }

        Set<UUID> pixelPlayers = renderPixelOverlays(settings);
        Set<UUID> aiming = renderAimHud(settings, pixelPlayers);
        Set<UUID> activeShooters = renderShooterHud(settings, pixelPlayers);
        Set<UUID> threatened = renderThreatHudAndAudio(settings, pixelPlayers);
        removeStaleBars(aimBars, aiming);
        removeStaleBars(shooterBars, activeShooters);
        removeStaleBars(targetBars, threatened);
        pruneWarnings(targetThreats.keySet());
    }

    public void preparePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        packReady.remove(playerId);
        // Always remove the previous plugin-owned pack before applying the new
        // snapshot, including when reload disables or delegates the HUD pack.
        player.removeResourcePack(HUD_PACK_ID);
        PluginSettings settings = settingsManager.current();
        if (!settings.hudEnabled() || !settings.pixelHudEnabled()) {
            return;
        }
        if (settings.assumeServerPackProvidesHud()) {
            packReady.add(playerId);
            return;
        }
        String url = settings.hudResourcePackUrl();
        if (url.isBlank()) {
            return;
        }

        byte[] sha1 = decodeSha1(settings.hudResourcePackSha1());
        player.addResourcePack(
                HUD_PACK_ID,
                url,
                sha1,
                settings.hudResourcePackPrompt(),
                settings.hudResourcePackRequired());
    }

    public void handleResourcePackStatus(PlayerResourcePackStatusEvent event) {
        PluginSettings settings = settingsManager.current();
        // In delegated mode there is no reliable plugin-owned pack UUID to
        // correlate; preparePlayer intentionally trusts the administrator.
        if (settings.assumeServerPackProvidesHud() || !HUD_PACK_ID.equals(event.getID())) {
            return;
        }
        UUID playerId = event.getPlayer().getUniqueId();
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            packReady.add(playerId);
        } else if (isTerminalPackFailure(event.getStatus())) {
            packReady.remove(playerId);
        }
    }

    public void forgetPlayer(UUID playerId) {
        nextWarningTick.remove(playerId);
        packReady.remove(playerId);
        overlayPlayers.remove(playerId);
        telemetryStates.remove(playerId);
        removeBar(shooterBars, playerId);
        removeBar(targetBars, playerId);
        removeBar(aimBars, playerId);
    }

    public void shutdown() {
        clearOverlays();
        clearBars(shooterBars);
        clearBars(targetBars);
        clearBars(aimBars);
        shooterStates.clear();
        targetThreats.clear();
        aimDisplays.clear();
        telemetryStates.clear();
        nextWarningTick.clear();
        packReady.clear();
    }

    private Set<UUID> renderPixelOverlays(PluginSettings settings) {
        if (!settings.pixelHudEnabled()) {
            clearOverlays();
            return Set.of();
        }

        Set<UUID> candidates = new HashSet<>(shooterStates.keySet());
        candidates.addAll(targetThreats.keySet());
        candidates.addAll(aimDisplays.keySet());
        Set<UUID> active = new HashSet<>();
        for (UUID playerId : candidates) {
            ShooterState shooter = shooterStates.get(playerId);
            TargetThreat threat = targetThreats.get(playerId);
            AimDisplay aim = aimDisplays.get(playerId);
            Player player = threat != null ? threat.target : shooter != null ? shooter.player : aim.shooter;
            if (!packReady.contains(playerId) || !player.isOnline()) {
                continue;
            }

            Location eye = player.getEyeLocation();
            Vector velocity = player.getVelocity();
            double clearance = groundClearance(player);
            TelemetryState telemetry = telemetryStates.computeIfAbsent(playerId,
                    ignored -> new TelemetryState());
            telemetry.update(eye.getYaw(), eye.getPitch(), velocity.length() * 20.0,
                    eye.getY(), clearance);
            char threatLayer = 0;
            if (threat != null) {
                int sector = HudFormat.directionSector(
                        eye.getYaw(), eye.getX(), eye.getZ(), threat.arrowX, threat.arrowZ);
                double proximity = HudFormat.proximity(threat.distance, settings.lockRetentionRange());
                threatLayer = HudFormat.threatGlyph(sector, proximity);
            }
            char lockMarker = aim != null && aim.targetPresent
                    ? HudFormat.lockMarkerGlyph(aim.screenX, aim.screenY, aim.locked)
                    : 0;
            char lockProgress = aim == null
                    ? 0
                    : HudFormat.lockProgressGlyph(aim.progress, aim.locked);

            String overlay = HudFormat.composeLayers(
                    HudFormat.baseGlyph(),
                    HudFormat.headingGlyph((float) telemetry.yaw),
                    HudFormat.pitchGlyph((float) telemetry.pitch),
                    HudFormat.progradeGlyph((float) telemetry.yaw, (float) telemetry.pitch,
                            velocity.getX(), velocity.getY(), velocity.getZ()),
                    HudFormat.speedGlyph(telemetry.speed),
                    HudFormat.altitudeGlyph(telemetry.altitude),
                    HudFormat.heightGlyph(telemetry.clearance),
                    HudFormat.weaponGlyph(shooter == null ? 0 : shooter.active),
                    lockMarker,
                    lockProgress,
                    threatLayer);
            showOverlay(player, overlay);
            active.add(playerId);
        }

        removeStaleOverlays(active);
        return active;
    }

    private Set<UUID> renderAimHud(PluginSettings settings, Set<UUID> pixelPlayers) {
        if (!settings.shooterBossBar()) {
            clearBars(aimBars);
            return Set.of();
        }

        Set<UUID> active = new HashSet<>();
        for (Map.Entry<UUID, AimDisplay> entry : aimDisplays.entrySet()) {
            AimDisplay aim = entry.getValue();
            if (pixelPlayers.contains(entry.getKey()) || !aim.shooter.isOnline()) {
                continue;
            }
            active.add(entry.getKey());
            BossBar bar = aimBars.computeIfAbsent(entry.getKey(), ignored ->
                    Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SEGMENTED_20));
            attach(bar, aim.shooter);
            if (aim.locked) {
                bar.setTitle("§a◆  LOCK  §f松弦发射");
                bar.setProgress(1.0);
            } else if (aim.targetPresent) {
                bar.setTitle("§e◇  手动标定  §f" + Math.round(aim.progress * 100.0) + "%");
                bar.setProgress(aim.progress);
            } else {
                bar.setTitle("§e◇  将目标保持在准星内");
                bar.setProgress(0.0);
            }
            bar.setVisible(true);
        }
        return active;
    }

    private Set<UUID> renderShooterHud(PluginSettings settings, Set<UUID> pixelPlayers) {
        if (!settings.shooterBossBar()) {
            clearBars(shooterBars);
            return Set.of();
        }

        Set<UUID> active = new HashSet<>();
        for (Map.Entry<UUID, ShooterState> entry : shooterStates.entrySet()) {
            ShooterState state = entry.getValue();
            if (pixelPlayers.contains(entry.getKey()) || !state.player.isOnline()) {
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

    private Set<UUID> renderThreatHudAndAudio(PluginSettings settings, Set<UUID> pixelPlayers) {
        Set<UUID> threatenedWithBars = new HashSet<>();
        for (Map.Entry<UUID, TargetThreat> entry : targetThreats.entrySet()) {
            TargetThreat threat = entry.getValue();
            Player target = threat.target;
            if (!target.isOnline()) {
                continue;
            }

            if (settings.targetBossBar() && !pixelPlayers.contains(entry.getKey())) {
                threatenedWithBars.add(entry.getKey());
                BossBar bar = targetBars.computeIfAbsent(entry.getKey(), ignored ->
                        Bukkit.createBossBar("", BarColor.RED, BarStyle.SEGMENTED_20));
                attach(bar, target);
                bar.setTitle(threat.count > 1 ? "§4⚠  多枚导弹来袭  ×" + threat.count : "§4⚠  导弹来袭");
                bar.setProgress(HudFormat.proximity(threat.distance, settings.lockRetentionRange()));
                bar.setVisible(true);
            }

            if (settings.warningAudio()) {
                playSpatialWarning(target, threat, settings);
            }
        }
        if (!settings.targetBossBar()) {
            clearBars(targetBars);
        }
        return threatenedWithBars;
    }

    private void playSpatialWarning(Player target, TargetThreat threat, PluginSettings settings) {
        UUID id = target.getUniqueId();
        if (serviceTick < nextWarningTick.getOrDefault(id, 0L)) {
            return;
        }

        double proximity = HudFormat.proximity(threat.distance, settings.lockRetentionRange());
        int interval = HudFormat.warningInterval(
                threat.distance,
                settings.lockRetentionRange(),
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

        if (hasCustomAudio(target)) {
            String event = proximity >= 0.65 ? SOUND_MISSILE_CRITICAL : SOUND_MISSILE_WARNING;
            target.playSound(cue, event, SoundCategory.PLAYERS,
                    (float) (0.55 + proximity * 0.35),
                    (float) (0.92 + proximity * 0.16));
        } else {
            float sensorVolume = (float) (0.35 + proximity * 0.45);
            float sensorPitch = (float) (0.62 + proximity * 0.68);
            target.playSound(cue, Sound.BLOCK_SCULK_SENSOR_CLICKING, sensorVolume, sensorPitch);
            if (proximity >= 0.65) {
                target.playSound(cue, Sound.ENTITY_WARDEN_HEARTBEAT,
                        (float) (0.25 + proximity * 0.35),
                        (float) (0.82 + proximity * 0.28));
            }
        }
        nextWarningTick.put(id, serviceTick + interval);
    }

    public void playLockSounds(Player shooter, Player target) {
        if (shooter != null && shooter.isOnline()) {
            if (hasCustomAudio(shooter)) {
                shooter.playSound(shooter.getLocation(), SOUND_LOCK_CONFIRM,
                        SoundCategory.PLAYERS, 0.82f, 1.0f);
            } else {
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.45f);
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.35f, 1.8f);
            }
        }
        if (hasCustomAudio(target)) {
            target.playSound(target.getLocation(), SOUND_MISSILE_WARNING,
                    SoundCategory.PLAYERS, 0.85f, 0.88f);
        } else {
            target.playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.75f, 0.65f);
            target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.05f);
        }
    }

    public void playManualLockCue(Player shooter) {
        if (shooter == null || !shooter.isOnline()) {
            return;
        }
        if (hasCustomAudio(shooter)) {
            shooter.playSound(shooter.getLocation(), SOUND_LOCK_CONFIRM,
                    SoundCategory.PLAYERS, 0.82f, 1.0f);
        } else {
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.45f);
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.35f, 1.8f);
        }
    }

    public void playLaunchCue(Player shooter) {
        if (shooter != null && shooter.isOnline() && hasCustomAudio(shooter)) {
            shooter.playSound(shooter.getLocation(), SOUND_LAUNCH,
                    SoundCategory.PLAYERS, 0.76f, 1.0f);
        }
    }

    private boolean hasCustomAudio(Player player) {
        PluginSettings settings = settingsManager.current();
        return player != null && settings.hudEnabled() && settings.pixelHudEnabled()
                && packReady.contains(player.getUniqueId());
    }

    private static double groundClearance(Player player) {
        try {
            Location location = player.getLocation();
            World world = player.getWorld();
            int blockX = (int) Math.floor(location.getX());
            int blockZ = (int) Math.floor(location.getZ());
            int surfaceY = world.getHighestBlockYAt(blockX, blockZ);
            return Math.max(0.0, location.getY() - (surfaceY + 1.0));
        } catch (RuntimeException ignored) {
            // Height telemetry must never interrupt the guidance scheduler.
            return 0.0;
        }
    }

    private void showOverlay(Player player, String glyphs) {
        player.sendActionBar(Component.text(glyphs).font(HUD_FONT));
        overlayPlayers.put(player.getUniqueId(), player);
    }

    private void removeStaleOverlays(Set<UUID> active) {
        Iterator<Map.Entry<UUID, Player>> iterator = overlayPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Player> entry = iterator.next();
            if (active.contains(entry.getKey())) {
                continue;
            }
            if (entry.getValue().isOnline()) {
                entry.getValue().sendActionBar(Component.empty());
            }
            telemetryStates.remove(entry.getKey());
            iterator.remove();
        }
    }

    private void clearOverlays() {
        for (Player player : overlayPlayers.values()) {
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
        }
        overlayPlayers.clear();
        telemetryStates.clear();
    }

    private static byte[] decodeSha1(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        byte[] result = new byte[20];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return result;
    }

    private static boolean isTerminalPackFailure(PlayerResourcePackStatusEvent.Status status) {
        return status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL
                || status == PlayerResourcePackStatusEvent.Status.FAILED_RELOAD
                || status == PlayerResourcePackStatusEvent.Status.DISCARDED;
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

    private static double clamp(double value, double min, double max) {
        double safe = Double.isFinite(value) ? value : min;
        return Math.max(min, Math.min(max, safe));
    }

    private static double wrapDegrees(double value) {
        return ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
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

    private static final class AimDisplay {
        final Player shooter;
        final boolean targetPresent;
        final double progress;
        final boolean locked;
        final double screenX;
        final double screenY;

        AimDisplay(Player shooter, boolean targetPresent, double progress,
                   boolean locked, double screenX, double screenY) {
            this.shooter = shooter;
            this.targetPresent = targetPresent;
            this.progress = progress;
            this.locked = locked;
            this.screenX = screenX;
            this.screenY = screenY;
        }
    }

    private static final class TelemetryState {
        private static final double SMOOTHING = 0.32;
        boolean initialized;
        double yaw;
        double pitch;
        double speed;
        double altitude;
        double clearance;

        void update(double nextYaw, double nextPitch, double nextSpeed,
                    double nextAltitude, double nextClearance) {
            if (!initialized) {
                initialized = true;
                yaw = nextYaw;
                pitch = nextPitch;
                speed = nextSpeed;
                altitude = nextAltitude;
                clearance = nextClearance;
                return;
            }
            yaw += wrapDegrees(nextYaw - yaw) * SMOOTHING;
            pitch += (nextPitch - pitch) * SMOOTHING;
            speed += (nextSpeed - speed) * SMOOTHING;
            altitude += (nextAltitude - altitude) * SMOOTHING;
            clearance += (nextClearance - clearance) * SMOOTHING;
        }
    }
}
