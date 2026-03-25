package ru.strange.client.ui.clickgui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.api.setting.impl.StringSetting;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.ui.clickgui.mouse.GuiMouseClicked;
import ru.strange.client.ui.clickgui.render.GuiRender;
import ru.strange.client.utils.Helper;
import ru.strange.client.utils.other.KeyBindPolicy;

public class GuiClient extends Screen implements Helper {

    public GuiClient() {
        super(Text.literal(Strange.name));
        GuiLocalization.initialize();
        GuiScreen.width = 225;
        GuiScreen.height = 217;
        GuiScreen.categories = Category.values();
        GuiScreen.modules = Strange.get.manager.getType(GuiScreen.selectedCategories);
        GuiScreen.themes = Theme.values();
    }

    @Override
    protected void init() {
        super.init();
        GuiScreen.width = 225;
        GuiScreen.height = 217;
        GuiScreen.x = this.width / 2f - GuiScreen.width / 2f;
        GuiScreen.y = this.height / 2f - GuiScreen.height / 2f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        ThemeManager.update();
        GuiRender.renderGui(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mouseBindCode = BindSettings.mouseCode(button);

        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {

                // Бинд самого модуля
                if (m.binding) {
                    m.bind = mouseBindCode;
                    m.binding = false;
                    m.displayName = m.name;

                    if (ru.strange.client.Strange.get != null && ru.strange.client.Strange.get.configManager != null) {
                        ru.strange.client.Strange.get.configManager.autoSave();
                    }
                    return true;
                }

                // BindSettings внутри модуля
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof BindSettings) {
                        BindSettings s = (BindSettings) setting;
                        if (s.hidden.get()) continue;

                        if (s.active) {
                            s.set(mouseBindCode);
                            s.active = false;
                            return true;
                        }
                    }
                }
            }
        }

        return GuiMouseClicked.mouseClickedGui(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float modulesX = GuiScreen.x + 7;
        float modulesY = GuiScreen.y + 64;
        float modulesWidth = 211;
        float modulesHeight = GuiScreen.height - 64 - 7;

        if (GuiScreen.isHovered(mouseX, mouseY, modulesX, modulesY, modulesWidth, modulesHeight)) {
            GuiScreen.scroll.handleScroll(verticalAmount);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {

                // Бинд самого модуля
                if (m.binding) {
                    boolean changed = false;
                    if (KeyBindPolicy.isClearKey(keyCode)) {
                        m.bind = -1;
                        changed = true;
                    } else if (!KeyBindPolicy.isProtectedFunctionKey(keyCode)) {
                        m.bind = keyCode;
                        changed = true;
                    }

                    m.binding = false;
                    m.displayName = m.name;

                    if (changed && ru.strange.client.Strange.get != null && ru.strange.client.Strange.get.configManager != null) {
                        ru.strange.client.Strange.get.configManager.autoSave();
                    }
                    return true;
                }

                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof BindSettings) {
                        BindSettings s = (BindSettings) setting;
                        if (s.hidden.get()) continue;

                        if (s.active) {
                            if (KeyBindPolicy.isClearKey(keyCode)) {
                                s.set(-1);
                            } else if (!KeyBindPolicy.isProtectedFunctionKey(keyCode)) {
                                s.set(keyCode);
                            }

                            s.active = false;
                            return true;
                        }
                    }

                    if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                        if (setting instanceof StringSetting) {
                            StringSetting s = (StringSetting) setting;
                            if (s.hidden.get()) continue;

                            if (s.active && s.input.length() > 0) {
                                s.input = s.input.substring(0, s.input.length() - 1);
                                s.triggerAutoSave();
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof StringSetting) {
                        StringSetting s = (StringSetting) setting;
                        if (s.hidden.get()) continue;

                        if (s.active) {
                            StringBuilder result = new StringBuilder();
                            for (int i = 0; i < Character.toString(codePoint).length(); i++) {
                                char c2 = Character.toString(codePoint).charAt(i);
                                if (c2 >= 32 && c2 != 127) {
                                    result.append(c2);
                                }
                            }
                            s.input += result;
                            s.triggerAutoSave();
                            return true;
                        }
                    }
                }
            }
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void close() {
        flushDeferredSettingSave();
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof SliderSetting) {
                        SliderSetting s = (SliderSetting) setting;
                        s.sliding = false;
                    }
                    if (setting instanceof HueSetting) {
                        HueSetting s = (HueSetting) setting;
                        s.sliding = false;
                        s.colorSliding = false;
                    }
                    if (setting instanceof BindSettings) {
                        BindSettings s = (BindSettings) setting;
                        s.active = false;
                    }
                }
                m.binding = false;
                m.displayName = m.name;
            }
        }
        super.close();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        flushDeferredSettingSave();
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof SliderSetting) {
                        SliderSetting s = (SliderSetting) setting;
                        s.sliding = false;
                    }
                    if (setting instanceof HueSetting) {
                        HueSetting s = (HueSetting) setting;
                        s.sliding = false;
                        s.colorSliding = false;
                    }
                }
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void flushDeferredSettingSave() {
        boolean hadSlidingSetting = false;
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof SliderSetting slider && slider.sliding) {
                        hadSlidingSetting = true;
                    }
                    if (setting instanceof HueSetting hue && (hue.sliding || hue.colorSliding)) {
                        hadSlidingSetting = true;
                    }
                }
            }
        }

        if (hadSlidingSetting && Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.flushAutoSave();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
