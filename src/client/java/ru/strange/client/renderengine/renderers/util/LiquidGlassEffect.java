package ru.strange.client.renderengine.renderers.util;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import ru.strange.client.Strange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class LiquidGlassEffect {

    private static LiquidGlassEffect instance;
    private final LegacyBlurProcessor screenBlur = new LegacyBlurProcessor();

    private int programId;
    private int quadVao;
    private int quadVbo;

    private int uModelViewMat;
    private int uProjMat;
    private int uRect;
    private int uScreenTex;
    private int uBlurTex;
    private int uSize;
    private int uRadius;
    private int uScreenUV;
    private int uTexelSize;
    private int uTintColor;
    private int uAlpha;

    private boolean initialized;

    public static LiquidGlassEffect getInstance() {
        if (instance == null) {
            instance = new LiquidGlassEffect();
        }
        return instance;
    }

    private void init() {
        if (initialized) {
            return;
        }

        try {
            String vertex = readResource("/assets/strange/shaders/liquidglass/liquidglass.vert");
            String fragment = readResource("/assets/strange/shaders/liquidglass/liquidglass.frag");
            programId = createProgram(vertex, fragment);

            uModelViewMat = GL20.glGetUniformLocation(programId, "uModelViewMat");
            uProjMat = GL20.glGetUniformLocation(programId, "uProjMat");
            uRect = GL20.glGetUniformLocation(programId, "uRect");
            uScreenTex = GL20.glGetUniformLocation(programId, "uScreenTex");
            uBlurTex = GL20.glGetUniformLocation(programId, "uBlurTex");
            uSize = GL20.glGetUniformLocation(programId, "uSize");
            uRadius = GL20.glGetUniformLocation(programId, "uRadius");
            uScreenUV = GL20.glGetUniformLocation(programId, "uScreenUV");
            uTexelSize = GL20.glGetUniformLocation(programId, "uTexelSize");
            uTintColor = GL20.glGetUniformLocation(programId, "uTintColor");
            uAlpha = GL20.glGetUniformLocation(programId, "uAlpha");

            quadVao = GL30.glGenVertexArrays();
            quadVbo = GL15.glGenBuffers();
            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
            float[] vertices = new float[] {
                    -1f, -1f, 0f, 0f,
                     1f, -1f, 1f, 0f,
                    -1f,  1f, 0f, 1f,
                     1f,  1f, 1f, 1f
            };
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
            int stride = 4 * Float.BYTES;
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
            GL20.glEnableVertexAttribArray(1);
            GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            initialized = true;
        } catch (Exception e) {
            Strange.LOGGER.error("Failed to initialize LiquidGlassEffect", e);
        }
    }

    public void draw(DrawContext ctx, float x, float y, float width, float height,
                     float radius, int tintColor, float blurRadius, float alpha) {
        draw(ctx, x, y, width, height, radius, radius, radius, radius, tintColor, blurRadius, alpha);
    }

    public void draw(DrawContext ctx, float x, float y, float width, float height,
                     float r1, float r2, float r3, float r4,
                     int tintColor, float blurRadius, float alpha) {
        init();
        if (!initialized) {
            return;
        }

        float clampedAlpha = Math.max(0f, Math.min(1f, alpha));
        if (clampedAlpha <= 0.0f || width <= 0.0f || height <= 0.0f) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return;
        }

        Framebuffer framebuffer = mc.getFramebuffer();
        if (framebuffer == null) {
            return;
        }

        int screenTexId = extractColorTextureId(framebuffer);
        if (screenTexId == 0) {
            return;
        }

        int fbW = framebuffer.textureWidth;
        int fbH = framebuffer.textureHeight;
        float scaledW = mc.getWindow().getScaledWidth();
        float scaledH = mc.getWindow().getScaledHeight();
        float scaleX = (float) fbW / scaledW;
        float scaleY = (float) fbH / scaledH;

        float fx = x * scaleX;
        float fy = y * scaleY;
        float fWidth = width * scaleX;
        float fHeight = height * scaleY;
        fy = fbH - fy - fHeight;

        float u0 = fx / fbW;
        float v0 = fy / fbH;
        float u1 = (fx + fWidth) / fbW;
        float v1 = (fy + fHeight) / fbH;

        float blurProgress = Math.max(0.0f, Math.min(1.0f, (blurRadius - 1.0f) / 47.0f));
        float effectiveBlur = 9.5f + (float) Math.pow(blurProgress, 0.82f) * 28.0f;
        float radiusPx = Math.max(effectiveBlur * Math.max(scaleX, scaleY), KawaseBlur.getInstance().minimumRadius());
        screenBlur.prepareScreenBlur(fbW, fbH, radiusPx);
        int blurTexId = screenBlur.getPreparedBlurTex();
        if (blurTexId == 0) {
            blurTexId = screenTexId;
        }

        float tintR = ((tintColor >> 16) & 0xFF) / 255f;
        float tintG = ((tintColor >> 8) & 0xFF) / 255f;
        float tintB = (tintColor & 0xFF) / 255f;

        GlState.Snapshot snapshot = GlState.push();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL20.glUseProgram(programId);

            float[] ortho = {
                    2f / scaledW, 0f, 0f, 0f,
                    0f, -2f / scaledH, 0f, 0f,
                    0f, 0f, -1f, 0f,
                    -1f, 1f, 0f, 1f
            };
            float[] identity = {
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f
            };

            GL20.glUniformMatrix4fv(uProjMat, false, ortho);
            GL20.glUniformMatrix4fv(uModelViewMat, false, identity);
            GL20.glUniform4f(uRect, x, y, width, height);
            GL20.glUniform2f(uSize, width, height);
            GL20.glUniform4f(uRadius, r1, r2, r3, r4);
            GL20.glUniform4f(uScreenUV, u0, v0, u1, v1);
            GL20.glUniform2f(uTexelSize, 1.0f / fbW, 1.0f / fbH);
            GL20.glUniform3f(uTintColor, tintR, tintG, tintB);
            GL20.glUniform1f(uAlpha, clampedAlpha);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, screenTexId);
            GL20.glUniform1i(uScreenTex, 0);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, blurTexId);
            GL20.glUniform1i(uBlurTex, 1);

            GL30.glBindVertexArray(quadVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
        } finally {
            GlState.pop(snapshot);
        }
    }

    private static int extractColorTextureId(Framebuffer framebuffer) {
        GpuTexture colorAttachment = framebuffer.getColorAttachment();
        if (colorAttachment instanceof GlTexture gl) {
            return gl.getGlId();
        }
        try {
            var method = framebuffer.getClass().getMethod("getColorAttachmentView");
            Object viewObj = method.invoke(framebuffer);
            if (viewObj instanceof com.mojang.blaze3d.textures.GpuTextureView view) {
                GpuTexture texture = view.texture();
                if (texture instanceof GlTexture glTexture) {
                    return glTexture.getGlId();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vs = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
        int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer status = stack.mallocInt(1);
            GL20.glGetProgramiv(program, GL20.GL_LINK_STATUS, status);
            if (status.get(0) == 0) {
                String log = GL20.glGetProgramInfoLog(program);
                GL20.glDeleteShader(vs);
                GL20.glDeleteShader(fs);
                GL20.glDeleteProgram(program);
                throw new IllegalStateException("Liquid glass link failed: " + log);
            }
        }

        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer status = stack.mallocInt(1);
            GL20.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
            if (status.get(0) == 0) {
                String log = GL20.glGetShaderInfoLog(shader);
                GL20.glDeleteShader(shader);
                throw new IllegalStateException("Liquid glass shader compile failed: " + log);
            }
        }

        return shader;
    }

    private static String readResource(String path) {
        try (InputStream inputStream = LiquidGlassEffect.class.getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("Resource not found: " + path);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }
}