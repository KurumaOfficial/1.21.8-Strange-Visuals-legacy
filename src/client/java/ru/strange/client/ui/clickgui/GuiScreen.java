package ru.strange.client.ui.clickgui;

import net.minecraft.client.MinecraftClient;
import ru.strange.client.module.Theme;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.utils.math.ScrollUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GuiScreen {
    public static MinecraftClient mc = MinecraftClient.getInstance();
    public static ScrollUtil scroll = new ScrollUtil();

    public static float x, y;
    public static float width, height;

    public static Category[] categories;
    public static List<Module> modules;
    public static Theme[] themes;
    public static Category selectedCategories = Category.World;
    public static Theme selectedTheme = Theme.WHITE;
    public static Theme preSelectedTheme;

    public static boolean isHovered(double mouseX, double mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public static float calcUP(Module module) {
        java.util.List<Setting> settings1 = new ArrayList<>();
        java.util.List<Setting> settings2 = new ArrayList<>();

        for (int i = 0; i < module.getSettingsForGUI().size(); i++) {
            Setting setting = module.getSettingsForGUI().get(i);
            if (i % 2 == 0) {
                settings1.add(setting);
            } else {
                settings2.add(setting);
            }
        }

        float up1 = 0;
        float up2 = 0;

        if (!module.getSettingsForGUI().isEmpty() && module.open) {
            for (Setting setting : settings1) {
                float widthSettings = 102;
                float heightSettings = 16;

                if (setting instanceof BooleanSetting) {
                    BooleanSetting s = (BooleanSetting) setting;
                    if (s.hidden.get()) continue;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof SliderSetting) {
                    SliderSetting s = (SliderSetting) setting;
                    if (s.hidden.get()) continue;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof ModeSetting) {
                    ModeSetting s = (ModeSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up1 += s.modes.size() * 6;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof MultiBooleanSetting) {
                    MultiBooleanSetting s = (MultiBooleanSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up1 += s.settings.size() * 6;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof ListSetting) {
                    ListSetting s = (ListSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up1 += s.list.size() * 6;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof BindSettings) {
                    BindSettings s = (BindSettings) setting;
                    if (s.hidden.get()) continue;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof ButtonSetting) {
                    ButtonSetting s = (ButtonSetting) setting;
                    if (s.hidden.get()) continue;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof StringSetting) {
                    StringSetting s = (StringSetting) setting;
                    if (s.hidden.get()) continue;
                    up1 += heightSettings + 4;
                }
                if (setting instanceof HueSetting) {
                    HueSetting s = (HueSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up1 += 80;
                    up1 += heightSettings + 4;
                }
            }



            for (Setting setting : settings2) {
                float widthSettings = 102;
                float heightSettings = 16;

                if (setting instanceof BooleanSetting) {
                    BooleanSetting s = (BooleanSetting) setting;
                    if (s.hidden.get()) continue;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof SliderSetting) {
                    SliderSetting s = (SliderSetting) setting;
                    if (s.hidden.get()) continue;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof ModeSetting) {
                    ModeSetting s = (ModeSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up2 += s.modes.size() * 6;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof MultiBooleanSetting) {
                    MultiBooleanSetting s = (MultiBooleanSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up2 += s.settings.size() * 6;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof ListSetting) {
                    ListSetting s = (ListSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up2 += s.list.size() * 6;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof BindSettings) {
                    BindSettings s = (BindSettings) setting;
                    if (s.hidden.get()) continue;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof ButtonSetting) {
                    ButtonSetting s = (ButtonSetting) setting;
                    if (s.hidden.get()) continue;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof StringSetting) {
                    StringSetting s = (StringSetting) setting;
                    if (s.hidden.get()) continue;
                    up2 += heightSettings + 4;
                }
                if (setting instanceof HueSetting) {
                    HueSetting s = (HueSetting) setting;
                    if (s.hidden.get()) continue;
                    if (s.opened) up2 += 80;
                    up2 += heightSettings + 4;
                }
            }
        }

        return Math.max(up1, up2);
    }
}
