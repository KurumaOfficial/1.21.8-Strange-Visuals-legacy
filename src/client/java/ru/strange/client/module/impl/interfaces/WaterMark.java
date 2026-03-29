package ru.strange.client.module.impl.interfaces;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;
import ru.strange.client.module.impl.interfaces.hud.*;
import ru.strange.client.utils.other.ServerUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@IModule(
        name = "Водяной знак",
        description = "",
        category = Category.Interface,
        bind = -1
)
public class WaterMark extends Module {
    private static final String SET_NICK = "Ник";
    private static final String SET_FPS = "ФПС";
    private static final String SET_PING = "Пинг";
    private static final String SET_TIME = "Время";
    private static final String SET_SERVER = "Сервер";

    private static final String EL_MODULES = "Модули";
    private static final String EL_TARGET_HUD = "Таргет худ";
    private static final String EL_INVENTORY = "Инвентарь";
    private static final String EL_POTIONS = "Потионы";
    private static final String EL_COOLDOWNS = "Кулдауны";
    private static final String EL_COORDS = "Координаты";
    private static final String EL_BINDS = "Бинды";
    private static final String EL_EDIT_MODE = "Редактирование";

    private static final String POTION_STYLE_COLUMNS = "Столбцы";
    private static final String POTION_STYLE_LIST = "Список";

    private static final String[] WATERMARK_SEGMENT_KEYS = {
            SET_NICK, SET_FPS, SET_PING, SET_TIME, SET_SERVER
    };
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public MultiBooleanSetting settings = new MultiBooleanSetting(
            "Ватермарка",
            new BooleanSetting(SET_NICK, true),
            new BooleanSetting(SET_FPS, true),
            new BooleanSetting(SET_PING, true),
            new BooleanSetting(SET_TIME, false),
            new BooleanSetting(SET_SERVER, true)
    );

    public MultiBooleanSetting elements = new MultiBooleanSetting(
            "Элементы",
            new BooleanSetting(EL_MODULES, true),
            new BooleanSetting(EL_TARGET_HUD, true),
            new BooleanSetting(EL_INVENTORY, true),
            new BooleanSetting(EL_POTIONS, true),
            new BooleanSetting(EL_COOLDOWNS, true),
            new BooleanSetting(EL_COORDS, true),
            new BooleanSetting(EL_BINDS, true),
            new BooleanSetting(EL_EDIT_MODE, false)
    );

    public ModeSetting potionStyle = new ModeSetting("Стиль потионов", POTION_STYLE_COLUMNS, POTION_STYLE_COLUMNS, POTION_STYLE_LIST);

    private final Identifier logoIcon      = Strange.id("icons/gui/logo.png");
    private final Identifier playerIcon    = Strange.id("icons/gui/player.png");
    private final Identifier otherIcon     = Strange.id("icons/gui/other.png");
    private final Identifier worldIcon     = Strange.id("icons/gui/world.png");
    private final Identifier utilitiesIcon = Strange.id("icons/gui/utilities.png");
    private final Identifier interfaceIcon = Strange.id("icons/gui/interface.png");
    private final Identifier coordsIcon    = Strange.id("icons/gui/coord.png");
    private final Identifier inventoryIcon = Strange.id("icons/gui/invent.png");
    private final Identifier keyboardIcon  = Strange.id("icons/gui/keyboard.png");

    private float animatedWidth = 71f;
    private final Map<String, Float> segProgress = new LinkedHashMap<>();

    private static final float HEIGHT      = 16f;
    private static final float WIDTH_SPEED = 0.18f;
    private static final float SEG_SPEED   = 0.22f;

    private boolean initialized;
    private boolean lastMouseDown;

    private int   activeDrag;
    private float dragOffsetX;
    private float dragOffsetY;

    float watermarkX = 10f;
    float watermarkY = 10f;
    float modulesX;
    float modulesY;
    float targetX;
    float targetY;
    float inventoryX;
    float inventoryY;
    float potionsX;
    float potionsY;
    float cooldownsX;
    float cooldownsY;
    float coordsX;
    float coordsY;
    float bindsX;
    float bindsY;

    public final TargetHudRenderer targetRenderer;
    private final ModuleListRenderer moduleList;
    private final InventoryHudRenderer inventory;
    private final PotionHudRenderer potion;
    private final CooldownHudRenderer cooldown;
    private final CoordsHudRenderer coords;
    private final BindHudRenderer binds;

    public static WaterMark INSTANCE;

    public static boolean shouldHideVanillaPotionHud() {
        return INSTANCE != null && INSTANCE.enable && INSTANCE.elements.get(EL_POTIONS);
    }

    public WaterMark() {
        INSTANCE = this;
        addSettings(settings);
        addSettings(elements);
        addSettings(potionStyle);

        targetRenderer = new TargetHudRenderer(this);
        moduleList     = new ModuleListRenderer(this, playerIcon, otherIcon, worldIcon, utilitiesIcon, interfaceIcon);
        inventory      = new InventoryHudRenderer(this, inventoryIcon);
        potion         = new PotionHudRenderer(this);
        cooldown       = new CooldownHudRenderer(this);
        coords         = new CoordsHudRenderer(this, coordsIcon);
        binds          = new BindHudRenderer(this, keyboardIcon, playerIcon, otherIcon, worldIcon, utilitiesIcon, interfaceIcon);
    }

    public void setPotionMode(int mode) {
        potion.setMode(mode);
    }

    public int getPotionMode() {
        return potion.getMode();
    }

    @Override
    public JsonObject save() {
        JsonObject object = super.save();
        JsonObject layout = new JsonObject();
        layout.addProperty("initialized", initialized);
        layout.addProperty("watermarkX", watermarkX);
        layout.addProperty("watermarkY", watermarkY);
        layout.addProperty("modulesX", modulesX);
        layout.addProperty("modulesY", modulesY);
        layout.addProperty("targetX", targetX);
        layout.addProperty("targetY", targetY);
        layout.addProperty("inventoryX", inventoryX);
        layout.addProperty("inventoryY", inventoryY);
        layout.addProperty("potionsX", potionsX);
        layout.addProperty("potionsY", potionsY);
        layout.addProperty("cooldownsX", cooldownsX);
        layout.addProperty("cooldownsY", cooldownsY);
        layout.addProperty("coordsX", coordsX);
        layout.addProperty("coordsY", coordsY);
        layout.addProperty("bindsX", bindsX);
        layout.addProperty("bindsY", bindsY);
        layout.addProperty("potionMode", potion.getMode());
        object.add("HudLayout", layout);
        return object;
    }

    @Override
    public boolean load(JsonObject object) {
        boolean shouldEnable = super.load(object);
        initialized = false;

        if (object != null && object.has("HudLayout") && object.get("HudLayout").isJsonObject()) {
            JsonObject layout = object.getAsJsonObject("HudLayout");
            initialized = layout.has("initialized") && layout.get("initialized").getAsBoolean();
            watermarkX  = readFloat(layout, "watermarkX", watermarkX);
            watermarkY  = readFloat(layout, "watermarkY", watermarkY);
            modulesX    = readFloat(layout, "modulesX", modulesX);
            modulesY    = readFloat(layout, "modulesY", modulesY);
            targetX     = readFloat(layout, "targetX", targetX);
            targetY     = readFloat(layout, "targetY", targetY);
            inventoryX  = readFloat(layout, "inventoryX", inventoryX);
            inventoryY  = readFloat(layout, "inventoryY", inventoryY);
            potionsX    = readFloat(layout, "potionsX", potionsX);
            potionsY    = readFloat(layout, "potionsY", potionsY);
            cooldownsX  = readFloat(layout, "cooldownsX", cooldownsX);
            cooldownsY  = readFloat(layout, "cooldownsY", cooldownsY);
            coordsX     = readFloat(layout, "coordsX", coordsX);
            coordsY     = readFloat(layout, "coordsY", coordsY);
            bindsX      = readFloat(layout, "bindsX", bindsX);
            bindsY      = readFloat(layout, "bindsY", bindsY);

            if (layout.has("potionMode")) {
                potion.setMode(layout.get("potionMode").getAsInt());
            }
        }

        return shouldEnable;
    }

    @EventInit
    public void onScreen(EventScreen e) {
        if (mc.player == null || mc.world == null) return;

        DrawContext ctx = e.drawContext();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        initPositions(sw, sh);
        targetRenderer.updateState();
        potion.setMode(resolvePotionMode());
        moduleList.prepareFrame();
        updateDragging(sw, sh);

        renderWatermark(e);

        if (elements.get(EL_MODULES)) moduleList.render(ctx, modulesX, modulesY);
        if (elements.get(EL_TARGET_HUD)) targetRenderer.render(ctx, targetX, targetY);
        if (elements.get(EL_INVENTORY)) inventory.render(ctx, inventoryX, inventoryY);
        if (elements.get(EL_POTIONS)) potion.render(ctx, potionsX, potionsY);
        if (elements.get(EL_COOLDOWNS)) cooldown.render(ctx, cooldownsX, cooldownsY);
        if (elements.get(EL_COORDS)) coords.render(ctx, coordsX, coordsY);
        if (elements.get(EL_BINDS)) binds.render(ctx, bindsX, bindsY);
        if (isEditing())                renderEditor(ctx);

        lastMouseDown = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (event.getTarget() instanceof net.minecraft.entity.LivingEntity living) {
            targetRenderer.rememberTarget(living);
        }
    }

    private void initPositions(int sw, int sh) {
        if (initialized) return;
        initialized = true;

        modulesX   = 10f;
        modulesY   = 48f;

        targetX    = sw * 0.27f;
        targetY    = sh * 0.53f;

        inventoryX = sw - InventoryHudRenderer.W - 14f;
        inventoryY = sh * 0.17f;

        potionsX   = sw - 96f;
        potionsY   = 8f;

        cooldownsX = 12f;
        cooldownsY = sh * 0.28f;

        coordsX    = 12f;
        coordsY    = sh * 0.24f;

        bindsX     = 12f;
        bindsY     = sh * 0.38f;
    }

    private void renderWatermark(EventScreen e) {
        float x  = watermarkX;
        float y  = watermarkY;
        float dt = 1.0f;

        String nickText   = mc.player.getName().getString();
        String fpsText    = mc.getCurrentFps() + " FPS";
        String pingText;
        String timeText   = LocalTime.now().format(TIME_FORMATTER);
        String serverText = ModLocalization.raw(getServerName());

        int pingValue = -1;
        if (mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            pingValue = mc.getNetworkHandler()
                    .getPlayerListEntry(mc.player.getUuid())
                    .getLatency();
        }
        pingText = pingValue >= 0 ? pingValue + " ms" : "N/A";

        String[] segmentValues = {nickText, fpsText, pingText, timeText, serverText};

        for (int i = 0; i < WATERMARK_SEGMENT_KEYS.length; ++i) {
            String key = WATERMARK_SEGMENT_KEYS[i];
            float p = approach(segProgress.getOrDefault(key, 0f),
                    settings.get(key) ? 1f : 0f,
                    SEG_SPEED * dt);
            segProgress.put(key, p);
        }

        String title      = "Strange Visual";
        float titleWidth  = FontDraw.getWidth(FontDraw.FontType.MEDIUM, title, 7);
        float baseWidth   = 15f + titleWidth + 6f;
        float targetWidth = baseWidth;

        for (int i = 0; i < WATERMARK_SEGMENT_KEYS.length; ++i) {
            float p = segProgress.getOrDefault(WATERMARK_SEGMENT_KEYS[i], 0f);
            if (p <= 0.001f) continue;
            targetWidth += (FontDraw.getWidth(FontDraw.FontType.MEDIUM, segmentValues[i], 7) + 6f) * p;
        }

        animatedWidth = lerp(animatedWidth, targetWidth, WIDTH_SPEED * dt);

        RenderUtil.drawClientRect(e.drawContext(), x, y, animatedWidth - 2f, HEIGHT);

        RenderUtil.Image.draw(e.drawContext(), logoIcon,
                x + 4.09f, y + 4f, 8.17f, 8f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, e.drawContext(), title,
                x + 15f, y + 10.5f, 7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178));

        float cursorX = x + baseWidth;
        for (int i = 0; i < WATERMARK_SEGMENT_KEYS.length; ++i) {
            float p = segProgress.getOrDefault(WATERMARK_SEGMENT_KEYS[i], 0f);
            if (p <= 0.01f) continue;
            String text = segmentValues[i];
            float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
            cursorX = drawSegmentAnimated(e, cursorX, y, text, p, textW, 6f);
        }
    }

    private float drawSegmentAnimated(EventScreen e, float cursorX, float y,
                                      String text, float p, float textW, float pad) {
        float slide = (1f - p) * 6f;
        float drawX = cursorX - slide;

        RenderUtil.Round.draw(e.drawContext(), drawX - 3f, y + 4f, 1f, 8f, 0.5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(150 * p)));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, e.drawContext(), text,
                drawX, y + 10.5f, 7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(178 * p)));

        return cursorX + (textW + pad) * p;
    }

    private void updateDragging(int sw, int sh) {
        if (!isEditing()) { activeDrag = 0; return; }

        float mouseX   = getMouseX();
        float mouseY   = getMouseY();
        boolean down   = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
        boolean clicked = down && !lastMouseDown;
        boolean released = !down && lastMouseDown;
        boolean alt    = isAltDown();

        float potionW   = Math.max(potion.getGroupWidth(), 24f);
        float potionH   = Math.max(potion.getGroupHeight(), 24f);
        float moduleW   = Math.max(moduleList.getWidth(), 40f);
        float moduleH   = Math.max(moduleList.getHeight(), 12f);
        float cooldownH = Math.max(cooldown.getGroupHeight(), CooldownHudRenderer.CARD_H);
        float coordsW   = coords.getWidth();
        float bindsW    = Math.max(binds.getWidth(), 40f);
        float bindsH    = Math.max(binds.getHeight(), 12f);

        if (clicked && alt) {
            if (elements.get(EL_COORDS) && inside(mouseX, mouseY, coordsX, coordsY, coordsW, CoordsHudRenderer.H)) {
                activeDrag = 7; dragOffsetX = mouseX - coordsX; dragOffsetY = mouseY - coordsY;
            } else if (elements.get(EL_COOLDOWNS) && inside(mouseX, mouseY, cooldownsX, cooldownsY, CooldownHudRenderer.CARD_W, cooldownH)) {
                activeDrag = 6; dragOffsetX = mouseX - cooldownsX; dragOffsetY = mouseY - cooldownsY;
            } else if (elements.get(EL_POTIONS) && inside(mouseX, mouseY, potionsX, potionsY, potionW, potionH)) {
                activeDrag = 5; dragOffsetX = mouseX - potionsX; dragOffsetY = mouseY - potionsY;
            } else if (elements.get(EL_INVENTORY) && inside(mouseX, mouseY, inventoryX, inventoryY, InventoryHudRenderer.W, InventoryHudRenderer.H)) {
                activeDrag = 4; dragOffsetX = mouseX - inventoryX; dragOffsetY = mouseY - inventoryY;
            } else if (elements.get(EL_TARGET_HUD) && inside(mouseX, mouseY, targetX, targetY, TargetHudRenderer.getW(), TargetHudRenderer.getH())) {
                activeDrag = 3; dragOffsetX = mouseX - targetX; dragOffsetY = mouseY - targetY;
            } else if (elements.get(EL_MODULES) && inside(mouseX, mouseY, modulesX, modulesY, moduleW, moduleH)) {
                activeDrag = 2; dragOffsetX = mouseX - modulesX; dragOffsetY = mouseY - modulesY;
            } else if (elements.get(EL_BINDS) && inside(mouseX, mouseY, bindsX, bindsY, bindsW, bindsH)) {
                activeDrag = 8; dragOffsetX = mouseX - bindsX; dragOffsetY = mouseY - bindsY;
            } else if (inside(mouseX, mouseY, watermarkX, watermarkY, animatedWidth - 2f, HEIGHT)) {
                activeDrag = 1; dragOffsetX = mouseX - watermarkX; dragOffsetY = mouseY - watermarkY;
            }
        }

        if (!down || !alt) {
            if (released && Strange.get != null && Strange.get.configManager != null) {
                Strange.get.configManager.flushAutoSave();
            }
            activeDrag = 0;
            return;
        }

        switch (activeDrag) {
            case 1 -> {
                watermarkX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - (animatedWidth - 2f) - 2f);
                watermarkY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - HEIGHT - 2f);
                markLayoutDirty();
            }
            case 2 -> {
                modulesX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - moduleW - 2f);
                modulesY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - moduleH - 2f);
                markLayoutDirty();
            }
            case 3 -> {
                targetX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - TargetHudRenderer.getW() - 2f);
                targetY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - TargetHudRenderer.getH() - 2f);
                markLayoutDirty();
            }
            case 4 -> {
                inventoryX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - InventoryHudRenderer.W - 2f);
                inventoryY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - InventoryHudRenderer.H - 2f);
                markLayoutDirty();
            }
            case 5 -> {
                potionsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - potionW - 2f);
                potionsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - potionH - 2f);
                markLayoutDirty();
            }
            case 6 -> {
                cooldownsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - CooldownHudRenderer.CARD_W - 2f);
                cooldownsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - cooldownH - 2f);
                markLayoutDirty();
            }
            case 7 -> {
                coordsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - coordsW - 2f);
                coordsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - CoordsHudRenderer.H - 2f);
                markLayoutDirty();
            }
            case 8 -> {
                bindsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - bindsW - 2f);
                bindsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - bindsH - 2f);
                markLayoutDirty();
            }
        }
    }

    private void renderEditor(DrawContext ctx) {
        drawBorder(ctx, watermarkX - 1f, watermarkY - 1f, animatedWidth, HEIGHT + 2f, ModLocalization.raw("Watermark"));

        if (elements.get(EL_MODULES) && moduleList.getHeight() > 0f) {
            drawBorder(ctx, modulesX - 1f, modulesY - 1f, moduleList.getWidth() + 2f, moduleList.getHeight() + 2f, ModLocalization.raw("Modules"));
        }
        if (elements.get(EL_TARGET_HUD)) {
            drawBorder(ctx, targetX - 1f, targetY - 1f, TargetHudRenderer.getW() + 2f, TargetHudRenderer.getH() + 2f, ModLocalization.raw("Target"));
        }
        if (elements.get(EL_INVENTORY)) {
            drawBorder(ctx, inventoryX - 1f, inventoryY - 1f, InventoryHudRenderer.W + 2f, InventoryHudRenderer.H + 2f, ModLocalization.raw("Inventory"));
        }
        if (elements.get(EL_POTIONS) && potion.getDisplayCount() > 0) {
            float pw = potion.getGroupWidth();
            float ph = potion.getGroupHeight();
            drawBorder(ctx, potionsX - 1f, potionsY - 1f, pw + 2f, ph + 2f, ModLocalization.raw("Potions"));
        }
        if (elements.get(EL_COOLDOWNS) && cooldown.getDisplayCount() > 0) {
            drawBorder(ctx, cooldownsX - 1f, cooldownsY - 1f, CooldownHudRenderer.CARD_W + 2f, cooldown.getGroupHeight() + 2f, ModLocalization.raw("Cooldowns"));
        }
        if (elements.get(EL_COORDS)) {
            drawBorder(ctx, coordsX - 1f, coordsY - 1f, coords.getWidth() + 2f, CoordsHudRenderer.H + 2f, ModLocalization.raw("Coords"));
        }
        if (elements.get(EL_BINDS) && binds.getDisplayCount() > 0) {
            drawBorder(ctx, bindsX - 1f, bindsY - 1f, binds.getWidth() + 2f, binds.getHeight() + 2f, ModLocalization.raw("Binds"));
        }

        String hint  = ModLocalization.tr("hud.move_hint");
        float hintW  = FontDraw.getWidth(FontDraw.FontType.MEDIUM, hint, 4) + 10f;
        float hx     = ctx.getScaledWindowWidth() / 2f - hintW / 2f;

        RenderUtil.drawClientRect(ctx, hx, 44f, hintW, 12f);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, hint,
                hx + 5f, 52f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));
    }

    private void drawBorder(DrawContext ctx, float x, float y, float w, float h, String label) {
        RenderUtil.Border.draw(ctx, x, y, w, h, 6f, 0.7f, editorColor());
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, label,
                x + 5f, Math.max(4f, y - 2f), 4, editorColor());
    }

    public boolean isEditing() {
        return elements.get(EL_EDIT_MODE) && mc.currentScreen != null;
    }

    public String trimToWidth(String text, float maxWidth, int size) {
        if (text == null) return "";
        if (FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size) <= maxWidth) return text;
        String dots   = "...";
        String result = text;
        while (!result.isEmpty()
                && FontDraw.getWidth(FontDraw.FontType.MEDIUM, result + dots, size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + dots;
    }

    private int editorColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 200);
    }

    private boolean isMouseDown(int button) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
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

    private boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private void markLayoutDirty() {
        if (Strange.get != null && Strange.get.configManager != null) {
            Strange.get.configManager.markDirty();
        }
    }

    private static float readFloat(JsonObject object, String key, float fallback) {
        return object.has(key) ? object.get(key).getAsFloat() : fallback;
    }

    private String getServerName() {
        if (mc.isInSingleplayer()) return "singleplayer";
        String address = ServerUtil.getServerAddress();
        if (address != null && !address.isBlank()) {
            return address;
        }
        if (mc.getCurrentServerEntry() != null) {
            String addr = mc.getCurrentServerEntry().address;
            if (addr != null && !addr.isEmpty()) return addr;
            String name = mc.getCurrentServerEntry().name;
            if (name != null && !name.isEmpty()) return name;
        }
        return "unknown";
    }

    static float lerp(float from, float to, float speed) {
        return from + (to - from) * MathHelper.clamp(speed, 0f, 1f);
    }

    private static float approach(float value, float target, float speed) {
        if (value < target) return Math.min(value + speed, target);
        return Math.max(value - speed, target);
    }

    private int resolvePotionMode() {
        return POTION_STYLE_LIST.equalsIgnoreCase(potionStyle.get()) ? 1 : 0;
    }
}
