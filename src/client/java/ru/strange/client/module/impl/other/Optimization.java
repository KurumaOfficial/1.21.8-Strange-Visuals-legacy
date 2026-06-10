package ru.strange.client.module.impl.other;

import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.render.ChunkBuilderMode;
import net.minecraft.particle.ParticlesMode;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Оптимизация",
        description = "Автоматическая оптимизация настроек графики",
        category = Category.Other,
        bind = -1
)
public class Optimization extends Module {
    private static final double OPTIMIZED_ENTITY_DISTANCE_SCALE = 0.35D;
    private static final int OPTIMIZED_VIEW_DISTANCE = 6;
    private static final int OPTIMIZED_SIMULATION_DISTANCE = 5;
    private static final double MINIMAL_EFFECT_SCALE = 0.0D;
    private static final int MINIMAL_MIPMAP_LEVELS = 0;
    private static final int MINIMAL_BIOME_BLEND_RADIUS = 0;
    private static final double CROWDED_SERVER_ESP_RANGE = 20.0D;

    private static Optimization instance;

    private final BooleanSetting autoMinVanilla = new BooleanSetting("Auto Min Vanilla", true);
    private final BooleanSetting fastGraphics = new BooleanSetting("Fast Graphics", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableAmbientOcclusion = new BooleanSetting("Ambient Occlusion", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableMipmap = new BooleanSetting("Mipmap Levels", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting minimalBiomeBlend = new BooleanSetting("Biome Blend", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableDistortionEffects = new BooleanSetting("Distortion FX", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableFovEffects = new BooleanSetting("FOV Effects", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableDarknessEffects = new BooleanSetting("Darkness FX", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting disableDamageTilt = new BooleanSetting("Damage Tilt", true).hidden(() -> !autoMinVanilla.get());
    private final BooleanSetting prioritizeNearbyChunks = new BooleanSetting("Nearby Chunks", true).hidden(() -> !autoMinVanilla.get());

    private final BooleanSetting disableParticles = new BooleanSetting("Партиклы", false);
    private final BooleanSetting disableClouds = new BooleanSetting("Облака", false);
    private final BooleanSetting reduceEntityRendering = new BooleanSetting("Энтити", false);
    private final BooleanSetting reduceViewDistance = new BooleanSetting("Дальность чанков", false);
    private final BooleanSetting reduceSimulationDistance = new BooleanSetting("Симуляция", false);
    private final BooleanSetting disableEntityShadows = new BooleanSetting("Тени энтити", false);
    private final BooleanSetting disableVsync = new BooleanSetting("VSync", false);

    private final BooleanSetting disableHudBlur = new BooleanSetting("HUD Blur", true);
    private final BooleanSetting disableScreenShaders = new BooleanSetting("Screen Shaders", true);
    private final BooleanSetting disableHandShaders = new BooleanSetting("Shader Hand", true);
    private final BooleanSetting disableModuleParticles = new BooleanSetting("Module Particles", true);
    private final SliderSetting particleMultiplier = new SliderSetting("Множитель частиц", 1.0f, 0.0f, 4.0f, 0.25f, false)
            .hidden(() -> disableModuleParticles.get());
    private final BooleanSetting limitTargetEffects = new BooleanSetting("Target FX", true);
    private final BooleanSetting limitEntityEsp = new BooleanSetting("Entity ESP", true);
    private final BooleanSetting limitWorldEffects = new BooleanSetting("World FX", true);
    private final BooleanSetting disableTrailBloom = new BooleanSetting("Trail Bloom", true);

    private CloudRenderMode originalCloudMode;
    private GraphicsMode originalGraphicsMode;
    private ChunkBuilderMode originalChunkBuilderMode;
    private double originalEntityDistance;
    private double originalDistortionEffectScale;
    private double originalFovEffectScale;
    private double originalDarknessEffectScale;
    private double originalDamageTiltStrength;
    private int originalViewDistance;
    private int originalSimulationDistance;
    private int originalMipmapLevels;
    private int originalBiomeBlendRadius;
    private boolean originalEntityShadows;
    private boolean originalVsync;
    private boolean originalAmbientOcclusion;
    private ParticlesMode originalParticles;

    private boolean particlesApplied;
    private boolean cloudsApplied;
    private boolean entityDistanceApplied;
    private boolean viewDistanceApplied;
    private boolean simulationDistanceApplied;
    private boolean entityShadowsApplied;
    private boolean vsyncApplied;
    private boolean graphicsModeApplied;
    private boolean ambientOcclusionApplied;
    private boolean mipmapApplied;
    private boolean biomeBlendApplied;
    private boolean distortionEffectsApplied;
    private boolean fovEffectsApplied;
    private boolean darknessEffectsApplied;
    private boolean damageTiltApplied;
    private boolean chunkBuilderModeApplied;

    public Optimization() {
        instance = this;
        addSettings(
                autoMinVanilla,
                fastGraphics,
                disableAmbientOcclusion,
                disableMipmap,
                minimalBiomeBlend,
                disableDistortionEffects,
                disableFovEffects,
                disableDarknessEffects,
                disableDamageTilt,
                prioritizeNearbyChunks,
                disableParticles,
                disableClouds,
                reduceEntityRendering,
                reduceViewDistance,
                reduceSimulationDistance,
                disableEntityShadows,
                disableVsync,
                disableHudBlur,
                disableScreenShaders,
                disableHandShaders,
                disableModuleParticles,
                particleMultiplier,
                limitTargetEffects,
                limitEntityEsp,
                limitWorldEffects,
                disableTrailBloom
        );
    }

    private static Optimization active() {
        return instance != null && instance.enable ? instance : null;
    }

    public static boolean shouldSkipHudBlur() {
        Optimization module = active();
        return module != null && module.disableHudBlur.get();
    }

    public static boolean shouldSkipScreenFilters() {
        Optimization module = active();
        return module != null && module.disableScreenShaders.get();
    }

    public static boolean shouldDisableHandShaders() {
        Optimization module = active();
        return module != null && module.disableHandShaders.get();
    }

    public static boolean shouldDisableModuleParticles() {
        Optimization module = active();
        return module != null && module.disableModuleParticles.get();
    }

    public static float getParticleMultiplier() {
        Optimization module = active();
        if (module == null || module.disableModuleParticles.get()) return 0f;
        return module.particleMultiplier.get();
    }

    public static boolean shouldLimitTargetEffects() {
        Optimization module = active();
        return module != null && module.limitTargetEffects.get();
    }

    public static boolean shouldLimitEntityEsp() {
        Optimization module = active();
        return module != null && module.limitEntityEsp.get();
    }

    public static boolean shouldLimitWorldEffects() {
        Optimization module = active();
        return module != null && module.limitWorldEffects.get();
    }

    public static boolean shouldSkipTrailBloomPass() {
        Optimization module = active();
        return module != null && (module.disableTrailBloom.get() || module.limitWorldEffects.get());
    }

    public static int capTargetEspGhostCount(int configured) {
        return shouldLimitTargetEffects() ? Math.min(configured, 8) : configured;
    }

    public static int capTargetEspCubeCount(int configured) {
        return shouldLimitTargetEffects() ? Math.min(configured, 10) : configured;
    }

    public static int capJumpCircleSegments(int configured) {
        return shouldLimitWorldEffects() ? Math.min(configured, 36) : configured;
    }

    public static int capDashCubeCount(int configured) {
        return shouldLimitWorldEffects() ? Math.min(configured, 2) : configured;
    }

    public static double capEntityEspRange(double configured) {
        return shouldLimitEntityEsp() ? Math.min(configured, CROWDED_SERVER_ESP_RANGE) : configured;
    }

    public static boolean shouldDashCubesTrackAllEntities() {
        return !shouldLimitWorldEffects();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.options == null) {
            return;
        }

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

        syncOptimizations();
    }

    private void syncOptimizations() {
        boolean vanillaProfile = autoMinVanilla.get();

        graphicsModeApplied = syncFlag("graphics_mode",
                vanillaProfile && fastGraphics.get(),
                graphicsModeApplied,
                this::applyFastGraphics,
                this::restoreGraphicsMode);
        ambientOcclusionApplied = syncFlag("ambient_occlusion",
                vanillaProfile && disableAmbientOcclusion.get(),
                ambientOcclusionApplied,
                this::applyAmbientOcclusionDisabled,
                this::restoreAmbientOcclusion);
        mipmapApplied = syncFlag("mipmap_levels",
                vanillaProfile && disableMipmap.get(),
                mipmapApplied,
                this::applyMipmapDisabled,
                this::restoreMipmapLevels);
        biomeBlendApplied = syncFlag("biome_blend",
                vanillaProfile && minimalBiomeBlend.get(),
                biomeBlendApplied,
                this::applyBiomeBlendMinimal,
                this::restoreBiomeBlendRadius);
        distortionEffectsApplied = syncFlag("distortion_effects",
                vanillaProfile && disableDistortionEffects.get(),
                distortionEffectsApplied,
                this::applyDistortionEffectsDisabled,
                this::restoreDistortionEffects);
        fovEffectsApplied = syncFlag("fov_effects",
                vanillaProfile && disableFovEffects.get(),
                fovEffectsApplied,
                this::applyFovEffectsDisabled,
                this::restoreFovEffects);
        darknessEffectsApplied = syncFlag("darkness_effects",
                vanillaProfile && disableDarknessEffects.get(),
                darknessEffectsApplied,
                this::applyDarknessEffectsDisabled,
                this::restoreDarknessEffects);
        damageTiltApplied = syncFlag("damage_tilt",
                vanillaProfile && disableDamageTilt.get(),
                damageTiltApplied,
                this::applyDamageTiltDisabled,
                this::restoreDamageTilt);
        chunkBuilderModeApplied = syncFlag("chunk_builder_mode",
                vanillaProfile && prioritizeNearbyChunks.get(),
                chunkBuilderModeApplied,
                this::applyChunkBuilderNearby,
                this::restoreChunkBuilderMode);

        cloudsApplied = syncFlag("clouds",
                vanillaProfile || disableClouds.get(),
                cloudsApplied,
                this::applyCloudsOff,
                this::restoreClouds);
        entityDistanceApplied = syncFlag("entity_distance",
                vanillaProfile || reduceEntityRendering.get(),
                entityDistanceApplied,
                this::applyEntityDistanceReduction,
                this::restoreEntityDistance);
        particlesApplied = syncFlag("particles",
                vanillaProfile || disableParticles.get(),
                particlesApplied,
                this::applyParticlesMinimal,
                this::restoreParticles);
        viewDistanceApplied = syncFlag("view_distance",
                reduceViewDistance.get(),
                viewDistanceApplied,
                this::applyViewDistanceReduction,
                this::restoreViewDistance);
        simulationDistanceApplied = syncFlag("simulation_distance",
                vanillaProfile || reduceSimulationDistance.get(),
                simulationDistanceApplied,
                this::applySimulationDistanceReduction,
                this::restoreSimulationDistance);
        entityShadowsApplied = syncFlag("entity_shadows",
                vanillaProfile || disableEntityShadows.get(),
                entityShadowsApplied,
                this::applyEntityShadowsDisabled,
                this::restoreEntityShadows);
        vsyncApplied = syncFlag("vsync",
                vanillaProfile || disableVsync.get(),
                vsyncApplied,
                this::applyVsyncDisabled,
                this::restoreVsync);
    }

    private boolean syncFlag(String settingKey, boolean targetState, boolean currentState, Runnable apply, Runnable restore) {
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
        } catch (RuntimeException exception) {
            Strange.LOGGER.warn("Failed to update optimization setting {}", settingKey, exception);
            return currentState;
        }
    }

    private void restoreSettings() {
        if (mc.options == null) {
            return;
        }

        if (graphicsModeApplied) {
            restoreGraphicsMode();
        }
        if (ambientOcclusionApplied) {
            restoreAmbientOcclusion();
        }
        if (mipmapApplied) {
            restoreMipmapLevels();
        }
        if (biomeBlendApplied) {
            restoreBiomeBlendRadius();
        }
        if (distortionEffectsApplied) {
            restoreDistortionEffects();
        }
        if (fovEffectsApplied) {
            restoreFovEffects();
        }
        if (darknessEffectsApplied) {
            restoreDarknessEffects();
        }
        if (damageTiltApplied) {
            restoreDamageTilt();
        }
        if (chunkBuilderModeApplied) {
            restoreChunkBuilderMode();
        }
        if (cloudsApplied) {
            restoreClouds();
        }
        if (entityDistanceApplied) {
            restoreEntityDistance();
        }
        if (particlesApplied) {
            restoreParticles();
        }
        if (viewDistanceApplied) {
            restoreViewDistance();
        }
        if (simulationDistanceApplied) {
            restoreSimulationDistance();
        }
        if (entityShadowsApplied) {
            restoreEntityShadows();
        }
        if (vsyncApplied) {
            restoreVsync();
        }
    }

    private void resetAppliedState() {
        particlesApplied = false;
        cloudsApplied = false;
        entityDistanceApplied = false;
        viewDistanceApplied = false;
        simulationDistanceApplied = false;
        entityShadowsApplied = false;
        vsyncApplied = false;
        graphicsModeApplied = false;
        ambientOcclusionApplied = false;
        mipmapApplied = false;
        biomeBlendApplied = false;
        distortionEffectsApplied = false;
        fovEffectsApplied = false;
        darknessEffectsApplied = false;
        damageTiltApplied = false;
        chunkBuilderModeApplied = false;

        originalCloudMode = null;
        originalGraphicsMode = null;
        originalChunkBuilderMode = null;
        originalParticles = null;
        originalEntityDistance = 0.0D;
        originalDistortionEffectScale = 0.0D;
        originalFovEffectScale = 0.0D;
        originalDarknessEffectScale = 0.0D;
        originalDamageTiltStrength = 0.0D;
        originalViewDistance = 0;
        originalSimulationDistance = 0;
        originalMipmapLevels = 0;
        originalBiomeBlendRadius = 0;
        originalEntityShadows = false;
        originalVsync = false;
        originalAmbientOcclusion = false;
    }

    public boolean shouldDisableParticles() {
        return enable && (autoMinVanilla.get() || disableParticles.get());
    }

    private void applyParticlesMinimal() {
        originalParticles = mc.options.getParticles().getValue();
        mc.options.getParticles().setValue(ParticlesMode.MINIMAL);
    }

    private void restoreParticles() {
        if (originalParticles != null && mc.options.getParticles().getValue() == ParticlesMode.MINIMAL) {
            mc.options.getParticles().setValue(originalParticles);
        }
        originalParticles = null;
    }

    private void applyCloudsOff() {
        originalCloudMode = mc.options.getCloudRenderMode().getValue();
        mc.options.getCloudRenderMode().setValue(CloudRenderMode.OFF);
    }

    private void restoreClouds() {
        if (originalCloudMode != null && mc.options.getCloudRenderMode().getValue() == CloudRenderMode.OFF) {
            mc.options.getCloudRenderMode().setValue(originalCloudMode);
        }
        originalCloudMode = null;
    }

    private void applyFastGraphics() {
        originalGraphicsMode = mc.options.getGraphicsMode().getValue();
        mc.options.getGraphicsMode().setValue(GraphicsMode.FAST);
    }

    private void restoreGraphicsMode() {
        if (originalGraphicsMode != null && mc.options.getGraphicsMode().getValue() == GraphicsMode.FAST) {
            mc.options.getGraphicsMode().setValue(originalGraphicsMode);
        }
        originalGraphicsMode = null;
    }

    private void applyAmbientOcclusionDisabled() {
        originalAmbientOcclusion = mc.options.getAo().getValue();
        mc.options.getAo().setValue(false);
    }

    private void restoreAmbientOcclusion() {
        if (!mc.options.getAo().getValue()) {
            mc.options.getAo().setValue(originalAmbientOcclusion);
        }
    }

    private void applyMipmapDisabled() {
        originalMipmapLevels = mc.options.getMipmapLevels().getValue();
        mc.options.getMipmapLevels().setValue(MINIMAL_MIPMAP_LEVELS);
    }

    private void restoreMipmapLevels() {
        if (mc.options.getMipmapLevels().getValue() == MINIMAL_MIPMAP_LEVELS) {
            mc.options.getMipmapLevels().setValue(originalMipmapLevels);
        }
    }

    private void applyBiomeBlendMinimal() {
        originalBiomeBlendRadius = mc.options.getBiomeBlendRadius().getValue();
        mc.options.getBiomeBlendRadius().setValue(MINIMAL_BIOME_BLEND_RADIUS);
    }

    private void restoreBiomeBlendRadius() {
        if (mc.options.getBiomeBlendRadius().getValue() == MINIMAL_BIOME_BLEND_RADIUS) {
            mc.options.getBiomeBlendRadius().setValue(originalBiomeBlendRadius);
        }
    }

    private void applyDistortionEffectsDisabled() {
        originalDistortionEffectScale = mc.options.getDistortionEffectScale().getValue();
        mc.options.getDistortionEffectScale().setValue(MINIMAL_EFFECT_SCALE);
    }

    private void restoreDistortionEffects() {
        if (Double.compare(mc.options.getDistortionEffectScale().getValue(), MINIMAL_EFFECT_SCALE) == 0) {
            mc.options.getDistortionEffectScale().setValue(originalDistortionEffectScale);
        }
    }

    private void applyFovEffectsDisabled() {
        originalFovEffectScale = mc.options.getFovEffectScale().getValue();
        mc.options.getFovEffectScale().setValue(MINIMAL_EFFECT_SCALE);
    }

    private void restoreFovEffects() {
        if (Double.compare(mc.options.getFovEffectScale().getValue(), MINIMAL_EFFECT_SCALE) == 0) {
            mc.options.getFovEffectScale().setValue(originalFovEffectScale);
        }
    }

    private void applyDarknessEffectsDisabled() {
        originalDarknessEffectScale = mc.options.getDarknessEffectScale().getValue();
        mc.options.getDarknessEffectScale().setValue(MINIMAL_EFFECT_SCALE);
    }

    private void restoreDarknessEffects() {
        if (Double.compare(mc.options.getDarknessEffectScale().getValue(), MINIMAL_EFFECT_SCALE) == 0) {
            mc.options.getDarknessEffectScale().setValue(originalDarknessEffectScale);
        }
    }

    private void applyDamageTiltDisabled() {
        originalDamageTiltStrength = mc.options.getDamageTiltStrength().getValue();
        mc.options.getDamageTiltStrength().setValue(MINIMAL_EFFECT_SCALE);
    }

    private void restoreDamageTilt() {
        if (Double.compare(mc.options.getDamageTiltStrength().getValue(), MINIMAL_EFFECT_SCALE) == 0) {
            mc.options.getDamageTiltStrength().setValue(originalDamageTiltStrength);
        }
    }

    private void applyChunkBuilderNearby() {
        originalChunkBuilderMode = mc.options.getChunkBuilderMode().getValue();
        mc.options.getChunkBuilderMode().setValue(ChunkBuilderMode.NEARBY);
    }

    private void restoreChunkBuilderMode() {
        if (originalChunkBuilderMode != null && mc.options.getChunkBuilderMode().getValue() == ChunkBuilderMode.NEARBY) {
            mc.options.getChunkBuilderMode().setValue(originalChunkBuilderMode);
        }
        originalChunkBuilderMode = null;
    }

    private void applyEntityDistanceReduction() {
        originalEntityDistance = mc.options.getEntityDistanceScaling().getValue();
        mc.options.getEntityDistanceScaling().setValue(OPTIMIZED_ENTITY_DISTANCE_SCALE);
    }

    private void restoreEntityDistance() {
        if (Double.compare(mc.options.getEntityDistanceScaling().getValue(), OPTIMIZED_ENTITY_DISTANCE_SCALE) == 0) {
            mc.options.getEntityDistanceScaling().setValue(originalEntityDistance);
        }
    }

    private void applyViewDistanceReduction() {
        originalViewDistance = mc.options.getViewDistance().getValue();
        mc.options.getViewDistance().setValue(OPTIMIZED_VIEW_DISTANCE);
    }

    private void restoreViewDistance() {
        if (mc.options.getViewDistance().getValue() == OPTIMIZED_VIEW_DISTANCE) {
            mc.options.getViewDistance().setValue(originalViewDistance);
        }
    }

    private void applySimulationDistanceReduction() {
        originalSimulationDistance = mc.options.getSimulationDistance().getValue();
        mc.options.getSimulationDistance().setValue(OPTIMIZED_SIMULATION_DISTANCE);
    }

    private void restoreSimulationDistance() {
        if (mc.options.getSimulationDistance().getValue() == OPTIMIZED_SIMULATION_DISTANCE) {
            mc.options.getSimulationDistance().setValue(originalSimulationDistance);
        }
    }

    private void applyEntityShadowsDisabled() {
        originalEntityShadows = mc.options.getEntityShadows().getValue();
        mc.options.getEntityShadows().setValue(false);
    }

    private void restoreEntityShadows() {
        if (!mc.options.getEntityShadows().getValue()) {
            mc.options.getEntityShadows().setValue(originalEntityShadows);
        }
    }

    private void applyVsyncDisabled() {
        originalVsync = mc.options.getEnableVsync().getValue();
        mc.options.getEnableVsync().setValue(false);
    }

    private void restoreVsync() {
        if (!mc.options.getEnableVsync().getValue()) {
            mc.options.getEnableVsync().setValue(originalVsync);
        }
    }
}
