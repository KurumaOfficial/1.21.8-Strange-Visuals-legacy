package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class AltAddAccountScreen extends Screen {

    private final Screen parent;
    private final Consumer<String> onAdd;
    private TextFieldWidget nameField;

    public AltAddAccountScreen(Screen parent, Consumer<String> onAdd) {
        super(Text.literal("Add Account"));
        this.parent = parent;
        this.onAdd = onAdd;
    }

    @Override
    protected void init() {
        super.init();

        int fieldW = 220;
        int fieldH = 20;
        int cx = width / 2 - fieldW / 2;
        int cy = height / 2 - fieldH / 2;

        nameField = new TextFieldWidget(textRenderer, cx, cy, fieldW, fieldH, Text.empty());
        nameField.setMaxLength(16);
        nameField.setPlaceholder(Text.literal("Username"));
        addDrawableChild(nameField);
        setInitialFocus(nameField);

        int btnW = 100;
        int btnH = 20;
        int gap = 6;
        int totalW = btnW * 2 + gap;
        int bx = width / 2 - totalW / 2;
        int by = cy + fieldH + 12;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> submit())
                .dimensions(bx, by, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> close())
                .dimensions(bx + btnW + gap, by, btnW, btnH).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Add Account"), width / 2, height / 2 - 40, 0xFFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void submit() {
        if (nameField == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            close();
            return;
        }
        onAdd.accept(name);
        close();
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
