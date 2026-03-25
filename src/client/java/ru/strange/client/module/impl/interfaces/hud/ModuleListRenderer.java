package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Список активных модулей (HUD-панель слева).
 */
public final class ModuleListRenderer {

    // ── layout constants ──────────────────────────────────────────────
    private static final float ITEM_HEIGHT = 12.8f;
    private static final float ITEM_STEP   = 13.6f;
    private static final float ICON_SIZE   =  7.5f;
    private static final float TEXT_SIZE   =  5f;

    private static final String[] PREVIEW_NAMES = {
            "Боксы", "Джампики", "Китайская шляпа", "Кубики", "Таргет рендер"
    };

    private final WaterMark owner;

    // category icons – resolved from owner's Identifiers
    private final Identifier playerIcon;
    private final Identifier otherIcon;
    private final Identifier worldIcon;
    private final Identifier utilitiesIcon;
    private final Identifier interfaceIcon;

    public ModuleListRenderer(WaterMark owner,
                              Identifier playerIcon,
                              Identifier otherIcon,
                              Identifier worldIcon,
                              Identifier utilitiesIcon,
                              Identifier interfaceIcon) {
        this.owner          = owner;
        this.playerIcon     = playerIcon;
        this.otherIcon      = otherIcon;
        this.worldIcon      = worldIcon;
        this.utilitiesIcon  = utilitiesIcon;
        this.interfaceIcon  = interfaceIcon;
    }

    // ── public API ────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        List<Module> modules = getVisibleModules();
        boolean preview = modules.isEmpty() && owner.isEditing();
        if (!preview && modules.isEmpty()) return;

        float currentY = y;

        if (preview) {
            for (int i = 0; i < PREVIEW_NAMES.length; i++) {
                drawRow(ctx, x, currentY, ModLocalization.raw(PREVIEW_NAMES[i]), getPreviewIcon(i));
                currentY += ITEM_STEP;
            }
            return;
        }

        for (Module module : modules) {
            drawRow(ctx, x, currentY, module.getDisplayName(), getModuleIcon(module));
            currentY += ITEM_STEP;
        }
    }

    public float getWidth() {
        List<Module> modules = getVisibleModules();
        float width = 60f;

        if (modules.isEmpty() && owner.isEditing()) {
            for (String p : PREVIEW_NAMES) width = Math.max(width, cardWidth(ModLocalization.raw(p)));
            return width;
        }
        for (Module m : modules) width = Math.max(width, cardWidth(m.getDisplayName()));
        return width;
    }

    public float getHeight() {
        int count = getVisibleModules().size();
        if (count == 0 && owner.isEditing()) count = PREVIEW_NAMES.length;
        if (count == 0) return 0f;
        return ITEM_HEIGHT + (count - 1) * ITEM_STEP;
    }

    // ── private helpers ───────────────────────────────────────────────

    private void drawRow(DrawContext ctx, float x, float y, String name, Identifier icon) {
        float width = cardWidth(name);
        RenderUtil.drawClientRect(ctx, x, y, width, ITEM_HEIGHT);

        RenderUtil.Image.draw(ctx, icon,
                x + 4.5f, y + 2.7f,
                ICON_SIZE, ICON_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, name,
                x + 14.5f, y + 8.65f,
                (int) TEXT_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));
    }

    private float cardWidth(String name) {
        return FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, (int) TEXT_SIZE) + 24f;
    }

    private List<Module> getVisibleModules() {
        List<Module> visible = new ArrayList<>();
        for (Module m : Strange.get.manager.getModules()) {
            if (m == null || m == owner || !m.enable) continue;
            visible.add(m);
        }
        return visible;
    }

    private Identifier getPreviewIcon(int index) {
        return switch (index) {
            case 0 -> playerIcon;
            case 1 -> utilitiesIcon;
            case 2 -> worldIcon;
            case 3 -> otherIcon;
            default -> interfaceIcon;
        };
    }

    private Identifier getModuleIcon(Module module) {
        try {
            IModule ann = module.getClass().getAnnotation(IModule.class);
            if (ann != null) {
                String cat = ann.category().name().toLowerCase();
                if (cat.contains("player") || cat.contains("combat") || cat.contains("movement")) return playerIcon;
                if (cat.contains("world")  || cat.contains("render"))                              return worldIcon;
                if (cat.contains("util")   || cat.contains("misc"))                                return utilitiesIcon;
                if (cat.contains("interface") || cat.contains("client"))                            return interfaceIcon;
            }
        } catch (Exception ignored) {}
        return otherIcon;
    }
}
