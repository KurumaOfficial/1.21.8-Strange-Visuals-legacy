package ru.strange.client.utils.other;

import net.minecraft.util.math.MathHelper;
import ru.strange.client.utils.Helper;

public class FreeLookHandler implements Helper {

    private static boolean active;

    private static float freeYaw;
    private static float freePitch;

    private static float lockedYaw;
    private static float lockedPitch;

    public static void setActive(boolean state) {
        if (mc.player == null) {
            active = false;
            return;
        }

        if (state) {
            if (active) return;

            active = true;

            lockedYaw = mc.player.getYaw();
            lockedPitch = mc.player.getPitch();

            freeYaw = lockedYaw;
            freePitch = lockedPitch;
        } else {
            if (!active) return;

            active = false;

            lockedYaw = mc.player.getYaw();
            lockedPitch = mc.player.getPitch();
            freeYaw = lockedYaw;
            freePitch = lockedPitch;
        }
    }

    public static void handleMouse(double dx, double dy) {
        if (!active || mc.player == null) return;

        freeYaw += (float) (dx * 0.15f);
        freePitch += (float) (dy * 0.15f);
        freePitch = MathHelper.clamp(freePitch, -90.0f, 90.0f);
    }

    public static void tick() {
        if (!active || mc.player == null) return;

        boolean forward = mc.options.forwardKey.isPressed();
        boolean back = mc.options.backKey.isPressed();
        boolean left = mc.options.leftKey.isPressed();
        boolean right = mc.options.rightKey.isPressed();

        if (forward || back || left || right) {
            float movementYaw = freeYaw;
            float f = 0f;
            float s = 0f;
            if (forward) f += 1f;
            if (back) f -= 1f;
            if (right) s += 1f;
            if (left) s -= 1f;

            if (f > 0f) {
                if (s > 0f) movementYaw -= 45f;
                else if (s < 0f) movementYaw += 45f;
            } else if (f < 0f) {
                movementYaw += 180f;
                if (s > 0f) movementYaw += 45f;
                else if (s < 0f) movementYaw -= 45f;
            } else if (s > 0f) {
                movementYaw -= 90f;
            } else if (s < 0f) {
                movementYaw += 90f;
            }

            mc.player.setYaw(movementYaw);
        }

        lockedYaw = mc.player.getYaw();
        lockedPitch = mc.player.getPitch();
    }

    public static boolean isActive() {
        return active;
    }

    public static float getFreeYaw() {
        return freeYaw;
    }

    public static float getFreePitch() {
        return freePitch;
    }

    public static float getLockedYaw() {
        return lockedYaw;
    }

    public static float getLockedPitch() {
        return lockedPitch;
    }
}