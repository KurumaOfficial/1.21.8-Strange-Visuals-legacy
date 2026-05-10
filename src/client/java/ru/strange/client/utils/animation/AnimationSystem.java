package ru.strange.client.utils.animation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Central update loop for time-based animations. Maintains a collection of
 * {@link Animated} instances and advances them once per frame. The system is
 * thread-safe for single-threaded game execution and guarantees deterministic
 * updates by clamping the frame delta to a small range.
 */
public final class AnimationSystem {

    private static final float MIN_DELTA_SECONDS = 1.0e-4f;
    private static final float MAX_DELTA_SECONDS = 1.0f / 15.0f; // avoid large jumps on stalls

    private static final AnimationSystem INSTANCE = new AnimationSystem();

    private final Object lock = new Object();
    private final List<Animated> animatedObjects = new ArrayList<>();
    private long lastTickNanos = System.nanoTime();
    private float lastDeltaSeconds = 0f;

    private AnimationSystem() {
    }

    public static AnimationSystem getInstance() {
        return INSTANCE;
    }

    /**
     * Advances the animation system by one frame. The method calculates the
     * elapsed time since the previous tick, clamps the delta to a reasonable
     * range, and updates all registered animation drivers. Drivers that report
     * completion are automatically removed from the update list.
     */
    public void tick() {
        long now = System.nanoTime();
        long elapsed = now - lastTickNanos;
        lastTickNanos = now;
        if (elapsed < 0L) {
            elapsed = 0L;
        }
        float delta = elapsed / 1_000_000_000.0f;
        if (delta < MIN_DELTA_SECONDS) {
            delta = MIN_DELTA_SECONDS;
        } else if (delta > MAX_DELTA_SECONDS) {
            delta = MAX_DELTA_SECONDS;
        }
        lastDeltaSeconds = delta;

        synchronized (lock) {
            if (animatedObjects.isEmpty()) {
                return;
            }
            Iterator<Animated> iterator = animatedObjects.iterator();
            while (iterator.hasNext()) {
                Animated animated = iterator.next();
                boolean keepUpdating = animated.update(delta);
                if (!keepUpdating) {
                    iterator.remove();
                }
            }
        }
    }

    public float getLastDeltaSeconds() {
        return lastDeltaSeconds;
    }

    /**
     * Ensures that the specified animation driver is scheduled for updates.
     * Drivers can safely call this method multiple times without being
     * registered more than once.
     */
    public void ensureRegistered(Animated animated) {
        if (animated == null) {
            return;
        }
        synchronized (lock) {
            if (!animatedObjects.contains(animated)) {
                animatedObjects.add(animated);
            }
        }
    }

    /**
     * Removes an animation driver from the update loop.
     */
    public void unregister(Animated animated) {
        if (animated == null) {
            return;
        }
        synchronized (lock) {
            animatedObjects.remove(animated);
        }
    }

    public interface Animated {
        /**
         * Advances the animation state.
         *
         * @param deltaSeconds duration of the frame in seconds (clamped)
         * @return {@code true} if the animation requires further updates,
         * {@code false} if it has settled and can be removed.
         */
        boolean update(float deltaSeconds);
    }
}
