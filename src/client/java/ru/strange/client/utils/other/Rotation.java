package ru.strange.client.utils.other;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.utils.Helper;

public class Rotation implements Helper {
    private float yaw, pitch;

    public Rotation(Entity entity) {
        yaw = entity.getYaw();
        pitch = entity.getPitch();
    }

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public double getDelta(Rotation targetRotation) {
        double yawDelta = net.minecraft.util.math.MathHelper.wrapDegrees(targetRotation.getYaw() - yaw);
        double pitchDelta = MathHelper.wrapDegrees(targetRotation.getPitch() - pitch);

        return Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
    }

    public static Rotation getReal() {
        return new Rotation(FreeLookHandler.getFreeYaw(), FreeLookHandler.getFreePitch());
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}