package ru.strange.client.module.impl.other;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.ModeSetting;

@IModule(
        name = "Гамма",
        description = " ",
        category = Category.Other,
        bind = -1
)
public class FullBright extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Гамма", "Гамма", "Ночное зрение");
    private boolean injectedNightVision;

    public FullBright() {
        addSettings(mode);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mode.is("Ночное зрение")) {
            applyNightVision();
        }
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @Override
    public void onDisable() {
        clearInjectedNightVision();
        super.onDisable();
        if (mc.worldRenderer != null) {
            mc.worldRenderer.reload();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null) {
            return;
        }

        if (mode.is("Ночное зрение")) {
            applyNightVision();
        } else if (injectedNightVision) {
            clearInjectedNightVision();
        }
    }

    public boolean usesGammaBoost() {
        return enable && mode.is("Гамма");
    }

    private void applyNightVision() {
        if (mc.player == null) {
            return;
        }

        StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (current == null || current.getDuration() <= 220) {
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, false, false, false));
            injectedNightVision = true;
        }
    }

    private void clearInjectedNightVision() {
        if (mc.player == null) {
            injectedNightVision = false;
            return;
        }

        if (injectedNightVision) {
            StatusEffectInstance current = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (current != null && current.getDuration() <= 260 && current.getAmplifier() == 0) {
                mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            }
        }

        injectedNightVision = false;
    }
}
