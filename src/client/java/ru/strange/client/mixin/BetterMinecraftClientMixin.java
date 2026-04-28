package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;

@Mixin(MinecraftClient.class)
public class BetterMinecraftClientMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module != null) {
            module.clientTick();
        }
    }
}