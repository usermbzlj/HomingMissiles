package org.bukkit.event.entity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
public class EntityShootBowEvent {
 public LivingEntity getEntity() { return null; }
 public Entity getProjectile() { return null; }
 public ItemStack getBow() { return null; }
 public void setCancelled(boolean cancelled) {}
}
