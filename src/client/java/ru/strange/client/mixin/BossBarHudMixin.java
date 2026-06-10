package ru.strange.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.RenderUtil;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    private int strange$bossBg_x;
    private int strange$bossBg_y;
    private int strange$bossBg_w;
    private int strange$bossBg_h;
    private boolean strange$bossBgCaptured;

    @Redirect(
            method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V",
                    ordinal = 0
            ),
            require = 0
    )
    private void strange$replaceBossBarBackground(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                                  int u, int v, int regionWidth, int regionHeight,
                                                  int x, int y, int width, int height) {
        if (!strange$shouldReplace()) {
            context.drawGuiTexture(pipeline, texture, u, v, regionWidth, regionHeight, x, y, width, height);
            strange$bossBgCaptured = false;
            return;
        }

        strange$bossBg_x = x;
        strange$bossBg_y = y;
        strange$bossBg_w = width;
        strange$bossBg_h = height;
        strange$bossBgCaptured = true;

        int bgColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 200);
        RenderUtil.Round.draw(context, x - 2.0f, y - 2.0f, width + 4.0f, height + 4.0f, 3.0f, bgColor);
    }

    @Redirect(
            method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;I[Lnet/minecraft/util/Identifier;[Lnet/minecraft/util/Identifier;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V",
                    ordinal = 1
            ),
            require = 0
    )
    private void strange$replaceBossBarProgress(DrawContext context, RenderPipeline pipeline, Identifier texture,
                                                int u, int v, int regionWidth, int regionHeight,
                                                int x, int y, int width, int height) {
        if (!strange$shouldReplace() || !strange$bossBgCaptured) {
            context.drawGuiTexture(pipeline, texture, u, v, regionWidth, regionHeight, x, y, width, height);
            return;
        }

        int progressColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 230);
        RenderUtil.Round.draw(context, x, y, width, height, 1.5f, progressColor);
    }

    private static boolean strange$shouldReplace() {
        WaterMark watermark = WaterMark.INSTANCE;
        return watermark != null && watermark.enable;
    }
}
