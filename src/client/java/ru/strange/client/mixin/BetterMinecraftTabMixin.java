package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;

@Mixin(PlayerListHud.class)
public class BetterMinecraftTabMixin {

    @Unique
    private boolean strange$pushedThisFrame = false;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderStart(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        strange$pushedThisFrame = false;

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothTab()) return;

        double progress = module.getTabProgress();
        if (progress >= 0.999D) return;

        float p = easeOutCubic(clampProgress(progress));
        float inv = 1.0f - p;
        float settle = 1.0f + (float) Math.sin(p * Math.PI) * inv * 0.05f;

        float anchorX = scaledWindowWidth / 2.0f;
        float anchorY = 9.0f;

        float scaleX = 0.72f + 0.28f * p;
        float scaleY = (0.52f + 0.48f * p) * settle;

        float yOffset = inv * inv * 30.0f + inv * 10.0f;
        float centerPull = inv * 7.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX, anchorY - yOffset);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY - centerPull);

        strange$pushedThisFrame = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(DrawContext context, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        if (strange$pushedThisFrame) {
            context.getMatrices().popMatrix();
            strange$pushedThisFrame = false;
        }
    }

    @Unique
    private static float clampProgress(double progress) {
        return (float) Math.max(0.0D, Math.min(1.0D, progress));
    }

    @Unique
    private static float easeOutCubic(float progress) {
        float inverted = 1.0f - progress;
        return 1.0f - inverted * inverted * inverted;
    }
}
