package ru.strange.client.mixin;

import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.utils.other.SkinUtil;
import ru.strange.client.utils.render.PauseMenuPreviewLayout;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenButtonsMixin extends Screen {

    protected GameMenuScreenButtonsMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void strange$addButtons(CallbackInfo ci) {
        PauseMenuPreviewLayout.Bounds bounds = PauseMenuPreviewLayout.getPreviewBounds(this);

        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 8;

        int centerX = (bounds.x1() + bounds.x2()) / 2;
        int y = Math.max(bounds.y1() + 8, bounds.y2() - 28);

        int leftX  = centerX - buttonWidth - (spacing / 2);
        int rightX = centerX + (spacing / 2);

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(ModLocalization.tr("pause.apply_skin")),
                        btn -> strange$onApplySkinClicked()
                ).dimensions(leftX, y, buttonWidth, buttonHeight).build()
        );

        this.addDrawableChild(
                ButtonWidget.builder(
                        Text.literal(ModLocalization.tr("pause.reset_skin")),
                        btn -> strange$onResetSkinClicked()
                ).dimensions(rightX, y, buttonWidth, buttonHeight).build()
        );
    }

    @Unique
    private void strange$onApplySkinClicked() {
        // ✔ открывает диалог и применяет скин
        SkinUtil.uiPickAndApplySkin();
    }

    @Unique
    private void strange$onResetSkinClicked() {
        // ✔ удаляет файл и возвращает ванильный скин
        SkinUtil.uiResetSkin();
    }
}
