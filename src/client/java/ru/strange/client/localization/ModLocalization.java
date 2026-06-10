package ru.strange.client.localization;

import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ModLocalization {
    private static final String MOD_RU_RESOURCE = "/assets/strange/lang/mod_ru.json";
    private static final String MOD_EN_RESOURCE = "/assets/strange/lang/mod_en.json";
    private static final String MOD_RAW_RU_RESOURCE = "/assets/strange/lang/mod_raw_ru.json";
    private static final String MOD_RAW_EN_RESOURCE = "/assets/strange/lang/mod_raw_en.json";
    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final String MOJIBAKE_MARKERS = "Ѓ‚„…†‡€‰™љњќћџЎўЉЊЋЏ¦µ";

    private static final Map<String, String> RU = ResourceLocalizationLoader.load(MOD_RU_RESOURCE);
    private static final Map<String, String> EN = ResourceLocalizationLoader.load(MOD_EN_RESOURCE);
    private static final Map<String, String> RU_RAW = ResourceLocalizationLoader.load(MOD_RAW_RU_RESOURCE);
    private static final Map<String, String> EN_RAW = ResourceLocalizationLoader.load(MOD_RAW_EN_RESOURCE);
    private static final Map<String, String> RU_RAW_NORMALIZED = normalizeRawMap(RU_RAW);
    private static final Map<String, String> EN_RAW_NORMALIZED = normalizeRawMap(EN_RAW);

    private ModLocalization() {
    }

    public static String tr(String key, Object... args) {
        String template = currentMap().getOrDefault(key, EN.getOrDefault(key, key));
        return LocalizationFormatUtil.format("mod", key, template, args);
    }

    public static String raw(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }

        String repaired = repairMojibakeIfNeeded(value);
        String resolved = resolveRawValue(repaired);
        return resolved != null ? resolved : repaired;
    }

    public static String rawEnglish(String value) {
        if (value == null || value.isBlank()) {
            return value == null ? "" : value;
        }

        String repaired = repairMojibakeIfNeeded(value);
        String resolved = resolveEnglishRawValue(repaired);
        return resolved != null ? resolved : repaired;
    }

    private static Map<String, String> currentMap() {
        return currentLanguage() == GuiLanguage.RU ? RU : EN;
    }

    private static Map<String, String> currentRawMap() {
        return currentLanguage() == GuiLanguage.RU ? RU_RAW : EN_RAW;
    }

    private static Map<String, String> currentNormalizedRawMap() {
        return currentLanguage() == GuiLanguage.RU ? RU_RAW_NORMALIZED : EN_RAW_NORMALIZED;
    }

    private static GuiLanguage currentLanguage() {
        GuiLocalization.initialize();
        return GuiLocalization.currentLanguage();
    }

    private static String resolveRawValue(String value) {
        String exact = currentRawMap().get(value);
        if (exact != null) {
            return exact;
        }

        exact = EN_RAW.get(value);
        if (exact != null) {
            return exact;
        }

        String normalizedKey = normalizeRawKey(value);
        String normalized = currentNormalizedRawMap().get(normalizedKey);
        if (normalized != null) {
            return normalized;
        }

        return EN_RAW_NORMALIZED.get(normalizedKey);
    }

    private static String resolveEnglishRawValue(String value) {
        String exact = EN_RAW.get(value);
        if (exact != null) {
            return exact;
        }

        String normalizedKey = normalizeRawKey(value);
        return EN_RAW_NORMALIZED.get(normalizedKey);
    }

    private static Map<String, String> normalizeRawMap(Map<String, String> values) {
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            normalized.putIfAbsent(normalizeRawKey(entry.getKey()), entry.getValue());
        }
        return normalized;
    }

    private static String normalizeRawKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String repairMojibakeIfNeeded(String value) {
        if (!looksLikeMojibake(value)) {
            return value;
        }

        try {
            String repaired = new String(value.getBytes(WINDOWS_1251), StandardCharsets.UTF_8);
            return repaired.chars().anyMatch(ch -> ch >= 0x0400 && ch <= 0x04FF) ? repaired : value;
        } catch (RuntimeException exception) {
            return value;
        }
    }

    private static boolean looksLikeMojibake(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (MOJIBAKE_MARKERS.indexOf(value.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }
}
