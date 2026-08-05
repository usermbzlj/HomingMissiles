package cn.yjj.homingmissiles.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PluginSettings(
        int configVersion,
        double trackingRange,
        int maxLifetimeTicks,
        int activationDelayTicks,
        double turnRateDegreesPerTick,
        double accelerationPerTick,
        double minSpeed,
        double maxSpeed,
        double leadTicks,
        boolean dynamicRetargeting,
        double switchAdvantageBlocks,
        boolean requireLineOfSight,
        boolean targetCreative,
        boolean targetSpectator,
        boolean respectVanish,
        boolean noGravity,
        int maxTrackedArrows,
        int maxTrackedPerPlayer,
        int launchCooldownTicks,
        boolean cancelRejectedShot,
        double overrideDamage,
        boolean glowingArrow,
        boolean particles,
        int particleIntervalTicks,
        boolean targetMarkerParticles,
        boolean launchSound,
        boolean lockSounds,
        boolean impactEffects,
        boolean selfDestructEffects,
        boolean removeArrowsOnDisable,
        boolean recoverArrowsOnEnable,
        Set<String> disabledWorlds,
        FeedbackMode launchFeedback,
        FeedbackMode lockShooterFeedback,
        FeedbackMode lockTargetFeedback,
        FeedbackMode rejectionFeedback,
        boolean hudEnabled,
        boolean shooterActionBar,
        boolean targetActionBar,
        boolean warningBeep,
        int beepMinIntervalTicks,
        int beepMaxIntervalTicks,
        String bowName,
        List<String> bowLore,
        Map<String, String> messages,
        int helpPageSize,
        int maxGiveAmount
) {
    public enum FeedbackMode {
        ACTIONBAR,
        CHAT,
        OFF;

        public static FeedbackMode parse(String raw, FeedbackMode fallback) {
            if (raw == null) {
                return fallback;
            }
            try {
                return valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
