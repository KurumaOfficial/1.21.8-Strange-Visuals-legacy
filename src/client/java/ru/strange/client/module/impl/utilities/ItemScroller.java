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
        description = "Быстрое перемещение предметов скроллом и шифт-перетаскиванием",
        category = Category.Utilities,
        bind = -1
)
public class ItemScroller extends Module {

    private final SliderSetting scrollSpeed = new SliderSetting("Скорость", 1, 1, 10, 1, false);
    private long lastScrollTime = 0;

    public ItemScroller() {
        addSettings(scrollSpeed);
    }

    public long getActionDelayMs() {
        float speedValue = Math.max(1.0f, scrollSpeed.get());
        return Math.max(15L, Math.round(230.0f - speedValue * 20.0f));
    }

    @EventInit
    public void onMouseScroll(EventMouseScroll event) {
        if (!enable || mc.currentScreen == null || !(mc.currentScreen instanceof HandledScreen<?> handledScreen)) {
            return;
        }

        if (event.verticalAmount() == 0 || mc.interactionManager == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastScrollTime < getActionDelayMs()) {
            return;
        }

        double mouseX = event.mouseX() / mc.getWindow().getScaleFactor();
        double mouseY = event.mouseY() / mc.getWindow().getScaleFactor();

        Slot slot = handledScreen.getSlotAt(mouseX, mouseY);
        if (slot == null || !slot.hasStack()) {
            return;
        }

        if (quickMoveSlot(handledScreen, slot)) {
            lastScrollTime = now;
        }
    }

    public boolean quickMoveSlot(HandledScreen<?> screen, Slot slot) {
        if (screen == null || slot == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }

        if (slot.id < 0 || !slot.hasStack()) {
            return false;
        }

        mc.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slot.id,
                0,
                SlotActionType.QUICK_MOVE,
                mc.player
        );
        return true;
    }
}
