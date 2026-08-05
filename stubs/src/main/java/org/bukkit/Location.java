package org.bukkit;
import org.bukkit.util.Vector;
public class Location {
 public World getWorld() { return null; }
 public Vector toVector() { return null; }
 public double distanceSquared(Location other) { return 0; }
 public double distance(Location other) { return Math.sqrt(distanceSquared(other)); }
 public Location add(double x, double y, double z) { return this; }
}
