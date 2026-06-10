package ru.strange.client.module.impl.utilities;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.utils.other.BindUtil;
import ru.strange.client.utils.render.RenderUtil;

import java.util.Comparator;

@IModule(
        name = "ПвП Хелпер",
        description = "Авто-поиск предметов в инвентаре, Mace подсветка цели",
        category = Category.Utilities,
        bind = -1
)
public class PvPHelper extends Module {

    private static final int CHEST_ARMOR_SLOT = 6;
    private static final int HOTBAR_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;
    private static final double MACE_RANGE = 4.0;
    private static final int LINE_BUFFER_SIZE = 1 << 10;

    private static final RenderPipeline MACE_LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "pvphelper_mace_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer MACE_LINE_LAYER = RenderLayer.of(
            "pvphelper_mace_lines",
            LINE_BUFFER_SIZE,
            false,
            true,
            MACE_LINE_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(java.util.OptionalDouble.of(2.0)))
                    .build(false)
    );

    private final BindSettings pearlBind = new BindSettings("Бинд на пёрл", GLFW.GLFW_KEY_P);
    private final BindSettings elytraBind = new BindSettings("Бинд на элитру", GLFW.GLFW_KEY_Y);
    private final BindSettings maceBind = new BindSettings("Бинд на Mace", GLFW.GLFW_KEY_M);
    private final BooleanSetting pearlSearch = new BooleanSetting("Искать жемчуг", true);
    private final BooleanSetting elytraSearch = new BooleanSetting("Искать элитру", true);
    private final BooleanSetting maceSearch = new BooleanSetting("Искать Mace", true);
    private final BooleanSetting maceHighlight = new BooleanSetting("Mace подсветка", true);

    private boolean pearlLatch;
    private boolean elytraLatch;
    private boolean maceLatch;
    private LivingEntity maceTarget;

    public PvPHelper() {
        addSettings(pearlBind, elytraBind, maceBind, pearlSearch, elytraSearch, maceSearch, maceHighlight);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (mc.currentScreen != null) {
            pearlLatch = false;
            elytraLatch = false;
            maceLatch = false;
            maceTarget = null;
            return;
        }

        boolean pearlDown = BindUtil.isDown(pearlBind.get());
        if (pearlDown && !pearlLatch) {
            if (pearlSearch.get()) {
                equipFromInventory(Items.ENDER_PEARL);
            } else {
                selectInHotbar(Items.ENDER_PEARL);
            }
            pearlLatch = true;
        } else if (!pearlDown) {
            pearlLatch = false;
        }

        boolean elytraDown = BindUtil.isDown(elytraBind.get());
        if (elytraDown && !elytraLatch) {
            if (elytraSearch.get()) {
                toggleElytraChestSwap();
            } else {
                selectInHotbar(Items.ELYTRA);
            }
            elytraLatch = true;
        } else if (!elytraDown) {
            elytraLatch = false;
        }

        boolean maceDown = BindUtil.isDown(maceBind.get());
        if (maceDown && !maceLatch) {
            if (maceSearch.get()) {
                equipFromInventory(Items.MACE);
            } else {
                selectInHotbar(Items.MACE);
            }
            maceLatch = true;
        } else if (!maceDown) {
            maceLatch = false;
        }

        updateMaceTarget();
    }

    private void updateMaceTarget() {
        if (!enable || !maceHighlight.get() || mc.player == null || mc.world == null) {
            maceTarget = null;
            return;
        }
        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty() || held.getItem() != Items.MACE) {
            maceTarget = null;
            return;
        }
        Vec3d eye = mc.player.getEyePos();
        maceTarget = mc.world.getEntitiesByClass(LivingEntity.class, mc.player.getBoundingBox().expand(MACE_RANGE),
                e -> e != mc.player && e.isAlive() && !e.isInvisible() && e.distanceTo(mc.player) <= MACE_RANGE)
            .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(eye))).orElse(null);
    }

    @EventInit
    public void onRender3D(EventRender3D e) {
        if (maceTarget == null) return;
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        Box box = maceTarget.getBoundingBox();
        Vec3d pos = maceTarget.getPos();
        boolean sneaking = maceTarget.isSneaking();
        double dx = sneaking ? 0.0 : (box.getLengthX() - 0.6) / 2.0;

        double wx = pos.x;
        double wy = box.minY;
        double wz = pos.z;
        double wh = box.maxY - box.minY;

        MatrixStack matrices = e.getMatrixStack();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        BufferAllocator allocator = new BufferAllocator(LINE_BUFFER_SIZE);
        VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);
        try {
            int color = RenderUtil.ColorUtil.getMainColor(1, 1);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            Matrix4f mat = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = immediate.getBuffer(MACE_LINE_LAYER);

            // Bottom face
            drawBoxEdge(buffer, mat, wx - dx, wy, wz - dx, wx + dx, wy, wz - dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy, wz - dx, wx + dx, wy, wz + dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy, wz + dx, wx - dx, wy, wz + dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx - dx, wy, wz + dx, wx - dx, wy, wz - dx, r, g, b, a);
            // Top face
            drawBoxEdge(buffer, mat, wx - dx, wy + wh, wz - dx, wx + dx, wy + wh, wz - dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy + wh, wz - dx, wx + dx, wy + wh, wz + dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy + wh, wz + dx, wx - dx, wy + wh, wz + dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx - dx, wy + wh, wz + dx, wx - dx, wy + wh, wz - dx, r, g, b, a);
            // Vertical edges
            drawBoxEdge(buffer, mat, wx - dx, wy, wz - dx, wx - dx, wy + wh, wz - dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy, wz - dx, wx + dx, wy + wh, wz - dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx + dx, wy, wz + dx, wx + dx, wy + wh, wz + dx, r, g, b, a);
            drawBoxEdge(buffer, mat, wx - dx, wy, wz + dx, wx - dx, wy + wh, wz + dx, r, g, b, a);

            immediate.draw();
        } finally {
            allocator.close();
            matrices.pop();
        }
    }

    private static void drawBoxEdge(VertexConsumer buffer, Matrix4f mat,
                                    double x1, double y1, double z1,
                                    double x2, double y2, double z2,
                                    int r, int g, int b, int a) {
        buffer.vertex(mat, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buffer.vertex(mat, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }

    private void selectInHotbar(Item item) {
        if (mc.player == null) return;
        int slot = findInHotbar(item);
        if (slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
        }
    }

    private void equipFromInventory(Item item) {
        if (mc.player == null || mc.interactionManager == null) return;

        int slot = findInHotbar(item);
        if (slot != -1) {
            mc.player.getInventory().setSelectedSlot(slot);
            return;
        }

        int invSlot = findInInventory(item);
        if (invSlot == -1) return;

        int emptySlot = findEmptyHotbarSlot();
        int targetSlot = emptySlot != -1 ? emptySlot : 0;

        int sourceSlotId = invSlot < 9 ? 36 + invSlot : 9 + (invSlot - 9);
        int targetSlotId = 36 + targetSlot;

        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, sourceSlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, targetSlotId, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, sourceSlotId, 0, SlotActionType.PICKUP, mc.player);

        mc.player.getInventory().setSelectedSlot(targetSlot);
    }

    private int findEmptyHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    private void toggleElytraChestSwap() {
        if (mc.player == null || mc.interactionManager == null) return;

        boolean openedByModule = false;
        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
            openedByModule = true;
        }

        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            if (openedByModule) mc.setScreen(null);
            return;
        }

        int chestSlotId = CHEST_ARMOR_SLOT;
        if (chestSlotId < 0 || chestSlotId >= mc.player.playerScreenHandler.slots.size()) {
            if (openedByModule) mc.setScreen(null);
            return;
        }

        ItemStack chestStack = mc.player.playerScreenHandler.getSlot(chestSlotId).getStack();
        boolean wearingElytra = !chestStack.isEmpty() && chestStack.getItem() == Items.ELYTRA;

        if (!wearingElytra) {
            int elytraIndex = findInInventory(Items.ELYTRA);
            if (elytraIndex == -1) {
                if (openedByModule) mc.setScreen(null);
                return;
            }
            int sourceSlotId = elytraIndex < 9 ? 36 + elytraIndex : 9 + (elytraIndex - 9);
            swapSlots(sourceSlotId, chestSlotId);
        } else {
            int chestplateIndex = findBestChestplateIndex();
            if (chestplateIndex == -1) {
                if (openedByModule) mc.setScreen(null);
                return;
            }
            int sourceSlotId = chestplateIndex < 9 ? 36 + chestplateIndex : 9 + (chestplateIndex - 9);
            swapSlots(sourceSlotId, chestSlotId);
        }

        if (openedByModule) mc.setScreen(null);
    }

    private void swapSlots(int slotA, int slotB) {
        if (mc.player == null || mc.interactionManager == null) return;
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotB, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, slotA, 0, SlotActionType.PICKUP, mc.player);
    }

    private int findInHotbar(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findInInventory(Item item) {
        if (mc.player == null) return -1;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findBestChestplateIndex() {
        if (mc.player == null) return -1;
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (item == Items.ELYTRA) continue;
            if (!isChestplate(item)) continue;
            int score = getChestplateScore(item);
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

    private int resolveBagSlot(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : 9 + (invIndex - 9);
    }
}