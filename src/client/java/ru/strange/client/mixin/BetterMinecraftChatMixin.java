package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;

@Mixin(ChatScreen.class)
public class BetterMinecraftChatMixin {

    @Unique
    private boolean strange$pushedMatrix = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        strange$pushedMatrix = false;

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothChat()) return;

        double progress = module.getChatProgress();
        if (progress >= 0.999D) return;

        float p = easeOutQuint(clampProgress(progress));
        float inv = 1.0f - p;
        float settle = 1.0f + (float) Math.sin(p * Math.PI) * inv * 0.04f;

        int screenHeight = context.getScaledWindowHeight();

        float anchorX = 4.0f;
        float anchorY = screenHeight - 14.0f;

        float scaleX = 0.88f + 0.12f * p;
        float scaleY = (0.40f + 0.60f * p) * settle;

        float yOffset = inv * inv * 32.0f + inv * 12.0f;
        float xOffset = inv * 8.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX - xOffset, anchorY + yOffset);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY);

        strange$pushedMatrix = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (strange$pushedMatrix) {
            context.getMatrices().popMatrix();
            strange$pushedMatrix = false;
        }
    }

    @Unique
    private static float clampProgress(double progress) {
        return (float) Math.max(0.0D, Math.min(1.0D, progress));
    }

    @Unique
    private static float easeOutQuint(float progress) {
        float inverted = 1.0f - progress;
        return 1.0f - inverted * inverted * inverted * inverted * inverted;
    }
}