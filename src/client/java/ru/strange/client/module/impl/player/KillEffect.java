package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import ru.strange.client.Strange;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventAttack;
import ru.strange.client.mixin.accessor.ClientWorldAccessor;
import ru.strange.client.event.impl.EventChangeWorld;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.event.impl.EventTotemPop;
import ru.strange.client.event.impl.EventUpdate;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.HueSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;
import ru.strange.client.module.impl.utilities.FakePlayer;
import ru.strange.client.renderengine.renderers.util.ShaderThemePreset;
import ru.strange.client.renderengine.renderers.util.ShaderThemeVisuals;
import ru.strange.client.utils.math.Mathf;
import ru.strange.client.utils.particle.ParticleUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "Kill Effect",
        description = "Эффекты убийства и тотема: молния, партиклы, шоквейв",
        category = Category.Player,
        bind = -1
)
public class KillEffect extends Module {

    private static final int QUAD_BUFFER_SIZE_BYTES = 1 << 10;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 18;
    private static final long PARTICLE_LIFESPAN_MS = 1200L;

    private final ModeSetting killMode = new ModeSetting("Убийство", "Комбо",
            "Нет", "Молния", "Партиклы", "Вспышка", "Шоквейв", "Ванильные", "Комбо");
    private final ModeSetting totemMode = new ModeSetting("Тотем", "Партиклы",
            "Нет", "Молния", "Партиклы", "Вспышка", "Шоквейв", "Ванильные", "Комбо");
    private final ModeSetting lightningStyle = new ModeSetting("Стиль молнии", "Кастомная",
            "Кастомная", "Ванильная")
            .hidden(() -> !usesLightningSettings());
    private final ModeSetting particleStyle = new ModeSetting("Стиль частиц", "Custom", "Custom", "Theme")
            .hidden(() -> !usesCustomParticles());
    private final ModeSetting particleType = new ModeSetting("Тип частиц", "Star",
            "Star", "Heart", "Snow", "Bloom", "Dollar", "Triangle", "Sakura", "Gemini", "Sims")
            .hidden(() -> !usesCustomParticles() || !particleStyle.is("Custom"));
    private final ModeSetting vanillaParticle = new ModeSetting("Ванильные частицы", "Totem",
            "Totem", "End Rod", "Electric Spark", "Soul Fire", "Glow", "Damage Indicator", "Crit")
            .hidden(() -> !usesVanillaMode());
    private final BooleanSetting shaderColors = new BooleanSetting("Shader Colors", false)
            .hidden(() -> !usesColorSettings());
    private final ModeSetting shaderTheme = new ModeSetting("Shader Theme", ShaderThemePreset.COSMOS.displayName(), ShaderThemePreset.names())
            .hidden(() -> !shouldShowShaderTheme());

    private final BooleanSetting jumpCircleOnKill = new BooleanSetting("JumpCircle On Kill", false)
            .hidden(() -> killMode.is("Нет"));
    private final BooleanSetting jumpCircleOnTotem = new BooleanSetting("JumpCircle On Totem", false)
            .hidden(() -> totemMode.is("Нет"));

    private final HueSetting colorSetting = new HueSetting("Цвет", new Color(255, 215, 110))
            .hidden(() -> !usesColorSettings());
    private final SliderSetting amount = new SliderSetting("Частицы", 28, 8, 64, 1, false)
            .hidden(() -> !usesParticleAmount());
    private final SliderSetting size = new SliderSetting("Размер", 0.55f, 0.15f, 1.2f, 0.05f, false)
            .hidden(() -> !usesCustomParticles());
    private final SliderSetting boltWidth = new SliderSetting("Толщина молнии", 2.0f, 1.0f, 4.0f, 0.5f, false)
            .hidden(() -> !usesLightningSettings());
    private final SliderSetting shockwaveRadius = new SliderSetting("Радиус волны", 3.4f, 1.5f, 8.0f, 0.1f, false)
            .hidden(() -> !usesShockwaveSettings());
    private final SliderSetting shockwaveWidth = new SliderSetting("Ширина волны", 0.28f, 0.08f, 0.7f, 0.02f, false)
            .hidden(() -> !usesShockwaveSettings());

    private final List<ParticleUtil.Particle> particles = new ArrayList<>();
    private final List<LightningBurst> bursts = new ArrayList<>();
    private final List<ShockwavePulse> shockwaves = new ArrayList<>();
    private final BufferAllocator renderBufferAllocator = new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers = VertexConsumerProvider.immediate(renderBufferAllocator);
    private long lastUpdateTime = System.nanoTime();

    public KillEffect() {
        addSettings(
                killMode, totemMode,
                lightningStyle,
                particleStyle, particleType, vanillaParticle,
                shaderColors, shaderTheme,
                jumpCircleOnKill, jumpCircleOnTotem,
                colorSetting, amount, size, boltWidth, shockwaveRadius, shockwaveWidth
        );
    }

    private void resetState() {
        particles.clear();
        bursts.clear();
        shockwaves.clear();
        lastUpdateTime = System.nanoTime();
    }

    @EventInit
    public void onAttack(EventAttack event) {
        if (!enable || !event.isConfirmed() || !event.isKilled() || event.getTarget() == null) {
            return;
        }

        migrateLegacyShaderParticleType();
        spawnConfiguredEffect(normalizeMode(killMode.get()), event.getTarget());
        triggerJumpCircle(event.getTarget(), jumpCircleOnKill.get());
    }

    @EventInit
    public void onTotemPop(EventTotemPop event) {
        if (!enable || event.getEntity() == null) {
            return;
        }
        if (event.getEntity().getCommandTags().contains(FakePlayer.TAG_FAKE_PLAYER)) {
            return;
        }

        migrateLegacyShaderParticleType();
        spawnConfiguredEffect(normalizeMode(totemMode.get()), event.getEntity());
        triggerJumpCircle(event.getEntity(), jumpCircleOnTotem.get());
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.world == null || mc.player == null) {
            resetState();
            return;
        }

        if (particles.isEmpty() && bursts.isEmpty() && shockwaves.isEmpty()) {
            return;
        }

        VertexConsumerProvider.Immediate immediate = renderVertexConsumers;

        try {
            if (!particles.isEmpty()) {
                ParticleUtil.renderParticles(event.getMatrixStack(), immediate, mc.gameRenderer.getCamera().getPos(), particles, event.getTickDelta());
            }

            if (!bursts.isEmpty() || !shockwaves.isEmpty()) {
                VertexConsumer lineBuffer = immediate.getBuffer(BOLT_LAYER);
                Matrix4f matrix = event.getMatrixStack().peek().getPositionMatrix();
                Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();

                bursts.removeIf(LightningBurst::isDead);
                for (LightningBurst burst : bursts) {
                    burst.render(lineBuffer, matrix, cameraPos);
                }

                shockwaves.removeIf(ShockwavePulse::isDead);
                for (ShockwavePulse pulse : shockwaves) {
                    pulse.render(lineBuffer, matrix, cameraPos);
                }
            }

            immediate.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.world == null || mc.player == null) {
            resetState();
            return;
        }

        long now = System.nanoTime();
        double deltaTime = (now - lastUpdateTime) / 1_000_000_000.0;
        lastUpdateTime = now;

        if (!particles.isEmpty()) {
            ParticleUtil.updateParticles(particles, 300, 850, deltaTime);
            particles.removeIf(particle -> particle.time().finished(PARTICLE_LIFESPAN_MS));
        }
    }

    @Override
    public void toggle() {
        super.toggle();
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    @EventInit
    public void onChangeWorld(EventChangeWorld event) {
        resetState();
    }

    private void migrateLegacyShaderParticleType() {
        if (!ShaderThemeVisuals.isShaderMode(particleType.get())) {
            return;
        }

        particleType.currentMode = "Star";
        particleStyle.currentMode = "Theme";
    }

    private int resolveBaseColor(Entity entity, double phase) {
        if (shaderColors.get()) {
            double entityPhase = entity == null ? phase : phase + entity.getId() * 0.37 + entity.getX() * 0.09 + entity.getZ() * 0.13;
            return ShaderThemeVisuals.animatedPrimary(shaderTheme.get(), entityPhase);
        }
        return colorSetting.getRGB();
    }

    private int resolveAccentColor(Entity entity, double phase) {
        if (shaderColors.get()) {
            double entityPhase = entity == null ? phase : phase + entity.getId() * 0.21 + entity.getX() * 0.07 + entity.getY() * 0.17;
            return ShaderThemeVisuals.animatedSecondary(shaderTheme.get(), entityPhase);
        }
        return RenderUtilColor.mix(colorSetting.getRGB(), 0xFFFFFFFF, 0.35f);
    }

    private void spawnConfiguredEffect(String mode, Entity entity) {
        if (entity == null || mode.equals("Нет")) {
            return;
        }

        Vec3d pos = entity.getPos().add(0.0, entity.getHeight() * 0.45, 0.0);
        int color = resolveBaseColor(entity, System.currentTimeMillis() * 0.0015);
        int accent = resolveAccentColor(entity, System.currentTimeMillis() * 0.0021 + 0.8);
        lastUpdateTime = System.nanoTime();

        switch (mode) {
            case "Молния" -> spawnLightning(pos, color, accent);
            case "Партиклы" -> spawnCustomParticles(pos, resolveCustomParticle(), color, 0.95);
            case "Вспышка" -> {
                spawnCustomParticles(pos, resolveHighlightParticle(), accent, 1.18);
                spawnVanillaBurst(pos, ParticleTypes.END_ROD, 0.07, 0.85f);
            }
            case "Шоквейв" -> {
                spawnShockwave(pos, color, accent);
                spawnCustomParticles(pos, resolveHighlightParticle(), accent, 1.05);
            }
            case "Ванильные" -> spawnVanillaBurst(pos, resolveVanillaParticle(), 0.08, 1.0f);
            case "Комбо" -> {
                spawnLightning(pos, color, accent);
                spawnShockwave(pos, color, accent);
                spawnCustomParticles(pos, resolveCustomParticle(), color, 1.00);
                spawnCustomParticles(pos, resolveHighlightParticle(), accent, 1.24);
                spawnVanillaBurst(pos, ParticleTypes.END_ROD, 0.10, 1.15f);
            }
            default -> {
            }
        }
    }

    private void triggerJumpCircle(Entity entity, boolean enabled) {
        if (!enabled || entity == null || Strange.get == null || Strange.get.manager == null) {
            return;
        }

        JumpCircle jumpCircle = Strange.get.manager.get(JumpCircle.class);
        if (jumpCircle != null) {
            jumpCircle.spawnExternal(entity.getPos().add(0.0, 0.04, 0.0));
        }
    }

    private void spawnLightning(Vec3d pos, int color, int accentColor) {
        if (lightningStyle.is("Ванильная")) {
            spawnVanillaLightningEntity(pos);
            return;
        }
        bursts.add(new LightningBurst(pos, color, accentColor, false));
    }

    private static int nextCosmeticLightningId = -50_000;

    private void spawnVanillaLightningEntity(Vec3d pos) {
        if (mc.world == null) return;
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(mc.world, net.minecraft.entity.SpawnReason.TRIGGERED);
        if (lightning == null) return;
        lightning.refreshPositionAfterTeleport(pos.x, pos.y, pos.z);
        lightning.setCosmetic(true);
        try {
            int id;
            synchronized (KillEffect.class) {
                id = nextCosmeticLightningId--;
                while (mc.world.getEntityById(id) != null) {
                    id = nextCosmeticLightningId--;
                }
            }
            lightning.setId(id);
            ((ClientWorldAccessor) mc.world).invokeAddEntity(lightning);
        } catch (Throwable t) {
            spawnVanillaBurst(pos.add(0.0, 0.3, 0.0), ParticleTypes.ELECTRIC_SPARK, 0.12, 0.95f);
            playLightningSound(pos);
        }
    }

    private void playLightningSound(Vec3d pos) {
        if (mc.world == null) {
            return;
        }
        mc.world.playSoundClient(pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.28f, 1.25f, false);
    }

    private void spawnCustomParticles(Vec3d pos, ParticleUtil.ParticleType type, int color, double speedMultiplier) {
        int count = (int) amount.get();
        float particleSize = size.get();
        for (int i = 0; i < count; i++) {
            Vec3d velocity = new Vec3d(
                    ThreadLocalRandom.current().nextDouble(-1.35, 1.35),
                    ThreadLocalRandom.current().nextDouble(0.2, 1.8),
                    ThreadLocalRandom.current().nextDouble(-1.35, 1.35)
            );
            particles.add(new ParticleUtil.Particle(
                    mc,
                    type,
                    pos,
                    velocity,
                    i,
                    (int) Mathf.step(Mathf.randomValue(0, 360), 15),
                    color,
                    particleSize,
                    speedMultiplier
            ));
        }
    }

    private void spawnVanillaBurst(Vec3d pos, ParticleEffect effect, double baseSpeed, float yBoost) {
        if (mc.world == null) {
            return;
        }

        int count = (int) amount.get();
        for (int i = 0; i < count; i++) {
            double velocityX = ThreadLocalRandom.current().nextDouble(-baseSpeed, baseSpeed);
            double velocityY = ThreadLocalRandom.current().nextDouble(baseSpeed * 0.35, baseSpeed * yBoost);
            double velocityZ = ThreadLocalRandom.current().nextDouble(-baseSpeed, baseSpeed);
            mc.world.addParticleClient(
                    effect,
                    pos.x + ThreadLocalRandom.current().nextDouble(-0.35, 0.35),
                    pos.y + ThreadLocalRandom.current().nextDouble(-0.15, 0.55),
                    pos.z + ThreadLocalRandom.current().nextDouble(-0.35, 0.35),
                    velocityX,
                    velocityY,
                    velocityZ
            );
        }
    }

    private void spawnShockwave(Vec3d pos, int color, int accentColor) {
        shockwaves.add(new ShockwavePulse(pos, color, accentColor));
    }

    private ParticleUtil.ParticleType resolveCustomParticle() {
        if (particleStyle.is("Theme")) {
            return ShaderThemeVisuals.particleType(shaderTheme.get());
        }

        return switch (particleType.get()) {
            case "Heart" -> ParticleUtil.ParticleType.HEART;
            case "Snow" -> ParticleUtil.ParticleType.SNOW;
            case "Bloom" -> ParticleUtil.ParticleType.BLOOM;
            case "Dollar" -> ParticleUtil.ParticleType.DOLLAR;
            case "Triangle" -> ParticleUtil.ParticleType.TRIANGLE;
            case "Sakura" -> ParticleUtil.ParticleType.SAKURA;
            case "Gemini" -> ParticleUtil.ParticleType.GEMINI;
            case "Sims" -> ParticleUtil.ParticleType.SIMS;
            default -> ParticleUtil.ParticleType.STAR;
        };
    }

    private ParticleUtil.ParticleType resolveHighlightParticle() {
        return particleStyle.is("Theme") ? ShaderThemeVisuals.particleType(shaderTheme.get()) : ParticleUtil.ParticleType.BLOOM;
    }

    private ParticleEffect resolveVanillaParticle() {
        return switch (vanillaParticle.get()) {
            case "End Rod" -> ParticleTypes.END_ROD;
            case "Electric Spark" -> ParticleTypes.ELECTRIC_SPARK;
            case "Soul Fire" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Glow" -> ParticleTypes.GLOW;
            case "Damage Indicator" -> ParticleTypes.DAMAGE_INDICATOR;
            case "Crit" -> ParticleTypes.CRIT;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
    }

    private String normalizeMode(String mode) {
        if ("Звезда".equalsIgnoreCase(mode)) {
            return "Партиклы";
        }
        return mode;
    }

    private boolean shouldShowShaderTheme() {
        return (shaderColors.get() && usesColorSettings()) || (usesCustomParticles() && particleStyle.is("Theme"));
    }

    private boolean usesColorSettings() {
        return usesLightningSettings() || usesCustomParticles() || usesShockwaveSettings();
    }

    private boolean usesParticleAmount() {
        return usesCustomParticles() || usesVanillaMode();
    }

    private boolean usesLightningSettings() {
        return modeUsesLightning(killMode.get()) || modeUsesLightning(totemMode.get());
    }

    private boolean usesCustomParticles() {
        return modeUsesCustomParticles(killMode.get()) || modeUsesCustomParticles(totemMode.get());
    }

    private boolean usesVanillaMode() {
        return modeUsesVanillaOnly(killMode.get()) || modeUsesVanillaOnly(totemMode.get());
    }

    private boolean usesShockwaveSettings() {
        return modeUsesShockwave(killMode.get()) || modeUsesShockwave(totemMode.get());
    }

    private boolean modeUsesLightning(String mode) {
        String normalized = normalizeMode(mode);
        return normalized.equals("Молния") || normalized.equals("Комбо");
    }

    private boolean modeUsesCustomParticles(String mode) {
        String normalized = normalizeMode(mode);
        return normalized.equals("Партиклы") || normalized.equals("Вспышка") || normalized.equals("Шоквейв") || normalized.equals("Комбо");
    }

    private boolean modeUsesVanillaOnly(String mode) {
        return normalizeMode(mode).equals("Ванильные");
    }

    private boolean modeUsesShockwave(String mode) {
        String normalized = normalizeMode(mode);
        return normalized.equals("Шоквейв") || normalized.equals("Комбо");
    }

    private final class LightningBurst {
        private final List<Vec3d> points = new ArrayList<>();
        private final List<LineSegment> branches = new ArrayList<>();
        private final int color;
        private final int accentColor;
        private final boolean vanillaStyle;
        private final long startedAt = System.currentTimeMillis();
        private final long lifeTime;

        private LightningBurst(Vec3d origin, int color, int accentColor, boolean vanillaStyle) {
            this.color = color;
            this.accentColor = accentColor;
            this.vanillaStyle = vanillaStyle;
            this.lifeTime = vanillaStyle ? 480L : 420L;

            double height = vanillaStyle
                    ? ThreadLocalRandom.current().nextDouble(2.8, 4.0)
                    : ThreadLocalRandom.current().nextDouble(2.1, 3.4);
            int pointCount = vanillaStyle ? 8 : 7;

            Vec3d current = origin;
            points.add(current);
            for (int i = 1; i < pointCount; i++) {
                float progress = i / (float) (pointCount - 1);
                double horizontalSpread = vanillaStyle ? 0.22 : 0.35;
                current = origin.add(
                        ThreadLocalRandom.current().nextDouble(-horizontalSpread, horizontalSpread),
                        height * progress,
                        ThreadLocalRandom.current().nextDouble(-horizontalSpread, horizontalSpread)
                );
                points.add(current);

                if (i > 1 && i < pointCount - 1) {
                    double branchSpread = vanillaStyle ? 0.22 : 0.35;
                    Vec3d branchEnd = current.add(
                            ThreadLocalRandom.current().nextDouble(-branchSpread, branchSpread),
                            ThreadLocalRandom.current().nextDouble(0.12, vanillaStyle ? 0.62 : 0.45),
                            ThreadLocalRandom.current().nextDouble(-branchSpread, branchSpread)
                    );
                    branches.add(new LineSegment(current, branchEnd));
                }
            }
        }

        private boolean isDead() {
            return System.currentTimeMillis() - startedAt >= lifeTime;
        }

        private void render(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos) {
            float lifeProgress = 1.0f - ((System.currentTimeMillis() - startedAt) / (float) lifeTime);
            float clampedLife = MathHelper.clamp(lifeProgress, 0.0f, 1.0f);
            int baseColor = vanillaStyle
                    ? RenderUtilColor.mix(color, accentColor, 0.72f)
                    : RenderUtilColor.mix(color, accentColor, 0.24f);
            int alpha = (int) ((vanillaStyle ? 235.0f : 220.0f) * clampedLife);
            int rgba = RenderUtilColor.withAlpha(baseColor, alpha);
            float thickness = Math.max(1.0f, boltWidth.get() + (vanillaStyle ? 0.8f : 0.0f));

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d start = points.get(i).subtract(cameraPos);
                Vec3d end = points.get(i + 1).subtract(cameraPos);
                putThickLine(buffer, matrix, start, end, rgba, thickness);
            }

            for (LineSegment branch : branches) {
                putThickLine(
                        buffer,
                        matrix,
                        branch.start.subtract(cameraPos),
                        branch.end.subtract(cameraPos),
                        RenderUtilColor.withAlpha(RenderUtilColor.mix(accentColor, baseColor, 0.55f), vanillaStyle ? alpha / 2 : alpha / 3),
                        Math.max(1.0f, thickness * (vanillaStyle ? 0.88f : 0.75f))
                );
            }
        }

        private void putThickLine(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, int rgba, float thickness) {
            putLine(buffer, matrix, start, end, rgba);
            if (thickness <= 1.05f) {
                return;
            }

            Vec3d direction = end.subtract(start);
            Vec3d offset = new Vec3d(-direction.z, 0.0, direction.x);
            if (offset.lengthSquared() < 1.0E-5) {
                offset = new Vec3d(0.02, 0.0, 0.0);
            } else {
                offset = offset.normalize().multiply((thickness - 1.0f) * 0.015f);
            }

            int sideAlpha = Math.max(20, ((rgba >> 24) & 0xFF) / 2);
            putLine(buffer, matrix, start.add(offset), end.add(offset), RenderUtilColor.withAlpha(rgba, sideAlpha));
            putLine(buffer, matrix, start.subtract(offset), end.subtract(offset), RenderUtilColor.withAlpha(rgba, sideAlpha));
        }

        private void putLine(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, int rgba) {
            int r = (rgba >> 16) & 0xFF;
            int g = (rgba >> 8) & 0xFF;
            int b = rgba & 0xFF;
            int a = (rgba >> 24) & 0xFF;
            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a);
            buffer.vertex(matrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, a);
        }
    }

    private final class ShockwavePulse {
        private final Vec3d origin;
        private final int color;
        private final int accentColor;
        private final long startedAt = System.currentTimeMillis();
        private final long lifeTime = 540L;

        private ShockwavePulse(Vec3d origin, int color, int accentColor) {
            this.origin = origin;
            this.color = color;
            this.accentColor = accentColor;
        }

        private boolean isDead() {
            return System.currentTimeMillis() - startedAt >= lifeTime;
        }

        private void render(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos) {
            float progress = MathHelper.clamp((System.currentTimeMillis() - startedAt) / (float) lifeTime, 0.0f, 1.0f);
            float eased = easeOut(progress);
            float radius = shockwaveRadius.get() * eased;
            float width = Math.max(0.04f, shockwaveWidth.get() * (1.0f - progress * 0.45f));
            float alpha = 1.0f - progress;
            int segments = 56;

            int innerColor = RenderUtilColor.withAlpha(color, (int) (110.0f * alpha));
            int outerColor = RenderUtilColor.withAlpha(RenderUtilColor.mix(color, accentColor, 0.36f), (int) (205.0f * alpha));
            int spokeColor = RenderUtilColor.withAlpha(RenderUtilColor.mix(color, accentColor, 0.18f), (int) (145.0f * alpha));

            for (int i = 0; i < segments; i++) {
                double angle0 = (Math.PI * 2.0 * i) / segments;
                double angle1 = (Math.PI * 2.0 * (i + 1)) / segments;

                Vec3d inner0 = sample(radius - width, angle0, progress, cameraPos);
                Vec3d outer0 = sample(radius + width, angle0, progress, cameraPos);
                Vec3d inner1 = sample(radius - width, angle1, progress, cameraPos);
                Vec3d outer1 = sample(radius + width, angle1, progress, cameraPos);

                drawLine(buffer, matrix, inner0, inner1, innerColor);
                drawLine(buffer, matrix, outer0, outer1, outerColor);
                if ((i & 5) == 0) {
                    drawLine(buffer, matrix, inner0, outer0, spokeColor);
                }
            }
        }

        private Vec3d sample(double radius, double angle, float progress, Vec3d cameraPos) {
            double yOffset = Math.sin(angle * 4.0 + progress * 10.0) * 0.03 + progress * 0.18;
            return new Vec3d(
                    origin.x + Math.cos(angle) * Math.max(0.05, radius) - cameraPos.x,
                    origin.y + yOffset - cameraPos.y,
                    origin.z + Math.sin(angle) * Math.max(0.05, radius) - cameraPos.z
            );
        }
    }

    private float easeOut(float value) {
        float inverted = 1.0f - value;
        return 1.0f - inverted * inverted * inverted;
    }

    private void drawLine(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, int color) {
        putVertex(buffer, matrix, start, color);
        putVertex(buffer, matrix, end, color);
    }

    private void putVertex(VertexConsumer buffer, Matrix4f matrix, Vec3d pos, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, a);
    }

    private record LineSegment(Vec3d start, Vec3d end) {
    }

    private static final class RenderUtilColor {
        private static int withAlpha(int color, int alpha) {
            return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
        }

        private static int mix(int colorA, int colorB, float amount) {
            amount = Math.max(0.0f, Math.min(1.0f, amount));
            int r = (int) (((colorA >> 16) & 0xFF) + (((colorB >> 16) & 0xFF) - ((colorA >> 16) & 0xFF)) * amount);
            int g = (int) (((colorA >> 8) & 0xFF) + (((colorB >> 8) & 0xFF) - ((colorA >> 8) & 0xFF)) * amount);
            int b = (int) ((colorA & 0xFF) + ((colorB & 0xFF) - (colorA & 0xFF)) * amount);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }

    private static final RenderPipeline BOLT_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "kill_effect_bolt"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer BOLT_LAYER = RenderLayer.of(
            "strange_kill_effect_bolt",
            QUAD_BUFFER_SIZE_BYTES,
            false,
            true,
            BOLT_PIPELINE,
            RenderLayer.MultiPhaseParameters.builder()
                    .lineWidth(new RenderPhase.LineWidth(OptionalDouble.of(2.5)))
                    .build(false)
    );
}
