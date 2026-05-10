package ru.strange.client.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventTotemPop;

@Mixin(net.minecraft.client.network.ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Shadow
    private ClientWorld world;

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    private void strange$trackTotemPop(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet == null || world == null || packet.getStatus() != 35) {
            return;
        }

        Entity entity = packet.getEntity(world);
        if (entity != null) {
            EventManager.call(new EventTotemPop(entity));
        }
    }
}
