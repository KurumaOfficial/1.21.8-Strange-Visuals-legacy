package ru.strange.client.module.impl.player;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;

import java.awt.Color;

@IModule(
        name = "Нимб",
        description = "Вращающийся нимб над игроком",
        category = Category.Player,
        bind = -1
)
public class Nimb extends Module {

    private static final int BUFFER_SIZE = 1 << 16;
    private static final Identifier NIMBUS_TEXTURE = Identifier.of("strange", "textures/world/dashbloom.png");
    private static final float TAU = (float) (Math.PI * 2.0);

    private final SliderSetting radius = new SliderSetting("Радиус", 0.78f, 0.3f, 1.8f, 0.05f, false);
    private final SliderSetting height = new SliderSetting("Высота", 0.28f, -0.2f, 1.1f, 0.02f, false);
    private final SliderSetting spriteSize = new SliderSetting("Размер спрайта", 0.30f, 0.10f, 0.70f, 0.02f, false);
    private final SliderSetting count = new SliderSetting("Количество", 12.0f, 6.0f, 20.0f, 1.0f, false);
    private final SliderSetting speed = new SliderSetting("Скорость", 0.95f, 0.2f, 2.4f, 0.05f, false);
    private final SliderSetting alpha = new SliderSetting("Альфа", 170.0f, 20.0f, 255.0f, 5.0f, false);
    private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
    private final BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false);
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());
    private final HueSetting color = new HueSetting("Цвет", new Color(255, 220, 120));

    private final BufferAllocator allocator = new BufferAllocator(BUFFER_SIZE);
    private final VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(allocator);

    public Nimb() {
        addSettings(radius, height, spriteSize, count, speed, alpha, rainbow, shaderColors, shaderTheme, color);
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        float partialTicks = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

        double px = mc.player.lastRenderX + (mc.player.getX() - mc.player.lastRenderX) * partialTicks;
        double py = mc.player.lastRenderY + (mc.player.getY() - mc.player.lastRenderY) * partialTicks;
        double pz = mc.player.lastRenderZ + (mc.player.getZ() - mc.player.lastRenderZ) * partialTicks;

        float x = (float) (px - cameraPos.x);
        float y = (float) (py + mc.player.getHeight() + height.get() - cameraPos.y);
        float z = (float) (pz - cameraPos.z);

        long now = System.currentTimeMillis();
        float orbitTime = (float) (now * 0.0017f * speed.get());
        float bob = (float) Math.sin(orbitTime * 1.8f) * 0.018f;

        MatrixStack matrices = event.getMatrixStack();
        int cnt = Math.max(6, Math.min(20, (int) count.get()));

        try {
            VertexConsumer buffer = immediate.getBuffer(RenderLayer.getEntityTranslucent(NIMBUS_TEXTURE));

            for (int i = 0; i < cnt; i++) {
                float phase = (TAU / cnt) * i + orbitTime;
                float pulse = 0.85f + 0.15f * (float) Math.sin(orbitTime * 2.6f + i * 0.62f);
                float ringRadius = radius.get() + (float) Math.sin(orbitTime + i * 0.35f) * 0.03f;

                float sx = x + (float) Math.cos(phase) * ringRadius;
                float sz = z + (float) Math.sin(phase) * ringRadius;
                float sy = y + bob + (float) Math.sin(phase * 2.0f + orbitTime * 1.2f) * 0.016f;

                int rgb = resolveColor(orbitTime + i * 0.08f);
                int depthAlpha = Math.max(0, Math.min(255, (int) (alpha.get() * pulse)));
                float size = spriteSize.get() * (0.82f + 0.18f * pulse);

                drawNimbusSprite(matrices, buffer, sx, sy, sz, size, rgb, depthAlpha);
            }

            int innerCount = Math.max(4, cnt / 2);
            for (int i = 0; i < innerCount; i++) {
                float phase = (TAU / innerCount) * i - orbitTime * 0.78f;
                float ringRadius = radius.get() * 0.62f;

                float sx = x + (float) Math.cos(phase) * ringRadius;
                float sz = z + (float) Math.sin(phase) * ringRadius;
                float sy = y + 0.065f + bob * 0.6f + (float) Math.sin(phase * 1.8f - orbitTime) * 0.012f;

                int rgb = resolveColor(orbitTime * 0.7 + i * 0.11f + 4.0);
                int depthAlpha = Math.max(0, Math.min(255, (int) (alpha.get() * 0.44f)));
                float size = spriteSize.get() * 0.64f;

                drawNimbusSprite(matrices, buffer, sx, sy, sz, size, rgb, depthAlpha);
            }

            immediate.draw();
        } finally {
            allocator.clear();
        }
    }

    private void drawNimbusSprite(MatrixStack matrices, VertexConsumer buffer,
                                  float x, float y, float z,
                                  float size,
                                  int rgb, int alphaValue) {
        if (size <= 0.0f || alphaValue <= 2) {
            return;
        }

        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        matrices.scale(size, size, 1.0f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(0.0f, 1.0f)
                .overlay(0, 240)
                .light(15, 15)
                .normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(1.0f, 1.0f)
                .overlay(0, 240)
                .light(15, 15)
                .normal(0, 0, 1);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(1.0f, 0.0f)
                .overlay(0, 240)
                .light(15, 15)
                .normal(0, 0, 1);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f)
                .color(r, g, b, alphaValue)
                .texture(0.0f, 0.0f)
                .overlay(0, 240)
                .light(15, 15)
                .normal(0, 0, 1);

        matrices.pop();
    }

    private int resolveColor(double phase) {
        if (shaderColors.get()) {
            return ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), phase);
        }
        if (rainbow.get()) {
            float hue = (float) ((System.currentTimeMillis() * 0.00024 + phase * 0.08) % 1.0);
            return Color.HSBtoRGB(hue, 0.85f, 1.0f);
        }
        return color.getRGB();
    }
}
