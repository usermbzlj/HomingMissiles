package org.bukkit.boss;
import org.bukkit.entity.Player;
import java.util.List;
public interface BossBar {
 void setTitle(String title);
 void setProgress(double progress);
 void addPlayer(Player player);
 void removeAll();
 List<Player> getPlayers();
 void setVisible(boolean visible);
}
