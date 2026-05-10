package ru.strange.client.module;

import ru.strange.client.ui.clickgui.localization.GuiLocalization;

import java.awt.*;

public enum Theme {

    WHITE("Белая",
            new Color(0xF7F8FA),
            new Color(0xFFFFFF),
            new Color(0x000000)
    ),

    BLACK("Чёрная",
            new Color(0x141414),
            new Color(0x171717),
            new Color(0xFFFFFF)
    ),

    TRANSPARENT_WHITE("Прозрачная Белая",
            new Color(0x80F7F8FA, true),
            new Color(0xFFFFFF),
            new Color(0x000000)
    ),

    TRANSPARENT_BLACK("Прозрачная Чёрная",
            new Color(0x80141414, true),
            new Color(0x171717),
            new Color(0xFFFFFF)
    ),

    PINK("Розовая",
            new Color(0xFFCCE2),
            new Color(0x80FFD9E9, true),
            new Color(0x000000)
    ),

    PURPLE("Фиолетовая",
            new Color(0xB2A4FF),
            new Color(0x80C6BCFF, true),
            new Color(0x000000)
    );

    private final String name;
    private final Color main;
    private final Color bg;
    private final Color text;

    Theme(String name, Color main, Color bg, Color text) {
        this.name = name;
        this.main = main;
        this.bg = bg;
        this.text = text;
    }

    public String getName() { return GuiLocalization.themeName(this, name); }
    public Color getMain() { return main; }
    public Color getBg() { return bg; }
    public Color getText() { return text; }
}
