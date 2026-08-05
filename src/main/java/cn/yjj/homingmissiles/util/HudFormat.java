package cn.yjj.homingmissiles.util;

public final class HudFormat {
    private HudFormat() {
    }

    public static String cardinal(float viewerYawDegrees, double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx * dx + dz * dz < 1.0E-8) {
            return "·";
        }

        double absolute = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = absolute - viewerYawDegrees;
        relative = ((relative + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;

        int quarter = (int) Math.round(relative / 90.0);
        return switch (quarter) {
            case 1, -3 -> "←";
            case -1, 3 -> "→";
            case 2, -2 -> "↓";
            default -> "↑";
        };
    }

    public static String hardpoints(int active, int capacity) {
        int safeCapacity = Math.max(1, capacity);
        int safeActive = Math.max(0, Math.min(active, safeCapacity));
        StringBuilder result = new StringBuilder(safeCapacity * 2);
        for (int i = 0; i < safeCapacity; i++) {
            result.append(i < safeActive ? "◆" : "◇");
            if (i + 1 < safeCapacity) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    public static double ratio(int value, int maximum) {
        if (maximum <= 0) {
            return 0.0;
        }
        return clamp((double) value / maximum, 0.0, 1.0);
    }

    /**
     * Converts range into an intentionally imprecise threat gauge for the target HUD.
     */
    public static double proximity(double distance, double trackingRange) {
        if (!Double.isFinite(distance) || !Double.isFinite(trackingRange) || trackingRange <= 0.0) {
            return 0.0;
        }
        return clamp(1.0 - distance / trackingRange, 0.0, 1.0);
    }

    public static int warningInterval(double distance, double trackingRange, int min, int max) {
        int safeMin = Math.max(1, Math.min(min, max));
        int safeMax = Math.max(safeMin, Math.max(min, max));
        double closeness = proximity(distance, trackingRange);
        // Ease-in keeps distant alerts calm and makes the terminal phase accelerate decisively.
        double urgency = closeness * closeness;
        return (int) Math.round(safeMax - (safeMax - safeMin) * urgency);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
