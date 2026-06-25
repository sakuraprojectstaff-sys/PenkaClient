package fun.rich.utils.display.geometry;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.utils.display.item.ItemRender;
import fun.rich.utils.display.shape.ShapeProperties;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL40C;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@UtilityClass
public class Render2D implements QuickImports {
    private final Identifier bloom = Identifier.of("minecraft", "textures/features/particles/bloom.png");

    private final List<Quad> QUAD = new ArrayList<>();
    private final List<GradientQuad> GRADIENT_QUAD = new ArrayList<>();
    private final List<Line> LINE = new ArrayList<>();
    private final List<TextureQuad> TEXTURE = new ArrayList<>();
    private final List<TextureQuad> ADDITIVE_TEXTURE = new ArrayList<>();

    public void onRender(DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        Matrix4f fallbackMatrix = matrices.peek().getPositionMatrix();

        if (!QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Quad quad : QUAD) {
                Matrix4f matrix = quad.matrix != null ? quad.matrix : fallbackMatrix;
                drawEngine.quad(matrix, buffer, quad.x, quad.y, quad.width, quad.height, quad.color);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.disableBlend();
            QUAD.clear();
        }

        if (!GRADIENT_QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (GradientQuad quad : GRADIENT_QUAD) {
                Matrix4f matrix = quad.matrix != null ? quad.matrix : fallbackMatrix;

                float x1 = quad.x;
                float y1 = quad.y;
                float x2 = quad.x + quad.width;
                float y2 = quad.y + quad.height;

                buffer.vertex(matrix, x1, y1, 0).color(quad.topLeft);
                buffer.vertex(matrix, x1, y2, 0).color(quad.bottomLeft);
                buffer.vertex(matrix, x2, y2, 0).color(quad.bottomRight);
                buffer.vertex(matrix, x2, y1, 0).color(quad.topRight);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.disableBlend();
            GRADIENT_QUAD.clear();
        }

        if (!LINE.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Line line : LINE) {
                float dx = line.x2 - line.x1;
                float dy = line.y2 - line.y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len <= 0.0001f) continue;

                Matrix4f matrix = line.matrix != null ? line.matrix : fallbackMatrix;
                float half = Math.max(0.5f, line.thickness * 0.5f);
                float nx = -dy / len * half;
                float ny = dx / len * half;

                float ax = line.x1 + nx;
                float ay = line.y1 + ny;
                float bx = line.x1 - nx;
                float by = line.y1 - ny;
                float cx = line.x2 - nx;
                float cy = line.y2 - ny;
                float dx2 = line.x2 + nx;
                float dy2 = line.y2 + ny;

                buffer.vertex(matrix, ax, ay, 0).color(line.startColor);
                buffer.vertex(matrix, bx, by, 0).color(line.startColor);
                buffer.vertex(matrix, cx, cy, 0).color(line.endColor);
                buffer.vertex(matrix, dx2, dy2, 0).color(line.endColor);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());

            RenderSystem.disableBlend();
            LINE.clear();
        }

        if (!TEXTURE.isEmpty()) {
            flushTextureBatch(TEXTURE, fallbackMatrix, false);
            TEXTURE.clear();
        }

        if (!ADDITIVE_TEXTURE.isEmpty()) {
            flushTextureBatch(ADDITIVE_TEXTURE, fallbackMatrix, true);
            ADDITIVE_TEXTURE.clear();
        }
    }

    private void flushTextureBatch(List<TextureQuad> list, Matrix4f fallbackMatrix, boolean additive) {
        Set<Identifier> identifiers = new LinkedHashSet<>();
        for (TextureQuad quad : list) {
            identifiers.add(quad.texture);
        }

        RenderSystem.enableBlend();
        RenderSystem.disableCull();

        if (additive) {
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        for (Identifier id : identifiers) {
            RenderSystem.setShaderTexture(0, id);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (TextureQuad quad : list) {
                if (!quad.texture.equals(id)) continue;

                Matrix4f matrix = quad.matrix != null ? quad.matrix : fallbackMatrix;
                buffer.vertex(matrix, quad.x1, quad.y1, 0).texture(quad.u1, quad.v1).color(quad.color);
                buffer.vertex(matrix, quad.x1, quad.y2, 0).texture(quad.u1, quad.v2).color(quad.color);
                buffer.vertex(matrix, quad.x2, quad.y2, 0).texture(quad.u2, quad.v2).color(quad.color);
                buffer.vertex(matrix, quad.x2, quad.y1, 0).texture(quad.u2, quad.v1).color(quad.color);
            }
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public void defaultDrawStack(DrawContext context, ItemStack stack, float x, float y, boolean rect, boolean drawItemInSlot, float scale) {
        MatrixStack matrix = context.getMatrices();
        if (rect) {
            drawBlurredRound(matrix, x, y, 16 * scale + 2, 16 * scale + 2, 2, ColorAssist.HALF_BLACK);
        }

        matrix.push();
        matrix.translate(x + 1, y + 1, 0);
        matrix.scale(scale, scale, 1);
        context.drawItem(stack, 0, 0);
        if (drawItemInSlot) {
            context.drawStackOverlay(mc.textRenderer, stack, 0, 0);
        }
        matrix.pop();
    }

    public void drawStack(MatrixStack matrix, ItemStack stack, float x, float y, boolean rect, float scale) {
        float posX = x + 1;
        float posY = y + 1;
        float padding = 1;

        matrix.push();
        matrix.translate(posX, posY, 0);
        if (rect) {
            drawBlurredRound(matrix, -padding, -padding, 16 * scale + padding * 2, 16 * scale + padding * 2, 1.5f, ColorAssist.HALF_BLACK);
        }
        matrix.scale(scale, scale, 1);
        ItemRender.drawItem(matrix, stack, 0, 0, true, true);
        matrix.pop();
    }

    public void drawTexture(DrawContext context, Identifier id, float x, float y, int size) {
        if (id == null) return;

        MatrixStack matrix = context.getMatrices();
        matrix.push();
        matrix.translate(x, y, 0);
        matrix.scale(size, size, 1);

        RenderSystem.enableBlend();
        drawTexture(matrix, id, 0, 0, 1, 1, size, size, size, size, size, size, -1);
        RenderSystem.disableBlend();

        matrix.pop();
    }

    public void drawTexture(DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor) {
        drawTexture(context, id, x, y, size, round, uvSize, regionSize, textureSize, backgroundColor, -1);
    }

    public void drawTexture(DrawContext context, Identifier id, float x, float y, float size, float round, int uvSize, int regionSize, int textureSize, int backgroundColor, int color) {
        MatrixStack matrix = context.getMatrices();
        drawRound(matrix, x, y, size, size, round, backgroundColor);

        if (id == null) return;

        matrix.push();
        matrix.translate(x, y, 0);
        matrix.scale(size, size, 1);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL40C.GL_DST_ALPHA, GL40C.GL_ONE_MINUS_DST_ALPHA);
        drawTexture(matrix, id, 0, 0, 1, 1, uvSize, uvSize, regionSize, regionSize, textureSize, textureSize, color);
        RenderSystem.disableBlend();

        matrix.pop();
    }

    public void drawSprite(MatrixStack matrix, Sprite sprite, float x, float y, float width, int height) {
        if (width == 0 || height == 0) return;
        drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), -1);
    }

    public void drawSprite(MatrixStack matrix, Sprite sprite, float x, float y, float width, int height, int color) {
        if (width == 0 || height == 0) return;
        drawTexturedQuad(matrix, sprite.getAtlasId(), x, x + width, y, y + height, sprite.getMinU(), sprite.getMaxU(), sprite.getMinV(), sprite.getMaxV(), color);
    }

    public void drawTexture(MatrixStack matrix, Identifier texture, int x, int y, float width, float height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        drawTexture(matrix, texture, x, x + width, y, y + height, 0, regionWidth, regionHeight, u, v, textureWidth, textureHeight, color);
    }

    public void drawTexture(MatrixStack matrix, Identifier texture, float x1, float x2, float y1, float y2, float z, int regionWidth, int regionHeight, float u, float v, int textureWidth, int textureHeight, int color) {
        drawTexturedQuad(
                matrix,
                texture,
                x1, x2, y1, y2,
                (u + 0.0F) / textureWidth,
                (u + regionWidth) / (float) textureWidth,
                (v + 0.0F) / textureHeight,
                (v + regionHeight) / (float) textureHeight,
                color
        );
    }

    public void drawTexturedQuad(MatrixStack matrix, Identifier texture, float x1, float x2, float y1, float y2, float u1, float u2, float v1, float v2, int color) {
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();

        buffer.vertex(matrix4f, x1, y1, 0).texture(u1, v1).color(color);
        buffer.vertex(matrix4f, x1, y2, 0).texture(u1, v2).color(color);
        buffer.vertex(matrix4f, x2, y2, 0).texture(u2, v2).color(color);
        buffer.vertex(matrix4f, x2, y1, 0).texture(u2, v1).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    public void drawCircle(MatrixStack matrix, float x, float y, float radius, int color) {
        drawCircle(matrix, x, y, radius, color, getCircleSegments(radius));
    }

    public void drawCircle(MatrixStack matrix, float x, float y, float radius, int color, int segments) {
        if (radius <= 0) return;

        segments = Math.max(8, segments);
        color = ColorAssist.multAlpha(color, getGlobalAlpha());

        float angleStep = (float) (Math.PI * 2.0 / segments);
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float angle1 = i * angleStep;
            float angle2 = (i + 1) * angleStep;

            float x1 = x + radius * (float) Math.cos(angle1);
            float y1 = y + radius * (float) Math.sin(angle1);
            float x2 = x + radius * (float) Math.cos(angle2);
            float y2 = y + radius * (float) Math.sin(angle2);

            buffer.vertex(matrix4f, x, y, 0).color(color);
            buffer.vertex(matrix4f, x1, y1, 0).color(color);
            buffer.vertex(matrix4f, x2, y2, 0).color(color);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    public void drawCircleOutline(MatrixStack matrix, float x, float y, float radius, float thickness, int color) {
        drawArc(matrix, x, y, radius, thickness, 0f, 360f, color);
    }

    public void drawArc(MatrixStack matrix, float x, float y, float radius, float thickness, float startDeg, float endDeg, int color) {
        if (radius <= 0 || thickness <= 0) return;

        color = ColorAssist.multAlpha(color, getGlobalAlpha());

        float innerRadius = Math.max(0f, radius - thickness);
        float start = (float) Math.toRadians(startDeg);
        float end = (float) Math.toRadians(endDeg);

        while (end < start) {
            end += (float) (Math.PI * 2.0);
        }

        float span = end - start;
        if (span <= 0.0001f) return;

        int segments = Math.max(8, (int) Math.ceil(radius * span * 0.35f));
        Matrix4f matrix4f = matrix.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < segments; i++) {
            float t1 = i / (float) segments;
            float t2 = (i + 1) / (float) segments;
            float a1 = start + span * t1;
            float a2 = start + span * t2;

            float ox1 = x + radius * (float) Math.cos(a1);
            float oy1 = y + radius * (float) Math.sin(a1);
            float ox2 = x + radius * (float) Math.cos(a2);
            float oy2 = y + radius * (float) Math.sin(a2);

            float ix1 = x + innerRadius * (float) Math.cos(a1);
            float iy1 = y + innerRadius * (float) Math.sin(a1);
            float ix2 = x + innerRadius * (float) Math.cos(a2);
            float iy2 = y + innerRadius * (float) Math.sin(a2);

            buffer.vertex(matrix4f, ox1, oy1, 0).color(color);
            buffer.vertex(matrix4f, ix1, iy1, 0).color(color);
            buffer.vertex(matrix4f, ix2, iy2, 0).color(color);

            buffer.vertex(matrix4f, ox1, oy1, 0).color(color);
            buffer.vertex(matrix4f, ix2, iy2, 0).color(color);
            buffer.vertex(matrix4f, ox2, oy2, 0).color(color);
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    public void drawProgressRing(MatrixStack matrix, float x, float y, float radius, float thickness, float progress, int color) {
        progress = Math.max(0f, Math.min(1f, progress));
        if (progress <= 0f) return;
        drawArc(matrix, x, y, radius, thickness, -90f, -90f + 360f * progress, color);
    }

    public void drawRound(MatrixStack matrix, float x, float y, float width, float height, float round, int color) {
        if (width <= 0 || height <= 0) return;

        int finalColor = ColorAssist.multAlpha(color, getGlobalAlpha());
        try {
            rectangle.render(ShapeProperties.create(matrix, x, y, width, height).round(round).color(finalColor).build());
        } catch (Throwable ignored) {
            drawQuad(matrix, x, y, width, height, finalColor);
        }
    }

    public void drawBlurredRound(MatrixStack matrix, float x, float y, float width, float height, float round, int color) {
        if (width <= 0 || height <= 0) return;

        int finalColor = ColorAssist.multAlpha(color, getGlobalAlpha());
        try {
            blur.render(ShapeProperties.create(matrix, x, y, width, height).round(round).color(finalColor).build());
        } catch (Throwable ignored) {
            drawShadow(matrix, x, y, width, height, 8, 0.5f, finalColor);
            drawRound(matrix, x, y, width, height, round, finalColor);
        }
    }

    public void drawRoundOutline(MatrixStack matrix, float x, float y, float width, float height, float round, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        int finalColor = ColorAssist.multAlpha(color, getGlobalAlpha());
        float t = Math.min(thickness, Math.min(width * 0.5f, height * 0.5f));

        drawRound(matrix, x, y, width, t, round, finalColor);
        drawRound(matrix, x, y + height - t, width, t, round, finalColor);
        drawQuad(matrix, x, y + t, t, height - t * 2f, finalColor);
        drawQuad(matrix, x + width - t, y + t, t, height - t * 2f, finalColor);
    }

    public void drawGlow(float x, float y, float width, float height, int color) {
        drawGlow(x, y, width, height, color, 1.0f);
    }

    public void drawGlow(float x, float y, float width, float height, int color, float strength) {
        if (width <= 0 || height <= 0 || strength <= 0) return;

        strength = Math.max(0f, Math.min(2f, strength));
        int baseColor = ColorAssist.multAlpha(color, getGlobalAlpha());

        int c1 = applyOpacity(baseColor, 0.18f * strength);
        int c2 = applyOpacity(baseColor, 0.32f * strength);
        int c3 = applyOpacity(baseColor, 0.52f * strength);

        queueAdditiveTexture(bloom, x - 18, y - 18, width + 36, height + 36, c1);
        queueAdditiveTexture(bloom, x - 10, y - 10, width + 20, height + 20, c2);
        queueAdditiveTexture(bloom, x - 4, y - 4, width + 8, height + 8, c3);
    }

    public void drawGlow(MatrixStack matrix, float x, float y, float width, float height, int color, float strength) {
        if (width <= 0 || height <= 0 || strength <= 0) return;

        strength = Math.max(0f, Math.min(2f, strength));
        int baseColor = ColorAssist.multAlpha(color, getGlobalAlpha());

        int c1 = applyOpacity(baseColor, 0.18f * strength);
        int c2 = applyOpacity(baseColor, 0.32f * strength);
        int c3 = applyOpacity(baseColor, 0.52f * strength);

        queueAdditiveTexture(matrix, bloom, x - 18, y - 18, width + 36, height + 36, c1);
        queueAdditiveTexture(matrix, bloom, x - 10, y - 10, width + 20, height + 20, c2);
        queueAdditiveTexture(matrix, bloom, x - 4, y - 4, width + 8, height + 8, c3);
    }

    public void drawBloom(float x, float y, float size, int color, float alpha) {
        if (size <= 0 || alpha <= 0) return;
        queueAdditiveTexture(bloom, x, y, size, size, applyOpacity(ColorAssist.multAlpha(color, getGlobalAlpha()), alpha));
    }

    public void drawBloom(MatrixStack matrix, float x, float y, float size, int color, float alpha) {
        if (size <= 0 || alpha <= 0) return;
        queueAdditiveTexture(matrix, bloom, x, y, size, size, applyOpacity(ColorAssist.multAlpha(color, getGlobalAlpha()), alpha));
    }

    public void drawPanel(MatrixStack matrix, float x, float y, float width, float height, float round, int backgroundColor, int outlineColor, int glowColor) {
        drawBlurredRound(matrix, x, y, width, height, round, applyOpacity(backgroundColor, 0.65f));
        drawRound(matrix, x, y, width, height, round, backgroundColor);
        drawRoundOutline(matrix, x, y, width, height, round, 1f, outlineColor);
        drawGlow(matrix, x, y, width, height, glowColor, 0.6f);
    }

    public void queueTexture(Identifier texture, float x, float y, float width, float height, int color) {
        enqueueTextureQuad(null, texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void queueTexture(MatrixStack matrix, Identifier texture, float x, float y, float width, float height, int color) {
        enqueueTextureQuad(copyMatrix(matrix), texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void queueTexture(Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        enqueueTextureQuad(null, texture, x, y, width, height, u1, v1, u2, v2, color);
    }

    public void queueTexture(MatrixStack matrix, Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        enqueueTextureQuad(copyMatrix(matrix), texture, x, y, width, height, u1, v1, u2, v2, color);
    }

    private void enqueueTextureQuad(Matrix4f matrix, Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        if (texture == null || width == 0 || height == 0) return;
        TEXTURE.add(new TextureQuad(matrix, texture, x, y, x + width, y + height, u1, v1, u2, v2, ColorAssist.multAlpha(color, getGlobalAlpha())));
    }

    public void queueAdditiveTexture(Identifier texture, float x, float y, float width, float height, int color) {
        enqueueAdditiveTextureQuad(null, texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void queueAdditiveTexture(MatrixStack matrix, Identifier texture, float x, float y, float width, float height, int color) {
        enqueueAdditiveTextureQuad(copyMatrix(matrix), texture, x, y, width, height, 0f, 0f, 1f, 1f, color);
    }

    public void queueAdditiveTexture(Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        enqueueAdditiveTextureQuad(null, texture, x, y, width, height, u1, v1, u2, v2, color);
    }

    public void queueAdditiveTexture(MatrixStack matrix, Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        enqueueAdditiveTextureQuad(copyMatrix(matrix), texture, x, y, width, height, u1, v1, u2, v2, color);
    }

    private void enqueueAdditiveTextureQuad(Matrix4f matrix, Identifier texture, float x, float y, float width, float height, float u1, float v1, float u2, float v2, int color) {
        if (texture == null || width == 0 || height == 0) return;
        ADDITIVE_TEXTURE.add(new TextureQuad(matrix, texture, x, y, x + width, y + height, u1, v1, u2, v2, ColorAssist.multAlpha(color, getGlobalAlpha())));
    }

    public void drawQuad(float x, float y, float width, float height, int color) {
        enqueueQuad(null, x, y, width, height, color);
    }

    public void drawQuad(MatrixStack matrix, float x, float y, float width, float height, int color) {
        enqueueQuad(copyMatrix(matrix), x, y, width, height, color);
    }

    private void enqueueQuad(Matrix4f matrix, float x, float y, float width, float height, int color) {
        if (width == 0 || height == 0) return;
        QUAD.add(new Quad(matrix, x, y, width, height, ColorAssist.multAlpha(color, getGlobalAlpha())));
    }

    public void drawQuadGradient(float x, float y, float width, float height, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        enqueueGradientQuad(null, x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
    }

    public void drawQuadGradient(MatrixStack matrix, float x, float y, float width, float height, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        enqueueGradientQuad(copyMatrix(matrix), x, y, width, height, topLeft, topRight, bottomRight, bottomLeft);
    }

    private void enqueueGradientQuad(Matrix4f matrix, float x, float y, float width, float height, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (width == 0 || height == 0) return;

        float alpha = getGlobalAlpha();
        GRADIENT_QUAD.add(new GradientQuad(
                matrix,
                x, y, width, height,
                ColorAssist.multAlpha(topLeft, alpha),
                ColorAssist.multAlpha(topRight, alpha),
                ColorAssist.multAlpha(bottomRight, alpha),
                ColorAssist.multAlpha(bottomLeft, alpha)
        ));
    }

    public void drawVerticalGradient(float x, float y, float width, float height, int topColor, int bottomColor) {
        drawQuadGradient(x, y, width, height, topColor, topColor, bottomColor, bottomColor);
    }

    public void drawVerticalGradient(MatrixStack matrix, float x, float y, float width, float height, int topColor, int bottomColor) {
        drawQuadGradient(matrix, x, y, width, height, topColor, topColor, bottomColor, bottomColor);
    }

    public void drawHorizontalGradient(float x, float y, float width, float height, int leftColor, int rightColor) {
        drawQuadGradient(x, y, width, height, leftColor, rightColor, rightColor, leftColor);
    }

    public void drawHorizontalGradient(MatrixStack matrix, float x, float y, float width, float height, int leftColor, int rightColor) {
        drawQuadGradient(matrix, x, y, width, height, leftColor, rightColor, rightColor, leftColor);
    }

    public void drawRectOutline(float x, float y, float width, float height, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        float t = Math.min(thickness, Math.min(width * 0.5f, height * 0.5f));
        drawQuad(x, y, width, t, color);
        drawQuad(x, y + height - t, width, t, color);
        drawQuad(x, y + t, t, height - t * 2f, color);
        drawQuad(x + width - t, y + t, t, height - t * 2f, color);
    }

    public void drawRectOutline(MatrixStack matrix, float x, float y, float width, float height, float thickness, int color) {
        if (width <= 0 || height <= 0 || thickness <= 0) return;

        float t = Math.min(thickness, Math.min(width * 0.5f, height * 0.5f));
        drawQuad(matrix, x, y, width, t, color);
        drawQuad(matrix, x, y + height - t, width, t, color);
        drawQuad(matrix, x, y + t, t, height - t * 2f, color);
        drawQuad(matrix, x + width - t, y + t, t, height - t * 2f, color);
    }

    public void drawLine(float x1, float y1, float x2, float y2, int color) {
        drawLine(x1, y1, x2, y2, 1f, color, color);
    }

    public void drawLine(float x1, float y1, float x2, float y2, float thickness, int color) {
        drawLine(x1, y1, x2, y2, thickness, color, color);
    }

    public void drawLine(float x1, float y1, float x2, float y2, float thickness, int startColor, int endColor) {
        enqueueLine(null, x1, y1, x2, y2, thickness, startColor, endColor);
    }

    public void drawLine(MatrixStack matrix, float x1, float y1, float x2, float y2, float thickness, int color) {
        drawLine(matrix, x1, y1, x2, y2, thickness, color, color);
    }

    public void drawLine(MatrixStack matrix, float x1, float y1, float x2, float y2, float thickness, int startColor, int endColor) {
        enqueueLine(copyMatrix(matrix), x1, y1, x2, y2, thickness, startColor, endColor);
    }

    private void enqueueLine(Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int startColor, int endColor) {
        if (thickness <= 0) return;
        LINE.add(new Line(
                matrix,
                x1, y1, x2, y2, thickness,
                ColorAssist.multAlpha(startColor, getGlobalAlpha()),
                ColorAssist.multAlpha(endColor, getGlobalAlpha())
        ));
    }

    public void drawShadow(float x, float y, float width, float height, float blurRadius, int color) {
        drawShadow(x, y, width, height, blurRadius, 0.6f, color);
    }

    public void drawShadow(float x, float y, float width, float height, float blurRadius, float strength, int color) {
        if (width <= 0 || height <= 0 || blurRadius <= 0 || strength <= 0) return;

        int steps = Math.max(1, (int) Math.ceil(blurRadius));
        strength = Math.max(0f, Math.min(1f, strength));

        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float falloff = 1f - t;
            falloff *= falloff;
            int c = applyOpacity(ColorAssist.multAlpha(color, getGlobalAlpha()), falloff * strength);
            drawRectOutline(x - i, y - i, width + i * 2f, height + i * 2f, 1f, c);
        }
    }

    public void drawShadow(MatrixStack matrix, float x, float y, float width, float height, float blurRadius, float strength, int color) {
        if (width <= 0 || height <= 0 || blurRadius <= 0 || strength <= 0) return;

        int steps = Math.max(1, (int) Math.ceil(blurRadius));
        strength = Math.max(0f, Math.min(1f, strength));

        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float falloff = 1f - t;
            falloff *= falloff;
            int c = applyOpacity(ColorAssist.multAlpha(color, getGlobalAlpha()), falloff * strength);
            drawRectOutline(matrix, x - i, y - i, width + i * 2f, height + i * 2f, 1f, c);
        }
    }

    public static Color applyOpacity(Color color, float opacity) {
        opacity = Math.min(1, Math.max(0, opacity));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * opacity));
    }

    public static int applyOpacity(int colorInt, float opacity) {
        opacity = Math.min(1, Math.max(0, opacity));
        Color color = new Color(colorInt, true);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * opacity)).getRGB();
    }

    public static void endBuilding(BufferBuilder bb) {
        BuiltBuffer builtBuffer = bb.endNullable();
        if (builtBuffer != null) {
            BufferRenderer.drawWithGlobalProgram(builtBuffer);
        }
    }

    private Matrix4f copyMatrix(MatrixStack matrix) {
        return matrix == null ? null : new Matrix4f(matrix.peek().getPositionMatrix());
    }

    private float getGlobalAlpha() {
        return RenderSystem.getShaderColor()[3];
    }

    private int getCircleSegments(float radius) {
        return Math.max(12, Math.min(96, (int) (radius * 1.8f)));
    }

    public record Quad(Matrix4f matrix, float x, float y, float width, float height, int color) {
    }

    public record GradientQuad(Matrix4f matrix, float x, float y, float width, float height, int topLeft, int topRight, int bottomRight, int bottomLeft) {
    }

    public record Line(Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int startColor, int endColor) {
    }

    public record TextureQuad(Matrix4f matrix, Identifier texture, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, int color) {
    }
}