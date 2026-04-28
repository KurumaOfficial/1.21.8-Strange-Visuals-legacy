package ru.strange.client.module.impl.utilities;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.mixin.MinecraftClientAccessor;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Быстрый опыт",
        description = "Ручное ускорение броска опыта",
        category = Category.Utilities,
        bind = -1
)
public class FastExp extends Module {

    private final SliderSetting throwCooldown = new SliderSetting("Кулдаун броска", 0, 0, 4, 1, false);

    public FastExp() {
        addSettings(throwCooldown);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || !mc.options.useKey.isPressed()) return;

        Item mainItem = mc.player.getMainHandStack().getItem();
        Item offItem = mc.player.getOffHandStack().getItem();
        if (mainItem != Items.EXPERIENCE_BOTTLE && offItem != Items.EXPERIENCE_BOTTLE) return;

        MinecraftClientAccessor accessor = (MinecraftClientAccessor) mc;
        int cooldown = (int) throwCooldown.get();
        if (accessor.getItemUseCooldown() > cooldown) {
            accessor.setItemUseCooldown(cooldown);
        }
    }
}
