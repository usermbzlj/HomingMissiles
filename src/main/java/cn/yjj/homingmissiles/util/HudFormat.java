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
}
