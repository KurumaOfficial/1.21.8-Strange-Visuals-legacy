package ru.strange.client.utils.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import ru.strange.client.utils.Helper;

public class RenderPlayer implements Helper {
    public static void onRenderPlayer(DrawContext context, Screen screen, int mouseX, int mouseY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        PauseMenuPreviewLayout.Bounds bounds = PauseMenuPreviewLayout.getPreviewBounds(screen);

        int size = 55;

        float scale = 0.0625F;

        InventoryScreen.drawEntity(
                context,
                bounds.x1(), bounds.y1(), bounds.x2(), bounds.y2(),
                size,
                scale,
                (float) mouseX, (float) mouseY,
                player
        );
    }
}
