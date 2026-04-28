package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.CursorParticles;

@Mixin(Screen.class)
public class CursorParticlesScreenMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void strange$renderCursorParticles(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        CursorParticles.renderScreenParticles(context);
    }
}
