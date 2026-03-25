package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Overlay;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.resource.ResourceReload;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.StarterMenu.CustomLoadingScreen;

import java.util.Optional;
import java.util.function.Consumer;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin extends Overlay {
    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    @Final
    private ResourceReload reload;

    @Shadow
    @Final
    private Consumer<Optional<Throwable>> exceptionHandler;

    @Shadow
    @Final
    private boolean reloading;

    @Shadow
    private float progress;

    @Shadow
    private long reloadCompleteTime;

    @Shadow
    private long reloadStartTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void strange$renderCustomSplash(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        long now = Util.getMeasuringTimeMs();
        if (this.reloading && this.reloadStartTime == -1L) {
            this.reloadStartTime = now;
        }

        float completeProgress = this.reloadCompleteTime > -1L ? (now - this.reloadCompleteTime) / 1000.0F : -1.0F;
        float startProgress = this.reloadStartTime > -1L ? (now - this.reloadStartTime) / 500.0F : -1.0F;

        if (completeProgress >= 2.0F) {
            this.client.setOverlay(null);
            ci.cancel();
            return;
        }

        int width = Math.max(1, this.client.getWindow().getScaledWidth());
        int height = Math.max(1, this.client.getWindow().getScaledHeight());

        this.progress = MathHelper.clamp(this.progress * 0.95F + this.reload.getProgress() * 0.050000012F, 0.0F, 1.0F);

        float alpha = 1.0F;
        if (completeProgress >= 0.0F) {
            alpha = 1.0F - MathHelper.clamp(completeProgress, 0.0F, 1.0F);
        } else if (this.reloading) {
            alpha = MathHelper.clamp(startProgress, 0.0F, 1.0F);
        }

        CustomLoadingScreen.render(context, width, height, this.progress, now, alpha, this.reloading);

        if (this.reloadCompleteTime == -1L && this.reload.isComplete() && (!this.reloading || startProgress >= 2.0F)) {
            try {
                this.reload.throwException();
                this.exceptionHandler.accept(Optional.empty());
            } catch (Throwable throwable) {
                this.exceptionHandler.accept(Optional.of(throwable));
            }

            this.reloadCompleteTime = Util.getMeasuringTimeMs();
            Screen currentScreen = this.client.currentScreen;
            if (currentScreen != null) {
                currentScreen.init(this.client, width, height);
            }
        }

        ci.cancel();
    }
}
