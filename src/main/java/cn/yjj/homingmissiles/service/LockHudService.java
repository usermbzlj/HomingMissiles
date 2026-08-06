package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.util.HudFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
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

    private final SettingsManager settingsManager;

    private long serviceTick;
    private final Map<UUID, ShooterState> shooterStates = new HashMap<>();
    private final Map<UUID, TargetThreat> targetThreats = new HashMap<>();
    private final Map<UUID, Long> nextWarningTick = new HashMap<>();
    private final Map<UUID, BossBar> shooterBars = new HashMap<>();
    private final Map<UUID, BossBar> targetBars = new HashMap<>();
    private final Map<UUID, Player> overlayPlayers = new HashMap<>();
    private final Set<UUID> packReady = new HashSet<>();

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
            clearOverlays();
            clearBars(shooterBars);
            clearBars(targetBars);
            nextWarningTick.clear();
            return;
        }

        Set<UUID> pixelPlayers = renderPixelOverlays(settings);
        Set<UUID> activeShooters = renderShooterHud(settings, pixelPlayers);
        Set<UUID> threatened = renderThreatHudAndAudio(settings, pixelPlayers);
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
        removeBar(shooterBars, playerId);
        removeBar(targetBars, playerId);
    }

    public void shutdown() {
        clearOverlays();
        clearBars(shooterBars);
        clearBars(targetBars);
        shooterStates.clear();
        targetThreats.clear();
        nextWarningTick.clear();
        packReady.clear();
    }

    private Set<UUID> renderPixelOverlays(PluginSettings settings) {
        if (!settings.pixelHudEnabled()) {
            clearOverlays();
            return Set.of();
        }

        Set<UUID> active = new HashSet<>();
        // Incoming-threat symbology has priority when a player is both shooter and target.
        for (Map.Entry<UUID, TargetThreat> entry : targetThreats.entrySet()) {
            UUID playerId = entry.getKey();
            TargetThreat threat = entry.getValue();
            if (!packReady.contains(playerId) || !threat.target.isOnline()) {
                continue;
            }
            Location eye = threat.target.getEyeLocation();
            int sector = HudFormat.directionSector(
                    eye.getYaw(), eye.getX(), eye.getZ(), threat.arrowX, threat.arrowZ);
            double proximity = HudFormat.proximity(threat.distance, settings.lockRetentionRange());
            showGlyph(threat.target, HudFormat.threatGlyph(sector, proximity));
            active.add(playerId);
        }

        for (Map.Entry<UUID, ShooterState> entry : shooterStates.entrySet()) {
            UUID playerId = entry.getKey();
            ShooterState state = entry.getValue();
            if (active.contains(playerId) || !packReady.contains(playerId) || !state.player.isOnline()) {
                continue;
            }
            showGlyph(state.player, HudFormat.shooterGlyph(state.active));
            active.add(playerId);
        }

        removeStaleOverlays(active);
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

    private void showGlyph(Player player, char glyph) {
        player.sendActionBar(Component.text(String.valueOf(glyph)).font(HUD_FONT));
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
