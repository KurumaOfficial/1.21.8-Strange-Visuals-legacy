package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;
import ru.strange.client.utils.render.RenderPlayer;

@Mixin(GameMenuScreen.class)
public class GameMenuScreenMixin {

    @Unique
    private boolean strange$pushedMatrix = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void strange$animateRenderStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        strange$pushedMatrix = false;

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothScreens()) {
            return;
        }

        double progress = module.getPauseMenuProgress();
        if (progress >= 0.999) {
            return;
        }

        float p = (float) progress;
        float inverse = 1.0f - p;
        float settle = 1.0f + (float) Math.sin(p * Math.PI) * inverse * 0.025f;
        float scaleX = (0.93f + 0.07f * p) * settle;
        float scaleY = 0.78f + 0.22f * p;
        float yOffset = inverse * inverse * 28.0f + inverse * 12.0f;
        float anchorX = context.getScaledWindowWidth() / 2.0f;
        float anchorY = context.getScaledWindowHeight() / 2.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX, anchorY - yOffset);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY);
        strange$pushedMatrix = true;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void strange$renderPlayer(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        GameMenuScreen screen = (GameMenuScreen) (Object) this;

        RenderPlayer.onRenderPlayer(context, screen, mouseX, mouseY);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void strange$animateRenderEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (strange$pushedMatrix) {
            context.getMatrices().popMatrix();
            strange$pushedMatrix = false;
        }
    }
}