package ru.strange.client.renderengine.renderers.util;

import org.lwjgl.opengl.*;

import java.nio.IntBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

public final class GlState {

    public static final class Snapshot {
        public int framebuffer;
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
    }

    private GlState() {}

    public static Snapshot push() {
        Snapshot s = new Snapshot();
        try (var stack = stackPush()) {
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

            java.nio.ByteBuffer cm = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, cm);
            s.colorMaskR = cm.get(0) != 0;
            s.colorMaskG = cm.get(1) != 0;
            s.colorMaskB = cm.get(2) != 0;
            s.colorMaskA = cm.get(3) != 0;

            java.nio.ByteBuffer dm = stack.malloc(1);
            GL11.glGetBooleanv(GL11.GL_DEPTH_WRITEMASK, dm);
            s.depthMask = dm.get(0) != 0;
        }
        return s;
    }

    public static void pop(Snapshot s) {
        if (s == null) return;

        GL20.glUseProgram(s.program);
        GL30.glBindVertexArray(s.vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, s.arrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, s.elementArrayBuffer);

        GL13.glActiveTexture(s.activeTexture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, s.texture2D);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, s.unpackAlignment);

        setEnabled(GL11.GL_SCISSOR_TEST, s.scissorEnabled);
        setEnabled(GL11.GL_DEPTH_TEST, s.depthTestEnabled);
        setEnabled(GL11.GL_CULL_FACE, s.cullFaceEnabled);
        setEnabled(GL11.GL_BLEND, s.blendEnabled);
        setEnabled(GL30.GL_FRAMEBUFFER_SRGB, s.framebufferSrgbEnabled);
        GL14.glBlendFuncSeparate(s.blendSrcRGB, s.blendDstRGB, s.blendSrcAlpha, s.blendDstAlpha);

        GL11.glColorMask(s.colorMaskR, s.colorMaskG, s.colorMaskB, s.colorMaskA);
        GL11.glDepthMask(s.depthMask);

        GL11.glViewport(s.viewport[0], s.viewport[1], s.viewport[2], s.viewport[3]);
        GL11.glScissor(s.scissorBox[0], s.scissorBox[1], s.scissorBox[2], s.scissorBox[3]);

        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, s.drawFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, s.readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, s.framebuffer);

        GL11.glReadBuffer(s.readBuffer);
        GL11.glDrawBuffer(s.drawBuffer);
    }

    private static void setEnabled(int cap, boolean enabled) {
        if (enabled) GL11.glEnable(cap); else GL11.glDisable(cap);
    }
}

