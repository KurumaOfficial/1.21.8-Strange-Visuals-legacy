package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.localization.LocalizationFormatUtil;
import ru.strange.client.localization.ResourceLocalizationLoader;

import java.util.Map;

public final class MenuLocalization {
    private static final String RU_RESOURCE = "/assets/strange/lang/menu_ru.json";
    private static final String EN_RESOURCE = "/assets/strange/lang/menu_en.json";

    private static final Map<String, String> RU = ResourceLocalizationLoader.load(RU_RESOURCE);
    private static final Map<String, String> EN = ResourceLocalizationLoader.load(EN_RESOURCE);

    private MenuLocalization() {
    }

    public static void initialize() {
        // Menu language follows the game's language dynamically.
    }

    public static MenuLanguage currentLanguage() {
        MinecraftClient client = MinecraftClient.getInstance();
        String languageCode = client == null || client.options == null ? null : client.options.language;
        return MenuLanguage.fromGameLanguageCode(languageCode);
    }

    public static String tr(String key, Object... args) {
        String template = currentMap().getOrDefault(key, EN.getOrDefault(key, key));
        return LocalizationFormatUtil.format("menu", key, template, args);
    }

    private static Map<String, String> currentMap() {
        return currentLanguage() == MenuLanguage.RU ? RU : EN;
    }
}
