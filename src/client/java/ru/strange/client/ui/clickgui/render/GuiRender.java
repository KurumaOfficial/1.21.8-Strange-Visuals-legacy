package ru.strange.client.ui.clickgui.render;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.ui.clickgui.GuiScreen;

public class GuiRender extends GuiScreen {
    public static void renderGui(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        x = mc.getWindow().getScaledWidth() / 2f - width / 2f;
        y = mc.getWindow().getScaledHeight() / 2f - height / 2f;

        GuiRenderBackGround.renderBackGround(context);
        GuiRenderCategory.renderCategory(context);
        GuiRenderModule.renderModule(context, mouseX, mouseY);
        GuiRenderTheme.renderTheme(context);
    }
}
