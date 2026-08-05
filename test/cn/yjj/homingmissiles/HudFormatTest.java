package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.util.HudFormat;

public final class HudFormatTest {
    private static void equal(String actual, String expected, String name) {
        if (!actual.equals(expected)) {
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
        System.out.println("HudFormatTest: PASS");
    }
}
