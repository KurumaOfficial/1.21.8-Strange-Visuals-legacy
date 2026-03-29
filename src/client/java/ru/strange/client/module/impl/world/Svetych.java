package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.math.TimerUtil;
import ru.strange.client.utils.math.animation.Animation;
import ru.strange.client.utils.math.animation.Direction;
import ru.strange.client.utils.math.animation.impl.EaseInOutQuad;
import ru.strange.client.utils.render.Render3D;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@IModule(
        name = "Кубики",
        description = "Летающие кубики жоско",
        category = Category.World,
        bind = -1
)
public class Svetych extends Module {
    private final List<Particle> particles = new ArrayList<>();
    private final TimerUtil.satosTime timer = new TimerUtil.satosTime();
    private static final Identifier GLOW_TEXTURE_C = Identifier.of("strange", "textures/world/dashbloom.png");
    private static final Identifier GLOW_TEXTURE_G = Identifier.of("strange", "textures/world/dashbloomsample.png");
    public static SliderSetting cube = new SliderSetting("Кол кубиков", 100, 50, 300, 1, false);
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));

    public Svetych() {
        addSettings(cube, colorSetting);
    }

    @EventInit
    public void onUpdate(EventMotion e) {
        if (mc.player == null || mc.world == null) return;
        if (particles.size() < cube.get() && timer.hasReached(200L)) {
            particles.add(new Particle(mc.player.getPos(), mc.player.getHeight()));
            timer.reset();
        }
    }

    @EventInit
    public void onRender3D(EventRender3D e) {
        if (particles.isEmpty()) return;
        VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = e.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        long now = System.currentTimeMillis();

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        float rotation = (now % 9000L) / 9000f * 360f;

        int baseColor = colorSetting.getRGB();

        java.util.Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.update(now);
            if (p.shouldRemove()) {
                iterator.remove();
            }
        }

        for (Particle p : particles) {
//            if (!RenderUtil.isInFieldOfView(mc, p.x, p.y, p.z)) continue;
            p.render(matrices, immediate, cameraPos, now, baseColor, rotation, cameraYaw, cameraPitch);
        }
        immediate.draw();
    }

    public static class Particle {
        double x, y, z, mX, mY, mZ;
        long start;
        float phase;
        Animation animation = new EaseInOutQuad(1200, 1);
        float cachedAlpha = 1.0f;
        long lastAlphaUpdate = 0;
        private static final MinecraftClient mc = MinecraftClient.getInstance();
        private static final long ALPHA_CACHE_DURATION = 16;

        public Particle(Vec3d pos, float h) {
            this.start = System.currentTimeMillis();
            this.phase = (float) (Math.random() * 100.0);
            double radius = 2.0 + Math.random() * 3.0;
            double angle = Math.random() * Math.PI * 2.0;
            this.x = pos.x + Math.cos(angle) * radius;
            this.z = pos.z + Math.sin(angle) * radius;
            this.y = pos.y + 2.0 + (Math.random() * (h + 2.0));
            this.mX = (Math.random() - 0.5) * 0.001;
            this.mY = (Math.random() - 0.5) * 0.001;
            this.mZ = (Math.random() - 0.5) * 0.001;
            animation.setDirection(Direction.FORWARDS);
            animation.reset();
        }

        public void update(long now) {
            if (mc.world == null) return;

            double velMagSq = mX * mX + mY * mY + mZ * mZ;
            if (velMagSq > 0.0001) {
                if (isHit(x + mX, y, z)) {
                    mX *= -0.8;
                } else {
                    x += mX;
                }
                if (isHit(x, y + mY, z)) {
                    mY *= -0.8;
                } else {
                    y += mY;
                }
                if (isHit(x, y, z + mZ)) {
                    mZ *= -0.8;
                } else {
                    z += mZ;
                }
            } else {
                x += mX;
                y += mY;
                z += mZ;
            }

            mX *= 1.0;
            mY *= 1.0;
            mZ *= 1.0;

            if (animation.getDirection() != Direction.BACKWARDS && now - start > 10000L) {
                animation.setDirection(Direction.BACKWARDS);
            }

            if (now - lastAlphaUpdate > ALPHA_CACHE_DURATION) {
                cachedAlpha = (float) animation.getOutput();
                lastAlphaUpdate = now;
            }
        }

        public float getAlpha() {
            return cachedAlpha;
        }

        private boolean isHit(double px, double py, double pz) {
            BlockPos pos = BlockPos.ofFloored(px, py, pz);
            return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
        }

        public boolean shouldRemove() {
            return animation.getDirection() == Direction.BACKWARDS && cachedAlpha <= 0;
        }

        public void render(MatrixStack matrices, VertexConsumerProvider immediate, Vec3d cameraPos, long time,
                           int baseColor, float rotation, float cameraYaw, float cameraPitch) {
            float alpha = getAlpha();
            if (alpha <= 0) return;

            float alpha02 = alpha * 0.2f;
            float alpha04 = alpha * 0.4f;
            int glowCol = RenderUtil.ColorUtil.multAlpha(baseColor, alpha);
            float cubeSize = 0.26f;
            float cubeSize6 = cubeSize * 6f;
            float cubeSize2 = cubeSize * 2f;

            float relX = (float)(x - cameraPos.x);
            float relY = (float)(y - cameraPos.y);
            float relZ = (float)(z - cameraPos.z);

            float rotY = rotation + phase;
            float rotX = rotation * 0.5f;

            matrices.push();
            matrices.translate(relX, relY, relZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            Matrix4f mat = matrices.peek().getPositionMatrix();

            Render3D.drawCube(immediate.getBuffer(COLOR_QUADS_LAYER), mat, RenderUtil.ColorUtil.multAlpha(baseColor, alpha02), cubeSize);
            Render3D.drawCubeLines(immediate.getBuffer(COLOR_LINES_LAYER), mat, RenderUtil.ColorUtil.multAlpha(baseColor, alpha04), cubeSize);
            matrices.pop();

            matrices.push();
            matrices.translate(relX, relY, relZ);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
            Matrix4f gMat = matrices.peek().getPositionMatrix();

            Render3D.drawGlow(immediate.getBuffer(GLOW_LAYER), gMat, glowCol, (int)(80 * alpha), cubeSize6);
            Render3D.drawGlow(immediate.getBuffer(GLOW_LAYER_G), gMat, glowCol, (int)(140 * alpha), cubeSize2);
            matrices.pop();
        }
    }

    private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET).withLocation(Identifier.of("strange", "svetych_phys_color")).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS).withCull(false).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build());
    private static final RenderLayer COLOR_QUADS_LAYER = RenderLayer.of("svetych_phys_cube", 1024, false, true, COLOR_PIPELINE, RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET).withLocation(Identifier.of("strange", "svetych_lines")).withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES).withCull(false).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build());
    private static final RenderLayer COLOR_LINES_LAYER = RenderLayer.of("svetych_lines", 1024, false, true, LINES_PIPELINE, RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET).withLocation(Identifier.of("strange", "svetych_phys_glow")).withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS).withCull(false).withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withBlend(BlendFunction.LIGHTNING).build());
    private static final RenderLayer GLOW_LAYER = RenderLayer.of("svetych_phys_glow", 1024, false, true, GLOW_PIPELINE, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(GLOW_TEXTURE_C, false)).build(false));
    private static final RenderLayer GLOW_LAYER_G = RenderLayer.of("svetych_phys_glow_g", 1024, false, true, GLOW_PIPELINE, RenderLayer.MultiPhaseParameters.builder().texture(new RenderPhase.Texture(GLOW_TEXTURE_G, false)).build(false));
}