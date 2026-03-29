package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.utils.render.RenderPlayer;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void strange$renderPlayer(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GameMenuScreen screen = (GameMenuScreen) (Object) this;

        RenderPlayer.onRenderPlayer(context, screen, mouseX, mouseY);
    }
}