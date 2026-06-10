package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.combat.CombatUtil;
import ru.strange.client.utils.math.StopWatch;

import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "Тейп маус",
        description = "более аккуратный автоклик по цели",
        category = Category.Utilities,
        bind = -1
)
public class TapeMouse extends Module {

    private final SliderSetting delay = new SliderSetting("Задержка", 1000, 100, 5000, 100, false);
    private final SliderSetting jitter = new SliderSetting("Джиттер", 15, 0, 45, 1, false);
    private final BooleanSetting onlyWhileAttackHeld = new BooleanSetting("Только с ЛКМ", true);
    private final BooleanSetting respectCooldown = new BooleanSetting("Синхрон с КД", true);
    private final SliderSetting minCooldown = new SliderSetting("Мин. КД", 0.92f, 0.5f, 1.0f, 0.01f, false).hidden(() -> !respectCooldown.get());
    private final SliderSetting range = new SliderSetting("Дистанция", 3.1f, 2.5f, 6.0f, 0.1f, false);

    private final StopWatch timerUtil = new StopWatch();
    private long nextDelayMs = 0L;

    public TapeMouse() {
        addSettings(delay, jitter, onlyWhileAttackHeld, respectCooldown, minCooldown, range);
    }

    @Override
    public void toggle() {
        super.toggle();
        resetTimer();
    }

    @EventInit
    public void onEvent(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.currentScreen != null) {
            return;
        }

        if (onlyWhileAttackHeld.get() && !isAttackButtonHeld()) {
            resetTimer();
            return;
        }

        if (nextDelayMs <= 0L) {
            nextDelayMs = rollDelay();
        }

        if (timerUtil.finished(nextDelayMs) && doAttack()) {
            timerUtil.reset();
            nextDelayMs = rollDelay();
        }
    }

    private boolean doAttack() {
        if (mc.player == null || mc.interactionManager == null || mc.player.isUsingItem()) {
            return false;
        }

        HitResult hitResult = mc.crosshairTarget;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return false;
        }

        Entity target = entityHitResult.getEntity();
        if (!CombatUtil.isValidAttackTarget(target)) {
            return false;
        }

        if (!CombatUtil.isAttackReachable(mc.player, target, range.get())) {
            return false;
        }

        if (respectCooldown.get() && mc.player.getAttackCooldownProgress(0.5f) < minCooldown.get()) {
            return false;
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    private long rollDelay() {
        float baseDelay = delay.get();
        float spread = baseDelay * (jitter.get() / 100.0f);
        if (spread <= 0.0f) {
            return Math.max(50L, Math.round(baseDelay));
        }

        return Math.max(50L, Math.round(baseDelay + ThreadLocalRandom.current().nextDouble(-spread, spread)));
    }

    private void resetTimer() {
        timerUtil.reset();
        nextDelayMs = rollDelay();
    }

    private boolean isAttackButtonHeld() {
        return mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }
}
