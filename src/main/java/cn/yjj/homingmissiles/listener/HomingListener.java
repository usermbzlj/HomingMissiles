package cn.yjj.homingmissiles.listener;

import cn.yjj.homingmissiles.config.PluginSettings;
import cn.yjj.homingmissiles.config.SettingsManager;
import cn.yjj.homingmissiles.item.HomingBowFactory;
import cn.yjj.homingmissiles.service.HomingService;
import cn.yjj.homingmissiles.util.MessageService;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;

public final class HomingListener implements Listener {
    private final SettingsManager settingsManager;
    private final MessageService messages;
    private final HomingBowFactory bowFactory;
    private final HomingService homingService;

    public HomingListener(SettingsManager settingsManager, MessageService messages,
                          HomingBowFactory bowFactory, HomingService homingService) {
        this.settingsManager = settingsManager;
        this.messages = messages;
        this.bowFactory = bowFactory;
        this.homingService = homingService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) {
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        if (!bowFactory.isHomingBow(event.getBow())) {
            return;
        }

        PluginSettings settings = settingsManager.current();
        if (!shooter.hasPermission("homingmissiles.use")) {
            reject(event, arrow, settings);
            messages.feedback(shooter, settings.rejectionFeedback(), "rejected-permission");
            return;
        }

        HomingService.TrackResult result = homingService.tryTrack(shooter, arrow);
        if (result.accepted()) {
            return;
        }

        reject(event, arrow, settings);
        switch (result.reason()) {
            case GLOBAL_LIMIT -> messages.feedback(shooter, settings.rejectionFeedback(),
                    "rejected-global-limit", "limit", settings.maxTrackedArrows());
            case PLAYER_LIMIT -> messages.feedback(shooter, settings.rejectionFeedback(),
                    "rejected-player-limit",
                    "active", homingService.activeCount(shooter.getUniqueId()),
                    "limit", settings.maxTrackedPerPlayer());
            case COOLDOWN -> messages.feedback(shooter, settings.rejectionFeedback(),
                    "rejected-cooldown", "ticks", result.remainingTicks());
            case WORLD_DISABLED -> messages.feedback(shooter, settings.rejectionFeedback(),
                    "rejected-world");
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof AbstractArrow arrow) {
            homingService.handleHit(arrow);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        homingService.recoverEntities(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        homingService.suspendEntities(event.getEntities());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        homingService.forgetPlayer(event.getPlayer().getUniqueId());
    }

    private static void reject(EntityShootBowEvent event, AbstractArrow arrow, PluginSettings settings) {
        if (settings.cancelRejectedShot()) {
            event.setCancelled(true);
            if (arrow.isValid()) {
                arrow.remove();
            }
        }
    }
}
