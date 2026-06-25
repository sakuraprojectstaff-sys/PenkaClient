package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
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

public final class Trail {

    private final Cosmetic p;

    private final Map<UUID, State> map = new HashMap<>();

    public Trail(Cosmetic parent) {
        this.p = parent;
    }

    public void deactivate() {
        map.clear();
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.bodyTrailOn()) return;

        MatrixStack ms = e.getStack();
        float pt = e.getPartialTicks();
        boolean camFirst = p.mc.options.getPerspective().isFirstPerson();

        boolean allowSelf = Cosmetic.bool(p.bodyTrailSelf);
        boolean allowOthers = Cosmetic.bool(p.bodyTrailOthers);
        boolean allowFirst = Cosmetic.bool(p.bodyTrailFirstPerson);

        for (PlayerEntity pl : p.mc.world.getPlayers()) {
            if (pl == null) continue;
            if (pl.isInvisible()) continue;

            boolean isSelf = pl == p.mc.player;

            if (isSelf && !allowSelf) continue;
            if (!isSelf && !allowOthers) continue;
            if (isSelf && camFirst && !allowFirst) continue;

            renderBodyTrail(ms, pl, pt);
        }
    }

    private void renderBodyTrail(MatrixStack ms, PlayerEntity player, float pt) {
        double px = MathHelper.lerp(pt, player.prevX, player.getX());
        double py = MathHelper.lerp(pt, player.prevY, player.getY());
        double pz = MathHelper.lerp(pt, player.prevZ, player.getZ());

        Vec3d rawPos = new Vec3d(px, py, pz);

        long now = System.currentTimeMillis();

        int limit = Math.max(6, Math.min(90, p.bodyTrailPoints.getInt()));
        long step = Math.max(6L, Math.min(120L, (long) p.bodyTrailStepMs.getInt()));
        long lifeMs = MathHelper.clamp((long) limit * step, 220L, 1800L);

        UUID uuid = player.getUuid();
        State st = map.computeIfAbsent(uuid, k -> new State());

        if (st.birthMs == 0L) st.birthMs = now;
        if (st.lastUpdateMs == 0L) st.lastUpdateMs = now;

        long dmsL = now - st.lastUpdateMs;
        if (dmsL < 0L) dmsL = 0L;
        if (dmsL > 120L) dmsL = 120L;
        st.lastUpdateMs = now;

        st.smoothPos = smooth(st.smoothPos, rawPos, (double) dmsL);

        if (st.lastEmitPos == null) {
            st.lastEmitPos = st.smoothPos;
            st.accMs = 0.0;
            st.q.clear();
            st.q.addFirst(new Pt(st.smoothPos, now));
        } else {
            st.accMs += (double) dmsL;

            double span = Math.max(1.0, st.accMs);
            int loops = (int) Math.floor(span / (double) step);
            if (loops > 8) loops = 8;

            Vec3d from = st.lastEmitPos;
            Vec3d to = st.smoothPos;

            for (int i = 0; i < loops; i++) {
                double t = ((double) (i + 1) * (double) step) / span;
                if (t > 1.0) t = 1.0;
                Vec3d pos = lerp(from, to, t);
                if (st.lastEmitPos == null || st.lastEmitPos.squaredDistanceTo(pos) > 1.0E-8) {
                    st.lastEmitPos = pos;
                    st.q.addFirst(new Pt(pos, now));
                }
            }

            st.accMs = st.accMs - (double) loops * (double) step;
            if (st.accMs < 0.0) st.accMs = 0.0;
        }

        while (!st.q.isEmpty() && now - st.q.peekLast().time > lifeMs) st.q.removeLast();
        while (st.q.size() > limit) st.q.removeLast();

        if (st.q.size() < 2) return;

        float alphaMul = p.bodyTrailAlpha.getValue();
        float width = p.bodyTrailWidth.getValue();
        float lineW = MathHelper.clamp(width * 6.0f, 1.0f, 10.0f);

        float fadeIn = (now - st.birthMs) / 220.0f;
        if (fadeIn < 0.0f) fadeIn = 0.0f;
        if (fadeIn > 1.0f) fadeIn = 1.0f;
        fadeIn = fadeIn * fadeIn;

        int cA = p.bodyTrailColorA.getColor();
        int cB = p.bodyTrailColorB.getColor();
        boolean grad = p.bodyTrailMode.isSelected("Градиент");

        float h = player.getHeight();

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.defaultBlendFunc();
        renderWall(ms, st, h, alphaMul * fadeIn, grad, cA, cB);
        RenderSystem.lineWidth(lineW);
        renderLine(ms, st, h, alphaMul * fadeIn * 0.95f, grad, cA, cB, true);
        renderLine(ms, st, h, alphaMul * fadeIn * 0.95f, grad, cA, cB, false);

        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        renderWall(ms, st, h, alphaMul * fadeIn * 0.55f, grad, cA, cB);
        RenderSystem.lineWidth(MathHelper.clamp(lineW * 1.15f, 1.0f, 12.0f));
        renderLine(ms, st, h, alphaMul * fadeIn * 0.55f, grad, cA, cB, true);
        renderLine(ms, st, h, alphaMul * fadeIn * 0.55f, grad, cA, cB, false);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderWall(MatrixStack ms, State st, float height, float alphaMul, boolean grad, int cA, int cB) {
        int n = st.q.size();
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int idx = 0;
        for (Pt p0 : st.q) {
            float tt = n <= 1 ? 0.0f : (idx / (float) (n - 1));
            float fade = 1.0f - tt;
            fade = fade * fade;

            int col = grad ? lerpArgb(cA, cB, tt) : cA;

            float rr = ((col >>> 16) & 0xFF) / 255.0f;
            float gg = ((col >>> 8) & 0xFF) / 255.0f;
            float bb0 = (col & 0xFF) / 255.0f;

            float a = ((col >>> 24) & 0xFF) / 255.0f;
            a = MathHelper.clamp(a * fade * alphaMul, 0.0f, 1.0f);

            Vec3d pos = p0.pos;

            bb.vertex(m, (float) pos.x, (float) (pos.y + (double) height), (float) pos.z).color(rr, gg, bb0, a);
            bb.vertex(m, (float) pos.x, (float) (pos.y + 5.0E-4), (float) pos.z).color(rr, gg, bb0, a);

            idx++;
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private void renderLine(MatrixStack ms, State st, float height, float alphaMul, boolean grad, int cA, int cB, boolean top) {
        int n = st.q.size();
        Matrix4f m = ms.peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINE_STRIP, VertexFormats.POSITION_COLOR);

        int idx = 0;
        for (Pt p0 : st.q) {
            float tt = n <= 1 ? 0.0f : (idx / (float) (n - 1));
            float fade = MathHelper.clamp(tt, 0.0f, 1.0f);
            if (fade > 1.0f) fade = 1.0f;

            int col = grad ? lerpArgb(cA, cB, tt) : cA;

            float rr = ((col >>> 16) & 0xFF) / 255.0f;
            float gg = ((col >>> 8) & 0xFF) / 255.0f;
            float bb0 = (col & 0xFF) / 255.0f;

            float a = ((col >>> 24) & 0xFF) / 255.0f;
            a = MathHelper.clamp(a * fade * alphaMul, 0.0f, 1.0f);

            Vec3d pos = p0.pos;
            double y = top ? (pos.y + (double) height) : (pos.y + 5.0E-4);

            bb.vertex(m, (float) pos.x, (float) y, (float) pos.z).color(rr, gg, bb0, a);

            idx++;
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private Vec3d smooth(Vec3d prev, Vec3d raw, double dtMs) {
        if (prev == null) return raw;

        double tau = 95.0;
        double a = 1.0 - Math.exp(-dtMs / tau);
        if (a < 0.0) a = 0.0;
        if (a > 1.0) a = 1.0;

        return new Vec3d(
                prev.x + (raw.x - prev.x) * a,
                prev.y + (raw.y - prev.y) * a,
                prev.z + (raw.z - prev.z) * a
        );
    }

    private Vec3d lerp(Vec3d a, Vec3d b, double t) {
        if (t <= 0.0) return a;
        if (t >= 1.0) return b;
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private int lerpArgb(int a, int b, float t) {
        float p0 = MathHelper.clamp(t, 0.0f, 1.0f);

        int aa = (a >>> 24) & 0xFF;
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;

        int ba = (b >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;

        int ra = (int) (aa + (ba - aa) * p0);
        int rr = (int) (ar + (br - ar) * p0);
        int rg = (int) (ag + (bg - ag) * p0);
        int rb = (int) (ab + (bb - ab) * p0);

        ra = MathHelper.clamp(ra, 0, 255);
        rr = MathHelper.clamp(rr, 0, 255);
        rg = MathHelper.clamp(rg, 0, 255);
        rb = MathHelper.clamp(rb, 0, 255);

        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static final class State {
        final ArrayDeque<Pt> q = new ArrayDeque<>();
        long birthMs = 0L;
        long lastUpdateMs = 0L;
        double accMs = 0.0;
        Vec3d smoothPos = null;
        Vec3d lastEmitPos = null;
    }

    private static final class Pt {
        final Vec3d pos;
        final long time;

        Pt(Vec3d pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}
