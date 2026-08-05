package org.bukkit.entity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import java.util.UUID;
public interface Entity {
 UUID getUniqueId();
 World getWorld();
 Location getLocation();
 Vector getVelocity();
 void setVelocity(Vector velocity);
 boolean isValid();
 boolean isDead();
 void remove();
 void setGlowing(boolean glowing);
 void setGravity(boolean gravity);
 PersistentDataContainer getPersistentDataContainer();
}
