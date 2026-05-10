package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.utils.other.ServerRestrictionManager;

@Mixin(MinecraftClient.class)
public class ServerRestrictionMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void strange$serverRestrictions(CallbackInfo ci) {
        ServerRestrictionManager.tick();
    }
}