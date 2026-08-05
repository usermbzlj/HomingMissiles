package cn.yjj.homingmissiles.model;

import org.bukkit.entity.AbstractArrow;

import java.util.UUID;

public final class TrackedArrow {
    private final AbstractArrow arrow;
    private final UUID shooterId;
    private UUID targetId;
    private UUID lockNotifiedTarget;
    private int ageTicks;

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
}
