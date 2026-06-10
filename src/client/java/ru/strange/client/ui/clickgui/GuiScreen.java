package ru.strange.client.ui.clickgui;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.utils.other.ModuleVisibilityUtil;
import ru.strange.client.utils.math.ScrollUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class GuiScreen {
    private static final float MODULE_PANEL_X_OFFSET = 7.0F;
    private static final float MODULE_PANEL_Y_OFFSET = 55.0F;
    private static final float MODULE_PANEL_WIDTH = 195.0F;
    private static final float MODULE_HEADER_HEIGHT = 22.0F;
    private static final float MODULE_VERTICAL_SPACING = 26F;

    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static ScrollUtil scroll = new ScrollUtil();

    public static float x, y;
    public static float width, height;

    public static Category[] categories;
    public static List<Module> modules;
    public static Theme[] themes;
    public static Category selectedCategories = Category.World;
    public static String searchQuery = "";
    public static boolean searchActive = false;

    public static void resetTransientInteractionState() {
        if (Strange.get == null || Strange.get.manager == null) {
            return;
        }

        searchQuery = "";
        searchActive = false;

        for (Category category : Category.values()) {
            for (Module module : Strange.get.manager.getType(category)) {
                module.binding = false;
                module.displayName = module.name;

                for (Setting setting : module.getSettingsForGUI()) {
                    if (setting instanceof SliderSetting slider) {
                        slider.sliding = false;
                    }
                    if (setting instanceof HueSetting hue) {
                        hue.sliding = false;
                        hue.colorSliding = false;
                    }
                    if (setting instanceof BindSettings bind) {
                        bind.active = false;
                    }
                    if (setting instanceof StringSetting string) {
                        string.active = false;
                    }
                }
            }
        }
    }

    public static void resetDragInteractionState() {
        if (Strange.get == null || Strange.get.manager == null) {
            return;
        }

        for (Category category : Category.values()) {
            for (Module module : Strange.get.manager.getType(category)) {
                for (Setting setting : module.getSettingsForGUI()) {
                    if (setting instanceof SliderSetting slider) {
                        slider.sliding = false;
                    }
                    if (setting instanceof HueSetting hue) {
                        hue.sliding = false;
                        hue.colorSliding = false;
                    }
                }
            }
        }
    }

    public static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static ModulePanel modulePanelBounds() {
        return new ModulePanel(
                x + MODULE_PANEL_X_OFFSET,
                y + MODULE_PANEL_Y_OFFSET,
                MODULE_PANEL_WIDTH,
                height - MODULE_PANEL_Y_OFFSET - 10.0F
        );
    }

    public static float moduleHeaderHeight() {
        return MODULE_HEADER_HEIGHT;
    }

    public static float moduleVerticalSpacing() {
        return MODULE_VERTICAL_SPACING;
    }

    public static List<Module> visibleModules() {
        List<Module> visible = new ArrayList<>();

        Category[] cats = searchQuery.isBlank() ? new Category[]{selectedCategories} : Category.values();

        for (Category cat : cats) {
            List<Module> list = Strange.get.manager.getType(cat);
            if (list == null) continue;
            for (Module module : list) {
                if (ModuleVisibilityUtil.shouldShow(module) && matchesSearch(module)) {
                    visible.add(module);
                }
            }
        }

        return visible;
    }

    public static boolean matchesSearch(Module module) {
        String query = normalizeSearch(searchQuery);
        if (query.isEmpty()) {
            return true;
        }
        return containsSearchQuery(module.name, query)
                || containsSearchQuery(module.getLocalizedName(), query)
                || containsSearchQuery(ModLocalization.rawEnglish(module.name), query)
                || containsSearchQuery(module.description, query)
                || containsSearchQuery(module.getLocalizedDescription(), query)
                || containsSearchQuery(ModLocalization.rawEnglish(module.description), query);
    }

    private static boolean containsSearchQuery(String value, String query) {
        return normalizeSearch(value).contains(query);
    }

    private static String normalizeSearch(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static SettingsColumns splitSettingsByColumn(Module module) {
        List<Setting> left = new ArrayList<>();
        List<Setting> right = new ArrayList<>();
        if (module == null) {
            return new SettingsColumns(left, right);
        }

        List<Setting> settings = module.getSettingsForGUI();
        for (int i = 0; i < settings.size(); i++) {
            Setting setting = settings.get(i);
            if ((i & 1) == 0) {
                left.add(setting);
            } else {
                right.add(setting);
            }
        }

        return new SettingsColumns(left, right);
    }

    public static float calcUP(Module module) {
        if (module == null || module.getSettingsForGUI().isEmpty() || !module.open) {
            return 0.0F;
        }

        SettingsColumns settingsColumns = splitSettingsByColumn(module);
        return Math.max(measureSettingsColumnHeight(settingsColumns.left()), measureSettingsColumnHeight(settingsColumns.right()));
    }

    private static float measureSettingsColumnHeight(List<Setting> settings) {
        float totalHeight = 0.0F;
        for (Setting setting : settings) {
            totalHeight += measureSettingHeight(setting);
        }
        return totalHeight;
    }

    private static float measureSettingHeight(Setting setting) {
        if (setting instanceof BooleanSetting s) {
            return s.hidden.get() ? 0.0F : 20.0F;
        }
        if (setting instanceof SliderSetting s) {
            return s.hidden.get() ? 0.0F : 20.0F;
        }
        if (setting instanceof BindSettings s) {
            return s.hidden.get() ? 0.0F : 20.0F;
        }
        if (setting instanceof ButtonSetting s) {
            return s.hidden.get() ? 0.0F : 20.0F;
        }
        if (setting instanceof StringSetting s) {
            return s.hidden.get() ? 0.0F : 20.0F;
        }
        if (setting instanceof ModeSetting s) {
            if (s.hidden.get()) {
                return 0.0F;
            }
            return 20.0F + (s.opened ? s.modes.size() * 6.0F : 0.0F);
        }
        if (setting instanceof MultiBooleanSetting s) {
            if (s.hidden.get()) {
                return 0.0F;
            }
            return 20.0F + (s.opened ? s.settings.size() * 6.0F : 0.0F);
        }
        if (setting instanceof ListSetting s) {
            if (s.hidden.get()) {
                return 0.0F;
            }
            return 20.0F + (s.opened ? s.list.size() * 6.0F : 0.0F);
        }
        if (setting instanceof HueSetting s) {
            if (s.hidden.get()) {
                return 0.0F;
            }
            return 20.0F + (s.opened ? 80.0F : 0.0F);
        }
        return 0.0F;
    }

    public record ModulePanel(float x, float y, float width, float height) {
    }

    public record SettingsColumns(List<Setting> left, List<Setting> right) {
    }
}
