package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.StarterMenu.MenuBackgroundManager;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Shadow
    protected MinecraftClient client;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
    private void strange$replaceScreenBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!MenuBackgroundManager.shouldUseCustomBackground(screen)) {
            return;
        }

        MenuBackgroundManager.renderPanoramaBackground(context, width, height, deltaTicks);
        if (MenuBackgroundManager.shouldApplyBlur(screen)) {
            MenuBackgroundManager.renderBlur(context, 0, 0, width, height);
        }
        MenuBackgroundManager.renderDarkening(context, 0, 0, width, height, false);
        ci.cancel();
    }

    @Inject(method = "renderPanoramaBackground(Lnet/minecraft/client/gui/DrawContext;F)V", at = @At("HEAD"), cancellable = true)
    private void strange$replacePanorama(DrawContext context, float deltaTicks, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!MenuBackgroundManager.shouldUseCustomBackground(screen)) {
            return;
        }

        MenuBackgroundManager.renderPanoramaBackground(context, width, height, deltaTicks);
        ci.cancel();
    }

    @Inject(method = "renderDarkening(Lnet/minecraft/client/gui/DrawContext;IIII)V", at = @At("HEAD"), cancellable = true)
    private void strange$replaceDarkening(DrawContext context, int x, int y, int width, int height, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (!MenuBackgroundManager.shouldUseCustomBackground(screen)) {
            return;
        }

        MenuBackgroundManager.renderDarkening(context, x, y, width, height, false);
        ci.cancel();
    }

    @Inject(method = "renderBackgroundTexture(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;IIFFII)V", at = @At("HEAD"), cancellable = true)
    private static void strange$replaceBackgroundTexture(DrawContext context, Identifier textureId, int x, int y, float u, float v, int width, int height, CallbackInfo ci) {
        if (!MenuBackgroundManager.shouldReplaceBackgroundTexture(textureId, x, y, width, height)) {
            return;
        }

        MenuBackgroundManager.renderMenuTextureBackground(context, x, y, width, height);
        ci.cancel();
    }
}
