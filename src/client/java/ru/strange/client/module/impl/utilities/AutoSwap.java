package ru.strange.client.module.impl.utilities;

import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
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
        description = "",
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
    private final SliderSetting delay = new SliderSetting("Задержка", 150, 50, 400, 10, false);

    private boolean lastKeyState;
    private boolean swapRequested;

    private long requestTime;
    private long pressCooldown;

    private int pendingHotbarSlot = -1;
    private Item pendingItem = Items.AIR;

    public AutoSwap() {
        addSettings(from, to, swapKey, delay);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (swapKey.get() == -1) return;

        long window = mc.getWindow().getHandle();
        boolean pressed = isBindDown(window, swapKey.get());

        if (pressed && !lastKeyState) {
            long now = System.currentTimeMillis();
            if (now - pressCooldown >= 250L) {
                beginSwap();
                pressCooldown = now;
            }
        }

        lastKeyState = pressed;

        if (!swapRequested) return;

        if (mc.currentScreen != null) {
            resetSwapState();
            return;
        }

        if (pendingItem == Items.AIR) {
            pendingItem = resolveWantedOffhandItem();

            if (pendingItem == Items.AIR) {
                resetSwapState();
                return;
            }

            if (mc.player.getOffHandStack().getItem() == pendingItem) {
                resetSwapState();
                return;
            }

            pendingHotbarSlot = findInHotbar(pendingItem);
            if (pendingHotbarSlot == -1) {
                resetSwapState();
                return;
            }

            requestTime = System.currentTimeMillis();
            return;
        }

        if (System.currentTimeMillis() - requestTime < (long) delay.get()) {
            return;
        }

        doSwap();
    }

    private void beginSwap() {
        swapRequested = true;
        requestTime = 0L;
        pendingHotbarSlot = -1;
        pendingItem = Items.AIR;
    }

    private void doSwap() {
        if (mc.player == null) {
            resetSwapState();
            return;
        }

        if (pendingHotbarSlot < 0 || pendingHotbarSlot > 8) {
            resetSwapState();
            return;
        }

        if (mc.player.getInventory().getStack(pendingHotbarSlot).getItem() != pendingItem) {
            resetSwapState();
            return;
        }

        int previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(pendingHotbarSlot);

        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN,
                    Direction.DOWN
            ));
        }

        mc.player.getInventory().setSelectedSlot(previousSelectedSlot);
        resetSwapState();
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

    private void resetSwapState() {
        swapRequested = false;
        requestTime = 0L;
        pendingHotbarSlot = -1;
        pendingItem = Items.AIR;
    }

    @Override
    public void onDisable() {
        resetSwapState();
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
