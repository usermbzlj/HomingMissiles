package cn.yjj.homingmissiles.config;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.net.URI;
import java.net.URISyntaxException;

public final class SettingsManager {
    public static final int CONFIG_VERSION = 7;
    public static final int HARD_MAX_TRACKED_PER_PLAYER = 4;

    private static final Map<String, Tunable> TUNABLES;
    private static final Map<String, Preset> PRESETS;

    static {
        Map<String, Tunable> tunables = new LinkedHashMap<>();
        register(tunables, new Tunable("range", "tracking.range", 80.0, 4.0, 256.0, "首次捕获范围（格）"));
        register(tunables, new Tunable("retention-range", "tracking.lock-retention-range", 192.0, 8.0, 512.0, "锁定保持范围（格）"));
        register(tunables, new Tunable("lifetime", "tracking.max-lifetime-ticks", 300.0, 20.0, 1200.0, "寿命（tick）"));
        register(tunables, new Tunable("delay", "tracking.activation-delay-ticks", 4.0, 0.0, 100.0, "启动延迟（tick）"));
        register(tunables, new Tunable("turn", "tracking.turn-rate-degrees-per-tick", 8.0, 0.1, 180.0, "每tick最大转角（度）"));
        register(tunables, new Tunable("acceleration", "tracking.acceleration-per-tick", 0.025, -0.2, 0.5, "巡航段每tick加速度"));
        register(tunables, new Tunable("min-speed", "tracking.min-speed", 1.2, 0.05, 10.0, "最低速度"));
        register(tunables, new Tunable("max-speed", "tracking.max-speed", 3.4, 0.05, 20.0, "巡航段最高速度"));
        register(tunables, new Tunable("terminal-delay", "tracking.terminal-boost.delay-ticks", 45.0, 0.0, 400.0, "后程助推延迟（tick）"));
        register(tunables, new Tunable("terminal-acceleration", "tracking.terminal-boost.acceleration-per-tick", 0.075, 0.0, 2.0, "后程每tick加速度"));
        register(tunables, new Tunable("terminal-max-speed", "tracking.terminal-boost.max-speed", 5.6, 0.05, 30.0, "后程最高速度"));
        register(tunables, new Tunable("lead", "tracking.lead-ticks", 4.0, 0.0, 40.0, "目标预判tick"));
        register(tunables, new Tunable("max-lead", "tracking.max-lead-ticks", 24.0, 0.0, 100.0, "最大截击预判tick"));
        register(tunables, new Tunable("switch-advantage", "tracking.switch-advantage-blocks", 3.0, 0.0, 64.0, "切换目标所需距离优势"));
        register(tunables, new Tunable("lock-time", "targeting.manual-lock.duration-ticks", 16.0, 1.0, 100.0, "手动锁定时长（tick）"));
        register(tunables, new Tunable("lock-cone", "targeting.manual-lock.cone-degrees", 10.0, 1.0, 45.0, "手动锁定视锥半角（度）"));
        TUNABLES = Collections.unmodifiableMap(tunables);

        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("balanced", new Preset("balanced", "均衡", Map.of(
                "turn", 8.0, "acceleration", 0.025, "min-speed", 1.2, "max-speed", 3.4,
                "terminal-acceleration", 0.075, "terminal-max-speed", 5.6, "lead", 4.0, "max-lead", 24.0)));
        presets.put("agile", new Preset("agile", "高机动", Map.of(
                "turn", 14.0, "acceleration", 0.04, "min-speed", 1.3, "max-speed", 3.8,
                "terminal-acceleration", 0.1, "terminal-max-speed", 6.2, "lead", 5.0, "max-lead", 28.0)));
        presets.put("realistic", new Preset("realistic", "大转弯半径", Map.of(
                "turn", 4.5, "acceleration", 0.018, "min-speed", 1.0, "max-speed", 3.0,
                "terminal-acceleration", 0.06, "terminal-max-speed", 5.0, "lead", 6.0, "max-lead", 30.0)));
        PRESETS = Collections.unmodifiableMap(presets);
    }

    private final HomingMissilesPlugin plugin;
    private volatile PluginSettings current;

    public SettingsManager(HomingMissilesPlugin plugin) {
        this.plugin = plugin;
    }

    public PluginSettings current() {
        return current;
    }

    public LoadReport reload() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        List<String> warnings = new ArrayList<>();

        int configVersion = c.getInt("config-version", CONFIG_VERSION);
        if (configVersion != CONFIG_VERSION) {
            warnings.add("config-version=" + configVersion + "，当前插件期望 " + CONFIG_VERSION
                    + "；缺失的新字段将使用安全默认值。保留原文件，不会覆盖你的配置。");
        }

        double range = boundedDouble(c, "tracking.range", 80.0, 1.0, 512.0, warnings);
        double retentionRange = boundedDouble(c, "tracking.lock-retention-range", 192.0, 1.0, 1024.0, warnings);
        if (retentionRange < range) {
            warnings.add("tracking.lock-retention-range 小于首次捕获范围，已在内存中提升为 " + range);
            retentionRange = range;
        }
        int lifetime = boundedInt(c, "tracking.max-lifetime-ticks", 300, 1, 72000, warnings);
        int delay = boundedInt(c, "tracking.activation-delay-ticks", 4, 0, 1200, warnings);
        double turn = boundedDouble(c, "tracking.turn-rate-degrees-per-tick", 8.0, 0.1, 180.0, warnings);
        double acceleration = boundedDouble(c, "tracking.acceleration-per-tick", 0.025, -1.0, 2.0, warnings);
        double minSpeed = boundedDouble(c, "tracking.min-speed", 1.2, 0.01, 20.0, warnings);
        double maxSpeed = boundedDouble(c, "tracking.max-speed", 3.4, 0.01, 50.0, warnings);
        if (maxSpeed < minSpeed) {
            warnings.add("tracking.max-speed 小于 min-speed，已在内存中提升为 " + minSpeed);
            maxSpeed = minSpeed;
        }
        int terminalDelay = boundedInt(c, "tracking.terminal-boost.delay-ticks", 45, 0, 1200, warnings);
        int terminalEscapeTrigger = boundedInt(c, "tracking.terminal-boost.escape-trigger-ticks", 6, 1, 100, warnings);
        double terminalAcceleration = boundedDouble(
                c, "tracking.terminal-boost.acceleration-per-tick", 0.075, 0.0, 3.0, warnings);
        double terminalMaxSpeed = boundedDouble(c, "tracking.terminal-boost.max-speed", 5.6, 0.01, 60.0, warnings);
        if (terminalMaxSpeed < maxSpeed) {
            warnings.add("tracking.terminal-boost.max-speed 小于巡航最高速度，已在内存中提升为 " + maxSpeed);
            terminalMaxSpeed = maxSpeed;
        }
        double leadTicks = boundedDouble(c, "tracking.lead-ticks", 4.0, 0.0, 100.0, warnings);
        double maxLeadTicks = boundedDouble(c, "tracking.max-lead-ticks", 24.0, 0.0, 200.0, warnings);
        if (maxLeadTicks < leadTicks) {
            warnings.add("tracking.max-lead-ticks 小于基础预判，已在内存中提升为 " + leadTicks);
            maxLeadTicks = leadTicks;
        }

        int manualLockDuration = boundedInt(
                c, "targeting.manual-lock.duration-ticks", 16, 1, 100, warnings);
        int manualLockGrace = boundedInt(
                c, "targeting.manual-lock.break-grace-ticks", 4, 0, 20, warnings);
        double manualLockCone = boundedDouble(
                c, "targeting.manual-lock.cone-degrees", 10.0, 1.0, 45.0, warnings);
        double manualLockBreakCone = boundedDouble(
                c, "targeting.manual-lock.break-cone-degrees", 16.0, 1.0, 60.0, warnings);
        if (manualLockBreakCone < manualLockCone) {
            warnings.add("targeting.manual-lock.break-cone-degrees 小于锁定视锥，已在内存中提升为 "
                    + manualLockCone);
            manualLockBreakCone = manualLockCone;
        }

        int globalLimit = boundedInt(c, "limits.max-tracked-arrows", 128, 1, 10000, warnings);
        int perPlayerLimit = boundedInt(c, "limits.max-tracked-per-player", 4, 1,
                Math.min(globalLimit, HARD_MAX_TRACKED_PER_PLAYER), warnings);
        int cooldown = boundedInt(c, "limits.launch-cooldown-ticks", 0, 0, 1200, warnings);
        int particleInterval = boundedInt(c, "visual.particle-interval-ticks", 1, 1, 200, warnings);
        int pageSize = boundedInt(c, "commands.help-page-size", 7, 4, 12, warnings);
        int maxGive = boundedInt(c, "commands.max-give-amount", 64, 1, 2304, warnings);
        int warningMin = boundedInt(c, "hud.warning-min-interval-ticks", 4, 1, 40, warnings);
        int warningMax = boundedInt(c, "hud.warning-max-interval-ticks", 24, 1, 100, warnings);
        if (warningMax < warningMin) {
            warnings.add("hud.warning-max-interval-ticks 小于 min，已在内存中提升为 " + warningMin);
            warningMax = warningMin;
        }
        int bowPowerLevel = boundedInt(c, "item.enchantments.power-level", 5, 0, 5, warnings);
        int clientModDetectionTicks = boundedInt(c, "hud.client-mod.detection-grace-ticks", 40, 10, 200, warnings);
        String clientModDownloadUrl = c.getString("hud.client-mod.download-url",
                "https://github.com/usermbzlj/HomingMissiles/releases/tag/v3.1.0").trim();
        if (!clientModDownloadUrl.isEmpty()
                && (!isHttpUrl(clientModDownloadUrl) || !isAscii(clientModDownloadUrl))) {
            warnings.add("hud.client-mod.download-url 必须是只含 ASCII 的 http/https URL，已忽略");
            clientModDownloadUrl = "";
        }

        String resourcePackUrl = c.getString("hud.resource-pack.url", "").trim();
        if (!resourcePackUrl.isEmpty() && (!isHttpUrl(resourcePackUrl) || !isAscii(resourcePackUrl))) {
            warnings.add("hud.resource-pack.url 必须是只含 ASCII 的 http/https URL，已禁用自动发送");
            resourcePackUrl = "";
        }
        String resourcePackSha1 = c.getString("hud.resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        if (!resourcePackSha1.isEmpty() && !resourcePackSha1.matches("[0-9a-f]{40}")) {
            warnings.add("hud.resource-pack.sha1 必须是40位十六进制 SHA-1，已忽略");
            resourcePackSha1 = "";
        }
        if (!resourcePackUrl.isEmpty() && resourcePackSha1.isEmpty()) {
            warnings.add("hud.resource-pack.url 已配置但 sha1 为空；建议填写构建产物的 SHA-1 以启用客户端缓存与完整性校验");
        }

        boolean selfHostEnabled = c.getBoolean("hud.resource-pack.self-host.enabled", false);
        String selfHostBind = c.getString("hud.resource-pack.self-host.bind-address", "0.0.0.0").trim();
        if (selfHostBind.isEmpty() || !isAscii(selfHostBind)) {
            warnings.add("hud.resource-pack.self-host.bind-address 无效，已禁用内置资源包服务");
            selfHostEnabled = false;
            selfHostBind = "0.0.0.0";
        }
        int selfHostPort = boundedInt(c, "hud.resource-pack.self-host.port", 25568, 1024, 65535, warnings);
        String selfHostPath = c.getString(
                "hud.resource-pack.self-host.path", "/homingmissiles/hud-1.21.11.zip").trim();
        if (!isSafeHttpPath(selfHostPath)) {
            warnings.add("hud.resource-pack.self-host.path 必须是简单的绝对 URL 路径，已禁用内置资源包服务");
            selfHostEnabled = false;
            selfHostPath = "/homingmissiles/hud-1.21.11.zip";
        }
        if (selfHostEnabled) {
            String reason = validateSelfHostedUrl(resourcePackUrl, resourcePackSha1, selfHostPort, selfHostPath);
            if (reason != null) {
                warnings.add(reason + "，已禁用内置资源包服务");
                selfHostEnabled = false;
            }
        }

        Set<String> disabledWorlds = new LinkedHashSet<>();
        for (String world : c.getStringList("worlds.disabled")) {
            if (world != null && !world.isBlank()) {
                disabledWorlds.add(world.toLowerCase(Locale.ROOT));
            }
        }

        Map<String, String> messages = loadMessages(c);

        current = new PluginSettings(
                configVersion,
                range,
                retentionRange,
                lifetime,
                delay,
                turn,
                acceleration,
                minSpeed,
                maxSpeed,
                terminalDelay,
                terminalEscapeTrigger,
                terminalAcceleration,
                terminalMaxSpeed,
                leadTicks,
                maxLeadTicks,
                c.getBoolean("tracking.dynamic-retargeting", true),
                boundedDouble(c, "tracking.switch-advantage-blocks", 3.0, 0.0, 256.0, warnings),
                c.getBoolean("tracking.require-line-of-sight", false),
                c.getBoolean("tracking.target-creative", false),
                c.getBoolean("tracking.target-spectator", false),
                c.getBoolean("tracking.respect-vanish", true),
                c.getBoolean("tracking.no-gravity", true),
                manualLockDuration,
                manualLockGrace,
                manualLockCone,
                manualLockBreakCone,
                globalLimit,
                perPlayerLimit,
                cooldown,
                c.getBoolean("limits.cancel-rejected-shot", true),
                boundedDouble(c, "combat.minimum-arrow-damage", 12.0, -1.0, 100.0, warnings),
                c.getBoolean("visual.glowing-arrow", false),
                c.getBoolean("visual.particles", true),
                particleInterval,
                c.getBoolean("audio.launch-sound", true),
                c.getBoolean("audio.lock-sounds", true),
                c.getBoolean("effects.launch", true),
                c.getBoolean("effects.impact", true),
                c.getBoolean("effects.self-destruct", true),
                c.getBoolean("lifecycle.remove-arrows-on-disable", true),
                c.getBoolean("lifecycle.recover-arrows-on-enable", true),
                Collections.unmodifiableSet(disabledWorlds),
                feedback(c, "feedback.launch", PluginSettings.FeedbackMode.ACTIONBAR, warnings),
                feedback(c, "feedback.lock-shooter", PluginSettings.FeedbackMode.ACTIONBAR, warnings),
                feedback(c, "feedback.lock-target", PluginSettings.FeedbackMode.ACTIONBAR, warnings),
                feedback(c, "feedback.rejection", PluginSettings.FeedbackMode.ACTIONBAR, warnings),
                c.getBoolean("hud.enabled", true),
                c.getBoolean("hud.pixel-overlay", true),
                clientModDownloadUrl,
                clientModDetectionTicks,
                c.getBoolean("hud.shooter-bossbar", true),
                c.getBoolean("hud.target-bossbar", true),
                resourcePackUrl,
                resourcePackSha1,
                c.getBoolean("hud.resource-pack.required", false),
                color(c.getString("hud.resource-pack.prompt", "&b启用动态飞行导弹头显与座舱音效")),
                c.getBoolean("hud.resource-pack.assume-server-pack-provides-hud", false),
                selfHostEnabled,
                selfHostBind,
                selfHostPort,
                selfHostPath,
                c.getBoolean("hud.warning-audio", true),
                warningMin,
                warningMax,
                color(c.getString("item.name", "&b&l制导弓")),
                colorList(c.getStringList("item.lore")),
                c.getBoolean("item.enchantments.flame", true),
                c.getBoolean("item.enchantments.infinity", true),
                c.getBoolean("item.enchantments.unbreakable", true),
                bowPowerLevel,
                Collections.unmodifiableMap(messages),
                pageSize,
                maxGive
        );
        return new LoadReport(current, List.copyOf(warnings));
    }

    private static boolean isSafeHttpPath(String value) {
        return value != null
                && value.length() >= 2
                && value.length() <= 160
                && value.charAt(0) == '/'
                && !value.endsWith("/")
                && !value.contains("//")
                && !value.contains("..")
                && value.matches("/[A-Za-z0-9._/-]+");
    }

    private static String validateSelfHostedUrl(String url, String sha1, int port, String path) {
        if (url == null || url.isBlank()) {
            return "启用 self-host 时 hud.resource-pack.url 不能为空";
        }
        if (sha1 == null || !sha1.matches("[0-9a-f]{40}")) {
            return "启用 self-host 时 hud.resource-pack.sha1 必须有效";
        }
        try {
            URI uri = new URI(url);
            int effectivePort = uri.getPort() >= 0 ? uri.getPort() : 80;
            if (!"http".equalsIgnoreCase(uri.getScheme())) {
                return "内置资源包服务只提供 HTTP，url 必须使用 http://";
            }
            if (uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return "内置资源包 url 必须包含主机且不能带查询或片段";
            }
            if (effectivePort != port || !path.equals(uri.getRawPath())) {
                return "内置资源包 url 的端口和路径必须与 self-host 配置一致";
            }
        } catch (URISyntaxException ex) {
            return "内置资源包 url 语法无效";
        }
        return null;
    }

    public TuneResult setTunable(String key, double value) {
        Tunable tunable = TUNABLES.get(normalizeKey(key));
        if (tunable == null) {
            return TuneResult.failure("未知参数：" + key);
        }
        if (!Double.isFinite(value)) {
            return TuneResult.failure("数值必须是有限数字。");
        }
        if (value < tunable.min() || value > tunable.max()) {
            return TuneResult.failure(tunable.displayName() + "允许范围为 "
                    + compact(tunable.min()) + "～" + compact(tunable.max()) + "。");
        }
        Object stored = isIntegerPath(tunable.path()) ? (int) Math.round(value) : value;
        plugin.getConfig().set(tunable.path(), stored);
        plugin.saveConfig();
        LoadReport report = reload();
        return TuneResult.success(tunable, value, report.warnings());
    }

    public TuneResult resetTunable(String key) {
        Tunable tunable = TUNABLES.get(normalizeKey(key));
        if (tunable == null) {
            return TuneResult.failure("未知参数：" + key);
        }
        Object stored = isIntegerPath(tunable.path()) ? (int) tunable.defaultValue() : tunable.defaultValue();
        plugin.getConfig().set(tunable.path(), stored);
        plugin.saveConfig();
        LoadReport report = reload();
        return TuneResult.success(tunable, tunable.defaultValue(), report.warnings());
    }

    public LoadReport resetAllTunables() {
        for (Tunable tunable : TUNABLES.values()) {
            Object stored = isIntegerPath(tunable.path()) ? (int) tunable.defaultValue() : tunable.defaultValue();
            plugin.getConfig().set(tunable.path(), stored);
        }
        plugin.saveConfig();
        return reload();
    }

    public PresetResult applyPreset(String name) {
        Preset preset = PRESETS.get(normalizeKey(name));
        if (preset == null) {
            return PresetResult.failure("未知预设：" + name);
        }
        for (Map.Entry<String, Double> entry : preset.values().entrySet()) {
            Tunable tunable = TUNABLES.get(entry.getKey());
            plugin.getConfig().set(tunable.path(), entry.getValue());
        }
        plugin.saveConfig();
        LoadReport report = reload();
        return PresetResult.success(preset, report.warnings());
    }

    public Map<String, Tunable> tunables() {
        return TUNABLES;
    }

    public Map<String, Preset> presets() {
        return PRESETS;
    }

    public double currentTunableValue(Tunable tunable) {
        PluginSettings s = current;
        return switch (tunable.key()) {
            case "range" -> s.trackingRange();
            case "retention-range" -> s.lockRetentionRange();
            case "lifetime" -> s.maxLifetimeTicks();
            case "delay" -> s.activationDelayTicks();
            case "turn" -> s.turnRateDegreesPerTick();
            case "acceleration" -> s.accelerationPerTick();
            case "min-speed" -> s.minSpeed();
            case "max-speed" -> s.maxSpeed();
            case "terminal-delay" -> s.terminalBoostDelayTicks();
            case "terminal-acceleration" -> s.terminalAccelerationPerTick();
            case "terminal-max-speed" -> s.terminalMaxSpeed();
            case "lead" -> s.leadTicks();
            case "max-lead" -> s.maxLeadTicks();
            case "switch-advantage" -> s.switchAdvantageBlocks();
            default -> throw new IllegalArgumentException("未映射的参数：" + tunable.key());
        };
    }

    private static void register(Map<String, Tunable> map, Tunable tunable) {
        map.put(tunable.key(), tunable);
    }

    private static String normalizeKey(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static boolean isHttpUrl(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private static boolean isAscii(String value) {
        return value.chars().allMatch(character -> character >= 0x20 && character <= 0x7e);
    }

    private static boolean isIntegerPath(String path) {
        return path.endsWith("ticks");
    }

    private static double boundedDouble(FileConfiguration c, String path, double def,
                                        double min, double max, List<String> warnings) {
        double value = c.getDouble(path, def);
        if (!Double.isFinite(value)) {
            warnings.add(path + " 不是有限数字，使用默认值 " + def);
            return def;
        }
        if (value < min || value > max) {
            double corrected = Math.max(min, Math.min(max, value));
            warnings.add(path + "=" + value + " 超出安全范围，内存中修正为 " + corrected);
            return corrected;
        }
        return value;
    }

    private static int boundedInt(FileConfiguration c, String path, int def,
                                  int min, int max, List<String> warnings) {
        int value = c.getInt(path, def);
        if (value < min || value > max) {
            int corrected = Math.max(min, Math.min(max, value));
            warnings.add(path + "=" + value + " 超出安全范围，内存中修正为 " + corrected);
            return corrected;
        }
        return value;
    }

    private static PluginSettings.FeedbackMode feedback(FileConfiguration c, String path,
                                                         PluginSettings.FeedbackMode fallback,
                                                         List<String> warnings) {
        String raw = c.getString(path, fallback.name().toLowerCase(Locale.ROOT));
        PluginSettings.FeedbackMode parsed = PluginSettings.FeedbackMode.parse(raw, fallback);
        boolean legacyBoolean = raw != null
                && (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false"));
        if (raw != null && !legacyBoolean && !raw.equalsIgnoreCase(parsed.name())) {
            warnings.add(path + "=" + raw + " 无效，使用 " + parsed.name().toLowerCase(Locale.ROOT));
        }
        return parsed;
    }

    private static Map<String, String> loadMessages(FileConfiguration c) {
        Map<String, String> result = new LinkedHashMap<>();
        defaults().forEach((key, value) -> result.put(key, color(c.getString("messages." + key, value))));
        return result;
    }

    private static Map<String, String> defaults() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "&8[&b制导箭&8] &r");
        m.put("no-permission", "&c你没有权限执行此操作。&7（{permission}）");
        m.put("player-only", "&c该命令只能由游戏内玩家执行。");
        m.put("player-not-found", "&c找不到在线玩家：&f{player}");
        m.put("world-not-found", "&c找不到世界：&f{world}");
        m.put("invalid-number", "&c{input} 不是有效数字。");
        m.put("invalid-integer", "&c{input} 不是有效整数。");
        m.put("invalid-amount", "&c数量必须是 {min}～{max} 的整数。");
        m.put("unknown-command", "&c未知子命令：&f{input}&c。{suggestion}");
        m.put("internal-error", "&c命令执行失败。错误编号：&f{reference}&c，请查看服务端日志。");
        m.put("usage", "&e正确用法：&f{usage}");
        m.put("launch", "&a制导箭已发射 &8· &7在途 &f{active}/{limit}");
        m.put("guidance-link", "&b制导链路已建立。&7目标遥测已由系统保密。");
        m.put("inbound-warning", "&4警告：&c侦测到来袭制导弹药。");
        m.put("rejected-permission", "&c你没有使用制导弓的权限。");
        m.put("rejected-global-limit", "&e全服在途制导箭已达到上限 {limit}。");
        m.put("rejected-player-limit", "&e你已有 {active}/{limit} 支制导箭在途。");
        m.put("rejected-cooldown", "&e制导弓冷却中，还需 {ticks} tick。");
        m.put("rejected-world", "&e当前世界已禁用制导弓。");
        m.put("rejected-manual-lock", "&c未完成手动锁定。&7拉住弓并把目标保持在锁定框内。");
        m.put("give-sender", "&a已给予 &f{player} &a制导弓 ×{amount}。{dropped}");
        m.put("give-target", "&a你获得了制导弓 ×{amount}。");
        m.put("clear", "&a已清除 &f{count} &a支在途制导箭。");
        m.put("reload", "&a配置已原子重载。&7警告：&f{warnings}&7，当前在途箭保留。");
        m.put("preset", "&a已应用预设：&f{name}&7（{key}）");
        m.put("tune", "&a已设置 &f{key}&a = &f{value}&7（{description}）");
        m.put("tune-reset", "&a已重置 &f{key}&a = &f{value}");
        return m;
    }

    public static String color(String text) {
        return text == null ? "" : text.replace('&', '§');
    }

    private static List<String> colorList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String line : list) {
            result.add(color(line));
        }
        return List.copyOf(result);
    }

    public static String compact(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    public record Tunable(String key, String path, double defaultValue,
                          double min, double max, String displayName) {
    }

    public record Preset(String key, String displayName, Map<String, Double> values) {
    }

    public record LoadReport(PluginSettings settings, List<String> warnings) {
    }

    public record TuneResult(boolean success, String error, Tunable tunable,
                             double value, List<String> warnings) {
        static TuneResult success(Tunable tunable, double value, List<String> warnings) {
            return new TuneResult(true, null, tunable, value, warnings);
        }

        static TuneResult failure(String error) {
            return new TuneResult(false, error, null, 0.0, List.of());
        }
    }

    public record PresetResult(boolean success, String error, Preset preset, List<String> warnings) {
        static PresetResult success(Preset preset, List<String> warnings) {
            return new PresetResult(true, null, preset, warnings);
        }

        static PresetResult failure(String error) {
            return new PresetResult(false, error, null, List.of());
        }
    }
}
