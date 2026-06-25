package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FTRender extends Module {

    MultiSelectSetting items = new MultiSelectSetting("Предметы", "Что рисовать")
            .value("Дезка", "Явка", "Огненый Заряд", "Божья Аура", "Трапка", "Пласт")
            .selected("Дезка", "Явка", "Огненый Заряд", "Божья Аура", "Трапка", "Пласт");

    static final Identifier BLOOM_TEX = Identifier.of("textures/features/particles/bloom.png");

    static final boolean XRAY = false;

    static final int CIRCLE_STEPS = 640;

    static final float R_BIG = 10.0f;
    static final float R_SMALL = 2.0f;

    static final float CUBE_SIZE = 4.0f;

    static final float PLANE_W = 4.0f;
    static final float PLANE_H = 4.0f;
    static final float PLANE_THICK = 1.0f;
    static final float PLANE_DIST = 4.0f;

    static final float RING_LINE_W = 0.020f;
    static final float EDGE_LINE_W = 0.014f;

    static final float RING_FILL_ALPHA = 0.050f;
    static final float SHAPE_FILL_ALPHA = 0.030f;

    public FTRender() {
        super("FTRender", "FT Render", ModuleCategory.RENDER);
        setup(items);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) return;

        float pt = e.getPartialTicks();

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        if (items.isSelected("Дезка") && (isHolding(main, Items.ENDER_EYE) || isHolding(off, Items.ENDER_EYE))) {
            boolean near = hasOtherPlayerInRadius(mc.player, R_BIG);
            drawFenceRing(e.getStack(), mc.player, pt, R_BIG, near ? 0xFF2ED35F : 0xFF238A3E, near ? 0xFF8DFFB0 : 0xFF58D67C);
            return;
        }

        if (items.isSelected("Явка") && (isHolding(main, Items.SUGAR) || isHolding(off, Items.SUGAR))) {
            boolean near = hasOtherPlayerInRadius(mc.player, R_BIG);
            drawFenceRing(e.getStack(), mc.player, pt, R_BIG, near ? 0xFFF6F6F6 : 0xFFD0D0D0, near ? 0xFFFFFFFF : 0xFFE8E8E8);
            return;
        }

        if (items.isSelected("Огненый Заряд") && (isHolding(main, Items.FIRE_CHARGE) || isHolding(off, Items.FIRE_CHARGE))) {
            boolean near = hasOtherPlayerInRadius(mc.player, R_BIG);
            drawRing(e.getStack(), mc.player, pt, R_BIG, near ? 0xFFFF7A2E : 0xFFCC4A1A, near ? 0xFFFFD27D : 0xFFFF9F62, true, 0.11f, 0.060f);
            return;
        }

        if (items.isSelected("Божья Аура") && (isHolding(main, Items.PHANTOM_MEMBRANE) || isHolding(off, Items.PHANTOM_MEMBRANE))) {
            boolean near = hasOtherPlayerInRadius(mc.player, R_SMALL);
            drawRing(e.getStack(), mc.player, pt, R_SMALL, near ? 0xFF39D7FF : 0xFF1DA6C7, near ? 0xFFB9F2FF : 0xFF6FE4FF, false, 0.0f, 0.0f);
            return;
        }

        if (items.isSelected("Трапка") && (isHolding(main, Items.NETHERITE_SCRAP) || isHolding(off, Items.NETHERITE_SCRAP))) {
            drawCubeAbove(e.getStack(), mc.player, pt, CUBE_SIZE, 0xFFB87436, 0xFFFFB56E);
            return;
        }

        if (items.isSelected("Пласт") && (isHolding(main, Items.DRIED_KELP) || isHolding(off, Items.DRIED_KELP))) {
            drawPlaneSmart(e.getStack(), mc.player, pt, PLANE_W, PLANE_H, PLANE_THICK, 0xFFDDF6FF, 0xFF66DBFF);
        }
    }

    boolean isHolding(ItemStack s, Item item) {
        return s != null && !s.isEmpty() && s.getItem() == item;
    }

    Vec3d lerpPos(PlayerEntity p, float pt) {
        return new Vec3d(
                MathHelper.lerp(pt, p.prevX, p.getX()),
                MathHelper.lerp(pt, p.prevY, p.getY()),
                MathHelper.lerp(pt, p.prevZ, p.getZ())
        );
    }

    boolean hasOtherPlayerInRadius(PlayerEntity self, float radius) {
        Vec3d c = self.getPos().add(0.0, -1.4, 0.0);
        Box box = new Box(c.x - radius, c.y - radius, c.z - radius, c.x + radius, c.y + radius, c.z + radius);
        List<PlayerEntity> ps = mc.world.getEntitiesByClass(PlayerEntity.class, box, p -> p != null && p != self);
        double rr = (double) radius * (double) radius;
        for (PlayerEntity p : ps) {
            if (p.squaredDistanceTo(c) <= rr) return true;
        }
        return false;
    }

    void drawFenceRing(MatrixStack ms, PlayerEntity player, float pt, float radius, int outlineArgb, int glowRgb) {
        Vec3d wpos = lerpPos(player, pt).add(0.0, -1.4, 0.0);
        double ry = wpos.y + 0.028f;

        float fenceH = 2.0f;
        float y0 = 0.0012f;
        float y1 = 0.82f;
        float y2 = 1.50f;
        float y3 = fenceH;

        float railW = 0.022f;
        float postW = 0.020f;
        float wallW = 0.11f;

        int line = withAlpha(outlineArgb, 0.98f);
        int lineSoft = withAlpha(glowRgb, 0.40f);

        int wallCenter0 = withAlpha(glowRgb, 0.05f);
        int wallCenter1 = withAlpha(glowRgb, 0.22f);

        int wallInner0 = withAlpha(glowRgb, 0.00f);
        int wallInner1 = withAlpha(glowRgb, 0.14f);

        int wallOuter0 = withAlpha(glowRgb, 0.00f);
        int wallOuter1 = withAlpha(glowRgb, 0.10f);

        int posts = MathHelper.clamp((int) (radius * 6.0f), 56, 96);
        int postStep = 2;

        beginState(XRAY);
        RenderSystem.disablePolygonOffset();

        ms.push();
        ms.translate(wpos.x, ry, wpos.z);

        Matrix4f m = ms.peek().getPositionMatrix();
        Vec3d camLocal = mc.gameRenderer.getCamera().getPos().subtract(wpos.x, ry, wpos.z);

        drawVerticalRingWall(m, radius, y0, y3, wallCenter0, wallCenter1);
        drawVerticalRingWall(m, radius - wallW * 0.5f, y0, y3, wallInner0, wallInner1);
        drawVerticalRingWall(m, radius + wallW * 0.5f, y0, y3, wallOuter0, wallOuter1);

        drawCircleStripSolid(m, radius - railW * 0.5f, radius + railW * 0.5f, y0, withAlpha(line, 0.90f));
        drawCircleStripSolid(m, radius - railW * 0.5f, radius + railW * 0.5f, y1, withAlpha(line, 0.75f));
        drawCircleStripSolid(m, radius - railW * 0.5f, radius + railW * 0.5f, y2, withAlpha(line, 0.78f));
        drawCircleStripSolid(m, radius - (railW + 0.006f) * 0.5f, radius + (railW + 0.006f) * 0.5f, y3, line);

        for (int i = 0; i < posts; i += postStep) {
            float ang = (float) (i * (Math.PI * 2.0) / posts);
            float x = (float) (Math.sin(ang) * radius);
            float z = (float) (-Math.cos(ang) * radius);

            Vec3d a = new Vec3d(x, y0, z);
            Vec3d b = new Vec3d(x, y3, z);

            drawLineSolid(m, a, b, camLocal, postW, withAlpha(line, 0.88f));
            drawLineSolid(m, a, b, camLocal, postW * 1.5f, withAlpha(lineSoft, 0.28f));
        }

        ms.pop();
        endState();

        beginBloom(XRAY);
        RenderSystem.disablePolygonOffset();

        ms.push();
        ms.translate(wpos.x, ry, wpos.z);

        Matrix4f bm = ms.peek().getPositionMatrix();
        Vec3d bCamLocal = mc.gameRenderer.getCamera().getPos().subtract(wpos.x, ry, wpos.z);

        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.055f, y0, withAlpha(glowRgb, 0.62f));
        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.045f, y1, withAlpha(glowRgb, 0.20f));
        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.050f, y2, withAlpha(glowRgb, 0.22f));

        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.080f, y3, withAlpha(glowRgb, 0.78f));
        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.140f, y3, withAlpha(glowRgb, 0.28f));

        drawBloomRingStripTexY(bm, bCamLocal, radius, 0.18f, (y0 + y3) * 0.55f, withAlpha(glowRgb, 0.07f));

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (int i = 0; i < posts; i += postStep) {
            float ang = (float) (i * (Math.PI * 2.0) / posts);
            float x = (float) (Math.sin(ang) * radius);
            float z = (float) (-Math.cos(ang) * radius);

            Vec3d a = new Vec3d(x, y0, z);
            Vec3d b = new Vec3d(x, y3, z);

            putLineQuadTexBloomWidthOnly(bb, bm, a, b, bCamLocal, 0.040f, withAlpha(glowRgb, 0.16f));
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());

        ms.pop();
        endBloom();
    }

    void drawRing(MatrixStack ms, PlayerEntity player, float pt, float radius, int outlineArgb, int glowRgb, boolean volumetric, float volumeHeight, float sideAlpha) {
        Vec3d wpos = lerpPos(player, pt).add(0.0, -1.4, 0.0);
        double ry = wpos.y + 0.028f;

        int fill = withAlpha(glowRgb, RING_FILL_ALPHA);
        int line = withAlpha(outlineArgb, 0.98f);

        float rIn = radius - RING_LINE_W * 0.5f;
        float rOut = radius + RING_LINE_W * 0.5f;

        beginState(XRAY);

        ms.push();
        ms.translate(wpos.x, ry, wpos.z);

        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder fan = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        fan.vertex(m, 0.0f, 0.0f, 0.0f).color(fill);
        for (int i = 0; i <= CIRCLE_STEPS; i++) {
            float ang = (float) (i * (Math.PI * 2.0) / CIRCLE_STEPS);
            float x = (float) (Math.sin(ang) * radius);
            float z = (float) (-Math.cos(ang) * radius);
            fan.vertex(m, x, 0.0f, z).color(fill);
        }
        BufferRenderer.drawWithGlobalProgram(fan.end());

        drawCircleStripSolid(m, rIn, rOut, 0.0010f, line);

        if (volumetric) {
            float h = Math.max(0.04f, volumeHeight);
            drawCircleStripSolid(m, rIn + 0.004f, rOut + 0.004f, h, withAlpha(outlineArgb, 0.35f));
            drawVerticalRingWall(m, rOut, 0.0010f, h, withAlpha(glowRgb, sideAlpha * 0.70f), withAlpha(glowRgb, sideAlpha));
            drawVerticalRingWall(m, rIn, 0.0010f, h, withAlpha(glowRgb, sideAlpha * 0.40f), withAlpha(glowRgb, sideAlpha * 0.65f));
        }

        ms.pop();
        endState();

        beginBloom(XRAY);

        ms.push();
        ms.translate(wpos.x, ry, wpos.z);

        Matrix4f bm = ms.peek().getPositionMatrix();
        Vec3d camLocal = mc.gameRenderer.getCamera().getPos().subtract(wpos.x, ry, wpos.z);

        drawBloomRingStripTex(bm, camLocal, radius, 0.040f, withAlpha(glowRgb, volumetric ? 0.70f : 0.62f));
        drawBloomRingStripTex(bm, camLocal, radius, 0.080f, withAlpha(glowRgb, volumetric ? 0.38f : 0.32f));
        drawBloomRingStripTex(bm, camLocal, radius, 0.140f, withAlpha(glowRgb, volumetric ? 0.18f : 0.14f));
        drawBloomRingStripTex(bm, camLocal, radius, 0.220f, withAlpha(glowRgb, volumetric ? 0.09f : 0.07f));

        if (volumetric) {
            float h = Math.max(0.04f, volumeHeight);
            drawBloomRingStripTexY(bm, camLocal, radius + 0.004f, 0.050f, h, withAlpha(glowRgb, 0.28f));
            drawBloomRingStripTexY(bm, camLocal, radius + 0.004f, 0.100f, h, withAlpha(glowRgb, 0.14f));
        }

        ms.pop();
        endBloom();
    }

    void drawCubeAbove(MatrixStack ms, PlayerEntity player, float pt, float size, int outlineArgb, int glowRgb) {
        Vec3d p = lerpPos(player, pt);
        Vec3d baseW = p.add(0.0, player.getHeight() + 0.25, 0.0);

        float half = size * 0.5f;

        float[][] v = {
                {-half, -half, -half},
                { half, -half, -half},
                { half,  half, -half},
                {-half,  half, -half},
                {-half, -half,  half},
                { half, -half,  half},
                { half,  half,  half},
                {-half,  half,  half}
        };

        int[][] faces = {
                {0, 1, 2, 3},
                {5, 4, 7, 6},
                {4, 5, 1, 0},
                {3, 2, 6, 7},
                {4, 0, 3, 7},
                {1, 5, 6, 2}
        };

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        int fill = withAlpha(glowRgb, SHAPE_FILL_ALPHA);
        int line = withAlpha(outlineArgb, 0.96f);

        beginState(XRAY);

        ms.push();
        ms.translate(baseW.x, baseW.y, baseW.z);

        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int[] face : faces) {
            for (int idx : face) quads.vertex(m, v[idx][0], v[idx][1], v[idx][2]).color(fill);
        }
        BufferRenderer.drawWithGlobalProgram(quads.end());

        Vec3d camLocal = mc.gameRenderer.getCamera().getPos().subtract(baseW);

        for (int[] ed : edges) {
            Vec3d a = new Vec3d(v[ed[0]][0], v[ed[0]][1], v[ed[0]][2]);
            Vec3d b = new Vec3d(v[ed[1]][0], v[ed[1]][1], v[ed[1]][2]);
            drawLineSolid(m, a, b, camLocal, EDGE_LINE_W, line);
        }

        ms.pop();
        endState();

        beginBloom(XRAY);

        ms.push();
        ms.translate(baseW.x, baseW.y, baseW.z);

        Matrix4f bm = ms.peek().getPositionMatrix();
        Vec3d bCamLocal = mc.gameRenderer.getCamera().getPos().subtract(baseW);

        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.040f, withAlpha(glowRgb, 0.66f));
        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.080f, withAlpha(glowRgb, 0.32f));
        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.140f, withAlpha(glowRgb, 0.14f));
        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.210f, withAlpha(glowRgb, 0.07f));

        ms.pop();
        endBloom();
    }

    void drawPlaneSmart(MatrixStack ms, PlayerEntity player, float pt, float w, float h, float thick, int outlineArgb, int glowRgb) {
        Vec3d p = lerpPos(player, pt);
        Vec3d eyeW = p.add(0.0, player.getStandingEyeHeight(), 0.0);
        Vec3d look = player.getRotationVec(pt);
        Vec3d endW = eyeW.add(look.x * PLANE_DIST, look.y * PLANE_DIST, look.z * PLANE_DIST);

        BlockHitResult hit = mc.world.raycast(new RaycastContext(
                eyeW, endW,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));

        float pitch = player.getPitch(pt);
        float yaw = player.getYaw(pt);

        boolean down = pitch > 45.0f;
        boolean up = pitch < -45.0f;
        boolean horizontal = !down && !up;

        Vec3d centerW;
        Direction face;

        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            face = hit.getSide();
            BlockPos bp = hit.getBlockPos();
            Vec3d c = new Vec3d(bp.getX() + 0.5, bp.getY() + 0.5, bp.getZ() + 0.5);
            double off = 0.501;
            centerW = c.add(face.getOffsetX() * off, face.getOffsetY() * off, face.getOffsetZ() * off);
        } else {
            Vec3d pp = eyeW.add(look.x * PLANE_DIST, look.y * PLANE_DIST, look.z * PLANE_DIST);
            centerW = new Vec3d(Math.floor(pp.x) + 0.5, Math.floor(pp.y) + 0.5, Math.floor(pp.z) + 0.5);
            if (horizontal) face = yawToFace(yaw);
            else if (down) face = Direction.UP;
            else face = Direction.DOWN;
        }

        float hw = w * 0.5f;
        float hh = h * 0.5f;
        float ht = Math.max(0.008f, thick * 0.5f);

        float[][] v;
        if (face == Direction.UP || face == Direction.DOWN) {
            v = new float[][]{
                    {-hw, -ht, -hh},
                    { hw, -ht, -hh},
                    { hw,  ht, -hh},
                    {-hw,  ht, -hh},
                    {-hw, -ht,  hh},
                    { hw, -ht,  hh},
                    { hw,  ht,  hh},
                    {-hw,  ht,  hh}
            };
        } else if (face == Direction.EAST || face == Direction.WEST) {
            v = new float[][]{
                    {-ht, -hh, -hw},
                    { ht, -hh, -hw},
                    { ht,  hh, -hw},
                    {-ht,  hh, -hw},
                    {-ht, -hh,  hw},
                    { ht, -hh,  hw},
                    { ht,  hh,  hw},
                    {-ht,  hh,  hw}
            };
        } else {
            v = new float[][]{
                    {-hw, -hh, -ht},
                    { hw, -hh, -ht},
                    { hw,  hh, -ht},
                    {-hw,  hh, -ht},
                    {-hw, -hh,  ht},
                    { hw, -hh,  ht},
                    { hw,  hh,  ht},
                    {-hw,  hh,  ht}
            };
        }

        int[][] faces = {
                {0, 1, 2, 3},
                {5, 4, 7, 6},
                {4, 5, 1, 0},
                {3, 2, 6, 7},
                {4, 0, 3, 7},
                {1, 5, 6, 2}
        };

        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };

        int fill = withAlpha(glowRgb, 0.055f);
        int line = withAlpha(outlineArgb, 0.97f);

        beginState(XRAY);

        ms.push();
        ms.translate(centerW.x, centerW.y, centerW.z);

        Matrix4f m = ms.peek().getPositionMatrix();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int[] f : faces) {
            for (int idx : f) quads.vertex(m, v[idx][0], v[idx][1], v[idx][2]).color(fill);
        }
        BufferRenderer.drawWithGlobalProgram(quads.end());

        Vec3d camLocal = mc.gameRenderer.getCamera().getPos().subtract(centerW);

        for (int[] ed : edges) {
            Vec3d a = new Vec3d(v[ed[0]][0], v[ed[0]][1], v[ed[0]][2]);
            Vec3d b = new Vec3d(v[ed[1]][0], v[ed[1]][1], v[ed[1]][2]);
            drawLineSolid(m, a, b, camLocal, EDGE_LINE_W, line);
        }

        ms.pop();
        endState();

        beginBloom(XRAY);

        ms.push();
        ms.translate(centerW.x, centerW.y, centerW.z);

        Matrix4f bm = ms.peek().getPositionMatrix();
        Vec3d bCamLocal = mc.gameRenderer.getCamera().getPos().subtract(centerW);

        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.028f, withAlpha(glowRgb, 0.65f));
        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.055f, withAlpha(glowRgb, 0.28f));
        drawBloomEdgesBatch(bm, bCamLocal, v, edges, 0.100f, withAlpha(glowRgb, 0.12f));

        ms.pop();
        endBloom();
    }

    Direction yawToFace(float yawDeg) {
        float y = MathHelper.wrapDegrees(yawDeg);
        if (y >= -45.0f && y < 45.0f) return Direction.SOUTH;
        if (y >= 45.0f && y < 135.0f) return Direction.WEST;
        if (y >= -135.0f && y < -45.0f) return Direction.EAST;
        return Direction.NORTH;
    }

    void beginState(boolean xray) {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ZERO
        );

        if (xray) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        if (!xray) {
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.5f, -1.5f);
        }
    }

    void endState() {
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    void beginBloom(boolean xray) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        if (xray) RenderSystem.disableDepthTest();
        else RenderSystem.enableDepthTest();

        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        if (!xray) {
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-2.5f, -2.5f);
        }

        RenderSystem.setShaderTexture(0, BLOOM_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
    }

    void endBloom() {
        RenderSystem.disablePolygonOffset();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    void drawCircleStripSolid(Matrix4f m, float r0, float r1, float y, int argb) {
        if (((argb >>> 24) & 0xFF) <= 0) return;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= CIRCLE_STEPS; i++) {
            float ang = (float) (i * (Math.PI * 2.0) / CIRCLE_STEPS);
            float sx = (float) Math.sin(ang);
            float cz = (float) -Math.cos(ang);
            bb.vertex(m, sx * r0, y, cz * r0).color(argb);
            bb.vertex(m, sx * r1, y, cz * r1).color(argb);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    void drawVerticalRingWall(Matrix4f m, float radius, float y0, float y1, int c0, int c1) {
        if ((((c0 >>> 24) & 0xFF) <= 0) && (((c1 >>> 24) & 0xFF) <= 0)) return;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= CIRCLE_STEPS; i++) {
            float ang = (float) (i * (Math.PI * 2.0) / CIRCLE_STEPS);
            float sx = (float) Math.sin(ang);
            float cz = (float) -Math.cos(ang);
            float x = sx * radius;
            float z = cz * radius;
            bb.vertex(m, x, y0, z).color(c0);
            bb.vertex(m, x, y1, z).color(c1);
        }
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    void drawBloomRingStripTex(Matrix4f m, Vec3d camLocal, float radius, float width, int argb) {
        drawBloomRingStripTexY(m, camLocal, radius, width, 0.00135f, argb);
    }

    void drawBloomRingStripTexY(Matrix4f m, Vec3d camLocal, float radius, float width, float y, int argb) {
        if (((argb >>> 24) & 0xFF) <= 0) return;

        float r0 = Math.max(0.0f, radius - width * 0.5f);
        float r1 = radius + width * 0.5f;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int i = 0; i <= CIRCLE_STEPS; i++) {
            float ang = (float) (i * (Math.PI * 2.0) / CIRCLE_STEPS);
            float sx = (float) Math.sin(ang);
            float cz = (float) -Math.cos(ang);

            Vec3d pInner = new Vec3d(sx * r0, y, cz * r0);
            Vec3d pOuter = new Vec3d(sx * r1, y, cz * r1);

            Vec3d center = pInner.add(pOuter).multiply(0.5);
            Vec3d dir = pOuter.subtract(pInner);
            double len = dir.length();
            if (len < 1e-6) continue;
            dir = dir.multiply(1.0 / len);

            Vec3d view = camLocal.subtract(center);
            double vlen = view.length();
            if (vlen > 1e-6) view = view.multiply(1.0 / vlen);
            else view = new Vec3d(0, 1, 0);

            double facing = Math.abs(dir.dotProduct(view));
            float aMul = (float) (0.72 + 0.28 * facing);
            int c = withAlpha(argb, aMul);

            bb.vertex(m, (float) pInner.x, (float) pInner.y, (float) pInner.z).texture(0.5f, 0.0f).color(c);
            bb.vertex(m, (float) pOuter.x, (float) pOuter.y, (float) pOuter.z).texture(0.5f, 1.0f).color(c);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    void drawLineSolid(Matrix4f m, Vec3d a, Vec3d b, Vec3d camLocal, float width, int argb) {
        if (((argb >>> 24) & 0xFF) <= 0) return;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        putLineQuadColor(bb, m, a, b, camLocal, width, argb);
        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    void drawBloomEdgesBatch(Matrix4f m, Vec3d camLocal, float[][] v, int[][] edges, float width, int argb) {
        if (((argb >>> 24) & 0xFF) <= 0) return;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (int[] ed : edges) {
            Vec3d a = new Vec3d(v[ed[0]][0], v[ed[0]][1], v[ed[0]][2]);
            Vec3d b = new Vec3d(v[ed[1]][0], v[ed[1]][1], v[ed[1]][2]);
            putLineQuadTexBloomWidthOnly(bb, m, a, b, camLocal, width, argb);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    void putLineQuadColor(BufferBuilder bb, Matrix4f m, Vec3d a, Vec3d b, Vec3d camLocal, float width, int argb) {
        Vec3d side = computeSide(a, b, camLocal, width);
        if (side == null) return;

        Vec3d a1 = a.add(side);
        Vec3d a2 = a.subtract(side);
        Vec3d b1 = b.add(side);
        Vec3d b2 = b.subtract(side);

        bb.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).color(argb);
        bb.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).color(argb);
        bb.vertex(m, (float) b2.x, (float) b2.y, (float) b2.z).color(argb);
        bb.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).color(argb);
    }

    void putLineQuadTexBloomWidthOnly(BufferBuilder bb, Matrix4f m, Vec3d a, Vec3d b, Vec3d camLocal, float width, int argb) {
        Vec3d side = computeSide(a, b, camLocal, width);
        if (side == null) return;

        Vec3d a1 = a.add(side);
        Vec3d a2 = a.subtract(side);
        Vec3d b1 = b.add(side);
        Vec3d b2 = b.subtract(side);

        bb.vertex(m, (float) a1.x, (float) a1.y, (float) a1.z).texture(0.5f, 0.0f).color(argb);
        bb.vertex(m, (float) b1.x, (float) b1.y, (float) b1.z).texture(0.5f, 0.0f).color(argb);
        bb.vertex(m, (float) b2.x, (float) b2.y, (float) b2.z).texture(0.5f, 1.0f).color(argb);
        bb.vertex(m, (float) a2.x, (float) a2.y, (float) a2.z).texture(0.5f, 1.0f).color(argb);
    }

    Vec3d computeSide(Vec3d a, Vec3d b, Vec3d camLocal, float width) {
        Vec3d dir = b.subtract(a);
        double len = dir.length();
        if (len < 1e-6) return null;
        dir = dir.multiply(1.0 / len);

        Vec3d mid = a.add(b).multiply(0.5);
        Vec3d view = camLocal.subtract(mid);
        double vlen = view.length();
        if (vlen < 1e-6) view = new Vec3d(0, 1, 0);
        else view = view.multiply(1.0 / vlen);

        Vec3d side = dir.crossProduct(view);
        double sl = side.length();

        if (sl < 1e-6) {
            side = dir.crossProduct(new Vec3d(0, 1, 0));
            sl = side.length();
            if (sl < 1e-6) {
                side = dir.crossProduct(new Vec3d(1, 0, 0));
                sl = side.length();
                if (sl < 1e-6) return null;
            }
        }

        return side.multiply((width * 0.5) / sl);
    }

    static int withAlpha(int argb, float alphaMul) {
        alphaMul = MathHelper.clamp(alphaMul, 0.0f, 1.0f);
        int a = (argb >>> 24) & 0xFF;
        int na = Math.max(0, Math.min(255, (int) (a * alphaMul)));
        return (na << 24) | (argb & 0x00FFFFFF);
    }
}