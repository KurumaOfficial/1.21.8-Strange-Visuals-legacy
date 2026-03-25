package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Method;

public class ScaledResolution {
    private static final int FALLBACK_WIDTH = 1920;
    private static final int FALLBACK_HEIGHT = 1080;
    private static int lastKnownWidth = FALLBACK_WIDTH;
    private static int lastKnownHeight = FALLBACK_HEIGHT;

    private final double scaledWidthD;
    private final double scaledHeightD;
    private int scaledWidth;
    private int scaledHeight;
    private static int scaleFactor;

    public ScaledResolution(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) {
            this.scaledWidth = lastKnownWidth;
            this.scaledHeight = lastKnownHeight;
            scaleFactor = 1;
            this.scaledWidthD = this.scaledWidth;
            this.scaledHeightD = this.scaledHeight;
            return;
        }

        int windowWidth = mc.getWindow().getWidth();
        int windowHeight = mc.getWindow().getHeight();
        if (mc.getWindow().isMinimized()
                || mc.getWindow().hasZeroWidthOrHeight()
                || windowWidth <= 0
                || windowHeight <= 0) {
            windowWidth = lastKnownWidth;
            windowHeight = lastKnownHeight;
        } else {
            lastKnownWidth = windowWidth;
            lastKnownHeight = windowHeight;
        }

        this.scaledWidth = Math.max(1, windowWidth);
        this.scaledHeight = Math.max(1, windowHeight);
        scaleFactor = 1;

        boolean forceUnicodeFont = false;
        try {
            GameOptions options = mc.options;
            try {
                Method getMethod = GameOptions.class.getMethod("getForceUnicodeFont");
                @SuppressWarnings("unchecked")
                SimpleOption<Boolean> flag = (SimpleOption<Boolean>) getMethod.invoke(options);
                forceUnicodeFont = flag != null && flag.getValue();
            } catch (NoSuchMethodException e) {
                Method forceUnicodeFontMethod = GameOptions.class.getDeclaredMethod("forceUnicodeFont");
                forceUnicodeFontMethod.setAccessible(true);
                @SuppressWarnings("unchecked")
                SimpleOption<Boolean> flag = (SimpleOption<Boolean>) forceUnicodeFontMethod.invoke(options);
                forceUnicodeFont = flag != null && flag.getValue();
            }
        } catch (Exception ignored) {
            // Use the default scaling path when the option is unavailable.
        }

        int maxScaleFactor = 2;
        while (scaleFactor < maxScaleFactor
                && this.scaledWidth / (scaleFactor + 1) >= 320
                && this.scaledHeight / (scaleFactor + 1) >= 240) {
            ++scaleFactor;
        }

        if (forceUnicodeFont && scaleFactor % 2 != 0 && scaleFactor != 1) {
            --scaleFactor;
        }

        this.scaledWidthD = (double) this.scaledWidth / scaleFactor;
        this.scaledHeightD = (double) this.scaledHeight / scaleFactor;
        this.scaledWidth = MathHelper.ceil(this.scaledWidthD);
        this.scaledHeight = MathHelper.ceil(this.scaledHeightD);
    }

    public int getWidth() {
        return this.scaledWidth;
    }

    public int getHeight() {
        return this.scaledHeight;
    }

    public double getScaledWidth_double() {
        return this.scaledWidthD;
    }

    public double getScaledHeight_double() {
        return this.scaledHeightD;
    }

    public static int getScaleFactor() {
        return scaleFactor;
    }
}
