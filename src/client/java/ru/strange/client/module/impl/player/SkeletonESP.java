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
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
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
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

@IModule(
        name = "Skeleton ESP",
        description = "Скелетный ESP по сущностям",
        category = Category.Player,
        bind = -1
)
public class SkeletonESP extends Module {

    private static final int BUFFER_SIZE = 1 << 16;

    private final SliderSetting range = new SliderSetting("Дистанция", 32.0f, 8.0f, 64.0f, 1.0f, false);
    private final SliderSetting lineWidth = new SliderSetting("Толщина", 2.2f, 1.0f, 5.0f, 0.2f, false);
    private final ModeSetting colorStyle = new ModeSetting("Style", "Default", "Default", "Theme");
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !usesThemeStyle());
    private final HueSetting color = new HueSetting("Цвет", new Color(140, 210, 255));
    private final BooleanSetting hitColorize = new BooleanSetting("HitColor", true);
    private final HueSetting hitColor = new HueSetting("Цвет удара", new Color(255, 115, 115)).hidden(() -> !hitColorize.get());

    private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
    private final Map<Integer, RenderLayer> depthLayerCache = new ConcurrentHashMap<>();
    private final Map<Integer, RenderLayer> noDepthLayerCache = new ConcurrentHashMap<>();
    private float lastLineWidth = -1f;

    public SkeletonESP() {
        addSettings(range, lineWidth, colorStyle, shaderTheme, color, hitColorize, hitColor);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        double rangeValue = range.get();
        double rangeSq = rangeValue * rangeValue;
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrixStack();
        float tickDelta = event.getTickDelta();

        float currentWidth = lineWidth.get();
        if (Math.abs(currentWidth - lastLineWidth) > 0.01f) {
            depthLayerCache.clear();
            noDepthLayerCache.clear();
            lastLineWidth = currentWidth;
        }
        RenderLayer lineLayer = getLineLayer(false, currentWidth);
        RenderLayer xrayLayer = getLineLayer(true, currentWidth + 0.2f);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        try {
            if (shouldRender(mc.player, rangeSq, true)) {
                int selfColor = resolveColor(mc.player, tickDelta);
                if (shouldRenderThroughModel(mc.player, true)) {
                    renderSkeleton(immediate.getBuffer(xrayLayer), matrix, cameraPos, mc.player, tickDelta, selfColor, 165);
                }
                renderSkeleton(immediate.getBuffer(lineLayer), matrix, cameraPos, mc.player, tickDelta, selfColor);
            }

            for (Entity entity : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(rangeValue))) {
                if (!(entity instanceof LivingEntity living) || !shouldRender(living, rangeSq, false)) {
                    continue;
                }

                int skeletonColor = resolveColor(living, tickDelta);
                if (shouldRenderThroughModel(living, false)) {
                    renderSkeleton(immediate.getBuffer(xrayLayer), matrix, cameraPos, living, tickDelta, skeletonColor, 110);
                }
                renderSkeleton(immediate.getBuffer(lineLayer), matrix, cameraPos, living, tickDelta, skeletonColor);
            }

            immediate.draw();
        } finally {
            allocator.clear();
        }
    }

    private boolean shouldRender(LivingEntity entity, double rangeSq, boolean self) {
        if (entity == null || entity.isRemoved() || entity.isInvisible()) {
            return false;
        }

        if (self) {
            if (mc.options == null || mc.options.getPerspective().isFirstPerson()) {
                return false;
            }
            return true;
        }

        if (entity == mc.player) {
            return false;
        }

        if (entity instanceof PlayerEntity player && player.isSpectator()) {
            return false;
        }

        if (mc.player.squaredDistanceTo(entity) > rangeSq) {
            return false;
        }

        return true;
    }

    private boolean shouldRenderThroughModel(LivingEntity entity, boolean self) {
        if (self) {
            return true;
        }
        return mc.player != null && mc.player.canSee(entity);
    }

    private int resolveColor(LivingEntity entity, float tickDelta) {
        double phase = entity.getId() * 0.37 + (entity.age + tickDelta) * 0.15;
        int baseColor = usesThemeStyle()
                ? ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), phase)
                : color.getRGB();

        float hitPulse = hitColorize.get()
                ? CombatStateTracker.getInstance().getEntityPulse(entity, CombatStateTracker.Marker.HIT, 420L)
                : 0.0f;
        return RenderUtil.ColorUtil.interpolate(baseColor, hitColor.getRGB(), hitPulse);
    }

    private boolean usesThemeStyle() {
        return colorStyle.is("Theme") || colorStyle.is("Shader");
    }

    private void renderSkeleton(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, LivingEntity entity, float tickDelta, int color) {
        renderSkeleton(buffer, matrix, cameraPos, entity, tickDelta, color, 220);
    }

    private void renderSkeleton(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, LivingEntity entity, float tickDelta, int color, int alpha) {
        if (entity instanceof PlayerEntity player) {
            renderPlayerSkeleton(buffer, matrix, cameraPos, player, tickDelta, color, alpha);
            return;
        }

        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - cameraPos.x;
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - cameraPos.y;
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - cameraPos.z;

        float bodyYaw = MathHelper.lerp(tickDelta, entity.lastBodyYaw, entity.bodyYaw);
        float yawRad = (float) Math.toRadians(bodyYaw);
        double h = entity.getHeight();
        double w = entity.getWidth();

        double crouchOffset = entity.isSneaking() ? -0.10 : 0.0;
        double footY = 0.02 + crouchOffset;
        double kneeY = h * 0.34 + crouchOffset;
        double pelvisY = h * 0.54 + crouchOffset;
        double chestY = h * 0.75 + crouchOffset;
        double neckY = h * 0.84 + crouchOffset;
        double headY = h * 0.95 + crouchOffset;
        double handY = h * 0.48 + crouchOffset;

        float limbPhase = entity.limbAnimator.getAnimationProgress(tickDelta);
        float limbAmp = MathHelper.clamp(entity.limbAnimator.getAmplitude(tickDelta), 0.0f, 1.0f);
        if (!entity.limbAnimator.isLimbMoving() || limbAmp < 0.045f) {
            limbAmp = 0.0f;
        } else {
            // Damp tiny residual amplitudes after stop so skeleton does not twitch while idling.
            limbAmp *= limbAmp;
        }

        float attackSwing = MathHelper.clamp(entity.getHandSwingProgress(tickDelta), 0.0f, 1.0f);
        float armSwing = MathHelper.sin(limbPhase) * (0.24f * limbAmp)
            + MathHelper.sin(attackSwing * (float) Math.PI) * 0.26f;
        float legSwing = MathHelper.sin(limbPhase + (float) Math.PI) * (0.34f * limbAmp);
        float walkBob = MathHelper.sin(limbPhase * 2.0f) * 0.02f * limbAmp;

        pelvisY += walkBob;
        chestY += walkBob;
        neckY += walkBob;
        headY += walkBob;

        double shoulder = Math.max(0.18, w * 0.45);
        double hip = Math.max(0.14, w * 0.28);

        Vec3d pelvis = point(x, y, z, 0.0, pelvisY, 0.0, yawRad);
        Vec3d chest = point(x, y, z, 0.0, chestY, 0.0, yawRad);
        Vec3d neck = point(x, y, z, 0.0, neckY, 0.0, yawRad);
        Vec3d head = point(x, y, z, 0.0, headY, 0.0, yawRad);

        Vec3d shoulderL = point(x, y, z, shoulder, chestY, 0.0, yawRad);
        Vec3d shoulderR = point(x, y, z, -shoulder, chestY, 0.0, yawRad);
        Vec3d handL = point(x, y, z, shoulder * 1.12, handY + Math.abs(armSwing) * 0.08, -armSwing, yawRad);
        Vec3d handR = point(x, y, z, -shoulder * 1.12, handY + Math.abs(armSwing) * 0.08, armSwing, yawRad);

        Vec3d hipL = point(x, y, z, hip, pelvisY, 0.0, yawRad);
        Vec3d hipR = point(x, y, z, -hip, pelvisY, 0.0, yawRad);
        Vec3d kneeL = point(x, y, z, hip, kneeY, 0.02 - legSwing * 0.55, yawRad);
        Vec3d kneeR = point(x, y, z, -hip, kneeY, 0.02 + legSwing * 0.55, yawRad);
        Vec3d footL = point(x, y, z, hip, footY + Math.abs(legSwing) * 0.10, 0.03 - legSwing, yawRad);
        Vec3d footR = point(x, y, z, -hip, footY + Math.abs(legSwing) * 0.10, 0.03 + legSwing, yawRad);

        line(buffer, matrix, head, neck, color, alpha);
        line(buffer, matrix, neck, chest, color, alpha);
        line(buffer, matrix, chest, pelvis, color, alpha);

        line(buffer, matrix, shoulderL, shoulderR, color, alpha);
        line(buffer, matrix, shoulderL, handL, color, alpha);
        line(buffer, matrix, shoulderR, handR, color, alpha);

        line(buffer, matrix, hipL, hipR, color, alpha);
        line(buffer, matrix, pelvis, hipL, color, alpha);
        line(buffer, matrix, pelvis, hipR, color, alpha);
        line(buffer, matrix, hipL, kneeL, color, alpha);
        line(buffer, matrix, kneeL, footL, color, alpha);
        line(buffer, matrix, hipR, kneeR, color, alpha);
        line(buffer, matrix, kneeR, footR, color, alpha);
    }

    private void renderPlayerSkeleton(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos, PlayerEntity player, float tickDelta, int color, int alpha) {
        List<Vec3d[]> bones = buildPlayerBones(player, cameraPos, tickDelta);
        for (Vec3d[] bone : bones) {
            line(buffer, matrix, bone[0], bone[1], color, alpha);
        }
    }

    private List<Vec3d[]> buildPlayerBones(PlayerEntity player, Vec3d cameraPos, float tickDelta) {
        List<Vec3d[]> bones = new ArrayList<>(15);

        double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - cameraPos.x;
        double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - cameraPos.y;
        double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - cameraPos.z;

        float bodyYaw = MathHelper.lerp(tickDelta, player.lastBodyYaw, player.bodyYaw);
        float headYaw = player.getHeadYaw();
        float pitch = player.getPitch(tickDelta);

        float swing = player.limbAnimator.getAnimationProgress(tickDelta);
        float swingAmt = Math.min(MathHelper.clamp(player.limbAnimator.getAmplitude(tickDelta), 0.0f, 1.0f), 1.0f) * 0.5f;
        float handSwing = MathHelper.clamp(player.getHandSwingProgress(tickDelta), 0.0f, 1.0f);

        boolean elytra = player.isGliding();
        boolean sneak = player.isSneaking();
        double height = player.getHeight();

        MatrixStack stack = new MatrixStack();
        stack.translate(x, y, z);

        if (sneak && !elytra) {
            stack.translate(0.0, 0.125, 0.0);
        }

        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));

        float bodyPitchDeg = 0.0f;
        if (elytra) {
            bodyPitchDeg = 90.0f + pitch;
        } else if (sneak) {
            bodyPitchDeg = 28.0f;
        }

        if (elytra || sneak) {
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(bodyPitchDeg));
        }

        if (sneak && !elytra) {
            stack.translate(0.0, -0.13, 0.0);
        }

        stack.push();
        stack.translate(0.0, height * 0.75, 0.0);
        Vec3d neck = stackPosition(stack);

        stack.push();
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw - headYaw));
        if (!elytra) {
            stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        }
        stack.translate(0.0, height * 0.15, 0.0);
        Vec3d head = stackPosition(stack);
        stack.pop();

        stack.push();
        stack.translate(0.25, 0.0, 0.0);
        Vec3d leftShoulder = stackPosition(stack);

        float leftArmRot;
        if (elytra) {
            leftArmRot = -0.2f;
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-5.0f));
        } else {
            leftArmRot = MathHelper.cos(swing * 0.6662f + (float) Math.PI) * 0.8f * swingAmt;
        }

        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.toDegrees(leftArmRot)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d leftElbow = stackPosition(stack);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0.0f, leftArmRot * 15.0f)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d leftHand = stackPosition(stack);
        stack.pop();

        stack.push();
        stack.translate(-0.25, 0.0, 0.0);
        Vec3d rightShoulder = stackPosition(stack);

        float rightArmRot;
        if (elytra) {
            rightArmRot = -0.2f;
            stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(5.0f));
        } else {
            rightArmRot = MathHelper.cos(swing * 0.6662f) * 0.8f * swingAmt;
        }

        if (handSwing > 0.0f && !elytra) {
            float swingProgress = 1.0f - handSwing;
            swingProgress *= swingProgress;
            float swingRot = MathHelper.sin(swingProgress * (float) Math.PI);

            float headYawDiff = MathHelper.wrapDegrees(headYaw - bodyYaw);
            float yawFactor = MathHelper.clamp(headYawDiff / 75.0f, -1.0f, 1.0f);

            stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(swingRot * 15.0f * yawFactor));
            rightArmRot += -swingRot * 0.8f;
        }

        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.toDegrees(rightArmRot)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d rightElbow = stackPosition(stack);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.max(0.0f, rightArmRot * 15.0f)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d rightHand = stackPosition(stack);
        stack.pop();

        stack.pop();

        stack.push();
        stack.translate(0.0, height * 0.5, 0.0);
        Vec3d waist = stackPosition(stack);
        stack.pop();

        stack.push();
        stack.translate(0.0, height * 0.3, 0.0);
        Vec3d pelvis = stackPosition(stack);

        stack.push();
        stack.translate(0.125, 0.0, 0.0);
        Vec3d leftHip = stackPosition(stack);

        float leftLegRot = MathHelper.cos(swing * 0.6662f) * 0.5f * swingAmt;
        if (elytra) {
            leftLegRot = 0.1f;
        }

        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.toDegrees(leftLegRot)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d leftKnee = stackPosition(stack);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(leftLegRot) * 15.0f));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d leftFoot = stackPosition(stack);
        stack.pop();

        stack.push();
        stack.translate(-0.125, 0.0, 0.0);
        Vec3d rightHip = stackPosition(stack);

        float rightLegRot = MathHelper.cos(swing * 0.6662f + (float) Math.PI) * 0.5f * swingAmt;
        if (elytra) {
            rightLegRot = 0.1f;
        }

        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.toDegrees(rightLegRot)));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d rightKnee = stackPosition(stack);
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(Math.abs(rightLegRot) * 15.0f));
        stack.translate(0.0, -0.25, 0.0);
        Vec3d rightFoot = stackPosition(stack);
        stack.pop();

        stack.pop();

        addBone(bones, neck, head);
        addBone(bones, neck, waist);
        addBone(bones, waist, pelvis);
        addBone(bones, neck, leftShoulder);
        addBone(bones, neck, rightShoulder);
        addBone(bones, leftShoulder, leftElbow);
        addBone(bones, leftElbow, leftHand);
        addBone(bones, rightShoulder, rightElbow);
        addBone(bones, rightElbow, rightHand);
        addBone(bones, pelvis, leftHip);
        addBone(bones, pelvis, rightHip);
        addBone(bones, leftHip, leftKnee);
        addBone(bones, leftKnee, leftFoot);
        addBone(bones, rightHip, rightKnee);
        addBone(bones, rightKnee, rightFoot);

        return bones;
    }

    private static void addBone(List<Vec3d[]> bones, Vec3d from, Vec3d to) {
        bones.add(new Vec3d[]{from, to});
    }

    private static Vec3d stackPosition(MatrixStack stack) {
        Vector3f pos = stack.peek().getPositionMatrix().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
        return new Vec3d(pos.x, pos.y, pos.z);
    }

    private Vec3d point(double baseX, double baseY, double baseZ, double localX, double localY, double localZ, float yawRad) {
        double cos = Math.cos(yawRad);
        double sin = Math.sin(yawRad);
        double rx = localX * cos - localZ * sin;
        double rz = localX * sin + localZ * cos;
        return new Vec3d(baseX + rx, baseY + localY, baseZ + rz);
    }

    private void line(VertexConsumer buffer, Matrix4f matrix, Vec3d from, Vec3d to, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        float normalX;
        float normalY;
        float normalZ;
        if (length <= 1.0E-6D) {
            normalX = 0.0F;
            normalY = 1.0F;
            normalZ = 0.0F;
        } else {
            float invLength = (float) (1.0D / length);
            normalX = (float) dx * invLength;
            normalY = (float) dy * invLength;
            normalZ = (float) dz * invLength;
        }

        buffer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z).color(r, g, b, alpha).normal(normalX, normalY, normalZ);
        buffer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z).color(r, g, b, alpha).normal(normalX, normalY, normalZ);
    }

    private RenderLayer getLineLayer(boolean noDepth, float width) {
        int key = Float.floatToIntBits(width);
        if (noDepth) {
            return noDepthLayerCache.computeIfAbsent(key, unused -> createLineLayer("strange_skeleton_line_nodepth_" + key, SKELETON_LINE_NODEPTH_PIPELINE, width));
        }
        return depthLayerCache.computeIfAbsent(key, unused -> createLineLayer("strange_skeleton_line_depth_" + key, SKELETON_LINE_DEPTH_PIPELINE, width));
    }

    private static RenderLayer createLineLayer(String name, RenderPipeline pipeline, float width) {
        return RenderLayer.of(
                name,
                BUFFER_SIZE,
                false,
                true,
                pipeline,
                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(width)))
                        .build(false)
        );
    }

    private static final RenderPipeline SKELETON_LINE_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("strange", "skeleton_line_depth"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderPipeline SKELETON_LINE_NODEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                    .withLocation(Identifier.of("strange", "skeleton_line_nodepth"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

}
