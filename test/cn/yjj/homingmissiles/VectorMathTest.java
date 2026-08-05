package cn.yjj.homingmissiles;

import cn.yjj.homingmissiles.util.VectorMath;
import org.bukkit.util.Vector;

public final class VectorMathTest {
    private static void near(double actual, double expected, double eps, String name) {
        if (Math.abs(actual - expected) > eps) {
            throw new AssertionError(name + ": " + actual + " != " + expected);
        }
    }

    private static void finite(Vector vector, String name) {
        if (!VectorMath.isFinite(vector)) {
            throw new AssertionError(name + " produced a non-finite vector");
        }
        near(vector.length(), 1.0, 1e-6, name + " length");
    }

    public static void main(String[] args) {
        Vector quarterTurn = VectorMath.rotateTowards(
                new Vector(1, 0, 0), new Vector(0, 0, 1), Math.toRadians(45));
        near(quarterTurn.getX(), Math.sqrt(0.5), 1e-6, "90deg x");
        near(quarterTurn.getZ(), Math.sqrt(0.5), 1e-6, "90deg z");
        finite(quarterTurn, "90deg");

        Vector same = VectorMath.rotateTowards(
                new Vector(1, 0, 0), new Vector(1, 0, 0), Math.toRadians(5));
        near(same.getX(), 1.0, 1e-6, "same x");
        finite(same, "same");

        Vector opposite = VectorMath.rotateTowards(
                new Vector(1, 0, 0), new Vector(-1, 0, 0), Math.toRadians(10));
        finite(opposite, "opposite");
        near(opposite.dot(new Vector(1, 0, 0)), Math.cos(Math.toRadians(10)), 1e-6,
                "opposite max turn");

        Vector zeroSource = VectorMath.rotateTowards(
                new Vector(0, 0, 0), new Vector(0, 1, 0), Math.toRadians(8));
        finite(zeroSource, "zero source");
        near(zeroSource.getY(), 1.0, 1e-6, "zero source fallback");

        Vector bothZero = VectorMath.rotateTowards(
                new Vector(0, 0, 0), new Vector(0, 0, 0), Math.toRadians(8));
        finite(bothZero, "both zero");

        System.out.println("VectorMathTest: PASS");
    }
}
