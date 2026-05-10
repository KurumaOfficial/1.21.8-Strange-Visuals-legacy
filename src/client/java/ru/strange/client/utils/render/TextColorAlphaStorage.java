package ru.strange.client.utils.render;

/**
 * Thread-local storage for pending TextColor alpha values.
 * Used to capture the full color value (including alpha) during TextColor construction.
 */
public class TextColorAlphaStorage {
    private static final ThreadLocal<Integer> PENDING_COLOR = new ThreadLocal<>();

    /**
     * Sets the pending color value for the current thread.
     * @param rgb The full ARGB color value to store
     */
    public static void setPending(int rgb) {
        PENDING_COLOR.set(rgb);
    }

    /**
     * Gets the pending color value for the current thread.
     * @return The pending color value, or 0 if not set
     */
    public static int getPending() {
        Integer pending = PENDING_COLOR.get();
        return pending != null ? pending : 0;
    }

    /**
     * Clears the pending color value for the current thread.
     */
    public static void clearPending() {
        PENDING_COLOR.remove();
    }
}
