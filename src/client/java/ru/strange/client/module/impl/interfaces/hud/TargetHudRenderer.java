package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;
import ru.strange.client.utils.render.FontDraw.FontType;
import ru.strange.client.utils.render.RenderUtil.ColorUtil;
import ru.strange.client.utils.render.RenderUtil.Round;

import java.lang.reflect.Method;
import java.util.Optional;

public final class TargetHudRenderer {
    private static final float W = 108.0F;
    private static final float H = 36.0F;
    private static final float HEAD_SIZE = 20.0F;
    private static final float BAR_H = 4.0F;
    private static final float ARMOR_SCALE = 0.66F;
    private static final float ARMOR_STEP = 11.0F;

    private LivingEntity target;
    private LivingEntity healthAnimatedEntity;
    private long targetKeepUntil;
    private float targetAlpha;
    private float displayedHealth = 20.0F;
    private boolean lastAttackDown;

    private final WaterMark owner;

    public TargetHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void updateState() {
        long now = System.currentTimeMillis();
        var mc = owner.mc;

        LivingEntity hovered = this.getHoveredTarget(mc);
        boolean attackDown = isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_1);

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
        boolean show = valid && (hovered != null || now < targetKeepUntil);

        targetAlpha = lerp(targetAlpha, show ? 1.0F : 0.0F, show ? 0.14F : 0.08F);

        if (!show && targetAlpha < 0.02F) {
            target = null;
            healthAnimatedEntity = null;
        }
    }

    public void render(DrawContext ctx, float x, float y) {
        boolean preview = this.target == null && this.owner.isEditing();
        float alpha = preview ? 0.95F : this.targetAlpha;
        if (alpha < 0.02F) return;

        RenderUtil.drawClientRect(ctx, x, y, W, H);

        float headX = x + 5.0F;
        float headY = y + 5.0F;

        if (preview) {
            this.drawFaceTexture(ctx,
                    Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"),
                    headX + 3.0F, headY + 3.0F,
                    (int) (HEAD_SIZE - 6.0F),
                    8.0F, 8.0F,
                    64, 32,
                    false
            );
        } else {
            this.drawTargetPortrait(ctx, this.target, headX, headY, HEAD_SIZE, alpha);
        }

        float health;
        float maxHealth;

        if (preview) {
            health = 16.0F;
            maxHealth = 20.0F;
            this.displayedHealth = 16.0F;
        } else {
            float real = this.target.getHealth() + this.target.getAbsorptionAmount();
            float realMax = Math.max(1.0F, this.target.getMaxHealth() + this.target.getAbsorptionAmount());

            if (this.healthAnimatedEntity != this.target) {
                this.healthAnimatedEntity = this.target;
                this.displayedHealth = real;
            } else {
                this.displayedHealth = lerp(this.displayedHealth, real, 0.10F);
            }

            health = this.displayedHealth;
            maxHealth = realMax;
        }

        String hpText = oneDecimal(health);
        float hpWidth = FontDraw.getWidth(FontType.MEDIUM, hpText, 4);

        float textX = x + 29.0F;
        float textY = y + 11.2F;
        float hpX = x + W - hpWidth - 6.0F;

        float maxNameWidth = Math.max(18.0F, hpX - textX - 4.0F);
        String name = preview
                ? ModLocalization.tr("hud.target.preview")
                : this.owner.trimToWidth(this.target.getName().getString(), maxNameWidth, 5);

        FontDraw.drawText(FontType.MEDIUM, ctx, name, textX, textY, 5, this.textTheme(alpha));
        FontDraw.drawText(FontType.MEDIUM, ctx, hpText, hpX, textY, 4, this.subTextTheme(alpha));

        float armorX = x + 29.0F;
        float armorY = y + 16.2F;

        for (int i = 0; i < 4; ++i) {
            ItemStack stack;
            if (preview) {
                stack = switch (i) {
                    case 0 -> Items.NETHERITE_HELMET.getDefaultStack();
                    case 1 -> Items.NETHERITE_CHESTPLATE.getDefaultStack();
                    case 2 -> Items.NETHERITE_LEGGINGS.getDefaultStack();
                    default -> Items.NETHERITE_BOOTS.getDefaultStack();
                };
            } else {
                stack = switch (i) {
                    case 0 -> this.target.getEquippedStack(EquipmentSlot.HEAD);
                    case 1 -> this.target.getEquippedStack(EquipmentSlot.CHEST);
                    case 2 -> this.target.getEquippedStack(EquipmentSlot.LEGS);
                    default -> this.target.getEquippedStack(EquipmentSlot.FEET);
                };
            }

            this.renderArmorMini(ctx, stack, armorX + (float)i * ARMOR_STEP, armorY);
        }

        float progress = MathHelper.clamp(health / maxHealth, 0.0F, 1.0F);
        float barX = x + 5.0F;
        float barY = y + H - 6.0F;
        float barW = W - 10.0F;
        float radius = 2.0F;

        Round.draw(ctx, barX, barY, barW, BAR_H, radius, this.barBackgroundTheme(alpha));
        float fillW = barW * progress;
        if (fillW > 0.01F) {
            fillW = Math.max(fillW, BAR_H);
            Round.draw(ctx, barX, barY, Math.min(fillW, barW), BAR_H, radius, this.barFillTheme(alpha));
        }
    }

    public static float getW() {
        return W;
    }

    public static float getH() {
        return H;
    }

    private void drawTargetPortrait(DrawContext ctx, LivingEntity entity, float x, float y, float boxSize, float alpha) {
        if (entity == null) return;

        if (entity instanceof AbstractClientPlayerEntity player) {
            int size = (int) (boxSize - 4.0F);
            float dx = x + (boxSize - (float) size) / 2.0F;
            float dy = y + (boxSize - (float) size) / 2.0F;
            this.drawPlayerHead(ctx, player, dx, dy, size);
            return;
        }

        int size = (int) (boxSize - 6.0F);
        float dx = x + (boxSize - (float) size) / 2.0F;
        float dy = y + (boxSize - (float) size) / 2.0F + 1.0F;

        if (!this.drawKnownMobFace(ctx, entity, dx, dy, size)) {
            if (!this.drawRendererTextureFace(ctx, entity, dx, dy, size)) {
                ItemStack fallback = this.getEntityIconStack(entity);
                if (!fallback.isEmpty()) {
                    float scale = Math.min((boxSize - 6.0F) / 16.0F, 0.9F);
                    float itemSize = 16.0F * scale;
                    float ix = x + (boxSize - itemSize) / 2.0F;
                    float iy = y + (boxSize - itemSize) / 2.0F;
                    this.drawScaledItem(ctx, fallback, ix, iy, scale);
                }
            }
        }
    }

    private boolean drawKnownMobFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) return false;

        String path = id.getPath();

        try {
            switch (path) {
                case "zombie" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "husk" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/husk.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "drowned" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/drowned.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "giant" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "piglin" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "piglin_brute" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin_brute.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "zombified_piglin" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/zombified_piglin.png"), x, y, size, 8.0F, 8.0F, 64, 64, true);
                    return true;
                }
                case "skeleton" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"), x, y, size, 8.0F, 8.0F, 64, 32, false);
                    return true;
                }
                case "wither_skeleton" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/wither_skeleton.png"), x, y, size, 8.0F, 8.0F, 64, 32, false);
                    return true;
                }
                case "stray" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/stray.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "creeper" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/creeper/creeper.png"), x, y, size, 8.0F, 8.0F, 64, 32, false);
                    return true;
                }
                case "enderman" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/enderman/enderman.png"), x, y, size, 8.0F, 8.0F, 64, 32, false);
                    return true;
                }
                case "zombie_villager" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "villager" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/villager/villager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "wandering_trader" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/wandering_trader.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "pillager" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/pillager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "vindicator" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/vindicator.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "evoker" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/evoker.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "illusioner" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/illusioner.png"), x, y, size, 8.0F, 8.0F, 64, 64, false);
                    return true;
                }
                case "witch" -> {
                    this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/witch.png"), x, y, size, 8.0F, 8.0F, 64, 128, false);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean drawRendererTextureFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        try {
            Identifier texture = this.getEntityTextureFromRenderer(entity);
            if (texture == null) return false;

            int[] sz = this.guessTextureSize(entity);
            this.drawFaceTexture(ctx, texture, x, y, size, 8.0F, 8.0F, sz[0], sz[1], false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Identifier getEntityTextureFromRenderer(LivingEntity entity) {
        try {
            Object dispatcher = owner.mc.getEntityRenderDispatcher();
            Object renderer = null;

            for (Method m : dispatcher.getClass().getMethods()) {
                if (!"getRenderer".equals(m.getName()) || m.getParameterCount() != 1) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(entity.getClass())) continue;
                m.setAccessible(true);
                renderer = m.invoke(dispatcher, entity);
                break;
            }

            if (renderer == null) return null;

            for (Method m : renderer.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                if (!Identifier.class.isAssignableFrom(m.getReturnType())) continue;
                if (!m.getParameterTypes()[0].isAssignableFrom(entity.getClass())) continue;
                m.setAccessible(true);
                Object result = m.invoke(renderer, entity);
                if (result instanceof Identifier id) return id;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private int[] guessTextureSize(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String path = id == null ? "" : id.getPath();

        return switch (path) {
            case "zombie", "husk", "drowned", "giant",
                 "piglin", "piglin_brute", "zombified_piglin",
                 "villager", "zombie_villager",
                 "pillager", "vindicator", "evoker", "illusioner",
                 "wandering_trader", "stray" -> new int[]{64, 64};
            case "witch" -> new int[]{64, 128};
            default -> new int[]{64, 32};
        };
    }

    private void drawFaceTexture(DrawContext ctx, Identifier texture,
                                 float x, float y, int size,
                                 float u, float v, int texW, int texH, boolean overlay) {
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, u, v, size, size, 8, 8, texW, texH);
            if (overlay) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        (int) x, (int) y, u + 32.0F, v, size, size, 8, 8, texW, texH);
            }
        } catch (Exception ignored) {
        }
    }

    private void drawPlayerHead(DrawContext ctx, AbstractClientPlayerEntity player,
                                float x, float y, int size) {
        try {
            Identifier skin = player.getSkinTextures().texture();
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } catch (Exception ignored) {
            ctx.drawItem(Items.PLAYER_HEAD.getDefaultStack(), (int) x, (int) y);
        }
    }

    public void drawScaledItem(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack != null && !stack.isEmpty()) {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawItem(stack, 0, 0);
            ctx.getMatrices().popMatrix();
        }
    }

    private void renderArmorMini(DrawContext ctx, ItemStack stack, float x, float y) {
        drawScaledItem(ctx, stack, x, y, ARMOR_SCALE);
    }

    private ItemStack getEntityIconStack(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        String path = id == null ? "" : id.getPath();

        return switch (path) {
            case "armor_stand" -> Items.ARMOR_STAND.getDefaultStack();
            case "iron_golem" -> Items.IRON_BLOCK.getDefaultStack();
            case "snow_golem" -> Items.SNOW_BLOCK.getDefaultStack();
            case "wither" -> Items.WITHER_SKELETON_SKULL.getDefaultStack();
            case "ender_dragon" -> Items.DRAGON_HEAD.getDefaultStack();
            case "giant" -> Items.ZOMBIE_HEAD.getDefaultStack();
            case "wither_skeleton" -> Items.WITHER_SKELETON_SKULL.getDefaultStack();
            case "skeleton" -> Items.SKELETON_SKULL.getDefaultStack();
            case "zombie" -> Items.ZOMBIE_HEAD.getDefaultStack();
            case "creeper" -> Items.CREEPER_HEAD.getDefaultStack();
            case "piglin", "piglin_brute", "zombified_piglin" -> Items.PIGLIN_HEAD.getDefaultStack();
            default -> {
                ItemStack egg = getSpawnEggIcon(entity);
                yield egg.isEmpty() ? Items.NAME_TAG.getDefaultStack() : egg;
            }
        };
    }

    private ItemStack getSpawnEggIcon(LivingEntity entity) {
        try {
            for (Method m : SpawnEggItem.class.getDeclaredMethods()) {
                if (!"forEntity".equals(m.getName()) || m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                Object result = m.invoke(null, entity.getType());

                if (result instanceof SpawnEggItem egg) {
                    return new ItemStack(egg);
                }

                if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof SpawnEggItem egg) {
                    return new ItemStack(egg);
                }
            }
        } catch (Exception ignored) {
        }

        return ItemStack.EMPTY;
    }

    /**
     * Легитный hover-target:
     * - не игрок сам
     * - не spectator
     * - не мертвый
     * - не удалённый
     * - не слишком далеко
     * - если невидимка, то только если на ней есть броня/предмет
     */
    private LivingEntity getHoveredTarget(MinecraftClient mc) {
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)) {
            return null;
        }

        Entity entity = hit.getEntity();
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }

        if (entity == mc.player) {
            return null;
        }

        if (!living.isAlive()) {
            return null;
        }

        if (living.isRemoved()) {
            return null;
        }

        if (living instanceof PlayerEntity player && player.isSpectator()) {
            return null;
        }

        if (mc.player != null && mc.player.distanceTo(living) > 6.0f) {
            return null;
        }

        if (living.isInvisible() && !hasVisibleEquipment(living)) {
            return null;
        }

        return living;
    }

    private boolean hasVisibleEquipment(LivingEntity entity) {
        if (entity == null) return false;

        return !entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.FEET).isEmpty()
                || !entity.getMainHandStack().isEmpty()
                || !entity.getOffHandStack().isEmpty();
    }

    private int textTheme(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int) (230.0F * alpha));
    }

    private int subTextTheme(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int) (210.0F * alpha));
    }

    private int barBackgroundTheme(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int) (55.0F * alpha));
    }

    private int barFillTheme(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int) (235.0F * alpha));
    }

    private static boolean isMouseDown(MinecraftClient mc, int button) {
        return GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * MathHelper.clamp(speed, 0.0F, 1.0F);
    }

    private static String oneDecimal(float value) {
        int whole = (int) value;
        int decimal = (int) (Math.abs(value - (float) whole) * 10.0F);
        return whole + "." + decimal;
    }
}
