package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventHandAnimation;
import ru.strange.client.event.impl.EventRenderItem;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Кастомизировать руки",
        description = "Кастомизация анимации руки",
        category = Category.Interface,
        bind = -1
)
public class SwingAnimation extends Module {

    // Анимация — тип анимации при взмахе
    public static ModeSetting swingMode = new ModeSetting("Анимация",
            "Smooth","Smooth","Swipe","Swipe back","SwipeD", "Down","Spin","Off");

    // Скорость анимации — множитель скорости: 0.5 = вдвое медленнее, 1.0 = стандарт, 2.0 = вдвое быстрее
    public static SliderSetting animSpeed = new SliderSetting("Скорость анимации", 1.0F, 0.5F, 2.0F, 0.05F, false)
            .hidden(() -> swingMode.is("Off"));

    // Размер анимации — масштаб движений: 1.0 = стандарт, выше = драматичнее
    public static SliderSetting animgsize = new SliderSetting("Размер анимации", 1.0F, 0.1F, 3.0F, 0.05F, false)
            .hidden(() -> swingMode.is("Off") || swingMode.is("Spin"));

    // Модель Руки — сдвиг позиции рук
    public static BooleanSetting customhands = new BooleanSetting("Модель Руки", false);

    public static SliderSetting right_x = new SliderSetting("X правая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());
    public static SliderSetting right_y = new SliderSetting("Y правая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());
    public static SliderSetting right_z = new SliderSetting("Z правая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());

    public static SliderSetting lefvt_x = new SliderSetting("X левая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());
    public static SliderSetting lefvt_y = new SliderSetting("Y левая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());
    public static SliderSetting lefvt_z = new SliderSetting("Z левая", 0.0f, -2.0f, 2.0f, 0.01f, false)
            .hidden(() -> !customhands.get());

    public SwingAnimation() {
        addSettings(swingMode, animSpeed, animgsize, customhands, right_x, lefvt_x, right_y, lefvt_y, right_z, lefvt_z);
    }

    @EventInit
    public void onEvent(EventHandAnimation event) {
        if (!enable || swingMode.is("Off") || mc.player == null) {
            return;
        }

        MatrixStack matrix = event.getMatrices();
        float rawProgress = event.getSwingProgress();

        // Скорость: pow(progress, 1/speed) — при speed>1 анимация быстрее, при <1 медленнее
        float speed = animSpeed.get();
        float swingProgress;
        if (rawProgress <= 0.0f || rawProgress >= 1.0f || Math.abs(speed - 1.0f) < 0.01f) {
            swingProgress = rawProgress;
        } else {
            swingProgress = (float) Math.pow(rawProgress, 1.0 / speed);
        }

        Arm renderArm = event.getHand() == Hand.MAIN_HAND
                ? mc.player.getMainArm()
                : mc.player.getMainArm().getOpposite();
        int i = renderArm == Arm.RIGHT ? 1 : -1;

        // Размер анимации — множитель интенсивности
        float sz = animgsize.get();

        float sin1 = (float) Math.sin(swingProgress * Math.PI);
        float sinSqrt = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);

        switch (swingMode.get()) {
            case "Swipe" -> {
                matrix.translate((float) i * 0.67F, -0.32F, -1F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -sz * 30));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            }
            case "Swipe back" -> {
                matrix.translate((float) i * 0.67F, -0.32F, -1F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * sz * 30));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
            }
            case "SwipeD" -> {
                matrix.translate((float) i * 0.67F, -0.32F, -1F);
                matrix.translate(sinSqrt * -sz * 0.1f, 0, sinSqrt * -sz * 0.1f);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSqrt * -sz * 15));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50.0F * i));
            }
            case "Down" -> {
                matrix.translate((float) i * 0.67F, -0.32F, -1F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -sz * 30));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
            }
            case "Spin" -> {
                matrix.translate(i * 0.56F, -0.42F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(swingProgress * 360.0f));
                matrix.translate(0, -0.1, 0);
            }
            case "Smooth" -> {
                matrix.translate(i * 0.56F, -0.42F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + sin1 * -sz * 10)));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * sin1 * -sz * 7));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -sz * 30));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
                matrix.translate(0, -0.1, 0);
            }
        }
        event.cancel();
    }

    @EventInit
    public void onEvent(EventRenderItem event) {
        if (mc.player == null) {
            return;
        }

        boolean rightHand = event.getHand() == Hand.MAIN_HAND
                ? mc.player.getMainArm() == Arm.RIGHT
                : mc.player.getMainArm() != Arm.RIGHT;
        var matrix = event.getMatrix();
        if (customhands.get()) {
            if (rightHand) {
                matrix.translate(right_x.get(), right_y.get(), right_z.get());
            } else {
                matrix.translate(lefvt_x.get(), lefvt_y.get(), lefvt_z.get());
            }
        }
    }
}