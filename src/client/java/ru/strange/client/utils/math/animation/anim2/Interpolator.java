package ru.strange.client.utils.math.animation.anim2;

public final class Interpolator {

    private Interpolator() {
    }

    public static int lerp(int input, int target, double step) {
        return (int) Math.round(input + step * (target - input));
    }

    public static float lerp(float input, float target, double step) {
        return (float) (input + step * (target - input));
    }

    public static double lerp(double input, double target, double step) {
        return input + step * (target - input);
    }
}
