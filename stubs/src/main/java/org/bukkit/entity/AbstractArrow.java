package org.bukkit.entity;
public interface AbstractArrow extends Projectile {
 enum PickupStatus { DISALLOWED, ALLOWED, CREATIVE_ONLY }
 void setPickupStatus(PickupStatus status);
 void setDamage(double damage);
 boolean isInBlock();
}
