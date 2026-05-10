package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventRotation;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;
import ru.strange.client.utils.other.FreeLookHandler;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract float clipToSpace(float desiredCameraDistance);

    @Unique
    private float strange$tickProgress;

    @Unique
    private boolean strange$thirdPerson;

    @Unique
    private boolean strange$inverseView;

    @Inject(method = "update", at = @At("HEAD"))
    private void onUpdateHead(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickProgress, CallbackInfo ci) {
        this.strange$tickProgress = tickProgress;
        this.strange$thirdPerson = thirdPerson;
        this.strange$inverseView = inverseView;
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getYaw(F)F"
            )
    )
    private float redirectYaw(Entity entity, float tickDelta) {
        if (FreeLookHandler.isActive() && entity == MinecraftClient.getInstance().player) {
            return FreeLookHandler.getFreeYaw();
        }

        return entity.getYaw(tickDelta);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getPitch(F)F"
            )
    )
    private float redirectPitch(Entity entity, float tickDelta) {
        if (FreeLookHandler.isActive() && entity == MinecraftClient.getInstance().player) {
            return FreeLookHandler.getFreePitch();
        }

        return entity.getPitch(tickDelta);
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"
            )
    )
    private void redirectSetRotation(Camera instance, float yaw, float pitch) {
        EventRotation event = new EventRotation(yaw, pitch, strange$tickProgress);
        EventManager.call(event);
        this.setRotation(event.getYaw(), event.getPitch());
    }

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"
            )
    )
    private float redirectClipToSpace(Camera instance, float desiredCameraDistance) {
        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothCamera()) {
            return this.clipToSpace(desiredCameraDistance);
        }

        float animatedDistance = desiredCameraDistance * module.getThirdPersonDistanceScale();
        return this.clipToSpace(Math.max(0.05f, animatedDistance));
    }
}