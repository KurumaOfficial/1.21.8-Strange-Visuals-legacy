package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.Strange;
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
import ru.strange.client.utils.combat.CombatStateTracker;
import ru.strange.client.utils.render.Render3D;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.OptionalDouble;

@IModule(
        name = "FakeHitboxes",
        description = "Фейковое отображение увеличенных хитбоксов",
        category = Category.Player,
        bind = -1
)
public class FakeHitboxes extends Module {

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;

    private final ModeSetting mode = new ModeSetting("Режим", "Под прицелом", "Под прицелом", "Игроки");
    private final ModeSetting colorStyle = new ModeSetting("Style", "Default", "Default", "Theme");
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !usesThemeStyle());
    private final SliderSetting expand = new SliderSetting("Расширение", 0.28f, 0.05f, 1.5f, 0.05f, false);
    private final SliderSetting range = new SliderSetting("Дистанция", 5.0f, 2.0f, 16.0f, 0.5f, false);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true);
    private final HueSetting colorSetting = new HueSetting("Цвет", new Color(122, 210, 255));
    private final HueSetting hitColor = new HueSetting("Цвет удара", new Color(255, 120, 120));

    public FakeHitboxes() {
        addSettings(mode, colorStyle, shaderTheme, expand, range, fill, colorSetting, hitColor);
    }

    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private final int[] gradientColors = new int[4];

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        float tickDelta = event.getTickDelta();
        double rangeValue = range.get();
        double rangeSq = rangeValue * rangeValue;
        double expandValue = expand.get();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            if (mode.is("Под прицелом")) {
                renderTargeted(event.getMatrixStack(), immediate, cameraPos, tickDelta, rangeSq, expandValue);
            } else {
                Box searchBounds = mc.player.getBoundingBox().expand(rangeValue);
                for (Entity entity : mc.world.getOtherEntities(mc.player, searchBounds)) {
                    if (!shouldRenderOverlayEntity(entity, rangeSq)) {
                        continue;
                    }
                    renderExpandedBox(event.getMatrixStack(), immediate, entity, cameraPos, tickDelta, expandValue);
                }
            }

            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    private void renderTargeted(MatrixStack matrices, VertexConsumerProvider.Immediate immediate,
                                Vec3d cameraPos, float tickDelta, double rangeSq, double expandValue) {
        Entity target = getTargetedEntity();
        if (target == null || !shouldRenderOverlayEntity(target, rangeSq)) {
            return;
        }

        renderExpandedBox(matrices, immediate, target, cameraPos, tickDelta, expandValue);
    }

    private void renderExpandedBox(MatrixStack matrices, VertexConsumerProvider.Immediate immediate, Entity target,
                                   Vec3d cameraPos, float partialTicks, double expandValue) {
        double x = MathHelper.lerp(partialTicks, target.lastRenderX, target.getX());
        double y = MathHelper.lerp(partialTicks, target.lastRenderY, target.getY());
        double z = MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ());

        Box box = target.getBoundingBox().expand(expandValue);
        double minX = box.minX - target.getX() + x - cameraPos.x;
        double minY = box.minY - target.getY() + y - cameraPos.y;
        double minZ = box.minZ - target.getZ() + z - cameraPos.z;
        double maxX = box.maxX - target.getX() + x - cameraPos.x;
        double maxY = box.maxY - target.getY() + y - cameraPos.y;
        double maxZ = box.maxZ - target.getZ() + z - cameraPos.z;

        int baseColor = resolveBaseColor(target, x, y, z);
        gradientColors[0] = RenderUtil.ColorUtil.multDark(baseColor, 0.42f);
        gradientColors[1] = RenderUtil.ColorUtil.multDark(baseColor, 0.66f);
        gradientColors[2] = gradientColors[0];
        gradientColors[3] = gradientColors[1];

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (fill.get()) {
            VertexConsumer fillBuffer = immediate.getBuffer(BOX_FILL_LAYER);
            Render3D.drawBoxFill(fillBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 75);
        }

        VertexConsumer lineBuffer = immediate.getBuffer(BOX_LINE_LAYER);
        Render3D.drawBoxOutline(lineBuffer, matrix, minX, minY, minZ, maxX, maxY, maxZ, gradientColors, 255, 0.18, 0.09);
    }

    private boolean usesThemeStyle() {
        return colorStyle.is("Theme") || colorStyle.is("Shader");
    }

    private int resolveBaseColor(Entity target, double x, double y, double z) {
        float hitPulse = CombatStateTracker.getInstance().getEntityPulse(target, CombatStateTracker.Marker.HIT, 420L);
        int themedBase = usesThemeStyle()
                ? ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), x * 0.43 + y * 0.27 + z * 0.19)
                : colorSetting.getRGB();
        return RenderUtil.ColorUtil.interpolate(themedBase, hitColor.getRGB(), hitPulse);
    }

    private boolean shouldRenderOverlayEntity(Entity entity, double rangeSq) {
        if (entity == null || mc.world == null || mc.player == null) {
            return false;
        }
        if (entity == mc.player || entity.isRemoved()) {
            return false;
        }
        if (mc.player.squaredDistanceTo(entity) > rangeSq) {
            return false;
        }

        return !mode.is("Под прицелом") || entity == getTargetedEntity();
    }

    private boolean shouldOverrideDebugEntity(Entity entity) {
        return entity != null && !entity.isRemoved();
    }

    private Entity getTargetedEntity() {
        HitResult hitResult = mc.crosshairTarget;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult.getEntity();
        }
        return null;
    }

    private Box getExpandedBoundingBox(Entity entity) {
        return entity.getBoundingBox().expand(expand.get());
    }

    private float[] getDebugColor(boolean green, Entity entity) {
        if (green) {
            return new float[] {0.0f, 1.0f, 0.0f};
        }

        int color = resolveBaseColor(entity, entity.getX(), entity.getY(), entity.getZ());
        return new float[] {
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f
        };
    }

    private static FakeHitboxes getInstance() {
        if (Strange.get == null || Strange.get.manager == null) {
            return null;
        }
        return Strange.get.manager.get(FakeHitboxes.class);
    }

    public static boolean shouldOverrideDebugHitbox(Entity entity) {
        FakeHitboxes module = getInstance();
        return module != null && module.enable && module.shouldOverrideDebugEntity(entity);
    }

    public static Box getDebugBoundingBox(Entity entity) {
        FakeHitboxes module = getInstance();
        return module == null ? entity.getBoundingBox() : module.getExpandedBoundingBox(entity);
    }

    public static float[] getDebugHitboxColor(Entity entity, boolean green) {
        FakeHitboxes module = getInstance();
        return module == null ? new float[] {1.0f, 1.0f, 1.0f} : module.getDebugColor(green, entity);
    }

    private static final RenderPipeline BOX_FILL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "fake_hitboxes_fill"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline BOX_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "fake_hitboxes_line"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOX_FILL_LAYER = RenderLayer.of(
            "strange_fake_hitboxes_fill",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            BOX_FILL_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder().build(false)
    );

    private static final RenderLayer BOX_LINE_LAYER = RenderLayer.of(
            "strange_fake_hitboxes_line",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            BOX_LINE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.2)))
                    .build(false)
    );
}
