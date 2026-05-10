package ru.strange.client.mixin;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.SpiderEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpiderEntityModel.class)
public abstract class SpiderEntityModelMixin {

    @Shadow private ModelPart rightHindLeg;
    @Shadow private ModelPart leftHindLeg;

    @Shadow private ModelPart rightMiddleLeg;
    @Shadow private ModelPart leftMiddleLeg;

    @Shadow private ModelPart rightMiddleFrontLeg;
    @Shadow private ModelPart leftMiddleFrontLeg;

    @Shadow private ModelPart rightFrontLeg;
    @Shadow private ModelPart leftFrontLeg;

    @Inject(method = "setAngles", at = @At("TAIL"))
    private void hideLegs(LivingEntityRenderState state, CallbackInfo ci) {
        rightHindLeg.visible = false;
        leftHindLeg.visible = false;

        rightMiddleLeg.visible = false;
        leftMiddleLeg.visible = false;

        rightMiddleFrontLeg.visible = false;
        leftMiddleFrontLeg.visible = false;

        rightFrontLeg.visible = false;
        leftFrontLeg.visible = false;
    }
}
