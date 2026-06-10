package ru.strange.client.StarterMenu;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.Strange;
import ru.strange.client.utils.render.FontDraw;
import ru.strange.client.utils.render.RenderUtil;

import java.util.List;

public class DevlogsScreen extends Screen {
    private static final int PANEL_PADDING = 14;
    private static final int ENTRY_GAP = 18;
    private static final int SCROLL_STEP = 24;

    private final Screen parent;
    private int scrollOffset;

    public DevlogsScreen(Screen parent) {
        super(Text.literal(MenuLocalization.tr("menu.devlogs.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        MenuLocalization.initialize();
        int buttonWidth = Math.min(120, Math.max(80, width - 24));
        addDrawableChild(ButtonWidget.builder(Text.literal(MenuLocalization.tr("common.back")), button -> close())
                .dimensions(width / 2 - buttonWidth / 2, height - 28, buttonWidth, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal(MenuLocalization.tr("common.open_browser")), button -> openLink())
                .dimensions(width / 2 - buttonWidth / 2, height - 50, buttonWidth, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        float panelWidth = Math.min(420f, width - 24f);
        float panelHeight = Math.min(280f, height - 56f);
        float panelX = (width - panelWidth) / 2f;
        float panelY = 24f;

        int panelBg = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getBackGroundColor(1, 1), 210);
        int panelBorder = RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getMainColor(1, 1), 120);
        RenderUtil.Round.draw(context, panelX, panelY, panelWidth, panelHeight, 8f, panelBg);
        RenderUtil.Border.draw(context, panelX, panelY, panelWidth, panelHeight, 8f, 0.6f, panelBorder);

        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                MenuLocalization.tr("menu.devlogs.title"),
                width / 2f,
                panelY + 16f,
                10,
                RenderUtil.ColorUtil.getTextColor(1, 1),
                false
        );
        FontDraw.drawCenter(
                FontDraw.FontType.MEDIUM,
                context,
                MenuLocalization.tr("menu.devlogs.subtitle", Strange.version),
                width / 2f,
                panelY + 28f,
                6,
                RenderUtil.ColorUtil.replAlpha(RenderUtil.ColorUtil.getTextColor(1, 1), 170),
                false
        );

        float contentX = panelX + PANEL_PADDING;
        float contentWidth = panelWidth - PANEL_PADDING * 2f;
        float contentTop = panelY + 42f;
        float contentBottom = panelY + panelHeight - 10f;
        float contentHeight = contentBottom - contentTop;

        context.enableScissor(
                (int) contentX,
                (int) contentTop,
                (int) (contentX + contentWidth),
                (int) contentBottom
        );

        float y = contentTop - scrollOffset;
        for (DevlogEntry entry : DevlogRegistry.entries()) {
            if (y + 40f < contentTop || y > contentBottom) {
                y += measureEntryHeight(entry, contentWidth) + ENTRY_GAP;
                continue;
            }

            FontDraw.drawText(
                    FontDraw.FontType.MEDIUM,
                    context,
                    entry.version() + "  ·  " + entry.date(),
                    contentX,
                    y + 8f,
                    7,
                    RenderUtil.ColorUtil.getMainColor(1, 1)
            );

            float lineY = y + 20f;
            for (String line : entry.lines()) {
                FontDraw.drawText(
                        FontDraw.FontType.MEDIUM,
                        context,
                        "• " + line,
                        contentX + 4f,
                        lineY,
                        6,
                        RenderUtil.ColorUtil.getTextColor(1, 1)
                );
                lineY += 11f;
            }

            y += measureEntryHeight(entry, contentWidth) + ENTRY_GAP;
        }

        context.disableScissor();

        int maxScroll = Math.max(0, (int) (computeTotalHeight(contentWidth) - contentHeight));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, scrollOffset - (int) (verticalAmount * SCROLL_STEP));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void openLink() {
        DevlogEntry latest = DevlogRegistry.entries().getFirst();
        String url = latest.url();
        if (url != null && !url.isBlank()) {
            Strange.openUrl(url);
        }
    }

    private static float measureEntryHeight(DevlogEntry entry, float width) {
        return 20f + entry.lines().size() * 11f;
    }

    private float computeTotalHeight(float width) {
        float total = 0f;
        List<DevlogEntry> entries = DevlogRegistry.entries();
        for (int i = 0; i < entries.size(); i++) {
            total += measureEntryHeight(entries.get(i), width);
            if (i < entries.size() - 1) {
                total += ENTRY_GAP;
            }
        }
        return total;
    }

    public record DevlogEntry(String version, String date, List<String> lines, String url) {
        public DevlogEntry(String version, String date, List<String> lines) {
            this(version, date, lines, null);
        }
    }

    public static final class DevlogRegistry {
        private DevlogRegistry() {
        }

        public static List<DevlogEntry> entries() {
            return List.of(
                    new DevlogEntry(
                        "v1.6.0",
                        "03.06.2026",
                        List.of("Мини-игры: Тетрис и Пакман (промокод BoxingGames)", "NEON тема с тёмно-фиолетовым фоном", "Исправления багов и улучшения GUI"),
                        "https://discord.gg/r2YN5KhzT5"
                    ),
                    new DevlogEntry(
                        "v1.5.0",
                            "02.06.2026",
                            List.of(
                                    MenuLocalization.tr("menu.devlogs.v150.search"),
                                    MenuLocalization.tr("menu.devlogs.v150.devlogs"),
                                    MenuLocalization.tr("menu.devlogs.v150.cape"),
                                    MenuLocalization.tr("menu.devlogs.v150.gui")
                            )
                    ),
                    new DevlogEntry(
                            "v1.4.0",
                            "15.05.2026",
                            List.of(
                                    MenuLocalization.tr("menu.devlogs.v140.menu"),
                                    MenuLocalization.tr("menu.devlogs.v140.alt"),
                                    MenuLocalization.tr("menu.devlogs.v140.themes")
                            )
                    ),
                    new DevlogEntry(
                            "v1.3.0",
                            "01.05.2026",
                            List.of(
                                    MenuLocalization.tr("menu.devlogs.v130.capes"),
                                    MenuLocalization.tr("menu.devlogs.v130.hud"),
                                    MenuLocalization.tr("menu.devlogs.v130.commands")
                            )
                    )
            );
        }
    }
}
