package ru.strange.client.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventJump;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "jump", at = @At("HEAD"))
    public void jumpYo(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;

        if (self instanceof ClientPlayerEntity) {
            EventManager.call(new EventJump());
        }
    }
}
