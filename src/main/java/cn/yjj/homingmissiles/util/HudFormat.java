package cn.yjj.homingmissiles.util;

public final class HudFormat {
    public static final int LAYER_SPACER = 0xE0FF;
    public static final int BASE_GLYPH = 0xE100;
    public static final int WEAPON_GLYPH_BASE = 0xE101;
    public static final int THREAT_GLYPH_BASE = 0xE106;
    public static final int PITCH_GLYPH_BASE = 0xE11E;
    public static final int HEADING_GLYPH_BASE = 0xE137;
    public static final int SPEED_GLYPH_BASE = 0xE17F;
    public static final int ALTITUDE_GLYPH_BASE = 0xE1D0;
    public static final int HEIGHT_GLYPH_BASE = 0xE231;
    public static final int PROGRADE_GLYPH_BASE = 0xE272;
    public static final int LOCK_MARKER_GLYPH_BASE = 0xE2A3;
    public static final int LOCK_PROGRESS_GLYPH_BASE = 0xE305;
    public static final int GLYPH_COUNT = 535;

    private static final int PITCH_BUCKETS = 25;
    private static final int HEADING_BUCKETS = 72;
    private static final int SPEED_BUCKETS = 81;
    private static final int ALTITUDE_BUCKETS = 97;
    private static final int HEIGHT_BUCKETS = 65;

    private HudFormat() {
    }

    public static String cardinal(float viewerYawDegrees, double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx * dx + dz * dz < 1.0E-8) {
            return "·";
        }

        double absolute = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = wrapDegrees(absolute - viewerYawDegrees);
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

    /** Converts range into an intentionally imprecise threat gauge for the target HUD. */
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
        double urgency = closeness * closeness;
        return (int) Math.round(safeMax - (safeMax - safeMin) * urgency);
    }

    public static char baseGlyph() {
        return BASE_GLYPH;
    }

    public static char weaponGlyph(int active) {
        return (char) (WEAPON_GLYPH_BASE + Math.max(0, Math.min(4, active)));
    }

    /** Compatibility alias for integrations compiled against the previous HUD formatter. */
    public static char shooterGlyph(int active) {
        return weaponGlyph(active);
    }

    /**
     * Returns one of eight relative sectors. 0 is forward, 2 is left,
     * 4 is behind and 6 is right, matching the HUD sprite atlas.
     */
    public static int directionSector(float viewerYawDegrees,
                                      double fromX, double fromZ,
                                      double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (dx * dx + dz * dz < 1.0E-8) {
            return 0;
        }
        double absolute = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = ((absolute - viewerYawDegrees) % 360.0 + 360.0) % 360.0;
        return Math.floorMod((int) Math.round(relative / 45.0), 8);
    }

    public static int urgencyBand(double proximity) {
        double safe = clamp(proximity, 0.0, 1.0);
        if (safe >= 0.72) {
            return 2;
        }
        return safe >= 0.38 ? 1 : 0;
    }

    public static char threatGlyph(int directionSector, double proximity) {
        int sector = Math.floorMod(directionSector, 8);
        return (char) (THREAT_GLYPH_BASE + urgencyBand(proximity) * 8 + sector);
    }

    public static int headingDegrees(float minecraftYawDegrees) {
        return Math.floorMod((int) Math.round(minecraftYawDegrees + 180.0), 360);
    }

    public static char headingGlyph(float minecraftYawDegrees) {
        int bucket = Math.floorMod((int) Math.round(headingDegrees(minecraftYawDegrees) / 5.0), HEADING_BUCKETS);
        return (char) (HEADING_GLYPH_BASE + bucket);
    }

    public static char pitchGlyph(float minecraftPitchDegrees) {
        int bucket = bucket(minecraftPitchDegrees, -60.0, 5.0, PITCH_BUCKETS);
        return (char) (PITCH_GLYPH_BASE + bucket);
    }

    public static char speedGlyph(double blocksPerSecond) {
        int bucket = bucket(blocksPerSecond, 0.0, 2.0, SPEED_BUCKETS);
        return (char) (SPEED_GLYPH_BASE + bucket);
    }

    public static char altitudeGlyph(double blockY) {
        int bucket = bucket(blockY, -64.0, 4.0, ALTITUDE_BUCKETS);
        return (char) (ALTITUDE_GLYPH_BASE + bucket);
    }

    public static char heightGlyph(double blocksAboveGround) {
        int bucket = bucket(blocksAboveGround, 0.0, 4.0, HEIGHT_BUCKETS);
        return (char) (HEIGHT_GLYPH_BASE + bucket);
    }

    public static char progradeGlyph(float viewerYawDegrees, float viewerPitchDegrees,
                                     double velocityX, double velocityY, double velocityZ) {
        double horizontal = Math.hypot(velocityX, velocityZ);
        if (!Double.isFinite(horizontal) || horizontal * horizontal + velocityY * velocityY < 1.0E-6) {
            return (char) (PROGRADE_GLYPH_BASE + 24);
        }

        double velocityYaw = Math.toDegrees(Math.atan2(-velocityX, velocityZ));
        double velocityPitch = -Math.toDegrees(Math.atan2(velocityY, horizontal));
        double yawOffset = clamp(-wrapDegrees(velocityYaw - viewerYawDegrees), -36.0, 36.0);
        double pitchOffset = clamp(velocityPitch - viewerPitchDegrees, -30.0, 30.0);
        int column = (int) Math.round(yawOffset / 12.0) + 3;
        int row = (int) Math.round(pitchOffset / 10.0) + 3;
        return (char) (PROGRADE_GLYPH_BASE + row * 7 + column);
    }

    public static char lockMarkerGlyph(double screenX, double screenY, boolean locked) {
        int column = bucket(screenX, -1.0, 1.0 / 3.0, 7);
        int row = bucket(screenY, -1.0, 1.0 / 3.0, 7);
        int modeOffset = locked ? 49 : 0;
        return (char) (LOCK_MARKER_GLYPH_BASE + modeOffset + row * 7 + column);
    }

    public static char lockProgressGlyph(double progress, boolean locked) {
        if (locked) {
            return (char) (LOCK_PROGRESS_GLYPH_BASE + 17);
        }
        int bucket = (int) Math.round(clamp(progress, 0.0, 1.0) * 16.0);
        return (char) (LOCK_PROGRESS_GLYPH_BASE + bucket);
    }

    public static String composeLayers(char... glyphs) {
        StringBuilder result = new StringBuilder(Math.max(1, glyphs.length * 2 - 1));
        for (char glyph : glyphs) {
            if (glyph == 0) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append((char) LAYER_SPACER);
            }
            result.append(glyph);
        }
        return result.toString();
    }

    private static int bucket(double value, double minimum, double step, int count) {
        double safe = Double.isFinite(value) ? value : minimum;
        return Math.max(0, Math.min(count - 1, (int) Math.round((safe - minimum) / step)));
    }

    private static double wrapDegrees(double value) {
        return ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
