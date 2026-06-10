package ru.strange.client.renderengine.renderers.util;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class KawaseBlur {
    private static final int MAX_LEVELS = 6;
    private static final float MIN_RADIUS = 0.5f;
    private static final float MAX_RADIUS = 150.0f;
    private static final int MAX_SAMPLES = 48;
    private static final float SMALL_RADIUS_THRESHOLD = 50.0f;

    private static final class LevelTarget {
        int fbo;
        int texture;
        int width;
        int height;
    }

    private final ShaderProgram downsampleProgram;
    private final ShaderProgram upsampleProgram;
    private final ShaderProgram smallHProgram;
    private final ShaderProgram smallVProgram;
    private final int intermediateInternalFormat;
    private final int intermediatePixelType;
    private final int downSamplerLoc;
    private final int downTexelSizeLoc;
    private final int downOffsetLoc;
    private final int upSamplerLoc;
    private final int upTexelSizeLoc;
    private final int upOffsetLoc;
    private final int smallHSamplerLoc;
    private final int smallHTexelSizeLoc;
    private final int smallHRadiusLoc;
    private final int smallVSamplerLoc;
    private final int smallVTexelSizeLoc;
    private final int smallVRadiusLoc;
    private final int smallHWeightsLoc;
    private final int smallVWeightsLoc;

    private int quadVao;
    private int quadVbo;

    private final LevelTarget[] pyramid = new LevelTarget[MAX_LEVELS];
    private final LevelTarget fullResolutionTarget = new LevelTarget();
    private final LevelTarget smallTempTarget = new LevelTarget();

    private static KawaseBlur instance;

    public static KawaseBlur getInstance() {
        if (instance == null) {
            instance = new KawaseBlur();
        }
        return instance;
    }

    public float minimumRadius() {
        return MIN_RADIUS;
    }

    public float smallKernelThreshold() {
        return SMALL_RADIUS_THRESHOLD;
    }

    public KawaseBlur() {
        this(GL30.GL_RGBA16F, GL11.GL_FLOAT);
    }

    public KawaseBlur(int intermediateInternalFormat, int intermediatePixelType) {
        if (intermediateInternalFormat == 0) {
            throw new IllegalArgumentException("intermediateInternalFormat must be a valid OpenGL format constant");
        }
        if (intermediatePixelType == 0) {
            throw new IllegalArgumentException("intermediatePixelType must be a valid OpenGL pixel type constant");
        }
        this.downsampleProgram = ShaderProgram.fromResources(
                "/assets/strange/shaders/blur/blur_fullscreen.vert",
                "/assets/strange/shaders/blur/blur_downsample.frag");
        this.upsampleProgram = ShaderProgram.fromResources(
                "/assets/strange/shaders/blur/blur_fullscreen.vert",
                "/assets/strange/shaders/blur/blur_upsample.frag");
        this.smallHProgram = ShaderProgram.fromResources(
                "/assets/strange/shaders/blur/blur_fullscreen.vert",
                "/assets/strange/shaders/blur/blur_small_horizontal.frag");
        this.smallVProgram = ShaderProgram.fromResources(
                "/assets/strange/shaders/blur/blur_fullscreen.vert",
                "/assets/strange/shaders/blur/blur_small_vertical.frag");

        this.intermediateInternalFormat = intermediateInternalFormat;
        this.intermediatePixelType = intermediatePixelType;

        this.downSamplerLoc = downsampleProgram.getUniformLocation("uSource");
        this.downTexelSizeLoc = downsampleProgram.getUniformLocation("uTexelSize");
        this.downOffsetLoc = downsampleProgram.getUniformLocation("uOffset");
        this.upSamplerLoc = upsampleProgram.getUniformLocation("uSource");
        this.upTexelSizeLoc = upsampleProgram.getUniformLocation("uTexelSize");
        this.upOffsetLoc = upsampleProgram.getUniformLocation("uOffset");
        this.smallHSamplerLoc = smallHProgram.getUniformLocation("uSource");
        this.smallHTexelSizeLoc = smallHProgram.getUniformLocation("uTexelSize");
        this.smallHRadiusLoc = smallHProgram.getUniformLocation("uRadius");
        this.smallVSamplerLoc = smallVProgram.getUniformLocation("uSource");
        this.smallVTexelSizeLoc = smallVProgram.getUniformLocation("uTexelSize");
        this.smallVRadiusLoc = smallVProgram.getUniformLocation("uRadius");
        this.smallHWeightsLoc = smallHProgram.getUniformLocation("uWeights");
        this.smallVWeightsLoc = smallVProgram.getUniformLocation("uWeights");

        for (int i = 0; i < pyramid.length; i++) {
            pyramid[i] = new LevelTarget();
        }

        this.quadVao = GL30.glGenVertexArrays();
        this.quadVbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        float[] vertices = new float[]{
                -1f, -1f, 0f, 0f,
                1f, -1f, 1f, 0f,
                -1f, 1f, 0f, 1f,
                1f, 1f, 1f, 1f
        };
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        int stride = 4 * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 2L * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public void destroy() {
        for (LevelTarget level : pyramid) {
            destroyLevel(level);
        }
        destroyLevel(fullResolutionTarget);
        destroyLevel(smallTempTarget);

        if (quadVao != 0) {
            GL30.glDeleteVertexArrays(quadVao);
            quadVao = 0;
        }
        if (quadVbo != 0) {
            GL15.glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }

        downsampleProgram.delete();
        upsampleProgram.delete();
        smallHProgram.delete();
        smallVProgram.delete();
    }

    public int blurFromColorTexture(int sourceTexture, int width, int height, float radiusPx) {
        return blurFromColorTexture(sourceTexture, width, height, radiusPx, true);
    }

    public int blurFromColorTexture(int sourceTexture, int width, int height, float radiusPx, boolean preserveState) {
        if (sourceTexture == 0 || width <= 0 || height <= 0) {
            return 0;
        }

        float effectiveRadius = Math.max(radiusPx, MIN_RADIUS);

        boolean useSmallKernel = effectiveRadius <= SMALL_RADIUS_THRESHOLD;
        int passCount = 0;
        float[] offsets = null;

        if (useSmallKernel) {
            ensureLevel(fullResolutionTarget, width, height);
            ensureLevel(smallTempTarget, width, height);
        } else {
            passCount = determinePassCount(effectiveRadius, width, height);
            if (passCount <= 0) {
                return sourceTexture;
            }
            offsets = buildOffsets(passCount, effectiveRadius);
            ensureIntermediateTargets(width, height, passCount);
            ensureLevel(fullResolutionTarget, width, height);
        }

        GlStateSnapshot snapshot = preserveState ? GlStateSnapshot.push() : null;
        try (TextureUnitGuard unit0 = TextureUnitGuard.capture(0, GL11.GL_TEXTURE_2D)) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL30.glBindVertexArray(quadVao);

            if (useSmallKernel) {
                runSmallRadiusBlur(sourceTexture, width, height, effectiveRadius);
            } else {
                runDownsampleBlur(sourceTexture, width, height, passCount, offsets);
            }

            return fullResolutionTarget.texture;
        } finally {
            GL30.glBindVertexArray(0);
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            if (preserveState && snapshot != null) {
                snapshot.pop();
            }
        }
    }

    private float[] calculateGaussianWeights(float radius) {
        float clampedRadius = Math.min(Math.max(radius, MIN_RADIUS), MAX_RADIUS);
        float sigma = Math.max(clampedRadius * 0.5f, 0.02f);
        float sigma2 = sigma * sigma;
        float twoSigma2 = 2.0f * sigma2;

        float[] weights = new float[MAX_SAMPLES + 1];
        weights[0] = 1.0f;

        float stepFactor = (float) Math.exp(-1.0 / twoSigma2);
        float ratioFactor = (float) Math.exp(-1.0 / sigma2);
        float incremental = stepFactor;
        float current = weights[0];
        float total = weights[0];

        int samples = (int) Math.ceil(clampedRadius) + 1;
        samples = Math.min(samples, MAX_SAMPLES);

        for (int i = 0; i < samples; i++) {
            current *= incremental;
            weights[i + 1] = current;
            total += 2.0f * current;
            incremental *= ratioFactor;
        }

        float normalization = 1.0f / total;
        for (int i = 0; i <= samples; i++) {
            weights[i] *= normalization;
        }
        return weights;
    }

    private void runSmallRadiusBlur(int sourceTexture, int width, int height, float effectiveRadius) {
        // Calculate weights on CPU
        float[] weights = calculateGaussianWeights(effectiveRadius);

        // Horizontal pass
        smallHProgram.use();
        if (smallHSamplerLoc >= 0) {
            GL20.glUniform1i(smallHSamplerLoc, 0);
        }
        if (smallHTexelSizeLoc >= 0) {
            GL20.glUniform2f(smallHTexelSizeLoc, 1.0f / Math.max(1, width), 1.0f / Math.max(1, height));
        }
        if (smallHRadiusLoc >= 0) {
            GL20.glUniform1f(smallHRadiusLoc, effectiveRadius);
        }
        if (smallHWeightsLoc >= 0) {
            GL20.glUniform1fv(smallHWeightsLoc, weights);
        }
        bindTarget(smallTempTarget);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture);
        drawQuad();

        // Vertical pass
        smallVProgram.use();
        if (smallVSamplerLoc >= 0) {
            GL20.glUniform1i(smallVSamplerLoc, 0);
        }
        if (smallVTexelSizeLoc >= 0) {
            GL20.glUniform2f(smallVTexelSizeLoc, 1.0f / Math.max(1, width), 1.0f / Math.max(1, height));
        }
        if (smallVRadiusLoc >= 0) {
            GL20.glUniform1f(smallVRadiusLoc, effectiveRadius);
        }
        if (smallVWeightsLoc >= 0) {
            GL20.glUniform1fv(smallVWeightsLoc, weights);
        }
        bindTarget(fullResolutionTarget);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, smallTempTarget.texture);
        drawQuad();
    }

    private void runDownsampleBlur(int sourceTexture, int width, int height, int passCount, float[] offsets) {
        if (offsets == null || offsets.length != passCount) {
            throw new IllegalArgumentException("offsets length must match passCount");
        }

        int currentTexture = sourceTexture;
        int currentWidth = width;
        int currentHeight = height;

        downsampleProgram.use();
        if (downSamplerLoc >= 0) {
            GL20.glUniform1i(downSamplerLoc, 0);
        }
        for (int i = 0; i < passCount; i++) {
            LevelTarget target = pyramid[i];
            bindTarget(target);
            if (downTexelSizeLoc >= 0) {
                GL20.glUniform2f(downTexelSizeLoc,
                        1.0f / Math.max(1, currentWidth),
                        1.0f / Math.max(1, currentHeight));
            }
            if (downOffsetLoc >= 0) {
                GL20.glUniform1f(downOffsetLoc, offsets[i]);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
            drawQuad();
            currentTexture = target.texture;
            currentWidth = target.width;
            currentHeight = target.height;
        }

        upsampleProgram.use();
        if (upSamplerLoc >= 0) {
            GL20.glUniform1i(upSamplerLoc, 0);
        }
        for (int i = passCount - 2; i >= 0; i--) {
            LevelTarget target = pyramid[i];
            bindTarget(target);
            if (upTexelSizeLoc >= 0) {
                GL20.glUniform2f(upTexelSizeLoc,
                        1.0f / Math.max(1, currentWidth),
                        1.0f / Math.max(1, currentHeight));
            }
            if (upOffsetLoc >= 0) {
                GL20.glUniform1f(upOffsetLoc, offsets[i]);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
            drawQuad();
            currentTexture = target.texture;
            currentWidth = target.width;
            currentHeight = target.height;
        }

        bindTarget(fullResolutionTarget);
        if (upTexelSizeLoc >= 0) {
            GL20.glUniform2f(upTexelSizeLoc,
                    1.0f / Math.max(1, currentWidth),
                    1.0f / Math.max(1, currentHeight));
        }
        if (upOffsetLoc >= 0) {
            GL20.glUniform1f(upOffsetLoc, offsets.length > 0 ? offsets[0] : MIN_RADIUS);
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
        drawQuad();
    }

    private void drawQuad() {
        GL11.glDrawArrays(GL11.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void bindTarget(LevelTarget target) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.fbo);
        GL11.glViewport(0, 0, target.width, target.height);
        GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
    }

    private void ensureIntermediateTargets(int baseWidth, int baseHeight, int passCount) {
        for (int i = 0; i < passCount; i++) {
            int divisor = 1 << (i + 1);
            int w = Math.max(1, baseWidth / divisor);
            int h = Math.max(1, baseHeight / divisor);
            ensureLevel(pyramid[i], w, h);
        }
    }

    private void ensureLevel(LevelTarget target, int width, int height) {
        if (target.texture != 0 && (target.width != width || target.height != height)) {
            GL11.glDeleteTextures(target.texture);
            GL30.glDeleteFramebuffers(target.fbo);
            target.texture = 0;
            target.fbo = 0;
        }
        if (target.texture == 0) {
            target.texture = createRenderTexture(width, height);
            target.fbo = createFramebuffer(target.texture);
        }
        target.width = width;
        target.height = height;
    }

    private void destroyLevel(LevelTarget target) {
        if (target == null) {
            return;
        }
        if (target.texture != 0) {
            GL11.glDeleteTextures(target.texture);
            target.texture = 0;
        }
        if (target.fbo != 0) {
            GL30.glDeleteFramebuffers(target.fbo);
            target.fbo = 0;
        }
        target.width = 0;
        target.height = 0;
    }

    private int createRenderTexture(int width, int height) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, intermediateInternalFormat, width, height, 0,
                GL11.GL_RGBA, intermediatePixelType, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return tex;
    }

    private int createFramebuffer(int texture) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(fbo);
            GL11.glDeleteTextures(texture);
            throw new IllegalStateException("Blur framebuffer incomplete: status=" + status);
        }
        return fbo;
    }

    private int determinePassCount(float radiusPx, int width, int height) {
        int available = 0;
        int w = width;
        int h = height;
        while (available < MAX_LEVELS && (w > 1 || h > 1)) {
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
            available++;
            if (w == 1 && h == 1) {
                break;
            }
        }
        if (available == 0) {
            available = 1;
        }
        // Logarithmic scale for smooth transitions
        int desired = Math.max(1, (int) Math.ceil(Math.sqrt(radiusPx / 2f)));
        return Math.min(available, desired);
    }

    private float[] buildOffsets(int passCount, float radiusPx) {
        float[] offsets = new float[passCount];
        for (int i = 0; i < passCount; i++) {
            float levelScale = 1f / (float) (1 << i);
            float baseOffset = radiusPx / (float) passCount;
            offsets[i] = Math.max(MIN_RADIUS, baseOffset * levelScale * 2f + 0.5f);
        }
        return offsets;
    }

    // --- Inner Classes ---

    private static final class ShaderProgram {
        private final int programId;

        public ShaderProgram(String vertexSource, String fragmentSource) {
            int vs = compile(GL20.GL_VERTEX_SHADER, vertexSource);
            int fs = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vs);
            GL20.glAttachShader(programId, fs);
            GL20.glLinkProgram(programId);

            try (var stack = MemoryStack.stackPush()) {
                IntBuffer status = stack.mallocInt(1);
                GL20.glGetProgramiv(programId, GL20.GL_LINK_STATUS, status);
                if (status.get(0) == 0) {
                    String log = GL20.glGetProgramInfoLog(programId);
                    GL20.glDeleteShader(vs);
                    GL20.glDeleteShader(fs);
                    GL20.glDeleteProgram(programId);
                    throw new IllegalStateException("Program link failed: " + log);
                }
            }

            GL20.glDetachShader(programId, vs);
            GL20.glDetachShader(programId, fs);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
        }

        public static ShaderProgram fromResources(String vertexPath, String fragmentPath) {
            String vs = readText(vertexPath);
            String fs = readText(fragmentPath);
            return new ShaderProgram(vs, fs);
        }

        private static int compile(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            try (var stack = MemoryStack.stackPush()) {
                IntBuffer status = stack.mallocInt(1);
                GL20.glGetShaderiv(shader, GL20.GL_COMPILE_STATUS, status);
                if (status.get(0) == 0) {
                    String log = GL20.glGetShaderInfoLog(shader);
                    GL20.glDeleteShader(shader);
                    throw new IllegalStateException("Shader compile failed: " + log);
                }
            }
            return shader;
        }

        public void use() {
            GL20.glUseProgram(programId);
        }

        public void delete() {
            GL20.glDeleteProgram(programId);
        }

        public int getUniformLocation(String name) {
            return GL20.glGetUniformLocation(programId, name);
        }

        private static String readText(String path) {
            try (InputStream in = KawaseBlur.class.getResourceAsStream(path)) {
                if (in == null) throw new IllegalStateException("Resource not found: " + path);
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append('\n');
                    }
                    return sb.toString();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to read resource: " + path, e);
            }
        }
    }

    private static final class GlStateSnapshot {
        public int framebuffer; // legacy combined binding
        public int drawFramebuffer;
        public int readFramebuffer;
        public final int[] viewport = new int[4];
        public boolean scissorEnabled;
        public final int[] scissorBox = new int[4];
        public boolean depthTestEnabled;
        public boolean cullFaceEnabled;
        public boolean blendEnabled;
        public boolean framebufferSrgbEnabled;
        public int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
        public boolean colorMaskR, colorMaskG, colorMaskB, colorMaskA;
        public boolean depthMask;
        public int program;
        public int vao;
        public int arrayBuffer;
        public int elementArrayBuffer;
        public int activeTexture;
        public int texture2D;
        public int unpackAlignment;
        public int readBuffer;
        public int drawBuffer;

        private GlStateSnapshot() {}

        public static GlStateSnapshot push() {
            GlStateSnapshot s = new GlStateSnapshot();
            try (var stack = MemoryStack.stackPush()) {
                IntBuffer buf4 = stack.mallocInt(4);
                IntBuffer buf1 = stack.mallocInt(1);

                s.framebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
                s.drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
                s.readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);

                GL11.glGetIntegerv(GL11.GL_VIEWPORT, buf4);
                s.viewport[0] = buf4.get(0);
                s.viewport[1] = buf4.get(1);
                s.viewport[2] = buf4.get(2);
                s.viewport[3] = buf4.get(3);

                s.scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, buf4);
                s.scissorBox[0] = buf4.get(0);
                s.scissorBox[1] = buf4.get(1);
                s.scissorBox[2] = buf4.get(2);
                s.scissorBox[3] = buf4.get(3);

                s.depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                s.cullFaceEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                s.blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
                s.framebufferSrgbEnabled = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);

                GL11.glGetIntegerv(GL14.GL_BLEND_SRC_RGB, buf1); s.blendSrcRGB = buf1.get(0);
                GL11.glGetIntegerv(GL14.GL_BLEND_DST_RGB, buf1); s.blendDstRGB = buf1.get(0);
                GL11.glGetIntegerv(GL14.GL_BLEND_SRC_ALPHA, buf1); s.blendSrcAlpha = buf1.get(0);
                GL11.glGetIntegerv(GL14.GL_BLEND_DST_ALPHA, buf1); s.blendDstAlpha = buf1.get(0);

                s.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
                s.vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
                s.arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
                s.elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
                s.activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
                s.texture2D = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                s.unpackAlignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
                s.readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
                s.drawBuffer = GL11.glGetInteger(GL11.GL_DRAW_BUFFER);

                ByteBuffer cm = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, cm);
                s.colorMaskR = cm.get(0) != 0;
                s.colorMaskG = cm.get(1) != 0;
                s.colorMaskB = cm.get(2) != 0;
                s.colorMaskA = cm.get(3) != 0;

                ByteBuffer dm = stack.malloc(1);
                GL11.glGetBooleanv(GL11.GL_DEPTH_WRITEMASK, dm);
                s.depthMask = dm.get(0) != 0;
            }
            return s;
        }

        public void pop() {
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);

            GL13.glActiveTexture(activeTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture2D);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, unpackAlignment);

            setEnabled(GL11.GL_SCISSOR_TEST, scissorEnabled);
            setEnabled(GL11.GL_DEPTH_TEST, depthTestEnabled);
            setEnabled(GL11.GL_CULL_FACE, cullFaceEnabled);
            setEnabled(GL11.GL_BLEND, blendEnabled);
            setEnabled(GL30.GL_FRAMEBUFFER_SRGB, framebufferSrgbEnabled);
            GL14.glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);

            GL11.glColorMask(colorMaskR, colorMaskG, colorMaskB, colorMaskA);
            GL11.glDepthMask(depthMask);

            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);

            GL11.glReadBuffer(readBuffer);
            GL11.glDrawBuffer(drawBuffer);
        }

        private static void setEnabled(int cap, boolean enabled) {
            if (enabled) GL11.glEnable(cap); else GL11.glDisable(cap);
        }
    }

    private static final class TextureUnitGuard implements AutoCloseable {
        private final int unit;
        private final int previousActiveUnit;
        private final int[] targets;
        private final int[] bindings;
        private boolean closed;

        private TextureUnitGuard(int unit, int previousActiveUnit, int[] targets, int[] bindings) {
            this.unit = unit;
            this.previousActiveUnit = previousActiveUnit;
            this.targets = targets;
            this.bindings = bindings;
        }

        static TextureUnitGuard capture(int unit, int... requestedTargets) {
            int previousActive = Math.max(0, GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE) - GL13.GL_TEXTURE0);
            int[] targets = deduplicateTargets(requestedTargets);
            int[] bindings = new int[targets.length];
            if (targets.length > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                for (int i = 0; i < targets.length; i++) {
                    bindings[i] = GL11.glGetInteger(bindingEnumForTarget(targets[i]));
                }
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + previousActive);
            }
            return new TextureUnitGuard(unit, previousActive, targets, bindings);
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (targets.length > 0) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
                for (int i = 0; i < targets.length; i++) {
                    GL11.glBindTexture(targets[i], bindings[i]);
                }
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + previousActiveUnit);
        }

        private static int[] deduplicateTargets(int[] requestedTargets) {
            if (requestedTargets == null || requestedTargets.length == 0) return new int[0];
            int[] copy = Arrays.copyOf(requestedTargets, requestedTargets.length);
            int count = 0;
            for (int value : copy) {
                if (value <= 0) continue;
                boolean exists = false;
                for (int i = 0; i < count; i++) {
                    if (copy[i] == value) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) copy[count++] = value;
            }
            return Arrays.copyOf(copy, count);
        }

        private static int bindingEnumForTarget(int target) {
            switch (target) {
                case GL11.GL_TEXTURE_1D: return GL11.GL_TEXTURE_BINDING_1D;
                case GL11.GL_TEXTURE_2D: return GL11.GL_TEXTURE_BINDING_2D;
                case GL12.GL_TEXTURE_3D: return GL12.GL_TEXTURE_BINDING_3D;
                case GL13.GL_TEXTURE_CUBE_MAP: return GL13.GL_TEXTURE_BINDING_CUBE_MAP;
                case GL30.GL_TEXTURE_1D_ARRAY: return GL30.GL_TEXTURE_BINDING_1D_ARRAY;
                case GL30.GL_TEXTURE_2D_ARRAY: return GL30.GL_TEXTURE_BINDING_2D_ARRAY;
                case GL31.GL_TEXTURE_RECTANGLE: return GL31.GL_TEXTURE_BINDING_RECTANGLE;
                case GL31.GL_TEXTURE_BUFFER: return GL31.GL_TEXTURE_BINDING_BUFFER;
                case GL40.GL_TEXTURE_CUBE_MAP_ARRAY: return GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY;
                default: return GL11.GL_TEXTURE_BINDING_2D;
            }
        }
    }
}
