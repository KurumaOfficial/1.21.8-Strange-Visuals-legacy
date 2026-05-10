package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;

import java.awt.*;
import java.util.function.Supplier;

public class HueSetting extends Setting {
    public float current, minimum, maximum, increment;
    public float originalMaximum = 106; // Сохраняем оригинальное значение maximum
    public float sliderWidth;
    public boolean sliding;
    public boolean colorSliding;
    public String description;
    public boolean opened;

    public float saturation = 1.0f;
    public float brightness = 1.0f;

    public HueSetting(String name, float current) {
        this.name = name;
        this.minimum = 0;
        this.current = current;
        this.maximum = 106;
        this.originalMaximum = 106;
        this.increment = 1;
        this.saturation = 1.0f;
        this.brightness = 1.0f;
    }

    public HueSetting(String name, float current, float saturation, float brightness) {
        this.name = name;
        this.minimum = 0;
        this.current = current;
        this.maximum = 106;
        this.originalMaximum = 106;
        this.increment = 1;
        this.saturation = saturation;
        this.brightness = brightness;
    }

    /**
     * Конструктор для создания HueSetting из Color объекта
     * @param name название настройки
     * @param color цвет для установки
     */
    public HueSetting(String name, Color color) {
        this.name = name;
        this.minimum = 0;
        this.maximum = 106;
        this.originalMaximum = 106;
        this.increment = 1;
        setColor(color);
    }

    public HueSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    /**
     * Получает цвет в формате HSB
     * @return цвет на основе hue, saturation и brightness
     */
    public Color getColor() {
        float hue = current / originalMaximum;
        float adjustedBrightness = Math.max(brightness, 0.1f);
        Color color = Color.getHSBColor(hue, saturation, adjustedBrightness);

        if (color.getRed() == 0 && color.getGreen() == 0 && color.getBlue() == 0) {
            return new Color(0, 0, 0, color.getAlpha());
        }

        return color;
    }

    /**
     * Устанавливает цвет из Color объекта
     * @param color цвет для установки
     */
    public void setColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        // Используем originalMaximum для правильной установки current
        this.current = hsb[0] * originalMaximum;
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    /**
     * Получает hue (0-1)
     */
    public float getHue() {
        return current / maximum;
    }

    /**
     * Получает цвет в RGB формате
     */
    public int getRGB() {
        return getColor().getRGB();
    }

    /**
     * Получает цвет в RGB формате с альфой
     */
    public int getRGBA(int alpha) {
        Color color = getColor();
        return (alpha << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }

}