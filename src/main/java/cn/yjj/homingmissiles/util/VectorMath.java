package cn.yjj.homingmissiles.util;

import org.bukkit.util.Vector;

public final class VectorMath {
    private VectorMath() {
    }

    public static Vector rotateTowards(Vector from, Vector to, double maxAngleRadians) {
        Vector a = safeNormalize(from, to);
        Vector b = safeNormalize(to, new Vector(1, 0, 0));
        double dot = clamp(a.dot(b), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle <= maxAngleRadians || angle < 1.0E-7) {
            return b;
        }

        Vector axis = a.clone().crossProduct(b);
        if (axis.lengthSquared() < 1.0E-10) {
            axis = Math.abs(a.getY()) < 0.9
                    ? a.clone().crossProduct(new Vector(0, 1, 0))
                    : a.clone().crossProduct(new Vector(1, 0, 0));
        }
        axis.normalize();

        double cos = Math.cos(maxAngleRadians);
        double sin = Math.sin(maxAngleRadians);
        Vector term1 = a.clone().multiply(cos);
        Vector term2 = axis.clone().crossProduct(a).multiply(sin);
        Vector term3 = axis.clone().multiply(axis.dot(a) * (1.0 - cos));
        Vector result = term1.add(term2).add(term3);
        return safeNormalize(result, b);
    }

    public static boolean isFinite(Vector vector) {
        return vector != null
                && Double.isFinite(vector.getX())
                && Double.isFinite(vector.getY())
                && Double.isFinite(vector.getZ());
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vector safeNormalize(Vector primary, Vector fallback) {
        if (isFinite(primary) && primary.lengthSquared() > 1.0E-12) {
            return primary.clone().normalize();
        }
        if (isFinite(fallback) && fallback.lengthSquared() > 1.0E-12) {
            return fallback.clone().normalize();
        }
        return new Vector(1, 0, 0);
    }
}
