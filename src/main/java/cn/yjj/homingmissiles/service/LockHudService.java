package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.util.HudFormat;
import cn.yjj.homingmissiles.util.MessageService;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LockHudService {
    private final SettingsManager settingsManager;
    private final MessageService messages;

    private long serviceTick;
    private final Map<UUID, TargetThreat> targetThreats = new HashMap<>();
    private final Map<UUID, ShooterLock> shooterLocks = new HashMap<>();
    private final Map<UUID, Long> nextBeepTick = new HashMap<>();
    private final Set<Player> hudActiveLast = new HashSet<>();
    private final Set<Player> hudActiveCurrent = new HashSet<>();

    public LockHudService(SettingsManager settingsManager, MessageService messages) {
        this.settingsManager = settingsManager;
        this.messages = messages;
    }

    public void beginTick(long serviceTick) {
        this.serviceTick = serviceTick;
        targetThreats.clear();
        shooterLocks.clear();
        hudActiveCurrent.clear();
    }

    public void reportLock(Player shooter, Player target, Location arrowLocation, double distance) {
        if (target == null) {
            return;
        }

        String shooterName = shooter != null ? shooter.getName() : "?";
        double ax = arrowLocation.getX();
        double az = arrowLocation.getZ();

        UUID targetId = target.getUniqueId();
        TargetThreat existingTarget = targetThreats.get(targetId);
        if (existingTarget == null || distance < existingTarget.distance) {
            targetThreats.put(targetId, new TargetThreat(target, shooterName, distance, ax, az));
        }

        if (shooter != null && shooter.isOnline()) {
            UUID shooterId = shooter.getUniqueId();
            ShooterLock existingShooter = shooterLocks.get(shooterId);
            if (existingShooter == null || distance < existingShooter.distance) {
                shooterLocks.put(shooterId, new ShooterLock(shooter, target.getName(), distance, ax, az));
            }
        }
    }

    public void endTick() {
        PluginSettings settings = settingsManager.current();
        if (!settings.hudEnabled()) {
            clearStaleHud();
            rotateHudHolders();
            pruneBeeps(Set.of());
            return;
        }

        Set<UUID> threatened = new HashSet<>();
        for (TargetThreat threat : targetThreats.values()) {
            Player target = threat.target;
            if (!target.isOnline()) {
                continue;
            }
            threatened.add(target.getUniqueId());

            Location eye = target.getEyeLocation();
            String dir = HudFormat.cardinal(eye.getYaw(), eye.getX(), eye.getZ(), threat.arrowX, threat.arrowZ);
            String distanceText = String.format(Locale.ROOT, "%.1f", threat.distance);

            if (settings.targetActionBar()) {
                target.sendActionBar(messages.text(
                        "hud-target",
                        "distance", distanceText,
                        "dir", dir,
                        "shooter", threat.shooterName));
                hudActiveCurrent.add(target);
            }

            if (settings.warningBeep()) {
                maybeBeep(target, threat.distance, settings);
            }
        }

        for (ShooterLock lock : shooterLocks.values()) {
            Player shooter = lock.shooter;
            if (!shooter.isOnline()) {
                continue;
            }

            Location eye = shooter.getEyeLocation();
            String dir = HudFormat.cardinal(eye.getYaw(), eye.getX(), eye.getZ(), lock.arrowX, lock.arrowZ);
            String distanceText = String.format(Locale.ROOT, "%.1f", lock.distance);

            if (settings.shooterActionBar()) {
                shooter.sendActionBar(messages.text(
                        "hud-shooter",
                        "target", lock.targetName,
                        "distance", distanceText,
                        "dir", dir));
                hudActiveCurrent.add(shooter);
            }
        }

        clearStaleHud();
        rotateHudHolders();
        pruneBeeps(threatened);
    }

    private void maybeBeep(Player target, double distance, PluginSettings settings) {
        UUID id = target.getUniqueId();
        long next = nextBeepTick.getOrDefault(id, 0L);
        if (serviceTick < next) {
            return;
        }

        int min = settings.beepMinIntervalTicks();
        int max = settings.beepMaxIntervalTicks();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }

        int interval = beepInterval(distance, min, max);
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.55f);
        nextBeepTick.put(id, serviceTick + Math.max(1, interval));
    }

    private static int beepInterval(double distance, int min, int max) {
        if (distance > 32.0) {
            return max;
        }
        if (distance > 16.0) {
            return (min + max) / 2;
        }
        if (distance > 8.0) {
            return Math.max(min, (min + max) / 4);
        }
        return min;
    }

    private void clearStaleHud() {
        for (Player player : hudActiveLast) {
            if (hudActiveCurrent.contains(player)) {
                continue;
            }
            if (player != null && player.isOnline()) {
                player.sendActionBar("");
            }
        }
    }

    private void rotateHudHolders() {
        hudActiveLast.clear();
        hudActiveLast.addAll(hudActiveCurrent);
    }

    private void pruneBeeps(Set<UUID> threatened) {
        Iterator<UUID> it = nextBeepTick.keySet().iterator();
        while (it.hasNext()) {
            if (!threatened.contains(it.next())) {
                it.remove();
            }
        }
    }

    private static final class TargetThreat {
        final Player target;
        final String shooterName;
        final double distance;
        final double arrowX;
        final double arrowZ;

        TargetThreat(Player target, String shooterName, double distance, double arrowX, double arrowZ) {
            this.target = target;
            this.shooterName = shooterName;
            this.distance = distance;
            this.arrowX = arrowX;
            this.arrowZ = arrowZ;
        }
    }

    private static final class ShooterLock {
        final Player shooter;
        final String targetName;
        final double distance;
        final double arrowX;
        final double arrowZ;

        ShooterLock(Player shooter, String targetName, double distance, double arrowX, double arrowZ) {
            this.shooter = shooter;
            this.targetName = targetName;
            this.distance = distance;
            this.arrowX = arrowX;
            this.arrowZ = arrowZ;
        }
    }
}
