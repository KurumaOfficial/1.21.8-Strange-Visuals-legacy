package ru.strange.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.RenderLayer;
import ru.strange.client.utils.render.TintedVertexConsumer;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.other.ItemPhysics;
import ru.strange.client.module.impl.utilities.ShulkerPreview;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Unique
    private static final ThreadLocal<Float> strange$itemAge = ThreadLocal.withInitial(() -> 0f);

    @Unique
    private static final ThreadLocal<Boolean> strange$itemOnGround = ThreadLocal.withInitial(() -> false);

    @Unique
    private static final ThreadLocal<ItemStack> strange$itemStack = ThreadLocal.withInitial(() -> ItemStack.EMPTY);

    @Unique
    private static final ThreadLocal<Boolean> strange$filledShulker = ThreadLocal.withInitial(() -> false);

    /**
     * Забираем состояние предмета при updateRenderState
     */
    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void strange$captureState(ItemEntity itemEntity, ItemEntityRenderState itemEntityRenderState, float f, CallbackInfo ci) {
        boolean onGround = itemEntity.isOnGround();
        strange$itemOnGround.set(onGround);
        ItemStack stack = itemEntity.getStack();
        strange$itemStack.set(stack);
        strange$filledShulker.set(onGround && ShulkerPreview.shouldEnhanceDroppedShulker(stack));
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
        boolean filledShulker = strange$filledShulker.get();
        if (!ItemPhysics.isActivePhysics() && !filledShulker) return;

        float age = strange$itemAge.get();
        boolean onGround = strange$itemOnGround.get();

        float scale = ItemPhysics.isActivePhysics() ? ItemPhysics.getItemScale() : 1.0f;
        if (filledShulker) {
            scale *= ShulkerPreview.getDroppedShulkerScale(strange$itemStack.get());
        }
        matrices.scale(scale, scale, scale);

        if (ItemPhysics.isActivePhysics()) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));

            if (!onGround) {
                float spin = age * 24.0f;
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            }
        }
    }

    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/ItemEntityRenderer;renderStack(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/ItemStackEntityRenderState;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/Box;)V"
            )
    )
    private void strange$renderTintedShulker(
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            int light,
            ItemStackEntityRenderState state,
            Random random,
            Box box,
            Operation<Void> original
    ) {
        if (!strange$filledShulker.get()) {
            original.call(matrices, vertexConsumers, light, state, random, box);
            return;
        }

        float tintMix = ShulkerPreview.getDroppedShulkerTintMix(strange$itemStack.get());
        original.call(matrices, TintedVertexConsumer.wrap(vertexConsumers, tintMix), light, state, random, box);
    }

}
