package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
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
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final Identifier logoIcon = Identifier.of(Strange.rootRes, "/icons/gui/logo.png");

    private final Identifier playerIcon = Identifier.of(Strange.rootRes, "/icons/gui/player.png");
    private final Identifier otherIcon = Identifier.of(Strange.rootRes, "/icons/gui/other.png");
    private final Identifier worldIcon = Identifier.of(Strange.rootRes, "/icons/gui/world.png");
    private final Identifier utilitiesIcon = Identifier.of(Strange.rootRes, "/icons/gui/utilities.png");
    private final Identifier interfaceIcon = Identifier.of(Strange.rootRes, "/icons/gui/interface.png");
    private final Identifier coordsIcon = Identifier.of(Strange.rootRes, "/icons/gui/coord.png");
    private final Identifier inventoryIcon = Identifier.of(Strange.rootRes, "/icons/gui/invent.png");

    private float animatedWidth = 71f;
    private final Map<String, Float> segProgress = new LinkedHashMap<>();

    private static final float HEIGHT = 16f;
    private static final float WIDTH_SPEED = 0.18f;
    private static final float SEG_SPEED = 0.22f;

    private static final float TARGET_W = 108f;
    private static final float TARGET_H = 36f;
    private static final float TARGET_HEAD_SIZE = 20f;
    private static final float TARGET_BAR_H = 4f;
    private static final float TARGET_ARMOR_SCALE = 0.66f;
    private static final float TARGET_ARMOR_STEP = 11f;

    private static final float INV_W = 172f;
    private static final float INV_H = 82f;

    private static final float POTION_W = 24f;
    private static final float POTION_H = 48f;
    private static final float POTION_GAP = 4f;

    private static final float COOLDOWN_W = 92f;
    private static final float COOLDOWN_H = 20f;
    private static final float COOLDOWN_GAP = 4f;

    private static final float COORDS_H = 18f;
    private static final float COORDS_MIN_W = 54f;
    private static final float COORDS_ICON_SPACE = 18f;

    private static final float MODULE_ITEM_HEIGHT = 12.8f;
    private static final float MODULE_ITEM_STEP = 13.6f;
    private static final float MODULE_ICON_SIZE = 7.5f;
    private static final float MODULE_TEXT_SIZE = 5f;

    private boolean initialized;
    private boolean lastMouseDown;
    private boolean targetLastAttackDown;

    private int activeDrag;
    private float dragOffsetX;
    private float dragOffsetY;

    private float watermarkX = 10f;
    private float watermarkY = 10f;

    private float modulesX;
    private float modulesY;

    private float targetX;
    private float targetY;

    private float inventoryX;
    private float inventoryY;

    private float potionsX;
    private float potionsY;

    private float cooldownsX;
    private float cooldownsY;

    private float coordsX;
    private float coordsY;

    private LivingEntity target;
    private LivingEntity healthAnimatedEntity;
    private long targetKeepUntil;
    private float targetAlpha;
    private float displayedHealth = 20f;

    private static final String[] MODULE_PREVIEW = {
            "Боксы",
            "Джампики",
            "Китайская шляпа",
            "Кубики",
            "Таргет рендер"
    };

    private static class CooldownInfo {
        private final ItemStack stack;
        private final int remainingTicks;
        private final int totalTicks;
        private final float fillProgress;

        private CooldownInfo(ItemStack stack, int remainingTicks, int totalTicks, float fillProgress) {
            this.stack = stack;
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
            this.fillProgress = fillProgress;
        }
    }

    private static class CooldownState {
        private final int remainingTicks;
        private final int totalTicks;

        private CooldownState(int remainingTicks, int totalTicks) {
            this.remainingTicks = remainingTicks;
            this.totalTicks = totalTicks;
        }
    }

    public WaterMark() {
        addSettings(settings);
        addSettings(elements);
    }

    public static boolean shouldHideVanillaPotionHud() {
        try {
            for (Module module : Strange.get.manager.getModules()) {
                if (module instanceof WaterMark) {
                    WaterMark wm = (WaterMark) module;
                    return wm.enable && wm.elements.get("Потионы");
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @EventInit
    public void onScreen(EventScreen e) {
        if (mc.player == null || mc.world == null) return;

        DrawContext ctx = e.drawContext();
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();

        initPositions(sw, sh);
        updateTargetState();
        updateDragging(sw, sh);

        renderWatermark(e);

        if (elements.get("Модули")) {
            renderModuleList(ctx);
        }

        if (elements.get("Таргет худ")) {
            renderTargetHud(ctx);
        }

        if (elements.get("Инвентарь")) {
            renderInventoryHud(ctx);
        }

        if (elements.get("Потионы")) {
            renderPotionHud(ctx);
        }

        if (elements.get("Кулдауны")) {
            renderCooldownHud(ctx);
        }

        if (elements.get("Координаты")) {
            renderCoordsHud(ctx);
        }

        if (isEditing()) {
            renderEditor(ctx);
        }

        lastMouseDown = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
    }

    private void initPositions(int sw, int sh) {
        if (initialized) return;
        initialized = true;

        modulesX = 10f;
        modulesY = 48f;

        targetX = sw * 0.27f;
        targetY = sh * 0.53f;

        inventoryX = sw - INV_W - 14f;
        inventoryY = sh * 0.17f;

        potionsX = sw - 96f;
        potionsY = 8f;

        cooldownsX = 12f;
        cooldownsY = sh * 0.28f;

        coordsX = 12f;
        coordsY = sh * 0.24f;
    }

    /* =========================
       WATERMARK
       ========================= */

    private void renderWatermark(EventScreen e) {
        float x = watermarkX;
        float y = watermarkY;
        float dt = 1.0f;

        String nickText = mc.player.getName().getString();
        
        String fpsText = mc.getCurrentFps() + " FPS";

        int pingValue = -1;
        if (mc.getNetworkHandler() != null
                && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) {
            pingValue = mc.getNetworkHandler()
                    .getPlayerListEntry(mc.player.getUuid())
                    .getLatency();
        }
        String pingText = pingValue >= 0 ? pingValue + " ms" : "N/A";

        String timeText = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String serverText = getServerName();

        Map<String, String> segments = new LinkedHashMap<>();
        segments.put("Ник", nickText);
        segments.put("ФПС", fpsText);
        segments.put("Пинг", pingText);
        segments.put("Время", timeText);
        segments.put("Сервер", serverText);

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String key = seg.getKey();
            boolean enabled = settings.get(key);

            float p = segProgress.getOrDefault(key, 0f);
            float target = enabled ? 1f : 0f;

            p = approach(p, target, SEG_SPEED * dt);
            segProgress.put(key, p);
        }

        String title = "Strange Visual";
        float titleWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, title, 7);
        float baseWidth = 15f + titleWidth + 6f;

        float targetWidth = baseWidth;

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String text = seg.getValue();

            float p = segProgress.getOrDefault(seg.getKey(), 0f);
            if (p <= 0.001f) continue;

            float w = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
            targetWidth += (w + 6f) * p;
        }

        animatedWidth = lerp(animatedWidth, targetWidth, WIDTH_SPEED * dt);

        RenderUtil.drawClientRect(e.drawContext(), x, y, animatedWidth - 2f, HEIGHT);

        RenderUtil.Image.draw(
                e.drawContext(),
                logoIcon,
                x + 4.09f, y + 4f,
                8.17f, 8f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                e.drawContext(),
                title,
                x + 15f,
                y + 10.5f,
                7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 178)
        );

        float cursorX = x + baseWidth;

        for (Map.Entry<String, String> seg : segments.entrySet()) {
            String text = seg.getValue();

            float p = segProgress.getOrDefault(seg.getKey(), 0f);
            if (p <= 0.01f) continue;

            float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
            cursorX = drawSegmentAnimated(e, cursorX, y, text, p, textW, 6f);
        }
    }

    private float drawSegmentAnimated(EventScreen e, float cursorX, float y, String text, float p, float textW, float pad) {
        int baseAlphaText = 178;
        int baseAlphaSep = 150;

        int aText = (int) (baseAlphaText * p);
        int aSep = (int) (baseAlphaSep * p);

        float slide = (1f - p) * 6f;
        float drawX = cursorX - slide;

        RenderUtil.Round.draw(
                e.drawContext(),
                drawX - 3f,
                y + 4f,
                1f,
                8f,
                0.5f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), aSep)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                e.drawContext(),
                text,
                drawX,
                y + 10.5f,
                7,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), aText)
        );

        return cursorX + (textW + pad) * p;
    }

    /* =========================
       MODULE LIST
       ========================= */

    private void renderModuleList(DrawContext ctx) {
        List<Module> modules = getVisibleModules();
        boolean preview = modules.isEmpty() && isEditing();

        if (!preview && modules.isEmpty()) return;

        float x = modulesX;
        float y = modulesY;
        float currentY = y;

        if (preview) {
            for (int i = 0; i < MODULE_PREVIEW.length; i++) {
                String name = MODULE_PREVIEW[i];
                float width = getModuleCardWidth(name);

                RenderUtil.drawClientRect(ctx, x, currentY, width, MODULE_ITEM_HEIGHT);

                Identifier icon = getPreviewIcon(i);
                RenderUtil.Image.draw(
                        ctx,
                        icon,
                        x + 4.5f,
                        currentY + 2.7f,
                        MODULE_ICON_SIZE,
                        MODULE_ICON_SIZE,
                        RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210)
                );

                FontDraw.drawText(
                        FontDraw.FontType.MEDIUM,
                        ctx,
                        name,
                        x + 14.5f,
                        currentY + 8.65f,
                        (int) MODULE_TEXT_SIZE,
                        moduleText()
                );

                currentY += MODULE_ITEM_STEP;
            }
            return;
        }

        for (Module module : modules) {
            String name = module.name;
            float width = getModuleCardWidth(name);

            RenderUtil.drawClientRect(ctx, x, currentY, width, MODULE_ITEM_HEIGHT);

            Identifier icon = getModuleIcon(module);
            RenderUtil.Image.draw(
                    ctx,
                    icon,
                    x + 4.5f,
                    currentY + 2.7f,
                    MODULE_ICON_SIZE,
                    MODULE_ICON_SIZE,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210)
            );

            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    name,
                    x + 14.5f,
                    currentY + 8.65f,
                    (int) MODULE_TEXT_SIZE,
                    moduleText()
            );

            currentY += MODULE_ITEM_STEP;
        }
    }

    private List<Module> getVisibleModules() {
        List<Module> visible = new ArrayList<>();

        for (Module module : Strange.get.manager.getModules()) {
            if (module == null) continue;
            if (module == this) continue;
            if (!module.enable) continue;

            visible.add(module);
        }

        return visible;
    }

    private float getModuleCardWidth(String name) {
        return FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, (int) MODULE_TEXT_SIZE) + 24f;
    }

    private float getModuleListWidth() {
        List<Module> modules = getVisibleModules();
        float width = 60f;

        if (modules.isEmpty() && isEditing()) {
            for (String preview : MODULE_PREVIEW) {
                width = Math.max(width, getModuleCardWidth(preview));
            }
            return width;
        }

        for (Module module : modules) {
            width = Math.max(width, getModuleCardWidth(module.name));
        }

        return width;
    }

    private float getModuleListHeight() {
        int count = getVisibleModules().size();
        if (count == 0 && isEditing()) count = MODULE_PREVIEW.length;
        if (count == 0) return 0f;
        return MODULE_ITEM_HEIGHT + (count - 1) * MODULE_ITEM_STEP;
    }

    private Identifier getPreviewIcon(int index) {
        if (index == 0) return playerIcon;
        if (index == 1) return utilitiesIcon;
        if (index == 2) return worldIcon;
        if (index == 3) return otherIcon;
        return interfaceIcon;
    }

    private Identifier getModuleIcon(Module module) {
        try {
            IModule annotation = module.getClass().getAnnotation(IModule.class);
            if (annotation != null) {
                String cat = annotation.category().name().toLowerCase();

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
            }
        } catch (Throwable ignored) {
        }

        return otherIcon;
    }

    private int moduleText() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220);
    }

    /* =========================
       TARGET HUD
       ========================= */

    private void updateTargetState() {
        long now = System.currentTimeMillis();
        LivingEntity hovered = getHoveredTarget();
        boolean attackDown = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);

        if (hovered != null) {
            target = hovered;
            targetKeepUntil = now + 3000L;
        }

        if (attackDown && !targetLastAttackDown && hovered != null) {
            target = hovered;
            targetKeepUntil = now + 3000L;
        }

        targetLastAttackDown = attackDown;

        boolean valid = target != null && target.isAlive() && !target.isRemoved();
        boolean shouldShow = valid && (hovered != null || now < targetKeepUntil);

        targetAlpha = lerp(targetAlpha, shouldShow ? 1f : 0f, shouldShow ? 0.14f : 0.08f);

        if (!shouldShow && targetAlpha < 0.02f) {
            target = null;
            healthAnimatedEntity = null;
        }
    }

    private void renderTargetHud(DrawContext ctx) {
        boolean preview = target == null && isEditing();
        float alpha = preview ? 0.95f : targetAlpha;

        if (alpha < 0.02f) return;

        float x = targetX;
        float y = targetY;

        RenderUtil.drawClientRect(ctx, x, y, TARGET_W, TARGET_H);

        float headX = x + 5f;
        float headY = y + 5f;

        if (preview) {
            drawFaceTexture(
                    ctx,
                    Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"),
                    headX + 3f,
                    headY + 3f,
                    (int) (TARGET_HEAD_SIZE - 6f),
                    8f,
                    8f,
                    64,
                    32,
                    false
            );
        } else {
            drawTargetPortrait(ctx, target, headX, headY, TARGET_HEAD_SIZE, alpha);
        }

        float health;
        float maxHealth;

        if (preview) {
            health = 16.0f;
            maxHealth = 20.0f;
            displayedHealth = 16.0f;
        } else {
            float realHealth = target.getHealth() + target.getAbsorptionAmount();
            float realMaxHealth = Math.max(1f, target.getMaxHealth() + target.getAbsorptionAmount());

            if (healthAnimatedEntity != target) {
                healthAnimatedEntity = target;
                displayedHealth = realHealth;
            } else {
                displayedHealth = lerp(displayedHealth, realHealth, 0.10f);
            }

            health = displayedHealth;
            maxHealth = realMaxHealth;
        }

        String hpText = oneDecimal(health);
        float hpWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, hpText, 4);

        float textX = x + 29f;
        float textY = y + 11.2f;
        float hpX = x + TARGET_W - hpWidth - 6f;

        float maxNameWidth = Math.max(18f, hpX - textX - 4f);
        String name = preview
                ? "Странный тип"
                : trimToWidth(target.getName().getString(), maxNameWidth, 5);

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                name,
                textX,
                textY,
                5,
                targetTextTheme(alpha)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                hpText,
                hpX,
                textY,
                4,
                targetSubTextTheme(alpha)
        );

        float armorX = x + 29f;
        float armorY = y + 16.2f;

        for (int i = 0; i < 4; i++) {
            ItemStack stack;

            if (preview) {
                if (i == 0) stack = Items.NETHERITE_HELMET.getDefaultStack();
                else if (i == 1) stack = Items.NETHERITE_CHESTPLATE.getDefaultStack();
                else if (i == 2) stack = Items.NETHERITE_LEGGINGS.getDefaultStack();
                else stack = Items.NETHERITE_BOOTS.getDefaultStack();
            } else {
                if (i == 0) stack = target.getEquippedStack(EquipmentSlot.HEAD);
                else if (i == 1) stack = target.getEquippedStack(EquipmentSlot.CHEST);
                else if (i == 2) stack = target.getEquippedStack(EquipmentSlot.LEGS);
                else stack = target.getEquippedStack(EquipmentSlot.FEET);
            }

            renderArmorMini(ctx, stack, armorX + i * TARGET_ARMOR_STEP, armorY);
        }

        float progress = MathHelper.clamp(health / maxHealth, 0f, 1f);

        float barX = x + 5f;
        float barY = y + TARGET_H - 6f;
        float barW = TARGET_W - 10f;
        float radius = TARGET_BAR_H / 2f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                TARGET_BAR_H,
                radius,
                targetBarBackgroundTheme(alpha)
        );

        float fillW = barW * progress;
        if (fillW > 0.01f) {
            fillW = Math.max(fillW, TARGET_BAR_H);

            RenderUtil.Round.draw(
                    ctx,
                    barX,
                    barY,
                    Math.min(fillW, barW),
                    TARGET_BAR_H,
                    radius,
                    targetBarFillTheme(alpha)
            );
        }
    }

    private LivingEntity getHoveredTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult) {
            EntityHitResult hit = (EntityHitResult) mc.crosshairTarget;
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity && entity != mc.player) {
                return (LivingEntity) entity;
            }
        }
        return null;
    }

    private void drawTargetPortrait(DrawContext ctx, LivingEntity entity, float x, float y, float boxSize, float alpha) {
        if (entity == null) return;

        if (entity instanceof AbstractClientPlayerEntity player) {
            int size = (int) (boxSize - 4f);
            float drawX = x + (boxSize - size) / 2f;
            float drawY = y + (boxSize - size) / 2f;
            drawPlayerHead(ctx, player, drawX, drawY, size, alpha);
            return;
        }

        int size = (int) (boxSize - 6f);
        float drawX = x + (boxSize - size) / 2f;
        float drawY = y + (boxSize - size) / 2f + 1f;

        if (drawKnownMobFace(ctx, entity, drawX, drawY, size)) {
            return;
        }

        if (drawRendererTextureFace(ctx, entity, drawX, drawY, size)) {
            return;
        }

        ItemStack fallback = getEntityIconStack(entity);
        if (!fallback.isEmpty()) {
            float scale = Math.min((boxSize - 6f) / 16f, 0.9f);
            float itemSize = 16f * scale;
            float itemX = x + (boxSize - itemSize) / 2f;
            float itemY = y + (boxSize - itemSize) / 2f;
            drawScaledItem(ctx, fallback, itemX, itemY, scale);
        }
    }

    private boolean drawKnownMobFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) return false;

        String path = id.getPath();

        try {
            switch (path) {
                case "zombie":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;
                case "husk":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/husk.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;
                case "drowned":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/drowned.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;
                case "giant":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;

                case "piglin":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;
                case "piglin_brute":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin_brute.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;
                case "zombified_piglin":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/zombified_piglin.png"), x, y, size, 8f, 8f, 64, 64, true);
                    return true;

                case "skeleton":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"), x, y, size, 8f, 8f, 64, 32, false);
                    return true;
                case "wither_skeleton":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/wither_skeleton.png"), x, y, size, 8f, 8f, 64, 32, false);
                    return true;
                case "stray":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/stray.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;

                case "creeper":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/creeper/creeper.png"), x, y, size, 8f, 8f, 64, 32, false);
                    return true;
                case "enderman":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/enderman/enderman.png"), x, y, size, 8f, 8f, 64, 32, false);
                    return true;

                case "zombie_villager":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "villager":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/villager/villager.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "wandering_trader":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/wandering_trader.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;

                case "pillager":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/pillager.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "vindicator":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/vindicator.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "evoker":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/evoker.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "illusioner":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/illusioner.png"), x, y, size, 8f, 8f, 64, 64, false);
                    return true;
                case "witch":
                    drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/witch.png"), x, y, size, 8f, 8f, 64, 128, false);
                    return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean drawRendererTextureFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        try {
            Identifier texture = getEntityTextureFromRenderer(entity);
            if (texture == null) return false;

            int[] guessed = guessTextureSize(entity);
            drawFaceTexture(
                    ctx,
                    texture,
                    x,
                    y,
                    size,
                    8f,
                    8f,
                    guessed[0],
                    guessed[1],
                    false
            );
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Identifier getEntityTextureFromRenderer(LivingEntity entity) {
        try {
            Object dispatcher = mc.getEntityRenderDispatcher();
            Object renderer = null;

            for (Method method : dispatcher.getClass().getMethods()) {
                if (!method.getName().equals("getRenderer")) continue;
                if (method.getParameterCount() != 1) continue;

                Class<?> type = method.getParameterTypes()[0];
                if (!type.isAssignableFrom(entity.getClass())) continue;

                method.setAccessible(true);
                renderer = method.invoke(dispatcher, entity);
                break;
            }

            if (renderer == null) return null;

            for (Method method : renderer.getClass().getMethods()) {
                if (method.getParameterCount() != 1) continue;
                if (!Identifier.class.isAssignableFrom(method.getReturnType())) continue;

                Class<?> type = method.getParameterTypes()[0];
                if (!type.isAssignableFrom(entity.getClass())) continue;

                method.setAccessible(true);
                Object result = method.invoke(renderer, entity);
                if (result instanceof Identifier) {
                    return (Identifier) result;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private int[] guessTextureSize(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String path = id == null ? "" : id.getPath();

        switch (path) {
            case "zombie":
            case "husk":
            case "drowned":
            case "giant":
            case "piglin":
            case "piglin_brute":
            case "zombified_piglin":
            case "villager":
            case "zombie_villager":
            case "pillager":
            case "vindicator":
            case "evoker":
            case "illusioner":
            case "wandering_trader":
            case "stray":
                return new int[]{64, 64};

            case "witch":
                return new int[]{64, 128};

            default:
                return new int[]{64, 32};
        }
    }

    private void drawFaceTexture(DrawContext ctx, Identifier texture, float x, float y, int size, float u, float v, int texW, int texH, boolean overlay) {
        try {
            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    (int) x,
                    (int) y,
                    u,
                    v,
                    size,
                    size,
                    8,
                    8,
                    texW,
                    texH
            );

            if (overlay) {
                ctx.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        texture,
                        (int) x,
                        (int) y,
                        u + 32f,
                        v,
                        size,
                        size,
                        8,
                        8,
                        texW,
                        texH
                );
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawPlayerHead(DrawContext ctx, AbstractClientPlayerEntity player, float x, float y, int size, float alpha) {
        try {
            Identifier skin = player.getSkinTextures().texture();

            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    skin,
                    (int) x,
                    (int) y,
                    8f,
                    8f,
                    size,
                    size,
                    8,
                    8,
                    64,
                    64
            );

            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    skin,
                    (int) x,
                    (int) y,
                    40f,
                    8f,
                    size,
                    size,
                    8,
                    8,
                    64,
                    64
            );
        } catch (Throwable ignored) {
            ctx.drawItem(Items.PLAYER_HEAD.getDefaultStack(), (int) x, (int) y);
        }
    }

    private void drawScaledItem(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty()) return;

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(scale, scale);
        ctx.drawItem(stack, 0, 0);
        ctx.getMatrices().popMatrix();
    }

    private void renderArmorMini(DrawContext ctx, ItemStack stack, float x, float y) {
        if (stack == null || stack.isEmpty()) return;
        drawScaledItem(ctx, stack, x, y, TARGET_ARMOR_SCALE);
    }

    private ItemStack getEntityIconStack(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String path = id == null ? "" : id.getPath();

        if ("armor_stand".equals(path)) return Items.ARMOR_STAND.getDefaultStack();
        if ("iron_golem".equals(path)) return Items.IRON_BLOCK.getDefaultStack();
        if ("snow_golem".equals(path)) return Items.SNOW_BLOCK.getDefaultStack();
        if ("wither".equals(path)) return Items.WITHER_SKELETON_SKULL.getDefaultStack();
        if ("ender_dragon".equals(path)) return Items.DRAGON_HEAD.getDefaultStack();
        if ("giant".equals(path)) return Items.ZOMBIE_HEAD.getDefaultStack();
        if ("wither_skeleton".equals(path)) return Items.WITHER_SKELETON_SKULL.getDefaultStack();
        if ("skeleton".equals(path)) return Items.SKELETON_SKULL.getDefaultStack();
        if ("zombie".equals(path)) return Items.ZOMBIE_HEAD.getDefaultStack();
        if ("creeper".equals(path)) return Items.CREEPER_HEAD.getDefaultStack();
        if ("piglin".equals(path) || "piglin_brute".equals(path) || "zombified_piglin".equals(path)) {
            return Items.PIGLIN_HEAD.getDefaultStack();
        }

        ItemStack egg = getSpawnEggIcon(entity);
        if (!egg.isEmpty()) {
            return egg;
        }

        return Items.NAME_TAG.getDefaultStack();
    }

    private ItemStack getSpawnEggIcon(LivingEntity entity) {
        try {
            for (Method method : SpawnEggItem.class.getDeclaredMethods()) {
                if (!method.getName().equals("forEntity")) continue;
                if (method.getParameterCount() != 1) continue;

                method.setAccessible(true);
                Object result = method.invoke(null, entity.getType());

                if (result instanceof SpawnEggItem) {
                    return new ItemStack((SpawnEggItem) result);
                }

                if (result instanceof java.util.Optional) {
                    java.util.Optional<?> optional = (java.util.Optional<?>) result;
                    if (optional.isPresent() && optional.get() instanceof SpawnEggItem) {
                        return new ItemStack((SpawnEggItem) optional.get());
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return ItemStack.EMPTY;
    }

    /* =========================
       INVENTORY HUD
       ========================= */

    private void renderInventoryHud(DrawContext ctx) {
        float x = inventoryX;
        float y = inventoryY;

        drawInventoryCard(ctx, x, y, INV_W, INV_H);

        RenderUtil.Image.draw(
                ctx,
                inventoryIcon,
                x + 6f,
                y + 4f,
                12f,
                12f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                "Инвентарь твоей мамы",
                x + 24f,
                y + 11.1f,
                5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215)
        );

        RenderUtil.Round.draw(
                ctx,
                x + 6f,
                y + 18f,
                INV_W - 12f,
                1f,
                0.5f,
                inventoryLineColor(1f)
        );

        int start = 9;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = start + row * 9 + col;
                ItemStack stack = mc.player.getInventory().getStack(index);

                float sx = x + 6f + col * 18f;
                float sy = y + 24f + row * 18f;

                drawInventorySlot(ctx, sx, sy, stack);
            }
        }

        if (isInventoryAreaEmpty()) {
            float gridX = x + 6f;
            float gridY = y + 24f;
            float gridW = 16f + 8f * 18f;
            float gridH = 16f + 2f * 18f;

            drawInventoryOverlayText(
                    ctx,
                    "ЕБАТЬ ТЫ БОМЖ",
                    gridX + gridW / 2f,
                    gridY + gridH / 2f + 5f,
                    11
            );
        }
    }

    private boolean isInventoryAreaEmpty() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private void drawInventoryCard(DrawContext ctx, float x, float y, float w, float h) {
        RenderUtil.drawClientRect(ctx, x, y, w, h);
    }

    private void drawInventorySlot(DrawContext ctx, float x, float y, ItemStack stack) {
        RenderUtil.Round.draw(ctx, x, y, 16f, 16f, 5f, inventorySlotBackground(1f));

        if (stack != null && !stack.isEmpty()) {
            ctx.drawItem(stack, (int) x, (int) y);
        }
    }

    private void drawInventoryOverlayText(DrawContext ctx, String text, float centerX, float y, int size) {
        float width = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size);
        float drawX = centerX - width / 2f;

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                text,
                drawX,
                y,
                size,
                inventoryOverlayColor(1f)
        );
    }

    /* =========================
       POTION HUD
       ========================= */

    private void renderPotionHud(DrawContext ctx) {
        List<StatusEffectInstance> effects = getSortedEffects();

        boolean preview = effects.isEmpty() && isEditing();
        int count = preview ? 3 : effects.size();

        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            float x = potionsX + i * (POTION_W + POTION_GAP);
            float y = potionsY;

            Identifier iconTexture;
            String time;
            float progress;

            if (preview) {
                if (i == 0) {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/speed.png");
                    time = "0:01";
                    progress = 0.10f;
                } else if (i == 1) {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/strength.png");
                    time = "0:16";
                    progress = 0.45f;
                } else {
                    iconTexture = Identifier.of("minecraft", "textures/mob_effect/jump_boost.png");
                    time = "1:12";
                    progress = 0.85f;
                }
            } else {
                StatusEffectInstance effect = effects.get(i);
                iconTexture = getPotionTexture(effect);
                time = formatPotionTime(effect);
                progress = getPotionProgress(effect);
            }

            drawPotionCard(ctx, x, y, iconTexture, time, progress);
        }
    }

    private List<StatusEffectInstance> getSortedEffects() {
        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        effects.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
        return effects;
    }

    private void drawPotionCard(DrawContext ctx, float x, float y, Identifier iconTexture, String time, float progress) {
        RenderUtil.drawClientRect(ctx, x, y, POTION_W, POTION_H);

        int iconSize = 12;
        float iconX = x + POTION_W / 2f - iconSize / 2f;
        float iconY = y + 4f;

        drawPotionEffectTexture(ctx, iconTexture, iconX, iconY, iconSize);

        float barW = 3f;
        float barH = 20f;
        float barX = x + POTION_W / 2f - barW / 2f;
        float barY = y + 18f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                barH,
                1f,
                potionBarBackground(1f)
        );

        float fillH = barH * MathHelper.clamp(progress, 0f, 1f);

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY + (barH - fillH),
                barW,
                fillH,
                1f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 225)
        );

        float tw = FontDraw.getWidth(FontDraw.FontType.MEDIUM, time, 4);

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                time,
                x + POTION_W / 2f - tw / 2f,
                y + 42.5f,
                4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 215)
        );
    }

    private void drawPotionEffectTexture(DrawContext ctx, Identifier texture, float x, float y, int size) {
        try {
            ctx.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    texture,
                    (int) x,
                    (int) y,
                    0f,
                    0f,
                    size,
                    size,
                    18,
                    18,
                    18,
                    18
            );
        } catch (Throwable ignored) {
            ctx.drawItem(Items.POTION.getDefaultStack(), (int) x, (int) y);
        }
    }

    private Identifier getPotionTexture(StatusEffectInstance effect) {
        Identifier id = Registries.STATUS_EFFECT.getId(effect.getEffectType().value());
        String path = id == null ? "speed" : id.getPath();
        return Identifier.of("minecraft", "textures/mob_effect/" + path + ".png");
    }

    private String formatPotionTime(StatusEffectInstance effect) {
        int duration = effect.getDuration() / 20;
        int minutes = duration / 60;
        int seconds = duration % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds;
    }

    private float getPotionProgress(StatusEffectInstance effect) {
        float seconds = effect.getDuration() / 20f;
        return MathHelper.clamp(seconds / 90f, 0f, 1f);
    }

    private float getPotionGroupWidth() {
        int count = getPotionDisplayCount();
        if (count <= 0) return 0f;
        return count * POTION_W + (count - 1) * POTION_GAP;
    }

    private int getPotionDisplayCount() {
        int count = mc.player != null ? mc.player.getStatusEffects().size() : 0;
        if (count <= 0 && isEditing()) return 3;
        return count;
    }

    /* =========================
       COOLDOWNS HUD
       ========================= */

    private void renderCooldownHud(DrawContext ctx) {
        List<CooldownInfo> infos = getCooldownInfos();

        boolean preview = infos.isEmpty() && isEditing();
        int count = preview ? 2 : infos.size();

        if (count <= 0) return;

        for (int i = 0; i < count; i++) {
            float x = cooldownsX;
            float y = cooldownsY + i * (COOLDOWN_H + COOLDOWN_GAP);

            ItemStack icon;
            String timeText;
            float fill;

            if (preview) {
                if (i == 0) {
                    icon = Items.ENCHANTED_GOLDEN_APPLE.getDefaultStack();
                    timeText = "128 сек";
                    fill = 0.12f;
                } else {
                    icon = Items.ENDER_PEARL.getDefaultStack();
                    timeText = "16 сек";
                    fill = 0.76f;
                }
            } else {
                CooldownInfo info = infos.get(i);
                icon = info.stack;
                timeText = formatCooldownTime(info.remainingTicks);
                fill = info.fillProgress;
            }

            drawCooldownCard(ctx, x, y, icon, timeText, fill);
        }
    }

    private void drawCooldownCard(DrawContext ctx, float x, float y, ItemStack icon, String timeText, float fillProgress) {
        RenderUtil.drawClientRect(ctx, x, y, COOLDOWN_W, COOLDOWN_H);

        float iconX = x + 5f;
        float iconY = y + 3f;

        if (icon != null && !icon.isEmpty()) {
            drawScaledItem(ctx, icon, iconX, iconY, 0.62f);
        }

        RenderUtil.Round.draw(
                ctx,
                x + 16f,
                y + 4f,
                1.2f,
                12f,
                0.6f,
                cooldownSeparatorColor(1f)
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                timeText,
                x + 22f,
                y + 8.3f,
                4,
                cooldownTextColor(1f)
        );

        float barX = x + 22f;
        float barY = y + 14.2f;
        float barW = COOLDOWN_W - 28f;
        float barH = 3f;
        float radius = barH / 2f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                barH,
                radius,
                cooldownBarBackground(1f)
        );

        float fillW = barW * MathHelper.clamp(fillProgress, 0f, 1f);
        if (fillW > 0.01f) {
            fillW = Math.max(fillW, barH);

            RenderUtil.Round.draw(
                    ctx,
                    barX,
                    barY,
                    Math.min(fillW, barW),
                    barH,
                    radius,
                    cooldownBarFill(1f)
            );
        }
    }

    private List<CooldownInfo> getCooldownInfos() {
        List<CooldownInfo> result = new ArrayList<>();

        if (mc.player == null) return result;

        Object manager = mc.player.getItemCooldownManager();
        Map<Object, CooldownState> states = readCooldownStates(manager);

        if (states.isEmpty()) return result;

        Map<Object, ItemStack> matchedStacks = new LinkedHashMap<>();

        collectCooldownStack(matchedStacks, manager, states, mc.player.getMainHandStack());
        collectCooldownStack(matchedStacks, manager, states, mc.player.getOffHandStack());

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            collectCooldownStack(matchedStacks, manager, states, mc.player.getInventory().getStack(i));
        }

        for (Map.Entry<Object, ItemStack> entry : matchedStacks.entrySet()) {
            CooldownState state = states.get(entry.getKey());
            if (state == null) continue;
            if (state.remainingTicks <= 0 || state.totalTicks <= 0) continue;

            float remainingNorm = MathHelper.clamp(state.remainingTicks / (float) state.totalTicks, 0f, 1f);
            float fill = 1f - remainingNorm;

            result.add(new CooldownInfo(
                    entry.getValue(),
                    state.remainingTicks,
                    state.totalTicks,
                    fill
            ));
        }

        result.sort((a, b) -> Integer.compare(b.remainingTicks, a.remainingTicks));
        return result;
    }

    private void collectCooldownStack(Map<Object, ItemStack> out, Object manager, Map<Object, CooldownState> states, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        Object key = resolveCooldownKeyForStack(manager, stack, states);
        if (key == null) return;
        if (out.containsKey(key)) return;

        out.put(key, stack.copy());
    }

    private Object resolveCooldownKeyForStack(Object manager, ItemStack stack, Map<Object, CooldownState> states) {
        Item item = stack.getItem();

        for (Object key : states.keySet()) {
            if (key == item) {
                return key;
            }
        }

        Identifier itemId = Registries.ITEM.getId(item);
        for (Object key : states.keySet()) {
            if (key instanceof Identifier id && id.equals(itemId)) {
                return key;
            }

            if (String.valueOf(key).equals(String.valueOf(itemId))) {
                return key;
            }
        }

        Object group = getCooldownGroupForStack(manager, stack);
        if (group != null) {
            for (Object key : states.keySet()) {
                if (key.equals(group) || String.valueOf(key).equals(String.valueOf(group))) {
                    return key;
                }
            }
        }

        if (isCoolingDown(manager, stack) && states.size() == 1) {
            return states.keySet().iterator().next();
        }

        return null;
    }

    private Object getCooldownGroupForStack(Object manager, ItemStack stack) {
        try {
            for (Method method : manager.getClass().getMethods()) {
                String name = method.getName().toLowerCase();
                if (!name.contains("group")) continue;
                if (method.getParameterCount() != 1) continue;

                method.setAccessible(true);
                Class<?> param = method.getParameterTypes()[0];

                if (param.isAssignableFrom(stack.getClass())) {
                    return method.invoke(manager, stack);
                }

                if (param.isAssignableFrom(stack.getItem().getClass())) {
                    return method.invoke(manager, stack.getItem());
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean isCoolingDown(Object manager, ItemStack stack) {
        try {
            for (Method method : manager.getClass().getMethods()) {
                if (!method.getName().equals("isCoolingDown")) continue;
                if (method.getParameterCount() != 1) continue;
                if (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) continue;

                method.setAccessible(true);
                Class<?> param = method.getParameterTypes()[0];

                if (param.isAssignableFrom(stack.getClass())) {
                    Object result = method.invoke(manager, stack);
                    if (result instanceof Boolean) return (Boolean) result;
                }

                if (param.isAssignableFrom(stack.getItem().getClass())) {
                    Object result = method.invoke(manager, stack.getItem());
                    if (result instanceof Boolean) return (Boolean) result;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private Map<Object, CooldownState> readCooldownStates(Object manager) {
        Map<Object, CooldownState> result = new LinkedHashMap<>();
        if (manager == null) return result;

        try {
            Field mapField = null;
            Field tickField = null;

            for (Field field : manager.getClass().getDeclaredFields()) {
                if (mapField == null && Map.class.isAssignableFrom(field.getType())) {
                    mapField = field;
                } else if (tickField == null && field.getType() == int.class) {
                    tickField = field;
                }
            }

            if (mapField == null) return result;

            mapField.setAccessible(true);
            Object rawMap = mapField.get(manager);
            if (!(rawMap instanceof Map<?, ?>)) return result;

            int currentTick = 0;
            if (tickField != null) {
                tickField.setAccessible(true);
                currentTick = tickField.getInt(manager);
            }

            for (Map.Entry<?, ?> entry : ((Map<?, ?>) rawMap).entrySet()) {
                Object stateObj = entry.getValue();
                if (stateObj == null) continue;

                int[] ticks = readCooldownEntryTicks(stateObj);
                int startTick = ticks[0];
                int endTick = ticks[1];

                if (endTick <= startTick) continue;

                int remainingTicks = Math.max(0, endTick - currentTick);
                int totalTicks = Math.max(1, endTick - startTick);

                if (remainingTicks <= 0) continue;

                result.put(entry.getKey(), new CooldownState(remainingTicks, totalTicks));
            }
        } catch (Throwable ignored) {
        }

        return result;
    }

    private int[] readCooldownEntryTicks(Object stateObj) {
        try {
            List<Field> intFields = new ArrayList<>();

            for (Field field : stateObj.getClass().getDeclaredFields()) {
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    intFields.add(field);
                }
            }

            if (intFields.size() >= 2) {
                int a = intFields.get(0).getInt(stateObj);
                int b = intFields.get(1).getInt(stateObj);

                if (a <= b) {
                    return new int[]{a, b};
                } else {
                    return new int[]{b, a};
                }
            }
        } catch (Throwable ignored) {
        }

        return new int[]{0, 0};
    }

    private String formatCooldownTime(int remainingTicks) {
        int seconds = Math.max(1, (int) Math.ceil(remainingTicks / 20.0));
        return seconds + " сек";
    }

    private int getCooldownDisplayCount() {
        int count = getCooldownInfos().size();
        if (count <= 0 && isEditing()) return 2;
        return count;
    }

    private float getCooldownGroupHeight() {
        int count = getCooldownDisplayCount();
        if (count <= 0) return 0f;
        return count * COOLDOWN_H + (count - 1) * COOLDOWN_GAP;
    }

    /* =========================
       COORDS HUD
       ========================= */

    private void renderCoordsHud(DrawContext ctx) {
        String text = getCoordsText();
        float w = getCoordsHudWidth(text);

        float x = coordsX;
        float y = coordsY;

        RenderUtil.drawClientRect(ctx, x, y, w, COORDS_H);

        float sepX = x + 13f;

        float iconSize = 8f;
        float iconX = x + (13f - iconSize) / 2f + 1.0f;
        float iconY = y + (COORDS_H - iconSize) / 2f;

        RenderUtil.Image.draw(
                ctx,
                coordsIcon,
                iconX,
                iconY,
                iconSize,
                iconSize,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220)
        );

        RenderUtil.Round.draw(
                ctx,
                sepX,
                y + 3.5f,
                1.0f,
                11f,
                0.5f,
                coordsSeparatorColor(1f)
        );

        float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);

        float textAreaX = sepX + 4f;
        float textAreaW = w - (textAreaX - x) - 4f;

        float textX = textAreaX + (textAreaW - textW) / 2f;
        float textY = y + 11.2f;

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                text,
                textX,
                textY,
                7,
                coordsTextColor(1f)
        );
    }

    private String getCoordsText() {
        if (mc.player == null) return "0, 0, 0";
        return mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ();
    }

    private float getCoordsHudWidth() {
        return getCoordsHudWidth(getCoordsText());
    }

    private float getCoordsHudWidth(String text) {
        float textW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, 7);
        return textW + 22f;
    }

    /* =========================
       EDITOR / DRAG
       ========================= */

    private void updateDragging(int sw, int sh) {
        if (!isEditing()) {
            activeDrag = 0;
            return;
        }

        float mouseX = getMouseX();
        float mouseY = getMouseY();
        boolean down = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);
        boolean clicked = down && !lastMouseDown;
        boolean alt = isAltDown();

        float potionGroupWidth = Math.max(getPotionGroupWidth(), POTION_W);
        float moduleWidth = Math.max(getModuleListWidth(), 40f);
        float moduleHeight = Math.max(getModuleListHeight(), 12f);
        float cooldownWidth = COOLDOWN_W;
        float cooldownHeight = Math.max(getCooldownGroupHeight(), COOLDOWN_H);
        float coordsWidth = getCoordsHudWidth();
        float coordsHeight = COORDS_H;

        if (clicked && alt) {
            if (elements.get("Координаты") && inside(mouseX, mouseY, coordsX, coordsY, coordsWidth, coordsHeight)) {
                activeDrag = 7;
                dragOffsetX = mouseX - coordsX;
                dragOffsetY = mouseY - coordsY;
            } else if (elements.get("Кулдауны") && inside(mouseX, mouseY, cooldownsX, cooldownsY, cooldownWidth, cooldownHeight)) {
                activeDrag = 6;
                dragOffsetX = mouseX - cooldownsX;
                dragOffsetY = mouseY - cooldownsY;
            } else if (elements.get("Потионы") && inside(mouseX, mouseY, potionsX, potionsY, potionGroupWidth, POTION_H)) {
                activeDrag = 5;
                dragOffsetX = mouseX - potionsX;
                dragOffsetY = mouseY - potionsY;
            } else if (elements.get("Инвентарь") && inside(mouseX, mouseY, inventoryX, inventoryY, INV_W, INV_H)) {
                activeDrag = 4;
                dragOffsetX = mouseX - inventoryX;
                dragOffsetY = mouseY - inventoryY;
            } else if (elements.get("Таргет худ") && inside(mouseX, mouseY, targetX, targetY, TARGET_W, TARGET_H)) {
                activeDrag = 3;
                dragOffsetX = mouseX - targetX;
                dragOffsetY = mouseY - targetY;
            } else if (elements.get("Модули") && inside(mouseX, mouseY, modulesX, modulesY, moduleWidth, moduleHeight)) {
                activeDrag = 2;
                dragOffsetX = mouseX - modulesX;
                dragOffsetY = mouseY - modulesY;
            } else if (inside(mouseX, mouseY, watermarkX, watermarkY, animatedWidth - 2f, HEIGHT)) {
                activeDrag = 1;
                dragOffsetX = mouseX - watermarkX;
                dragOffsetY = mouseY - watermarkY;
            }
        }

        if (!down || !alt) {
            activeDrag = 0;
            return;
        }

        if (activeDrag == 1) {
            watermarkX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - (animatedWidth - 2f) - 2f);
            watermarkY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - HEIGHT - 2f);
        } else if (activeDrag == 2) {
            modulesX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - moduleWidth - 2f);
            modulesY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - moduleHeight - 2f);
        } else if (activeDrag == 3) {
            targetX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - TARGET_W - 2f);
            targetY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - TARGET_H - 2f);
        } else if (activeDrag == 4) {
            inventoryX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - INV_W - 2f);
            inventoryY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - INV_H - 2f);
        } else if (activeDrag == 5) {
            potionsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - potionGroupWidth - 2f);
            potionsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - POTION_H - 2f);
        } else if (activeDrag == 6) {
            cooldownsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - cooldownWidth - 2f);
            cooldownsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - cooldownHeight - 2f);
        } else if (activeDrag == 7) {
            coordsX = MathHelper.clamp(mouseX - dragOffsetX, 2f, sw - coordsWidth - 2f);
            coordsY = MathHelper.clamp(mouseY - dragOffsetY, 2f, sh - coordsHeight - 2f);
        }
    }

    private void renderEditor(DrawContext ctx) {
        RenderUtil.Border.draw(ctx, watermarkX - 1f, watermarkY - 1f, animatedWidth, HEIGHT + 2f, 6f, 0.7f, editorColor());

        if (elements.get("Модули") && getModuleListHeight() > 0f) {
            RenderUtil.Border.draw(ctx, modulesX - 1f, modulesY - 1f, getModuleListWidth() + 2f, getModuleListHeight() + 2f, 6f, 0.7f, editorColor());
        }

        if (elements.get("Таргет худ")) {
            RenderUtil.Border.draw(ctx, targetX - 1f, targetY - 1f, TARGET_W + 2f, TARGET_H + 2f, 6f, 0.7f, editorColor());
        }

        if (elements.get("Инвентарь")) {
            RenderUtil.Border.draw(ctx, inventoryX - 1f, inventoryY - 1f, INV_W + 2f, INV_H + 2f, 9f, 0.7f, editorColor());
        }

        if (elements.get("Потионы") && getPotionDisplayCount() > 0) {
            RenderUtil.Border.draw(ctx, potionsX - 1f, potionsY - 1f, getPotionGroupWidth() + 2f, POTION_H + 2f, 4f, 0.7f, editorColor());
        }

        if (elements.get("Кулдауны") && getCooldownDisplayCount() > 0) {
            RenderUtil.Border.draw(ctx, cooldownsX - 1f, cooldownsY - 1f, COOLDOWN_W + 2f, getCooldownGroupHeight() + 2f, 9f, 0.7f, editorColor());
        }

        if (elements.get("Координаты")) {
            RenderUtil.Border.draw(ctx, coordsX - 1f, coordsY - 1f, getCoordsHudWidth() + 2f, COORDS_H + 2f, 6f, 0.7f, editorColor());
        }

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                "Watermark",
                watermarkX + 4f,
                Math.max(4f, watermarkY - 2f),
                4,
                editorColor()
        );

        if (elements.get("Модули") && getModuleListHeight() > 0f) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Modules",
                    modulesX + 4f,
                    Math.max(4f, modulesY - 2f),
                    4,
                    editorColor()
            );
        }

        if (elements.get("Таргет худ")) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Target",
                    targetX + 4f,
                    Math.max(4f, targetY - 2f),
                    4,
                    editorColor()
            );
        }

        if (elements.get("Инвентарь")) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Inventory",
                    inventoryX + 4f,
                    Math.max(4f, inventoryY - 2f),
                    4,
                    editorColor()
            );
        }

        if (elements.get("Потионы") && getPotionDisplayCount() > 0) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Potions",
                    potionsX + 4f,
                    Math.max(4f, potionsY - 2f),
                    4,
                    editorColor()
            );
        }

        if (elements.get("Кулдауны") && getCooldownDisplayCount() > 0) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Cooldowns",
                    cooldownsX + 4f,
                    Math.max(4f, cooldownsY - 2f),
                    4,
                    editorColor()
            );
        }

        if (elements.get("Координаты")) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    ctx,
                    "Coords",
                    coordsX + 4f,
                    Math.max(4f, coordsY - 2f),
                    4,
                    editorColor()
            );
        }

        String hint = "ALT + ЛКМ - двигать";
        float hintW = FontDraw.getWidth(FontDraw.FontType.MEDIUM, hint, 4) + 10f;
        float hx = ctx.getScaledWindowWidth() / 2f - hintW / 2f;
        float hy = 44f;

        drawThemeCard(ctx, hx, hy, hintW, 12f);

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                hint,
                hx + 5f,
                hy + 8f,
                4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220)
        );
    }

    /* =========================
       COLORS
       ========================= */

    private int editorColor() {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 200);
    }

    private int targetTextTheme(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (230 * alpha));
    }

    private int targetSubTextTheme(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (210 * alpha));
    }

    private int targetBarBackgroundTheme(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (55 * alpha));
    }

    private int targetBarFillTheme(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (235 * alpha));
    }

    private int inventoryLineColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (18 * alpha));
    }

    private int inventorySlotBackground(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (10 * alpha));
    }

    private int inventoryOverlayColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (70 * alpha));
    }

    private int potionBarBackground(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (55 * alpha));
    }

    private int cooldownTextColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (230 * alpha));
    }

    private int cooldownSeparatorColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (45 * alpha));
    }

    private int cooldownBarBackground(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (60 * alpha));
    }

    private int cooldownBarFill(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (235 * alpha));
    }

    private int coordsTextColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (230 * alpha));
    }

    private int coordsSeparatorColor(float alpha) {
        return RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (45 * alpha));
    }

    /* =========================
       HELPERS
       ========================= */

    private void drawThemeCard(DrawContext ctx, float x, float y, float w, float h) {
        RenderUtil.drawClientRect(ctx, x, y, w, h);
    }

    /* =========================
       UTILS
       ========================= */

    private boolean isEditing() {
        return elements.get("Редактирование") && mc.currentScreen != null;
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

    private String getServerName() {
        if (mc.isInSingleplayer()) return "singleplayer";

        if (mc.getCurrentServerEntry() != null) {
            if (mc.getCurrentServerEntry().name != null && !mc.getCurrentServerEntry().name.isEmpty()) {
                return mc.getCurrentServerEntry().name;
            }

            if (mc.getCurrentServerEntry().address != null && !mc.getCurrentServerEntry().address.isEmpty()) {
                return mc.getCurrentServerEntry().address;
            }
        }

        return "unknown";
    }

    private String trimToWidth(String text, float maxWidth, int size) {
        if (text == null) return "";

        if (FontDraw.getWidth(FontDraw.FontType.MEDIUM, text, size) <= maxWidth) {
            return text;
        }

        String dots = "...";
        String result = text;

        while (!result.isEmpty()
                && FontDraw.getWidth(FontDraw.FontType.MEDIUM, result + dots, size) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }

        return result + dots;
    }

    private String oneDecimal(float value) {
        int whole = (int) value;
        int decimal = (int) ((Math.abs(value - whole)) * 10f);
        return whole + "." + decimal;
    }

    private static float lerp(float from, float to, float speed) {
        speed = MathHelper.clamp(speed, 0f, 1f);
        return from + (to - from) * speed;
    }

    private static float approach(float value, float target, float speed) {
        if (value < target) return Math.min(value + speed, target);
        return Math.max(value - speed, target);
    }
}