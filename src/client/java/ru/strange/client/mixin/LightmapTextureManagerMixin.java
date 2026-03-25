package ru.strange.client.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.strange.client.Strange;
import ru.strange.client.module.impl.other.FullBright;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 1))
    private float night$getGammaValue(Double instance) {
        if (Strange.get != null && Strange.get.manager != null) {
            FullBright module = Strange.get.manager.get(FullBright.class);
            if (module != null && module.usesGammaBoost()) {
                return 200F;
            }
        }
        return instance.floatValue();
    }
}
