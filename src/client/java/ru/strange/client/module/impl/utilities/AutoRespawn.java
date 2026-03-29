package ru.strange.client.module.impl.utilities;

import net.minecraft.client.gui.screen.DeathScreen;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;

import java.util.Locale;

@IModule(
        name = "Авто респавн",
        description = "Автоматический респавн с показом координат смерти",
        category = Category.Utilities,
        bind = -1
)
public class AutoRespawn extends Module {

    private final BooleanSetting deathCoords = new BooleanSetting("Координаты смерти", true);

    private boolean shouldRespawn = true;

    public AutoRespawn() {
        addSettings(deathCoords);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) return;

        if (mc.currentScreen instanceof DeathScreen) {
            if (shouldRespawn) {
                if (deathCoords.get()) {
                    sendDeathCoords();
                }

                mc.player.requestRespawn();
                mc.setScreen(null);
                shouldRespawn = false;
            }
        } else {
            shouldRespawn = true;
        }
    }

    private void sendDeathCoords() {
        if (mc.player == null) return;

        String coords = String.format(
                Locale.US,
                "Координаты смерти: %.1f, %.1f, %.1f",
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ()
        );

        if (mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
            mc.inGameHud.getChatHud().addMessage(net.minecraft.text.Text.literal(coords));
        }
    }
}