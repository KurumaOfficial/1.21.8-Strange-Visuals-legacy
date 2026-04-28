package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;
import ru.strange.client.module.impl.interfaces.CustomCrosshair;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.module.impl.interfaces.hud.HotbarHudRenderer;
import ru.strange.client.module.impl.interfaces.hud.ScoreboardHudRenderer;
import ru.strange.client.module.impl.other.NoRender;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private PlayerListHud playerListHud;

    private int strange$scoreboardBgLeft;
    private int strange$scoreboardBgTop;
    private int strange$scoreboardBgRight;

    @Shadow
    protected abstract void renderOverlay(DrawContext context, Identifier texture, float opacity);

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        EventManager.call(new EventScreen(MinecraftClient.getInstance(), context));

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.shouldRenderTabClosingFrame() || client.world == null) {
            return;
        }

        Scoreboard scoreboard = client.world.getScoreboard();
        ScoreboardObjective objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.LIST);
        this.playerListHud.render(context, context.getScaledWindowWidth(), scoreboard, objective);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CustomCrosshair.isActive() && client.options.getPerspective() == Perspective.FIRST_PERSON) {
            ci.cancel();
        }
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.enabled("Убрать портал")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(CallbackInfo ci) {
        if (WaterMark.shouldHideVanillaPotionHud()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        strange$scoreboardBgLeft = Integer.MIN_VALUE;
        strange$scoreboardBgTop = Integer.MIN_VALUE;
        strange$scoreboardBgRight = Integer.MIN_VALUE;

        if (NoRender.enabled("Убрать скорборд")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (WaterMark.shouldHideVanillaHotbar()) {
            HotbarHudRenderer.renderOverlay(context);
            ci.cancel();
        }
    }

    @Redirect(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
                    ordinal = 0
            )
    )
    private void strange$replaceScoreboardHeaderFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (!WaterMark.shouldHideVanillaScoreboard()) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        strange$scoreboardBgLeft = x1;
        strange$scoreboardBgTop = y1;
        strange$scoreboardBgRight = x2;
    }

    @Redirect(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V",
                    ordinal = 1
            )
    )
    private void strange$replaceScoreboardBodyFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (!WaterMark.shouldHideVanillaScoreboard()) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        int left = strange$scoreboardBgLeft != Integer.MIN_VALUE ? strange$scoreboardBgLeft : x1;
        int right = strange$scoreboardBgRight != Integer.MIN_VALUE ? strange$scoreboardBgRight : x2;
        int top = y1;
        int bottom = y2;
        ScoreboardHudRenderer.drawBackground(context, left, top, right, bottom);
    }

    @Redirect(
            method = "renderMiscOverlays",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V"
            )
    )
    private void strange$removePumpkinOverlay(InGameHud instance, DrawContext context, Identifier texture, float opacity) {
        if (NoRender.enabled("Убрать тыкву")
                && texture != null
                && texture.getPath() != null
                && texture.getPath().toLowerCase().contains("pumpkin")) {
            return;
        }

        this.renderOverlay(context, texture, opacity);
    }
}
