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
        equal(HudFormat.baseGlyph(), '\uE100', "base layer glyph");
        equal(HudFormat.weaponGlyph(0), '\uE101', "empty hardpoint layer");
        equal(HudFormat.shooterGlyph(4), '\uE105', "four-channel compatibility glyph");
        equal(HudFormat.threatGlyph(0, 0.0), '\uE106', "distant forward threat glyph");
        equal(HudFormat.threatGlyph(7, 1.0), '\uE11D', "terminal sector-seven glyph");
        equal(HudFormat.pitchGlyph(-90f), '\uE11E', "pitch lower clamp");
        equal(HudFormat.pitchGlyph(0f), '\uE12A', "level pitch layer");
        equal(HudFormat.pitchGlyph(90f), '\uE136', "pitch upper clamp");
        equal(HudFormat.headingDegrees(180f), 0, "north heading");
        equal(HudFormat.headingDegrees(-90f), 90, "east heading");
        equal(HudFormat.headingGlyph(180f), '\uE137', "north heading layer");
        equal(HudFormat.headingGlyph(0f), '\uE15B', "south heading layer");
        equal(HudFormat.speedGlyph(0.0), '\uE17F', "zero speed layer");
        equal(HudFormat.speedGlyph(999.0), '\uE1CF', "speed clamp layer");
        equal(HudFormat.altitudeGlyph(-100.0), '\uE1D0', "altitude lower clamp");
        equal(HudFormat.altitudeGlyph(999.0), '\uE230', "altitude upper clamp");
        equal(HudFormat.heightGlyph(0.0), '\uE231', "ground height layer");
        equal(HudFormat.heightGlyph(999.0), '\uE271', "height upper clamp");
        near(HudFormat.targetScreenX(0f, 10.0, 0.0, 90.0), -1.0, 1.0E-9,
                "facing south puts east target on screen left");
        near(HudFormat.targetScreenX(0f, -10.0, 0.0, 90.0), 1.0, 1.0E-9,
                "facing south puts west target on screen right");
        near(HudFormat.targetScreenX(180f, 10.0, 0.0, 90.0), 1.0, 1.0E-9,
                "facing north puts east target on screen right");
        near(HudFormat.targetScreenX(180f, -10.0, 0.0, 90.0), -1.0, 1.0E-9,
                "facing north puts west target on screen left");
        equal(HudFormat.progradeGlyph(0f, 0f, 0.0, 0.0, 0.0), '\uE28A', "stationary prograde");
        equal(HudFormat.lockMarkerGlyph(0.0, 0.0, false), '\uE2BB', "acquiring marker center");
        equal(HudFormat.lockMarkerGlyph(0.0, 0.0, true), '\uE2EC', "locked marker center");
        equal(HudFormat.lockProgressGlyph(0.0, false), '\uE305', "lock progress start");
        equal(HudFormat.lockProgressGlyph(1.0, true), '\uE316', "lock progress complete");
        String layers = HudFormat.composeLayers('\uE100', '\uE137', '\uE28A');
        equal(layers.length(), 5, "three-layer sequence length");
        equal(layers.charAt(1), (char) HudFormat.LAYER_SPACER, "negative spacer placement");
        System.out.println("HudFormatTest: PASS");
    }
}
