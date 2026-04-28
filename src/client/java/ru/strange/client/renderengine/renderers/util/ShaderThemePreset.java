package ru.strange.client.renderengine.renderers.util;

import java.util.Locale;

public enum ShaderThemePreset {
    COSMOS("Космос 2", 0xFF6A0DAD, 0xFFB45CFF, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, false),
    AURORA("Аврора", 0xFF2DE1C2, 0xFFB2FFF2, 0.75f, 0.82f, 1.18f, 1.08f, 0.80f, 0.60f, 0.28f, false),
    LAVA("Лава", 0xFFFF5A1F, 0xFFFFD15C, 0.58f, 0.94f, 1.26f, 0.92f, 1.35f, 0.42f, 0.08f, false),
    OCEAN("Океан", 0xFF0E6BFF, 0xFF68F3FF, 0.92f, 0.68f, 1.02f, 0.86f, 0.96f, 0.72f, 0.16f, false),
    MATRIX("Матрица", 0xFF00C853, 0xFFA7FFCF, 1.45f, 1.18f, 0.86f, 0.78f, 1.22f, 0.52f, 0.10f, false),
    TOXIN("Токсин", 0xFF73FF2F, 0xFFE2FF74, 0.82f, 0.92f, 1.16f, 1.02f, 1.14f, 0.56f, 0.08f, false),
    THUNDER("Гроза", 0xFF436CFF, 0xFFE6F2FF, 1.12f, 1.28f, 0.96f, 1.34f, 1.42f, 1.06f, 0.18f, false),
    CRYSTAL("Кристалл", 0xFF7B8DFF, 0xFFEAF2FF, 0.78f, 0.74f, 1.08f, 1.22f, 1.28f, 0.96f, 0.22f, false),
    SUNSET("Закат", 0xFFFF6B6B, 0xFFFFD36E, 0.62f, 0.76f, 1.20f, 0.92f, 0.86f, 0.46f, 0.06f, false),
    AMBER("Янтарь", 0xFFC97A15, 0xFFFFD88A, 0.54f, 0.66f, 1.06f, 0.88f, 1.04f, 0.34f, 0.08f, false),
    NEON("Неон", 0xFFFF00D4, 0xFF00F0FF, 1.28f, 1.06f, 1.10f, 1.24f, 1.30f, 0.92f, 0.18f, false),
    VOID("Пустота", 0xFF140A2A, 0xFF6A3DFF, 0.48f, 0.58f, 1.34f, 1.12f, 0.92f, 0.28f, 0.42f, false),
    SAKURA("Сакура", 0xFFFF8CCB, 0xFFFFDDF3, 0.72f, 0.78f, 1.02f, 0.98f, 0.82f, 0.58f, 0.20f, false),
    COBWEB("Паутина", 0xFFEEE6FF, 0xFF8D66FF, 0.20f, 0.54f, 0.96f, 0.84f, 1.44f, 0.08f, 0.00f, false),
    GLACIER("Ледник", 0xFF8FDEFF, 0xFFF0FBFF, 0.42f, 0.62f, 1.08f, 1.18f, 1.36f, 0.22f, 0.10f, false),
    SOLARIS("Солярис", 0xFFFFA100, 0xFFFFF0A6, 0.34f, 1.08f, 1.34f, 1.02f, 1.54f, 0.18f, 0.05f, false),
    FRACTAL("Фрактал", 0xFF6D4CFF, 0xFFFF7CE8, 0.78f, 0.88f, 1.30f, 1.10f, 1.68f, 0.74f, 0.18f, false),
    ECLIPSE("Затмение", 0xFF12091E, 0xFFFFB65E, 0.52f, 0.70f, 1.16f, 1.26f, 1.22f, 0.32f, 0.28f, false),
    CIRCUIT("Схема", 0xFF2EFFB3, 0xFFB8FFF0, 0.24f, 1.14f, 1.00f, 0.96f, 1.40f, 0.12f, 0.04f, false),
    CORAL("Коралл", 0xFFFF7F74, 0xFFFFD8B2, 0.60f, 0.72f, 1.10f, 0.92f, 1.24f, 0.26f, 0.10f, false),
    PRISM("Призма", 0xFF74B7FF, 0xFFFF7BFF, 0.86f, 0.94f, 1.20f, 1.28f, 1.62f, 0.92f, 0.18f, false),
    MONSOON("Муссон", 0xFF2F68C7, 0xFFBCE8FF, 0.46f, 1.04f, 1.12f, 1.18f, 1.18f, 0.24f, 0.12f, false),
    BLOOM("Блум", 0xFFFF74C6, 0xFFFFF4A8, 0.58f, 0.82f, 1.14f, 0.90f, 1.34f, 0.42f, 0.10f, false),
    PULSE_NEBULA("Небула", 0xFF6C3BFF, 0xFFFF87E8, 0.92f, 0.92f, 1.08f, 1.04f, 1.18f, 0.72f, 0.22f, true),
    PULSE_STARFIELD("Старфилд", 0xFF2E68FF, 0xFFBFE6FF, 1.18f, 1.16f, 1.22f, 1.06f, 1.10f, 1.28f, 0.96f, true),
    PULSE_COBWEB("Паутина", 0xFFEEE9FF, 0xFFA27BFF, 0.34f, 0.62f, 1.02f, 1.16f, 1.68f, 0.10f, 0.02f, true),
    PULSE_PLASMA("Плазма", 0xFFFF7A42, 0xFFFFF17A, 0.82f, 1.22f, 1.28f, 1.08f, 1.08f, 0.92f, 0.34f, true);

    private static final ShaderThemePreset[] SELECTABLE_PRESETS = {
            PULSE_NEBULA,
            PULSE_STARFIELD,
            PULSE_COBWEB,
            PULSE_PLASMA,
            COSMOS,
            AURORA,
            OCEAN,
            TOXIN,
            THUNDER,
            SUNSET,
            VOID,
            SOLARIS,
            FRACTAL,
            MONSOON,
            BLOOM
    };

    private final String displayName;
    private final int primaryColor;
    private final int accentColor;
    private final float densityScale;
    private final float speedScale;
    private final float intensityScale;
    private final float edgeScale;
    private final float patternScale;
    private final float sparkleScale;
    private final float starMix;
    private final boolean pulse;

    ShaderThemePreset(String displayName, int primaryColor, int accentColor,
                      float densityScale, float speedScale, float intensityScale,
                      float edgeScale, float patternScale, float sparkleScale, float starMix,
                      boolean pulse) {
        this.displayName = displayName;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
        this.densityScale = densityScale;
        this.speedScale = speedScale;
        this.intensityScale = intensityScale;
        this.edgeScale = edgeScale;
        this.patternScale = patternScale;
        this.sparkleScale = sparkleScale;
        this.starMix = starMix;
        this.pulse = pulse;
    }

    public String displayName() {
        if (this == COSMOS) {
            return "Космос";
        }
        return displayName;
    }

    public int primaryColor() {
        return primaryColor;
    }

    public int accentColor() {
        return accentColor;
    }

    public float densityScale() {
        return densityScale;
    }

    public float speedScale() {
        return speedScale;
    }

    public float intensityScale() {
        return intensityScale;
    }

    public float edgeScale() {
        return edgeScale;
    }

    public float patternScale() {
        return patternScale;
    }

    public float sparkleScale() {
        return sparkleScale;
    }

    public float starMix() {
        return starMix;
    }

    public boolean isPulse() {
        return pulse;
    }

    public int themeIndex() {
        return ordinal();
    }

    public static ShaderThemePreset byName(String name) {
        ShaderThemePreset preset = findByName(name);
        return preset == null ? COSMOS : toSelectablePreset(preset);
    }

    public static ShaderThemePreset findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        String normalized = normalizeKey(name);
        for (ShaderThemePreset preset : values()) {
            if (normalizeKey(preset.displayName).equals(normalized) || normalizeKey(preset.name()).equals(normalized)) {
                return preset;
            }
        }

        return switch (normalized) {
            case "cosmos", "cosmos2", "космос", "космос2" -> COSMOS;
            case "aurora" -> AURORA;
            case "ocean" -> OCEAN;
            case "toxin" -> TOXIN;
            case "thunder" -> THUNDER;
            case "sunset" -> SUNSET;
            case "void" -> VOID;
            case "solaris" -> SOLARIS;
            case "fractal" -> FRACTAL;
            case "monsoon" -> MONSOON;
            case "bloom" -> BLOOM;
            case "pulse", "pulsenebula", "nebula" -> PULSE_NEBULA;
            case "pulsestarfield", "starfield" -> PULSE_STARFIELD;
            case "pulsecobweb", "cobweb" -> PULSE_COBWEB;
            case "pulseplasma", "plasma" -> PULSE_PLASMA;
            case "lava", "amber" -> SOLARIS;
            case "matrix", "circuit" -> TOXIN;
            case "crystal", "glacier" -> OCEAN;
            case "sakura", "coral" -> BLOOM;
            case "eclipse" -> VOID;
            case "prism", "neon" -> FRACTAL;
            default -> null;
        };
    }

    public static boolean isPulseName(String name) {
        return byName(name).isPulse();
    }

    public static String[] names() {
        ShaderThemePreset[] values = selectablePresets();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].displayName();
        }
        return names;
    }

    public static ShaderThemePreset[] selectablePresets() {
        return SELECTABLE_PRESETS.clone();
    }

    public static String normalizeSelectableDisplayName(String name) {
        return byName(name).displayName();
    }

    private static ShaderThemePreset toSelectablePreset(ShaderThemePreset preset) {
        return switch (preset) {
            case LAVA, AMBER -> SOLARIS;
            case MATRIX, CIRCUIT -> TOXIN;
            case CRYSTAL, GLACIER -> OCEAN;
            case SAKURA, CORAL -> BLOOM;
            case ECLIPSE -> VOID;
            case PRISM, NEON -> FRACTAL;
            case COBWEB -> PULSE_COBWEB;
            default -> preset;
        };
    }

    private static String normalizeKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("•", "")
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
        return normalized;
    }

    public static int mixColors(int first, int second, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));

        int a1 = (first >> 24) & 0xFF;
        int r1 = (first >> 16) & 0xFF;
        int g1 = (first >> 8) & 0xFF;
        int b1 = first & 0xFF;

        int a2 = (second >> 24) & 0xFF;
        int r2 = (second >> 16) & 0xFF;
        int g2 = (second >> 8) & 0xFF;
        int b2 = second & 0xFF;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
