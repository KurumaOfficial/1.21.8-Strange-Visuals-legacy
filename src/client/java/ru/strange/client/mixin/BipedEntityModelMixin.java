package ru.strange.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.player.PlayerAnimations;

@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin {

    @Shadow protected ModelPart rightArm;
    @Shadow protected ModelPart leftArm;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void strange$applyXBArmPose(BipedEntityRenderState state, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) {
            return;
        }

        if (!PlayerAnimations.shouldAffectLocalPlayer(playerState.id)) {
            return;
        }

        if (PlayerAnimations.isXBMode()) {
            applyXBPose(rightArm, true);
            applyXBPose(leftArm, false);
        } else if (PlayerAnimations.isDrochkaMode()) {
            applyDrochkaPose();
        }
    }

    private void applyXBPose(ModelPart arm, boolean rightArm) {
        arm.pitch = PlayerAnimations.getArmPitch(rightArm);
        arm.yaw   = PlayerAnimations.getArmYaw(rightArm);
        arm.roll  = PlayerAnimations.getArmRoll(rightArm);
        arm.originY += -PlayerAnimations.getSleeveOffsetY(rightArm) * 2.4f;
    }

    private void applyDrochkaPose() {
        // Обе руки двигаются синхронно в режиме "Дрочка".
        rightArm.pitch = PlayerAnimations.getDrochkaRightPitch();
        rightArm.yaw   = PlayerAnimations.getDrochkaRightYaw();
        rightArm.roll  = PlayerAnimations.getDrochkaRightRoll();

        leftArm.pitch = PlayerAnimations.getDrochkaLeftPitch();
        leftArm.yaw   = PlayerAnimations.getDrochkaLeftYaw();
        leftArm.roll  = PlayerAnimations.getDrochkaLeftRoll();
    }
}