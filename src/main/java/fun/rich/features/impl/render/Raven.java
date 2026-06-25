package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class Raven {

    static final Identifier VORON_TEX = Identifier.of("mre", "textures/voron.png");
    static final Identifier FEATHER_TEX = Identifier.of("mre", "textures/pero.png");

    static final Identifier ITACHI_SOUND_MINECRAFT = Identifier.of("minecraft", "itachi");
    static final Identifier ITACHI_SOUND_MRE = Identifier.of("mre", "itachi");

    static final long ULT_MS = 19000L;
    static final long ULT_ASCEND_MS = 2400L;
    static final long ULT_IN_MS = 2600L;
    static final long ULT_HOLD_MS = 9200L;
    static final long ULT_BREAK_MS = 2800L;
    static final long ULT_DISPERSE_MS = 3000L;

    static final long RETURN_MS = 850L;

    static final int ULT_RAVENS = 42;

    Cosmetic p;

    @NonFinal BatEntity bat;
    @NonFinal ParrotEntity parrot;
    @NonFinal ParrotEntity raven;

    @NonFinal AllayEntity fairy;
    @NonFinal BeeEntity bee;
    @NonFinal VexEntity vex;
    @NonFinal FoxEntity fox;

    @NonFinal PigEntity pig;
    @NonFinal FrogEntity frog;
    @NonFinal PufferfishEntity pufferfish;
    @NonFinal SlimeEntity slime;

    @NonFinal double slimeJumpY = 0.0;
    @NonFinal double slimeJumpVel = 0.0;
    @NonFinal boolean slimeJumpAir = false;
    @NonFinal long slimeLastHopMs = 0L;

    @NonFinal int lastMode = -1;
    @NonFinal long lastMs = 0L;
    @NonFinal long lastBatTickMs = 0L;
    @NonFinal long lastPetTickMs = 0L;
    @NonFinal long lastFoxTickMs = 0L;

    @NonFinal Vec3d smoothPos = Vec3d.ZERO;
    @NonFinal Vec3d prevSmoothPos = Vec3d.ZERO;
    @NonFinal float smoothYaw = 0.0f;
    @NonFinal float smoothPitch = 0.0f;

    @NonFinal float moveSp = 0.0f;
    @NonFinal float wantHoldTime = 0.0f;
    @NonFinal float wantOrbitTime = 0.0f;
    @NonFinal boolean holdMode = false;

    @NonFinal float orbitAng = 0.0f;
    @NonFinal float orbitBlend = 1.0f;

    @NonFinal float baseYawDeg = Float.NaN;

    @NonFinal RenderCompat compat = new RenderCompat();
    @NonFinal ParrotWingCompat parrotWing = new ParrotWingCompat();
    @NonFinal BatRoostCompat batRoost = new BatRoostCompat();

    @NonFinal FoxStateCompat foxState = new FoxStateCompat();
    @NonFinal FoxLimbCompat foxLimb = new FoxLimbCompat();

    @NonFinal WalkerLimbCompat walkerLimb = new WalkerLimbCompat();

    @NonFinal Vec3d foxSmoothVel = Vec3d.ZERO;
    @NonFinal double foxGroundY = Double.NaN;
    @NonFinal double foxGroundTargetY = Double.NaN;
    @NonFinal long foxGroundLastSampleMs = 0L;
    @NonFinal Vec3d foxAnimPrevPos = Vec3d.ZERO;
    @NonFinal float foxSpeedSm = 0.0f;

    @NonFinal Vec3d walkerAnimPrevPos = Vec3d.ZERO;
    @NonFinal float walkerSpeedSm = 0.0f;

    @NonFinal PufferStateCompat pufferState = new PufferStateCompat();

    @NonFinal boolean ultActive = false;
    @NonFinal long ultStartMs = 0L;
    @NonFinal long ultLastMs = 0L;
    @NonFinal Vec3d ultStartPos = Vec3d.ZERO;
    @NonFinal float ultStartYaw = 0.0f;
    @NonFinal PositionedSoundInstance ultSound = null;

    @NonFinal boolean returnActive = false;
    @NonFinal long returnStartMs = 0L;
    @NonFinal Vec3d returnFrom = Vec3d.ZERO;
    @NonFinal Vec3d returnPrev = Vec3d.ZERO;
    @NonFinal boolean returnHasFrom = false;

    @NonFinal List<Illusion> illusions = new ArrayList<>();

    public Raven(Cosmetic parent) {
        this.p = parent;
    }

    public void deactivate() {
        stopUltSound();
        ultActive = false;
        ultStartMs = 0L;
        ultLastMs = 0L;
        ultStartPos = Vec3d.ZERO;
        ultStartYaw = 0.0f;
        illusions.clear();

        returnActive = false;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;

        bat = null;
        parrot = null;
        raven = null;

        fairy = null;
        bee = null;
        vex = null;
        fox = null;

        pig = null;
        frog = null;
        pufferfish = null;
        slime = null;

        lastMode = -1;
        lastMs = 0L;
        lastBatTickMs = 0L;
        lastPetTickMs = 0L;
        lastFoxTickMs = 0L;

        resetMotion();

        slimeJumpY = 0.0;
        slimeJumpVel = 0.0;
        slimeJumpAir = false;
        slimeLastHopMs = 0L;
        compat.reset();
        parrotWing.reset();
        batRoost.reset();
        foxState.reset();
        foxLimb.reset();
        walkerLimb.reset();
        pufferState.reset();
    }

    public void triggerUltimate() {
        if (p.mc.world == null || p.mc.player == null) return;
        if (!p.petIsRaven()) return;

        long now = System.currentTimeMillis();
        if (ultActive) return;

        ensureRaven(p.mc.player);

        returnActive = false;
        returnStartMs = 0L;
        returnFrom = Vec3d.ZERO;
        returnPrev = Vec3d.ZERO;
        returnHasFrom = false;

        float td = p.mc.getRenderTickCounter().getTickDelta(false);
        PlayerEntity pl = p.mc.player;

        double px = MathHelper.lerp(td, pl.prevX, pl.getX());
        double py = MathHelper.lerp(td, pl.prevY, pl.getY());
        double pz = MathHelper.lerp(td, pl.prevZ, pl.getZ());

        Vec3d eye = new Vec3d(px, py + pl.getStandingEyeHeight() * 0.82, pz);

        if (smoothPos == Vec3d.ZERO) {
            smoothPos = eye;
            prevSmoothPos = eye;
        }

        ultActive = true;
        ultStartMs = now;
        ultLastMs = now;
        ultStartPos = smoothPos;
        ultStartYaw = Float.isNaN(baseYawDeg) ? pl.bodyYaw : baseYawDeg;

        illusions.clear();

        Random r = new Random(now ^ 0x9E3779B97F4A7C15L);

        for (int i = 0; i < ULT_RAVENS; i++) {
            ParrotEntity e = createParrot();
            setParrotVariantSafe(e, 0);
            try {
                e.setCustomNameVisible(false);
                e.setCustomName(null);
            } catch (Throwable ignored) {
            }

            Illusion il = new Illusion();
            il.entity = e;
            il.seedA = r.nextFloat() * 1000.0f;
            il.seedB = r.nextFloat() * 1000.0f;
            il.phase = r.nextFloat();
            il.spinMul = 0.85f + r.nextFloat() * 1.55f;
            il.u = (r.nextFloat() - 0.5f) * 1.0f;
            il.v = (r.nextFloat() - 0.5f) * 1.0f;
            il.fly = randomDir(r).multiply(0.55 + r.nextDouble() * 1.35);
            il.pos = Vec3d.ZERO;
            il.prevPos = Vec3d.ZERO;
            il.vel = Vec3d.ZERO;
            il.hasPos = false;
            il.burst = false;
            il.burstMs = 0L;
            il.layer = frac((i * 0.6180339887f) + (il.seedA * 0.0007f));
            il.ang0 = (float) (i * (Math.PI * 2.0 / ULT_RAVENS));
            il.lastBodyPoint = Vec3d.ZERO;
            illusions.add(il);
        }

        playUltSound();
    }

    public void onWorldRender(WorldRenderEvent e) {
        if (p.mc.world == null || p.mc.player == null) return;

        if (p.pollRavenScene()) triggerUltimate();

        if (!ultActive && !Cosmetic.bool(p.petFirstPerson) && isFirstPerson(p.mc)) return;

        long now = System.currentTimeMillis();
        float dt = lastMs == 0L ? 0.016f : MathHelper.clamp((now - lastMs) / 1000.0f, 0.0f, 0.06f);
        lastMs = now;

        float pt = e.getPartialTicks();
        PlayerEntity pl = p.mc.player;

        int mode = modeFromSetting();
        if (mode != lastMode) {
            lastMode = mode;
            resetMotion();
            lastBatTickMs = 0L;
            lastPetTickMs = 0L;
            lastFoxTickMs = 0L;
            if (!ultActive) stopUltSound();
            if (!ultActive) {
                ultStartMs = 0L;
                ultLastMs = 0L;
                ultStartPos = Vec3d.ZERO;
                ultStartYaw = 0.0f;
                illusions.clear();
                returnActive = false;
                returnStartMs = 0L;
                returnFrom = Vec3d.ZERO;
                returnPrev = Vec3d.ZERO;
                returnHasFrom = false;
            }
            foxState.reset();
            foxLimb.reset();
            walkerLimb.reset();
            pufferState.reset();
        }

        double px = MathHelper.lerp(pt, pl.prevX, pl.getX());
        double py = MathHelper.lerp(pt, pl.prevY, pl.getY());
        double pz = MathHelper.lerp(pt, pl.prevZ, pl.getZ());

        Vec3d pv = pl.getVelocity();
        float rawSp = (float) Math.sqrt(pv.x * pv.x + pv.z * pv.z);
        float spA = 1.0f - (float) Math.exp(-dt / 0.10f);
        moveSp = moveSp + (rawSp - moveSp) * spA;

        float bodyYawDeg = MathHelper.lerpAngleDegrees(pt, pl.prevBodyYaw, pl.bodyYaw);

        if (Float.isNaN(baseYawDeg)) baseYawDeg = bodyYawDeg;
        float want = baseYawDeg;
        float upd;

        if (pl.isSprinting() || moveSp > 0.11f) {
            want = bodyYawDeg;
            upd = 1.0f - (float) Math.exp(-dt / 0.14f);
        } else if (moveSp > 0.06f) {
            want = bodyYawDeg;
            upd = 1.0f - (float) Math.exp(-dt / 0.22f);
        } else {
            upd = 0.0f;
        }

        if (upd > 0.0f) baseYawDeg = lerpAngle(baseYawDeg, want, upd);

        double yawRad = Math.toRadians(baseYawDeg);
        Vec3d forward = new Vec3d(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);

        float radiusBase = MathHelper.clamp((float) p.petRadius.getValue(), 0.15f, 2.5f);
        float heightBase = MathHelper.clamp((float) p.petHeight.getValue(), -0.5f, 3.0f);
        float speed = MathHelper.clamp((float) p.petSpeed.getValue(), 0.05f, 5.0f);
        float scale = MathHelper.clamp((float) p.petScale.getValue(), 0.15f, 2.0f);

        VertexConsumerProvider.Immediate vcp = p.mc.getBufferBuilders().getEntityVertexConsumers();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;

        if (mode == 2 && returnActive && !ultActive) {
            ensureRaven(pl);
            renderReturn(e, vcp, light, pl, pt, dt, px, py, pz, forward, right, scale, now);
            vcp.draw();
            return;
        }

        if (mode == 2 && ultActive) {
            ensureRaven(pl);
            renderUltimate(e, vcp, light, pl, pt, dt, px, py, pz, forward, right, radiusBase, heightBase, speed, scale, now);
            return;
        }

        if (mode == 0) {
            ensureBat(pl);
            renderBatWithParrotLogic(e, vcp, light, bat, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now);
            vcp.draw();
            return;
        }

        if (mode == 2) {
            ensureRaven(pl);
            renderParrotLike(e, vcp, light, raven, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now, true);
            vcp.draw();
            return;
        }

        if (mode == 1) {
            ensureParrot(pl);
            renderParrotLike(e, vcp, light, parrot, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale, now, false);
            vcp.draw();
            return;
        }

        if (mode == 3) {
            ensureFairy(pl);
            renderGenericFlyer(e, vcp, light, fairy, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now, 0.90f, 0.55f);
            vcp.draw();
            return;
        }

        if (mode == 4) {
            ensureBee(pl);
            renderGenericFlyer(e, vcp, light, bee, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.95f, now, 1.10f, 0.70f);
            vcp.draw();
            return;
        }

        if (mode == 5) {
            ensureVex(pl);
            renderGenericFlyer(e, vcp, light, vex, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now, 1.25f, 0.75f);
            vcp.draw();
            return;
        }

        if (mode == 7) {
            ensurePig(pl);
            renderGroundWalker(e, vcp, light, pig, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now);
            vcp.draw();
            return;
        }

        if (mode == 8) {
            ensureFrog(pl);
            renderGroundWalker(e, vcp, light, frog, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.98f, now);
            vcp.draw();
            return;
        }

        if (mode == 9) {
            ensurePufferfish(pl);
            renderPufferfishOrbit(e, vcp, light, pufferfish, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now);
            vcp.draw();
            return;
        }

        if (mode == 10) {
            ensureSlime(pl);
            renderGroundWalker(e, vcp, light, slime, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.72f, now);
            vcp.draw();
            return;
        }

        ensureFox(pl);
        renderFoxAI(e, vcp, light, fox, pl, pt, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, scale * 0.92f, now);
        vcp.draw();
    }


    private void beginReturn(Vec3d from, Vec3d prev, long now) {
        returnActive = true;
        returnStartMs = now;
        returnFrom = from;
        returnPrev = prev;
        returnHasFrom = true;
    }

    private void renderReturn(WorldRenderEvent e,
                              VertexConsumerProvider.Immediate vcp,
                              int light,
                              PlayerEntity pl,
                              float pt,
                              float dt,
                              double px, double py, double pz,
                              Vec3d forward, Vec3d right,
                              float scale,
                              long now) {

        if (raven == null) return;
        if (!returnHasFrom) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;
            return;
        }

        double t = now / 1000.0;
        Vec3d target = computeLeftShoulder(px, py, pz, pl, forward, right, t);

        float p0 = (float) MathHelper.clamp((now - returnStartMs) / (float) RETURN_MS, 0.0, 1.0);
        float a = easeInOut(p0);

        Vec3d pos = lerpVec(returnFrom, target, a);
        float arc = (float) Math.sin(a * Math.PI) * 0.14f;
        pos = new Vec3d(pos.x, pos.y + arc, pos.z);

        Vec3d dir = pos.subtract(returnPrev);
        if (dir.lengthSquared() < 1.0e-8) dir = target.subtract(pos);
        if (dir.lengthSquared() < 1.0e-8) dir = new Vec3d(0.0, 0.0, 1.0);

        float yaw = yawFromDir(dir);
        float pitch = pitchFromDir(dir);

        animateParrotWingsConstant(raven, t, 2.35f);
        setEntityPoseAngles(raven, yaw, pitch, pl.age);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(raven);
        if (!(rr instanceof EntityRenderer)) return;

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.scale(scale * 1.28f, scale * 1.28f, scale * 1.28f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);
        renderCompat((EntityRenderer) rr, raven, yaw, pt, ms, useVcp, light);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        ms.pop();

        returnPrev = pos;

        if (p0 >= 0.9995f) {
            returnActive = false;
            returnStartMs = 0L;
            returnFrom = Vec3d.ZERO;
            returnPrev = Vec3d.ZERO;
            returnHasFrom = false;

            smoothPos = target;
            prevSmoothPos = target;
            smoothYaw = yaw;
            smoothPitch = pitch;
        }
    }

    private void renderUltimate(WorldRenderEvent e,
                                VertexConsumerProvider.Immediate vcp,
                                int light,
                                PlayerEntity pl,
                                float pt,
                                float dt,
                                double px, double py, double pz,
                                Vec3d forward, Vec3d right,
                                float radiusBase, float heightBase, float speed, float scale,
                                long now) {

        long elapsed = now - ultStartMs;
        if (elapsed >= ULT_MS) {
            stopUltSound();
            ultActive = false;

            float h = (float) pl.getHeight();
            float headY = (float) (py + h) + 0.10f;

            Vec3d top = new Vec3d(px, headY + 1.65, pz).add(forward.multiply(0.35));

            Vec3d from = top;
            Vec3d prev = top.subtract(new Vec3d(0.0, 0.0, 0.05));
            beginReturn(from, prev, now);

            ultStartMs = 0L;
            ultLastMs = 0L;
            ultStartPos = Vec3d.ZERO;
            ultStartYaw = 0.0f;
            illusions.clear();
            return;
        }

        float dt2 = ultLastMs == 0L ? dt : MathHelper.clamp((now - ultLastMs) / 1000.0f, 0.0f, 0.06f);
        ultLastMs = now;

        double t = now / 1000.0;

        Camera cam = p.mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();

        float h = (float) pl.getHeight();
        float footY = (float) py - 0.02f;
        float headY = (float) (py + h) + 0.10f;
        Vec3d center = new Vec3d(px, py + h * 0.52, pz);

        float pAsc = MathHelper.clamp(elapsed / (float) ULT_ASCEND_MS, 0.0f, 1.0f);
        float pIn = MathHelper.clamp((elapsed - ULT_ASCEND_MS) / (float) ULT_IN_MS, 0.0f, 1.0f);
        float pHold = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS)) / (float) ULT_HOLD_MS, 0.0f, 1.0f);
        float pBreak = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS)) / (float) ULT_BREAK_MS, 0.0f, 1.0f);
        float pDisp = MathHelper.clamp((elapsed - (ULT_ASCEND_MS + ULT_IN_MS + ULT_HOLD_MS + ULT_BREAK_MS)) / (float) ULT_DISPERSE_MS, 0.0f, 1.0f);

        float eAsc = easeInOut(pAsc);
        float eIn = easeInOut(pIn);
        float eHold = easeInOut(pHold);
        float eBreak = easeInOut(pBreak);
        float eDisp = easeInOut(pDisp);

        float attach = MathHelper.clamp(eIn * 1.10f + eHold, 0.0f, 1.0f);
        float detach = MathHelper.clamp(eBreak * 0.95f + eDisp, 0.0f, 1.0f);

        float yPad = 0.10f;
        float yMin = footY + yPad;
        float yMax = headY - yPad;

        float bodyR = 0.34f + 0.18f * attach;
        float shellR0 = MathHelper.clamp(1.05f + radiusBase * 0.55f, 1.05f, 2.40f);
        float shellR1 = MathHelper.clamp(0.24f + radiusBase * 0.12f, 0.24f, 0.62f);
        float shellR = lerp(shellR0, shellR1, easeInOut(attach));

        float fogAlpha = MathHelper.clamp(attach * (1.0f - detach), 0.0f, 1.0f);
        fogAlpha = fogAlpha * fogAlpha;

        int glowCol = p.ravenColor.getColor();
        float glowA = MathHelper.clamp((p.ravenGlow.getValue() / 100.0f) * 0.85f, 0.0f, 1.0f);

        float birdsAlpha = MathHelper.clamp((p.ravenAlpha.getValue() / 255.0f) * (0.85f + 0.25f * attach) * (1.0f - 0.80f * detach), 0.0f, 1.0f);

        MatrixStack ms = e.getStack();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(true);

        {
            VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);
            renderMainAscendRaven(ms, useVcp, light, pl, pt, forward, right, px, py, pz, headY, eAsc, attach, detach, (float) t, scale, elapsed);
        }

        long spawnDelay = 520L;
        float spawnStep = 14.0f;

        VertexConsumerProvider useVcp = new TextureSwapVcp(vcp, VORON_TEX);

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);

            float aIn = easeInOut(MathHelper.clamp((elapsed - spawnDelay - (long) (i * spawnStep)) / 860.0f, 0.0f, 1.0f));
            float aOut = 1.0f - easeInOut(MathHelper.clamp(eDisp * 1.12f + i * 0.0085f, 0.0f, 1.0f));
            float alpha = aIn * aOut * birdsAlpha;

            if (alpha <= 0.0035f) continue;

            float layerK = (float) Math.sin(il.layer * Math.PI);
            layerK = 0.66f + 0.62f * layerK;

            float rrBase = lerp(shellR, bodyR, easeInOut(attach));
            float rr = rrBase * layerK;

            double ang = il.ang0
                    + (t * (2.35 + speed * 0.85) * il.spinMul)
                    + (Math.sin(t * 0.95 + il.seedA * 0.01) * 0.24);

            float yL = lerp(yMin, yMax, il.layer);
            float yW = (float) (Math.sin(t * 3.35 + il.seedB * 0.01) * (0.08 + 0.12 * (1.0f - detach)));

            float jitter = (float) ((0.040 + 0.085 * (1.0f - attach)) * (1.0f - 0.55f * detach));
            float jx = (float) (Math.sin(t * 6.4 + il.seedA) * jitter);
            float jz = (float) (Math.cos(t * 6.1 + il.seedB) * jitter);

            Vec3d bodyPoint = new Vec3d(
                    center.x + Math.cos(ang) * bodyR,
                    yL,
                    center.z + Math.sin(ang) * bodyR
            );

            Vec3d target = new Vec3d(
                    center.x + Math.cos(ang) * rr + jx,
                    yL + yW,
                    center.z + Math.sin(ang) * rr + jz
            );

            il.lastBodyPoint = bodyPoint;

            if (detach > 0.0f) {
                if (!il.burst) {
                    il.burst = true;
                    il.burstMs = now;
                    Vec3d out = target.subtract(center);
                    double l = out.length();
                    if (l > 1.0e-6) out = out.multiply(1.0 / l);
                    else out = new Vec3d(0.0, 1.0, 0.0);
                    Vec3d kick = out.multiply(2.8 + 7.2 * detach)
                            .add(il.fly.multiply(2.4 + 6.4 * detach))
                            .add(0.0, 0.65 + 1.05 * detach, 0.0);
                    il.vel = kick;
                    il.pos = target;
                    il.prevPos = il.pos;
                    il.hasPos = true;
                }
            }

            if (!il.hasPos) {
                il.pos = target;
                il.prevPos = target;
                il.vel = Vec3d.ZERO;
                il.hasPos = true;
            }

            il.prevPos = il.pos;

            if (!il.burst) {
                Vec3d dv = target.subtract(il.pos);
                Vec3d acc = dv.multiply(0.58 + 0.34 * attach);
                il.vel = il.vel.add(acc.multiply(dt2 * 18.0));
                il.vel = il.vel.multiply(0.84 + 0.10 * attach);
                il.pos = il.pos.add(il.vel.multiply(dt2 * 18.0));
            } else {
                il.vel = il.vel.multiply(0.986);
                il.vel = il.vel.add(0.0, -0.012 * dt2 * 60.0, 0.0);
                il.vel = il.vel.add(
                        Math.sin(t * 1.35 + il.seedA * 0.02) * 0.010 * dt2 * 60.0,
                        Math.sin(t * 1.55 + il.seedB * 0.02) * 0.006 * dt2 * 60.0,
                        Math.cos(t * 1.30 + il.seedB * 0.02) * 0.010 * dt2 * 60.0
                );
                il.pos = il.pos.add(il.vel.multiply(dt2 * (19.5 + 10.5 * detach)));
            }

            Vec3d dir = il.pos.subtract(il.prevPos);
            if (dir.lengthSquared() < 1.0e-8) dir = target.subtract(center);
            float yaw = yawFromDir(dir);
            float pitch = pitchFromDir(dir);

            animateParrotWingsConstant(il.entity, t + il.seedA * 0.004, 1.25f + 0.65f * attach);

            ms.push();
            ms.translate(il.pos.x, il.pos.y, il.pos.z);
            ms.scale(scale * 1.22f, scale * 1.22f, scale * 1.22f);

            setEntityPoseAngles(il.entity, yaw, pitch, pl.age);

            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            renderCompat((EntityRenderer) p.mc.getEntityRenderDispatcher().getRenderer(il.entity), il.entity, yaw, pt, ms, useVcp, light);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            ms.pop();
        }

        vcp.draw();

        if (fogAlpha > 0.010f && detach < 0.985f && attach > 0.18f && elapsed > (ULT_ASCEND_MS + 220L)) {
            renderBirdFog(ms, cam, camPos, (float) t, attach, detach, fogAlpha, glowCol, glowA, center, yMin, yMax, bodyR);
        }

        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderMainAscendRaven(MatrixStack ms,
                                       VertexConsumerProvider vcp,
                                       int light,
                                       PlayerEntity pl,
                                       float pt,
                                       Vec3d forward, Vec3d right,
                                       double px, double py, double pz,
                                       float headY,
                                       float eAsc,
                                       float attach,
                                       float detach,
                                       float t,
                                       float scale,
                                       long elapsed) {

        if (raven == null) return;

        Vec3d start = computeLeftShoulder(px, py, pz, pl, forward, right, t);
        Vec3d top = new Vec3d(px, headY + 1.65, pz).add(forward.multiply(0.35));

        Vec3d pos = lerpVec(start, top, eAsc);

        float par = MathHelper.clamp((elapsed - ULT_ASCEND_MS) / 220.0f, 0.0f, 1.0f);
        float hover = easeInOut(par) * (1.0f - MathHelper.clamp(detach * 1.10f, 0.0f, 1.0f));

        float wob = 0.06f + 0.05f * attach;
        float ox = (float) (Math.cos(t * 2.2f) * (0.12f + 0.10f * hover));
        float oz = (float) (Math.sin(t * 2.2f) * (0.12f + 0.10f * hover));
        float oy = (float) (Math.sin(t * 3.4f) * (wob + 0.03f * hover));

        pos = new Vec3d(pos.x + ox, pos.y + oy, pos.z + oz);

        Vec3d vel = new Vec3d(-Math.sin(t * 2.2f), 0.0, Math.cos(t * 2.2f));
        float yaw = yawFromDir(vel);
        float pitch = -8.0f + (float) Math.sin(t * 2.8f) * 2.8f;

        float hold = MathHelper.clamp(attach * (1.0f - 0.70f * detach), 0.0f, 1.0f);
        animateParrotWingsConstant(raven, t, 1.75f + 0.85f * hold);
        setEntityPoseAngles(raven, yaw, pitch, pl.age);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(raven);
        if (!(rr instanceof EntityRenderer)) return;

        float a = 1.0f - MathHelper.clamp(detach * 1.10f, 0.0f, 1.0f);
        if (a <= 0.01f) return;

        ms.push();
        ms.translate(pos.x, pos.y, pos.z);
        ms.scale(scale * 1.35f, scale * 1.35f, scale * 1.35f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, a);

        renderCompat((EntityRenderer) rr, raven, yaw, pt, ms, vcp, light);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        ms.pop();
    }

    private void renderBirdFog(MatrixStack ms,
                               Camera cam,
                               Vec3d camPos,
                               float t,
                               float attach,
                               float detach,
                               float fogAlpha,
                               int glowCol,
                               float glowA,
                               Vec3d center,
                               float yMin,
                               float yMax,
                               float bodyR) {

        if (fogAlpha <= 0.0035f) return;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, FEATHER_TEX);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        float dens = 1.05f + 1.55f * attach;
        float baseA = MathHelper.clamp(fogAlpha * dens, 0.0f, 1.0f);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int perBird = 7;
        float segNoise = 0.10f + 0.18f * attach;

        for (int i = 0; i < illusions.size(); i++) {
            Illusion il = illusions.get(i);
            if (!il.hasPos) continue;
            Vec3d p0 = il.pos;
            if (p0 == null) continue;
            if (p0 == Vec3d.ZERO) continue;

            Vec3d body = il.lastBodyPoint == null ? center : il.lastBodyPoint;

            float k0 = 0.72f + 0.28f * (float) Math.sin(t * 1.9f + il.seedA * 0.01f);
            float a0 = MathHelper.clamp(baseA * k0, 0.0f, 1.0f);
            if (a0 <= 0.002f) continue;

            for (int s = 0; s < perBird; s++) {
                float w = perBird <= 1 ? 0.0f : (s / (float) (perBird - 1));
                float ww = 1.0f - w;
                ww = ww * ww;

                float nx = (float) Math.sin(t * (3.4f + s * 0.35f) + il.seedA + s * 1.7f) * segNoise;
                float ny = (float) Math.sin(t * (2.7f + s * 0.28f) + il.seedB + s * 2.3f) * (segNoise * 0.55f);
                float nz = (float) Math.cos(t * (3.2f + s * 0.31f) + il.seedB + s * 1.1f) * segNoise;

                Vec3d pp = lerpVec(p0, body, MathHelper.clamp(0.08f + 0.86f * w, 0.0f, 1.0f));
                pp = new Vec3d(pp.x + nx, pp.y + ny, pp.z + nz);

                float sBase = (0.70f + 1.25f * attach) * (1.12f - 0.10f * w);
                float sPulse = 0.90f + 0.20f * (float) Math.sin(t * 2.1f + il.seedB * 0.01f + s * 0.9f);
                float size = sBase * sPulse;
                size = Math.min(size, 2.10f + 1.05f * attach);

                float aa = MathHelper.clamp(a0 * (0.72f * ww), 0.0f, 1.0f);
                float roll = (t * (85.0f - 35.0f * w)) + (il.seedA + il.seedB) * 0.18f + s * 11.0f;

                pushBillboard(bb, ms, cam, pp.x, pp.y, pp.z, size, roll, 0.0f, 0.0f, 0.0f, aa);
            }
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());

        if (glowA > 0.002f && fogAlpha > 0.010f && detach < 0.985f) {
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

            float rr = ((glowCol >>> 16) & 255) / 255.0f;
            float gg = ((glowCol >>> 8) & 255) / 255.0f;
            float bb0 = (glowCol & 255) / 255.0f;

            BufferBuilder bb2 = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            float gBase = MathHelper.clamp(glowA * fogAlpha * (1.10f + 0.85f * attach), 0.0f, 1.0f);

            for (int i = 0; i < illusions.size(); i++) {
                Illusion il = illusions.get(i);
                if (!il.hasPos) continue;
                Vec3d p0 = il.pos;
                if (p0 == null) continue;
                if (p0 == Vec3d.ZERO) continue;

                float k = 0.60f + 0.40f * (float) Math.sin(t * 2.05f + il.seedA * 0.01f);
                float a = MathHelper.clamp(gBase * k, 0.0f, 1.0f);
                if (a <= 0.002f) continue;

                float s = (0.72f + 1.00f * attach) * (0.90f + 0.18f * (float) Math.sin(t * 1.25f + il.seedB * 0.01f));
                s = Math.min(s, 1.85f + 0.95f * attach);

                float roll = (t * 80.0f) + il.seedB * 0.2f;

                pushBillboard(bb2, ms, cam, p0.x, p0.y, p0.z, s, roll, rr, gg, bb0, a);
            }

            BufferRenderer.drawWithGlobalProgram(bb2.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void pushBillboard(BufferBuilder bb,
                               MatrixStack ms,
                               Camera cam,
                               double x, double y, double z,
                               float size,
                               float rollDeg,
                               float r, float g, float b, float a) {

        if (a <= 0.001f || size <= 0.001f) return;

        ms.push();
        ms.translate(x, y, z);
        ms.multiply(cam.getRotation());
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        Matrix4f m = ms.peek().getPositionMatrix();

        float h = size * 0.5f;

        bb.vertex(m, -h, h, 0).texture(0f, 1f).color(r, g, b, a);
        bb.vertex(m, h, h, 0).texture(1f, 1f).color(r, g, b, a);
        bb.vertex(m, h, -h, 0).texture(1f, 0f).color(r, g, b, a);
        bb.vertex(m, -h, -h, 0).texture(0f, 0f).color(r, g, b, a);

        ms.pop();
    }

    private void playUltSound() {
        stopUltSound();
        if (p.mc.world == null || p.mc.player == null) return;

        PositionedSoundInstance s1 = tryCreateMaster(ITACHI_SOUND_MINECRAFT);
        if (s1 != null) {
            ultSound = s1;
            try {
                p.mc.getSoundManager().play(ultSound);
                return;
            } catch (Throwable ignored) {
                ultSound = null;
            }
        }

        PositionedSoundInstance s2 = tryCreateMaster(ITACHI_SOUND_MRE);
        if (s2 != null) {
            ultSound = s2;
            try {
                p.mc.getSoundManager().play(ultSound);
            } catch (Throwable ignored) {
                ultSound = null;
            }
        }
    }

    private PositionedSoundInstance tryCreateMaster(Identifier id) {
        try {
            SoundEvent ev = SoundEvent.of(id);
            return PositionedSoundInstance.master(ev, 1.0f);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void stopUltSound() {
        if (ultSound == null) return;
        try {
            p.mc.getSoundManager().stop(ultSound);
        } catch (Throwable ignored) {
        }
        ultSound = null;
    }

    private static Vec3d randomDir(Random r) {
        double x = (r.nextDouble() - 0.5) * 2.0;
        double y = (r.nextDouble() - 0.5) * 2.0;
        double z = (r.nextDouble() - 0.5) * 2.0;
        Vec3d v = new Vec3d(x, y, z);
        double l = v.length();
        if (l < 1.0e-6) return new Vec3d(0.0, 1.0, 0.0);
        return v.multiply(1.0 / l);
    }

    private void setEntityPoseAngles(Entity entity, float yaw, float pitch, int age) {
        entity.prevX = entity.getX();
        entity.prevY = entity.getY();
        entity.prevZ = entity.getZ();

        entity.prevYaw = entity.getYaw();
        entity.setYaw(yaw);

        if (entity instanceof ParrotEntity pe) {
            pe.bodyYaw = yaw;
            pe.headYaw = yaw;
            pe.prevBodyYaw = yaw;
            pe.prevHeadYaw = yaw;

            pe.prevPitch = pe.getPitch();
            pe.setPitch(pitch);

            pe.setOnGround(false);
            pe.setPose(EntityPose.STANDING);
            pe.age = age;
            return;
        }

        if (entity instanceof BatEntity be) {
            be.bodyYaw = yaw;
            be.headYaw = yaw;
            be.prevBodyYaw = yaw;
            be.prevHeadYaw = yaw;

            be.prevPitch = be.getPitch();
            be.setPitch(pitch);

            be.setOnGround(false);
            be.setPose(EntityPose.STANDING);
            be.age = age;
            return;
        }

        if (entity instanceof LivingEntity le) {
            try {
                le.bodyYaw = yaw;
                le.headYaw = yaw;
                le.prevBodyYaw = yaw;
                le.prevHeadYaw = yaw;
            } catch (Throwable ignored) {
            }

            le.prevPitch = le.getPitch();
            le.setPitch(pitch);
            le.setPose(EntityPose.STANDING);
            le.age = age;
            try {
                le.setOnGround(false);
            } catch (Throwable ignored) {
            }
        }
    }

    private void setEntityPoseAngles(Entity entity, float yaw, float pitch, int age, boolean onGround, EntityPose pose) {
        entity.prevX = entity.getX();
        entity.prevY = entity.getY();
        entity.prevZ = entity.getZ();

        entity.prevYaw = entity.getYaw();
        entity.setYaw(yaw);

        if (entity instanceof LivingEntity le) {
            try {
                le.bodyYaw = yaw;
                le.headYaw = yaw;
                le.prevBodyYaw = yaw;
                le.prevHeadYaw = yaw;
            } catch (Throwable ignored) {
            }

            le.prevPitch = le.getPitch();
            le.setPitch(pitch);
            le.setPose(pose);
            le.age = age;
            try {
                le.setOnGround(onGround);
            } catch (Throwable ignored) {
            }
        } else {
            entity.setPose(pose);
        }
    }

    private int modeFromSetting() {
        if (p.petIsBat()) return 0;
        if (p.petIsParrot()) return 1;
        if (p.petIsRaven()) return 2;
        if (p.petIsFairy()) return 3;
        if (p.petIsBee()) return 4;
        if (p.petIsVex()) return 5;
        if (p.petIsFox()) return 6;
        if (p.petIsPig()) return 7;
        if (p.petIsFrog()) return 8;
        if (p.petIsPufferfish()) return 9;
        if (p.petIsSlime()) return 10;
        return 1;
    }

    private void resetMotion() {
        smoothPos = Vec3d.ZERO;
        prevSmoothPos = Vec3d.ZERO;
        smoothYaw = 0.0f;
        smoothPitch = 0.0f;

        moveSp = 0.0f;
        wantHoldTime = 0.0f;
        wantOrbitTime = 0.0f;
        holdMode = false;

        orbitAng = 0.0f;
        orbitBlend = 1.0f;

        baseYawDeg = Float.NaN;

        foxSmoothVel = Vec3d.ZERO;
        foxGroundY = Double.NaN;
        foxGroundTargetY = Double.NaN;
        foxGroundLastSampleMs = 0L;
        foxAnimPrevPos = Vec3d.ZERO;
        foxSpeedSm = 0.0f;

        walkerAnimPrevPos = Vec3d.ZERO;
        walkerSpeedSm = 0.0f;
    }

    private void ensureBat(PlayerEntity pl) {
        if (bat == null || bat.getWorld() != p.mc.world) {
            bat = createBat();
            lastBatTickMs = 0L;
        }
        bat.age = pl.age;
    }

    private void ensureParrot(PlayerEntity pl) {
        if (parrot == null || parrot.getWorld() != p.mc.world) parrot = createParrot();
        parrot.age = pl.age;
        setParrotVariantSafe(parrot, 0);
    }

    private void ensureRaven(PlayerEntity pl) {
        if (raven == null || raven.getWorld() != p.mc.world) raven = createParrot();
        raven.age = pl.age;
        try {
            raven.setCustomNameVisible(false);
            raven.setCustomName(null);
        } catch (Throwable ignored) {
        }
        setParrotVariantSafe(raven, 0);
    }

    private void ensureFairy(PlayerEntity pl) {
        if (fairy == null || fairy.getWorld() != p.mc.world) fairy = createAllay();
        fairy.age = pl.age;
    }

    private void ensureBee(PlayerEntity pl) {
        if (bee == null || bee.getWorld() != p.mc.world) bee = createBee();
        bee.age = pl.age;
    }

    private void ensureVex(PlayerEntity pl) {
        if (vex == null || vex.getWorld() != p.mc.world) vex = createVex();
        vex.age = pl.age;
    }

    private void ensureFox(PlayerEntity pl) {
        if (fox == null || fox.getWorld() != p.mc.world) {
            fox = createFox();
            foxState.reset();
            foxLimb.reset();
            foxSmoothVel = Vec3d.ZERO;
            foxGroundY = Double.NaN;
            foxGroundTargetY = Double.NaN;
            foxGroundLastSampleMs = 0L;
            foxAnimPrevPos = Vec3d.ZERO;
            foxSpeedSm = 0.0f;
        }
        fox.age = pl.age;
        setFoxSnowVariantSafe(fox);
        try {
            fox.setCustomNameVisible(false);
            fox.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensurePig(PlayerEntity pl) {
        if (pig == null || pig.getWorld() != p.mc.world) {
            pig = createPig();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
        }
        pig.age = pl.age;
        try {
            pig.setCustomNameVisible(false);
            pig.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensureFrog(PlayerEntity pl) {
        if (frog == null || frog.getWorld() != p.mc.world) {
            frog = createFrog();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
        }
        frog.age = pl.age;
        setFrogColdVariantSafe(frog);
        try {
            frog.setCustomNameVisible(false);
            frog.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensurePufferfish(PlayerEntity pl) {
        if (pufferfish == null || pufferfish.getWorld() != p.mc.world) {
            pufferfish = createPufferfish();
            pufferState.reset();
        }
        pufferfish.age = pl.age;
        try {
            pufferfish.setCustomNameVisible(false);
            pufferfish.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private void ensureSlime(PlayerEntity pl) {
        if (slime == null || slime.getWorld() != p.mc.world) {
            slimeJumpY = 0.0;
            slimeJumpVel = 0.0;
            slimeJumpAir = false;
            slimeLastHopMs = 0L;
            slime = createSlime();
            walkerAnimPrevPos = Vec3d.ZERO;
            walkerSpeedSm = 0.0f;
        }
        slime.age = pl.age;
        setSlimeSizeSafe(slime, 1);
        try {
            slime.setCustomNameVisible(false);
            slime.setCustomName(null);
        } catch (Throwable ignored) {
        }
    }

    private static void setSlimeSizeSafe(SlimeEntity s, int size) {
        if (s == null) return;
        size = MathHelper.clamp(size, 1, 2);
        try {
            for (Method m : s.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (!n.equals("setsize") && !n.contains("setsize")) continue;
                if (m.getParameterCount() == 2) {
                    Class<?>[] p0 = m.getParameterTypes();
                    if ((p0[0] == int.class || p0[0] == Integer.class) && (p0[1] == boolean.class || p0[1] == Boolean.class)) {
                        m.invoke(s, size, false);
                        return;
                    }
                } else if (m.getParameterCount() == 1) {
                    Class<?> t = m.getParameterTypes()[0];
                    if (t == int.class || t == Integer.class) {
                        m.invoke(s, size);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void renderBatWithParrotLogic(WorldRenderEvent e,
                                          VertexConsumerProvider.Immediate vcp,
                                          int light,
                                          BatEntity b,
                                          PlayerEntity pl,
                                          float pt,
                                          float dt,
                                          double px, double py, double pz,
                                          Vec3d forward, Vec3d right,
                                          float baseYawDeg,
                                          float radiusBase, float heightBase, float speed, float scale,
                                          long now) {

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        b.prevX = b.getX();
        b.prevY = b.getY();
        b.prevZ = b.getZ();
        b.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        b.prevYaw = b.getYaw();
        b.setYaw(smoothYaw);
        b.bodyYaw = smoothYaw;
        b.headYaw = smoothYaw;
        b.prevBodyYaw = smoothYaw;
        b.prevHeadYaw = smoothYaw;

        b.prevPitch = b.getPitch();
        b.setPitch(smoothPitch);

        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);

        batRoost.forceFlying(b);
        tickBatForAnim(b, now);
        b.age = pl.age;

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(b);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, b, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderParrotLike(WorldRenderEvent e,
                                  VertexConsumerProvider.Immediate vcp,
                                  int light,
                                  ParrotEntity bird,
                                  PlayerEntity pl,
                                  float pt,
                                  float dt,
                                  double px, double py, double pz,
                                  Vec3d forward, Vec3d right,
                                  float baseYawDeg,
                                  float radiusBase, float heightBase, float speed, float scale,
                                  long now,
                                  boolean ravenMode) {

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        bird.prevX = bird.getX();
        bird.prevY = bird.getY();
        bird.prevZ = bird.getZ();
        bird.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        bird.prevYaw = bird.getYaw();
        bird.setYaw(smoothYaw);
        bird.bodyYaw = smoothYaw;
        bird.headYaw = smoothYaw;
        bird.prevBodyYaw = smoothYaw;
        bird.prevHeadYaw = smoothYaw;

        bird.prevPitch = bird.getPitch();
        bird.setPitch(smoothPitch);

        bird.setOnGround(false);
        bird.setPose(EntityPose.STANDING);

        float flapSpeed = ravenMode ? 1.15f : 5.25f;
        animateParrotWingsConstant(bird, t, flapSpeed);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(bird);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        float s = ravenMode ? (scale * 1.28f) : scale;

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(s, s, s);

        VertexConsumerProvider useVcp = ravenMode ? new TextureSwapVcp(vcp, VORON_TEX) : vcp;
        renderCompat(renderer, bird, smoothYaw, pt, ms, useVcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderGenericFlyer(WorldRenderEvent e,
                                    VertexConsumerProvider.Immediate vcp,
                                    int light,
                                    Entity ent,
                                    PlayerEntity pl,
                                    float pt,
                                    float dt,
                                    double px, double py, double pz,
                                    Vec3d forward, Vec3d right,
                                    float baseYawDeg,
                                    float radiusBase, float heightBase, float speed, float scale,
                                    long now,
                                    float tickMul,
                                    float pitchMul) {

        if (ent == null) return;

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        ent.prevX = ent.getX();
        ent.prevY = ent.getY();
        ent.prevZ = ent.getZ();
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        Vec3d vEst = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        vEst = vEst.multiply(inv);

        double vMax = 0.22 + 0.25 * speed;
        double vl = Math.sqrt(vEst.x * vEst.x + vEst.z * vEst.z + vEst.y * vEst.y);
        if (vl > vMax && vl > 1.0e-8) vEst = vEst.multiply(vMax / vl);

        try {
            ent.setVelocity(vEst.x, vEst.y * 0.45, vEst.z);
        } catch (Throwable ignored) {
        }

        float p0 = smoothPitch * pitchMul;
        setEntityPoseAngles(ent, smoothYaw, p0, pl.age);

        tickEntityForAnim(ent, now, tickMul);

        Object rr = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderGroundWalker(WorldRenderEvent e,
                                    VertexConsumerProvider.Immediate vcp,
                                    int light,
                                    LivingEntity ent,
                                    PlayerEntity pl,
                                    float pt,
                                    float dt,
                                    double px, double py, double pz,
                                    Vec3d forward, Vec3d right,
                                    float baseYawDeg,
                                    float radiusBase, float heightBase, float speed, float scale,
                                    long now) {

        if (ent == null) return;

        if (ent instanceof SlimeEntity se) setSlimeSizeSafe(se, 1);

        double t = now / 1000.0;

        Vec3d playerPos = new Vec3d(px, py, pz);

        double side = -0.55;
        double back = 0.85;
        Vec3d tgtXZ = playerPos.add(forward.multiply(-back)).add(right.multiply(side));

        double ground = sampleGroundY(tgtXZ.x, tgtXZ.z, py);
        double y0 = Double.isNaN(ground) ? py : ground;

        double yOff = 0.02;
        if (ent instanceof FrogEntity) yOff = 0.00;
        if (ent instanceof SlimeEntity) yOff = 0.01;

        float bob = (float) (Math.sin(t * 2.15) * 0.010);

        if (ent instanceof SlimeEntity) {
            long periodMs = (long) MathHelper.clamp(520.0 - speed * 55.0, 340.0, 520.0);

            if (!slimeJumpAir) {
                if (slimeLastHopMs == 0L) slimeLastHopMs = now;
                if (now - slimeLastHopMs >= periodMs) {
                    slimeLastHopMs = now;

                    double v = 0.34 + 0.08 * MathHelper.clamp(speed, 0.05f, 5.0f);
                    slimeJumpVel = v;
                    slimeJumpAir = true;
                }
            }

            if (slimeJumpAir) {
                double g = -2.10;
                slimeJumpVel = slimeJumpVel + g * dt;
                slimeJumpY = slimeJumpY + slimeJumpVel * dt;

                if (slimeJumpY <= 0.0) {
                    slimeJumpY = 0.0;
                    slimeJumpVel = 0.0;
                    slimeJumpAir = false;
                }
            }

            bob = 0.0f;
        }

        Vec3d target = new Vec3d(tgtXZ.x, y0 + yOff + bob + (ent instanceof SlimeEntity ? slimeJumpY : 0.0), tgtXZ.z);

        if (smoothPos == Vec3d.ZERO) {
            smoothPos = target;
            prevSmoothPos = target;
            walkerAnimPrevPos = target;
            walkerSpeedSm = 0.0f;
            smoothYaw = baseYawDeg;
            smoothPitch = 0.0f;
        }

        prevSmoothPos = smoothPos;

        float posA = 1.0f - (float) Math.exp(-dt / 0.14f);
        smoothPos = smoothPos.add(target.subtract(smoothPos).multiply(posA));

        float yawA = 1.0f - (float) Math.exp(-dt / 0.18f);
        smoothYaw = lerpAngle(smoothYaw, baseYawDeg, yawA);
        smoothPitch = 0.0f;

        Vec3d animPrev = walkerAnimPrevPos == Vec3d.ZERO ? smoothPos : walkerAnimPrevPos;
        walkerAnimPrevPos = smoothPos;

        ent.prevX = animPrev.x;
        ent.prevY = animPrev.y;
        ent.prevZ = animPrev.z;
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        ent.prevYaw = ent.getYaw();
        ent.setYaw(smoothYaw);

        try {
            ent.bodyYaw = smoothYaw;
            ent.headYaw = smoothYaw;
            ent.prevBodyYaw = smoothYaw;
            ent.prevHeadYaw = smoothYaw;
        } catch (Throwable ignored) {
        }

        boolean onGround = true;
        if (ent instanceof SlimeEntity) onGround = !slimeJumpAir;

        setEntityPoseAngles(ent, smoothYaw, 0.0f, pl.age, onGround, EntityPose.STANDING);

        Vec3d d = smoothPos.subtract(animPrev);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;

        double sp = Math.sqrt((d.x * inv) * (d.x * inv) + (d.z * inv) * (d.z * inv));
        float spNow = (float) sp;
        float spA2 = 1.0f - (float) Math.exp(-dt / 0.18f);
        walkerSpeedSm = walkerSpeedSm + (spNow - walkerSpeedSm) * spA2;

        try {
            ent.setNoGravity(true);
        } catch (Throwable ignored) {
        }
        try {
            ent.setOnGround(onGround);
        } catch (Throwable ignored) {
        }
        try {
            if (ent instanceof SlimeEntity) {
                ent.setVelocity(d.x * inv, slimeJumpVel, d.z * inv);
            } else {
                ent.setVelocity(d.x * inv, 0.0, d.z * inv);
            }
        } catch (Throwable ignored) {
        }

        if (ent instanceof PigEntity || ent instanceof FrogEntity) {
            float limbSpeed = walkerSpeedSm * 0.30f;
            walkerLimb.apply(ent, limbSpeed > 0.01f, limbSpeed);
        }

        tickEntityForAnim(ent, now, 1.0f);

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderPufferfishOrbit(WorldRenderEvent e,
                                       VertexConsumerProvider.Immediate vcp,
                                       int light,
                                       PufferfishEntity ent,
                                       PlayerEntity pl,
                                       float pt,
                                       float dt,
                                       double px, double py, double pz,
                                       Vec3d forward, Vec3d right,
                                       float baseYawDeg,
                                       float radiusBase, float heightBase, float speed, float scale,
                                       long now) {

        if (ent == null) return;

        double t = now / 1000.0;

        updateShoulderOrbit(pl, dt, px, py, pz, forward, right, baseYawDeg, radiusBase, heightBase, speed, t);

        ent.prevX = ent.getX();
        ent.prevY = ent.getY();
        ent.prevZ = ent.getZ();
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        Vec3d vEst = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        vEst = vEst.multiply(inv);

        double vMax = 0.20 + 0.22 * speed;
        double vl = Math.sqrt(vEst.x * vEst.x + vEst.z * vEst.z + vEst.y * vEst.y);
        if (vl > vMax && vl > 1.0e-8) vEst = vEst.multiply(vMax / vl);

        try {
            ent.setNoGravity(true);
        } catch (Throwable ignored) {
        }
        try {
            ent.setOnGround(false);
        } catch (Throwable ignored) {
        }
        try {
            ent.setVelocity(vEst.x, vEst.y * 0.40, vEst.z);
        } catch (Throwable ignored) {
        }

        float s = (float) ((Math.sin(t * 1.35) + 1.0) * 0.5);
        int puff;
        if (s < 0.33f) puff = 0;
        else if (s < 0.66f) puff = 1;
        else puff = 2;

        pufferState.apply(ent, puff);

        setEntityPoseAngles(ent, smoothYaw, 0.0f, pl.age, false, EntityPose.STANDING);

        tickEntityForAnim(ent, now, 1.0f);

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, smoothYaw, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static float dirYaw(Direction dir) {
        if (dir == null) return 0.0f;
        try {
            return dir.getPositiveHorizontalDegrees();
        } catch (Throwable ignored) {
        }
        try {
            Method m = dir.getClass().getMethod("asRotation");
            Object r = m.invoke(dir);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = dir.getClass().getMethod("getHorizontal");
            Object r = m.invoke(dir);
            if (r instanceof Number n) return n.floatValue() * 90.0f;
        } catch (Throwable ignored) {
        }
        switch (dir) {
            case SOUTH: return 0.0f;
            case WEST: return 90.0f;
            case NORTH: return 180.0f;
            case EAST: return 270.0f;
            default: return 0.0f;
        }
    }

    private static float dimHeight(Object dims) {
        if (dims == null) return 0.0f;

        try {
            Field f = dims.getClass().getDeclaredField("height");
            try { f.setAccessible(true); } catch (Throwable ignored) {}
            Object v = f.get(dims);
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        try {
            Method m = dims.getClass().getMethod("height");
            Object r = m.invoke(dims);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        try {
            Method m = dims.getClass().getMethod("getHeight");
            Object r = m.invoke(dims);
            if (r instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }

        return 0.0f;
    }

    private void renderFoxAI(WorldRenderEvent e,
                             VertexConsumerProvider.Immediate vcp,
                             int light,
                             FoxEntity ent,
                             PlayerEntity pl,
                             float pt,
                             float dt,
                             double px, double py, double pz,
                             Vec3d forward, Vec3d right,
                             float baseYawDeg,
                             float radiusBase, float heightBase, float speed, float scale,
                             long now) {

        if (ent == null) return;

        setFoxSnowVariantSafe(ent);

        double t = now / 1000.0;

        boolean playerMoving = pl.isSprinting() || moveSp > 0.07f;

        foxState.update(pl, playerMoving, now);

        int mode = foxState.mode;

        Vec3d playerPos = new Vec3d(px, py, pz);

        Vec3d tgtXZ;
        if (mode == 0) {
            double side = -0.55;
            double back = 0.75;
            tgtXZ = playerPos.add(forward.multiply(-back)).add(right.multiply(side));
        } else if (mode == 1) {
            double side = -0.42;
            double back = 0.62;
            tgtXZ = playerPos.add(forward.multiply(-back)).add(right.multiply(side));
        } else {
            double side = -0.48;
            double back = 0.70;
            tgtXZ = playerPos.add(forward.multiply(-back)).add(right.multiply(side));
        }

        double ground = sampleGroundY(tgtXZ.x, tgtXZ.z, py);
        double y0;
        if (Double.isNaN(ground)) y0 = py;
        else y0 = ground;

        double yOff = 0.06;

        if (mode == 2) {
            double stick = -0.30;

            float hs = 0.75f;
            float hh = 0.25f;
            try {
                Object dStand = ent.getDimensions(EntityPose.STANDING);
                Object dSleep = ent.getDimensions(EntityPose.SLEEPING);
                hs = dimHeight(dStand);
                hh = dimHeight(dSleep);
            } catch (Throwable ignored) {
            }

            double extra = Math.max(0.0, (hs - hh) * 0.5);

            yOff = 0.06 + extra + stick;
        }

        float bob = (mode == 0) ? (float) (Math.sin(t * 2.15) * 0.018) : 0.0f;
        if (mode == 2) bob = 0.0f;

        Vec3d target = new Vec3d(tgtXZ.x, y0 + yOff + bob, tgtXZ.z);

        if (smoothPos == Vec3d.ZERO) {
            smoothPos = target;
            prevSmoothPos = target;
            foxAnimPrevPos = target;
            foxSpeedSm = 0.0f;
            smoothYaw = baseYawDeg;
            smoothPitch = 0.0f;
        }

        prevSmoothPos = smoothPos;

        float posTau;
        if (mode == 0) posTau = 0.10f;
        else if (mode == 1) posTau = 0.16f;
        else posTau = 0.22f;

        float posA = 1.0f - (float) Math.exp(-dt / Math.max(0.05f, posTau));
        smoothPos = smoothPos.add(target.subtract(smoothPos).multiply(posA));

        float wantYaw = baseYawDeg;
        float yawTau = mode == 0 ? 0.14f : 0.20f;
        float yawA = 1.0f - (float) Math.exp(-dt / Math.max(0.06f, yawTau));
        smoothYaw = lerpAngle(smoothYaw, wantYaw, yawA);
        smoothPitch = 0.0f;

        Vec3d animPrev = foxAnimPrevPos == Vec3d.ZERO ? smoothPos : foxAnimPrevPos;
        foxAnimPrevPos = smoothPos;

        ent.prevX = animPrev.x;
        ent.prevY = animPrev.y;
        ent.prevZ = animPrev.z;
        ent.setPos(smoothPos.x, smoothPos.y, smoothPos.z);

        ent.prevYaw = ent.getYaw();
        ent.setYaw(smoothYaw);

        try {
            ent.bodyYaw = smoothYaw;
            ent.headYaw = smoothYaw;
            ent.prevBodyYaw = smoothYaw;
            ent.prevHeadYaw = smoothYaw;
        } catch (Throwable ignored) {
        }

        int i = MathHelper.floor((smoothYaw * 4.0f / 360.0f) + 0.5f) & 3;
        Direction dir = switch (i) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };

        boolean onGround = true;

        if (mode == 2) {
            float sleepYaw = dirYaw(dir);

            ent.prevYaw = ent.getYaw();
            ent.setYaw(sleepYaw);

            try {
                ent.bodyYaw = sleepYaw;
                ent.headYaw = sleepYaw;
                ent.prevBodyYaw = sleepYaw;
                ent.prevHeadYaw = sleepYaw;
            } catch (Throwable ignored) {
            }

            foxState.forceSleep(ent, dir);

            setEntityPoseAngles(ent, sleepYaw, 0.0f, pl.age, onGround, EntityPose.STANDING);

            try {
                ent.setNoGravity(true);
            } catch (Throwable ignored) {
            }
            try {
                ent.setVelocity(0.0, 0.0, 0.0);
            } catch (Throwable ignored) {
            }

            tickFoxForAnim(ent, now);
        } else if (mode == 1) {
            foxState.applySittingFlag(ent);
            setEntityPoseAngles(ent, smoothYaw, 0.0f, pl.age, onGround, EntityPose.SITTING);

            try {
                ent.setNoGravity(true);
            } catch (Throwable ignored) {
            }
            try {
                ent.setVelocity(0.0, 0.0, 0.0);
            } catch (Throwable ignored) {
            }

            tickFoxForAnim(ent, now);
        } else {
            foxState.applyMovingFlags(ent);
            setEntityPoseAngles(ent, smoothYaw, 0.0f, pl.age, onGround, EntityPose.STANDING);

            Vec3d d = smoothPos.subtract(animPrev);
            double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
            double sp = Math.sqrt((d.x * inv) * (d.x * inv) + (d.z * inv) * (d.z * inv));

            float spNow = (float) sp;
            float spA2 = 1.0f - (float) Math.exp(-dt / 0.18f);
            foxSpeedSm = foxSpeedSm + (spNow - foxSpeedSm) * spA2;

            float limbSpeed = foxSpeedSm * 0.26f;

            foxLimb.apply(ent, animPrev, smoothPos, dt, true, limbSpeed > 0.01f, limbSpeed);

            try {
                ent.setNoGravity(true);
            } catch (Throwable ignored) {
            }
            try {
                ent.setVelocity(d.x * inv, 0.0, d.z * inv);
            } catch (Throwable ignored) {
            }

            tickFoxForAnim(ent, now);
        }

        Object rr0 = p.mc.getEntityRenderDispatcher().getRenderer(ent);
        if (!(rr0 instanceof EntityRenderer)) return;
        EntityRenderer renderer = (EntityRenderer) rr0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        float yawForRender = (mode == 2) ? dirYaw(dir) : smoothYaw;

        MatrixStack ms = e.getStack();
        ms.push();
        ms.translate(smoothPos.x, smoothPos.y, smoothPos.z);
        ms.scale(scale, scale, scale);

        renderCompat(renderer, ent, yawForRender, pt, ms, vcp, light);

        ms.pop();

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private double sampleGroundY(double x, double z, double fallbackY) {
        if (p.mc.world == null) return Double.NaN;

        int ix = MathHelper.floor(x);
        int iz = MathHelper.floor(z);

        int minY;
        int maxY;
        try {
            minY = p.mc.world.getBottomY();
            maxY = minY + p.mc.world.getHeight();
        } catch (Throwable ignored) {
            minY = -64;
            maxY = 320;
        }

        double maxTopY = fallbackY + 1.25;

        int startY = MathHelper.clamp(MathHelper.floor(fallbackY) + 3, minY + 2, maxY - 2);

        double g0 = scanGroundDown(ix, iz, startY, 90, minY, maxTopY);
        if (!Double.isNaN(g0)) return g0;

        double g1 = scanGroundDown(ix + 1, iz, startY, 90, minY, maxTopY);
        double g2 = scanGroundDown(ix, iz + 1, startY, 90, minY, maxTopY);
        double g3 = scanGroundDown(ix + 1, iz + 1, startY, 90, minY, maxTopY);

        double best = Double.NaN;

        if (!Double.isNaN(g1)) best = g1;
        if (!Double.isNaN(g2)) best = Double.isNaN(best) ? g2 : Math.max(best, g2);
        if (!Double.isNaN(g3)) best = Double.isNaN(best) ? g3 : Math.max(best, g3);

        if (!Double.isNaN(best)) return best;

        double deep = scanGroundDown(ix, iz, startY, 170, minY, maxTopY);
        if (!Double.isNaN(deep)) return deep;

        return Double.NaN;
    }

    private double scanGroundDown(int x, int z, int startY, int depth, int minY, double maxTopY) {
        if (p.mc.world == null) return Double.NaN;

        int endY = Math.max(minY + 1, startY - Math.max(12, depth));

        BlockPos.Mutable m = new BlockPos.Mutable(x, startY, z);

        for (int y = startY; y >= endY; y--) {
            m.set(x, y, z);

            BlockState st;
            try {
                st = p.mc.world.getBlockState(m);
            } catch (Throwable ignored) {
                return Double.NaN;
            }

            if (st == null || st.isAir()) continue;

            VoxelShape sh;
            try {
                sh = st.getCollisionShape(p.mc.world, m);
            } catch (Throwable ignored) {
                sh = null;
            }

            if (sh == null || sh.isEmpty()) continue;

            double top = y + sh.getMax(Direction.Axis.Y);

            if (top > maxTopY) continue;

            return top + 0.015;
        }

        return Double.NaN;
    }

    private void updateShoulderOrbit(PlayerEntity pl,
                                     float dt,
                                     double px, double py, double pz,
                                     Vec3d forward, Vec3d right,
                                     float baseYawDeg,
                                     float radiusBase, float heightBase, float speed,
                                     double t) {

        boolean wantHold = pl.isSprinting() || moveSp > 0.12f;
        boolean wantOrbit = !pl.isSprinting() && moveSp < 0.07f;

        if (wantHold) {
            wantHoldTime += dt;
            wantOrbitTime = 0.0f;
        } else if (wantOrbit) {
            wantOrbitTime += dt;
            wantHoldTime = 0.0f;
        } else {
            wantHoldTime = Math.max(0.0f, wantHoldTime - dt * 0.75f);
            wantOrbitTime = Math.max(0.0f, wantOrbitTime - dt * 0.75f);
        }

        if (!holdMode && wantHoldTime >= 0.16f) holdMode = true;
        if (holdMode && wantOrbitTime >= 0.20f) holdMode = false;

        float blendTau = holdMode ? 0.30f : 0.36f;
        float blendA = 1.0f - (float) Math.exp(-dt / Math.max(0.06f, blendTau));

        float wantBlend = holdMode ? 0.0f : 1.0f;
        orbitBlend = orbitBlend + (wantBlend - orbitBlend) * blendA;
        float b = easeInOut(MathHelper.clamp(orbitBlend, 0.0f, 1.0f));

        float orbitAngVel = speed * 1.25f;
        orbitAng = wrapRad(orbitAng + (orbitAngVel * dt * (0.26f + 0.26f * (1.0f - b))));

        double ox = Math.cos(orbitAng) * radiusBase;
        double oz = Math.sin(orbitAng) * radiusBase;

        double orbitY = py + pl.getStandingEyeHeight() * 0.35 + heightBase + Math.sin(t * 1.6) * 0.055;

        Vec3d orbitTarget = new Vec3d(
                px + right.x * ox + forward.x * oz,
                orbitY,
                pz + right.z * ox + forward.z * oz
        );

        Vec3d shoulderTarget = computeLeftShoulder(px, py, pz, pl, forward, right, t);
        Vec3d target = lerpVec(shoulderTarget, orbitTarget, b);

        float posTau = 0.09f + 0.08f * (1.0f - b);
        float posA2 = 1.0f - (float) Math.exp(-dt / Math.max(0.05f, posTau));

        if (smoothPos == Vec3d.ZERO) {
            prevSmoothPos = target;
            smoothPos = target;
        } else {
            prevSmoothPos = smoothPos;
            smoothPos = smoothPos.add(target.subtract(smoothPos).multiply(posA2));
        }

        Vec3d dPos = smoothPos.subtract(prevSmoothPos);
        double inv = dt > 1.0e-4 ? (1.0 / dt) : 60.0;
        Vec3d vEst = dPos.multiply(inv);

        float orbitYaw;
        if ((vEst.x * vEst.x + vEst.z * vEst.z) > 1.0e-8) {
            orbitYaw = (float) (MathHelper.atan2(vEst.z, vEst.x) * (180.0 / Math.PI)) - 90.0f;
        } else {
            orbitYaw = smoothYaw;
        }

        float holdYaw = baseYawDeg;
        float targetYaw = lerpAngle(holdYaw, orbitYaw, b);

        float yawA = 1.0f - (float) Math.exp(-dt / 0.16f);
        smoothYaw = lerpAngle(smoothYaw, targetYaw, yawA);

        float orbitPitch = (float) MathHelper.clamp(-vEst.y * 7.5, -16.0, 16.0);
        float holdPitch = (float) Math.sin(t * 1.45) * 2.0f;
        float targetPitch = holdPitch + (orbitPitch - holdPitch) * b;

        float pitchA = 1.0f - (float) Math.exp(-dt / 0.20f);
        smoothPitch = smoothPitch + (targetPitch - smoothPitch) * pitchA;
    }

    private void tickBatForAnim(BatEntity bat, long now) {
        if (bat == null) return;
        if (lastBatTickMs == 0L) lastBatTickMs = now;
        long step = 50L;
        long elapsed = now - lastBatTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(3L, elapsed / step);
        for (int i = 0; i < (int) ticks; i++) {
            try {
                bat.tick();
            } catch (Throwable ignored) {
            }
        }
        lastBatTickMs += ticks * step;
    }

    private void tickFoxForAnim(FoxEntity fox, long now) {
        if (fox == null) return;
        if (lastFoxTickMs == 0L) lastFoxTickMs = now;
        long step = 50L;
        long elapsed = now - lastFoxTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(2L, elapsed / step);
        for (int i = 0; i < (int) ticks; i++) {
            try {
                fox.tick();
            } catch (Throwable ignored) {
            }
        }
        lastFoxTickMs += ticks * step;
    }

    private void tickEntityForAnim(Entity e, long now, float mul) {
        if (e == null) return;
        if (lastPetTickMs == 0L) lastPetTickMs = now;
        long step = 50L;
        long elapsed = now - lastPetTickMs;
        if (elapsed < step) return;
        long ticks = Math.min(6L, elapsed / step);
        int k = Math.max(1, Math.min(10, (int) Math.floor(ticks * MathHelper.clamp(mul, 0.35f, 2.35f))));
        for (int i = 0; i < k; i++) {
            try {
                e.tick();
            } catch (Throwable ignored) {
            }
        }
        lastPetTickMs += ticks * step;
    }

    private Vec3d computeLeftShoulder(double px, double py, double pz,
                                      PlayerEntity pl,
                                      Vec3d forward, Vec3d right,
                                      double t) {

        double baseY = py + pl.getStandingEyeHeight() * 0.74 + 0.08;
        baseY += Math.sin(t * 2.9) * 0.030;

        double lr = -0.34;
        double back = 0.12;
        double up = Math.sin(t * 1.25) * 0.016;

        Vec3d backDir = forward.multiply(-1.0);

        return new Vec3d(
                px + right.x * lr + backDir.x * back,
                baseY + up,
                pz + right.z * lr + backDir.z * back
        );
    }

    private BatEntity createBat() {
        BatEntity b = new BatEntity(EntityType.BAT, p.mc.world);
        b.setNoGravity(true);
        b.setSilent(true);
        b.setInvisible(false);
        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);
        b.setCustomNameVisible(false);
        b.setCustomName(null);
        try {
            b.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            b.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return b;
    }

    private ParrotEntity createParrot() {
        ParrotEntity b = new ParrotEntity(EntityType.PARROT, p.mc.world);
        b.setNoGravity(true);
        b.setSilent(true);
        b.setInvisible(false);
        b.setOnGround(false);
        b.setPose(EntityPose.STANDING);
        b.setCustomNameVisible(false);
        b.setCustomName(null);
        try {
            b.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            b.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return b;
    }

    private AllayEntity createAllay() {
        AllayEntity a = new AllayEntity(EntityType.ALLAY, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private BeeEntity createBee() {
        BeeEntity a = new BeeEntity(EntityType.BEE, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private VexEntity createVex() {
        VexEntity a = new VexEntity(EntityType.VEX, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private FoxEntity createFox() {
        FoxEntity a = new FoxEntity(EntityType.FOX, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setFoxSnowVariantSafe(a);
        return a;
    }

    private PigEntity createPig() {
        PigEntity a = new PigEntity(EntityType.PIG, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private FrogEntity createFrog() {
        FrogEntity a = new FrogEntity(EntityType.FROG, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setFrogColdVariantSafe(a);
        return a;
    }

    private PufferfishEntity createPufferfish() {
        PufferfishEntity a = new PufferfishEntity(EntityType.PUFFERFISH, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(false);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        return a;
    }

    private SlimeEntity createSlime() {
        SlimeEntity a = new SlimeEntity(EntityType.SLIME, p.mc.world);
        a.setNoGravity(true);
        a.setSilent(true);
        a.setInvisible(false);
        a.setOnGround(true);
        a.setPose(EntityPose.STANDING);
        a.setCustomNameVisible(false);
        a.setCustomName(null);
        try {
            a.setAiDisabled(true);
        } catch (Throwable ignored) {
        }
        try {
            a.setInvulnerable(true);
        } catch (Throwable ignored) {
        }
        setSlimeSizeSafe(a, 1);
        return a;
    }

    private void animateParrotWingsConstant(ParrotEntity b, double t, float flapSpeed) {
        if (!parrotWing.inited || parrotWing.entityClass != b.getClass()) {
            parrotWing.reset();
            parrotWing.entityClass = b.getClass();
            initParrotWingCompat(b.getClass());
        }

        float devAmp = 0.24f;

        float flap = (float) (Math.sin(t * flapSpeed * 6.2) * 0.5 + 0.5);
        float dev = 0.10f + flap * devAmp;

        trySet(b, parrotWing.prevMaxWingDeviation, getFloat(b, parrotWing.maxWingDeviation));
        trySet(b, parrotWing.maxWingDeviation, dev);

        float ang = (float) (t * flapSpeed * 7.4);
        trySet(b, parrotWing.prevFlapAngle, getFloat(b, parrotWing.flapAngle));
        trySet(b, parrotWing.flapAngle, ang);

        float fp = (float) (t * flapSpeed * 3.6);
        trySet(b, parrotWing.prevFlapProgress, getFloat(b, parrotWing.flapProgress));
        trySet(b, parrotWing.flapProgress, fp);

        tryInvokeNoArg(b, parrotWing.updateLimbs);
    }

    private void initParrotWingCompat(Class<?> cls) {
        parrotWing.maxWingDeviation = findFloatField(cls, "maxWingDeviation");
        parrotWing.prevMaxWingDeviation = findFloatField(cls, "prevMaxWingDeviation");
        parrotWing.flapAngle = findFloatField(cls, "flapAngle");
        parrotWing.prevFlapAngle = findFloatField(cls, "prevFlapAngle");
        parrotWing.flapProgress = findFloatField(cls, "flapProgress");
        parrotWing.prevFlapProgress = findFloatField(cls, "prevFlapProgress");

        for (Method m : cls.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getReturnType() != void.class) continue;
            String n = m.getName().toLowerCase();
            if (n.contains("update") && (n.contains("limb") || n.contains("anim"))) {
                parrotWing.updateLimbs = m;
                break;
            }
        }

        parrotWing.inited = true;
    }

    private void renderCompat(EntityRenderer renderer,
                              Entity entity,
                              float yaw,
                              float tickDelta,
                              Object matrices,
                              Object vcp,
                              int light) {

        if (!compat.inited || compat.rendererClass != renderer.getClass()) {
            compat.reset();
            compat.rendererClass = renderer.getClass();
            initCompat(renderer.getClass());
        }

        try {
            if (compat.mode == 6 && compat.render != null) {
                compat.render.invoke(renderer, entity, yaw, tickDelta, matrices, vcp, light);
                return;
            }

            if (compat.mode == 4 && compat.render != null && compat.getState != null) {
                Object state = compat.getState.invoke(renderer, entity, tickDelta);
                if (state == null) return;
                compat.render.invoke(renderer, state, matrices, vcp, light);
            }
        } catch (Throwable ignored) {
        }
    }

    private void initCompat(Class<?> cls) {
        Method render6 = null;
        Method render4 = null;
        Method getState = null;

        for (Method m : cls.getMethods()) {
            if (!m.getName().equals("render")) continue;
            Class<?>[] p0 = m.getParameterTypes();

            if (p0.length == 6
                    && Entity.class.isAssignableFrom(p0[0])
                    && p0[1] == float.class
                    && p0[2] == float.class
                    && p0[3].getName().endsWith("MatrixStack")
                    && p0[4].getName().endsWith("VertexConsumerProvider")
                    && p0[5] == int.class) {
                render6 = m;
                break;
            }

            if (p0.length == 4
                    && p0[1].getName().endsWith("MatrixStack")
                    && p0[2].getName().endsWith("VertexConsumerProvider")
                    && p0[3] == int.class
                    && p0[0].getName().toLowerCase().contains("renderstate")) {
                render4 = m;
            }
        }

        if (render6 != null) {
            compat.render = render6;
            compat.mode = 6;
            compat.inited = true;
            return;
        }

        if (render4 != null) {
            for (Method m : cls.getMethods()) {
                Class<?>[] p0 = m.getParameterTypes();
                if (p0.length != 2) continue;
                if (!Entity.class.isAssignableFrom(p0[0])) continue;
                if (p0[1] != float.class) continue;
                if (m.getReturnType() == void.class) continue;
                String rn = m.getReturnType().getName().toLowerCase();
                if (!rn.contains("renderstate")) continue;
                getState = m;
                break;
            }

            if (getState != null) {
                compat.render = render4;
                compat.getState = getState;
                compat.mode = 4;
                compat.inited = true;
            }
        }
    }

    private static void setParrotVariantSafe(ParrotEntity p0, int idx) {
        if (p0 == null) return;

        try {
            for (Method m : p0.getClass().getMethods()) {
                if (!m.getName().equals("setVariant")) continue;
                if (m.getParameterCount() != 1) continue;

                Class<?> t = m.getParameterTypes()[0];

                if (t == int.class || t == Integer.class) {
                    m.invoke(p0, idx);
                    return;
                }

                if (t.isEnum()) {
                    Object[] cc = t.getEnumConstants();
                    if (cc != null && cc.length > 0) {
                        int k = MathHelper.clamp(idx, 0, cc.length - 1);
                        m.invoke(p0, cc[k]);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setFrogColdVariantSafe(FrogEntity frog) {
        if (frog == null) return;

        try {
            for (Method m : frog.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                String n = m.getName().toLowerCase();
                if (!n.contains("variant")) continue;

                Class<?> t = m.getParameterTypes()[0];
                if (!t.isEnum()) continue;

                Object warm = findEnumConst(t, "WARM");
                if (warm == null) warm = findEnumConst(t, "GREEN");
                if (warm == null) warm = findEnumConst(t, "TEMPERATE");
                if (warm == null) warm = findEnumConst(t, "COLD");
                if (warm == null) {
                    Object[] cc = t.getEnumConstants();
                    if (cc != null && cc.length > 0) warm = cc[0];
                }
                if (warm == null) continue;

                m.invoke(frog, warm);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field f : frog.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                if (!f.getType().isEnum()) continue;
                String n = f.getName().toLowerCase();
                if (!n.contains("variant")) continue;

                Object warm = findEnumConst(f.getType(), "WARM");
                if (warm == null) warm = findEnumConst(f.getType(), "GREEN");
                if (warm == null) warm = findEnumConst(f.getType(), "TEMPERATE");
                if (warm == null) warm = findEnumConst(f.getType(), "COLD");
                if (warm == null) {
                    Object[] cc = f.getType().getEnumConstants();
                    if (cc != null && cc.length > 0) warm = cc[0];
                }
                if (warm == null) continue;

                try {
                    f.set(frog, warm);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setFoxSnowVariantSafe(FoxEntity fox) {
        if (fox == null) return;

        try {
            for (Method m : fox.getClass().getMethods()) {
                if (m.getParameterCount() != 1) continue;
                String n = m.getName().toLowerCase();
                if (!(n.contains("variant") || n.contains("type"))) continue;

                Class<?> t = m.getParameterTypes()[0];
                if (!t.isEnum()) continue;

                Object snow = findEnumConst(t, "SNOW");
                if (snow == null) snow = findEnumConst(t, "WHITE");
                if (snow == null) {
                    Object[] cc = t.getEnumConstants();
                    if (cc != null && cc.length > 0) snow = cc[0];
                }
                if (snow == null) continue;

                m.invoke(fox, snow);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field f : fox.getClass().getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                if (!f.getType().isEnum()) continue;
                String n = f.getName().toLowerCase();
                if (!(n.contains("variant") || n.contains("type"))) continue;

                Object snow = findEnumConst(f.getType(), "SNOW");
                if (snow == null) snow = findEnumConst(f.getType(), "WHITE");
                if (snow == null) {
                    Object[] cc = f.getType().getEnumConstants();
                    if (cc != null && cc.length > 0) snow = cc[0];
                }
                if (snow == null) continue;

                try {
                    f.set(fox, snow);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object findEnumConst(Class<?> enumCls, String name) {
        if (enumCls == null || !enumCls.isEnum() || name == null) return null;
        try {
            Object[] cc = enumCls.getEnumConstants();
            if (cc == null) return null;
            for (Object o : cc) {
                if (o == null) continue;
                if (o.toString().equalsIgnoreCase(name)) return o;
                if (o instanceof Enum<?> en) {
                    if (en.name().equalsIgnoreCase(name)) return o;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float getFloat(Object o, Field f) {
        if (o == null || f == null) return 0.0f;
        try {
            return f.getFloat(o);
        } catch (Throwable ignored) {
            try {
                Object v = f.get(o);
                if (v instanceof Number n) return n.floatValue();
            } catch (Throwable ignored2) {
            }
        }
        return 0.0f;
    }

    private static void trySet(Object o, Field f, float v) {
        if (o == null || f == null) return;
        try {
            f.setFloat(o, v);
            return;
        } catch (Throwable ignored) {
        }
        try {
            f.set(o, v);
        } catch (Throwable ignored) {
        }
    }

    private static Field findFloatField(Class<?> cls, String name) {
        if (cls == null || name == null) return null;
        Class<?> c = cls;
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }
                if (f.getType() == float.class || f.getType() == Float.class) return f;
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static void tryInvokeNoArg(Object o, Method m) {
        if (o == null || m == null) return;
        try {
            m.invoke(o);
        } catch (Throwable ignored) {
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeInOut(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static Vec3d lerpVec(Vec3d a, Vec3d b, float t) {
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    private static float lerpAngle(float a, float b, float t) {
        float d = MathHelper.wrapDegrees(b - a);
        return a + d * t;
    }

    private static float yawFromDir(Vec3d dir) {
        double x = dir.x;
        double z = dir.z;
        if (Math.abs(x) < 1.0e-9 && Math.abs(z) < 1.0e-9) return 0.0f;
        return (float) (MathHelper.atan2(z, x) * (180.0 / Math.PI)) - 90.0f;
    }

    private static float pitchFromDir(Vec3d dir) {
        double x = dir.x;
        double y = dir.y;
        double z = dir.z;
        double h = Math.sqrt(x * x + z * z);
        if (h < 1.0e-9) return y > 0.0 ? -90.0f : 90.0f;
        return (float) (-(MathHelper.atan2(y, h) * (180.0 / Math.PI)));
    }

    private static float wrapRad(float a) {
        float twoPi = (float) (Math.PI * 2.0);
        a = a % twoPi;
        if (a < 0.0f) a += twoPi;
        return a;
    }

    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    private static boolean isFirstPerson(MinecraftClient mc) {
        if (mc == null) return false;
        try {
            return mc.options.getPerspective().isFirstPerson();
        } catch (Throwable ignored) {
        }
        try {
            Object o = mc.options.getClass().getMethod("getPerspective").invoke(mc.options);
            if (o != null) {
                try {
                    Method m = o.getClass().getMethod("isFirstPerson");
                    Object r = m.invoke(o);
                    if (r instanceof Boolean b) return b;
                } catch (Throwable ignored2) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class Illusion {
        ParrotEntity entity;
        float seedA;
        float seedB;
        float phase;
        float spinMul;
        float u;
        float v;
        Vec3d fly;
        Vec3d pos;
        Vec3d prevPos;
        Vec3d vel;
        boolean hasPos;
        boolean burst;
        long burstMs;
        float layer;
        float ang0;
        Vec3d lastBodyPoint;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class RenderCompat {
        boolean inited = false;
        int mode = 0;
        Class<?> rendererClass = null;
        Method render = null;
        Method getState = null;

        void reset() {
            inited = false;
            mode = 0;
            rendererClass = null;
            render = null;
            getState = null;
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class ParrotWingCompat {
        boolean inited = false;
        Class<?> entityClass = null;
        Field maxWingDeviation = null;
        Field prevMaxWingDeviation = null;
        Field flapAngle = null;
        Field prevFlapAngle = null;
        Field flapProgress = null;
        Field prevFlapProgress = null;
        Method updateLimbs = null;

        void reset() {
            inited = false;
            entityClass = null;
            maxWingDeviation = null;
            prevMaxWingDeviation = null;
            flapAngle = null;
            prevFlapAngle = null;
            flapProgress = null;
            prevFlapProgress = null;
            updateLimbs = null;
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class BatRoostCompat {
        boolean inited = false;
        Class<?> entityClass = null;
        Method setRoosting = null;
        Method setHanging = null;
        Field roosting = null;
        Field hanging = null;

        void reset() {
            inited = false;
            entityClass = null;
            setRoosting = null;
            setHanging = null;
            roosting = null;
            hanging = null;
        }

        void forceFlying(BatEntity b) {
            if (b == null) return;

            if (!inited || entityClass != b.getClass()) {
                reset();
                entityClass = b.getClass();

                for (Method m : b.getClass().getMethods()) {
                    if (m.getParameterCount() != 1) continue;
                    if (m.getParameterTypes()[0] != boolean.class && m.getParameterTypes()[0] != Boolean.class) continue;
                    String n = m.getName().toLowerCase();
                    if (n.contains("roost")) setRoosting = m;
                    if (n.contains("hang")) setHanging = m;
                }

                for (Field f : b.getClass().getDeclaredFields()) {
                    String n = f.getName().toLowerCase();
                    if (f.getType() != boolean.class && f.getType() != Boolean.class) continue;
                    if (n.contains("roost")) roosting = f;
                    if (n.contains("hang")) hanging = f;
                    try {
                        f.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                }

                inited = true;
            }

            try {
                if (setRoosting != null) setRoosting.invoke(b, false);
            } catch (Throwable ignored) {
            }
            try {
                if (setHanging != null) setHanging.invoke(b, false);
            } catch (Throwable ignored) {
            }
            try {
                if (roosting != null) roosting.setBoolean(b, false);
            } catch (Throwable ignored) {
            }
            try {
                if (hanging != null) hanging.setBoolean(b, false);
            } catch (Throwable ignored) {
            }
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class WalkerLimbCompat {
        boolean inited = false;
        Class<?> livingClass = null;
        Method updateLimbsBool = null;
        Method updateLimbsFloat = null;

        void reset() {
            inited = false;
            livingClass = null;
            updateLimbsBool = null;
            updateLimbsFloat = null;
        }

        void apply(LivingEntity e, boolean shouldMove, float speedSm) {
            if (e == null) return;

            initIfNeeded(e.getClass());

            float limb = MathHelper.clamp(speedSm * 0.12f, 0.0f, 1.05f);

            try {
                if (updateLimbsFloat != null) {
                    updateLimbsFloat.invoke(e, limb);
                } else if (updateLimbsBool != null) {
                    updateLimbsBool.invoke(e, shouldMove);
                }
            } catch (Throwable ignored) {
            }
        }

        void initIfNeeded(Class<?> cls) {
            if (cls == null) return;
            if (inited && livingClass == cls) return;

            reset();
            livingClass = cls;

            for (Method m : cls.getMethods()) {
                if (m.getReturnType() != void.class) continue;
                if (m.getParameterCount() != 1) continue;
                String n = m.getName().toLowerCase();

                Class<?> p0 = m.getParameterTypes()[0];

                if ((p0 == boolean.class || p0 == Boolean.class) && n.contains("update") && n.contains("limb")) {
                    updateLimbsBool = m;
                    continue;
                }

                if ((p0 == float.class || p0 == Float.class) && n.contains("update") && n.contains("limb")) {
                    updateLimbsFloat = m;
                }
            }

            inited = true;
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class PufferStateCompat {
        boolean inited = false;
        Class<?> entityClass = null;
        Method setPuffState = null;
        Field puffStateField = null;

        void reset() {
            inited = false;
            entityClass = null;
            setPuffState = null;
            puffStateField = null;
        }

        void apply(PufferfishEntity p, int state) {
            if (p == null) return;

            state = MathHelper.clamp(state, 0, 2);

            if (!inited || entityClass != p.getClass()) {
                reset();
                entityClass = p.getClass();

                for (Method m : entityClass.getMethods()) {
                    if (m.getParameterCount() != 1) continue;
                    String n = m.getName().toLowerCase();
                    if (!n.contains("puff")) continue;
                    Class<?> t = m.getParameterTypes()[0];
                    if (t == int.class || t == Integer.class || t.isEnum()) {
                        setPuffState = m;
                        break;
                    }
                }

                for (Field f : entityClass.getDeclaredFields()) {
                    String n = f.getName().toLowerCase();
                    if (!n.contains("puff")) continue;
                    Class<?> t = f.getType();
                    if (t == int.class || t == Integer.class || t.isEnum()) {
                        try {
                            f.setAccessible(true);
                        } catch (Throwable ignored) {
                        }
                        puffStateField = f;
                        break;
                    }
                }

                inited = true;
            }

            try {
                if (setPuffState != null) {
                    Class<?> t = setPuffState.getParameterTypes()[0];
                    if (t == int.class || t == Integer.class) {
                        setPuffState.invoke(p, state);
                        return;
                    }
                    if (t.isEnum()) {
                        Object[] cc = t.getEnumConstants();
                        if (cc != null && cc.length > 0) {
                            int k = MathHelper.clamp(state, 0, cc.length - 1);
                            setPuffState.invoke(p, cc[k]);
                            return;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                if (puffStateField != null) {
                    Class<?> t = puffStateField.getType();
                    if (t == int.class) {
                        puffStateField.setInt(p, state);
                        return;
                    }
                    if (t == Integer.class) {
                        puffStateField.set(p, state);
                        return;
                    }
                    if (t.isEnum()) {
                        Object[] cc = t.getEnumConstants();
                        if (cc != null && cc.length > 0) {
                            int k = MathHelper.clamp(state, 0, cc.length - 1);
                            puffStateField.set(p, cc[k]);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class FoxStateCompat {
        int mode = 0;
        long modeSinceMs = 0L;

        long lastMoveMs = 0L;
        long lastIdleMs = 0L;

        Vec3d wanderTarget = null;
        long nextWanderMs = 0L;

        Random rnd = new Random(0xC0FFEE);

        boolean inited = false;
        Class<?> foxClass = null;
        Method setSitting = null;
        Method setSleeping = null;
        Method setCrouching = null;
        Method setRollingHead = null;
        Method setRolling = null;
        Field sitting = null;
        Field sleeping = null;
        Field crouching = null;
        Field rollingHead = null;
        Field rolling = null;

        Method setSleepingDirection = null;
        Field sleepingDirection = null;

        void forceSleep(FoxEntity fox, Direction dir) {
            if (fox == null) return;
            initIfNeeded(fox);

            setBool(fox, setSitting, sitting, false);
            setBool(fox, setSleeping, sleeping, true);

            setBool(fox, setCrouching, crouching, false);
            setBool(fox, setRollingHead, rollingHead, false);
            setBool(fox, setRolling, rolling, false);

            Direction use = dir == null ? Direction.SOUTH : dir;

            try {
                if (setSleepingDirection != null) {
                    Class<?> p0 = setSleepingDirection.getParameterTypes()[0];
                    if (p0 == Direction.class) {
                        setSleepingDirection.invoke(fox, use);
                    } else {
                        setSleepingDirection.invoke(fox, java.util.Optional.of(use));
                    }
                } else if (sleepingDirection != null) {
                    Class<?> t = sleepingDirection.getType();
                    if (t == Direction.class) {
                        sleepingDirection.set(fox, use);
                    } else {
                        sleepingDirection.set(fox, java.util.Optional.of(use));
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        void reset() {
            mode = 0;
            modeSinceMs = 0L;
            lastMoveMs = 0L;
            lastIdleMs = 0L;
            wanderTarget = null;
            nextWanderMs = 0L;
            rnd = new Random(0xC0FFEE ^ System.nanoTime());
            inited = false;
            foxClass = null;
            setSitting = null;
            setSleeping = null;
            setCrouching = null;
            setRollingHead = null;
            setRolling = null;
            sitting = null;
            sleeping = null;
            crouching = null;
            rollingHead = null;
            rolling = null;
            setSleepingDirection = null;
            sleepingDirection = null;
        }

        void update(PlayerEntity pl, boolean playerMoving, long now) {
            if (lastMoveMs == 0L) lastMoveMs = now;
            if (lastIdleMs == 0L) lastIdleMs = now;

            if (playerMoving) lastMoveMs = now;
            else lastIdleMs = now;

            long idleFor = now - lastMoveMs;
            long moveFor = now - lastIdleMs;

            int want;
            if (idleFor > 18000L) want = 2;
            else if (idleFor > 4500L) want = 1;
            else want = 0;

            if (mode == 2 && playerMoving && moveFor > 650L) want = 0;
            if (mode == 1 && playerMoving && moveFor > 450L) want = 0;

            if (modeSinceMs == 0L) modeSinceMs = now;

            long since = now - modeSinceMs;

            if (want != mode) {
                long gate = mode == 2 ? 900L : 650L;
                if (since > gate) {
                    mode = want;
                    modeSinceMs = now;
                    wanderTarget = null;
                    nextWanderMs = 0L;
                }
            }

            if (mode == 1) {
                if (nextWanderMs == 0L) nextWanderMs = now + 12000L + (long) (rnd.nextDouble() * 9000.0);
                if (wanderTarget == null && now > nextWanderMs) nextWanderMs = now;
            }
        }

        void initIfNeeded(FoxEntity fox) {
            if (fox == null) return;
            if (inited && foxClass == fox.getClass()) return;

            inited = true;
            foxClass = fox.getClass();

            for (Method m : foxClass.getMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?> p0 = m.getParameterTypes()[0];
                String n = m.getName().toLowerCase();

                if ((p0 == boolean.class || p0 == Boolean.class)) {
                    if (n.contains("sit")) setSitting = m;
                    if (n.contains("sleep")) setSleeping = m;
                    if (n.contains("crouch")) setCrouching = m;
                    if (n.contains("roll") && n.contains("head")) setRollingHead = m;
                    else if (n.contains("roll")) setRolling = m;
                }

                if (n.contains("sleep") && n.contains("direction")) {
                    if (p0 == Direction.class) setSleepingDirection = m;
                    else if (p0.getName().toLowerCase().contains("optional")) setSleepingDirection = m;
                }
            }

            for (Field f : foxClass.getDeclaredFields()) {
                String n = f.getName().toLowerCase();

                try {
                    f.setAccessible(true);
                } catch (Throwable ignored) {
                }

                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    if (n.contains("sit")) sitting = f;
                    if (n.contains("sleep")) sleeping = f;
                    if (n.contains("crouch")) crouching = f;
                    if (n.contains("roll") && n.contains("head")) rollingHead = f;
                    else if (n.contains("roll")) rolling = f;
                }

                if (n.contains("sleep") && n.contains("direction")) {
                    Class<?> t = f.getType();
                    if (t == Direction.class || t.getName().toLowerCase().contains("optional")) sleepingDirection = f;
                }
            }
        }

        void applySittingFlag(FoxEntity fox) {
            if (fox == null) return;
            initIfNeeded(fox);

            setBool(fox, setSitting, sitting, true);
            setBool(fox, setCrouching, crouching, false);
            setBool(fox, setRollingHead, rollingHead, false);
            setBool(fox, setRolling, rolling, false);

            boolean sleep = false;
            long idleFor = System.currentTimeMillis() - lastMoveMs;
            if (idleFor > 38000L) sleep = true;
            setBool(fox, setSleeping, sleeping, sleep);

            if (!sleep) clearSleepDir(fox);
        }

        void applyMovingFlags(FoxEntity fox) {
            if (fox == null) return;
            initIfNeeded(fox);

            setBool(fox, setSitting, sitting, false);
            setBool(fox, setSleeping, sleeping, false);

            boolean crouch = false;
            if (mode == 1) {
                long since = System.currentTimeMillis() - modeSinceMs;
                if (since > 8000L && (since % 24000L) < 1300L) crouch = rnd.nextDouble() < 0.35;
            }
            setBool(fox, setCrouching, crouching, crouch);

            setBool(fox, setRollingHead, rollingHead, false);
            setBool(fox, setRolling, rolling, false);

            clearSleepDir(fox);
        }

        void clearSleepDir(FoxEntity fox) {
            if (fox == null) return;

            try {
                if (setSleepingDirection != null) {
                    Class<?> p0 = setSleepingDirection.getParameterTypes()[0];
                    if (p0 != Direction.class) {
                        setSleepingDirection.invoke(fox, java.util.Optional.empty());
                    }
                } else if (sleepingDirection != null) {
                    Class<?> t = sleepingDirection.getType();
                    if (t != Direction.class && t.getName().toLowerCase().contains("optional")) {
                        sleepingDirection.set(fox, java.util.Optional.empty());
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        private static void setBool(Object o, Method m, Field f, boolean v) {
            try {
                if (m != null) {
                    m.invoke(o, v);
                    return;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (f != null) {
                    if (f.getType() == boolean.class) f.setBoolean(o, v);
                    else f.set(o, v);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    static final class FoxLimbCompat {
        boolean inited = false;
        Class<?> livingClass = null;
        Method updateLimbsBool = null;
        Method updateLimbsFloat = null;

        void reset() {
            inited = false;
            livingClass = null;
            updateLimbsBool = null;
            updateLimbsFloat = null;
        }

        void apply(FoxEntity fox, Vec3d prev, Vec3d cur, float dt, boolean onGround, boolean shouldMove, float speedSm) {
            if (fox == null) return;

            initIfNeeded(fox.getClass());

            try {
                fox.prevX = prev.x;
                fox.prevY = prev.y;
                fox.prevZ = prev.z;
            } catch (Throwable ignored) {
            }

            try {
                fox.setOnGround(onGround);
            } catch (Throwable ignored) {
            }

            float limb = MathHelper.clamp(speedSm * 0.10f, 0.0f, 0.90f);

            try {
                if (updateLimbsFloat != null) {
                    updateLimbsFloat.invoke(fox, limb);
                } else if (updateLimbsBool != null) {
                    updateLimbsBool.invoke(fox, shouldMove);
                }
            } catch (Throwable ignored) {
            }
        }

        void initIfNeeded(Class<?> cls) {
            if (cls == null) return;
            if (inited && livingClass == cls) return;

            reset();
            livingClass = cls;

            for (Method m : cls.getMethods()) {
                if (m.getReturnType() != void.class) continue;
                if (m.getParameterCount() != 1) continue;
                String n = m.getName().toLowerCase();

                Class<?> p0 = m.getParameterTypes()[0];

                if ((p0 == boolean.class || p0 == Boolean.class) && n.contains("update") && n.contains("limb")) {
                    updateLimbsBool = m;
                    continue;
                }

                if ((p0 == float.class || p0 == Float.class) && n.contains("update") && n.contains("limb")) {
                    updateLimbsFloat = m;
                }
            }

            inited = true;
        }
    }

    static final class TextureSwapVcp implements VertexConsumerProvider {
        final VertexConsumerProvider parent;
        final Identifier tex;
        final Map<RenderLayer, RenderLayer> cache = new IdentityHashMap<>();

        TextureSwapVcp(VertexConsumerProvider parent, Identifier tex) {
            this.parent = parent;
            this.tex = tex;
        }

        @Override
        public VertexConsumer getBuffer(RenderLayer layer) {
            if (layer == null) return parent.getBuffer(null);
            RenderLayer repl = cache.get(layer);
            if (repl == null) {
                repl = trySwap(layer, tex);
                cache.put(layer, repl);
            }
            return parent.getBuffer(repl);
        }

        private static RenderLayer trySwap(RenderLayer layer, Identifier tex) {
            if (layer == null || tex == null) return layer;

            String n;
            try {
                n = layer.toString().toLowerCase();
            } catch (Throwable ignored) {
                n = "";
            }

            boolean looksEntity = n.contains("entity") || n.contains("cutout") || n.contains("translucent") || n.contains("no_cull") || n.contains("nocull");
            if (!looksEntity) return layer;

            try {
                return RenderLayer.getEntityCutoutNoCull(tex);
            } catch (Throwable ignored) {
            }
            try {
                return RenderLayer.getEntityCutout(tex);
            } catch (Throwable ignored) {
            }
            try {
                return RenderLayer.getEntityTranslucent(tex);
            } catch (Throwable ignored) {
            }
            return layer;
        }
    }
}
