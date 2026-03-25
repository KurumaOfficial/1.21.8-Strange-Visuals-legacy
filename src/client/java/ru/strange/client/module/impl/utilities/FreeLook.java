package ru.strange.client.module.impl.utilities;

import net.minecraft.client.option.Perspective;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.utils.other.BindUtil;
import ru.strange.client.utils.other.FreeLookHandler;

@IModule(
        name = "Фри Лук",
        category = Category.Utilities,
        description = "",
        bind = -1
)
public class FreeLook extends Module {

    private final BindSettings key = new BindSettings("Кнопка", GLFW.GLFW_KEY_LEFT_ALT);
    private final BooleanSetting autoThirdPerson = new BooleanSetting("Авто F5", true);

    private Perspective prevPerspective = Perspective.FIRST_PERSON;
    private boolean holding = false;

    public FreeLook() {
        addSettings(key, autoThirdPerson);
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!enable || mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null || key.get() == -1) return;

        boolean pressed = BindUtil.isDown(key.get());

        if (pressed) {
            if (!holding) {
                holding = true;
                onPress();
            }

            FreeLookHandler.tick();
        } else {
            if (holding) {
                holding = false;
                onRelease();
            }
        }
    }

    private void onPress() {
        if (mc.player == null) return;

        if (autoThirdPerson.get()) {
            prevPerspective = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }

        FreeLookHandler.setActive(true);
    }

    private void onRelease() {
        if (mc.player == null) return;

        if (autoThirdPerson.get()) {
            mc.options.setPerspective(prevPerspective);
        }

        FreeLookHandler.setActive(false);
    }

    @Override
    public void onDisable() {
        holding = false;

        if (mc.player != null && autoThirdPerson.get()) {
            mc.options.setPerspective(prevPerspective);
        }

        FreeLookHandler.setActive(false);
        super.onDisable();
    }
}