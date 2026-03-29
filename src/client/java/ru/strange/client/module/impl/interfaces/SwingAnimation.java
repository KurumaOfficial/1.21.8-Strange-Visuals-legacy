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

    public static ModeSetting swingMode = new ModeSetting("Анимация",
            "Smooth","Smooth","Swipe","Swipe back","SwipeD", "Down","Spin","Off");

    public static SliderSetting animSpeed = new SliderSetting("Скорость анимации",1,0.1F,3,0.1F,false);

    public static SliderSetting animgsize = new SliderSetting("Размер анимации", 3.7F, 1, 10, 0.1F,false)
            .hidden(() -> swingMode.is("Off"));

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
        addSettings(swingMode,animSpeed,animgsize, customhands, right_x, lefvt_x, right_y, lefvt_y, right_z, lefvt_z);
    }

    @EventInit
    public void onEvent(EventHandAnimation event) {
        if (!enable || swingMode.is("Off")) {
            return;
        }

        if (!event.getHand().equals(Hand.MAIN_HAND)) {
            return;
        }

        String swingModeValue = this.swingMode.get();
        if (swingModeValue.equals("Off")) {
            return;
        }

        if (event.getHand().equals(Hand.MAIN_HAND)) {
            MatrixStack matrix = event.getMatrices();
            float swingProgress = event.getSwingProgress();
            int i = mc.player.getMainArm().equals(Arm.RIGHT) ? 1 : -1;
            float sin1 =  (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
            float sin2 =  (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
            float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5F);

            float sinD = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
            float sinS = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
            switch (swingMode.get()) {
                case "Swipe" -> {
                    matrix.translate((float) i * 0.67F, -0.32F, -1F );
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                    matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));

                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin1) * -animgsize.get() * 10));
                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                }
                case "Swipe back" -> {
                    matrix.translate((float) i * 0.67F, -0.32F, -1F );
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                    matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));

                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin1) * animgsize.get() * 10));
                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));
                }
                case "SwipeD" -> {


                    matrix.translate((float) i * 0.67F, -0.32F, -1F );

                    matrix.translate(sinS * -animgsize.get() / 35, 0, sinS * -animgsize.get() / 35);

                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25));

                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinS * -animgsize.get() * 5));

                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F));
                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50.0F));
                }
                case "Down" -> {

                    matrix.translate((float) i * 0.67F, -0.32F, -1F );
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
                    matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30 * i));

                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin1) * -animgsize.get() * 10));
                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
                }
                case "Spin" -> {
                    matrix.translate(i * 0.56F, -0.42F, -0.72F);

                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-0 + swingProgress * 360));

                    matrix.translate(0, -0.1, 0);
                }
                case "Smooth" -> {
                    matrix.translate(i * 0.56F, -0.42F, -0.72F);
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0F + sin1 * -animgsize.get() * 3)));
                    matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) i * sin2 * -animgsize.get() * 2));
                    matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -animgsize.get() * 10));
                    matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) i * -45.0F));
                    matrix.translate(0, -0.1, 0);
                }
            }
            event.cancel();
        }

    }

    @EventInit
    public void onEvent(EventRenderItem event) {
        boolean rightHand = event.isRightHand();
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