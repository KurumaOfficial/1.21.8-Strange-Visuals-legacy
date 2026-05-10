package ru.strange.client.utils.animation;

/**
 * Encapsulates the physical properties of a second-order spring. The
 * configuration is immutable and can be shared between multiple spring-driven
 * animations.
 */
public final class SpringConfig {

    private final float frequencyHz;
    private final float dampingRatio;

    private SpringConfig(float frequencyHz, float dampingRatio) {
        if (frequencyHz <= 0.0f) {
            throw new IllegalArgumentException("frequencyHz must be > 0");
        }
        if (dampingRatio <= 0.0f) {
            throw new IllegalArgumentException("dampingRatio must be > 0");
        }
        this.frequencyHz = frequencyHz;
        this.dampingRatio = dampingRatio;
    }

    public static SpringConfig of(float frequencyHz, float dampingRatio) {
        return new SpringConfig(frequencyHz, dampingRatio);
    }

    public float getFrequencyHz() {
        return frequencyHz;
    }

    public float getDampingRatio() {
        return dampingRatio;
    }
}
