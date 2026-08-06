package org.bukkit;
import org.bukkit.util.Vector;
public class Location implements Cloneable {
 public World getWorld() { return null; }
 public Vector toVector() { return null; }
 public double getX() { return 0; }
 public double getY() { return 0; }
 public double getZ() { return 0; }
 public float getYaw() { return 0f; }
 public float getPitch() { return 0f; }
 public Vector getDirection() { return new Vector(); }
 public double distanceSquared(Location other) { return 0; }
 public double distance(Location other) { return Math.sqrt(distanceSquared(other)); }
 public Location add(double x, double y, double z) { return this; }
 public Location add(Vector vector) { return this; }
 public Location clone() { return this; }
}
