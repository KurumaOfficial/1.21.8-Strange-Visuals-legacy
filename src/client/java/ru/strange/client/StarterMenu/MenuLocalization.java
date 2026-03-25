package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MenuLocalization {
    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        register("menu.singleplayer", "Одиночная игра", "Singleplayer");
        register("menu.multiplayer", "Сетевая игра", "Multiplayer");
        register("menu.alt_manager", "Alt Manager", "Alt Manager");
        register("menu.options", "Настройки", "Options");
        register("menu.exit", "Выход", "Exit");
        register("menu.version", "Версия", "Version");
        register("menu.greeting.morning", "Доброе утро", "Good morning");
        register("menu.greeting.afternoon", "Добрый день", "Good afternoon");
        register("menu.greeting.evening", "Добрый вечер", "Good evening");
        register("menu.greeting.night", "Доброй ночи", "Good night");

        register("common.yes", "Да", "Yes");
        register("common.no", "Нет", "No");
        register("common.back", "Назад", "Back");
        register("common.delete", "Удалить", "Delete");
        register("common.use", "Применить", "Use");
        register("common.close", "Закрыть", "Close");

        register("alt.title", "Alt Manager", "Alt Manager");
        register("alt.subtitle", "Локальные офлайн-профили и быстрый выбор ника", "Local offline profiles and quick nickname switching");
        register("alt.active_profiles", "Активные профили", "Active profiles");
        register("alt.deleted_profiles", "Удаленные профили", "Deleted profiles");
        register("alt.total_profiles", "Профилей: %s", "Profiles: %s");
        register("alt.current_profile", "Текущий раздел", "Current section");
        register("alt.current_session", "Текущий ник", "Current nickname");
        register("alt.no_profiles", "Профили еще не добавлены", "No profiles yet");
        register("alt.no_selection", "Профиль не выбран", "No profile selected");
        register("alt.deleted_title", "Удаленные профили", "Deleted profiles");
        register("alt.deleted_empty", "Удаленных профилей нет", "No deleted profiles");
        register("alt.nickname", "Никнейм", "Nickname");
        register("alt.deleted_button", "Удаленные", "Deleted");
        register("alt.toggle_active", "Активные", "Active");
        register("alt.delete_all", "Удалить все", "Delete all");
        register("alt.clear_deleted", "Очистить удаленные", "Clear deleted");
        register("alt.clear_deleted_done", "Удаленные профили очищены", "Deleted profiles cleared");
        register("alt.random", "Рандом", "Random");
        register("alt.add", "Добавить", "Add");
        register("alt.restore", "Вернуть", "Restore");
        register("alt.created", "Создан", "Created");
        register("alt.pinned", "Закреплен", "Pinned");
        register("alt.sort", "Сортировка", "Sort");
        register("alt.hint", "Выберите профиль слева и используйте действия справа", "Select a profile on the left and use actions on the right");
        register("alt.list_hint", "ЛКМ выбирает профиль, колесо мыши листает список", "Left click selects a profile, mouse wheel scrolls");
        register("alt.invalid_name_hint", "Ник: 3-16 символов, буквы/цифры/_", "Nickname: 3-16 chars, letters/digits/_");
        register("alt.exists", "Такой профиль уже есть", "This profile already exists");
        register("alt.added", "Добавлен: %s", "Added: %s");
        register("alt.generated", "Сгенерирован: %s", "Generated: %s");
        register("alt.deleted_one", "Удален: %s", "Deleted: %s");
        register("alt.deleted_permanent", "Удален навсегда: %s", "Deleted permanently: %s");
        register("alt.restore_profile", "Восстановить профиль", "Restore profile");
        register("alt.restored", "Восстановлен: %s", "Restored: %s");
        register("alt.deleted_all_done", "Все профили перенесены в удаленные", "All profiles moved to deleted");
        register("alt.invalid_name", "Некорректный ник", "Invalid nickname");
        register("alt.change_in_game", "Меняйте профиль только из главного меню", "Change profiles only from the main menu");
        register("alt.change_failed", "Не удалось сменить профиль", "Failed to switch profile");
        register("alt.changed", "Профиль: %s", "Profile: %s");
        register("alt.save_failed", "Ошибка сохранения alt-профилей", "Failed to save alt profiles");
        register("alt.sort.newest", "Сначала новые", "Newest first");
        register("alt.sort.oldest", "Сначала старые", "Oldest first");
        register("alt.sort.az", "A-Z", "A-Z");
        register("alt.sort.za", "Z-A", "Z-A");
        register("alt.use_profile", "Применить профиль", "Use profile");
        register("alt.pin_profile", "Закрепить", "Pin");
        register("alt.unpin_profile", "Открепить", "Unpin");
        register("alt.delete_profile", "Удалить профиль", "Delete profile");
        register("alt.delete_forever", "Удалить навсегда", "Delete forever");
        register("alt.deleted_panel_hint", "Удаленный профиль можно восстановить или удалить навсегда", "Deleted profiles can be restored or deleted forever");
        register("alt.current_badge", "Текущий", "Current");
        register("alt.pinned_badge", "PIN", "PIN");
        register("alt.list_title", "Профили", "Profiles");
        register("alt.details_title", "Детали", "Details");
        register("alt.add_hint", "Добавьте ник вручную или сгенерируйте случайный", "Add a nickname manually or generate one");
    }

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
        if (args == null || args.length == 0) {
            return template;
        }
        return String.format(Locale.ROOT, template, args);
    }

    private static Map<String, String> currentMap() {
        return currentLanguage() == MenuLanguage.RU ? RU : EN;
    }

    private static void register(String key, String ruValue, String enValue) {
        RU.put(key, ruValue);
        EN.put(key, enValue);
    }
}
