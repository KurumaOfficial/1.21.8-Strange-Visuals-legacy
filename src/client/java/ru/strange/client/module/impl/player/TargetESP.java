package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.math.animation.anim.util.Easings;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.math.Mathf;
import ru.strange.client.utils.math.animation.Animation;
import ru.strange.client.utils.math.animation.Direction;
import ru.strange.client.utils.math.animation.anim.util.Animation2;
import ru.strange.client.utils.math.animation.anim.util.Easing;
import ru.strange.client.utils.math.animation.impl.EaseInOutQuad;
import ru.strange.client.utils.render.Render3D;
import ru.strange.client.utils.render.RenderUtil.ColorUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

@IModule(
        name = "Таргет рендер",
        description = "Рендер текущей цели",
        category = Category.Player,
        bind = -1
)
public class TargetESP extends Module {

    public static ModeSetting typeTargetEsp = new ModeSetting("Режим","Картинка", "Картинка","Призраки","Кубики");

    public static ModeSetting typeGhost = new ModeSetting("Режим призраков","Обычный","Обычный","Новый","Старый").hidden(() -> !typeTargetEsp.is("Призраки"));

    public static ModeSetting typeCube = new ModeSetting("Режим кубиков","Новый","Новый","Старый").hidden(() -> !typeTargetEsp.is("Кубики"));

    public static SliderSetting imageSize = new SliderSetting("Размер картинки", 1.5f, 0.5f, 3.0f, 0.1f, false).hidden(() -> !typeTargetEsp.is("Картинка"));

    public static SliderSetting cubeSize = new SliderSetting("Размер кубиков", 0.19f, 0.1f, 0.5f, 0.01f, false).hidden(() -> !typeTargetEsp.is("Кубики") || !typeCube.is("Новый"));

    public static SliderSetting cubeCount = new SliderSetting("Количество кубиков", 24, 12, 48, 1, false).hidden(() -> !typeTargetEsp.is("Кубики") || !typeCube.is("Новый"));

    public static SliderSetting oldCubeSize = new SliderSetting("Размер кубиков", 0.12f, 0.05f, 0.3f, 0.01f, false).hidden(() -> !typeTargetEsp.is("Кубики") || !typeCube.is("Старый"));

    public static SliderSetting oldCubeSpawnRate = new SliderSetting("Скорость спавна", 0.02f, 0.01f, 0.1f, 0.01f, false).hidden(() -> !typeTargetEsp.is("Кубики") || !typeCube.is("Старый"));

    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));


    public static SliderSetting ghostCount = new SliderSetting("Ghost Count", 12, 6, 24, 1, false).hidden(() -> !typeTargetEsp.is("Призраки"));

    public static SliderSetting ghostRadius = new SliderSetting("Ghost Radius", 1.0f, 0.5f, 2.5f, 0.05f, false).hidden(() -> !typeTargetEsp.is("Призраки"));

    public static SliderSetting ghostSpeed = new SliderSetting("Ghost Speed", 1.0f, 0.4f, 2.4f, 0.05f, false).hidden(() -> !typeTargetEsp.is("Призраки"));

    public static SliderSetting ghostTrajectory = new SliderSetting("Ghost Path", 1.0f, 0.35f, 2.2f, 0.05f, false).hidden(() -> !typeTargetEsp.is("Призраки"));

    public static BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false);
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());

    public TargetESP() {
        addSettings(typeTargetEsp,typeGhost,typeCube,imageSize,cubeSize,cubeCount,oldCubeSize,oldCubeSpawnRate,ghostCount,ghostRadius,ghostSpeed,ghostTrajectory,colorSetting,shaderColors,shaderTheme);
    }


    private static final Identifier TARGET_TEXTURE_N = Identifier.of("strange", "textures/world/targetn.png");
    private static final Identifier GLOW_TEXTURE = Identifier.of("strange", "textures/world/glow.png");
    private static final Identifier GLOW_TEXTURE_C = Identifier.of("strange", "textures/world/dashbloom.png");

    public static Animation2 alpha = new Animation2();
    public static Animation2 size = new Animation2();
    private LivingEntity lastTarget = null;
    private static long lastTime = 0;
    private float animationNurik = 0.0F;
    private long currentTimeSpirits = 0;

    private final ArrayList<OldCubeParticle> oldCubeParticles = new ArrayList<>();
    private static long oldCubeLastTime = System.currentTimeMillis();
    private static float oldCubeDeltaTime = 0.0f;
    private static final long OLD_CUBE_LIFE_TIME = 1000L;
    private static final int OLD_CUBE_PARTICLES_PER_SPAWN = 1;
    private static final int MAX_PARTICLES = 50; // Лимит частиц для производительности
    private float oldCubeSpawnAccumulator = 0f;

    @EventInit
    public void onRender(EventRender3D e) {
        alpha.update();

        LivingEntity target = null;
        if (mc.targetedEntity instanceof LivingEntity) {
            target = (LivingEntity) mc.targetedEntity;
        }

        if (mc.world == null || mc.player == null) return;

        alpha.run((double) (target == null || target.isInvisible() ? 0 : 1), 0.35, Easings.QUART_OUT);

        if (alpha.getValue() > 0) {
            if (target != null) {
                if (lastTarget != target) {
                    lastTime = 0;
                    currentTimeSpirits = 0;
                    animationNurik = 0.0F;
                }
                lastTarget = target;
            }

            if (lastTarget != null && !typeTargetEsp.is("Не отображать")) {
                BufferAllocator allocator = new BufferAllocator(1 << 18);
                VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

                try {
                    if (typeTargetEsp.is("Картинка")) {
                        renderDiamondNewStyle(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }
                    if (typeTargetEsp.is("Призраки") && typeGhost.is("Обычный")) {
                        renderGhosts(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }
                    if (typeTargetEsp.is("Призраки")&& typeGhost.is("Новый")) {
                        renderSpirits(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }
                    if (typeTargetEsp.is("Призраки")&& typeGhost.is("Старый")) {
                        renderSpiritsOld(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }
                    if (typeTargetEsp.is("Кубики") && typeCube.is("Новый")) {
                        renderCubes(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }
                    if (typeTargetEsp.is("Кубики") && typeCube.is("Старый")) {
                        renderCubesOld(e.getMatrixStack(), immediate, lastTarget, e.getTickDelta());
                    }

                    immediate.draw();
                } finally {
                    allocator.close();
                }
            }
        } else {
            lastTarget = null;
            lastTime = 0;
            currentTimeSpirits = 0;
            animationNurik = 0.0F;
        }
    }

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;

    private int getThemeColor(double phase) {
        return shaderColors.get()
                ? ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), phase)
                : colorSetting.getRGB();
    }

    private void renderDiamondNewStyle(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        Vec3d lerpedPos = target.getLerpedPos(partialTicks);
        double x = lerpedPos.x;
        double y = lerpedPos.y;
        double z = lerpedPos.z;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        matrices.push();
        matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

        long currentTimeMillis = System.currentTimeMillis();
        float rotate = (float) Mathf.clamp((float) 0, (float) (360 * 2), (float) (((Math.sin(currentTimeMillis / 1600D) + 1F) / 2F) * 360 * 2));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));

        size.update();

        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
        size.run((double) hurtPC, 0.4, (Easing) Easings.QUART_OUT);

        float rzs = size.get();
        float sizePC = (float) alpha.getValue();

        int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
        int colorS = ColorUtil.overCol(ColorUtil.multAlpha(getThemeColor(target.getId() * 0.41 + 0.3), sizePC), redColor, size.get());

        float size = imageSize.get() - 0.9F * sizePC + (0.35F - 0.35F * rzs);
        matrices.scale(size, size, 1.0f);

        RenderLayer renderLayer = TARGET_RENDER_LAYER;

        Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
        VertexConsumer bloomBuffer = immediate.getBuffer(renderLayer);

        drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255 * sizePC));

        matrices.pop();
    }

    private void renderSpirits(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        if (target == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) {
            currentTimeSpirits = currentTime;
        }

        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) {
            animationNurik += ghostSpeed.get() * (float) (5L * timeDiff) / 900.0F;
        }
        currentTimeSpirits = currentTime;

        Vec3d lerpedPos = target.getLerpedPos(partialTicks);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        double x = lerpedPos.x - cameraPos.x;
        double y = lerpedPos.y  - cameraPos.y;
        double z = lerpedPos.z - cameraPos.z;

        float alphaPC = (float) alpha.getValue();

        size.update();
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
        size.run((double) hurtPC, 0.4, (Easing) Easings.QUART_OUT);
        float atts = size.get();

        int fadeColor = getThemeColor(animationNurik * 0.11 + target.getId() * 0.37);
        int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
        int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

        RenderLayer renderLayer = GLOW_RENDER_LAYER;

        int n2 = 3;
        int n3 = Math.max(6, (int) ghostCount.get());
        int n4 = 3 * n2;
        float radiusScale = ghostRadius.get();
        float trajectoryScale = ghostTrajectory.get();

        matrices.push();

        Camera camera = mc.gameRenderer.getCamera();

        for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
                float f2 = animationNurik + (float) j * 0.1F;
                float f3 = 0.75F * radiusScale;
                float f4 = 0.5F * trajectoryScale;
                int n5 = (int) Math.pow((double) i, 2.0F);

                matrices.push();

                // 3D спиральное движение с глубиной
                double spiralRadius = f3 * (1.0 + Math.sin(animationNurik * 0.5 + j * 0.3) * 0.2 * trajectoryScale);
                double particleX = x + (spiralRadius * Math.sin(f2 + (float) n5));
                double particleY = y + (double) f4 + (double) ((0.3F * trajectoryScale) * Math.sin(animationNurik + (float) j * 0.2F)) + (double) (0.2F * (float) i);
                double particleZ = z + (spiralRadius * Math.cos(f2 - (float) n5));

                matrices.translate(particleX, particleY, particleZ);

                // Динамический размер с перспективой
                float depthFactor = (float)(1.0 - (i / (float)n4) * 0.3);
                float scale = (0.005F + (float) j / 2000.0F) * depthFactor;
                matrices.scale(scale, scale, scale);

                // Вращение для 3D эффекта
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(animationNurik * 2 + j * 10));

                Matrix4f matrix = matrices.peek().getPositionMatrix();
                VertexConsumer buffer = immediate.getBuffer(renderLayer);

                // Градиент цвета по глубине
                float depthAlpha = alphaPC * depthFactor;
                int color = ColorUtil.multAlpha(baseColor, depthAlpha);
                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int a = (int) (depthAlpha * 255.0F);

                int n7 = -25;
                int n8 = 50;

                buffer.vertex(matrix, (float) n7, (float) (n7 + n8), 0.0f)
                        .color(r, g, b, a)
                        .texture(0.0F, 1.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, (float) (n7 + n8), (float) (n7 + n8), 0.0f)
                        .color(r, g, b, a)
                        .texture(1.0F, 1.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, (float) (n7 + n8), (float) n7, 0.0f)
                        .color(r, g, b, a)
                        .texture(1.0F, 0.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, (float) n7, (float) n7, 0.0f)
                        .color(r, g, b, a)
                        .texture(0.0F, 0.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                matrices.pop();
            }
        }

        matrices.pop();
    }

    private void renderSpiritsOld(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        if (target == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTimeSpirits == 0) {
            currentTimeSpirits = currentTime;
        }

        long timeDiff = currentTime - currentTimeSpirits;
        if (timeDiff > 0) {
            animationNurik += ghostSpeed.get() * (float) (5L * timeDiff) / 200.0F;
        }
        currentTimeSpirits = currentTime;

        Vec3d lerpedPos = target.getLerpedPos(partialTicks);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        double x = lerpedPos.x - cameraPos.x;
        double y = lerpedPos.y + 1.1F - cameraPos.y;
        double z = lerpedPos.z - cameraPos.z;

        float alphaPC = (float) alpha.getValue();

        RenderLayer renderLayer = GLOW_RENDER_LAYER;

        int espLength = 17 ;
        int factor = 6;
        float shaking = Math.max(0.45F, 1.25F / ghostTrajectory.get());
        float amplitude = 1.1F * ghostTrajectory.get();
        float iAge = animationNurik;

        Camera camera = mc.gameRenderer.getCamera();
        double targetWidth = (target.getWidth() + 0.12F) * ghostRadius.get();
        boolean canSee = mc.player.canSee(target);

        VertexConsumer buffer = immediate.getBuffer(renderLayer);
        size.update();
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
        size.run((double) hurtPC, (double) 0.4, (Easing) Easings.QUART_OUT);
        float atts = size.get();

        int fadeColor = getThemeColor(animationNurik * 0.17 + target.getId() * 0.61);
        int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
        int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
                double radians = Math.toRadians((((float) i / 1.5f + iAge) * factor + (j * 120)) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(iAge * 2 + i * (j + 1)) * amplitude) / shaking;

                float offset = ((float) i / espLength);

                matrices.push();

                matrices.translate(
                        x + Math.cos(radians) * targetWidth,
                        y + sinQuad,
                        z + Math.sin(radians) * targetWidth
                );
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                Matrix4f matrix = matrices.peek().getPositionMatrix();


                int color = ColorUtil.multAlpha(baseColor, offset * alphaPC);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int a = (color >> 24) & 0xFF;

                float scale = Math.max(0.25f * offset, 0.22f);

                buffer.vertex(matrix, -scale, scale, 0.0f)
                        .color(r, g, b, a)
                        .texture(0.0F, 1.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, scale, scale, 0.0f)
                        .color(r, g, b, a)
                        .texture(1.0F, 1.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, scale, -scale, 0.0f)
                        .color(r, g, b, a)
                        .texture(1.0F, 0.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                buffer.vertex(matrix, -scale, -scale, 0.0f)
                        .color(r, g, b, a)
                        .texture(0.0F, 0.0F)
                        .overlay(OverlayTexture.DEFAULT_UV)
                        .light(0xF000F0)
                        .normal(0, 0, 1);

                matrices.pop();
            }
        }
    }


    private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 0, 1);
    }

    private static final String PIPELINE_NAMESPACE = "strange";

    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline COLOR_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/color_quads"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer COLOR_QUADS_LAYER = RenderLayer.of(
            "strange_color_quads",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            COLOR_QUADS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderPipeline CUBE_LINES_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "targetesp_cube_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer CUBE_LINES_LAYER = RenderLayer.of(
            "targetesp_cube_lines",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            CUBE_LINES_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer TARGET_RENDER_LAYER = RenderLayer.of(
            TARGET_TEXTURE_N.toString(),
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            TEXTURED_QUADS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(TARGET_TEXTURE_N, false))
                    .build(false)
    );

    private static final RenderLayer GLOW_RENDER_LAYER = RenderLayer.of(
            GLOW_TEXTURE.getNamespace(),
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            TEXTURED_QUADS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(GLOW_TEXTURE, false))
                    .build(false)
    );

    private static final RenderLayer GLOW_C_RENDER_LAYER = RenderLayer.of(
            GLOW_TEXTURE_C.getNamespace(),
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            TEXTURED_QUADS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .texture(new RenderPhase.Texture(GLOW_TEXTURE_C, false))
                    .build(false)
    );


    private void renderGhosts(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (target == null) {
            return;
        }
        double radius = (0.3 + target.getWidth() / 2) * ghostRadius.get();

        size.update();
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));


        size.run((double) hurtPC, (double) 0.4, (Easing) Easings.QUART_OUT);

        float atts = size.get();

        float speed = 30 / Math.max(0.2f, ghostSpeed.get());
        float size = 0.4F - 0.1F * atts;
        double distance = (6 - (int) (1 * atts)) * ghostTrajectory.get();
        int length = Math.max(16, (int) (ghostCount.get() * 3) - (int) (8 * atts));

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Camera camera = mc.gameRenderer.getCamera();

        if (lastTime == 0) {
            lastTime = System.currentTimeMillis();
        }
        long currentTime = System.currentTimeMillis();


        Vec3d interpolated = target.getLerpedPos(partialTicks);
        interpolated = new Vec3d(interpolated.x, interpolated.y + 0.32 + target.getHeight() / 2, interpolated.z);

        double posX = interpolated.x + 0.2;
        double posY = interpolated.y;
        double posZ = interpolated.z;

        RenderLayer renderLayer = GLOW_RENDER_LAYER;
        VertexConsumer buffer = immediate.getBuffer(renderLayer);

        float sizePC = (float) alpha.getValue();
        int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));



        int fadeColor = getThemeColor(currentTime * 0.001 + target.getId() * 0.23);
        int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, sizePC), redColor, atts);

        int color1 = baseColor;
        int color2 = ColorUtil.multDark(baseColor, 0.8f);
        int color3 = ColorUtil.multDark(baseColor, 0.6f);
        int color4 = ColorUtil.multDark(baseColor, 0.4f);

        matrices.push();

        matrices.translate(posX - cameraPos.x, posY - cameraPos.y, posZ - cameraPos.z);

        float sfz = 0.3F * ghostTrajectory.get();

        for (int i = 0; i < length; i++) {
            double angle = 0.05f * (currentTime - lastTime - (i * distance)) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.cos(angle * Math.PI) * radius;

            float t = i / (float) (length - 1);
            float scale = 1.0f - t * sfz;
            float curSize = size * scale;

            matrices.push();
            matrices.translate(s, o, -c);
            matrices.translate(-curSize / 2f, -curSize / 2f, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2f, curSize / 2f, 0);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);

            matrices.pop();
        }


        for (int i = 0; i < length; i++) {
            double angle = 0.05f * (currentTime - lastTime - (i * distance)) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.sin(angle * Math.PI) * radius;

            float t = i / (float) (length - 1);
            float scale = 1.0f - t * sfz;
            float curSize = size * scale;

            matrices.push();
            matrices.translate(-s, o, -c);
            matrices.translate(-curSize / 2f, -curSize / 2f, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2f, curSize / 2f, 0);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);

            matrices.pop();
        }


        for (int i = 0; i < length; i++) {
            double angle = 0.05f * (currentTime - lastTime - (i * distance)) / speed;
            double s = Math.sin(angle * Math.PI) * radius;
            double c = Math.cos(angle * Math.PI) * radius;
            double o = Math.sin(angle * Math.PI) * radius;

            float t = i / (float) (length - 1);
            float scale = 1.0f - t * sfz;
            float curSize = size * scale;

            matrices.push();
            matrices.translate(s, o, c);
            matrices.translate(-curSize / 2f, -curSize / 2f, 0);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.translate(curSize / 2f, curSize / 2f, 0);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawTexturedQuad(buffer, matrix, color1, color2, color3, color4, curSize);

            matrices.pop();
        }

        matrices.pop();
    }

    private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, int color1, int color2, int color3, int color4, float size) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        int r3 = (color3 >> 16) & 0xFF;
        int g3 = (color3 >> 8) & 0xFF;
        int b3 = color3 & 0xFF;
        int a3 = (color3 >> 24) & 0xFF;

        int r4 = (color4 >> 16) & 0xFF;
        int g4 = (color4 >> 8) & 0xFF;
        int b4 = color4 & 0xFF;
        int a4 = (color4 >> 24) & 0xFF;

        buffer.vertex(matrix, 0, -size, 0).texture(0, 0).color(r1, g1, b1, a1);
        buffer.vertex(matrix, -size, -size, 0).texture(0, 1).color(r2, g2, b2, a2);
        buffer.vertex(matrix, -size, 0, 0).texture(1, 1).color(r3, g3, b3, a3);
        buffer.vertex(matrix, 0, 0, 0).texture(1, 0).color(r4, g4, b4, a4);
    }

    private void renderCubes(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        if (target == null) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        long time = System.currentTimeMillis();

        int count = (int)cubeCount.get();
        double radius =  0.4 + target.getWidth() / 2 + 0.35F - 0.35F * alpha.get();;
        double heightRange = target.getHeight();

        Vec3d lerpedPos = target.getLerpedPos(partialTicks);

        float alphaPC = (float) alpha.getValue();

        size.update();
        int hurtTicks = target.hurtTime;
        float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
        size.run((double) hurtPC, 0.4, (Easing) Easings.QUART_OUT);
        float atts = size.get();

        int redColor = ColorUtil.getColor(200, 70, 70, (int) (60 * alphaPC));
        int fadeColor = getThemeColor(time * 0.0017 + target.getId() * 0.47);
        int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35f);


        int color = ColorUtil.overCol(baseColor, redColor, atts);
        int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                ColorUtil.getColor(200, 100, 100, (int) (255 * alphaPC)), atts);

        for (int i = 0; i < count; i++) {

            double r1 = Math.sin(i * 132.12 + 4.12);
            double r2 = Math.cos(i * 453.21 + 1.23);
            double r3 = Math.sin(i * 789.34 + 9.87);

            double curRadius = radius;

            double speedFactor = 1.0 ;
            double angleOffset = (Math.PI * 2 / count) * i;

            double timeFactor = (time / 6000.0) * (Math.PI * 2) * speedFactor;
            double angle = timeFactor + angleOffset;

            double x = Math.cos(angle) * curRadius;
            double z = Math.sin(angle) * curRadius;


            double ySpeed = 1.0 + r1 * 0.2;
            double yPhase = angleOffset + r3 * 2;
            double yOffset = Math.sin((time / 9000.0) * (Math.PI * 2) * ySpeed + yPhase) * 0.45 + 0.55;
            double y = yOffset * heightRange;

            double cX = lerpedPos.x + x - cameraPos.x;
            double cY = lerpedPos.y + y - cameraPos.y;
            double cZ = lerpedPos.z + z - cameraPos.z;

            matrices.push();
            matrices.translate(cX, cY, cZ);


            float pulse = 1.0f + 0.15f * (float) Math.sin(time / 400.0 + i * 1.5);
            float cubeSize = this.cubeSize.get() * pulse;

            double hurtFactor = atts * (0.5 + 0.5 * Math.sin(i * 123.45));
            if (hurtFactor > 0.05) {
                cubeSize *= (1.0 - hurtFactor * 0.2);
                double pushOut = hurtFactor * 0.4;
                matrices.translate(Math.cos(angle) * pushOut, 0, Math.sin(angle) * pushOut);
            }

            matrices.push();

            float selfRotSpeed = 12000 + (float)r3 * 2000;
            float selfRot = (time % (long)Math.abs(selfRotSpeed)) / Math.abs(selfRotSpeed) * 360f;

            if (i % 3 == 0) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
            } else if (i % 3 == 1) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
            } else {
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
            }

            VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            Render3D.drawCube(buffer, matrix, ColorUtil.multAlpha(color,0.5F), cubeSize);

            int lineColor = ColorUtil.multAlpha(color, alphaPC * 1);
            VertexConsumer lineBuffer = immediate.getBuffer(CUBE_LINES_LAYER);
            Render3D.drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);

            matrices.pop();


            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

            RenderLayer glowLayer = GLOW_C_RENDER_LAYER;
            VertexConsumer glowBuffer = immediate.getBuffer(glowLayer);
            Matrix4f glowMatrix = matrices.peek().getPositionMatrix();

            float glowSize = cubeSize * 3;
            matrices.scale(glowSize, glowSize, glowSize);

            drawGradientQuad(glowBuffer, glowMatrix, glowCol, (int) (125 * alphaPC));

            matrices.pop();
            matrices.pop();
        }
    }

    private void renderCubesOld(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, LivingEntity target, float partialTicks) {
        if (target == null) {
            // Очищаем частицы если нет таргета
            oldCubeParticles.clear();
            return;
        }

        // Удаляем старые частицы более эффективно
        Iterator<OldCubeParticle> iterator = oldCubeParticles.iterator();
        while (iterator.hasNext()) {
            OldCubeParticle particle = iterator.next();
            if (particle.animation.getDirection() != Direction.FORWARDS && particle.animation.getOutput() <= 0) {
                iterator.remove();
            }
        }

        // Обновляем deltaTime
        long currentTime = System.currentTimeMillis();
        oldCubeDeltaTime = Math.max(0.001f, Math.min(0.1f, (currentTime - oldCubeLastTime) / 1000.0f)); // Ограничиваем deltaTime
        oldCubeLastTime = currentTime;

        // Спавним новые частицы только когда есть таргет и не превышен лимит
        if (oldCubeParticles.size() < MAX_PARTICLES) {
            oldCubeSpawnAccumulator += oldCubeDeltaTime;
            float spawnInterval = oldCubeSpawnRate.get();
            while (oldCubeSpawnAccumulator >= spawnInterval && oldCubeParticles.size() < MAX_PARTICLES) {
                oldCubeSpawnAccumulator -= spawnInterval;
                for (int i = 0; i < OLD_CUBE_PARTICLES_PER_SPAWN && oldCubeParticles.size() < MAX_PARTICLES; i++) {
                    double rand = MathHelper.random(0, 360);
                    double x = Math.cos(rand * Math.PI / 180) * 0.7f;
                    double y = MathHelper.getRandomNumberBetween(0.04F, 0.2f);
                    double z = Math.sin(rand * Math.PI / 180) * 0.7f;
                    oldCubeParticles.add(new OldCubeParticle(target, x, y, z));
                }
            }
        }

        // Обновляем и рендерим частицы
        if (!oldCubeParticles.isEmpty()) {
            float alphaPC = (float) alpha.getValue();

            size.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin((double) hurtTicks * (Math.PI / 20D));
            size.run((double) hurtPC, 0.4, (Easing) Easings.QUART_OUT);
            float atts = size.get();

            int redColor = ColorUtil.getColor(200, 70, 70, (int) (60 * alphaPC));
            int fadeColor = getThemeColor(currentTime * 0.0021 + target.getId() * 0.73);
            int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35f);
            int color = ColorUtil.overCol(baseColor, redColor, atts);
            int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                    ColorUtil.getColor(200, 100, 100, (int) (255 * alphaPC)), atts);

            // Кэшируем данные камеры
            Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            float yaw = mc.gameRenderer.getCamera().getYaw();

            for (OldCubeParticle particle : oldCubeParticles) {
                particle.update(partialTicks);
                particle.render(matrices, immediate, color, glowCol, alphaPC, atts, partialTicks, cameraPos, pitch, yaw, GLOW_C_RENDER_LAYER);
            }
        }
    }

    public static class OldCubeParticle {
        double x, y, z;
        double posX, posY, posZ;
        double motionX, motionY, motionZ;
        long time;
        LivingEntity entity;
        Animation animation = new EaseInOutQuad(500, 1);
        private double velocityY;

        public OldCubeParticle(LivingEntity entity, double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.entity = entity;
            this.time = System.currentTimeMillis();
            this.velocityY = MathHelper.getRandomNumberBetween(0.01f, 0.04f);
        }

        public long getTime() {
            return time;
        }

        public void update(float partialTicks) {
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - this.getTime();

            animation.setDirection((elapsed <= OLD_CUBE_LIFE_TIME - 200L) ? Direction.FORWARDS : Direction.BACKWARDS);

            this.y += velocityY * (oldCubeDeltaTime * 60);

            if (entity != null) {
                Vec3d lerpedPos = entity.getLerpedPos(partialTicks);
                this.motionX = x + lerpedPos.x;
                this.motionY = y + lerpedPos.y;
                this.motionZ = z + lerpedPos.z;
            }
        }

        public void render(MatrixStack matrixStack, VertexConsumerProvider.Immediate immediate, int color, int glowCol, float alphaPC, float atts, float partialTicks, Vec3d cameraPos, float pitch, float yaw, RenderLayer glowLayer) {
            long currentTime = System.currentTimeMillis();
            double rotation = (currentTime - this.getTime()) / 10.0;

            posX = MathHelper.interpolate(posX, this.motionX - cameraPos.x, 0.2f);
            posY = MathHelper.interpolate(posY, this.motionY - cameraPos.y, 0.2f);
            posZ = MathHelper.interpolate(posZ, this.motionZ - cameraPos.z, 0.2f);

            float animOutput = (float) animation.getOutput();
            if (animOutput <= 0) return;

            float pulse = 1.0f + 0.15f * (float) Math.sin((currentTime - this.getTime()) / 400.0);
            float cubeSize = oldCubeSize.get() + 0.04f * animOutput;

            matrixStack.push();
            matrixStack.translate(posX, posY, posZ);

            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation));

            Matrix4f matrix = matrixStack.peek().getPositionMatrix();

            int cubeColor = ColorUtil.multAlpha(color, 0.5F * animOutput);
            VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
            Render3D.drawCube(buffer, matrix, cubeColor, cubeSize);

            int lineColor = ColorUtil.multAlpha(color, alphaPC * animOutput);
            VertexConsumer lineBuffer = immediate.getBuffer(CUBE_LINES_LAYER);
            Render3D.drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);

            matrixStack.pop();

            // Рисуем глоу
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

            VertexConsumer glowBuffer = immediate.getBuffer(glowLayer);
            Matrix4f glowMatrix = matrixStack.peek().getPositionMatrix();

            float glowSize = cubeSize * 3;
            matrixStack.scale(glowSize, glowSize, glowSize);

            int glowColorWithAlpha = ColorUtil.replAlpha(glowCol, (int) (125 * alphaPC * animOutput));
            drawGradientQuad(glowBuffer, glowMatrix, glowColorWithAlpha, (int) ColorUtil.getAlpha(glowColorWithAlpha));

            matrixStack.pop();
            matrixStack.pop();
        }
    }
}
