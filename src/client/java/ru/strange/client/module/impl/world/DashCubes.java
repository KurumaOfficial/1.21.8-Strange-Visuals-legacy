package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventJump;
import ru.strange.client.event.impl.EventMotion;
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
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.stream.Collectors;

@IModule(
        name = "Прыг кубики",
        description = "Рендерит небольшие кубики вокруг игроков",
        category = Category.World,
        bind = -1
)
public class DashCubes extends Module {

    public static SliderSetting count = new SliderSetting("Количество", 3, 1, 20, 1, false);
    public static BooleanSetting jumping = new BooleanSetting("Прыжки", true);
    public static BooleanSetting allEntity = new BooleanSetting("Все сущности", true);
    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));
    public static ModeSetting colorMode = new ModeSetting("Режим цвета", "Client", "Client", "RGB", "Astolfo", "Random");

    public static BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false);
    public static ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());

    public DashCubes() {
        addSettings(count, jumping, allEntity, colorSetting, colorMode, shaderColors, shaderTheme);
    }

    private static final int RES_PX = 16;
    private static final long CUBE_LIFETIME_MS = 2000L;
    private static final double SPAWN_MIN_DST = 0.6;
    private static final double SPAWN_MAX_DST = 2.6;
    private static final double MAX_DISTANCE_FROM_ENTITY = 6.0;
    private static final int[] WHITE_YAWS = new int[]{0, 90, 180, 270};
    private final Random RANDOM = new Random(192372624L);
    private final List<DashCubic> cubes = new ArrayList<>();

    private static final int LINE_BUFFER_SIZE_BYTES = 1 << 10;

    private static final RenderPipeline CUBE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "dash_cubes_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer CUBE_LAYER = RenderLayer.of(
            "strange_dash_cubes",
            LINE_BUFFER_SIZE_BYTES,
            false,
            true,
            CUBE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(1.5)))
                    .build(false)
    );

    private static final int FILL_BUFFER_SIZE_BYTES = 1 << 10;

    private static final RenderPipeline CUBE_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "dash_cubes_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer CUBE_FILL_LAYER = RenderLayer.of(
            "strange_dash_cubes_fill",
            FILL_BUFFER_SIZE_BYTES,
            false,
            true,
            CUBE_FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .build(false)
    );

    @Override
    public void toggle() {
        super.toggle();
        cubes.clear();
    }

    @EventInit
    public void onWorldChange(EventChangeWorld e) {
        cubes.clear();
    }

    @EventInit
    public void onUpdate(EventMotion e) {
        if (mc.world == null || mc.player == null) return;

        List<LivingEntity> entities = mc.world.getPlayers().stream()
                .filter(Objects::nonNull)
                .filter(entity -> entity.isAlive())
                .filter(entity -> !entity.isRemoved())
                .filter(entity -> !entity.isInvisible())
                .filter(entity -> {
                    if (!allEntity.get()) {
                        return entity == mc.player;
                    }
                    return mc.player.distanceTo(entity) <= 12.0f;
                })
                .map(e2 -> (LivingEntity) e2)
                .collect(Collectors.toList());

        if (entities.isEmpty()) {
            cubes.removeIf(DashCubic::isDead);
            return;
        }

        for (LivingEntity entity : entities) {
            Vec3d entityPos = entity.getPos();

            List<GenBox> candidates = new ArrayList<>();
            for (BlockPos pos : getPlaceableAround(entity, SPAWN_MIN_DST, SPAWN_MAX_DST, 3)) {
                GenBox box = findRandomPointAboveSurface(pos, entityPos, SPAWN_MIN_DST, SPAWN_MAX_DST);
                if (box != null) {
                    candidates.add(box);
                }
            }

            if (candidates.isEmpty()) continue;

            int maxCubes = (int) count.get();
            for (int i = 0; i < maxCubes; i++) {
                if (candidates.isEmpty()) break;
                int idx = MathHelper.clampI(
                        (int) (RANDOM.nextFloat() * (candidates.size() - 1)),
                        0,
                        candidates.size() - 1
                );
                GenBox box = candidates.get(idx);
                cubes.add(new DashCubic(box, entity, CUBE_LIFETIME_MS, MAX_DISTANCE_FROM_ENTITY));
            }
        }

        cubes.forEach(DashCubic::updateLogic);
        cubes.removeIf(DashCubic::isDead);
    }

    @EventInit
    public void onJump(EventJump e) {
        if (!jumping.get() || mc.player == null) return;

        for (DashCubic cube : cubes) {
            if (cube.owner == mc.player) {
                cube.tryStartJump();
            }
        }
    }

    @EventInit
    public void onRender3D(EventRender3D e) {
        if (mc.world == null || mc.player == null) return;
        if (cubes.isEmpty()) return;

        float partialTicks = e.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = e.getMatrixStack();

        List<DashCubic> visible = cubes.stream()
                .filter(c -> !c.isDead())
                .collect(Collectors.toList());

        if (visible.isEmpty()) return;

        BufferAllocator allocator = new BufferAllocator(1 << 18);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        try {
            VertexConsumer fillBuffer = immediate.getBuffer(CUBE_FILL_LAYER);
            for (DashCubic cube : visible) {
                cube.renderFill(matrices, fillBuffer, cameraPos, partialTicks);
            }

            VertexConsumer outlineBuffer = immediate.getBuffer(CUBE_LAYER);
            for (DashCubic cube : visible) {
                cube.renderOutline(matrices, outlineBuffer, cameraPos, partialTicks);
            }

            immediate.draw();
        } finally {
            allocator.close();
        }
    }

    private List<BlockPos> getPlaceableAround(LivingEntity base, double dstXZMin, double dstXZMax, int offsetDown) {
        List<BlockPos> result = new ArrayList<>();
        if (mc.world == null || base == null) return result;

        double xE = base.getX();
        double yE = base.getY() + 1.0;
        double zE = base.getZ();

        for (double x = xE - dstXZMax; x < xE + dstXZMax; x++) {
            for (double z = zE - dstXZMax; z < zE + dstXZMax; z++) {
                for (double y = yE - offsetDown; y < yE; y++) {
                    BlockPos pos = BlockPos.ofFloored(x, y, z);
                    if (canPlaceCube(pos) && result.stream().noneMatch(p -> p.equals(pos))) {
                        result.add(pos);
                    }
                }
            }
        }
        return result;
    }
    
    private boolean canPlaceCube(BlockPos pos) {
        if (mc.world == null) return false;

        // Текущий блок должен быть проходимым
        BlockState stateAt = mc.world.getBlockState(pos);
        VoxelShape shapeAt = stateAt.getCollisionShape(mc.world, pos);
        if (!shapeAt.isEmpty()) {
            return false;
        }

        // Блок снизу должен иметь поверхность
        BlockPos below = pos.down();
        BlockState stateBelow = mc.world.getBlockState(below);
        VoxelShape shapeBelow = stateBelow.getCollisionShape(mc.world, below);

        if (shapeBelow.isEmpty()) {
            return false;
        }

        Box box = new Box(pos);
        return mc.world.getOtherEntities(null, box).isEmpty();
    }

    private double getSurfaceY(BlockPos airPos) {
        if (mc.world == null) return airPos.getY();

        BlockPos below = airPos.down();
        BlockState stateBelow = mc.world.getBlockState(below);
        VoxelShape shapeBelow = stateBelow.getCollisionShape(mc.world, below);

        if (shapeBelow.isEmpty()) {
            return below.getY() + 1.0;
        }

        try {
            Box bounds = shapeBelow.getBoundingBox();
            return below.getY() + bounds.maxY;
        } catch (Exception e) {
            // На случай если shape пустая (не должно быть, но страхуемся)
            return below.getY() + 1.0;
        }
    }

    private GenBox findRandomPointAboveSurface(BlockPos pos, Vec3d center,
                                               double minDst, double maxDst) {
        if (mc.world == null) return null;

        double surfaceY = getSurfaceY(pos);

        int attempts = 64;
        while (attempts-- > 0) {
            double rx = pos.getX() + RANDOM.nextDouble();
            double rz = pos.getZ() + RANDOM.nextDouble();

            double cubeHalf = (1.0 / RES_PX) / 2.0;
            double ry = surfaceY + cubeHalf;

            Vec3d vec = new Vec3d(rx, ry, rz);
            double dst = vec.distanceTo(center);
            if (dst >= minDst && dst <= maxDst) {
                return new GenBox(rx, ry, rz, RES_PX);
            }
        }
        return null;
    }

    private int getRandomYaw() {
        return WHITE_YAWS[RANDOM.nextInt(WHITE_YAWS.length)];
    }

    private int getRandomJumpPixels() {
        return MathHelper.getRandomNumberBetween(2, 12);
    }

    private int getCubeColor(float alphaPc) {
        if (shaderColors.get()) {
            int themeColor = ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), System.currentTimeMillis() * 0.002 + alphaPc * 4.0);
            return RenderUtil.ColorUtil.multAlpha(themeColor, MathHelper.clamp(alphaPc, 0.0f, 1.0f));
        }

        int baseColor = switch (colorMode.get()) {
            case "RGB" -> RenderUtil.ColorUtil.rainbow(10, 0, 1f, 1f, 1f);
            case "Astolfo" -> RenderUtil.ColorUtil.skyRainbow(25, 0);
            case "Random" -> Color.getHSBColor(
                    (float) RANDOM.nextInt(255) / 255.0f,
                    1.0f,
                    1.0f
            ).getRGB();
            default -> colorSetting.getRGB();
        };

        alphaPc = MathHelper.clamp(alphaPc, 0.0f, 1.0f);
        return RenderUtil.ColorUtil.multAlpha(baseColor, alphaPc);
    }

    private static class GenBox {
        final Vec3d center;
        final double halfSize;

        GenBox(double x, double y, double z, int resPx) {
            double resOff = 1.0 / resPx;
            double snappedX = Math.floor(x * resPx) / resPx;
            double snappedZ = Math.floor(z * resPx) / resPx;

            this.halfSize = resOff / 2.0;
            this.center = new Vec3d(
                    snappedX + halfSize,
                    y,
                    snappedZ + halfSize
            );
        }
    }

    private class DashCubic {
        private final GenBox box;
        private final LivingEntity owner;
        private final long lifeTimeMs;
        private final long spawnTime = System.currentTimeMillis();
        private final double maxDistanceAtEntity;
        private static final long OUT_OF_RANGE_FADE_MS = 400L;

        private int jumpTicksMax;
        private int jumpTicks;
        private int prevJumpTicks;
        private int jumpYaw;
        private double jumpHeight;
        private double jumpProgress;
        private float lastAlphaPc = 0.0f;

        private boolean outOfRange = false;
        private long outOfRangeStart = 0L;

        DashCubic(GenBox box, LivingEntity owner, long lifeTimeMs, double maxDistanceAtEntity) {
            this.box = box;
            this.owner = owner;
            this.lifeTimeMs = lifeTimeMs;
            this.maxDistanceAtEntity = maxDistanceAtEntity;
        }

        boolean isDead() {
            if (box == null || owner == null || owner.isRemoved()) return true;
            if (owner.isInvisible()) return true;

            float timePc = getTimePc();
            if (timePc >= 1.0f) return true;

            double dist = owner.getPos().distanceTo(box.center);
            if (dist > maxDistanceAtEntity) {
                if (!outOfRange) {
                    outOfRange = true;
                    outOfRangeStart = System.currentTimeMillis();
                }
            }

            if (outOfRange && getRangeFade() <= 0.01f) {
                return true;
            }

            return false;
        }

        float getTimePc() {
            long dt = System.currentTimeMillis() - spawnTime;
            return MathHelper.clamp((float) dt / (float) lifeTimeMs, 0.0f, 1.0f);
        }

        private float getRangeFade() {
            if (!outOfRange) return 1.0f;
            long dt = System.currentTimeMillis() - outOfRangeStart;
            float pc = MathHelper.clamp((float) dt / (float) OUT_OF_RANGE_FADE_MS, 0.0f, 1.0f);
            return 1.0f - pc;
        }

        void updateLogic() {
            prevJumpTicks = jumpTicks;

            if (jumpTicks > 0) {
                jumpTicks--;
            }
            jumpProgress = jumpTicksMax > 0
                    ? (float) jumpTicks / (float) jumpTicksMax
                    : 0.0;
        }

        void tryStartJump() {
            if (!jumping.get()) return;

            if (jumpTicks <= 0) {
                this.jumpTicksMax = this.jumpTicks = (int) (14.0F * (0.5F + 0.5F * RANDOM.nextFloat()));
                this.jumpHeight = (getRandomJumpPixels() * (1.0 / 16.0));
                this.jumpYaw = getRandomYaw();
            }
        }

        private double getJumpYOffset(float partialTicks) {
            if (jumpTicksMax <= 0) return 0.0;

            float t = MathHelper.lerp(prevJumpTicks, jumpTicks, partialTicks) / (float) jumpTicksMax;

            if (t > 0.5f) t = 1.0f - t;

            double val = t * 2.0 * jumpHeight;
            return Double.isNaN(val) ? 0.0 : val;
        }

        void renderFill(MatrixStack matrices, VertexConsumer fillBuffer, Vec3d cameraPos, float partialTicks) {
            if (box == null || owner == null) return;

            float timePc = getTimePc();
            if (timePc >= 1.0f) return;

            float targetAlphaPc;
            if (timePc < 0.1f) {
                targetAlphaPc = timePc / 0.1f;
            } else if (timePc > 0.8f) {
                targetAlphaPc = 1.0f - ((timePc - 0.8f) / 0.2f);
            } else {
                targetAlphaPc = 1.0f;
            }

            float alphaPc = MathHelper.lerp(targetAlphaPc, lastAlphaPc, partialTicks);
            lastAlphaPc = alphaPc;

            alphaPc *= getRangeFade();

            if (alphaPc <= 0.01f) return;

            double jumpOffset = getJumpYOffset(partialTicks);

            double cx = box.center.x;
            double cy = box.center.y + jumpOffset;
            double cz = box.center.z;

            double hx = box.halfSize;
            double hy = box.halfSize;
            double hz = box.halfSize;

            int color = getCubeColor(alphaPc);
            int fillColor = RenderUtil.ColorUtil.multAlpha(color, 0.22f);

            matrices.push();
            matrices.translate(
                    cx - cameraPos.x,
                    cy - cameraPos.y,
                    cz - cameraPos.z
            );

            if (jumpTicksMax > 0) {
                float phase = 1.0f - (MathHelper.lerp(prevJumpTicks, jumpTicks, partialTicks) / (float) jumpTicksMax);
                float pitch = (float) (90.0 * phase);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(jumpYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            }

            float scale = alphaPc;
            matrices.scale(scale, scale, scale);
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            drawCubeFill(fillBuffer, matrix4f, hx, hy, hz, fillColor);
            matrices.pop();
        }

        void renderOutline(MatrixStack matrices, VertexConsumer outlineBuffer, Vec3d cameraPos, float partialTicks) {
            if (box == null || owner == null) return;

            float timePc = getTimePc();
            if (timePc >= 1.0f) return;

            float targetAlphaPc;
            if (timePc < 0.1f) {
                targetAlphaPc = timePc / 0.1f;
            } else if (timePc > 0.8f) {
                targetAlphaPc = 1.0f - ((timePc - 0.8f) / 0.2f);
            } else {
                targetAlphaPc = 1.0f;
            }

            float alphaPc = MathHelper.lerp(targetAlphaPc, lastAlphaPc, partialTicks);
            alphaPc *= getRangeFade();
            if (alphaPc <= 0.01f) return;

            double jumpOffset = getJumpYOffset(partialTicks);

            double cx = box.center.x;
            double cy = box.center.y + jumpOffset;
            double cz = box.center.z;

            double hx = box.halfSize;
            double hy = box.halfSize;
            double hz = box.halfSize;

            int color = getCubeColor(alphaPc);
            int outlineColor = RenderUtil.ColorUtil.multAlpha(color, 0.35f);

            matrices.push();
            matrices.translate(
                    cx - cameraPos.x,
                    cy - cameraPos.y,
                    cz - cameraPos.z
            );

            if (jumpTicksMax > 0) {
                float phase = 1.0f - (MathHelper.lerp(prevJumpTicks, jumpTicks, partialTicks) / (float) jumpTicksMax);
                float pitch = (float) (90.0 * phase);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(jumpYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            }

            float scale = alphaPc;
            matrices.scale(scale, scale, scale);
            Matrix4f matrix4f = matrices.peek().getPositionMatrix();
            drawCubeEdges(outlineBuffer, matrix4f, hx, hy, hz, outlineColor);
            matrices.pop();
        }

        private void drawCubeEdges(VertexConsumer buffer, Matrix4f matrix,
                                   double hx, double hy, double hz,
                                   int color) {
            float x1 = (float) -hx;
            float y1 = (float) -hy;
            float z1 = (float) -hz;
            float x2 = (float) hx;
            float y2 = (float) hy;
            float z2 = (float) hz;

            int r = RenderUtil.ColorUtil.red(color);
            int g = RenderUtil.ColorUtil.green(color);
            int b = RenderUtil.ColorUtil.blue(color);
            int a = RenderUtil.ColorUtil.alpha(color);

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
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          int r, int g, int b, int a) {
            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        }

        private void drawCubeFill(VertexConsumer buffer, Matrix4f matrix,
                                  double hx, double hy, double hz,
                                  int color) {
            float x1 = (float) -hx;
            float y1 = (float) -hy;
            float z1 = (float) -hz;
            float x2 = (float) hx;
            float y2 = (float) hy;
            float z2 = (float) hz;

            int r = RenderUtil.ColorUtil.red(color);
            int g = RenderUtil.ColorUtil.green(color);
            int b = RenderUtil.ColorUtil.blue(color);
            int a = RenderUtil.ColorUtil.alpha(color);

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);

            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);

            buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, y2, z2).color(r, g, b, a);
            buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a);

            buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a);
            buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
            buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a);
        }
    }
}