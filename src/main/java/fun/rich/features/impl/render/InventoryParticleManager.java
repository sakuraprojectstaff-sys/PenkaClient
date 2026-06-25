package fun.rich.features.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.utils.display.geometry.Render2D;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL40C;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

final class InventoryParticleManager {
    private static final Identifier BLOOM = Identifier.of("minecraft", "textures/features/particles/bloom.png");
    private static final int MAX_PARTICLES = 700;

    private final ArrayList<InventoryParticle> particles = new ArrayList<>();
    private long lastTimeNanos;

    void clear() {
        particles.clear();
        lastTimeNanos = 0L;
    }

    void update(long now) {
        if (lastTimeNanos == 0L) {
            lastTimeNanos = now;
            return;
        }

        float dt = (now - lastTimeNanos) * 0.000000001f;
        lastTimeNanos = now;

        if (dt <= 0.0f) {
            return;
        }

        dt = Math.min(dt, 0.05f);

        for (int i = particles.size() - 1; i >= 0; i--) {
            InventoryParticle particle = particles.get(i);
            particle.age += dt;

            if (particle.age >= particle.lifetime) {
                particles.remove(i);
                continue;
            }

            particle.velocityY += particle.gravity * dt;

            float dragFactor = (float) Math.pow(particle.drag, dt * 60.0f);
            particle.velocityX *= dragFactor;
            particle.velocityY *= dragFactor;

            particle.x += particle.velocityX * dt;
            particle.y += particle.velocityY * dt;
            particle.size += particle.sizeVelocity * dt;

            if (particle.size < 0.18f) {
                particle.size = 0.18f;
            }
        }
    }

    void spawn(float centerX, float centerY, InventoryParticlePreset preset, boolean hover, ItemStack stack) {
        if (particles.size() >= MAX_PARTICLES) {
            particles.subList(0, Math.min(48, particles.size())).clear();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        float hueShift = ((path.hashCode() & 0x7fffffff) % 21) / 100.0f;

        float x = centerX + random.nextFloat(-preset.spreadX(), preset.spreadX());
        float y = centerY + random.nextFloat(-preset.spreadY(), preset.spreadY());
        float velocityX = random.nextFloat(preset.velocityXMin(), preset.velocityXMax());
        float velocityY = random.nextFloat(preset.velocityYMin(), preset.velocityYMax());

        if (hover) {
            velocityX *= 1.12f;
            velocityY *= 1.08f;
        }

        float gravity = preset.gravity() * (hover ? 1.05f : 1.0f);
        float drag = Math.max(0.88f, preset.drag() - (hover ? 0.006f : 0.0f));
        float lifetime = random.nextFloat(preset.minLifetime(), preset.maxLifetime()) * (stack.hasGlint() ? 1.06f : 1.0f);
        float size = random.nextFloat(preset.minSize(), preset.maxSize()) * (hover ? 1.12f : 1.0f);
        float sizeVelocity = random.nextFloat(preset.minSizeVelocity(), preset.maxSizeVelocity());
        float alpha = random.nextFloat(preset.minAlpha(), preset.maxAlpha()) * (stack.hasGlint() ? 1.08f : 1.0f);

        int baseColor = shiftHue(mix(preset.primaryColor(), preset.secondaryColor(), random.nextFloat()), hueShift);
        int accentColor = shiftHue(mix(preset.accentColor(), 0xFFFFFFFF, random.nextFloat(0.0f, 0.35f)), hueShift * 0.35f);

        particles.add(new InventoryParticle(
                x,
                y,
                velocityX,
                velocityY,
                gravity,
                drag,
                lifetime,
                size,
                sizeVelocity,
                alpha,
                baseColor,
                accentColor,
                preset.style()
        ));
    }

    void render(DrawContext drawContext) {
        if (particles.isEmpty()) {
            return;
        }

        MatrixStack matrices = drawContext.getMatrices();

        for (InventoryParticle particle : particles) {
            float progress = clamp(particle.age / particle.lifetime);
            float fadeIn = clamp(progress / 0.12f);
            float fadeOut = progress < 0.58f ? 1.0f : clamp(1.0f - (progress - 0.58f) / 0.42f);
            float alpha = particle.alpha * fadeIn * fadeOut * fadeOut;

            if (alpha <= 0.02f) {
                continue;
            }

            float size = particle.size * (0.84f + (1.0f - progress) * 0.28f);

            switch (particle.style) {
                case SOFT -> renderSoft(matrices, particle, size, alpha);
                case BUBBLE -> renderBubble(matrices, particle, size, alpha);
                case SPARK -> renderSpark(matrices, particle, size, alpha);
                case RING -> renderRing(matrices, particle, size, alpha);
                case DUST -> renderDust(matrices, particle, size, alpha);
                case EMBER -> renderEmber(matrices, particle, size, alpha);
                case SHARD -> renderShard(matrices, particle, size, alpha);
                case HOLY -> renderHoly(matrices, particle, size, alpha);
                case VOID -> renderVoid(matrices, particle, size, alpha);
            }
        }
    }

    private void renderSoft(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        drawBloom(matrices, particle.x, particle.y, size * 1.25f, withAlpha(particle.color, alpha * 0.75f));
        Render2D.drawCircle(matrices, particle.x, particle.y, size * 0.22f, withAlpha(particle.accentColor, alpha * 0.62f));
    }

    private void renderBubble(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        Render2D.drawCircle(matrices, particle.x, particle.y, size * 0.24f, withAlpha(particle.color, alpha * 0.12f));
        Render2D.drawCircleOutline(matrices, particle.x, particle.y, size * 0.34f, Math.max(0.8f, size * 0.08f), withAlpha(particle.color, alpha * 0.78f));
        Render2D.drawCircle(matrices, particle.x - size * 0.11f, particle.y - size * 0.11f, size * 0.06f, withAlpha(0xFFFFFFFF, alpha * 0.92f));
    }

    private void renderSpark(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        float len = size * 0.42f;
        float thickness = Math.max(0.75f, size * 0.09f);

        drawLine(matrices, particle.x - len, particle.y, particle.x + len, particle.y, thickness, withAlpha(particle.color, alpha * 0.95f));
        drawLine(matrices, particle.x, particle.y - len, particle.x, particle.y + len, thickness, withAlpha(particle.accentColor, alpha * 0.88f));
        drawDiamond(matrices, particle.x, particle.y, size * 0.18f, withAlpha(particle.accentColor, alpha * 0.72f));
    }

    private void renderRing(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        Render2D.drawCircleOutline(matrices, particle.x, particle.y, size * 0.34f, Math.max(0.8f, size * 0.10f), withAlpha(particle.color, alpha * 0.84f));
        Render2D.drawCircle(matrices, particle.x, particle.y, size * 0.08f, withAlpha(particle.accentColor, alpha * 0.75f));
    }

    private void renderDust(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        Render2D.drawCircle(matrices, particle.x, particle.y, size * 0.20f, withAlpha(particle.color, alpha * 0.72f));
        Render2D.drawCircle(matrices, particle.x + size * 0.08f, particle.y - size * 0.05f, size * 0.08f, withAlpha(particle.accentColor, alpha * 0.46f));
    }

    private void renderEmber(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        drawBloom(matrices, particle.x, particle.y, size * 1.05f, withAlpha(particle.color, alpha * 0.68f));
        drawLine(matrices, particle.x, particle.y + size * 0.05f, particle.x, particle.y + size * 0.45f, Math.max(0.7f, size * 0.08f), withAlpha(particle.color, alpha * 0.54f));
        drawDiamond(matrices, particle.x, particle.y, size * 0.16f, withAlpha(particle.accentColor, alpha * 0.74f));
    }

    private void renderShard(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        drawBloom(matrices, particle.x, particle.y, size * 0.78f, withAlpha(particle.color, alpha * 0.44f));
        drawDiamond(matrices, particle.x, particle.y, size * 0.24f, withAlpha(particle.color, alpha * 0.92f));
        drawLine(matrices, particle.x, particle.y - size * 0.24f, particle.x, particle.y + size * 0.24f, Math.max(0.65f, size * 0.06f), withAlpha(particle.accentColor, alpha * 0.60f));
    }

    private void renderHoly(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        drawBloom(matrices, particle.x, particle.y, size * 1.45f, withAlpha(particle.color, alpha * 0.72f));
        Render2D.drawCircleOutline(matrices, particle.x, particle.y, size * 0.30f, Math.max(0.8f, size * 0.09f), withAlpha(particle.accentColor, alpha * 0.82f));
        drawLine(matrices, particle.x - size * 0.25f, particle.y, particle.x + size * 0.25f, particle.y, Math.max(0.8f, size * 0.08f), withAlpha(particle.accentColor, alpha * 0.86f));
        drawLine(matrices, particle.x, particle.y - size * 0.25f, particle.x, particle.y + size * 0.25f, Math.max(0.8f, size * 0.08f), withAlpha(particle.color, alpha * 0.80f));
    }

    private void renderVoid(MatrixStack matrices, InventoryParticle particle, float size, float alpha) {
        drawBloom(matrices, particle.x, particle.y, size * 1.30f, withAlpha(particle.color, alpha * 0.76f));
        Render2D.drawCircleOutline(matrices, particle.x, particle.y, size * 0.30f, Math.max(0.8f, size * 0.08f), withAlpha(particle.accentColor, alpha * 0.64f));
        Render2D.drawCircle(matrices, particle.x, particle.y, size * 0.10f, withAlpha(particle.accentColor, alpha * 0.55f));
    }

    private void drawBloom(MatrixStack matrices, float x, float y, float size, int color) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL40C.GL_SRC_ALPHA, GL40C.GL_ONE);
        Render2D.drawTexturedQuad(matrices, BLOOM, x - size * 0.5f, x + size * 0.5f, y - size * 0.5f, y + size * 0.5f, 0f, 1f, 0f, 1f, color);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawLine(MatrixStack matrices, float x1, float y1, float x2, float y2, float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len <= 0.0001f) {
            return;
        }

        float half = Math.max(0.5f, thickness * 0.5f);
        float nx = -dy / len * half;
        float ny = dx / len * half;

        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buffer.vertex(matrix, x1 + nx, y1 + ny, 0).color(color);
        buffer.vertex(matrix, x1 - nx, y1 - ny, 0).color(color);
        buffer.vertex(matrix, x2 - nx, y2 - ny, 0).color(color);
        buffer.vertex(matrix, x2 + nx, y2 + ny, 0).color(color);
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
    }

    private void drawDiamond(MatrixStack matrices, float x, float y, float radius, int color) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        buffer.vertex(matrix, x, y - radius, 0).color(color);
        buffer.vertex(matrix, x - radius, y, 0).color(color);
        buffer.vertex(matrix, x + radius, y, 0).color(color);

        buffer.vertex(matrix, x, y + radius, 0).color(color);
        buffer.vertex(matrix, x - radius, y, 0).color(color);
        buffer.vertex(matrix, x + radius, y, 0).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private float clamp(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private int withAlpha(int color, float alpha) {
        alpha = clamp(alpha);
        int a = (int) (alpha * 255.0f);
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int mix(int first, int second, float t) {
        t = clamp(t);

        int a1 = (first >>> 24) & 255;
        int r1 = (first >>> 16) & 255;
        int g1 = (first >>> 8) & 255;
        int b1 = first & 255;

        int a2 = (second >>> 24) & 255;
        int r2 = (second >>> 16) & 255;
        int g2 = (second >>> 8) & 255;
        int b2 = second & 255;

        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);

        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }

    private int shiftHue(int color, float shift) {
        int a = (color >>> 24) & 255;
        int r = (color >>> 16) & 255;
        int g = (color >>> 8) & 255;
        int b = color & 255;

        float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
        float hue = hsb[0] + shift;
        while (hue > 1.0f) hue -= 1.0f;
        while (hue < 0.0f) hue += 1.0f;

        int rgb = java.awt.Color.HSBtoRGB(hue, hsb[1], hsb[2]);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }
}