package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.command.HomingBowCommand;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import cn.yjj.homingmissiles.listener.HomingListener;
import cn.yjj.homingmissiles.service.HomingService;
import cn.yjj.homingmissiles.service.LockHudService;
import cn.yjj.homingmissiles.util.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class HomingMissilesPlugin extends JavaPlugin {
    public static final String VERSION = "2.0.0";

    private SettingsManager settingsManager;
    private MessageService messages;
    private HomingBowFactory bowFactory;
    private HomingService homingService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        settingsManager = new SettingsManager(this);
        SettingsManager.LoadReport loadReport = settingsManager.reload();
        messages = new MessageService(settingsManager);
        bowFactory = new HomingBowFactory(this, settingsManager);
        LockHudService lockHud = new LockHudService(settingsManager, messages);
        homingService = new HomingService(this, settingsManager, messages, lockHud);

        getServer().getPluginManager().registerEvents(
                new HomingListener(settingsManager, messages, bowFactory, homingService), this);

        HomingBowCommand commandHandler = new HomingBowCommand(
                this, settingsManager, messages, bowFactory, homingService);
        PluginCommand pluginCommand = Objects.requireNonNull(
                getCommand("homingbow"), "plugin.yml 中缺少 homingbow 命令声明");
        pluginCommand.setExecutor(commandHandler);
        pluginCommand.setTabCompleter(commandHandler);

        homingService.start();
        int recovered = homingService.recoverLoadedArrows();

        getLogger().info("HomingMissiles " + VERSION + " 已启用：多人、多箭、连续物理制导。"
                + " 追踪上限=" + settingsManager.current().maxTrackedArrows()
                + "，恢复箭数=" + recovered
                + "，配置警告=" + loadReport.warnings().size());
        for (String warning : loadReport.warnings()) {
            getLogger().warning("配置：" + warning);
        }
    }

    @Override
    public void onDisable() {
        if (homingService != null) {
            homingService.shutdown();
        }
    }

    public SettingsManager settingsManager() {
        return settingsManager;
    }

    public MessageService messages() {
        return messages;
    }

    public HomingBowFactory bowFactory() {
        return bowFactory;
    }

    public HomingService homingService() {
        return homingService;
    }
}
