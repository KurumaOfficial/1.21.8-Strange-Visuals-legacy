package ru.strange.client.module.impl.utilities;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventMouseScroll;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Прокрутка предметов",
        description = " ",
        category = Category.Utilities,
        bind = -1
)
public class ItemScroller extends Module {

    private final SliderSetting scrollSpeed = new SliderSetting("Скорость", 1, 1, 10, 1, false);

    public ItemScroller() {
        addSettings(scrollSpeed);
    }

    @EventInit
    public void onMouseScroll(EventMouseScroll event) {
        if (!enable || mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        if (event.verticalAmount() == 0 || mc.interactionManager == null) {
            return;
        }

        double mouseX = event.mouseX() / mc.getWindow().getScaleFactor();
        double mouseY = event.mouseY() / mc.getWindow().getScaleFactor();

        Slot slot = handledScreen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) {
            return;
        }

        int direction = event.verticalAmount() > 0 ? 1 : -1;
        int count = (int) scrollSpeed.get();

        for (int i = 0; i < count; i++) {
            if (direction > 0) {
                moveItemsToContainer(handledScreen, slot);
            } else {
                moveItemsFromContainer(handledScreen, slot);
            }
        }
    }

    private void moveItemsToContainer(HandledScreen<?> screen, Slot slot) {
        if (slot.inventory == mc.player.getInventory() && slot.id >= 9 && slot.id < 45) {
            mc.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slot.id,
                    0,
                    SlotActionType.QUICK_MOVE,
                    mc.player
            );
        }
    }

    private void moveItemsFromContainer(HandledScreen<?> screen, Slot slot) {
        if (slot.inventory != mc.player.getInventory()) {
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
