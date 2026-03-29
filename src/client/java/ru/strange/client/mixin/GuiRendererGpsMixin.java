package ru.strange.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.utilities.GpsHudShaderPass;

@Mixin(GuiRenderer.class)
public class GuiRendererGpsMixin {

    @Inject(
            method = "render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;renderPreparedDraws(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void strange$renderGpsHudShader(GpuBufferSlice dynamicTransforms, CallbackInfo ci) {
        GpsHudShaderPass.renderQueued();
    }
}
