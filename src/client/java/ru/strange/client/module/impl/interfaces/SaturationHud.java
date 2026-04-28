package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventScreen;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;

import java.awt.*;

@IModule(
        name = "Сытость HUD",
        description = "Отображение уровня сытости",
        category = Category.Interface,
        bind = -1
)
public class SaturationHud extends Module {

    private static final Identifier FOOD_EMPTY_TEXTURE = Identifier.ofVanilla("hud/food_empty");
    private static final Identifier FOOD_HALF_TEXTURE = Identifier.ofVanilla("hud/food_half");
    private static final Identifier FOOD_FULL_TEXTURE = Identifier.ofVanilla("hud/food_full");

    private final BooleanSetting showNumbers = new BooleanSetting("Показать число", true);
    private final HueSetting color = new HueSetting("Цвет", new Color(255, 170, 0));

    public SaturationHud() {
        addSettings(showNumbers, color);
    }

    @EventInit
    public void onScreen(EventScreen event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden) return;
        if (mc.player.isSpectator() || mc.player.getAbilities().creativeMode) return;

        DrawContext context = event.drawContext();
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        float saturation = mc.player.getHungerManager().getSaturationLevel();
        int saturationInt = (int) Math.ceil(saturation);

        int rightX = sw / 2 + 91;
        int foodY = sh - 39;
        int satY = foodY - 10;

        Color tint = color.getColor();
        int tintArgb = (tint.getAlpha() << 24) | (tint.getRed() << 16) | (tint.getGreen() << 8) | tint.getBlue();

        for (int i = 0; i < 10; i++) {
            int x = rightX - i * 8 - 9;
            int iconIndex = i * 2 + 1;

            context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_EMPTY_TEXTURE, x, satY, 9, 9);

            if (iconIndex < saturationInt) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_FULL_TEXTURE, x, satY, 9, 9);
            } else if (iconIndex == saturationInt) {
                context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_HALF_TEXTURE, x, satY, 9, 9);
            }
        }

        if (showNumbers.get()) {
            String text = String.format("%.1f", saturation);
            int textX = rightX - mc.textRenderer.getWidth(text);
            context.drawTextWithShadow(mc.textRenderer, text, textX, satY - 10, tintArgb);
        }
    }
}
