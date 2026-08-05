package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.model.TrackedArrow;
import cn.yjj.homingmissiles.util.GuidanceMath;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class GuidanceMathTest {
    private static void near(double actual, double expected, double epsilon, String name) {
        if (Math.abs(actual - expected) > epsilon) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        near(GuidanceMath.interceptTimeTicks(
                new Vector(10, 0, 0), new Vector(0, 0, 0), 2.0, 40.0),
                5.0, 1.0E-9, "stationary intercept");
        near(GuidanceMath.interceptTimeTicks(
                new Vector(10, 0, 0), new Vector(1, 0, 0), 2.0, 40.0),
                10.0, 1.0E-9, "receding intercept");
        near(GuidanceMath.interceptTimeTicks(
                new Vector(10, 0, 0), new Vector(3, 0, 0), 2.0, 24.0),
                24.0, 1.0E-9, "temporarily impossible intercept uses maximum lead");

        UUID target = UUID.randomUUID();
        TrackedArrow state = new TrackedArrow(null, UUID.randomUUID(), 0);
        state.observeLock(target, 20.0, 100, 3);
        state.observeLock(target, 20.1, 100, 3);
        state.observeLock(target, 20.2, 100, 3);
        state.observeLock(target, 20.3, 100, 3);
        if (!state.terminalBoosted()) {
            throw new AssertionError("opening target must ignite terminal boost");
        }
        state.clearLockObservation();
        if (!state.terminalBoosted()) {
            throw new AssertionError("terminal motor must remain ignited after lock loss");
        }

        TrackedArrow delayed = new TrackedArrow(null, UUID.randomUUID(), 0);
        delayed.observeLock(target, 20.0, 3, 20);
        delayed.observeLock(target, 19.9, 3, 20);
        delayed.observeLock(target, 19.8, 3, 20);
        if (!delayed.terminalBoosted()) {
            throw new AssertionError("lock duration must ignite terminal boost");
        }
        System.out.println("GuidanceMathTest: PASS");
    }
}
