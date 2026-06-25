package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class Nimb {

    private final Cosmetic p;

    private final TorusCache torusCache = new TorusCache();
    private final Quaternionf qTmp = new Quaternionf();

    public Nimb(Cosmetic parent) {
        this.p = parent;
    }

    public void deactivate() {
        torusCache.reset();
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.haloOn()) return;

        MatrixStack ms = e.getStack();
        float pt = e.getPartialTicks();
        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        boolean camFirst = p.mc.options.getPerspective().isFirstPerson();

        boolean allowOthers = Cosmetic.bool(p.haloOthers);
        boolean allowFirst = Cosmetic.bool(p.haloFirstPerson);

        double max = Math.max(8.0, Math.min(192.0, p.haloDistance.getValue()));
        double maxSq = max * max;

        for (PlayerEntity pl : p.mc.world.getPlayers()) {
            if (pl == null) continue;
            if (pl.isInvisible()) continue;

            boolean isSelf = pl == p.mc.player;

            if (!isSelf && !allowOthers) continue;
            if (isSelf && camFirst && !allowFirst) continue;

            double px = MathHelper.lerp(pt, pl.prevX, pl.getX());
            double py = MathHelper.lerp(pt, pl.prevY, pl.getY());
            double pz = MathHelper.lerp(pt, pl.prevZ, pl.getZ());

            double hy = py + pl.getHeight() + 0.12 + p.haloYOffset.getValue();

            double dx = camPos.x - px;
            double dy = camPos.y - hy;
            double dz = camPos.z - pz;

            if ((dx * dx + dy * dy + dz * dz) > maxSq) continue;

            float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, pl.prevBodyYaw, pl.bodyYaw);
            double bodyYawRad = Math.toRadians(bodyYawDeg);

            renderHalo(ms, cam, pl, px, py, pz, bodyYawRad);
        }
    }

    private void renderHalo(MatrixStack ms, Camera cam, PlayerEntity player,
                            double px, double py, double pz, double bodyYawRad) {

        double hy = py + player.getHeight() + 0.12 + p.haloYOffset.getValue();

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        float bobAmp = MathHelper.clamp(p.haloThickness.getValue() * 0.8f, 0.0f, 0.12f);
        if (Cosmetic.bool(p.haloAnimate)) hy += Math.sin(t * 2.8f) * bobAmp;

        float majorR = p.haloRadius.getValue();
        float tubeR = Math.max(0.010f, p.haloThickness.getValue() * 0.5f);

        int col = p.haloColor.getColor();

        Vec3d viewDir = cam.getPos().subtract(px, hy, pz);
        double vlen = viewDir.length();
        if (vlen > 1.0E-6) viewDir = viewDir.multiply(1.0 / vlen);
        else viewDir = new Vec3d(0, 0, 1);

        Vec3d lightDir = new Vec3d(0.35, 1.0, 0.25);
        double llen = lightDir.length();
        if (llen > 1.0E-6) lightDir = lightDir.multiply(1.0 / llen);
        else lightDir = new Vec3d(0, 1, 0);

        int majorSeg;
        int minorSeg;
        int glowPasses;

        if (p.haloQuality.isSelected("Низкое")) {
            majorSeg = 64;
            minorSeg = 18;
            glowPasses = 1;
        } else if (p.haloQuality.isSelected("Высокое")) {
            majorSeg = 128;
            minorSeg = 30;
            glowPasses = 3;
        } else if (p.haloQuality.isSelected("Ультра")) {
            majorSeg = 160;
            minorSeg = 36;
            glowPasses = 3;
        } else {
            majorSeg = 96;
            minorSeg = 24;
            glowPasses = 2;
        }

        torusCache.ensure(majorSeg, minorSeg);

        ms.push();
        ms.translate(px, hy, pz);
        qTmp.identity().rotateY((float) bodyYawRad);
        ms.multiply(qTmp);

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        drawHaloTorusFast(ms, majorR, tubeR, col, viewDir, lightDir);

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        if (glowPasses >= 1) {
            int glowA = withAlphaMul(col, 0.34f);
            drawHaloTorusFast(ms, majorR, tubeR * 1.55f, glowA, viewDir, lightDir);
        }
        if (glowPasses >= 2) {
            int glowB = withAlphaMul(col, 0.16f);
            drawHaloTorusFast(ms, majorR, tubeR * 2.35f, glowB, viewDir, lightDir);
        }
        if (glowPasses >= 3) {
            int glowC = withAlphaMul(col, 0.08f);
            drawHaloTorusFast(ms, majorR, tubeR * 3.25f, glowC, viewDir, lightDir);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ms.pop();
    }

    private void drawHaloTorusFast(MatrixStack ms, float majorR, float tubeR, int argb, Vec3d viewDir, Vec3d lightDir) {
        int a0 = (argb >>> 24) & 0xFF;
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;

        int majorSeg = torusCache.majorSeg;
        int minorSeg = torusCache.minorSeg;

        float[] majorCos = torusCache.majorCos;
        float[] majorSin = torusCache.majorSin;
        float[] minorCos = torusCache.minorCos;
        float[] minorSin = torusCache.minorSin;

        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < majorSeg; i++) {
            int i1 = i + 1;
            if (i1 == majorSeg) i1 = 0;

            float ca0 = majorCos[i];
            float sa0 = majorSin[i];
            float ca1 = majorCos[i1];
            float sa1 = majorSin[i1];

            for (int j = 0; j < minorSeg; j++) {
                int j1 = j + 1;
                if (j1 == minorSeg) j1 = 0;

                float cb0 = minorCos[j];
                float sb0 = minorSin[j];
                float cb1 = minorCos[j1];
                float sb1 = minorSin[j1];

                float nxA = cb0 * ca0;
                float nyA = sb0;
                float nzA = cb0 * sa0;

                float nxB = cb0 * ca1;
                float nyB = sb0;
                float nzB = cb0 * sa1;

                float nxC = cb1 * ca1;
                float nyC = sb1;
                float nzC = cb1 * sa1;

                float nxD = cb1 * ca0;
                float nyD = sb1;
                float nzD = cb1 * sa0;

                float ringA = majorR + tubeR * cb0;
                float ringB = majorR + tubeR * cb0;
                float ringC = majorR + tubeR * cb1;
                float ringD = majorR + tubeR * cb1;

                float xA = ringA * ca0;
                float yA = tubeR * sb0;
                float zA = ringA * sa0;

                float xB = ringB * ca1;
                float yB = tubeR * sb0;
                float zB = ringB * sa1;

                float xC = ringC * ca1;
                float yC = tubeR * sb1;
                float zC = ringC * sa1;

                float xD = ringD * ca0;
                float yD = tubeR * sb1;
                float zD = ringD * sa0;

                int cA = shadeHalo(nxA, nyA, nzA, viewDir, lightDir, r0, g0, b0, a0);
                int cB = shadeHalo(nxB, nyB, nzB, viewDir, lightDir, r0, g0, b0, a0);
                int cC = shadeHalo(nxC, nyC, nzC, viewDir, lightDir, r0, g0, b0, a0);
                int cD = shadeHalo(nxD, nyD, nzD, viewDir, lightDir, r0, g0, b0, a0);

                bb.vertex(m, xA, yA, zA).color(cA);
                bb.vertex(m, xB, yB, zB).color(cB);
                bb.vertex(m, xC, yC, zC).color(cC);

                bb.vertex(m, xA, yA, zA).color(cA);
                bb.vertex(m, xC, yC, zC).color(cC);
                bb.vertex(m, xD, yD, zD).color(cD);
            }
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private int shadeHalo(float nx, float ny, float nz, Vec3d viewDir, Vec3d lightDir, int r0, int g0, int b0, int a0) {
        float dv = (float) (nx * viewDir.x + ny * viewDir.y + nz * viewDir.z);
        if (dv < 0.0f) dv = 0.0f;
        if (dv > 1.0f) dv = 1.0f;

        float dl = (float) (nx * lightDir.x + ny * lightDir.y + nz * lightDir.z);
        dl = (dl + 1.0f) * 0.5f;
        if (dl < 0.0f) dl = 0.0f;
        if (dl > 1.0f) dl = 1.0f;

        float rim = 1.0f - dv;
        rim = rim * rim;

        float dv2 = dv * dv;
        float dv4 = dv2 * dv2;
        float dv8 = dv4 * dv4;
        float dv10 = dv8 * dv2;

        float base = 0.22f + 0.68f * dl;
        float spec = dv10 * 0.20f;
        float add = rim * 0.30f;

        float light = base + add + spec;
        if (light > 1.0f) light = 1.0f;

        int r = clamp255((int) (r0 * light));
        int g = clamp255((int) (g0 * light));
        int b = clamp255((int) (b0 * light));

        return ((a0 & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private int clamp255(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    private int withAlphaMul(int argb, float mul) {
        int a0 = (argb >>> 24) & 0xFF;
        int a = MathHelper.clamp((int) (a0 * mul), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static final class TorusCache {
        int majorSeg;
        int minorSeg;
        float[] majorCos;
        float[] majorSin;
        float[] minorCos;
        float[] minorSin;

        void reset() {
            majorSeg = 0;
            minorSeg = 0;
            majorCos = null;
            majorSin = null;
            minorCos = null;
            minorSin = null;
        }

        void ensure(int major, int minor) {
            if (major == majorSeg && minor == minorSeg && majorCos != null && minorCos != null) return;

            majorSeg = Math.max(12, major);
            minorSeg = Math.max(8, minor);

            majorCos = new float[majorSeg];
            majorSin = new float[majorSeg];
            minorCos = new float[minorSeg];
            minorSin = new float[minorSeg];

            for (int i = 0; i < majorSeg; i++) {
                double a = (i / (double) majorSeg) * (Math.PI * 2.0);
                majorCos[i] = (float) Math.cos(a);
                majorSin[i] = (float) Math.sin(a);
            }

            for (int j = 0; j < minorSeg; j++) {
                double b = (j / (double) minorSeg) * (Math.PI * 2.0);
                minorCos[j] = (float) Math.cos(b);
                minorSin[j] = (float) Math.sin(b);
            }
        }
    }
}
