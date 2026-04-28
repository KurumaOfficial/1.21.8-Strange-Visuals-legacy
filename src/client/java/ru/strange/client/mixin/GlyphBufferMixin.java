package ru.strange.client.mixin;

import me.x150.renderer.fontng.GlyphBuffer;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.text.TextColor;
import org.joml.Matrix3x2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.strange.client.utils.render.ITextColorWithAlpha;

@Mixin(value = GlyphBuffer.class, remap = false)
public abstract class GlyphBufferMixin {
    @Redirect(
            method = "createBounds",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ScreenRect;transformEachVertex(Lorg/joml/Matrix3x2f;)Lnet/minecraft/client/gui/ScreenRect;"
            ),
            remap = false
    )
    private static ScreenRect disableTransform(ScreenRect instance, Matrix3x2f transformation) {
        return instance;
    }
    @Redirect(
            method = "lambda$draw$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/TextColor;getRgb()I"
            ),
            remap = false,
            require = 0
    )
    private int getFullColorForGlyphs(TextColor textColor) {
        return ((ITextColorWithAlpha) (Object)textColor).strange$getFullColor();
    }

    @ModifyConstant(
            method = "lambda$draw$1",
            constant = @Constant(intValue = -16777216), // 0xFF000000 as signed int
            remap = false,
            require = 0
    )
    private int removeGlyphAlphaMask(int constant) {
        return 0; // | 0xFF000000
    }

    @Redirect(
            method = "lambda$draw$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/text/TextColor;getRgb()I"
            ),
            remap = false,
            require = 0
    )
    private static int getFullColorForDecorations(TextColor textColor) {
        return ((ITextColorWithAlpha) (Object)textColor).strange$getFullColor();
    }
    @ModifyConstant(
            method = "lambda$draw$3",
            constant = @Constant(intValue = -16777216),
            remap = false,
            require = 0
    )
    private static int removeDecorationAlphaMask(int constant) {
        return 0;
    }
}
