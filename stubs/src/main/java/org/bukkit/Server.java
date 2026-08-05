package org.bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
public interface Server {
 PluginManager getPluginManager();
 BukkitScheduler getScheduler();
 Player getPlayer(UUID id);
 Player getPlayerExact(String name);
 Collection<? extends Player> getOnlinePlayers();
 List<World> getWorlds();
 World getWorld(String name);
}
