package ru.strange.client.manager.cfg;

import com.google.gson.JsonObject;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Module;
import ru.strange.client.ui.clickgui.GuiScreen;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class Config implements ConfigUpdater {

    private final String name;
    private final File file;

    public Config(String name) {
        this.name = name;
        this.file = Strange.get != null && Strange.get.configManager != null
                ? Strange.get.configManager.resolveConfigFile(name)
                : new File(ConfigManager.configDirectory, name + ".json");
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    @Override
    public JsonObject save() {
        JsonObject jsonObject = new JsonObject();
        JsonObject modulesObject = new JsonObject();

        for (Module module : Strange.get.manager.getModules()) {
            modulesObject.add(module.name, module.save());
        }

        jsonObject.add("Features", modulesObject);
        jsonObject.addProperty("Theme", ThemeManager.getTheme().name());

        JsonObject guiObject = new JsonObject();
        guiObject.addProperty("selectedCategory", GuiScreen.selectedCategories.name());
        jsonObject.add("Gui", guiObject);
        return jsonObject;
    }

    @Override
    public void load(JsonObject object) {
        if (object.has("Features") && object.get("Features").isJsonObject()) {
            JsonObject modulesObject = object.getAsJsonObject("Features");
            List<Module> modulesToEnable = new ArrayList<>();
            for (Module module : Strange.get.manager.getModules()) {
                module.setEnable(false);

                boolean shouldEnable = module.restoreDefaultState();
                if (modulesObject.has(module.name) && modulesObject.get(module.name).isJsonObject()) {
                    shouldEnable = module.load(modulesObject.getAsJsonObject(module.name));
                }

                if (shouldEnable) {
                    modulesToEnable.add(module);
                }
            }

            for (Module module : modulesToEnable) {
                module.setEnable(true);
            }
        }

        String themeName = readStringProperty(object, "Theme");
        if (themeName != null) {
            try {
                Theme theme = Theme.valueOf(themeName);
                ThemeManager.setTheme(theme, false);
                ThemeManager.finishAnimation();
            } catch (IllegalArgumentException exception) {
                Strange.LOGGER.warn("Failed to load theme {} from config {}", themeName, name, exception);
            }
        }

        if (object.has("Gui") && object.get("Gui").isJsonObject()) {
            JsonObject guiObject = object.getAsJsonObject("Gui");
            String categoryName = readStringProperty(guiObject, "selectedCategory");
            if (categoryName != null) {
                try {
                    GuiScreen.selectedCategories = ru.strange.client.module.api.Category.valueOf(categoryName);
                    GuiScreen.modules = Strange.get.manager.getType(GuiScreen.selectedCategories);
                    GuiScreen.scroll.reset();
                } catch (IllegalArgumentException exception) {
                    Strange.LOGGER.warn("Failed to load GUI category {} from config {}", categoryName, name, exception);
                }
            }
        }
    }

    private static String readStringProperty(JsonObject object, String propertyName) {
        if (object == null || propertyName == null || !object.has(propertyName)) {
            return null;
        }

        var element = object.get(propertyName);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString()
                : null;
    }
}
