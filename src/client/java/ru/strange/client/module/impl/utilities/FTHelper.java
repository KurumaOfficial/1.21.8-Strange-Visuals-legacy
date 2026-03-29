package ru.strange.client.module.impl.utilities;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.*;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.*;

@IModule(
        name = "ФТХелпер",
        description = "Помощник для бросков и дистанций",
        category = Category.Utilities,
        bind = -1
)
public class FTHelper extends Module {
    private static final BooleanSetting potionEnabled = new BooleanSetting("Проекция Зелия", true);
    private static final HueSetting potionColor = new HueSetting("Цвет", new Color(120, 220, 120));

    private static final BooleanSetting snowballEnabled = new BooleanSetting("Проекция Снежка", true);
    private static final HueSetting snowballColor = new HueSetting("Цвет", new Color(120, 180, 255));

    private static final BooleanSetting enderEyeEnabled = new BooleanSetting("Проекция Дезориентация", true);
    private static final HueSetting enderEyeColor = new HueSetting("Цвет", new Color(210, 120, 255));

    private static final BooleanSetting sugarEnabled = new BooleanSetting("Проекция Явной пыли", true);
    private static final HueSetting sugarColor = new HueSetting("Цвет", new Color(255, 200, 120));

    private static final BooleanSetting fireChargeEnabled = new BooleanSetting("Проекция Огненый смерчь", true);
    private static final HueSetting fireChargeColor = new HueSetting("Цвет", new Color(255, 140, 80));


    private static final int LINE_BUFFER_SIZE_BYTES = 1 << 10;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(net.minecraft.util.Identifier.of("strange", "fthelper_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "fthelper_lines",
            LINE_BUFFER_SIZE_BYTES,
            false,
            true,
            LINE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(java.util.OptionalDouble.of(2.0)))
                    .build(false)
    );

    public FTHelper() {
        addSettings(
                potionEnabled, potionColor,
                snowballEnabled, snowballColor,
                enderEyeEnabled, enderEyeColor,
                sugarEnabled, sugarColor,
                fireChargeEnabled, fireChargeColor
        );
    }

    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) return;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();

        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            VertexConsumer lineBuffer = immediate.getBuffer(LINE_LAYER);

            if (potionEnabled.get() && isThrowablePotion(item)) {
                HitResult hit = traceProjectile(mc.player, event.getTickDelta(), 0.5f, 0.05f);
                Vec3d center = getHitCenter(hit);
                int color = applyAlpha(potionColor.getRGB(), 200);
                if (center != null) {
                    drawCircle(lineBuffer, matrix, cameraPos, center, 4.0f, markRedIfEntity(center, 4.0f, color));
                }
            } else if (snowballEnabled.get() && item == Items.SNOWBALL) {
                HitResult hit = traceProjectile(mc.player, event.getTickDelta(), 1.5f, 0.03f);
                Vec3d center = getHitCenter(hit);
                int color = applyAlpha(snowballColor.getRGB(), 200);
                if (center != null) {
                    drawCircle(lineBuffer, matrix, cameraPos, center, 7.0f, markRedIfEntity(center, 7.0f, color));
                }
            } else if (item == Items.ENDER_EYE && enderEyeEnabled.get()) {
                Vec3d center = getPlayerCenter();
                if (center != null) {
                    int color = applyAlpha(enderEyeColor.getRGB(), 200);
                    drawCircle(lineBuffer, matrix, cameraPos, center, 10.0f, markRedIfEntity(center, 10.0f, color));
                }
            } else if (item == Items.SUGAR && sugarEnabled.get()) {
                Vec3d center = getPlayerCenter();
                if (center != null) {
                    int color = applyAlpha(sugarColor.getRGB(), 200);
                    drawCircle(lineBuffer, matrix, cameraPos, center, 10.0f, markRedIfEntity(center, 10.0f, color));
                }
            } else if (item == Items.FIRE_CHARGE && fireChargeEnabled.get()) {
                Vec3d center = getPlayerCenter();
                if (center != null) {
                    int color = applyAlpha(fireChargeColor.getRGB(), 200);
                    drawCircle(lineBuffer, matrix, cameraPos, center, 10.0f, markRedIfEntity(center, 10.0f, color));
                }
            }

            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private static boolean isThrowablePotion(Item item) {
        return item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
    }

    private static HitResult traceProjectile(PlayerEntity player, float tickDelta, float velocity, float gravity) {
        Vec3d pos = player.getEyePos();
        Vec3d dir = player.getRotationVec(tickDelta).normalize();
        Vec3d vel = dir.multiply(velocity);

        for (int i = 0; i < 200; i++) {
            Vec3d nextPos = pos.add(vel);
            HitResult hit = player.getWorld().raycast(new RaycastContext(
                    pos,
                    nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    player
            ));
            if (hit.getType() != HitResult.Type.MISS) {
                return hit;
            }
            pos = nextPos;
            vel = vel.multiply(0.99f).subtract(0.0, gravity, 0.0);
            if (pos.y < player.getWorld().getBottomY()) break;
        }
        return null;
    }

    private static Vec3d getHitCenter(HitResult hit) {
        if (!(hit instanceof BlockHitResult blockHit)) return null;
        BlockPos pos = blockHit.getBlockPos();
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.01, pos.getZ() + 0.5);
    }

    private static Vec3d getPlayerCenter() {
        Vec3d pos = mc.player.getPos();
        return new Vec3d(pos.x, Math.floor(pos.y) + 0.01, pos.z);
    }

    private static void drawCircle(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, Vec3d center, float radius, int color) {
        int segments = 90;
        double step = (Math.PI * 2.0) / segments;

        double baseX = center.x - cameraPos.x;
        double baseY = center.y - cameraPos.y;
        double baseZ = center.z - cameraPos.z;

        for (int i = 0; i < segments; i++) {
            double a1 = i * step;
            double a2 = (i + 1) * step;

            double x1 = baseX + Math.cos(a1) * radius;
            double z1 = baseZ + Math.sin(a1) * radius;
            double x2 = baseX + Math.cos(a2) * radius;
            double z2 = baseZ + Math.sin(a2) * radius;

            drawLine(buffer, matrix, x1, baseY, z1, x2, baseY, z2, color);
        }
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    private static void drawLine(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, Vec3d start, Vec3d end, int color) {
        drawLine(buffer, matrix,
                start.x - cameraPos.x, start.y - cameraPos.y, start.z - cameraPos.z,
                end.x - cameraPos.x, end.y - cameraPos.y, end.z - cameraPos.z,
                color);
    }

    private static boolean isLineHittingVisibleEntity(Vec3d start, Vec3d end) {
        double minX = Math.min(start.x, end.x) - 0.2;
        double minY = Math.min(start.y, end.y) - 0.2;
        double minZ = Math.min(start.z, end.z) - 0.2;
        double maxX = Math.max(start.x, end.x) + 0.2;
        double maxY = Math.max(start.y, end.y) + 0.2;
        double maxZ = Math.max(start.z, end.z) + 0.2;

        for (Entity entity : mc.world.getOtherEntities(mc.player, new Box(minX, minY, minZ, maxX, maxY, maxZ))) {
            if (entity == mc.player || entity.isInvisible()) continue;
            Box box = entity.getBoundingBox().expand(0.1);
            if (intersectsSegment(box, start, end)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectsSegment(Box box, Vec3d start, Vec3d end) {
        double[] range = {0.0, 1.0};
        double dx = end.x - start.x;
        double dy = end.y - start.y;
        double dz = end.z - start.z;

        if (!slabIntersect(start.x, dx, box.minX, box.maxX, range)) return false;
        if (!slabIntersect(start.y, dy, box.minY, box.maxY, range)) return false;
        if (!slabIntersect(start.z, dz, box.minZ, box.maxZ, range)) return false;
        return range[1] >= range[0] && range[1] >= 0.0 && range[0] <= 1.0;
    }

    private static boolean slabIntersect(double start, double dir, double min, double max, double[] range) {
        if (Math.abs(dir) < 1.0e-8) {
            if (start < min || start > max) return false;
            return true;
        }
        double inv = 1.0 / dir;
        double t1 = (min - start) * inv;
        double t2 = (max - start) * inv;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        double newMin = Math.max(range[0], t1);
        double newMax = Math.min(range[1], t2);
        if (newMax < newMin) return false;
        range[0] = newMin;
        range[1] = newMax;
        return true;
    }

    private static int applyAlpha(int color, int alpha) {
        return RenderUtil.ColorUtil.replAlpha(color, alpha);
    }

    private static int markRedIfEntity(Vec3d center, float radius, int color) {
        if (isEntityInsideRadius(center, radius)) {
            return applyAlpha(Color.RED.getRGB(), 255);
        }
        return color;
    }

    private static boolean isEntityInsideRadius(Vec3d center, float radius) {
        double radiusSq = radius * radius;
        Box searchBounds = new Box(
                center.x - radius,
                center.y - radius,
                center.z - radius,
                center.x + radius,
                center.y + radius,
                center.z + radius
        );

        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBounds)) {
            if (entity == mc.player || entity.isInvisible()) continue;
            Vec3d pos = entity.getPos();
            double dx = pos.x - center.x;
            double dy = pos.y - center.y;
            double dz = pos.z - center.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                return true;
            }
        }
        return false;
    }
}
