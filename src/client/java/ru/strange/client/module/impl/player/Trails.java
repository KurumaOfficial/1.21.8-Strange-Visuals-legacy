package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;

import java.io.IOException;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.animation.util.Animation;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.render.RenderUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@IModule(
        name = "Шлейф",
        description = "Шлейф частиц за игроком",
        category = Category.Player,
        bind = -1
)
public class Trails extends Module {
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;

    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));
    public static ModeSetting colorMode = new ModeSetting("Режим цвета", "Client", "Client", "Random", "Astolfo");
    public static SliderSetting dashLength = new SliderSetting("Длина", 0.75f, 0.5f, 1.5f, 0.01f, false);
    public static SliderSetting dashSize = new SliderSetting("Размер", 8.0f, 5.0f, 10.0f, 0.1f, false);
    public static SliderSetting moveLerp = new SliderSetting("Сглаживание", 0.2f, 0.1f, 0.5f, 0.01f, false);
    public static BooleanSetting lighting = new BooleanSetting("Свечение", true);
    public static BooleanSetting drawInFirstPerson = new BooleanSetting("От первого лица", true);

    public static BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false);
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());

    public Trails() {
        addSettings(colorSetting, colorMode, shaderColors, shaderTheme, dashLength, dashSize, moveLerp, lighting, drawInFirstPerson);
    }

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final String PIPELINE_NAMESPACE = "strange";
    
    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.ADDITIVE)
                    .build()
    );

    private static final Map<Identifier, RenderLayer> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
    private static final Identifier DASH_CUBIC_BLOOM_TEX = Identifier.of("strange", "textures/world/dashbloom.png");
    private static final List<ResourceLocationWithSizes> DASH_CUBIC_TEXTURES = new ArrayList<>();
    private static final List<List<ResourceLocationWithSizes>> DASH_CUBIC_ANIMATED_TEXTURES = new ArrayList<>();
    
    private final Random RANDOM = Random.create();
    private final List<DashCubic> DASH_CUBICS = new ArrayList<>();
    private final List<DashCubic> renderCubics = new ArrayList<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    
    private double prevPosXP;
    private double prevPosYP;
    private double prevPosZP;
    private boolean hasPreviousPosition;
    
    private final Animation lightingAnimation = new Animation();

    static {
        addAll_DASH_CUBIC_TEXTURES();
        addAll_DASH_CUBIC_ANIMATED_TEXTURES();
    }

    public static void addAll_DASH_CUBIC_TEXTURES() {
        if (!DASH_CUBIC_TEXTURES.isEmpty()) {
            DASH_CUBIC_TEXTURES.clear();
        }
        int dashTexturesCount = 21;
        for (int ct = 1; ct <= dashTexturesCount; ct++) {
            Identifier resourceLocation = Identifier.of("strange", "textures/world/dash_cubes/dashcubic" + ct + ".png");
            DASH_CUBIC_TEXTURES.add(new ResourceLocationWithSizes(resourceLocation));
        }
    }

    public static void addAll_DASH_CUBIC_ANIMATED_TEXTURES() {
        if (!DASH_CUBIC_ANIMATED_TEXTURES.isEmpty()) {
            DASH_CUBIC_ANIMATED_TEXTURES.clear();
        }
        int[] dashGroupsNumber = new int[]{11, 23, 32, 16, 32};
        for (int packageNumber = 0; packageNumber < dashGroupsNumber.length; packageNumber++) {
            ArrayList<ResourceLocationWithSizes> animatedTexturesList = new ArrayList<>();
            for (int fragNumber = 1; fragNumber <= dashGroupsNumber[packageNumber]; fragNumber++) {
                Identifier resourceLocation = Identifier.of("strange", "textures/world/dash_cubes/group_dashs/group" + (packageNumber + 1) + "/dashcubic" + fragNumber + ".png");
                animatedTexturesList.add(new ResourceLocationWithSizes(resourceLocation));
            }
            if (!animatedTexturesList.isEmpty()) {
                DASH_CUBIC_ANIMATED_TEXTURES.add(animatedTexturesList);
            }
        }
    }

    @Override
    public void toggle() {
        super.toggle();
        resetTrailState();
    }

    @Override
    public void onDisable() {
        resetTrailState();
        super.onDisable();
    }

    @EventInit
    public void onEvent(EventChangeWorld event) {
        resetTrailState();
    }

    @EventInit
    public void onUpdate(EventMotion e) {
        if (mc.player == null || mc.world == null) {
            resetTrailState();
            return;
        }

        if (lighting.get()) {
            lightingAnimation.run(1, 0.5, ru.strange.client.utils.animation.util.Easings.CUBIC_OUT, true);
        } else {
            lightingAnimation.run(0, 0.5, ru.strange.client.utils.animation.util.Easings.CUBIC_OUT, true);
        }
        
        if (lightingAnimation.isAlive()) {
            lightingAnimation.update();
        }

        // Удаляем старые кубики
        long now = System.currentTimeMillis();
        for (int i = DASH_CUBICS.size() - 1; i >= 0; --i) {
            if (DASH_CUBICS.get(i).isDead(now)) {
                DASH_CUBICS.remove(i);
            }
        }

        if (!hasPreviousPosition) {
            prevPosXP = mc.player.getX();
            prevPosYP = mc.player.getY();
            prevPosZP = mc.player.getZ();
            hasPreviousPosition = true;
            return;
        }

        // Обновление позиций для сглаживания
        prevPosXP = MathHelper.lerp(prevPosXP, mc.player.getX(), moveLerp.get());
        prevPosYP = MathHelper.lerp(prevPosYP, mc.player.getY(), moveLerp.get());
        prevPosZP = MathHelper.lerp(prevPosZP, mc.player.getZ(), moveLerp.get());
        
        onEntityMove(mc.player, new Vec3d(prevPosXP, prevPosYP, prevPosZP));
    }

    public void onEntityMove(Entity baseIn, Vec3d prev) {
        if (!(baseIn instanceof LivingEntity)) return;
        
        LivingEntity entity = (LivingEntity) baseIn;
        Vec3d pos = entity.getPos();
        double dx = pos.x - prev.x;
        double dy = pos.y - prev.y;
        double dz = pos.z - prev.z;
        double entitySpeedXZSq = dx * dx + dz * dz;
        
        if (entitySpeedXZSq < 0.0064) {
            return;
        }

        boolean animated = true;
        double entitySpeed = Math.sqrt(entitySpeedXZSq + dy * dy);
        int countMax = (int) MathHelper.clamp((int)(entitySpeed / 0.08), 1, 16);
        if (Optimization.shouldLimitWorldEffects()) {
            countMax = Math.min(countMax, 6);
        }
        
        for (int count = 0; count < countMax; ++count) {
            DASH_CUBICS.add(new DashCubic(
                new DashBase(entity, 0.04f, new DashTexture(animated), (float)count / (float)countMax, getRandomTimeAnimationPerTime())
            ));
        }
    }

    private int getRandomTimeAnimationPerTime() {
        return (int)((float)(550 + RANDOM.nextInt(300)) * dashLength.get());
    }

    private int getColorDashCubic() {
        if (shaderColors.get()) {
            return ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), System.currentTimeMillis() * 0.002);
        }
        return switch (colorMode.get()) {
            case "Random" -> Color.getHSBColor((float) RANDOM.nextInt(255) / 255.0f, 1.0f, 1.0f).getRGB();
            case "Astolfo" -> Color.getHSBColor((float) (System.currentTimeMillis() % 1000L) / 1000.0F, 0.8F, 1.0F).getRGB();
            default -> colorSetting.getRGB();
        };
    }

    private void collectRenderableCubics(long now) {
        renderCubics.clear();
        for (int i = 0; i < DASH_CUBICS.size(); ++i) {
            DashCubic dashCubic = DASH_CUBICS.get(i);
            if (dashCubic != null && dashCubic.getAlpha(now) > 0.01f) {
                renderCubics.add(dashCubic);
            }
        }
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON && !drawInFirstPerson.get()) return;

        float partialTicks = event.getTickDelta();
        long frameTimeMs = System.currentTimeMillis();
        collectRenderableCubics(frameTimeMs);
        if (renderCubics.isEmpty()) return;

        MatrixStack matrices = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Quaternionfc cameraRotation = mc.gameRenderer.getCamera().getRotation();

        try {
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            
            float lightingPC = lightingAnimation.get();
            
            // Рендер основных текстур
            for (int i = 0; i < renderCubics.size(); ++i) {
                renderCubics.get(i).drawDash(matrices, renderVertexConsumers, partialTicks, false, lightingPC, frameTimeMs, cameraRotation);
            }
            
            // Рендер bloom эффекта
            if (!Optimization.shouldSkipTrailBloomPass()) {
                for (int i = 0; i < renderCubics.size(); ++i) {
                    renderCubics.get(i).drawDash(matrices, renderVertexConsumers, partialTicks, true, lightingPC, frameTimeMs, cameraRotation);
                }
            }
            
            renderVertexConsumers.draw();
        } finally {
            matrices.pop();
            renderBufferAllocator.clear();
        }
    }

    private void resetTrailState() {
        DASH_CUBICS.clear();
        hasPreviousPosition = false;
        prevPosXP = 0.0;
        prevPosYP = 0.0;
        prevPosZP = 0.0;
    }

    private static class ResourceLocationWithSizes {
        private final Identifier source;
        private final int[] resolution;

        private ResourceLocationWithSizes(Identifier source) {
            this.source = source;
            this.resolution = getTextureResolution(source);
        }

        private static int[] getTextureResolution(Identifier location) {
            try (InputStream stream = mc.getResourceManager()
                    .getResource(location)
                    .orElseThrow()
                    .getInputStream()) {
                BufferedImage image = ImageIO.read(stream);
                if (image == null) {
                    return new int[]{16, 16};
                }
                return new int[]{image.getWidth(), image.getHeight()};
            } catch (IOException | RuntimeException e) {
                return new int[]{16, 16}; // Дефолтное разрешение
            }
        }

        public Identifier getResource() {
            return source;
        }

        public int[] getResolution() {
            return resolution;
        }
    }

    private class DashCubic {
        private final long startTime = System.currentTimeMillis();
        private final DashBase base;
        private final int color = getColorDashCubic();

        private DashCubic(DashBase base) {
            this.base = base;
        }
        
        private float getAlpha(long now) {
            float timePC = getTimePC(now);
            
            // Плавное появление в первые 10%
            if (timePC < 0.1f) {
                return timePC / 0.1f;
            }
            
            // Плавное исчезание в последние 20%
            if (timePC > 0.8f) {
                return 1.0f - ((timePC - 0.8f) / 0.2f);
            }
            
            return 1.0f;
        }

        private double getRenderPosX(float pTicks) {
            return base.prevPosX + (base.posX - base.prevPosX) * pTicks;
        }

        private double getRenderPosY(float pTicks) {
            return base.prevPosY + (base.posY - base.prevPosY) * pTicks;
        }

        private double getRenderPosZ(float pTicks) {
            return base.prevPosZ + (base.posZ - base.prevPosZ) * pTicks;
        }

        private float getTimePC(long now) {
            return (float)(now - startTime) / (float)base.rMTime;
        }

        private boolean isDead(long now) {
            return getTimePC(now) >= 1.0f;
        }

        private void drawDash(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, float partialTicks, boolean isBloomRenderer, float lightingPC, long frameTimeMs, Quaternionfc cameraRotation) {
            ResourceLocationWithSizes textureSized = base.dashTexture.getResourceWithSizes(frameTimeMs);
            if (textureSized == null) return;

            float aPC = getAlpha(frameTimeMs);
            if (aPC < 0.01f) return; // Не рендерим если почти невидимо
            
            // Конвертируем значение от 5-10 в 0.05-0.1
            float scale = (dashSize.get() / 100.0f) * aPC;
            float extX = (float)textureSized.getResolution()[0] * scale;
            float extY = (float)textureSized.getResolution()[1] * scale;
            
            double renderX = getRenderPosX(partialTicks);
            double renderY = getRenderPosY(partialTicks);
            double renderZ = getRenderPosZ(partialTicks);

            matrices.push();
            matrices.translate(renderX, renderY, renderZ);
            
            // Поворот к камере
            matrices.multiply(cameraRotation);
            matrices.scale(-0.1f, -0.1f, 0.1f);

            MatrixStack.Entry entry = matrices.peek();
            Matrix4f matrix4f = entry.getPositionMatrix();
            Matrix3f normalMatrix = entry.getNormalMatrix();

            if (isBloomRenderer) {
                // Рендер bloom
                RenderLayer bloomLayer = getRenderLayer(DASH_CUBIC_BLOOM_TEX);
                VertexConsumer buffer = immediate.getBuffer(bloomLayer);
                
                float extXY = (float)Math.sqrt(extX * extX + extY * extY);
                float timePcOf = 1.0f - getTimePC(frameTimeMs);
                timePcOf = Math.max(0, Math.min(1, timePcOf));
                
                int bloomColor = RenderUtil.ColorUtil.multAlpha(color, 0.2f * aPC);
                drawTexturedQuad(buffer, matrix4f, normalMatrix, -extXY / 1.75f, -extXY / 1.75f, extXY / 0.875f, extXY / 0.875f, bloomColor);
                
                if (lightingPC != 0.0f) {
                    float aMul = aPC * lightingPC;
                    extXY *= 1.0f + 6.0f * timePcOf * aMul;
                    int lightColor = RenderUtil.ColorUtil.multAlpha(RenderUtil.ColorUtil.multDark(color, aMul / 4.0f), 0.35f * aMul);
                    drawTexturedQuad(buffer, matrix4f, normalMatrix, -extXY / 2.0f, -extXY / 2.0f, extXY, extXY, lightColor);
                }
            } else {
                // Рендер основной текстуры
                RenderLayer textureLayer = getRenderLayer(textureSized.getResource());
                VertexConsumer buffer = immediate.getBuffer(textureLayer);
                
                int textureColor = RenderUtil.ColorUtil.multAlpha(RenderUtil.ColorUtil.multDark(color, aPC), aPC);
                drawTexturedQuad(buffer, matrix4f, normalMatrix, -extX / 2.0f, -extY / 2.0f, extX, extY, textureColor);
            }

            matrices.pop();
        }

        private RenderLayer getRenderLayer(Identifier texture) {
            return RENDER_LAYER_CACHE.computeIfAbsent(texture, tex -> 
                RenderLayer.of(
                    tex.toString(),
                    QUAD_BUFFER_SIZE_BYTES,
                    false,
                    true,
                    TEXTURED_QUADS_PIPELINE,
                    RenderLayer.MultiPhaseParameters.builder()
                            .texture(new RenderPhase.Texture(tex, false))
                            .build(false)
                )
            );
        }

        private static final Vector3f REUSABLE_NORMAL = new Vector3f(0, 0, 1);

        private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height, int color) {
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            REUSABLE_NORMAL.set(0, 0, 1);
            normalMatrix.transform(REUSABLE_NORMAL);
            REUSABLE_NORMAL.normalize();

            float x1 = x;
            float y1 = y;
            float x2 = x + width;
            float y2 = y + height;

            buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, a).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
            buffer.vertex(matrix, x2, y1, 0.0f).color(r, g, b, a).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
            buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, a).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
            buffer.vertex(matrix, x1, y2, 0.0f).color(r, g, b, a).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        }
    }

    private class DashBase {
        private final LivingEntity entity;
        private double motionX;
        private double motionY;
        private double motionZ;
        private double posX;
        private double posY;
        private double posZ;
        private double prevPosX;
        private double prevPosY;
        private double prevPosZ;
        private final int rMTime;
        private final DashTexture dashTexture;

        private DashBase(LivingEntity entity, float speedDash, DashTexture dashTexture, float offsetTickPC, int rmTime) {
            this.rMTime = rmTime;
            this.entity = entity;
            
            this.motionX = -(entity.lastX - entity.getX());
            this.motionY = -(entity.lastY - entity.getY());
            this.motionZ = -(entity.lastZ - entity.getZ());
            
            double randomizeVal = 0.7f;
            this.posX = entity.lastX - motionX * offsetTickPC + (Math.random() * 0.175 - 0.0875);
            this.posY = entity.lastY - motionY * offsetTickPC + (entity.getHeight() / (entity.isSwimming() ? 2.4 : 1.0) / 3.0 + entity.getHeight() / (entity.isSwimming() ? 2.4 : 1.0) / 4.0 * Math.random() * randomizeVal);
            this.posZ = entity.lastZ - motionZ * offsetTickPC + (Math.random() * 0.175 - 0.0875);
            
            this.prevPosX = this.posX;
            this.prevPosY = this.posY;
            this.prevPosZ = this.posZ;
            
            this.motionX *= speedDash;
            this.motionY *= speedDash;
            this.motionZ *= speedDash;
            
            this.dashTexture = dashTexture;
        }
    }

    private class DashTexture {
        private final List<ResourceLocationWithSizes> frames;
        private final ResourceLocationWithSizes singleTexture;
        private final boolean animated;
        private final long timeAfterSpawn;
        private final long animationPerTime;

        private DashTexture(boolean animated) {
            // Как в оригинале: 60% шанс анимированной текстуры
            boolean shouldAnimate = animated && RANDOM.nextInt(100) > 40;
            this.animated = shouldAnimate;
            
            if (this.animated) {
                this.timeAfterSpawn = System.currentTimeMillis();
                
                // Выбираем случайную группу анимаций из всех доступных
                if (!DASH_CUBIC_ANIMATED_TEXTURES.isEmpty()) {
                    int randomGroup = RANDOM.nextInt(DASH_CUBIC_ANIMATED_TEXTURES.size());
                    this.frames = DASH_CUBIC_ANIMATED_TEXTURES.get(randomGroup);
                } else {
                    this.frames = DASH_CUBIC_TEXTURES;
                }
                this.singleTexture = null;
                this.animationPerTime = getRandomTimeAnimationPerTime();
            } else {
                this.frames = null;
                
                // Выбираем случайную статичную текстуру из основных
                this.singleTexture = DASH_CUBIC_TEXTURES.isEmpty()
                        ? null
                        : DASH_CUBIC_TEXTURES.get(RANDOM.nextInt(DASH_CUBIC_TEXTURES.size()));
                
                this.timeAfterSpawn = 0;
                this.animationPerTime = 0;
            }
        }

        private ResourceLocationWithSizes getResourceWithSizes(long now) {
            if (animated && frames != null && !frames.isEmpty()) {
                int timeOfSpawn = (int)(now - timeAfterSpawn);
                float timePC = (float)(timeOfSpawn % (int)animationPerTime) / (float)animationPerTime;
                int fragCount = frames.size();
                int fragNumber = (int) MathHelper.clamp(timePC * fragCount, 0.0f, fragCount - 1);
                return frames.get(fragNumber);
            }
            return singleTexture;
        }
    }
}
