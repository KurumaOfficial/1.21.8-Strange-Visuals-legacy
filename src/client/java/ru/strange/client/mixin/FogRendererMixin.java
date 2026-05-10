package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
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
        Vector4f original = cir.getReturnValue();
        if (!CustomFog.isActiveFog()) {
            return;
        }

        int color = CustomFog.getFogColorRGB();
        float blend = CustomFog.getFogBlendStrength();
        boolean adaptWeather = true;

        MinecraftClient client = MinecraftClient.getInstance();
        if (adaptWeather && client != null && client.world != null) {
            float rain = client.world.getRainGradient(1.0f);
            float thunder = client.world.getThunderGradient(1.0f);
            if (blend < 0.70f) {
                blend *= 1.0f - Math.min(0.35f, rain * 0.22f + thunder * 0.16f);
            }
        }

        blend = Math.max(0.0f, Math.min(1.0f, blend));

        float r = ColorHelper.getRedFloat(color);
        float g = ColorHelper.getGreenFloat(color);
        float b = ColorHelper.getBlueFloat(color);

        if (original == null) {
            cir.setReturnValue(new Vector4f(r, g, b, 1.0f));
            return;
        }

        if (blend >= 0.995f) {
            cir.setReturnValue(new Vector4f(r, g, b, original.w));
            return;
        }

        float resolvedR = original.x + (r - original.x) * blend;
        float resolvedG = original.y + (g - original.y) * blend;
        float resolvedB = original.z + (b - original.z) * blend;
        cir.setReturnValue(new Vector4f(resolvedR, resolvedG, resolvedB, original.w));
    }
}