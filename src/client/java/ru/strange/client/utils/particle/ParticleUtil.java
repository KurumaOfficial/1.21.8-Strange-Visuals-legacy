package ru.strange.client.utils.particle;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import ru.strange.client.utils.animation.util.Animation;
import ru.strange.client.utils.animation.util.Easings;
import ru.strange.client.utils.math.Mathf;
import ru.strange.client.utils.math.StopWatch;
import ru.strange.client.utils.player.PlayerUtil;
import ru.strange.client.utils.render.RenderUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ParticleUtil {
    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final String PIPELINE_NAMESPACE = "strange";
    private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_TEX_COLOR_SNIPPET)
                    .withLocation(Identifier.of(PIPELINE_NAMESPACE, "pipeline/world/textured_quads"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    public static final Map<ParticleType, RenderLayer> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
    private static final Vector3f REUSABLE_NORMAL = new Vector3f(0, 0, 1);

    private static RenderLayer resolveRenderLayer(ParticleType type) {
        return RENDER_LAYER_CACHE.computeIfAbsent(type, particleType -> {
            Identifier texture = particleType.texture();
            return RenderLayer.of(
                    texture.toString(),
                    QUAD_BUFFER_SIZE_BYTES,
                    false,
                    true,
                    TEXTURED_QUADS_PIPELINE,
                    RenderLayer.MultiPhaseParameters.builder()
                            .texture(new RenderPhase.Texture(texture, false))
                            .build(false)
            );
        });
    }

    public static void renderParticle(MatrixStack matrix, VertexConsumer buffer,
                                     Quaternionfc cameraRotation,
                                     Particle particle, float x, float y, float z,
                                     float pos, int color, int alpha) {

        matrix.push();

        matrix.translate(x, y, z);
        matrix.multiply(cameraRotation);

        MatrixStack.Entry entry = matrix.peek();
        Matrix4f matrix4f = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();

        drawTexturedQuad(buffer, matrix4f, normalMatrix,
                -pos, -pos, pos * 2, pos * 2, color, alpha);

        if (particle.type == ParticleType.BLOOM) {
            drawTexturedQuad(buffer, matrix4f, normalMatrix,
                    -pos / 2, -pos / 2, pos, pos, color, alpha);
        }

        matrix.pop();
    }

    private static void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y, float width, float height, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        REUSABLE_NORMAL.set(0, 0, 1);
        normalMatrix.transform(REUSABLE_NORMAL);
        REUSABLE_NORMAL.normalize();

        float x1 = x;
        float y1 = y;
        float x2 = x + width;
        float y2 = y + height;

        buffer.vertex(matrix, x1, y1, 0.0f).color(r, g, b, alpha).texture(0, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x2, y1, 0.0f).color(r, g, b, alpha).texture(1, 1).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x2, y2, 0.0f).color(r, g, b, alpha).texture(1, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
        buffer.vertex(matrix, x1, y2, 0.0f).color(r, g, b, alpha).texture(0, 0).overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
    }

    public static void updateParticles(java.util.List<Particle> particles, long fadeInTime, long fadeOutTime, double deltaTime) {
        if (particles.isEmpty()) return;
        double frameFactor = deltaTime * 60.0;
        double friction = Math.pow(0.999, frameFactor);

        for (Particle particle : particles) {
            particle.update(true, frameFactor, friction);

            boolean notFinishedFadeIn = !particle.time().finished(fadeInTime);
            boolean finishedFadeOut = particle.time().finished(fadeOutTime);

            if (notFinishedFadeIn) {
                particle.animation().run(1, 0.4, Easings.QUAD_OUT, true);
            } else if (finishedFadeOut) {
                particle.animation().run(0, 0.4, Easings.QUAD_OUT, true);
            }

            if (particle.animation.isAlive()) {
                particle.animation.update();
            }
        }
    }

    public static void renderParticles(MatrixStack matrix, VertexConsumerProvider.Immediate immediate, Vec3d cameraPos, java.util.List<Particle> particles) {
        if (particles.isEmpty()) return;

        Quaternionfc cameraRotation = MinecraftClient.getInstance().gameRenderer.getCamera().getRotation();
        ParticleType lastType = null;
        VertexConsumer lastBuffer = null;
        matrix.push();
        for (Particle particle : particles) {
            float animValue = particle.animation.get();
            int alpha = (int) (animValue * 255);
            if (alpha <= 0) continue;

            Vec3d v = particle.position();
            ParticleType type = particle.type();
            if (type != lastType) {
                lastType = type;
                lastBuffer = immediate.getBuffer(resolveRenderLayer(type));
            }

            renderParticle(
                    matrix,
                    lastBuffer,
                    cameraRotation,
                    particle,
                    (float) (v.x - cameraPos.x),
                    (float) (v.y - cameraPos.y),
                    (float) (v.z - cameraPos.z),
                    particle.size,
                    particle.color(),
                    alpha
            );
        }
        matrix.pop();
    }

    public enum ParticleType {
        HEART("heart", false),
        STAR("star", false),
        SNOW("snowflake", false),
        BLOOM("firefly", false),
        DOLLAR("dollar", false),
        TRIANGLE("triangle", false),
        SAKURA("sakura", false),
        GEMINI("genshin", false),
        SIMS("rhombus", false);

        private final Identifier texture;
        private final boolean rotatable;

        ParticleType(String name, boolean rotatable) {
            this.texture = Identifier.of("strange", "textures/world/" + name + ".png");
            this.rotatable = rotatable;
        }

        public Identifier texture() {
            return texture;
        }

        public boolean rotatable() {
            return rotatable;
        }
    }

    public static class Particle {
        private net.minecraft.client.MinecraftClient mc;
        private final ParticleType type;
        private Vec3d position;
        private Vec3d velocity;
        private final int index;
        private final int rotate;
        private final int color;
        private final float size;
        private static final double BASE_VELOCITY = 0.05;
        private final double speedMultiplier;

        private final StopWatch time = new StopWatch();
        private final Animation animation = new Animation();

        public Particle(
                net.minecraft.client.MinecraftClient mc,
                ParticleType type,
                Vec3d position,
                Vec3d velocity,
                int index,
                int rotate,
                int color,
                float size,
                double speedMultiplier
        ) {
            this.mc = mc;
            this.type = type;
            this.position = position;
            this.velocity = velocity.multiply(BASE_VELOCITY);
            this.index = index;
            this.rotate = rotate;
            this.color = color;
            this.size = size;
            this.speedMultiplier = speedMultiplier;

            this.time.reset();
        }

        // ===== GETTERS =====

        public ParticleType type() {
            return type;
        }

        public Vec3d position() {
            return position;
        }

        public Vec3d velocity() {
            return velocity;
        }

        public int index() {
            return index;
        }

        public int rotate() {
            return rotate;
        }

        public int color() {
            return color;
        }

        public float size() {
            return size;
        }

        public double speedMultiplier() {
            return speedMultiplier;
        }

        public StopWatch time() {
            return time;
        }

        public Animation animation() {
            return animation;
        }

        // ===== LOGIC =====

        public void update(boolean physic, double frameFactor, double friction) {
            double velocityX = velocity.x;
            double velocityY = velocity.y;
            double velocityZ = velocity.z;

            if (physic && mc.world != null) {
                double velMagSq =
                        velocityX * velocityX +
                                velocityY * velocityY +
                                velocityZ * velocityZ;

                if (velMagSq > 0.0001) {
                    if (PlayerUtil.isBlockSolid(position.x, position.y, position.z + velocityZ)) {
                        velocityX *= 1.35F;
                        velocityY *= 1.35F;
                        velocityZ *= -1.1;
                    }

                    if (PlayerUtil.isBlockSolid(position.x, position.y + velocityY, position.z)) {
                        velocityX *= 1.35F;
                        velocityY *= -1.1;
                        velocityZ *= 1.35F;
                    }

                    if (PlayerUtil.isBlockSolid(position.x + velocityX, position.y, position.z)) {
                        velocityX *= -1.1;
                        velocityY *= 1.35F;
                        velocityZ *= 1.35F;
                    }
                }

                velocityX *= friction;
                velocityY = velocityY * friction - 0.00002;
                velocityZ *= friction;
            }

            velocity = new Vec3d(velocityX, velocityY, velocityZ);
            double deltaMultiplier = frameFactor * speedMultiplier;
            position = new Vec3d(
                    position.x + velocityX * deltaMultiplier,
                    position.y + velocityY * deltaMultiplier,
                    position.z + velocityZ * deltaMultiplier
            );
        }
    }
}
