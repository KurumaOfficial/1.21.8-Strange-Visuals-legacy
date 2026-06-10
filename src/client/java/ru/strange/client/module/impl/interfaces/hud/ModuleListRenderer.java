package ru.strange.client.module.impl.interfaces.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import ru.strange.client.Strange;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.renderengine.renderers.util.FrameTracker;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ModuleListRenderer {

    private static final float ITEM_HEIGHT = 12.8f;
    private static final float ITEM_STEP = 13.6f;
    private static final float ICON_SIZE = 7.5f;
    private static final float TEXT_SIZE = 5f;

    private static final String[] PREVIEW_NAMES = {
            "Боксы", "Джампики", "Китайская шляпа", "Кубики", "Таргет рендер"
    };

    private final WaterMark owner;

    private final Identifier playerIcon;
    private final Identifier otherIcon;
    private final Identifier worldIcon;
    private final Identifier utilitiesIcon;
    private final Identifier interfaceIcon;
    private final List<Row> activeRows = new ArrayList<>();
    private final List<Row> previewRows = new ArrayList<>();

    private float activeWidth = 60f;
    private float previewWidth = 60f;
    private float activeHeight = 0f;
    private float previewHeight = 0f;
    private int preparedFrame = Integer.MIN_VALUE;

    public ModuleListRenderer(WaterMark owner,
                              Identifier playerIcon,
                              Identifier otherIcon,
                              Identifier worldIcon,
                              Identifier utilitiesIcon,
                              Identifier interfaceIcon) {
        this.owner = owner;
        this.playerIcon = playerIcon;
        this.otherIcon = otherIcon;
        this.worldIcon = worldIcon;
        this.utilitiesIcon = utilitiesIcon;
        this.interfaceIcon = interfaceIcon;
    }

    public void prepareFrame() {
        preparedFrame = FrameTracker.getFrame();
        rebuildActiveRows();
        if (owner.isEditing()) {
            rebuildPreviewRows();
        } else {
            previewRows.clear();
            previewWidth = 60f;
            previewHeight = 0f;
        }
    }

    public void render(DrawContext ctx, float x, float y) {
        ensureFramePrepared();

        boolean preview = activeRows.isEmpty() && owner.isEditing();
        if (!preview && activeRows.isEmpty()) {
            return;
        }

        boolean rightSide = isRightSide(x);
        float maxWidth = getWidth();
        float currentY = y;
        List<Row> rows = preview ? previewRows : activeRows;

        for (int i = 0; i < rows.size(); ++i) {
            Row row = rows.get(i);
            float rowX = rightSide ? (x + maxWidth - row.width()) : x;
            drawRow(ctx, rowX, currentY, row);
            currentY += ITEM_STEP;
        }
    }

    public float getWidth() {
        ensureFramePrepared();
        return activeRows.isEmpty() && owner.isEditing() ? previewWidth : activeWidth;
    }

    public float getHeight() {
        ensureFramePrepared();
        return activeRows.isEmpty() && owner.isEditing() ? previewHeight : activeHeight;
    }

    private boolean isRightSide(float x) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return false;
        float screenWidth = mc.getWindow().getScaledWidth();
        float centerX = x + getWidth() / 2f;
        return centerX > screenWidth / 2f;
    }

    private void ensureFramePrepared() {
        if (preparedFrame != FrameTracker.getFrame()) {
            prepareFrame();
        }
    }

    private void drawRow(DrawContext ctx, float x, float y, Row row) {
        RenderUtil.drawClientRect(ctx, x, y, row.width(), ITEM_HEIGHT);

        RenderUtil.Image.draw(ctx, row.icon(),
                x + 4.5f, y + 2.7f,
                ICON_SIZE, ICON_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210));

        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, row.name(),
                x + 14.5f, y + 8.65f,
                (int) TEXT_SIZE,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 220));
    }

    private float cardWidth(String name) {
        return FontDraw.getWidth(FontDraw.FontType.MEDIUM, name, (int) TEXT_SIZE) + 24f;
    }

    private void rebuildActiveRows() {
        activeRows.clear();
        activeWidth = 60f;

        for (Module module : Strange.get.manager.getModules()) {
            if (module == null || module == owner || !module.enable) {
                continue;
            }

            String name = module.getDisplayName();
            float width = cardWidth(name);
            activeRows.add(new Row(name, width, getModuleIcon(module)));
            activeWidth = Math.max(activeWidth, width);
        }

        activeRows.sort(Comparator.comparingDouble(Row::width).reversed());
        activeHeight = activeRows.isEmpty() ? 0f : ITEM_HEIGHT + (activeRows.size() - 1) * ITEM_STEP;
    }

    private void rebuildPreviewRows() {
        previewRows.clear();
        previewWidth = 60f;

        for (String previewName : PREVIEW_NAMES) {
            String name = ModLocalization.raw(previewName);
            float width = cardWidth(name);
            previewRows.add(new Row(name, width, getPreviewIconByName(previewName)));
            previewWidth = Math.max(previewWidth, width);
        }

        previewRows.sort(Comparator.comparingDouble(Row::width).reversed());
        previewHeight = previewRows.isEmpty() ? 0f : ITEM_HEIGHT + (previewRows.size() - 1) * ITEM_STEP;
    }

    private Identifier getPreviewIconByName(String name) {
        return switch (name) {
            case "Боксы" -> playerIcon;
            case "Джампики" -> utilitiesIcon;
            case "Китайская шляпа" -> worldIcon;
            case "Кубики" -> otherIcon;
            default -> interfaceIcon;
        };
    }

    private Identifier getModuleIcon(Module module) {
        IModule ann = module.getClass().getAnnotation(IModule.class);
        if (ann != null) {
            String cat = ann.category().name().toLowerCase();
            if (cat.contains("player") || cat.contains("combat") || cat.contains("movement")) return playerIcon;
            if (cat.contains("world") || cat.contains("render")) return worldIcon;
            if (cat.contains("util") || cat.contains("misc")) return utilitiesIcon;
            if (cat.contains("interface") || cat.contains("client")) return interfaceIcon;
        }
        return otherIcon;
    }

    private record Row(String name, float width, Identifier icon) {
    }
}
