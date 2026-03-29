package ru.strange.client.module.impl.utilities;

import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

@IModule(
        name = "Чат Хелпер",
        description = "Анти-спам и сохранение истории чата",
        category = Category.Utilities,
        bind = -1
)
public class ChatHelper extends Module {

    private static volatile ChatHelper instance;

    private final BooleanSetting antiSpam = new BooleanSetting("Анти-спам", true);
    private final BooleanSetting keepHistory = new BooleanSetting("Сохранять историю", true);

    /**
     * Singleton инициализируется через конструктор, вызываемый Manager
     * один раз при загрузке модулей.
     */
    public ChatHelper() {
        if (instance != null) {
            throw new IllegalStateException("ChatHelper module already initialized");
        }
        instance = this;
        addSettings(antiSpam, keepHistory);
    }

    public static ChatHelper getInstance() {
        return instance;
    }

    public static boolean isAntiSpamEnabled() {
        return instance != null && instance.enable && instance.antiSpam.get();
    }

    public static boolean isKeepHistoryEnabled() {
        return instance != null && instance.enable && instance.keepHistory.get();
    }
}