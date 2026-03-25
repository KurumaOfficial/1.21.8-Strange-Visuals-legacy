package ru.strange.client.utils.math;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.GL11;

public class ScrollUtil {
    public static MinecraftClient mc = MinecraftClient.getInstance();
    private static ScaledResolution sr;
    private static Window mw;
    
    public static ScaledResolution getScaledResolution() {
        if (sr == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.getWindow() != null) {
                sr = new ScaledResolution(client);
            }
        }
        return sr;
    }
    
    public static Window getWindow() {
        if (mw == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                mw = client.getWindow();
            }
        }
        return mw;
    }
    
    private float target, scroll, max;
    private float speed = 8F;
    private boolean enabled;


    public ScrollUtil() {
        setEnabled(true);
    }

    public void update() {
        scroll = lerp(scroll, target, speed / 100F);
    }
    
    public void handleScroll(double scrollY) {
        if (!enabled) return;
        
        float wheel = (float) scrollY * (speed * 10F);
        float stretch = 0;
        // target: 0 = верх контента, max (отрицательное) = низ контента
        target = Math.min(Math.max(target + (wheel / 2F), max - stretch), stretch);
    }

    public <T extends Number> T lerp(T input, T target, double step) {
        double start = input.doubleValue();
        double end = target.doubleValue();
        double result = start + step * (end - start);

        if (input instanceof Integer) {
            return (T) Integer.valueOf((int) Math.round(result));
        } else if (input instanceof Double) {
            return (T) Double.valueOf(result);
        } else if (input instanceof Float) {
            return (T) Float.valueOf((float) result);
        } else if (input instanceof Long) {
            return (T) Long.valueOf(Math.round(result));
        } else if (input instanceof Short) {
            return (T) Short.valueOf((short) Math.round(result));
        } else if (input instanceof Byte) {
            return (T) Byte.valueOf((byte) Math.round(result));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + input.getClass().getSimpleName());
        }
    }
    public static void enable() {
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    public static void disable() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    public static void scissor(Window window, double x, double y, double width, double height) {
        if (x + width == x || y + height == y || x < 0 || y + height < 0) return;
        final double scaleFactor = window.getScaleFactor();
        GL11.glScissor((int) Math.round(x * scaleFactor), (int) Math.round((window.getScaledHeight() - (y + height)) * scaleFactor), (int) Math.round(width * scaleFactor), (int) Math.round(height * scaleFactor));
    }
    public void reset() {
        this.scroll = 0F;
        this.target = 0F;
    }

    public void setMax(float max, float height) {
        float newMax = Math.min(0.0f, -max + height);
        this.max = newMax;
        this.target = Math.min(Math.max(target, newMax), 0.0f);
        this.scroll = Math.min(Math.max(scroll, newMax), 0.0f);

        if (newMax == 0.0f) {
            reset();
        }
    }

    public float getTarget() {
        return target;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    public float getScroll() {
        return scroll;
    }

    public void setScroll(float scroll) {
        this.scroll = scroll;
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
