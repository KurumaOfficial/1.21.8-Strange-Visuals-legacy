package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.player.PlayerEntity;
import ru.strange.client.event.Event;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.other.CapeUtil;
import ru.strange.client.utils.other.KeyUtil;

/**
 * Модуль для управления кастомными плащами
 */
@IModule(
        name = "Плащи",
        description = "Загрузка и применение кастомных плащей",
        category = Category.Utilities,
        bind = -1
)
public class CapeModule extends Module {
    private final BindSettings loadBind = new BindSettings("Загрузить плащ", 76); // GLFW_KEY_L
    private final BindSettings resetBind = new BindSettings("Сбросить плащ", 82); // GLFW_KEY_R

    public CapeModule() {
        addSettings(loadBind, resetBind);
    }

    @Override
    public void onEnable() {
        // Cape loading is handled automatically via mixin in PlayerListEntryMixin
    }

    @Override
    public void onDisable() {
        // Cleanup on disable if needed
    }

    public void onEvent(Event event) {
        if (event instanceof EventRender3D) {
            // CapeUtil applies cape automatically via mixin in PlayerListEntryMixin
            // We just provide UI controls
        }
    }

    public void onLoadCapePressed() {
        CapeUtil.uiPickAndApplyCape();
    }

    public void onResetCapePressed() {
        CapeUtil.uiResetCape();
    }

    // Helper methods to check if bind keys pressed (could be called from update loop)
    public boolean isLoadPressed() {
        return loadBind.get() != 0 && org.lwjgl.glfw.GLFW.glfwGetKey(
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle(),
                loadBind.get()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    public boolean isResetPressed() {
        return resetBind.get() != 0 && org.lwjgl.glfw.GLFW.glfwGetKey(
                net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle(),
                resetBind.get()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}