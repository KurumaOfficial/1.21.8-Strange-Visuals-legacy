package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
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

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;

@IModule(
        name = "JumpHit",
        description = "Красивый эффект по блокам при прыжке",
        category = Category.World,
        bind = -1
)
public class JumpHit extends Module {

    private static final int BUFFER_SIZE = 1 << 18;

    private final ModeSetting mode = new ModeSetting("Режим", "Волна", "Волна", "Пульс", "Круги", "Взрыв");
    private final SliderSetting size = new SliderSetting("Размер", 3.2f, 1.5f, 8.0f, 0.1f, false);
    private final SliderSetting speed = new SliderSetting("Скорость", 1.0f, 0.4f, 2.2f, 0.1f, false);
    private final HueSetting color = new HueSetting("Цвет", new Color(131, 166, 232));
    private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
    private final BooleanSetting onlyOnGround = new BooleanSetting("Только приземление", false);
    private final BooleanSetting onlyThirdPerson = new BooleanSetting("Только 3 лицо", false);

    private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

    private final List<JumpEffect> effects = new ArrayList<>();
    private boolean wasInAir = false;
    private boolean wasOnGround = false;

    public JumpHit() {
        addSettings(mode, size, speed, color, rainbow, onlyOnGround, onlyThirdPerson);
    }

    @Override
    public void onDisable() {
        effects.clear();
        wasInAir = false;
        wasOnGround = false;
        super.onDisable();
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        updateEffects();
        if (effects.isEmpty()) return;
        if (onlyThirdPerson.get() && !isThirdPerson()) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrixStack();
        float tickDelta = event.getTickDelta();
        float time = (mc.player.age + tickDelta) * 0.05f;

        try {
            Iterator<JumpEffect> it = effects.iterator();
            while (it.hasNext()) {
                JumpEffect effect = it.next();
                float progress = effect.getProgress();
                if (progress >= 1.0f) {
                    it.remove();
                    continue;
                }
                renderEffect(matrices, cameraPos, effect, progress, time);
            }
            immediate.draw();
        } finally {
            allocator.clear();
        }
    }

    private void updateEffects() {
        if (mc.player == null || mc.world == null) return;

        if (onlyThirdPerson.get() && !isThirdPerson()) {
            effects.clear();
            return;
        }

        boolean onGround = mc.player.isOnGround();
        boolean inAir = !onGround;
        double velY = mc.player.getVelocity().y;

        effects.removeIf(e -> e.getProgress() >= 1.0f);

        if (onlyOnGround.get()) {
            if (wasInAir && onGround) {
                spawnEffect();
            }
        } else {
            boolean jumpedNow = wasOnGround && !onGround && velY > 0.08;
            boolean jumpPressedFromGround = onGround && mc.options.jumpKey.isPressed();

            if (jumpedNow || jumpPressedFromGround) {
                if (effects.isEmpty() || System.currentTimeMillis() - effects.get(effects.size() - 1).spawnTime > 300L) {
                    spawnEffect();
                }
            }
        }

        wasInAir = inAir;
        wasOnGround = onGround;
    }

    private boolean isThirdPerson() {
        if (mc.options == null) return false;
        Perspective p = mc.options.getPerspective();
        return p == Perspective.THIRD_PERSON_BACK || p == Perspective.THIRD_PERSON_FRONT;
    }

    private void spawnEffect() {
        GroundHit hit = getGroundHit();
        if (hit == null) return;

        effects.add(new JumpEffect(
                hit.surfaceY,
                mc.player.getX(),
                mc.player.getZ(),
                size.get(),
                getDuration()
        ));
    }

    private long getDuration() {
        float sp = speed.get();
        float base = 1200.0f;
        return (long) (base / Math.max(0.25f, sp));
    }

    private GroundHit getGroundHit() {
        Vec3d start = new Vec3d(mc.player.getX(), mc.player.getBoundingBox().minY + 0.05, mc.player.getZ());
        Vec3d end = start.subtract(0.0, 6.0, 0.0);

        RaycastContext ctx = new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );

        BlockHitResult res = mc.world.raycast(ctx);
        if (res.getType() == HitResult.Type.BLOCK) {
            return new GroundHit(res.getPos().y);
        }

        BlockPos p = mc.player.getBlockPos().down();
        return new GroundHit(p.getY() + 1.0);
    }

    private void renderEffect(MatrixStack matrices, Vec3d cameraPos, JumpEffect effect, float progress, float time) {
        float baseWidth = 2.4f;
        float glowWidth = 4.0f;
        int glowLayers = 4;
        float glowIntensity = 1.7f;

        drawPass(matrices, cameraPos, effect, progress, time, baseWidth, 1.0f, 0.0f);

        for (int i = 1; i <= glowLayers; i++) {
            float t = i / (float) (glowLayers + 1);
            float width = baseWidth + lerp(0.4f, glowWidth, t);
            float alphaMul = (float) Math.pow(1.0f - t, 2.1f) * 0.6f * glowIntensity;
            float mixWhite = 0.08f + 0.5f * t;
            drawPass(matrices, cameraPos, effect, progress, time, width, alphaMul, mixWhite);
        }
    }

    private void drawPass(MatrixStack matrices, Vec3d cameraPos, JumpEffect effect, float progress, float time,
                          float width, float alphaMul, float mixToWhite) {
        VertexConsumer buffer = immediate.getBuffer(layerForWidth(width));
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        switch (mode.get()) {
            case "Пульс" -> drawPulse(buffer, matrix, cameraPos, effect, progress, time, alphaMul, mixToWhite);
            case "Круги" -> drawCircles(buffer, matrix, cameraPos, effect, progress, time, alphaMul, mixToWhite);
            case "Взрыв" -> drawExplosion(buffer, matrix, cameraPos, effect, progress, time, alphaMul, mixToWhite);
            default -> drawWave(buffer, matrix, cameraPos, effect, progress, time, alphaMul, mixToWhite);
        }
    }

    private void drawWave(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, JumpEffect e, float progress, float time,
                          float alphaMul, float mixToWhite) {
        float waveR = e.radius * easeOutExpo(progress);
        float fade = 1.0f - progress;
        float fadeSq = fade * fade;

        float bandHalf = 0.58f;
        float softness = 0.80f;

        drawArea(buffer, matrix, cameraPos, e, time, alphaMul, mixToWhite, (x, z, dist, distNorm, angNorm) -> {
            float ring = bandAlpha((float) dist, waveR, bandHalf, softness);
            float wobble = 0.9f + 0.1f * (float) Math.sin(angNorm * Math.PI * 2.0f + time * 2.5f);
            return ring * fadeSq * wobble;
        });
    }

    private void drawPulse(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, JumpEffect e, float progress, float time,
                           float alphaMul, float mixToWhite) {
        float pulse = (float) Math.sin(progress * Math.PI);
        float fade = 1.0f - progress;
        float aBase = (float) Math.pow(pulse, 1.25f) * (float) Math.pow(fade, 1.25f);

        drawArea(buffer, matrix, cameraPos, e, time, alphaMul, mixToWhite, (x, z, dist, distNorm, angNorm) -> {
            float core = 1.0f - distNorm;
            core = smoothStep(core);
            core = (float) Math.pow(core, 1.3f);
            float swirl = 0.9f + 0.1f * (float) Math.sin((angNorm + distNorm) * (float) Math.PI * 2.0f + time * 3.0f);
            return aBase * core * swirl;
        });
    }

    private void drawCircles(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, JumpEffect e, float progress, float time,
                             float alphaMul, float mixToWhite) {
        int rings = 3;
        float spacing = 0.15f;
        float bandHalf = 0.58f;
        float softness = 0.80f;

        drawArea(buffer, matrix, cameraPos, e, time, alphaMul, mixToWhite, (x, z, dist, distNorm, angNorm) -> {
            float out = 0.0f;

            for (int i = 0; i < rings; i++) {
                float delay = i * spacing;
                float lp = progress - delay;
                if (lp <= 0.0f || lp >= 1.0f) continue;

                float rr = e.radius * (float) Math.pow(lp, 0.83f);
                float ringFade = 1.0f - lp;
                ringFade *= ringFade;

                float ring = bandAlpha((float) dist, rr, bandHalf, softness);
                out += ring * ringFade;
            }

            return out;
        });
    }

    private void drawExplosion(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, JumpEffect e, float progress, float time,
                               float alphaMul, float mixToWhite) {
        float bandHalf = 0.58f;
        float softness = 0.80f;
        float spikePower = 0.95f;
        float flashPower = 0.50f;

        float pFast = (float) Math.pow(progress, 0.55f);
        float exR = e.radius * pFast;
        float fade = 1.0f - progress;
        float fadePow = (float) Math.pow(fade, 2.0f);

        drawArea(buffer, matrix, cameraPos, e, time, alphaMul, mixToWhite, (x, z, dist, distNorm, angNorm) -> {
            float ring = bandAlpha((float) dist, exR, bandHalf, softness);

            int sector = (int) (angNorm * 20.0f);
            float noise = sectorNoise01(sector, e.spawnTime);
            float spikes = smoothStep((noise - 0.4f) / 0.6f) * spikePower;

            float spikeLen = (0.25f + 0.55f * spikes) * (0.25f + 0.08f * e.radius);
            float spiky = bandAlpha((float) dist, exR + spikeLen, bandHalf * 0.55f, softness * 1.15f);

            float flash = 0.0f;
            if (progress < 0.22f) {
                float t = 1.0f - (float) (dist / Math.max(0.8, e.radius * 0.40));
                t = MathHelper.clamp(t, 0.0f, 1.0f);
                flash = (float) Math.pow(t, 2.0f) * flashPower * (1.0f - progress / 0.22f);
            }

            return (ring + spiky) * fadePow + flash;
        });
    }

    private void drawArea(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, JumpEffect e, float time,
                          float alphaMul, float mixToWhite, AlphaFunc func) {
        int scan = MathHelper.ceil(e.radius + 3.5f);

        for (int x = MathHelper.floor(e.originX) - scan; x <= MathHelper.floor(e.originX) + scan; x++) {
            for (int z = MathHelper.floor(e.originZ) - scan; z <= MathHelper.floor(e.originZ) + scan; z++) {
                double dx = (x + 0.5) - e.originX;
                double dz = (z + 0.5) - e.originZ;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist > e.radius + 3.5) continue;

                float distNorm = (float) MathHelper.clamp(dist / e.radius, 0.0, 1.0);
                float angNorm = normAngle(dx, dz);

                float a = func.alpha(x, z, dist, distNorm, angNorm);
                if (a < 0.01f) continue;

                BlockPos bp = resolveGroundColumn(x, z, e);
                if (bp == null) continue;

                int rgb = getColor(distNorm, angNorm, time);
                if (mixToWhite > 0.0f) rgb = mixWhite(rgb, mixToWhite);

                int alpha = (int) (255.0f * a * alphaMul);
                drawCollisionOutline(buffer, matrix, cameraPos, bp, rgb, alpha);
            }
        }
    }

    private BlockPos resolveGroundColumn(int x, int z, JumpEffect effect) {
        int topY = MathHelper.floor(effect.surfaceY + 1.25);
        int minY = topY - 16;

        for (int y = topY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = mc.world.getBlockState(pos);

            if (state.isAir()) continue;
            if (state.isIn(BlockTags.LEAVES)) continue;
            if (state.isIn(BlockTags.LOGS)) continue;

            VoxelShape shape = state.getCollisionShape(mc.world, pos, ShapeContext.absent());
            if (shape.isEmpty()) continue;

            double maxTop = getShapeMaxYWorld(shape, pos);
            if (maxTop > effect.surfaceY + 1.05) continue;

            return pos;
        }
        return null;
    }

    private double getShapeMaxYWorld(VoxelShape shape, BlockPos pos) {
        double max = -1e9;
        for (Box bb : shape.getBoundingBoxes()) {
            max = Math.max(max, bb.maxY + pos.getY());
        }
        return max;
    }

    private void drawCollisionOutline(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, BlockPos pos, int color, int alpha) {
        if (alpha <= 0) return;

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getCollisionShape(mc.world, pos, ShapeContext.absent());
        if (shape.isEmpty()) return;

        float lift = 0.02f;

        for (Box local : shape.getBoundingBoxes()) {
            Box world = local.offset(pos).expand(0.002).offset(0.0, lift, 0.0);
            Box bb = world.offset(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            drawBoxOutline(buffer, matrix, bb, color, alpha);
        }
    }

    private void drawBoxOutline(VertexConsumer buffer, Matrix4f matrix, Box bb, int color, int alpha) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = alpha / 255.0f;

        double x1 = bb.minX, y1 = bb.minY, z1 = bb.minZ;
        double x2 = bb.maxX, y2 = bb.maxY, z2 = bb.maxZ;

        line(buffer, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        line(buffer, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        line(buffer, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        line(buffer, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        line(buffer, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);
    }

    private void line(VertexConsumer buffer, Matrix4f matrix,
                      double x1, double y1, double z1,
                      double x2, double y2, double z2,
                      float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    private int getColor(float distNorm, float angNorm, float time) {
        int base = color.getRGB();

        if (rainbow.get()) {
            float hue = (time * 0.18f + distNorm * 0.22f + angNorm * 0.35f) % 1.0f;
            return Color.HSBtoRGB(hue, 0.85f, 1.0f);
        }

        float brightness = 0.75f + 0.25f * (1.0f - distNorm);
        return multBrightness(base, brightness);
    }

    private int multBrightness(int color, float mul) {
        mul = MathHelper.clamp(mul, 0.0f, 2.0f);
        int r = MathHelper.clamp((int) (((color >> 16) & 0xFF) * mul), 0, 255);
        int g = MathHelper.clamp((int) (((color >> 8) & 0xFF) * mul), 0, 255);
        int b = MathHelper.clamp((int) ((color & 0xFF) * mul), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private int mixWhite(int color, float k) {
        k = MathHelper.clamp(k, 0.0f, 1.0f);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        r = (int) MathHelper.lerp(k, r, 255);
        g = (int) MathHelper.lerp(k, g, 255);
        b = (int) MathHelper.lerp(k, b, 255);

        return (r << 16) | (g << 8) | b;
    }

    private RenderLayer layerForWidth(float width) {
        if (width <= 1.25f) return JUMP_LINE_LAYER_1;
        if (width <= 2.25f) return JUMP_LINE_LAYER_2;
        if (width <= 3.25f) return JUMP_LINE_LAYER_3;
        if (width <= 4.25f) return JUMP_LINE_LAYER_4;
        if (width <= 5.25f) return JUMP_LINE_LAYER_5;
        return JUMP_LINE_LAYER_6;
    }

    private float bandAlpha(float dist, float center, float halfWidth, float softness) {
        float d = Math.abs(dist - center);
        float inner = Math.max(0.0f, halfWidth);
        float outer = inner + Math.max(0.0001f, softness);

        if (d >= outer) return 0.0f;
        if (d <= inner) return 1.0f;

        float t = 1.0f - (d - inner) / (outer - inner);
        return smoothStep(t);
    }

    private float smoothStep(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private float easeOutExpo(float t) {
        return t >= 1.0f ? 1.0f : 1.0f - (float) Math.pow(2.0, -10.0 * t);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float normAngle(double dx, double dz) {
        double a = Math.atan2(dz, dx) / (Math.PI * 2.0);
        a = a - Math.floor(a);
        return (float) a;
    }

    private float sectorNoise01(int sector, long seed) {
        long h = seed ^ (sector * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return ((h >>> 40) & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private interface AlphaFunc {
        float alpha(int x, int z, double dist, float distNorm, float angNorm);
    }

    private static class GroundHit {
        final double surfaceY;

        GroundHit(double surfaceY) {
            this.surfaceY = surfaceY;
        }
    }

    private static class JumpEffect {
        final double surfaceY;
        final double originX;
        final double originZ;
        final float radius;
        final long duration;
        final long spawnTime;

        JumpEffect(double surfaceY, double originX, double originZ, float radius, long duration) {
            this.surfaceY = surfaceY;
            this.originX = originX;
            this.originZ = originZ;
            this.radius = radius;
            this.duration = duration;
            this.spawnTime = System.currentTimeMillis();
        }

        float getProgress() {
            long lived = System.currentTimeMillis() - spawnTime;
            return MathHelper.clamp((float) lived / (float) duration, 0.0f, 1.0f);
        }
    }

    private static final RenderPipeline JUMP_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "jump_hit_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static RenderLayer createLineLayer(String name, double width) {
        return RenderLayer.of(
                name,
                BUFFER_SIZE,
                false,
                true,
                JUMP_LINE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .build(false)
        );
    }

    private static final RenderLayer JUMP_LINE_LAYER_1 = createLineLayer("jump_hit_lines_1", 1.0);
    private static final RenderLayer JUMP_LINE_LAYER_2 = createLineLayer("jump_hit_lines_2", 2.0);
    private static final RenderLayer JUMP_LINE_LAYER_3 = createLineLayer("jump_hit_lines_3", 3.0);
    private static final RenderLayer JUMP_LINE_LAYER_4 = createLineLayer("jump_hit_lines_4", 4.0);
    private static final RenderLayer JUMP_LINE_LAYER_5 = createLineLayer("jump_hit_lines_5", 5.0);
    private static final RenderLayer JUMP_LINE_LAYER_6 = createLineLayer("jump_hit_lines_6", 6.0);
}