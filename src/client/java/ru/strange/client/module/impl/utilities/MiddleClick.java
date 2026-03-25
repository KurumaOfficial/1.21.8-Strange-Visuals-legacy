package ru.strange.client.module.impl.utilities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventMouseInput;
import ru.strange.client.localization.ModLocalization;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.utils.math.TimerUtil;
import ru.strange.client.utils.other.BindUtil;
import ru.strange.client.utils.other.SoundUtil;

@IModule(
        name = "Добавить друга",
        category = Category.Utilities,
        description = "Добавляем своего друга по кнопке",
        bind = -1
)
public class MiddleClick extends Module {

    public static BindSettings friendkey = new BindSettings("Кнопка", BindSettings.mouseCode(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));

    private final TimerUtil swapWatchK = new TimerUtil();

    public MiddleClick() {
        addSettings(friendkey);
    }

    @EventInit
    public void onMouseClick(EventMouseInput e) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;
        if (e.action() != GLFW.GLFW_PRESS) return;

        if (!BindUtil.matchesMouse(friendkey.get(), e.button())) return;
        if (!swapWatchK.hasTimeElapsed(200)) return;

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) return;

        Entity entity = ((EntityHitResult) hitResult).getEntity();
        if (!(entity instanceof PlayerEntity player)) return;

        String name = player.getGameProfile().getName();
        if (name == null || name.isBlank() || Strange.get == null || Strange.get.friendManager == null) return;

        if (!Strange.get.friendManager.isFriend(name)) {
            Text msg = Text.literal(ModLocalization.tr("friend.added.prefix"))
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(name).formatted(Formatting.GREEN))
                    .append(Text.literal(ModLocalization.tr("friend.added.suffix")).formatted(Formatting.GRAY));

            mc.player.sendMessage(msg, false);

            Strange.get.friendManager.add(name);
            SoundUtil.playSound_wav("add", 0.5f);
        } else {
            Text msg = Text.literal(ModLocalization.tr("friend.added.prefix"))
                    .formatted(Formatting.WHITE)
                    .append(Text.literal(name).formatted(Formatting.RED))
                    .append(Text.literal(ModLocalization.tr("friend.removed.suffix")).formatted(Formatting.GRAY));

            mc.player.sendMessage(msg, false);

            Strange.get.friendManager.remove(name);
            SoundUtil.playSound_wav("remove", 0.5f);
        }

        swapWatchK.reset();
    }
}
