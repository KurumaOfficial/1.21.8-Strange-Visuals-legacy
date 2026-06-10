package ru.strange.client.module.impl.interfaces;

import net.minecraft.text.Text;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.manager.cfg.Config;
import ru.strange.client.manager.cfg.ConfigManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ButtonSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.api.setting.impl.StringSetting;
import ru.strange.client.utils.other.CapeUtil;

import java.util.ArrayList;
import java.util.List;

@IModule(
        name = "Клик гуи",
        description = "Настройки клик гуи (Ctrl+←/→ для переключения стиля)",
        category = Category.Interface,
        bind = -1
)
public class ClickGui extends Module {

    private static ClickGui INSTANCE;

    public static final String STYLE_CLASSIC = "Классика";
    public static final String STYLE_NEW = "Новый";

    public final ModeSetting guiStyle = new ModeSetting("Стиль гуи", STYLE_CLASSIC, STYLE_CLASSIC, STYLE_NEW);
    public final ModeSetting hudStyle = new ModeSetting("Стиль HUD", STYLE_CLASSIC, STYLE_CLASSIC, STYLE_NEW);
    public final BooleanSetting glassEffect = new BooleanSetting("Стекло", true);
    public final SliderSetting glassBlur = new SliderSetting("Блюр", 20.0f, 1.0f, 48.0f, 1.0f, false);
    public final SliderSetting glassAlpha = new SliderSetting("Прозрачность", 0.42f, 0.05f, 1.0f, 0.01f, true);

    public final StringSetting configName = new StringSetting("Имя конфига", Strange.DEFAULT_CONFIG_NAME);
    public final ButtonSetting saveConfigButton = new ButtonSetting("Конфиг: сохранить", 0, "Сохранить", this::saveConfigFromGui);
    public final ButtonSetting loadConfigButton = new ButtonSetting("Конфиг: загрузить", 1, "Загрузить", this::loadConfigFromGui);
    public final ButtonSetting deleteConfigButton = new ButtonSetting("Конфиг: удалить", 2, "Удалить", this::deleteConfigFromGui);
    public final ButtonSetting resetConfigButton = new ButtonSetting("Конфиг: сброс", 3, "Сброс", this::resetConfigFromGui);
    public final ButtonSetting listConfigButton = new ButtonSetting("Конфиг: список", 4, "Список", this::listConfigsFromGui);
    public final ButtonSetting openCapeFolderButton = new ButtonSetting("Плащи: папка", 5, "Открыть папку", this::openCapeFolder);

    public ClickGui() {
        INSTANCE = this;
        glassBlur.hidden = () -> !glassEffect.get();
        glassAlpha.hidden = () -> !glassEffect.get();
        addSettings(
                guiStyle,
                hudStyle,
                glassEffect,
                glassBlur,
                glassAlpha,
                configName,
                saveConfigButton,
                loadConfigButton,
                deleteConfigButton,
                resetConfigButton,
                listConfigButton,
                openCapeFolderButton
        );
    }

    public static ClickGui getInstance() {
        return INSTANCE;
    }

    public boolean isNewStyle() {
        return guiStyle.is(STYLE_NEW);
    }

    public boolean isNewHudStyle() {
        return hudStyle.is(STYLE_NEW);
    }

    public boolean isGlassEnabled() {
        return glassEffect.get();
    }

    public float getGlassBlur() {
        return glassBlur.get();
    }

    public float getGlassAlpha() {
        return glassAlpha.get();
    }

    private void saveConfigFromGui() {
        ConfigManager manager = resolveConfigManager();
        if (manager == null) {
            notifyLocal("Менеджер конфигов недоступен");
            return;
        }

        String name = resolveConfigName(manager, true);
        if (name == null) {
            return;
        }

        boolean saved = manager.saveSnapshot(name);
        notifyLocal(saved ? "Конфиг сохранен: " + name : "Не удалось сохранить конфиг: " + name);
    }

    private void loadConfigFromGui() {
        ConfigManager manager = resolveConfigManager();
        if (manager == null) {
            notifyLocal("Менеджер конфигов недоступен");
            return;
        }

        String name = resolveConfigName(manager, false);
        if (name == null) {
            notifyLocal("Введите имя конфига");
            return;
        }

        boolean loaded = manager.loadSnapshot(name);
        notifyLocal(loaded ? "Конфиг загружен: " + name : "Не удалось загрузить конфиг: " + name);
    }

    private void deleteConfigFromGui() {
        ConfigManager manager = resolveConfigManager();
        if (manager == null) {
            notifyLocal("Менеджер конфигов недоступен");
            return;
        }

        String name = resolveConfigName(manager, false);
        if (name == null) {
            notifyLocal("Введите имя конфига");
            return;
        }

        boolean deleted = manager.deleteConfig(name);
        notifyLocal(deleted ? "Конфиг удален: " + name : "Не удалось удалить конфиг: " + name);
    }

    private void resetConfigFromGui() {
        ConfigManager manager = resolveConfigManager();
        if (manager == null) {
            notifyLocal("Менеджер конфигов недоступен");
            return;
        }

        boolean reset = manager.resetToDefaults();
        notifyLocal(reset ? "Конфиг сброшен к стандартным" : "Не удалось сбросить конфиг");
    }

    private void listConfigsFromGui() {
        ConfigManager manager = resolveConfigManager();
        if (manager == null) {
            notifyLocal("Менеджер конфигов недоступен");
            return;
        }

        List<String> names = new ArrayList<>();
        for (Config config : manager.getLoadedConfigs()) {
            names.add(config.getName());
        }

        if (names.isEmpty()) {
            notifyLocal("Список конфигов пуст");
            return;
        }

        String joined = String.join(", ", names);
        notifyLocal("Список конфигов: " + joined);
    }

    private ConfigManager resolveConfigManager() {
        if (Strange.get == null) {
            return null;
        }
        return Strange.get.configManager;
    }

    private String resolveConfigName(ConfigManager manager, boolean fallbackToActive) {
        String rawName = configName.get();
        if (rawName == null || rawName.isBlank()) {
            return fallbackToActive ? manager.getActiveConfigName() : null;
        }

        String normalized = manager.normalizeConfigName(rawName);
        if (normalized == null) {
            notifyLocal("Некорректное имя конфига");
            return null;
        }
        return normalized;
    }

    private void notifyLocal(String message) {
        if (mc == null || mc.player == null || message == null || message.isBlank()) {
            return;
        }
        mc.player.sendMessage(Text.literal(ModLocalization.raw(message)), false);
    }

    private void openCapeFolder() {
        CapeUtil.openCapeDirectory();
    }
}
