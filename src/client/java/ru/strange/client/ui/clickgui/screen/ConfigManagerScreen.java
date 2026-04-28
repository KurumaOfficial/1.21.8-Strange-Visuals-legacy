package ru.strange.client.ui.clickgui.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.manager.cfg.Config;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ConfigManagerScreen extends Screen {

    private static final float PANEL_W = 370.0f;
    private static final float PANEL_H = 258.0f;
    private static final float HEADER_H = 24.0f;
    private static final float LIST_X_PAD = 10.0f;
    private static final float LIST_Y_PAD = 36.0f;
    private static final float LIST_H = 140.0f;
    private static final float ROW_H = 18.0f;

    private final Screen parent;
    private final List<String> configs = new ArrayList<>();

    private TextFieldWidget nameField;
    private String selectedConfig;

    private float panelX;
    private float panelY;
    private float animation;
    private long lastFrameTime;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    private float listScroll;

    private String statusText = "";
    private long statusUntil;

    public ConfigManagerScreen(Screen parent) {
        super(Text.literal("Config Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        panelX = (width - PANEL_W) * 0.5f;
        panelY = (height - PANEL_H) * 0.5f;
        animation = 0.0f;
        lastFrameTime = System.currentTimeMillis();

        nameField = new TextFieldWidget(textRenderer, (int) (panelX + 12), (int) (panelY + PANEL_H - 64), (int) (PANEL_W - 24), 18, Text.literal(""));
        nameField.setMaxLength(96);
        addDrawableChild(nameField);

        refreshConfigs();
        nameField.setText(selectedConfig == null ? "" : selectedConfig);
        updateFieldPosition();
        setInitialFocus(nameField);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String fieldText = nameField == null ? "" : nameField.getText();
        super.resize(client, width, height);
        if (nameField != null) {
            nameField.setText(fieldText);
        }
        panelX = Math.max(2.0f, (this.width - PANEL_W) * 0.5f);
        panelY = Math.max(2.0f, (this.height - PANEL_H) * 0.5f);
        updateFieldPosition();
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (nameField != null && nameField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            performSave();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return nameField != null && nameField.charTyped(chr, modifiers) || super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && inside(mouseX, mouseY, panelX, panelY, PANEL_W, HEADER_H)) {
            dragging = true;
            dragOffsetX = (float) mouseX - panelX;
            dragOffsetY = (float) mouseY - panelY;
            return true;
        }

        if (nameField != null && nameField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            if (clickConfigRow(mouseX, mouseY)) {
                return true;
            }
            if (clickButton(mouseX, mouseY, 0, "Save")) {
                performSave();
                return true;
            }
            if (clickButton(mouseX, mouseY, 1, "Load")) {
                performLoad();
                return true;
            }
            if (clickButton(mouseX, mouseY, 2, "Delete")) {
                performDelete();
                return true;
            }
            if (clickButton(mouseX, mouseY, 3, "Reset")) {
                performReset();
                return true;
            }
            if (clickButton(mouseX, mouseY, 4, "Refresh")) {
                refreshConfigs();
                setStatus("List refreshed", true);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_1) {
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        panelX = clamp((float) mouseX - dragOffsetX, 2.0f, width - PANEL_W - 2.0f);
        panelY = clamp((float) mouseY - dragOffsetY, 2.0f, height - PANEL_H - 2.0f);
        updateFieldPosition();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float listX = panelX + LIST_X_PAD;
        float listY = panelY + LIST_Y_PAD;
        float listW = PANEL_W - LIST_X_PAD * 2.0f;
        if (!inside(mouseX, mouseY, listX, listY, listW, LIST_H)) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        float maxScroll = Math.max(0.0f, configs.size() * ROW_H - LIST_H);
        listScroll = clamp(listScroll - (float) verticalAmount * 16.0f, 0.0f, maxScroll);
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateAnimation();

        context.fill(0, 0, width, height, 0x7A000000);

        float centerX = panelX + PANEL_W * 0.5f;
        float centerY = panelY + PANEL_H * 0.5f;
        float scale = 0.92f + 0.08f * animation;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(centerX, centerY);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-centerX, -centerY);

        renderPanel(context, mouseX, mouseY);
        context.getMatrices().popMatrix();

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderPanel(DrawContext context, int mouseX, int mouseY) {
        RenderUtil.drawClientRect(context, panelX, panelY, PANEL_W, PANEL_H);

        int titleColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 230);
        int subColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 160);

        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, "Config Manager", panelX + 10.0f, panelY + 11.0f, 7, titleColor);
        String activeName = Strange.get != null && Strange.get.configManager != null
                ? Strange.get.configManager.getActiveConfigName()
                : "default";
        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, "Active: " + activeName, panelX + PANEL_W - 112.0f, panelY + 11.0f, 5, subColor);

        renderConfigList(context, mouseX, mouseY);
        renderButtons(context, mouseX, mouseY);

        if (nameField != null) {
            nameField.render(context, mouseX, mouseY, 0.0f);
        }

        FontDraw.drawText(FontDraw.FontType.MEDIUM, context, "Config name", panelX + 12.0f, panelY + PANEL_H - 70.0f, 5, subColor);

        if (statusText != null && !statusText.isEmpty() && System.currentTimeMillis() < statusUntil) {
            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    context,
                    statusText,
                    panelX + 12.0f,
                    panelY + PANEL_H - 14.0f,
                    5,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 210)
            );
        }
    }

    private void renderConfigList(DrawContext context, int mouseX, int mouseY) {
        float listX = panelX + LIST_X_PAD;
        float listY = panelY + LIST_Y_PAD;
        float listW = PANEL_W - LIST_X_PAD * 2.0f;

        RenderUtil.Round.draw(
                context,
                listX,
                listY,
                listW,
                LIST_H,
                5.0f,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 210)
        );

        float maxScroll = Math.max(0.0f, configs.size() * ROW_H - LIST_H);
        listScroll = clamp(listScroll, 0.0f, maxScroll);

        float drawY = listY - listScroll;
        for (String configName : configs) {
            float rowY = drawY;
            drawY += ROW_H;

            if (rowY + ROW_H < listY || rowY > listY + LIST_H) {
                continue;
            }

            boolean selected = configName.equalsIgnoreCase(selectedConfig == null ? "" : selectedConfig);
            boolean hovered = inside(mouseX, mouseY, listX + 3.0f, rowY + 1.0f, listW - 6.0f, ROW_H - 2.0f);

            int rowColor = selected
                    ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 120)
                    : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), hovered ? 160 : 120);
            RenderUtil.Round.draw(context, listX + 3.0f, rowY + 1.0f, listW - 6.0f, ROW_H - 2.0f, 4.0f, rowColor);

            String display = configName;
            if (Strange.get != null && Strange.get.configManager != null
                    && configName.equalsIgnoreCase(Strange.get.configManager.getActiveConfigName())) {
                display = display + "  [active]";
            }

            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    context,
                    display,
                    listX + 8.0f,
                    rowY + 11.0f,
                    5,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 218)
            );
        }

        if (configs.isEmpty()) {
            FontDraw.drawCenter(
                    FontDraw.FontType.MEDIUM,
                    context,
                    "No configs found",
                    listX + listW * 0.5f,
                    listY + LIST_H * 0.5f,
                    5,
                    RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 150),
                    false
            );
        }
    }

    private void renderButtons(DrawContext context, int mouseX, int mouseY) {
        renderButton(context, mouseX, mouseY, 0, "Save");
        renderButton(context, mouseX, mouseY, 1, "Load");
        renderButton(context, mouseX, mouseY, 2, "Delete");
        renderButton(context, mouseX, mouseY, 3, "Reset");
        renderButton(context, mouseX, mouseY, 4, "Refresh");
    }

    private void renderButton(DrawContext context, int mouseX, int mouseY, int index, String label) {
        float buttonW = (PANEL_W - 20.0f - 8.0f * 4.0f) / 5.0f;
        float x = panelX + 10.0f + index * (buttonW + 8.0f);
        float y = panelY + PANEL_H - 34.0f;
        boolean hovered = inside(mouseX, mouseY, x, y, buttonW, 18.0f);

        int color = hovered
                ? RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 165)
                : RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 170);

        RenderUtil.Round.draw(context, x, y, buttonW, 18.0f, 4.0f, color);
        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                label,
                x + buttonW * 0.5f,
                y + 11.0f,
                5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 225),
                false
        );
    }

    private boolean clickConfigRow(double mouseX, double mouseY) {
        float listX = panelX + LIST_X_PAD;
        float listY = panelY + LIST_Y_PAD;
        float listW = PANEL_W - LIST_X_PAD * 2.0f;
        if (!inside(mouseX, mouseY, listX, listY, listW, LIST_H)) {
            return false;
        }

        float drawY = listY - listScroll;
        for (String configName : configs) {
            float rowY = drawY;
            drawY += ROW_H;
            if (rowY + ROW_H < listY || rowY > listY + LIST_H) {
                continue;
            }
            if (inside(mouseX, mouseY, listX + 3.0f, rowY + 1.0f, listW - 6.0f, ROW_H - 2.0f)) {
                selectedConfig = configName;
                if (nameField != null) {
                    nameField.setText(configName);
                }
                return true;
            }
        }

        return false;
    }

    private boolean clickButton(double mouseX, double mouseY, int index, String label) {
        float buttonW = (PANEL_W - 20.0f - 8.0f * 4.0f) / 5.0f;
        float x = panelX + 10.0f + index * (buttonW + 8.0f);
        float y = panelY + PANEL_H - 34.0f;
        return inside(mouseX, mouseY, x, y, buttonW, 18.0f);
    }

    private void performSave() {
        if (Strange.get == null || Strange.get.configManager == null) {
            setStatus("Config manager unavailable", false);
            return;
        }

        String name = resolveInputName();
        if (name == null) {
            return;
        }

        if (Strange.get.configManager.saveSnapshot(name)) {
            selectedConfig = name;
            refreshConfigs();
            setStatus("Saved: " + name, true);
        } else {
            setStatus("Failed to save: " + name, false);
        }
    }

    private void performLoad() {
        if (Strange.get == null || Strange.get.configManager == null) {
            setStatus("Config manager unavailable", false);
            return;
        }

        String name = resolveTargetName();
        if (name == null) {
            setStatus("Select config to load", false);
            return;
        }

        if (Strange.get.configManager.loadSnapshot(name)) {
            selectedConfig = name;
            setStatus("Loaded: " + name, true);
        } else {
            setStatus("Failed to load: " + name, false);
        }
    }

    private void performDelete() {
        if (Strange.get == null || Strange.get.configManager == null) {
            setStatus("Config manager unavailable", false);
            return;
        }

        String name = resolveTargetName();
        if (name == null) {
            setStatus("Select config to delete", false);
            return;
        }

        if (Strange.get.configManager.deleteConfig(name)) {
            refreshConfigs();
            if (name.equalsIgnoreCase(selectedConfig == null ? "" : selectedConfig)) {
                selectedConfig = configs.isEmpty() ? null : configs.getFirst();
            }
            if (nameField != null) {
                nameField.setText(selectedConfig == null ? "" : selectedConfig);
            }
            setStatus("Deleted: " + name, true);
        } else {
            setStatus("Failed to delete: " + name, false);
        }
    }

    private void performReset() {
        if (Strange.get == null || Strange.get.configManager == null) {
            setStatus("Config manager unavailable", false);
            return;
        }

        if (Strange.get.configManager.resetToDefaults()) {
            setStatus("Reset to defaults", true);
            refreshConfigs();
        } else {
            setStatus("Failed to reset", false);
        }
    }

    private void refreshConfigs() {
        configs.clear();
        if (Strange.get != null && Strange.get.configManager != null) {
            for (Config config : Strange.get.configManager.getLoadedConfigs()) {
                configs.add(config.getName());
            }
        }

        configs.sort(String::compareToIgnoreCase);

        if (selectedConfig == null && !configs.isEmpty()) {
            selectedConfig = configs.getFirst();
        }
        if (selectedConfig != null && configs.stream().noneMatch(name -> name.equalsIgnoreCase(selectedConfig))) {
            selectedConfig = configs.isEmpty() ? null : configs.getFirst();
        }

        float maxScroll = Math.max(0.0f, configs.size() * ROW_H - LIST_H);
        listScroll = clamp(listScroll, 0.0f, maxScroll);
    }

    private String resolveInputName() {
        if (nameField == null || Strange.get == null || Strange.get.configManager == null) {
            return null;
        }

        String raw = nameField.getText() == null ? "" : nameField.getText().trim();
        if (raw.isEmpty()) {
            setStatus("Enter config name", false);
            return null;
        }

        String normalized = Strange.get.configManager.normalizeConfigName(raw);
        if (normalized == null) {
            setStatus("Invalid config name", false);
            return null;
        }

        nameField.setText(normalized);
        return normalized;
    }

    private String resolveTargetName() {
        if (nameField != null) {
            String text = nameField.getText();
            if (text != null && !text.trim().isEmpty()) {
                String normalized = Strange.get != null && Strange.get.configManager != null
                        ? Strange.get.configManager.normalizeConfigName(text.trim())
                        : text.trim();
                if (normalized != null) {
                    return normalized;
                }
            }
        }

        if (selectedConfig == null || selectedConfig.isBlank()) {
            return null;
        }

        if (Strange.get == null || Strange.get.configManager == null) {
            return selectedConfig;
        }

        return Strange.get.configManager.normalizeConfigName(selectedConfig);
    }

    private void setStatus(String text, boolean success) {
        statusText = success ? text : ("Error: " + text);
        statusUntil = System.currentTimeMillis() + 2200L;
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, Math.min(0.08f, (now - lastFrameTime) / 1000.0f));
        lastFrameTime = now;
        animation += (1.0f - animation) * Math.min(1.0f, 12.0f * dt);
        animation = clamp(animation, 0.0f, 1.0f);
    }

    private void updateFieldPosition() {
        if (nameField == null) {
            return;
        }

        nameField.setX((int) (panelX + 12));
        nameField.setY((int) (panelY + PANEL_H - 58));
        nameField.setWidth((int) (PANEL_W - 24));
    }

    private boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && my >= y && mx <= x + w && my <= y + h;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
