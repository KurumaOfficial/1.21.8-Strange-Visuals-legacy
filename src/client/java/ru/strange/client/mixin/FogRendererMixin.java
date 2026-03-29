package ru.strange.client.mixin;

import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.CustomFog;

@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true)
    private void strange$customFogColor(CallbackInfoReturnable<Vector4f> cir) {
        if (!CustomFog.isActiveFog()) return;

        int color = CustomFog.getFogColorRGB();

        float r = ColorHelper.getRedFloat(color);
        float g = ColorHelper.getGreenFloat(color);
        float b = ColorHelper.getBlueFloat(color);

        cir.setReturnValue(new Vector4f(r, g, b, 1.0f));
    }
}