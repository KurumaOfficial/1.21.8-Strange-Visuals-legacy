package ru.strange.client.ui.clickgui.mouse;

import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.*;
import ru.strange.client.ui.clickgui.GuiScreen;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.List;

public class GuiMouseClickedSettings extends GuiScreen {
    public static boolean clickedSettings(List<Setting> settings, double mouseX, double mouseY, float x, float y, float clipTop, float clipBottom) {
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
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.active = !s.active;
                    return true;
                }

                up += heightSettings + 4;
            }
            if (setting instanceof StringSetting) {
                StringSetting s = (StringSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.active = !s.active;
                    return true;
                } else {
                    s.active = false;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof ButtonSetting) {
                ButtonSetting s = (ButtonSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.press();
                    return true;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof HueSetting) {
                HueSetting s = (HueSetting) setting;
                if (s.hidden.get()) continue;
                int size = 82;
                s.maximum = size;
                
                // Вычисляем временное значение current для GUI (масштабированное)
                float currentGUI = s.current * (size / s.originalMaximum);
                
                if (s.sliding) {
                    // Устанавливаем currentGUI относительно GUI maximum (size)
                    currentGUI = (float) MathHelper.round(MathHelper.clamp((float) ((double) (mouseX - xSettings - size - 10) * (s.maximum - s.minimum) / size + s.maximum), s.minimum, s.maximum), s.increment);
                    // Масштабируем обратно к originalMaximum для правильного сохранения цвета
                    s.current = currentGUI * (s.originalMaximum / s.maximum);
                }
                
                s.sliderWidth = MathHelper.interpolate((((currentGUI) - s.minimum) / (s.maximum - s.minimum)) * size, s.sliderWidth, 0.15);

                if (isHoveredClipped(mouseX, mouseY, xSettings + 88, ySettings + 4, 10, 10, clipTop, clipBottom)) {
                    s.opened = !s.opened;
                    return true;
                }
                // Используем currentGUI для отображения в GUI
                float hue = currentGUI / size;
                Color color_hue = Color.getHSBColor(hue, s.saturation, s.brightness);
                Color colorWithAlpha = new Color(color_hue.getRed(), color_hue.getGreen(), color_hue.getBlue(), 255);

                if (s.opened) {
                    float xColor = xSettings + 5;
                    float yColor = ySettings + 16;
                    
                    // Обработка слайдинга для основного прямоугольника цвета
                    if (s.colorSliding || isHoveredClipped(mouseX, mouseY, xColor + 5, yColor + 5, 82, 60, clipTop, clipBottom)) {
                        float relativeX = (float) (mouseX - (xColor + 5));
                        float relativeY = (float) (mouseY - (yColor + 5));

                        float normalizedX = MathHelper.clamp(relativeX / 82.0f, 0.0f, 1.0f);
                        float normalizedY = MathHelper.clamp(relativeY / 60.0f, 0.0f, 1.0f);

                        s.saturation = normalizedX;
                        s.brightness = 1.0f - normalizedY;
                        s.triggerDeferredAutoSave();
                        
                        if (isHoveredClipped(mouseX, mouseY, xColor + 5, yColor + 5, 82, 60, clipTop, clipBottom)) {
                            s.colorSliding = true;
                            return true;
                        }
                    }
                    
                    if (s.sliding) {
                        s.triggerDeferredAutoSave();
                    }


                    if (isHoveredClipped(mouseX, mouseY, xColor + 5, yColor + 69, size, 4, clipTop, clipBottom)) {
                        s.sliding = true;
                        return true;
                    }
                    up += 80;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof BooleanSetting) {
                BooleanSetting s = (BooleanSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 88, ySettings + 4, 10, 10, clipTop, clipBottom)) {
                    s.set(!s.get());
                    return true;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof ModeSetting) {
                ModeSetting s = (ModeSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.opened = !s.opened;
                    return true;
                }
                if (s.opened) {
                    for (int i = 0; i < s.modes.size(); i++) {
                        if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 11 + 5 + i * 6, 40, 6, clipTop, clipBottom)) {
                            s.setMode(s.modes.get(i));
                            return true;
                        }
                    }
                    up += s.modes.size() * 6;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof MultiBooleanSetting) {
                MultiBooleanSetting s = (MultiBooleanSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.opened = !s.opened;
                    return true;
                }
                if (s.opened) {
                    for (int i = 0; i < s.settings.size(); i++) {
                        if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 11 + 5 + i * 6, 40, 6, clipTop, clipBottom)) {
                            s.settings.get(i).set(!s.settings.get(i).get());
                            // triggerAutoSave уже вызывается в BooleanSetting.set()
                            return true;
                        }
                    }
                    up += s.settings.size() * 6;
                }

                up += heightSettings + 4;
            }
            if (setting instanceof ListSetting) {
                ListSetting s = (ListSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 4, 40, 10, clipTop, clipBottom)) {
                    s.opened = !s.opened;
                    return true;
                }
                if (s.opened) {
                    for (int i = 0; i < s.list.size(); i++) {
                        if (isHoveredClipped(mouseX, mouseY, xSettings + 58, ySettings + 11 + 5 + i * 6, 40, 6, clipTop, clipBottom)) {
                            s.toggle(s.list.get(i));
                            return true;
                        }
                    }
                    up += s.list.size() * 6;
                }
                up += heightSettings + 4;
            }
            if (setting instanceof SliderSetting) {
                SliderSetting s = (SliderSetting) setting;
                if (s.hidden.get()) continue;
                if (isHoveredClipped(mouseX, mouseY, xSettings + 4, ySettings + 12, 94, 6, clipTop, clipBottom)) {
                    s.sliding = true;
                    return true;
                }
                up += heightSettings + 4;
            }
            index++;
        }
        return false;
    }

    private static boolean isHoveredClipped(double mouseX, double mouseY, float x, float y, float width, float height, float clipTop, float clipBottom) {
        return mouseY >= clipTop && mouseY <= clipBottom && isHovered(mouseX, mouseY, x, y, width, height);
    }
}
