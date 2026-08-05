package cn.yjj.homingmissiles.util;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageService {
    private final SettingsManager settingsManager;

    public MessageService(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public String text(String key, Object... replacements) {
        PluginSettings settings = settingsManager.current();
        String template = settings.messages().getOrDefault(key, "&c缺失消息：" + key);
        return replace(template, replacements);
    }

    public String prefixed(String key, Object... replacements) {
        return prefix() + text(key, replacements);
    }

    public String prefix() {
        return settingsManager.current().messages().getOrDefault("prefix", "§8[§b制导箭§8] §r");
    }

    public void send(CommandSender sender, String key, Object... replacements) {
        sender.sendMessage(prefixed(key, replacements));
    }

    public void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(SettingsManager.color(message));
    }

    public void feedback(Player player, PluginSettings.FeedbackMode mode,
                         String key, Object... replacements) {
        if (mode == PluginSettings.FeedbackMode.OFF) {
            return;
        }
        String message = prefixed(key, replacements);
        if (mode == PluginSettings.FeedbackMode.ACTIONBAR) {
            player.sendActionBar(message);
        } else {
            player.sendMessage(message);
        }
    }

    public void noPermission(CommandSender sender, String permission) {
        send(sender, "no-permission", "permission", permission);
    }

    private static String replace(String template, Object... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("消息占位符必须成对提供");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < replacements.length; i += 2) {
            values.put(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
