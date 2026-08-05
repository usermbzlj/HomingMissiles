package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.config.SettingsManager;

public final class SettingsUtilityTest {
    private static void equal(String actual, String expected, String name) {
        if (!actual.equals(expected)) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        if (SettingsManager.HARD_MAX_TRACKED_PER_PLAYER != 4) {
            throw new AssertionError("hard per-player limit must stay at four");
        }
        equal(SettingsManager.compact(8.0), "8", "integer compact");
        equal(SettingsManager.compact(0.015), "0.015", "decimal compact");
        equal(SettingsManager.compact(2.8000), "2.8", "trailing zero compact");
        System.out.println("SettingsUtilityTest: PASS");
    }
}
