package fun.rich.features.impl.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class PlayerAnimations {

    public static volatile PlayerAnimations INSTANCE;

    final Cosmetic owner;
    final MinecraftClient mc;

    final Deque<SmokePuff> smoke = new ArrayDeque<>();
    final List<Clone> clones = new ArrayList<>();

    long startMs = -1L;
    boolean smokeBurstDone = false;
    boolean clonesSpawned = false;

    final long SIGNS_MS = 980L;
    final long SMOKE_MS = 240L;
    final long APPEAR_MS = 220L;
    final long DISPERSE_MS = 620L;

    long holdMs = 1400L;
    long totalMs = SIGNS_MS + SMOKE_MS + APPEAR_MS + holdMs + DISPERSE_MS;

    final int DEFAULT_CLONES = 4;
    final double DEFAULT_RADIUS = 1.15;

    final int DUEL_CLONES = 2;

    final Identifier smokeTexture = Identifier.of("mre", "textures/oblok.png");
    final Identifier shadowSoundId = Identifier.of("minecraft", "naruto_shadow_clones");
    final Identifier shadowSoundIdFallback = Identifier.of("mre", "naruto_shadow_clones");

    Method dispatcherRenderMethod;
    Mode dispatcherMode = Mode.NONE;

    Vec3d anchorPos = Vec3d.ZERO;
    float anchorYaw = 0.0f;
    boolean anchorSet = false;

    boolean duelMode = false;
    boolean duelFinishSmoke = false;

    Method limbsMethod;
    int limbsMode = 0;

    DuelState duel;

    enum Mode {
        NONE,
        R9,
        R8,
        R10
    }

    enum DuelPhase {
        APPROACH,
        CIRCLE,
        WINDUP,
        STRIKE,
        CLASH,
        RECOIL,
        RESET,
        FINISH
    }

    static final class DuelExchange {
        long circleMs;
        long windupMs;
        long strikeMs;
        long clashMs;
        long recoilMs;
        long resetMs;

        int dir;
        boolean feint;
        boolean dodge;

        DuelExchange(long circleMs, long windupMs, long strikeMs, long clashMs, long recoilMs, long resetMs, int dir, boolean feint, boolean dodge) {
            this.circleMs = circleMs;
            this.windupMs = windupMs;
            this.strikeMs = strikeMs;
            this.clashMs = clashMs;
            this.recoilMs = recoilMs;
            this.resetMs = resetMs;
            this.dir = dir;
            this.feint = feint;
            this.dodge = dodge;
        }

        long totalMs() {
            return circleMs + windupMs + strikeMs + clashMs + recoilMs + resetMs;
        }
    }

    static final class DuelState {
        boolean init = false;

        long lastUpdateMs = 0L;

        DuelPhase phase = DuelPhase.APPROACH;
        long phaseStartMs = 0L;

        int exchangeIdx = 0;
        final List<DuelExchange> exchanges = new ArrayList<>();

        long lastSwingA = -1L;
        long lastSwingB = -1L;
        int lastClashSpark = -1;

        Vec3d curA = Vec3d.ZERO;
        Vec3d curB = Vec3d.ZERO;

        Vec3d velA = Vec3d.ZERO;
        Vec3d velB = Vec3d.ZERO;

        float yawA = 0.0f;
        float yawB = 0.0f;

        float headA = 0.0f;
        float headB = 0.0f;

        float pitchA = 0.0f;
        float pitchB = 0.0f;

        float bodyA = 0.0f;
        float bodyB = 0.0f;

        boolean sneakA = false;
        boolean sneakB = false;

        Vec3d lastAimA = Vec3d.ZERO;
        Vec3d lastAimB = Vec3d.ZERO;
    }

    public PlayerAnimations(Cosmetic owner) {
        this.owner = owner;
        this.mc = MinecraftClient.getInstance();
        INSTANCE = this;
        bindDispatcherRender();
        bindLimbsMethod();
    }

    public static float querySealsK(PlayerEntity p) {
        return querySignsK(p);
    }

    public static float querySignsK(PlayerEntity p) {
        PlayerAnimations a = INSTANCE;
        if (a == null || a.mc == null || a.mc.player == null) return 0.0f;
        if (p == null || a.mc.player.getId() != p.getId()) return 0.0f;
        if (!a.active()) return 0.0f;
        try {
            if (a.mc.options != null && a.mc.options.getPerspective().isFirstPerson()) return 0.0f;
        } catch (Throwable ignored) {
        }
        long dt = a.elapsed(a.now());
        return a.sealsK(dt);
    }

    public void deactivate() {
        startMs = -1L;
        smokeBurstDone = false;
        clonesSpawned = false;
        smoke.clear();
        clones.clear();
        anchorSet = false;
        anchorPos = Vec3d.ZERO;
        anchorYaw = 0.0f;
        duelMode = false;
        duelFinishSmoke = false;
        duel = null;
        holdMs = 1400L;
        totalMs = SIGNS_MS + SMOKE_MS + APPEAR_MS + holdMs + DISPERSE_MS;
    }

    boolean active() {
        return startMs > 0L;
    }

    long now() {
        return System.currentTimeMillis();
    }

    long elapsed(long now) {
        if (startMs <= 0L) return 0L;
        long e = now - startMs;
        if (e < 0L) e = 0L;
        return e;
    }

    float smooth01(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    float ease(float t) {
        return smooth01(MathHelper.clamp(t, 0.0f, 1.0f));
    }

    float sealsK(long e) {
        if (e <= 0L) return 0.0f;
        if (e < SIGNS_MS) return ease((float) e / (float) SIGNS_MS);
        long hold = SIGNS_MS + SMOKE_MS + (APPEAR_MS / 2L);
        if (e < hold) return 1.0f;
        long out = 520L;
        long d = e - hold;
        if (d >= out) return 0.0f;
        return 1.0f - ease((float) d / (float) out);
    }

    float globalFade(long e) {
        long holdEnd = SIGNS_MS + SMOKE_MS + APPEAR_MS + holdMs;
        if (e < holdEnd) return 1.0f;
        long d = e - holdEnd;
        float t = MathHelper.clamp((float) d / (float) DISPERSE_MS, 0.0f, 1.0f);
        return 1.0f - ease(t);
    }

    float clonesAppearK(long e) {
        long a0 = SIGNS_MS + SMOKE_MS;
        long a1 = a0 + APPEAR_MS;
        if (e <= a0) return 0.0f;
        if (e >= a1) return 1.0f;
        float t = (float) (e - a0) / (float) APPEAR_MS;
        return ease(t);
    }

    boolean isBaseSelected() {
        if (owner == null || owner.playerAnimType == null) return false;
        try {
            return owner.playerAnimType.isSelected("Теневые клоны");
        } catch (Throwable ignored) {
        }
        return false;
    }

    boolean isDuelSelected() {
        if (owner == null || owner.playerAnimType == null) return false;
        try {
            if (owner.playerAnimType.isSelected("Теневые клоны: дуэль")) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (owner.playerAnimType.isSelected("Техника теневого клонирования (дуэль)")) return true;
        } catch (Throwable ignored) {
        }
        try {
            if (owner.playerAnimType.isSelected("Дуэль клонов")) return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    boolean isAnySelected() {
        return isBaseSelected() || isDuelSelected();
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (mc.world == null || mc.player == null) return;
        if (!owner.playerAnimOn()) return;
        if (!isAnySelected()) return;

        if (owner.pollPlayerAnim()) {
            begin();
        }

        if (!active()) return;

        long n = now();
        long dt = elapsed(n);

        if (dt >= totalMs) {
            deactivate();
            return;
        }

        if (!smokeBurstDone && dt >= SIGNS_MS) {
            smokeBurstDone = true;
            spawnSmokeBurst(n);
        }

        if (!clonesSpawned && dt >= (SIGNS_MS + SMOKE_MS)) {
            clonesSpawned = true;
            spawnClones(n);
        }

        try {
            if (mc.options != null && mc.options.getPerspective().isFirstPerson()) return;
        } catch (Throwable ignored) {
        }

        updateAndRenderSmoke(e, n);
        renderClones(e, n);
    }

    void begin() {
        startMs = now();
        smokeBurstDone = false;
        clonesSpawned = false;
        smoke.clear();
        clones.clear();

        duelMode = isDuelSelected();
        duelFinishSmoke = false;

        holdMs = duelMode ? 12800L : 1400L;
        totalMs = SIGNS_MS + SMOKE_MS + APPEAR_MS + holdMs + DISPERSE_MS;

        if (mc.player != null) {
            anchorPos = mc.player.getPos();
            float y;
            try {
                y = mc.player.getBodyYaw();
            } catch (Throwable ignored) {
                try {
                    y = mc.player.getYaw();
                } catch (Throwable ignored2) {
                    y = 0.0f;
                }
            }
            anchorYaw = y;
            anchorSet = true;
        } else {
            anchorPos = Vec3d.ZERO;
            anchorYaw = 0.0f;
            anchorSet = false;
        }

        duel = duelMode ? new DuelState() : null;

        playTechniqueSound();

        if (dispatcherMode == Mode.NONE || dispatcherRenderMethod == null) bindDispatcherRender();
        if (limbsMethod == null) bindLimbsMethod();
    }

    void playTechniqueSound() {
        try {
            if (mc.getSoundManager() == null) return;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(shadowSoundId), 1.0f));
            return;
        } catch (Throwable ignored) {
        }
        try {
            if (mc.getSoundManager() == null) return;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvent.of(shadowSoundIdFallback), 1.0f));
        } catch (Throwable ignored) {
        }
    }

    void spawnSmokeBurst(long t) {
        Vec3d p = anchorSet ? anchorPos : (mc.player != null ? mc.player.getPos() : Vec3d.ZERO);

        for (int i = 0; i < 18; i++) {
            double ang = (Math.PI * 2.0) * ((double) i / 18.0);
            double r = 0.18 + (i % 6) * 0.06;
            double ox = Math.cos(ang) * r;
            double oz = Math.sin(ang) * r;
            double oy = 0.04 + (i % 4) * 0.06;
            float base = 0.55f + (i % 6) * 0.10f;
            long life = 560L + (i % 7) * 70L;
            smoke.add(new SmokePuff(p.x + ox, p.y + oy, p.z + oz, t, life, base, base * (1.85f + (i % 3) * 0.28f)));
        }

        for (int i = 0; i < 10; i++) {
            double ang = (Math.PI * 2.0) * ((double) i / 10.0);
            double r = 0.54 + (i % 3) * 0.12;
            double ox = Math.cos(ang) * r;
            double oz = Math.sin(ang) * r;
            double oy = 0.10 + (i % 3) * 0.08;
            float base = 0.92f + (i % 4) * 0.12f;
            long life = 820L + (i % 5) * 90L;
            smoke.add(new SmokePuff(p.x + ox, p.y + oy, p.z + oz, t, life, base, base * (2.35f + (i % 2) * 0.35f)));
        }
    }

    void spawnClones(long t) {
        if (mc.player == null || mc.world == null) return;

        PlayerEntity self = mc.player;
        GameProfile profile = self.getGameProfile();

        clones.clear();

        if (duelMode) {
            Vec3d base = anchorSet ? anchorPos : self.getPos();
            float cy = anchorSet ? anchorYaw : self.getYaw();

            Vec3d f = forwardFromYaw(cy);
            Vec3d r = new Vec3d(f.z, 0.0, -f.x);

            Vec3d center = base.add(f.multiply(2.20));
            Vec3d aPos = center.add(r.multiply(1.10));
            Vec3d bPos = center.add(r.multiply(-1.10));

            OtherClientPlayerEntity a = new OtherClientPlayerEntity(mc.world, profile);
            OtherClientPlayerEntity b = new OtherClientPlayerEntity(mc.world, profile);

            setupCloneEntity(a);
            setupCloneEntity(b);

            equipSword(a);
            equipSword(b);

            clones.add(new Clone(a, aPos.x - base.x, aPos.z - base.z, 0.0f, (t ^ 0x2A17C9B1L)));
            clones.add(new Clone(b, bPos.x - base.x, bPos.z - base.z, 0.0f, (t ^ 0x6F4D13A7L)));

            return;
        }

        for (int i = 0; i < DEFAULT_CLONES; i++) {
            OtherClientPlayerEntity c = new OtherClientPlayerEntity(mc.world, profile);
            setupCloneEntity(c);

            double a = (Math.PI * 2.0) * ((double) i / (double) DEFAULT_CLONES);
            double r = DEFAULT_RADIUS + (((t + i * 37L) & 7L) / 7.0) * 0.35;
            double ox = Math.cos(a) * r;
            double oz = Math.sin(a) * r;
            float yawOff = (float) Math.toDegrees(a) + 180.0f;

            clones.add(new Clone(c, ox, oz, yawOff, (t ^ (i * 1315423911L))));
        }
    }

    void setupCloneEntity(OtherClientPlayerEntity c) {
        try {
            c.setCustomNameVisible(false);
        } catch (Throwable ignored) {
        }
        try {
            c.setPose(EntityPose.STANDING);
        } catch (Throwable ignored) {
        }
        try {
            c.setInvisible(false);
        } catch (Throwable ignored) {
        }
        try {
            c.setSneaking(false);
        } catch (Throwable ignored) {
        }
    }

    void equipSword(OtherClientPlayerEntity e) {
        try {
            e.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        } catch (Throwable ignored) {
        }
        try {
            e.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        } catch (Throwable ignored) {
        }
    }

    Vec3d forwardFromYaw(float yaw) {
        float y = yaw * ((float) Math.PI / 180.0f);
        double sx = -MathHelper.sin(y);
        double cz = MathHelper.cos(y);
        return new Vec3d(sx, 0.0, cz);
    }

    float yawFromDelta(double dx, double dz) {
        return (float) (MathHelper.atan2(dz, dx) * 57.2957763671875) - 90.0f;
    }

    void updateAndRenderSmoke(WorldRenderEvent e, long n) {
        long dt = elapsed(n);

        boolean cloakCenter = dt >= SIGNS_MS && dt < (SIGNS_MS + SMOKE_MS);
        boolean cloakClones = dt >= (SIGNS_MS + SMOKE_MS) && dt < (SIGNS_MS + SMOKE_MS + APPEAR_MS);

        if (smoke.isEmpty() && !cloakCenter && !cloakClones) return;

        MatrixStack ms = eventStack(e);
        if (ms == null) return;

        Identifier tex = smokeTexture;

        float cy = mc.gameRenderer.getCamera().getYaw();
        float cp = mc.gameRenderer.getCamera().getPitch();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.blendFuncSeparate(
                GlStateManager.SrcFactor.SRC_ALPHA,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SrcFactor.ONE,
                GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA
        );

        Tessellator tess = Tessellator.getInstance();

        if (!smoke.isEmpty()) {
            RenderSystem.depthMask(false);

            BufferBuilder bb = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            smoke.removeIf(p -> (n - p.startMs) >= p.lifeMs);

            for (SmokePuff p : smoke) {
                long age = n - p.startMs;
                float t = MathHelper.clamp((float) age / (float) p.lifeMs, 0.0f, 1.0f);

                float aIn = ease(MathHelper.clamp(t / 0.16f, 0.0f, 1.0f));
                float aOut = 1.0f - ease(MathHelper.clamp((t - 0.22f) / 0.78f, 0.0f, 1.0f));
                float alpha = MathHelper.clamp(aIn * aOut, 0.0f, 1.0f);
                alpha = (float) Math.pow(alpha, 0.70);

                float sCurve = ease(t);
                float scaleBase = MathHelper.lerp(sCurve, p.scaleFrom, p.scaleTo) * 1.18f;

                double driftX = Math.sin((p.seed * 0.31 + age * 0.00315)) * 0.038;
                double driftZ = Math.cos((p.seed * 0.27 + age * 0.00300)) * 0.038;
                double driftY = 0.020 + t * 0.105;

                float spin = (float) ((p.seed & 2047L) * 0.176 + age * 0.020);

                long h = p.seed ^ (age * 1315423911L);
                boolean fu = ((h >>> 7) & 1L) != 0L;
                boolean fv = ((h >>> 8) & 1L) != 0L;

                int a0 = (int) (MathHelper.clamp(alpha, 0.0f, 1.0f) * 255.0f);
                if (a0 < 0) a0 = 0;
                if (a0 > 255) a0 = 255;

                ms.push();
                ms.translate(p.x + driftX, p.y + driftY, p.z + driftZ);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cy));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cp));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
                ms.scale(scaleBase, scaleBase, scaleBase);

                float s = 0.62f;
                var m = ms.peek().getPositionMatrix();

                float u0 = fu ? 1.0f : 0.0f;
                float u1 = fu ? 0.0f : 1.0f;
                float v0 = fv ? 1.0f : 0.0f;
                float v1 = fv ? 0.0f : 1.0f;

                bb.vertex(m, -s, -s, 0.0f).texture(u0, v0).color(255, 255, 255, a0);
                bb.vertex(m, s, -s, 0.0f).texture(u1, v0).color(255, 255, 255, a0);
                bb.vertex(m, s, s, 0.0f).texture(u1, v1).color(255, 255, 255, a0);
                bb.vertex(m, -s, s, 0.0f).texture(u0, v1).color(255, 255, 255, a0);

                ms.pop();
            }

            BufferRenderer.drawWithGlobalProgram(bb.end());
        }

        if (cloakCenter || cloakClones) {
            RenderSystem.depthMask(true);

            BufferBuilder bb = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            if (cloakCenter) {
                float t = (float) (dt - SIGNS_MS) / (float) SMOKE_MS;
                float k = 1.0f - ease(t);
                Vec3d base = anchorSet ? anchorPos : (mc.player != null ? mc.player.getPos() : Vec3d.ZERO);
                renderSmokeColumn(ms, bb, base.x, base.y, base.z, cy, cp, 1.60f, MathHelper.clamp(1.0f * k, 0.0f, 1.0f), (n ^ 0x6F12A9B31L));
            } else {
                float t = (float) (dt - (SIGNS_MS + SMOKE_MS)) / (float) APPEAR_MS;
                float k = 1.0f - ease(t);

                float tickDelta = eventPartialTicks(e);
                Vec3d base = anchorSet ? anchorPos : (mc.player != null ? mc.player.getLerpedPos(tickDelta) : Vec3d.ZERO);

                if (clonesSpawned && !clones.isEmpty()) {
                    for (Clone c : clones) {
                        double wx = base.x + c.offX;
                        double wy = base.y;
                        double wz = base.z + c.offZ;
                        long seed = (c.seed ^ (startMs * 1315423911L)) + (long) (c.offX * 99991.0) + (long) (c.offZ * 49999.0);
                        renderSmokeColumn(ms, bb, wx, wy, wz, cy, cp, 1.86f, MathHelper.clamp(1.0f * k, 0.0f, 1.0f), seed);
                    }
                } else {
                    for (int i = 0; i < DEFAULT_CLONES; i++) {
                        double a = (Math.PI * 2.0) * ((double) i / (double) DEFAULT_CLONES);
                        double r = DEFAULT_RADIUS + (((n + i * 37L) & 7L) / 7.0) * 0.35;
                        double wx = base.x + Math.cos(a) * r;
                        double wy = base.y;
                        double wz = base.z + Math.sin(a) * r;
                        long seed = (n ^ (i * 1315423911L));
                        renderSmokeColumn(ms, bb, wx, wy, wz, cy, cp, 1.86f, MathHelper.clamp(1.0f * k, 0.0f, 1.0f), seed);
                    }
                }
            }

            BufferRenderer.drawWithGlobalProgram(bb.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    void renderSmokeColumn(MatrixStack ms,
                           BufferBuilder bb,
                           double x,
                           double y,
                           double z,
                           float cy,
                           float cp,
                           float baseScale,
                           float alpha,
                           long seed) {
        if (alpha <= 0.001f) return;

        float aa = MathHelper.clamp(alpha, 0.0f, 1.0f);
        aa = (float) Math.pow(aa, 0.60);

        int col = 255;
        int a0 = (int) (aa * 255.0f);
        if (a0 < 0) a0 = 0;
        if (a0 > 255) a0 = 255;

        long time = now() - startMs;

        double t0 = (seed & 1023L) * 0.006135923151542565;
        double wob = Math.sin(time * 0.0078 + t0) * 0.070;
        double px = x + Math.cos(t0) * wob;
        double pz = z + Math.sin(t0) * wob;

        float rotBase = (float) (((seed >>> 11) & 2047L) * 0.185 + time * 0.026);

        int volSlices = 14;
        float h0 = -0.08f;
        float h1 = 2.05f;

        for (int i = 0; i < volSlices; i++) {
            float k = (float) i / (float) (volSlices - 1);
            float yy = MathHelper.lerp(k, h0, h1);

            float bell = 1.0f - MathHelper.abs(k - 0.44f) / 0.44f;
            if (bell < 0.0f) bell = 0.0f;

            float sc = baseScale * (1.62f + 0.95f * bell) * (1.10f - 0.20f * k);
            float aMul = (0.98f - 0.64f * k) * (0.82f + 0.18f * bell);
            int a = (int) (a0 * aMul);
            if (a < 0) a = 0;
            if (a > 255) a = 255;

            float spin = rotBase + i * 14.0f;

            long h = seed ^ (i * 2654435761L);
            boolean fu = ((h >>> 7) & 1L) != 0L;
            boolean fv = ((h >>> 8) & 1L) != 0L;

            ms.push();
            ms.translate(px, y + yy, pz);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cy));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cp));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spin));
            ms.scale(sc, sc, sc);

            float s = 0.96f;
            var m = ms.peek().getPositionMatrix();

            float u0 = fu ? 1.0f : 0.0f;
            float u1 = fu ? 0.0f : 1.0f;
            float v0 = fv ? 1.0f : 0.0f;
            float v1 = fv ? 0.0f : 1.0f;

            bb.vertex(m, -s, -s, 0.0f).texture(u0, v0).color(col, col, col, a);
            bb.vertex(m, s, -s, 0.0f).texture(u1, v0).color(col, col, col, a);
            bb.vertex(m, s, s, 0.0f).texture(u1, v1).color(col, col, col, a);
            bb.vertex(m, -s, s, 0.0f).texture(u0, v1).color(col, col, col, a);

            ms.pop();
        }
    }

    void renderClones(WorldRenderEvent e, long n) {
        if (clones.isEmpty() || mc.player == null) return;

        long dt = elapsed(n);
        if (dt < (SIGNS_MS + SMOKE_MS + APPEAR_MS)) return;

        if (dispatcherMode == Mode.NONE || dispatcherRenderMethod == null) bindDispatcherRender();
        if (dispatcherMode == Mode.NONE || dispatcherRenderMethod == null) return;

        MatrixStack ms = eventStack(e);
        if (ms == null) return;

        float appear = clonesAppearK(dt);
        float fade = globalFade(dt);
        float alphaMul = MathHelper.clamp(appear * fade, 0.0f, 1.0f);
        if (alphaMul <= 0.001f) return;

        float tickDelta = eventPartialTicks(e);

        Vec3d base = anchorSet ? anchorPos : mc.player.getLerpedPos(tickDelta);
        float cy = anchorSet ? anchorYaw : mc.player.getYaw();

        EntityRenderDispatcher disp = mc.getEntityRenderDispatcher();
        VertexConsumerProvider.Immediate baseVcp = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumerProvider vcp = new AlphaVcp(baseVcp, alphaMul);

        int light = 15728880;

        if (duelMode && clones.size() >= 2) {
            renderDuel(base, cy, dt, n, ms, vcp, light, tickDelta);
            baseVcp.draw();
            return;
        }

        for (Clone c : clones) {
            OtherClientPlayerEntity ent = c.ent;

            double wx = base.x + c.offX;
            double wy = base.y;
            double wz = base.z + c.offZ;

            double tt = (n - startMs);
            double sway = Math.sin(tt * 0.0048 + (c.seed & 1023L) * 0.01) * 0.045;

            double ang = Math.toRadians(c.yawOff);
            wx += Math.cos(ang) * sway;
            wz += Math.sin(ang) * sway;

            float yaw = cy + c.yawOff;

            double dx = wx - c.lastX;
            double dz = wz - c.lastZ;
            double sp = Math.sqrt(dx * dx + dz * dz);
            c.lastX = wx;
            c.lastZ = wz;

            setClonePose(ent, wx, wy, wz, yaw);
            applyWalk(ent, (float) MathHelper.clamp(sp * 65.0, 0.0, 1.35), (float) MathHelper.clamp(sp * 120.0, 0.0, 1.0));

            renderByDispatcher(disp, ent, wx, wy, wz, yaw, tickDelta, ms, vcp, light);
        }

        baseVcp.draw();
    }

    void renderDuel(Vec3d base, float baseYaw, long dt, long nowMs, MatrixStack ms, VertexConsumerProvider vcp, int light, float tickDelta) {
        Clone a = clones.get(0);
        Clone b = clones.get(1);

        long duelStart = SIGNS_MS + SMOKE_MS + APPEAR_MS;
        long local = dt - duelStart;
        if (local < 0L) local = 0L;
        if (local > holdMs) local = holdMs;

        DuelState st = duel;
        if (st == null) {
            st = new DuelState();
            duel = st;
        }

        if (!st.init) {
            st.init = true;
            st.lastUpdateMs = nowMs;
            st.phase = DuelPhase.APPROACH;
            st.phaseStartMs = 0L;
            st.exchangeIdx = 0;
            st.exchanges.clear();

            long seed = (a.seed ^ (b.seed * 1315423911L) ^ (startMs * 2654435761L));
            int exCount = 4;
            for (int i = 0; i < exCount; i++) {
                long h = mix(seed + i * 0x9E3779B97F4A7C15L);
                long circleMs = 1450L + (h & 255L) * 4L;
                long windupMs = 560L + ((h >>> 9) & 127L) * 2L;
                long strikeMs = 260L + ((h >>> 16) & 31L);
                long clashMs = 150L + ((h >>> 21) & 31L);
                long recoilMs = 280L + ((h >>> 28) & 63L);
                long resetMs = 650L + ((h >>> 35) & 127L) * 2L;
                int dir = (((h >>> 4) & 1L) == 0L) ? 1 : -1;
                boolean feint = (((h >>> 6) & 3L) == 0L);
                boolean dodge = (((h >>> 10) & 3L) == 0L);
                st.exchanges.add(new DuelExchange(circleMs, windupMs, strikeMs, clashMs, recoilMs, resetMs, dir, feint, dodge));
            }

            Vec3d initA = new Vec3d(base.x + a.offX, base.y, base.z + a.offZ);
            Vec3d initB = new Vec3d(base.x + b.offX, base.y, base.z + b.offZ);

            st.curA = initA;
            st.curB = initB;
            st.velA = Vec3d.ZERO;
            st.velB = Vec3d.ZERO;

            st.yawA = baseYaw;
            st.yawB = baseYaw + 180.0f;
            st.headA = st.yawA;
            st.headB = st.yawB;
            st.bodyA = st.yawA;
            st.bodyB = st.yawB;
            st.pitchA = 0.0f;
            st.pitchB = 0.0f;

            st.lastAimA = initB;
            st.lastAimB = initA;
        }

        float dtS = (nowMs - st.lastUpdateMs) / 1000.0f;
        if (dtS < 0.0f) dtS = 0.0f;
        if (dtS > 0.05f) dtS = 0.05f;
        st.lastUpdateMs = nowMs;

        Vec3d f = forwardFromYaw(baseYaw);
        Vec3d r = new Vec3d(f.z, 0.0, -f.x);
        Vec3d center = base.add(f.multiply(2.10));

        float warmIn = ease(MathHelper.clamp((float) local / 900.0f, 0.0f, 1.0f));
        float warmOut = 1.0f - ease(MathHelper.clamp(((float) local - (float) holdMs + 900.0f) / 900.0f, 0.0f, 1.0f));
        float alive = MathHelper.clamp(warmIn * warmOut, 0.0f, 1.0f);

        long phaseT = local - st.phaseStartMs;
        if (phaseT < 0L) phaseT = 0L;

        DuelExchange ex = st.exchangeIdx < st.exchanges.size() ? st.exchanges.get(st.exchangeIdx) : null;

        long approachMs = 1100L;
        long finishMs = 1400L;

        if (st.phase == DuelPhase.APPROACH && phaseT >= approachMs) {
            st.phase = DuelPhase.CIRCLE;
            st.phaseStartMs = local;
            phaseT = 0L;
        }

        if (ex != null) {
            if (st.phase == DuelPhase.CIRCLE && phaseT >= ex.circleMs) {
                st.phase = DuelPhase.WINDUP;
                st.phaseStartMs = local;
                phaseT = 0L;
            } else if (st.phase == DuelPhase.WINDUP && phaseT >= ex.windupMs) {
                st.phase = DuelPhase.STRIKE;
                st.phaseStartMs = local;
                phaseT = 0L;
            } else if (st.phase == DuelPhase.STRIKE && phaseT >= ex.strikeMs) {
                st.phase = DuelPhase.CLASH;
                st.phaseStartMs = local;
                phaseT = 0L;
            } else if (st.phase == DuelPhase.CLASH && phaseT >= ex.clashMs) {
                st.phase = DuelPhase.RECOIL;
                st.phaseStartMs = local;
                phaseT = 0L;
            } else if (st.phase == DuelPhase.RECOIL && phaseT >= ex.recoilMs) {
                st.phase = DuelPhase.RESET;
                st.phaseStartMs = local;
                phaseT = 0L;
            } else if (st.phase == DuelPhase.RESET && phaseT >= ex.resetMs) {
                st.exchangeIdx++;
                if (st.exchangeIdx >= st.exchanges.size()) {
                    st.phase = DuelPhase.FINISH;
                    st.phaseStartMs = local;
                    phaseT = 0L;
                } else {
                    st.phase = DuelPhase.CIRCLE;
                    st.phaseStartMs = local;
                    phaseT = 0L;
                }
            }
        } else {
            st.phase = DuelPhase.FINISH;
        }

        if (st.phase == DuelPhase.FINISH && phaseT >= finishMs) {
            if (!duelFinishSmoke) {
                duelFinishSmoke = true;
                spawnFinishSmoke(nowMs, st.curA);
                spawnFinishSmoke(nowMs, st.curB);
            }
        }

        double stanceDist = 2.34;
        double half = stanceDist * 0.5;

        Vec3d stanceA = center.add(r.multiply(half)).add(f.multiply(0.02));
        Vec3d stanceB = center.add(r.multiply(-half)).add(f.multiply(0.02));

        Vec3d targetA = stanceA;
        Vec3d targetB = stanceB;

        st.sneakA = false;
        st.sneakB = false;

        float prepPitchA = 0.0f;
        float prepPitchB = 0.0f;

        float addBodyA = 0.0f;
        float addBodyB = 0.0f;

        Vec3d aimA = st.curB;
        Vec3d aimB = st.curA;

        if (st.phase == DuelPhase.APPROACH) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) approachMs, 0.0f, 1.0f));
            double sep = MathHelper.lerp(k, 1.55, half);
            targetA = center.add(r.multiply(sep)).add(f.multiply(0.02));
            targetB = center.add(r.multiply(-sep)).add(f.multiply(0.02));
        } else if (st.phase == DuelPhase.CIRCLE && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.circleMs, 0.0f, 1.0f));
            double arc = 0.58;
            double rad = 0.48;
            double aAng = (k - 0.5) * arc * ex.dir;
            double bAng = (k - 0.5) * arc * -ex.dir;

            Vec3d offA = f.multiply(MathHelper.sin((float) aAng) * rad).add(r.multiply(MathHelper.cos((float) aAng) * 0.26 * ex.dir));
            Vec3d offB = f.multiply(MathHelper.sin((float) bAng) * rad).add(r.multiply(MathHelper.cos((float) bAng) * 0.26 * -ex.dir));

            targetA = stanceA.add(offA);
            targetB = stanceB.add(offB);

            float settle = 1.0f - ease(MathHelper.clamp(k / 0.18f, 0.0f, 1.0f));
            targetA = targetA.add(r.multiply(settle * 0.04 * ex.dir));
            targetB = targetB.add(r.multiply(settle * -0.04 * ex.dir));
        } else if (st.phase == DuelPhase.WINDUP && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.windupMs, 0.0f, 1.0f));

            boolean aAttacks = (st.exchangeIdx & 1) == 0;
            int swingDir = ex.dir;

            Vec3d aBack = f.multiply(-0.18 * (aAttacks ? 1.0 : 0.55)).add(r.multiply(0.12 * swingDir * (aAttacks ? 1.0 : 0.4)));
            Vec3d bSet = f.multiply(-0.06 * (aAttacks ? 0.3 : 1.0)).add(r.multiply(-0.08 * swingDir * (aAttacks ? 0.4 : 1.0)));

            targetA = stanceA.add(aBack.multiply(k));
            targetB = stanceB.add(bSet.multiply(k));

            if (ex.feint) {
                float fk = ease(MathHelper.clamp((k - 0.55f) / 0.45f, 0.0f, 1.0f));
                Vec3d fe = f.multiply(-0.08).add(r.multiply(0.18 * -swingDir));
                if (aAttacks) targetA = targetA.add(fe.multiply(fk));
                else targetB = targetB.add(fe.multiply(fk));
            }

            st.sneakA = aAttacks;
            st.sneakB = !aAttacks;

            prepPitchA = aAttacks ? -18.0f * k : -6.0f * k;
            prepPitchB = !aAttacks ? -18.0f * k : -6.0f * k;

            addBodyA = (aAttacks ? 18.0f : 7.0f) * k * swingDir;
            addBodyB = (aAttacks ? -7.0f : -18.0f) * k * swingDir;
        } else if (st.phase == DuelPhase.STRIKE && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.strikeMs, 0.0f, 1.0f));
            boolean aAttacks = (st.exchangeIdx & 1) == 0;
            int swingDir = ex.dir;

            Vec3d lunge = f.multiply(0.54).add(r.multiply(0.30 * swingDir));
            Vec3d meet = f.multiply(0.22).add(r.multiply(-0.16 * swingDir));

            Vec3d over = f.multiply(0.10).add(r.multiply(0.10 * swingDir));

            if (ex.dodge && !aAttacks) {
                Vec3d dodge = r.multiply(0.34 * swingDir).add(f.multiply(-0.06));
                targetB = stanceB.add(dodge.multiply(k));
                targetA = stanceA.add(lunge.multiply(k)).add(over.multiply(ease(MathHelper.clamp((k - 0.62f) / 0.38f, 0.0f, 1.0f))));
            } else if (ex.dodge && aAttacks) {
                Vec3d dodge = r.multiply(-0.34 * swingDir).add(f.multiply(-0.06));
                targetA = stanceA.add(dodge.multiply(k));
                targetB = stanceB.add(lunge.multiply(k)).add(over.multiply(ease(MathHelper.clamp((k - 0.62f) / 0.38f, 0.0f, 1.0f))));
            } else {
                if (aAttacks) {
                    targetA = stanceA.add(lunge.multiply(k));
                    targetB = stanceB.add(meet.multiply(k));
                } else {
                    targetB = stanceB.add(lunge.multiply(k));
                    targetA = stanceA.add(meet.multiply(k));
                }
            }

            st.sneakA = aAttacks;
            st.sneakB = !aAttacks;

            prepPitchA = (aAttacks ? -22.0f : -8.0f) * (1.0f - (1.0f - k) * (1.0f - k));
            prepPitchB = (!aAttacks ? -22.0f : -8.0f) * (1.0f - (1.0f - k) * (1.0f - k));

            addBodyA = (aAttacks ? 28.0f : 10.0f) * k * swingDir;
            addBodyB = (aAttacks ? -10.0f : -28.0f) * k * swingDir;

            if (aAttacks) {
                if (st.lastSwingA != st.exchangeIdx) {
                    st.lastSwingA = st.exchangeIdx;
                    duelSwing(a, nowMs, 0L);
                }
            } else {
                if (st.lastSwingB != st.exchangeIdx) {
                    st.lastSwingB = st.exchangeIdx;
                    duelSwing(b, nowMs, 0L);
                }
            }
        } else if (st.phase == DuelPhase.CLASH && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.clashMs, 0.0f, 1.0f));
            int swingDir = ex.dir;

            Vec3d pushA = f.multiply(0.08).add(r.multiply(0.10 * swingDir));
            Vec3d pushB = f.multiply(0.08).add(r.multiply(-0.10 * swingDir));

            targetA = stanceA.add(pushA.multiply(0.55 + 0.45 * (1.0 - k)));
            targetB = stanceB.add(pushB.multiply(0.55 + 0.45 * (1.0 - k)));

            st.sneakA = true;
            st.sneakB = true;

            prepPitchA = -10.0f - 8.0f * (1.0f - k);
            prepPitchB = -10.0f - 8.0f * (1.0f - k);

            addBodyA = 16.0f * swingDir * (1.0f - 0.5f * k);
            addBodyB = -16.0f * swingDir * (1.0f - 0.5f * k);

            Vec3d contactPos = midpoint(st.curA, st.curB).add(0.0, 1.2, 0.0);
            if (st.lastClashSpark != st.exchangeIdx) {
                st.lastClashSpark = st.exchangeIdx;
                spawnFinishSmoke(nowMs, contactPos);
            }
        } else if (st.phase == DuelPhase.RECOIL && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.recoilMs, 0.0f, 1.0f));
            int swingDir = ex.dir;

            Vec3d backA = f.multiply(-0.34).add(r.multiply(0.14 * swingDir));
            Vec3d backB = f.multiply(-0.34).add(r.multiply(-0.14 * swingDir));

            targetA = stanceA.add(backA.multiply(k));
            targetB = stanceB.add(backB.multiply(k));

            st.sneakA = false;
            st.sneakB = false;

            prepPitchA = 10.0f * (1.0f - (1.0f - k) * (1.0f - k));
            prepPitchB = 10.0f * (1.0f - (1.0f - k) * (1.0f - k));

            addBodyA = -12.0f * swingDir * (1.0f - k);
            addBodyB = 12.0f * swingDir * (1.0f - k);
        } else if (st.phase == DuelPhase.RESET && ex != null) {
            float k = ease(MathHelper.clamp((float) phaseT / (float) ex.resetMs, 0.0f, 1.0f));
            int swingDir = ex.dir;

            Vec3d driftA = r.multiply(0.10 * swingDir).add(f.multiply(-0.04));
            Vec3d driftB = r.multiply(-0.10 * swingDir).add(f.multiply(-0.04));

            targetA = lerpVec(stanceA.add(driftA), stanceA, k);
            targetB = lerpVec(stanceB.add(driftB), stanceB, k);

            st.sneakA = false;
            st.sneakB = false;

            prepPitchA = -2.0f * (1.0f - k);
            prepPitchB = -2.0f * (1.0f - k);

            addBodyA = 3.0f * swingDir * (1.0f - k);
            addBodyB = -3.0f * swingDir * (1.0f - k);
        } else if (st.phase == DuelPhase.FINISH) {
            float k = ease(MathHelper.clamp((float) phaseT / 1400.0f, 0.0f, 1.0f));

            Vec3d sepA = stanceA.add(r.multiply(0.36)).add(f.multiply(-0.22));
            Vec3d sepB = stanceB.add(r.multiply(-0.36)).add(f.multiply(-0.22));

            targetA = lerpVec(stanceA, sepA, k);
            targetB = lerpVec(stanceB, sepB, k);

            st.sneakA = false;
            st.sneakB = false;

            prepPitchA = -6.0f * (1.0f - k);
            prepPitchB = -6.0f * (1.0f - k);

            addBodyA = 10.0f * k;
            addBodyB = -10.0f * k;
        }

        double micro = 0.0;
        if (alive > 0.0f) {
            micro = Math.sin((nowMs - startMs) * 0.0042) * 0.006 * alive;
        }
        targetA = targetA.add(0.0, micro, 0.0);
        targetB = targetB.add(0.0, -micro, 0.0);

        float posSmooth = (st.phase == DuelPhase.STRIKE || st.phase == DuelPhase.CLASH) ? 0.085f : 0.110f;
        st.curA = smoothDampVec(st.curA, targetA, st.velA, posSmooth, dtS);
        st.velA = lastVel;
        st.curB = smoothDampVec(st.curB, targetB, st.velB, posSmooth, dtS);
        st.velB = lastVel;

        aimA = st.curB;
        aimB = st.curA;

        st.lastAimA = smoothAim(st.lastAimA, aimA, dtS, 0.120f);
        st.lastAimB = smoothAim(st.lastAimB, aimB, dtS, 0.120f);

        float wantYawA = yawFromDelta(st.lastAimA.x - st.curA.x, st.lastAimA.z - st.curA.z);
        float wantYawB = yawFromDelta(st.lastAimB.x - st.curB.x, st.lastAimB.z - st.curB.z);

        float yawSmooth = (st.phase == DuelPhase.STRIKE || st.phase == DuelPhase.CLASH) ? 0.065f : 0.095f;

        st.yawA = lerpAngleExp(st.yawA, wantYawA, dtS, yawSmooth);
        st.yawB = lerpAngleExp(st.yawB, wantYawB, dtS, yawSmooth);

        float bodyYawA = wrapDeg(st.yawA + addBodyA);
        float bodyYawB = wrapDeg(st.yawB + addBodyB);

        st.bodyA = lerpAngleExp(st.bodyA, bodyYawA, dtS, 0.085f);
        st.bodyB = lerpAngleExp(st.bodyB, bodyYawB, dtS, 0.085f);

        st.headA = lerpAngleExp(st.headA, st.yawA, dtS, 0.060f);
        st.headB = lerpAngleExp(st.headB, st.yawB, dtS, 0.060f);

        st.pitchA = lerpExp(st.pitchA, prepPitchA, dtS, 0.075f);
        st.pitchB = lerpExp(st.pitchB, prepPitchB, dtS, 0.075f);

        double adx = st.curA.x - a.lastX;
        double adz = st.curA.z - a.lastZ;
        double as = Math.sqrt(adx * adx + adz * adz);
        a.lastX = st.curA.x;
        a.lastZ = st.curA.z;

        double bdx = st.curB.x - b.lastX;
        double bdz = st.curB.z - b.lastZ;
        double bs = Math.sqrt(bdx * bdx + bdz * bdz);
        b.lastX = st.curB.x;
        b.lastZ = st.curB.z;

        setClonePoseYawPitch(a.ent, st.curA.x, st.curA.y, st.curA.z, st.bodyA, st.headA, st.pitchA, st.sneakA);
        setClonePoseYawPitch(b.ent, st.curB.x, st.curB.y, st.curB.z, st.bodyB, st.headB, st.pitchB, st.sneakB);

        float aLimb = (float) MathHelper.clamp(as * 48.0, 0.0, 1.15);
        float bLimb = (float) MathHelper.clamp(bs * 48.0, 0.0, 1.15);
        float aSpd = (float) MathHelper.clamp(as * 92.0, 0.0, 1.0);
        float bSpd = (float) MathHelper.clamp(bs * 92.0, 0.0, 1.0);

        applyWalk(a.ent, aLimb, aSpd);
        applyWalk(b.ent, bLimb, bSpd);

        renderByDispatcher(mc.getEntityRenderDispatcher(), a.ent, st.curA.x, st.curA.y, st.curA.z, st.bodyA, tickDelta, ms, vcp, light);
        renderByDispatcher(mc.getEntityRenderDispatcher(), b.ent, st.curB.x, st.curB.y, st.curB.z, st.bodyB, tickDelta, ms, vcp, light);
    }

    static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }

    static Vec3d midpoint(Vec3d a, Vec3d b) {
        return new Vec3d((a.x + b.x) * 0.5, (a.y + b.y) * 0.5, (a.z + b.z) * 0.5);
    }

    static Vec3d lerpVec(Vec3d a, Vec3d b, float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return new Vec3d(
                MathHelper.lerp(t, a.x, b.x),
                MathHelper.lerp(t, a.y, b.y),
                MathHelper.lerp(t, a.z, b.z)
        );
    }

    static float wrapDeg(float a) {
        return MathHelper.wrapDegrees(a);
    }

    static float lerpExp(float cur, float target, float dtS, float smoothTime) {
        if (smoothTime <= 0.0001f) return target;
        float k = 1.0f - (float) Math.exp(-dtS / smoothTime);
        if (k < 0.0f) k = 0.0f;
        if (k > 1.0f) k = 1.0f;
        return cur + (target - cur) * k;
    }

    static float lerpAngleExp(float cur, float target, float dtS, float smoothTime) {
        float d = MathHelper.wrapDegrees(target - cur);
        float k = 1.0f - (float) Math.exp(-dtS / Math.max(0.0001f, smoothTime));
        if (k < 0.0f) k = 0.0f;
        if (k > 1.0f) k = 1.0f;
        return MathHelper.wrapDegrees(cur + d * k);
    }

    static Vec3d lastVel = Vec3d.ZERO;

    static Vec3d smoothDampVec(Vec3d current, Vec3d target, Vec3d velocity, float smoothTime, float dtS) {
        float st = Math.max(0.0001f, smoothTime);
        float omega = 2.0f / st;
        float x = omega * dtS;
        float exp = 1.0f / (1.0f + x + 0.48f * x * x + 0.235f * x * x * x);

        Vec3d change = current.subtract(target);
        Vec3d temp = velocity.add(change.multiply(omega)).multiply(dtS);

        Vec3d newVel = velocity.subtract(temp.multiply(omega)).multiply(exp);
        Vec3d output = target.add(change.add(temp).multiply(exp));

        lastVel = newVel;
        return output;
    }

    static Vec3d smoothAim(Vec3d current, Vec3d target, float dtS, float smoothTime) {
        float k = 1.0f - (float) Math.exp(-dtS / Math.max(0.0001f, smoothTime));
        if (k < 0.0f) k = 0.0f;
        if (k > 1.0f) k = 1.0f;
        return current.add(target.subtract(current).multiply(k));
    }

    void duelSwing(Clone c, long nowMs, long periodMs) {
        if (nowMs - c.lastSwingMs < periodMs) return;
        c.lastSwingMs = nowMs;
        try {
            c.ent.swingHand(Hand.MAIN_HAND);
        } catch (Throwable ignored) {
        }
    }

    void spawnFinishSmoke(long t, Vec3d p) {
        for (int i = 0; i < 12; i++) {
            double ang = (Math.PI * 2.0) * ((double) i / 12.0);
            double r = 0.12 + (i % 4) * 0.06;
            double ox = Math.cos(ang) * r;
            double oz = Math.sin(ang) * r;
            double oy = 0.06 + (i % 3) * 0.06;
            float base = 0.62f + (i % 4) * 0.10f;
            long life = 520L + (i % 6) * 70L;
            smoke.add(new SmokePuff(p.x + ox, p.y + oy, p.z + oz, t, life, base, base * (1.70f + (i % 3) * 0.22f)));
        }
    }

    void setClonePose(OtherClientPlayerEntity ent, double x, double y, double z, float yaw) {
        ent.setPos(x, y, z);
        syncPrevPos(ent, x, y, z);

        try {
            ent.setYaw(yaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setBodyYaw(yaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setHeadYaw(yaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setPose(EntityPose.STANDING);
        } catch (Throwable ignored) {
        }
    }

    void setClonePoseYawPitch(OtherClientPlayerEntity ent, double x, double y, double z, float bodyYaw, float headYaw, float pitch, boolean sneak) {
        ent.setPos(x, y, z);
        syncPrevPos(ent, x, y, z);

        try {
            ent.setYaw(bodyYaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setBodyYaw(bodyYaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setHeadYaw(headYaw);
        } catch (Throwable ignored) {
        }
        try {
            ent.setPitch(pitch);
        } catch (Throwable ignored) {
        }
        try {
            ent.setSneaking(sneak);
        } catch (Throwable ignored) {
        }
        try {
            ent.setPose(EntityPose.STANDING);
        } catch (Throwable ignored) {
        }
    }

    void bindLimbsMethod() {
        limbsMethod = null;
        limbsMode = 0;

        try {
            Method m = LivingEntity.class.getDeclaredMethod("updateLimbs", float.class, float.class);
            m.setAccessible(true);
            limbsMethod = m;
            limbsMode = 2;
            return;
        } catch (Throwable ignored) {
        }

        for (Method m : LivingEntity.class.getDeclaredMethods()) {
            if (!m.getName().equals("updateLimbs")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[0] == float.class && p[1] == float.class) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored2) {
                }
                limbsMethod = m;
                limbsMode = 2;
                return;
            }
            if (p.length == 3 && p[0] == float.class && p[1] == float.class && p[2] == boolean.class) {
                try {
                    m.setAccessible(true);
                } catch (Throwable ignored2) {
                }
                limbsMethod = m;
                limbsMode = 3;
                return;
            }
        }
    }

    void applyWalk(LivingEntity ent, float limbDist, float speed) {
        if (ent == null) return;

        try {
            if (limbsMethod != null && limbsMode == 2) {
                limbsMethod.invoke(ent, speed, limbDist);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (limbsMethod != null && limbsMode == 3) {
                limbsMethod.invoke(ent, speed, limbDist, Boolean.FALSE);
                return;
            }
        } catch (Throwable ignored) {
        }

        trySetFloat(ent, "limbDistance", limbDist);
        trySetFloat(ent, "lastLimbDistance", limbDist);
        trySetFloat(ent, "limbAngle", speed);
        trySetFloat(ent, "lastLimbAngle", speed);
    }

    void trySetFloat(Object o, String name, float v) {
        if (o == null) return;
        Class<?> c = o.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                if (f.getType() == float.class) f.setFloat(o, v);
                return;
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
    }

    void bindDispatcherRender() {
        dispatcherRenderMethod = null;
        dispatcherMode = Mode.NONE;

        if (mc == null) return;
        EntityRenderDispatcher disp = mc.getEntityRenderDispatcher();
        if (disp == null) return;

        for (Method m : disp.getClass().getMethods()) {
            if (!m.getName().equals("render")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length < 8 || p.length > 10) continue;
            if (!Entity.class.isAssignableFrom(p[0])) continue;

            int idx = 1;

            if (idx + 2 >= p.length) continue;
            if (p[idx] != double.class || p[idx + 1] != double.class || p[idx + 2] != double.class) continue;
            idx += 3;

            boolean hasYaw = false;
            boolean hasTickDelta = false;

            if (idx < p.length && p[idx] == float.class) {
                hasYaw = true;
                idx++;
            }

            if (idx < p.length && p[idx] == float.class) {
                hasTickDelta = true;
                idx++;
            }

            if (idx >= p.length || p[idx] != MatrixStack.class) continue;
            idx++;

            if (idx >= p.length || !VertexConsumerProvider.class.isAssignableFrom(p[idx])) continue;
            idx++;

            if (idx >= p.length || p[idx] != int.class) continue;
            idx++;

            boolean hasBool = (idx < p.length && p[idx] == boolean.class);

            if (!hasYaw) continue;

            if (hasTickDelta && !hasBool && p.length == 9) {
                dispatcherRenderMethod = m;
                dispatcherMode = Mode.R9;
                return;
            }

            if (!hasTickDelta && !hasBool && p.length == 8) {
                dispatcherRenderMethod = m;
                dispatcherMode = Mode.R8;
                return;
            }

            if (hasTickDelta && hasBool && p.length == 10) {
                dispatcherRenderMethod = m;
                dispatcherMode = Mode.R10;
                return;
            }
        }
    }

    void renderByDispatcher(EntityRenderDispatcher disp,
                            Entity ent,
                            double x,
                            double y,
                            double z,
                            float yaw,
                            float tickDelta,
                            MatrixStack ms,
                            VertexConsumerProvider vcp,
                            int light) {
        try {
            if (dispatcherMode == Mode.R9 && dispatcherRenderMethod != null) {
                dispatcherRenderMethod.invoke(disp, ent, x, y, z, yaw, tickDelta, ms, vcp, light);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (dispatcherMode == Mode.R8 && dispatcherRenderMethod != null) {
                dispatcherRenderMethod.invoke(disp, ent, x, y, z, yaw, ms, vcp, light);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (dispatcherMode == Mode.R10 && dispatcherRenderMethod != null) {
                dispatcherRenderMethod.invoke(disp, ent, x, y, z, yaw, tickDelta, ms, vcp, light, Boolean.FALSE);
            }
        } catch (Throwable ignored) {
        }
    }

    static void syncPrevPos(Entity ent, double x, double y, double z) {
        setFieldIfPresent(ent, "prevX", x);
        setFieldIfPresent(ent, "prevY", y);
        setFieldIfPresent(ent, "prevZ", z);

        setFieldIfPresent(ent, "lastX", x);
        setFieldIfPresent(ent, "lastY", y);
        setFieldIfPresent(ent, "lastZ", z);

        setFieldIfPresent(ent, "lastRenderX", x);
        setFieldIfPresent(ent, "lastRenderY", y);
        setFieldIfPresent(ent, "lastRenderZ", z);
    }

    static void setFieldIfPresent(Object o, String name, double v) {
        if (o == null) return;
        Class<?> c = o.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                if (f.getType() == double.class) f.setDouble(o, v);
                if (f.getType() == float.class) f.setFloat(o, (float) v);
                return;
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
    }

    static MatrixStack eventStack(WorldRenderEvent e) {
        if (e == null) return null;

        try {
            Field f = e.getClass().getField("stack");
            Object v = f.get(e);
            if (v instanceof MatrixStack) return (MatrixStack) v;
        } catch (Throwable ignored) {
        }

        try {
            Field f = e.getClass().getDeclaredField("stack");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof MatrixStack) return (MatrixStack) v;
        } catch (Throwable ignored) {
        }

        for (String mname : new String[]{"getStack", "stack"}) {
            try {
                Method m = e.getClass().getMethod(mname);
                Object v = m.invoke(e);
                if (v instanceof MatrixStack) return (MatrixStack) v;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    float eventPartialTicks(WorldRenderEvent e) {
        float fallback = 0.0f;
        try {
            fallback = mc.getRenderTickCounter().getTickDelta(false);
        } catch (Throwable ignored) {
        }

        if (e == null) return fallback;

        try {
            Field f = e.getClass().getField("partialTicks");
            Object v = f.get(e);
            if (v instanceof Float) return (Float) v;
        } catch (Throwable ignored) {
        }

        try {
            Field f = e.getClass().getDeclaredField("partialTicks");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof Float) return (Float) v;
        } catch (Throwable ignored) {
        }

        for (String mname : new String[]{"getPartialTicks", "partialTicks", "getTickDelta"}) {
            try {
                Method m = e.getClass().getMethod(mname);
                Object v = m.invoke(e);
                if (v instanceof Float) return (Float) v;
            } catch (Throwable ignored) {
            }
        }

        return fallback;
    }

    static final class Clone {
        final OtherClientPlayerEntity ent;
        final double offX;
        final double offZ;
        final float yawOff;
        final long seed;

        double lastX;
        double lastZ;
        long lastSwingMs = 0L;

        Clone(OtherClientPlayerEntity ent, double offX, double offZ, float yawOff, long seed) {
            this.ent = ent;
            this.offX = offX;
            this.offZ = offZ;
            this.yawOff = yawOff;
            this.seed = seed;
            this.lastX = 0.0;
            this.lastZ = 0.0;
        }
    }

    static final class SmokePuff {
        final double x;
        final double y;
        final double z;
        final long startMs;
        final long lifeMs;
        final float scaleFrom;
        final float scaleTo;
        final long seed;

        SmokePuff(double x, double y, double z, long startMs, long lifeMs, float scaleFrom, float scaleTo) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startMs = startMs;
            this.lifeMs = lifeMs;
            this.scaleFrom = scaleFrom;
            this.scaleTo = scaleTo;
            this.seed = startMs ^ Double.doubleToLongBits(x * 31.0 + y * 17.0 + z * 13.0);
        }
    }

    static final class AlphaVcp implements VertexConsumerProvider {
        final VertexConsumerProvider base;
        final float aMul;

        AlphaVcp(VertexConsumerProvider base, float aMul) {
            this.base = base;
            this.aMul = aMul;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            VertexConsumer d = base.getBuffer(layer);
            return wrap(d, aMul);
        }

        static VertexConsumer wrap(VertexConsumer d, float aMul) {
            final Object[] self = new Object[1];

            Object proxy = Proxy.newProxyInstance(
                    VertexConsumer.class.getClassLoader(),
                    new Class[]{VertexConsumer.class},
                    (p, method, args) -> {
                        String n = method.getName();

                        if (args != null && args.length == 4 && (n.equals("color") || n.equals("fixedColor"))) {
                            if (args[3] instanceof Integer) {
                                int a = (Integer) args[3];
                                int v = (int) (a * aMul);
                                if (v < 0) v = 0;
                                if (v > 255) v = 255;
                                args[3] = v;
                            }
                            Object r = method.invoke(d, args);
                            if (method.getReturnType() == VertexConsumer.class) return self[0];
                            return r;
                        }

                        Object r = method.invoke(d, args);

                        if (method.getReturnType() == VertexConsumer.class) return self[0];
                        return r;
                    }
            );

            self[0] = proxy;
            return (VertexConsumer) proxy;
        }
    }
}
