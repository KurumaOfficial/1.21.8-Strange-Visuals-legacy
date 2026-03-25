package ru.strange.client.ui.clickgui.localization;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.api.Category;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GuiLocalization {
    private static final File SETTINGS_FILE = new File(Strange.root, "gui-settings.json");

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    private static boolean initialized;
    private static GuiLanguage currentLanguage;

    static {
        register("gui.category.player", "На игроке", "Player");
        register("gui.category.world", "В мире", "World");
        register("gui.category.utilities", "Утилиты", "Utils");
        register("gui.category.other", "Остальное", "Other");
        register("gui.category.interface", "Интерфейс", "UI");
        register("gui.category.theme", "Темы", "Themes");

        register("gui.theme.white", "Белая", "White");
        register("gui.theme.black", "Черная", "Black");
        register("gui.theme.transparent_white", "Прозрачная белая", "Glass White");
        register("gui.theme.transparent_black", "Прозрачная черная", "Glass Black");
        register("gui.theme.pink", "Розовая", "Rose");
        register("gui.theme.purple", "Фиолетовая", "Purple");

        register("gui.status.enabled", "ВКЛЮЧЕНО", "ENABLED");
        register("gui.status.disabled", "ВЫКЛЮЧЕНО", "DISABLED");
        register("gui.list.empty", "Пусто", "Empty");

        register("gui.rule.badge", "Правило %s -%s", "Rule %s -%s");
        register("gui.empty.profile_title", "Модули скрыты", "Modules hidden");
        register("gui.empty.profile_subtitle", "Текущий профиль сервера скрыл эту вкладку", "The current server profile hides this tab");
        register("gui.empty.category_title", "Пока пусто", "Nothing here");
        register("gui.empty.category_subtitle", "Попробуйте другую категорию", "Try another category");

        register("gui.lang.title", "Язык GUI", "GUI language");
        register("gui.lang.ru", "Русский", "Russian");
        register("gui.lang.en", "English", "English");
    }

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
        String template = currentMap().getOrDefault(key, EN.getOrDefault(key, key));
        if (args == null || args.length == 0) {
            return template;
        }
        return String.format(Locale.ROOT, template, args);
    }

    public static String categoryName(Category category, String fallback) {
        return switch (category) {
            case Player -> tr("gui.category.player");
            case World -> tr("gui.category.world");
            case Utilities -> tr("gui.category.utilities");
            case Other -> tr("gui.category.other");
            case Interface -> tr("gui.category.interface");
            case Theme -> tr("gui.category.theme");
        };
    }

    public static String themeName(Theme theme, String fallback) {
        return switch (theme) {
            case WHITE -> tr("gui.theme.white");
            case BLACK -> tr("gui.theme.black");
            case TRANSPARENT_WHITE -> tr("gui.theme.transparent_white");
            case TRANSPARENT_BLACK -> tr("gui.theme.transparent_black");
            case PINK -> tr("gui.theme.pink");
            case PURPLE -> tr("gui.theme.purple");
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

    private static void load() {
        if (!SETTINGS_FILE.isFile()) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(SETTINGS_FILE.toPath(), StandardCharsets.UTF_8)) {
            JsonObject object = JsonParser.parseReader(reader).getAsJsonObject();
            if (object.has("language")) {
                currentLanguage = GuiLanguage.byCode(object.get("language").getAsString());
            }
        } catch (IOException | RuntimeException exception) {
            Strange.LOGGER.warn("Failed to load GUI localization settings {}", SETTINGS_FILE.getAbsolutePath(), exception);
        }
    }

    private static void save() {
        JsonObject object = new JsonObject();
        object.addProperty("language", currentLanguage.code());

        try {
            Files.createDirectories(SETTINGS_FILE.toPath().getParent());
            try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE.toPath(), StandardCharsets.UTF_8)) {
                writer.write(object.toString());
            }
        } catch (IOException exception) {
            Strange.LOGGER.warn("Failed to save GUI localization settings {}", SETTINGS_FILE.getAbsolutePath(), exception);
        }
    }

    private static void register(String key, String ruValue, String enValue) {
        RU.put(key, ruValue);
        EN.put(key, enValue);
    }
}
