package ru.strange.client.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.strange.client.Strange;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventChat;
import ru.strange.client.mixin.accessor.ChatHudAccessor;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;
import ru.strange.client.module.impl.other.NameProtect;
import ru.strange.client.module.impl.utilities.ChatHelper;
import ru.strange.client.module.impl.utilities.GPS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private int strange$chatRenderTick;

    @Unique
    private final Map<Integer, Integer> strange$chatLineOffsets = new HashMap<>();

    @ModifyArgs(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHudLine;<init>(ILnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V"
            )
    )
    private void strange$protectIncomingChatMessage(Args args) {
        Text message = args.get(1);
        if (message != null) {
            args.set(1, NameProtect.process(message));
        }
    }

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

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("TAIL")
    )
    private void strange$dispatchChat(Text message, @Nullable MessageSignatureData signature, @Nullable MessageIndicator indicator, CallbackInfo ci) {
        if (message == null) {
            return;
        }

        EventManager.call(new EventChat(message, signature == null));

        if (Strange.get == null || Strange.get.manager == null) {
            return;
        }

        GPS gps = Strange.get.manager.get(GPS.class);
        if (gps != null) {
            gps.handleAutoGpsMessage(message, signature, indicator);
        }
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

    @Inject(method = "render", at = @At("HEAD"))
    private void strange$beginChatRender(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        strange$chatRenderTick = currentTick;
        strange$chatLineOffsets.clear();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void strange$endChatRender(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        strange$chatLineOffsets.clear();
    }

    @ModifyArgs(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)V"
            ),
            require = 0
    )
    private void strange$animateVisibleChatLine(Args args) {
        OrderedText text = args.get(1);
        int y = args.get(3);
        int offset = strange$getAnimatedChatOffset(text);
        if (offset != 0) {
            strange$chatLineOffsets.put(y, offset);
        }
        args.set(2, args.<Integer>get(2) + offset);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/MessageIndicator$Icon;draw(Lnet/minecraft/client/gui/DrawContext;II)V"
            ),
            require = 0
    )
    private void strange$animateVisibleChatIndicator(MessageIndicator.Icon icon, DrawContext context, int x, int y) {
        icon.draw(context, x + strange$chatLineOffsets.getOrDefault(y, 0), y);
    }

    @Unique
    private int strange$getAnimatedChatOffset(OrderedText text) {
        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothChat()) {
            return 0;
        }

        ChatHudLine.Visible visibleLine = strange$findVisibleLine(text);
        if (visibleLine == null) {
            return 0;
        }

        int age = Math.max(0, strange$chatRenderTick - visibleLine.addedTime());
        if (age >= 14) {
            return 0;
        }

        float progress = strange$easeOutCubic(Math.min(1.0f, age / 14.0f));
        float inverse = 1.0f - progress;
        float width = visibleLine.endOfEntry() ? 30.0f : 22.0f;
        return -Math.round(width * inverse + 12.0f * inverse * inverse);
    }

    @Unique
    private ChatHudLine.Visible strange$findVisibleLine(OrderedText text) {
        ChatHudAccessor accessor = (ChatHudAccessor) this;
        for (ChatHudLine.Visible visibleLine : accessor.getVisibleMessages()) {
            if (visibleLine.content() == text) {
                return visibleLine;
            }
        }
        return null;
    }

    @Unique
    private static float strange$easeOutCubic(float progress) {
        float inverse = 1.0f - progress;
        return 1.0f - inverse * inverse * inverse;
    }
}
