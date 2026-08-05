package cn.yjj.homingmissiles.command;

import cn.yjj.homingmissiles.HomingMissilesPlugin;
import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import cn.yjj.homingmissiles.service.HomingService;
import cn.yjj.homingmissiles.util.CommandUtil;
import cn.yjj.homingmissiles.util.MessageService;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HomingBowCommand implements CommandExecutor, TabCompleter {
    private final HomingMissilesPlugin plugin;
    private final SettingsManager settingsManager;
    private final MessageService messages;
    private final HomingBowFactory bowFactory;
    private final HomingService homingService;
    private final Map<String, HelpEntry> helpEntries = new LinkedHashMap<>();

    public HomingBowCommand(HomingMissilesPlugin plugin, SettingsManager settingsManager,
                            MessageService messages, HomingBowFactory bowFactory,
                            HomingService homingService) {
        this.plugin = plugin;
        this.settingsManager = settingsManager;
        this.messages = messages;
        this.bowFactory = bowFactory;
        this.homingService = homingService;
        registerHelp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        try {
            return dispatch(sender, label, args);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "命令执行异常，编号=" + reference + "，发送者=" + sender + "，参数=" + String.join(" ", args), ex);
            messages.send(sender, "internal-error", "reference", reference);
            return true;
        }
    }

    private boolean dispatch(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            sendDashboard(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help", "?" -> handleHelp(sender, label, args);
            case "get" -> handleGet(sender, label, args);
            case "give" -> handleGive(sender, label, args);
            case "status" -> handleStatus(sender, label, args);
            case "inspect" -> handleInspect(sender, label, args);
            case "clear", "cancel" -> handleClear(sender, label, args);
            case "preset" -> handlePreset(sender, label, args);
            case "tune" -> handleTune(sender, label, args);
            case "reload" -> handleReload(sender, label, args);
            case "version", "about" -> handleVersion(sender);
            default -> {
                String closest = CommandUtil.closest(sub, visibleCommandNames(sender));
                messages.send(sender, "unknown-command",
                        "input", sub,
                        "suggestion", closest == null ? "输入 §f/" + label + " help §c查看帮助。"
                                : "你是否想输入 §f/" + label + " " + closest + "§c？");
                yield true;
            }
        };
    }

    private boolean handleHelp(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.help")) {
            return true;
        }
        if (args.length >= 2 && !isInteger(args[1])) {
            HelpEntry entry = helpEntries.get(args[1].toLowerCase(Locale.ROOT));
            if (entry == null || !canSee(sender, entry)) {
                messages.send(sender, "unknown-command",
                        "input", args[1],
                        "suggestion", "输入 §f/" + label + " help §c查看可用命令。");
                return true;
            }
            sendDetailedHelp(sender, label, entry);
            return true;
        }

        int requestedPage = 1;
        if (args.length >= 2) {
            try {
                requestedPage = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                messages.send(sender, "invalid-integer", "input", args[1]);
                return true;
            }
        }

        List<HelpEntry> visible = helpEntries.values().stream().filter(entry -> canSee(sender, entry)).toList();
        int pageSize = settingsManager.current().helpPageSize();
        int pages = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
        int page = Math.max(1, Math.min(pages, requestedPage));
        int from = (page - 1) * pageSize;
        int to = Math.min(visible.size(), from + pageSize);

        messages.sendRaw(sender, "&8&m                                                    ");
        messages.sendRaw(sender, "&b&lHomingMissiles &7命令帮助 &8· &f" + page + "/" + pages);
        for (HelpEntry entry : visible.subList(from, to)) {
            messages.sendRaw(sender, "&f/" + label + " " + entry.syntax() + " &8- &7" + entry.summary());
        }
        messages.sendRaw(sender, "&7查看详细说明：&f/" + label + " help <子命令>");
        if (pages > 1) {
            messages.sendRaw(sender, "&7翻页：&f/" + label + " help " + (page == pages ? 1 : page + 1));
        }
        messages.sendRaw(sender, "&8&m                                                    ");
        return true;
    }

    private boolean handleGet(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.get")) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length > 2) {
            usage(sender, label, "get [数量]");
            return true;
        }
        Integer amount = parseAmount(sender, args.length == 2 ? args[1] : "1");
        if (amount == null) {
            return true;
        }
        GiveResult result = give(player, amount);
        messages.send(sender, "give-sender",
                "player", player.getName(), "amount", amount,
                "dropped", result.dropped() == 0 ? "" : " §e（背包已满，掉落 " + result.dropped() + " 把）");
        return true;
    }

    private boolean handleGive(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.give")) {
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            usage(sender, label, "give <玩家> [数量]");
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", "player", args[1]);
            return true;
        }
        Integer amount = parseAmount(sender, args.length == 3 ? args[2] : "1");
        if (amount == null) {
            return true;
        }

        GiveResult result = give(target, amount);
        messages.send(sender, "give-sender",
                "player", target.getName(), "amount", amount,
                "dropped", result.dropped() == 0 ? "" : " §e（背包已满，掉落 " + result.dropped() + " 把）");
        if (!sender.equals(target)) {
            messages.send(target, "give-target", "amount", amount);
        }
        return true;
    }

    private boolean handleStatus(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.status")) {
            return true;
        }
        boolean verbose = args.length >= 2 && args[1].equalsIgnoreCase("verbose");
        if (args.length > 2 || (args.length == 2 && !verbose)) {
            usage(sender, label, "status [verbose]");
            return true;
        }
        if (verbose && !require(sender, "homingmissiles.command.status.verbose")) {
            return true;
        }

        PluginSettings s = settingsManager.current();
        HomingService.StatusSnapshot status = homingService.status();
        messages.sendRaw(sender, "&8&m                                                    ");
        messages.sendRaw(sender, "&b&lHomingMissiles &f运行状态 &8· &7v" + HomingMissilesPlugin.VERSION);
        messages.sendRaw(sender, "&7在途制导箭：&f" + status.active() + "&7/&f" + status.limit()
                + " &8· &7本tick处理：&f" + status.processedLastTick());
        if (sender instanceof Player player) {
            messages.sendRaw(sender, "&7你的在途箭：&f" + homingService.activeCount(player.getUniqueId())
                    + "&7/&f" + s.maxTrackedPerPlayer());
        }
        messages.sendRaw(sender, "&7索敌：&f" + SettingsManager.compact(s.trackingRange()) + "格"
                + " &8· &7转角：&f" + SettingsManager.compact(s.turnRateDegreesPerTick()) + "°/tick"
                + " &8· &7速度：&f" + SettingsManager.compact(s.minSpeed()) + "～" + SettingsManager.compact(s.maxSpeed()));

        if (verbose) {
            messages.sendRaw(sender, "&7调度耗时：&f平均 " + String.format(Locale.ROOT, "%.3f", status.averageTickMillis())
                    + "ms &8· &f峰值 " + String.format(Locale.ROOT, "%.3f", status.peakTickMillis()) + "ms");
            messages.sendRaw(sender, "&7隔离异常：&f" + status.arrowFailures()
                    + " &8· &7服务tick：&f" + status.serviceTick());
            List<HomingService.ShooterCount> top = homingService.topShooters(5);
            if (!top.isEmpty()) {
                messages.sendRaw(sender, "&7在途箭最多的射手：");
                for (HomingService.ShooterCount count : top) {
                    messages.sendRaw(sender, "  &8- &f" + count.name() + " &7×" + count.count());
                }
            }
            messages.sendRaw(sender, "&7禁用世界：&f" + (s.disabledWorlds().isEmpty() ? "无" : String.join(", ", s.disabledWorlds())));
        }
        messages.sendRaw(sender, "&8&m                                                    ");
        return true;
    }

    private boolean handleInspect(CommandSender sender, String label, String[] args) {
        if (args.length > 2) {
            usage(sender, label, "inspect [玩家]");
            return true;
        }

        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                usage(sender, label, "inspect <玩家>");
                return true;
            }
            if (!require(sender, "homingmissiles.command.inspect.self")) {
                return true;
            }
            target = player;
        } else {
            target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                messages.send(sender, "player-not-found", "player", args[1]);
                return true;
            }
            boolean self = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
            if (!self && !require(sender, "homingmissiles.command.inspect.others")) {
                return true;
            }
            if (self && !require(sender, "homingmissiles.command.inspect.self")) {
                return true;
            }
        }

        HomingService.Inspection inspection = homingService.inspect(target);
        boolean holding = bowFactory.isHomingBow(target.getInventory().getItemInMainHand());
        messages.sendRaw(sender, "&8&m                                                    ");
        messages.sendRaw(sender, "&b&l制导状态检查 &8· &f" + target.getName());
        messages.sendRaw(sender, "&7主手物品：" + (holding ? "&a有效制导弓" : "&8不是制导弓"));
        messages.sendRaw(sender, "&7在途箭数：&f" + inspection.arrows().size());
        int shown = 0;
        for (HomingService.ArrowInfo arrow : inspection.arrows()) {
            if (shown++ >= 8) {
                messages.sendRaw(sender, "&8……其余 " + (inspection.arrows().size() - 8) + " 支省略");
                break;
            }
            messages.sendRaw(sender, "  &8- &7目标 &f" + arrow.targetName()
                    + " &8· &7年龄 &f" + arrow.ageTicks() + "t"
                    + " &8· &7速度 &f" + String.format(Locale.ROOT, "%.2f", arrow.speed())
                    + " &8· &7世界 &f" + arrow.worldName());
        }
        messages.sendRaw(sender, "&8&m                                                    ");
        return true;
    }

    private boolean handleClear(CommandSender sender, String label, String[] args) {
        String scope = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "mine";
        int count;
        switch (scope) {
            case "mine", "self" -> {
                if (!(sender instanceof Player player)) {
                    usage(sender, label, "clear <all|player|world> [名称]");
                    return true;
                }
                if (!require(sender, "homingmissiles.command.clear.own")) {
                    return true;
                }
                if (args.length > 2) {
                    usage(sender, label, "clear mine");
                    return true;
                }
                count = homingService.clearMine(player.getUniqueId());
            }
            case "all" -> {
                if (!require(sender, "homingmissiles.command.clear.admin")) {
                    return true;
                }
                if (args.length > 2) {
                    usage(sender, label, "clear all");
                    return true;
                }
                count = homingService.clearAll();
            }
            case "player" -> {
                if (!require(sender, "homingmissiles.command.clear.admin")) {
                    return true;
                }
                if (args.length != 3) {
                    usage(sender, label, "clear player <玩家>");
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) {
                    messages.send(sender, "player-not-found", "player", args[2]);
                    return true;
                }
                count = homingService.clearPlayer(target.getUniqueId());
            }
            case "world" -> {
                if (!require(sender, "homingmissiles.command.clear.admin")) {
                    return true;
                }
                if (args.length != 3) {
                    usage(sender, label, "clear world <世界>");
                    return true;
                }
                World world = plugin.getServer().getWorld(args[2]);
                if (world == null) {
                    messages.send(sender, "world-not-found", "world", args[2]);
                    return true;
                }
                count = homingService.clearWorld(world.getName());
            }
            default -> {
                usage(sender, label, "clear <mine|all|player|world> [名称]");
                return true;
            }
        }
        messages.send(sender, "clear", "count", count);
        return true;
    }

    private boolean handlePreset(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.preset")) {
            return true;
        }
        if (args.length != 2) {
            usage(sender, label, "preset <balanced|agile|realistic>");
            return true;
        }
        SettingsManager.PresetResult result = settingsManager.applyPreset(args[1]);
        if (!result.success()) {
            messages.sendRaw(sender, "&c" + result.error());
            usage(sender, label, "preset <balanced|agile|realistic>");
            return true;
        }
        messages.send(sender, "preset",
                "name", result.preset().displayName(), "key", result.preset().key());
        sendWarnings(sender, result.warnings());
        return true;
    }

    private boolean handleTune(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.tune")) {
            return true;
        }
        if (args.length == 1 || args[1].equalsIgnoreCase("show")) {
            sendTuneTable(sender, label);
            return true;
        }
        if (args[1].equalsIgnoreCase("reset")) {
            if (args.length == 2 || args[2].equalsIgnoreCase("all")) {
                SettingsManager.LoadReport report = settingsManager.resetAllTunables();
                messages.sendRaw(sender, "&a已重置所有在线可调参数为默认值。");
                sendWarnings(sender, report.warnings());
                return true;
            }
            if (args.length != 3) {
                usage(sender, label, "tune reset <参数|all>");
                return true;
            }
            SettingsManager.TuneResult result = settingsManager.resetTunable(args[2]);
            if (!result.success()) {
                messages.sendRaw(sender, "&c" + result.error());
                return true;
            }
            messages.send(sender, "tune-reset",
                    "key", result.tunable().key(), "value", SettingsManager.compact(result.value()));
            sendWarnings(sender, result.warnings());
            return true;
        }
        if (args.length != 3) {
            usage(sender, label, "tune <参数> <数值>");
            return true;
        }

        double value;
        try {
            value = Double.parseDouble(args[2]);
        } catch (NumberFormatException ex) {
            messages.send(sender, "invalid-number", "input", args[2]);
            return true;
        }
        SettingsManager.TuneResult result = settingsManager.setTunable(args[1], value);
        if (!result.success()) {
            messages.sendRaw(sender, "&c" + result.error());
            return true;
        }
        messages.send(sender, "tune",
                "key", result.tunable().key(),
                "value", SettingsManager.compact(settingsManager.currentTunableValue(result.tunable())),
                "description", result.tunable().displayName());
        sendWarnings(sender, result.warnings());
        return true;
    }

    private boolean handleReload(CommandSender sender, String label, String[] args) {
        if (!require(sender, "homingmissiles.command.reload")) {
            return true;
        }
        if (args.length != 1) {
            usage(sender, label, "reload");
            return true;
        }
        SettingsManager.LoadReport report = settingsManager.reload();
        messages.send(sender, "reload", "warnings", report.warnings().size());
        sendWarnings(sender, report.warnings());
        return true;
    }

    private boolean handleVersion(CommandSender sender) {
        if (!require(sender, "homingmissiles.command.version")) {
            return true;
        }
        messages.sendRaw(sender, "&b&lHomingMissiles &fv" + HomingMissilesPlugin.VERSION
                + " &8· &7Paper 1.21.x / Java 21");
        messages.sendRaw(sender, "&7连续Motion制导、多射手多箭、动态重选、热恢复、配置校验和完整命令交互。");
        return true;
    }

    private void sendDashboard(CommandSender sender, String label) {
        if (sender instanceof Player player) {
            boolean holding = bowFactory.isHomingBow(player.getInventory().getItemInMainHand());
            messages.sendRaw(sender, "&8&m                                                    ");
            messages.sendRaw(sender, "&b&lHomingMissiles &7玩家面板");
            messages.sendRaw(sender, "&7主手：" + (holding ? "&a制导弓已就绪" : "&8未持有制导弓")
                    + " &8· &7你的在途箭：&f" + homingService.activeCount(player.getUniqueId()));
            messages.sendRaw(sender, "&7常用：&f/" + label + " help &8· &f/" + label + " status &8· &f/" + label + " clear mine");
            messages.sendRaw(sender, "&8&m                                                    ");
        } else {
            handleHelp(sender, label, new String[]{"help"});
        }
    }

    private void sendTuneTable(CommandSender sender, String label) {
        messages.sendRaw(sender, "&8&m                                                    ");
        messages.sendRaw(sender, "&b&l在线制导调参 &8· &7修改后立即生效并写入 config.yml");
        for (SettingsManager.Tunable tunable : settingsManager.tunables().values()) {
            messages.sendRaw(sender, "&f" + tunable.key() + "&7 = &b"
                    + SettingsManager.compact(settingsManager.currentTunableValue(tunable))
                    + " &8[" + SettingsManager.compact(tunable.min()) + "～" + SettingsManager.compact(tunable.max()) + "]"
                    + " &8- &7" + tunable.displayName());
        }
        messages.sendRaw(sender, "&7修改：&f/" + label + " tune <参数> <数值>");
        messages.sendRaw(sender, "&7重置：&f/" + label + " tune reset <参数|all>");
        messages.sendRaw(sender, "&8&m                                                    ");
    }

    private void sendDetailedHelp(CommandSender sender, String label, HelpEntry entry) {
        messages.sendRaw(sender, "&8&m                                                    ");
        messages.sendRaw(sender, "&b&l/" + label + " " + entry.name());
        messages.sendRaw(sender, "&7用途：&f" + entry.summary());
        messages.sendRaw(sender, "&7用法：&f/" + label + " " + entry.syntax());
        for (String line : entry.details()) {
            messages.sendRaw(sender, "&8- &7" + line);
        }
        messages.sendRaw(sender, "&7权限：&f" + entry.permission());
        messages.sendRaw(sender, "&8&m                                                    ");
    }

    private GiveResult give(Player target, int amount) {
        int dropped = 0;
        for (int i = 0; i < amount; i++) {
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(bowFactory.create());
            for (ItemStack leftover : leftovers.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), leftover);
                dropped++;
            }
        }
        return new GiveResult(amount, dropped);
    }

    private Integer parseAmount(CommandSender sender, String raw) {
        int amount;
        try {
            amount = Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            messages.send(sender, "invalid-integer", "input", raw);
            return null;
        }
        int max = settingsManager.current().maxGiveAmount();
        if (amount < 1 || amount > max) {
            messages.send(sender, "invalid-amount", "min", 1, "max", max);
            return null;
        }
        return amount;
    }

    private void usage(CommandSender sender, String label, String syntax) {
        messages.send(sender, "usage", "usage", "/" + label + " " + syntax);
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        messages.noPermission(sender, permission);
        return false;
    }

    private boolean canSee(CommandSender sender, HelpEntry entry) {
        return sender.hasPermission(entry.permission());
    }

    private void sendWarnings(CommandSender sender, List<String> warnings) {
        for (String warning : warnings) {
            messages.sendRaw(sender, "&e配置警告：&7" + warning);
        }
    }

    private Collection<String> visibleCommandNames(CommandSender sender) {
        return helpEntries.values().stream()
                .filter(entry -> canSee(sender, entry))
                .map(HelpEntry::name)
                .toList();
    }

    private void registerHelp() {
        addHelp("help", "homingmissiles.command.help", "help [页码|子命令]", "查看分页帮助或某条命令的详细说明",
                "只显示你有权限使用的命令。", "支持 /hbow help tune 这种定向帮助。");
        addHelp("get", "homingmissiles.command.get", "get [数量]", "给自己领取制导弓",
                "背包满时物品会安全掉落在脚下。", "数量上限由 commands.max-give-amount 控制。");
        addHelp("give", "homingmissiles.command.give", "give <玩家> [数量]", "向在线玩家发放制导弓",
                "兼容旧版 /hbow give 命令。", "支持Tab补全在线玩家和常用数量。");
        addHelp("status", "homingmissiles.command.status", "status [verbose]", "查看制导系统运行状态",
                "verbose 会显示调度耗时、隔离异常和活跃射手。", "详细模式需要额外权限。");
        addHelp("inspect", "homingmissiles.command.inspect.self", "inspect [玩家]", "检查持弓状态和在途箭目标",
                "默认检查自己。", "检查他人需要 homingmissiles.command.inspect.others。");
        addHelp("clear", "homingmissiles.command.clear.own", "clear <mine|all|player|world> [名称]", "清理在途制导箭",
                "mine 仅删除自己的箭。", "all/player/world 需要管理员权限。", "cancel 是 clear 的别名。");
        addHelp("preset", "homingmissiles.command.preset", "preset <balanced|agile|realistic>", "切换一组经过校验的制导参数",
                "balanced：均衡。", "agile：高机动。", "realistic：转弯半径更大。");
        addHelp("tune", "homingmissiles.command.tune", "tune [show|reset|参数 数值]", "在游戏中安全地查询和修改参数",
                "所有参数都有白名单和上下界校验。", "修改会写入 config.yml 并立即生效。");
        addHelp("reload", "homingmissiles.command.reload", "reload", "原子重载并校验配置",
                "非法值会使用安全修正值并显示警告。", "当前在途箭不会被清空。");
        addHelp("version", "homingmissiles.command.version", "version", "查看插件版本与运行平台",
                "about 是 version 的别名。");
    }

    private void addHelp(String name, String permission, String syntax, String summary, String... details) {
        helpEntries.put(name, new HelpEntry(name, permission, syntax, summary, List.of(details)));
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        try {
            if (args.length == 1) {
                return CommandUtil.filterPrefix(visibleCommandNames(sender), args[0]);
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("help") || sub.equals("?")) {
                if (args.length == 2) {
                    List<String> values = new ArrayList<>(visibleCommandNames(sender));
                    values.add("1");
                    values.add("2");
                    return CommandUtil.filterPrefix(values, args[1]);
                }
            }
            if (sub.equals("get") && args.length == 2) {
                return CommandUtil.filterPrefix(List.of("1", "2", "4", "8", "16"), args[1]);
            }
            if (sub.equals("give")) {
                if (args.length == 2) {
                    return onlinePlayerNames(args[1]);
                }
                if (args.length == 3) {
                    return CommandUtil.filterPrefix(List.of("1", "2", "4", "8", "16", "32", "64"), args[2]);
                }
            }
            if (sub.equals("status") && args.length == 2
                    && sender.hasPermission("homingmissiles.command.status.verbose")) {
                return CommandUtil.filterPrefix(List.of("verbose"), args[1]);
            }
            if (sub.equals("inspect") && args.length == 2
                    && sender.hasPermission("homingmissiles.command.inspect.others")) {
                return onlinePlayerNames(args[1]);
            }
            if (sub.equals("clear") || sub.equals("cancel")) {
                if (args.length == 2) {
                    List<String> scopes = new ArrayList<>();
                    if (sender.hasPermission("homingmissiles.command.clear.own")) {
                        scopes.add("mine");
                    }
                    if (sender.hasPermission("homingmissiles.command.clear.admin")) {
                        scopes.addAll(List.of("all", "player", "world"));
                    }
                    return CommandUtil.filterPrefix(scopes, args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("player")) {
                    return onlinePlayerNames(args[2]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("world")) {
                    List<String> worlds = plugin.getServer().getWorlds().stream().map(World::getName).sorted().toList();
                    return CommandUtil.filterPrefix(worlds, args[2]);
                }
            }
            if (sub.equals("preset") && args.length == 2) {
                return CommandUtil.filterPrefix(settingsManager.presets().keySet(), args[1]);
            }
            if (sub.equals("tune")) {
                if (args.length == 2) {
                    List<String> values = new ArrayList<>(settingsManager.tunables().keySet());
                    values.add("show");
                    values.add("reset");
                    return CommandUtil.filterPrefix(values, args[1]);
                }
                if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
                    List<String> values = new ArrayList<>(settingsManager.tunables().keySet());
                    values.add("all");
                    return CommandUtil.filterPrefix(values, args[2]);
                }
                if (args.length == 3) {
                    SettingsManager.Tunable tunable = settingsManager.tunables().get(args[1].toLowerCase(Locale.ROOT));
                    if (tunable != null) {
                        return CommandUtil.filterPrefix(List.of(
                                SettingsManager.compact(tunable.defaultValue()),
                                SettingsManager.compact(tunable.min()),
                                SettingsManager.compact(tunable.max())), args[2]);
                    }
                }
            }
            return List.of();
        } catch (RuntimeException ignored) {
            // Tab补全永远不应把异常传播给命令系统。
            return List.of();
        }
    }

    private List<String> onlinePlayerNames(String prefix) {
        Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
        return CommandUtil.filterPrefix(players.stream().map(Player::getName).sorted(Comparator.naturalOrder()).toList(), prefix);
    }

    private record HelpEntry(String name, String permission, String syntax,
                             String summary, List<String> details) {
    }

    private record GiveResult(int amount, int dropped) {
    }
}
