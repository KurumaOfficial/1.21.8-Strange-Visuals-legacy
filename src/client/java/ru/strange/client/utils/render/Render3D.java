package ru.strange.client.utils.render;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix4f;

public class Render3D {
    /**
     * Draws a filled box with gradient colors on each face.
     * Used in ESP and ItemESP modules.
     *
     * @param buffer The vertex consumer buffer
     * @param matrix The transformation matrix
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     * @param colors Array of 4 gradient colors (one per corner)
     * @param fillAlpha The alpha value for the fill (0-255)
     */
    public static void drawBoxFill(VertexConsumer buffer, Matrix4f matrix,
                                   double minX, double minY, double minZ,
                                   double maxX, double maxY, double maxZ,
                                   int[] colors, int fillAlpha) {
        int c0 = RenderUtil.ColorUtil.replAlpha(colors[0], fillAlpha);
        int c1 = RenderUtil.ColorUtil.replAlpha(colors[1], fillAlpha);
        int c2 = RenderUtil.ColorUtil.replAlpha(colors[2], fillAlpha);
        int c3 = RenderUtil.ColorUtil.replAlpha(colors[3], fillAlpha);

        int r0 = (c0 >> 16) & 0xFF;
        int g0 = (c0 >> 8) & 0xFF;
        int b0 = c0 & 0xFF;
        int a0 = (c0 >> 24) & 0xFF;
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int a1 = (c1 >> 24) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF;
        int r3 = (c3 >> 16) & 0xFF;
        int g3 = (c3 >> 8) & 0xFF;
        int b3 = c3 & 0xFF;
        int a3 = (c3 >> 24) & 0xFF;

        // Bottom face
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r0, g0, b0, a0);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r1, g1, b1, a1);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r2, g2, b2, a2);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r3, g3, b3, a3);

        // Top face
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r0, g0, b0, a0);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r3, g3, b3, a3);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r2, g2, b2, a2);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r1, g1, b1, a1);

        // Front face (maxZ)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r3, g3, b3, a3);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r2, g2, b2, a2);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r2, g2, b2, a2);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r3, g3, b3, a3);

        // Back face (minZ)
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r1, g1, b1, a1);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r0, g0, b0, a0);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r0, g0, b0, a0);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r1, g1, b1, a1);

        // Left face (minX)
        buffer.vertex(matrix, (float) minX, (float) minY, (float) minZ).color(r0, g0, b0, a0);
        buffer.vertex(matrix, (float) minX, (float) minY, (float) maxZ).color(r3, g3, b3, a3);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) maxZ).color(r3, g3, b3, a3);
        buffer.vertex(matrix, (float) minX, (float) maxY, (float) minZ).color(r0, g0, b0, a0);

        // Right face (maxX)
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) maxZ).color(r2, g2, b2, a2);
        buffer.vertex(matrix, (float) maxX, (float) minY, (float) minZ).color(r1, g1, b1, a1);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) minZ).color(r1, g1, b1, a1);
        buffer.vertex(matrix, (float) maxX, (float) maxY, (float) maxZ).color(r2, g2, b2, a2);
    }

    /**
     * Draws a box outline with dashed lines and gradient colors.
     * Used in ESP and ItemESP modules.
     *
     * @param buffer The vertex consumer buffer
     * @param matrix The transformation matrix
     * @param minX Minimum X coordinate
     * @param minY Minimum Y coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxY Maximum Y coordinate
     * @param maxZ Maximum Z coordinate
     * @param colors Array of 4 gradient colors (one per corner)
     * @param outlineAlpha The alpha value for the outline (0-255)
     * @param dashLength The length of each dash
     * @param gapLength The length of each gap between dashes
     */
    public static void drawBoxOutline(VertexConsumer buffer, Matrix4f matrix,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ,
                                      int[] colors, int outlineAlpha, double dashLength, double gapLength) {
        int c0 = RenderUtil.ColorUtil.replAlpha(colors[0], outlineAlpha);
        int c1 = RenderUtil.ColorUtil.replAlpha(colors[1], outlineAlpha);
        int c2 = RenderUtil.ColorUtil.replAlpha(colors[2], outlineAlpha);
        int c3 = RenderUtil.ColorUtil.replAlpha(colors[3], outlineAlpha);

        // Bottom face edges
        drawDashedLineSegment(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, c0, c1, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, c1, c2, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, c2, c3, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, c3, c0, dashLength, gapLength);

        // Top face edges
        drawDashedLineSegment(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, c0, c1, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, c1, c2, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, c2, c3, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, c3, c0, dashLength, gapLength);

        // Vertical edges
        drawDashedLineSegment(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, c0, c0, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, c1, c1, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, c2, c2, dashLength, gapLength);
        drawDashedLineSegment(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, c3, c3, dashLength, gapLength);
    }

    /**
     * Draws a dashed line segment with gradient color interpolation.
     * Used in ESP and ItemESP modules for box outlines.
     *
     * @param buffer The vertex consumer buffer
     * @param matrix The transformation matrix
     * @param x1 Start X coordinate
     * @param y1 Start Y coordinate
     * @param z1 Start Z coordinate
     * @param x2 End X coordinate
     * @param y2 End Y coordinate
     * @param z2 End Z coordinate
     * @param color1 Start color
     * @param color2 End color
     * @param dashLength The length of each dash
     * @param gapLength The length of each gap between dashes
     */
    public static void drawDashedLineSegment(VertexConsumer buffer, Matrix4f matrix,
                                             double x1, double y1, double z1,
                                             double x2, double y2, double z2,
                                             int color1, int color2, double dashLength, double gapLength) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (length < 0.001) return;

        double unitX = dx / length;
        double unitY = dy / length;
        double unitZ = dz / length;

        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int a1 = (color1 >> 24) & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF;

        double segmentLength = dashLength + gapLength;
        double currentPos = 0;

        while (currentPos < length) {
            double dashStart = currentPos;
            double dashEnd = Math.min(currentPos + dashLength, length);

            if (dashEnd > dashStart) {
                double startX = x1 + unitX * dashStart;
                double startY = y1 + unitY * dashStart;
                double startZ = z1 + unitZ * dashStart;

                double endX = x1 + unitX * dashEnd;
                double endY = y1 + unitY * dashEnd;
                double endZ = z1 + unitZ * dashEnd;

                double t = dashStart / length;
                int r = (int) (r1 + (r2 - r1) * t);
                int g = (int) (g1 + (g2 - g1) * t);
                int b = (int) (b1 + (b2 - b1) * t);
                int a = (int) (a1 + (a2 - a1) * t);

                buffer.vertex(matrix, (float) startX, (float) startY, (float) startZ).color(r, g, b, a);

                t = dashEnd / length;
                r = (int) (r1 + (r2 - r1) * t);
                g = (int) (g1 + (g2 - g1) * t);
                b = (int) (b1 + (b2 - b1) * t);
                a = (int) (a1 + (a2 - a1) * t);

                buffer.vertex(matrix, (float) endX, (float) endY, (float) endZ).color(r, g, b, a);
            }

            currentPos += segmentLength;
        }
    }

    /**
     * Draws a simple block box with fill only (solid color).
     * Used in BlockESP for rendering block entities.
     *
     * @param buffer The vertex consumer buffer (QUADS)
     * @param matrix The transformation matrix
     * @param minX   Minimum X coordinate (relative to camera)
     * @param minY   Minimum Y coordinate (relative to camera)
     * @param minZ   Minimum Z coordinate (relative to camera)
     * @param maxX   Maximum X coordinate (relative to camera)
     * @param maxY   Maximum Y coordinate (relative to camera)
     * @param maxZ   Maximum Z coordinate (relative to camera)
     * @param color  The color to render the box
     */
    public static void drawBlockBox(VertexConsumer buffer, Matrix4f matrix,
                                    float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ, int color) {

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        // Top face
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);

        // Bottom face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);

        // Front face
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);

        // Back face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);

        // Left face
        buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);

        // Right face
        buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
        buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
    }


    /**
     * Draws a box with separate outline and fill colors.
     * Used in TargetESP for axis-aligned boxes.
     *
     * @param buffer    The vertex consumer buffer (QUADS)
     * @param matrix    The transformation matrix
     * @param minX      Minimum X coordinate
     * @param minY      Minimum Y coordinate
     * @param minZ      Minimum Z coordinate
     * @param maxX      Maximum X coordinate
     * @param maxY      Maximum Y coordinate
     * @param maxZ      Maximum Z coordinate
     * @param colorOut  The outline color
     * @param colorFill The fill color
     */
    public static void drawAxisBox(VertexConsumer buffer, Matrix4f matrix,
                                   float minX, float minY, float minZ,
                                   float maxX, float maxY, float maxZ,
                                   int colorOut, int colorFill) {
        int rOut = (colorOut >> 16) & 0xFF;
        int gOut = (colorOut >> 8) & 0xFF;
        int bOut = colorOut & 0xFF;
        int aOut = (colorOut >> 24) & 0xFF;

        int rFill = (colorFill >> 16) & 0xFF;
        int gFill = (colorFill >> 8) & 0xFF;
        int bFill = colorFill & 0xFF;
        int aFill = (colorFill >> 24) & 0xFF;

        // Top face (outline)
        buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);

        // Bottom face (outline)
        buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);

        // Front face (outline)
        buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);

        // Back face (outline)
        buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);

        // Left face (outline)
        buffer.vertex(matrix, minX, minY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, maxY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, minX, minY, maxZ).color(rOut, gOut, bOut, aOut);

        // Right face (outline)
        buffer.vertex(matrix, maxX, minY, minZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rOut, gOut, bOut, aOut);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rOut, gOut, bOut, aOut);

        // Fill faces
        // Top face (fill)
        buffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);

        // Bottom face (fill)
        buffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);

        // Front face (fill)
        buffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);

        // Back face (fill)
        buffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);

        // Left face (fill)
        buffer.vertex(matrix, minX, minY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, minY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, minX, maxY, minZ).color(rFill, gFill, bFill, aFill);

        // Right face (fill)
        buffer.vertex(matrix, maxX, minY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, minZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, maxY, maxZ).color(rFill, gFill, bFill, aFill);
        buffer.vertex(matrix, maxX, minY, maxZ).color(rFill, gFill, bFill, aFill);
    }

    /**
     * Draws a cube with solid faces.
     * Used in Svetych for rendering floating cubes.
     *
     * @param buffer The vertex consumer buffer (QUADS)
     * @param matrix The transformation matrix
     * @param color  The color to render the cube
     * @param size   The size of the cube
     */
    public static void drawCube(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
        float half = size / 2f;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        int a = (color >> 24) & 255;

        // Top face
        buffer.vertex(matrix, -half, half, -half).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, half, -half).color(r, g, b, a);

        // Bottom face
        buffer.vertex(matrix, -half, -half, -half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, -half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, half).color(r, g, b, a);
        buffer.vertex(matrix, -half, -half, half).color(r, g, b, a);

        // Front face
        buffer.vertex(matrix, -half, half, half).color(r, g, b, a);
        buffer.vertex(matrix, -half, -half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, half, half).color(r, g, b, a);

        // Back face
        buffer.vertex(matrix, -half, half, -half).color(r, g, b, a);
        buffer.vertex(matrix, half, half, -half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, -half).color(r, g, b, a);
        buffer.vertex(matrix, -half, -half, -half).color(r, g, b, a);

        // Left face
        buffer.vertex(matrix, -half, half, -half).color(r, g, b, a);
        buffer.vertex(matrix, -half, -half, -half).color(r, g, b, a);
        buffer.vertex(matrix, -half, -half, half).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, half).color(r, g, b, a);

        // Right face
        buffer.vertex(matrix, half, half, -half).color(r, g, b, a);
        buffer.vertex(matrix, half, half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, half).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, -half).color(r, g, b, a);
    }

    /**
     * Draws cube outline (edges) with lines.
     * Used in Svetych and TargetESP for rendering cube edges.
     *
     * @param buffer The vertex consumer buffer (LINES)
     * @param matrix The transformation matrix
     * @param color  The color to render the lines
     * @param size   The size of the cube
     */
    public static void drawCubeLines(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
        float half = size / 2f;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        int a = (color >> 24) & 255;

        // Bottom edges
        drawLine(buffer, matrix, -half, -half, -half, half, -half, -half, r, g, b, a);
        drawLine(buffer, matrix, half, -half, -half, half, -half, half, r, g, b, a);
        drawLine(buffer, matrix, half, -half, half, -half, -half, half, r, g, b, a);
        drawLine(buffer, matrix, -half, -half, half, -half, -half, -half, r, g, b, a);

        // Top edges
        drawLine(buffer, matrix, -half, half, -half, half, half, -half, r, g, b, a);
        drawLine(buffer, matrix, half, half, -half, half, half, half, r, g, b, a);
        drawLine(buffer, matrix, half, half, half, -half, half, half, r, g, b, a);
        drawLine(buffer, matrix, -half, half, half, -half, half, -half, r, g, b, a);

        // Vertical edges
        drawLine(buffer, matrix, -half, -half, -half, -half, half, -half, r, g, b, a);
        drawLine(buffer, matrix, half, -half, -half, half, half, -half, r, g, b, a);
        drawLine(buffer, matrix, half, -half, half, half, half, half, r, g, b, a);
        drawLine(buffer, matrix, -half, -half, half, -half, half, half, r, g, b, a);
    }

    /**
     * Draws a glow effect (billboard quad) with texture.
     * Used in Svetych and ItemESP modules.
     *
     * @param buffer The vertex consumer buffer
     * @param matrix The transformation matrix
     * @param color The color (RGB, alpha is separate)
     * @param alpha The alpha value (0-255)
     * @param size The size of the glow quad
     */
    public static void drawGlow(VertexConsumer buffer, Matrix4f matrix, int color, int alpha, float size) {
        float halfSize = size / 2f;
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;

        buffer.vertex(matrix, -halfSize, -halfSize, 0)
                .color(r, g, b, alpha)
                .texture(0, 1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, halfSize, -halfSize, 0)
                .color(r, g, b, alpha)
                .texture(1, 1)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, halfSize, halfSize, 0)
                .color(r, g, b, alpha)
                .texture(1, 0)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
        buffer.vertex(matrix, -halfSize, halfSize, 0)
                .color(r, g, b, alpha)
                .texture(0, 0)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(0xF000F0)
                .normal(0, 0, 1);
    }

    /**
     * Helper method to draw a line segment.
     *
     * @param buffer The vertex consumer buffer
     * @param matrix The transformation matrix
     * @param x1     Start X coordinate
     * @param y1     Start Y coordinate
     * @param z1     Start Z coordinate
     * @param x2     End X coordinate
     * @param y2     End Y coordinate
     * @param z2     End Z coordinate
     * @param r      Red color component (0-255)
     * @param g      Green color component (0-255)
     * @param b      Blue color component (0-255)
     * @param a      Alpha component (0-255)
     */
    private static void drawLine(VertexConsumer buffer, Matrix4f matrix,
                                 float x1, float y1, float z1,
                                 float x2, float y2, float z2,
                                 int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }
}
