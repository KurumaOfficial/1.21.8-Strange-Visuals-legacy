package ru.strange.client.utils.other;

import ru.strange.client.event.EventInit;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.utils.Helper;

import static net.minecraft.util.math.MathHelper.clamp;
import static net.minecraft.util.math.MathHelper.wrapDegrees;

public class RotationHandler implements Helper {

    public RotationHandler() {
        EventManager.register(this);
    }

    private static RotationTask currentTask = RotationTask.IDLE;

    private static float currentTurnSpeed;
    private static int currentPriority;
    private static int currentTimeout;

    private static int idleTicks;


    @EventInit
    public void onUpdate(EventUpdate event) {
        idleTicks++;

        if (currentTask == RotationTask.AIM && idleTicks > currentTimeout) {
            currentTask = RotationTask.RESET;
        }

        if (currentTask == RotationTask.RESET) {
            if (updateRotation(Rotation.getReal(), currentTurnSpeed)) {
                currentTask = RotationTask.IDLE;
                currentPriority = 0;
                FreeLookHandler.setActive(false);
            }
        }
    }

    public static void update(Rotation rotation, float turnSpeed, int timeout, int priority) {
        if (currentPriority > priority) {
            return;
        }

        if (currentTask == RotationTask.IDLE) {
            FreeLookHandler.setActive(true);
        }

        currentTurnSpeed = turnSpeed;
        currentTimeout = timeout;
        currentPriority = priority;

        currentTask = RotationTask.AIM;

        updateRotation(rotation, turnSpeed);
    }

    private static boolean updateRotation(Rotation rotation, float turnSpeed) {
        Rotation currentRotation = new Rotation(mc.player);

        float yawDelta = wrapDegrees(rotation.getYaw() - currentRotation.getYaw());
        float pitchDelta = rotation.getPitch() - currentRotation.getPitch();

        float totalDelta = Math.abs(yawDelta) + Math.abs(pitchDelta);

        float yawSpeed = (totalDelta == 0) ? 0 : Math.abs(yawDelta / totalDelta) * turnSpeed;
        float pitchSpeed = (totalDelta == 0) ? 0 : Math.abs(pitchDelta / totalDelta) * turnSpeed;

        mc.player.yaw += GCDUtil.getSensitivity(clamp(yawDelta, -yawSpeed, yawSpeed));
        mc.player.setPitch(clamp(mc.player.getPitch() + GCDUtil.getSensitivity(clamp(pitchDelta, -pitchSpeed, pitchSpeed)), -90, 90));

        Rotation finalRotation = new Rotation(mc.player);

        idleTicks = 0;

        return finalRotation.getDelta(rotation) < currentTurnSpeed;
    }

    private enum RotationTask {
        AIM,
        RESET,
        IDLE
    }

    public static RotationTask getCurrentTask() {
        return currentTask;
    }

    public static void setCurrentTask(RotationTask currentTask) {
        RotationHandler.currentTask = currentTask;
    }

    public static float getCurrentTurnSpeed() {
        return currentTurnSpeed;
    }

    public static void setCurrentTurnSpeed(float currentTurnSpeed) {
        RotationHandler.currentTurnSpeed = currentTurnSpeed;
    }

    public static int getCurrentPriority() {
        return currentPriority;
    }

    public static void setCurrentPriority(int currentPriority) {
        RotationHandler.currentPriority = currentPriority;
    }

    public static int getCurrentTimeout() {
        return currentTimeout;
    }

    public static void setCurrentTimeout(int currentTimeout) {
        RotationHandler.currentTimeout = currentTimeout;
    }

    public static int getIdleTicks() {
        return idleTicks;
    }

    public static void setIdleTicks(int idleTicks) {
        RotationHandler.idleTicks = idleTicks;
    }

}
