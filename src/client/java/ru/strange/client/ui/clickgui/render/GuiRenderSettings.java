package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.Theme;
import ru.strange.client.module.ThemeManager;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.ui.clickgui.newstyle.NewGuiState;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.other.KeyUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.List;

public class GuiRenderSettings extends GuiScreen {
    private static final int PULSE_GOLD = new Color(255, 208, 92).getRGB();

    public static void renderSettings(DrawContext ctx, java.util.List<Setting> settings, float x, float y, double mouseX, double mouseY) {
        boolean themea = ThemeManager.getTheme() == Theme.TRANSPARENT_WHITE || ThemeManager.getTheme() == Theme.TRANSPARENT_BLACK || ThemeManager.getTheme() == Theme.PURPLE || ThemeManager.getTheme() == Theme.PINK;

        float up = 0;
        int index = 0;
        for (Setting setting : settings) {
            float widthSettings = 109;
            float heightSettings = 16;

            float xSettings = x;
            float ySettings = y + up;

            if (setting instanceof BindSettings) {
                BindSettings s = (BindSettings) setting;
                if (s.hidden.get()) continue;

                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/b_s.png"), xSettings + 89, ySettings + 5, 8, 8, RenderUtil.ColorUtil.getTextColor(1,1));

                if (s.active) {
                    FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.tr("common.press"), xSettings + 61, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                } else {
                    String textS = KeyUtil.getKey(s.get()).toUpperCase();
                    if (textS.length() > 8) {
                        textS = textS.substring(0, 8) + "...";
                    }
                    FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS, xSettings + 61, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                }

                up += heightSettings + 4;
            }

            if (setting instanceof StringSetting) {
                StringSetting s = (StringSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/t_s.png"), xSettings + 89, ySettings + 5, 8, 8, RenderUtil.ColorUtil.getTextColor(1,1));

                String textS = (s.get().isEmpty() && !s.active) ? "..." : s.get() + (s.active ? (System.currentTimeMillis() % 1000 >= 500 ? " " : "_") : " ");
                if (textS.length() > 8) {
                    textS = textS.substring(0, 8) + "...";
                }
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS, xSettings + 61, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));

                up += heightSettings + 4;
            }

            if (setting instanceof ButtonSetting) {
                ButtonSetting s = (ButtonSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/settings.png"), xSettings + 88, ySettings + 4, 10, 10, RenderUtil.ColorUtil.getTextColor(1,1));

                String textS = ModLocalization.raw(s.getActionLabel());
                if (textS.length() > 8) {
                    textS = textS.substring(0, 8) + "...";
                }
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS, xSettings + 61, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));

                up += heightSettings + 4;
            }

            if (setting instanceof HueSetting) {
                HueSetting s = (HueSetting) setting;
                if (s.hidden.get()) continue;
                int size = 82;
                s.maximum = size;

                float currentGUI = s.current * (size / s.originalMaximum);

                if (s.sliding) {
                    currentGUI = (float) MathHelper.round(MathHelper.clamp((float) ((double) (mouseX - xSettings - size - 10) * (s.maximum - s.minimum) / size + s.maximum), s.minimum, s.maximum), s.increment);
                    s.current = currentGUI * (s.originalMaximum / s.maximum);
                    s.triggerDeferredAutoSave();
                }

                s.sliderWidth = NewGuiState.smoothLerp(s.sliderWidth, (((currentGUI) - s.minimum) / (s.maximum - s.minimum)) * size, 0.15f);

                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 88, ySettings + 4, 10, 10, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));

                float hue = currentGUI / size;
                Color color_hue = Color.getHSBColor(hue, s.saturation, s.brightness);
                Color colorWithAlpha = new Color(color_hue.getRed(), color_hue.getGreen(), color_hue.getBlue(), 255);
                Color color_hue_100 = Color.getHSBColor(hue, 1, 1);
                Color colorWithAlpha_100 = new Color(color_hue_100.getRed(), color_hue_100.getGreen(), color_hue_100.getBlue(), 255);

                RenderUtil.Round.draw(ctx, xSettings + 90, ySettings + 6, 6, 6, 1, colorWithAlpha);
                if (s.opened) {
                    float xColor = xSettings + 5;
                    float yColor = ySettings + 16;

                    if (s.colorSliding) {
                        float relativeX = (float) (mouseX - (xColor + 5));
                        float relativeY = (float) (mouseY - (yColor + 5));

                        float normalizedX = MathHelper.clamp(relativeX / 82.0f, 0.0f, 1.0f);
                        float normalizedY = MathHelper.clamp(relativeY / 60.0f, 0.0f, 1.0f);

                        s.saturation = normalizedX;
                        s.brightness = 1.0f - normalizedY;
                        s.triggerDeferredAutoSave();
                    }

                    RenderUtil.Round.draw(ctx, xColor, yColor, 92, 78, 2.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                    RenderUtil.Round.draw(ctx, xColor + 5, yColor + 5, 82, 60, 1.5f, colorWithAlpha_100);
                    RenderUtil.Image.draw(ctx, Strange.id("icons/gui/c_bg.png"), xColor + 5, yColor + 5, 82, 60, new Color(255, 255, 255, 255));

                    float circleX = xColor + 5 + (s.saturation * 82.0f) - 3;
                    float circleY = yColor + 5 + ((1.0f - s.brightness) * 60.0f) - 3;

                    RenderUtil.Border.draw(ctx, circleX, circleY, 6, 6, 2, 0.3f, new Color(0xFFFFFF));

                    RenderUtil.Image.draw(ctx, Strange.id("icons/gui/hue.png"), xColor + 5, yColor + 69, size, 4, new Color(255, 255, 255, 255));
                    RenderUtil.Border.draw(ctx, xColor + 5 - 3 + s.sliderWidth, yColor + 69 - 1, 6, 6, 2, 0.3f, new Color(0xFFFFFF));

                    up += 80;
                }

                up += heightSettings + 4;
            }

            if (setting instanceof BooleanSetting) {
                BooleanSetting s = (BooleanSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 88, ySettings + 4, 10, 10, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                if (s.get()) {
                    RenderUtil.Round.draw(ctx, xSettings + 90, ySettings + 6, 6, 6, 1, RenderUtil.ColorUtil.getTextColor(1,1));
                }
                up += heightSettings + 4;
            }

            if (setting instanceof ModeSetting) {
                ModeSetting s = (ModeSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, resolveSettingLabelColor(s));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10 + (s.opened ? (s.modes.size() * 6 + 5) : 0), 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/m_d.png"), xSettings + 87, ySettings + 3, 12, 12, RenderUtil.ColorUtil.getTextColor(1,1));

                String textS = ModLocalization.raw(s.get());
                if (textS.length() > 8) {
                    textS = textS.substring(0, 8) + "...";
                }
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS, xSettings + 61, ySettings + 11, 5, resolveModeValueColor(s.currentMode, true));

                if (s.opened) {
                    for (int i = 0; i < s.modes.size(); i++) {
                        String textS2 = ModLocalization.raw(s.modes.get(i));
                        if (textS2.length() > 10) {
                            textS2 = textS2.substring(0, 10) + "...";
                        }
                        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS2, xSettings + 61, ySettings + 11 + 10 + i * 6, 5, resolveModeValueColor(s.modes.get(i), s.modes.get(i).equals(s.currentMode)));
                    }
                    up += s.modes.size() * 6;
                }

                up += heightSettings + 4;
            }

            if (setting instanceof MultiBooleanSetting) {
                MultiBooleanSetting s = (MultiBooleanSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10 + (s.opened ? (s.settings.size() * 6 + 5) : 0), 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, ModLocalization.tr("common.select"), xSettings + 63, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 60, ySettings + 6, 2, 6, 1, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/m_d.png"), xSettings + 87, ySettings + 3, 12, 12, RenderUtil.ColorUtil.getTextColor(1,1));

                if (s.opened) {
                    for (int i = 0; i < s.settings.size(); i++) {
                        String textS2 = s.settings.get(i).getDisplayName();
                        if (textS2.length() > 10) {
                            textS2 = textS2.substring(0, 10) + "...";
                        }
                        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS2, xSettings + 61, ySettings + 11 + 10 + i * 6, 5, s.settings.get(i).get() ? RenderUtil.ColorUtil.getTextColor(1,1) : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), 90));
                    }
                    up += s.settings.size() * 6;
                }

                up += heightSettings + 4;
            }

            if (setting instanceof ListSetting) {
                ListSetting s = (ListSetting) setting;
                if (s.hidden.get()) continue;
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 6, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 58, ySettings + 4, 40, 10 + (s.opened ? (s.list.size() * 6 + 5) : 0), 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Image.draw(ctx, Strange.id("icons/gui/m_d.png"), xSettings + 87, ySettings + 3, 12, 12, RenderUtil.ColorUtil.getTextColor(1,1));

                String textS = s.getSelectedDisplay();
                if (textS.length() > 10) {
                    textS = textS.substring(0, 10) + "...";
                }
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, textS, xSettings + 61, ySettings + 11, 5, RenderUtil.ColorUtil.getTextColor(1,1));

                if (s.opened) {
                    for (int i = 0; i < s.list.size(); i++) {
                        String option = s.list.get(i);
                        String optionText = ModLocalization.raw(option);
                        if (optionText.length() > 10) {
                            optionText = optionText.substring(0, 10) + "...";
                        }
                        int color = s.isSelected(option)
                                ? RenderUtil.ColorUtil.getTextColor(1,1)
                                : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1,1), 90);
                        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, optionText, xSettings + 61, ySettings + 11 + 10 + i * 6, 5, color);
                    }
                    up += s.list.size() * 6;
                }

                up += heightSettings + 4;
            }

            if (setting instanceof SliderSetting) {
                SliderSetting s = (SliderSetting) setting;
                if (s.hidden.get()) continue;

                int size = 94;
                if (s.sliding) {
                    s.current = (float) MathHelper.round(MathHelper.clamp((float) ((double) (mouseX - xSettings - size - 4) * (s.maximum - s.minimum) / size + s.maximum), s.minimum, s.maximum), s.increment);
                    s.triggerDeferredAutoSave();
                }
                s.sliderWidth = NewGuiState.smoothLerp(s.sliderWidth, (((s.current) - s.minimum) / (s.maximum - s.minimum)) * size, 0.3f);

                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, setting.getDisplayName(), xSettings + 5, ySettings + 8, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, String.valueOf(s.get()), xSettings + 90, ySettings + 8, 5, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 4, ySettings + 12, 94, 6, 1.5f, themea ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1,1), 125) : RenderUtil.ColorUtil.getMainColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 5, ySettings + 13, s.sliderWidth - 2, 4, 1, RenderUtil.ColorUtil.getTextColor(1,1));
                RenderUtil.Round.draw(ctx, xSettings + 1 + s.sliderWidth, ySettings + 12, 3, 6, 1, RenderUtil.ColorUtil.getTextColor(1,1));

                up += heightSettings + 4;
            }

            index++;
        }
    }

    private static int resolveSettingLabelColor(ModeSetting setting) {
        if (ShaderThemePreset.isPulseName(setting.currentMode)) {
            return PULSE_GOLD;
        }
        return RenderUtil.ColorUtil.getTextColor(1, 1);
    }

    private static int resolveModeValueColor(String value, boolean selected) {
        if (ShaderThemePreset.isPulseName(value)) {
            return selected ? PULSE_GOLD : RenderUtil.ColorUtil.replAlpha(PULSE_GOLD, 150);
        }
        return selected
                ? RenderUtil.ColorUtil.getTextColor(1, 1)
                : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 90);
    }
}
