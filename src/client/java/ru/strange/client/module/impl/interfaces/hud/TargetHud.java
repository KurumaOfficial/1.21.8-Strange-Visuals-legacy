package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HUD-элемент: таргет-худ — показывает информацию о цели (здоровье, броню, портрет).
 */
public class TargetHud extends HudElement {

    /* ── Размеры ── */
    public static final float W = 108f;
    public static final float H = 36f;

    private static final float HEAD_SIZE = 20f;
    private static final float BAR_H = 4f;
    private static final float ARMOR_SCALE = 0.66f;
    private static final float ARMOR_STEP = 11f;

    /* ── Reflection caches (CRIT-03) ── */
    private static final Map<Class<? extends LivingEntity>, Identifier> entityTextureCache = new ConcurrentHashMap<>();
    private static volatile Method cachedSpawnEggMethod;
    private static volatile boolean spawnEggMethodSearched;

    /* ── Instance state ── */
    private LivingEntity target;
    private LivingEntity healthAnimatedEntity;
    private long targetKeepUntil;
    private float targetAlpha;
    private float displayedHealth = 20f;
    private boolean lastAttackDown;

    /* ── HudElement contract ── */

    @Override
    public void initPosition(int sw, int sh) {
        x = sw * 0.27f;
        y = sh * 0.53f;
    }

    @Override
    public float getWidth() {
        return W;
    }

    @Override
    public float getHeight() {
        return H;
    }

    @Override
    public void render(DrawContext ctx, boolean editing) {
        this.editing = editing;
        updateTargetState();

        boolean preview = target == null && editing;
        float alpha = preview ? 0.95f : targetAlpha;

        if (alpha < 0.02f) return;

        float x = this.x;
        float y = this.y;

        RenderUtil.drawClientRect(ctx, x, y, W, H);

        float headX = x + 5f;
        float headY = y + 5f;

        if (preview) {
            drawFaceTexture(
                    ctx,
                    Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"),
                    headX + 3f,
                    headY + 3f,
                    (int) (HEAD_SIZE - 6f),
                    8f,
                    8f,
                    64,
                    32,
                    false
            );
        } else {
            drawTargetPortrait(ctx, target, headX, headY, HEAD_SIZE, alpha);
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
        float hpX = x + W - hpWidth - 6f;

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
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (230 * alpha))
        );

        FontDraw.drawText(
                FontDraw.FontType.MEDIUM,
                ctx,
                hpText,
                hpX,
                textY,
                4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (210 * alpha))
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

            renderArmorMini(ctx, stack, armorX + i * ARMOR_STEP, armorY);
        }

        float progress = MathHelper.clamp(health / maxHealth, 0f, 1f);

        float barX = x + 5f;
        float barY = y + H - 6f;
        float barW = W - 10f;
        float radius = BAR_H / 2f;

        RenderUtil.Round.draw(
                ctx,
                barX,
                barY,
                barW,
                BAR_H,
                radius,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (55 * alpha))
        );

        float fillW = barW * progress;
        if (fillW > 0.01f) {
            fillW = Math.max(fillW, BAR_H);

            RenderUtil.Round.draw(
                    ctx,
                    barX,
                    barY,
                    Math.min(fillW, barW),
                    BAR_H,
                    radius,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (235 * alpha))
            );
        }
    }

    /* ── Target tracking ── */

    private void updateTargetState() {
        long now = System.currentTimeMillis();
        LivingEntity hovered = getHoveredTarget();
        boolean attackDown = isMouseDown(GLFW.GLFW_MOUSE_BUTTON_1);

        if (hovered != null) {
            target = hovered;
            targetKeepUntil = now + 3000L;
        }

        if (attackDown && !lastAttackDown && hovered != null) {
            target = hovered;
            targetKeepUntil = now + 3000L;
        }

        lastAttackDown = attackDown;

        boolean valid = target != null && target.isAlive() && !target.isRemoved();
        boolean shouldShow = valid && (hovered != null || now < targetKeepUntil);

        targetAlpha = lerp(targetAlpha, shouldShow ? 1f : 0f, shouldShow ? 0.14f : 0.08f);

        if (!shouldShow && targetAlpha < 0.02f) {
            target = null;
            healthAnimatedEntity = null;
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

    /* ── Portrait dispatch ── */

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

    /* ── Known mob faces (big switch) ── */

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

    /* ── Renderer texture fallback ── */

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
        @SuppressWarnings("unchecked")
        Class<? extends LivingEntity> entityClass = (Class<? extends LivingEntity>) entity.getClass();
        Identifier cached = entityTextureCache.get(entityClass);
        if (cached != null) return cached;
        if (entityTextureCache.containsKey(entityClass)) return null; // already searched, was null

        try {
            Object dispatcher = mc.getEntityRenderDispatcher();
            Object renderer = null;

            for (Method method : dispatcher.getClass().getMethods()) {
                if (!method.getName().equals("getRenderer")) continue;
                if (method.getParameterCount() != 1) continue;
                Class<?> type = method.getParameterTypes()[0];
                if (!type.isAssignableFrom(entityClass)) continue;
                method.setAccessible(true);
                renderer = method.invoke(dispatcher, entity);
                break;
            }

            if (renderer == null) {
                entityTextureCache.put(entityClass, null);
                return null;
            }

            for (Method method : renderer.getClass().getMethods()) {
                if (method.getParameterCount() != 1) continue;
                if (!Identifier.class.isAssignableFrom(method.getReturnType())) continue;
                Class<?> type = method.getParameterTypes()[0];
                if (!type.isAssignableFrom(entityClass)) continue;
                method.setAccessible(true);
                Object result = method.invoke(renderer, entity);
                if (result instanceof Identifier id) {
                    entityTextureCache.put(entityClass, id);
                    return id;
                }
            }
        } catch (Throwable ignored) {}

        entityTextureCache.put(entityClass, null);
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

    /* ── Low-level texture drawing ── */

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

    /* ── Armor mini icons ── */

    private void renderArmorMini(DrawContext ctx, ItemStack stack, float x, float y) {
        if (stack == null || stack.isEmpty()) return;
        drawScaledItem(ctx, stack, x, y, ARMOR_SCALE);
    }

    /* ── Entity fallback icons ── */

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
            if (!spawnEggMethodSearched) {
                spawnEggMethodSearched = true;
                for (Method m : SpawnEggItem.class.getDeclaredMethods()) {
                    if (m.getName().equals("forEntity") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        cachedSpawnEggMethod = m;
                        break;
                    }
                }
            }
            if (cachedSpawnEggMethod == null) return ItemStack.EMPTY;

            Object result = cachedSpawnEggMethod.invoke(null, entity.getType());

            if (result instanceof SpawnEggItem egg) {
                return new ItemStack(egg);
            }
            if (result instanceof java.util.Optional<?> optional) {
                if (optional.isPresent() && optional.get() instanceof SpawnEggItem egg) {
                    return new ItemStack(egg);
                }
            }
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }
}
