package org.bukkit.entity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.PlayerInventory;
public interface Player extends LivingEntity, CommandSender {
 boolean isOnline();
 GameMode getGameMode();
 Location getEyeLocation();
 PlayerInventory getInventory();
 String getName();
 void playSound(Location location, Sound sound, float volume, float pitch);
 void sendActionBar(String message);
 boolean canSee(Player player);
}
