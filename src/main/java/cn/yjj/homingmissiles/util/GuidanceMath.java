package cn.yjj.homingmissiles.util;

import org.bukkit.util.Vector;

public final class GuidanceMath {
    private static final double EPSILON = 1.0E-9;

    private GuidanceMath() {
    }

    /**
     * Solves |relativePosition + targetVelocity * t| = missileSpeed * t and
     * returns the earliest usable intercept time. When the target is currently
     * outrunning the missile, maximumLeadTicks is returned so guidance still
     * points ahead while the terminal motor accelerates.
     */
    public static double interceptTimeTicks(Vector relativePosition, Vector targetVelocity,
                                            double missileSpeed, double maximumLeadTicks) {
        if (!VectorMath.isFinite(relativePosition) || !VectorMath.isFinite(targetVelocity)
                || !Double.isFinite(missileSpeed) || !Double.isFinite(maximumLeadTicks)
                || maximumLeadTicks <= 0.0) {
            return 0.0;
        }

        double speed = Math.max(EPSILON, missileSpeed);
        double c = relativePosition.lengthSquared();
        if (c <= EPSILON) {
            return 0.0;
        }

        double a = targetVelocity.lengthSquared() - speed * speed;
        double b = 2.0 * relativePosition.dot(targetVelocity);
        double candidate = Double.NaN;
        if (Math.abs(a) <= EPSILON) {
            if (Math.abs(b) > EPSILON) {
                candidate = -c / b;
            }
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant >= 0.0 && Double.isFinite(discriminant)) {
                double root = Math.sqrt(discriminant);
                double first = (-b - root) / (2.0 * a);
                double second = (-b + root) / (2.0 * a);
                candidate = smallestPositive(first, second);
            }
        }

        if (!Double.isFinite(candidate) || candidate < 0.0) {
            candidate = maximumLeadTicks;
        }
        return VectorMath.clamp(candidate, 0.0, maximumLeadTicks);
    }

    public static boolean shouldIgniteTerminal(boolean alreadyIgnited,
                                                int lockedTicks,
                                                int openingTicks,
                                                int delayTicks,
                                                int escapeTriggerTicks) {
        return alreadyIgnited
                || lockedTicks >= Math.max(0, delayTicks)
                || openingTicks >= Math.max(1, escapeTriggerTicks);
    }

    private static double smallestPositive(double first, double second) {
        boolean firstValid = Double.isFinite(first) && first >= 0.0;
        boolean secondValid = Double.isFinite(second) && second >= 0.0;
        if (firstValid && secondValid) {
            return Math.min(first, second);
        }
        if (firstValid) {
            return first;
        }
        return secondValid ? second : Double.NaN;
    }
}
