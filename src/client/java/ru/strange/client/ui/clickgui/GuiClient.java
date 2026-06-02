package ru.strange.client.ui.clickgui;

import net.minecraft.client.MinecraftClient;
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

    private long appearStartNanos;
    private long closeStartNanos = -1L;
    private static final float APPEAR_DURATION = 0.26f;
    private static final float CLOSE_DURATION = 0.18f;

    public GuiClient() {
        super(Text.literal(Strange.name));
        GuiLocalization.initialize();
        GuiScreen.width = 225;
        GuiScreen.height = 217;
        GuiScreen.categories = Category.values();
        GuiScreen.modules = Strange.get.manager.getType(GuiScreen.selectedCategories);
        GuiScreen.themes = Theme.values();
        appearStartNanos = System.nanoTime();
    }

    @Override
    protected void init() {
        super.init();
        GuiScreen.width = 225;
        GuiScreen.height = 217;
        GuiScreen.x = this.width / 2f - GuiScreen.width / 2f;
        GuiScreen.y = this.height / 2f - GuiScreen.height / 2f;
        appearStartNanos = System.nanoTime();
        closeStartNanos = -1L;
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        ru.strange.client.ui.clickgui.newstyle.NewGuiState.updateFrameDelta();
        ThemeManager.update();

        float scale = 1.0f;
        float alpha = 1.0f;

        if (closeStartNanos > 0) {
            float closeT = Math.min(1f, (System.nanoTime() - closeStartNanos) / 1_000_000_000f / CLOSE_DURATION);
            float eased = easeInCubic(closeT);
            scale = 1.0f - 0.10f * eased;
            alpha = 1.0f - eased;
            if (closeT >= 1.0f) {
                closeStartNanos = -1L;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null) mc.setScreen(null);
                return;
            }
        } else {
            float openT = Math.min(1f, (System.nanoTime() - appearStartNanos) / 1_000_000_000f / APPEAR_DURATION);
            float eased = easeOutCubic(openT);
            scale = 0.90f + 0.10f * eased;
            alpha = 0.05f + 0.95f * eased;
        }

        float anchorX = GuiScreen.x + GuiScreen.width / 2f;
        float anchorY = GuiScreen.y + GuiScreen.height / 2f;
        GuiRenderScale.set(scale, anchorX, anchorY);

        if (scale < 0.999f || scale > 1.001f || alpha < 0.999f) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(anchorX, anchorY);
            context.getMatrices().scale(scale, scale);
            context.getMatrices().translate(-anchorX, -anchorY);
            GuiRender.renderGui(context, mouseX, mouseY, deltaTicks);
            context.getMatrices().popMatrix();

            // Fade overlay: black/transparent rect over the panel area to fake alpha.
            if (alpha < 0.999f) {
                int overlayAlpha = Math.max(0, Math.min(255, (int) ((1f - alpha) * 200f)));
                int color = (overlayAlpha << 24);
                context.fill(0, 0, this.width, this.height, color);
            }
        } else {
            GuiRender.renderGui(context, mouseX, mouseY, deltaTicks);
        }
    }

    private static double[] layoutMouse(double mouseX, double mouseY) {
        return GuiRenderScale.toLayoutMouse(mouseX, mouseY);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return closeStartNanos < 0;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double[] layout = layoutMouse(mouseX, mouseY);
        mouseX = layout[0];
        mouseY = layout[1];

        float searchWidth = 90;
        float searchHeight = 20;

        float searchX = GuiScreen.x + (GuiScreen.width / 2f) - (searchWidth / 2f);
        float searchY = GuiScreen.y + GuiScreen.height + 8;

        if (GuiScreen.isHovered(mouseX, mouseY, searchX, searchY, searchWidth, searchHeight)) {
            GuiScreen.searchActive = true;
            return true;
        }

        if (GuiScreen.searchActive) {
            GuiScreen.searchActive = false;
        }

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
        double[] layout = layoutMouse(mouseX, mouseY);
        mouseX = layout[0];
        mouseY = layout[1];

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
        // Ctrl+Left/Right Arrow to switch GUI style
        if (hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                ru.strange.client.module.impl.interfaces.ClickGui clickGui = ru.strange.client.module.impl.interfaces.ClickGui.getInstance();
                if (clickGui != null) {
                    boolean isNew = clickGui.isNewStyle();
                    clickGui.guiStyle.setMode(isNew ? clickGui.STYLE_CLASSIC : clickGui.STYLE_NEW);
                    // Reopen GUI with new style
                    close();
                    mc.setScreen(new GuiClient());
                    return true;
                }
            }
        }

        if (GuiScreen.searchActive) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                GuiScreen.searchActive = false;
                GuiScreen.searchQuery = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!GuiScreen.searchQuery.isEmpty()) {
                    GuiScreen.searchQuery = GuiScreen.searchQuery.substring(0, GuiScreen.searchQuery.length() - 1);
                }
                return true;
            }
            return true;
        }

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
        if (GuiScreen.searchActive) {
            if (codePoint >= 32 && codePoint != 127) {
                GuiScreen.searchQuery += codePoint;
            }
            return true;
        }

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
        if (closeStartNanos < 0) {
            flushDeferredSettingSave();
            GuiScreen.resetTransientInteractionState();
            closeStartNanos = System.nanoTime();
            return;
        }
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
