package ru.strange.client.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.module.impl.other.SkyColor;

@Mixin(World.class)
public class WorldSkyColorMixin {

    @Inject(
            method = "getSkyColor(Lnet/minecraft/util/math/Vec3d;F)Lnet/minecraft/util/math/Vec3d;",
            at = @At("RETURN"),
            cancellable = true,
            require = 0
    )
    private void strange$overrideSkyColor(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        if (!SkyColor.isActiveSkyColor()) return;
        if (!((Object) this instanceof ClientWorld world)) return;

        Vec3d original = cir.getReturnValue();
        if (original == null) original = Vec3d.ZERO;

        float blend = SkyColor.getBlendStrength();
        if (blend <= 0.0f) return;

        int color = SkyColor.getSkyColorRGB();
        double tr = ((color >> 16) & 0xFF) / 255.0;
        double tg = ((color >> 8) & 0xFF) / 255.0;
        double tb = (color & 0xFF) / 255.0;

        if (SkyColor.shouldAdaptWeather()) {
            float rain = world.getRainGradient(tickDelta);
            float thunder = world.getThunderGradient(tickDelta);
            float darken = 1.0f - Math.min(0.45f, rain * 0.24f + thunder * 0.20f);
            tr *= darken; tg *= darken; tb *= darken;
        }

        double r = original.x + (tr - original.x) * blend;
        double g = original.y + (tg - original.y) * blend;
        double b = original.z + (tb - original.z) * blend;

        cir.setReturnValue(new Vec3d(clamp01(r), clamp01(g), clamp01(b)));
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}