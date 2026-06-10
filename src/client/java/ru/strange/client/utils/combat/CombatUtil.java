package ru.strange.client.utils.combat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

// Привет Горелкинг
public final class CombatUtil {
    public static final double DEFAULT_TARGET_LOOK_RANGE = 24.0D;

    private CombatUtil() {
    }

    public static boolean isValidAttackTarget(Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isRemoved()
                && living.hurtTime >= 0;
    }

    public static boolean isRealCritical(PlayerEntity player) {
        if (player == null) {
            return false;
        }

        return player.fallDistance > 0.0f
                && !player.isOnGround()
                && !player.isSprinting()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.isSubmergedInWater()
                && !player.hasVehicle()
                && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS);
    }

    public static boolean isCriticalAttack(PlayerEntity player, Entity target, float cooldownProgress) {
        return target instanceof LivingEntity
                && cooldownProgress > 0.9f
                && isRealCritical(player);
    }

    public static boolean isCrosshairLivingTarget(HitResult hitResult) {
        return hitResult instanceof EntityHitResult entityHitResult
                && entityHitResult.getEntity() instanceof LivingEntity;
    }

    public static boolean isAttackReachable(ClientPlayerEntity player, Entity target, double maxDistance) {
        return player != null
                && target != null
                && player.squaredDistanceTo(target) <= maxDistance * maxDistance;
    }

    public static LivingEntity findCrosshairLivingTarget(MinecraftClient mc, double maxDistance) {
        if (mc == null || mc.world == null || mc.player == null) {
            return null;
        }

        LivingEntity directTarget = extractLivingTarget(mc.crosshairTarget);
        if (isTrackableLivingTarget(mc.player, directTarget, maxDistance)) {
            return directTarget;
        }

        if (mc.targetedEntity instanceof LivingEntity living
                && isTrackableLivingTarget(mc.player, living, maxDistance)) {
            return living;
        }

        Entity camera = mc.getCameraEntity();
        if (camera == null) {
            camera = mc.player;
        }

        float tickProgress = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d start = camera.getCameraPosVec(tickProgress);
        Vec3d direction = camera.getRotationVec(tickProgress).normalize();
        return raycastLivingTarget(mc, camera, mc.player, start, direction, maxDistance);
    }

    public static LivingEntity raycastLivingTarget(MinecraftClient mc, Entity camera, PlayerEntity viewer,
                                                   Vec3d start, Vec3d direction, double maxDistance) {
        if (mc == null || mc.world == null || camera == null || viewer == null || start == null || direction == null) {
            return null;
        }

        Vec3d normalizedDirection = direction.normalize();
        if (normalizedDirection.lengthSquared() <= 1.0E-8) {
            return null;
        }

        Vec3d end = start.add(normalizedDirection.multiply(maxDistance));

        HitResult blockHit = mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                camera
        ));

        double maxDistanceSq = maxDistance * maxDistance;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDistanceSq = Math.min(maxDistanceSq, start.squaredDistanceTo(blockHit.getPos()));
            end = blockHit.getPos();
        }

        Box searchBox = camera.getBoundingBox()
                .stretch(normalizedDirection.multiply(maxDistance))
                .expand(1.0D);

        EntityHitResult entityHit = ProjectileUtil.raycast(
                camera,
                start,
                end,
                searchBox,
                entity -> isTrackableLivingTarget(viewer, entity, maxDistance),
                maxDistanceSq
        );

        return entityHit != null && entityHit.getEntity() instanceof LivingEntity living
                ? living
                : null;
    }

    private static LivingEntity extractLivingTarget(HitResult hitResult) {
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }

        return entityHitResult.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static boolean isTrackableLivingTarget(PlayerEntity viewer, Entity entity, double maxDistance) {
        return viewer != null
                && entity instanceof LivingEntity living
                && entity != viewer
                && living.isAlive()
                && !living.isRemoved()
                && (!(living instanceof PlayerEntity player) || !player.isSpectator())
                && viewer.squaredDistanceTo(living) <= maxDistance * maxDistance
                && (!living.isInvisible() || hasVisibleEquipment(living));
    }

    private static boolean hasVisibleEquipment(LivingEntity entity) {
        return entity != null
                && (!entity.getEquippedStack(EquipmentSlot.HEAD).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.LEGS).isEmpty()
                || !entity.getEquippedStack(EquipmentSlot.FEET).isEmpty()
                || !entity.getMainHandStack().isEmpty()
                || !entity.getOffHandStack().isEmpty());
    }
}
