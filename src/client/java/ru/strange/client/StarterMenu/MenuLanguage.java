package ru.strange.client.StarterMenu;

import java.util.Locale;

public enum MenuLanguage {
    RU("ru"),
    EN("en");

    private final String code;

    MenuLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MenuLanguage byCode(String code) {
        if (code == null) {
            return RU;
        }

        for (MenuLanguage language : values()) {
            if (language.code.equalsIgnoreCase(code)) {
                return language;
            }
        }

        return RU;
    }

    public static MenuLanguage fromGameLanguageCode(String code) {
        if (code == null) {
            return EN;
        }

        return code.toLowerCase(Locale.ROOT).startsWith("ru") ? RU : EN;
    }
}
