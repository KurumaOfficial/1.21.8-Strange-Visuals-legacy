package ru.strange.client.event.impl;

import net.minecraft.entity.Entity;
import ru.strange.client.event.Event;

public class EventAttack extends Event {
    public enum Phase {
        ATTEMPT,
        CONFIRMED
    }

    private final Phase phase;
    private Entity target;
    private final float healthBefore;
    private final float healthAfter;
    private final float cooldownProgress;
    private final boolean critical;
    private final boolean sweeping;
    private final boolean damaging;
    private final boolean killed;

    public EventAttack(Entity target, Phase phase) {
        this(target, phase, 0.0f, 0.0f, 0.0f, false, false, false, false);
    }

    public EventAttack(Entity target, Phase phase, float healthBefore, float healthAfter, float cooldownProgress,
                       boolean critical, boolean sweeping, boolean damaging, boolean killed) {
        this.target = target;
        this.phase = phase;
        this.healthBefore = healthBefore;
        this.healthAfter = healthAfter;
        this.cooldownProgress = cooldownProgress;
        this.critical = critical;
        this.sweeping = sweeping;
        this.damaging = damaging;
        this.killed = killed;
    }

    public static EventAttack attempt(Entity target) {
        return new EventAttack(target, Phase.ATTEMPT);
    }

    public static EventAttack confirmed(Entity target, float healthBefore, float healthAfter, float cooldownProgress,
                                        boolean critical, boolean sweeping, boolean damaging, boolean killed) {
        return new EventAttack(target, Phase.CONFIRMED, healthBefore, healthAfter, cooldownProgress, critical, sweeping, damaging, killed);
    }

    public Entity getTarget() {
        return target;
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    public Phase getPhase() {
        return phase;
    }

    public boolean isAttempt() {
        return phase == Phase.ATTEMPT;
    }

    public boolean isConfirmed() {
        return phase == Phase.CONFIRMED;
    }

    public float getHealthBefore() {
        return healthBefore;
    }

    public float getHealthAfter() {
        return healthAfter;
    }

    public float getCooldownProgress() {
        return cooldownProgress;
    }

    public boolean isCritical() {
        return critical;
    }

    public boolean isSweeping() {
        return sweeping;
    }

    public boolean isDamaging() {
        return damaging;
    }

    public boolean isKilled() {
        return killed;
    }
}
