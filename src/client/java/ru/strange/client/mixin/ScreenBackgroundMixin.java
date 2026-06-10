package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.StarterMenu.MenuBackgroundManager;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;

@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
    @Shadow
    protected MinecraftClient client;

    @Shadow
    public int width;

    @Shadow
    public int height;

    @Unique
    private boolean strange$backgroundMatrixPushed;

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
    private void strange$replaceScreenBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        strange$backgroundMatrixPushed = false;
        float progress = strange$getContainerBackgroundProgress(screen);
        if (progress < 0.999F) {
            strange$pushContainerBackgroundTransform(context, progress);
            strange$backgroundMatrixPushed = true;
        }

        if (!MenuBackgroundManager.shouldUseCustomBackground(screen)) {
            return;
        }

        MenuBackgroundManager.renderPanoramaBackground(context, width, height, deltaTicks);
        if (MenuBackgroundManager.shouldApplyBlur(screen)) {
            MenuBackgroundManager.renderBlur(context, 0, 0, width, height);
        }
        MenuBackgroundManager.renderDarkening(context, 0, 0, width, height, false);
        if (strange$backgroundMatrixPushed) {
            context.getMatrices().popMatrix();
            strange$backgroundMatrixPushed = false;
        }
        ci.cancel();
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"))
    private void strange$finishScreenBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if (strange$backgroundMatrixPushed) {
            context.getMatrices().popMatrix();
            strange$backgroundMatrixPushed = false;
        }
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

    @Unique
    private float strange$getContainerBackgroundProgress(Screen screen) {
        if (!(screen instanceof HandledScreen<?>)) {
            return 1.0F;
        }

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothScreens()) {
            return 1.0F;
        }

        return (float) module.getContainerProgress();
    }

    @Unique
    private void strange$pushContainerBackgroundTransform(DrawContext context, float progress) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));
        float inverse = 1.0F - p;
        float settle = 1.0F + (float) Math.sin(p * Math.PI) * inverse * 0.02F;
        float scaleX = (0.95F + 0.05F * p) * settle;
        float scaleY = 0.70F + 0.30F * p;
        float yOffset = inverse * inverse * 24.0F + inverse * 10.0F;
        float depthLift = inverse * 10.0F;
        float anchorX = context.getScaledWindowWidth() * 0.5F;
        float anchorY = context.getScaledWindowHeight() * 0.5F;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX, anchorY - yOffset + depthLift);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY);
    }
}
