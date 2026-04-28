package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.math.animation.anim.util.Animation2;
import ru.strange.client.utils.math.animation.anim.util.Easing;
import ru.strange.client.utils.math.animation.anim.util.Easings;

@IModule(
        name = "Плавный интерфейс",
        description = "Плавная анимация открытия интерфейсов",
        category = Category.Interface,
        bind = -1
)
public class BetterMinecraft extends Module {

    private static BetterMinecraft instance;

    private final BooleanSetting smoothTab = new BooleanSetting("Плавный таб", true);
    private final SliderSetting tabSpeed = new SliderSetting("Скорость таба", 430.0f, 180.0f, 900.0f, 10.0f, false);

    private final BooleanSetting smoothCamera = new BooleanSetting("Плавная камера", true);
    private final SliderSetting cameraSmoothness = new SliderSetting("Сглаживание камеры", 0.22f, 0.05f, 0.75f, 0.01f, true);

    private final BooleanSetting smoothChat = new BooleanSetting("Плавный чат", true);
    private final SliderSetting chatSpeed = new SliderSetting("Скорость чата", 400.0f, 160.0f, 900.0f, 10.0f, false);

    private final BooleanSetting smoothScreens = new BooleanSetting("Плавные экраны", true);
    private final SliderSetting screenSpeed = new SliderSetting("Скорость экранов", 460.0f, 180.0f, 950.0f, 10.0f, false);

    private final Animation2 tabAnimation = new Animation2(0.0);
    private final Animation2 chatAnimation = new Animation2(0.0);
    private final Animation2 pauseMenuAnimation = new Animation2(0.0);
    private final Animation2 containerAnimation = new Animation2(0.0);
    private final Animation2 perspectiveAnimation = new Animation2(0.0);

    private boolean tabPressed = false;

    private boolean cameraPrimed = false;
    private float smoothedCameraYaw;
    private float smoothedCameraPitch;
    private Perspective lastPerspective = Perspective.FIRST_PERSON;
    private boolean lastThirdPerson;
    private boolean lastInverseView;
    private boolean lastFreeLook;

    public BetterMinecraft() {
        addSettings(
                smoothTab, tabSpeed,
                smoothCamera, cameraSmoothness,
                smoothChat, chatSpeed,
                smoothScreens, screenSpeed
        );
        instance = this;
        resetAnimations();
    }

    public static BetterMinecraft getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        resetAnimations();
        tabPressed = false;
        resetCameraSmoothing();
        lastPerspective = Perspective.FIRST_PERSON;
        super.onDisable();
    }

    private void resetAnimations() {
        tabAnimation.set(0.0);
        chatAnimation.set(0.0);
        pauseMenuAnimation.set(0.0);
        containerAnimation.set(0.0);
        perspectiveAnimation.set(0.0);
    }

    public boolean isSmoothTab() {
        return enable && smoothTab.get();
    }

    public boolean isSmoothChat() {
        return enable && smoothChat.get();
    }

    public boolean isSmoothCamera() {
        return enable && smoothCamera.get();
    }

    public boolean isSmoothScreens() {
        return enable && smoothScreens.get();
    }

    private double tabSeconds() {
        return Math.max(0.08, tabSpeed.get() / 1000.0);
    }

    private double chatSeconds() {
        return Math.max(0.08, chatSpeed.get() / 1000.0);
    }

    private double screenSeconds() {
        return Math.max(0.08, screenSpeed.get() / 1000.0);
    }

    private double pauseMenuSeconds() {
        return Math.max(0.12, screenSeconds() * 1.08);
    }

    private double containerSeconds() {
        return Math.max(0.10, screenSeconds() * 0.82);
    }

    public void clientTick() {
        if (!enable) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        Perspective currentPerspective = mc.options.getPerspective();
        if (mc.player == null) {
            resetCameraSmoothing();
            lastPerspective = currentPerspective;
        } else if (currentPerspective != lastPerspective) {
            resetCameraSmoothing();
            lastPerspective = currentPerspective;
        }

        syncScreenAnimations(mc.currentScreen);
        handleTab(mc);
        syncPerspectiveAnimation(currentPerspective);
    }

    private void resetCameraSmoothing() {
        cameraPrimed = false;
        lastThirdPerson = false;
        lastInverseView = false;
        lastFreeLook = false;
    }

    private void syncScreenAnimations(Screen current) {
        if (isSmoothChat()) {
            boolean chatVisible = current instanceof ChatScreen;
            animateTowards(chatAnimation, chatVisible ? 1.0 : 0.0, chatVisible ? chatSeconds() : Math.max(0.18, chatSeconds() * 0.78), chatVisible ? Easings.EXPO_OUT : Easings.QUART_OUT);
        }

        if (isSmoothScreens()) {
            boolean pauseVisible = current instanceof GameMenuScreen;
            boolean containerVisible = current instanceof HandledScreen;
            animateTowards(pauseMenuAnimation, pauseVisible ? 1.0 : 0.0, pauseVisible ? pauseMenuSeconds() : Math.max(0.22, pauseMenuSeconds() * 0.74), pauseVisible ? Easings.BACK_OUT : Easings.CUBIC_OUT);
            animateTowards(containerAnimation, containerVisible ? 1.0 : 0.0, containerVisible ? containerSeconds() : Math.max(0.18, containerSeconds() * 0.72), containerVisible ? Easings.QUINT_OUT : Easings.CUBIC_OUT);
        }
    }

    private void handleTab(MinecraftClient mc) {
        tabPressed = mc.options.playerListKey.isPressed();
        if (!isSmoothTab()) {
            return;
        }

        animateTowards(tabAnimation,
                tabPressed ? 1.0 : 0.0,
                tabPressed ? Math.max(0.22, tabSeconds()) : Math.max(0.18, tabSeconds() * 0.70),
                tabPressed ? Easings.EXPO_OUT : Easings.QUAD_OUT);
    }

    private void syncPerspectiveAnimation(Perspective perspective) {
        if (!isSmoothCamera()) {
            perspectiveAnimation.set(perspective == Perspective.FIRST_PERSON ? 0.0 : 1.0);
            return;
        }

        boolean firstPerson = perspective == Perspective.FIRST_PERSON;
        animateTowards(perspectiveAnimation,
                firstPerson ? 0.0 : 1.0,
                firstPerson ? 0.18 : 0.34,
                firstPerson ? Easings.CUBIC_OUT : Easings.CIRC_OUT);
    }

    private void animateTowards(Animation2 animation, double target, double durationSeconds, Easing easing) {
        animation.update();
        if (Math.abs(animation.getToValue() - target) <= 1.0E-4D && (animation.isAlive() || Math.abs(animation.getValue() - target) <= 1.0E-4D)) {
            return;
        }

        animation.run(target, Math.max(0.08, durationSeconds), easing, false);
    }

    public double getTabProgress() {
        if (!isSmoothTab()) return 1.0;
        tabAnimation.update();
        return tabAnimation.getValue();
    }

    public boolean shouldRenderTabClosingFrame() {
        if (!isSmoothTab()) {
            return false;
        }

        double progress = getTabProgress();
        return !tabPressed && progress > 0.01D;
    }

    public double getChatProgress() {
        if (!isSmoothChat()) return 1.0;
        chatAnimation.update();
        return chatAnimation.getValue();
    }

    public double getPauseMenuProgress() {
        if (!isSmoothScreens()) return 1.0;
        pauseMenuAnimation.update();
        return pauseMenuAnimation.getValue();
    }

    public double getContainerProgress() {
        if (!isSmoothScreens()) return 1.0;
        containerAnimation.update();
        return containerAnimation.getValue();
    }

    public void updateCameraRotation(float yaw, float pitch, float partialTicks, boolean thirdPerson, boolean inverseView, boolean freeLookActive) {
        boolean modeChanged = thirdPerson != lastThirdPerson
                || inverseView != lastInverseView
                || freeLookActive != lastFreeLook;

        lastThirdPerson = thirdPerson;
        lastInverseView = inverseView;
        lastFreeLook = freeLookActive;

        if (!isSmoothCamera()) {
            snapCameraRotation(yaw, pitch);
            return;
        }

        if (!cameraPrimed || modeChanged) {
            snapCameraRotation(yaw, pitch);
            return;
        }

        float yawDelta = MathHelper.wrapDegrees(yaw - smoothedCameraYaw);
        if (Math.abs(yawDelta) > 120.0f) {
            snapCameraRotation(yaw, pitch);
            return;
        }

        float factor = resolveCameraSmoothing(partialTicks);
        smoothedCameraYaw += yawDelta * factor;
        smoothedCameraPitch += (pitch - smoothedCameraPitch) * factor;
        smoothedCameraPitch = MathHelper.clamp(smoothedCameraPitch, -90.0f, 90.0f);
    }

    private void snapCameraRotation(float yaw, float pitch) {
        smoothedCameraYaw = yaw;
        smoothedCameraPitch = MathHelper.clamp(pitch, -90.0f, 90.0f);
        cameraPrimed = true;
    }

    public float getSmoothedCameraYaw() {
        return smoothedCameraYaw;
    }

    public float getSmoothedCameraPitch() {
        return smoothedCameraPitch;
    }

    public float getThirdPersonDistanceScale() {
        if (!isSmoothCamera()) {
            return 1.0f;
        }

        perspectiveAnimation.update();
        return MathHelper.clamp((float) perspectiveAnimation.getValue(), 0.0f, 1.0f);
    }

    private float resolveCameraSmoothing(float partialTicks) {
        float base = Math.max(0.05f, cameraSmoothness.get());
        float frameFactor = Math.max(1.0f, partialTicks * 3.25f);
        return 1.0f - (float) Math.pow(1.0f - base, frameFactor);
    }
}