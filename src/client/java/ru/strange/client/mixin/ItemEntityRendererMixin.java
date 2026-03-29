package ru.strange.client.mixin;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.other.ItemPhysics;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Unique
    private static final ThreadLocal<Float> strange$itemAge = ThreadLocal.withInitial(() -> 0f);

    @Unique
    private static final ThreadLocal<Boolean> strange$itemOnGround = ThreadLocal.withInitial(() -> false);

    /**
     * Забираем состояние предмета при updateRenderState
     */
    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void strange$captureState(ItemEntity itemEntity, ItemEntityRenderState itemEntityRenderState, float f, CallbackInfo ci) {
        strange$itemOnGround.set(itemEntity.isOnGround());
    }

    /**
     * Убираем ванильную ротацию
     */
    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ItemEntity;getRotation(FF)F"
            )
    )
    private float strange$disableVanillaRotation(float age, float uniqueOffset) {
        if (ItemPhysics.isActivePhysics()) {
            strange$itemAge.set(age);
            return 0.0f;
        }
        return ItemEntity.getRotation(age, uniqueOffset);
    }

    /**
     * Убираем bob вверх-вниз
     */
    @ModifyVariable(
            method = "render",
            at = @At("STORE"),
            ordinal = 1
    )
    private float strange$modifyBobOffset(float originalOffset, ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        if (!ItemPhysics.isActivePhysics()) {
            return originalOffset;
        }

        return 0.0f;
    }

    /**
     * Физика:
     * - на земле лежит
     * - в воздухе лежа крутится
     */
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"
            )
    )
    private void strange$itemPhysics(
            ItemEntityRenderState state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            CallbackInfo ci
    ) {
        if (!ItemPhysics.isActivePhysics()) return;

        float age = strange$itemAge.get();
        boolean onGround = strange$itemOnGround.get();

        float scale = ItemPhysics.getItemScale();
        matrices.scale(scale, scale, scale);

        // всегда лежит
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));

        // крутится только пока летит
        if (!onGround) {
            float spin = age * 24.0f;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
        }
    }
}