package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (chatText.startsWith(".") && Strange.get.commandManager != null) {
            boolean handled = Strange.get.commandManager.handleCommand(chatText);
            if (handled) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void strange$updateDotSuggestions(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (chatField == null) {
            return;
        }

        String text = chatField.getText();
        if (text != null && text.startsWith(".") && Strange.get != null && Strange.get.commandManager != null) {
            String suffix = Strange.get.commandManager.getSuggestionSuffix(text);
            chatField.setSuggestion(suffix == null || suffix.isEmpty() ? null : suffix);
        } else {
            chatField.setSuggestion(null);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void strange$completeDotCommand(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode != GLFW.GLFW_KEY_TAB || chatField == null || Strange.get == null || Strange.get.commandManager == null) {
            return;
        }

        String text = chatField.getText();
        if (text == null || !text.startsWith(".")) {
            return;
        }

        String completed = Strange.get.commandManager.applySuggestion(text);
        if (completed == null || completed.equals(text)) {
            return;
        }

        chatField.setText(completed);
        chatField.setCursorToEnd(false);
        cir.setReturnValue(true);
    }
}
