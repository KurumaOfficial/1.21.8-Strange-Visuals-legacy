package ru.strange.client.ui.clickgui.newstyle;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.utils.Helper;
import ru.strange.client.utils.other.KeyBindPolicy;

/**
 * Screen for the new dropdown GUI, faithfully ported from Rockstar's DropDownScreen lifecycle.
 * Handles input routing for binds, string fields, keys, and mouse events.
 */
public class NewGuiClient extends Screen implements Helper {

    public NewGuiClient() {
        super(Text.literal(Strange.name));
    }

    @Override
    protected void init() {
        super.init();
        NewGuiState.openAnimation = 0f;
        NewGuiState.closing = false;
        NewGuiState.searchFocused = false;
        NewGuiState.searchQuery = "";
        NewGuiState.searchAnimation = 0f;
        NewGuiState.searchAppendAnimation = 0f;
        NewGuiState.initPositions(this.width, this.height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        NewGuiState.updateFrameDelta();
        float dt60 = NewGuiState.deltaSeconds * 60.0f;

        if (NewGuiState.closing) {
            NewGuiState.openAnimation = Math.max(0f, NewGuiState.openAnimation - 0.035f * dt60);
            if (NewGuiState.openAnimation <= 0.001f) {
                NewGuiState.closing = false;
                NewGuiState.resetInteractionState();
                NewGuiState.resetState();
                super.close();
                return;
            }
        } else {
            NewGuiState.openAnimation = Math.min(1f, NewGuiState.openAnimation + 0.03f * dt60);
        }

        NewGuiRender.render(context, mouseX, mouseY, this.width, this.height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle active bind settings capturing mouse (ported from ModuleComponent/BindSettingComponent)
        int mouseBindCode = BindSettings.mouseCode(button);

        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                // Module bind mode
                if (m.binding) {
                    m.setBind(mouseBindCode);
                    m.binding = false;
                    m.displayName = m.name;
                    return true;
                }
                // Setting bind mode
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof BindSettings s) {
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

        return NewGuiMouseHandler.mouseClicked(mouseX, mouseY, button, this.width, this.height);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return NewGuiMouseHandler.mouseScrolled(mouseX, mouseY, verticalAmount, this.width, this.height);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handleSearchKey(keyCode)) return true;

        if (handleSliderKey(keyCode)) return true;

        // Handle active string setting keys first
        if (handleStringSettingKey(keyCode)) return true;

        // Handle module bind mode (ported from ModuleComponent.onKeyPressed)
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
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
                // Handle setting bind mode (ported from BindSettingComponent.onKeyPressed)
                for (Setting setting : m.getSettingsForGUI()) {
                    if (setting instanceof BindSettings s) {
                        if (s.hidden.get()) continue;
                        if (s.active) {
                            if (KeyBindPolicy.isClearKey(keyCode)) s.set(-1);
                            else if (!KeyBindPolicy.isProtectedFunctionKey(keyCode)) s.set(keyCode);
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
        if (NewGuiState.searchFocused) {
            if (!Character.isISOControl(codePoint)) {
                NewGuiState.searchQuery = NewGuiState.searchQuery + codePoint;
            }
            return true;
        }

        // Route to active string setting (ported from DropDownScreen.charTyped -> panel.charTyped -> StringSettingComponent)
        StringSetting active = findActiveString();
        if (active != null) {
            active.set(active.get() + codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void close() {
        if (NewGuiState.closing) {
            // Already closing — force immediate close
            NewGuiState.resetInteractionState();
            NewGuiState.resetState();
            super.close();
        } else {
            flushSave();
            NewGuiState.closing = true;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        flushSave();
        NewGuiMouseHandler.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void flushSave() {
        boolean dirty = false;
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting s : m.getSettingsForGUI()) {
                    if (s instanceof SliderSetting sl && sl.sliding) dirty = true;
                    if (s instanceof HueSetting h && (h.sliding || h.colorSliding)) dirty = true;
                }
            }
        }
        if (dirty && Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.flushAutoSave();
        }
    }

    private boolean handleStringSettingKey(int keyCode) {
        StringSetting active = findActiveString();
        if (active == null) return false;

        boolean ctrl = hasControlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = mc.keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                active.set(active.get() + clipboard);
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            mc.keyboard.setClipboard(active.get());
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            mc.keyboard.setClipboard(active.get());
            active.set("");
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            String text = active.get();
            if (!text.isEmpty()) {
                active.set(text.substring(0, text.length() - 1));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            active.active = false;
            return true;
        }
        return true;
    }

    private boolean handleSearchKey(int keyCode) {
        boolean ctrl = hasControlDown();
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            NewGuiState.searchFocused = true;
            deactivateStringInputs();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            if (!NewGuiState.searchFocused) {
                NewGuiState.searchFocused = true;
                deactivateStringInputs();
            } else {
                // Keep focus on repeated TAB presses; do not open a module.
            }
            return true;
        }
        if (!NewGuiState.searchFocused) {
            return false;
        }

        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            String clipboard = mc.keyboard.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                NewGuiState.searchQuery = NewGuiState.searchQuery + clipboard;
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            mc.keyboard.setClipboard(NewGuiState.searchQuery);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            mc.keyboard.setClipboard(NewGuiState.searchQuery);
            NewGuiState.searchQuery = "";
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!NewGuiState.searchQuery.isEmpty()) {
                int end = NewGuiState.searchQuery.offsetByCodePoints(NewGuiState.searchQuery.length(), -1);
                NewGuiState.searchQuery = NewGuiState.searchQuery.substring(0, end);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            String query = NewGuiState.searchQuery == null ? "" : NewGuiState.searchQuery.trim();
            if (!query.isEmpty()) {
                Module first = findFirstMatchedModule();
                if (first != null) {
                    first.toggle();
                }
            }
            NewGuiState.searchFocused = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!NewGuiState.searchQuery.isEmpty()) {
                NewGuiState.searchQuery = "";
            } else {
                NewGuiState.searchFocused = false;
            }
            return true;
        }
        return true;
    }

    private boolean handleSliderKey(int keyCode) {
        if (keyCode != GLFW.GLFW_KEY_RIGHT && keyCode != GLFW.GLFW_KEY_LEFT) {
            return false;
        }
        SliderSetting slider = NewGuiState.currentSliderSetting;
        if (slider == null) {
            return false;
        }

        float delta = slider.increment * 0.7f * (keyCode == GLFW.GLFW_KEY_RIGHT ? 1f : -1f);
        float next = ru.strange.client.utils.math.MathHelper.clamp(slider.current + delta, slider.minimum, slider.maximum);
        slider.current = (float) ru.strange.client.utils.math.MathHelper.round(next, slider.increment);
        slider.triggerAutoSave();
        return true;
    }

    private void deactivateStringInputs() {
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting s : m.getSettingsForGUI()) {
                    if (s instanceof StringSetting ss) {
                        ss.active = false;
                    }
                }
            }
        }
    }

    private Module findFirstMatchedModule() {
        for (Category category : NewGuiState.CATEGORIES) {
            java.util.List<Module> modules = NewGuiState.getVisibleModules(category);
            if (!modules.isEmpty()) {
                return modules.get(0);
            }
        }
        return null;
    }

    private void openMatchedModule(Module module) {
        int panelIndex = NewGuiState.getCategoryIndex(module.category);
        if (panelIndex < 0) {
            return;
        }
        if (module.getSettingsForGUI().isEmpty()) {
            NewGuiState.setBlocking(module, 1f);
            NewGuiState.setShake(module, 1f);
            return;
        }
        NewGuiState.selectedModule[panelIndex] = module;
        NewGuiState.settingsScroll[panelIndex].reset();
        NewGuiState.searchFocused = false;
    }

    private StringSetting findActiveString() {
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting s : m.getSettingsForGUI()) {
                    if (s instanceof StringSetting ss && !ss.hidden.get() && ss.active) return ss;
                }
            }
        }
        return null;
    }
}
