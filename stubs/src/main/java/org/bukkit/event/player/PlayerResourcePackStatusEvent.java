package org.bukkit.event.player;
import org.bukkit.entity.Player;
import java.util.UUID;
public class PlayerResourcePackStatusEvent {
 public enum Status { SUCCESSFULLY_LOADED, DECLINED, FAILED_DOWNLOAD, ACCEPTED, DOWNLOADED, INVALID_URL, FAILED_RELOAD, DISCARDED }
 public Player getPlayer() { return null; }
 public UUID getID() { return null; }
 public Status getStatus() { return null; }
}
