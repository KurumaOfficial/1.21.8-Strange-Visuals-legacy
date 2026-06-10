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

    private static final float LINE_W             = 98.0F;
    private static final float CIRCLE_W           = 96.0F;
    private static final float H                  = 32.0F;
    private static final float HEAD_SIZE          = 16.0F;
    private static final float BAR_H              = 3.5F;
    private static final float ARMOR_SCALE        = 0.65F; // Увеличили масштаб брони
    private static final float ARMOR_STEP         = 11.5F; // Увеличили отступ между предметами брони
    private static final float HEALTH_CIRCLE_SIZE = 22.0F;
    private static final long  TARGET_KEEP_DURATION_MS = 3000L;
    private static final double TARGET_LOOK_RANGE = CombatUtil.DEFAULT_TARGET_LOOK_RANGE;
    private static final Identifier PREVIEW_TARGET_FACE_TEXTURE =
            Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png");

    private LivingEntity target;
    private LivingEntity displayTarget;
    private LivingEntity healthAnimatedEntity;
    private long    targetKeepUntil;
    private float   targetAlpha;
    private float   displayedHealth = 20.0F;
    private boolean lastAttackDown;
    private long    lastUpdateTime = 0L;

    private final WaterMark owner;
    private final Map<ReflectionLookupKey, Optional<Method>> rendererLookupMethodCache = new HashMap<>();
    private final Map<ReflectionLookupKey, Optional<Method>> textureLookupMethodCache  = new HashMap<>();
    private Optional<Method> spawnEggLookupMethod;

    private record ReflectionLookupKey(Class<?> ownerType, Class<?> argumentType) {}

    public TargetHudRenderer(WaterMark owner) {
        this.owner = owner;
    }

    // ─── state ────────────────────────────────────────────────────────────────

    public void updateState() {
        long now = System.currentTimeMillis();
        var mc = owner.mc;
        if (mc == null || mc.world == null || mc.player == null || mc.getWindow() == null) {
            lastAttackDown = false;
            clearTargetState();
            return;
        }

        if (!isTrackedTargetValid(mc, target)) {
            if (target != null) displayTarget = target;
            target = null;
            healthAnimatedEntity = null;
            targetKeepUntil = 0L;
        }

        LivingEntity hovered = getHoveredTarget(mc);
        boolean attackDown   = isMouseDown(mc, GLFW.GLFW_MOUSE_BUTTON_1);

        if (hovered != null) {
            target          = hovered;
            displayTarget   = hovered;
            targetKeepUntil = now + TARGET_KEEP_DURATION_MS;
        }

        if (attackDown && !lastAttackDown && hovered != null) {
            target          = hovered;
            displayTarget   = hovered;
            targetKeepUntil = now + TARGET_KEEP_DURATION_MS;
        }

        lastAttackDown = attackDown;

        boolean valid = isTrackedTargetValid(mc, target);
        boolean keepAlive = valid && now < targetKeepUntil;

        if (keepAlive || hovered != null) {
            targetAlpha = lerp(targetAlpha, 1.0F, 0.18F);
        } else {
            targetAlpha = lerp(targetAlpha, 0.0F, 0.12F);
            if (targetAlpha < 0.02F) {
                clearTargetState();
            }
        }
    }

    // ─── render ───────────────────────────────────────────────────────────────

    public void render(DrawContext ctx, float x, float y) {
        boolean preview = target == null && owner.isEditing();
        float   alpha   = preview ? 0.95F : targetAlpha;
        if (alpha < 0.02F) return;

        LivingEntity renderTarget = preview ? null : (target != null ? target : displayTarget);
        if (!preview && renderTarget == null) return;

        boolean circleHealth = usesCircleHealth();
        float   width        = getWidth();

        RenderUtil.drawClientRect(ctx, x, y, width, H);
        Round.draw(ctx, x, y, width, H, 5.0F,
                ColorUtil.replAlpha(ColorUtil.getBackGroundColor(1, 1), (int)(150.0F * alpha)));

        // Голова: строго по центру по вертикали
        float headX = x + 5.0F;
        float headY = y + (H - HEAD_SIZE) / 2.0F;

        if (preview) {
            drawFaceTexture(ctx, PREVIEW_TARGET_FACE_TEXTURE,
                    headX + 1.0F, headY + 1.0F,
                    (int)(HEAD_SIZE - 2.0F),
                    8.0F, 8.0F, 64, 32, false, "preview-target-face");
        } else {
            drawTargetPortrait(ctx, renderTarget, headX, headY, HEAD_SIZE, alpha);
        }

        // Здоровье с frame-rate independent анимацией
        float health, maxHealth;
        if (preview) {
            health    = 16.0F;
            maxHealth = 20.0F;
            displayedHealth = 16.0F;
        } else {
            float real    = renderTarget.getHealth() + renderTarget.getAbsorptionAmount();
            float realMax = Math.max(1.0F, renderTarget.getMaxHealth() + renderTarget.getAbsorptionAmount());
            if (healthAnimatedEntity != renderTarget) {
                healthAnimatedEntity = renderTarget;
                displayedHealth = real;
                lastUpdateTime = System.currentTimeMillis();
            } else {
                // Frame-rate independent lerp
                long currentTime = System.currentTimeMillis();
                float deltaTime = Math.min((currentTime - lastUpdateTime) / 1000.0F, 0.1F); // Cap at 100ms
                lastUpdateTime = currentTime;
                
                // Exponential smoothing: speed = 1 - e^(-k * dt), where k controls smoothness
                float smoothingFactor = 1.0F - (float)Math.exp(-8.0 * deltaTime);
                displayedHealth = lerp(displayedHealth, real, smoothingFactor);
            }
            health    = displayedHealth;
            maxHealth = realMax;
        }

        float progress     = MathHelper.clamp(health / maxHealth, 0.0F, 1.0F);
        float textX        = headX + HEAD_SIZE + 5.0F;
        float contentRight = x + width - 5.0F;
        float maxNameWidth;

        // Выравниваем имя и броню по центру панели
        float blockTop = y + (H - 17.0F) / 2.0F;
        float nameY    = blockTop + 7.5F;
        float armorY   = y + (H - HEAD_SIZE) / 2.0F + (HEAD_SIZE - 4.0F) / 2.0F; // Центрируем броню по вертикали с головой

        String hpText = circleHealth ? compactHealthText(health) : oneDecimal(health);

        if (circleHealth) {
            float circleSize = HEALTH_CIRCLE_SIZE;
            float circleX    = contentRight - circleSize - 1.0F;
            float circleY    = y + (H - circleSize) / 2.0F;
            maxNameWidth     = Math.max(18.0F, circleX - textX - 3.0F);

            renderHealthCircle(ctx, circleX, circleY, circleSize, progress, alpha);

            // Текст HP: точно по центру круга с правильным размером шрифта
            float fontH   = FontDraw.getHeight(FontType.MEDIUM, 5);
            float ascent  = FontDraw.getAscent(FontType.MEDIUM, 5);
            float hpTextY = circleY + (circleSize - fontH) / 2.0F + ascent + 0.5F;
            FontDraw.drawCenter(FontType.MEDIUM, ctx, hpText,
                    circleX + circleSize / 2.0F,
                    hpTextY,
                    5, textTheme(alpha), false);
        } else {
            float hpX = contentRight - FontDraw.getWidth(FontType.MEDIUM, hpText, 5);
            maxNameWidth = Math.max(18.0F, hpX - textX - 4.0F);
            FontDraw.drawText(FontType.MEDIUM, ctx, hpText, hpX, nameY - 0.2F, 5, textTheme(alpha));
        }

        String name = preview
                ? ModLocalization.tr("hud.target.preview")
                : owner.trimToWidth(renderTarget.getName().getString(), maxNameWidth, 5);

        FontDraw.drawText(FontType.MEDIUM, ctx, name, textX, nameY, 5, textTheme(alpha));
        renderArmorRow(ctx, preview, renderTarget, textX, armorY);

        if (!circleHealth) {
            float barX   = x + 5.0F;
            float barY   = y + H - 6.0F;
            float barW   = width - 10.0F;
            float radius = 2.0F;
            Round.draw(ctx, barX, barY, barW, BAR_H, radius, barBackgroundTheme(alpha));
            float fillW = barW * progress;
            if (fillW > 0.01F) {
                fillW = Math.max(fillW, BAR_H);
                Round.draw(ctx, barX, barY, Math.min(fillW, barW), BAR_H, radius, barFillTheme(alpha));
            }
        }
    }

    public void rememberTarget(LivingEntity rememberedTarget) {
        if (!isTrackedTargetValid(owner.mc, rememberedTarget)) return;
        target          = rememberedTarget;
        displayTarget   = rememberedTarget;
        targetKeepUntil = System.currentTimeMillis() + TARGET_KEEP_DURATION_MS;
    }

    public float getWidth()  { return usesCircleHealth() ? CIRCLE_W : LINE_W; }
    public float getHeight() { return H; }

    // ─── armor row ────────────────────────────────────────────────────────────

    private void renderArmorRow(DrawContext ctx, boolean preview, LivingEntity renderTarget,
                                float startX, float startY) {
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
            renderArmorMini(ctx, stack, startX + (float) i * ARMOR_STEP, startY);
        }
    }

    // ─── health circle ────────────────────────────────────────────────────────

    private void renderHealthCircle(DrawContext ctx, float x, float y, float size,
                                    float progress, float alpha) {
        float cx     = x + size / 2.0F;
        float cy     = y + size / 2.0F;
        float outerR = size / 2.0F;
        float innerR = outerR - 3.5F;

        Round.draw(ctx, x + 3.0F, y + 3.0F, size - 6.0F, size - 6.0F,
                (size - 6.0F) / 2.0F,
                ColorUtil.replAlpha(ColorUtil.getBackGroundColor(1, 1), (int)(110.0F * alpha)));

        drawArc(ctx, cx, cy, outerR, innerR, 0.0F, 1.0F,
                ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int)(38.0F * alpha)));

        if (progress > 0.005F) {
            drawArc(ctx, cx, cy, outerR, innerR, 0.0F, progress,
                    ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int)(235.0F * alpha)));
        }
    }

    private void drawArc(DrawContext ctx, float cx, float cy,
                         float outerR, float innerR,
                         float startP, float endP, int color) {
        if (endP <= startP) return;
        int   steps   = Math.max(6, (int)(72 * (endP - startP)));
        float dotSize = (outerR - innerR) * 0.92F;
        float midR    = (outerR + innerR) / 2.0F;
        for (int i = 0; i <= steps; i++) {
            float  t     = startP + (endP - startP) * (i / (float) steps);
            double angle = -Math.PI / 2.0 + t * Math.PI * 2.0;
            float  dotX  = cx + (float) Math.cos(angle) * midR - dotSize / 2.0F;
            float  dotY  = cy + (float) Math.sin(angle) * midR - dotSize / 2.0F;
            Round.draw(ctx, dotX, dotY, dotSize, dotSize, dotSize / 2.0F, color);
        }
    }

    // ─── portrait ─────────────────────────────────────────────────────────────

    private void drawTargetPortrait(DrawContext ctx, LivingEntity entity,
                                    float x, float y, float boxSize, float alpha) {
        if (entity == null) return;

        if (entity instanceof AbstractClientPlayerEntity player) {
            int   size = (int)(boxSize - 2.0F);
            float dx   = x + (boxSize - size) / 2.0F;
            float dy   = y + (boxSize - size) / 2.0F;
            drawPlayerHead(ctx, player, dx, dy, size);
            return;
        }

        int   size = (int)(boxSize - 4.0F);
        float dx   = x + (boxSize - size) / 2.0F;
        float dy   = y + (boxSize - size) / 2.0F;

        if (!drawKnownMobFace(ctx, entity, dx, dy, size)) {
            if (!drawRendererTextureFace(ctx, entity, dx, dy, size)) {
                ItemStack fallback = getEntityIconStack(entity);
                if (!fallback.isEmpty()) {
                    float scale    = Math.min((boxSize - 4.0F) / 16.0F, 0.9F);
                    float itemSize = 16.0F * scale;
                    drawScaledItem(ctx, fallback,
                            x + (boxSize - itemSize) / 2.0F,
                            y + (boxSize - itemSize) / 2.0F,
                            scale);
                }
            }
        }
    }

    // ─── known mob faces ──────────────────────────────────────────────────────

    private boolean drawKnownMobFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        if (id == null) return false;
        String path = id.getPath();
        return switch (path) {
            case "zombie"           -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"),            x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "husk"             -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/husk.png"),              x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "drowned"          -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/drowned.png"),           x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "giant"            -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie/zombie.png"),            x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "piglin"           -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin.png"),            x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "piglin_brute"     -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/piglin_brute.png"),      x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "zombified_piglin" -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/piglin/zombified_piglin.png"),  x, y, size, 8F, 8F, 64,  64,  true,  "known-mob-face:" + path);
            case "skeleton"         -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/skeleton.png"),        x, y, size, 8F, 8F, 64,  32,  false, "known-mob-face:" + path);
            case "wither_skeleton"  -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/wither_skeleton.png"), x, y, size, 8F, 8F, 64,  32,  false, "known-mob-face:" + path);
            case "stray"            -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/skeleton/stray.png"),           x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "creeper"          -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/creeper/creeper.png"),          x, y, size, 8F, 8F, 64,  32,  false, "known-mob-face:" + path);
            case "enderman"         -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/enderman/enderman.png"),        x, y, size, 8F, 8F, 64,  32,  false, "known-mob-face:" + path);
            case "zombie_villager"  -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/zombie_villager/zombie_villager.png"), x, y, size, 8F, 8F, 64, 64, false, "known-mob-face:" + path);
            case "villager"         -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/villager/villager.png"),        x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "wandering_trader" -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/wandering_trader.png"),         x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "pillager"         -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/pillager.png"),         x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "vindicator"       -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/vindicator.png"),       x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "evoker"           -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/evoker.png"),           x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "illusioner"       -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/illager/illusioner.png"),       x, y, size, 8F, 8F, 64,  64,  false, "known-mob-face:" + path);
            case "witch"            -> drawFaceTexture(ctx, Identifier.of("minecraft", "textures/entity/witch.png"),                    x, y, size, 8F, 8F, 64, 128, false, "known-mob-face:" + path);
            default -> false;
        };
    }

    private boolean drawRendererTextureFace(DrawContext ctx, LivingEntity entity, float x, float y, int size) {
        Identifier texture = getEntityTextureFromRenderer(entity);
        if (texture == null) return false;
        int[] sz = guessTextureSize(entity);
        return drawFaceTexture(ctx, texture, x, y, size, 8.0F, 8.0F, sz[0], sz[1], false,
                "renderer-texture-face:" + entityDebugId(entity));
    }

    private Identifier getEntityTextureFromRenderer(LivingEntity entity) {
        try {
            Object dispatcher           = owner.mc.getEntityRenderDispatcher();
            Method rendererLookupMethod = resolveRendererLookupMethod(dispatcher.getClass(), entity.getClass());
            if (rendererLookupMethod == null) {
                HudRenderDiagnostics.debugOnce("entity-renderer-method:" + entityDebugId(entity),
                        "Failed to resolve target HUD renderer lookup method",
                        new NoSuchMethodException("No compatible getRenderer method found for " + entity.getClass().getName()));
                return null;
            }
            Object renderer = rendererLookupMethod.invoke(dispatcher, entity);
            if (renderer == null) return null;
            Method textureLookupMethod = resolveTextureLookupMethod(renderer.getClass(), entity.getClass());
            if (textureLookupMethod == null) {
                HudRenderDiagnostics.debugOnce("entity-renderer-texture-method:" + entityDebugId(entity),
                        "Failed to resolve target HUD renderer texture method",
                        new NoSuchMethodException("No compatible texture method found for " + renderer.getClass().getName()));
                return null;
            }
            Object result = textureLookupMethod.invoke(renderer, entity);
            if (result instanceof Identifier id) return id;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HudRenderDiagnostics.debugOnce("entity-renderer-texture-lookup:" + entityDebugId(entity),
                    "Failed to resolve target HUD renderer texture", e);
        }
        return null;
    }

    // ─── reflection helpers ───────────────────────────────────────────────────

    private Method resolveRendererLookupMethod(Class<?> dispatcherClass, Class<?> entityClass) {
        return resolveCachedLookupMethod(rendererLookupMethodCache, dispatcherClass, entityClass, "getRenderer", false);
    }

    private Method resolveTextureLookupMethod(Class<?> rendererClass, Class<?> entityClass) {
        return resolveCachedLookupMethod(textureLookupMethodCache, rendererClass, entityClass, null, true);
    }

    private Method resolveCachedLookupMethod(Map<ReflectionLookupKey, Optional<Method>> cache,
                                             Class<?> ownerClass, Class<?> argumentClass,
                                             String methodName, boolean requireIdentifierReturn) {
        return cache.computeIfAbsent(new ReflectionLookupKey(ownerClass, argumentClass), ignored ->
                Optional.ofNullable(findBestMatchingMethod(ownerClass, argumentClass, methodName, requireIdentifierReturn))
        ).orElse(null);
    }

    private static Method findBestMatchingMethod(Class<?> ownerClass, Class<?> argumentClass,
                                                 String methodName, boolean requireIdentifierReturn) {
        Method bestMethod   = null;
        int    bestDistance = Integer.MAX_VALUE;
        for (Method method : collectCandidateMethods(ownerClass)) {
            if (methodName != null && !methodName.equals(method.getName())) continue;
            if (method.getParameterCount() != 1) continue;
            if (requireIdentifierReturn && !Identifier.class.isAssignableFrom(method.getReturnType())) continue;
            Class<?> paramType = method.getParameterTypes()[0];
            if (!paramType.isAssignableFrom(argumentClass)) continue;
            int distance = inheritanceDistance(argumentClass, paramType);
            if (distance < bestDistance) { bestMethod = method; bestDistance = distance; }
        }
        if (bestMethod != null) trySetAccessible(bestMethod);
        return bestMethod;
    }

    private static Set<Method> collectCandidateMethods(Class<?> ownerClass) {
        LinkedHashSet<Method> methods = new LinkedHashSet<>();
        for (Class<?> c = ownerClass; c != null; c = c.getSuperclass())
            for (Method m : c.getDeclaredMethods()) methods.add(m);
        for (Method m : ownerClass.getMethods()) methods.add(m);
        return methods;
    }

    private static void trySetAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException e) {
            HudRenderDiagnostics.debugOnce("target-hud-access:" + method.toGenericString(),
                    "Failed to open target HUD reflective method", e);
        }
    }

    private static int inheritanceDistance(Class<?> childClass, Class<?> parentClass) {
        if (childClass == parentClass) return 0;
        int distance = 1;
        for (Class<?> c = childClass.getSuperclass(); c != null; c = c.getSuperclass()) {
            if (c == parentClass) return distance;
            distance++;
        }
        return (parentClass.isInterface() && parentClass.isAssignableFrom(childClass)) ? 512 : Integer.MAX_VALUE;
    }

    // ─── texture / item helpers ───────────────────────────────────────────────

    private int[] guessTextureSize(LivingEntity entity) {
        Identifier id   = Registries.ENTITY_TYPE.getId(entity.getType());
        String     path = id == null ? "" : id.getPath();
        return switch (path) {
            case "zombie", "husk", "drowned", "giant",
                 "piglin", "piglin_brute", "zombified_piglin",
                 "villager", "zombie_villager",
                 "pillager", "vindicator", "evoker", "illusioner",
                 "wandering_trader", "stray" -> new int[]{64, 64};
            case "witch" -> new int[]{64, 128};
            default      -> new int[]{64, 32};
        };
    }

    private boolean drawFaceTexture(DrawContext ctx, Identifier texture,
                                    float x, float y, int size,
                                    float u, float v, int texW, int texH,
                                    boolean overlay, String debugKey) {
        if (texture == null || size <= 0 || texW <= 0 || texH <= 0) return false;
        try {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                    (int) x, (int) y, u, v, size, size, 8, 8, texW, texH);
            if (overlay) {
                ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture,
                        (int) x, (int) y, u + 32.0F, v, size, size, 8, 8, texW, texH);
            }
            return true;
        } catch (RuntimeException e) {
            HudRenderDiagnostics.debugOnce(debugKey, "Failed to draw target HUD face texture", e);
            return false;
        }
    }

    private void drawPlayerHead(DrawContext ctx, AbstractClientPlayerEntity player,
                                float x, float y, int size) {
        if (player == null || size <= 0) return;
        try {
            Identifier skin = player.getSkinTextures().texture();
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 8.0F,  8.0F, size, size, 8, 8, 64, 64);
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, skin, (int) x, (int) y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } catch (RuntimeException e) {
            HudRenderDiagnostics.debugOnce("player-head-texture", "Failed to draw target HUD player head", e);
            float scale    = Math.max(0.5F, size / 16.0F);
            float itemSize = 16.0F * scale;
            drawScaledItem(ctx, Items.PLAYER_HEAD.getDefaultStack(),
                    x + (size - itemSize) / 2.0F,
                    y + (size - itemSize) / 2.0F, scale);
        }
    }

    public void drawScaledItem(DrawContext ctx, ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.isEmpty() || !Float.isFinite(scale) || scale <= 0.0F) return;
        try {
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(x, y);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawItem(stack, 0, 0);
            ctx.getMatrices().popMatrix();
        } catch (RuntimeException e) {
            HudRenderDiagnostics.debugOnce("target-hud-item:" + itemDebugId(stack),
                    "Failed to draw target HUD item fallback", e);
        }
    }

    private void renderArmorMini(DrawContext ctx, ItemStack stack, float x, float y) {
        drawScaledItem(ctx, stack, x, y, ARMOR_SCALE);
    }

    private ItemStack getEntityIconStack(LivingEntity entity) {
        Identifier id   = Registries.ENTITY_TYPE.getId(entity.getType());
        String     path = id == null ? "" : id.getPath();
        return switch (path) {
            case "armor_stand"     -> Items.ARMOR_STAND.getDefaultStack();
            case "iron_golem"      -> Items.IRON_BLOCK.getDefaultStack();
            case "snow_golem"      -> Items.SNOW_BLOCK.getDefaultStack();
            case "wither"          -> Items.WITHER_SKELETON_SKULL.getDefaultStack();
            case "ender_dragon"    -> Items.DRAGON_HEAD.getDefaultStack();
            case "giant"           -> Items.ZOMBIE_HEAD.getDefaultStack();
            case "wither_skeleton" -> Items.WITHER_SKELETON_SKULL.getDefaultStack();
            case "skeleton"        -> Items.SKELETON_SKULL.getDefaultStack();
            case "zombie"          -> Items.ZOMBIE_HEAD.getDefaultStack();
            case "creeper"         -> Items.CREEPER_HEAD.getDefaultStack();
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
            if (lookupMethod == null) return ItemStack.EMPTY;
            Object result = lookupMethod.invoke(null, entity.getType());
            if (result instanceof SpawnEggItem egg) return new ItemStack(egg);
            if (result instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof SpawnEggItem egg)
                return new ItemStack(egg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HudRenderDiagnostics.debugOnce("spawn-egg-icon:" + entityDebugId(entity),
                    "Failed to resolve target HUD spawn egg icon", e);
        }
        return ItemStack.EMPTY;
    }

    private Method resolveSpawnEggLookupMethod() {
        if (spawnEggLookupMethod != null) return spawnEggLookupMethod.orElse(null);
        Method resolved = null;
        for (Method method : SpawnEggItem.class.getDeclaredMethods()) {
            if (!"forEntity".equals(method.getName()) || method.getParameterCount() != 1) continue;
            trySetAccessible(method);
            resolved = method;
            break;
        }
        spawnEggLookupMethod = Optional.ofNullable(resolved);
        return resolved;
    }

    // ─── hover detection ──────────────────────────────────────────────────────

    private LivingEntity getHoveredTarget(MinecraftClient mc) {
        if (mc == null || mc.world == null || mc.player == null) return null;

        if (mc.crosshairTarget instanceof EntityHitResult hit) {
            Entity entity = hit.getEntity();
            if (entity instanceof LivingEntity living && isHoverValid(mc, living)) return living;
        }

        if (mc.targetedEntity instanceof LivingEntity living && isHoverValid(mc, living)) return living;

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

    // ─── misc ─────────────────────────────────────────────────────────────────

    private void clearTargetState() {
        target               = null;
        displayTarget        = null;
        healthAnimatedEntity = null;
        targetKeepUntil      = 0L;
        targetAlpha          = 0.0F;
        displayedHealth      = 20.0F;
    }

    private static boolean isTrackedTargetValid(MinecraftClient mc, LivingEntity entity) {
        return mc != null
                && mc.world != null
                && entity != null
                && entity.getWorld() == mc.world
                && entity.isAlive()
                && !entity.isRemoved();
    }

    private boolean usesCircleHealth() {
        return owner != null && owner.isTargetHudCircleMode();
    }

    private String compactHealthText(float value) {
        if (!Float.isFinite(value)) return "0";
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05F) return Integer.toString(rounded);
        String decimal = oneDecimal(value);
        return decimal.length() > 4 ? Integer.toString(rounded) : decimal;
    }

    private int textTheme(float alpha)          { return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int)(230.0F * alpha)); }
    private int barBackgroundTheme(float alpha)  { return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int)(55.0F  * alpha)); }
    private int barFillTheme(float alpha)        { return ColorUtil.replAlpha(ColorUtil.getTextColor(1, 1), (int)(235.0F * alpha)); }

    private static boolean isMouseDown(MinecraftClient mc, int button) {
        return mc != null && mc.getWindow() != null
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), button) == GLFW.GLFW_PRESS;
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * MathHelper.clamp(speed, 0.0F, 1.0F);
    }

    private static String oneDecimal(float value) {
        if (!Float.isFinite(value)) return "0.0";
        int rounded = Math.round(value * 10.0F);
        return (rounded / 10) + "." + Math.abs(rounded % 10);
    }

    private static String entityDebugId(LivingEntity entity) {
        Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
        return id == null ? entity.getClass().getName() : id.toString();
    }

    private static String itemDebugId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id == null ? stack.getItem().toString() : id.toString();
    }
}