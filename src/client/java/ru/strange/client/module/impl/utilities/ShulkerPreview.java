package ru.strange.client.module.impl.utilities;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@IModule(
        name = "Просмотр шалкеров",
        description = "Просмотр содержимого шалкеров в инвентаре и подсветка на земле",
        category = Category.Utilities,
        bind = -1
)
public class ShulkerPreview extends Module {

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int PADDING = 4;

    private static final Identifier SHULKER_TOOLTIP_BACKGROUND = Strange.id("icons/gui/shulker_box_tooltip.png");
    private static final float PANEL_LERP_SPEED = 0.26f;
    private static final float PANEL_SCALE_MIN = 0.96f;
    private static final float PANEL_SCALE_MAX = 1.00f;

    private static ShulkerPreview instance;
    private static final PreviewState PREVIEW_STATE = new PreviewState();

    public ShulkerPreview() {
        instance = this;
    }

    public static void renderPreview(DrawContext context, HandledScreen<?> handledScreen, double mouseX, double mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (instance == null || !instance.enable || mc.player == null) {
            return;
        }

        Slot slot = handledScreen.getSlotAt(mouseX, mouseY);
        PreviewTarget target = resolveTarget(mc, slot, mouseX, mouseY, context.getScaledWindowWidth());
        PREVIEW_STATE.setTarget(target);
        PREVIEW_STATE.update();

        if (PREVIEW_STATE.alpha <= 0.01f) {
            return;
        }

        int tooltipX = Math.round(PREVIEW_STATE.x);
        int tooltipY = Math.round(PREVIEW_STATE.y);
        int totalW = Math.round(PREVIEW_STATE.width);
        int totalH = Math.round(PREVIEW_STATE.height);
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(PREVIEW_STATE.alpha * 255.0f)));

        context.getMatrices().pushMatrix();
        float centerX = tooltipX + totalW / 2f;
        float centerY = tooltipY + totalH / 2f;
        float scale = PANEL_SCALE_MIN + (PANEL_SCALE_MAX - PANEL_SCALE_MIN) * easeOutCubic(PREVIEW_STATE.alpha);
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-centerX, -centerY);

        drawTooltipBackground(context, tooltipX, tooltipY, totalW, totalH, scaledAlpha);

        int titleColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (235.0f * PREVIEW_STATE.alpha));
        int separatorColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (42.0f * PREVIEW_STATE.alpha));
        int accentColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getClientColor(), (int) (210.0f * PREVIEW_STATE.alpha));
        int slotColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int) (120.0f * PREVIEW_STATE.alpha));
        int slotOutline = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int) (34.0f * PREVIEW_STATE.alpha));
        int filledOutline = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getClientColor(), (int) (96.0f * PREVIEW_STATE.alpha));

        RenderUtil.Round.draw(context, tooltipX + 6f, tooltipY + 4f, Math.min(44, totalW - 12), 2f, 1f, accentColor);
        context.drawTextWithShadow(mc.textRenderer, PREVIEW_STATE.title, tooltipX + PADDING, tooltipY + 6, titleColor);
        RenderUtil.Round.draw(context, tooltipX + PADDING, tooltipY + 14f, totalW - PADDING * 2f, 1.2f, 0.6f, separatorColor);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                int x = tooltipX + PADDING + col * SLOT_SIZE;
                int y = tooltipY + PADDING + 18 + row * SLOT_SIZE;

                if (index >= PREVIEW_STATE.items.size()) {
                    continue;
                }

                ItemStack item = PREVIEW_STATE.items.get(index);
                RenderUtil.Round.draw(context, x, y, SLOT_SIZE - 1f, SLOT_SIZE - 1f, 4f, slotColor);
                RenderUtil.Border.draw(context, x, y, SLOT_SIZE - 1f, SLOT_SIZE - 1f, 4f, 0.8f, item.isEmpty() ? slotOutline : filledOutline);

                if (!item.isEmpty()) {
                    context.drawItem(item, x + 1, y + 1);
                    context.drawStackOverlay(mc.textRenderer, item, x + 1, y + 1);
                }
            }
        }

        context.getMatrices().popMatrix();
    }

    public static boolean shouldSimplifyTooltip(ItemStack stack) {
        return instance != null && instance.enable && isShulkerStack(stack);
    }

    public static List<Text> getSimplifiedTooltip(ItemStack stack) {
        return Collections.singletonList(stack.getName());
    }

    public static boolean shouldHideVanillaTooltip(HandledScreen<?> handledScreen, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (instance == null || !instance.enable || mc.player == null) return false;

        Slot slot = handledScreen.getSlotAt(mouseX, mouseY);
        return slot != null && slot.hasStack() && isShulkerStack(slot.getStack());
    }

    public static boolean shouldEnhanceDroppedShulker(ItemStack stack) {
        return instance != null && instance.enable && hasStoredItems(stack);
    }

    public static float getDroppedShulkerScale(ItemStack stack) {
        return shouldEnhanceDroppedShulker(stack) ? 1.34f : 1.0f;
    }

    public static float getDroppedShulkerTintMix(ItemStack stack) {
        return shouldEnhanceDroppedShulker(stack) ? 0.72f : 0.0f;
    }

    private static PreviewTarget resolveTarget(MinecraftClient mc, Slot slot, double mouseX, double mouseY, int screenWidth) {
        if (slot == null || !slot.hasStack()) {
            return null;
        }

        ItemStack stack = slot.getStack();
        if (!isShulkerStack(stack)) {
            return null;
        }

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return null;
        }

        List<ItemStack> items = new ArrayList<>();
        container.stream().forEach(item -> items.add(item.copy()));
        while (items.size() < COLS * ROWS) {
            items.add(ItemStack.EMPTY);
        }

        Text title = stack.getName();
        int titleWidth = mc.textRenderer.getWidth(title);

        int gridW = COLS * SLOT_SIZE;
        int gridH = ROWS * SLOT_SIZE;
        int totalW = Math.max(gridW + PADDING * 2 + 2, titleWidth + PADDING * 2 + 6);
        int totalH = gridH + PADDING * 2 + 14 + 4;

        float tooltipX = (float) mouseX + 12f;
        float tooltipY = (float) mouseY - totalH - 4f;

        if (tooltipX + totalW > screenWidth) {
            tooltipX = screenWidth - totalW - 2f;
        }
        if (tooltipY < 2f) {
            tooltipY = (float) mouseY + 12f;
        }

        return new PreviewTarget(tooltipX, tooltipY, totalW, totalH, title, items);
    }

    private static boolean isShulkerStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof ShulkerBoxBlock;
        }
        return false;
    }

    private static boolean hasStoredItems(ItemStack stack) {
        if (!isShulkerStack(stack)) {
            return false;
        }

        ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
        if (container == null) {
            return false;
        }

        return container.stream().anyMatch(itemStack -> !itemStack.isEmpty());
    }

    private static void drawTooltipBackground(DrawContext context, int x, int y, int width, int height, int alpha) {
        RenderUtil.Image.draw(context, SHULKER_TOOLTIP_BACKGROUND, x, y, width, height, new Color(255, 255, 255, alpha));
    }

    private static float easeOutCubic(float value) {
        float inverted = 1.0f - value;
        return 1.0f - inverted * inverted * inverted;
    }

    private static float lerp(float from, float to, float speed) {
        return from + (to - from) * speed;
    }

    private static final class PreviewTarget {
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final Text title;
        private final List<ItemStack> items;

        private PreviewTarget(float x, float y, float width, float height, Text title, List<ItemStack> items) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.title = title;
            this.items = items;
        }
    }

    private static final class PreviewState {
        private float x = 0f;
        private float y = 0f;
        private float width = 0f;
        private float height = 0f;
        private float alpha = 0f;
        private float targetX = 0f;
        private float targetY = 0f;
        private float targetWidth = 0f;
        private float targetHeight = 0f;
        private float targetAlpha = 0f;
        private Text title = Text.empty();
        private List<ItemStack> items = Collections.emptyList();
        private boolean initialized = false;

        private void setTarget(PreviewTarget target) {
            if (target == null) {
                targetAlpha = 0f;
                return;
            }

            targetX = target.x;
            targetY = target.y;
            targetWidth = target.width;
            targetHeight = target.height;
            targetAlpha = 1f;
            title = target.title;
            items = target.items;

            if (!initialized || alpha <= 0.01f) {
                x = targetX;
                y = targetY;
                width = targetWidth;
                height = targetHeight;
                initialized = true;
            }
        }

        private void update() {
            if (!initialized && targetAlpha <= 0f) {
                return;
            }

            float moveSpeed = targetAlpha > alpha ? PANEL_LERP_SPEED : 0.18f;
            float sizeSpeed = targetAlpha > alpha ? PANEL_LERP_SPEED : 0.18f;
            float alphaSpeed = targetAlpha > alpha ? 0.24f : 0.16f;

            x = lerp(x, targetX, moveSpeed);
            y = lerp(y, targetY, moveSpeed);
            width = lerp(width, targetWidth, sizeSpeed);
            height = lerp(height, targetHeight, sizeSpeed);
            alpha = lerp(alpha, targetAlpha, alphaSpeed);

            if (alpha <= 0.01f && targetAlpha <= 0f) {
                alpha = 0f;
                initialized = false;
            }
        }
    }
}
