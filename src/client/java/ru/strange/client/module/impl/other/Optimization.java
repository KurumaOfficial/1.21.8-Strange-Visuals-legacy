package ru.strange.client.module.impl.other;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.particle.ParticlesMode;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

@IModule(
        name = "Оптимизация",
        description = "Автоматическая оптимизация настроек графики",
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
    private double originalEntityDistance;
    private int originalViewDistance;
    private int originalSimulationDistance;
    private boolean originalEntityShadows;
    private boolean originalVsync;
    private ParticlesMode originalParticles;
    private boolean savedOriginals;

    private boolean particlesApplied;
    private boolean cloudsApplied;
    private boolean entityDistanceApplied;
    private boolean viewDistanceApplied;
    private boolean simulationDistanceApplied;
    private boolean entityShadowsApplied;
    private boolean vsyncApplied;

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
        if (mc.options == null) {
            return;
        }

        saveOriginals();
        syncOptimizations();
    }

    @Override
    public void onDisable() {
        restoreSettings();
        resetAppliedState();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.options == null) {
            return;
        }

        saveOriginals();
        syncOptimizations();
    }

    private void saveOriginals() {
        if (mc.options == null || savedOriginals) {
            return;
        }

        try {
            originalCloudMode = mc.options.getCloudRenderMode().getValue();
            originalEntityDistance = mc.options.getEntityDistanceScaling().getValue();
            originalViewDistance = mc.options.getViewDistance().getValue();
            originalSimulationDistance = mc.options.getSimulationDistance().getValue();
            originalEntityShadows = mc.options.getEntityShadows().getValue();
            originalVsync = mc.options.getEnableVsync().getValue();
            originalParticles = mc.options.getParticles().getValue();
            savedOriginals = true;
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to capture original graphics settings", e);
        }
    }

    private void syncOptimizations() {
        cloudsApplied = syncFlag(disableClouds.get(), cloudsApplied, this::applyCloudsOff, this::restoreClouds);
        entityDistanceApplied = syncFlag(reduceEntityRendering.get(), entityDistanceApplied, this::applyEntityDistanceReduction, this::restoreEntityDistance);
        particlesApplied = syncFlag(disableParticles.get(), particlesApplied, this::applyParticlesMinimal, this::restoreParticles);
        viewDistanceApplied = syncFlag(reduceViewDistance.get(), viewDistanceApplied, this::applyViewDistanceReduction, this::restoreViewDistance);
        simulationDistanceApplied = syncFlag(reduceSimulationDistance.get(), simulationDistanceApplied, this::applySimulationDistanceReduction, this::restoreSimulationDistance);
        entityShadowsApplied = syncFlag(disableEntityShadows.get(), entityShadowsApplied, this::applyEntityShadowsDisabled, this::restoreEntityShadows);
        vsyncApplied = syncFlag(disableVsync.get(), vsyncApplied, this::applyVsyncDisabled, this::restoreVsync);
    }

    private boolean syncFlag(boolean targetState, boolean currentState, Runnable apply, Runnable restore) {
        if (targetState == currentState) {
            return currentState;
        }

        try {
            if (targetState) {
                apply.run();
                return true;
            }

            restore.run();
            return false;
        } catch (Exception e) {
            Strange.LOGGER.warn("Failed to update optimization setting", e);
            return currentState;
        }
    }

    private void restoreSettings() {
        if (mc.options == null || !savedOriginals) {
            return;
        }

        restoreClouds();
        restoreEntityDistance();
        restoreParticles();
        restoreViewDistance();
        restoreSimulationDistance();
        restoreEntityShadows();
        restoreVsync();
        savedOriginals = false;
    }

    private void resetAppliedState() {
        particlesApplied = false;
        cloudsApplied = false;
        entityDistanceApplied = false;
        viewDistanceApplied = false;
        simulationDistanceApplied = false;
        entityShadowsApplied = false;
        vsyncApplied = false;
    }

    public boolean shouldDisableParticles() {
        return enable && disableParticles.get();
    }

    private void applyParticlesMinimal() {
        mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
    }

    private void restoreParticles() {
        if (originalParticles != null) {
            mc.options.getParticles().setValue(originalParticles);
        }
    }

    private void applyCloudsOff() {
        mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
    }

    private void restoreClouds() {
        if (originalCloudMode != null) {
            mc.options.getCloudRenderMode().setValue(originalCloudMode);
        }
    }

    private void applyEntityDistanceReduction() {
        mc.options.getEntityDistanceScaling().setValue(0.5);
    }

    private void restoreEntityDistance() {
        mc.options.getEntityDistanceScaling().setValue(originalEntityDistance);
    }

    private void applyViewDistanceReduction() {
        mc.options.getViewDistance().setValue(6);
    }

    private void restoreViewDistance() {
        mc.options.getViewDistance().setValue(originalViewDistance);
    }

    private void applySimulationDistanceReduction() {
        mc.options.getSimulationDistance().setValue(6);
    }

    private void restoreSimulationDistance() {
        mc.options.getSimulationDistance().setValue(originalSimulationDistance);
    }

    private void applyEntityShadowsDisabled() {
        mc.options.getEntityShadows().setValue(false);
    }

    private void restoreEntityShadows() {
        mc.options.getEntityShadows().setValue(originalEntityShadows);
    }

    private void applyVsyncDisabled() {
        mc.options.getEnableVsync().setValue(false);
    }

    private void restoreVsync() {
        mc.options.getEnableVsync().setValue(originalVsync);
    }
}
