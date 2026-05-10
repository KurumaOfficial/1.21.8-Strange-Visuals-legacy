package ru.strange.client.utils.animation;

/**
 * Collection of high-quality easing curves inspired by cubic bezier presets and
 * physically-plausible interpolations. These can be combined with the
 * spring-based animators to sculpt motion.
 */
public final class Easings {

    public static final EasingFunction EASE_OUT_CUBIC = t -> {
        float p = clamp(t);
        float inv = 1.0f - p;
        return 1.0f - inv * inv * inv;
    };

    public static final EasingFunction EASE_IN_OUT_QUINT = t -> {
        float p = clamp(t);
        if (p < 0.5f) {
            float scaled = p * 2.0f;
            return 0.5f * scaled * scaled * scaled * scaled * scaled;
        }
        float scaled = (p - 0.5f) * 2.0f;
        float inv = 1.0f - scaled;
        return 1.0f - 0.5f * inv * inv * inv * inv * inv;
    };

    public static final EasingFunction SMOOTH_STEP = t -> {
        float p = clamp(t);
        return p * p * (3.0f - 2.0f * p);
    };

    private Easings() {
    }

    private static float clamp(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        if (value >= 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
