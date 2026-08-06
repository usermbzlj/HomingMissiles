package org.bukkit.entity;
import org.bukkit.inventory.ItemStack;
public interface LivingEntity extends Entity {
 boolean hasLineOfSight(Entity other);
 default boolean isHandRaised() { return false; }
 default boolean hasActiveItem() { return false; }
 default ItemStack getActiveItem() { return null; }
}
