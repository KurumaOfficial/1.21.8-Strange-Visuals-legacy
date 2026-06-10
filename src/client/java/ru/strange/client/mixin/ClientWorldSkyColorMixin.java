package ru.strange.client.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.SkyColor;

@Mixin(ClientWorld.class)
public class ClientWorldSkyColorMixin {

    @Inject(
            method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void strange$overrideSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        try {
            if (!SkyColor.isActiveSkyColor()) return;

            ClientWorld world = (ClientWorld) (Object) this;
            if (world == null) return;

            Vec3d original = cir.getReturnValue();
            if (original == null) original = Vec3d.ZERO;

            float blend = SkyColor.getBlendStrength();
            if (blend < 0.001f) return;

            int color = SkyColor.getSkyColorRGB();
            double tr = ((color >> 16) & 0xFF) / 255.0;
            double tg = ((color >> 8) & 0xFF) / 255.0;
            double tb = (color & 0xFF) / 255.0;

            if (SkyColor.shouldAdaptWeather()) {
                try {
                    float rain = world.getRainGradient(tickDelta);
                    float thunder = world.getThunderGradient(tickDelta);
                    if (!Float.isNaN(rain) && !Float.isNaN(thunder)) {
                        float darken = 1.0f - Math.min(0.45f, rain * 0.24f + thunder * 0.20f);
                        tr *= Math.max(0.3f, darken);
                        tg *= Math.max(0.3f, darken);
                        tb *= Math.max(0.3f, darken);
                    }
                } catch (Exception e) {
                    //IgnoreException - если нельзя получить значения погоды, просто используем цвет без адаптации
                }
            }

            double r = original.x + (tr - original.x) * blend;
            double g = original.y + (tg - original.y) * blend;
            double b = original.z + (tb - original.z) * blend;

            cir.setReturnValue(new Vec3d(clamp01(r), clamp01(g), clamp01(b)));
        } catch (Exception e) {
            // LastResort - полностью игнорируем ошибку, возвращаем оригинальный цвет
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}