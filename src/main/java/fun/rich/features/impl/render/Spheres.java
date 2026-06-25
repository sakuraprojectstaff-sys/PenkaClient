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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Spheres {

    private final Cosmetic p;

    private final Map<UUID, TrailState> sphereTrailMap = new HashMap<>();

    private int lastEditIdx = Integer.MIN_VALUE;
    private float allLR = 0.0f;
    private float allFB = 0.0f;
    private final float[] offLR = new float[]{0.0f, 0.0f, 0.0f};
    private final float[] offFB = new float[]{0.0f, 0.0f, 0.0f};

    public Spheres(Cosmetic parent) {
        this.p = parent;
    }

    public void deactivate() {
        sphereTrailMap.clear();
        lastEditIdx = Integer.MIN_VALUE;
        allLR = 0.0f;
        allFB = 0.0f;
        offLR[0] = offLR[1] = offLR[2] = 0.0f;
        offFB[0] = offFB[1] = offFB[2] = 0.0f;
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.spheresOn()) return;

        syncOffsetEditor();

        MatrixStack ms = e.getStack();
        float pt = e.getPartialTicks();
        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        boolean camFirst = p.mc.options.getPerspective().isFirstPerson();

        boolean drawSelf = Cosmetic.bool(p.self);
        boolean drawOthers = Cosmetic.bool(p.others);
        boolean drawFirst = Cosmetic.bool(p.firstPerson);

        for (PlayerEntity pl : p.mc.world.getPlayers()) {
            if (pl == null) continue;
            if (pl.isInvisible()) continue;

            boolean isSelf = pl == p.mc.player;

            if (isSelf) {
                if (!drawSelf) continue;
                if (camFirst && !drawFirst) continue;
            } else {
                if (!drawOthers) continue;
            }

            renderSpheres(ms, cam, camPos, pl, pt);
        }
    }

    private void renderSpheres(MatrixStack ms, Camera cam, Vec3d camPos, PlayerEntity player, float pt) {
        double px = MathHelper.lerp(pt, player.prevX, player.getX());
        double py = MathHelper.lerp(pt, player.prevY, player.getY());
        double pz = MathHelper.lerp(pt, player.prevZ, player.getZ());

        float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, player.prevBodyYaw, player.bodyYaw);
        double bodyYawRad = Math.toRadians(bodyYawDeg);

        Vec3d forward = new Vec3d(-Math.sin(bodyYawRad), 0.0, Math.cos(bodyYawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

        double baseY = py + player.getHeight() * 0.5 + p.height.getValue();
        double headY = py + player.getHeight() + p.height.getValue();

        float baseR = p.radius.getValue();
        float s = p.size.getValue();

        int[] baseCols = new int[]{
                p.exort.getColor(),
                p.wex.getColor(),
                p.quas.getColor()
        };

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;
        float bobAmp = MathHelper.clamp(p.size.getValue() * 0.85f, 0.0f, 0.18f);

        float[] phase = new float[]{0.0f, 2.0943951f, 4.1887903f};
        float[] staticOffsetsDeg = new float[]{180f, -90f, 360f};

        boolean doTrail = !p.effect.isSelected("Нет");
        boolean fireTrail = p.effect.isSelected("Огненный след");
        int limit = Math.max(6, Math.min(48, p.trailLen.getInt()));
        float trailMul = p.trailPower.getValue();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();

        for (int i = 0; i < 3; i++) {
            float ph = phase[i];

            float breatheK = 1.0f + (float) (Math.sin(t * 2.1f + ph) * p.breathe.getValue());
            float rr = baseR * breatheK;

            double sx;
            double sy;
            double sz;
            float alphaMul = 1.0f;

            if (p.mode.isSelected("Орбита")) {
                float ang = (t * p.speed.getValue()) + ph + (float) bodyYawRad;
                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = baseY + Math.sin(t * 2.8f + ph) * bobAmp;
            } else if (p.mode.isSelected("Спираль")) {
                double yTop = headY + p.spiralHeight.getValue();
                double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
                double span = Math.max(0.001, yTop - yBottom);

                float prog = frac(t * (0.18f * p.speed.getValue()) + (i * 0.33333334f));
                float ang = (t * (2.10f * p.speed.getValue())) + ph + (float) bodyYawRad;

                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = yTop - (prog * span);
                alphaMul = spiralFade((float) ((sy - yBottom) / span));
            } else if (p.mode.isSelected("Спираль V2")) {
                double yTop = headY + p.spiralHeight.getValue();
                double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
                double span = Math.max(0.001, yTop - yBottom);

                float prog = frac(t * (0.22f * p.speed.getValue()) + (i * 0.33333334f));
                float rr2 = rr * (0.80f + 0.20f * (float) Math.sin(t * 1.7f + ph));
                float ang = (t * (2.65f * p.speed.getValue())) + (ph * 1.15f) + (float) bodyYawRad;

                sx = px + Math.cos(ang) * rr2;
                sz = pz + Math.sin(ang) * rr2;

                double base = yTop - (prog * span);
                sy = base + Math.sin(t * 1.35f + ph) * (span * 0.04);
                alphaMul = spiralFade((float) ((sy - yBottom) / span));
            } else if (p.mode.isSelected("Волны")) {
                float ang = (t * p.speed.getValue()) + ph + (float) bodyYawRad;
                float w = (float) Math.sin((ang * p.waveFreq.getValue()) + (t * 1.20f));

                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = headY + w * p.waveAmp.getValue() + Math.sin(t * 2.8f + ph) * bobAmp;
            } else {
                double ang = Math.toRadians(bodyYawDeg + staticOffsetsDeg[i]);
                sx = px + Math.cos(ang) * rr;
                sz = pz + Math.sin(ang) * rr;
                sy = baseY + Math.sin(t * 2.8f + ph) * bobAmp;
            }

            float addLR = allLR + offLR[i];
            float addFB = allFB + offFB[i];

            sx += right.x * addLR + forward.x * addFB;
            sz += right.z * addLR + forward.z * addFB;

            Vec3d viewDir = camPos.subtract(sx, sy, sz);
            double len = viewDir.length();
            if (len > 1.0E-6) viewDir = viewDir.multiply(1.0 / len);
            else viewDir = new Vec3d(0, 0, 1);

            int col = withAlphaMul(baseCols[i], alphaMul);

            if (doTrail) {
                pushSphereTrail(player.getUuid(), i, new Vec3d(sx, sy, sz), limit);
                renderSphereTrail(ms, cam, player.getUuid(), i, col, trailMul, fireTrail);
            }

            if (alphaMul > 0.0025f) renderOne(ms, cam, sx, sy, sz, s, col, viewDir);
        }

        if (p.mode.isSelected("Спираль V2")) {
            renderSpiralV2ExtraRight(ms, cam, camPos, px, py, pz, headY, bodyYawRad, forward, right, doTrail, fireTrail, limit, trailMul, player.getUuid());
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderSpiralV2ExtraRight(MatrixStack ms, Camera cam, Vec3d camPos,
                                          double px, double py, double pz,
                                          double headY,
                                          double bodyYawRad,
                                          Vec3d forward, Vec3d right,
                                          boolean doTrail, boolean fireTrail, int limit, float trailMul,
                                          UUID uuid) {

        float t = (System.currentTimeMillis() % 1_000_000L) / 1000.0f;

        double yTop = headY + p.spiralHeight.getValue();
        double yBottom = (py + p.height.getValue()) - p.spiralHeight.getValue();
        double span = Math.max(0.001, yTop - yBottom);

        float baseR = p.radius.getValue();
        float s = p.size.getValue();

        float[] ph = new float[]{0.0f, 2.0943951f, 4.1887903f};

        int[] cols = new int[]{
                p.exort.getColor(),
                p.wex.getColor(),
                p.quas.getColor()
        };

        for (int j = 0; j < 3; j++) {
            float phj = ph[j];

            float prog = frac(t * (0.22f * p.speed.getValue()) + (j * 0.33333334f));
            float rr = baseR * (0.80f + 0.20f * (float) Math.sin(t * 1.7f + phj));

            float ang = (t * (2.65f * p.speed.getValue())) + (phj * 1.15f) + (float) bodyYawRad + (float) Math.PI;

            double sx = px + Math.cos(ang) * rr;
            double sz = pz + Math.sin(ang) * rr;

            double base = yTop - (prog * span);
            double sy = base + Math.sin(t * 1.35f + phj) * (span * 0.04);

            sx += right.x * allLR + forward.x * allFB;
            sz += right.z * allLR + forward.z * allFB;

            Vec3d viewDir = camPos.subtract(sx, sy, sz);
            double len = viewDir.length();
            if (len > 1.0E-6) viewDir = viewDir.multiply(1.0 / len);
            else viewDir = new Vec3d(0, 0, 1);

            float alphaMul = spiralFade((float) ((sy - yBottom) / span));
            int col = withAlphaMul(cols[j], alphaMul);
            int idx = 3 + j;

            if (doTrail) {
                pushSphereTrail(uuid, idx, new Vec3d(sx, sy, sz), limit);
                renderSphereTrail(ms, cam, uuid, idx, col, trailMul, fireTrail);
            }

            if (alphaMul > 0.0025f) renderOne(ms, cam, sx, sy, sz, s, col, viewDir);
        }
    }

    private float spiralFade(float norm) {
        float p0 = MathHelper.clamp(norm, 0.0f, 1.0f);
        float edge = 0.14f;
        float f1 = MathHelper.clamp(p0 / edge, 0.0f, 1.0f);
        float f2 = MathHelper.clamp((1.0f - p0) / edge, 0.0f, 1.0f);
        float a = Math.min(f1, f2);
        return a * a * (3.0f - 2.0f * a);
    }

    private int withAlphaMul(int argb, float mul) {
        int a0 = (argb >>> 24) & 0xFF;
        int a = MathHelper.clamp((int) (a0 * mul), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private void renderOne(MatrixStack ms, Camera cam, double x, double y, double z, float r, int argb, Vec3d viewDir) {
        ms.push();
        ms.translate(x, y, z);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();

        drawSphere(ms, r, argb, viewDir);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, p.defaultTexture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);

        float bloomSize = r * 6.0f;
        int a0 = (argb >>> 24) & 0xFF;
        int bloomA = Math.min(255, (int) (a0 * 0.62f));
        int bloom = (bloomA << 24) | (argb & 0x00FFFFFF);

        drawBillboard(ms, cam, bloomSize, bloom);

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        ms.pop();
    }

    private void drawBillboard(MatrixStack ms, Camera cam, float size, int argb) {
        ms.push();
        ms.multiply(cam.getRotation());

        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float h = size * 0.5f;

        bb.vertex(m, -h, h, 0).texture(0f, 1f).color(argb);
        bb.vertex(m, h, h, 0).texture(1f, 1f).color(argb);
        bb.vertex(m, h, -h, 0).texture(1f, 0f).color(argb);
        bb.vertex(m, -h, -h, 0).texture(0f, 0f).color(argb);

        BufferRenderer.drawWithGlobalProgram(bb.end());

        ms.pop();
    }

    private void drawSphere(MatrixStack ms, float radius, int argb, Vec3d viewDir) {
        int a0 = (argb >>> 24) & 0xFF;
        int r0 = (argb >>> 16) & 0xFF;
        int g0 = (argb >>> 8) & 0xFF;
        int b0 = argb & 0xFF;

        int stacks = 18;
        int slices = 28;

        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < stacks; i++) {
            double v0 = (double) i / (double) stacks;
            double v1 = (double) (i + 1) / (double) stacks;

            double phi0 = (v0 * Math.PI) - (Math.PI / 2.0);
            double phi1 = (v1 * Math.PI) - (Math.PI / 2.0);

            double sy0 = Math.sin(phi0);
            double sy1 = Math.sin(phi1);

            double sr0 = Math.cos(phi0);
            double sr1 = Math.cos(phi1);

            for (int j = 0; j < slices; j++) {
                double u0 = (double) j / (double) slices;
                double u1 = (double) (j + 1) / (double) slices;

                double th0 = u0 * (Math.PI * 2.0);
                double th1 = u1 * (Math.PI * 2.0);

                float x00n = (float) (Math.cos(th0) * sr0);
                float z00n = (float) (Math.sin(th0) * sr0);
                float y00n = (float) sy0;

                float x10n = (float) (Math.cos(th1) * sr0);
                float z10n = (float) (Math.sin(th1) * sr0);
                float y10n = (float) sy0;

                float x01n = (float) (Math.cos(th0) * sr1);
                float z01n = (float) (Math.sin(th0) * sr1);
                float y01n = (float) sy1;

                float x11n = (float) (Math.cos(th1) * sr1);
                float z11n = (float) (Math.sin(th1) * sr1);
                float y11n = (float) sy1;

                int c00 = shade(x00n, y00n, z00n, viewDir, r0, g0, b0, a0);
                int c11 = shade(x11n, y11n, z11n, viewDir, r0, g0, b0, a0);
                int c10 = shade(x10n, y10n, z10n, viewDir, r0, g0, b0, a0);
                int c01 = shade(x01n, y01n, z01n, viewDir, r0, g0, b0, a0);

                bb.vertex(m, x00n * radius, y00n * radius, z00n * radius).color(c00);
                bb.vertex(m, x11n * radius, y11n * radius, z11n * radius).color(c11);
                bb.vertex(m, x10n * radius, y10n * radius, z10n * radius).color(c10);

                bb.vertex(m, x00n * radius, y00n * radius, z00n * radius).color(c00);
                bb.vertex(m, x01n * radius, y01n * radius, z01n * radius).color(c01);
                bb.vertex(m, x11n * radius, y11n * radius, z11n * radius).color(c11);
            }
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private int shade(float nx, float ny, float nz, Vec3d viewDir, int r0, int g0, int b0, int a0) {
        double dot = nx * viewDir.x + ny * viewDir.y + nz * viewDir.z;
        dot = MathHelper.clamp(dot, 0.0, 1.0);

        double rim = 1.0 - dot;
        double rim2 = rim * rim;
        double rim4 = rim2 * rim2;

        double base = 0.40 + 0.60 * dot;
        double add = 0.22 * rim4;

        double light = base + add;
        if (light > 1.0) light = 1.0;

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

    private float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private void pushSphereTrail(UUID uuid, int idx, Vec3d pos, int limit) {
        TrailState st = sphereTrailMap.computeIfAbsent(uuid, k -> new TrailState(6));
        ArrayDeque<TrailPoint> q = st.queues[MathHelper.clamp(idx, 0, st.queues.length - 1)];
        q.addFirst(new TrailPoint(pos, System.currentTimeMillis()));
        while (q.size() > limit) q.removeLast();
    }

    private void renderSphereTrail(MatrixStack ms, Camera cam, UUID uuid, int idx, int argb, float mul, boolean fire) {
        TrailState st = sphereTrailMap.get(uuid);
        if (st == null) return;

        ArrayDeque<TrailPoint> q = st.queues[MathHelper.clamp(idx, 0, st.queues.length - 1)];
        if (q.isEmpty()) return;

        float a0 = ((argb >>> 24) & 0xFF) / 255.0f;
        float r0 = ((argb >>> 16) & 0xFF) / 255.0f;
        float g0 = ((argb >>> 8) & 0xFF) / 255.0f;
        float b0 = (argb & 0xFF) / 255.0f;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, p.defaultTexture);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        int n = q.size();
        int k = 0;

        for (TrailPoint tp : q) {
            float tt = n <= 1 ? 0.0f : (k / (float) (n - 1));
            float fade = 1.0f - tt;
            fade = fade * fade;

            float a = MathHelper.clamp(a0 * (0.60f * fade) * mul, 0.0f, 1.0f);
            float ss = (p.size.getValue() * 4.8f) * (0.55f + 0.70f * fade);

            float rr = r0, gg = g0, bb = b0;
            if (fire) {
                float heat = MathHelper.clamp(0.22f + 0.78f * fade, 0.0f, 1.0f);
                rr = rr * (1.0f - heat) + 1.0f * heat;
                gg = gg * (1.0f - heat) + 0.78f * heat;
                bb = bb * (1.0f - heat) + 0.32f * heat;
                a = MathHelper.clamp(a * 1.18f, 0.0f, 1.0f);
            }

            ms.push();
            ms.translate(tp.pos.x, tp.pos.y, tp.pos.z);
            ms.multiply(cam.getRotation());

            Matrix4f m = ms.peek().getPositionMatrix();
            BufferBuilder bbld = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            float h = ss * 0.5f;

            bbld.vertex(m, -h, h, 0).texture(0f, 1f).color(rr, gg, bb, a);
            bbld.vertex(m, h, h, 0).texture(1f, 1f).color(rr, gg, bb, a);
            bbld.vertex(m, h, -h, 0).texture(1f, 0f).color(rr, gg, bb, a);
            bbld.vertex(m, -h, -h, 0).texture(0f, 0f).color(rr, gg, bb, a);

            BufferRenderer.drawWithGlobalProgram(bbld.end());

            ms.pop();

            k++;
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private void syncOffsetEditor() {
        int idx = getEditIndex();

        if (idx != lastEditIdx) {
            lastEditIdx = idx;
            if (idx < 0) {
                p.editLR.setValue(allLR);
                p.editFB.setValue(allFB);
            } else {
                p.editLR.setValue(offLR[idx]);
                p.editFB.setValue(offFB[idx]);
            }
            return;
        }

        float vLR = p.editLR.getValue();
        float vFB = p.editFB.getValue();

        if (idx < 0) {
            if (!eq(vLR, allLR) || !eq(vFB, allFB)) {
                allLR = vLR;
                allFB = vFB;
            }
        } else {
            if (!eq(vLR, offLR[idx]) || !eq(vFB, offFB[idx])) {
                offLR[idx] = vLR;
                offFB[idx] = vFB;
            }
        }
    }

    private boolean eq(float a, float b) {
        return Math.abs(a - b) <= 1.0E-4f;
    }

    private int getEditIndex() {
        if (p.sphereEditor.isSelected("Экзорт")) return 0;
        if (p.sphereEditor.isSelected("Векс")) return 1;
        if (p.sphereEditor.isSelected("Квас")) return 2;
        return -1;
    }

    private static final class TrailState {
        final ArrayDeque<TrailPoint>[] queues;

        @SuppressWarnings("unchecked")
        TrailState(int count) {
            queues = new ArrayDeque[count];
            for (int i = 0; i < count; i++) queues[i] = new ArrayDeque<>();
        }
    }

    private static final class TrailPoint {
        final Vec3d pos;
        final long time;

        TrailPoint(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}
