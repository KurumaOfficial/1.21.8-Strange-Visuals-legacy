package ru.strange.client.module.impl.other;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Плавная камера",
    description = "Визуальная инерция кадра без влияния на повороты",
        category = Category.Other,
        bind = -1
)
public class SmoothCamera extends Module {

    private static SmoothCamera instance;

    private final SliderSetting speed = new SliderSetting("Плавность", 0.08f, 0.01f, 0.3f, 0.01f, false);
    private final BooleanSetting onlyF5 = new BooleanSetting("Только F5", false);

    private float swayX;
    private float swayY;
    private float roll;
    private float lastYaw;
    private float lastPitch;
    private boolean effectPrimed;
    private boolean lastThirdPerson;
    private boolean lastInverseView;
    private boolean lastFreeLook;

    private double lastPlayerY;
    private boolean wasOnGround;

    public SmoothCamera() {
        instance = this;
        addSettings(speed, onlyF5);
    }

    public static SmoothCamera getInstance() {
        return instance;
    }

    public boolean isVisualActive() {
        return enable;
    }

    public boolean shouldAffectView(boolean thirdPerson) {
        return enable && (!onlyF5.get() || thirdPerson);
    }

    public void updateVisualEffect(float yaw, float pitch, float partialTicks, boolean thirdPerson, boolean inverseView, boolean freeLookActive) {
        boolean modeChanged = thirdPerson != lastThirdPerson
                || inverseView != lastInverseView
                || freeLookActive != lastFreeLook;

        lastThirdPerson = thirdPerson;
        lastInverseView = inverseView;
        lastFreeLook = freeLookActive;

        if (!shouldAffectView(thirdPerson)) {
            resetVisualSmoothing();
            return;
        }

        if (!effectPrimed || modeChanged) {
            snap(yaw, pitch);
            return;
        }

        float clampedPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        float yawDelta = MathHelper.wrapDegrees(yaw - lastYaw);
        float pitchDelta = clampedPitch - lastPitch;
        if (Math.abs(yawDelta) > 45.0f || Math.abs(pitchDelta) > 30.0f) {
            snap(yaw, pitch);
            return;
        }

        swayX = MathHelper.clamp(swayX - yawDelta * 0.0022f, -0.048f, 0.048f);
        swayY = MathHelper.clamp(swayY + pitchDelta * 0.0018f, -0.038f, 0.038f);
        roll = MathHelper.clamp(roll - yawDelta * 0.26f, -3.25f, 3.25f);

        float recovery = resolveRecovery(partialTicks);
        swayX += (0.0f - swayX) * recovery;
        swayY += (0.0f - swayY) * recovery;
        roll += (0.0f - roll) * recovery;

        lastYaw = yaw;
        lastPitch = clampedPitch;
    }

    public void applyWorldTransform(MatrixStack matrices) {
        if (!shouldAffectView(lastThirdPerson)) {
            return;
        }

        matrices.translate(swayX, swayY, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
    }

    public void applyHandTransform(MatrixStack matrices) {
        if (!shouldAffectView(lastThirdPerson)) {
            return;
        }

        matrices.translate(swayX * 1.85f, -swayY * 1.35f, 0.0f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll * 1.75f));
    }

    public void resetVisualSmoothing() {
        effectPrimed = false;
        swayX = 0.0f;
        swayY = 0.0f;
        roll = 0.0f;
        lastThirdPerson = false;
        lastInverseView = false;
        lastFreeLook = false;
        lastPlayerY = mc != null && mc.player != null ? mc.player.getY() : 0.0;
        wasOnGround = true;
    }

    private void snap(float yaw, float pitch) {
        lastYaw = yaw;
        lastPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        swayX = 0.0f;
        swayY = 0.0f;
        roll = 0.0f;
        effectPrimed = true;
    }

    private float resolveRecovery(float partialTicks) {
        float base = Math.max(0.01f, speed.get());
        float frameFactor = Math.max(1.0f, partialTicks * 3.25f);
        return 1.0f - (float) Math.pow(1.0f - base, frameFactor);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        resetVisualSmoothing();
    }

    @Override
    public void onDisable() {
        resetVisualSmoothing();
        super.onDisable();
    }
}
