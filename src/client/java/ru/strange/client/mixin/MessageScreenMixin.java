package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.StarterMenu.MenuBackgroundManager;

@Mixin(MessageScreen.class)
public abstract class MessageScreenMixin extends Screen {
    protected MessageScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "renderBackground(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
    private void strange$replaceMessageBackground(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!MenuBackgroundManager.shouldUseCustomBackground((Screen) (Object) this)) {
            return;
        }

        MenuBackgroundManager.renderPanoramaBackground(context, width, height, delta);
        if (MenuBackgroundManager.shouldApplyBlur((Screen) (Object) this)) {
            MenuBackgroundManager.renderBlur(context, 0, 0, width, height);
        }
        MenuBackgroundManager.renderDarkening(context, 0, 0, width, height, false);
        ci.cancel();
    }
}
