package ru.strange.client.module.impl.utilities;

import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Авто Свап",
        description = "Мгновенный свап предметов в оффхенд",
        category = Category.Utilities,
        bind = -1
)
public class AutoSwap extends Module {

    private final ModeSetting from = new ModeSetting(
            "Свап с",
            "Тотем",
            "Тотем",
            "Сфера"
    );

    private final ModeSetting to = new ModeSetting(
            "Свап на",
            "Сфера",
            "Тотем",
            "Сфера"
    );

    private final BindSettings swapKey = new BindSettings("Кнопка свапа", GLFW.GLFW_KEY_G);
    private final SliderSetting delay = new SliderSetting("Задержка", 50, 0, 400, 10, false);

    private boolean lastKeyState;
    private long pressCooldown;
    private long pendingSwapTime;
    private boolean hasPending;

    public AutoSwap() {
        addSettings(from, to, swapKey, delay);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (swapKey.get() == -1) return;
        if (mc.currentScreen != null) {
            hasPending = false;
            return;
        }

        long window = mc.getWindow().getHandle();
        boolean pressed = isBindDown(window, swapKey.get());
        long now = System.currentTimeMillis();

        if (pressed && !lastKeyState && now - pressCooldown >= 100L) {
            pressCooldown = now;

            if ((long) delay.get() == 0) {
                executeSwap();
            } else {
                hasPending = true;
                pendingSwapTime = now;
            }
        }

        lastKeyState = pressed;

        if (hasPending && now - pendingSwapTime >= (long) delay.get()) {
            executeSwap();
            hasPending = false;
        }
    }

    private void executeSwap() {
        if (mc.player == null || mc.player.networkHandler == null) return;

        Item wantedItem = resolveWantedOffhandItem();
        if (wantedItem == Items.AIR) return;
        if (mc.player.getOffHandStack().getItem() == wantedItem) return;

        int slot = findInHotbar(wantedItem);
        if (slot == -1) return;

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
        ));

        mc.player.getInventory().setSelectedSlot(previousSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
    }

    private Item resolveWantedOffhandItem() {
        if (mc.player == null) return Items.AIR;

        Item fromItem = getItemByType(from.get());
        Item toItem = getItemByType(to.get());
        Item offhandItem = mc.player.getOffHandStack().getItem();

        if (fromItem == Items.AIR || toItem == Items.AIR) {
            return Items.AIR;
        }

        if (fromItem == toItem) {
            return fromItem;
        }

        if (offhandItem == fromItem) {
            return toItem;
        }

        if (offhandItem == toItem) {
            return fromItem;
        }

        return fromItem;
    }

    private Item getItemByType(String type) {
        return switch (type) {
            case "Тотем" -> Items.TOTEM_OF_UNDYING;
            case "Сфера" -> Items.PLAYER_HEAD;
            default -> Items.AIR;
        };
    }

    private int findInHotbar(Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public void onDisable() {
        hasPending = false;
        lastKeyState = false;
        super.onDisable();
    }

    private static boolean isBindDown(long window, int keyCode) {
        if (keyCode == -1) return false;

        if (BindSettings.isMouseCode(keyCode)) {
            return GLFW.glfwGetMouseButton(window, BindSettings.toMouseButton(keyCode)) == GLFW.GLFW_PRESS;
        }

        return InputUtil.isKeyPressed(window, keyCode);
    }
}
