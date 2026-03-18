package ru.strange.client.module.impl.other;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.particle.ParticlesMode;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

/**
 * Toggleable game optimization module.
 * <p>
 * Saves original {@link net.minecraft.client.option.GameOptions} values on enable,
 * applies optimized values every tick, and restores originals on disable.
 * Uses direct Minecraft API — no reflection.
 */
@IModule(
        name = "Оптимизация",
        description = "",
        category = Category.Other,
        bind = -1
)
public class Optimization extends Module {

    private final BooleanSetting disableParticles = new BooleanSetting("Партиклы", false);
    private final BooleanSetting disableClouds = new BooleanSetting("Облака", false);
    private final BooleanSetting reduceEntityRendering = new BooleanSetting("Энтити", false);
    private final BooleanSetting reduceViewDistance = new BooleanSetting("Дальность чанков", false);
    private final BooleanSetting reduceSimulationDistance = new BooleanSetting("Симуляция", false);
    private final BooleanSetting disableEntityShadows = new BooleanSetting("Тени энтити", false);
    private final BooleanSetting disableVsync = new BooleanSetting("VSync", false);

    private CloudRenderMode originalCloudMode;
    private double originalEntityDistance = 1.0;
    private int originalViewDistance;
    private int originalSimulationDistance;
    private boolean originalEntityShadows;
    private boolean originalVsync;
    private ParticlesMode originalParticles;

    public Optimization() {
        addSettings(
                disableParticles,
                disableClouds,
                reduceEntityRendering,
                reduceViewDistance,
                reduceSimulationDistance,
                disableEntityShadows,
                disableVsync
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.options == null) return;

        originalCloudMode = mc.options.getCloudRenderMode().getValue();
        originalEntityDistance = mc.options.getEntityDistanceScaling().getValue();
        originalViewDistance = mc.options.getViewDistance().getValue();
        originalSimulationDistance = mc.options.getSimulationDistance().getValue();
        originalEntityShadows = mc.options.getEntityShadows().getValue();
        originalVsync = mc.options.getEnableVsync().getValue();
        originalParticles = mc.options.getParticles().getValue();

        applyOptimizations();
    }

    @Override
    public void onDisable() {
        restoreSettings();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.options == null) return;
        applyOptimizations();
    }

    private void applyOptimizations() {
        if (mc.options == null) return;

        if (disableClouds.get()) {
            mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
        }

        if (reduceEntityRendering.get()) {
            mc.options.getEntityDistanceScaling().setValue(0.5);
        }

        if (disableParticles.get()) {
            mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
        }

        if (reduceViewDistance.get()) {
            mc.options.getViewDistance().setValue(6);
        }

        if (reduceSimulationDistance.get()) {
            mc.options.getSimulationDistance().setValue(6);
        }

        if (disableEntityShadows.get()) {
            mc.options.getEntityShadows().setValue(false);
        }

        if (disableVsync.get()) {
            mc.options.getEnableVsync().setValue(false);
        }
    }

    private void restoreSettings() {
        if (mc.options == null) return;

        mc.options.getCloudRenderMode().setValue(originalCloudMode);
        mc.options.getEntityDistanceScaling().setValue(originalEntityDistance);
        mc.options.getViewDistance().setValue(originalViewDistance);
        mc.options.getSimulationDistance().setValue(originalSimulationDistance);
        mc.options.getEntityShadows().setValue(originalEntityShadows);
        mc.options.getEnableVsync().setValue(originalVsync);
        mc.options.getParticles().setValue(originalParticles);
    }

    /**
     * Queried by particle-related code to check if particles should be suppressed.
     */
    public boolean shouldDisableParticles() {
        return enable && disableParticles.get();
    }
}