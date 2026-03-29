package ru.strange.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.Strange;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventLook;
import ru.strange.client.event.impl.EventMouseInput;
import ru.strange.client.event.impl.EventMouseScroll;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.other.FreeLookHandler;

@Mixin(Mouse.class)
public abstract class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (handleModuleMouseBindCapture(button)) {
            ci.cancel();
            return;
        }

        if (handleSettingMouseBindCapture(button)) {
            ci.cancel();
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        EventMouseInput event = new EventMouseInput(window, button, action, modifiers);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }
        if (mc.player != null) {
            int bindCode = BindSettings.mouseCode(button);
            for (Module m : Strange.get.manager.getBind(bindCode)) {
                m.toggle();
            }
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    private void onMouseScroll(long window, double horizontalAmount, double verticalAmount, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        double mouseX = mc.mouse.getX();
        double mouseY = mc.mouse.getY();
        EventManager.call(new EventMouseScroll(mouseX, mouseY, horizontalAmount, verticalAmount));
    }

    @Redirect(
            method = "updateMouse",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
            )
    )
    private void redirectLook(ClientPlayerEntity player, double yawDelta, double pitchDelta) {
        EventLook event = new EventLook(yawDelta, pitchDelta);
        EventManager.call(event);

        if (FreeLookHandler.isActive()) {
            FreeLookHandler.handleMouse(event.yaw, event.pitch);
            return;
        }

        if (!event.isCancelled()) {
            player.changeLookDirection(event.yaw, event.pitch);
        }
    }

    private boolean handleModuleMouseBindCapture(int button) {
        for (Module module : Strange.get.manager.getModules()) {
            if (!module.binding) continue;

            module.setBind(BindSettings.mouseCode(button));
            module.binding = false;
            module.displayName = module.name;

            return true;
        }
        return false;
    }

    private boolean handleSettingMouseBindCapture(int button) {
        for (Module module : Strange.get.manager.getModules()) {
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BindSettings bind && bind.active) {
                    bind.set(BindSettings.mouseCode(button));
                    bind.active = false;
                    return true;
                }
            }
        }
        return false;
    }
}
