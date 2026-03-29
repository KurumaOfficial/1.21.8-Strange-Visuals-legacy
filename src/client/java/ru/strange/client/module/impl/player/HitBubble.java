package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.renderengine.renderers.util.MaskedCosmosRenderer;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.animation.util.Animation;
import ru.strange.client.utils.animation.util.Easings;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

@IModule(
        name = "Хит бабл",
        description = "Эффект кольца при ударе по цели",
        category = Category.Player,
        bind = -1
)
public class HitBubble extends Module {
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;

    public static ModeSetting textureMode = new ModeSetting("Текстура", "Bubble1", "Bubble1", "Bubble2");
    public static ModeSetting renderMode = new ModeSetting("Режим", "Texture", "Texture", "Shader");
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !isShaderRender());
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(255, 255, 255))
            .hidden(HitBubble::isShaderRender);

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final String PIPELINE_NAMESPACE = "strange";
    private static final Identifier BUBBLE1_TEXTURE = Identifier.of(Strange.rootRes, "textures/world/bubble1.png");
    private static final Identifier BUBBLE2_TEXTURE = Identifier.of(Strange.rootRes, "textures/world/bubble2.png");

    private static final RenderPipeline BUBBLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/hit_bubble"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.ADDITIVE)
                    .build()
    );

    private static final RenderPipeline BUBBLE_MASK_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/hit_bubble_mask"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );

    private final List<Bubble> bubbles = new ArrayList<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);

    public HitBubble() {
        addSettings(textureMode, renderMode, shaderTheme, colorSetting);
    }

    private static RenderLayer createLayer(Identifier texture) {
        return RenderLayer.of(
                texture.toString(),
                QUAD_BUFFER_SIZE_BYTES,
                false,
                true,
                BUBBLE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .build(false)
        );
    }

    private static RenderLayer createMaskLayer(Identifier texture) {
        return RenderLayer.of(
                texture.toString() + "_mask",
                QUAD_BUFFER_SIZE_BYTES,
                false,
                true,
                BUBBLE_MASK_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .texture(new RenderPhase.Texture(texture, false))
                        .build(false)
        );
    }

    private static boolean isShaderRender() {
        return renderMode.is("Shader");
    }

    private void clearBubbles() {
        bubbles.clear();
    }

    @Override
    public void toggle() {
        super.toggle();
        clearBubbles();
    }

    @Override
    public void onDisable() {
        clearBubbles();
        super.onDisable();
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        clearBubbles();
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!event.isConfirmed() || mc.player == null || mc.world == null || Optimization.shouldDisableModuleParticles()) {
            return;
        }

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living)) {
            return;
        }

        PlayerEntity player = mc.player;
        Vec3d fromEye = player.getPos().add(0.0, player.getEyeHeight(player.getPose()), 0.0);
        Vec3d to = living.getPos().add(0.0, living.getHeight() * 0.8, 0.0);
        Vec3d diff = to.subtract(fromEye);

        double distance = Math.max(0.0, player.distanceTo(living) - 0.5 + player.getWidth() * 0.5);
        double yawRad = Math.atan2(diff.z, diff.x) - Math.PI / 2.0;
        double xOff = -Math.sin(yawRad) * distance;
        double zOff = Math.cos(yawRad) * distance;

        bubbles.add(new Bubble(new Vec3d(player.getX() + xOff, to.y, player.getZ() + zOff)));
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (mc.player == null || mc.world == null || Optimization.shouldDisableModuleParticles()) {
            clearBubbles();
            return;
        }

        bubbles.removeIf(Bubble::isDead);
        for (Bubble bubble : bubbles) {
            bubble.update();
        }
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            clearBubbles();
            return;
        }

        if (Optimization.shouldDisableModuleParticles()) {
            clearBubbles();
            return;
        }

        if (bubbles.isEmpty()) {
            return;
        }

        migrateLegacyShaderSelection();
        if (bubbles.isEmpty()) {
            return;
        }

        float partialTicks = event.getTickDelta();
        MatrixStack matrices = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Quaternionf cameraRotation = mc.gameRenderer.getCamera().getRotation();

        if (isShaderRender()) {
            renderShaderBubbles(getCurrentMaskLayer(), matrices, cameraPos, cameraRotation, partialTicks);
            return;
        }

        try {
            VertexConsumer buffer = renderVertexConsumers.getBuffer(getCurrentLayer());
            for (Bubble bubble : bubbles) {
                bubble.renderTextured(matrices, buffer, cameraPos, cameraRotation);
            }
            renderVertexConsumers.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void renderShaderBubbles(RenderLayer maskLayer, MatrixStack matrices, Vec3d cameraPos, Quaternionf cameraRotation, float partialTicks) {
        ShaderThemePreset preset = ShaderThemePreset.byName(shaderTheme.get());
        MaskedCosmosRenderer.getInstance().render(
                immediate -> {
                    VertexConsumer maskBuffer = immediate.getBuffer(maskLayer);
                    for (Bubble bubble : bubbles) {
                        bubble.renderMask(matrices, maskBuffer, cameraPos, cameraRotation);
                    }
                },
                34.0f * preset.densityScale(),
                0.24f * preset.speedScale(),
                0.88f * preset.intensityScale(),
                preset.primaryColor(),
                preset.accentColor(),
                0.34f * preset.edgeScale(),
                preset.themeIndex(),
                preset.patternScale(),
                preset.sparkleScale(),
                preset.starMix(),
                preset.isPulse() ? 0.82f : 1.0f,
                false
        );
    }

    private void migrateLegacyShaderSelection() {
        if (!ShaderThemeVisuals.isShaderMode(textureMode.get())) {
            return;
        }

        textureMode.currentMode = "Bubble1";
        renderMode.currentMode = "Shader";
    }

    private RenderLayer getCurrentLayer() {
        return textureMode.get().equalsIgnoreCase("Bubble2") ? BUBBLE2_LAYER : BUBBLE1_LAYER;
    }

    private RenderLayer getCurrentMaskLayer() {
        return textureMode.get().equalsIgnoreCase("Bubble2") ? BUBBLE2_MASK_LAYER : BUBBLE1_MASK_LAYER;
    }

    private static final RenderLayer BUBBLE1_LAYER = createLayer(BUBBLE1_TEXTURE);
    private static final RenderLayer BUBBLE2_LAYER = createLayer(BUBBLE2_TEXTURE);
    private static final RenderLayer BUBBLE1_MASK_LAYER = createMaskLayer(BUBBLE1_TEXTURE);
    private static final RenderLayer BUBBLE2_MASK_LAYER = createMaskLayer(BUBBLE2_TEXTURE);

    private final class Bubble {
        private static final long LIFE_TIME_MS = 900L;

        private final Vec3d pos;
        private final long startTime = System.currentTimeMillis();
        private final Animation anim = new Animation();
        private final Animation rotAnim = new Animation();
        private final Vector3f reusableNormal = new Vector3f(0.0f, 0.0f, 1.0f);

        private Bubble(Vec3d pos) {
            this.pos = pos;
            anim.set(0.0);
            anim.run(1.0, LIFE_TIME_MS / 2_000.0, Easings.QUAD_OUT, false);
            rotAnim.set(0.0);
            rotAnim.run(1.0, LIFE_TIME_MS / 1000.0, Easings.QUAD_OUT, true);
        }

        private boolean isDead() {
            return System.currentTimeMillis() - startTime >= LIFE_TIME_MS;
        }

        private void update() {
            anim.update();
            rotAnim.update();
        }

        private float getAlpha() {
            long age = System.currentTimeMillis() - startTime;
            if (age <= 0L || age >= LIFE_TIME_MS) {
                return 0.0f;
            }

            float progress = MathHelper.clamp((float) age / (float) LIFE_TIME_MS, 0.0f, 1.0f);
            float in = MathHelper.clamp(progress / 0.35f, 0.0f, 1.0f);
            float out = MathHelper.clamp((1.0f - progress) / 0.35f, 0.0f, 1.0f);
            return (float) Easings.QUART_OUT.ease(Math.min(in, out));
        }

        private void renderTextured(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, Quaternionf cameraRotation) {
            float alpha = getAlpha();
            if (alpha <= 0.01f) {
                return;
            }

            renderInternal(matrices, buffer, cameraPos, cameraRotation, RenderUtil.ColorUtil.multAlpha(colorSetting.getRGB(), alpha));
        }

        private void renderMask(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, Quaternionf cameraRotation) {
            float alpha = getAlpha();
            if (alpha <= 0.01f) {
                return;
            }

            renderInternal(matrices, buffer, cameraPos, cameraRotation, RenderUtil.ColorUtil.multAlpha(0xFFFFFFFF, alpha));
        }

        private void renderInternal(MatrixStack matrices, VertexConsumer buffer, Vec3d cameraPos, Quaternionf cameraRotation, int color) {
            float scale = 0.9f * (0.7f + anim.get() * 0.6f);

            matrices.push();
            matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
            matrices.multiply(cameraRotation);
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rotAnim.get() * 360.0f));
            matrices.scale(scale, scale, scale);

            MatrixStack.Entry entry = matrices.peek();
            drawTexturedQuad(buffer, entry.getPositionMatrix(), entry.getNormalMatrix(), -0.5f, -0.5f, 1.0f, 1.0f, color);
            matrices.pop();
        }

        private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height, int color) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            reusableNormal.set(0.0f, 0.0f, 1.0f);
            normalMatrix.transform(reusableNormal);
            reusableNormal.normalize();

            float x1 = x;
            float y1 = y;
            float x2 = x + width;
            float y2 = y + height;

            buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).texture(0.0f, 1.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                    .normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
            buffer.vertex(matrix, x2, y1, 0.0f).color(r, g, b, a).texture(1.0f, 1.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                    .normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
            buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a).texture(1.0f, 0.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                    .normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
            buffer.vertex(matrix, x1, y2, 0.0f).color(r, g, b, a).texture(0.0f, 0.0f)
                    .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0)
                    .normal(reusableNormal.x, reusableNormal.y, reusableNormal.z);
        }
    }
}
