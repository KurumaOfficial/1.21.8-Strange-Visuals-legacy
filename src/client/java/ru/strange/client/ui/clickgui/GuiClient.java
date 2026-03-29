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
                    m.setBind(mouseBindCode);
                    m.binding = false;
                    m.displayName = m.name;
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
        if (GuiScreen.selectedCategories == Category.Theme) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        GuiScreen.ModulePanel modulePanel = GuiScreen.modulePanelBounds();

        if (GuiScreen.isHovered(mouseX, mouseY, modulePanel.x(), modulePanel.y(), modulePanel.width(), modulePanel.height())) {
            GuiScreen.scroll.handleScroll(verticalAmount);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleActiveStringSettingKeyPress(keyCode)) {
            return true;
        }

        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {

                // Бинд самого модуля
                if (m.binding) {
                    if (KeyBindPolicy.isClearKey(keyCode)) {
                        m.setBind(BindSettings.NONE);
                    } else if (!KeyBindPolicy.isProtectedFunctionKey(keyCode)) {
                        m.setBind(keyCode);
                    }

                    m.binding = false;
                    m.displayName = m.name;
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
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (handleActiveStringSettingCharTyped(codePoint)) {
            return true;
        }

        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof StringSetting s && !s.hidden.get() && s.active) {
                        return true;
                    }
                }
            }
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void close() {
        flushDeferredSettingSave();
        GuiScreen.resetTransientInteractionState();
        super.close();
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        flushDeferredSettingSave();
        GuiScreen.resetDragInteractionState();
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

    private boolean handleActiveStringSettingKeyPress(int keyCode) {
        StringSetting activeSetting = findActiveStringSetting();
        if (activeSetting == null) {
            return false;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_V) {
            appendToStringSetting(activeSetting, readClipboardText());
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_C) {
            writeClipboardText(activeSetting.input);
            return true;
        }

        if (hasControlDown() && keyCode == GLFW.GLFW_KEY_X) {
            writeClipboardText(activeSetting.input);
            if (!activeSetting.input.isEmpty()) {
                activeSetting.set("");
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            activeSetting.active = false;
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE || (hasControlDown() && keyCode == GLFW.GLFW_KEY_BACKSPACE)) {
            if (!activeSetting.input.isEmpty()) {
                activeSetting.set("");
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!activeSetting.input.isEmpty()) {
                activeSetting.set(activeSetting.input.substring(0, activeSetting.input.length() - 1));
            }
            return true;
        }

        return false;
    }

    private boolean handleActiveStringSettingCharTyped(char codePoint) {
        StringSetting activeSetting = findActiveStringSetting();
        if (activeSetting == null) {
            return false;
        }

        appendToStringSetting(activeSetting, Character.toString(codePoint));
        return true;
    }

    private StringSetting findActiveStringSetting() {
        for (Category category : Category.values()) {
            for (Module module : Strange.get.manager.getType(category)) {
                for (Setting setting : module.getSettingsForGUI()) {
                    if (setting instanceof StringSetting stringSetting && !stringSetting.hidden.get() && stringSetting.active) {
                        return stringSetting;
                    }
                }
            }
        }
        return null;
    }

    private void appendToStringSetting(StringSetting setting, String rawText) {
        String filtered = filterPrintableText(rawText);
        if (setting == null || filtered.isEmpty()) {
            return;
        }

        setting.set(setting.input + filtered);
    }

    private String readClipboardText() {
        return client == null || client.keyboard == null ? "" : client.keyboard.getClipboard();
    }

    private void writeClipboardText(String value) {
        if (client == null || client.keyboard == null) {
            return;
        }

        client.keyboard.setClipboard(value == null ? "" : value);
    }

    private static String filterPrintableText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character >= 32 && character != 127) {
                result.append(character);
            }
        }
        return result.toString();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
