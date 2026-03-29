package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.MultiBooleanSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.combat.CombatStateTracker;
import ru.strange.client.utils.render.Render3D;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.OptionalDouble;

@IModule(
        name = "Боксы",
        description = "боксы и глоу-обводка по целям",
        category = Category.Player,
        bind = -1
)
public class Box extends Module {

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;
    private static final Identifier GLOW_TEXTURE = Identifier.of("strange", "textures/world/glow.png");

    public static ModeSetting renderMode = new ModeSetting("Режим", "Оба", "Бокс", "Глоу", "Оба");
    public static MultiBooleanSetting targets = new MultiBooleanSetting(
            "Кого отображать",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Мобы", true)
    );
    public static ModeSetting colorStyle = new ModeSetting("Style", "Default", "Default", "Theme");
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !usesThemeStyle());
    public static BooleanSetting friendShader = new BooleanSetting("Friend Theme", true).hidden(() -> !usesThemeStyle());
    public static ModeSetting friendShaderTheme = new ModeSetting("Friend Shader Theme", ShaderThemePreset.AURORA.displayName(), ShaderThemePreset.names())
            .hidden(() -> !usesThemeStyle() || !friendShader.get());
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));
    public static BooleanSetting hitColorize = new BooleanSetting("HitColor", true);
    public static HueSetting hitColor = new HueSetting("Цвет удара", new Color(255, 110, 110)).hidden(() -> !hitColorize.get());
    public static SliderSetting range = new SliderSetting("Дистанция", 32, 8, 64, 1, false);
    public static SliderSetting glowSize = new SliderSetting("Размер глоу", 1.0f, 0.5f, 2.5f, 0.1f, false).hidden(() -> renderMode.is("Бокс"));

    public Box() {
        addSettings(renderMode, targets, colorStyle, shaderTheme, friendShader, friendShaderTheme, colorSetting, hitColorize, hitColor, range, glowSize);
    }

    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private final int[] gradientColors = new int[4];

    @EventInit
    public void render(EventRender3D event) {
        if (mc.world == null || mc.player == null) return;
        float partialTicks = event.getTickDelta();
        double rangeValue = Optimization.capEntityEspRange(range.get());
        double rangeSq = rangeValue * rangeValue;
        String effectiveRenderMode = resolveEffectiveRenderMode();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        net.minecraft.util.math.Box searchBounds = mc.player.getBoundingBox().expand(rangeValue);
        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            for (Entity entity : mc.world.getOtherEntities(mc.player, searchBounds)) {
                if (!shouldRender(entity, rangeSq)) {
                    continue;
                }

                double x = MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX());
                double y = MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY());
                double z = MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ());
                double centerY = y + entity.getHeight() * 0.5;
                if (!isInFieldOfView(camera, cameraPos, x, centerY, z) || !isVisible(cameraPos, x, centerY, z)) {
                    continue;
                }

                int baseColor = resolveEntityColor(entity);
                if (Optimization.shouldLimitEntityEsp() && effectiveRenderMode.equals(renderMode.modes.getFirst())) {
                    renderBox(event.getMatrixStack(), immediate, entity, cameraPos, x, y, z, baseColor);
                    continue;
                }
                if (!renderMode.is("Глоу")) {
                    renderBox(event.getMatrixStack(), immediate, entity, cameraPos, x, y, z, baseColor);
                }
                if (!renderMode.is("Бокс")) {
                    renderGlow(event.getMatrixStack(), immediate, entity, camera, cameraPos, x, y, z, baseColor);
                }
            }

            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private boolean shouldRender(Entity entity, double rangeSq) {
        if (entity == null || entity == mc.player || entity.isRemoved() || entity.isInvisible()) return false;
        if (mc.player.squaredDistanceTo(entity) > rangeSq) return false;

        if (entity instanceof PlayerEntity) {
            return targets.get("Игроки");
        }
        return entity instanceof LivingEntity && targets.get("Мобы");
    }

    private int resolveEntityColor(Entity entity) {
        boolean isFriend = entity instanceof AbstractClientPlayerEntity player
                && Strange.get.friendManager.isFriend(player.getGameProfile().getName());
        int baseColor;

        if (usesThemeStyle()) {
            String theme = isFriend && friendShader.get() ? friendShaderTheme.get() : shaderTheme.get();
            baseColor = ShaderThemeVisuals.animatedPrimary(
                    theme,
                    entity.getId() * 0.33 + entity.getX() * 0.17 + entity.getY() * 0.11 + entity.getZ() * 0.21
            );
        } else if (isFriend) {
            baseColor = Color.GREEN.getRGB();
        } else {
            baseColor = colorSetting.getRGB();
        }

        float hitPulse = hitColorize.get()
                ? CombatStateTracker.getInstance().getEntityPulse(entity, CombatStateTracker.Marker.HIT, 420L)
                : 0.0f;
        return RenderUtil.ColorUtil.interpolate(baseColor, hitColor.getRGB(), hitPulse);
    }

    private static boolean usesThemeStyle() {
        return colorStyle.is("Theme") || colorStyle.is("Shader");
    }

    private String resolveEffectiveRenderMode() {
        return Optimization.shouldLimitEntityEsp() ? renderMode.modes.getFirst() : renderMode.get();
    }

    private boolean isInFieldOfView(Camera camera, Vec3d cameraPos, double entityX, double entityCenterY, double entityZ) {
        double dx = entityX - cameraPos.x;
        double dy = entityCenterY - cameraPos.y;
        double dz = entityZ - cameraPos.z;
        double distXZSq = dx * dx + dz * dz;
        if (distXZSq < 1.0E-12) return true;

        double distXZ = Math.sqrt(distXZSq);
        float targetYaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        float targetPitch = (float) (-(MathHelper.atan2(dy, distXZ) * 180.0 / Math.PI));

        float dyaw = MathHelper.wrapDegrees(targetYaw - camera.getYaw());
        float dpitch = MathHelper.wrapDegrees(targetPitch - camera.getPitch());
        return Math.abs(dyaw) <= 75.0f && Math.abs(dpitch) <= 75.0f;
    }

    private boolean isVisible(Vec3d cameraPos, double entityX, double entityCenterY, double entityZ) {
        HitResult hit = mc.world.raycast(new RaycastContext(
                cameraPos,
                new Vec3d(entityX, entityCenterY, entityZ),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void renderBox(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Entity target,
                           Vec3d cameraPos, double x, double y, double z, int baseColor) {
        net.minecraft.util.math.Box boundingBox = target.getBoundingBox();

        double minX = boundingBox.minX - target.getX() + x - cameraPos.x;
        double minY = boundingBox.minY - target.getY() + y - cameraPos.y;
        double minZ = boundingBox.minZ - target.getZ() + z - cameraPos.z;
        double maxX = boundingBox.maxX - target.getX() + x - cameraPos.x;
        double maxY = boundingBox.maxY - target.getY() + y - cameraPos.y;
        double maxZ = boundingBox.maxZ - target.getZ() + z - cameraPos.z;

        int color1 = RenderUtil.ColorUtil.multDark(baseColor, 0.35f);
        int color2 = RenderUtil.ColorUtil.multDark(baseColor, 0.62f);
        gradientColors[0] = color1;
        gradientColors[1] = color2;
        gradientColors[2] = color1;
        gradientColors[3] = color2;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer fillBuffer = immediate.getBuffer(BOX_FILL_LAYER);
        Render3D.drawBoxFill(fillBuffer, matrix, minX, minY + 0.01f, minZ, maxX, maxY, maxZ, gradientColors, 92);

        VertexConsumer lineBuffer = immediate.getBuffer(BOX_LINE_LAYER);
        Render3D.drawBoxOutline(lineBuffer, matrix, minX, minY + 0.01f, minZ, maxX, maxY, maxZ, gradientColors, 255, 0.15, 0.08);
    }

    private void renderGlow(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Entity target,
                            Camera camera, Vec3d cameraPos, double x, double y, double z, int color) {
        float size = (target.getWidth() + 0.6f) * glowSize.get();
        float height = (target.getHeight() + 0.45f) * glowSize.get();
        int alpha = 70 + (int) (CombatStateTracker.getInstance().getEntityPulse(target, CombatStateTracker.Marker.HIT, 420L) * 90.0f);

        matrices.push();
        matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() * 0.55f, z - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrices.scale(size, height, 1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
        drawGlowQuad(glowBuffer, matrix, color, alpha);
        matrices.pop();
    }

    private void drawGlowQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minecraft", "rendertype_lequal_depth_test"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline BOX_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("minecraft", "rendertype_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "box_glow"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of(
            "strange_esp_box_fill",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            BOX_FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
            "strange_esp_box_line",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            BOX_LINE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.0)))
                    .build(false)
    );

    private static final RenderLayer GLOW_LAYER = RenderLayer.of(
            "strange_box_glow",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            GLOW_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(GLOW_TEXTURE, false))
                    .build(false)
    );
}
