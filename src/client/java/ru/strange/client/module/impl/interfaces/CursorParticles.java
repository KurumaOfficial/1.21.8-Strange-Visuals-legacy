package ru.strange.client.module.impl.interfaces;

import net.minecraft.client.gui.DrawContext;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.render.RenderUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "Cursor Particles",
        description = "Частицы от курсора в меню и чате",
        category = Category.Interface,
        bind = -1
)
public class CursorParticles extends Module {

    private static CursorParticles INSTANCE;

    private final SliderSetting maxParticles = new SliderSetting("Лимит", 140.0f, 30.0f, 360.0f, 1.0f, false);
    private final SliderSetting spawnRate = new SliderSetting("Спавн", 2.2f, 0.2f, 6.0f, 0.1f, false);
    private final SliderSetting lifeTime = new SliderSetting("Время жизни", 620.0f, 180.0f, 2200.0f, 20.0f, false);
    private final SliderSetting size = new SliderSetting("Размер", 3.0f, 1.2f, 8.0f, 0.2f, false);
    private final SliderSetting speed = new SliderSetting("Скорость", 1.0f, 0.3f, 3.0f, 0.1f, false);
    private final BooleanSetting rainbow = new BooleanSetting("Радуга", false);
    private final BooleanSetting shaderColors = new BooleanSetting("Shader Colors", true);
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shaderColors.get());
    private final HueSetting color = new HueSetting("Цвет", new Color(140, 210, 255));

    private final List<CursorParticle> particles = new ArrayList<>();
    private float lastMouseX = Float.NaN;
    private float lastMouseY = Float.NaN;
    private long lastFrameTime;

    public CursorParticles() {
        INSTANCE = this;
        addSettings(maxParticles, spawnRate, lifeTime, size, speed, rainbow, shaderColors, shaderTheme, color);
    }

    @Override
    public void onDisable() {
        particles.clear();
        lastMouseX = Float.NaN;
        lastMouseY = Float.NaN;
        lastFrameTime = 0L;
        super.onDisable();
    }

    public static void renderScreenParticles(DrawContext context) {
        if (INSTANCE == null || !INSTANCE.enable) {
            return;
        }
        INSTANCE.renderInternal(context);
    }

    private void renderInternal(DrawContext context) {
        if (mc.currentScreen == null) {
            particles.clear();
            lastMouseX = Float.NaN;
            lastMouseY = Float.NaN;
            return;
        }

        float mouseX = getScaledMouse(mc.mouse.getX(), mc.getWindow().getScaledWidth(), mc.getWindow().getWidth());
        float mouseY = getScaledMouse(mc.mouse.getY(), mc.getWindow().getScaledHeight(), mc.getWindow().getHeight());
        if (!Float.isFinite(mouseX) || !Float.isFinite(mouseY)) {
            return;
        }

        long now = System.currentTimeMillis();
        float dt = resolveDeltaTime(now);

        spawnParticles(mouseX, mouseY, now);
        updateParticles(dt, now);
        renderParticles(context, now);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    private void spawnParticles(float mouseX, float mouseY, long now) {
        if (!Float.isFinite(lastMouseX) || !Float.isFinite(lastMouseY)) {
            return;
        }

        float dx = mouseX - lastMouseX;
        float dy = mouseY - lastMouseY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < 0.15f) {
            return;
        }

        int spawnCount = Math.max(1, Math.min(14, (int) Math.floor(distance * spawnRate.get() * 0.18f)));
        for (int i = 0; i < spawnCount; i++) {
            float t = (i + 1.0f) / (spawnCount + 1.0f);
            float x = lastMouseX + dx * t;
            float y = lastMouseY + dy * t;
            particles.add(new CursorParticle(x, y, now, (long) lifeTime.get()));
        }

        int limit = Math.max(0, (int) maxParticles.get());
        while (particles.size() > limit && !particles.isEmpty()) {
            particles.removeFirst();
        }
    }

    private void updateParticles(float dt, long now) {
        float speedScale = speed.get();
        Iterator<CursorParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            CursorParticle particle = iterator.next();
            particle.vx *= 0.985f;
            particle.vy *= 0.985f;
            particle.x += particle.vx * dt * 60.0f * speedScale;
            particle.y += particle.vy * dt * 60.0f * speedScale;

            if (particle.progress(now) >= 1.0f) {
                iterator.remove();
            }
        }
    }

    private void renderParticles(DrawContext context, long now) {
        for (CursorParticle particle : particles) {
            float progress = particle.progress(now);
            if (progress >= 1.0f) {
                continue;
            }

            float fadeIn = Math.min(1.0f, progress / 0.2f);
            float fadeOut = 1.0f - progress;
            float alphaPc = fadeIn * fadeOut * fadeOut;

            float drawSize = size.get() * (0.8f + 0.35f * (1.0f - progress));
            int rgb = resolveColor(now, particle.seed, particle.x, particle.y);
            int alpha = Math.max(0, Math.min(255, (int) (255.0f * alphaPc)));
            int colorWithAlpha = RenderUtil.ColorUtil.replAlpha(rgb, alpha);

            RenderUtil.Round.draw(
                    context,
                    particle.x - drawSize * 0.5f,
                    particle.y - drawSize * 0.5f,
                    drawSize,
                    drawSize,
                    drawSize * 0.5f,
                    colorWithAlpha
            );
        }
    }

    private int resolveColor(long now, float seed, float x, float y) {
        if (shaderColors.get()) {
            return ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), now * 0.0022 + seed * 6.3 + x * 0.02 + y * 0.01);
        }
        if (rainbow.get()) {
            float hue = (float) ((now * 0.00025 + seed * 0.35) % 1.0);
            return Color.HSBtoRGB(hue, 0.86f, 1.0f);
        }
        return color.getRGB();
    }

    private float resolveDeltaTime(long now) {
        if (lastFrameTime <= 0L) {
            lastFrameTime = now;
            return 1.0f / 60.0f;
        }

        float dt = (now - lastFrameTime) / 1000.0f;
        lastFrameTime = now;
        if (!Float.isFinite(dt)) {
            return 1.0f / 60.0f;
        }
        return Math.max(0.001f, Math.min(0.08f, dt));
    }

    private float getScaledMouse(double raw, int scaled, int window) {
        if (!Double.isFinite(raw) || scaled <= 0 || window <= 0) {
            return Float.NaN;
        }
        return (float) (raw * (double) scaled / (double) window);
    }

    private static final class CursorParticle {
        float x;
        float y;
        float vx;
        float vy;
        final float seed;
        final long bornAt;
        final long life;

        CursorParticle(float x, float y, long now, long life) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            this.x = x;
            this.y = y;
            this.seed = random.nextFloat();
            this.vx = (random.nextFloat() - 0.5f) * 0.46f;
            this.vy = (random.nextFloat() - 0.5f) * 0.46f;
            this.bornAt = now;
            this.life = Math.max(1L, life + random.nextLong(-90L, 90L));
        }

        float progress(long now) {
            return Math.max(0.0f, Math.min(1.0f, (now - bornAt) / (float) life));
        }
    }
}
