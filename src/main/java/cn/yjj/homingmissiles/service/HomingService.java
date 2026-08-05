package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.model.TrackedArrow;
import cn.yjj.homingmissiles.util.MessageService;
import cn.yjj.homingmissiles.util.VectorMath;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class HomingService {
    private final HomingMissilesPlugin plugin;
    private final SettingsManager settingsManager;
    private final MessageService messages;
    private final LockHudService lockHud;
    private final Map<UUID, TrackedArrow> tracked = new LinkedHashMap<>();
    private final Map<UUID, Integer> shooterCounts = new HashMap<>();
    private final Map<UUID, Long> lastLaunchTick = new HashMap<>();

    private final NamespacedKey projectileKey;
    private final NamespacedKey shooterKey;
    private final NamespacedKey ageKey;
    private final NamespacedKey sessionKey;
    private final String sessionId = UUID.randomUUID().toString();

    private BukkitTask tickTask;
    private long serviceTick;
    private long totalArrowFailures;
    private long totalTicks;
    private int processedLastTick;
    private double averageTickMillis;
    private double peakTickMillis;

    public HomingService(HomingMissilesPlugin plugin, SettingsManager settingsManager,
                         MessageService messages, LockHudService lockHud) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        this.messages = messages;
        this.lockHud = lockHud;
        this.projectileKey = new NamespacedKey(plugin, "homing_projectile");
        this.shooterKey = new NamespacedKey(plugin, "shooter_uuid");
        this.ageKey = new NamespacedKey(plugin, "age_ticks");
        this.sessionKey = new NamespacedKey(plugin, "session_id");
    }

    public void start() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::safeTick, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        boolean remove = settingsManager.current().removeArrowsOnDisable();
        for (TrackedArrow state : new ArrayList<>(tracked.values())) {
            AbstractArrow arrow = state.arrow();
            if (arrow == null || !arrow.isValid()) {
                continue;
            }
            if (remove) {
                arrow.remove();
            } else {
                persistState(state);
            }
        }
        tracked.clear();
        shooterCounts.clear();
        lastLaunchTick.clear();
        lockHud.shutdown();
    }

    public int recoverLoadedArrows() {
        if (!settingsManager.current().recoverArrowsOnEnable()) {
            return 0;
        }
        int recovered = 0;
        for (World world : plugin.getServer().getWorlds()) {
            recovered += recoverEntities(world.getEntities());
        }
        return recovered;
    }

    public int recoverEntities(Iterable<? extends Entity> entities) {
        if (!settingsManager.current().recoverArrowsOnEnable()) {
            return 0;
        }
        int recovered = 0;
        PluginSettings settings = settingsManager.current();
        for (Entity entity : entities) {
            if (!(entity instanceof AbstractArrow arrow) || tracked.containsKey(arrow.getUniqueId())) {
                continue;
            }
            PersistentDataContainer pdc = arrow.getPersistentDataContainer();
            Byte marked = pdc.get(projectileKey, PersistentDataType.BYTE);
            if (marked == null || marked != (byte) 1 || arrow.isDead() || arrow.isInBlock()) {
                continue;
            }
            String storedSession = pdc.get(sessionKey, PersistentDataType.STRING);
            if (!sessionId.equals(storedSession) && settings.removeArrowsOnDisable()) {
                clearPersistentState(arrow);
                arrow.remove();
                continue;
            }
            String rawShooter = pdc.get(shooterKey, PersistentDataType.STRING);
            Integer age = pdc.get(ageKey, PersistentDataType.INTEGER);
            try {
                UUID shooterId = UUID.fromString(rawShooter == null ? "" : rawShooter);
                if (tracked.size() >= settings.maxTrackedArrows()
                        || activeCount(shooterId) >= settings.maxTrackedPerPlayer()) {
                    clearPersistentState(arrow);
                    arrow.setGravity(true);
                    continue;
                }
                addState(new TrackedArrow(arrow, shooterId, age == null ? 0 : age));
                applyArrowProperties(arrow);
                recovered++;
            } catch (IllegalArgumentException ex) {
                clearPersistentState(arrow);
                plugin.getLogger().warning("忽略射手UUID损坏的遗留制导箭：" + arrow.getUniqueId());
            }
        }
        return recovered;
    }

    public int suspendEntities(Iterable<? extends Entity> entities) {
        int suspended = 0;
        for (Entity entity : entities) {
            if (!(entity instanceof AbstractArrow arrow)) {
                continue;
            }
            TrackedArrow state = tracked.remove(arrow.getUniqueId());
            if (state == null) {
                continue;
            }
            persistState(state);
            decrementShooter(state.shooterId());
            suspended++;
        }
        return suspended;
    }

    public void forgetPlayer(UUID playerId) {
        lastLaunchTick.remove(playerId);
        lockHud.forgetPlayer(playerId);
    }

    public TrackResult tryTrack(Player shooter, AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        if (isWorldDisabled(arrow.getWorld())) {
            return TrackResult.rejected(RejectReason.WORLD_DISABLED, 0);
        }

        boolean bypass = shooter.hasPermission("homingmissiles.bypass.limits");
        if (!bypass && tracked.size() >= settings.maxTrackedArrows()) {
            return TrackResult.rejected(RejectReason.GLOBAL_LIMIT, 0);
        }

        int own = activeCount(shooter.getUniqueId());
        // 四联数据链是玩法硬约束；管理员权限也不能制造第五枚在途导弹。
        if (own >= settings.maxTrackedPerPlayer()) {
            return TrackResult.rejected(RejectReason.PLAYER_LIMIT, 0);
        }

        if (!bypass && settings.launchCooldownTicks() > 0) {
            long last = lastLaunchTick.getOrDefault(shooter.getUniqueId(), Long.MIN_VALUE / 2);
            long elapsed = serviceTick - last;
            if (elapsed < settings.launchCooldownTicks()) {
                return TrackResult.rejected(RejectReason.COOLDOWN,
                        (int) (settings.launchCooldownTicks() - elapsed));
            }
        }

        applyArrowProperties(arrow);
        TrackedArrow state = new TrackedArrow(arrow, shooter.getUniqueId(), 0);
        addState(state);
        persistState(state);
        lastLaunchTick.put(shooter.getUniqueId(), serviceTick);

        spawnLaunchEffects(arrow);
        messages.feedback(shooter, settings.launchFeedback(), "launch",
                "active", activeCount(shooter.getUniqueId()),
                "limit", settings.maxTrackedPerPlayer());
        return TrackResult.success();
    }

    public void handleHit(AbstractArrow arrow) {
        TrackedArrow removed = removeState(arrow.getUniqueId(), false);
        if (removed == null) {
            return;
        }
        clearPersistentState(arrow);
        if (settingsManager.current().impactEffects()) {
            Location loc = arrow.getLocation();
            World world = arrow.getWorld();
            world.spawnParticle(Particle.EXPLOSION, loc, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.SONIC_BOOM, loc, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 18, 0.22, 0.18, 0.22, 0.035);
            world.spawnParticle(Particle.FLAME, loc, 12, 0.18, 0.18, 0.18, 0.045);
            world.spawnParticle(Particle.CRIT, loc, 16, 0.3, 0.25, 0.3, 0.18);
            world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.1f, 0.82f);
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.85f, 0.68f);
        }
    }

    public int activeCount() {
        return tracked.size();
    }

    public int activeCount(UUID shooterId) {
        return shooterCounts.getOrDefault(shooterId, 0);
    }

    public StatusSnapshot status() {
        return new StatusSnapshot(
                tracked.size(),
                settingsManager.current().maxTrackedArrows(),
                processedLastTick,
                averageTickMillis,
                peakTickMillis,
                totalArrowFailures,
                serviceTick,
                Map.copyOf(shooterCounts));
    }

    public Inspection inspect(Player player) {
        List<ArrowInfo> arrows = new ArrayList<>();
        for (TrackedArrow state : tracked.values()) {
            if (!state.shooterId().equals(player.getUniqueId())) {
                continue;
            }
            Player target = state.targetId() == null ? null : plugin.getServer().getPlayer(state.targetId());
            AbstractArrow arrow = state.arrow();
            arrows.add(new ArrowInfo(
                    arrow.getUniqueId(),
                    state.ageTicks(),
                    target == null ? "搜索中" : target.getName(),
                    arrow.getVelocity().length(),
                    arrow.getWorld().getName()));
        }
        arrows.sort(Comparator.comparingInt(ArrowInfo::ageTicks).reversed());
        return new Inspection(player.getUniqueId(), player.getName(), List.copyOf(arrows));
    }

    public int clearMine(UUID shooterId) {
        return clearWhere(state -> state.shooterId().equals(shooterId));
    }

    public int clearPlayer(UUID shooterId) {
        return clearMine(shooterId);
    }

    public int clearWorld(String worldName) {
        return clearWhere(state -> state.arrow().getWorld().getName().equalsIgnoreCase(worldName));
    }

    public int clearAll() {
        return clearWhere(state -> true);
    }

    public List<ShooterCount> topShooters(int limit) {
        return shooterCounts.entrySet().stream()
                .map(entry -> {
                    Player player = plugin.getServer().getPlayer(entry.getKey());
                    return new ShooterCount(entry.getKey(), player == null ? entry.getKey().toString() : player.getName(), entry.getValue());
                })
                .sorted(Comparator.comparingInt(ShooterCount::count).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    public boolean isWorldDisabled(World world) {
        return settingsManager.current().disabledWorlds().contains(world.getName().toLowerCase(Locale.ROOT));
    }

    private void safeTick() {
        long started = System.nanoTime();
        serviceTick++;
        int processed = 0;
        enforcePerPlayerLimit();
        lockHud.beginTick(serviceTick);

        Iterator<Map.Entry<UUID, TrackedArrow>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedArrow> entry = iterator.next();
            TrackedArrow state = entry.getValue();
            try {
                if (!processArrow(state)) {
                    decrementShooter(state.shooterId());
                    iterator.remove();
                } else {
                    processed++;
                }
            } catch (RuntimeException ex) {
                totalArrowFailures++;
                AbstractArrow arrow = state.arrow();
                plugin.getLogger().log(Level.WARNING,
                        "制导箭 " + entry.getKey() + " 计算异常，已隔离并删除，避免中断整个tick任务。", ex);
                if (arrow != null && arrow.isValid()) {
                    clearPersistentState(arrow);
                    arrow.remove();
                }
                decrementShooter(state.shooterId());
                iterator.remove();
            }
        }

        lockHud.endTick();
        processedLastTick = processed;
        double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
        totalTicks++;
        averageTickMillis = totalTicks == 1 ? elapsedMillis : averageTickMillis * 0.95 + elapsedMillis * 0.05;
        peakTickMillis = Math.max(peakTickMillis, elapsedMillis);
    }

    private void enforcePerPlayerLimit() {
        int limit = settingsManager.current().maxTrackedPerPlayer();
        Map<UUID, Integer> admitted = new HashMap<>();
        Iterator<Map.Entry<UUID, TrackedArrow>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedArrow state = iterator.next().getValue();
            AbstractArrow arrow = state.arrow();
            if (arrow == null || !arrow.isValid() || arrow.isDead()) {
                continue;
            }
            int position = admitted.merge(state.shooterId(), 1, Integer::sum);
            if (position <= limit) {
                continue;
            }
            clearPersistentState(arrow);
            arrow.remove();
            decrementShooter(state.shooterId());
            iterator.remove();
        }
    }

    private boolean processArrow(TrackedArrow state) {
        AbstractArrow arrow = state.arrow();
        int age = state.incrementAge();
        PluginSettings settings = settingsManager.current();

        if (arrow == null || !arrow.isValid() || arrow.isDead()) {
            return false;
        }
        if (arrow.isInBlock()) {
            clearPersistentState(arrow);
            return false;
        }
        if (isWorldDisabled(arrow.getWorld())) {
            clearPersistentState(arrow);
            arrow.setGravity(true);
            return false;
        }
        if (age >= settings.maxLifetimeTicks()) {
            selfDestruct(arrow);
            return false;
        }
        if (age % 20 == 0) {
            persistState(state);
        }
        Player shooter = plugin.getServer().getPlayer(state.shooterId());
        lockHud.reportOutbound(shooter);
        if (age < settings.activationDelayTicks()) {
            spawnSearchEffect(arrow, state);
            return true;
        }

        Player target = selectTarget(state, arrow);
        if (target == null) {
            state.targetId(null);
            state.lockNotifiedTarget(null);
            spawnSearchEffect(arrow, state);
            return true;
        }

        state.targetId(target.getUniqueId());
        notifyLockIfNeeded(state, target);
        steerArrow(arrow, target);
        spawnTrackingEffects(arrow, state);

        Location arrowLocation = arrow.getLocation();
        double distance = arrowLocation.distance(target.getEyeLocation());
        lockHud.reportLock(target, arrowLocation, distance);
        return true;
    }

    private Player selectTarget(TrackedArrow state, AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        World world = arrow.getWorld();
        Location arrowLocation = arrow.getLocation();
        double rangeSquared = settings.trackingRange() * settings.trackingRange();

        Player current = state.targetId() == null ? null : plugin.getServer().getPlayer(state.targetId());
        boolean currentValid = isValidTarget(current, state, world, arrowLocation, rangeSquared, arrow);

        Player nearest = null;
        double nearestDistanceSquared = rangeSquared;
        for (Player candidate : world.getPlayers()) {
            if (!isValidTarget(candidate, state, world, arrowLocation, rangeSquared, arrow)) {
                continue;
            }
            double distanceSquared = candidate.getEyeLocation().distanceSquared(arrowLocation);
            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = candidate;
            }
        }

        if (!settings.dynamicRetargeting()) {
            return currentValid ? current : nearest;
        }
        if (!currentValid || nearest == null || current == null) {
            return nearest;
        }
        if (nearest.getUniqueId().equals(current.getUniqueId())) {
            return current;
        }

        double currentDistance = Math.sqrt(current.getEyeLocation().distanceSquared(arrowLocation));
        double nearestDistance = Math.sqrt(nearestDistanceSquared);
        return nearestDistance + settings.switchAdvantageBlocks() < currentDistance ? nearest : current;
    }

    private boolean isValidTarget(Player player, TrackedArrow state, World world,
                                  Location arrowLocation, double rangeSquared, AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }
        if (player.getUniqueId().equals(state.shooterId())) {
            return false;
        }
        if (!player.getWorld().equals(world)) {
            return false;
        }
        if (player.hasPermission("homingmissiles.target.exempt")) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.SPECTATOR && !settings.targetSpectator()) {
            return false;
        }
        if (mode == GameMode.CREATIVE && !settings.targetCreative()) {
            return false;
        }
        if (player.getEyeLocation().distanceSquared(arrowLocation) > rangeSquared) {
            return false;
        }

        Player shooter = plugin.getServer().getPlayer(state.shooterId());
        if (settings.respectVanish() && shooter != null && shooter.isOnline() && !shooter.canSee(player)) {
            return false;
        }
        return !settings.requireLineOfSight() || player.hasLineOfSight(arrow);
    }

    private void steerArrow(AbstractArrow arrow, Player target) {
        PluginSettings settings = settingsManager.current();
        Vector currentVelocity = arrow.getVelocity();
        Location arrowLocation = arrow.getLocation();

        Vector predictedTarget = target.getEyeLocation().toVector()
                .add(target.getVelocity().clone().multiply(settings.leadTicks()));
        Vector toTarget = predictedTarget.subtract(arrowLocation.toVector());
        if (!VectorMath.isFinite(toTarget) || toTarget.lengthSquared() < 1.0E-8) {
            return;
        }

        Vector desiredDirection = toTarget.normalize();
        double currentSpeed = VectorMath.isFinite(currentVelocity) ? currentVelocity.length() : 0.0;
        Vector currentDirection = currentSpeed < 1.0E-6
                ? desiredDirection.clone()
                : currentVelocity.clone().normalize();

        Vector newDirection = VectorMath.rotateTowards(
                currentDirection, desiredDirection, Math.toRadians(settings.turnRateDegreesPerTick()));
        double newSpeed = VectorMath.clamp(
                currentSpeed + settings.accelerationPerTick(), settings.minSpeed(), settings.maxSpeed());
        Vector newVelocity = newDirection.multiply(newSpeed);
        if (!VectorMath.isFinite(newVelocity)) {
            throw new IllegalStateException("制导速度出现非有限值");
        }
        arrow.setVelocity(newVelocity);
    }

    private void notifyLockIfNeeded(TrackedArrow state, Player target) {
        UUID targetId = target.getUniqueId();
        if (targetId.equals(state.lockNotifiedTarget())) {
            return;
        }
        state.lockNotifiedTarget(targetId);

        PluginSettings settings = settingsManager.current();
        Player shooter = plugin.getServer().getPlayer(state.shooterId());
        if (settings.lockSounds()) {
            if (shooter != null && shooter.isOnline()) {
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.45f);
                shooter.playSound(shooter.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.35f, 1.8f);
            }
            target.playSound(target.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.75f, 0.65f);
            target.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.05f);
        }

        if (shooter != null && shooter.isOnline()) {
            // 不向射手暴露目标名、距离、方向或速度；BossBar 也只显示在途通道占用。
            messages.feedback(shooter, settings.lockShooterFeedback(), "guidance-link");
        }
        messages.feedback(target, settings.lockTargetFeedback(), "inbound-warning");

        if (settings.particles()) {
            Location loc = state.arrow().getLocation();
            state.arrow().getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK, loc, 7, 0.08, 0.08, 0.08, 0.025);
        }
    }

    private void spawnLaunchEffects(AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        Location loc = exhaustLocation(arrow, 0.2);
        World world = arrow.getWorld();
        if (settings.launchEffects() && settings.particles()) {
            world.spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 7, 0.12, 0.12, 0.12, 0.025);
            world.spawnParticle(Particle.SMALL_FLAME, loc, 9, 0.08, 0.08, 0.08, 0.035);
        }
        if (settings.launchSound()) {
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.9f, 0.72f);
            world.playSound(loc, Sound.ITEM_CROSSBOW_SHOOT, 0.55f, 0.55f);
        }
    }

    private void spawnTrackingEffects(AbstractArrow arrow, TrackedArrow state) {
        PluginSettings settings = settingsManager.current();
        if (!settings.particles() || state.ageTicks() % settings.particleIntervalTicks() != 0) {
            return;
        }
        World world = arrow.getWorld();
        Location exhaust = exhaustLocation(arrow, 0.38);
        world.spawnParticle(Particle.SMALL_FLAME, exhaust, 2, 0.018, 0.018, 0.018, 0.006);
        world.spawnParticle(Particle.WHITE_SMOKE, exhaust, 2, 0.035, 0.035, 0.035, 0.012);
        if (state.ageTicks() % 4 == 0) {
            world.spawnParticle(Particle.END_ROD, exhaust, 1, 0.012, 0.012, 0.012, 0.0);
        }
        if (state.ageTicks() % 3 == 0) {
            Location wake = exhaustLocation(arrow, 0.78);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, wake, 1, 0.025, 0.025, 0.025, 0.002);
        }
    }

    private void spawnSearchEffect(AbstractArrow arrow, TrackedArrow state) {
        PluginSettings settings = settingsManager.current();
        if (!settings.particles() || state.ageTicks() % settings.particleIntervalTicks() != 0) {
            return;
        }
        Location exhaust = exhaustLocation(arrow, 0.32);
        arrow.getWorld().spawnParticle(Particle.WHITE_SMOKE, exhaust, 1, 0.025, 0.025, 0.025, 0.004);
        if (state.ageTicks() % 3 == 0) {
            arrow.getWorld().spawnParticle(Particle.SMALL_FLAME, exhaust, 1, 0.01, 0.01, 0.01, 0.002);
        }
    }

    private void selfDestruct(AbstractArrow arrow) {
        if (settingsManager.current().selfDestructEffects()) {
            Location loc = arrow.getLocation();
            World world = arrow.getWorld();
            world.spawnParticle(Particle.FLASH, loc, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 14, 0.2, 0.2, 0.2, 0.025);
            world.spawnParticle(Particle.CLOUD, loc, 8, 0.15, 0.15, 0.15, 0.04);
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 0.8f, 0.58f);
            world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 0.5f);
        }
        clearPersistentState(arrow);
        arrow.remove();
    }

    private void applyArrowProperties(AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGravity(!settings.noGravity());
        arrow.setGlowing(settings.glowingArrow());
        if (settings.minimumDamage() >= 0.0 && arrow.getDamage() < settings.minimumDamage()) {
            arrow.setDamage(settings.minimumDamage());
        }
    }

    private static Location exhaustLocation(AbstractArrow arrow, double distance) {
        Location result = arrow.getLocation();
        Vector velocity = arrow.getVelocity();
        if (VectorMath.isFinite(velocity) && velocity.lengthSquared() > 1.0E-8) {
            result.add(velocity.clone().normalize().multiply(-distance));
        }
        return result;
    }

    private void persistState(TrackedArrow state) {
        AbstractArrow arrow = state.arrow();
        if (arrow == null || !arrow.isValid()) {
            return;
        }
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(shooterKey, PersistentDataType.STRING, state.shooterId().toString());
        pdc.set(ageKey, PersistentDataType.INTEGER, state.ageTicks());
        pdc.set(sessionKey, PersistentDataType.STRING, sessionId);
    }

    private void clearPersistentState(AbstractArrow arrow) {
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.remove(projectileKey);
        pdc.remove(shooterKey);
        pdc.remove(ageKey);
        pdc.remove(sessionKey);
    }

    private void addState(TrackedArrow state) {
        tracked.put(state.arrow().getUniqueId(), state);
        shooterCounts.merge(state.shooterId(), 1, Integer::sum);
    }

    private TrackedArrow removeState(UUID arrowId, boolean removeEntity) {
        TrackedArrow state = tracked.remove(arrowId);
        if (state == null) {
            return null;
        }
        decrementShooter(state.shooterId());
        AbstractArrow arrow = state.arrow();
        if (arrow != null && arrow.isValid()) {
            clearPersistentState(arrow);
            if (removeEntity) {
                arrow.remove();
            }
        }
        return state;
    }

    private int clearWhere(Predicate<TrackedArrow> predicate) {
        int count = 0;
        Iterator<Map.Entry<UUID, TrackedArrow>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            TrackedArrow state = iterator.next().getValue();
            if (!predicate.test(state)) {
                continue;
            }
            AbstractArrow arrow = state.arrow();
            if (arrow != null && arrow.isValid()) {
                clearPersistentState(arrow);
                arrow.remove();
            }
            decrementShooter(state.shooterId());
            iterator.remove();
            count++;
        }
        return count;
    }

    private void decrementShooter(UUID shooterId) {
        shooterCounts.computeIfPresent(shooterId, (id, count) -> count <= 1 ? null : count - 1);
    }

    public enum RejectReason {
        NONE,
        GLOBAL_LIMIT,
        PLAYER_LIMIT,
        COOLDOWN,
        WORLD_DISABLED
    }

    public record TrackResult(boolean accepted, RejectReason reason, int remainingTicks) {
        static TrackResult success() {
            return new TrackResult(true, RejectReason.NONE, 0);
        }

        static TrackResult rejected(RejectReason reason, int remainingTicks) {
            return new TrackResult(false, reason, remainingTicks);
        }
    }

    public record StatusSnapshot(int active, int limit, int processedLastTick,
                                 double averageTickMillis, double peakTickMillis,
                                 long arrowFailures, long serviceTick,
                                 Map<UUID, Integer> shooterCounts) {
    }

    public record ArrowInfo(UUID arrowId, int ageTicks, String targetName,
                            double speed, String worldName) {
    }

    public record Inspection(UUID playerId, String playerName, List<ArrowInfo> arrows) {
    }

    public record ShooterCount(UUID shooterId, String name, int count) {
    }
}
