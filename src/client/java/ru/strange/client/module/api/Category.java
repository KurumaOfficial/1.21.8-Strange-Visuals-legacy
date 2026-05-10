package ru.strange.client.module.api;

import ru.strange.client.ui.clickgui.localization.GuiLocalization;

public enum Category {
    Player("На игроке"),
    World("В мире"),
    Utilities("Утилиты"),
    Other("Остальное"),
    Interface("Интерфейс"),
    Theme("Темы"),
    Combat("Бой");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return GuiLocalization.categoryName(this, name);
    }
}
