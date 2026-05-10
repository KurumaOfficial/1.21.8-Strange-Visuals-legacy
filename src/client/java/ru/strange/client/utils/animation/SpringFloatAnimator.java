package ru.strange.client.utils.animation;

/**
 * Second-order spring solver for animating scalar properties. The driver works
 * in continuous time, supports range clamping, and automatically removes itself
 * from the {@link AnimationSystem} once it settles.
 */
public final class SpringFloatAnimator implements AnimationSystem.Animated {

    private static final float MIN_DELTA_SECONDS = 1.0e-4f;
    private static final float MAX_DELTA_SECONDS = 1.0f / 60.0f;

    private final AnimationSystem animationSystem;
    private final SpringConfig config;
    private final float minValue;
    private final float maxValue;
    private final float positionTolerance;
    private final float velocityTolerance;

    private float value;
    private float target;
    private float velocity;
    private EasingFunction outputTransform = EasingFunction.identity();

    public SpringFloatAnimator(AnimationSystem animationSystem,
                               SpringConfig config,
                               float initialValue,
                               float minValue,
                               float maxValue,
                               float positionTolerance,
                               float velocityTolerance) {
        if (animationSystem == null) {
            throw new IllegalArgumentException("animationSystem must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue must be <= maxValue");
        }
        if (positionTolerance <= 0.0f || velocityTolerance <= 0.0f) {
            throw new IllegalArgumentException("tolerances must be > 0");
        }
        this.animationSystem = animationSystem;
        this.config = config;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.positionTolerance = positionTolerance;
        this.velocityTolerance = velocityTolerance;
        float clamped = clamp(initialValue);
        this.value = clamped;
        this.target = clamped;
        this.velocity = 0.0f;
    }

    public void setOutputTransform(EasingFunction transform) {
        this.outputTransform = transform == null ? EasingFunction.identity() : transform;
    }

    public void snapTo(float newValue) {
        float clamped = clamp(newValue);
        this.value = clamped;
        this.target = clamped;
        this.velocity = 0.0f;
        animationSystem.unregister(this);
    }

    public void setTarget(float targetValue) {
        float clamped = clamp(targetValue);
        if (Math.abs(clamped - target) <= positionTolerance * 0.25f) {
            target = clamped;
            if (isSettled()) {
                snapTo(clamped);
            }
            return;
        }
        this.target = clamped;
        animationSystem.ensureRegistered(this);
    }

    public float getValue() {
        float normalized = 0.0f;
        float range = maxValue - minValue;
        if (range > 0.0f) {
            normalized = (value - minValue) / range;
        }
        float eased = outputTransform.ease(clamp01(normalized));
        return minValue + eased * range;
    }

    public float getRawValue() {
        return value;
    }

    public float getTarget() {
        return target;
    }

    public boolean isSettled() {
        float displacement = Math.abs(target - value);
        return displacement <= positionTolerance && Math.abs(velocity) <= velocityTolerance;
    }

    @Override
    public boolean update(float deltaSeconds) {
        float dt = deltaSeconds;
        if (dt < MIN_DELTA_SECONDS) {
            dt = MIN_DELTA_SECONDS;
        } else if (dt > MAX_DELTA_SECONDS) {
            dt = MAX_DELTA_SECONDS;
        }

        float omega = (float) (2.0 * Math.PI * config.getFrequencyHz());
        float damping = 2.0f * config.getDampingRatio() * omega;
        float stiffness = omega * omega;

        float previousDisplacement = value - target;
        float acceleration = -stiffness * previousDisplacement - damping * velocity;

        velocity += acceleration * dt;
        value += velocity * dt;
        if (value < minValue) {
            value = minValue;
            velocity = 0.0f;
            // Останавливаем анимацию при достижении minValue
            return false;
        } else if (value > maxValue) {
            value = maxValue;
            velocity = 0.0f;
            // Останавливаем анимацию при достижении maxValue
            return false;
        }

        float currentDisplacement = value - target;
        if ((previousDisplacement > 0.0f && currentDisplacement < 0.0f)
                || (previousDisplacement < 0.0f && currentDisplacement > 0.0f)) {
            value = target;
            velocity = 0.0f;
            return false;
        }

        if (isSettled()) {
            value = target;
            velocity = 0.0f;
            return false;
        }
        return true;
    }

    private float clamp(float candidate) {
        if (candidate <= minValue) {
            return minValue;
        }
        if (candidate >= maxValue) {
            return maxValue;
        }
        return candidate;
    }

    private static float clamp01(float value) {
        if (value <= 0.0f) {
            return 0.0f;
        }
        if (value >= 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
