package ru.strange.client.mixin;

import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.utils.render.ITextColorWithAlpha;
import ru.strange.client.utils.render.TextColorAlphaStorage;

@Mixin(TextColor.class)
public class TextColorMixin implements ITextColorWithAlpha {
    @Final
    @Shadow
    private int rgb;

    @Unique
    private int strange$fullColor = 0;

    @Unique
    private boolean strange$hasAlpha = false;

    @ModifyVariable(
            method = "<init>(I)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static int captureFullColor(int rgb) {
        TextColorAlphaStorage.setPending(rgb);
        return rgb;
    }

    @ModifyVariable(
            method = "<init>(ILjava/lang/String;)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true
    )
    private static int captureFullColorNamed(int rgb) {
        TextColorAlphaStorage.setPending(rgb);
        return rgb;
    }

    @Inject(method = "<init>(I)V", at = @At("RETURN"))
    private void storeCapturedColor(int rgb, CallbackInfo ci) {
        final var pending = TextColorAlphaStorage.getPending();
        this.strange$fullColor = pending;
        this.strange$hasAlpha = (pending & 0xFF000000) != 0;
        TextColorAlphaStorage.clearPending();
    }

    @Inject(method = "<init>(ILjava/lang/String;)V", at = @At("RETURN"))
    private void storeCapturedColorNamed(int rgb, String name, CallbackInfo ci) {
        final var pending = TextColorAlphaStorage.getPending();
        this.strange$fullColor = pending;
        this.strange$hasAlpha = (pending & 0xFF000000) != 0;
        TextColorAlphaStorage.clearPending();
    }

    @Override
    @Unique
    public int strange$getFullColor() {
        int result;

        if (this.strange$hasAlpha) {
            result = (this.strange$fullColor & 0xFF000000) | (this.rgb & 0xFFFFFF);
        } else {
            result = 0xFF000000 | this.rgb;
        }

        return result;
    }
}