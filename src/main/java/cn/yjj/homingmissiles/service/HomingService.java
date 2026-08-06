package cn.yjj.homingmissiles.service;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import cn.yjj.homingmissiles.model.TrackedArrow;
import cn.yjj.homingmissiles.util.GuidanceMath;
import cn.yjj.homingmissiles.util.MessageService;
import cn.yjj.homingmissiles.util.ParticleEffectSupport;
import cn.yjj.homingmissiles.util.VectorMath;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class HomingService {
    private final HomingMissilesPlugin plugin;
    private final SettingsManager settingsManager;
    private final MessageService messages;
    private final LockHudService lockHud;
    private final ManualLockService manualLocks;
    private final Map<UUID, TrackedArrow> tracked = new LinkedHashMap<>();
    private final Map<UUID, Integer> shooterCounts = new HashMap<>();
    private final Map<UUID, Long> lastLaunchTick = new HashMap<>();
    private final Set<String> disabledEffectStages = new HashSet<>();

    private final NamespacedKey projectileKey;
    private final NamespacedKey shooterKey;
    private final NamespacedKey ageKey;
    private final NamespacedKey targetKey;
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
                         MessageService messages, HomingBowFactory bowFactory,
                         LockHudService lockHud) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        this.messages = messages;
        this.lockHud = lockHud;
        this.manualLocks = new ManualLockService(settingsManager, bowFactory, lockHud);
        this.projectileKey = new NamespacedKey(plugin, "homing_projectile");
        this.shooterKey = new NamespacedKey(plugin, "shooter_uuid");
        this.ageKey = new NamespacedKey(plugin, "age_ticks");
        this.targetKey = new NamespacedKey(plugin, "target_uuid");
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
        manualLocks.clear();
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
            String rawTarget = pdc.get(targetKey, PersistentDataType.STRING);
            Integer age = pdc.get(ageKey, PersistentDataType.INTEGER);
            try {
                UUID shooterId = UUID.fromString(rawShooter == null ? "" : rawShooter);
                UUID targetId = UUID.fromString(rawTarget == null ? "" : rawTarget);
                if (tracked.size() >= settings.maxTrackedArrows()
                        || activeCount(shooterId) >= settings.maxTrackedPerPlayer()) {
                    clearPersistentState(arrow);
                    arrow.setGravity(true);
                    continue;
                }
                TrackedArrow state = new TrackedArrow(arrow, shooterId, age == null ? 0 : age);
                state.targetId(targetId);
                addState(state);
                applyArrowProperties(arrow);
                recovered++;
            } catch (IllegalArgumentException ex) {
                clearPersistentState(arrow);
                arrow.setGravity(true);
                plugin.getLogger().warning("忽略射手或目标 UUID 损坏的遗留制导箭：" + arrow.getUniqueId());
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
        manualLocks.forgetPlayer(playerId);
        lockHud.forgetPlayer(playerId);
    }

    public void prepareHud(Player player) {
        lockHud.preparePlayer(player);
    }

    public void handleResourcePackStatus(PlayerResourcePackStatusEvent event) {
        lockHud.handleResourcePackStatus(event);
    }

    public void refreshHudResources() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            lockHud.preparePlayer(player);
        }
    }

    public TrackResult tryTrack(Player shooter, AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        if (isWorldDisabled(arrow.getWorld())) {
            return TrackResult.rejected(RejectReason.WORLD_DISABLED, 0);
        }
        if (manualLocks.lockedTarget(shooter) == null) {
            return TrackResult.rejected(RejectReason.MANUAL_LOCK_REQUIRED, 0);
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

        Player target = manualLocks.consumeLockedTarget(shooter);
        if (target == null) {
            return TrackResult.rejected(RejectReason.MANUAL_LOCK_REQUIRED, 0);
        }

        applyArrowProperties(arrow);
        TrackedArrow state = new TrackedArrow(arrow, shooter.getUniqueId(), 0);
        state.targetId(target.getUniqueId());
        addState(state);
        persistState(state);
        lastLaunchTick.put(shooter.getUniqueId(), serviceTick);

        runEffectSafely("launch", () -> spawnLaunchEffects(arrow, shooter));
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
            runEffectSafely("impact", () -> spawnImpactEffects(arrow));
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
        manualLocks.tick(plugin.getServer().getOnlinePlayers(), serviceTick);

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
            runEffectSafely("search", () -> spawnSearchEffect(arrow, state));
            return true;
        }

        Player target = selectTarget(state, arrow);
        if (target == null) {
            state.lockNotifiedTarget(null);
            state.clearLockObservation();
            runEffectSafely("search", () -> spawnSearchEffect(arrow, state));
            return true;
        }

        Location arrowLocation = arrow.getLocation();
        double distance = arrowLocation.distance(target.getEyeLocation());
        state.observeLock(target.getUniqueId(), distance,
                settings.terminalBoostDelayTicks(), settings.terminalEscapeTriggerTicks());
        state.targetId(target.getUniqueId());
        notifyLockIfNeeded(state, target);
        steerArrow(state, arrow, target);
        runEffectSafely("tracking", () -> spawnTrackingEffects(arrow, state));

        lockHud.reportLock(target, arrowLocation, distance);
        return true;
    }

    private Player selectTarget(TrackedArrow state, AbstractArrow arrow) {
        PluginSettings settings = settingsManager.current();
        World world = arrow.getWorld();
        Location arrowLocation = arrow.getLocation();
        double retentionRangeSquared = settings.lockRetentionRange() * settings.lockRetentionRange();

        Player current = state.targetId() == null ? null : plugin.getServer().getPlayer(state.targetId());
        return isValidTarget(current, state, world, arrowLocation, retentionRangeSquared, arrow)
                ? current
                : null;
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

    private void steerArrow(TrackedArrow state, AbstractArrow arrow, Player target) {
        PluginSettings settings = settingsManager.current();
        Vector currentVelocity = arrow.getVelocity();
        Location arrowLocation = arrow.getLocation();

        Vector relativePosition = target.getEyeLocation().toVector().subtract(arrowLocation.toVector());
        double currentSpeed = VectorMath.isFinite(currentVelocity) ? currentVelocity.length() : 0.0;
        double interceptTicks = GuidanceMath.interceptTimeTicks(
                relativePosition,
                target.getVelocity(),
                Math.max(currentSpeed, settings.minSpeed()),
                settings.maxLeadTicks());
        double leadTicks = Math.max(settings.leadTicks(), interceptTicks);
        Vector predictedTarget = target.getEyeLocation().toVector()
                .add(target.getVelocity().clone().multiply(leadTicks));
        Vector toTarget = predictedTarget.subtract(arrowLocation.toVector());
        if (!VectorMath.isFinite(toTarget) || toTarget.lengthSquared() < 1.0E-8) {
            return;
        }

        Vector desiredDirection = toTarget.normalize();
        Vector currentDirection = currentSpeed < 1.0E-6
                ? desiredDirection.clone()
                : currentVelocity.clone().normalize();

        Vector newDirection = VectorMath.rotateTowards(
                currentDirection, desiredDirection, Math.toRadians(settings.turnRateDegreesPerTick()));
        double acceleration = state.terminalBoosted()
                ? settings.terminalAccelerationPerTick()
                : settings.accelerationPerTick();
        double maximumSpeed = state.terminalBoosted()
                ? settings.terminalMaxSpeed()
                : settings.maxSpeed();
        double newSpeed = VectorMath.clamp(
                currentSpeed + acceleration, settings.minSpeed(), maximumSpeed);
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
            runEffectSafely("lock-audio", () -> lockHud.playLockSounds(shooter, target));
        }

        if (shooter != null && shooter.isOnline()) {
            // 不向射手暴露目标名、距离、方向或速度；像素 HMD 也只显示在途通道占用。
            messages.feedback(shooter, settings.lockShooterFeedback(), "guidance-link");
        }
        messages.feedback(target, settings.lockTargetFeedback(), "inbound-warning");

        if (settings.particles()) {
            runEffectSafely("lock-particle", () -> spawnLockParticle(state.arrow()));
        }
    }

    private static void spawnLockParticle(AbstractArrow arrow) {
        Location loc = arrow.getLocation();
        arrow.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK, loc, 7, 0.08, 0.08, 0.08, 0.025);
    }

    private static void spawnImpactEffects(AbstractArrow arrow) {
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

    private void spawnLaunchEffects(AbstractArrow arrow, Player shooter) {
        PluginSettings settings = settingsManager.current();
        Location loc = exhaustLocation(arrow, 0.2);
        World world = arrow.getWorld();
        if (settings.launchEffects() && settings.particles()) {
            requireFlash(world, loc, Color.fromRGB(255, 232, 168));
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 7, 0.12, 0.12, 0.12, 0.025);
            world.spawnParticle(Particle.SMALL_FLAME, loc, 9, 0.08, 0.08, 0.08, 0.035);
        }
        if (settings.launchSound()) {
            world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.9f, 0.72f);
            world.playSound(loc, Sound.ITEM_CROSSBOW_SHOOT, 0.55f, 0.55f);
            lockHud.playLaunchCue(shooter);
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
        if (state.terminalBoosted()) {
            world.spawnParticle(Particle.FLAME, exhaust, 3, 0.025, 0.025, 0.025, 0.018);
            world.spawnParticle(Particle.CLOUD, exhaust, 1, 0.02, 0.02, 0.02, 0.008);
        }
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
            runEffectSafely("self-destruct", () -> spawnSelfDestructEffects(arrow));
        }
        clearPersistentState(arrow);
        arrow.remove();
    }

    private static void spawnSelfDestructEffects(AbstractArrow arrow) {
        Location loc = arrow.getLocation();
        World world = arrow.getWorld();
        requireFlash(world, loc, Color.fromRGB(255, 92, 36));
        world.spawnParticle(Particle.LARGE_SMOKE, loc, 14, 0.2, 0.2, 0.2, 0.025);
        world.spawnParticle(Particle.CLOUD, loc, 8, 0.15, 0.15, 0.15, 0.04);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST_FAR, 0.8f, 0.58f);
        world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 0.5f);
    }

    private static void requireFlash(World world, Location location, Color color) {
        if (!ParticleEffectSupport.spawnFlash(world, location, color)) {
            throw new IllegalStateException("不支持的 FLASH 粒子数据类型："
                    + Particle.FLASH.getDataType().getName());
        }
    }

    private void runEffectSafely(String stage, Runnable effect) {
        if (disabledEffectStages.contains(stage)) {
            return;
        }
        try {
            effect.run();
        } catch (RuntimeException ex) {
            disabledEffectStages.add(stage);
            plugin.getLogger().log(Level.WARNING,
                    "特效阶段 " + stage + " 与当前服务端 API 不兼容，已禁用该阶段；制导主循环继续运行。", ex);
        }
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
        if (state.targetId() == null) {
            pdc.remove(targetKey);
        } else {
            pdc.set(targetKey, PersistentDataType.STRING, state.targetId().toString());
        }
        pdc.set(sessionKey, PersistentDataType.STRING, sessionId);
    }

    private void clearPersistentState(AbstractArrow arrow) {
        PersistentDataContainer pdc = arrow.getPersistentDataContainer();
        pdc.remove(projectileKey);
        pdc.remove(shooterKey);
        pdc.remove(ageKey);
        pdc.remove(targetKey);
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
        WORLD_DISABLED,
        MANUAL_LOCK_REQUIRED
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
