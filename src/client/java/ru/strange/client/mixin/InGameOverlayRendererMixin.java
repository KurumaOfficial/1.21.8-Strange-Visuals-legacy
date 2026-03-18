package ru.strange.client.mixin;

import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.other.NoRender;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void injectRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (NoRender.settings.get("Убрать огонь")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderOverlays", at = @At("HEAD"), cancellable = true)
    private void injectRenderOverlays(boolean renderPumpkinBlur, float tickDelta, CallbackInfo ci) {
        if (NoRender.settings.get("Убрать тыкву") && renderPumpkinBlur) {
            ci.cancel();
        }
    }
}
