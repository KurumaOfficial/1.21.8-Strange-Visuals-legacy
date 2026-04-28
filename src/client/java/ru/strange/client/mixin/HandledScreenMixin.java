package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.interfaces.BetterMinecraft;
import ru.strange.client.module.impl.utilities.ItemScroller;
import ru.strange.client.module.impl.utilities.ShulkerPreview;

import java.util.List;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    @Unique
    private boolean strange$pushedMatrix = false;

    private Slot lastSlot = null;
    private long lastMoveTime = 0;

    @Inject(method = "render", at = @At("HEAD"))
    private void strange$animateRenderStart(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        strange$pushedMatrix = false;

        if ((Object) this instanceof InventoryScreen || (Object) this instanceof CreativeInventoryScreen) {
            return;
        }

        BetterMinecraft module = BetterMinecraft.getInstance();
        if (module == null || !module.isSmoothScreens()) {
            return;
        }

        double progress = module.getContainerProgress();
        if (progress >= 0.999) {
            return;
        }

        float p = (float) progress;
        float inverse = 1.0f - p;
        float settle = 1.0f + (float) Math.sin(p * Math.PI) * inverse * 0.02f;
        float scaleX = (0.95f + 0.05f * p) * settle;
        float scaleY = 0.70f + 0.30f * p;
        float yOffset = inverse * inverse * 24.0f + inverse * 10.0f;
        float depthLift = inverse * 10.0f;
        float anchorX = context.getScaledWindowWidth() / 2.0f;
        float anchorY = context.getScaledWindowHeight() / 2.0f;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(anchorX, anchorY - yOffset + depthLift);
        context.getMatrices().scale(scaleX, scaleY);
        context.getMatrices().translate(-anchorX, -anchorY);
        strange$pushedMatrix = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void strange$animateRenderEnd(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (strange$pushedMatrix) {
            context.getMatrices().popMatrix();
            strange$pushedMatrix = false;
        }

        ShulkerPreview.renderPreview(context, (HandledScreen<?>) (Object) this, mouseX, mouseY);
    }

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true)
    private void strange$hideVanillaShulkerTooltip(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        if (ShulkerPreview.shouldHideVanillaTooltip((HandledScreen<?>) (Object) this, mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"), cancellable = true)
    private void strange$simplifyShulkerTooltip(ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        if (ShulkerPreview.shouldSimplifyTooltip(stack)) {
            cir.setReturnValue(ShulkerPreview.getSimplifiedTooltip(stack));
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        boolean shiftPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (!shiftPressed) {
            lastSlot = null;
            return;
        }

        ItemScroller itemScroller = getEnabledItemScroller();
        if (itemScroller == null) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        Slot slot = screen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack() || mc.interactionManager == null) return;

        if (itemScroller.quickMoveSlot(screen, slot)) {
            lastSlot = slot;
            lastMoveTime = System.currentTimeMillis();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        boolean shiftPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (!shiftPressed) {
            lastSlot = null;
            return;
        }

        ItemScroller itemScroller = getEnabledItemScroller();
        if (itemScroller == null) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        Slot slot = screen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack() || mc.interactionManager == null) return;

        if (slot != lastSlot && System.currentTimeMillis() - lastMoveTime >= itemScroller.getActionDelayMs()) {
            if (itemScroller.quickMoveSlot(screen, slot)) {
                lastSlot = slot;
                lastMoveTime = System.currentTimeMillis();
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private ItemScroller getEnabledItemScroller() {
        if (Strange.get == null || Strange.get.manager == null) {
            return null;
        }

        Module itemScroller = Strange.get.manager.getModule(ItemScroller.class);
        if (!(itemScroller instanceof ItemScroller scroller) || !scroller.enable) {
            return null;
        }
        return scroller;
    }
}
