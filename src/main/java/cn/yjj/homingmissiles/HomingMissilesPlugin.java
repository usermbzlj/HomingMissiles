package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.command.HomingBowCommand;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import cn.yjj.homingmissiles.listener.HomingListener;
import cn.yjj.homingmissiles.service.HomingService;
import cn.yjj.homingmissiles.service.HudPackServer;
import cn.yjj.homingmissiles.service.LockHudService;
import cn.yjj.homingmissiles.util.MessageService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.io.IOException;
import java.util.logging.Level;

public final class HomingMissilesPlugin extends JavaPlugin {
    public static final String VERSION = "3.1.3";

    private SettingsManager settingsManager;
    private MessageService messages;
    private HomingBowFactory bowFactory;
    private HomingService homingService;
    private HudPackServer hudPackServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        settingsManager = new SettingsManager(this);
        SettingsManager.LoadReport loadReport = settingsManager.reload();
        hudPackServer = new HudPackServer(getLogger());
        refreshHudHosting();
        messages = new MessageService(settingsManager);
        bowFactory = new HomingBowFactory(this, settingsManager);
        LockHudService lockHud = new LockHudService(this, settingsManager);
        homingService = new HomingService(this, settingsManager, messages, bowFactory, lockHud);

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
        homingService.refreshHudResources();

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
        if (hudPackServer != null) {
            hudPackServer.close();
        }
    }

    public void refreshHudHosting() {
        if (hudPackServer == null || settingsManager == null || settingsManager.current() == null) {
            return;
        }
        var settings = settingsManager.current();
        var config = new HudPackServer.Config(
                settings.hudSelfHostEnabled(),
                settings.hudSelfHostBindAddress(),
                settings.hudSelfHostPort(),
                settings.hudSelfHostPath(),
                settings.hudResourcePackSha1());
        try {
            HudPackServer.StartResult result = hudPackServer.restart(getDataFolder().toPath(), config);
            if (result.enabled()) {
                getLogger().info("内置 HUD 资源包服务已启动：http://" + result.bindAddress() + ":"
                        + result.port() + result.path() + "，字节=" + result.contentLength()
                        + "，SHA-1=" + result.sha1());
            }
        } catch (IOException | RuntimeException ex) {
            getLogger().log(Level.SEVERE, "内置 HUD 资源包服务启动失败；像素 HUD 将降级为 BossBar", ex);
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
