package org.bukkit;
public enum Particle {
 POOF, FLAME, ELECTRIC_SPARK, SMOKE, EXPLOSION, SONIC_BOOM, LARGE_SMOKE,
 CRIT, FLASH(Color.class), SMALL_FLAME, WHITE_SMOKE, END_ROD, CAMPFIRE_COSY_SMOKE, CLOUD;

 private final Class<?> dataType;

 Particle() {
  this(Void.class);
 }

 Particle(Class<?> dataType) {
  this.dataType = dataType;
 }

 public Class<?> getDataType() {
  return dataType;
 }
}
