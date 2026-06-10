package ru.strange.client.module.impl.utilities;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;
import net.minecraft.client.gui.hud.MessageIndicator;
import org.joml.Matrix4f;
import ru.strange.client.command.CommandManager;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(
        name = "GPS",
        description = "Показывает путь до координат с обходом блоков",
        category = Category.Utilities,
        bind = -1
)
public class GPS extends Module {
    private static final double SURFACE_OFFSET = 0.20D;
    private static final double MARKER_WORLD_Y_OFFSET = 1.15D;
    private static final double ROUTE_REBUILD_DISTANCE_SQ = 25.0D;
    private static final double ROUTE_POINT_EPSILON_SQ = 0.010D;
    private static final long ROUTE_REBUILD_INTERVAL_MS = 350L;
    private static final int MAX_OPTIMIZED_SAMPLES = 192;
    private static final int MAX_BLOCK_SAMPLES = 960;
    private static final int OPTIMIZED_SEARCH_RADIUS = 2;
    private static final int BLOCK_SEARCH_RADIUS = 1;
    private static final double OPTIMIZED_MAX_FALL_PER_STEP = 3.25D;
    private static final double BLOCK_MAX_FALL_PER_STEP = 1.25D;
    private static final int SURFACE_SCAN_DEPTH = 8;
    private static final int MAX_BLOCK_ROUTE_STEPS = 4096;
    private static final int LINE_BUFFER_SIZE = 1 << 16;
    private static final float HUD_CARD_HEIGHT = 18.0F;
    private static final float HUD_CARD_MIN_WIDTH = 44.0F;
    private static final float HUD_CARD_MARGIN = 4.0F;
    private static final float HUD_ICON_AREA = 18.0F;
    private static final float HUD_ICON_SIZE = 10.0F;
    private static final float HUD_EDGE_MARGIN = 18.0F;
    private static final float HUD_MARKER_CLAMP_MARGIN = 12.0F;
    private static final float HUD_WORLD_MARKER_SIZE = 28.0F;
    private static final float HUD_WORLD_LABEL_GAP = 4.0F;
    private static final int HUD_TEXT_SIZE = 7;
    private static final Identifier WORLD_MARKER_TEXTURE = Identifier.of("strange", "textures/world/gps_marker.png");
    private static final Identifier HUD_MARKER_TEXTURE = Identifier.of("strange", "textures/hud/gps_marker.png");
    private static final Identifier DYNAMIC_MARKER_MASK_TEXTURE = Strange.id("dynamic/gps_marker_mask");

    public final ModeSetting markerColorMode = new ModeSetting("Marker Color Mode", "Normal", "Normal", "Shader");
    public final ModeSetting markerShaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !markerColorMode.is("Shader"));

    public final HueSetting colorSetting = new HueSetting("Цвет", new Color(255, 255, 255));
    public final SliderSetting markerSize = new SliderSetting("Размер маркера", 1.0f, 0.5f, 2.5f, 0.1f, false);
    public final BooleanSetting autoGpsEnabled = new BooleanSetting("Авто GPS", false);
    public final BooleanSetting lineEnabled = new BooleanSetting("Линия", false);
    public final ModeSetting lineMode = new ModeSetting("Режим линии", "Оптимизированный", "Оптимизированный", "По блокам")
            .hidden(() -> !lineEnabled.get());
    public final SliderSetting lineWidth = new SliderSetting("Толщина", 2.5f, 1.0f, 5.0f, 0.5f, false)
            .hidden(() -> !lineEnabled.get());

    private static final Pattern AUTO_GPS_COORD_PATTERN = Pattern.compile(
            "(?:Появил(?:ся|ась)|спавн)\\S*\\s+(?:на\\s+)?координат\\S*\\s*:?\\s*[\\[({]?\\s*(-?\\d+)[,\\s]+(-?\\d+)[,\\s]+(-?\\d+)\\s*[\\])}]?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final RenderPipeline GPS_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("strange", "gps_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build()
    );

    private final List<Vec3d> pathPoints = new ArrayList<>(96);
    private final Map<Integer, RenderLayer> gpsLayers = new HashMap<>();
    private final Map<Long, Vec3d> surfacePointCache = new HashMap<>(256);
    private final List<Vec3d> routeBuildBuffer = new ArrayList<>(128);
    private final List<Vec3d> routeCompactionBuffer = new ArrayList<>(128);
    private final List<Vec3d> routeSimplifyBuffer = new ArrayList<>(128);
    private final BufferAllocator renderAllocator = new BufferAllocator(LINE_BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate renderConsumers = VertexConsumerProvider.immediate(renderAllocator);
    private final BlockPos.Mutable surfaceProbePos = new BlockPos.Mutable();

    private BlockPos targetPos;
    private Vec3d markerWorldPos = Vec3d.ZERO;
    private BlockPos routeStartAnchor;
    private long lastRouteBuildAt;
    private boolean routeDirty = true;
    private boolean markerTextureResolved;
    private Identifier cachedMarkerTexture;
    private String lastRouteMode;
    private Identifier cachedMaskMarkerTexture;
    private Identifier cachedTintedMarkerSource;
    private NativeImageBackedTexture maskMarkerTexture;

    public GPS() {
        addSettings(colorSetting, markerSize, markerColorMode, markerShaderTheme, autoGpsEnabled, lineEnabled, lineMode, lineWidth);
    }

    public void handleAutoGpsMessage(Text message, MessageSignatureData signature, MessageIndicator indicator) {
        if (!autoGpsEnabled.get() || message == null) {
            return;
        }

        String raw = message.getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        boolean likelyServerEvent = signature == null
                || indicator != null
                || raw.contains("Уровень лута:")
                || raw.contains("координат");
        if (!likelyServerEvent) {
            return;
        }

        Matcher matcher = AUTO_GPS_COORD_PATTERN.matcher(raw);
        if (!matcher.find()) {
            return;
        }

        try {
            int x = Integer.parseInt(matcher.group(1));
            int y = Integer.parseInt(matcher.group(2));
            int z = Integer.parseInt(matcher.group(3));

            if (!enable) {
                toggle();
            }
            setTarget(x, y, z);
        } catch (NumberFormatException ignored) {
        }
    }

    @Override
    public void onDisable() {
        clearRuntimeState(false);
        super.onDisable();
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        targetPos = null;
        clearRuntimeState(false);
        markerTextureResolved = false;
        cachedMarkerTexture = null;
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (mc.player == null || mc.world == null || targetPos == null) {
            return;
        }

        markerWorldPos = resolveMarkerWorldPos(targetPos);
        if (!lineEnabled.get()) {
            invalidateRoute();
            return;
        }

        if (lastRouteMode == null || !lineMode.get().equalsIgnoreCase(lastRouteMode)) {
            routeDirty = true;
        }

        if (shouldRebuildRoute(mc.player)) {
            rebuildRoute(false);
        }
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null || targetPos == null || !lineEnabled.get() || pathPoints.size() < 2) {
            return;
        }

        MatrixStack matrices = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        double renderDistance = mc.options.getViewDistance().getValue() * 16.0 + 96.0;
        double renderDistanceSq = renderDistance * renderDistance;

        renderAllocator.clear();
        matrices.push();
        try {
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            renderRouteLine(matrices, cameraPos, renderDistanceSq);
            renderConsumers.draw();
        } finally {
            matrices.pop();
            renderAllocator.clear();
        }
    }

    @EventInit
    public void onScreen(EventScreen event) {
        GpsHudShaderPass.clearQueued();
        if (targetPos == null || mc.player == null || mc.world == null) {
            return;
        }

        markerWorldPos = resolveMarkerWorldPos(targetPos);
        renderHudMarker(event.drawContext(), event.client());
    }

    public void setTarget(int x, int y, int z) {
        targetPos = new BlockPos(x, y, z);
        markerWorldPos = resolveMarkerWorldPos(targetPos);
        invalidateRoute();

        if (lineEnabled.get() && mc.player != null && mc.world != null) {
            rebuildRoute(true);
        }

        sendClientMessage("GPS set to " + x + ", " + y + ", " + z);
    }

    public void clearPath() {
        targetPos = null;
        clearRuntimeState(false);
        sendClientMessage("GPS cleared");
    }

    private void clearRuntimeState(boolean keepTextureState) {
        markerWorldPos = Vec3d.ZERO;
        invalidateRoute();
        surfacePointCache.clear();
        if (!keepTextureState) {
            markerTextureResolved = false;
            cachedMarkerTexture = null;
            cachedMaskMarkerTexture = null;
            cachedTintedMarkerSource = null;
            releaseMarkerTextures();
        }
    }

    private boolean shouldRebuildRoute(PlayerEntity player) {
        if (targetPos == null || !lineEnabled.get()) {
            return false;
        }
        if (!enoughTimePassedSinceRouteBuild()) {
            return false;
        }
        if (pathPoints.isEmpty() || routeDirty || routeStartAnchor == null || lastRouteMode == null) {
            return true;
        }
        if (!lineMode.get().equalsIgnoreCase(lastRouteMode)) {
            return true;
        }

        BlockPos playerPos = player.getBlockPos();
        double dx = playerPos.getX() - routeStartAnchor.getX();
        double dy = playerPos.getY() - routeStartAnchor.getY();
        double dz = playerPos.getZ() - routeStartAnchor.getZ();
        return dx * dx + dy * dy + dz * dz >= ROUTE_REBUILD_DISTANCE_SQ;
    }

    private boolean enoughTimePassedSinceRouteBuild() {
        return System.currentTimeMillis() - lastRouteBuildAt >= ROUTE_REBUILD_INTERVAL_MS;
    }

    private void invalidateRoute() {
        pathPoints.clear();
        routeStartAnchor = null;
        routeDirty = true;
        lastRouteMode = null;
    }

    private void rebuildRoute(boolean announce) {
        if (mc.player == null || mc.world == null || targetPos == null) {
            return;
        }

        routeStartAnchor = mc.player.getBlockPos();
        lastRouteBuildAt = System.currentTimeMillis();
        routeDirty = false;
        lastRouteMode = lineMode.get();
        surfacePointCache.clear();

        Vec3d start = new Vec3d(mc.player.getX(), mc.player.getY() + 0.10D, mc.player.getZ());
        Vec3d routeGoal = resolveRouteGoal(targetPos, start.y);
        boolean blockMode = isBlockMode();
        List<Vec3d> route = routeBuildBuffer;
        if (blockMode) {
            buildBlockRoute(start, routeGoal, route);
        } else {
            buildSurfaceRoute(start, routeGoal, route);
        }

        postProcessRoute(route, blockMode);
        if (route.size() < 2) {
            route.clear();
            route.add(start);
            route.add(routeGoal);
        }

        pathPoints.clear();
        pathPoints.addAll(route);

        if (announce) {
            sendClientMessage(lineEnabled.get() ? "Route ready" : "Marker ready");
        }
    }

    private void buildSurfaceRoute(Vec3d start, Vec3d goal, List<Vec3d> route) {
        route.clear();
        route.add(start);

        double horizontalDistance = horizontalDistance(start, goal);
        if (horizontalDistance < 1.0D) {
            route.add(goal);
            return;
        }

        double stepDistance = resolveOptimizedStepDistance(horizontalDistance);
        int steps = Math.max(2, Math.min(MAX_OPTIMIZED_SAMPLES, (int) Math.ceil(horizontalDistance / stepDistance)));

        Vec3d previous = start;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            double desiredX = MathHelper.lerp(t, start.x, goal.x);
            double desiredZ = MathHelper.lerp(t, start.z, goal.z);
            Vec3d nextPoint = selectBestRoutePoint(desiredX, desiredZ, previous, goal, false);
            nextPoint = softenDrop(previous, nextPoint, OPTIMIZED_MAX_FALL_PER_STEP);

            if (nextPoint.squaredDistanceTo(previous) > ROUTE_POINT_EPSILON_SQ) {
                route.add(nextPoint);
                previous = nextPoint;
            }
        }

        route.add(goal);
    }

    private void buildBlockRoute(Vec3d start, Vec3d goal, List<Vec3d> route) {
        route.clear();
        route.add(start);

        int currentX = MathHelper.floor(start.x);
        int currentZ = MathHelper.floor(start.z);
        int goalX = MathHelper.floor(goal.x);
        int goalZ = MathHelper.floor(goal.z);
        Vec3d currentPoint = new Vec3d(currentX + 0.5D, start.y, currentZ + 0.5D);
        int stepCount = 0;

        while ((currentX != goalX || currentZ != goalZ) && stepCount++ < MAX_BLOCK_ROUTE_STEPS) {
            int stepX = Integer.compare(goalX, currentX);
            int stepZ = Integer.compare(goalZ, currentZ);

            StepCandidate bestCandidate = null;
            if (stepX != 0) {
                bestCandidate = chooseBetterStep(bestCandidate, currentX + stepX, currentZ, currentPoint, goalX, goalZ);
            }
            if (stepZ != 0) {
                bestCandidate = chooseBetterStep(bestCandidate, currentX, currentZ + stepZ, currentPoint, goalX, goalZ);
            }

            if (bestCandidate == null) {
                break;
            }

            appendBlockStep(route, currentPoint, bestCandidate.point());
            currentX = bestCandidate.x();
            currentZ = bestCandidate.z();
            currentPoint = bestCandidate.point();
        }

        if (route.isEmpty() || route.get(route.size() - 1).squaredDistanceTo(goal) > ROUTE_POINT_EPSILON_SQ) {
            appendBlockStep(route, currentPoint, goal);
        }
    }

    private double resolveOptimizedStepDistance(double horizontalDistance) {
        return MathHelper.clamp(horizontalDistance / 72.0D, 2.25D, 5.50D);
    }

    private boolean isBlockMode() {
        return lineMode.index == 1;
    }

    private boolean isShaderMarkerMode() {
        return markerColorMode.is("Shader");
    }

    private StepCandidate chooseBetterStep(StepCandidate currentBest, int candidateX, int candidateZ, Vec3d previousPoint, int goalX, int goalZ) {
        Vec3d candidatePoint = getSurfacePoint(candidateX, candidateZ, previousPoint.y);
        double remainingX = goalX - candidateX;
        double remainingZ = goalZ - candidateZ;
        double remainingPenalty = remainingX * remainingX + remainingZ * remainingZ;
        double verticalDelta = candidatePoint.y - previousPoint.y;
        double risePenalty = verticalDelta > 0.0D ? verticalDelta * 1.8D : 0.0D;
        double dropPenalty = verticalDelta < 0.0D ? Math.abs(verticalDelta) * 2.8D : 0.0D;
        double steepPenalty = Math.abs(verticalDelta) > BLOCK_MAX_FALL_PER_STEP ? Math.abs(verticalDelta) * 8.0D : 0.0D;
        double unloadedPenalty = isColumnLoaded(candidateX, candidateZ) ? 0.0D : 6.0D;
        double score = remainingPenalty + risePenalty + dropPenalty + steepPenalty + unloadedPenalty;

        StepCandidate candidate = new StepCandidate(candidateX, candidateZ, candidatePoint, score);
        if (currentBest == null || candidate.score() < currentBest.score()) {
            return candidate;
        }
        return currentBest;
    }

    private void appendBlockStep(List<Vec3d> route, Vec3d previous, Vec3d next) {
        if (previous.squaredDistanceTo(next) <= ROUTE_POINT_EPSILON_SQ) {
            return;
        }

        if (Math.abs(previous.y - next.y) <= 0.001D) {
            route.add(next);
            return;
        }

        if (next.y > previous.y) {
            route.add(new Vec3d(previous.x, next.y, previous.z));
            route.add(next);
            return;
        }

        route.add(new Vec3d(next.x, previous.y, next.z));
        route.add(next);
    }

    private Vec3d selectBestRoutePoint(double desiredX, double desiredZ, Vec3d previous, Vec3d goal, boolean blockMode) {
        int radius = blockMode ? BLOCK_SEARCH_RADIUS : OPTIMIZED_SEARCH_RADIUS;
        int centerX = MathHelper.floor(desiredX);
        int centerZ = MathHelper.floor(desiredZ);
        Vec3d bestPoint = approximateSurfacePoint(desiredX, desiredZ, previous.y);
        double bestScore = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int candidateX = centerX + dx;
                int candidateZ = centerZ + dz;
                Vec3d candidate = getSurfacePoint(candidateX, candidateZ, previous.y);
                double score = scoreRoutePoint(candidate, candidateX, candidateZ, desiredX, desiredZ, previous, goal, blockMode);
                if (score < bestScore) {
                    bestScore = score;
                    bestPoint = candidate;
                }
            }
        }

        return bestPoint;
    }

    private double scoreRoutePoint(Vec3d candidate, int candidateX, int candidateZ, double desiredX, double desiredZ,
                                   Vec3d previous, Vec3d goal, boolean blockMode) {
        double offsetPenalty = squaredHorizontalDistance(candidate.x, candidate.z, desiredX, desiredZ) * (blockMode ? 1.10D : 1.65D);
        double verticalDelta = candidate.y - previous.y;
        double risePenalty = verticalDelta > 0.0D ? verticalDelta * (blockMode ? 1.35D : 0.80D) : 0.0D;
        double dropPenalty = verticalDelta < 0.0D ? Math.abs(verticalDelta) * (blockMode ? 2.20D : 1.35D) : 0.0D;
        double steepPenalty = verticalDelta < -(blockMode ? BLOCK_MAX_FALL_PER_STEP : OPTIMIZED_MAX_FALL_PER_STEP)
                ? Math.abs(verticalDelta + (blockMode ? BLOCK_MAX_FALL_PER_STEP : OPTIMIZED_MAX_FALL_PER_STEP)) * (blockMode ? 8.0D : 5.0D)
                : 0.0D;
        double forward = (candidate.x - previous.x) * (goal.x - previous.x) + (candidate.z - previous.z) * (goal.z - previous.z);
        double backwardPenalty = forward < 0.0D ? Math.abs(forward) * 0.60D + 24.0D : 0.0D;
        double unloadedPenalty = isColumnLoaded(candidateX, candidateZ) ? 0.0D : (blockMode ? 4.0D : 2.0D);
        return offsetPenalty + risePenalty + dropPenalty + steepPenalty + backwardPenalty + unloadedPenalty;
    }

    private Vec3d softenDrop(Vec3d previous, Vec3d current, double maxFallPerStep) {
        double minimumY = previous.y - maxFallPerStep;
        if (current.y >= minimumY) {
            return current;
        }
        return new Vec3d(current.x, minimumY, current.z);
    }

    private void postProcessRoute(List<Vec3d> route, boolean blockMode) {
        if (route.size() < 2) {
            return;
        }

        List<Vec3d> compacted = routeCompactionBuffer;
        compacted.clear();
        Vec3d last = null;
        for (Vec3d point : route) {
            if (last == null || point.squaredDistanceTo(last) > ROUTE_POINT_EPSILON_SQ) {
                compacted.add(point);
                last = point;
            }
        }

        replaceRouteContents(route, compacted);
        if (!blockMode) {
            simplifyRoute(route);
        }
    }

    private void simplifyRoute(List<Vec3d> route) {
        if (route.size() < 3) {
            return;
        }

        List<Vec3d> simplified = routeSimplifyBuffer;
        simplified.clear();
        int lookAhead = 8;
        int index = 0;
        simplified.add(route.get(0));

        while (index < route.size() - 1) {
            int nextIndex = index + 1;
            int maxIndex = Math.min(route.size() - 1, index + lookAhead);
            for (int candidateIndex = maxIndex; candidateIndex > index + 1; candidateIndex--) {
                if (canShortcut(route.get(index), route.get(candidateIndex))) {
                    nextIndex = candidateIndex;
                    break;
                }
            }

            simplified.add(route.get(nextIndex));
            index = nextIndex;
        }

        replaceRouteContents(route, simplified);
    }

    private void replaceRouteContents(List<Vec3d> route, List<Vec3d> replacement) {
        route.clear();
        route.addAll(replacement);
        replacement.clear();
    }

    private boolean canShortcut(Vec3d start, Vec3d end) {
        double horizontalDistance = horizontalDistance(start, end);
        double maxShortcutDistance = 9.0D;
        if (horizontalDistance > maxShortcutDistance) {
            return false;
        }

        int samples = Math.max(1, (int) Math.ceil(horizontalDistance));
        double clearance = 0.20D;
        for (int i = 1; i < samples; i++) {
            double t = i / (double) samples;
            double x = MathHelper.lerp(t, start.x, end.x);
            double y = MathHelper.lerp(t, start.y, end.y);
            double z = MathHelper.lerp(t, start.z, end.z);
            Vec3d surface = getSurfacePoint(MathHelper.floor(x), MathHelper.floor(z), y);
            if (y < surface.y + clearance) {
                return false;
            }
        }

        return true;
    }

    private Vec3d resolveRouteGoal(BlockPos target, double fallbackY) {
        if (target == null) {
            return new Vec3d(0.5D, fallbackY, 0.5D);
        }

        Vec3d surfacePoint = getSurfacePoint(target.getX(), target.getZ(), Math.max(fallbackY, target.getY()));
        double goalY = Math.max(surfacePoint.y, target.getY() + SURFACE_OFFSET);
        return new Vec3d(target.getX() + 0.5D, goalY, target.getZ() + 0.5D);
    }

    private Vec3d resolveMarkerWorldPos(BlockPos target) {
        return new Vec3d(
                target.getX() + 0.5D,
                target.getY() + MARKER_WORLD_Y_OFFSET,
                target.getZ() + 0.5D
        );
    }

    private Vec3d getSurfacePoint(int x, int z, double fallbackY) {
        if (mc.world == null) {
            return approximateSurfacePoint(x + 0.5D, z + 0.5D, fallbackY);
        }

        long key = packColumnKey(x, z);
        Vec3d cached = surfacePointCache.get(key);
        if (cached != null) {
            return cached;
        }

        Vec3d computed = computeSurfacePoint(x, z, fallbackY);
        surfacePointCache.put(key, computed);
        return computed;
    }

    private Vec3d computeSurfacePoint(int x, int z, double fallbackY) {
        if (mc.world == null) {
            return approximateSurfacePoint(x + 0.5D, z + 0.5D, fallbackY);
        }

        if (!isColumnLoaded(x, z)) {
            return approximateSurfacePoint(x + 0.5D, z + 0.5D, fallbackY);
        }

        int bottomY = mc.world.getBottomY();
        int topY = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int searchStartY = Math.max(bottomY, topY - 1);
        BlockPos.Mutable probePos = surfaceProbePos;

        for (int y = searchStartY; y >= Math.max(bottomY, searchStartY - SURFACE_SCAN_DEPTH); y--) {
            probePos.set(x, y, z);
            BlockState state = mc.world.getBlockState(probePos);
            if (state.isAir()) {
                continue;
            }

            if (!state.getFluidState().isEmpty()) {
                return new Vec3d(x + 0.5D, y + 0.875D + SURFACE_OFFSET, z + 0.5D);
            }

            VoxelShape shape = state.getCollisionShape(mc.world, probePos);
            if (!shape.isEmpty()) {
                return new Vec3d(x + 0.5D, y + shape.getMax(Direction.Axis.Y) + SURFACE_OFFSET, z + 0.5D);
            }
        }

        return approximateSurfacePoint(x + 0.5D, z + 0.5D, fallbackY);
    }

    private boolean isColumnLoaded(int x, int z) {
        return mc.world != null && mc.world.getChunkManager().isChunkLoaded(x >> 4, z >> 4);
    }

    private Vec3d approximateSurfacePoint(double x, double z, double fallbackY) {
        double safeY = Double.isFinite(fallbackY) ? fallbackY : 64.0D;
        if (mc.world != null) {
            safeY = Math.max(mc.world.getBottomY() + 1.0D, safeY);
        }
        return new Vec3d(MathHelper.floor(x) + 0.5D, safeY, MathHelper.floor(z) + 0.5D);
    }

    private void renderRouteLine(MatrixStack matrices, Vec3d cameraPos, double renderDistanceSq) {
        VertexConsumer buffer = renderConsumers.getBuffer(getGpsLayer());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        int color = colorSetting.getRGB();
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        for (int i = 0; i < pathPoints.size() - 1; i++) {
            Vec3d start = pathPoints.get(i);
            Vec3d end = pathPoints.get(i + 1);

            if (start.squaredDistanceTo(cameraPos) > renderDistanceSq && end.squaredDistanceTo(cameraPos) > renderDistanceSq) {
                continue;
            }

            putLine(buffer, matrix, start, end, r, g, b, 235);
        }
    }

    private void renderHudMarker(DrawContext context, net.minecraft.client.MinecraftClient client) {
        if (client == null || client.player == null || targetPos == null) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        MarkerPlacement placement = resolveMarkerPlacement(client, screenWidth, screenHeight);
        if (placement == null) {
            return;
        }

        Vec3d playerPos = client.player.getPos();
        double dx = targetPos.getX() + 0.5D - playerPos.x;
        double dy = targetPos.getY() + 0.5D - playerPos.y;
        double dz = targetPos.getZ() + 0.5D - playerPos.z;
        String label = String.valueOf(Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)));
        int color = colorSetting.getRGB();
        Identifier markerTexture = resolveMarkerTexture();
        if (placement.directional()) {
            renderDirectionalHudMarker(context, screenWidth, screenHeight, placement, label, color, markerTexture);
            return;
        }

        renderWorldHudMarker(context, screenWidth, screenHeight, placement, label, color, markerTexture);
    }

    private void renderDirectionalHudMarker(DrawContext context, int screenWidth, int screenHeight,
                                            MarkerPlacement placement, String label, int color, Identifier markerTexture) {
        float cardWidth = resolveHudCardWidth(label);
        float cardX = MathHelper.clamp(placement.x() - cardWidth / 2.0F, HUD_CARD_MARGIN, screenWidth - cardWidth - HUD_CARD_MARGIN);
        float cardY = MathHelper.clamp(placement.y() - HUD_CARD_HEIGHT / 2.0F, HUD_CARD_MARGIN, screenHeight - HUD_CARD_HEIGHT - HUD_CARD_MARGIN);
        float iconX = cardX + (HUD_ICON_AREA - HUD_ICON_SIZE) / 2.0F + 1.0F;
        float iconY = cardY + (HUD_CARD_HEIGHT - HUD_ICON_SIZE) / 2.0F;
        int iconColor = RenderUtil.ColorUtil.replAlpha(color, 220);
        int separatorColor = RenderUtil.ColorUtil.replAlpha(color, 72);

        RenderUtil.drawClientRect(context, cardX, cardY, cardWidth, HUD_CARD_HEIGHT);
        renderMarkerIcon(context, iconX, iconY, HUD_ICON_SIZE, HUD_ICON_SIZE, iconColor, markerTexture, placement.directional());

        float separatorX = cardX + HUD_ICON_AREA - 5.0F;
        RenderUtil.Round.draw(context, separatorX, cardY + 3.5F, 1.0F, 11.0F, 0.5F, separatorColor);

        float textWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, label, HUD_TEXT_SIZE);
        float textAreaX = separatorX + 4.0F;
        float textAreaWidth = cardWidth - (textAreaX - cardX) - 4.0F;
        float textX = textAreaX + (textAreaWidth - textWidth) / 2.0F;
        float textY = cardY + 11.2F;
        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, label, textX, textY, HUD_TEXT_SIZE, textColor);
    }

    private void renderWorldHudMarker(DrawContext context, int screenWidth, int screenHeight,
                                      MarkerPlacement placement, String label, int color, Identifier markerTexture) {
        float size = resolveWorldMarkerSize();
        float drawX = MathHelper.clamp(placement.x() - size / 2.0F, HUD_CARD_MARGIN, screenWidth - size - HUD_CARD_MARGIN);
        float drawY = MathHelper.clamp(placement.y() - size / 2.0F, HUD_CARD_MARGIN, screenHeight - size - HUD_CARD_MARGIN);

        renderMarkerIcon(context, drawX, drawY, size, size, color, markerTexture, false);

        float textWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, label, HUD_TEXT_SIZE);
        float labelCardWidth = Math.max(28.0F, textWidth + 8.0F);
        float labelCardX = MathHelper.clamp(drawX + size / 2.0F - labelCardWidth / 2.0F, HUD_CARD_MARGIN, screenWidth - labelCardWidth - HUD_CARD_MARGIN);
        float labelCardY = drawY + size + HUD_WORLD_LABEL_GAP;
        if (labelCardY > screenHeight - HUD_CARD_HEIGHT - HUD_CARD_MARGIN) {
            labelCardY = drawY - HUD_WORLD_LABEL_GAP - HUD_CARD_HEIGHT;
        }
        RenderUtil.drawClientRect(context, labelCardX, labelCardY, labelCardWidth, HUD_CARD_HEIGHT);

        float textX = labelCardX + (labelCardWidth - textWidth) / 2.0F;
        float textY = labelCardY + 11.2F;
        int textColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, label, textX, textY, HUD_TEXT_SIZE, textColor, true);
    }

    private void renderMarkerIcon(DrawContext context, float x, float y, float width, float height, int color,
                                  Identifier markerTexture, boolean clamped) {
        if (markerTexture == null) {
            drawFallbackHudMarker(context, Math.round(x), Math.round(y), Math.round(width), color, clamped);
            return;
        }

        if (isShaderMarkerMode()) {
            if (mc != null && mc.currentScreen != null) {
                int guiSafeColor = ShaderThemeVisuals.animatedPrimary(
                        markerShaderTheme.get(),
                        System.currentTimeMillis() * 0.0022 + x * 0.08 + y * 0.06
                );
                RenderUtil.Image.draw(context, markerTexture, x, y, width, height, RenderUtil.ColorUtil.replAlpha(guiSafeColor, clamped ? 220 : 255));
                return;
            }

            Identifier maskTexture = resolveMarkerMaskTexture();
            if (maskTexture == null) {
                RenderUtil.Image.draw(context, markerTexture, x, y, width, height, RenderUtil.ColorUtil.replAlpha(color, clamped ? 220 : 255));
                return;
            }

            ShaderThemePreset preset = ShaderThemePreset.byName(markerShaderTheme.get());
            GpsHudShaderPass.queue(maskTexture, x, y, width, height, clamped ? 220 : 255, preset);
            return;
        }

        RenderUtil.Image.draw(context, markerTexture, x, y, width, height, color);
    }

    private float resolveHudCardWidth(String label) {
        float textWidth = FontDraw.getWidth(FontDraw.FontType.MEDIUM, label, HUD_TEXT_SIZE);
        return Math.max(HUD_CARD_MIN_WIDTH, textWidth + HUD_ICON_AREA + 8.0F);
    }

    private float resolveWorldMarkerSize() {
        return HUD_WORLD_MARKER_SIZE * MathHelper.clamp(markerSize.get(), 0.5f, 2.5f);
    }

    private MarkerPlacement resolveMarkerPlacement(net.minecraft.client.MinecraftClient client, int width, int height) {
        if (width <= 0 || height <= 0) {
            return null;
        }

        CameraAngles angles = resolveCameraAngles(client);
        if (angles != null && angles.forward()) {
            float verticalFovDegrees = client.options.getFov().getValue();
            double verticalFov = Math.toRadians(verticalFovDegrees);
            double aspect = Math.max(1.0E-3D, width / (double) Math.max(1, height));
            double horizontalFov = 2.0D * Math.atan(Math.tan(verticalFov / 2.0D) * aspect);
            double horizontalHalfFovDegrees = Math.toDegrees(horizontalFov / 2.0D);
            double verticalHalfFovDegrees = verticalFovDegrees / 2.0D;

            if (Math.abs(angles.relativeYaw()) <= horizontalHalfFovDegrees
                    && Math.abs(angles.relativePitch()) <= verticalHalfFovDegrees) {
                double normalizedX = Math.tan(Math.toRadians(angles.relativeYaw())) / Math.tan(horizontalFov / 2.0D);
                double normalizedY = Math.tan(Math.toRadians(angles.relativePitch())) / Math.tan(verticalFov / 2.0D);
                float placementX = MathHelper.clamp((float) (width / 2.0D + normalizedX * width / 2.0D), HUD_MARKER_CLAMP_MARGIN, width - HUD_MARKER_CLAMP_MARGIN);
                float placementY = MathHelper.clamp((float) (height / 2.0D + normalizedY * height / 2.0D), HUD_MARKER_CLAMP_MARGIN, height - HUD_MARKER_CLAMP_MARGIN);
                return new MarkerPlacement(placementX, placementY, false);
            }
        }

        return resolveDirectionalPlacement(client, width, height);
    }

    private CameraAngles resolveCameraAngles(net.minecraft.client.MinecraftClient client) {
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        double dx = markerWorldPos.x - cameraPos.x;
        double dy = markerWorldPos.y - cameraPos.y;
        double dz = markerWorldPos.z - cameraPos.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(horizontalDistance)) {
            return null;
        }

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(1.0E-4D, horizontalDistance))));
        float relativeYaw = MathHelper.wrapDegrees(targetYaw - client.gameRenderer.getCamera().getYaw());
        float relativePitch = MathHelper.wrapDegrees(targetPitch - client.gameRenderer.getCamera().getPitch());
        return new CameraAngles(relativeYaw, relativePitch, Math.abs(relativeYaw) <= 90.0F);
    }

    private MarkerPlacement clampPlacementToEdge(float centerX, float centerY, float dx, float dy, int width, int height) {
        float maxX = centerX - HUD_EDGE_MARGIN;
        float maxY = centerY - HUD_EDGE_MARGIN;
        float scale = Float.POSITIVE_INFINITY;

        if (Math.abs(dx) > 1.0E-3F) {
            scale = Math.min(scale, maxX / Math.abs(dx));
        }
        if (Math.abs(dy) > 1.0E-3F) {
            scale = Math.min(scale, maxY / Math.abs(dy));
        }
        if (!Float.isFinite(scale)) {
            scale = 1.0F;
        }

        float clampedX = centerX + dx * scale;
        float clampedY = centerY + dy * scale;
        clampedX = MathHelper.clamp(clampedX, HUD_EDGE_MARGIN, width - HUD_EDGE_MARGIN);
        clampedY = MathHelper.clamp(clampedY, HUD_EDGE_MARGIN, height - HUD_EDGE_MARGIN);
        return new MarkerPlacement(clampedX, clampedY, true);
    }

    private MarkerPlacement resolveDirectionalPlacement(net.minecraft.client.MinecraftClient client, int width, int height) {
        Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
        double dx = markerWorldPos.x - cameraPos.x;
        double dz = markerWorldPos.z - cameraPos.z;
        double horizontalDistance = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        float yawRadians = (float) Math.toRadians(client.gameRenderer.getCamera().getYaw());
        float angle = (float) (Math.atan2(dz, dx) - yawRadians - Math.PI / 2.0D);

        float dirX = (float) Math.sin(angle);
        float dirY = (float) -Math.cos(angle);
        dirY += (float) MathHelper.clamp((cameraPos.y - markerWorldPos.y) / horizontalDistance, -0.45D, 0.45D);

        double length = Math.sqrt(dirX * dirX + dirY * dirY);
        if (!Double.isFinite(length) || length < 1.0E-4D) {
            dirX = 0.0F;
            dirY = -1.0F;
            length = 1.0D;
        }

        dirX /= (float) length;
        dirY /= (float) length;
        float centerX = width / 2.0F;
        float centerY = height / 2.0F;
        return clampPlacementToEdge(centerX, centerY, dirX, dirY, width, height);
    }

    private void drawFallbackHudMarker(DrawContext context, int x, int y, int size, int color, boolean clamped) {
        int outline = withAlpha(0xFFFFFF, clamped ? 0.78F : 0.92F);
        int fill = withAlpha(color, clamped ? 0.82F : 0.96F);
        int center = x + size / 2;
        int half = Math.max(2, size / 2);
        context.fill(center - 1, y, center + 1, y + size - 3, outline);
        context.fill(x + 1, y + half - 1, x + size - 1, y + half + 1, outline);
        context.fill(x + 3, y + 3, x + size - 3, y + size - 6, fill);
        context.fill(center - 3, y + size - 6, center + 3, y + size, fill);
    }

    private void putLine(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, int r, int g, int b, int a) {
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;
        double lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq < 1.0E-6D) {
            return;
        }

        float invLength = (float) (1.0D / Math.sqrt(lengthSq));
        float normalX = (float) dx * invLength;
        float normalY = (float) dy * invLength;
        float normalZ = (float) dz * invLength;
        buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z)
                .color(r, g, b, a)
                .normal(normalX, normalY, normalZ);
        buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z)
                .color(r, g, b, a)
                .normal(normalX, normalY, normalZ);
    }

    private RenderLayer getGpsLayer() {
        int widthKey = Math.max(1, Math.round(lineWidth.get() * 2.0F));
        return gpsLayers.computeIfAbsent(widthKey, key -> createGpsLayer(key / 2.0F));
    }

    private RenderLayer createGpsLayer(float width) {
        return RenderLayer.of(
                "gps_line_" + String.format(Locale.ROOT, "%.1f", width),
                4096,
                false,
                true,
                GPS_LINE_PIPELINE,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .build(false)
        );
    }

    private Identifier resolveMarkerTexture() {
        return resolveMarkerTextureSource();
    }

    private Identifier resolveMarkerMaskTexture() {
        Identifier sourceTexture = resolveMarkerTextureSource();
        if (sourceTexture == null || mc == null || mc.getResourceManager() == null) {
            return null;
        }

        if (cachedMaskMarkerTexture != null
                && sourceTexture.equals(cachedTintedMarkerSource)) {
            return cachedMaskMarkerTexture;
        }

        try (InputStream inputStream = mc.getResourceManager().getResource(sourceTexture).orElseThrow().getInputStream()) {
            NativeImage sourceImage = NativeImage.read(inputStream);
            NativeImage maskImage = new NativeImage(NativeImage.Format.RGBA, sourceImage.getWidth(), sourceImage.getHeight(), false);
            for (int y = 0; y < sourceImage.getHeight(); y++) {
                for (int x = 0; x < sourceImage.getWidth(); x++) {
                    int sourceArgb = sourceImage.getColorArgb(x, y);
                    int alpha = (sourceArgb >>> 24) & 0xFF;
                    maskImage.setColorArgb(x, y, (alpha << 24) | 0x00FFFFFF);
                }
            }
            sourceImage.close();

            if (mc != null) {
                mc.getTextureManager().destroyTexture(DYNAMIC_MARKER_MASK_TEXTURE);
            }
            if (maskMarkerTexture != null) {
                maskMarkerTexture.close();
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> DYNAMIC_MARKER_MASK_TEXTURE.toString(), maskImage);
            texture.setFilter(true, false);
            texture.upload();

            mc.getTextureManager().registerTexture(DYNAMIC_MARKER_MASK_TEXTURE, texture);
            maskMarkerTexture = texture;
            cachedMaskMarkerTexture = DYNAMIC_MARKER_MASK_TEXTURE;
            cachedTintedMarkerSource = sourceTexture;
            return cachedMaskMarkerTexture;
        } catch (IOException | RuntimeException exception) {
            if (mc != null) {
                mc.getTextureManager().destroyTexture(DYNAMIC_MARKER_MASK_TEXTURE);
            }
            if (maskMarkerTexture != null) {
                maskMarkerTexture.close();
                maskMarkerTexture = null;
            }
            cachedMaskMarkerTexture = null;
            Strange.LOGGER.debug("Failed to build GPS marker mask texture from {}", sourceTexture, exception);
            return null;
        }
    }

    private Identifier resolveMarkerTextureSource() {
        if (!markerTextureResolved) {
            markerTextureResolved = true;
            cachedMarkerTexture = null;
            if (mc != null && mc.getResourceManager() != null) {
                if (mc.getResourceManager().getResource(WORLD_MARKER_TEXTURE).isPresent()) {
                    cachedMarkerTexture = WORLD_MARKER_TEXTURE;
                } else if (mc.getResourceManager().getResource(HUD_MARKER_TEXTURE).isPresent()) {
                    cachedMarkerTexture = HUD_MARKER_TEXTURE;
                }
            }
        }
        return cachedMarkerTexture;
    }

    private void releaseMarkerTextures() {
        if (mc != null) {
            mc.getTextureManager().destroyTexture(DYNAMIC_MARKER_MASK_TEXTURE);
        }
        cachedMaskMarkerTexture = null;
        cachedTintedMarkerSource = null;
        if (maskMarkerTexture != null) {
            maskMarkerTexture.close();
            maskMarkerTexture = null;
        }
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        return Math.sqrt(squaredHorizontalDistance(a.x, a.z, b.x, b.z));
    }

    private static double squaredHorizontalDistance(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    private static long packColumnKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int withAlpha(int rgb, float alpha) {
        int alphaChannel = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (alphaChannel << 24) | (rgb & 0xFFFFFF);
    }

    private void sendClientMessage(String text) {
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(
                    Text.literal("[GPS] ").formatted(Formatting.GRAY)
                            .append(CommandManager.legacyText(text)),
                    false
            );
        }
    }

    private record MarkerPlacement(float x, float y, boolean directional) {
    }

    private record StepCandidate(int x, int z, Vec3d point, double score) {
    }

    private record CameraAngles(float relativeYaw, float relativePitch, boolean forward) {
    }
}
