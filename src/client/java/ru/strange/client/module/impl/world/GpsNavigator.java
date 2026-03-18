package ru.strange.client.module.impl.world;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * .gps x y z
 * Визуальный маршрут до точки + усечение хвоста позади игрока.
 * Подключи обработку чата сам: вызовите GpsNavigator.handleChat(message).
 */
@IModule(
        name = "ГПС",
        description = "Векторный маршрут до координат",
        category = Category.World,
        bind = -1
)
public class GpsNavigator extends Module {

    public static HueSetting colorSetting = new HueSetting("Цвет", new Color(131, 166, 232));
    public static SliderSetting width = new SliderSetting("Толщина", 0.06f, 0.01f, 0.2f, 0.005f, false);
    public static BooleanSetting trimBehind = new BooleanSetting("Обрезать хвост", true);

    public GpsNavigator() {
        addSettings(colorSetting, width, trimBehind);
    }

    private static final int LINE_BUFFER_SIZE_BYTES = 1 << 10;

    private static final RenderPipeline GPS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(net.minecraft.util.Identifier.of("strange", "gps_path_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(com.mojang.blaze3d.platform.DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(com.mojang.blaze3d.pipeline.BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer GPS_LAYER = RenderLayer.of(
            "strange_gps_path",
            LINE_BUFFER_SIZE_BYTES,
            false,
            true,
            GPS_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    /** Маршрут в мировых координатах (по центрам блоков / промежуточным точкам). */
    private final List<Vec3d> pathPoints = new ArrayList<>();
    private int headIndex = 0; // индекс ближайшей к игроку точки вперёд по маршруту

    // ===================== Публичный API для чата ======================

    /**
     * Вызвать из хука отправки чата.
     * Возвращает true, если сообщение было обработано и не должно уходить на сервер.
     */
    public static boolean handleChat(String message) {
        message = message.trim();
        if (!message.toLowerCase().startsWith(".gps")) return false;

        String[] split = message.split("\\s+");
        if (split.length != 4) {
            sendClientMessage("Использование: .gps <x> <y> <z>");
            return true;
        }

        try {
            int x = Integer.parseInt(split[1]);
            int y = Integer.parseInt(split[2]);
            int z = Integer.parseInt(split[3]);

            if (mc == null || mc.world == null || mc.player == null) {
                sendClientMessage("Мир ещё не загружен.");
                return true;
            }

            GpsNavigator gps = Strange.get.manager.get(GpsNavigator.class);
            if (gps == null) {
                sendClientMessage("Модуль GPS выключен.");
                return true;
            }

            gps.computePathTo(new BlockPos(x, y, z));
            return true;
        } catch (NumberFormatException ex) {
            sendClientMessage("Координаты должны быть целыми числами.");
            return true;
        }
    }

    // ===================== Жизненный цикл модуля =======================

    @Override
    public void toggle() {
        super.toggle();
        pathPoints.clear();
        headIndex = 0;
    }

    @EventInit
    public void onWorldChange(EventChangeWorld e) {
        pathPoints.clear();
        headIndex = 0;
    }

    // Обрезаем хвост позади игрока
    @EventInit
    public void onMotion(EventMotion e) {
        if (pathPoints.isEmpty() || !trimBehind.get()) return;
        if (mc.player == null) return;

        Vec3d playerPos = mc.player.getPos();

        // Находим ближайший сегмент вперёд по пути
        int bestIndex = headIndex;
        double bestDistSq = Double.MAX_VALUE;

        for (int i = headIndex; i < pathPoints.size(); i++) {
            Vec3d p = pathPoints.get(i);
            double d = playerPos.squaredDistanceTo(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                bestIndex = i;
            }
        }

        // Удаляем всё позади этой точки
        if (bestIndex > headIndex && bestIndex < pathPoints.size()) {
            pathPoints.subList(0, bestIndex).clear();
        }
        headIndex = 0;
    }

    // Рендер линии
    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (pathPoints.size() < 2) return;

        MatrixStack matrices = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        BufferAllocator allocator = new BufferAllocator(1 << 18);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

        try {
            VertexConsumer buffer = immediate.getBuffer(GPS_LAYER);
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            Matrix4f matrix = matrices.peek().getPositionMatrix();

            int baseColor = colorSetting.getRGB();

            // Лёгкое "сглаживание" маршрута по Catmull-Rom сплайну
            List<Vec3d> smooth = buildSmoothPath(pathPoints);

            int n = smooth.size();
            for (int i = 0; i < n - 1; i++) {
                Vec3d a = liftIfInsideBlock(smooth.get(i));
                Vec3d b = liftIfInsideBlock(smooth.get(i + 1));

                float t = (float) i / (float) (n - 1);
                // Плавный градиент: начало/конец мягче
                float edgeFade = (float) Math.min(t / 0.1f, (1.0f - t) / 0.1f);
                edgeFade = MathHelper.clamp(edgeFade, 0.0f, 1.0f);
                float alphaPc = 0.20f + 0.80f * edgeFade;

                int col = RenderUtil.ColorUtil.multAlpha(baseColor, alphaPc);

                int r = RenderUtil.ColorUtil.red(col);
                int g = RenderUtil.ColorUtil.green(col);
                int bC = RenderUtil.ColorUtil.blue(col);
                int aC = RenderUtil.ColorUtil.alpha(col);

                buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).color(r, g, bC, aC);
                buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).color(r, g, bC, aC);
            }

            matrices.pop();
            immediate.draw();
        } finally {
            allocator.close();
        }
    }

    // ===================== Поиск пути =======================

    private void computePathTo(BlockPos target) {
        pathPoints.clear();
        headIndex = 0;

        if (mc.player == null || mc.world == null) return;
        PlayerEntity player = mc.player;

        BlockPos start = player.getBlockPos();

        // Оцениваем расстояние до цели и подбираем лимиты под него
        int manhattan = start.getManhattanDistance(target);
        int maxRadius = Math.max(64, manhattan + 16);              // насколько далеко от старта можем уходить
        int maxNodes = Math.min(12000, Math.max(512, maxRadius * 32)); // ограничение по количеству узлов

        List<BlockPos> nodePath = findPathAStar(start, target, maxNodes, maxRadius);

        if (!nodePath.isEmpty()) {
            for (BlockPos pos : nodePath) {
                pathPoints.add(Vec3d.ofCenter(pos));
            }
            sendClientMessage("маршрут найден (A*), точек: " + pathPoints.size());
            return;
        }

        // Фоллбек: прямая линия до цели, даже если A* не нашёл путь.
        BlockPos end = target;
        Vec3d startCenter = Vec3d.ofCenter(start);
        Vec3d endCenter = Vec3d.ofCenter(end);

        double dist = startCenter.distanceTo(endCenter);
        int steps = Math.max(2, Math.min(4096, (int) (dist * 2.0))); // ~0.5 блока на сегмент, но с разумным лимитом

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            double x = MathHelper.lerp(t, startCenter.x, endCenter.x);
            double y = MathHelper.lerp(t, startCenter.y, endCenter.y);
            double z = MathHelper.lerp(t, startCenter.z, endCenter.z);
            pathPoints.add(new Vec3d(x, y, z));
        }

        sendClientMessage("прямой маршрут построен, точек: " + pathPoints.size());
    }

    /**
     * Простой A* по 3D сетке с ограничением по радиусу и высоте.
     */
    private List<BlockPos> findPathAStar(BlockPos start, BlockPos goal, int maxNodes, int maxRadius) {
        if (start.equals(goal)) return Collections.singletonList(start);

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Map<BlockPos, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0.0, heuristic(start, goal));
        open.add(startNode);
        allNodes.put(start, startNode);

        int explored = 0;

        while (!open.isEmpty() && explored < maxNodes) {
            Node current = open.poll();
            explored++;

            if (current.pos.equals(goal) || current.pos.getSquaredDistance(goal) <= 4) {
                return reconstruct(current);
            }

            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos nextPos = current.pos.offset(dir);
                // шаг вверх/вниз максимум на 1 блок
                nextPos = adjustYForStep(nextPos);
                if (nextPos == null) continue;

                if (nextPos.getManhattanDistance(start) > maxRadius) continue;

                double tentativeG = current.gScore + 1.0;

                Node existing = allNodes.get(nextPos);
                if (existing != null && tentativeG >= existing.gScore) continue;

                double h = heuristic(nextPos, goal);
                Node node = new Node(nextPos, current, tentativeG, tentativeG + h);
                allNodes.put(nextPos, node);
                open.add(node);
            }
        }

        return Collections.emptyList();
    }

    private double heuristic(BlockPos a, BlockPos b) {
        return a.getSquaredDistance(b);
    }

    /**
     * Возвращает позицию с поправкой по Y (шаг вверх/вниз на 1 блок), если там можно стоять.
     */
    private BlockPos adjustYForStep(BlockPos base) {
        if (isWalkable(base)) return base;
        BlockPos up = base.up();
        if (isWalkable(up)) return up;
        BlockPos down = base.down();
        if (isWalkable(down)) return down;
        return null;
    }

    /**
     * Можно ли игроку стоять в этом блоке (ноги + голова не пересекаются с коллизиями, под ногами есть опора).
     */
    private boolean isWalkable(BlockPos pos) {
        if (mc.world == null) return false;

        BlockPos feet = pos;
        BlockPos head = pos.up();
        BlockPos below = pos.down();

        if (!isAiry(feet) || !isAiry(head)) return false;

        BlockState belowState = mc.world.getBlockState(below);
        if (belowState.isAir()) return false;

        VoxelShape shape = belowState.getCollisionShape(mc.world, below);
        return !shape.isEmpty();
    }

    private boolean isAiry(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isAir() || state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private List<BlockPos> reconstruct(Node node) {
        List<BlockPos> result = new ArrayList<>();
        Node current = node;
        while (current != null) {
            result.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(result);
        return result;
    }

    private static class Node {
        final BlockPos pos;
        final Node parent;
        final double gScore;
        final double fScore;

        Node(BlockPos pos, Node parent, double gScore, double fScore) {
            this.pos = pos;
            this.parent = parent;
            this.gScore = gScore;
            this.fScore = fScore;
        }
    }

    private static void sendClientMessage(String text) {
        if (mc != null && mc.player != null) {
            mc.player.sendMessage(
                    Text.literal("[GPS] ")
                            .formatted(Formatting.GRAY)
                            .append(Text.literal(text).formatted(Formatting.WHITE)),
                    false
            );
        }
    }

    // ===================== Вспомогательные методы для сглаживания/подъёма линии =======================

    /**
     * Поднимает точку вверх до ближайшего "воздуха", если она находится внутри твёрдого блока.
     */
    private Vec3d liftIfInsideBlock(Vec3d point) {
        if (mc.world == null) return point;
        BlockPos pos = BlockPos.ofFloored(point);
        BlockState state = mc.world.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mc.world, pos);
        if (shape.isEmpty()) return point;

        // Находим верхнюю границу коллизии и поднимаем чуть выше
        double topY = shape.getMax(net.minecraft.util.math.Direction.Axis.Y) + pos.getY();
        return new Vec3d(point.x, topY + 0.1, point.z);
    }

    /**
     * Строит сглаженный путь на основе исходных точек, используя Catmull-Rom сплайн.
     */
    private List<Vec3d> buildSmoothPath(List<Vec3d> original) {
        int size = original.size();
        if (size <= 2) return new ArrayList<>(original);

        List<Vec3d> out = new ArrayList<>();

        // Добавляем первую точку
        out.add(original.get(0));

        int segments = 8; // сколько под-отрезков между опорными точками

        for (int i = 0; i < size - 1; i++) {
            Vec3d p0 = i == 0 ? original.get(i) : original.get(i - 1);
            Vec3d p1 = original.get(i);
            Vec3d p2 = original.get(i + 1);
            Vec3d p3 = i + 2 < size ? original.get(i + 2) : original.get(i + 1);

            for (int j = 1; j <= segments; j++) {
                double t = (double) j / (double) segments;
                Vec3d pt = catmullRom(p0, p1, p2, p3, t);
                out.add(pt);
            }
        }

        return out;
    }

    private Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5 * ((2.0 * p1.x) +
                (-p0.x + p2.x) * t +
                (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 +
                (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3);

        double y = 0.5 * ((2.0 * p1.y) +
                (-p0.y + p2.y) * t +
                (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 +
                (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3);

        double z = 0.5 * ((2.0 * p1.z) +
                (-p0.z + p2.z) * t +
                (2.0 * p0.z - 5.0 * p1.z + 4.0 * p2.z - p3.z) * t2 +
                (-p0.z + 3.0 * p1.z - 3.0 * p2.z + p3.z) * t3);

        return new Vec3d(x, y, z);
    }
}

