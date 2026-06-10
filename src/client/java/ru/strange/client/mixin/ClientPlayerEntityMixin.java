package ru.strange.client.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventMotion;
import ru.strange.client.event.impl.EventUpdate;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Shadow
    protected Input input;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        EventManager.call(new EventUpdate());
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onUpdateWalkingPlayer(CallbackInfo ci) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;

        if (player == null) {
            return;
        }

        Box axisalignedbb = player.getBoundingBox();
        EventMotion eventPre = new EventMotion(
                player.getYaw(),
                player.getPitch(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.isOnGround(),
                axisalignedbb,
                null
        );

        EventManager.call(eventPre);

        if (eventPre.isCancelled()) {
            return;
        }

        if (eventPre.getYaw() != player.getYaw() || eventPre.getPitch() != player.getPitch()) {
            player.setYaw(eventPre.getYaw());
            player.setPitch(eventPre.getPitch());
        }

        if (eventPre.getPosX() != player.getX() || eventPre.getPosY() != player.getY() || eventPre.getPosZ() != player.getZ()) {
            player.refreshPositionAndAngles(eventPre.getPosX(), eventPre.getPosY(), eventPre.getPosZ(), eventPre.getYaw(), eventPre.getPitch());
        }

        if (eventPre.isOnGround() != player.isOnGround()) {
            player.setOnGround(eventPre.isOnGround());
        }
    }
}