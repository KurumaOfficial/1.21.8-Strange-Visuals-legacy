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
import ru.strange.client.utils.combat.CombatUtil;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;
import ru.strange.client.utils.render.FontDraw.FontType;
import ru.strange.client.utils.render.RenderUtil.ColorUtil;
import ru.strange.client.utils.render.RenderUtil.Round;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class TargetHudRenderer {
    private static final float W = 108.0F;
    private static final float H = 36.0F;
    private static final float HEAD_SIZE = 20.0F;
    private static final float BAR_H = 4.0F;
    private static final float ARMOR_SCALE = 0.66F;
    private static final float ARMOR_STEP = 11.0F;
    private static final long TARGET_KEEP_DURATION_MS = 3000L;
    private static final double TARGET_LOOK_RANGE = CombatUtil.DEFAULT_TARGET_LOOK_RANGE;

    private LivingEntity target;
    private LivingEntity healthAnimatedEntity;
    private long targetKeepUntil;
    private float targetAlpha;
    private float displayedHealth = 20.0F;
    private boolean lastAttackDown;

    private final WaterMark owner;
    private final Map<ReflectionLookupKey, Optional<Method>> rendererLookupMethodCache = new HashMap<>();
    private final Map<ReflectionLookupKey, Optional<Method>> textureLookupMethodCache = new HashMap<>();
    private Optional<Method> spawnEggLookupMethod;

    private record ReflectionLookupKey(Class<?> ownerType, Class<?> argumentType) {
    }

    public TargetHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    public void updateState() {
        long now = System.currentTimeMillis();
        var mc = owner.mc;
        if (mc == null || mc.world == null || mc.player == null || mc.getWindow() == null) {
            lastAttackDown = false;
            clearTargetState();
            return;
        }

        if (!isTrackedTargetValid(mc, target)) {
            target = null;
            healthAnimatedEntity = null;
            targetKeepUntil = 0L;
        }

        LivingEntity hovered = this.getHoveredTarget(mc);
        boolean attackDown = isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_1);

        if (hovered != null) {
            target = hovered;
            targetKeepUntil = now + TARGET_KEEP_DURATION_MS;
        }

        if (attackDown && !lastAttackDown && hovered != null) {
            target = hovered;
            targetKeepUntil = now + TARGET_KEEP_DURATION_MS;
        }

        lastAttackDown = attackDown;

        boolean valid = isTrackedTargetValid(mc, target);
        boolean show = valid && (hovered != null || now < targetKeepUntil);

        targetAlpha = lerp(targetAlpha, show ? 1.0F : 0.0F, show ? 0.14F : 0.08F);

        if (!show && targetAlpha < 0.02F) {
            clearTargetState();
        }
    }

    public void render(DrawContext ctx, float x, float y) {
        boolean preview = this.target == null && this.owner.isEditing();
        float alpha = preview ? 0.95F : this.targetAlpha;
        if (alpha < 0.02F) return;

        LivingEntity renderTarget = this.target;

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
                    false,
                    "preview-target-face"
            );
        } else {
            this.drawTargetPortrait(ctx, renderTarget, headX, headY, HEAD_SIZE, alpha);
        }

        float health;
        float maxHealth;

        if (preview) {
            health = 16.0F;
            maxHealth = 20.0F;
            this.displayedHealth = 16.0F;
        } else {
            float real = renderTarget.getHealth() + renderTarget.getAbsorptionAmount();
            float realMax = Math.max(1.0F, renderTarget.getMaxHealth() + renderTarget.getAbsorptionAmount());

            if (this.healthAnimatedEntity != renderTarget) {
                this.healthAnimatedEntity = renderTarget;
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
                : this.owner.trimToWidth(renderTarget.getName().getString(), maxNameWidth, 5);

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
                    case 0 -> renderTarget.getEquippedStack(EquipmentSlot.HEAD);
                    case 1 -> renderTarget.getEquippedStack(EquipmentSlot.CHEST);
                    case 2 -> renderTarget.getEquippedStack(EquipmentSlot.LEGS);
                    default -> renderTarget.getEquippedStack(EquipmentSlot.FEET);
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

    public void rememberTarget(LivingEntity rememberedTarget) {
        if (!isTrackedTargetValid(owner.mc, rememberedTarget)) {
            return;
        }

        target = rememberedTarget;
        targetKeepUntil = System.currentTimeMillis() + TARGET_KEEP_DURATION_MS;
    }

    public static float getW() {
        return W;
    }

    public static float getH() {
        return H;
    }

    private void clearTargetState() {
        target = null;
        healthAnimatedEntity = null;
        targetKeepUntil = 0L;
        targetAlpha = 0.0F;
        displayedHealth = 20.0F;
    }

    private static boolean isTrackedTargetValid(MinecraftClient mc, LivingEntity entity) {
        return mc != null
                && mc.world != null
                && entity != null
                && entity.getWorld() == mc.world
                && entity.isAlive()
                && !entity.isRemoved();
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
        return switch (path) {
            case "zombie" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "husk" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/husk.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "drowned" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/drowned.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "giant" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "piglin" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "piglin_brute" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin_brute.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "zombified_piglin" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/zombified_piglin.png"), x, y, size, 8.0F, 8.0F, 64, 64, true, "known-mob-face:" + path);
            case "skeleton" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"), x, y, size, 8.0F, 8.0F, 64, 32, false, "known-mob-face:" + path);
            case "wither_skeleton" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/wither_skeleton.png"), x, y, size, 8.0F, 8.0F, 64, 32, false, "known-mob-face:" + path);
            case "stray" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/stray.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "creeper" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/creeper/creeper.png"), x, y, size, 8.0F, 8.0F, 64, 32, false, "known-mob-face:" + path);
            case "enderman" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/enderman/enderman.png"), x, y, size, 8.0F, 8.0F, 64, 32, false, "known-mob-face:" + path);
            case "zombie_villager" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "villager" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/villager/villager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "wandering_trader" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/wandering_trader.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "pillager" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/pillager.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "vindicator" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/vindicator.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "evoker" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/evoker.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "illusioner" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/illusioner.png"), x, y, size, 8.0F, 8.0F, 64, 64, false, "known-mob-face:" + path);
            case "witch" -> this.drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/witch.png"), x, y, size, 8.0F, 8.0F, 64, 128, false, "known-mob-face:" + path);
            default -> false;
        };
    }

    private boolean drawRendererTextureFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        Identifier texture = this.getEntityTextureFromRenderer(entity);
        if (texture == null) return false;

        int[] sz = this.guessTextureSize(entity);
        String debugKey = "renderer-texture-face:" + entityDebugId(entity);
        return this.drawFaceTexture(ctx, texture, x, y, size, 8.0F, 8.0F, sz[0], sz[1], false, debugKey);
    }

    private Identifier getEntityTextureFromRenderer(LivingEntity entity) {
        try {
            Object dispatcher = owner.mc.getEntityRenderDispatcher();
            Method rendererLookupMethod = resolveRendererLookupMethod(dispatcher.getClass(), entity.getClass());
            if (rendererLookupMethod == null) {
                HudRenderDiagnostics.debugOnce(
                        "entity-renderer-method:" + entityDebugId(entity),
                        "Failed to resolve target HUD renderer lookup method",
                        new NoSuchMethodException("No compatible getRenderer method found for " + entity.getClass().getName())
                );
                return null;
            }

            Object renderer = rendererLookupMethod.invoke(dispatcher, entity);
            if (renderer == null) return null;

            Method textureLookupMethod = resolveTextureLookupMethod(renderer.getClass(), entity.getClass());
            if (textureLookupMethod == null) {
                HudRenderDiagnostics.debugOnce(
                        "entity-renderer-texture-method:" + entityDebugId(entity),
                        "Failed to resolve target HUD renderer texture method",
                        new NoSuchMethodException("No compatible texture method found for " + renderer.getClass().getName())
                );
                return null;
            }

            Object result = textureLookupMethod.invoke(renderer, entity);
            if (result instanceof Identifier id) {
                return id;
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            String debugKey = "entity-renderer-texture-lookup:" + entityDebugId(entity);
            HudRenderDiagnostics.debugOnce(debugKey, "Failed to resolve target HUD renderer texture", exception);
        }

        return null;
    }

    private Method resolveRendererLookupMethod(Class<?> dispatcherClass, Class<?> entityClass) {
        return resolveCachedLookupMethod(rendererLookupMethodCache, dispatcherClass, entityClass, "getRenderer", false);
    }

    private Method resolveTextureLookupMethod(Class<?> rendererClass, Class<?> entityClass) {
        return resolveCachedLookupMethod(textureLookupMethodCache, rendererClass, entityClass, null, true);
    }

    private Method resolveCachedLookupMethod(Map<ReflectionLookupKey, Optional<Method>> cache,
                                             Class<?> ownerClass,
                                             Class<?> argumentClass,
                                             String methodName,
                                             boolean requireIdentifierReturn) {
        ReflectionLookupKey key = new ReflectionLookupKey(ownerClass, argumentClass);
        return cache.computeIfAbsent(key, ignored -> Optional.ofNullable(
                findBestMatchingMethod(ownerClass, argumentClass, methodName, requireIdentifierReturn)
        )).orElse(null);
    }

    private static Method findBestMatchingMethod(Class<?> ownerClass,
                                                 Class<?> argumentClass,
                                                 String methodName,
                                                 boolean requireIdentifierReturn) {
        Method bestMethod = null;
        int bestDistance = Integer.MAX_VALUE;
        Set<Method> candidates = collectCandidateMethods(ownerClass);

        for (Method method : candidates) {
            if (methodName != null && !methodName.equals(method.getName())) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            if (requireIdentifierReturn && !Identifier.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(argumentClass)) {
                continue;
            }

            int distance = inheritanceDistance(argumentClass, parameterType);
            if (distance < bestDistance) {
                bestMethod = method;
                bestDistance = distance;
            }
        }

        if (bestMethod != null) {
            trySetAccessible(bestMethod);
        }
        return bestMethod;
    }

    private static Set<Method> collectCandidateMethods(Class<?> ownerClass) {
        LinkedHashSet<Method> methods = new LinkedHashSet<>();
        for (Class<?> cursor = ownerClass; cursor != null; cursor = cursor.getSuperclass()) {
            for (Method method : cursor.getDeclaredMethods()) {
                methods.add(method);
            }
        }
        for (Method method : ownerClass.getMethods()) {
            methods.add(method);
        }
        return methods;
    }

    private static void trySetAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException exception) {
            HudRenderDiagnostics.debugOnce(
                    "target-hud-access:" + method.toGenericString(),
                    "Failed to open target HUD reflective method",
                    exception
            );
        }
    }

    private static int inheritanceDistance(Class<?> childClass, Class<?> parentClass) {
        if (childClass == parentClass) {
            return 0;
        }

        int distance = 1;
        Class<?> cursor = childClass.getSuperclass();
        while (cursor != null) {
            if (cursor == parentClass) {
                return distance;
            }
            cursor = cursor.getSuperclass();
            distance++;
        }

        if (parentClass.isInterface() && parentClass.isAssignableFrom(childClass)) {
            return 512;
        }

        return Integer.MAX_VALUE;
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

    private boolean drawFaceTexture(DrawContext ctx, Identifier texture,
                                    float x, float y, int size,
                                    float u, float v, int texW, int texH, boolean overlay,
                                    String debugKey) {
        if (texture == null || size <= 0 || texW <= 0 || texH <= 0) {
            return false;
        }
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, u, v, size, size, 8, 8, texW, texH);
            if (overlay) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        (int) x, (int) y, u + 32.0F, v, size, size, 8, 8, texW, texH);
            }
            return true;
        } catch (RuntimeException exception) {
            HudRenderDiagnostics.debugOnce(debugKey, "Failed to draw target HUD face texture", exception);
            return false;
        }
    }

    private void drawPlayerHead(DrawContext ctx, AbstractClientPlayerEntity player,
                                float x, float y, int size) {
        if (player == null || size <= 0) {
            return;
        }
        try {
            Identifier skin = player.getSkinTextures().texture();
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } catch (RuntimeException exception) {
            HudRenderDiagnostics.debugOnce("player-head-texture", "Failed to draw target HUD player head", exception);
            float scale = Math.max(0.5F, size / 16.0F);
            float itemSize = 16.0F * scale;
            float itemX = x + (size - itemSize) / 2.0F;
            float itemY = y + (size - itemSize) / 2.0F;
            drawScaledItem(ctx, Items.PLAYER_HEAD.getDefaultStack(), itemX, itemY, scale);
        }
    }

    public void drawScaledItem(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty() || !Float.isFinite(scale) || scale <= 0.0F) {
            return;
        }

        try {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawItem(stack, 0, 0);
            ctx.getMatrices().popMatrix();
        } catch (RuntimeException exception) {
            HudRenderDiagnostics.debugOnce(
                    "target-hud-item:" + itemDebugId(stack),
                    "Failed to draw target HUD item fallback",
                    exception
            );
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
            Method lookupMethod = resolveSpawnEggLookupMethod();
            if (lookupMethod == null) {
                return ItemStack.EMPTY;
            }

            Object result = lookupMethod.invoke(null, entity.getType());
            if (result instanceof SpawnEggItem egg) {
                return new ItemStack(egg);
            }

            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof SpawnEggItem egg) {
                return new ItemStack(egg);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            String debugKey = "spawn-egg-icon:" + entityDebugId(entity);
            HudRenderDiagnostics.debugOnce(debugKey, "Failed to resolve target HUD spawn egg icon", exception);
        }

        return ItemStack.EMPTY;
    }

    private Method resolveSpawnEggLookupMethod() {
        if (spawnEggLookupMethod != null) {
            return spawnEggLookupMethod.orElse(null);
        }

        Method resolved = null;
        for (Method method : SpawnEggItem.class.getDeclaredMethods()) {
            if (!"forEntity".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            trySetAccessible(method);
            resolved = method;
            break;
        }

        spawnEggLookupMethod = Optional.ofNullable(resolved);
        return resolved;
    }

    private static String entityDebugId(LivingEntity entity) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        return entityId == null ? entity.getClass().getName() : entityId.toString();
    }

    private static String itemDebugId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        return itemId == null ? stack.getItem().toString() : itemId.toString();
    }

    /**
     * Легитный hover-target:
     * - не игрок сам
     * - не spectator
     * - не мертвый
     * - не удалённый
     * - не слишком далеко
     * - если невидимка, то только если на ней есть броня/предмет
     *
     * Используем прямую проверку mc.crosshairTarget (как в проверенной рабочей версии),
     * с фоллбэком на mc.targetedEntity и CombatUtil-рейкаст для дальних целей.
     */
    private LivingEntity getHoveredTarget(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) {
            return null;
        }

        // 1) Прямая проверка crosshairTarget — самый надёжный путь
        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity living && isHoverValid(mc, living)) {
                return living;
            }
        }

        // 2) Фоллбэк: targetedEntity
        if (mc.targetedEntity instanceof LivingEntity living && isHoverValid(mc, living)) {
            return living;
        }

        // 3) Фоллбэк: CombatUtil рейкаст для дальних целей
        return CombatUtil.findCrosshairLivingTarget(mc, TARGET_LOOK_RANGE);
    }

    private static boolean isHoverValid(MinecraftClient mc, LivingEntity living) {
        if (living == mc.player) return false;
        if (!living.isAlive() || living.isRemoved()) return false;
        if (living instanceof PlayerEntity player && player.isSpectator()) return false;
        if (mc.player.squaredDistanceTo(living) > TARGET_LOOK_RANGE * TARGET_LOOK_RANGE) return false;
        if (living.isInvisible() && !hasVisibleEquipment(living)) return false;
        return true;
    }

    private static boolean hasVisibleEquipment(LivingEntity entity) {
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
        return mc != null
                && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * MathHelper.clamp(speed, 0.0F, 1.0F);
    }

    private static String oneDecimal(float value) {
        if (!Float.isFinite(value)) {
            return "0.0";
        }

        int rounded = Math.round(value * 10.0F);
        int whole = rounded / 10;
        int decimal = Math.abs(rounded % 10);
        return whole + "." + decimal;
    }
}
