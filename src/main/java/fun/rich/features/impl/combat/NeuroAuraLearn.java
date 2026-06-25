package fun.rich.features.impl.combat;

import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.features.aura.striking.StrikerConstructor;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

final class NeuroAuraLearn {

    private static final float NEURO_LEARN_RATE_TRACK = 0.10f;
    private static final float NEURO_LEARN_RATE_ATTACK = 0.16f;

    private static final float TRACK_ERR_SOFT = 28.0f;
    private static final float TRACK_ERR_HARD = 85.0f;
    private static final float ATTACK_ERR_SOFT = 38.0f;
    private static final float ATTACK_ERR_HARD = 105.0f;

    private static final float MIN_LR_MUL = 0.02f;
    private static final float MAX_LR_MUL = 1.35f;

    private static final int PATTERN_SAMPLES_TRACK = 2;
    private static final int PATTERN_SAMPLES_ATTACK = 4;

    private static final int POINT_SAMPLES_TRACK = 3;
    private static final int POINT_SAMPLES_ATTACK = 6;

    private static final float CAM_VEL_EMA = 0.18f;

    private static final long EXEC_NOTIFY_COOLDOWN_MS = 220L;
    private static final float EXEC_GCD_MIN = 0.006f;
    private static final float EXEC_GCD_MAX = 0.45f;

    private static final long SAVE_COOLDOWN_MS = 1400L;

    private static Method turnsGetYaw;
    private static Method turnsGetPitch;
    private static Field turnsYawField;
    private static Field turnsPitchField;

    private static Constructor<?> turnsCtorFF;
    private static Constructor<?> turnsCtorDD;
    private static boolean turnsCtorTried = false;

    private static Field swingTicksFieldA;
    private static Field swingTicksFieldB;
    private static Field swingTicksFieldC;
    private static Method swingTicksMethod;

    private static Field auraAimModeField;
    private static Field auraNeuroModeField;

    private final NeuroRotationModel model = new NeuroRotationModel();

    private NeuroAuraExec exec;
    private boolean execReplayLoaded = false;
    private long lastExecNotifyMs = 0L;

    private boolean loaded = false;

    private int prevSwingTicks = 0;
    private int attackBurstTicks = 0;

    private float prevCamYaw = 0.0f;
    private float prevCamPitch = 0.0f;
    private float emaCamVelYaw = 0.0f;
    private float emaCamVelPitch = 0.0f;

    private float lastClampedYaw = 0.0f;
    private float lastClampedPitch = 0.0f;
    private boolean hasLastClamped = false;

    private int lastTargetId = Integer.MIN_VALUE;
    private int targetStableTicks = 0;

    private boolean dirty = false;
    private long lastSaveMs = 0L;

    NeuroRotationModel getModel() {
        return model;
    }

    NeuroRotationModel getHumanizerModel() {
        return model;
    }

    void setExec(NeuroAuraExec exec) {
        this.exec = exec;
        this.execReplayLoaded = false;
        this.lastExecNotifyMs = 0L;
    }

    void attachExec(NeuroAuraExec exec) {
        setExec(exec);
    }

    void bindExec(NeuroAuraExec exec) {
        setExec(exec);
    }

    NeuroAuraExec.NeuroRotationModel getReplayModel() {
        if (exec == null) return null;
        return exec.model;
    }

    NeuroRotationModel.HumanizerProfile suggestHumanizer(LivingEntity target, boolean attackSample) {
        float camVel = (float) Math.sqrt(emaCamVelYaw * emaCamVelYaw + emaCamVelPitch * emaCamVelPitch);
        return model.suggestHumanizer(target, attackSample, camVel);
    }

    void onActivate(Aura a) {
        prevSwingTicks = 0;
        attackBurstTicks = 0;
        hasLastClamped = false;
        lastTargetId = Integer.MIN_VALUE;
        targetStableTicks = 0;

        dirty = false;
        loaded = false;
        lastSaveMs = 0L;

        execReplayLoaded = false;
        lastExecNotifyMs = 0L;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            prevSwingTicks = neuroSwingTicks(mc.player);
            prevCamYaw = mc.player.getYaw();
            prevCamPitch = mc.player.getPitch();
            emaCamVelYaw = 0.0f;
            emaCamVelPitch = 0.0f;
        }
    }

    void onDeactivate(Aura a) {
        flushSave(a);
        if (exec != null) {
            try {
                exec.notifyModelUpdated();
            } catch (Exception ignored) {
            }
        }
    }

    void learnStep(Aura a, boolean onAttack) {
        if (a == null) return;
        if (!auraIsNeuroLearn(a)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!loaded) {
            loaded = true;
            model.load();
            prevSwingTicks = neuroSwingTicks(mc.player);
            prevCamYaw = mc.player.getYaw();
            prevCamPitch = mc.player.getPitch();
            emaCamVelYaw = 0.0f;
            emaCamVelPitch = 0.0f;
        }

        ensureExecReplayLoaded();

        LivingEntity t = a.getTarget();
        if (t == null) return;

        int tid;
        try {
            tid = t.getId();
        } catch (Exception e) {
            tid = System.identityHashCode(t);
        }

        if (tid != lastTargetId) {
            lastTargetId = tid;
            targetStableTicks = 0;
            hasLastClamped = false;
        } else {
            targetStableTicks++;
        }

        int curSwing = neuroSwingTicks(mc.player);
        boolean justAttacked = neuroSwingStarted(prevSwingTicks, curSwing);
        prevSwingTicks = curSwing;

        if (justAttacked) attackBurstTicks = 3;
        boolean burstAttack = onAttack || attackBurstTicks > 0;

        float camYaw = mc.player.getYaw();
        float camPitch = mc.player.getPitch();

        float dYawTick = wrapDeg(camYaw - prevCamYaw);
        float dPitchTick = camPitch - prevCamPitch;

        prevCamYaw = camYaw;
        prevCamPitch = camPitch;

        emaCamVelYaw = lerp(emaCamVelYaw, dYawTick, CAM_VEL_EMA);
        emaCamVelPitch = lerp(emaCamVelPitch, dPitchTick, CAM_VEL_EMA);

        float camVel = (float) Math.sqrt(emaCamVelYaw * emaCamVelYaw + emaCamVelPitch * emaCamVelPitch);

        StrikerConstructor.AttackPerpetratorConfigurable cfg = a.getCachedConfig() != null ? a.getCachedConfig() : a.getConfig();
        Turns baseAngle = cfg != null ? cfg.getAngle() : a.getConfig().getAngle();

        float baseYaw = turnsYaw(baseAngle, camYaw);
        float basePitch = turnsPitch(baseAngle, camPitch);

        float rawDy = wrapDeg(camYaw - baseYaw);
        float rawDp = camPitch - basePitch;

        float err = (float) Math.sqrt(rawDy * rawDy + rawDp * rawDp);

        float soft = burstAttack ? ATTACK_ERR_SOFT : TRACK_ERR_SOFT;
        float hard = burstAttack ? ATTACK_ERR_HARD : TRACK_ERR_HARD;

        float clipMul = 1.0f;
        if (err > soft) {
            float tMul = soft / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, MIN_LR_MUL, 1.0f);
        }
        if (err > hard) {
            float tMul = hard / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, 0.02f, 1.0f);
        }

        float velMul = clamp(0.55f + camVel * 0.11f, 0.55f, 1.25f);
        float stableMul = clamp(0.35f + (targetStableTicks / 8.0f), 0.35f, 1.0f);

        float lrBase = burstAttack ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        float lrMul = clipMul * velMul * stableMul;
        if (burstAttack && justAttacked) lrMul *= 1.10f;
        lrMul = clamp(lrMul, MIN_LR_MUL, MAX_LR_MUL);

        float maxErr = hard * 1.65f;
        float dy = clamp(rawDy, -maxErr, maxErr);
        float dp = clamp(rawDp, -maxErr, maxErr);

        float clampedYaw = baseYaw + dy;
        float clampedPitch = basePitch + dp;

        Box hb = a.getCachedHitbox();
        Vec3d computed = a.getCachedPoint();
        Vec3d aimedPoint = neuroAimPointFromView(hb, computed, t);

        int patternSamples = burstAttack ? PATTERN_SAMPLES_ATTACK : PATTERN_SAMPLES_TRACK;
        int pointSamples = burstAttack ? POINT_SAMPLES_ATTACK : POINT_SAMPLES_TRACK;

        if (camVel > 3.0f) {
            pointSamples = Math.min(pointSamples + 1, burstAttack ? 8 : 5);
            patternSamples = Math.min(patternSamples + 1, burstAttack ? 6 : 3);
        }

        if (camVel > 5.0f) {
            pointSamples = Math.min(pointSamples + 1, burstAttack ? 9 : 6);
        }

        if (patternSamples < 1) patternSamples = 1;
        if (pointSamples < 1) pointSamples = 1;

        if (!hasLastClamped) {
            lastClampedYaw = clampedYaw;
            lastClampedPitch = clampedPitch;
            hasLastClamped = true;
        }

        float dYawPath = wrapDeg(clampedYaw - lastClampedYaw);
        float dPitchPath = clampedPitch - lastClampedPitch;

        int seedBase = mixSeed(mc.player.age, tid);

        int corrLabel = -1;
        try {
            corrLabel = corrIndex(a.getCorrectionType());
        } catch (Exception ignored) {
        }

        if (corrLabel >= 0) {
            try {
                model.learnCorrection(t, burstAttack, lrBase * lrMul, corrLabel);
            } catch (Exception ignored) {
            }
        }

        float stepYawAbs = Math.abs(dYawTick);
        float stepPitchAbs = Math.abs(dPitchTick);
        float jitterYaw = Math.abs(dYawTick - emaCamVelYaw);
        float jitterPitch = Math.abs(dPitchTick - emaCamVelPitch);

        touchDirty();

        boolean execLearned = false;

        for (int i = 0; i < patternSamples; i++) {
            float tt = (patternSamples == 1) ? 1.0f : (i + 1) / (float) (patternSamples + 1);

            float py = lastClampedYaw + dYawPath * tt;
            float pp = lastClampedPitch + dPitchPath * tt;

            if (burstAttack) {
                float os = (i == patternSamples - 1) ? 1.18f : 1.0f;
                py = lastClampedYaw + dYawPath * tt * os;
                pp = lastClampedPitch + dPitchPath * tt * os;
            }

            float ddy = clamp(wrapDeg(py - baseYaw), -maxErr, maxErr);
            float ddp = clamp(pp - basePitch, -maxErr, maxErr);

            float fy = baseYaw + ddy;
            float fp = basePitch + ddp;

            Turns playerTurns = turnsDirect(fy, fp);
            if (playerTurns == null) {
                playerTurns = turnsFromYawPitch(fy, fp);
            }
            if (playerTurns == null) {
                continue;
            }

            float lr = lrBase * lrMul * (1.0f / Math.max(1, patternSamples));

            for (int p = 0; p < pointSamples; p++) {
                Vec3d pt = pickLearnPoint(hb, aimedPoint, computed, t, seedBase, i, p, pointSamples, burstAttack, camVel);
                if (pt == null) continue;

                float lrPt = lr;
                if (!burstAttack && p > 0) lrPt *= 0.62f;
                if (burstAttack && p > 1) lrPt *= 0.75f;

                try {
                    model.learn(t, baseAngle, playerTurns, hb, pt, lrPt, burstAttack, camVel, stepYawAbs, stepPitchAbs, jitterYaw, jitterPitch);
                } catch (Exception ignored) {
                }

                if (learnExecReplaySample(t, hb, pt, burstAttack, ddy, ddp, stepYawAbs, stepPitchAbs, jitterYaw, jitterPitch, lrPt, lrBase, ampFrom(ddy, ddp))) {
                    execLearned = true;
                }
            }
        }

        lastClampedYaw = clampedYaw;
        lastClampedPitch = clampedPitch;

        if (attackBurstTicks > 0) attackBurstTicks--;

        if (execLearned) {
            notifyExecReplayUpdated(false);
        }

        maybeSave(false);
    }

    void flushSave(Aura a) {
        if (!dirty) {
            notifyExecReplayUpdated(true);
            return;
        }
        maybeSave(true);
        notifyExecReplayUpdated(true);
    }

    private void touchDirty() {
        dirty = true;
    }

    private void maybeSave(boolean force) {
        if (!dirty && !force) return;
        long now = System.currentTimeMillis();
        if (!force && lastSaveMs != 0L && (now - lastSaveMs) < SAVE_COOLDOWN_MS) return;
        try {
            model.save();
            dirty = false;
            lastSaveMs = now;
        } catch (Exception ignored) {
        }
    }

    private void ensureExecReplayLoaded() {
        NeuroAuraExec e = this.exec;
        if (e == null) return;
        if (execReplayLoaded) return;
        execReplayLoaded = true;
        try {
            if (!e.loaded) {
                e.model.load();
                e.loaded = true;
            }
        } catch (Exception ignored) {
        }
    }

    private boolean learnExecReplaySample(LivingEntity target,
                                          Box hb,
                                          Vec3d point,
                                          boolean attackSample,
                                          float dyDeg,
                                          float dpDeg,
                                          float stepYawAbs,
                                          float stepPitchAbs,
                                          float jitterYawAbs,
                                          float jitterPitchAbs,
                                          float lrPt,
                                          float lrBase,
                                          float amp) {
        NeuroAuraExec e = this.exec;
        if (e == null || target == null || point == null) return false;

        NeuroAuraExec.NeuroRotationModel replay = e.model;
        if (replay == null) return false;

        Box box = hb != null ? hb : target.getBoundingBox();
        if (box == null) return false;

        double sx = box.maxX - box.minX;
        double sy = box.maxY - box.minY;
        double sz = box.maxZ - box.minZ;
        if (sx <= 1.0E-9 || sy <= 1.0E-9 || sz <= 1.0E-9) return false;

        float px = (float) clampD((point.x - box.minX) / sx, 0.0, 1.0);
        float py = (float) clampD((point.y - box.minY) / sy, 0.0, 1.0);
        float pz = (float) clampD((point.z - box.minZ) / sz, 0.0, 1.0);

        float yawSp = MathHelper.clamp(stepYawAbs * 20.0f, 0.0f, 240.0f);
        float pitSp = MathHelper.clamp(stepPitchAbs * 20.0f, 0.0f, 240.0f);

        float yJ = MathHelper.clamp(jitterYawAbs, 0.0f, 48.0f);
        float pJ = MathHelper.clamp(jitterPitchAbs, 0.0f, 48.0f);

        float gY = model.gcdYawEma > 0.0001f ? MathHelper.clamp(model.gcdYawEma, EXEC_GCD_MIN, EXEC_GCD_MAX) : 0.0f;
        float gP = model.gcdPitchEma > 0.0001f ? MathHelper.clamp(model.gcdPitchEma, EXEC_GCD_MIN, EXEC_GCD_MAX) : 0.0f;

        float rateMul = lrBase <= 0.0f ? 1.0f : (lrPt / lrBase);
        float rateBase = attackSample ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        float rate = MathHelper.clamp(rateBase * rateMul, 0.01f, 0.40f);

        try {
            boolean ok = replay.learn(
                    target,
                    attackSample,
                    dyDeg,
                    dpDeg,
                    px,
                    py,
                    pz,
                    yawSp,
                    pitSp,
                    yJ,
                    pJ,
                    gY,
                    gP,
                    MathHelper.clamp(amp, 0.0f, 50.0f),
                    rate
            );
            if (ok) {
                e.markModelDirty();
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void notifyExecReplayUpdated(boolean force) {
        NeuroAuraExec e = this.exec;
        if (e == null) return;

        long now = System.currentTimeMillis();
        if (!force && (now - lastExecNotifyMs) < EXEC_NOTIFY_COOLDOWN_MS) {
            try {
                e.markModelDirty();
            } catch (Exception ignored) {
            }
            return;
        }

        lastExecNotifyMs = now;
        try {
            e.notifyModelUpdated();
        } catch (Exception ignored) {
        }
    }

    private static float ampFrom(float dy, float dp) {
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    private static boolean auraIsNeuroLearn(Aura a) {
        if (a == null) return false;

        try {
            Field fAim = auraAimModeField;
            if (fAim == null) {
                fAim = a.getClass().getDeclaredField("aimMode");
                fAim.setAccessible(true);
                auraAimModeField = fAim;
            }
            Object vAim = fAim.get(a);
            if (vAim instanceof SelectSetting sAim) {
                if (!sAim.isSelected("Neuro Aura")) return false;
            }
        } catch (Exception ignored) {
        }

        try {
            Field fN = auraNeuroModeField;
            if (fN == null) {
                fN = a.getClass().getDeclaredField("neuroMode");
                fN.setAccessible(true);
                auraNeuroModeField = fN;
            }
            Object v = fN.get(a);
            if (v instanceof SelectSetting s) {
                return s.isSelected("Запоминающий");
            }
        } catch (Exception ignored) {
        }

        return true;
    }

    private Vec3d pickLearnPoint(Box hb, Vec3d aimed, Vec3d computed, LivingEntity t, int seedBase, int patIdx, int pIdx, int pCount, boolean onAttack, float camVel) {
        if (pIdx == 0) return aimed != null ? aimed : computed;
        if (pIdx == 1 && computed != null) return computed;

        if (hb == null) return aimed != null ? aimed : computed;

        double cx = (hb.minX + hb.maxX) * 0.5;
        double cy = (hb.minY + hb.maxY) * 0.5;
        double cz = (hb.minZ + hb.maxZ) * 0.5;

        double sx = (hb.maxX - hb.minX) * 0.5;
        double sy = (hb.maxY - hb.minY) * 0.5;
        double sz = (hb.maxZ - hb.minZ) * 0.5;

        int s = seedBase ^ (patIdx * 0x9E3779B9) ^ (pIdx * 0x85EBCA6B);
        float r1 = rand01(s);
        float r2 = rand01(s ^ 0x68BC21EB);
        float r3 = rand01(s ^ 0x02E5BE93);

        float rel = relativeHeightBlocks(t);

        float biasUp = onAttack ? 0.10f : 0.0f;
        float biasMid = camVel > 2.0f ? 0.06f : 0.0f;

        float surfaceBias = 0.0f;
        if (rel <= -0.55f) {
            float k = clamp((-rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = 0.18f + 0.30f * k;
        } else if (rel >= 0.55f) {
            float k = clamp((rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = -(0.18f + 0.30f * k);
        }

        float velBiasX = 0.0f;
        float velBiasZ = 0.0f;
        if (t != null) {
            try {
                Vec3d v = t.getVelocity();
                double hl = Math.sqrt(v.x * v.x + v.z * v.z);
                if (hl > 1.0E-4) {
                    float k = (float) MathHelper.clamp(hl / 0.55, 0.0, 1.0);
                    velBiasX = (float) MathHelper.clamp(v.x / hl, -1.0, 1.0) * 0.20f * k;
                    velBiasZ = (float) MathHelper.clamp(v.z / hl, -1.0, 1.0) * 0.20f * k;
                }
            } catch (Exception ignored) {
            }
        }

        float oy = (r2 - 0.5f) * 0.90f + biasUp + biasMid + surfaceBias;
        float ox = (r1 - 0.5f) * 0.95f + velBiasX;
        float oz = (r3 - 0.5f) * 0.95f + velBiasZ;

        if (!onAttack && pIdx == pCount - 1) {
            if (rel >= 0.55f) {
                oy = -0.34f;
            } else if (rel <= -0.55f) {
                oy = 0.52f;
            } else {
                oy = 0.18f;
            }
            ox *= 0.65f;
            oz *= 0.65f;
        }

        double px = cx + ox * sx;
        double py = cy + oy * sy;
        double pz = cz + oz * sz;

        px = clampD(px, hb.minX + 1.0E-4, hb.maxX - 1.0E-4);
        py = clampD(py, hb.minY + 1.0E-4, hb.maxY - 1.0E-4);
        pz = clampD(pz, hb.minZ + 1.0E-4, hb.maxZ - 1.0E-4);

        return new Vec3d(px, py, pz);
    }

    private float relativeHeightBlocks(LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || t == null) return 0.0f;
        try {
            double dy = t.getEyeY() - mc.player.getEyeY();
            return (float) dy;
        } catch (Exception ignored) {
        }
        return 0.0f;
    }

    private Turns turnsFromYawPitch(float yaw, float pitch) {
        try {
            Vec3d v = model.vecFromYawPitch(yaw, pitch);
            return MathAngle.fromVec3d(v);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Turns turnsDirect(float yaw, float pitch) {
        try {
            if (!turnsCtorTried) {
                turnsCtorTried = true;
                try {
                    Constructor<?> c = Turns.class.getDeclaredConstructor(float.class, float.class);
                    c.setAccessible(true);
                    turnsCtorFF = c;
                } catch (Exception ignored) {
                }
                try {
                    Constructor<?> c = Turns.class.getDeclaredConstructor(double.class, double.class);
                    c.setAccessible(true);
                    turnsCtorDD = c;
                } catch (Exception ignored) {
                }
            }

            if (turnsCtorFF != null) {
                Object o = turnsCtorFF.newInstance(yaw, pitch);
                if (o instanceof Turns t) return t;
            }
            if (turnsCtorDD != null) {
                Object o = turnsCtorDD.newInstance((double) yaw, (double) pitch);
                if (o instanceof Turns t) return t;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Vec3d neuroAimPointFromView(Box hb, Vec3d fallback, LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return fallback;
        if (hb == null) return fallback;

        Vec3d eye = mc.player.getEyePos();

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        Vec3d dir;
        try {
            dir = model.vecFromYawPitch(yaw, pitch);
        } catch (Exception e) {
            dir = null;
        }
        if (dir == null) return fallback;

        double dist = 7.0;
        if (t != null) {
            try {
                dist = Math.max(3.0, mc.player.distanceTo(t) + 3.5);
            } catch (Exception ignored) {
            }
        }

        Vec3d end = eye.add(dir.multiply(dist));
        Vec3d hit = boxRaycast(hb, eye, end);
        if (hit != null) return hit;

        if (fallback != null) return fallback;

        return new Vec3d((hb.minX + hb.maxX) * 0.5, (hb.minY + hb.maxY) * 0.6, (hb.minZ + hb.maxZ) * 0.5);
    }

    private static Vec3d boxRaycast(Box box, Vec3d start, Vec3d end) {
        if (box == null || start == null || end == null) return null;
        try {
            Method m = Box.class.getMethod("raycast", Vec3d.class, Vec3d.class);
            Object r = m.invoke(box, start, end);
            if (r instanceof java.util.Optional<?> opt) {
                Object v = opt.orElse(null);
                if (v instanceof Vec3d p) return p;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static float turnsYaw(Turns t, float def) {
        if (t == null) return def;
        try {
            Method m = turnsGetYaw;
            if (m == null) {
                try {
                    m = Turns.class.getMethod("getYaw");
                } catch (Exception ignored) {
                }
                if (m == null) {
                    try {
                        m = Turns.class.getMethod("yaw");
                    } catch (Exception ignored) {
                    }
                }
                turnsGetYaw = m;
            }
            if (m != null) {
                Object v = m.invoke(t);
                if (v instanceof Number n) return n.floatValue();
            }
        } catch (Exception ignored) {
        }
        try {
            Field f = turnsYawField;
            if (f == null) {
                try {
                    f = Turns.class.getDeclaredField("yaw");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                turnsYawField = f;
            }
            if (f != null) {
                Object v = f.get(t);
                if (v instanceof Number n) return n.floatValue();
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    private static float turnsPitch(Turns t, float def) {
        if (t == null) return def;
        try {
            Method m = turnsGetPitch;
            if (m == null) {
                try {
                    m = Turns.class.getMethod("getPitch");
                } catch (Exception ignored) {
                }
                if (m == null) {
                    try {
                        m = Turns.class.getMethod("pitch");
                    } catch (Exception ignored) {
                    }
                }
                turnsGetPitch = m;
            }
            if (m != null) {
                Object v = m.invoke(t);
                if (v instanceof Number n) return n.floatValue();
            }
        } catch (Exception ignored) {
        }
        try {
            Field f = turnsPitchField;
            if (f == null) {
                try {
                    f = Turns.class.getDeclaredField("pitch");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                turnsPitchField = f;
            }
            if (f != null) {
                Object v = f.get(t);
                if (v instanceof Number n) return n.floatValue();
            }
        } catch (Exception ignored) {
        }
        return def;
    }

    static int neuroSwingTicks(Object player) {
        if (player == null) return 0;

        try {
            Field f = swingTicksFieldA;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("handSwingTicks");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldA = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Field f = swingTicksFieldB;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("handSwingingTicks");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldB = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Method m = swingTicksMethod;
            if (m == null) {
                try {
                    m = player.getClass().getMethod("getHandSwingTicks");
                } catch (Exception ignored) {
                }
                if (m != null) m.setAccessible(true);
                swingTicksMethod = m;
            }
            if (m != null) {
                Object r = m.invoke(player);
                if (r instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        try {
            Field f = swingTicksFieldC;
            if (f == null) {
                try {
                    f = player.getClass().getDeclaredField("ticksSinceLastSwing");
                    f.setAccessible(true);
                } catch (Exception ignored) {
                }
                swingTicksFieldC = f;
            }
            if (f != null && f.getType() == int.class) return f.getInt(player);
            if (f != null) {
                Object v = f.get(player);
                if (v instanceof Integer i) return i;
            }
        } catch (Exception ignored) {
        }

        return 0;
    }

    static boolean neuroSwingStarted(int prev, int cur) {
        if (cur < prev) return true;
        if (prev == 0 && cur > 0) return true;
        if (prev > 0 && cur == 0) return true;
        return false;
    }

    private static float wrapDeg(float v) {
        v %= 360.0f;
        if (v >= 180.0f) v -= 360.0f;
        if (v < -180.0f) v += 360.0f;
        return v;
    }

    private static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int mixSeed(int a, int b) {
        int x = a * 0x9E3779B9;
        x ^= b * 0x85EBCA6B;
        x ^= (x >>> 16);
        x *= 0xC2B2AE35;
        x ^= (x >>> 13);
        x *= 0x27D4EB2F;
        x ^= (x >>> 16);
        return x;
    }

    private static float rand01(int x) {
        x ^= (x >>> 16);
        x *= 0x7FEB352D;
        x ^= (x >>> 15);
        x *= 0x846CA68B;
        x ^= (x >>> 16);
        int m = x & 0x00FFFFFF;
        return m / 16777215.0f;
    }

    private static int corrIndex(SelectSetting s) {
        if (s == null) return 0;
        if (s.isSelected("Focused")) return 1;
        if (s.isSelected("Focus V2")) return 2;
        if (s.isSelected("Not visible")) return 3;
        if (s.isSelected("Neuro")) return 0;
        return 0;
    }

    public static final class NeuroRotationModel {
        private static final int INPUT_SIZE = 10;
        private static final int HIDDEN1 = 32;
        private static final int HIDDEN2 = 32;
        private static final int OUTPUT_SIZE = 3;

        private static final int REPLAY_CAP = 24576;
        private static final int REPLAY_MAGIC = 0x52425031;

        private final float[] w1 = new float[INPUT_SIZE * HIDDEN1];
        private final float[] b1 = new float[HIDDEN1];
        private final float[] w2 = new float[HIDDEN1 * HIDDEN2];
        private final float[] b2 = new float[HIDDEN2];
        private final float[] w3 = new float[HIDDEN2 * OUTPUT_SIZE];
        private final float[] b3 = new float[OUTPUT_SIZE];

        private final float[] h1 = new float[HIDDEN1];
        private final float[] h2 = new float[HIDDEN2];
        private final float[] out = new float[OUTPUT_SIZE];
        private final float[] dOut = new float[OUTPUT_SIZE];
        private final float[] dH2 = new float[HIDDEN2];
        private final float[] dH1 = new float[HIDDEN1];

        private final float[] bufInput = new float[INPUT_SIZE];
        private final float[] bufTarget = new float[OUTPUT_SIZE];

        private final float[] replayX = new float[REPLAY_CAP * INPUT_SIZE];
        private final float[] replayY = new float[REPLAY_CAP * OUTPUT_SIZE];
        private final float[] replayLr = new float[REPLAY_CAP];
        private final int[] replayMeta = new int[REPLAY_CAP];

        private int replaySize;
        private int replayWrite;
        private long replaySeen;
        private int replayLastHash;
        private int replayTrainCounter;

        private float yawSpeedEma;
        private float pitchSpeedEma;
        private float yawJitterEma;
        private float pitchJitterEma;
        private float ampEma;
        private float posErrEma;
        private float camVelEma;
        private float gcdYawEma;
        private float gcdPitchEma;
        private float samples;

        public static final class HumanizerProfile {
            final float noiseYaw;
            final float noisePitch;
            final float freqYaw;
            final float freqPitch;
            final float gate;
            final float gcdHintYaw;
            final float gcdHintPitch;
            final float confidence;

            HumanizerProfile(float noiseYaw, float noisePitch, float freqYaw, float freqPitch, float gate, float gcdHintYaw, float gcdHintPitch, float confidence) {
                this.noiseYaw = noiseYaw;
                this.noisePitch = noisePitch;
                this.freqYaw = freqYaw;
                this.freqPitch = freqPitch;
                this.gate = gate;
                this.gcdHintYaw = gcdHintYaw;
                this.gcdHintPitch = gcdHintPitch;
                this.confidence = confidence;
            }

            public float noiseYaw() {
                return noiseYaw;
            }

            public float noisePitch() {
                return noisePitch;
            }

            public float freqYaw() {
                return freqYaw;
            }

            public float freqPitch() {
                return freqPitch;
            }

            public float gate() {
                return gate;
            }

            public float gcdHintYaw() {
                return gcdHintYaw;
            }

            public float gcdHintPitch() {
                return gcdHintPitch;
            }

            public float confidence() {
                return confidence;
            }
        }

        NeuroRotationModel() {
            initRandomWeights();
        }

        private void initRandomWeights() {
            java.util.Random rnd = new java.util.Random(0xC0FFEE);
            float scale1 = (float) (1.0 / Math.sqrt(INPUT_SIZE));
            for (int i = 0; i < w1.length; i++) {
                w1[i] = (float) (rnd.nextGaussian() * scale1);
            }
            for (int i = 0; i < b1.length; i++) {
                b1[i] = 0.0f;
            }

            float scale2 = (float) (1.0 / Math.sqrt(HIDDEN1));
            for (int i = 0; i < w2.length; i++) {
                w2[i] = (float) (rnd.nextGaussian() * scale2);
            }
            for (int i = 0; i < b2.length; i++) {
                b2[i] = 0.0f;
            }

            float scale3 = (float) (1.0 / Math.sqrt(HIDDEN2));
            for (int i = 0; i < w3.length; i++) {
                w3[i] = (float) (rnd.nextGaussian() * scale3);
            }
            for (int i = 0; i < b3.length; i++) {
                b3[i] = 0.0f;
            }

            yawSpeedEma = 0.0f;
            pitchSpeedEma = 0.0f;
            yawJitterEma = 0.0f;
            pitchJitterEma = 0.0f;
            ampEma = 0.0f;
            posErrEma = 0.0f;
            camVelEma = 0.0f;
            gcdYawEma = 0.0f;
            gcdPitchEma = 0.0f;
            samples = 0.0f;

            replaySize = 0;
            replayWrite = 0;
            replaySeen = 0L;
            replayLastHash = 0;
            replayTrainCounter = 0;
        }

        HumanizerProfile suggestHumanizer(LivingEntity t, boolean attackSample, float camVel) {
            float replayFactor = MathHelper.clamp(replaySize / 4000.0f, 0.0f, 1.0f);
            float sampleFactor = MathHelper.clamp((samples / 600.0f) * 0.78f + replayFactor * 0.45f, 0.0f, 1.0f);
            float posErr = MathHelper.clamp(posErrEma, 0.0f, 1.5f);
            float confE = (float) Math.exp(-posErr * 1.25f);
            float confidence = MathHelper.clamp(sampleFactor * confE, 0.0f, 1.0f);

            float speedN = MathHelper.clamp(((Math.abs(yawSpeedEma) + Math.abs(pitchSpeedEma)) * 0.5f) / 18.0f, 0.0f, 1.0f);
            float jitterN = MathHelper.clamp(((Math.abs(yawJitterEma) + Math.abs(pitchJitterEma)) * 0.5f) / 10.0f, 0.0f, 1.0f);
            float ampN = MathHelper.clamp(ampEma / 40.0f, 0.0f, 1.0f);
            float camN = MathHelper.clamp(camVel / 6.0f, 0.0f, 1.0f);

            float stable = confidence;
            float low = 1.0f - stable;

            float noiseYaw = 0.10f + stable * 0.30f + jitterN * 0.20f + ampN * 0.12f;
            float noisePitch = 0.08f + stable * 0.24f + jitterN * 0.16f + ampN * 0.10f;

            float speedMul = 1.0f - speedN * 0.30f;
            float camMul = 1.0f - camN * 0.55f;

            noiseYaw *= speedMul * camMul;
            noisePitch *= speedMul * camMul;

            if (attackSample) {
                noiseYaw *= 0.85f;
                noisePitch *= 0.80f;
            } else {
                noiseYaw *= 0.95f + low * 0.05f;
                noisePitch *= 0.95f + low * 0.05f;
            }

            noiseYaw = MathHelper.clamp(noiseYaw, 0.04f, 1.25f);
            noisePitch = MathHelper.clamp(noisePitch, 0.03f, 1.05f);

            float freqBase = 0.55f + speedN * 0.75f + jitterN * 0.40f + stable * 0.25f;
            if (attackSample) freqBase *= 1.05f;

            float freqYaw = MathHelper.clamp(freqBase, 0.45f, 2.40f);
            float freqPitch = MathHelper.clamp(freqBase * 0.92f, 0.40f, 2.20f);

            float gate = (0.30f + 0.70f * stable) * (1.0f - camN * 0.55f);
            if (attackSample) gate *= 0.86f;
            gate = MathHelper.clamp(gate, 0.10f, 1.0f);

            float gcdHintYaw = gcdYawEma > 0.0005f ? MathHelper.clamp(gcdYawEma, 0.0005f, 3.5f) : 0.0f;
            float gcdHintPitch = gcdPitchEma > 0.0005f ? MathHelper.clamp(gcdPitchEma, 0.0005f, 3.5f) : 0.0f;

            return new HumanizerProfile(noiseYaw, noisePitch, freqYaw, freqPitch, gate, gcdHintYaw, gcdHintPitch, confidence);
        }

        void learnCorrection(LivingEntity t, boolean attackSample, float baseLr, int corrType) {
            float dir;
            if (corrType == 1) {
                dir = -0.15f;
            } else if (corrType == 2) {
                dir = -0.30f;
            } else if (corrType == 3) {
                dir = 0.25f;
            } else {
                dir = 0.0f;
            }
            if (dir == 0.0f) return;
            float k = MathHelper.clamp(baseLr * 0.5f, 0.01f, 0.25f);
            posErrEma = updateEma(posErrEma, posErrEma + dir * 0.25f, k);
        }

        void learn(LivingEntity t, Turns baseAngle, Turns playerAngle, Box hb, Vec3d aimedPoint, float baseLr, boolean attackSample, float camVel, float stepYaw, float stepPitch, float jitterYaw, float jitterPitch) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || t == null) return;

            Vec3d tv = t.getVelocity();
            float sp = (float) tv.horizontalLength();
            float dist = mc.player.distanceTo(t);

            float relHeight;
            try {
                relHeight = (float) (t.getEyeY() - mc.player.getEyeY());
            } catch (Exception e) {
                relHeight = 0.0f;
            }

            float baseYaw = NeuroAuraLearn.turnsYaw(baseAngle, mc.player.getYaw());
            float basePitch = NeuroAuraLearn.turnsPitch(baseAngle, mc.player.getPitch());

            float plyYaw = NeuroAuraLearn.turnsYaw(playerAngle, baseYaw);
            float plyPitch = NeuroAuraLearn.turnsPitch(playerAngle, basePitch);

            float dyRaw = MathHelper.wrapDegrees(plyYaw - baseYaw);
            float dpRaw = MathHelper.clamp(plyPitch - basePitch, -90.0f, 90.0f);

            float dyClamp = MathHelper.clamp(dyRaw, -70.0f, 70.0f);
            float dpClamp = MathHelper.clamp(dpRaw, -50.0f, 50.0f);

            float dyNorm = dyClamp / 70.0f;
            float dpNorm = dpClamp / 50.0f;

            float heightNorm;
            if (hb != null && aimedPoint != null) {
                double hy = hb.maxY - hb.minY;
                if (hy > 1.0E-6) {
                    heightNorm = (float) ((aimedPoint.y - hb.minY) / hy);
                    heightNorm = MathHelper.clamp(heightNorm, 0.0f, 1.0f);
                } else {
                    heightNorm = 0.6f;
                }
            } else {
                heightNorm = 0.6f;
            }
            float heightScaled = heightNorm * 2.0f - 1.0f;

            float stepYawAbs = Math.abs(stepYaw);
            float stepPitchAbs = Math.abs(stepPitch);
            float jitterYawAbs = Math.abs(jitterYaw);
            float jitterPitchAbs = Math.abs(jitterPitch);
            float amp = (float) Math.sqrt(dyClamp * dyClamp + dpClamp * dpClamp);

            yawSpeedEma = updateEma(yawSpeedEma, stepYawAbs, 0.05f);
            pitchSpeedEma = updateEma(pitchSpeedEma, stepPitchAbs, 0.05f);
            yawJitterEma = updateEma(yawJitterEma, jitterYawAbs, 0.05f);
            pitchJitterEma = updateEma(pitchJitterEma, jitterPitchAbs, 0.05f);
            ampEma = updateEma(ampEma, amp, 0.05f);

            if (hb != null && aimedPoint != null) {
                double hx = hb.maxX - hb.minX;
                double hy = hb.maxY - hb.minY;
                double hz = hb.maxZ - hb.minZ;
                if (hx > 1.0E-6 && hy > 1.0E-6 && hz > 1.0E-6) {
                    float pxN = (float) ((aimedPoint.x - hb.minX) / hx);
                    float pyN = (float) ((aimedPoint.y - hb.minY) / hy);
                    float pzN = (float) ((aimedPoint.z - hb.minZ) / hz);
                    pxN = MathHelper.clamp(pxN, 0.0f, 1.0f);
                    pyN = MathHelper.clamp(pyN, 0.0f, 1.0f);
                    pzN = MathHelper.clamp(pzN, 0.0f, 1.0f);
                    float dx = pxN - 0.5f;
                    float dyPos = pyN - 0.65f;
                    float dz = pzN - 0.5f;
                    float posErr = (float) Math.sqrt(dx * dx + dyPos * dyPos + dz * dz);
                    posErrEma = updateEma(posErrEma, posErr, 0.05f);
                }
            }

            camVelEma = updateEma(camVelEma, camVel, 0.05f);
            if (stepYawAbs > 0.001f) gcdYawEma = updateGcd(gcdYawEma, stepYawAbs, 0.03f);
            if (stepPitchAbs > 0.001f) gcdPitchEma = updateGcd(gcdPitchEma, stepPitchAbs, 0.03f);
            if (samples < 1_000_000f) samples += 1.0f;

            float[] x = bufInput;
            x[0] = MathHelper.clamp(dist / 8.0f, 0.0f, 1.0f);
            x[1] = MathHelper.clamp(relHeight / 4.0f, -1.0f, 1.0f);
            x[2] = MathHelper.clamp(sp / 1.4f, 0.0f, 1.0f);
            x[3] = MathHelper.clamp(camVel / 8.0f, 0.0f, 1.0f);
            x[4] = MathHelper.clamp(stepYaw / 45.0f, -1.0f, 1.0f);
            x[5] = MathHelper.clamp(stepPitch / 45.0f, -1.0f, 1.0f);
            x[6] = MathHelper.clamp((jitterYawAbs + jitterPitchAbs) * 0.5f / 25.0f, 0.0f, 1.0f);
            boolean airborne = mc.player.isGliding() || t.isGliding() || !mc.player.isOnGround();
            x[7] = airborne ? 1.0f : 0.0f;
            x[8] = attackSample ? 1.0f : 0.0f;
            int sideBin = sideBin(tv, mc.player.getYaw());
            float sideVal;
            if (sideBin == 0) sideVal = -1.0f;
            else if (sideBin == 2) sideVal = 1.0f;
            else sideVal = 0.0f;
            x[9] = sideVal;

            float[] y = bufTarget;
            y[0] = dyNorm;
            y[1] = dpNorm;
            y[2] = heightScaled;

            float lr = MathHelper.clamp(baseLr * 0.65f, 0.0025f, 0.05f);
            if (attackSample) lr = MathHelper.clamp(lr * 1.5f, 0.0035f, 0.08f);

            trainSample(x, y, lr);

            float quality = qualityScore(attackSample, camVel, dyClamp, dpClamp, jitterYawAbs, jitterPitchAbs);
            pushReplaySample(x, y, lr, attackSample, quality);
            trainReplayMiniBatch(attackSample);
        }

        private float qualityScore(boolean attackSample, float camVel, float dy, float dp, float jy, float jp) {
            float amp = (float) Math.sqrt(dy * dy + dp * dp);
            float ampN = MathHelper.clamp(amp / 60.0f, 0.0f, 1.0f);
            float camN = MathHelper.clamp(camVel / 7.0f, 0.0f, 1.0f);
            float jitN = MathHelper.clamp((jy + jp) * 0.5f / 18.0f, 0.0f, 1.0f);
            float q = 0.35f + ampN * 0.35f + camN * 0.22f + jitN * 0.18f;
            if (attackSample) q += 0.12f;
            return MathHelper.clamp(q, 0.05f, 1.50f);
        }

        private void pushReplaySample(float[] input, float[] target, float lr, boolean attackSample, float quality) {
            if (input == null || target == null) return;

            int hash = replayHash(input, target, attackSample);
            if (!attackSample && replaySize > 0 && hash == replayLastHash) {
                if (java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.82f) return;
            }
            replayLastHash = hash;

            int idx;
            if (replaySize < REPLAY_CAP) {
                idx = replaySize;
                replaySize++;
            } else {
                idx = replayWrite;
            }
            replayWrite = (idx + 1) % REPLAY_CAP;
            replaySeen++;

            int xOff = idx * INPUT_SIZE;
            for (int i = 0; i < INPUT_SIZE; i++) replayX[xOff + i] = input[i];

            int yOff = idx * OUTPUT_SIZE;
            for (int i = 0; i < OUTPUT_SIZE; i++) replayY[yOff + i] = target[i];

            replayLr[idx] = MathHelper.clamp(lr, 0.0015f, 0.12f);

            int q = MathHelper.clamp((int) (MathHelper.clamp(quality, 0.0f, 2.0f) * 120.0f), 0, 255);
            int attack = attackSample ? 1 : 0;
            replayMeta[idx] = attack | (q << 8);
        }

        private void trainReplayMiniBatch(boolean attackContext) {
            if (replaySize < 32) return;

            replayTrainCounter++;
            int mod = attackContext ? 1 : 2;
            if ((replayTrainCounter % mod) != 0) return;

            int steps = attackContext ? 4 : 2;
            if (replaySize > 1024) steps++;
            if (replaySize > 4096 && attackContext) steps++;

            for (int i = 0; i < steps; i++) {
                int idx = sampleReplayIndex(attackContext);
                if (idx < 0) return;

                int meta = replayMeta[idx];
                boolean attackSample = (meta & 1) != 0;
                int q = (meta >>> 8) & 255;
                float qMul = 0.55f + (q / 255.0f) * 0.85f;

                float base = replayLr[idx];
                float lr = base * (attackContext ? 0.34f : 0.26f) * qMul;
                if (attackContext && !attackSample) lr *= 0.72f;
                if (!attackContext && attackSample) lr *= 0.88f;
                lr = MathHelper.clamp(lr, 0.0012f, attackContext ? 0.030f : 0.020f);

                int xOff = idx * INPUT_SIZE;
                int yOff = idx * OUTPUT_SIZE;

                for (int j = 0; j < INPUT_SIZE; j++) bufInput[j] = replayX[xOff + j];
                for (int j = 0; j < OUTPUT_SIZE; j++) bufTarget[j] = replayY[yOff + j];

                if (attackContext) {
                    bufInput[8] = 1.0f;
                }

                trainSample(bufInput, bufTarget, lr);
            }
        }

        private int sampleReplayIndex(boolean preferAttack) {
            if (replaySize <= 0) return -1;

            for (int attempt = 0; attempt < 10; attempt++) {
                boolean recentBias = java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.72f;
                int idx;

                if (recentBias) {
                    int span = Math.min(replaySize, 4096);
                    float u = java.util.concurrent.ThreadLocalRandom.current().nextFloat();
                    int back = (int) (u * u * span);
                    idx = latestMinus(back);
                } else {
                    idx = randomExistingIndex();
                }

                if (idx < 0) continue;

                if (preferAttack) {
                    boolean attack = (replayMeta[idx] & 1) != 0;
                    if (attack) return idx;
                    if (java.util.concurrent.ThreadLocalRandom.current().nextFloat() < 0.35f) return idx;
                    continue;
                }

                return idx;
            }

            return randomExistingIndex();
        }

        private int randomExistingIndex() {
            if (replaySize <= 0) return -1;
            if (replaySize < REPLAY_CAP) {
                return java.util.concurrent.ThreadLocalRandom.current().nextInt(replaySize);
            }
            return java.util.concurrent.ThreadLocalRandom.current().nextInt(REPLAY_CAP);
        }

        private int latestMinus(int back) {
            if (replaySize <= 0) return -1;
            int capUsed = replaySize < REPLAY_CAP ? replaySize : REPLAY_CAP;
            if (capUsed <= 0) return -1;
            if (back < 0) back = 0;
            if (back >= capUsed) back = capUsed - 1;

            int latest = replaySize < REPLAY_CAP ? (replaySize - 1) : ((replayWrite - 1 + REPLAY_CAP) % REPLAY_CAP);
            return (latest - back + REPLAY_CAP) % REPLAY_CAP;
        }

        private static int replayHash(float[] input, float[] target, boolean attack) {
            int h = attack ? 0x13579BDF : 0x2468ACE1;

            int q0 = (int) ((MathHelper.clamp(input[0], 0.0f, 1.0f) * 31.0f) + 0.5f);
            int q1 = (int) (((MathHelper.clamp(input[1], -1.0f, 1.0f) + 1.0f) * 15.0f) + 0.5f);
            int q2 = (int) ((MathHelper.clamp(input[2], 0.0f, 1.0f) * 31.0f) + 0.5f);
            int q3 = (int) ((MathHelper.clamp(input[3], 0.0f, 1.0f) * 31.0f) + 0.5f);
            int q4 = (int) (((MathHelper.clamp(target[0], -1.0f, 1.0f) + 1.0f) * 31.0f) + 0.5f);
            int q5 = (int) (((MathHelper.clamp(target[1], -1.0f, 1.0f) + 1.0f) * 31.0f) + 0.5f);
            int q6 = (int) (((MathHelper.clamp(target[2], -1.0f, 1.0f) + 1.0f) * 31.0f) + 0.5f);

            h = mixHash(h, q0);
            h = mixHash(h, q1);
            h = mixHash(h, q2);
            h = mixHash(h, q3);
            h = mixHash(h, q4);
            h = mixHash(h, q5);
            h = mixHash(h, q6);

            return h;
        }

        private static int mixHash(int h, int v) {
            h ^= v + 0x9E3779B9 + (h << 6) + (h >>> 2);
            return h;
        }

        private int logicalReplayIndex(int logicalOldestFirst) {
            if (replaySize <= 0) return -1;
            if (logicalOldestFirst < 0) logicalOldestFirst = 0;
            if (logicalOldestFirst >= replaySize) logicalOldestFirst = replaySize - 1;

            if (replaySize < REPLAY_CAP) return logicalOldestFirst;
            return (replayWrite + logicalOldestFirst) % REPLAY_CAP;
        }

        private void trainSample(float[] input, float[] target, float lr) {
            float[] y = forward(input);

            for (int k = 0; k < OUTPUT_SIZE; k++) {
                float err = y[k] - target[k];
                float gradOut = err * (1.0f - y[k] * y[k]);
                dOut[k] = gradOut;
            }

            for (int j = 0; j < HIDDEN2; j++) {
                float sum = 0.0f;
                int base = j * OUTPUT_SIZE;
                for (int k = 0; k < OUTPUT_SIZE; k++) {
                    sum += dOut[k] * w3[base + k];
                }
                dH2[j] = (1.0f - h2[j] * h2[j]) * sum;
            }

            for (int i = 0; i < HIDDEN1; i++) {
                float sum = 0.0f;
                int base = i * HIDDEN2;
                for (int j = 0; j < HIDDEN2; j++) {
                    sum += dH2[j] * w2[base + j];
                }
                dH1[i] = (1.0f - h1[i] * h1[i]) * sum;
            }

            for (int j = 0; j < HIDDEN2; j++) {
                int base = j * OUTPUT_SIZE;
                for (int k = 0; k < OUTPUT_SIZE; k++) {
                    int idx = base + k;
                    w3[idx] -= lr * dOut[k] * h2[j];
                }
            }
            for (int k = 0; k < OUTPUT_SIZE; k++) {
                b3[k] -= lr * dOut[k];
            }

            for (int i = 0; i < HIDDEN1; i++) {
                int base = i * HIDDEN2;
                for (int j = 0; j < HIDDEN2; j++) {
                    int idx = base + j;
                    w2[idx] -= lr * dH2[j] * h1[i];
                }
            }
            for (int j = 0; j < HIDDEN2; j++) {
                b2[j] -= lr * dH2[j];
            }

            for (int i = 0; i < INPUT_SIZE; i++) {
                int base = i * HIDDEN1;
                for (int j = 0; j < HIDDEN1; j++) {
                    int idx = base + j;
                    w1[idx] -= lr * dH1[j] * input[i];
                }
            }
            for (int j = 0; j < HIDDEN1; j++) {
                b1[j] -= lr * dH1[j];
            }
        }

        private float[] forward(float[] input) {
            for (int j = 0; j < HIDDEN1; j++) {
                float sum = b1[j];
                for (int i = 0; i < INPUT_SIZE; i++) {
                    sum += input[i] * w1[i * HIDDEN1 + j];
                }
                h1[j] = tanh(sum);
            }

            for (int j = 0; j < HIDDEN2; j++) {
                float sum = b2[j];
                for (int i = 0; i < HIDDEN1; i++) {
                    sum += h1[i] * w2[i * HIDDEN2 + j];
                }
                h2[j] = tanh(sum);
            }

            for (int k = 0; k < OUTPUT_SIZE; k++) {
                float sum = b3[k];
                for (int j = 0; j < HIDDEN2; j++) {
                    sum += h2[j] * w3[j * OUTPUT_SIZE + k];
                }
                out[k] = tanh(sum);
            }

            return out;
        }

        private static float tanh(float x) {
            return (float) Math.tanh(x);
        }

        private static float updateEma(float cur, float value, float alpha) {
            if (cur == 0.0f) return value;
            return cur + (value - cur) * alpha;
        }

        private static float updateGcd(float cur, float step, float alpha) {
            step = MathHelper.clamp(step, 0.0005f, 90.0f);
            if (cur <= 0.0001f) return step;
            float ratio = step / Math.max(cur, 0.0005f);
            float nearest = Math.max(1.0f, Math.round(ratio));
            float cand = step / Math.max(nearest, 1.0f);
            return cur + (cand - cur) * alpha;
        }

        private int sideBin(Vec3d tv, float playerYawDeg) {
            if (tv == null) return 1;
            double vx = tv.x;
            double vz = tv.z;
            double sp = Math.sqrt(vx * vx + vz * vz);
            if (sp < 0.02) return 1;

            double yaw = Math.toRadians(playerYawDeg);
            double rx = Math.cos(yaw);
            double rz = Math.sin(yaw);

            double s = vx * rx + vz * rz;

            if (s > 0.03) return 2;
            if (s < -0.03) return 0;
            return 1;
        }

        private java.nio.file.Path filePath(MinecraftClient mc) {
            java.io.File dir = new java.io.File(mc.runDirectory, "rich");
            if (!dir.exists()) dir.mkdirs();
            return new java.io.File(dir, "neuro_aura_nn.json").toPath();
        }

        void save() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            try {
                int extraFloats = 10;
                int fixedFloats = w1.length + b1.length + w2.length + b2.length + w3.length + b3.length + extraFloats;

                int replayEntries = replaySize;
                int replayEntryBytes = (INPUT_SIZE + OUTPUT_SIZE + 1) * 4 + 4;
                int replayHeaderBytes = 4 + 4 + 4 + 4 + 4 + 8 + 4;

                int bytes = 4 * 4 + fixedFloats * 4 + replayHeaderBytes + replayEntries * replayEntryBytes;

                ByteBuffer bb = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
                bb.putInt(INPUT_SIZE);
                bb.putInt(HIDDEN1);
                bb.putInt(HIDDEN2);
                bb.putInt(OUTPUT_SIZE);

                for (float v : w1) bb.putFloat(v);
                for (float v : b1) bb.putFloat(v);
                for (float v : w2) bb.putFloat(v);
                for (float v : b2) bb.putFloat(v);
                for (float v : w3) bb.putFloat(v);
                for (float v : b3) bb.putFloat(v);

                bb.putFloat(yawSpeedEma);
                bb.putFloat(pitchSpeedEma);
                bb.putFloat(yawJitterEma);
                bb.putFloat(pitchJitterEma);
                bb.putFloat(ampEma);
                bb.putFloat(posErrEma);
                bb.putFloat(camVelEma);
                bb.putFloat(gcdYawEma);
                bb.putFloat(gcdPitchEma);
                bb.putFloat(samples);

                bb.putInt(REPLAY_MAGIC);
                bb.putInt(INPUT_SIZE);
                bb.putInt(OUTPUT_SIZE);
                bb.putInt(replayEntries);
                bb.putInt(REPLAY_CAP);
                bb.putLong(replaySeen);
                bb.putInt(replayLastHash);

                for (int i = 0; i < replayEntries; i++) {
                    int src = logicalReplayIndex(i);
                    int xOff = src * INPUT_SIZE;
                    int yOff = src * OUTPUT_SIZE;

                    for (int k = 0; k < INPUT_SIZE; k++) bb.putFloat(replayX[xOff + k]);
                    for (int k = 0; k < OUTPUT_SIZE; k++) bb.putFloat(replayY[yOff + k]);
                    bb.putFloat(replayLr[src]);
                    bb.putInt(replayMeta[src]);
                }

                String b64 = Base64.getEncoder().encodeToString(bb.array());
                String json = "{\"v\":12,\"b64\":\"" + b64 + "\"}";

                Files.writeString(
                        filePath(mc),
                        json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            } catch (Exception ignored) {
            }
        }

        void load() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;
            try {
                var p = filePath(mc);
                if (!Files.exists(p)) return;

                String json = Files.readString(p, StandardCharsets.UTF_8);
                int ver = findJsonIntValue(json, "v", 11);

                int b0 = json.indexOf("\"b64\":\"");
                if (b0 < 0) return;
                int s = b0 + 7;
                int e = json.indexOf('"', s);
                if (e <= s) return;
                String b64 = json.substring(s, e);
                byte[] data = Base64.getDecoder().decode(b64);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

                int in = bb.getInt();
                int h1 = bb.getInt();
                int h2 = bb.getInt();
                int outSize = bb.getInt();

                if (in != INPUT_SIZE || h1 != HIDDEN1 || h2 != HIDDEN2 || outSize != OUTPUT_SIZE) {
                    return;
                }

                int minFixedBytes = 4 * 4 + (w1.length + b1.length + w2.length + b2.length + w3.length + b3.length + 10) * 4;
                if (data.length < minFixedBytes) {
                    return;
                }

                for (int i = 0; i < w1.length; i++) w1[i] = bb.getFloat();
                for (int i = 0; i < b1.length; i++) b1[i] = bb.getFloat();
                for (int i = 0; i < w2.length; i++) w2[i] = bb.getFloat();
                for (int i = 0; i < b2.length; i++) b2[i] = bb.getFloat();
                for (int i = 0; i < w3.length; i++) w3[i] = bb.getFloat();
                for (int i = 0; i < b3.length; i++) b3[i] = bb.getFloat();

                yawSpeedEma = bb.getFloat();
                pitchSpeedEma = bb.getFloat();
                yawJitterEma = bb.getFloat();
                pitchJitterEma = bb.getFloat();
                ampEma = bb.getFloat();
                posErrEma = bb.getFloat();
                camVelEma = bb.getFloat();
                gcdYawEma = bb.getFloat();
                gcdPitchEma = bb.getFloat();
                samples = bb.getFloat();

                clearReplay();

                if (ver >= 12 && bb.remaining() >= (4 + 4 + 4 + 4 + 4 + 8 + 4)) {
                    int magic = bb.getInt();
                    int inSize = bb.getInt();
                    int outSz = bb.getInt();
                    int rSize = bb.getInt();
                    int rCapStored = bb.getInt();
                    long rSeen = bb.getLong();
                    int rLastHash = bb.getInt();

                    if (magic == REPLAY_MAGIC && inSize == INPUT_SIZE && outSz == OUTPUT_SIZE && rSize > 0 && rCapStored > 0) {
                        int maxReadable = bb.remaining() / (((INPUT_SIZE + OUTPUT_SIZE + 1) * 4) + 4);
                        int toRead = Math.min(Math.max(rSize, 0), maxReadable);
                        int keep = Math.min(toRead, REPLAY_CAP);

                        int skipOld = Math.max(0, toRead - keep);
                        int entryFloats = INPUT_SIZE + OUTPUT_SIZE + 1;

                        for (int i = 0; i < skipOld; i++) {
                            for (int j = 0; j < entryFloats; j++) bb.getFloat();
                            bb.getInt();
                        }

                        for (int i = 0; i < keep; i++) {
                            int xOff = i * INPUT_SIZE;
                            int yOff = i * OUTPUT_SIZE;

                            for (int j = 0; j < INPUT_SIZE; j++) replayX[xOff + j] = bb.getFloat();
                            for (int j = 0; j < OUTPUT_SIZE; j++) replayY[yOff + j] = bb.getFloat();
                            replayLr[i] = bb.getFloat();
                            replayMeta[i] = bb.getInt();
                        }

                        replaySize = keep;
                        replayWrite = keep % REPLAY_CAP;
                        replaySeen = Math.max(rSeen, keep);
                        replayLastHash = rLastHash;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        private void clearReplay() {
            replaySize = 0;
            replayWrite = 0;
            replaySeen = 0L;
            replayLastHash = 0;
            replayTrainCounter = 0;
        }

        private static int findJsonIntValue(String json, String key, int def) {
            try {
                int k = json.indexOf("\"" + key + "\"");
                if (k < 0) return def;
                int colon = json.indexOf(':', k);
                if (colon < 0) return def;
                int i = colon + 1;
                while (i < json.length()) {
                    char c = json.charAt(i);
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        i++;
                        continue;
                    }
                    break;
                }
                int s = i;
                while (i < json.length()) {
                    char c = json.charAt(i);
                    if (c >= '0' && c <= '9') {
                        i++;
                        continue;
                    }
                    break;
                }
                if (i <= s) return def;
                return Integer.parseInt(json.substring(s, i));
            } catch (Exception ignored) {
            }
            return def;
        }

        Vec3d vecFromYawPitch(float yawDeg, float pitchDeg) {
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            double cp = Math.cos(pitch);
            return new Vec3d(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
        }
    }
}