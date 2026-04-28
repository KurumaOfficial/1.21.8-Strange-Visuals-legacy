package ru.strange.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.strange.client.module.impl.interfaces.WaterMark;
import ru.strange.client.utils.render.RenderUtil;

import java.lang.reflect.Field;
import java.util.Map;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {

    private Field bossBarsField;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void strange$drawVanillaBossBarBackground(DrawContext context, CallbackInfo ci) {
        WaterMark watermark = WaterMark.INSTANCE;
        if (watermark == null || !watermark.enable || context == null) {
            return;
        }

        Map<?, ?> bars = resolveBossBars();
        if (bars == null || bars.isEmpty()) {
            return;
        }

        int barX = context.getScaledWindowWidth() / 2 - 91;
        int barY = 12;
        int maxY = context.getScaledWindowHeight() / 3;

        for (Object ignored : bars.values()) {
            if (barY > maxY) {
                break;
            }

            RenderUtil.drawClientRect(context, barX - 2.0f, barY + 6.0f, 186.0f, 8.0f);
            barY += 19;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> resolveBossBars() {
        Object instance = this;
        try {
            if (bossBarsField == null || bossBarsField.getDeclaringClass() != instance.getClass()) {
                for (Field field : instance.getClass().getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        bossBarsField = field;
                        break;
                    }
                }
            }

            if (bossBarsField == null) {
                return null;
            }

            Object value = bossBarsField.get(instance);
            return value instanceof Map<?, ?> map ? map : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
