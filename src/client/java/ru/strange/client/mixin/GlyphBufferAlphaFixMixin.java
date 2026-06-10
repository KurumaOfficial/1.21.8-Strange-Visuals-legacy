package ru.strange.client.mixin;

import me.x150.renderer.fontng.GlyphBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = GlyphBuffer.class, remap = false)
public class GlyphBufferAlphaFixMixin {
    @ModifyVariable(
            method = "draw(Lnet/minecraft/client/gui/DrawContext;FF)V",
            at = @At(
                    value = "STORE",
                    ordinal = 0
            ),
            name = "i")
    private int modifyGlyphColor(int originalColor) {
        return originalColor & 0x00FFFFFF;
    }
}