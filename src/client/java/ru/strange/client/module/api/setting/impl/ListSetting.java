package ru.strange.client.module.api.setting.impl;

import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.ui.clickgui.localization.GuiLocalization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

public class ListSetting extends Setting {
    public List<String> list;
    public boolean opened;
    public String description;
    public List<String> selected = new ArrayList<>();
//    public Animation animation = new EaseInOutQuad(300, 1);
//    public Animation animation2 = new EaseInOutQuad(300, 1);

    public ListSetting(String name, String... settings) {
        this.name = name;
        this.list = Arrays.asList(settings);
        this.description = description;
    }

    public ListSetting hidden(Supplier<Boolean> hidden) {
        this.hidden = hidden;
        return this;
    }

    public String getFormattedList() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < list.size(); i++) {
            sb.append(ModLocalization.raw(list.get(i)));

            if (i == 2 && list.size() > 3) {
                sb.append("...");
                break;
            }

            if (i < list.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    public boolean isSelected(String element) {
        return selected.contains(element);
    }

    public void toggle(String element) {
        if (element == null || !list.contains(element)) {
            return;
        }

        if (selected.contains(element)) {
            selected.remove(element);
        } else {
            selected.add(element);
        }

        triggerAutoSave();
    }

    public String getSelectedDisplay() {
        if (selected.isEmpty()) {
            return GuiLocalization.tr("gui.list.empty");
        }

        List<String> localized = new ArrayList<>();
        for (String value : selected) {
            localized.add(ModLocalization.raw(value));
        }

        String joined = String.join(", ", localized);
        return joined.length() > 18 ? joined.substring(0, 18) + "..." : joined;
    }
}
