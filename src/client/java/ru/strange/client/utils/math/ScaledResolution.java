package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.Strange;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ScaledResolution {
    private static final int FALLBACK_WIDTH = 1920;
    private static final int FALLBACK_HEIGHT = 1080;
    private static int lastKnownWidth = FALLBACK_WIDTH;
    private static int lastKnownHeight = FALLBACK_HEIGHT;
    private static boolean loggedUnicodeLookupFailure;

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
            forceUnicodeFont = lookupForceUnicodeFont(mc.options);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException
                 | ClassCastException | SecurityException exception) {
            if (!loggedUnicodeLookupFailure) {
                loggedUnicodeLookupFailure = true;
                Strange.LOGGER.debug("Falling back to default scaled resolution path because forceUnicodeFont lookup failed", exception);
            }
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

    private static boolean lookupForceUnicodeFont(GameOptions options)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        try {
            Method getMethod = GameOptions.class.getMethod("getForceUnicodeFont");
            @SuppressWarnings("unchecked")
            SimpleOption<Boolean> flag = (SimpleOption<Boolean>) getMethod.invoke(options);
            return flag != null && flag.getValue();
        } catch (NoSuchMethodException exception) {
            Method forceUnicodeFontMethod = GameOptions.class.getDeclaredMethod("forceUnicodeFont");
            forceUnicodeFontMethod.setAccessible(true);
            @SuppressWarnings("unchecked")
            SimpleOption<Boolean> flag = (SimpleOption<Boolean>) forceUnicodeFontMethod.invoke(options);
            return flag != null && flag.getValue();
        }
    }
}
