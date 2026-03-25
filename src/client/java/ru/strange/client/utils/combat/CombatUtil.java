package ru.strange.client.utils.combat;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public final class CombatUtil {

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
}
