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

            mc.player.setYaw(lockedYaw);
            mc.player.setPitch(lockedPitch);
            mc.player.setHeadYaw(lockedYaw);
            mc.player.setBodyYaw(lockedYaw);

            freeYaw = lockedYaw;
            freePitch = lockedPitch;
        }
    }

    public static void handleMouse(double dx, double dy) {
        if (!active || mc.player == null) return;

        freeYaw += (float) (dx * 0.15f);
        freePitch += (float) (dy * 0.15f);
        freePitch = MathHelper.clamp(freePitch, -90.0f, 90.0f);

        lockPlayerRotation();
    }

    public static void tick() {
        if (!active || mc.player == null) return;
        lockPlayerRotation();
    }

    private static void lockPlayerRotation() {
        if (mc.player == null) return;

        mc.player.setYaw(lockedYaw);
        mc.player.setPitch(lockedPitch);
        mc.player.setHeadYaw(lockedYaw);
        mc.player.setBodyYaw(lockedYaw);
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