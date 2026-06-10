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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import ru.strange.client.module.impl.other.Optimization;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.math.MathHelper;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@IModule(
        name = "Прыг кубики",
        description = "Рендерит небольшие кубики вокруг игроков",
        category = Category.World,
        bind = -1
)
public class DashCubes extends Module {
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;

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
    private static final double TRACKED_ENTITY_RADIUS = 12.0;
    private static final double TRACKED_ENTITY_RADIUS_SQ = TRACKED_ENTITY_RADIUS * TRACKED_ENTITY_RADIUS;
    private static final int RANDOM_POINT_ATTEMPTS_PER_SPOT = 8;
    private static final int SPOT_SELECTION_ATTEMPT_MULTIPLIER = 8;
    private static final int SPOT_SELECTION_ATTEMPT_CAP = 96;
    private static final long PLACEABLE_CACHE_TTL_MS = 150L;
    private static final double PLACEABLE_CACHE_REBUILD_DISTANCE_SQ = 0.35 * 0.35;
    private static final long BASE_SPAWN_SYNC_INTERVAL_MS = 50L;
    private static final long OPTIMIZED_SPAWN_SYNC_INTERVAL_MS = 120L;
    private static final double CUBE_HALF_SIZE = (1.0 / RES_PX) / 2.0;
    private static final int[] WHITE_YAWS = new int[]{0, 90, 180, 270};
    private final Random RANDOM = new Random(192372624L);
    private final List<DashCubic> cubes = new ArrayList<>();
    private final List<LivingEntity> trackedEntities = new ArrayList<>();
    private final Map<UUID, PlaceableCache> placeableCacheByEntity = new HashMap<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private long lastSpawnSyncTime;

    private static final int LINE_BUFFER_SIZE_BYTES = 1 << 10;

    private static final RenderPipeline CUBE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "dash_cubes_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
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
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
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
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @EventInit
    public void onWorldChange(EventChangeWorld e) {
        resetState();
    }

    @EventInit
    public void onUpdate(EventMotion e) {
        if (mc.world == null || mc.player == null) {
            resetState();
            return;
        }

        long now = System.currentTimeMillis();
        if (shouldSyncSpawnState(now)) {
            collectTrackedEntities(trackedEntities);

            if (trackedEntities.isEmpty()) {
                placeableCacheByEntity.clear();
                updateAndPruneCubes(now);
                lastSpawnSyncTime = now;
                return;
            }

            for (LivingEntity entity : trackedEntities) {
                PlaceableCache cache = getOrBuildPlaceableCache(entity, now);
                spawnCubesForEntity(entity, cache.placeableSpots);
            }

            placeableCacheByEntity.values().removeIf(cache -> cache.lastSeenAt != now);
            lastSpawnSyncTime = now;
        }
        updateAndPruneCubes(now);
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
        long frameTimeMs = System.currentTimeMillis();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = e.getMatrixStack();

        try {
            VertexConsumer fillBuffer = renderVertexConsumers.getBuffer(CUBE_FILL_LAYER);
            for (DashCubic cube : cubes) {
                cube.renderFill(matrices, fillBuffer, cameraPos, partialTicks, frameTimeMs);
            }

            VertexConsumer outlineBuffer = renderVertexConsumers.getBuffer(CUBE_LAYER);
            for (DashCubic cube : cubes) {
                cube.renderOutline(matrices, outlineBuffer, cameraPos, partialTicks, frameTimeMs);
            }

            renderVertexConsumers.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void resetState() {
        cubes.clear();
        trackedEntities.clear();
        placeableCacheByEntity.clear();
        lastSpawnSyncTime = 0L;
    }

    private boolean shouldSyncSpawnState(long now) {
        long interval = Optimization.shouldLimitWorldEffects()
                ? OPTIMIZED_SPAWN_SYNC_INTERVAL_MS
                : BASE_SPAWN_SYNC_INTERVAL_MS;
        return now - lastSpawnSyncTime >= interval;
    }

    private void collectTrackedEntities(List<LivingEntity> result) {
        result.clear();
        if (mc.world == null || mc.player == null) {
            return;
        }

        if (!allEntity.get() || !Optimization.shouldDashCubesTrackAllEntities()) {
            result.add(mc.player);
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        if (mc.player.isAlive() && !mc.player.isRemoved() && !mc.player.isInvisible() && !mc.player.isSpectator()) {
            result.add(mc.player);
        }

        Box searchBounds = mc.player.getBoundingBox().expand(TRACKED_ENTITY_RADIUS);
        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBounds)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (!living.isAlive() || living.isRemoved() || living.isInvisible()) {
                continue;
            }
            if (living instanceof PlayerEntity player && player.isSpectator()) {
                continue;
            }
            double dx = living.getX() - playerPos.x;
            double dy = living.getY() - playerPos.y;
            double dz = living.getZ() - playerPos.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) > TRACKED_ENTITY_RADIUS_SQ) {
                continue;
            }
            result.add(living);
        }
    }

    private List<PlaceableSpot> getPlaceableAround(LivingEntity base, double dstXZMin, double dstXZMax, int offsetDown) {
        List<PlaceableSpot> result = new ArrayList<>();
        if (mc.world == null || base == null) return result;

        double xE = base.getX();
        double yE = base.getY() + 1.0;
        double zE = base.getZ();
        Set<BlockPos> seen = new HashSet<>();
        List<Box> blockingEntityBoxes = collectBlockingEntityBoxes(base, dstXZMax, offsetDown);
        BlockPos.Mutable cursor = new BlockPos.Mutable();

        for (double x = xE - dstXZMax; x < xE + dstXZMax; x++) {
            for (double z = zE - dstXZMax; z < zE + dstXZMax; z++) {
                for (double y = yE - offsetDown; y < yE; y++) {
                    cursor.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                    BlockPos pos = cursor.toImmutable();
                    if (!seen.add(pos)) {
                        continue;
                    }
                    if (canPlaceCube(pos, blockingEntityBoxes)) {
                        result.add(new PlaceableSpot(pos, getSurfaceY(pos)));
                    }
                }
            }
        }
        return result;
    }

    private List<Box> collectBlockingEntityBoxes(LivingEntity base, double dstXZMax, int offsetDown) {
        List<Box> result = new ArrayList<>();
        if (mc.world == null || base == null) {
            return result;
        }

        double xE = base.getX();
        double yE = base.getY() + 1.0;
        double zE = base.getZ();
        Box searchBounds = new Box(
                xE - dstXZMax,
                yE - offsetDown,
                zE - dstXZMax,
                xE + dstXZMax + 1.0,
                yE + 1.0,
                zE + dstXZMax + 1.0
        );

        for (Entity entity : mc.world.getOtherEntities(null, searchBounds, DashCubes::isBlockingEntity)) {
            result.add(entity.getBoundingBox());
        }
        return result;
    }

    private static boolean isBlockingEntity(Entity entity) {
        return entity != null && !entity.isRemoved() && entity.isAlive();
    }

    private boolean canPlaceCube(BlockPos pos, List<Box> blockingEntityBoxes) {
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
        for (Box entityBox : blockingEntityBoxes) {
            if (entityBox.intersects(box)) {
                return false;
            }
        }
        return true;
    }

    private double getSurfaceY(BlockPos airPos) {
        if (mc.world == null) return airPos.getY();

        BlockPos below = airPos.down();
        BlockState stateBelow = mc.world.getBlockState(below);
        VoxelShape shapeBelow = stateBelow.getCollisionShape(mc.world, below);

        if (shapeBelow.isEmpty()) {
            return below.getY() + 1.0;
        }

        Box bounds = shapeBelow.getBoundingBox();
        return below.getY() + bounds.maxY;
    }

    private GenBox findRandomPointAboveSurface(PlaceableSpot spot, Vec3d center,
                                               double minDst, double maxDst) {
        if (spot == null) return null;

        double minDstSq = minDst * minDst;
        double maxDstSq = maxDst * maxDst;
        int attempts = RANDOM_POINT_ATTEMPTS_PER_SPOT;
        while (attempts-- > 0) {
            double rx = spot.pos.getX() + RANDOM.nextDouble();
            double rz = spot.pos.getZ() + RANDOM.nextDouble();
            double ry = spot.surfaceY + CUBE_HALF_SIZE;

            double dx = rx - center.x;
            double dy = ry - center.y;
            double dz = rz - center.z;
            double dstSq = (dx * dx) + (dy * dy) + (dz * dz);
            if (dstSq >= minDstSq && dstSq <= maxDstSq) {
                return new GenBox(rx, ry, rz, RES_PX);
            }
        }
        return null;
    }

    private PlaceableCache getOrBuildPlaceableCache(LivingEntity entity, long now) {
        UUID cacheKey = entity.getUuid();
        PlaceableCache cache = placeableCacheByEntity.get(cacheKey);
        if (cache != null && cache.canReuse(entity, now)) {
            cache.lastSeenAt = now;
            return cache;
        }

        cache = new PlaceableCache(entity, getPlaceableAround(entity, SPAWN_MIN_DST, SPAWN_MAX_DST, 3), now);
        placeableCacheByEntity.put(cacheKey, cache);
        return cache;
    }

    private void spawnCubesForEntity(LivingEntity entity, List<PlaceableSpot> placeableSpots) {
        if (placeableSpots.isEmpty()) {
            return;
        }

        int maxCubes = Optimization.capDashCubeCount((int) count.get());
        if (maxCubes <= 0) {
            return;
        }

        Vec3d entityPos = entity.getPos();
        int maxAttempts = Math.min(
                SPOT_SELECTION_ATTEMPT_CAP,
                Math.max(placeableSpots.size(), maxCubes * SPOT_SELECTION_ATTEMPT_MULTIPLIER)
        );

        int spawned = 0;
        for (int attempts = 0; attempts < maxAttempts && spawned < maxCubes; attempts++) {
            PlaceableSpot spot = placeableSpots.get(RANDOM.nextInt(placeableSpots.size()));
            GenBox box = findRandomPointAboveSurface(spot, entityPos, SPAWN_MIN_DST, SPAWN_MAX_DST);
            if (box == null) {
                continue;
            }
            cubes.add(new DashCubic(box, entity, CUBE_LIFETIME_MS, MAX_DISTANCE_FROM_ENTITY));
            spawned++;
        }
    }

    private void updateAndPruneCubes(long now) {
        for (int i = cubes.size() - 1; i >= 0; i--) {
            DashCubic cube = cubes.get(i);
            cube.updateLogic();
            if (cube.isDead(now)) {
                cubes.remove(i);
            }
        }
    }

    private int getRandomYaw() {
        return WHITE_YAWS[RANDOM.nextInt(WHITE_YAWS.length)];
    }

    private int getRandomJumpPixels() {
        return MathHelper.getRandomNumberBetween(2, 12);
    }

    private int getCubeColor(float alphaPc, int randomColor, long timeMs) {
        if (shaderColors.get()) {
            int themeColor = ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), timeMs * 0.002 + alphaPc * 4.0);
            return RenderUtil.ColorUtil.multAlpha(themeColor, MathHelper.clamp(alphaPc, 0.0f, 1.0f));
        }

        int baseColor = switch (colorMode.get()) {
            case "RGB" -> RenderUtil.ColorUtil.rainbow(10, 0, 1f, 1f, 1f);
            case "Astolfo" -> RenderUtil.ColorUtil.skyRainbow(25, 0);
            case "Random" -> randomColor;
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

    private static class PlaceableSpot {
        final BlockPos pos;
        final double surfaceY;

        private PlaceableSpot(BlockPos pos, double surfaceY) {
            this.pos = pos;
            this.surfaceY = surfaceY;
        }
    }

    private static class PlaceableCache {
        final UUID entityUuid;
        final double anchorX;
        final double anchorY;
        final double anchorZ;
        final long createdAt;
        final List<PlaceableSpot> placeableSpots;
        long lastSeenAt;

        private PlaceableCache(LivingEntity entity, List<PlaceableSpot> placeableSpots, long now) {
            this.entityUuid = entity.getUuid();
            this.anchorX = entity.getX();
            this.anchorY = entity.getY();
            this.anchorZ = entity.getZ();
            this.createdAt = now;
            this.lastSeenAt = now;
            this.placeableSpots = placeableSpots;
        }

        private boolean canReuse(LivingEntity entity, long now) {
            if (entity.isRemoved()
                    || !entityUuid.equals(entity.getUuid())
                    || now - createdAt > PLACEABLE_CACHE_TTL_MS) {
                return false;
            }

            double dx = entity.getX() - anchorX;
            double dy = entity.getY() - anchorY;
            double dz = entity.getZ() - anchorZ;
            return (dx * dx) + (dy * dy) + (dz * dz) <= PLACEABLE_CACHE_REBUILD_DISTANCE_SQ;
        }
    }

    private class DashCubic {
        private final GenBox box;
        private final LivingEntity owner;
        private final long lifeTimeMs;
        private final long spawnTime = System.currentTimeMillis();
        private final double maxDistanceSqAtEntity;
        private final int randomColor = Color.getHSBColor(
                RANDOM.nextFloat(),
                1.0f,
                1.0f
        ).getRGB();
        private static final long OUT_OF_RANGE_FADE_MS = 400L;

        private int jumpTicksMax;
        private int jumpTicks;
        private int prevJumpTicks;
        private int jumpYaw;
        private double jumpHeight;
        private double jumpProgress;

        private boolean outOfRange = false;
        private long outOfRangeStart = 0L;

        DashCubic(GenBox box, LivingEntity owner, long lifeTimeMs, double maxDistanceAtEntity) {
            this.box = box;
            this.owner = owner;
            this.lifeTimeMs = lifeTimeMs;
            this.maxDistanceSqAtEntity = maxDistanceAtEntity * maxDistanceAtEntity;
        }

        boolean isDead(long now) {
            if (box == null || owner == null || owner.isRemoved()) return true;
            if (owner.isInvisible()) return true;

            float timePc = getTimePc(now);
            if (timePc >= 1.0f) return true;

            double dx = owner.getX() - box.center.x;
            double dy = owner.getY() - box.center.y;
            double dz = owner.getZ() - box.center.z;
            if ((dx * dx) + (dy * dy) + (dz * dz) > maxDistanceSqAtEntity) {
                if (!outOfRange) {
                    outOfRange = true;
                    outOfRangeStart = now;
                }
            }

            if (outOfRange && getRangeFade(now) <= 0.01f) {
                return true;
            }

            return false;
        }

        float getTimePc(long now) {
            long dt = now - spawnTime;
            return MathHelper.clamp((float) dt / (float) lifeTimeMs, 0.0f, 1.0f);
        }

        private float getRangeFade(long now) {
            if (!outOfRange) return 1.0f;
            long dt = now - outOfRangeStart;
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

            float remaining = MathHelper.lerp(prevJumpTicks, jumpTicks, partialTicks) / (float) jumpTicksMax;
            float progress = 1.0f - remaining;
            double val = Math.sin(progress * Math.PI) * jumpHeight;
            return Double.isNaN(val) ? 0.0 : val;
        }

        void renderFill(MatrixStack matrices, VertexConsumer fillBuffer, Vec3d cameraPos, float partialTicks, long frameTimeMs) {
            if (box == null || owner == null) return;

            float timePc = getTimePc(frameTimeMs);
            if (timePc >= 1.0f) return;

            float targetAlphaPc;
            if (timePc < 0.1f) {
                targetAlphaPc = timePc / 0.1f;
            } else if (timePc > 0.8f) {
                targetAlphaPc = 1.0f - ((timePc - 0.8f) / 0.2f);
            } else {
                targetAlphaPc = 1.0f;
            }

            float alphaPc = targetAlphaPc * getRangeFade(frameTimeMs);

            if (alphaPc <= 0.01f) return;

            double jumpOffset = getJumpYOffset(partialTicks);

            double cx = box.center.x;
            double cy = box.center.y + jumpOffset;
            double cz = box.center.z;

            double hx = box.halfSize;
            double hy = box.halfSize;
            double hz = box.halfSize;

            int color = getCubeColor(alphaPc, randomColor, frameTimeMs);
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

        void renderOutline(MatrixStack matrices, VertexConsumer outlineBuffer, Vec3d cameraPos, float partialTicks, long frameTimeMs) {
            if (box == null || owner == null) return;

            float timePc = getTimePc(frameTimeMs);
            if (timePc >= 1.0f) return;

            float targetAlphaPc;
            if (timePc < 0.1f) {
                targetAlphaPc = timePc / 0.1f;
            } else if (timePc > 0.8f) {
                targetAlphaPc = 1.0f - ((timePc - 0.8f) / 0.2f);
            } else {
                targetAlphaPc = 1.0f;
            }

            float alphaPc = targetAlphaPc * getRangeFade(frameTimeMs);
            if (alphaPc <= 0.01f) return;

            double jumpOffset = getJumpYOffset(partialTicks);

            double cx = box.center.x;
            double cy = box.center.y + jumpOffset;
            double cz = box.center.z;

            double hx = box.halfSize;
            double hy = box.halfSize;
            double hz = box.halfSize;

            int color = getCubeColor(alphaPc, randomColor, frameTimeMs);
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
