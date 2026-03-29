package ru.strange.client.module.impl.player;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventGlassHandsRender;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ButtonSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.renderengine.renderers.util.GlassHandRenderer;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.ui.clickgui.screen.ItemShaderProfilesScreen;
import ru.strange.client.utils.other.ItemShaderProfiles;

import java.util.Objects;

@IModule(
        name = "Shader Hand",
        description = "Transparent and shader-driven hand rendering",
        category = Category.Player,
        bind = -1
)
public class ShaderHand extends Module {

    private static ShaderHand instance;
    private int lastRendererSettingsHash = Integer.MIN_VALUE;
    private boolean batchedHandsActive;

    public final ModeSetting mode = new ModeSetting("Mode", "Glass", "Glass", "Shader", "Custom Shader");
    public final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names());

    public final SliderSetting blurRadius = new SliderSetting("Blur Radius", 2.5f, 0.5f, 10.0f, 0.1f, false);
    public final SliderSetting blurIterations = new SliderSetting("Blur Iterations", 3.0f, 1.0f, 8.0f, 1.0f, false);
    public final SliderSetting saturation = new SliderSetting("Saturation", 1.0f, 0.0f, 2.0f, 0.05f, false);
    public final BooleanSetting enableTint = new BooleanSetting("Tint", false);
    public final HueSetting tintColor = new HueSetting("Tint Color", 50f);
    public final SliderSetting tintIntensity = new SliderSetting("Tint Strength", 0.1f, 0.0f, 1.0f, 0.05f, false);
    public final BooleanSetting enableEdgeGlow = new BooleanSetting("Edge Glow", true);
    public final SliderSetting edgeGlowIntensity = new SliderSetting("Edge Glow Strength", 0.3f, 0.0f, 1.0f, 0.05f, false);

    public final BooleanSetting enableHandGlow = new BooleanSetting("Hand Glow", false);
    public final HueSetting handGlowColor = new HueSetting("Hand Glow Color", new java.awt.Color(170, 220, 255));
    public final SliderSetting handGlowStrength = new SliderSetting("Hand Glow Power", 0.45f, 0.0f, 1.2f, 0.05f, false);

    public final SliderSetting starDensity = new SliderSetting("Star Density", 28.0f, 5.0f, 60.0f, 1.0f, false);
    public final SliderSetting starSpeed = new SliderSetting("Star Speed", 0.3f, 0.0f, 2.0f, 0.05f, false);
    public final SliderSetting nebulaIntensity = new SliderSetting("Shader Intensity", 0.8f, 0.0f, 2.0f, 0.05f, false);
    public final HueSetting nebulaColor = new HueSetting("Shader Color", 80f, 0.8f, 0.7f);
    public final BooleanSetting cosmosEdgeGlow = new BooleanSetting("Shader Edge Glow", true);
    public final SliderSetting cosmosEdgeGlowIntensity = new SliderSetting("Shader Edge Strength", 0.5f, 0.0f, 1.0f, 0.05f, false);
    public final ButtonSetting profileEditor = new ButtonSetting("Custom Profiles", 0, "Open", this::openProfileEditor);
    public final SliderSetting pulseAlpha = new SliderSetting("Pulse Alpha", 0.75f, 0.05f, 1.5f, 0.05f, false);
    public final BooleanSetting pulseEffectOnly = new BooleanSetting("Pulse Effect Only", false);

    public ShaderHand() {
        instance = this;

        blurRadius.hidden = this::usesShaderRenderer;
        blurIterations.hidden = this::usesShaderRenderer;
        saturation.hidden = this::usesShaderRenderer;
        enableTint.hidden = this::usesShaderRenderer;
        tintColor.hidden = () -> usesShaderRenderer() || !enableTint.get();
        tintIntensity.hidden = () -> usesShaderRenderer() || !enableTint.get();
        enableEdgeGlow.hidden = this::usesShaderRenderer;
        edgeGlowIntensity.hidden = () -> usesShaderRenderer() || !enableEdgeGlow.get();

        enableHandGlow.hidden = this::usesShaderRenderer;
        handGlowColor.hidden = () -> usesShaderRenderer() || !enableHandGlow.get();
        handGlowStrength.hidden = () -> usesShaderRenderer() || !enableHandGlow.get();

        shaderTheme.hidden = this::isGlassMode;
        starDensity.hidden = this::isGlassMode;
        starSpeed.hidden = this::isGlassMode;
        nebulaIntensity.hidden = this::isGlassMode;
        nebulaColor.hidden = this::isGlassMode;
        cosmosEdgeGlow.hidden = this::isGlassMode;
        cosmosEdgeGlowIntensity.hidden = () -> isGlassMode() || !cosmosEdgeGlow.get();
        profileEditor.hidden = () -> !isCustomShaderMode();
        pulseAlpha.hidden = () -> isGlassMode() || !isPulseThemeSelected();
        pulseEffectOnly.hidden = () -> isGlassMode() || !isPulseThemeSelected();

        addSettings(
                mode, shaderTheme,
                blurRadius, blurIterations, saturation, enableTint, tintColor, tintIntensity, enableEdgeGlow, edgeGlowIntensity,
                enableHandGlow, handGlowColor, handGlowStrength,
                starDensity, starSpeed, nebulaIntensity, nebulaColor, cosmosEdgeGlow, cosmosEdgeGlowIntensity, profileEditor,
                pulseAlpha, pulseEffectOnly
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        lastRendererSettingsHash = Integer.MIN_VALUE;
        batchedHandsActive = false;
        GlassHandRenderer renderer = GlassHandRenderer.getInstance();
        renderer.setEnabled(true);
        updateRendererSettings();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        lastRendererSettingsHash = Integer.MIN_VALUE;
        batchedHandsActive = false;
        GlassHandRenderer.getInstance().setEnabled(false);
    }

    @EventInit
    public void onGlassHandsRender(EventGlassHandsRender event) {
        if (Optimization.shouldDisableHandShaders()) {
            batchedHandsActive = false;
            return;
        }

        GlassHandRenderer renderer = GlassHandRenderer.getInstance();
        if (!renderer.isEnabled()) {
            return;
        }

        if (event.getPhase() == EventGlassHandsRender.Phase.PRE) {
            if (event.isFirstInBatch() && !event.isLastInBatch() && canBatchHands(event.getStack(), event.getOtherStack())) {
                batchedHandsActive = true;
                updateRendererSettingsForStack(event.getStack());
                renderer.captureSceneBeforeHands();
                return;
            }

            if (!batchedHandsActive) {
                updateRendererSettingsForStack(event.getStack());
                renderer.captureSceneBeforeHands();
            }
        } else {
            if (batchedHandsActive) {
                if (event.isLastInBatch()) {
                    renderer.captureSceneAfterHands();
                    renderer.renderGlassEffect();
                    batchedHandsActive = false;
                }
                return;
            }

            renderer.captureSceneAfterHands();
            renderer.renderGlassEffect();
        }
    }

    private void updateRendererSettings() {
        updateRendererSettingsForStack(null);
    }

    private void updateRendererSettingsForStack(net.minecraft.item.ItemStack stack) {
        ItemShaderProfiles.ShaderProfile profile = resolveProfileForStack(stack);

        int settingsHash = computeRendererSettingsHash(profile);
        if (settingsHash == lastRendererSettingsHash) {
            return;
        }

        lastRendererSettingsHash = settingsHash;
        updateRendererSettings(profile);
    }

    private boolean canBatchHands(net.minecraft.item.ItemStack firstStack, net.minecraft.item.ItemStack secondStack) {
        return computeRendererSettingsHash(resolveProfileForStack(firstStack))
                == computeRendererSettingsHash(resolveProfileForStack(secondStack));
    }

    private ItemShaderProfiles.ShaderProfile resolveProfileForStack(net.minecraft.item.ItemStack stack) {
        if (!isCustomShaderMode()) {
            return null;
        }
        return ItemShaderProfiles.find(stack);
    }

    private int computeRendererSettingsHash(ItemShaderProfiles.ShaderProfile profile) {
        return Objects.hash(
                mode.get(),
                shaderTheme.get(),
                blurRadius.get(),
                blurIterations.get(),
                saturation.get(),
                enableTint.get(),
                tintColor.getColor().getRGB(),
                tintIntensity.get(),
                enableEdgeGlow.get(),
                edgeGlowIntensity.get(),
                enableHandGlow.get(),
                handGlowColor.getColor().getRGB(),
                handGlowStrength.get(),
                starDensity.get(),
                starSpeed.get(),
                nebulaIntensity.get(),
                nebulaColor.getColor().getRGB(),
                cosmosEdgeGlow.get(),
                cosmosEdgeGlowIntensity.get(),
                pulseAlpha.get(),
                pulseEffectOnly.get(),
                profile == null ? null : profile.itemId(),
                profile == null ? null : profile.themeName(),
                profile == null ? null : profile.tintColor(),
                profile == null ? null : profile.tintMix(),
                profile == null ? null : profile.pulseAlpha(),
                profile == null ? null : profile.pulseEffectOnly()
        );
    }

    private void updateRendererSettings(ItemShaderProfiles.ShaderProfile profile) {
        GlassHandRenderer renderer = GlassHandRenderer.getInstance();
        ShaderThemePreset fallbackPreset = getConfiguredShaderPreset();
        ShaderThemePreset preset = profile != null ? profile.resolveTheme(fallbackPreset) : fallbackPreset;
        int userTint = nebulaColor.getColor().getRGB() | 0xFF000000;
        int resolvedTint = profile != null ? profile.resolveTintColor(userTint) : userTint;
        boolean pulseTheme = preset.isPulse();
        float resolvedPulseAlpha = profile != null ? profile.resolvePulseAlpha(pulseAlpha.get()) : pulseAlpha.get();
        boolean resolvedPulseEffectOnly = profile != null ? profile.resolvePulseEffectOnly(pulseEffectOnly.get()) : pulseEffectOnly.get();
        float primaryTintMix = profile != null ? profile.resolveTintMix(0.22f) : 0.22f;
        float accentTintMix = profile != null ? Math.max(0.12f, primaryTintMix) : (pulseTheme ? 0.38f : 0.18f);

        renderer.setMode(usesShaderRenderer() ? 1 : 0);

        renderer.setBlurRadius(blurRadius.get());
        renderer.setBlurIterations((int) blurIterations.get());
        renderer.setSaturation(saturation.get());
        renderer.setReflect(true);

        if (enableTint.get()) {
            renderer.setTintColor(tintColor.getColor().getRGB());
            renderer.setTintIntensity(tintIntensity.get());
        } else {
            renderer.setTintColor(0x00000000);
            renderer.setTintIntensity(0.0f);
        }

        if (enableEdgeGlow.get()) {
            renderer.setEdgeGlowIntensity(edgeGlowIntensity.get());
        } else {
            renderer.setEdgeGlowIntensity(0.0f);
        }

        if (enableHandGlow.get()) {
            renderer.setGlowColor(handGlowColor.getColor().getRGB());
            renderer.setGlowIntensity(handGlowStrength.get());
        } else {
            renderer.setGlowColor(0xFFFFFFFF);
            renderer.setGlowIntensity(0.0f);
        }

        renderer.setCosmosThemeIndex(preset.themeIndex());
        renderer.setCosmosStarDensity(starDensity.get() * preset.densityScale());
        renderer.setCosmosStarSpeed(starSpeed.get() * preset.speedScale());
        renderer.setCosmosNebulaIntensity(nebulaIntensity.get() * preset.intensityScale());
        renderer.setCosmosNebulaColor(pulseTheme ? resolvedTint : ShaderThemePreset.mixColors(preset.primaryColor(), resolvedTint, primaryTintMix));
        renderer.setCosmosAccentColor(pulseTheme
                ? ShaderThemePreset.mixColors(preset.accentColor(), resolvedTint, accentTintMix)
                : ShaderThemePreset.mixColors(preset.accentColor(), resolvedTint, accentTintMix));
        renderer.setCosmosPatternScale(preset.patternScale());
        renderer.setCosmosSparkleScale(preset.sparkleScale());
        renderer.setCosmosStarMix(preset.starMix());
        renderer.setCosmosPulseAlpha(pulseTheme ? resolvedPulseAlpha : 1.0f);
        renderer.setCosmosPulseEffectOnly(pulseTheme && resolvedPulseEffectOnly);

        if (cosmosEdgeGlow.get()) {
            renderer.setCosmosEdgeGlowIntensity(cosmosEdgeGlowIntensity.get() * preset.edgeScale());
        } else {
            renderer.setCosmosEdgeGlowIntensity(0.0f);
        }
    }

    private boolean isGlassMode() {
        return mode.is("Glass");
    }

    private boolean usesShaderRenderer() {
        return mode.is("Shader") || mode.is("Custom Shader");
    }

    private boolean isCustomShaderMode() {
        return mode.is("Custom Shader");
    }

    private boolean isPulseThemeSelected() {
        return ShaderThemePreset.byName(shaderTheme.get()).isPulse();
    }

    private void openProfileEditor() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.setScreen(new ItemShaderProfilesScreen(client.currentScreen));
    }

    public static boolean isActive() {
        return instance != null && instance.enable;
    }

    public static ShaderThemePreset getConfiguredShaderPreset() {
        return instance == null ? ShaderThemePreset.COSMOS : ShaderThemePreset.byName(instance.shaderTheme.get());
    }
}
