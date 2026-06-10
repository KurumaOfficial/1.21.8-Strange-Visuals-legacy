package ru.strange.client.utils.math.animation.anim.util;

import ru.strange.client.Strange;

public class Animation2 {

    private static final double EPSILON = 1.0E-4D;

    private long startTime;
    private double durationMs = 1.0D;

    private double fromValue;
    private double toValue;

    private double value;
    private double prevValue;

    private Easing easing = Easings.CUBIC_OUT;
    private boolean debug;
    private Runnable finishAction;

    public Animation2() {
        this(0.0D);
    }

    public Animation2(double value) {
        this.value = value;
        this.prevValue = value;
        this.fromValue = value;
        this.toValue = value;
        this.startTime = 0L;
    }

    public Animation2 run(double target, double durationSeconds) {
        return this.run(target, durationSeconds, Easings.CUBIC_OUT, false);
    }

    public Animation2 run(double target, double durationSeconds, Easing easing) {
        return this.run(target, durationSeconds, easing, false);
    }

    public Animation2 run(double target, double durationSeconds, boolean safe) {
        return this.run(target, durationSeconds, Easings.CUBIC_OUT, safe);
    }

    public Animation2 run(double target, double durationSeconds, Easing easing, boolean safe) {
        this.updateState();

        if (safe && nearlyEquals(this.toValue, target)) {
            if (debug) {
                Strange.LOGGER.debug("Animation skipped: target already equals current target ({})", target);
            }
            return this;
        }

        if (nearlyEquals(durationSeconds, 0.0D)) {
            return this.set(target);
        }

        this.fromValue = this.value;
        this.toValue = target;
        this.durationMs = Math.max(1.0D, durationSeconds * 1000.0D);
        this.easing = easing == null ? Easings.CUBIC_OUT : easing;
        this.startTime = System.currentTimeMillis();

        if (debug) {
            Strange.LOGGER.debug(
                    "Animation start: from={}, to={}, durationMs={}",
                    this.fromValue, this.toValue, this.durationMs
            );
        }

        return this;
    }

    public boolean update() {
        this.prevValue = this.value;

        if (!this.isAlive()) {
            this.value = this.toValue;
            this.startTime = 0L;

            if (this.finishAction != null) {
                Runnable action = this.finishAction;
                this.finishAction = null;
                action.run();
            }

            return false;
        }

        this.value = interpolate(this.fromValue, this.toValue, this.getEasedProgress());
        return true;
    }

    private void updateState() {
        if (this.startTime == 0L) return;

        double progress = getProgress();
        if (progress >= 1.0D) {
            this.value = this.toValue;
            this.startTime = 0L;
            return;
        }

        this.value = interpolate(this.fromValue, this.toValue, this.getEasedProgress());
    }

    public Animation2 set(double value) {
        this.startTime = 0L;
        this.durationMs = 1.0D;
        this.fromValue = value;
        this.toValue = value;
        this.prevValue = this.value;
        this.value = value;
        return this;
    }

    public Animation2 stop() {
        this.updateState();
        this.startTime = 0L;
        this.fromValue = this.value;
        this.toValue = this.value;
        return this;
    }

    public Animation2 reset(double value) {
        this.finishAction = null;
        return this.set(value);
    }

    public boolean isAlive() {
        return this.startTime != 0L && !this.isFinished();
    }

    public boolean isFinished() {
        return this.getProgress() >= 1.0D;
    }

    public double getProgress() {
        if (this.startTime == 0L) return 1.0D;
        if (this.durationMs <= 0.0D) return 1.0D;

        double part = (System.currentTimeMillis() - this.startTime) / this.durationMs;
        return clamp01(part);
    }

    public double getEasedProgress() {
        return clamp01(this.easing.ease(this.getProgress()));
    }

    public double interpolate(double start, double end, double pct) {
        return start + (end - start) * pct;
    }

    public double getDelta() {
        return this.value - this.prevValue;
    }

    public float get() {
        return (float) this.value;
    }

    public float getPrev() {
        return (float) this.prevValue;
    }

    public long getStartTime() {
        return startTime;
    }

    public double getDurationMs() {
        return durationMs;
    }

    public double getFromValue() {
        return fromValue;
    }

    public double getToValue() {
        return toValue;
    }

    public double getValue() {
        return value;
    }

    public double getPrevValue() {
        return prevValue;
    }

    public Easing getEasing() {
        return easing;
    }

    public boolean isDebug() {
        return debug;
    }

    public Animation2 setStartTime(long startTime) {
        this.startTime = startTime;
        return this;
    }

    public Animation2 setDurationMs(double durationMs) {
        this.durationMs = Math.max(1.0D, durationMs);
        return this;
    }

    public Animation2 setFromValue(double fromValue) {
        this.fromValue = fromValue;
        return this;
    }

    public Animation2 setToValue(double toValue) {
        this.toValue = toValue;
        return this;
    }

    public Animation2 setValueDirect(double value) {
        this.value = value;
        return this;
    }

    public Animation2 setPrevValue(double prevValue) {
        this.prevValue = prevValue;
        return this;
    }

    public Animation2 setEasing(Easing easing) {
        this.easing = easing == null ? Easings.CUBIC_OUT : easing;
        return this;
    }

    public Animation2 setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    public Animation2 onFinished(Runnable action) {
        this.finishAction = action;
        return this;
    }

    private static boolean nearlyEquals(double a, double b) {
        return Math.abs(a - b) <= EPSILON;
    }

    private static double clamp01(double v) {
        return Math.max(0.0D, Math.min(1.0D, v));
    }
}