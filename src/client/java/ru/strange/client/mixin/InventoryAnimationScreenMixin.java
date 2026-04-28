package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;

@Mixin({InventoryScreen.class, CreativeInventoryScreen.class})
public abstract class InventoryAnimationScreenMixin {

    @Unique
    private boolean strange$pushedMatrix = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void strange$animateInventoryRenderStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        strange$pushedMatrix = false;

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothScreens()) {
            return;
        }

        double progress = module.getContainerProgress();
        if (progress >= 0.999) {
            return;
        }

        float p = (float) progress;
        float inverse = 1.0f - p;
        float settle = 1.0f + (float) Math.sin(p * Math.PI) * inverse * 0.02f;
        float scaleX = (0.95f + 0.05f * p) * settle;
        float scaleY = 0.70f + 0.30f * p;
        float yOffset = inverse * inverse * 24.0f + inverse * 10.0f;
        float depthLift = inverse * 10.0f;
        float anchorX = context.getScaledWindowWidth() / 2.0f;
        float anchorY = context.getScaledWindowHeight() / 2.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX, anchorY - yOffset + depthLift);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY);
        strange$pushedMatrix = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void strange$animateInventoryRenderEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (strange$pushedMatrix) {
            context.getMatrices().popMatrix();
            strange$pushedMatrix = false;
        }
    }
}
