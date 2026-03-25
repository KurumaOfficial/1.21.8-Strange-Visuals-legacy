package ru.strange.client.StarterMenu;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StarterMenuScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SMALL_BUTTON_WIDTH = 98;
    private static final int GAP = 4;
    private static final Identifier LOGO_TEX = Strange.id("icons/gui/logo.png");

    private final List<ButtonWidget> menuButtons = new ArrayList<>();

    private float alpha;

    public StarterMenuScreen(Screen ignoredScreen) {
        super(Text.literal(Strange.name));
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
        menuButtons.clear();

        int centerX = width / 2;
        int buttonX = centerX - BUTTON_WIDTH / 2;
        int buttonStartY = height / 2 - 2;

        addMenuButton(buttonX, buttonStartY, BUTTON_WIDTH, BUTTON_HEIGHT, "menu.singleplayer",
                button -> client.setScreen(new SelectWorldScreen(this)));
        addMenuButton(buttonX, buttonStartY + (BUTTON_HEIGHT + GAP), BUTTON_WIDTH, BUTTON_HEIGHT, "menu.multiplayer",
                button -> client.setScreen(new MultiplayerScreen(this)));
        addMenuButton(buttonX, buttonStartY + (BUTTON_HEIGHT + GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT, "menu.alt_manager",
                button -> client.setScreen(new AltManagerScreen(this)));

        int smallButtonY = buttonStartY + (BUTTON_HEIGHT + GAP) * 3 + 4;
        int smallButtonX = centerX - BUTTON_WIDTH / 2;
        addMenuButton(smallButtonX, smallButtonY, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT, "menu.options",
                button -> client.setScreen(new OptionsScreen(this, client.options)));
        addMenuButton(smallButtonX + BUTTON_WIDTH - SMALL_BUTTON_WIDTH, smallButtonY, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT, "menu.exit",
                button -> MinecraftClient.getInstance().scheduleStop());
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
        int centerX = width / 2;
        float logoSize = 42.0f;
        float logoX = centerX - logoSize / 2.0f;
        float logoY = height / 2.0f - 98.0f;
        float titleY = height / 2.0f - 48.0f;
        float greetingY = height / 2.0f - 21.0f;

        RenderUtil.Image.draw(context, LOGO_TEX, logoX, logoY, logoSize, logoSize, withAlpha(0xFFFFFF, alpha));

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                "Strange Visuals",
                centerX,
                titleY,
                12,
                withAlpha(0xEBEEF5, alpha),
                false
        );

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                getGreeting() + ", " + getSessionName(),
                centerX,
                greetingY,
                6,
                withAlpha(0xA0A8B4, alpha),
                false
        );

        context.drawTextWithShadow(
                textRenderer,
                Text.literal(Strange.version),
                4,
                height - 12,
                withAlpha(0xB8C0CC, alpha)
        );
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
            return MenuLocalization.tr("menu.greeting.morning");
        }
        if (hour >= 12 && hour < 17) {
            return MenuLocalization.tr("menu.greeting.afternoon");
        }
        if (hour >= 17 && hour < 22) {
            return MenuLocalization.tr("menu.greeting.evening");
        }
        return MenuLocalization.tr("menu.greeting.night");
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int withAlpha(int rgb, float alphaValue) {
        int alphaChannel = Math.max(0, Math.min(255, Math.round(alphaValue * 255.0f)));
        return (alphaChannel << 24) | (rgb & 0xFFFFFF);
    }
}
