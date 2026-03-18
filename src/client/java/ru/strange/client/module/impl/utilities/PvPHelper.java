package ru.strange.client.module.impl.utilities;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;

@IModule(
        name = "ПвП Хелпер",
        description = "",
        category = Category.Utilities,
        bind = -1
)
public class PvPHelper extends Module {

    private final BindSettings pearlBind = new BindSettings("Бинд на пёрл", GLFW.GLFW_KEY_P);
    private final BindSettings elytraBind = new BindSettings("Бинд на элитру", GLFW.GLFW_KEY_Y);

    private boolean pearlLatch;
    private boolean elytraLatch;

    public PvPHelper() {
        addSettings(pearlBind, elytraBind);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        long window = mc.getWindow().getHandle();

        boolean pearlDown = isKeyDown(window, pearlBind.get());
        if (pearlDown && !pearlLatch) {
            switchToItem(Items.ENDER_PEARL);
            pearlLatch = true;
        } else if (!pearlDown) {
            pearlLatch = false;
        }

        boolean elytraDown = isKeyDown(window, elytraBind.get());
        if (elytraDown && !elytraLatch) {
            toggleElytraChestSwapIfOpen();
            elytraLatch = true;
        } else if (!elytraDown) {
            elytraLatch = false;
        }
    }

    private void switchToItem(Item item) {
        if (mc.player == null) return;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                mc.player.getInventory().setSelectedSlot(i);
                break;
            }
        }
    }

    /**
     * Легитная замена:
     * работает только если открыт инвентарь
     */
    private void toggleElytraChestSwapIfOpen() {
        if (mc.player == null || mc.interactionManager == null) return;

        if (!(mc.currentScreen instanceof HandledScreen<?>)) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) return;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;

        int chestSlotId = 6;

        if (chestSlotId < 0 || chestSlotId >= mc.player.playerScreenHandler.slots.size()) return;

        ItemStack chestStack = mc.player.playerScreenHandler.getSlot(chestSlotId).getStack();
        boolean wearingElytra = !chestStack.isEmpty() && chestStack.getItem() == Items.ELYTRA;

        if (!wearingElytra) {
            int elytraInvIndex = findBestInventoryIndex(Items.ELYTRA);
            if (elytraInvIndex == -1) return;

            int sourceSlotId = inventoryIndexToSlotId(elytraInvIndex);
            if (sourceSlotId == -1) return;

            swapSlots(sourceSlotId, chestSlotId);
            return;
        }

        int chestplateInvIndex = findBestChestplateIndex();
        if (chestplateInvIndex == -1) return;

        int sourceSlotId = inventoryIndexToSlotId(chestplateInvIndex);
        if (sourceSlotId == -1) return;

        swapSlots(sourceSlotId, chestSlotId);
    }

    private void swapSlots(int slotA, int slotB) {
        if (mc.player == null || mc.interactionManager == null) return;

        int syncId = mc.player.currentScreenHandler.syncId;

        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotB, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findBestInventoryIndex(Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }

        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    private int findBestChestplateIndex() {
        if (mc.player == null) return -1;

        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (item == Items.ELYTRA) continue;
            if (!isChestplate(item)) continue;

            int score = getChestplateScore(item);

            if (i < 9) score += 50;

            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private boolean isChestplate(Item item) {
        return item == Items.NETHERITE_CHESTPLATE
                || item == Items.DIAMOND_CHESTPLATE
                || item == Items.IRON_CHESTPLATE
                || item == Items.CHAINMAIL_CHESTPLATE
                || item == Items.GOLDEN_CHESTPLATE
                || item == Items.LEATHER_CHESTPLATE;
    }

    private int getChestplateScore(Item item) {
        if (item == Items.NETHERITE_CHESTPLATE) return 600;
        if (item == Items.DIAMOND_CHESTPLATE) return 500;
        if (item == Items.IRON_CHESTPLATE) return 400;
        if (item == Items.CHAINMAIL_CHESTPLATE) return 300;
        if (item == Items.GOLDEN_CHESTPLATE) return 200;
        if (item == Items.LEATHER_CHESTPLATE) return 100;
        return 0;
    }

    private int inventoryIndexToSlotId(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex < 9) {
            return 36 + inventoryIndex;
        }
        if (inventoryIndex >= 9 && inventoryIndex < 36) {
            return inventoryIndex;
        }
        return -1;
    }

    private static boolean isKeyDown(long window, int keyCode) {
        if (keyCode == -1) return false;
        return InputUtil.isKeyPressed(window, keyCode);
    }
}