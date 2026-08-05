package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.util.HudFormat;

public final class HudFormatTest {
    private static void equal(String actual, String expected, String name) {
        if (!actual.equals(expected)) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    private static void equal(int actual, int expected, String name) {
        if (actual != expected) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    private static void equal(char actual, char expected, String name) {
        if (actual != expected) {
            throw new AssertionError(name + ": U+" + Integer.toHexString(actual)
                    + " != U+" + Integer.toHexString(expected));
        }
    }

    private static void near(double actual, double expected, double epsilon, String name) {
        if (Math.abs(actual - expected) > epsilon) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        // Facing south (yaw 0): threat to the south is forward
        equal(HudFormat.cardinal(0f, 0, 0, 0, 10), "↑", "south forward");
        equal(HudFormat.cardinal(0f, 0, 0, -10, 0), "←", "south left/west");
        equal(HudFormat.cardinal(0f, 0, 0, 10, 0), "→", "south right/east");
        equal(HudFormat.cardinal(0f, 0, 0, 0, -10), "↓", "south behind");
        equal(HudFormat.cardinal(0f, 0, 0, 0, 0), "·", "coincident");

        equal(HudFormat.hardpoints(2, 4), "◆ ◆ ◇ ◇", "hardpoint gauge");
        equal(HudFormat.hardpoints(9, 4), "◆ ◆ ◆ ◆", "hardpoint clamp");
        near(HudFormat.ratio(1, 4), 0.25, 1.0E-9, "channel ratio");
        near(HudFormat.proximity(48.0, 48.0), 0.0, 1.0E-9, "edge proximity");
        near(HudFormat.proximity(0.0, 48.0), 1.0, 1.0E-9, "terminal proximity");
        equal(HudFormat.warningInterval(48.0, 48.0, 4, 24), 24, "distant warning");
        equal(HudFormat.warningInterval(0.0, 48.0, 4, 24), 4, "terminal warning");
        equal(HudFormat.directionSector(0f, 0, 0, 0, 10), 0, "forward sector");
        equal(HudFormat.directionSector(0f, 0, 0, -10, 0), 2, "left sector");
        equal(HudFormat.directionSector(0f, 0, 0, 0, -10), 4, "rear sector");
        equal(HudFormat.directionSector(0f, 0, 0, 10, 0), 6, "right sector");
        equal(HudFormat.shooterGlyph(1), '\uE100', "one-channel glyph");
        equal(HudFormat.shooterGlyph(4), '\uE103', "four-channel glyph");
        equal(HudFormat.threatGlyph(0, 0.0), '\uE104', "distant forward threat glyph");
        equal(HudFormat.threatGlyph(7, 1.0), '\uE11B', "terminal sector-seven glyph");
        System.out.println("HudFormatTest: PASS");
    }
}
