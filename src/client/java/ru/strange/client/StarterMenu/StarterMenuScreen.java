package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StarterMenuScreen extends Screen {
    private static final String[] MORNING_GREETINGS = {
            "menu.greeting.morning",
            "menu.greeting.morning.soft",
            "menu.greeting.morning.light"
    };
    private static final String[] AFTERNOON_GREETINGS = {
            "menu.greeting.afternoon",
            "menu.greeting.afternoon.soft",
            "menu.greeting.afternoon.bright"
    };
    private static final String[] EVENING_GREETINGS = {
            "menu.greeting.evening",
            "menu.greeting.evening.soft",
            "menu.greeting.evening.warm"
    };
    private static final String[] NIGHT_GREETINGS = {
            "menu.greeting.night",
            "menu.greeting.night.soft",
            "menu.greeting.night.late"
    };

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SMALL_BUTTON_WIDTH = 98;
    private static final int GAP = 4;
    private static final int SCREEN_PADDING = 6;
    private static final int MIN_MAIN_BUTTON_WIDTH = 108;
    private static final Identifier LOGO_TEX = Strange.id("icons/gui/logo.png");

    private final Screen parent;
    private final List<ButtonWidget> menuButtons = new ArrayList<>();

    private float alpha;

    public StarterMenuScreen(Screen parent) {
        super(Text.literal(Strange.name));
        this.parent = parent;
    }

    public StarterMenuScreen() {
        this(null);
    }

    @Override
    protected void init() {
        MenuLocalization.initialize();
        AltStartupSessionSync.applyIfPossible(client);
        MenuBackgroundManager.refresh();

        alpha = 0.0f;
        clearChildren();
        menuButtons.clear();

        int centerX = width / 2;
        int mainButtonWidth = Math.min(BUTTON_WIDTH, Math.max(MIN_MAIN_BUTTON_WIDTH, width - SCREEN_PADDING * 2));
        int smallButtonWidth = Math.min(SMALL_BUTTON_WIDTH, Math.max(48, (mainButtonWidth - GAP) / 2));
        int buttonX = Math.max(SCREEN_PADDING, centerX - mainButtonWidth / 2);
        int buttonStartY = Math.max(24, height / 2 - 2);

        addMenuButton(buttonX, buttonStartY, mainButtonWidth, BUTTON_HEIGHT, "menu.singleplayer",
                button -> client.setScreen(new SelectWorldScreen(this)));
        addMenuButton(buttonX, buttonStartY + (BUTTON_HEIGHT + GAP), mainButtonWidth, BUTTON_HEIGHT, "menu.multiplayer",
                button -> client.setScreen(new MultiplayerScreen(this)));
        addMenuButton(buttonX, buttonStartY + (BUTTON_HEIGHT + GAP) * 2, mainButtonWidth, BUTTON_HEIGHT, "menu.alt_manager",
                button -> client.setScreen(new AltManagerScreen(this)));

        int smallButtonY = buttonStartY + (BUTTON_HEIGHT + GAP) * 3 + 4;
        int smallButtonX = buttonX;
        addMenuButton(smallButtonX, smallButtonY, smallButtonWidth, BUTTON_HEIGHT, "menu.options",
                button -> client.setScreen(new OptionsScreen(this, client.options)));
        addMenuButton(smallButtonX + mainButtonWidth - smallButtonWidth, smallButtonY, smallButtonWidth, BUTTON_HEIGHT, "menu.exit",
                button -> {
                    if (client != null) {
                        client.scheduleStop();
                    }
                });

        setFocused(null);
    }

    private void addMenuButton(int x, int y, int width, int height, String key, ButtonWidget.PressAction action) {
        ButtonWidget button = ButtonWidget.builder(Text.literal(MenuLocalization.tr(key)), action)
                .dimensions(x, y, width, height)
                .build();
        button.setAlpha(alpha);
        menuButtons.add(addDrawableChild(button));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        alpha = Math.min(1.0f, alpha + 0.05f);
        for (ButtonWidget button : menuButtons) {
            button.setAlpha(alpha);
        }

        super.render(context, mouseX, mouseY, delta);
        drawHeader(context);
    }

    private void drawHeader(DrawContext context) {
        HeaderLayout layout = resolveHeaderLayout();

        RenderUtil.Image.draw(context, LOGO_TEX, layout.logoX(), layout.logoY(), layout.logoSize(), layout.logoSize(), withAlpha(0xFFFFFF, alpha));

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                "Strange Visuals",
                layout.centerX(),
                layout.titleY(),
                12,
                withAlpha(0xEBEEF5, alpha),
                false
        );

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                getGreeting() + ", " + getSessionName(),
                layout.centerX(),
                layout.greetingY(),
                6,
                withAlpha(0xA0A8B4, alpha),
                false
        );

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(Strange.version),
                layout.versionX(),
                layout.versionY(),
                withAlpha(0xB8C0CC, alpha)
        );
    }

    private HeaderLayout resolveHeaderLayout() {
        int centerX = width / 2;
        int buttonStartY = Math.max(24, height / 2 - 2);
        float maxLogoSize = Math.min(42.0f, Math.min(width * 0.22f, height * 0.18f));
        float logoSize = Math.max(24.0f, maxLogoSize);
        float centerY = height / 2.0f;
        float logoY = MathHelper.clamp(centerY - 98.0f, 18.0f, buttonStartY - 58.0f);
        float titleY = MathHelper.clamp(centerY - 48.0f, logoY + logoSize + 8.0f, buttonStartY - 24.0f);
        float greetingY = MathHelper.clamp(centerY - 21.0f, titleY + 12.0f, buttonStartY - 8.0f);
        int versionX = 4;
        int versionY = Math.max(4, height - 12);
        return new HeaderLayout(centerX, centerX - logoSize / 2.0f, logoY, logoSize, titleY, greetingY, versionX, versionY);
    }

    private String getSessionName() {
        if (client == null || client.getSession() == null) {
            return "Player";
        }

        String username = client.getSession().getUsername();
        return username == null || username.isBlank() ? "Player" : username;
    }

    private String getGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour >= 5 && hour < 12) {
            return pickGreeting(MORNING_GREETINGS);
        }
        if (hour >= 12 && hour < 17) {
            return pickGreeting(AFTERNOON_GREETINGS);
        }
        if (hour >= 17 && hour < 22) {
            return pickGreeting(EVENING_GREETINGS);
        }
        return pickGreeting(NIGHT_GREETINGS);
    }

    private String pickGreeting(String[] keys) {
        if (keys.length == 0) {
            return MenuLocalization.tr("menu.greeting.afternoon");
        }

        String sessionName = getSessionName().toLowerCase(Locale.ROOT);
        long daySeed = LocalDate.now().toEpochDay();
        int seed = (int) (daySeed * 31L + sessionName.hashCode());
        return MenuLocalization.tr(keys[Math.floorMod(seed, keys.length)]);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (parent != null && !(parent instanceof TitleScreen)) {
                close();
            }
            return true;
        }

        if (handleMenuHotkey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleMenuHotkey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_S -> pressMenuButton(0);
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_M -> pressMenuButton(1);
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3, GLFW.GLFW_KEY_A -> pressMenuButton(2);
            case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_O -> pressMenuButton(3);
            case GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_Q -> pressMenuButton(4);
            default -> false;
        };
    }

    private boolean pressMenuButton(int index) {
        if (index < 0 || index >= menuButtons.size()) {
            return false;
        }

        ButtonWidget button = menuButtons.get(index);
        if (!button.active || !button.visible) {
            return false;
        }

        setFocused(button);
        button.onPress();
        return true;
    }

    @Override
    public void close() {
        closeToParent();
    }

    private void closeToParent() {
        if (client == null) {
            return;
        }

        Screen target = parent != null ? parent : new TitleScreen();
        if (target instanceof TitleScreen) {
            StrangeVisualsClient.suppressTitleScreenReplacement(target);
        }
        client.setScreen(target);
    }

    private static int withAlpha(int rgb, float alphaValue) {
        int alphaChannel = Math.max(0, Math.min(255, Math.round(alphaValue * 255.0f)));
        return (alphaChannel << 24) | (rgb & 0xFFFFFF);
    }

    private record HeaderLayout(int centerX, float logoX, float logoY, float logoSize, float titleY, float greetingY,
                                int versionX, int versionY) {
    }
}
