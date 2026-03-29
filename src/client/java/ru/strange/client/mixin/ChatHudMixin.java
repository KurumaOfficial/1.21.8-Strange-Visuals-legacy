package ru.strange.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.mixin.accessor.ChatHudAccessor;
import ru.strange.client.module.impl.utilities.ChatHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @Unique
    private static String strange$lastRawMessage;

    @Unique
    private static Text strange$lastStyledMessage;

    @Unique
    private static MessageSignatureData strange$lastSignature;

    @Unique
    private static MessageIndicator strange$lastIndicator;

    @Unique
    private static int strange$lastCount = 1;

    @Unique
    private static final AtomicBoolean strange$updatingMessage = new AtomicBoolean(false);

    @Unique
    private List<String> strange$historySnapshot;

    @Unique
    private static void strange$resetAntiSpamState() {
        strange$lastRawMessage = null;
        strange$lastStyledMessage = null;
        strange$lastSignature = null;
        strange$lastIndicator = null;
        strange$lastCount = 1;
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void strange$antiSpam(Text message, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if (!ChatHelper.isAntiSpamEnabled() || message == null || strange$updatingMessage.get()) {
            return;
        }

        String raw = message.getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }

        if (raw.equals(strange$lastRawMessage)
                && Objects.equals(signature, strange$lastSignature)
                && Objects.equals(indicator, strange$lastIndicator)) {
            ci.cancel();
            strange$lastCount++;

            Text baseMessage = strange$lastStyledMessage == null ? message.copy() : strange$lastStyledMessage.copy();
            Text rebuilt = baseMessage.copy()
                    .append(Text.literal(" [x" + strange$lastCount + "]").formatted(Formatting.GRAY));

            ChatHudAccessor accessor = (ChatHudAccessor) this;
            strange$removeLatestEntry(accessor);
            accessor.invokeReset();

            strange$updatingMessage.set(true);
            try {
                ((ChatHud) (Object) this).addMessage(rebuilt, strange$lastSignature, strange$lastIndicator);
            } finally {
                strange$updatingMessage.set(false);
            }
            return;
        }

        strange$lastRawMessage = raw;
        strange$lastStyledMessage = message.copy();
        strange$lastSignature = signature;
        strange$lastIndicator = indicator;
        strange$lastCount = 1;
    }

    @Inject(method = "clear", at = @At("HEAD"))
    private void strange$captureHistory(boolean clearHistory, CallbackInfo ci) {
        strange$resetAntiSpamState();
        if (clearHistory && ChatHelper.isKeepHistoryEnabled()) {
            ChatHud chatHud = (ChatHud) (Object) this;
            strange$historySnapshot = new ArrayList<>(chatHud.getMessageHistory());
        } else {
            strange$historySnapshot = null;
        }
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void strange$restoreHistory(boolean clearHistory, CallbackInfo ci) {
        if (!clearHistory || strange$historySnapshot == null || strange$historySnapshot.isEmpty()) {
            strange$historySnapshot = null;
            return;
        }

        ChatHud chatHud = (ChatHud) (Object) this;
        for (String historyEntry : strange$historySnapshot) {
            chatHud.getMessageHistory().add(historyEntry);
        }
        strange$historySnapshot = null;
    }

    @Unique
    private static void strange$removeLatestEntry(ChatHudAccessor accessor) {
        List<ChatHudLine> messages = accessor.getMessages();
        if (!messages.isEmpty()) {
            messages.remove(0);
        }

        List<ChatHudLine.Visible> visibleMessages = accessor.getVisibleMessages();
        while (!visibleMessages.isEmpty()) {
            ChatHudLine.Visible visible = visibleMessages.remove(0);
            if (visible.endOfEntry()) {
                break;
            }
        }
    }
}
