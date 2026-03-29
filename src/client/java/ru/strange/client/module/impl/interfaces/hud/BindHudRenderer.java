package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BindHudRenderer {

    private static final float ROW_H           = 16f;
    private static final float HEADER_H        = 20f;
    private static final float PAD_X           = 8f;
    private static final float PAD_Y           = 5f;
    private static final float TEXT_SIZE       = 5f;
    private static final float HEADER_SIZE     = 6f;
    private static final float HEADER_ICON     = 10f;
    private static final float ROW_ICON_SIZE   = 9f;
    private static final float MIN_W           = 92f;

    private static final float NAME_ICON_GAP   = 4f;
    private static final float ICON_BIND_GAP   = 8f;

    private static final String[][] PREVIEW = {
            {"AutoSwap", "R", "utilities"},
            {"KillAura", "H", "player"},
            {"ClickGui", "M", "interface"}
    };

    private final WaterMark owner;

    private final Identifier keyboardIcon;
    private final Identifier playerIcon;
    private final Identifier otherIcon;
    private final Identifier worldIcon;
    private final Identifier utilitiesIcon;
    private final Identifier interfaceIcon;

    public BindHudRenderer(WaterMark owner,
                           Identifier keyboardIcon,
                           Identifier playerIcon,
                           Identifier otherIcon,
                           Identifier worldIcon,
                           Identifier utilitiesIcon,
                           Identifier interfaceIcon) {
        this.owner = owner;
        this.keyboardIcon = keyboardIcon;
        this.playerIcon = playerIcon;
        this.otherIcon = otherIcon;
        this.worldIcon = worldIcon;
        this.utilitiesIcon = utilitiesIcon;
        this.interfaceIcon = interfaceIcon;
    }

    public void render(DrawContext ctx, float x, float y) {
        List<Module> modules = getBoundModules();
        boolean preview = modules.isEmpty() && owner.isEditing();
        int count = preview ? PREVIEW.length : modules.size();
        if (count <= 0) return;

        float totalW = getWidth();
        float totalH = getHeight();

        RenderUtil.drawClientRect(ctx, x, y, totalW, totalH);

        int headerColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 215);
        int lineColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 78);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx,
                ModLocalization.raw("Бинды"),
                x + PAD_X,
                y + PAD_Y + 10.8f,
                (int) HEADER_SIZE,
                headerColor);

        float headerIconX = x + totalW - PAD_X - HEADER_ICON;
        float headerIconY = y + PAD_Y + 2f;
        RenderUtil.Image.draw(ctx, keyboardIcon,
                headerIconX, headerIconY,
                HEADER_ICON, HEADER_ICON,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210));

        RenderUtil.Round.draw(ctx,
                x + PAD_X,
                y + HEADER_H,
                totalW - PAD_X * 2f,
                0.9f,
                0.4f,
                lineColor);

        float rowY = y + HEADER_H + PAD_Y;

        for (int i = 0; i < count; i++) {
            String name;
            String bind;
            Identifier icon;

            if (preview) {
                name = ModLocalization.raw(PREVIEW[i][0]);
                bind = formatBindText(PREVIEW[i][1]);
                icon = getPreviewIcon(PREVIEW[i][2]);
            } else {
                Module module = modules.get(i);
                name = module.getDisplayName();
                bind = formatBindText(module.getBindName());
                icon = getModuleIcon(module);
            }

            drawRow(ctx, x, rowY, totalW, name, bind, icon);
            rowY += ROW_H;
        }
    }

    public float getWidth() {
        List<Module> modules = getBoundModules();
        boolean preview = modules.isEmpty() && owner.isEditing();

        float maxNameBlockW = 0f;
        float maxBindW = 0f;

        if (preview) {
            for (String[] row : PREVIEW) {
                float nameW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, ModLocalization.raw(row[0]), (int) TEXT_SIZE);
                float nameBlockW = nameW + NAME_ICON_GAP + ROW_ICON_SIZE;

                maxNameBlockW = Math.max(maxNameBlockW, nameBlockW);
                maxBindW = Math.max(maxBindW,
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, formatBindText(row[1]), (int) TEXT_SIZE));
            }
        } else {
            for (Module module : modules) {
                float nameW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, module.getDisplayName(), (int) TEXT_SIZE);
                float nameBlockW = nameW + NAME_ICON_GAP + ROW_ICON_SIZE;

                maxNameBlockW = Math.max(maxNameBlockW, nameBlockW);
                maxBindW = Math.max(maxBindW,
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, formatBindText(module.getBindName()), (int) TEXT_SIZE));
            }
        }

        float rowsW =
                PAD_X +
                        maxNameBlockW +
                        ICON_BIND_GAP +
                        maxBindW +
                        PAD_X;

        float headerW =
                PAD_X +
                        FontDraw.getWidth(FontDraw.FontType.MEDIUM, ModLocalization.raw("Бинды"), (int) HEADER_SIZE) +
                        8f +
                        HEADER_ICON +
                        PAD_X;

        return Math.max(MIN_W, Math.max(rowsW, headerW));
    }

    public float getHeight() {
        int count = getDisplayCount();
        if (count <= 0) return 0f;
        return HEADER_H + PAD_Y + count * ROW_H + PAD_Y;
    }

    public int getDisplayCount() {
        int count = getBoundModules().size();
        if (count <= 0 && owner.isEditing()) return PREVIEW.length;
        return count;
    }

    private void drawRow(DrawContext ctx, float x, float y, float totalW,
                         String name, String bind, Identifier icon) {
        int textColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 220);
        int bindColor = RenderUtil.ColorUtil.replAlpha(
                RenderUtil.ColorUtil.getTextColor(1, 1), 170);

        float textY = y + ROW_H / 2f + 2.5f;

        float bindW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, bind, (int) TEXT_SIZE);
        float bindX = x + totalW - PAD_X - bindW;

        float maxNameWidth = bindX - (x + PAD_X) - ROW_ICON_SIZE - NAME_ICON_GAP - ICON_BIND_GAP;
        if (maxNameWidth < 10f) maxNameWidth = 10f;

        String trimmedName = owner.trimToWidth(name, maxNameWidth, (int) TEXT_SIZE);

        float nameX = x + PAD_X;
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, trimmedName,
                nameX, textY,
                (int) TEXT_SIZE, textColor);

        float nameW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, trimmedName, (int) TEXT_SIZE);

        float iconX = nameX + nameW + NAME_ICON_GAP;
        float iconY = y + (ROW_H / 2f) - (ROW_ICON_SIZE / 2f);

        RenderUtil.Image.draw(ctx, icon,
                iconX, iconY,
                ROW_ICON_SIZE, ROW_ICON_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, bind,
                bindX, textY,
                (int) TEXT_SIZE, bindColor);
    }

    private List<Module> getBoundModules() {
        List<Module> list = new ArrayList<>();

        if (Strange.get == null || Strange.get.manager == null) {
            return list;
        }

        for (Module module : Strange.get.manager.getModules()) {
            if (module == null || module == owner) continue;
            if (module.getBind() == -1) continue;
            if (!module.enable) continue;
            list.add(module);
        }

        list.sort(Comparator.comparing(Module::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private Identifier getPreviewIcon(String type) {
        return switch (type) {
            case "player" -> playerIcon;
            case "world" -> worldIcon;
            case "utilities" -> utilitiesIcon;
            case "interface" -> interfaceIcon;
            default -> otherIcon;
        };
    }

    private Identifier getModuleIcon(Module module) {
        if (module == null || module.category == null) {
            return otherIcon;
        }

        String cat = module.category.name().toLowerCase();

        if (cat.contains("player") || cat.contains("combat") || cat.contains("movement")) {
            return playerIcon;
        }
        if (cat.contains("world") || cat.contains("render")) {
            return worldIcon;
        }
        if (cat.contains("util") || cat.contains("misc")) {
            return utilitiesIcon;
        }
        if (cat.contains("interface") || cat.contains("client")) {
            return interfaceIcon;
        }

        return otherIcon;
    }

    private String formatBindText(String bind) {
        if (bind == null || bind.isEmpty()) {
            return ModLocalization.raw("NONE");
        }

        return switch (bind) {
            case "NONE", "null" -> ModLocalization.raw("NONE");

            case "Space" -> ModLocalization.raw("Space");
            case "Enter" -> ModLocalization.raw("Enter");
            case "Tab" -> ModLocalization.raw("Tab");
            case "BackSpace" -> ModLocalization.raw("BackSpace");
            case "Insert" -> ModLocalization.raw("Insert");
            case "Delete" -> ModLocalization.raw("Delete");
            case "Right" -> ModLocalization.raw("Right");
            case "Left" -> ModLocalization.raw("Left");
            case "Down" -> ModLocalization.raw("Down");
            case "Up" -> ModLocalization.raw("Up");
            case "PageUp" -> ModLocalization.raw("PageUp");
            case "PageDown" -> ModLocalization.raw("PageDown");
            case "Home" -> ModLocalization.raw("Home");
            case "End" -> ModLocalization.raw("End");
            case "CapsLock" -> ModLocalization.raw("CapsLock");
            case "ScrollLock" -> ModLocalization.raw("ScrollLock");
            case "NumLock" -> ModLocalization.raw("NumLock");
            case "PrintScreen" -> ModLocalization.raw("PrintScreen");
            case "Pause" -> ModLocalization.raw("Pause");
            case "Menu" -> ModLocalization.raw("Menu");

            case "LeftShift" -> ModLocalization.raw("LeftShift");
            case "RightShift" -> ModLocalization.raw("RightShift");
            case "LeftControl" -> ModLocalization.raw("LeftControl");
            case "RightControl" -> ModLocalization.raw("RightControl");
            case "LeftAlt" -> ModLocalization.raw("LeftAlt");
            case "RightAlt" -> ModLocalization.raw("RightAlt");
            case "LeftSuper" -> ModLocalization.raw("LeftSuper");
            case "RightSuper" -> ModLocalization.raw("RightSuper");

            case "Mouse Left" -> ModLocalization.raw("Mouse Left");
            case "Mouse Right" -> ModLocalization.raw("Mouse Right");
            case "Mouse Middle" -> ModLocalization.raw("Mouse Middle");
            case "Mouse 4" -> ModLocalization.raw("Mouse 4");
            case "Mouse 5" -> ModLocalization.raw("Mouse 5");
            case "Mouse 6" -> ModLocalization.raw("Mouse 6");
            case "Mouse 7" -> ModLocalization.raw("Mouse 7");
            case "Mouse 8" -> ModLocalization.raw("Mouse 8");

            case "M1" -> ModLocalization.raw("M1");
            case "M2" -> ModLocalization.raw("M2");
            case "M3" -> ModLocalization.raw("M3");
            case "M4" -> ModLocalization.raw("M4");
            case "M5" -> ModLocalization.raw("M5");
            case "M6" -> ModLocalization.raw("M6");
            case "M7" -> ModLocalization.raw("M7");
            case "M8" -> ModLocalization.raw("M8");

            default -> bind;
        };
    }
}