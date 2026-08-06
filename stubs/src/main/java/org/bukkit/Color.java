package org.bukkit;

public final class Color {
 private final int rgb;

 private Color(int rgb) {
  this.rgb = rgb;
 }

 public static Color fromRGB(int red, int green, int blue) {
  return new Color((red << 16) | (green << 8) | blue);
 }

 public int asRGB() {
  return rgb;
 }
}
