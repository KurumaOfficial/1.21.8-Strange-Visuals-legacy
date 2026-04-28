package ru.strange.client.renderengine.renderers.util;

import net.minecraft.util.Identifier;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.particle.ParticleUtil;

public final class ShaderThemeVisuals {
    private ShaderThemeVisuals() {
    }

    public static int animatedPrimary(String themeName, double phase) {
        ShaderThemePreset preset = ShaderThemePreset.byName(themeName);
        float time = (float) (System.currentTimeMillis() / 1000.0);
        float wave = 0.5f + 0.5f * (float) Math.sin(time * (0.9f + preset.speedScale() * 0.35f) + phase);
        int mixed = ShaderThemePreset.mixColors(preset.primaryColor(), preset.accentColor(), wave);
        if (preset.isPulse()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.4f + phase * 1.37f);
            mixed = ShaderThemePreset.mixColors(mixed, 0xFFFFFFFF, 0.12f + 0.18f * pulse);
        }
        return mixed;
    }

    public static int animatedSecondary(String themeName, double phase) {
        return animatedPrimary(themeName, phase + Math.PI * 0.5);
    }

    public static int applyAlpha(int color, float alphaPc) {
        return (MathHelper.clampI((int) (Math.max(0.0f, Math.min(1.0f, alphaPc)) * 255.0f), 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    public static ParticleUtil.ParticleType particleType(String themeName) {
        ShaderThemePreset preset = ShaderThemePreset.byName(themeName);
        return switch (preset) {
            case AURORA, OCEAN, GLACIER, CRYSTAL -> ParticleUtil.ParticleType.SNOW;
            case SAKURA, BLOOM -> ParticleUtil.ParticleType.SAKURA;
            case LAVA, SOLARIS, AMBER, CORAL, PULSE_PLASMA -> ParticleUtil.ParticleType.BLOOM;
            case MATRIX, CIRCUIT -> ParticleUtil.ParticleType.TRIANGLE;
            case TOXIN -> ParticleUtil.ParticleType.DOLLAR;
            case FRACTAL, PRISM, NEON, COBWEB, PULSE_COBWEB -> ParticleUtil.ParticleType.GEMINI;
            case VOID, ECLIPSE, MONSOON -> ParticleUtil.ParticleType.SIMS;
            case THUNDER, PULSE_STARFIELD -> ParticleUtil.ParticleType.STAR;
            default -> ParticleUtil.ParticleType.STAR;
        };
    }

    public static Identifier particleTexture(String themeName) {
        return particleType(themeName).texture();
    }

    public static boolean isShaderMode(String mode) {
        return "Shader".equalsIgnoreCase(mode) || "Шейдер".equalsIgnoreCase(mode);
    }
}
