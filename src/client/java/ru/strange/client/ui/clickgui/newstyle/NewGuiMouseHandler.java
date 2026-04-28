package ru.strange.client.ui.clickgui.newstyle;

import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.ui.clickgui.localization.GuiLanguage;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;
import ru.strange.client.utils.math.ScrollUtil;
import ru.strange.client.utils.other.KeyUtil;
import ru.strange.client.utils.render.FontDraw;

import java.util.List;

/**
 * Mouse handling for the new dropdown GUI, faithfully ported from Rockstar's MenuPanel.onMouseClicked.
 * When selectedModule != null: handle settings clicks + back button.
 * When selectedModule == null: handle module clicks (LMB=toggle, RMB=open settings, MMB=bind).
 */
public class NewGuiMouseHandler {

    public static boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        handleSearchClick(mouseX, mouseY, button);
        closeDetachedHuePickers(mouseX, mouseY, button);

        // Check theme bar first
        if (clickThemeBar(mouseX, mouseY, button)) return true;
        if (clickLanguageSwitch(mouseX, mouseY, button)) return true;

        // Iterate panels in reverse for proper overlap handling
        for (int i = NewGuiState.CATEGORIES.length - 1; i >= 0; i--) {
            float px = NewGuiState.getRenderedPanelX(i);
            float py = NewGuiState.panelY[i];
            float pw = NewGuiState.PANEL_WIDTH;
            float ph = NewGuiState.PANEL_HEIGHT;
            float headerHeight = NewGuiState.HEADER_HEIGHT;
            float separatorHeight = NewGuiState.SEPARATOR_HEIGHT;

            if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) continue;

            // ===== SETTINGS VIEW (when a module is selected in this panel) =====
            // Ported from MenuPanel.onMouseClicked: if selectedModuleComponent != null
            if (NewGuiState.selectedModule[i] != null) {
                Module selModule = NewGuiState.selectedModule[i];
                float settX = getSettingsViewX(i);

                // Back button area is limited to the actual arrow/title content, not the full row.
                float backY = py + headerHeight + separatorHeight;
                float backX = settX + NewGuiState.BACK_ARROW_OFFSET_LEFT - NewGuiState.BACK_HITBOX_PADDING_LEFT;
                float backW = getBackButtonWidth(selModule);
                if (mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + headerHeight && button == 0) {
                    // Close settings view — set selectedModule to null, scroll resets
                    NewGuiState.selectedModule[i] = null;
                    NewGuiState.settingsScroll[i].reset();
                    return true;
                }

                // Settings area clicks
                float settListY = backY + headerHeight;
                float settListH = ph - headerHeight * 2f - separatorHeight;
                if (mouseX >= px && mouseX <= px + pw && mouseY >= settListY && mouseY <= settListY + settListH) {
                    float scrollY = NewGuiState.settingsScroll[i].getScroll();
                    float settOffset = 0f;
                    for (Setting setting : selModule.getSettingsForGUI()) {
                        float settH = NewGuiState.measureSettingHeight(setting);
                        if (settH <= 0) continue;

                        float settY = settListY + settOffset + scrollY;
                        if (mouseY >= settY && mouseY < settY + settH) {
                            if (clickSetting(mouseX, mouseY, button, setting, settX, settY, pw)) {
                                return true;
                            }
                        }
                        settOffset += settH;
                    }
                }
                return true;
            }

            // ===== MODULE LIST VIEW =====
            // Ported from MenuPanel.onMouseClicked: isHovered at y+28, height-28
            float listY = py + headerHeight + separatorHeight;
            float listH = ph - headerHeight - separatorHeight - 0.5f;
            if (mouseY < listY || mouseY > listY + listH) return true;

            ScrollUtil scroll = NewGuiState.modulesScroll[i];
            float scrollY = scroll.getScroll();
            List<Module> modules = NewGuiState.getVisibleModules(NewGuiState.CATEGORIES[i]);

            float offset = 0f;
            for (Module module : modules) {
                float modY = py + offset + scrollY + headerHeight + separatorHeight;

                if (mouseX >= px && mouseX <= px + pw && mouseY >= modY && mouseY <= modY + NewGuiState.MODULE_HEIGHT) {
                    switch (button) {
                        case 0 -> { // LEFT — toggle module (ported from ModuleComponent: case LEFT)
                            module.toggle();
                            return true;
                        }
                        case 1 -> { // RIGHT — open settings view (ported from ModuleComponent: case RIGHT -> open())
                            if (!module.getSettingsForGUI().isEmpty()) {
                                NewGuiState.selectedModule[i] = module;
                                NewGuiState.settingsScroll[i].reset();
                            } else {
                                NewGuiState.setBlocking(module, 1f);
                                NewGuiState.setShake(module, 1f);
                            }
                            return true;
                        }
                        case 2 -> { // MIDDLE — bind mode (ported from ModuleComponent: case MIDDLE)
                            // Reset all binding modes in this panel
                            for (Module m : modules) {
                                m.binding = false;
                                m.displayName = m.name;
                            }
                            module.binding = true;
                            module.displayName = "Нажмите кнопку";
                            return true;
                        }
                    }
                }
                offset += NewGuiState.MODULE_HEIGHT;
            }
            return true;
        }
        return false;
    }

    private static boolean clickSetting(double mouseX, double mouseY, int button,
                                          Setting setting, float x, float y, float w) {
        // All setting clicks ported from Rockstar's setting component onMouseClicked methods
        // x = panel x, w = panel width (settings use full panel width like Rockstar)

        if (setting instanceof BooleanSetting s) {
            if (s.hidden.get()) return false;
            float toggleX = x + w - 22f;
            float toggleY = y + 7f;
            if (button == 0
                    && mouseX >= toggleX - 2f && mouseX <= toggleX + 15f
                    && mouseY >= toggleY - 2f && mouseY <= toggleY + 10f) {
                s.set(!s.get());
                return true;
            }
        }

        if (setting instanceof SliderSetting s) {
            if (s.hidden.get()) return false;
            // SliderSettingComponent: click on slider track area starts drag
            float sliderX = x + 9f;
            float sliderW = w - 18f;
            float trackY = y + 19f;
            if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= trackY - 5 && mouseY <= trackY + 8) {
                s.sliding = true;
                NewGuiState.currentSliderSetting = s;
                return true;
            }
        }

        if (setting instanceof ModeSetting s) {
            if (s.hidden.get()) return false;
            if (button != 0) return false;
            // ModeSettingComponent: click on option selects it
            float innerX = x + 9f;
            float innerW = w - 18f;
            float boxY = y + 20f;
            for (int i = 0; i < s.modes.size(); i++) {
                float optY = boxY + i * 12f;
                if (mouseX >= innerX - 1 && mouseX <= innerX + innerW + 1
                        && mouseY >= optY && mouseY < optY + 12f) {
                    s.setMode(s.modes.get(i));
                    return true;
                }
            }
        }

        if (setting instanceof BindSettings s) {
            if (s.hidden.get()) return false;
            String key = s.active ? "..." : KeyUtil.getKey(s.get()).toUpperCase();
            float keyW = Math.max(22f, FontDraw.getWidth(FontDraw.FontType.MEDIUM, key, 6) + 7f);
            float badgeX = x + w - 9f - keyW;
            float badgeY = y + 5f;
            boolean insideBadge = mouseX >= badgeX - 1f && mouseX <= badgeX + keyW + 1f
                && mouseY >= badgeY - 1f && mouseY <= badgeY + 13f;
            if (button == 0 && insideBadge) {
                s.active = !s.active;
                return true;
            }
            if (s.active && button != 0 && insideBadge) {
                s.set(BindSettings.mouseCode(button));
                s.active = false;
                return true;
            }
        }

        if (setting instanceof StringSetting s) {
            if (s.hidden.get()) return false;
            // StringSettingComponent: click on field area activates
            float fieldX = x + 8f;
            float fieldY = y + 15f;
            float fieldW = w - 16f;
            float fieldH = 15f;
            if (mouseX >= fieldX && mouseX <= fieldX + fieldW && mouseY >= fieldY && mouseY <= fieldY + fieldH) {
                s.active = !s.active;
                return true;
            } else {
                s.active = false;
            }
        }

        if (setting instanceof ButtonSetting s) {
            if (s.hidden.get()) return false;
            // ButtonSettingComponent: click on button area presses
            if (button == 0 && mouseX >= x + 7f && mouseX <= x + w - 7f
                    && mouseY >= y + 4f && mouseY <= y + 21f) {
                s.press();
                return true;
            }
        }

        if (setting instanceof HueSetting s) {
            if (s.hidden.get()) return false;
            // ColorSettingComponent: click on color preview toggles picker
            float previewX = x + w - NewGuiState.COLOR_PREVIEW_OFFSET_RIGHT - NewGuiState.COLOR_PREVIEW_SIZE;
            float previewY = y + NewGuiState.COLOR_PREVIEW_OFFSET_TOP;
            float previewPad = NewGuiState.COLOR_PREVIEW_CLICK_PADDING;
            if (mouseX >= previewX - previewPad && mouseX <= previewX + NewGuiState.COLOR_PREVIEW_SIZE + previewPad
                    && mouseY >= previewY - previewPad && mouseY <= previewY + NewGuiState.COLOR_PREVIEW_SIZE + previewPad) {
                s.opened = !s.opened;
                return true;
            }
            if (s.opened) {
                float pickerX = x + 8f;
                float pickerY = y + 20f;
                float pickerW = w - 16f;
                float pickerH = 50f;
                // Color area click
                if (mouseX >= pickerX && mouseX <= pickerX + pickerW
                        && mouseY >= pickerY && mouseY <= pickerY + pickerH) {
                    s.colorSliding = true;
                    return true;
                }
                // Hue bar click — render draws at pickerY + pickerH + 4
                float hueBarY = pickerY + pickerH + 4;
                if (mouseX >= pickerX && mouseX <= pickerX + pickerW
                        && mouseY >= hueBarY - 3 && mouseY <= hueBarY + 8) {
                    s.sliding = true;
                    return true;
                }
                if (button == 0) {
                    s.opened = false;
                }
            }
        }

        if (setting instanceof MultiBooleanSetting s) {
            if (s.hidden.get()) return false;
            if (button != 0) return false;
            // Like ModeSettingComponent: click on sub-option toggles it
            float innerX = x + 9f;
            float innerW = w - 18f;
            float boxY = y + 20f;
            for (int i = 0; i < s.settings.size(); i++) {
                float optY = boxY + i * 12f;
                if (mouseX >= innerX - 1 && mouseX <= innerX + innerW + 1
                        && mouseY >= optY && mouseY < optY + 12f) {
                    s.settings.get(i).set(!s.settings.get(i).get());
                    return true;
                }
            }
        }

        if (setting instanceof ListSetting s) {
            if (s.hidden.get()) return false;
            if (button != 0) return false;
            // Like SelectSettingComponent: click on option toggles selection
            float innerX = x + 9f;
            float innerW = w - 18f;
            float boxY = y + 20f;
            for (int i = 0; i < s.list.size(); i++) {
                float optY = boxY + i * 12f;
                if (mouseX >= innerX - 1 && mouseX <= innerX + innerW + 1
                        && mouseY >= optY && mouseY < optY + 12f) {
                    s.toggle(s.list.get(i));
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean mouseScrolled(double mouseX, double mouseY, double amount, int screenWidth, int screenHeight) {
        for (int i = NewGuiState.CATEGORIES.length - 1; i >= 0; i--) {
            float px = NewGuiState.getRenderedPanelX(i);
            float py = NewGuiState.panelY[i];
            float pw = NewGuiState.PANEL_WIDTH;
            float ph = NewGuiState.PANEL_HEIGHT;

            if (mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph) {
                // Route scroll to correct ScrollUtil based on current view
                // Ported from MenuPanel.onKeyPressed: if selectedModule != null -> settingsScroll, else modulesScroll
                if (NewGuiState.selectedModule[i] != null) {
                    NewGuiState.settingsScroll[i].handleScroll(amount);
                } else {
                    NewGuiState.modulesScroll[i].handleScroll(amount);
                }
                return true;
            }
        }
        return false;
    }

    public static void mouseReleased() {
        // Release all slider/color drags
        for (Category c : Category.values()) {
            for (Module m : Strange.get.manager.getType(c)) {
                for (Setting s : m.getSettingsForGUI()) {
                    if (s instanceof SliderSetting sl) sl.sliding = false;
                    if (s instanceof HueSetting h) { h.sliding = false; h.colorSliding = false; }
                }
            }
        }
    }

    private static void closeDetachedHuePickers(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        for (int panelIndex = 0; panelIndex < NewGuiState.CATEGORIES.length; panelIndex++) {
            Module selected = NewGuiState.selectedModule[panelIndex];
            if (selected == null) {
                continue;
            }

            float px = getSettingsViewX(panelIndex);
            float py = NewGuiState.panelY[panelIndex];
            float settListY = py + NewGuiState.HEADER_HEIGHT * 2f + NewGuiState.SEPARATOR_HEIGHT;
            float scrollY = NewGuiState.settingsScroll[panelIndex].getScroll();
            float offset = 0f;

            for (Setting setting : selected.getSettingsForGUI()) {
                float settH = NewGuiState.measureSettingHeight(setting);
                if (settH <= 0f) {
                    continue;
                }
                if (setting instanceof HueSetting hue && hue.opened) {
                    float sy = settListY + offset + scrollY;
                    float previewX = px + NewGuiState.PANEL_WIDTH - NewGuiState.COLOR_PREVIEW_OFFSET_RIGHT - NewGuiState.COLOR_PREVIEW_SIZE;
                    float previewY = sy + NewGuiState.COLOR_PREVIEW_OFFSET_TOP;
                    float previewPad = NewGuiState.COLOR_PREVIEW_CLICK_PADDING;
                    boolean insidePreview = mouseX >= previewX - previewPad
                        && mouseX <= previewX + NewGuiState.COLOR_PREVIEW_SIZE + previewPad
                        && mouseY >= previewY - previewPad
                        && mouseY <= previewY + NewGuiState.COLOR_PREVIEW_SIZE + previewPad;
                    boolean insidePicker = mouseX >= px + 8f && mouseX <= px + NewGuiState.PANEL_WIDTH - 8f
                        && mouseY >= sy + 20f && mouseY <= sy + 79f;
                    if (!insidePreview && !insidePicker) {
                        hue.opened = false;
                    }
                }
                offset += settH;
            }
        }
    }

    private static boolean clickThemeBar(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        Theme[] themes = Theme.values();
        float dotSz = 14f;
        float dotGap = 8f;
        float barY = NewGuiState.getThemeBarY();
        for (int i = 0; i < themes.length; i++) {
            float totalW = themes.length * dotSz + (themes.length - 1) * dotGap;
            float screenCenterX = NewGuiState.currentScreenWidth / 2f;
            float barX = screenCenterX - totalW / 2f;
            float dx = barX + i * (dotSz + dotGap);
            if (mouseX >= dx - 2 && mouseX <= dx + dotSz + 2
                    && mouseY >= barY - 2 && mouseY <= barY + dotSz + 2) {
                ThemeManager.setTheme(themes[i]);
                return true;
            }
        }
        return false;
    }

    private static boolean clickLanguageSwitch(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float ruX = NewGuiState.getLanguageRuX();
        float enX = NewGuiState.getLanguageEnX();
        float segW = NewGuiState.getLanguageSegmentWidth();
        float pillY = NewGuiState.getLanguagePillY();
        float pillH = NewGuiState.getLanguagePillHeight();
        if (mouseX >= ruX && mouseX <= ruX + segW && mouseY >= pillY && mouseY <= pillY + pillH) {
            GuiLocalization.setLanguage(GuiLanguage.RU);
            return true;
        }
        if (mouseX >= enX && mouseX <= enX + segW && mouseY >= pillY && mouseY <= pillY + pillH) {
            GuiLocalization.setLanguage(GuiLanguage.EN);
            return true;
        }
        return false;
    }

    private static void handleSearchClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return;
        }
        float alpha = NewGuiState.openAnimation * Math.max(NewGuiState.searchAnimation, NewGuiState.searchFocused ? 1f : 0f);
        if (alpha <= 0.01f) {
            NewGuiState.searchFocused = false;
            return;
        }

        float searchX = NewGuiState.getSearchX();
        float searchY = NewGuiState.getSearchY();
        if (mouseX >= searchX && mouseX <= searchX + NewGuiState.SEARCH_WIDTH
                && mouseY >= searchY && mouseY <= searchY + NewGuiState.SEARCH_HEIGHT) {
            NewGuiState.searchFocused = true;
            return;
        }
        NewGuiState.searchFocused = false;
    }

    private static float getSettingsViewX(int panelIndex) {
        return NewGuiState.getRenderedPanelX(panelIndex)
            + NewGuiState.PANEL_WIDTH * (1f - NewGuiState.swapAnimation[panelIndex]);
    }

    private static float getBackButtonWidth(Module module) {
        float arrowWidth = NewGuiState.BACK_ARROW_SIZE;
        float textWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, module.getDisplayName(), 7);
        return NewGuiState.BACK_HITBOX_PADDING_LEFT
            + arrowWidth
            + NewGuiState.BACK_TITLE_GAP
            + textWidth
            + NewGuiState.BACK_HITBOX_PADDING_RIGHT;
    }
}
