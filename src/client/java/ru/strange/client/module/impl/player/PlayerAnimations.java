package ru.strange.client.module.impl.player;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import ru.strange.client.event.EventInit;
import ru.strange.client.event.impl.EventRender3D;
import ru.strange.client.module.api.Category;
import ru.strange.client.module.api.IModule;
import ru.strange.client.module.api.Module;
import ru.strange.client.module.api.setting.impl.BindSettings;
import ru.strange.client.module.api.setting.impl.BooleanSetting;
import ru.strange.client.module.api.setting.impl.ModeSetting;
import ru.strange.client.module.api.setting.impl.SliderSetting;

@IModule(
        name = "Анимации",
        description = "Пасхалка — анимации рук игрока",
        category = Category.Player,
        bind = -1
)
public class PlayerAnimations extends Module {

    // Константы модели руки Minecraft biped (в блоках, 1px = 1/16)
    // BipedEntityModel: голова pivot (0,0,0), тело (0,0,0), rightArm pivot (-5,2,0), leftArm pivot (5,2,0)
    // Entity renderer: translate(entityPos), rotateY(180-bodyYaw), scale(-1,-1,1), translate(0,-1.501,0)
    private static final float PV_X_RIGHT = -5f / 16f;
    private static final float PV_X_LEFT  =  5f / 16f;
    private static final float PV_Y       =  2f / 16f;
    private static final float ARM_LENGTH =  12f / 16f;   // 12px
    private static final float MODEL_Y_OFFSET = -1.501f;  // MC LivingEntityRenderer offset
    private static final float LETTER_DEPTH   =  0.06f;
    private static final float LETTER_OUTWARD_OFFSET = 0.075f;
    private static final float LETTER_FORWARD_OFFSET = 0.018f;

    private static final float BASE_ARM_PITCH = -1.45f;
    private static final float BASE_ARM_YAW   =  0.06f;

    private static PlayerAnimations instance;

    private final BindSettings   activateBind    = new BindSettings("Клавиша активации", GLFW.GLFW_KEY_X);
    private final ModeSetting    animMode        = new ModeSetting("Анимация", "XB", "XB", "Дрочка");
    private final SliderSetting  speed           = new SliderSetting("Скорость", 0.7f, 0.2f, 2.0f, 0.1f, false);
    private final SliderSetting  amplitude       = new SliderSetting("Амплитуда", 0.30f, 0.05f, 0.6f, 0.01f, false);
    private final SliderSetting  scale           = new SliderSetting("Размер букв", 1.0f, 0.5f, 2.0f, 0.1f, false);
    private final BooleanSetting autoThirdPerson = new BooleanSetting("Авто F5", true);

    private static final int BUFFER_SIZE             = 1 << 12;
    private static final int RENDER_BUFFER_SIZE_BYTES = 1 << 14;

    private final BufferAllocator renderBufferAllocator =
            new BufferAllocator(RENDER_BUFFER_SIZE_BYTES);
    private final VertexConsumerProvider.Immediate renderVertexConsumers =
            VertexConsumerProvider.immediate(renderBufferAllocator);

    private Perspective previousPerspective = Perspective.FIRST_PERSON;
    private boolean forcedPerspective;

    public PlayerAnimations() {
        instance = this;
        addSettings(activateBind, animMode, speed, amplitude, scale, autoThirdPerson);
    }

    public static PlayerAnimations getInstance() { return instance; }

    // ---- Публичные методы для mixin ----

    public static boolean isXBMode() {
        return instance != null && instance.enable
                && instance.activateBind.isKeyDown(instance.activateBind.key)
                && instance.animMode.is("XB");
    }

    public static boolean isDrochkaMode() {
        return instance != null && instance.enable
                && instance.activateBind.isKeyDown(instance.activateBind.key)
                && instance.animMode.is("Дрочка");
    }

    /** Для совместимости с BipedEntityModelMixin */
    public static boolean isActive() {
        return isXBMode() || isDrochkaMode();
    }

    public static boolean shouldAffectLocalPlayer(int playerId) {
        return isActive()
                && mc != null && mc.player != null
                && mc.player.getId() == playerId
                && mc.options.getPerspective() != Perspective.FIRST_PERSON;
    }

    // XB poses
    public static float getArmPitch(boolean rightArm) {
        if (instance == null || !isXBMode()) return BASE_ARM_PITCH;
        return BASE_ARM_PITCH + instance.getWaveXB(rightArm) * 0.95f;
    }

    public static float getArmYaw(boolean rightArm) {
        return rightArm ? -BASE_ARM_YAW : BASE_ARM_YAW;
    }

    public static float getArmRoll(boolean rightArm) {
        if (instance == null || !isXBMode()) return 0f;
        return (rightArm ? -1f : 1f) * instance.getWaveXB(rightArm) * 0.16f;
    }

    public static float getSleeveOffsetY(boolean rightArm) {
        if (instance == null || !isXBMode()) return 0f;
        return instance.getWaveXB(rightArm) * 0.9f;
    }

    // Дрочка poses (правая рука — рука висит вниз-вперёд, между ног, качается вперёд-назад)
    public static float getDrochkaRightPitch() {
        if (instance == null || !isDrochkaMode()) return 0f;
        float t = instance.getDrochkaPhase();
        // Поза "между ног" с мягкой анимацией вперёд-назад.
        return 1.34f + (float) (Math.sin(t) * 0.26f);
    }

    public static float getDrochkaRightYaw() {
        return -0.12f;
    }

    public static float getDrochkaRightRoll() {
        if (instance == null || !isDrochkaMode()) return 0f;
        float t = instance.getDrochkaPhase();
        return 0.08f + (float) (Math.sin(t) * 0.05f);
    }

    // Дрочка poses (левая рука — зеркально правой)
    public static float getDrochkaLeftPitch() {
        if (instance == null || !isDrochkaMode()) return 0f;
        float t = instance.getDrochkaPhase();
        return 1.34f + (float) (Math.sin(t) * 0.26f);
    }

    public static float getDrochkaLeftYaw() {
        return 0.12f;
    }

    public static float getDrochkaLeftRoll() {
        if (instance == null || !isDrochkaMode()) return 0f;
        float t = instance.getDrochkaPhase();
        return -0.08f - (float) (Math.sin(t) * 0.05f);
    }

    // ---- Приватная арифметика ----
    private float getWaveXB(boolean rightArm) {
        float cycleDuration = 3000f / speed.get();
        float time = (System.currentTimeMillis() % (long) cycleDuration)
                / cycleDuration * 2f * (float) Math.PI;
        float phase = rightArm ? time : time + (float) Math.PI;
        return (float) Math.sin(phase) * amplitude.get();
    }

    private float getDrochkaPhase() {
        float cycleDuration = 700f / speed.get();
        return (System.currentTimeMillis() % (long) cycleDuration)
                / cycleDuration * 2f * (float) Math.PI;
    }

    // ---- Жизненный цикл ----
    @Override
    public void onEnable() {
        super.onEnable();
        if (mc == null || mc.player == null || !autoThirdPerson.get()) return;
        previousPerspective = mc.options.getPerspective();
        if (previousPerspective == Perspective.FIRST_PERSON) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            forcedPerspective = true;
        }
    }

    @Override
    public void onDisable() {
        if (mc != null && forcedPerspective && mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            mc.options.setPerspective(previousPerspective);
        }
        forcedPerspective = false;
        super.onDisable();
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        if (!activateBind.isKeyDown(activateBind.key)) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        if (animMode.is("XB")) {
            renderXBLetters(event);
        }
        // Дрочка — только arm-pose через BipedEntityModelMixin
    }

    // ===================================================
    //  XB — рендер букв у кисти, привязанный к реальной
    //  ориентации руки (pitch / yaw / roll)
    // ===================================================
    private void renderXBLetters(EventRender3D event) {
        float  tickDelta = event.getTickDelta();
        MatrixStack matrices = event.getMatrixStack();
        Camera camera   = mc.gameRenderer.getCamera();
        Vec3d  cam      = camera.getPos();
        Vec3d  playerPos = mc.player.getLerpedPos(tickDelta);
        float  bodyYaw  = mc.player.getBodyYaw();

        float s = 0.055f * scale.get();

        try {
            placeLetterAtWrist(matrices, cam, playerPos, bodyYaw, s, true);
            placeLetterAtWrist(matrices, cam, playerPos, bodyYaw, s, false);
            renderVertexConsumers.draw();
        } finally {
            renderBufferAllocator.clear();
        }
    }

    /**
     * Рисует букву billboard-методом в позиции кисти.
     * Позиция кисти вычисляется по тем же arm-transform, а сама буква разворачивается к камере,
     * чтобы текст не "заваливался" при боковом положении игрока.
     */
    private void placeLetterAtWrist(MatrixStack matrices, Vec3d cam,
                                     Vec3d playerPos, float bodyYaw,
                                     float s, boolean isRight) {
        Vec3d wristPos = resolveWristWorldPos(playerPos, bodyYaw, isRight);
        Camera camera = mc.gameRenderer.getCamera();

        matrices.push();
        matrices.translate(wristPos.x - cam.x, wristPos.y - cam.y, wristPos.z - cam.z);

        matrices.multiply(camera.getRotation());
        matrices.scale(-s, -s, s);

        if (isRight) {
            renderLetterX(matrices.peek().getPositionMatrix(), renderVertexConsumers);
        } else {
            renderLetterB(matrices.peek().getPositionMatrix(), renderVertexConsumers);
        }
        matrices.pop();
    }

    private Vec3d resolveWristWorldPos(Vec3d playerPos, float bodyYaw, boolean isRight) {
        float pitch = getArmPitch(isRight);
        float yaw   = getArmYaw(isRight);
        float roll  = getArmRoll(isRight);

        MatrixStack armStack = new MatrixStack();
        armStack.translate(playerPos.x, playerPos.y, playerPos.z);
        armStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        armStack.scale(-1f, -1f, 1f);
        armStack.translate(0f, MODEL_Y_OFFSET, 0f);

        float pvX = isRight ? PV_X_RIGHT : PV_X_LEFT;
        armStack.translate(pvX, PV_Y, 0f);
        armStack.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
        armStack.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        armStack.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        armStack.translate(0f, ARM_LENGTH, 0f);

        float outX = isRight ? -LETTER_OUTWARD_OFFSET : LETTER_OUTWARD_OFFSET;
        armStack.translate(outX, 0f, LETTER_FORWARD_OFFSET);

        Vector3f worldPos = armStack.peek().getPositionMatrix().transformPosition(new Vector3f(0f, 0f, 0f));
        return new Vec3d(worldPos.x, worldPos.y, worldPos.z);
    }

    // ===================================================
    //  Геометрия букв
    // ===================================================
    private void renderLetterX(Matrix4f m, VertexConsumerProvider.Immediate imm) {
        VertexConsumer buf  = imm.getBuffer(LETTER_LAYER);
        int fc = 0xFFFFFFFF, sc = 0xFFBFC7C7;
        float t = 0.15f;
        extrudedQuad(buf,m,-1f,-1f+t,-1f+t,-1f,1f,1f-t,1f-t,1f,LETTER_DEPTH,fc,sc);
        extrudedQuad(buf,m,-1f,1f-t,-1f+t,1f,1f,-1f+t,1f-t,-1f,LETTER_DEPTH,fc,sc);
    }

    private void renderLetterB(Matrix4f m, VertexConsumerProvider.Immediate imm) {
        VertexConsumer buf = imm.getBuffer(LETTER_LAYER);
        int fc = 0xFFFFFFFF, sc = 0xFFBFC7C7;
        extrudedRect(buf,m,-0.82f,-1.0f,-0.58f, 1.0f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m,-0.82f, 0.76f, 0.34f, 1.0f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m,-0.82f,-0.12f, 0.28f, 0.12f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m,-0.82f,-1.0f,  0.34f,-0.76f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m, 0.18f, 0.12f, 0.42f, 0.76f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m, 0.18f,-0.76f, 0.42f,-0.12f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m, 0.30f, 0.44f, 0.54f, 0.76f,LETTER_DEPTH,fc,sc);
        extrudedRect(buf,m, 0.30f,-0.76f, 0.54f,-0.44f,LETTER_DEPTH,fc,sc);
    }

    private void extrudedRect(VertexConsumer buf, Matrix4f m,
                               float x1,float y1,float x2,float y2,
                               float d,int fc,int sc) {
        extrudedQuad(buf,m,x1,y1,x2,y1,x2,y2,x1,y2,d,fc,sc);
    }

    private void extrudedQuad(VertexConsumer buf, Matrix4f m,
                               float ax,float ay,float bx,float by,
                               float cx,float cy,float dx,float dy,
                               float d,int fc,int sc) {
        quad(buf,m,ax,ay,bx,by,cx,cy,dx,dy, d,fc);
        quad(buf,m,dx,dy,cx,cy,bx,by,ax,ay,-d,fc);
        side(buf,m,ax,ay,bx,by,d,sc);
        side(buf,m,bx,by,cx,cy,d,sc);
        side(buf,m,cx,cy,dx,dy,d,sc);
        side(buf,m,dx,dy,ax,ay,d,sc);
    }

    private void quad(VertexConsumer buf,Matrix4f m,
                      float ax,float ay,float bx,float by,
                      float cx,float cy,float dx,float dy,float z,int c) {
        vtx(buf,m,ax,ay,z,c); vtx(buf,m,bx,by,z,c);
        vtx(buf,m,cx,cy,z,c); vtx(buf,m,dx,dy,z,c);
    }

    private void side(VertexConsumer buf,Matrix4f m,
                      float x1,float y1,float x2,float y2,float d,int c) {
        vtx(buf,m,x1,y1,-d,c); vtx(buf,m,x2,y2,-d,c);
        vtx(buf,m,x2,y2, d,c); vtx(buf,m,x1,y1, d,c);
    }

    private static void vtx(VertexConsumer buf,Matrix4f m,float x,float y,float z,int c) {
        buf.vertex(m,x,y,z).color((c>>16)&0xFF,(c>>8)&0xFF,c&0xFF,(c>>24)&0xFF);
    }

    // ===================================================
    //  Render Pipeline — depth write ON, буквы поверх руки
    // ===================================================
    private static final RenderPipeline LETTER_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                    .withLocation(Identifier.of("strange", "player_anim_letters"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(true)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build()
    );

    private static final RenderLayer LETTER_LAYER = RenderLayer.of(
            "strange_player_anim_letters", BUFFER_SIZE, false, true,
            LETTER_PIPELINE, RenderLayer.MultiPhaseParameters.builder().build(false)
    );
}
