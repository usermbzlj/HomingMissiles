package org.bukkit.entity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.PlayerInventory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.util.UUID;
import org.bukkit.plugin.Plugin;
public interface Player extends LivingEntity, CommandSender {
 boolean isOnline();
 boolean isGliding();
 GameMode getGameMode();
 Location getEyeLocation();
 PlayerInventory getInventory();
 String getName();
 void playSound(Location location, Sound sound, float volume, float pitch);
 void playSound(Location location, String sound, SoundCategory category, float volume, float pitch);
 void sendActionBar(String message);
 void sendActionBar(Component message);
 void addResourcePack(UUID id, String url, byte[] hash, String prompt, boolean force);
 void removeResourcePack(UUID id);
 void sendPluginMessage(Plugin source, String channel, byte[] message);
 void showTitle(Title title);
 void clearTitle();
 boolean canSee(Player player);
}
