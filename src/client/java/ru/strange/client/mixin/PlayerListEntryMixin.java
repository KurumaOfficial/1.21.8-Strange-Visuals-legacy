package ru.strange.client.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.NameProtect;
import ru.strange.client.utils.other.SkinUtil;
import ru.strange.client.utils.other.CapeUtil;

@Mixin(PlayerListEntry.class)
public class PlayerListEntryMixin {

    @Shadow @Final
    private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void strange$injectSkinUtil(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return;
        }

        SkinTextures original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        // Применяем кастомный скин для локального игрока
        if (this.profile.getId().equals(client.player.getUuid())) {
            SkinTextures updated = SkinUtil.updatedPlayerSkin(original, client.player);
            if (updated != original) {
                cir.setReturnValue(updated);
                return;
            }
        }

        // Применяем кастомные плащи для всех игроков (Привет Горелкинг)
        var playerEntity = client.world.getPlayerByUuid(this.profile.getId());
        if (playerEntity != null) {
            SkinTextures capeUpdated = CapeUtil.updatedPlayerSkin(original, playerEntity);
            if (capeUpdated != original) {
                cir.setReturnValue(capeUpdated);
            }
        }
    }

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void strange$protectDisplayName(CallbackInfoReturnable<Text> cir) {
        if (!NameProtect.isActive()) {
            return;
        }

        cir.setReturnValue(NameProtect.process(cir.getReturnValue()));
    }
}