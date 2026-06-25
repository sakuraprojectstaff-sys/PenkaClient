package fun.rich.utils.display.geometry;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.impl.render.TargetESP;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.utils.math.calc.Calculate;
import fun.rich.utils.math.projection.Projection;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@UtilityClass
public class Render3D implements QuickImports {
    private static final int FILL_DEFAULT = 0;
    private static final int FILL_PORTAL = 1;
    private static final int FILL_DEEP_PORTAL = 2;
    private static final int FILL_PORTAL_TEX1 = 3;
    private static final int FILL_PORTAL_TEX2 = 4;

    private final Map<VoxelShape, Pair<List<Box>, List<Line>>> SHAPE_OUTLINES = new HashMap<>();
    private final Map<VoxelShape, List<Box>> SHAPE_BOXES = new HashMap<>();
    public final List<Texture> TEXTURE_DEPTH = new ArrayList<>();
    public final List<Texture> TEXTURE = new ArrayList<>();
    public final List<Texture> ADDITIVE_TEXTURE_DEPTH = new ArrayList<>();
    public final List<Texture> ADDITIVE_TEXTURE = new ArrayList<>();
    public final List<Line> LINE_DEPTH = new ArrayList<>();
    public final List<Line> LINE = new ArrayList<>();
    public final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public final List<Quad> QUAD = new ArrayList<>();
    public final List<GradientQuad> GRADIENT_QUAD_DEPTH = new ArrayList<>();
    public final List<GradientQuad> GRADIENT_QUAD = new ArrayList<>();

    public final List<PortalQuad> PORTAL_QUAD_DEPTH = new ArrayList<>();
    public final List<PortalQuad> PORTAL_QUAD = new ArrayList<>();
    public final List<DeepPortalQuad> DEEP_PORTAL_QUAD_DEPTH = new ArrayList<>();
    public final List<DeepPortalQuad> DEEP_PORTAL_QUAD = new ArrayList<>();
    public final List<TexturedPortalQuad> TEXTURED_PORTAL_QUAD_DEPTH = new ArrayList<>();
    public final List<TexturedPortalQuad> TEXTURED_PORTAL_QUAD = new ArrayList<>();

    public final List<Ribbon> RIBBON_DEPTH = new ArrayList<>();
    public final List<Ribbon> RIBBON = new ArrayList<>();

    @Setter public Matrix4f lastProjMat = new Matrix4f();
    @Setter public MatrixStack.Entry lastWorldSpaceMatrix = new MatrixStack().peek();

    private final Identifier captureId = Identifier.of("minecraft", "textures/capture1.png"),
            bloom = Identifier.of("minecraft", "textures/features/particles/bloom.png"),
            deepPortalTex = Identifier.of("minecraft", "textures/features/portal/deep_portal.png"),
            portalTex1 = Identifier.of("mre", "textures/test1.png"),
            portalTex2 = Identifier.of("mre", "textures/test2.png");

    private final Identifier portalTex1Rt = Identifier.of("mre", "textures/test1_rt.png");
    private final Identifier portalTex2Rt = Identifier.of("mre", "textures/test2_rt.png");

    public final List<Crystal> crystalList = new ArrayList<>();

    private static Identifier resolvedDeepPortalTex;
    private static boolean deepPortalTexChecked;

    private static Identifier resolvedPortalTex1;
    private static Identifier resolvedPortalTex2;
    private static boolean portalTex1Checked;
    private static boolean portalTex2Checked;

    private static class Crystal {
        private final Entity entity;
        private final Vec3d position;
        private final Vec3d rotation;
        private final float size;
        private final float rotationSpeed;

        public Crystal(Entity entity, Vec3d position, Vec3d rotation) {
            this.entity = entity;
            this.position = position;
            this.rotation = rotation;
            this.size = 0.09f;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
        }

        public void render(MatrixStack ms) {
            ms.push();
            ms.translate(position.x, position.y, position.z);
            float pulsation = 1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.1f);
            ms.scale(pulsation, pulsation, pulsation);
            float selfRotation = (System.currentTimeMillis() % 36000) / 100.0f * rotationSpeed;
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y + selfRotation));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            int baseColor = ColorAssist.fade(90);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            drawCrystal(ms, baseColor, 0.2f, true);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            drawCrystal(ms, baseColor, 0.3f, true);
            drawCrystal(ms, baseColor, 0.8f, false);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            ms.pop();
        }

        private void drawCrystal(MatrixStack ms, int baseColor, float alpha, boolean filled) {
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(
                    filled ? VertexFormat.DrawMode.TRIANGLES : VertexFormat.DrawMode.DEBUG_LINES,
                    VertexFormats.POSITION_COLOR
            );
            float s = size;
            float h_prism = size * 1f;
            float h_pyramid = size * 1.5f;
            int numSides = 8;
            List<Vec3d> topVertices = new ArrayList<>();
            List<Vec3d> bottomVertices = new ArrayList<>();
            for (int i = 0; i < numSides; i++) {
                float angle = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(angle));
                float z = (float) (s * Math.sin(angle));
                topVertices.add(new Vec3d(x, h_prism / 2, z));
                bottomVertices.add(new Vec3d(x, -h_prism / 2, z));
            }
            Vec3d vTop = new Vec3d(0, h_prism / 2 + h_pyramid, 0);
            Vec3d vBottom = new Vec3d(0, -h_prism / 2 - h_pyramid, 0);
            int finalColor = ColorAssist.setAlpha(baseColor, (int) (alpha * 255));
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                Vec3d v3 = topVertices.get((i + 1) % numSides);
                Vec3d v4 = topVertices.get(i);
                drawQuad(ms, bufferBuilder, v1, v2, v3, v4, finalColor, filled);
            }
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = topVertices.get(i);
                Vec3d v2 = topVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vTop, v1, v2, finalColor, filled);
            }
            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vBottom, v2, v1, finalColor, filled);
            }
            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }

        private void drawTriangle(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, int color, boolean filled) {
            if (filled) {
                bb.vertex(ms.peek().getPositionMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).color(color);
            } else {
                bb.vertex(ms.peek().getPositionMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).color(color);
            }
        }

        private void drawQuad(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, int color, boolean filled) {
            if (filled) {
                drawTriangle(ms, bb, v1, v2, v3, color, true);
                drawTriangle(ms, bb, v1, v3, v4, color, true);
            } else {
                bb.vertex(ms.peek().getPositionMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v4.x, (float) v4.y, (float) v4.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v4.x, (float) v4.y, (float) v4.z).color(color);
                bb.vertex(ms.peek().getPositionMatrix(), (float) v1.x, (float) v1.y, (float) v1.z).color(color);
            }
        }
    }

    public void drawEntity(Entity entity, Vec3d pos, float yaw, int alpha, MatrixStack matrices, float tickDelta) {
        if (!(entity instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) entity;
        matrices.push();
        matrices.translate(pos.x, pos.y, pos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.scale(1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / 255.0F);
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
        if (renderer != null) {
            int light = renderer.getLight(livingEntity, tickDelta);
            VertexConsumerProvider vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();
            EntityRenderState renderState = renderer.getAndUpdateRenderState(livingEntity, tickDelta);
            if (renderState != null) {
                renderer.render(renderState, matrices, vertexConsumers, light);
            }
            ((VertexConsumerProvider.Immediate) vertexConsumers).draw();
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        matrices.pop();
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (!TEXTURE.isEmpty()) {
            Set<Identifier> identifiers = TEXTURE.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                TEXTURE.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            TEXTURE.clear();
        }

        if (!TEXTURE_DEPTH.isEmpty()) {
            Set<Identifier> identifiers = TEXTURE_DEPTH.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                TEXTURE_DEPTH.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            TEXTURE_DEPTH.clear();
        }

        if (!LINE.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                LINE.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }

        if (!QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            QUAD.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            QUAD.clear();
        }

        if (!GRADIENT_QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            GRADIENT_QUAD.forEach(quad -> vertexQuadGradient(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.colorX, quad.colorY, quad.colorW, quad.colorZ));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            GRADIENT_QUAD.clear();
        }

        if (!RIBBON.isEmpty()) {
            renderRibbons(RIBBON, false);
            RIBBON.clear();
        }

        if (!PORTAL_QUAD.isEmpty()) {
            renderPortalQuads(PORTAL_QUAD, false);
            PORTAL_QUAD.clear();
        }

        if (!DEEP_PORTAL_QUAD.isEmpty()) {
            renderDeepPortalQuads(DEEP_PORTAL_QUAD, false);
            DEEP_PORTAL_QUAD.clear();
        }

        if (!TEXTURED_PORTAL_QUAD.isEmpty()) {
            renderTexturedPortalQuads(TEXTURED_PORTAL_QUAD, false);
            TEXTURED_PORTAL_QUAD.clear();
        }

        if (!ADDITIVE_TEXTURE.isEmpty()) {
            Set<Identifier> identifiers = ADDITIVE_TEXTURE.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                ADDITIVE_TEXTURE.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            ADDITIVE_TEXTURE.clear();
        }

        if (!LINE_DEPTH.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE_DEPTH.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.RENDERTYPE_LINES);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.LINES, VertexFormats.LINES);
                LINE_DEPTH.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE_DEPTH.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }

        if (!QUAD_DEPTH.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            QUAD_DEPTH.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            QUAD_DEPTH.clear();
        }

        if (!GRADIENT_QUAD_DEPTH.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            GRADIENT_QUAD_DEPTH.forEach(quad -> vertexQuadGradient(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.colorX, quad.colorY, quad.colorW, quad.colorZ));
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            GRADIENT_QUAD_DEPTH.clear();
        }

        if (!RIBBON_DEPTH.isEmpty()) {
            renderRibbons(RIBBON_DEPTH, true);
            RIBBON_DEPTH.clear();
        }

        if (!PORTAL_QUAD_DEPTH.isEmpty()) {
            renderPortalQuads(PORTAL_QUAD_DEPTH, true);
            PORTAL_QUAD_DEPTH.clear();
        }

        if (!DEEP_PORTAL_QUAD_DEPTH.isEmpty()) {
            renderDeepPortalQuads(DEEP_PORTAL_QUAD_DEPTH, true);
            DEEP_PORTAL_QUAD_DEPTH.clear();
        }

        if (!TEXTURED_PORTAL_QUAD_DEPTH.isEmpty()) {
            renderTexturedPortalQuads(TEXTURED_PORTAL_QUAD_DEPTH, true);
            TEXTURED_PORTAL_QUAD_DEPTH.clear();
        }

        if (!ADDITIVE_TEXTURE_DEPTH.isEmpty()) {
            Set<Identifier> identifiers = ADDITIVE_TEXTURE_DEPTH.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                ADDITIVE_TEXTURE_DEPTH.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferRenderer.drawWithGlobalProgram(buffer.end());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            ADDITIVE_TEXTURE_DEPTH.clear();
        }
    }

    private void renderRibbons(List<Ribbon> list, boolean depth) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();

        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        double t = System.nanoTime() * 1.0E-9;

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (Ribbon r : list) {
            MatrixStack.Entry entry = baseQuadEntry(r.entry);

            Vec3d dir = r.end.subtract(r.start);
            double len = dir.length();
            if (len < 1.0E-6) continue;
            Vec3d dirN = dir.multiply(1.0 / len);

            Vec3d side = dirN.crossProduct(r.viewDir);
            double sLen = side.length();
            if (sLen < 1.0E-6) {
                side = dirN.crossProduct(new Vec3d(0, 1, 0));
                sLen = side.length();
                if (sLen < 1.0E-6) continue;
            }
            side = side.multiply(1.0 / sLen);

            float pulse = 0.55f + 0.45f * (float) Math.sin(t * r.pulseSpeed + r.seed * 0.07f);
            float width = r.width * (0.85f + 0.35f * pulse);

            Vec3d off = side.multiply(width * 0.5);

            Vec3d a = r.start.subtract(off);
            Vec3d b = r.start.add(off);
            Vec3d c = r.end.add(off);
            Vec3d d = r.end.subtract(off);

            int ca = mulAlphaArgb(r.colorStart, r.alphaStart);
            int cb = mulAlphaArgb(r.colorStart, r.alphaStart);
            int cc = mulAlphaArgb(r.colorEnd, r.alphaEnd);
            int cd = mulAlphaArgb(r.colorEnd, r.alphaEnd);

            buffer.vertex(entry, (float) a.x, (float) a.y, (float) a.z).color(ca);
            buffer.vertex(entry, (float) b.x, (float) b.y, (float) b.z).color(cb);
            buffer.vertex(entry, (float) c.x, (float) c.y, (float) c.z).color(cc);
            buffer.vertex(entry, (float) d.x, (float) d.y, (float) d.z).color(cd);

            if (r.glow > 0.0001f) {
                Vec3d gOff = side.multiply(width * (0.55 + r.glow * 1.6));
                Vec3d ga = r.start.subtract(gOff);
                Vec3d gb = r.start.add(gOff);
                Vec3d gc = r.end.add(gOff);
                Vec3d gd = r.end.subtract(gOff);

                int g1 = mulAlphaArgb(r.glowColor, r.glowAlpha * 0.65f);
                int g2 = mulAlphaArgb(r.glowColor, r.glowAlpha * 0.65f);
                int g3 = mulAlphaArgb(r.glowColor, r.glowAlpha * 0.25f);
                int g4 = mulAlphaArgb(r.glowColor, r.glowAlpha * 0.25f);

                buffer.vertex(entry, (float) ga.x, (float) ga.y, (float) ga.z).color(g1);
                buffer.vertex(entry, (float) gb.x, (float) gb.y, (float) gb.z).color(g2);
                buffer.vertex(entry, (float) gc.x, (float) gc.y, (float) gc.z).color(g3);
                buffer.vertex(entry, (float) gd.x, (float) gd.y, (float) gd.z).color(g4);
            }
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    public void drawRibbon(MatrixStack.Entry entry, Vec3d start, Vec3d end, Vec3d viewDir, float width, int colorStart, int colorEnd, float alphaStart, float alphaEnd, float pulseSpeed, float glow, int glowColor, float glowAlpha, boolean depth) {
        Ribbon r = new Ribbon(entry, start, end, viewDir, width, colorStart, colorEnd, alphaStart, alphaEnd, pulseSpeed, glow, glowColor, glowAlpha, mixHash((int) System.nanoTime()));
        if (depth) RIBBON_DEPTH.add(r);
        else RIBBON.add(r);
    }

    public void drawRibbonAroundEntity(LivingEntity entity, float radiusMul, float heightMul, float width, float alpha, boolean depth) {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Vec3d camPos = cam.getPos();
        Vec3d viewDir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).normalize();

        Vec3d base = Calculate.interpolate(entity);
        float w = entity.getWidth() * radiusMul;
        float h = entity.getHeight() * heightMul;

        double t = System.nanoTime() * 1.0E-9;
        int seg = 18;

        for (int i = 0; i < seg; i++) {
            double a0 = (t * 1.25 + i * (Math.PI * 2.0 / seg));
            double a1 = (t * 1.25 + (i + 1) * (Math.PI * 2.0 / seg));

            Vec3d p0 = base.add(Math.cos(a0) * w, 0.25 + (0.5 + 0.5 * Math.sin(t * 1.8 + i * 0.35)) * h, Math.sin(a0) * w);
            Vec3d p1 = base.add(Math.cos(a1) * w, 0.25 + (0.5 + 0.5 * Math.sin(t * 1.8 + (i + 1) * 0.35)) * h, Math.sin(a1) * w);

            Vec3d s0 = p0.subtract(camPos);
            Vec3d s1 = p1.subtract(camPos);

            int c0 = mixArgb(0xFF24D7FF, 0xFF7C2BFF, (float) i / (float) seg);
            int c1 = mixArgb(0xFF24D7FF, 0xFF7C2BFF, (float) (i + 1) / (float) seg);

            drawRibbon(null, s0, s1, viewDir, width, c0, c1, alpha * 0.75f, alpha * 0.15f, 2.2f, 0.9f, 0xFFB7F3FF, alpha * 0.22f, depth);
        }
    }

    public void drawSoftRing(Vec3d center, double radius, double thickness, int innerColor, int outerColor, int segments, boolean depth) {
        int seg = Math.max(18, segments);
        double inner = Math.max(0.001, radius - thickness * 0.5);
        double outer = Math.max(inner + 0.001, radius + thickness * 0.5);

        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2.0 * i / seg;
            double a1 = Math.PI * 2.0 * (i + 1) / seg;

            Vec3d i0 = center.add(Math.cos(a0) * inner, 0.0, Math.sin(a0) * inner);
            Vec3d i1 = center.add(Math.cos(a1) * inner, 0.0, Math.sin(a1) * inner);
            Vec3d o1 = center.add(Math.cos(a1) * outer, 0.0, Math.sin(a1) * outer);
            Vec3d o0 = center.add(Math.cos(a0) * outer, 0.0, Math.sin(a0) * outer);

            drawGradientQuad(null, i0, i1, o1, o0, innerColor, innerColor, outerColor, outerColor, depth);
        }
    }

    public void drawHelixRibbon(Vec3d center, double radius, double height, float width, int colorStart, int colorEnd, float alpha, float glow, int turns, int strands, boolean depth) {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Vec3d viewDir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).normalize();
        double time = System.nanoTime() * 1.0E-9;
        int samples = Math.max(32, turns * 24);

        for (int strand = 0; strand < Math.max(1, strands); strand++) {
            double phase = strand * (Math.PI * 2.0 / Math.max(1, strands)) + time * (strand % 2 == 0 ? 1.15 : -1.1);

            for (int i = 0; i < samples; i++) {
                float p0 = i / (float) samples;
                float p1 = (i + 1) / (float) samples;

                double a0 = phase + p0 * turns * Math.PI * 2.0;
                double a1 = phase + p1 * turns * Math.PI * 2.0;

                Vec3d s0 = center.add(Math.cos(a0) * radius, p0 * height, Math.sin(a0) * radius);
                Vec3d s1 = center.add(Math.cos(a1) * radius, p1 * height, Math.sin(a1) * radius);

                float mix0 = 0.5f + 0.5f * (float) Math.sin((float) a0 * 0.7f + (float) time);
                float mix1 = 0.5f + 0.5f * (float) Math.sin((float) a1 * 0.7f + (float) time);

                int c0 = mixArgb(colorStart, colorEnd, mix0);
                int c1 = mixArgb(colorStart, colorEnd, mix1);

                float aStart = alpha * (0.92f - p0 * 0.28f);
                float aEnd = alpha * (0.82f - p1 * 0.42f);

                drawRibbon(null, s0, s1, viewDir, width, c0, c1, aStart, aEnd, 2.4f, glow, mixArgb(c0, c1, 0.5f), 0.22f, depth);
            }
        }
    }

    public void drawGlowColumn(Vec3d bottomCenter, double height, float size, int color, float alpha, boolean depth) {
        double time = System.nanoTime() * 1.0E-9;
        int steps = Math.max(5, (int) Math.ceil(height / 0.22));

        for (int i = 0; i <= steps; i++) {
            float p = i / (float) steps;
            float fade = 1.0f - p * 0.55f;
            float pulse = 0.82f + 0.18f * (float) Math.sin(time * 2.1 + p * 7.2);
            float spriteSize = size * (0.85f + (float) Math.sin(p * Math.PI) * 0.65f) * pulse;
            Vec3d pos = bottomCenter.add(0.0, height * p, 0.0);
            drawBloomBillboard(pos, spriteSize, color, alpha * fade, (float) (time * 70.0 + p * 180.0), depth);
        }
    }

    public void drawWaypointPath(Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        Camera cam = mc.getEntityRenderDispatcher().camera;
        Vec3d viewDir = Vec3d.fromPolar(cam.getPitch(), cam.getYaw()).normalize();
        double time = System.nanoTime() * 1.0E-9;
        double len = start.distanceTo(end);
        int seg = Math.max(10, Math.min(72, (int) (len * 4.0)));
        double arc = Math.min(1.45, len * 0.06);

        for (int i = 0; i < seg; i++) {
            float p0 = i / (float) seg;
            float p1 = (i + 1) / (float) seg;

            Vec3d s0 = start.lerp(end, p0).add(0.0, Math.sin(p0 * Math.PI) * arc + Math.sin(time * 2.4 + p0 * 8.0) * 0.025, 0.0);
            Vec3d s1 = start.lerp(end, p1).add(0.0, Math.sin(p1 * Math.PI) * arc + Math.sin(time * 2.4 + p1 * 8.0) * 0.025, 0.0);

            int c0 = mixArgb(colorStart, colorEnd, p0);
            int c1 = mixArgb(colorStart, colorEnd, p1);

            float a0 = 0.92f - p0 * 0.35f;
            float a1 = 0.90f - p1 * 0.45f;

            drawRibbon(null, s0, s1, viewDir, width, c0, c1, a0, a1, 2.8f, 1.1f, mixArgb(c0, c1, 0.5f), 0.26f, depth);
        }

        drawBloomBillboard(end, Math.max(0.55f, width * 7.0f), colorEnd, 0.18f, (float) (time * 90.0), false);
    }

    public void drawWaypointMarker(BlockPos pos, int primaryColor, int secondaryColor, float scale) {
        drawWaypointMarker(Vec3d.ofCenter(pos), primaryColor, secondaryColor, scale);
    }

    public void drawWaypointMarker(Vec3d center, int primaryColor, int secondaryColor, float scale) {
        double time = System.nanoTime() * 1.0E-9;
        float phase = rand01(pointSeed(center, 71)) * 6.2831853f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 2.35 + phase);
        float bob = (float) Math.sin(time * 1.75 + phase) * 0.085f * scale;

        float baseRadius = 0.72f * scale;
        float height = 2.35f * scale + pulse * 0.16f;

        Vec3d base = center.add(0.0, 0.02, 0.0);
        Vec3d top = base.add(0.0, height + bob, 0.0);

        int mixA = mixArgb(primaryColor, secondaryColor, 0.28f);
        int mixB = mixArgb(primaryColor, secondaryColor, 0.72f);

        Box outerBase = new Box(
                base.x - baseRadius * 0.88,
                base.y - 0.016,
                base.z - baseRadius * 0.88,
                base.x + baseRadius * 0.88,
                base.y + 0.016,
                base.z + baseRadius * 0.88
        );

        Box innerBase = new Box(
                base.x - baseRadius * 0.58,
                base.y - 0.010,
                base.z - baseRadius * 0.58,
                base.x + baseRadius * 0.58,
                base.y + 0.010,
                base.z + baseRadius * 0.58
        );

        drawBoxTexturedPortal(null, outerBase, resolvePortalTex2(), 2, true);
        drawBoxDeepPortal(null, innerBase, mixA, true);

        drawSoftRing(base.add(0.0, 0.028 + pulse * 0.012, 0.0), baseRadius * (1.12 + pulse * 0.05), 0.18 * scale,
                mulAlphaArgb(mixA, 0.72f),
                mulAlphaArgb(mixA, 0.0f),
                42,
                false
        );

        drawSoftRing(base.add(0.0, 0.038 + pulse * 0.010, 0.0), baseRadius * (0.78 + pulse * 0.03), 0.09 * scale,
                mulAlphaArgb(0xFFEAF7FF, 0.74f),
                mulAlphaArgb(primaryColor, 0.0f),
                34,
                false
        );

        drawGlowColumn(base.add(0.0, 0.18, 0.0), height * 0.82, 0.55f * scale, mixArgb(primaryColor, secondaryColor, 0.35f), 0.12f, false);
        drawHelixRibbon(base.add(0.0, 0.12, 0.0), baseRadius * 0.56, height * 0.90, 0.070f * scale, primaryColor, secondaryColor, 0.95f, 1.0f, 2, 2, false);
        drawHelixRibbon(base.add(0.0, 0.18, 0.0), baseRadius * 0.34, height * 0.72, 0.040f * scale, 0xFFFFFFFF, mixB, 0.55f, 0.8f, 2, 1, false);

        drawSoftRing(top.add(0.0, -0.10 * scale, 0.0), baseRadius * 0.42, 0.08 * scale,
                mulAlphaArgb(0xFFEFFAFF, 0.88f),
                mulAlphaArgb(secondaryColor, 0.0f),
                28,
                false
        );

        drawBloomBillboard(top, 1.28f * scale, mixB, 0.13f, (float) (-time * 55.0), false);
        drawBloomBillboard(top, 0.72f * scale, 0xFFFFFFFF, 0.56f, (float) (time * 115.0), false);
        drawBloomBillboard(base.add(0.0, 0.08, 0.0), 1.85f * scale, mixA, 0.10f, (float) (time * 36.0), false);

        int orbitCount = 6;
        for (int i = 0; i < orbitCount; i++) {
            float fp = i / (float) orbitCount;
            double ang = time * 1.55 + phase + fp * Math.PI * 2.0;
            double oy = 0.55 + Math.sin(time * 2.0 + fp * 8.0 + phase) * 0.16 + fp * height * 0.42;
            Vec3d orb = base.add(Math.cos(ang) * baseRadius * 0.62, oy, Math.sin(ang) * baseRadius * 0.62);
            int c = mixArgb(primaryColor, secondaryColor, fp);
            drawBloomBillboard(orb, 0.18f * scale, c, 0.34f, (float) (time * 90.0 + i * 40.0), false);
        }

        int crownCount = 4;
        for (int i = 0; i < crownCount; i++) {
            double ang = -time * 1.15 + phase + i * (Math.PI * 2.0 / crownCount);
            Vec3d crown = top.add(Math.cos(ang) * baseRadius * 0.28, Math.sin(time * 2.8 + i) * 0.05 * scale, Math.sin(ang) * baseRadius * 0.28);
            drawBloomBillboard(crown, 0.20f * scale, secondaryColor, 0.28f, (float) (time * 70.0 + i * 60.0), false);
        }
    }

    public void drawBillboardTexture(Vec3d worldPos, Identifier id, float width, float height, Vector4i color, float rotationDeg, boolean additive, boolean depth) {
        MatrixStack.Entry entry = worldBillboardEntry(worldPos, rotationDeg);
        if (additive) {
            drawAdditiveTexture(entry, id, -width * 0.5f, -height * 0.5f, width, height, color, depth);
        } else {
            drawTexture(entry, id, -width * 0.5f, -height * 0.5f, width, height, color, depth);
        }
    }

    public void drawBloomBillboard(Vec3d worldPos, float size, int color, float alpha, float rotationDeg, boolean depth) {
        int c = mulAlphaArgb(color, alpha);
        drawBillboardTexture(worldPos, bloom, size, size, new Vector4i(c, c, c, c), rotationDeg, true, depth);
    }

    private MatrixStack.Entry worldBillboardEntry(Vec3d worldPos, float rotationDeg) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d rel = worldPos.subtract(camera.getPos());
        MatrixStack ms = new MatrixStack();
        ms.translate(rel.x, rel.y, rel.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        if (rotationDeg != 0.0f) {
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotationDeg));
        }
        return ms.peek().copy();
    }

    private int pointSeed(Vec3d pos, int salt) {
        long x = Double.doubleToLongBits(pos.x * 31.0 + salt * 7.0);
        long y = Double.doubleToLongBits(pos.y * 17.0 + salt * 11.0);
        long z = Double.doubleToLongBits(pos.z * 13.0 + salt * 19.0);
        long v = x ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
        return mixHash((int) (v ^ (v >>> 32)));
    }

    private void renderPortalQuads(List<PortalQuad> list, boolean depth) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        double t = System.nanoTime() * 1.0E-9;

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder base = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (PortalQuad q : list) {
            int seed = quadSeed(q.x, q.y, q.w, q.z, 17);
            float s = rand01(seed);
            float p = (float) (t * 0.22 + s * 6.2831853f);

            int c1 = portalPaletteColor(p + 0.0f, 0.46f, 0xFF050712, 0xFF10204A);
            int c2 = portalPaletteColor(p + 1.2f, 0.46f, 0xFF090A1F, 0xFF1D2E68);
            int c3 = portalPaletteColor(p + 2.1f, 0.46f, 0xFF080616, 0xFF16295C);
            int c4 = portalPaletteColor(p + 3.0f, 0.46f, 0xFF04050E, 0xFF11224F);
            vertexQuadGradient(baseQuadEntry(q.entry), base, q.x, q.y, q.w, q.z, c1, c2, c3, c4);
        }
        BufferRenderer.drawWithGlobalProgram(base.end());

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        for (int layer = 0; layer < 6; layer++) {
            BufferBuilder fog = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (PortalQuad q : list) {
                int seed = quadSeed(q.x, q.y, q.w, q.z, 100 + layer * 31);
                float r0 = rand01(seed);
                float r1 = rand01(seed ^ 0x68BC21EB);

                Vec3d center = quadCenter(q.x, q.y, q.w, q.z);
                Vec3d normal = quadNormal(q.x, q.y, q.w, q.z);

                float wave = 0.5f + 0.5f * (float) Math.sin(t * (0.35 + layer * 0.05) + r0 * 6.2831853f);
                float inset = 0.04f + layer * 0.12f + wave * 0.02f;
                double push = 0.00018 + layer * 0.00011 + r1 * 0.00008;

                Vec3d a = lerpToCenter(q.x, center, inset).add(normal.multiply(push));
                Vec3d b = lerpToCenter(q.y, center, inset).add(normal.multiply(push));
                Vec3d c = lerpToCenter(q.w, center, inset).add(normal.multiply(push));
                Vec3d d = lerpToCenter(q.z, center, inset).add(normal.multiply(push));

                float phase = (float) (t * (0.45 + layer * 0.03) + r0 * 4.5 + layer * 0.8);
                float alpha = Math.max(0.03f, 0.18f - layer * 0.018f) * (0.8f + wave * 0.35f);

                int c1 = portalPaletteColor(phase + 0.0f, alpha, 0xFF4A1BFF, 0xFF16D8FF);
                int c2 = portalPaletteColor(phase + 0.9f, alpha, 0xFF7A2BFF, 0xFF4FAEFF);
                int c3 = portalPaletteColor(phase + 1.8f, alpha, 0xFF2A1BCF, 0xFF55F0FF);
                int c4 = portalPaletteColor(phase + 2.7f, alpha, 0xFF6A2DFF, 0xFF1A8FFF);

                vertexQuadGradient(baseQuadEntry(q.entry), fog, a, b, c, d, c1, c2, c3, c4);
            }

            BufferRenderer.drawWithGlobalProgram(fog.end());
        }

        for (int starLayer = 0; starLayer < 2; starLayer++) {
            BufferBuilder stars = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (PortalQuad q : list) {
                MatrixStack.Entry entry = baseQuadEntry(q.entry);
                Vec3d a = q.x;
                Vec3d b = q.y;
                Vec3d d = q.z;
                Vec3d center = quadCenter(q.x, q.y, q.w, q.z);
                Vec3d normal = quadNormal(q.x, q.y, q.w, q.z);

                Vec3d uVec = b.subtract(a);
                Vec3d vVec = d.subtract(a);
                double uLen = uVec.length();
                double vLen = vVec.length();
                if (uLen < 1.0E-6 || vLen < 1.0E-6) continue;

                Vec3d uDir = uVec.multiply(1.0 / uLen);
                Vec3d vDir = vVec.multiply(1.0 / vLen);

                int faceSeed = quadSeed(q.x, q.y, q.w, q.z, 900 + starLayer * 131);
                int count = starLayer == 0 ? 18 : 10;

                for (int i = 0; i < count; i++) {
                    int s = mixHash(faceSeed ^ (i * 0x9E3779B9) ^ (starLayer * 0x7F4A7C15));

                    float ru = rand01(s);
                    float rv = rand01(s ^ 0xA511E9B3);
                    float rr = rand01(s ^ 0x63D83595);
                    float rp = rand01(s ^ 0xC2B2AE35);

                    float tw = 0.45f + 0.55f * (0.5f + 0.5f * (float) Math.sin(t * (0.55 + rr * 1.5 + starLayer * 0.22) + rp * 6.2831853f));
                    float drift = (float) (t * (0.004 + starLayer * 0.002 + rr * 0.003));

                    float u = fract01(ru + drift);
                    float v = fract01(rv + drift * 0.63f);

                    Vec3d p = a.add(uVec.multiply(u)).add(vVec.multiply(v));
                    p = p.add(normal.multiply(0.00035 + starLayer * 0.00022));

                    double faceMin = Math.min(uLen, vLen);
                    double size = faceMin * (0.006 + rr * 0.010) * (starLayer == 0 ? 1.0 : 1.4);

                    int glowColor = mulAlphaArgb(mixArgb(0xFF8FD9FF, 0xFFDCCBFF, rr), 0.22f * tw);
                    int coreColor = mulAlphaArgb(0xFFEAF7FF, 0.75f * tw);

                    writeFaceQuad(stars, entry, p, uDir, vDir, size * 2.3, glowColor);
                    writeFaceQuad(stars, entry, p, uDir, vDir, size, coreColor);

                    if (rr > 0.74f) {
                        writeFaceQuad(stars, entry, p, uDir, vDir, size * 0.45, mulAlphaArgb(0xFFFFFFFF, 0.95f * tw));
                    }
                }

                float haloPulse = 0.6f + 0.4f * (float) Math.sin(t * 0.85 + (faceSeed & 255) * 0.03f);
                Vec3d qa = lerpToCenter(q.x, center, 0.02f + starLayer * 0.08f).add(normal.multiply(0.00012 + starLayer * 0.00009));
                Vec3d qb = lerpToCenter(q.y, center, 0.02f + starLayer * 0.08f).add(normal.multiply(0.00012 + starLayer * 0.00009));
                Vec3d qc = lerpToCenter(q.w, center, 0.02f + starLayer * 0.08f).add(normal.multiply(0.00012 + starLayer * 0.00009));
                Vec3d qd = lerpToCenter(q.z, center, 0.02f + starLayer * 0.08f).add(normal.multiply(0.00012 + starLayer * 0.00009));

                int h1 = mulAlphaArgb(0xFF4A1CFF, 0.06f * haloPulse);
                int h2 = mulAlphaArgb(0xFF0FDFFF, 0.08f * haloPulse);
                int h3 = mulAlphaArgb(0xFF77C6FF, 0.07f * haloPulse);
                int h4 = mulAlphaArgb(0xFF5D2DFF, 0.06f * haloPulse);
                vertexQuadGradient(entry, stars, qa, qb, qc, qd, h1, h2, h3, h4);
            }

            BufferRenderer.drawWithGlobalProgram(stars.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private void renderDeepPortalQuads(List<DeepPortalQuad> list, boolean depth) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        double t = System.nanoTime() * 1.0E-9;

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        BufferBuilder base = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (DeepPortalQuad q : list) {
            float seed = rand01(q.seed);
            float p = (float) (t * 0.16 + seed * 6.2831853f);

            int c1 = portalPaletteColor(p + 0.0f, 0.62f, 0xFF02030A, 0xFF0A1233);
            int c2 = portalPaletteColor(p + 1.0f, 0.62f, 0xFF03040C, 0xFF111B46);
            int c3 = portalPaletteColor(p + 2.0f, 0.62f, 0xFF04050E, 0xFF0C153B);
            int c4 = portalPaletteColor(p + 3.0f, 0.62f, 0xFF020309, 0xFF0A1030);

            vertexQuadGradient(baseQuadEntry(q.entry), base, q.x, q.y, q.w, q.z, c1, c2, c3, c4);
        }
        BufferRenderer.drawWithGlobalProgram(base.end());

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        for (int layer = 0; layer < 12; layer++) {
            BufferBuilder fog = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (DeepPortalQuad q : list) {
                float r0 = rand01(q.seed ^ (layer * 0x45D9F3B));
                float r1 = rand01(q.seed ^ (layer * 0x27D4EB2D));
                float r2 = rand01(q.seed ^ (layer * 0x165667B1));

                Vec3d center = quadCenter(q.x, q.y, q.w, q.z);
                Vec3d normal = quadNormal(q.x, q.y, q.w, q.z);

                float breathe = 0.5f + 0.5f * (float) Math.sin(t * (0.42 + layer * 0.03) + r0 * 6.2831853f);
                float swirl = 0.5f + 0.5f * (float) Math.cos(t * (0.61 + layer * 0.02) + r1 * 6.2831853f);

                float inset = 0.03f + layer * 0.065f + breathe * 0.014f;
                double push = 0.00028 + layer * 0.00012 + swirl * 0.00008;

                Vec3d a = lerpToCenter(q.x, center, inset).add(normal.multiply(push));
                Vec3d b = lerpToCenter(q.y, center, inset).add(normal.multiply(push));
                Vec3d c = lerpToCenter(q.w, center, inset).add(normal.multiply(push));
                Vec3d d = lerpToCenter(q.z, center, inset).add(normal.multiply(push));

                float alpha = Math.max(0.02f, 0.20f - layer * 0.012f) * (0.8f + breathe * 0.35f);
                float phase = (float) (t * (0.33 + layer * 0.018) + r2 * 8.0f + layer * 0.42f);

                int c1 = portalPaletteColor(phase + 0.0f, alpha, 0xFF2E127F, 0xFF0FD3FF);
                int c2 = portalPaletteColor(phase + 0.8f, alpha, 0xFF4519B4, 0xFF4C9CFF);
                int c3 = portalPaletteColor(phase + 1.6f, alpha, 0xFF2A1495, 0xFF6DF7FF);
                int c4 = portalPaletteColor(phase + 2.4f, alpha, 0xFF5521D5, 0xFF1E7DFF);

                vertexQuadGradient(baseQuadEntry(q.entry), fog, a, b, c, d, c1, c2, c3, c4);
            }

            BufferRenderer.drawWithGlobalProgram(fog.end());
        }

        for (int starLayer = 0; starLayer < 3; starLayer++) {
            BufferBuilder stars = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            for (DeepPortalQuad q : list) {
                MatrixStack.Entry entry = baseQuadEntry(q.entry);
                Vec3d a = q.x;
                Vec3d b = q.y;
                Vec3d d = q.z;
                Vec3d center = quadCenter(q.x, q.y, q.w, q.z);
                Vec3d normal = quadNormal(q.x, q.y, q.w, q.z);

                Vec3d uVec = b.subtract(a);
                Vec3d vVec = d.subtract(a);
                double uLen = uVec.length();
                double vLen = vVec.length();
                if (uLen < 1.0E-6 || vLen < 1.0E-6) continue;

                Vec3d uDir = uVec.multiply(1.0 / uLen);
                Vec3d vDir = vVec.multiply(1.0 / vLen);

                int count = starLayer == 0 ? 28 : (starLayer == 1 ? 18 : 9);

                for (int i = 0; i < count; i++) {
                    int s = mixHash(q.seed ^ (starLayer * 0x9E3779B9) ^ (i * 0x7F4A7C15));

                    float ru = rand01(s);
                    float rv = rand01(s ^ 0xA511E9B3);
                    float rr = rand01(s ^ 0x63D83595);
                    float rp = rand01(s ^ 0xC2B2AE35);

                    float drift = (float) (t * (0.003 + starLayer * 0.0015 + rr * 0.002));
                    float u = fract01(ru + drift * (0.8f + starLayer * 0.2f));
                    float v = fract01(rv + drift * 0.47f);

                    float depthInset = 0.02f + starLayer * 0.12f + rr * 0.04f;
                    Vec3d pa = lerpToCenter(a, center, depthInset);
                    Vec3d pb = lerpToCenter(b, center, depthInset);
                    Vec3d pd = lerpToCenter(d, center, depthInset);

                    Vec3d pu = pb.subtract(pa);
                    Vec3d pv = pd.subtract(pa);

                    Vec3d p = pa.add(pu.multiply(u)).add(pv.multiply(v)).add(normal.multiply(0.00035 + starLayer * 0.00025));

                    float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.4 + rr * 1.8 + starLayer * 0.25) + rp * 6.2831853f));
                    double faceMin = Math.min(uLen, vLen);
                    double size = faceMin * (0.0045 + rr * 0.0095) * (1.0 + starLayer * 0.35);

                    int glowColor = mulAlphaArgb(mixArgb(0xFF4AB6FF, 0xFFC7B7FF, rr), (0.16f + starLayer * 0.05f) * tw);
                    int coreColor = mulAlphaArgb(0xFFF8FDFF, (0.60f + starLayer * 0.10f) * tw);

                    writeFaceQuad(stars, entry, p, uDir, vDir, size * 2.8, glowColor);
                    writeFaceQuad(stars, entry, p, uDir, vDir, size, coreColor);

                    if (rr > 0.82f) {
                        writeFaceQuad(stars, entry, p, uDir, vDir, size * 0.42, mulAlphaArgb(0xFFFFFFFF, 0.95f * tw));
                    }
                }
            }

            BufferRenderer.drawWithGlobalProgram(stars.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private void renderTexturedPortalQuads(List<TexturedPortalQuad> list, boolean depth) {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        if (depth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        double t = System.nanoTime() * 1.0E-9;

        Map<Identifier, List<TexturedPortalQuad>> grouped = new LinkedHashMap<>();
        for (TexturedPortalQuad q : list) {
            grouped.computeIfAbsent(q.texture, k -> new ArrayList<>()).add(q);
        }

        for (Map.Entry<Identifier, List<TexturedPortalQuad>> group : grouped.entrySet()) {
            RenderSystem.setShaderTexture(0, group.getKey());
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            BufferBuilder base = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (TexturedPortalQuad q : group.getValue()) {
                AxisPair axes = pickAxes(q.x, q.y, q.w, q.z);
                putTexturedPortalVertex(base, q.entry, q.x, axes, q.scale, (float) t, q.speedU, q.speedV, q.seed, q.color);
                putTexturedPortalVertex(base, q.entry, q.y, axes, q.scale, (float) t, q.speedU, q.speedV, q.seed, q.color);
                putTexturedPortalVertex(base, q.entry, q.w, axes, q.scale, (float) t, q.speedU, q.speedV, q.seed, q.color);
                putTexturedPortalVertex(base, q.entry, q.z, axes, q.scale, (float) t, q.speedU, q.speedV, q.seed, q.color);
            }
            BufferRenderer.drawWithGlobalProgram(base.end());

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            for (int pass = 0; pass < 2; pass++) {
                BufferBuilder glow = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (TexturedPortalQuad q : group.getValue()) {
                    AxisPair axes = pickAxes(q.x, q.y, q.w, q.z);
                    int c = mulAlphaArgb(q.color, pass == 0 ? 0.32f : 0.18f);
                    float scale = q.scale * (pass == 0 ? 1.35f : 2.05f);
                    float su = q.speedU * (pass == 0 ? -0.65f : 0.45f);
                    float sv = q.speedV * (pass == 0 ? 0.75f : -0.55f);
                    float tt = (float) t + (pass == 0 ? 17.37f : 43.91f);
                    int seed = q.seed ^ (pass == 0 ? 0x45D9F3B : 0x27D4EB2D);

                    putTexturedPortalVertex(glow, q.entry, q.x, axes, scale, tt, su, sv, seed, c);
                    putTexturedPortalVertex(glow, q.entry, q.y, axes, scale, tt, su, sv, seed, c);
                    putTexturedPortalVertex(glow, q.entry, q.w, axes, scale, tt, su, sv, seed, c);
                    putTexturedPortalVertex(glow, q.entry, q.z, axes, scale, tt, su, sv, seed, c);
                }
                BufferRenderer.drawWithGlobalProgram(glow.end());
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private MatrixStack.Entry baseQuadEntry(MatrixStack.Entry entry) {
        return entry == null ? lastWorldSpaceMatrix : entry;
    }

    private Vec3d quadCenter(Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        return new Vec3d(
                (a.x + b.x + c.x + d.x) * 0.25,
                (a.y + b.y + c.y + d.y) * 0.25,
                (a.z + b.z + c.z + d.z) * 0.25
        );
    }

    private Vec3d quadNormal(Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        Vec3d ab = b.subtract(a);
        Vec3d ad = d.subtract(a);
        Vec3d n = ab.crossProduct(ad);
        double len = n.length();
        if (len < 1.0E-6) return Vec3d.ZERO;
        return n.multiply(1.0 / len);
    }

    private Vec3d lerpToCenter(Vec3d p, Vec3d center, double t) {
        return p.add(center.subtract(p).multiply(t));
    }

    private void vertexQuadGradient(MatrixStack.Entry entry, BufferBuilder buffer, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int ca, int cb, int cc, int cd) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        buffer.vertex(entry, (float) a.x, (float) a.y, (float) a.z).color(ca);
        buffer.vertex(entry, (float) b.x, (float) b.y, (float) b.z).color(cb);
        buffer.vertex(entry, (float) c.x, (float) c.y, (float) c.z).color(cc);
        buffer.vertex(entry, (float) d.x, (float) d.y, (float) d.z).color(cd);
    }

    private void writeFaceQuad(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d center, Vec3d uDir, Vec3d vDir, double halfSize, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        Vec3d u = uDir.multiply(halfSize);
        Vec3d v = vDir.multiply(halfSize);

        Vec3d a = center.subtract(u).subtract(v);
        Vec3d b = center.add(u).subtract(v);
        Vec3d c = center.add(u).add(v);
        Vec3d d = center.subtract(u).add(v);

        buffer.vertex(entry, (float) a.x, (float) a.y, (float) a.z).color(color);
        buffer.vertex(entry, (float) b.x, (float) b.y, (float) b.z).color(color);
        buffer.vertex(entry, (float) c.x, (float) c.y, (float) c.z).color(color);
        buffer.vertex(entry, (float) d.x, (float) d.y, (float) d.z).color(color);
    }

    private int portalPaletteColor(float phase, float alpha, int colorA, int colorB) {
        float k = 0.5f + 0.5f * (float) Math.sin(phase);
        int c = mixArgb(colorA, colorB, k);
        return mulAlphaArgb(c, alpha);
    }

    private int mixArgb(int a, int b, float t) {
        t = Math.max(0.0f, Math.min(1.0f, t));

        int aa = (a >>> 24) & 255;
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;

        int ba = (b >>> 24) & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;

        int na = (int) (aa + (ba - aa) * t);
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);

        return (na << 24) | (r << 16) | (g << 8) | bl;
    }

    private int mulAlphaArgb(int color, float alphaMul) {
        alphaMul = Math.max(0.0f, Math.min(1.0f, alphaMul));
        int a = (color >>> 24) & 255;
        int na = (int) (a * alphaMul);
        return (color & 0x00FFFFFF) | (na << 24);
    }

    private int quadSeed(Vec3d a, Vec3d b, Vec3d c, Vec3d d, int salt) {
        long x = Double.doubleToLongBits(a.x * 17.0 + b.x * 31.0 + c.x * 13.0 + d.x * 29.0);
        long y = Double.doubleToLongBits(a.y * 19.0 + b.y * 37.0 + c.y * 11.0 + d.y * 23.0);
        long z = Double.doubleToLongBits(a.z * 41.0 + b.z * 43.0 + c.z * 47.0 + d.z * 53.0);
        long v = x ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        v ^= (v >>> 33);
        v *= 0xff51afd7ed558ccdL;
        v ^= (v >>> 33);
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= (v >>> 33);
        return (int) v;
    }

    private int mixHash(int x) {
        x ^= (x >>> 16);
        x *= 0x7feb352d;
        x ^= (x >>> 15);
        x *= 0x846ca68b;
        x ^= (x >>> 16);
        return x;
    }

    private float rand01(int seed) {
        return (mixHash(seed) & 0x00FFFFFF) / 16777215.0f;
    }

    private float fract01(float v) {
        return v - (float) Math.floor(v);
    }

    private Identifier resolveDeepPortalTexture() {
        if (deepPortalTexChecked) {
            return resolvedDeepPortalTex;
        }
        deepPortalTexChecked = true;
        Identifier chosen = bloom;
        try {
            if (mc != null && mc.getResourceManager() != null) {
                if (mc.getResourceManager().getResource(deepPortalTex).isPresent()) {
                    chosen = deepPortalTex;
                }
            }
        } catch (Throwable ignored) {
        }
        resolvedDeepPortalTex = chosen;
        return resolvedDeepPortalTex;
    }

    private Identifier tryRegisterTexture(Identifier src, Identifier runtimeId) {
        Identifier fallback = bloom;
        try {
            if (mc == null || mc.getResourceManager() == null || mc.getTextureManager() == null) return fallback;

            var opt = mc.getResourceManager().getResource(src);
            if (opt.isEmpty()) return fallback;

            NativeImage img;
            try (InputStream in = opt.get().getInputStream()) {
                img = NativeImage.read(in);
            }
            if (img == null || img.getWidth() <= 0 || img.getHeight() <= 0) return fallback;

            mc.getTextureManager().registerTexture(runtimeId, new NativeImageBackedTexture(img));
            return runtimeId;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private Identifier resolvePortalTex1() {
        if (portalTex1Checked) return resolvedPortalTex1;
        portalTex1Checked = true;
        resolvedPortalTex1 = tryRegisterTexture(portalTex1, portalTex1Rt);
        return resolvedPortalTex1;
    }

    private Identifier resolvePortalTex2() {
        if (portalTex2Checked) return resolvedPortalTex2;
        portalTex2Checked = true;
        resolvedPortalTex2 = tryRegisterTexture(portalTex2, portalTex2Rt);
        return resolvedPortalTex2;
    }

    private record AxisPair(int a, int b) {}

    private AxisPair pickAxes(Vec3d p1, Vec3d p2, Vec3d p3, Vec3d p4) {
        double eps = 1e-6;
        boolean xConst = Math.abs(p1.x - p2.x) < eps && Math.abs(p1.x - p3.x) < eps && Math.abs(p1.x - p4.x) < eps;
        boolean yConst = Math.abs(p1.y - p2.y) < eps && Math.abs(p1.y - p3.y) < eps && Math.abs(p1.y - p4.y) < eps;
        if (xConst) return new AxisPair(2, 1);
        if (yConst) return new AxisPair(0, 2);
        return new AxisPair(0, 1);
    }

    private float axisValue(Vec3d p, int axis) {
        return axis == 0 ? (float) p.x : (axis == 1 ? (float) p.y : (float) p.z);
    }

    private float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private void putDeepPortalVertex(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d p, AxisPair axes, float scale, float t, float speed, float seed, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;

        float a = axisValue(p, axes.a);
        float b = axisValue(p, axes.b);

        float u0 = a * scale + (t * speed) + seed;
        float v0 = b * scale + (t * (speed * 0.73f)) - seed * 0.25f;

        float warp = (float) Math.sin((u0 + v0 + t * 0.4f) * 6.2831853f) * 0.06f;
        float u = fract(u0 + warp);
        float v = fract(v0 - warp * 0.85f);

        buffer.vertex(entry, (float) p.x, (float) p.y, (float) p.z).texture(u, v).color(color);
    }

    private void putTexturedPortalVertex(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d p, AxisPair axes, float scale, float t, float speedU, float speedV, int seed, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;

        float a = axisValue(p, axes.a);
        float b = axisValue(p, axes.b);

        float rs0 = rand01(seed);
        float rs1 = rand01(seed ^ 0x68BC21EB);

        float u0 = a * scale + t * speedU + rs0 * 8.0f;
        float v0 = b * scale + t * speedV + rs1 * 8.0f;

        float warp = (float) Math.sin((u0 + v0 + t * 0.35f) * 6.2831853f) * 0.03f;
        float u = 0.02f + fract01(u0 + warp) * 0.96f;
        float v = 0.02f + fract01(v0 - warp * 0.85f) * 0.96f;

        buffer.vertex(entry, (float) p.x, (float) p.y, (float) p.z).texture(u, v).color(color);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width) {
        drawShape(blockPos, voxelShape, color, width, true, false);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        if (SHAPE_BOXES.containsKey(voxelShape)) {
            SHAPE_BOXES.get(voxelShape).forEach(box -> {
                box = box.offset(blockPos);
                if (Projection.canSee(box)) drawBox(box, color, width, true, fill, depth);
            });
            return;
        }
        SHAPE_BOXES.put(voxelShape, voxelShape.getBoundingBoxes());
    }

    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative0(blockPos, voxelShape, color, width, fill, depth, FILL_DEFAULT);
    }

    public void drawShapeAlternativePortal(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative0(blockPos, voxelShape, color, floatClampWidth(width), fill, depth, FILL_PORTAL);
    }

    public void drawShapeAlternativeDeepPortal(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative0(blockPos, voxelShape, color, floatClampWidth(width), fill, depth, FILL_DEEP_PORTAL);
    }

    public void drawShapeAlternativePortalTex1(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative0(blockPos, voxelShape, color, floatClampWidth(width), fill, depth, FILL_PORTAL_TEX1);
    }

    public void drawShapeAlternativePortalTex2(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        drawShapeAlternative0(blockPos, voxelShape, color, floatClampWidth(width), fill, depth, FILL_PORTAL_TEX2);
    }

    private float floatClampWidth(float width) {
        return Math.max(0.1f, width);
    }

    private void drawShapeAlternative0(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth, int fillMode) {
        Vec3d vec3d = Vec3d.of(blockPos);
        if (!Projection.canSee(new Box(blockPos))) {
            return;
        }

        Pair<List<Box>, List<Line>> pair = SHAPE_OUTLINES.get(voxelShape);
        if (pair == null) {
            List<Line> lines = new ArrayList<>();
            voxelShape.forEachEdge((minX, minY, minZ, maxX, maxY, maxZ) ->
                    lines.add(new Line(null, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), 0, 0, 0)));
            pair = new Pair<>(voxelShape.getBoundingBoxes(), lines);
            SHAPE_OUTLINES.put(voxelShape, pair);
        }

        if (fill) {
            for (Box b : pair.getLeft()) {
                Box box = b.offset(vec3d);
                switch (fillMode) {
                    case FILL_PORTAL -> drawBoxPortal(null, box, depth);
                    case FILL_DEEP_PORTAL -> drawBoxDeepPortal(null, box, color, depth);
                    case FILL_PORTAL_TEX1 -> drawBoxTexturedPortal(null, box, resolvePortalTex1(), 1, depth);
                    case FILL_PORTAL_TEX2 -> drawBoxTexturedPortal(null, box, resolvePortalTex2(), 2, depth);
                    default -> drawBox(null, box, color, width, false, true, depth);
                }
            }
        }

        if (fillMode == FILL_DEFAULT) {
            for (Line line : pair.getRight()) {
                drawLine(line.start.add(vec3d), line.end.add(vec3d), color, width, depth);
            }
        }
    }

    private void drawBoxPortal(MatrixStack.Entry entry, Box box, boolean depth) {
        box = box.expand(1e-3);
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        addPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), depth);
        addPortalQuad(entry, new Vec3d(x1, y2, z1), new Vec3d(x1, y2, z2), new Vec3d(x2, y2, z2), new Vec3d(x2, y2, z1), depth);

        addPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), depth);
        addPortalQuad(entry, new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), new Vec3d(x2, y1, z2), depth);

        addPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y1, z1), depth);
        addPortalQuad(entry, new Vec3d(x1, y1, z2), new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), depth);
    }

    private void drawBoxDeepPortal(MatrixStack.Entry entry, Box box, int color, boolean depth) {
        box = box.expand(1e-3);
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        int fillColor = ColorAssist.multAlpha(color, 0.55f);

        addDeepPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), fillColor, seedFrom(box, 1), depth);
        addDeepPortalQuad(entry, new Vec3d(x1, y2, z1), new Vec3d(x1, y2, z2), new Vec3d(x2, y2, z2), new Vec3d(x2, y2, z1), fillColor, seedFrom(box, 2), depth);

        addDeepPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), fillColor, seedFrom(box, 3), depth);
        addDeepPortalQuad(entry, new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), new Vec3d(x2, y1, z2), fillColor, seedFrom(box, 4), depth);

        addDeepPortalQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y1, z1), fillColor, seedFrom(box, 5), depth);
        addDeepPortalQuad(entry, new Vec3d(x1, y1, z2), new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), fillColor, seedFrom(box, 6), depth);
    }

    private void drawBoxTexturedPortal(MatrixStack.Entry entry, Box box, Identifier tex, int variant, boolean depth) {
        double pad = variant == 1 ? 1.0E-3 : 2.6E-3;
        box = box.expand(pad);

        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        int tint = variant == 1 ? mulAlphaArgb(0xFFE8F7FF, 0.46f) : mulAlphaArgb(0xFFF4EAFF, 0.40f);
        float scale = variant == 1 ? 0.95f : 1.35f;
        float speedU = variant == 1 ? 0.014f : -0.010f;
        float speedV = variant == 1 ? -0.009f : 0.016f;
        int seedXor = variant == 1 ? 0 : 0x45D9F3B;

        Vec3d a1 = new Vec3d(x1, y1, z1), b1 = new Vec3d(x2, y1, z1), c1 = new Vec3d(x2, y1, z2), d1 = new Vec3d(x1, y1, z2);
        Vec3d a2 = new Vec3d(x1, y2, z1), b2 = new Vec3d(x1, y2, z2), c2 = new Vec3d(x2, y2, z2), d2 = new Vec3d(x2, y2, z1);
        Vec3d a3 = new Vec3d(x1, y1, z1), b3 = new Vec3d(x1, y1, z2), c3 = new Vec3d(x1, y2, z2), d3 = new Vec3d(x1, y2, z1);
        Vec3d a4 = new Vec3d(x2, y1, z1), b4 = new Vec3d(x2, y2, z1), c4 = new Vec3d(x2, y2, z2), d4 = new Vec3d(x2, y1, z2);
        Vec3d a5 = new Vec3d(x1, y1, z1), b5 = new Vec3d(x1, y2, z1), c5 = new Vec3d(x2, y2, z1), d5 = new Vec3d(x2, y1, z1);
        Vec3d a6 = new Vec3d(x1, y1, z2), b6 = new Vec3d(x2, y1, z2), c6 = new Vec3d(x2, y2, z2), d6 = new Vec3d(x1, y2, z2);

        addTexturedPortalQuad(entry, a1, b1, c1, d1, tex, tint, quadSeed(a1, b1, c1, d1, 11) ^ seedXor, scale, speedU, speedV, depth);
        addTexturedPortalQuad(entry, a2, b2, c2, d2, tex, tint, quadSeed(a2, b2, c2, d2, 12) ^ seedXor, scale, speedU, speedV, depth);
        addTexturedPortalQuad(entry, a3, b3, c3, d3, tex, tint, quadSeed(a3, b3, c3, d3, 13) ^ seedXor, scale, speedU, speedV, depth);
        addTexturedPortalQuad(entry, a4, b4, c4, d4, tex, tint, quadSeed(a4, b4, c4, d4, 14) ^ seedXor, scale, speedU, speedV, depth);
        addTexturedPortalQuad(entry, a5, b5, c5, d5, tex, tint, quadSeed(a5, b5, c5, d5, 15) ^ seedXor, scale, speedU, speedV, depth);
        addTexturedPortalQuad(entry, a6, b6, c6, d6, tex, tint, quadSeed(a6, b6, c6, d6, 16) ^ seedXor, scale, speedU, speedV, depth);
    }

    private int seedFrom(Box box, int salt) {
        long x = Double.doubleToLongBits(box.minX * 31.0 + box.maxX * 17.0);
        long y = Double.doubleToLongBits(box.minY * 29.0 + box.maxY * 13.0);
        long z = Double.doubleToLongBits(box.minZ * 37.0 + box.maxZ * 19.0);
        long v = x ^ (y * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        v ^= (v >>> 33);
        v *= 0xff51afd7ed558ccdL;
        v ^= (v >>> 33);
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= (v >>> 33);
        return (int) v;
    }

    private void addPortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, boolean depth) {
        PortalQuad quad = new PortalQuad(entry, x, y, w, z);
        if (depth) PORTAL_QUAD_DEPTH.add(quad);
        else PORTAL_QUAD.add(quad);
    }

    private void addDeepPortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, int seed, boolean depth) {
        DeepPortalQuad quad = new DeepPortalQuad(entry, x, y, w, z, color, seed);
        if (depth) DEEP_PORTAL_QUAD_DEPTH.add(quad);
        else DEEP_PORTAL_QUAD.add(quad);
    }

    private void addTexturedPortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, Identifier texture, int color, int seed, float scale, float speedU, float speedV, boolean depth) {
        TexturedPortalQuad quad = new TexturedPortalQuad(entry, x, y, w, z, texture, color, seed, scale, speedU, speedV);
        if (depth) TEXTURED_PORTAL_QUAD_DEPTH.add(quad);
        else TEXTURED_PORTAL_QUAD.add(quad);
    }

    public void drawBox(Box box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public void drawBox(Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, depth);
    }

    public void drawBox(MatrixStack.Entry entry, Box box, int color, float width, boolean line, boolean fill, boolean depth) {
        box = box.expand(1e-3);
        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;
        if (fill) {
            int fillColor = ColorAssist.multAlpha(color, 0.1f);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x2, y1, z1), new Vec3d(x2, y1, z2), new Vec3d(x1, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y2, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y1, z1), fillColor, depth);
            drawQuad(entry, new Vec3d(x2, y1, z1), new Vec3d(x2, y2, z1), new Vec3d(x2, y2, z2), new Vec3d(x2, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z2), new Vec3d(x2, y1, z2), new Vec3d(x2, y2, z2), new Vec3d(x1, y2, z2), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y1, z1), new Vec3d(x1, y1, z2), new Vec3d(x1, y2, z2), new Vec3d(x1, y2, z1), fillColor, depth);
            drawQuad(entry, new Vec3d(x1, y2, z1), new Vec3d(x1, y2, z2), new Vec3d(x2, y2, z2), new Vec3d(x2, y2, z1), fillColor, depth);
        }
        if (line) {
            drawLine(entry, x1, y1, z1, x2, y1, z1, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y1, z2, color, width, depth);
            drawLine(entry, x2, y1, z2, x1, y1, z2, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y1, z1, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y1, z1, x1, y2, z1, color, width, depth);
            drawLine(entry, x2, y1, z2, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x1, y2, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x2, y2, z1, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y2, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y2, z2, x1, y2, z1, color, width, depth);
        }
    }

    public void vertexLine(MatrixStack matrices, VertexConsumer buffer, Vec3d start, Vec3d end, int startColor, int endColor) {
        vertexLine(matrices.peek(), buffer, start.toVector3f(), end.toVector3f(), startColor, endColor);
    }

    public void vertexLine(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        Vector3f vec = getNormal(start, end);
        buffer.vertex(entry, start).color(startColor).normal(entry, vec);
        buffer.vertex(entry, end).color(endColor).normal(entry, vec);
    }

    public void vertexQuad(MatrixStack.Entry entry, VertexConsumer buffer, Vec3d vec1, Vec3d vec2, Vec3d vec3, Vec3d vec4, int color) {
        vertexQuad(entry, buffer, vec1.toVector3f(), vec2.toVector3f(), vec3.toVector3f(), vec4.toVector3f(), color);
    }

    public void vertexQuad(MatrixStack.Entry entry, VertexConsumer buffer, Vector3f vec1, Vector3f vec2, Vector3f vec3, Vector3f vec4, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        buffer.vertex(entry, vec1).color(color);
        buffer.vertex(entry, vec2).color(color);
        buffer.vertex(entry, vec3).color(color);
        buffer.vertex(entry, vec4).color(color);
    }

    public void quadTexture(MatrixStack.Entry entry, BufferBuilder buffer, float x, float y, float width, float height, Vector4i color) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        buffer.vertex(entry, x, y + height, 0).texture(0, 0).color(color.x);
        buffer.vertex(entry, x + width, y + height, 0).texture(0, 1).color(color.y);
        buffer.vertex(entry, x + width, y, 0).texture(1, 1).color(color.w);
        buffer.vertex(entry, x, y, 0).texture(1, 0).color(color.z);
    }

    public Vector3f getNormal(Vector3f start, Vector3f end) {
        Vector3f normal = new Vector3f(start).sub(end);
        float sqrt = MathHelper.sqrt(normal.lengthSquared());
        if (sqrt <= 1.0E-6f) return new Vector3f(0, 1, 0);
        return normal.div(sqrt);
    }

    private float prevCubeSize = 0.0f;

    public void drawCube(LivingEntity lastTarget, float anim, float red, String png) {
        float baseSize = red - anim - 0.17F;
        float targetSize = baseSize;

        if (png != null) {
            if ("2".equals(png)) targetSize = red - anim - 0.05F;
            else if ("4".equals(png)) targetSize = red - anim + 0.05F;
            else if ("5".equals(png)) targetSize = red - anim + 0.07F;
        }

        float size = Calculate.interpolate(prevCubeSize, targetSize, 0.2f);
        prevCubeSize = size;

        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d vec = Calculate.interpolate(lastTarget).subtract(camera.getPos());
        MatrixStack matrix = new MatrixStack();
        matrix.push();
        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));
        matrix.translate(vec.x, vec.y + lastTarget.getBoundingBox().getLengthY() / 2, vec.z);
        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(Calculate.interpolate(prevEspValue, espValue)));

        MatrixStack.Entry entry = matrix.peek().copy();
        Render3D.drawTexture(entry, Identifier.of("minecraft", "textures/features/targetesp/capture" + png + ".png"), -size / 2, -size / 2, size, size, ColorAssist.multRedAndAlpha(new Vector4i(TargetESP.getInstance().colorSetting.getColor(), TargetESP.getInstance().colorSetting.getColor(), TargetESP.getInstance().colorSetting.getColor(), TargetESP.getInstance().colorSetting.getColor()), 1 + red * 10, anim), false);
        matrix.pop();
    }

    private final Random random = new Random();
    private final List<Vec3d> particles = new ArrayList<>();

    public void drawCircle(MatrixStack matrix, LivingEntity lastTarget, float anim, float red) {
        double cs = Calculate.interpolate(circleStep - 0.17, circleStep);
        Vec3d target = Calculate.interpolate(lastTarget);
        boolean canSee = Objects.requireNonNull(mc.player).canSee(lastTarget);

        float hitEffect = Math.min(red * 2f, 1f);
        float distanceMultiplier = 1.0f + (float) Math.sin(hitEffect * Math.PI) * 0.18f;

        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();

        if (canSee) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        int size = 64;
        float width = lastTarget.getWidth() * distanceMultiplier;
        float height = lastTarget.getHeight();

        for (int i = 0; i < size; i++) {
            double a1 = (Math.PI * 2.0) * (i / (double) size);
            double a2 = (Math.PI * 2.0) * ((i + 1) / (double) size);

            double yAnim = Calculate.absSinAnimation(cs) * height;
            double yAnim2 = Calculate.absSinAnimation(cs - 0.45) * height;

            double x1 = Math.cos(a1) * width;
            double z1 = Math.sin(a1) * width;
            double x2 = Math.cos(a2) * width;
            double z2 = Math.sin(a2) * width;

            int color = ColorAssist.multRed(TargetESP.getInstance().colorSetting.getColor(), 1 + red * 125);

            Vec3d p1 = target.add(x1, yAnim, z1);
            Vec3d p2 = target.add(x2, yAnim, z2);
            Vec3d p1b = target.add(x1, yAnim2, z1);
            Vec3d p2b = target.add(x2, yAnim2, z2);

            int cTop1 = ColorAssist.multAlpha(color, 0.76F * anim);
            int cTop2 = ColorAssist.multAlpha(color, 0.76F * anim);
            int cBot1 = ColorAssist.multAlpha(color, 0F);
            int cBot2 = ColorAssist.multAlpha(color, 0F);

            MatrixStack.Entry entry = matrix.peek();

            buffer.vertex(entry, (float) p1.x, (float) p1.y, (float) p1.z).color(cTop1);
            buffer.vertex(entry, (float) p1b.x, (float) p1b.y, (float) p1b.z).color(cBot1);
            buffer.vertex(entry, (float) p2.x, (float) p2.y, (float) p2.z).color(cTop2);

            buffer.vertex(entry, (float) p2.x, (float) p2.y, (float) p2.z).color(cTop2);
            buffer.vertex(entry, (float) p1b.x, (float) p1b.y, (float) p1b.z).color(cBot1);
            buffer.vertex(entry, (float) p2b.x, (float) p2b.y, (float) p2b.z).color(cBot2);

            Render3D.drawLine(
                    p1,
                    p2,
                    ColorAssist.multAlpha(color, anim),
                    2,
                    canSee
            );
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
    }

    public void drawGhosts(LivingEntity lastTarget, float anim, float red, float speed) {
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3d targetPos = Calculate.interpolate(lastTarget).subtract(camera.getPos());
        boolean canSee = mc.player.canSee(lastTarget);
        double iAge = Calculate.interpolate(mc.player.age - 1, mc.player.age);
        float halfHeight = lastTarget.getHeight() / 2 + 0.2F;
        float baseWidth = lastTarget.getWidth() + 0.2f;
        float minY = 0.2f;
        float maxY = lastTarget.getHeight() - 0.2F;

        float hitEffect = Math.min(red * 2f, 2f);
        float acceleration = (float) Math.sin(hitEffect * Math.PI) * 0.18f;
        float bany = (float) Math.sin(hitEffect * Math.PI) * -0.04f;

        for (int j = 0; j < 4; j++) {
            for (int i = 0, length = (int) 10.3f; i <= length; i++) {
                double baseAngle = ((i / 2F + iAge * speed * 2.0f) * length + (j * 90)) % (length * 180);
                double radians = Math.toRadians(baseAngle);

                float heightOffset = 0;
                float radiusOffset = 0;

                switch (j) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                        heightOffset = 0f;
                        radiusOffset = 1.04f;
                        break;
                }

                float distanceMultiplier = 1.0f + acceleration;

                double sinQuad = Math.sin(Math.toRadians(iAge * 0.7 + i * (j + halfHeight)) * 1.1) / 2;
                double adjustedSin = (j % 2 == 0) ? sinQuad : -sinQuad;
                double yOffset = minY + (adjustedSin + 0.5) * (maxY - minY) + heightOffset;
                float offset = ((float) (i + length) / (length + length));

                MatrixStack matrices = new MatrixStack();
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

                double finalWidth = baseWidth * distanceMultiplier * radiusOffset;
                matrices.translate(targetPos.x + Math.cos(radians) * finalWidth, targetPos.y + yOffset, targetPos.z + Math.sin(radians) * finalWidth);

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
                MatrixStack.Entry entry = matrices.peek().copy();
                int color = ColorAssist.multRedAndAlpha(TargetESP.getInstance().colorSetting.getColor(), 1 + red * 10, offset * anim);
                float scale = 0.6f * offset * (0.6f + speed * 0.1f) + bany;
                Render3D.drawTexture(entry, Identifier.of("minecraft", "textures/features/particles/bloom.png"), -scale / 2, -scale / 2, scale, scale, new Vector4i(color, color, color, color), canSee);
            }
        }
    }

    private float espValue = 1f, espSpeed = 1f, prevEspValue, circleStep;
    private boolean flipSpeed;

    public void updateTargetEsp() {
        prevEspValue = espValue;
        espValue += espSpeed;
        if (espSpeed > 25) flipSpeed = true;
        if (espSpeed < -25) flipSpeed = false;
        espSpeed = flipSpeed ? espSpeed - 0.5f : espSpeed + 0.5f;
        circleStep += 0.15f;
    }

    public void drawLine(MatrixStack.Entry entry, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(entry, new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), color, color, width, depth);
    }

    public void drawLine(Vec3d start, Vec3d end, int color, float width, boolean depth) {
        drawLine(null, start, end, color, color, width, depth);
    }

    public void drawLine(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width, boolean depth) {
        Line line = new Line(entry, start, end, colorStart, colorEnd, width);
        if (depth) LINE_DEPTH.add(line);
        else LINE.add(line);
    }

    public static void drawCircleQuad(BufferBuilder buffer, MatrixStack.Entry entry, Vec3d center, double radius, int color, int segments) {
        buffer.vertex(entry, (float) center.x, (float) center.y, (float) center.z).color(color);
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            buffer.vertex(entry, (float) (center.x + dx), (float) center.y, (float) (center.z + dz)).color(color);
        }
    }

    public void drawQuad(Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        drawQuad(null, x, y, w, z, color, depth);
    }

    public void drawQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, boolean depth) {
        Quad quad = new Quad(entry, x, y, w, z, color);
        if (depth) QUAD_DEPTH.add(quad);
        else QUAD.add(quad);
    }

    public void drawGradientQuad(Vec3d x, Vec3d y, Vec3d w, Vec3d z, int colorX, int colorY, int colorW, int colorZ, boolean depth) {
        drawGradientQuad(null, x, y, w, z, colorX, colorY, colorW, colorZ, depth);
    }

    public void drawGradientQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int colorX, int colorY, int colorW, int colorZ, boolean depth) {
        GradientQuad quad = new GradientQuad(entry, x, y, w, z, colorX, colorY, colorW, colorZ);
        if (depth) GRADIENT_QUAD_DEPTH.add(quad);
        else GRADIENT_QUAD.add(quad);
    }

    public void drawTexture(MatrixStack.Entry entry, Identifier id, float x, float y, float width, float height, Vector4i color, boolean depth) {
        Texture texture = new Texture(entry, id, x, y, width, height, color);
        if (depth) TEXTURE_DEPTH.add(texture);
        else TEXTURE.add(texture);
    }

    public void drawAdditiveTexture(MatrixStack.Entry entry, Identifier id, float x, float y, float width, float height, Vector4i color, boolean depth) {
        Texture texture = new Texture(entry, id, x, y, width, height, color);
        if (depth) ADDITIVE_TEXTURE_DEPTH.add(texture);
        else ADDITIVE_TEXTURE.add(texture);
    }

    public record Texture(MatrixStack.Entry entry, Identifier id, float x, float y, float width, float height, Vector4i color) {}
    public record Line(MatrixStack.Entry entry, Vec3d start, Vec3d end, int colorStart, int colorEnd, float width) {}
    public record Quad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color) {}
    public record GradientQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int colorX, int colorY, int colorW, int colorZ) {}
    public record PortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z) {}
    public record DeepPortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, int color, int seed) {}
    public record TexturedPortalQuad(MatrixStack.Entry entry, Vec3d x, Vec3d y, Vec3d w, Vec3d z, Identifier texture, int color, int seed, float scale, float speedU, float speedV) {}
    public record Ribbon(MatrixStack.Entry entry, Vec3d start, Vec3d end, Vec3d viewDir, float width, int colorStart, int colorEnd, float alphaStart, float alphaEnd, float pulseSpeed, float glow, int glowColor, float glowAlpha, int seed) {}
}