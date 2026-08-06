package org.bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.List;
public interface World {
 String getName();
 List<Player> getPlayers();
 List<Entity> getEntities();
 void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra);
 <T> void spawnParticle(Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, T data);
 void playSound(Location location, Sound sound, float volume, float pitch);
 default int getHighestBlockYAt(int x, int z) { return 0; }
 Item dropItemNaturally(Location location, ItemStack item);
}
