package ru.strange.client.mixin;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.Strange;
import ru.strange.client.module.impl.world.BlockOutline;

@Mixin(WorldRenderer.class)
public abstract class BlockOutlineCancelMixin {

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"), cancellable = true)
    private void strange$cancelVanillaBlockOutline(net.minecraft.client.render.Camera camera,
                                                   net.minecraft.client.render.VertexConsumerProvider.Immediate immediate,
                                                   net.minecraft.client.util.math.MatrixStack matrices,
                                                   boolean translucent,
                                                   CallbackInfo ci) {
        if (Strange.get == null || Strange.get.manager == null) return;
        BlockOutline module = Strange.get.manager.get(BlockOutline.class);
        if (module != null && module.enable) {
            ci.cancel();
        }
    }
}
