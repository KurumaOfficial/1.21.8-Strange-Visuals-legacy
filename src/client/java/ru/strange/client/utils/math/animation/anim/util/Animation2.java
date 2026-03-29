package ru.strange.client.utils.math.animation.anim.util;

import ru.strange.client.Strange;

public class Animation2 {

    private long start;
    private double duration;
    private double fromValue;
    private double toValue;
    private double value;
    private double prevValue;
    private Easing easing = Easings.SINE_OUT;
    private boolean debug = false;
    private Runnable finishAction;

    public Animation2 run(double valueTo, double duration) {
        return this.run(valueTo, duration, Easings.SINE_OUT, false);
    }

    public Animation2 run(double valueTo, double duration, Easing easing) {
        return this.run(valueTo, duration, easing, false);
    }

    public Animation2 run(double valueTo, double duration, boolean safe) {
        return this.run(valueTo, duration, Easings.SINE_OUT, safe);
    }

    public Animation2 run(double valueTo, double duration, Easing easing, boolean safe) {
        if (this.check(safe, valueTo)) {
            if (this.isDebug()) {
                Strange.LOGGER.debug("Animation skipped because the target value already matches the current state");
            }
        } else {
            this.setEasing(easing)
                    .setDuration(duration * 1000.0D)
                    .setStart(System.currentTimeMillis())
                    .setFromValue(this.getValue())
                    .setToValue(valueTo);

            if (this.isDebug()) {
                Strange.LOGGER.debug("Animation start: from={}, to={}, durationMs={}", this.getValue(), this.getToValue(), this.getDuration());
            }
        }
        return this;
    }

    public boolean update() {
        this.setPrevValue(this.getValue());
        boolean alive = this.isAlive();

        if (alive) {
            this.setValue(this.interpolate(
                    this.getFromValue(),
                    this.getToValue(),
                    this.getEasing().ease(this.calculatePart())
            ));
        } else {
            this.setStart(0L);
            this.setValue(this.getToValue());

            if (this.finishAction != null) {
                this.finishAction.run();
                this.finishAction = null;
            }
        }
        return alive;
    }

    public boolean isAlive() {
        return !this.isFinished();
    }

    public boolean isFinished() {
        return this.calculatePart() >= 1.0D;
    }

    public double calculatePart() {
        if (duration == 0) return 1.0;
        return (System.currentTimeMillis() - this.getStart()) / this.getDuration();
    }

    public boolean check(boolean safe, double valueTo) {
        return safe && this.isAlive() &&
                (valueTo == this.getFromValue() || valueTo == this.getToValue() || valueTo == this.getValue());
    }

    public double interpolate(double start, double end, double pct) {
        return start + (end - start) * pct;
    }

    public Animation2 setStart(long start) {
        this.start = start;
        return this;
    }

    public Animation2 setDuration(double duration) {
        this.duration = duration;
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

    public Animation2 setValue(double value) {
        this.value = value;
        return this;
    }

    public Animation2 setPrevValue(double prevValue) {
        this.prevValue = prevValue;
        return this;
    }

    public Animation2 setEasing(Easing easing) {
        this.easing = easing;
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

    public float get() {
        return (float) this.getValue();
    }

    public float getPrev() {
        return (float) this.getPrevValue();
    }

    public void set(double value) {
        this.run(value, 0.0000000000001D);
        this.update();
        this.setValue(value);
    }

    // --------- GETTERS ---------

    public long getStart() {
        return start;
    }

    public double getDuration() {
        return duration;
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
}
