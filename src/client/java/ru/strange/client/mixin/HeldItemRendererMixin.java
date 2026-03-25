package ru.strange.client.mixin;

import com.google.common.base.MoreObjects;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventGlassHandsRender;
import ru.strange.client.event.impl.EventHandAnimation;
import ru.strange.client.event.impl.EventRenderItem;

@Environment(EnvType.CLIENT)
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @Shadow
    private ItemStack mainHand;

    @Shadow
    private ItemStack offHand;

    @Shadow
    private float equipProgressMainHand;

    @Shadow
    private float lastEquipProgressMainHand;

    @Shadow
    private float equipProgressOffHand;

    @Shadow
    private float lastEquipProgressOffHand;

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light);

    @Inject(
            method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void fullRenderItemOverride(float tickProgress, MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player, int light, CallbackInfo ci) {
        ci.cancel();

        float f = player.getHandSwingProgress(tickProgress);
        Hand hand = MoreObjects.firstNonNull(player.preferredHand, Hand.MAIN_HAND);
        float pitch = player.getLerpedPitch(tickProgress);

        boolean renderMainHand = true;
        boolean renderOffHand = true;

        ItemStack mainStack = player.getMainHandStack();
        ItemStack offStack = player.getOffHandStack();
        boolean hasBow = mainStack.isOf(net.minecraft.item.Items.BOW) || offStack.isOf(net.minecraft.item.Items.BOW);
        boolean hasCrossbow = mainStack.isOf(net.minecraft.item.Items.CROSSBOW) || offStack.isOf(net.minecraft.item.Items.CROSSBOW);

        if (hasBow || hasCrossbow) {
            if (player.isUsingItem()) {
                ItemStack activeStack = player.getActiveItem();
                Hand activeHand = player.getActiveHand();
                if (activeStack.isOf(net.minecraft.item.Items.BOW) || activeStack.isOf(net.minecraft.item.Items.CROSSBOW)) {
                    renderMainHand = activeHand == Hand.MAIN_HAND;
                    renderOffHand = activeHand == Hand.OFF_HAND;
                }
            } else if (this.strange$isChargedCrossbow(mainStack)) {
                renderOffHand = false;
            }
        }
        if (renderMainHand) {
            float swing = hand == Hand.MAIN_HAND ? f : 0.0f;
            float equip = 1.0f - MathHelper.lerp(tickProgress, this.lastEquipProgressMainHand, this.equipProgressMainHand);
            strange$renderHand(player, tickProgress, pitch, Hand.MAIN_HAND, swing, this.mainHand, equip, matrices, vertexConsumers, light);
        }

        if (renderOffHand) {
            float swing = hand == Hand.OFF_HAND ? f : 0.0f;
            float equip = 1.0f - MathHelper.lerp(tickProgress, this.lastEquipProgressOffHand, this.equipProgressOffHand);
            strange$renderHand(player, tickProgress, pitch, Hand.OFF_HAND, swing, this.offHand, equip, matrices, vertexConsumers, light);
        }
    }

    @WrapOperation(method = "renderFirstPersonItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FFLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V", ordinal = 2))
    private void handAnimationHook(HeldItemRenderer instance, float swingProgress, float equipProgress, MatrixStack matrices, int armX, Arm arm, Operation<Void> original, @Local(ordinal = 0, argsOnly = true) AbstractClientPlayerEntity player, @Local(ordinal = 0, argsOnly = true) Hand hand) {
        EventHandAnimation event = new EventHandAnimation(matrices, hand, swingProgress);
        EventManager.call(event);
        if (!event.isCancelled()) original.call(instance, swingProgress, equipProgress, matrices, armX, arm);
    }

    @Unique
    private boolean strange$isChargedCrossbow(ItemStack stack) {
        return stack.isOf(net.minecraft.item.Items.CROSSBOW) && net.minecraft.item.CrossbowItem.isCharged(stack);
    }

    @Unique
    private void strange$renderHand(ClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swing,
                                    ItemStack item, float equip, MatrixStack matrices,
                                    VertexConsumerProvider.Immediate vertexConsumers, int light) {
        EventManager.call(new EventGlassHandsRender(EventGlassHandsRender.Phase.PRE, matrices, tickProgress, hand, item));

        matrices.push();
        EventRenderItem renderItemEvent = new EventRenderItem(matrices, hand, item);
        EventManager.call(renderItemEvent);
        this.renderFirstPersonItem(player, tickProgress, pitch, hand, swing, item, equip, matrices, vertexConsumers, light);
        matrices.pop();

        vertexConsumers.draw();
        EventManager.call(new EventGlassHandsRender(EventGlassHandsRender.Phase.POST, matrices, tickProgress, hand, item));
    }
}
