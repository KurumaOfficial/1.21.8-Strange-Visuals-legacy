package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD-элемент: список активных модулей.
 */
public class ModuleListHud extends HudElement {

    /* ── константы ── */
    public static final float ITEM_HEIGHT = 12.8f;
    public static final float ITEM_STEP   = 13.6f;
    public static final float ICON_SIZE   = 7.5f;
    public static final float TEXT_SIZE   = 5f;

    private static final String[] PREVIEW = {
            "Боксы",
            "Джампики",
            "Китайская шляпа",
            "Кубики",
            "Таргет рендер"
    };

    /* ── иконки категорий ── */
    private final Identifier playerIcon     = Identifier.of(Strange.rootRes, "/icons/gui/player.png");
    private final Identifier otherIcon      = Identifier.of(Strange.rootRes, "/icons/gui/other.png");
    private final Identifier worldIcon      = Identifier.of(Strange.rootRes, "/icons/gui/world.png");
    private final Identifier utilitiesIcon  = Identifier.of(Strange.rootRes, "/icons/gui/utilities.png");
    private final Identifier interfaceIcon  = Identifier.of(Strange.rootRes, "/icons/gui/interface.png");

    /* ── кеш иконок (CRIT-03) ── */
    private static final Map<Class<?>, Identifier> iconCache = new ConcurrentHashMap<>();

    /** Модуль-владелец (исключается из списка). */
    private final Module hostModule;

    public ModuleListHud(Module hostModule) {
        this.hostModule = hostModule;
    }

    /* ── позиция по умолчанию ── */

    @Override
    public void initPosition(int sw, int sh) {
        x = 10f;
        y = 48f;
    }

    /* ── размеры ── */

    @Override
    public float getWidth() {
        return Math.max(40f, getListWidth());
    }

    @Override
    public float getHeight() {
        int count = getVisibleModules().size();
        if (count == 0 && editing) count = PREVIEW.length;
        if (count == 0) return 12f;
        return ITEM_HEIGHT + (count - 1) * ITEM_STEP;
    }

    /* ── рендер ── */

    @Override
    public void render(DrawContext ctx, boolean editing) {
        this.editing = editing;
        renderModuleList(ctx);
    }

    private void renderModuleList(DrawContext ctx) {
        List<Module> modules = getVisibleModules();
        boolean preview = modules.isEmpty() && editing;

        if (!preview && modules.isEmpty()) return;

        float currentY = y;

        if (preview) {
            for (int i = 0; i < PREVIEW.length; i++) {
                String name = PREVIEW[i];
                float width = getCardWidth(name);

                RenderUtil.drawClientRect(ctx, x, currentY, width, ITEM_HEIGHT);

                Identifier icon = getPreviewIcon(i);
                RenderUtil.Image.draw(
                        ctx,
                        icon,
                        x + 4.5f,
                        currentY + 2.7f,
                        ICON_SIZE,
                        ICON_SIZE,
                        RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210)
                );

                FontDraw.drawText(
                        FontDraw.FontType.MEDIUM,
                        ctx,
                        name,
                        x + 14.5f,
                        currentY + 8.65f,
                        (int) TEXT_SIZE,
                        textColor()
                );

                currentY += ITEM_STEP;
            }
            return;
        }

        for (Module module : modules) {
            String name = module.name;
            float width = getCardWidth(name);

            RenderUtil.drawClientRect(ctx, x, currentY, width, ITEM_HEIGHT);

            Identifier icon = getModuleIcon(module);
            RenderUtil.Image.draw(
                    ctx,
                    icon,
                    x + 4.5f,
                    currentY + 2.7f,
                    ICON_SIZE,
                    ICON_SIZE,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210)
            );

            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    name,
                    x + 14.5f,
                    currentY + 8.65f,
                    (int) TEXT_SIZE,
                    textColor()
            );

            currentY += ITEM_STEP;
        }
    }

    /* ── вспомогательные методы ── */

    public List<Module> getVisibleModules() {
        List<Module> visible = new ArrayList<>();

        for (Module module : Strange.get.manager.getModules()) {
            if (module == null) continue;
            if (module == hostModule) continue;
            if (!module.enable) continue;

            visible.add(module);
        }

        return visible;
    }

    public float getCardWidth(String name) {
        return FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, (int) TEXT_SIZE) + 24f;
    }

    private float getListWidth() {
        List<Module> modules = getVisibleModules();
        float width = 60f;

        if (modules.isEmpty() && editing) {
            for (String preview : PREVIEW) {
                width = Math.max(width, getCardWidth(preview));
            }
            return width;
        }

        for (Module module : modules) {
            width = Math.max(width, getCardWidth(module.name));
        }

        return width;
    }

    private float getListHeight() {
        int count = getVisibleModules().size();
        if (count == 0 && editing) count = PREVIEW.length;
        if (count == 0) return 0f;
        return ITEM_HEIGHT + (count - 1) * ITEM_STEP;
    }

    private Identifier getPreviewIcon(int index) {
        if (index == 0) return playerIcon;
        if (index == 1) return utilitiesIcon;
        if (index == 2) return worldIcon;
        if (index == 3) return otherIcon;
        return interfaceIcon;
    }

    public Identifier getModuleIcon(Module module) {
        return iconCache.computeIfAbsent(module.getClass(), clazz -> {
            try {
                IModule annotation = clazz.getAnnotation(IModule.class);
                if (annotation != null) {
                    String cat = annotation.category().name().toLowerCase();
                    if (cat.contains("player") || cat.contains("combat") || cat.contains("movement")) return playerIcon;
                    if (cat.contains("world") || cat.contains("render")) return worldIcon;
                    if (cat.contains("util") || cat.contains("misc")) return utilitiesIcon;
                    if (cat.contains("interface") || cat.contains("client")) return interfaceIcon;
                }
            } catch (Throwable ignored) {}
            return otherIcon;
        });
    }

    public int textColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220);
    }
}
