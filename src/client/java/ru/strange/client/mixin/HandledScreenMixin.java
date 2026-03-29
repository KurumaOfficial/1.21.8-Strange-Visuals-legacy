package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.utilities.ItemScroller;
import ru.strange.client.utils.math.animation.AnimationUtils;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private Slot lastSlot = null;
    private long lastMoveTime = 0;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;

        boolean shiftPressed = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                              GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

        if (!shiftPressed) {
            lastSlot = null;
            return;
        }

        Module itemScroller = Strange.get.manager.getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.enable) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        Slot slot = screen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack() || mc.interactionManager == null) return;

        performQuickMove(screen, slot);
        lastSlot = slot;
        lastMoveTime = System.currentTimeMillis();
        cir.setReturnValue(true);
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

        Module itemScroller = Strange.get.manager.getModule(ItemScroller.class);
        if (itemScroller == null || !itemScroller.enable) return;

        HandledScreen<?> screen = (HandledScreen<?>) (Object) this;
        Slot slot = screen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack() || mc.interactionManager == null) return;

        if (slot != lastSlot && System.currentTimeMillis() - lastMoveTime > 50) {
            performQuickMove(screen, slot);
            lastSlot = slot;
            lastMoveTime = System.currentTimeMillis();
        }
    }

    private void performQuickMove(HandledScreen<?> screen, Slot slot) {
        if (slot.inventory == mc.player.getInventory() && slot.id >= 9 && slot.id < 45) {
            mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );
        } else if (slot.inventory != mc.player.getInventory()) {
            mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );
        }
    }
}
