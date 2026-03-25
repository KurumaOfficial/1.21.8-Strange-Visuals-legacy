package ru.strange.client.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.Strange;
import ru.strange.client.event.EventManager;
import ru.strange.client.event.impl.EventKeyInput;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.Setting;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.ui.clickgui.GuiClient;
import ru.strange.client.utils.other.KeyBindPolicy;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {

        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (action == GLFW.GLFW_PRESS && handleModuleBindCapture(key)) {
            ci.cancel();
            return;
        }

        if (action == GLFW.GLFW_PRESS && handleSettingBindCapture(key)) {
            ci.cancel();
            return;
        }

        if (KeyBindPolicy.isProtectedFunctionKey(key)) {
            return;
        }

        EventKeyInput event = new EventKeyInput(window, key, scancode, action, modifiers);
        EventManager.call(event);

        if (event.isCancelled()) {
            ci.cancel();
            return;
        }

        if (mc.currentScreen != null) return;

        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            boolean forceOpenGui = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean canUseDefaultGuiBind = Strange.get.manager.getBind(key).length == 0;
            if (forceOpenGui || canUseDefaultGuiBind) {
                mc.setScreen(new GuiClient());
                if (mc.mouse != null) mc.mouse.unlockCursor();
                ci.cancel();
                return;
            }
        }

        if (mc.player != null && action == GLFW.GLFW_PRESS) {
            for (Module m : Strange.get.manager.getBind(key)) {
                m.toggle();
            }
        }
    }

    private boolean handleModuleBindCapture(int key) {
        for (Module module : Strange.get.manager.getModules()) {
            if (!module.binding) continue;

            boolean changed = false;
            if (KeyBindPolicy.isClearKey(key)) {
                module.bind = -1;
                changed = true;
            } else if (!KeyBindPolicy.isProtectedFunctionKey(key)) {
                module.bind = key;
                changed = true;
            }

            module.binding = false;
            module.displayName = module.name;

            if (changed && ru.strange.client.Strange.get != null && ru.strange.client.Strange.get.configManager != null) {
                ru.strange.client.Strange.get.configManager.autoSave();
            }

            return true;
        }
        return false;
    }

    private boolean handleSettingBindCapture(int key) {
        for (Module module : Strange.get.manager.getModules()) {
            for (Setting setting : module.getSettings()) {
                if (setting instanceof BindSettings bind && bind.active) {
                    if (KeyBindPolicy.isClearKey(key)) {
                        bind.set(BindSettings.NONE);
                    } else if (!KeyBindPolicy.isProtectedFunctionKey(key)) {
                        bind.set(key);
                    }
                    bind.active = false;
                    return true;
                }
            }
        }
        return false;
    }
}
