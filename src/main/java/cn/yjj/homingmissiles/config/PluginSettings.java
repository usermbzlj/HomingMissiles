package cn.yjj.homingmissiles.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record PluginSettings(
        int configVersion,
        double trackingRange,
        double lockRetentionRange,
        int maxLifetimeTicks,
        int activationDelayTicks,
        double turnRateDegreesPerTick,
        double accelerationPerTick,
        double minSpeed,
        double maxSpeed,
        int terminalBoostDelayTicks,
        int terminalEscapeTriggerTicks,
        double terminalAccelerationPerTick,
        double terminalMaxSpeed,
        double leadTicks,
        double maxLeadTicks,
        boolean dynamicRetargeting,
        double switchAdvantageBlocks,
        boolean requireLineOfSight,
        boolean targetCreative,
        boolean targetSpectator,
        boolean respectVanish,
        boolean noGravity,
        int manualLockDurationTicks,
        int manualLockGraceTicks,
        double manualLockConeDegrees,
        double manualLockBreakConeDegrees,
        int maxTrackedArrows,
        int maxTrackedPerPlayer,
        int launchCooldownTicks,
        boolean cancelRejectedShot,
        double minimumDamage,
        boolean glowingArrow,
        boolean particles,
        int particleIntervalTicks,
        boolean launchSound,
        boolean lockSounds,
        boolean launchEffects,
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
        boolean pixelHudEnabled,
        String hudClientModDownloadUrl,
        int hudClientModDetectionTicks,
        boolean shooterBossBar,
        boolean targetBossBar,
        String hudResourcePackUrl,
        String hudResourcePackSha1,
        boolean hudResourcePackRequired,
        String hudResourcePackPrompt,
        boolean assumeServerPackProvidesHud,
        boolean hudSelfHostEnabled,
        String hudSelfHostBindAddress,
        int hudSelfHostPort,
        String hudSelfHostPath,
        boolean warningAudio,
        int warningMinIntervalTicks,
        int warningMaxIntervalTicks,
        String bowName,
        List<String> bowLore,
        boolean bowFlame,
        boolean bowInfinity,
        boolean bowUnbreakable,
        int bowPowerLevel,
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
            String normalized = raw.trim().toUpperCase();
            if ("FALSE".equals(normalized)) {
                return OFF;
            }
            if ("TRUE".equals(normalized)) {
                return fallback;
            }
            try {
                return valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }
}
