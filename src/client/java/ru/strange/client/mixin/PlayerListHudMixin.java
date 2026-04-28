package ru.strange.client.mixin;

import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.NameProtect;

@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void strange$protectPlayerListName(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (!NameProtect.isActive()) {
            return;
        }

        cir.setReturnValue(NameProtect.process(cir.getReturnValue()));
    }
}