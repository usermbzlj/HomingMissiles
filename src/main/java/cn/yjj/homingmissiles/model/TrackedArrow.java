package cn.yjj.homingmissiles.model;

import cn.yjj.homingmissiles.util.GuidanceMath;
import org.bukkit.entity.AbstractArrow;

import java.util.UUID;

public final class TrackedArrow {
    private final AbstractArrow arrow;
    private final UUID shooterId;
    private UUID targetId;
    private UUID lockNotifiedTarget;
    private UUID observedTarget;
    private int ageTicks;
    private int lockedTicks;
    private int openingTicks;
    private double lastTargetDistance = Double.NaN;
    private boolean terminalBoosted;

    public TrackedArrow(AbstractArrow arrow, UUID shooterId, int ageTicks) {
        this.arrow = arrow;
        this.shooterId = shooterId;
        this.ageTicks = Math.max(0, ageTicks);
    }

    public AbstractArrow arrow() {
        return arrow;
    }

    public UUID shooterId() {
        return shooterId;
    }

    public UUID targetId() {
        return targetId;
    }

    public void targetId(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID lockNotifiedTarget() {
        return lockNotifiedTarget;
    }

    public void lockNotifiedTarget(UUID targetId) {
        this.lockNotifiedTarget = targetId;
    }

    public int ageTicks() {
        return ageTicks;
    }

    public int incrementAge() {
        return ++ageTicks;
    }

    public void observeLock(UUID target, double distance,
                            int boostDelayTicks, int escapeTriggerTicks) {
        if (!target.equals(observedTarget)) {
            observedTarget = target;
            lockedTicks = 1;
            openingTicks = 0;
            lastTargetDistance = distance;
        } else {
            lockedTicks++;
            if (Double.isFinite(lastTargetDistance) && distance > lastTargetDistance + 0.04) {
                openingTicks++;
            } else {
                openingTicks = Math.max(0, openingTicks - 1);
            }
            lastTargetDistance = distance;
        }
        terminalBoosted = GuidanceMath.shouldIgniteTerminal(
                terminalBoosted, lockedTicks, openingTicks, boostDelayTicks, escapeTriggerTicks);
    }

    public void clearLockObservation() {
        observedTarget = null;
        lockedTicks = 0;
        openingTicks = 0;
        lastTargetDistance = Double.NaN;
    }

    public int lockedTicks() {
        return lockedTicks;
    }

    public int openingTicks() {
        return openingTicks;
    }

    public boolean terminalBoosted() {
        return terminalBoosted;
    }
}
