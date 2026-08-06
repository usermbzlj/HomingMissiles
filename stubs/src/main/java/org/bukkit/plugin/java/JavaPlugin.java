package org.bukkit.plugin.java;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import java.io.File;
import java.util.logging.Logger;
public class JavaPlugin implements Plugin {
 public void onEnable() {}
 public void onDisable() {}
 public void saveDefaultConfig() {}
 public void saveConfig() {}
 public void reloadConfig() {}
 public File getDataFolder() { return null; }
 public FileConfiguration getConfig() { return null; }
 public Server getServer() { return null; }
 public PluginCommand getCommand(String name) { return null; }
 public Logger getLogger() { return null; }
}
