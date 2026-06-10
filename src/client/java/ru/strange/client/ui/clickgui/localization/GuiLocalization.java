package ru.strange.client.ui.clickgui.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.localization.LocalizationFormatUtil;
import ru.strange.client.localization.ResourceLocalizationLoader;
import ru.strange.client.module.Theme;
import ru.strange.client.module.api.Category;
import ru.strange.client.utils.io.AtomicFileIO;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class GuiLocalization {
    private static final File SETTINGS_FILE = new File(Strange.root, "gui-settings.json");
    private static final String RU_RESOURCE = "/assets/strange/lang/gui_ru.json";
    private static final String EN_RESOURCE = "/assets/strange/lang/gui_en.json";

    private static final Map<String, String> RU = ResourceLocalizationLoader.load(RU_RESOURCE);
    private static final Map<String, String> EN = ResourceLocalizationLoader.load(EN_RESOURCE);

    private static boolean initialized;
    private static GuiLanguage currentLanguage;

    private GuiLocalization() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        load();
        if (currentLanguage == null) {
            currentLanguage = detectFromGame();
        }
    }

    public static synchronized GuiLanguage currentLanguage() {
        initialize();
        return currentLanguage;
    }

    public static synchronized void setLanguage(GuiLanguage language) {
        initialize();
        currentLanguage = language == null ? detectFromGame() : language;
        save();
    }

    public static synchronized String tr(String key, Object... args) {
        initialize();
        String template = translateOrDefault(key, key);
        return LocalizationFormatUtil.format("gui", key, template, args);
    }

    public static String categoryName(Category category, String fallback) {
        return switch (category) {
            case Player -> translateOrDefault("gui.category.player", fallback);
            case World -> translateOrDefault("gui.category.world", fallback);
            case Utilities -> translateOrDefault("gui.category.utilities", fallback);
            case Other -> translateOrDefault("gui.category.other", fallback);
            case Interface -> translateOrDefault("gui.category.interface", fallback);
            case Theme -> translateOrDefault("gui.category.theme", fallback);
        };
    }

    public static String themeName(Theme theme, String fallback) {
        return switch (theme) {
            case WHITE -> translateOrDefault("gui.theme.white", fallback);
            case BLACK -> translateOrDefault("gui.theme.black", fallback);
            case TRANSPARENT_WHITE -> translateOrDefault("gui.theme.transparent_white", fallback);
            case TRANSPARENT_BLACK -> translateOrDefault("gui.theme.transparent_black", fallback);
            case PINK -> translateOrDefault("gui.theme.pink", fallback);
            case PURPLE -> translateOrDefault("gui.theme.purple", fallback);
            case NEON -> translateOrDefault("gui.theme.neon", fallback);
        };
    }

    private static GuiLanguage detectFromGame() {
        MinecraftClient client = MinecraftClient.getInstance();
        String languageCode = client == null || client.options == null ? null : client.options.language;
        return GuiLanguage.fromGameLanguageCode(languageCode);
    }

    private static Map<String, String> currentMap() {
        return currentLanguage == GuiLanguage.RU ? RU : EN;
    }

    private static String translateOrDefault(String key, String fallback) {
        initialize();
        String resolvedFallback = fallback == null || fallback.isBlank() ? key : fallback;
        return currentMap().getOrDefault(key, EN.getOrDefault(key, resolvedFallback));
    }

    private static void load() {
        Path settingsPath = SETTINGS_FILE.toPath();
        Path tempPath = tempSettingsPath();
        if (!SETTINGS_FILE.isFile() && !Files.isRegularFile(tempPath)) {
            return;
        }

        JsonObject object = tryReadSettings(settingsPath, false);
        if (object == null) {
            object = tryReadSettings(tempPath, true);
            if (object != null) {
                promoteRecoveredSettings(tempPath, settingsPath);
            } else {
                return;
            }
        }

        JsonElement languageElement = object.get("language");
        if (languageElement != null && languageElement.isJsonPrimitive() && languageElement.getAsJsonPrimitive().isString()) {
            currentLanguage = GuiLanguage.byCode(languageElement.getAsString());
        }
    }

    private static void save() {
        JsonObject object = new JsonObject();
        object.addProperty("language", currentLanguage.code());

        Path settingsPath = SETTINGS_FILE.toPath();
        try {
            AtomicFileIO.writeUtf8StringAtomically(settingsPath, object.toString());
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to save GUI localization settings {}", SETTINGS_FILE.getAbsolutePath(), exception);
        }
    }

    private static JsonObject tryReadSettings(Path path, boolean temporaryFile) {
        if (!Files.isRegularFile(path)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                Strange.LOGGER.warn("GUI localization settings in {} are not a JSON object", path.toAbsolutePath());
                return null;
            }
            return parsed.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            String sourceType = temporaryFile ? "temporary GUI localization settings" : "GUI localization settings";
            Strange.LOGGER.warn("Failed to load {} from {}", sourceType, path.toAbsolutePath(), exception);
            return null;
        }
    }

    private static void promoteRecoveredSettings(Path tempPath, Path settingsPath) {
        try {
            AtomicFileIO.moveReplace(tempPath, settingsPath);
            Strange.LOGGER.warn("Recovered GUI localization settings from temporary file {}", tempPath.toAbsolutePath());
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to promote temporary GUI localization settings {}", tempPath.toAbsolutePath(), exception);
        }
    }

    private static Path tempSettingsPath() {
        return AtomicFileIO.tempPath(SETTINGS_FILE.toPath());
    }
}
