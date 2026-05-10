package ru.strange.client.utils.animation;

/**
 * Functional contract describing easing curves used to remap linear progress
 * values. Implementations must be pure and return values in the {@code [0, 1]}
 * range for inputs from {@code 0} to {@code 1}.
 */
@FunctionalInterface
public interface EasingFunction {

    float ease(float t);

    static EasingFunction identity() {
        return t -> t;
    }

    default EasingFunction compose(EasingFunction after) {
        if (after == null) {
            return this;
        }
        return t -> after.ease(ease(t));
    }
}
