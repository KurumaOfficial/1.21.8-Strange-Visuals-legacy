package ru.strange.client.module.impl.utilities;

import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;

@IModule(
        name = "Авто бег",
        description = "Автоматический бег",
        category = Category.Utilities,
        bind = -1
)
public class AutoRun extends Module {
    public static int tick = 0;

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
        boolean horizontal = mc.player.horizontalCollision && !mc.player.collidedSoftly;

        if (tick != 0) {
            mc.player.setSprinting(false);
            mc.options.sprintKey.setPressed(false);
            tick--;
            return;
        }

        if (!mc.player.isSneaking() && !horizontal && mc.options.forwardKey.isPressed() && !mc.player.isUsingItem()) {
            mc.player.setSprinting(true);
            mc.options.sprintKey.setPressed(true);
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }
}
