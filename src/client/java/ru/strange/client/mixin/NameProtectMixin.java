package ru.strange.client.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.MinecraftClient;
import ru.strange.client.module.impl.other.NameProtect;

@Mixin(PlayerEntity.class)
public class NameProtectMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void strange$hideDisplayName(CallbackInfoReturnable<Text> cir) {
        if (!NameProtect.isActive() || NameProtect.isCollectingAliases()) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self.getUuid().equals(mc.player.getUuid())) {
            String fakeName = NameProtect.getInstance().getFakeName();
            cir.setReturnValue(Text.literal(fakeName));
        }
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void strange$hideName(CallbackInfoReturnable<Text> cir) {
        if (!NameProtect.isActive() || NameProtect.isCollectingAliases()) return;
        PlayerEntity self = (PlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && self.getUuid().equals(mc.player.getUuid())) {
            cir.setReturnValue(Text.literal(NameProtect.getInstance().getFakeName()));
        }
    }
}
