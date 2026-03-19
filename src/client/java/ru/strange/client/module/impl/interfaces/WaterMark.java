package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;
import ru.strange.client.module.impl.interfaces.hud.*;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

@IModule(
        name = "Водяной знак",
        description = "",
        category = Category.Interface,
        bind = -1
)
public class WaterMark extends Module {

    public MultiBooleanSetting settings = new MultiBooleanSetting(
            "Ватермарка",
            new BooleanSetting("Ник", true),
            new BooleanSetting("ФПС", true),
            new BooleanSetting("Пинг", true),
            new BooleanSetting("Время", false),
            new BooleanSetting("Сервер", true)
    );

    public MultiBooleanSetting elements = new MultiBooleanSetting(
            "Элементы",
            new BooleanSetting("Модули", true),
            new BooleanSetting("Таргет худ", true),
            new BooleanSetting("Инвентарь", true),
            new BooleanSetting("Потионы", true),
            new BooleanSetting("Кулдауны", true),
            new BooleanSetting("Координаты", true),
            new BooleanSetting("Редактирование", false)
    );

    /* ── HUD-элементы ── */

    private final WatermarkBar watermarkBar = new WatermarkBar(settings);
    private final ModuleListHud moduleListHud = new ModuleListHud(this);
    private final TargetHud targetHud = new TargetHud();
    private final InventoryHud inventoryHud = new InventoryHud();
    private final PotionHud potionHud = new PotionHud();
    private final CooldownHud cooldownHud = new CooldownHud();
    private final CoordsHud coordsHud = new CoordsHud();

    private boolean initialized;
    private boolean lastMouseDown;
    private int activeDrag;
    private float dragOffsetX, dragOffsetY;

    public WaterMark() {
        addSettings(settings);
        addSettings(elements);
    }

    /**
     * Вызывается из InGameHudMixin для подавления ванильного Potion HUD.
     */
    public static boolean shouldHideVanillaPotionHud() {
        try {
            for (Module module : Strange.get.manager.getModules()) {
                if (module instanceof WaterMark wm) {
                    return wm.enable && wm.elements.get("Потионы");
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /* =========================
       ГЛАВНЫЙ ОБРАБОТЧИК
       ========================= */

    @EventInit
    public void onScreen(EventScreen e) {
        if (mc.player == null || mc.world == null) return;

        DrawContext ctx = e.drawContext();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        if (!initialized) {
            initialized = true;
            watermarkBar.initPosition(sw, sh);
            moduleListHud.initPosition(sw, sh);
            targetHud.initPosition(sw, sh);
            inventoryHud.initPosition(sw, sh);
            potionHud.initPosition(sw, sh);
            cooldownHud.initPosition(sw, sh);
            coordsHud.initPosition(sw, sh);
        }

        boolean edit = isEditing();
        watermarkBar.setEditing(edit);
        moduleListHud.setEditing(edit);
        targetHud.setEditing(edit);
        inventoryHud.setEditing(edit);
        potionHud.setEditing(edit);
        cooldownHud.setEditing(edit);
        coordsHud.setEditing(edit);

        updateDragging(sw, sh, edit);

        watermarkBar.render(ctx, edit);
        if (elements.get("Модули"))      moduleListHud.render(ctx, edit);
        if (elements.get("Таргет худ"))  targetHud.render(ctx, edit);
        if (elements.get("Инвентарь"))   inventoryHud.render(ctx, edit);
        if (elements.get("Потионы"))     potionHud.render(ctx, edit);
        if (elements.get("Кулдауны"))    cooldownHud.render(ctx, edit);
        if (elements.get("Координаты"))  coordsHud.render(ctx, edit);

        if (edit) renderEditor(ctx);

        lastMouseDown = HudElement.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
    }

    /* =========================
       DRAG EDITOR
       ========================= */

    private void updateDragging(int sw, int sh, boolean edit) {
        if (!edit) { activeDrag = 0; return; }

        float mouseX = getMouseX();
        float mouseY = getMouseY();
        boolean down = HudElement.isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
        boolean clicked = down && !lastMouseDown;
        boolean alt = isAltDown();

        if (clicked && alt) {
            if (elements.get("Координаты") && inside(mouseX, mouseY, coordsHud))
                startDrag(7, mouseX, mouseY, coordsHud);
            else if (elements.get("Кулдауны") && inside(mouseX, mouseY, cooldownHud))
                startDrag(6, mouseX, mouseY, cooldownHud);
            else if (elements.get("Потионы") && inside(mouseX, mouseY, potionHud))
                startDrag(5, mouseX, mouseY, potionHud);
            else if (elements.get("Инвентарь") && inside(mouseX, mouseY, inventoryHud))
                startDrag(4, mouseX, mouseY, inventoryHud);
            else if (elements.get("Таргет худ") && inside(mouseX, mouseY, targetHud))
                startDrag(3, mouseX, mouseY, targetHud);
            else if (elements.get("Модули") && inside(mouseX, mouseY, moduleListHud))
                startDrag(2, mouseX, mouseY, moduleListHud);
            else if (inside(mouseX, mouseY, watermarkBar))
                startDrag(1, mouseX, mouseY, watermarkBar);
        }

        if (!down || !alt) { activeDrag = 0; return; }

        HudElement dragged = getDragged();
        if (dragged != null) {
            dragged.x = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - dragged.getWidth() - 2f);
            dragged.y = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - dragged.getHeight() - 2f);
        }
    }

    private void startDrag(int id, float mx, float my, HudElement el) {
        activeDrag = id;
        dragOffsetX = mx - el.x;
        dragOffsetY = my - el.y;
    }

    private HudElement getDragged() {
        return switch (activeDrag) {
            case 1 -> watermarkBar;
            case 2 -> moduleListHud;
            case 3 -> targetHud;
            case 4 -> inventoryHud;
            case 5 -> potionHud;
            case 6 -> cooldownHud;
            case 7 -> coordsHud;
            default -> null;
        };
    }

    private void renderEditor(DrawContext ctx) {
        int c = editorColor();

        drawEditorFrame(ctx, watermarkBar, "Watermark", 6f, c);

        if (elements.get("Модули") && moduleListHud.getHeight() > 0f)
            drawEditorFrame(ctx, moduleListHud, "Modules", 6f, c);

        if (elements.get("Таргет худ"))
            drawEditorFrame(ctx, targetHud, "Target", 6f, c);

        if (elements.get("Инвентарь"))
            drawEditorFrame(ctx, inventoryHud, "Inventory", 9f, c);

        if (elements.get("Потионы") && potionHud.getHeight() > 0f)
            drawEditorFrame(ctx, potionHud, "Potions", 4f, c);

        if (elements.get("Кулдауны") && cooldownHud.getHeight() > 0f)
            drawEditorFrame(ctx, cooldownHud, "Cooldowns", 9f, c);

        if (elements.get("Координаты"))
            drawEditorFrame(ctx, coordsHud, "Coords", 6f, c);

        String hint = "ALT + ЛКМ - двигать";
        float hintW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, hint, 4) + 10f;
        float hx = ctx.getScaledWindowWidth() / 2f - hintW / 2f;
        float hy = 44f;

        RenderUtil.drawClientRect(ctx, hx, hy, hintW, 12f);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, hint,
                hx + 5f, hy + 8f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));
    }

    private void drawEditorFrame(DrawContext ctx, HudElement el, String label, float radius, int color) {
        RenderUtil.Border.draw(ctx, el.x - 1f, el.y - 1f,
                el.getWidth() + 2f, el.getHeight() + 2f, radius, 0.7f, color);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, label,
                el.x + 4f, Math.max(4f, el.y - 2f), 4, color);
    }

    /* =========================
       HELPERS
       ========================= */

    private boolean isEditing() {
        return elements.get("Редактирование") && mc.currentScreen != null;
    }

    private boolean isAltDown() {
        long handle = mc.getWindow().getHandle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    private float getMouseX() {
        return (float) (mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth());
    }

    private float getMouseY() {
        return (float) (mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight());
    }

    private boolean inside(float mx, float my, HudElement el) {
        return mx >= el.x && my >= el.y && mx <= el.x + el.getWidth() && my <= el.y + el.getHeight();
    }

    private int editorColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 200);
    }
}
