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
import ru.strange.client.manager.promo.PromoCodeManager;
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
        int smallOffset = 4;

        int smallButtonY = buttonStartY + (BUTTON_HEIGHT + GAP) * smallOffset + 4;
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

    private float devLogBoxX, devLogBoxY, devLogBoxW, devLogBoxH;
    private float gamepadBoxX, gamepadBoxY, gamepadBoxW, gamepadBoxH;
    private boolean gamepadExpanded;

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        alpha = Math.min(1.0f, alpha + 0.05f);
        for (ButtonWidget button : menuButtons) {
            button.setAlpha(alpha);
        }

        super.render(context, mouseX, mouseY, delta);
        drawHeader(context);
        drawDevLogWidget(context, mouseX, mouseY);
        drawGamepadWidget(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= devLogBoxX && mouseX <= devLogBoxX + devLogBoxW
                    && mouseY >= devLogBoxY && mouseY <= devLogBoxY + devLogBoxH) {
                client.setScreen(new DevlogsScreen(this));
                String url = DevlogsScreen.DevlogRegistry.entries().getFirst().url();
                if (url != null && !url.isBlank()) {
                    Strange.openUrl(url);
                }
                return true;
            }
            if (mouseX >= gamepadBoxX && mouseX <= gamepadBoxX + gamepadBoxW
                    && mouseY >= gamepadBoxY && mouseY <= gamepadBoxY + gamepadBoxH) {
                if (PromoCodeManager.isGamesUnlocked()) {
                    client.setScreen(new GamesScreen(this));
                } else {
                    gamepadExpanded = !gamepadExpanded;
                }
                return true;
            }
            if (gamepadExpanded) {
                float gamesBoxX = gamepadBoxX;
                float gamesBoxY = gamepadBoxY - 56f;
                float gamesBoxW = 100f;
                float gamesBoxH = 50f;
                if (mouseX >= gamesBoxX && mouseX <= gamesBoxX + gamesBoxW
                        && mouseY >= gamesBoxY && mouseY <= gamesBoxY + gamesBoxH) {
                    client.setScreen(new GamesScreen(this));
                    gamepadExpanded = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void drawDevLogWidget(DrawContext ctx, int mouseX, int mouseY) {
        if (alpha < 0.5f) return;
        List<DevlogsScreen.DevlogEntry> entries = DevlogsScreen.DevlogRegistry.entries();
        if (entries.isEmpty()) return;
        DevlogsScreen.DevlogEntry latest = entries.get(0);
        devLogBoxW = 160f;
        devLogBoxH = 50f;
        devLogBoxX = width - devLogBoxW - 8f;
        devLogBoxY = height - devLogBoxH - 8f;
        boolean hovered = mouseX >= devLogBoxX && mouseX <= devLogBoxX + devLogBoxW
                && mouseY >= devLogBoxY && mouseY <= devLogBoxY + devLogBoxH;
        float hoverAlpha = hovered ? 1.0f : 0.8f;
        int devBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(alpha * 140 * hoverAlpha));
        int devBorder = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(alpha * (hovered ? 120 : 60)));
        RenderUtil.Round.draw(ctx, devLogBoxX, devLogBoxY, devLogBoxW, devLogBoxH, 4f, devBg);
        RenderUtil.Border.draw(ctx, devLogBoxX, devLogBoxY, devLogBoxW, devLogBoxH, 4f, 0.5f, devBorder);
        FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "DevLog " + latest.version(),
            devLogBoxX + 6f, devLogBoxY + 6f, 5,
            RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 180)));
        int lineY = (int)(devLogBoxY + 18f);
        for (String line : latest.lines()) {
            FontDraw.drawText(FontDraw.FontType.MEDIUM, ctx, "• " + line,
                devLogBoxX + 6f, lineY, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 120)));
            lineY += 9;
        }
    }

    private void drawGamepadWidget(DrawContext ctx, int mouseX, int mouseY) {
        if (alpha < 0.5f) return;
        boolean unlocked = PromoCodeManager.isGamesUnlocked();
        gamepadBoxW = 60f;
        gamepadBoxH = 24f;
        gamepadBoxX = 8f;
        gamepadBoxY = height - gamepadBoxH - 8f;
        boolean hovered = mouseX >= gamepadBoxX && mouseX <= gamepadBoxX + gamepadBoxW
                && mouseY >= gamepadBoxY && mouseY <= gamepadBoxY + gamepadBoxH;

        float hoverAlpha = hovered ? 1.0f : 0.8f;
        int bg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(alpha * 140 * hoverAlpha));
        int border = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(alpha * (hovered ? 120 : 60)));
        RenderUtil.Round.draw(ctx, gamepadBoxX, gamepadBoxY, gamepadBoxW, gamepadBoxH, 5f, bg);
        RenderUtil.Border.draw(ctx, gamepadBoxX, gamepadBoxY, gamepadBoxW, gamepadBoxH, 5f, 0.5f, border);

        int iconColor = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 200));
        float cx = gamepadBoxX + gamepadBoxW / 2f;
        float cy = gamepadBoxY + gamepadBoxH / 2f + 1f;
        drawGamepadIcon(ctx, cx, cy, iconColor);

        if (gamepadExpanded && !unlocked) {
            float popupX = gamepadBoxX;
            float popupY = gamepadBoxY - 56f;
            float popupW = 100f;
            float popupH = 50f;
            int popBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), (int)(alpha * 180));
            int popBorder = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(alpha * 80));
            RenderUtil.Round.draw(ctx, popupX, popupY, popupW, popupH, 5f, popBg);
            RenderUtil.Border.draw(ctx, popupX, popupY, popupW, popupH, 5f, 0.5f, popBorder);
            FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, MenuLocalization.tr("menu.games"),
                popupX + popupW / 2f, popupY + 8f, 6,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 180)), false);
            FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, "StrangeGames",
                popupX + popupW / 2f, popupY + 20f, 5,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), (int)(alpha * 160)), false);
            FontDraw.drawCenter(FontDraw.FontType.MEDIUM, ctx, MenuLocalization.tr("menu.promo_hint"),
                popupX + popupW / 2f, popupY + 34f, 4,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), (int)(alpha * 100)), false);
        }
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
        int size = menuButtons.size();
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_S -> size > 0 && pressMenuButton(0);
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_M -> size > 1 && pressMenuButton(1);
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3, GLFW.GLFW_KEY_A -> size > 2 && pressMenuButton(2);
            case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4, GLFW.GLFW_KEY_O -> size > 3 && pressMenuButton(3);
            case GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_KP_5, GLFW.GLFW_KEY_Q -> size > 4 && pressMenuButton(4);
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

    private static void drawGamepadIcon(DrawContext ctx, float cx, float cy, int color) {
        float bodyW = 12f, bodyH = 7f;
        float gripW = 3f, gripH = 5f;
        float gripOff = 5.5f;

        RenderUtil.Round.draw(ctx, cx - bodyW / 2f, cy - bodyH / 2f, bodyW, bodyH, 2f, color);

        RenderUtil.Round.draw(ctx, cx - gripOff - gripW / 2f, cy - gripH / 2f, gripW, gripH, 1.5f, color);
        RenderUtil.Round.draw(ctx, cx + gripOff - gripW / 2f, cy - gripH / 2f, gripW, gripH, 1.5f, color);

        int dpad = RenderUtil.ColorUtil.replAlpha(color, 80);
        float dW = 1.5f, dH = 4f;
        RenderUtil.Round.draw(ctx, cx - 4f - dW / 2f, cy - dH / 2f, dW, dH, 0.5f, dpad);
        RenderUtil.Round.draw(ctx, cx - 4f - dH / 2f, cy - dW / 2f, dH, dW, 0.5f, dpad);

        float btnR = 1.2f;
        RenderUtil.Round.draw(ctx, cx + 3f - btnR, cy - 3f - btnR, btnR * 2f, btnR * 2f, btnR, dpad);
        RenderUtil.Round.draw(ctx, cx + 5f - btnR, cy - btnR, btnR * 2f, btnR * 2f, btnR, dpad);
        RenderUtil.Round.draw(ctx, cx + 3f - btnR, cy + btnR, btnR * 2f, btnR * 2f, btnR, dpad);
    }

    private record HeaderLayout(int centerX, float logoX, float logoY, float logoSize, float titleY, float greetingY,
                                int versionX, int versionY) {
    }

}
