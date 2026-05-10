package ru.strange.client.ui.clickgui.localization;

import java.util.Locale;

public enum GuiLanguage {
    RU("ru"),
    EN("en");

    private final String code;

    GuiLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static GuiLanguage byCode(String code) {
        if (code == null) {
            return null;
        }

        for (GuiLanguage language : values()) {
            if (language.code.equalsIgnoreCase(code)) {
                return language;
            }
        }

        return null;
    }

    public static GuiLanguage fromGameLanguageCode(String code) {
        if (code == null) {
            return EN;
        }

        return code.toLowerCase(Locale.ROOT).startsWith("ru") ? RU : EN;
    }
}
