package ru.strange.client.manager.cfg;

import com.google.gson.JsonObject;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.other.ModuleSounds;
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

        for (Module module : Strange.get.manager.module) {
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
        if (object.has("Features")) {
            JsonObject modulesObject = object.getAsJsonObject("Features");
            List<Module> modulesToEnable = new ArrayList<>();
            for (Module module : Strange.get.manager.module) {
                module.setEnable(false);
                if (modulesObject.has(module.name)) {
                    boolean shouldEnable = module.load(modulesObject.getAsJsonObject(module.name));
                    if (shouldEnable) {
                        modulesToEnable.add(module);
                    }
                } else if (module instanceof ModuleSounds) {
                    modulesToEnable.add(module);
                }
            }

            for (Module module : modulesToEnable) {
                module.setEnable(true);
            }
        }

        if (object.has("Theme")) {
            try {
                Theme theme = Theme.valueOf(object.get("Theme").getAsString());
                ThemeManager.setTheme(theme);
                ThemeManager.finishAnimation();
                GuiScreen.selectedTheme = theme;
                GuiScreen.preSelectedTheme = theme;
            } catch (Exception e) {
                Strange.LOGGER.warn("Failed to load theme from config {}", name, e);
            }
        }

        if (object.has("Gui") && object.get("Gui").isJsonObject()) {
            JsonObject guiObject = object.getAsJsonObject("Gui");
            if (guiObject.has("selectedCategory")) {
                try {
                    GuiScreen.selectedCategories = ru.strange.client.module.api.Category.valueOf(guiObject.get("selectedCategory").getAsString());
                    GuiScreen.modules = Strange.get.manager.getType(GuiScreen.selectedCategories);
                    GuiScreen.scroll.reset();
                } catch (Exception e) {
                    Strange.LOGGER.warn("Failed to load GUI category from config {}", name, e);
                }
            }
        }
    }
}
