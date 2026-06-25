package fun.rich.features.impl.combat;

import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.features.aura.striking.StrikerConstructor;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import fun.rich.utils.features.aura.warp.TurnsConfig;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import fun.rich.utils.math.task.TaskPriority;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class NeuroAuraExec {

    static final float NEURO_ASSIST_WHEN_MANUAL = 0.35f;

    static final float NEURO_BASE_STRENGTH = 0.88f;
    static final float NEURO_RAMP_MS = 360f;

    static final float NEURO_OVERSHOOT_PROB = 0.21f;
    static final float NEURO_OVERSHOOT_MIN = 0.94f;
    static final float NEURO_OVERSHOOT_MAX = 1.15f;

    static final float NEURO_NOISE_YAW_MIN = 0.06f;
    static final float NEURO_NOISE_YAW_MAX = 0.38f;

    static final float NEURO_NOISE_PITCH_MIN = 0.04f;
    static final float NEURO_NOISE_PITCH_MAX = 0.22f;

    static final float NEURO_NOISE_FREQ_MIN = 0.55f;
    static final float NEURO_NOISE_FREQ_MAX = 1.65f;

    static final float NEURO_MANUAL_CATCH_MIN = 0.005f;
    static final float NEURO_MANUAL_CATCH_MAX = 0.014f;

    static final long NEURO_TOGGLE_BLEND_MS = 480L;

    static final float AIM_MAX_YAW_DELTA = 38.0f;
    static final float AIM_MAX_PITCH_DELTA = 28.0f;

    static final float FALLBACK_GATE_CONF = 0.28f;
    static final float FALLBACK_POINT_GATE = 0.32f;
    static final float FALLBACK_MIN_CONFIDENCE = 0.22f;

    static final float HUMAN_JITTER_SCALE = 7.5f;
    static final float HUMAN_SPEED_SCALE = 18.0f;
    static final float HUMAN_AMP_SCALE = 26.0f;

    static final float GCD_MIN = 0.006f;
    static final float GCD_MAX = 0.45f;

    static final float NEURO_LEARN_RATE_TRACK = 0.10f;
    static final float NEURO_LEARN_RATE_ATTACK = 0.16f;

    static final long NEURO_SAVE_EVERY_MS = 4500L;

    static final float NEURO_CONF_EMA_ALPHA = 0.18f;
    static final float NEURO_POINT_CONF_EMA_ALPHA = 0.16f;
    static final long NEURO_CONF_DROP_HOLD_MS = 140L;
    static final float NEURO_OUTPUT_HOLD_BLEND = 0.68f;

    static final float NEURO_STYLE_EMA_ALPHA = 0.20f;

    static final float NEURO_DELTA_SOFT_YAW = 22.0f;
    static final float NEURO_DELTA_SOFT_PITCH = 16.0f;
    static final float NEURO_DELTA_HARD_YAW = AIM_MAX_YAW_DELTA;
    static final float NEURO_DELTA_HARD_PITCH = AIM_MAX_PITCH_DELTA;

    NeuroRotationModel model = new NeuroRotationModel();
    final PerlinNoise perlin = new PerlinNoise(1337);

    boolean loaded;
    boolean initialized;

    float manualFactor;
    float noisePhaseYaw;
    float noisePhasePitch;

    long toggleBlendStart;
    long toggleBlendUntil;
    boolean toggleBlend;

    boolean savedViewValid;
    float savedYaw;
    float savedPitch;

    boolean returnActive;
    long returnStartTime;
    long returnEndTime;
    long returnLastTime;
    float returnYaw;
    float returnPitch;
    float returnVelYaw;
    float returnVelPitch;

    boolean postReturnRelease;

    private int execPrevSwingTicks;

    int aimSmoothTargetId = -1;
    long aimSmoothLastTime;
    float aimSmoothedDy;
    float aimSmoothedDp;

    long aimEngageStart;
    long aimEngageUntil;
    int aimEngageTargetId = -1;
    boolean aimEngage;

    NeuroRotationModel.Prediction predCache;
    long predCacheTime;
    int predTargetId;
    boolean predCacheAttack;

    private int mpRvLastTid = Integer.MIN_VALUE;
    private float mpRvOrbit = 0.0f;
    private Vec3d mpRvCur = Vec3d.ZERO;
    private long mpRvLastMs = 0L;

    private long lastModelSaveMs = 0L;
    private boolean modelDirty = false;

    private long learnLastMs = 0L;
    private float learnPrevDy = 0.0f;
    private float learnPrevDp = 0.0f;
    private float learnGcdYawEma = 0.0f;
    private float learnGcdPitchEma = 0.0f;

    private int learnLastTid = Integer.MIN_VALUE;
    private Vec3d learnLastPoint = null;
    private long learnLastPointMs = 0L;

    float confEma;
    float pointConfEma;
    long confDropHoldUntil;

    float lastStableOutYaw;
    float lastStableOutPitch;
    long lastStableOutMs;

    float styleYawSpeedEma;
    float stylePitchSpeedEma;
    float styleYawJitterEma;
    float stylePitchJitterEma;
    float styleAmpEma;
    float styleGcdYawEma;
    float styleGcdPitchEma;

    void onActivate(Aura a) {
        manualFactor = 0.0f;

        noisePhaseYaw = rnd(0.0f, 999.0f);
        noisePhasePitch = rnd(0.0f, 999.0f);

        toggleBlendStart = 0L;
        toggleBlendUntil = 0L;
        toggleBlend = false;

        savedViewValid = false;
        savedYaw = 0.0f;
        savedPitch = 0.0f;

        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;
        returnYaw = 0.0f;
        returnPitch = 0.0f;
        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;

        postReturnRelease = false;

        execPrevSwingTicks = 0;

        aimSmoothTargetId = -1;
        aimSmoothLastTime = 0L;
        aimSmoothedDy = 0.0f;
        aimSmoothedDp = 0.0f;

        aimEngageStart = 0L;
        aimEngageUntil = 0L;
        aimEngageTargetId = -1;
        aimEngage = false;

        predCache = null;
        predCacheTime = 0L;
        predTargetId = -1;
        predCacheAttack = false;

        mpRvCur = Vec3d.ZERO;
        mpRvLastMs = 0L;
        mpRvLastTid = Integer.MIN_VALUE;
        mpRvOrbit = 0.0f;

        lastModelSaveMs = 0L;
        modelDirty = false;

        learnLastMs = 0L;
        learnPrevDy = 0.0f;
        learnPrevDp = 0.0f;
        learnGcdYawEma = 0.0f;
        learnGcdPitchEma = 0.0f;
        learnLastTid = Integer.MIN_VALUE;
        learnLastPoint = null;
        learnLastPointMs = 0L;

        confEma = 0.0f;
        pointConfEma = 0.0f;
        confDropHoldUntil = 0L;

        lastStableOutYaw = 0.0f;
        lastStableOutPitch = 0.0f;
        lastStableOutMs = 0L;

        styleYawSpeedEma = 0.0f;
        stylePitchSpeedEma = 0.0f;
        styleYawJitterEma = 0.0f;
        stylePitchJitterEma = 0.0f;
        styleAmpEma = 0.0f;
        styleGcdYawEma = 0.0f;
        styleGcdPitchEma = 0.0f;

        loaded = false;
        initialized = false;
    }

    void onDeactivate(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        trySaveModel(true);

        try {
            if (savedViewValid) startReturnToSaved(a, savedYaw, savedPitch);
            else scheduleReturnToSaved(a);
            postReturnRelease = true;
        } catch (Exception ignored) {
        }
    }

    void onTick(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!loaded) {
            loaded = true;
            try {
                model.load();
            } catch (Exception ignored) {
            }
        }

        if (!initialized) {
            initialized = true;

            predCache = null;
            predCacheTime = 0L;
            predTargetId = -1;
            predCacheAttack = false;

            execPrevSwingTicks = neuroSwingTicks(mc.player);

            mpRvCur = Vec3d.ZERO;
            mpRvLastMs = 0L;
            mpRvLastTid = Integer.MIN_VALUE;
            mpRvOrbit = 0.0f;

            confEma = 0.0f;
            pointConfEma = 0.0f;
            confDropHoldUntil = 0L;
            lastStableOutMs = 0L;

            styleYawSpeedEma = 0.0f;
            stylePitchSpeedEma = 0.0f;
            styleYawJitterEma = 0.0f;
            stylePitchJitterEma = 0.0f;
            styleAmpEma = 0.0f;
            styleGcdYawEma = 0.0f;
            styleGcdPitchEma = 0.0f;

            learnLastMs = 0L;
            learnPrevDy = 0.0f;
            learnPrevDp = 0.0f;
            learnGcdYawEma = 0.0f;
            learnGcdPitchEma = 0.0f;
            learnLastTid = Integer.MIN_VALUE;
            learnLastPoint = null;
            learnLastPointMs = 0L;
        }

        long now = System.currentTimeMillis();

        updateSavedViewAuto(mc, a);

        LivingEntity tt = a != null ? a.getTarget() : null;
        if (tt != null) {
            int tid = tt.getId();
            float k = 0.035f;
            noisePhaseYaw += k * (0.65f + 0.35f * ((tid * 1103515245) & 255) / 255.0f);
            noisePhasePitch += k * (0.65f + 0.35f * ((tid * 1664525) & 255) / 255.0f);
        } else {
            noisePhaseYaw += 0.020f;
            noisePhasePitch += 0.020f;
        }

        boolean exec = a != null && a.neuroExec();
        boolean neuroOn = a != null && a.neuroEnabled();

        if (exec) {
            updateManualFactor(mc);
        } else {
            manualFactor = 0.0f;
            aimEngage = false;
            aimEngageStart = 0L;
            aimEngageUntil = 0L;
            aimEngageTargetId = -1;
        }

        int cur = neuroSwingTicks(mc.player);

        boolean justAttacked = false;
        if (execPrevSwingTicks == 0) {
            execPrevSwingTicks = cur;
        } else {
            justAttacked = neuroSwingStarted(execPrevSwingTicks, cur);
            execPrevSwingTicks = cur;
        }

        if (neuroOn && mc.player != null) {
            if (!toggleBlend && exec) {
                toggleBlend = true;
                toggleBlendStart = now;
                toggleBlendUntil = now + toggleMsFromSmooth(a);
            } else if (toggleBlend && !exec) {
                toggleBlend = false;
                toggleBlendStart = 0L;
                toggleBlendUntil = 0L;
            }
        }

        if (exec && a != null && a.getTarget() != null && !isBusy(a)) {
            float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
            boolean doLearn = (mf >= 0.12f) || justAttacked;
            if (doLearn) {
                LivingEntity t = a.getTarget();
                int tid = t.getId();
                Vec3d p = (learnLastPoint != null && learnLastTid == tid && (now - learnLastPointMs) <= 260L) ? learnLastPoint : null;
                if (learnStep(a, t, p, justAttacked)) {
                    modelDirty = true;
                }
            }
        }

        trySaveModel(false);
    }

    boolean handlePreRotation(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        if (returnActive && a != null && a.neuroExec() && a.getTarget() != null) {
            stopReturnToSaved();
        }

        if (returnActive) {
            returnStep(a);
            return true;
        }

        return false;
    }

    boolean isBusy(Aura a) {
        return returnActive;
    }

    boolean handleNoTarget(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!returnActive && savedViewValid && mc.player != null) {
            startReturnToSaved(a, savedYaw, savedPitch);
            return true;
        } else if (!returnActive) {
            releaseCameraLockNow();
        }

        return false;
    }

    void onTargetSelected(Aura a, LivingEntity t) {
        if (t != null) {
            long now = System.currentTimeMillis();
            startAimEngage(t.getId(), now, a);
            predCache = null;
            predCacheTime = 0L;
            predTargetId = -1;
            predCacheAttack = false;

            noisePhaseYaw = rnd(0.0f, 999.0f);
            noisePhasePitch = rnd(0.0f, 999.0f);

            confDropHoldUntil = 0L;
            lastStableOutMs = 0L;

            learnLastTid = t.getId();
            learnLastPoint = null;
            learnLastPointMs = 0L;
        }
    }

    boolean tryApplyExecRotation(Aura a,
                                 StrikerConstructor.AttackPerpetratorConfigurable config,
                                 StrikeManager attackHandler,
                                 TurnsConnection controller,
                                 TurnsConfig rotationConfig) {
        if (a == null || !a.neuroExec()) return false;
        if (a.getTarget() == null) return false;
        if (controller == null || rotationConfig == null) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;

        LivingEntity target = a.getTarget();
        int tid = target.getId();

        long now = System.currentTimeMillis();

        boolean plannedAttack = attackHandler != null && attackHandler.canAttack(config, 5);

        NeuroRotationModel.Prediction pr;
        if (predCache != null && predTargetId == tid && predCacheAttack == plannedAttack && (now - predCacheTime) <= 200L) {
            pr = predCache;
        } else {
            pr = model.predict(target, plannedAttack);
        }

        NeuroRotationModel.FallbackPrediction fb = model.fallbackPredict(target, plannedAttack);

        updatePlaybackStats(pr, now);

        Turns baseTurns = config != null ? config.getAngle() : null;

        Box box = target.getBoundingBox();

        Vec3d predPoint = null;
        if (pr != null) {
            try {
                predPoint = model.pointFromPrediction(target, pr);
            } catch (Exception ignored) {
            }
        }

        Vec3d rv = null;
        try {
            rv = getMpRv(a);
        } catch (Exception ignored) {
        }

        Vec3d mpPoint = null;
        if (rv != null) {
            double fx = 0.5 + rv.x * 0.5;
            double fy = rv.y;
            double fz = 0.5 + rv.z * 0.5;

            fx = clampD(fx, 0.0, 1.0);
            fy = clampD(fy, 0.0, 1.0);
            fz = clampD(fz, 0.0, 1.0);

            if (fy < 0.36) fy = 0.36;
            if (fy > 0.82) fy = 0.82;

            double x = box.minX + (box.maxX - box.minX) * fx;
            double y = box.minY + (box.maxY - box.minY) * fy;
            double z = box.minZ + (box.maxZ - box.minZ) * fz;

            mpPoint = new Vec3d(
                    clampD(x, box.minX + 1.0E-4, box.maxX - 1.0E-4),
                    clampD(y, box.minY + 1.0E-4, box.maxY - 1.0E-4),
                    clampD(z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4)
            );
        }

        Vec3d point = null;

        if (mpPoint != null && predPoint != null && pr != null) {
            float pcRaw = MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f);
            float cRaw = MathHelper.clamp(pr.confidence, 0.0f, 1.0f);

            float pcMix = MathHelper.clamp(pcRaw * 0.65f + pointConfEma * 0.35f, 0.0f, 1.0f);
            float cMix = MathHelper.clamp(cRaw * 0.60f + confEma * 0.40f, 0.0f, 1.0f);

            if (pcMix < 0.35f || cMix < 0.25f) {
                point = mpPoint;
            } else {
                float w = 0.55f + 0.35f * (1.0f - (pcMix * 0.65f + cMix * 0.35f));
                w = MathHelper.clamp(w, 0.45f, 0.90f);
                point = new Vec3d(
                        predPoint.x + (mpPoint.x - predPoint.x) * w,
                        predPoint.y + (mpPoint.y - predPoint.y) * w,
                        predPoint.z + (mpPoint.z - predPoint.z) * w
                );
            }
        } else if (mpPoint != null) {
            point = mpPoint;
        } else if (predPoint != null) {
            point = predPoint;
        }

        if (point != null) {
            learnLastTid = tid;
            learnLastPoint = point;
            learnLastPointMs = now;

            Turns ang = angleFromPlayerView(point);
            if (ang != null) baseTurns = ang;
        }

        if (baseTurns == null) baseTurns = new Turns(mc.player.getYaw(), mc.player.getPitch());

        NeuroRotationModel.AngleDeg base = model.angleDegFromTurns(baseTurns);

        float s = smooth(a);

        float manualScale = 1.0f - manualFactor * (1.0f - NEURO_ASSIST_WHEN_MANUAL);
        manualScale = MathHelper.clamp(manualScale, 0.15f, 1.0f);

        float confRaw = pr != null ? MathHelper.clamp(pr.confidence, 0.0f, 1.0f) : 0.0f;
        float pcRaw = pr != null ? MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f) : 0.0f;

        float conf = MathHelper.clamp(confRaw * 0.58f + confEma * 0.42f, 0.0f, 1.0f);
        float pc = MathHelper.clamp(pcRaw * 0.62f + pointConfEma * 0.38f, 0.0f, 1.0f);

        float fbConf = fb != null ? fb.confidence : 0.0f;

        boolean preferFallback = pr == null
                || conf < FALLBACK_GATE_CONF
                || pc < FALLBACK_POINT_GATE
                || (fbConf > conf + 0.10f && fbConf >= FALLBACK_MIN_CONFIDENCE);

        float dyNeed = pr != null ? MathHelper.clamp(pr.yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA) : 0.0f;
        float dpNeed = pr != null ? MathHelper.clamp(pr.pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA) : 0.0f;

        if (fb != null) {
            float fdy = MathHelper.clamp(fb.yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
            float fdp = MathHelper.clamp(fb.pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

            if (preferFallback) {
                dyNeed = fdy;
                dpNeed = fdp;
                conf = fbConf;
                pc = Math.max(pc, MathHelper.clamp(fbConf, 0.0f, 1.0f));
            } else {
                float w = MathHelper.clamp((FALLBACK_GATE_CONF - conf) / FALLBACK_GATE_CONF, 0.0f, 1.0f);
                w *= MathHelper.clamp(fbConf, 0.0f, 1.0f);
                w *= MathHelper.lerp(s, 1.0f, 0.82f);
                dyNeed = dyNeed + (fdy - dyNeed) * w;
                dpNeed = dpNeed + (fdp - dpNeed) * w;
            }
        }

        dyNeed = softLimit(dyNeed, NEURO_DELTA_SOFT_YAW, NEURO_DELTA_HARD_YAW);
        dpNeed = softLimit(dpNeed, NEURO_DELTA_SOFT_PITCH, NEURO_DELTA_HARD_PITCH);

        float fovDeg = readFovDeg(a);
        if (fovDeg < 179.0f) {
            float curYaw0 = mc.player.getYaw();
            float aimYaw0 = base.yaw + dyNeed;
            float yawDiff0 = Math.abs(MathHelper.wrapDegrees(aimYaw0 - curYaw0));
            float gate = fovDeg * 0.5f;
            if (!plannedAttack && yawDiff0 > gate) return false;
            if (plannedAttack && yawDiff0 > gate + 25.0f) return false;
        }

        float confGate = MathHelper.clamp(0.25f + 0.75f * MathHelper.clamp(conf, 0.0f, 1.0f), 0.25f, 1.0f);

        float strength = NEURO_BASE_STRENGTH * manualScale * confGate;
        strength = MathHelper.clamp(strength, 0.05f, 1.0f);

        float ramp = 1.0f;
        if (toggleBlend && now <= toggleBlendUntil && toggleBlendUntil > toggleBlendStart) {
            float raw = (float) (now - toggleBlendStart) / (float) (toggleBlendUntil - toggleBlendStart);
            ramp = easeOutCubic(raw);
        }
        strength *= ramp;

        if (!aimEngage || aimEngageTargetId != tid) startAimEngage(tid, now, a);
        if (aimEngage && now <= aimEngageUntil && aimEngageUntil > aimEngageStart) {
            float raw = (float) (now - aimEngageStart) / (float) (aimEngageUntil - aimEngageStart);
            strength *= easeOutCubic(raw);
        }

        float overshoot = 1.0f;
        float osProb = NEURO_OVERSHOOT_PROB;
        if (plannedAttack) osProb *= 1.12f;
        osProb *= MathHelper.lerp(s, 1.0f, 0.92f);
        if (ThreadLocalRandom.current().nextFloat() < osProb) {
            overshoot = rnd(NEURO_OVERSHOOT_MIN, NEURO_OVERSHOOT_MAX);
        }

        if (aimSmoothTargetId != tid) {
            aimSmoothTargetId = tid;
            aimSmoothedDy = 0.0f;
            aimSmoothedDp = 0.0f;
            aimSmoothLastTime = 0L;
            startAimEngage(tid, now, a);
        }

        long dtMs = now - aimSmoothLastTime;
        if (aimSmoothLastTime == 0L) dtMs = 50L;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        aimSmoothLastTime = now;

        float yawPer50 = MathHelper.lerp(s, plannedAttack ? 16.5f : 18.0f, plannedAttack ? 4.0f : 4.2f);
        float pitchPer50 = MathHelper.lerp(s, plannedAttack ? 12.8f : 14.0f, plannedAttack ? 3.0f : 3.4f);

        float dyMax = yawPer50 * ((float) dtMs / 50.0f);
        float dpMax = pitchPer50 * ((float) dtMs / 50.0f);

        float dyErr = MathHelper.wrapDegrees(dyNeed - aimSmoothedDy);
        float dpErr = MathHelper.wrapDegrees(dpNeed - aimSmoothedDp);

        aimSmoothedDy += MathHelper.clamp(dyErr, -dyMax, dyMax);
        aimSmoothedDp += MathHelper.clamp(dpErr, -dpMax, dpMax);

        aimSmoothedDy = MathHelper.clamp(aimSmoothedDy, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
        aimSmoothedDp = MathHelper.clamp(aimSmoothedDp, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

        float nYaw = base.yaw + aimSmoothedDy * strength * overshoot;
        float nPitch = base.pitch + aimSmoothedDp * strength * overshoot;

        float human = 1.0f - MathHelper.clamp(conf, 0.0f, 1.0f);
        human = human * human;
        if (plannedAttack) human *= 0.55f;
        human *= MathHelper.lerp(s, 1.05f, 0.85f);

        float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
        human *= (1.0f - mf * 0.55f);

        float lj = 0.0f;
        float ls = 0.0f;
        float la = 0.0f;
        float gcdYaw = 0.0f;
        float gcdPitch = 0.0f;

        if (pr != null) {
            float pyj = styleYawJitterEma != 0.0f ? styleYawJitterEma : Math.abs(pr.yawJitter);
            float ppj = stylePitchJitterEma != 0.0f ? stylePitchJitterEma : Math.abs(pr.pitchJitter);
            float pys = styleYawSpeedEma != 0.0f ? styleYawSpeedEma : Math.abs(pr.yawSpeed);
            float pps = stylePitchSpeedEma != 0.0f ? stylePitchSpeedEma : Math.abs(pr.pitchSpeed);
            float pamp = styleAmpEma != 0.0f ? styleAmpEma : Math.abs(pr.amp);

            lj = MathHelper.clamp((pyj + ppj) / HUMAN_JITTER_SCALE, 0.0f, 1.0f);
            ls = MathHelper.clamp((pys + pps) / HUMAN_SPEED_SCALE, 0.0f, 1.0f);
            la = MathHelper.clamp(pamp / HUMAN_AMP_SCALE, 0.0f, 1.0f);

            gcdYaw = styleGcdYawEma > 0.0f ? styleGcdYawEma : pr.gcdYaw;
            gcdPitch = styleGcdPitchEma > 0.0f ? styleGcdPitchEma : pr.gcdPitch;
        }

        float learnHumanBoost = 0.20f + 0.80f * (0.55f * lj + 0.30f * ls + 0.15f * la);
        learnHumanBoost = MathHelper.clamp(learnHumanBoost, 0.20f, 1.15f);

        float aYaw = MathHelper.lerp(human, NEURO_NOISE_YAW_MIN, NEURO_NOISE_YAW_MAX) * learnHumanBoost;
        float aPitch = MathHelper.lerp(human, NEURO_NOISE_PITCH_MIN, NEURO_NOISE_PITCH_MAX) * learnHumanBoost;

        float fYaw = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);
        float fPitch = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);

        float tt = (float) ((now % 100000L) / 1000.0);

        float ny = perlin.fbm((tt * fYaw + noisePhaseYaw), (float) (tid * 0.013 + 0.11), 4, 2.0f, 0.55f);
        float np = perlin.fbm((tt * fPitch + noisePhasePitch), (float) (tid * 0.017 + 37.7), 4, 2.0f, 0.55f);

        float sway = perlin.noise((tt * 0.22f + noisePhaseYaw * 0.07f), (float) (tid * 0.009 + 9.3));
        float spn = perlin.noise((tt * 0.19f + noisePhasePitch * 0.07f), (float) (tid * 0.011 + 3.7));

        float nyOut = (ny * 0.72f + sway * 0.28f) * aYaw;
        float npOut = (np * 0.72f + spn * 0.28f) * aPitch;

        float kStr = strength * (plannedAttack ? 0.62f : 0.72f);
        nyOut *= kStr;
        npOut *= kStr;

        nYaw += nyOut;
        nPitch += npOut;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float outYaw = nYaw;
        float outPitch = nPitch;

        float gYaw = MathHelper.clamp(gcdYaw == 0.0f ? 0.0f : Math.abs(gcdYaw), GCD_MIN, GCD_MAX);
        float gPitch = MathHelper.clamp(gcdPitch == 0.0f ? 0.0f : Math.abs(gcdPitch), GCD_MIN, GCD_MAX);

        if (gYaw > 0.0f) {
            float dy = MathHelper.wrapDegrees(outYaw - curYaw);
            float q = quantize(dy, gYaw);
            outYaw = curYaw + q;
        }
        if (gPitch > 0.0f) {
            float dp = MathHelper.wrapDegrees(outPitch - curPitch);
            float q = quantize(dp, gPitch);
            outPitch = curPitch + q;
        }

        if (confDropHoldUntil > now && lastStableOutMs != 0L) {
            outYaw = blendAngle(outYaw, lastStableOutYaw, NEURO_OUTPUT_HOLD_BLEND);
            outPitch = MathHelper.lerp(NEURO_OUTPUT_HOLD_BLEND, outPitch, lastStableOutPitch);
        }

        outPitch = MathHelper.clamp(outPitch, -90.0f, 90.0f);

        if (conf >= 0.34f) {
            lastStableOutYaw = outYaw;
            lastStableOutPitch = outPitch;
            lastStableOutMs = now;
        }

        Turns out = model.turnsFromAngleDeg(outYaw, outPitch);
        Turns.VecRotation rotation = new Turns.VecRotation(out, out.toVector());

        int speed;
        if (plannedAttack) {
            speed = 40;
        } else {
            speed = (int) MathHelper.lerp(s, 28.0f, 14.0f);
            if (speed < 6) speed = 6;
            if (speed > 40) speed = 40;
        }

        if (plannedAttack) controller.clear();

        controller.rotateTo(rotation, target, speed, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, a);

        Aura.shouldRotate = true;
        Aura.fakeRotate = false;

        predCache = pr;
        predCacheTime = now;
        predTargetId = tid;
        predCacheAttack = plannedAttack;

        return true;
    }

    public Vec3d getMpRv(Aura a) {
        long now = System.currentTimeMillis();

        LivingEntity t = a != null ? a.getTarget() : null;
        if (t == null) {
            mpRvCur = Vec3d.ZERO;
            mpRvLastMs = 0L;
            mpRvLastTid = Integer.MIN_VALUE;
            mpRvOrbit = 0.0f;
            return Vec3d.ZERO;
        }

        int tid;
        try {
            tid = t.getId();
        } catch (Exception e) {
            tid = System.identityHashCode(t);
        }

        if (tid != mpRvLastTid) {
            mpRvLastTid = tid;
            mpRvCur = Vec3d.ZERO;
            mpRvLastMs = now;
            mpRvOrbit = rnd(0.0f, (float) (Math.PI * 2.0));
        }

        long dtMs = now - mpRvLastMs;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 60) dtMs = 60;
        mpRvLastMs = now;

        float s = smooth(a);

        float orbitSpeed = MathHelper.lerp(s, 0.46f, 0.28f);
        float dtScale = (float) dtMs / 50.0f;
        mpRvOrbit += orbitSpeed * dtScale + rnd(-0.018f, 0.018f) * dtScale;

        Vec3d desired = nextMpRvTarget(a);

        float tau = MathHelper.lerp(s, 140.0f, 85.0f);
        float alpha = (float) dtMs / tau;
        alpha = MathHelper.clamp(alpha, 0.0f, 1.0f);
        alpha = alpha * alpha * (3.0f - 2.0f * alpha);

        if (mpRvCur == Vec3d.ZERO) {
            mpRvCur = desired;
            return mpRvCur;
        }

        mpRvCur = new Vec3d(
                lerpD(mpRvCur.x, desired.x, alpha),
                lerpD(mpRvCur.y, desired.y, alpha),
                lerpD(mpRvCur.z, desired.z, alpha)
        );

        return mpRvCur;
    }

    private Vec3d nextMpRvTarget(Aura a) {
        float s = smooth(a);

        float o = mpRvOrbit;

        float y = 0.56f
                + 0.10f * (float) Math.sin(o * 0.74f + 0.9f)
                + 0.06f * (float) Math.sin(o * 1.28f + 2.2f)
                - 0.05f * (float) (0.5 + 0.5 * Math.sin(o * 0.35f + 1.7f));

        float x = (0.22f * (float) Math.sin(o))
                + (0.06f * (float) Math.sin(o * 1.85f + 0.25f));

        float z = (0.08f * (float) Math.cos(o * 0.92f + 0.4f))
                + (0.03f * (float) Math.sin(o * 1.35f + 1.1f));

        float jitter = MathHelper.lerp(s, 0.030f, 0.014f);
        x += rnd(-jitter, jitter);
        z += rnd(-jitter, jitter);
        y += rnd(-jitter * 0.55f, jitter * 0.55f);

        float sideClamp = MathHelper.lerp(s, 0.50f, 0.42f);
        float fwdClamp = MathHelper.lerp(s, 0.40f, 0.34f);

        x = MathHelper.clamp(x, -sideClamp, sideClamp);
        z = MathHelper.clamp(z, -fwdClamp, fwdClamp);
        y = MathHelper.clamp(y, 0.36f, 0.82f);

        if (a == null || !a.neuroExec()) {
            x *= 0.75f;
            z *= 0.75f;
            y = MathHelper.clamp(y, 0.40f, 0.80f);
        }

        return new Vec3d(x, y, z);
    }

    public Vec3d adjustPointForExec(Aura a, LivingEntity target, Box hb, Vec3d rawPoint) {
        if (target == null) return rawPoint;
        if (rawPoint == null) return null;

        Box box = hb != null ? hb : target.getBoundingBox();

        boolean plannedAttack = a != null && a.neuroExec();

        NeuroRotationModel.Prediction pr;
        long now = System.currentTimeMillis();
        if (predCache != null && predTargetId == target.getId() && (now - predCacheTime) <= 220L) {
            pr = predCache;
        } else {
            pr = model.predict(target, plannedAttack);
        }

        Vec3d predPoint = null;
        if (pr != null) {
            try {
                predPoint = model.pointFromPrediction(target, pr);
            } catch (Exception ignored) {
            }
        }

        float s = smooth(a);

        double x = rawPoint.x;
        double y = rawPoint.y;
        double z = rawPoint.z;

        if (predPoint != null) {
            float conf = pr.confidence;
            float pc = MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f);
            float w = 0.20f + 0.80f * (conf * 0.65f + pc * 0.35f);
            w = MathHelper.clamp(w, 0.08f, 0.95f);

            w *= MathHelper.lerp(s, 1.0f, 0.85f);

            x = x + (predPoint.x - x) * w;
            y = y + (predPoint.y - y) * w;
            z = z + (predPoint.z - z) * w;
        }

        if (a != null) {
            Vec3d rv = getMpRv(a);
            if (rv != null && rv != Vec3d.ZERO) {
                double fx = 0.5 + rv.x * 0.5;
                double fy = rv.y;
                double fz = 0.5 + rv.z * 0.5;

                fx = clampD(fx, 0.0, 1.0);
                fy = clampD(fy, 0.0, 1.0);
                fz = clampD(fz, 0.0, 1.0);

                double mpX = box.minX + (box.maxX - box.minX) * fx;
                double mpY = box.minY + (box.maxY - box.minY) * fy;
                double mpZ = box.minZ + (box.maxZ - box.minZ) * fz;

                float wMp = MathHelper.lerp(s, 0.62f, 0.45f);
                if (!a.neuroExec()) wMp = MathHelper.lerp(s, 0.72f, 0.52f);

                x = x + (mpX - x) * wMp;
                y = y + (mpY - y) * wMp;
                z = z + (mpZ - z) * wMp;
            }
        }

        x = clampD(x, box.minX + 1.0E-4, box.maxX - 1.0E-4);
        y = clampD(y, box.minY + 1.0E-4, box.maxY - 1.0E-4);
        z = clampD(z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4);

        return new Vec3d(x, y, z);
    }

    void scheduleReturnToSaved(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        savedViewValid = true;
        startReturnToSaved(a, savedYaw, savedPitch);
    }

    void startReturnToSaved(Aura a, float toYaw, float toPitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        savedYaw = toYaw;
        savedPitch = toPitch;

        returnActive = true;
        returnStartTime = System.currentTimeMillis();
        returnEndTime = returnStartTime + returnMsFromSmooth(a);
        returnLastTime = returnStartTime;

        returnYaw = mc.player.getYaw();
        returnPitch = mc.player.getPitch();

        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;

        postReturnRelease = false;

        Aura.shouldRotate = true;
        Aura.fakeRotate = false;
    }

    void stopReturnToSaved() {
        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;
        returnVelYaw = 0.0f;
        returnVelPitch = 0.0f;
    }

    void returnStep(Aura a) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            stopReturnToSaved();
            return;
        }

        long now = System.currentTimeMillis();
        if (now >= returnEndTime || returnEndTime <= returnStartTime) {
            mc.player.setYaw(savedYaw);
            mc.player.setPitch(savedPitch);
            stopReturnToSaved();
            if (postReturnRelease) {
                postReturnRelease = false;
                releaseCameraLockNow();
            }
            return;
        }

        long dtMs = now - returnLastTime;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        returnLastTime = now;

        float dt = (float) dtMs / 1000.0f;

        float targetYaw = savedYaw;
        float targetPitch = savedPitch;

        float s = smooth(a);

        float omega = 14.0f - 4.0f * s;
        if (omega < 8.0f) omega = 8.0f;
        if (omega > 14.0f) omega = 14.0f;
        float zeta = 1.0f;

        float dy = MathHelper.wrapDegrees(targetYaw - returnYaw);
        float dp = MathHelper.wrapDegrees(targetPitch - returnPitch);

        float ay = omega * omega * dy - 2.0f * zeta * omega * returnVelYaw;
        float ap = omega * omega * dp - 2.0f * zeta * omega * returnVelPitch;

        returnVelYaw += ay * dt;
        returnVelPitch += ap * dt;

        float lim = 210.0f;
        returnVelYaw = MathHelper.clamp(returnVelYaw, -lim, lim);
        returnVelPitch = MathHelper.clamp(returnVelPitch, -lim, lim);

        returnYaw += returnVelYaw * dt;
        returnPitch += returnVelPitch * dt;

        mc.player.setYaw(returnYaw);
        mc.player.setPitch(MathHelper.clamp(returnPitch, -90.0f, 90.0f));
    }

    private void updateManualFactor(MinecraftClient mc) {
        if (mc.player == null) return;

        float d = Math.abs(MathHelper.wrapDegrees(mc.player.getYaw() - savedYaw));
        float p = Math.abs(MathHelper.wrapDegrees(mc.player.getPitch() - savedPitch));

        float e = (d + p) * 0.5f;

        float catchRate = rnd(NEURO_MANUAL_CATCH_MIN, NEURO_MANUAL_CATCH_MAX);
        float k = MathHelper.clamp(e * catchRate, 0.0f, 1.0f);

        manualFactor = manualFactor + (k - manualFactor) * 0.12f;
        manualFactor = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
    }

    private void updateSavedViewAuto(MinecraftClient mc, Aura a) {
        if (mc.player == null) return;
        if (!savedViewValid) {
            savedViewValid = true;
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
            return;
        }
        float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
        LivingEntity t = a != null ? a.getTarget() : null;
        if (mf > 0.10f || t == null) {
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
        }
    }

    private void startAimEngage(int tid, long now, Aura a) {
        aimEngage = true;
        aimEngageTargetId = tid;
        aimEngageStart = now;
        aimEngageUntil = now + aimEngageMsFromSmooth(a);
    }

    private long aimEngageMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 520.0f, 240.0f);
        if (v < 180L) v = 180L;
        if (v > 650L) v = 650L;
        return v;
    }

    Turns angleFromPlayerView(Vec3d point) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || point == null) return new Turns(0.0f, 0.0f);
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Vec3d dir = point.subtract(eye);
        return MathAngle.fromVec3d(dir);
    }

    private long toggleMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 560.0f, 420.0f);
        if (v < 320L) v = 320L;
        if (v > 720L) v = 720L;
        return v;
    }

    private long returnMsFromSmooth(Aura a) {
        float s = smooth(a);
        long v = (long) MathHelper.lerp(s, 540.0f, 420.0f);
        if (v < 360L) v = 360L;
        if (v > 680L) v = 680L;
        return v;
    }

    private static float easeOutCubic(float t) {
        float x = MathHelper.clamp(t, 0.0f, 1.0f);
        float u = 1.0f - x;
        return 1.0f - u * u * u;
    }

    private void releaseCameraLockNow() {
        try {
            TurnsConnection.INSTANCE.clear();
        } catch (Exception ignored) {
        }
        Aura.fakeRotate = false;
        Aura.shouldRotate = false;

        returnActive = false;
        returnStartTime = 0L;
        returnEndTime = 0L;
        returnLastTime = 0L;

        aimEngage = false;
        aimEngageStart = 0L;
        aimEngageUntil = 0L;
        aimEngageTargetId = -1;

        confDropHoldUntil = 0L;
        lastStableOutMs = 0L;
        confEma = 0.0f;
        pointConfEma = 0.0f;
    }

    void markModelDirty() {
        modelDirty = true;
    }

    void notifyModelUpdated() {
        modelDirty = true;
        predCache = null;
        predCacheTime = 0L;
        predTargetId = -1;
        predCacheAttack = false;

        confEma = 0.0f;
        pointConfEma = 0.0f;
        confDropHoldUntil = 0L;
        lastStableOutMs = 0L;

        aimSmoothTargetId = -1;
        aimSmoothLastTime = 0L;
        aimSmoothedDy = 0.0f;
        aimSmoothedDp = 0.0f;
    }

    private void updatePlaybackStats(NeuroRotationModel.Prediction pr, long now) {
        float prevConf = confEma;

        float c = pr != null ? MathHelper.clamp(pr.confidence, 0.0f, 1.0f) : 0.0f;
        float pc = pr != null ? MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f) : 0.0f;

        if (prevConf > 0.34f && c < 0.14f) {
            confDropHoldUntil = now + NEURO_CONF_DROP_HOLD_MS;
        }

        confEma = ema(confEma, c, NEURO_CONF_EMA_ALPHA);
        pointConfEma = ema(pointConfEma, pc, NEURO_POINT_CONF_EMA_ALPHA);

        if (pr == null) return;

        styleYawSpeedEma = ema(styleYawSpeedEma, Math.abs(pr.yawSpeed), NEURO_STYLE_EMA_ALPHA);
        stylePitchSpeedEma = ema(stylePitchSpeedEma, Math.abs(pr.pitchSpeed), NEURO_STYLE_EMA_ALPHA);
        styleYawJitterEma = ema(styleYawJitterEma, Math.abs(pr.yawJitter), NEURO_STYLE_EMA_ALPHA);
        stylePitchJitterEma = ema(stylePitchJitterEma, Math.abs(pr.pitchJitter), NEURO_STYLE_EMA_ALPHA);
        styleAmpEma = ema(styleAmpEma, Math.abs(pr.amp), NEURO_STYLE_EMA_ALPHA);

        if (pr.gcdYaw > 0.0f) {
            styleGcdYawEma = styleGcdYawEma == 0.0f ? pr.gcdYaw : ema(styleGcdYawEma, pr.gcdYaw, 0.12f);
        }
        if (pr.gcdPitch > 0.0f) {
            styleGcdPitchEma = styleGcdPitchEma == 0.0f ? pr.gcdPitch : ema(styleGcdPitchEma, pr.gcdPitch, 0.12f);
        }
    }

    private static float ema(float cur, float value, float alpha) {
        if (alpha <= 0.0f) return cur;
        if (cur == 0.0f) return value;
        return cur + (value - cur) * MathHelper.clamp(alpha, 0.0f, 1.0f);
    }

    private static float softLimit(float v, float soft, float hard) {
        float av = Math.abs(v);
        if (av <= soft) return v;
        if (av >= hard) return Math.copySign(hard, v);

        float t = (av - soft) / Math.max(1.0e-6f, (hard - soft));
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        float eased = t * t * (3.0f - 2.0f * t);

        float out = soft + (hard - soft) * eased;
        return Math.copySign(out, v);
    }

    private static float blendAngle(float from, float to, float t) {
        float d = MathHelper.wrapDegrees(to - from);
        return from + d * MathHelper.clamp(t, 0.0f, 1.0f);
    }

    private float smooth(Aura a) {
        float v = 0.76f;
        if (a != null) {
            try {
                v = a.neuroSmoothValue();
            } catch (Exception ignored) {
            }
        }
        if (v < 0.20f) v = 0.20f;
        if (v > 0.95f) v = 0.95f;
        return v;
    }

    private static float readFovDeg(Aura a) {
        if (a == null) return 180.0f;

        Float v = callFloat0(a, "neuroFovValue");
        if (v == null) v = callFloat0(a, "getNeuroFov");
        if (v == null) v = callFloat0(a, "neuroFov");
        if (v == null) v = callFloat0(a, "fovValue");
        if (v == null) v = callFloat0(a, "getFov");
        if (v == null) v = callFloat0(a, "fov");

        if (v == null) {
            Object fs = readField0(a, "neuroFov");
            if (fs == null) fs = readField0(a, "fov");
            if (fs == null) fs = readField0(a, "fovSetting");
            if (fs == null) fs = readField0(a, "aimFov");

            if (fs != null) {
                v = callFloat0(fs, "getValue");
                if (v == null) v = callFloat0(fs, "get");
                if (v == null && fs instanceof Number) v = ((Number) fs).floatValue();
            }
        }

        if (v == null || Float.isNaN(v) || Float.isInfinite(v)) return 180.0f;
        v = MathHelper.clamp(v, 1.0f, 180.0f);
        return v;
    }

    private static Float callFloat0(Object obj, String name) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(name);
            m.setAccessible(true);
            Object r = m.invoke(obj);
            if (r instanceof Number) return ((Number) r).floatValue();
        } catch (Exception ignored) {
        }
        try {
            Method m = obj.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            Object r = m.invoke(obj);
            if (r instanceof Number) return ((Number) r).floatValue();
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Object readField0(Object obj, String name) {
        if (obj == null) return null;
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {
        }
        try {
            Field f = obj.getClass().getField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean learnStep(Aura a, LivingEntity target, Vec3d point, boolean attackSample) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return false;

        long now = System.currentTimeMillis();

        Vec3d aimPoint = point;
        if (aimPoint == null) {
            aimPoint = aimPointFromView(mc, target);
            if (aimPoint == null) aimPoint = target.getBoundingBox().getCenter();
        }

        Turns baseTurns = angleFromPlayerView(aimPoint);
        NeuroRotationModel.AngleDeg base = model.angleDegFromTurns(baseTurns);

        float pyaw = mc.player.getYaw();
        float ppitch = mc.player.getPitch();

        float dy = MathHelper.wrapDegrees(pyaw - base.yaw);
        float dp = MathHelper.wrapDegrees(ppitch - base.pitch);

        dy = MathHelper.clamp(dy, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
        dp = MathHelper.clamp(dp, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

        Box hb = target.getBoundingBox();
        double lx = hb.maxX - hb.minX;
        double ly = hb.maxY - hb.minY;
        double lz = hb.maxZ - hb.minZ;

        double fx = lx <= 1.0E-9 ? 0.5 : (aimPoint.x - hb.minX) / lx;
        double fy = ly <= 1.0E-9 ? 0.5 : (aimPoint.y - hb.minY) / ly;
        double fz = lz <= 1.0E-9 ? 0.5 : (aimPoint.z - hb.minZ) / lz;

        float px = (float) MathHelper.clamp(fx, 0.0, 1.0);
        float py = (float) MathHelper.clamp(fy, 0.0, 1.0);
        float pz = (float) MathHelper.clamp(fz, 0.0, 1.0);

        long dtMs = now - learnLastMs;
        if (learnLastMs == 0L) dtMs = 50L;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        learnLastMs = now;

        float dt = (float) dtMs / 1000.0f;
        float dyStep = MathHelper.wrapDegrees(dy - learnPrevDy);
        float dpStep = MathHelper.wrapDegrees(dp - learnPrevDp);

        float yawSp = dt <= 0.0f ? 0.0f : Math.abs(dyStep) / dt;
        float pitSp = dt <= 0.0f ? 0.0f : Math.abs(dpStep) / dt;

        float yJ = dyStep;
        float pJ = dpStep;

        float amp = (float) Math.sqrt(dy * dy + dp * dp);

        float ay = Math.abs(dyStep);
        float ap = Math.abs(dpStep);
        if (ay > 0.0005f) learnGcdYawEma = updateGcd(learnGcdYawEma, ay, 0.06f);
        if (ap > 0.0005f) learnGcdPitchEma = updateGcd(learnGcdPitchEma, ap, 0.06f);

        learnPrevDy = dy;
        learnPrevDp = dp;

        float gY = learnGcdYawEma > 0.0005f ? MathHelper.clamp(learnGcdYawEma, GCD_MIN, GCD_MAX) : 0.0f;
        float gP = learnGcdPitchEma > 0.0005f ? MathHelper.clamp(learnGcdPitchEma, GCD_MIN, GCD_MAX) : 0.0f;

        float mf = MathHelper.clamp(manualFactor, 0.0f, 1.0f);
        if (!attackSample && mf < 0.12f) return false;

        float rate = attackSample ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        rate = rate * (0.40f + 0.60f * mf);
        rate = MathHelper.clamp(rate, 0.01f, 0.40f);

        boolean ok = model.learn(target, attackSample, dy, dp, px, py, pz, yawSp, pitSp, yJ, pJ, gY, gP, amp, rate);
        if (ok) {
            modelDirty = true;
            return true;
        }
        return false;
    }

    private static float updateGcd(float cur, float step, float alpha) {
        step = MathHelper.clamp(step, 0.0005f, 90.0f);
        if (cur <= 0.0001f) return step;
        float ratio = step / Math.max(cur, 0.0005f);
        float nearest = Math.max(1.0f, Math.round(ratio));
        float cand = step / Math.max(nearest, 1.0f);
        return cur + (cand - cur) * MathHelper.clamp(alpha, 0.0f, 1.0f);
    }

    private Vec3d aimPointFromView(MinecraftClient mc, LivingEntity target) {
        if (mc == null || mc.player == null || target == null) return null;
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Vec3d look = mc.player.getRotationVec(1.0f);
        double reach = 8.0;
        Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);

        Box box = target.getBoundingBox();
        try {
            Optional<Vec3d> hit = box.raycast(eye, end);
            if (hit != null && hit.isPresent()) return hit.get();
        } catch (Exception ignored) {
        }

        Vec3d c = box.getCenter();
        return new Vec3d(
                clampD(c.x, box.minX + 1.0E-4, box.maxX - 1.0E-4),
                clampD(c.y, box.minY + 1.0E-4, box.maxY - 1.0E-4),
                clampD(c.z, box.minZ + 1.0E-4, box.maxZ - 1.0E-4)
        );
    }

    private void trySaveModel(boolean force) {
        if (!modelDirty && !force) return;

        long now = System.currentTimeMillis();
        if (!force && lastModelSaveMs != 0L && (now - lastModelSaveMs) < NEURO_SAVE_EVERY_MS) return;

        lastModelSaveMs = now;
        try {
            model.save();
            modelDirty = false;
        } catch (Exception ignored) {
        }
    }

    static int neuroSwingTicks(Object player) {
        Integer v = readIntField(player, "handSwingTicks");
        if (v != null) return v;
        v = readIntField(player, "handSwingProgressInt");
        if (v != null) return v;
        v = readIntField(player, "handSwingingTicks");
        if (v != null) return v;
        v = readIntField(player, "ticksSinceLastSwing");
        if (v != null) return v;
        return 0;
    }

    static boolean neuroSwingStarted(int prev, int cur) {
        if (cur < prev) return true;
        if (prev == 0 && cur > 0) return true;
        if (prev > 0 && cur == 0) return true;
        return false;
    }

    static Integer readIntField(Object obj, String name) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (Integer) f.get(obj);
        } catch (Exception ignored) {
        }
        try {
            var f = obj.getClass().getField(name);
            f.setAccessible(true);
            return (Integer) f.get(obj);
        } catch (Exception ignored) {
        }
        return null;
    }

    private static float rnd(float a, float b) {
        if (b <= a) return a;
        return a + ThreadLocalRandom.current().nextFloat() * (b - a);
    }

    private static double lerpD(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float quantize(float v, float step) {
        if (step <= 0.0f) return v;
        float s = Math.abs(step);
        return Math.round(v / s) * s;
    }

    public static final class NeuroRotationModel {

        private static final int DIST_BINS = 14;
        private static final int SPEED_BINS = 10;
        private static final int AIR_BINS = 2;
        private static final int MODE_BINS = 2;
        private static final int SIDE_BINS = 3;

        private static final int SIZE = DIST_BINS * SPEED_BINS * AIR_BINS * MODE_BINS * SIDE_BINS;

        private static final float DIST_MAX = 6.0f;
        private static final float SPEED_MAX = 0.75f;

        private static final int NN_IN = 7;
        private static final int NN_H1 = 24;
        private static final int NN_H2 = 16;
        private static final int NN_OUT = 13;

        private static final int MAGIC = 0x4F52414E;

        private static final int STORE_VER = 8;
        private static final int SAMPLE_BYTES = 18;

        private final NeuralNet net = new NeuralNet(NN_IN, NN_H1, NN_H2, NN_OUT);

        private final int[] count = new int[SIZE];

        private final float[] fbYawAvg = new float[SIZE];
        private final float[] fbPitchAvg = new float[SIZE];
        private final float[] fbErrEma = new float[SIZE];
        private final int[] fbCount = new int[SIZE];

        private float lossEma = 0.0f;

        private byte[] mem = null;
        private int memLen = 0;
        private int memCount = 0;

        private java.nio.file.Path filePath(MinecraftClient mc) {
            File dir = new File(mc.runDirectory, "rich");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "neuro_aura.json").toPath();
        }

        private static int sideBin(Vec3d tv, float playerYawDeg) {
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

        private int idxBins(int db, int sb, int ab, int mb, int xb) {
            int i = MathHelper.clamp(db, 0, DIST_BINS - 1);
            i = i * SPEED_BINS + MathHelper.clamp(sb, 0, SPEED_BINS - 1);
            i = i * AIR_BINS + MathHelper.clamp(ab, 0, AIR_BINS - 1);
            i = i * MODE_BINS + MathHelper.clamp(mb, 0, MODE_BINS - 1);
            i = i * SIDE_BINS + MathHelper.clamp(xb, 0, SIDE_BINS - 1);
            return i;
        }

        private int idxFor(LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || target == null) return -1;

            float dist = mc.player.distanceTo(target);
            Vec3d v = target.getVelocity();
            float sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);

            boolean air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
            int ab = air ? 1 : 0;

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            int db = MathHelper.clamp((int) Math.floor(distN * (DIST_BINS - 1)), 0, DIST_BINS - 1);
            int sb = MathHelper.clamp((int) Math.floor(speedN * (SPEED_BINS - 1)), 0, SPEED_BINS - 1);

            int mode = attackSample ? 1 : 0;
            int side = sideBin(v, mc.player.getYaw());

            return idxBins(db, sb, ab, mode, side);
        }

        private void fillFeatures(float[] x, LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();

            float dist = 3.0f;
            float sp = 0.0f;
            boolean air = false;
            int side = 1;

            if (mc.player != null && target != null) {
                dist = mc.player.distanceTo(target);
                Vec3d v = target.getVelocity();
                sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
                side = sideBin(v, mc.player.getYaw());
            }

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            x[0] = distN;
            x[1] = speedN;
            x[2] = air ? 1.0f : 0.0f;
            x[3] = attackSample ? 1.0f : 0.0f;

            x[4] = (side == 0) ? 1.0f : 0.0f;
            x[5] = (side == 1) ? 1.0f : 0.0f;
            x[6] = (side == 2) ? 1.0f : 0.0f;
        }

        boolean learn(LivingEntity target,
                      boolean attackSample,
                      float yawDeltaDeg,
                      float pitchDeltaDeg,
                      float px, float py, float pz,
                      float yawSpeed, float pitchSpeed,
                      float yawJitter, float pitchJitter,
                      float gcdYaw, float gcdPitch,
                      float amp,
                      float rate) {
            int i = idxFor(target, attackSample);
            if (i < 0) return false;

            float r = MathHelper.clamp(rate, 0.01f, 0.40f);

            float dy = MathHelper.clamp(yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
            float dp = MathHelper.clamp(pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

            float err = (Math.abs(dy) + Math.abs(dp)) * 0.5f;

            int c = count[i];
            if (c < 2000000000) count[i] = c + 1;

            float fbR = MathHelper.clamp(r * 0.65f, 0.01f, 0.30f);
            fbYawAvg[i] = fbYawAvg[i] + MathHelper.wrapDegrees(dy - fbYawAvg[i]) * fbR;
            fbPitchAvg[i] = fbPitchAvg[i] + MathHelper.wrapDegrees(dp - fbPitchAvg[i]) * fbR;
            fbErrEma[i] = fbErrEma[i] + (err - fbErrEma[i]) * (fbR * 0.85f);
            int fc = fbCount[i];
            if (fc < 2000000000) fbCount[i] = fc + 1;

            float[] x = net.tmpIn();
            fillFeatures(x, target, attackSample);

            float dyN = MathHelper.clamp(dy / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
            float dpN = MathHelper.clamp(dp / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);

            float pxx = MathHelper.clamp(px, 0.0f, 1.0f);
            float pyy = MathHelper.clamp(py, 0.0f, 1.0f);
            float pzz = MathHelper.clamp(pz, 0.0f, 1.0f);

            float ySp = MathHelper.clamp(yawSpeed, 0.0f, 240.0f) / 240.0f;
            float pSp = MathHelper.clamp(pitchSpeed, 0.0f, 240.0f) / 240.0f;

            float yJ = MathHelper.clamp(yawJitter / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
            float pJ = MathHelper.clamp(pitchJitter / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);

            float gY = (gcdYaw == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gcdYaw) / GCD_MAX, 0.0f, 1.0f);
            float gP = (gcdPitch == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gcdPitch) / GCD_MAX, 0.0f, 1.0f);

            float aN = MathHelper.clamp(amp / 50.0f, 0.0f, 1.0f);

            float errN = MathHelper.clamp(err / 20.0f, 0.0f, 1.0f);

            float[] t = net.tmpTarget();
            t[0] = dyN;
            t[1] = dpN;
            t[2] = pxx;
            t[3] = pyy;
            t[4] = pzz;
            t[5] = ySp;
            t[6] = pSp;
            t[7] = yJ;
            t[8] = pJ;
            t[9] = gY;
            t[10] = gP;
            t[11] = aN;
            t[12] = errN;

            float lr = 0.0025f + 0.020f * r;
            if (attackSample) lr *= 1.10f;

            float loss = net.train(x, t, lr, 0.00012f);
            if (loss > 0.0f) {
                if (lossEma == 0.0f) lossEma = loss;
                else lossEma = lossEma + (loss - lossEma) * 0.06f;
            }

            storeSampleFromCurrent(target, attackSample, x, t);

            return true;
        }

        private void storeSampleFromCurrent(LivingEntity target, boolean attackSample, float[] x, float[] t) {
            int c = memCount;
            boolean keep;
            if (c < 120000) keep = true;
            else if (c < 350000) keep = (ThreadLocalRandom.current().nextInt(2) == 0);
            else keep = (ThreadLocalRandom.current().nextInt(3) == 0);

            if (!keep) return;

            ensureMemCapacity(SAMPLE_BYTES);

            int side = (x[4] > 0.5f) ? 0 : (x[6] > 0.5f ? 2 : 1);
            int flags = 0;
            if (x[2] > 0.5f) flags |= 1;
            if (attackSample) flags |= 2;
            flags |= (side & 3) << 2;

            int o = memLen;

            mem[o++] = (byte) qU8(x[0]);
            mem[o++] = (byte) qU8(x[1]);
            mem[o++] = (byte) (flags & 255);

            putI16(mem, o, qI16(t[0])); o += 2;
            putI16(mem, o, qI16(t[1])); o += 2;

            mem[o++] = (byte) qU8(t[2]);
            mem[o++] = (byte) qU8(t[3]);
            mem[o++] = (byte) qU8(t[4]);

            mem[o++] = (byte) qU8(t[5]);
            mem[o++] = (byte) qU8(t[6]);

            mem[o++] = (byte) qI8(t[7]);
            mem[o++] = (byte) qI8(t[8]);

            mem[o++] = (byte) qU8(t[9]);
            mem[o++] = (byte) qU8(t[10]);

            mem[o++] = (byte) qU8(t[11]);
            mem[o++] = (byte) qU8(t[12]);

            memLen = o;
            if (memCount < Integer.MAX_VALUE - 1) memCount = memCount + 1;
        }

        private void ensureMemCapacity(int add) {
            int need = memLen + add;
            if (mem == null) {
                int cap = 8192;
                while (cap < need) cap <<= 1;
                mem = new byte[cap];
                return;
            }
            if (need <= mem.length) return;
            int cap = mem.length;
            while (cap < need) cap <<= 1;
            byte[] n = new byte[cap];
            System.arraycopy(mem, 0, n, 0, memLen);
            mem = n;
        }

        private static int qU8(float v01) {
            float v = MathHelper.clamp(v01, 0.0f, 1.0f);
            return MathHelper.clamp((int) (v * 255.0f + 0.5f), 0, 255);
        }

        private static short qI16(float vN11) {
            float v = MathHelper.clamp(vN11, -1.0f, 1.0f);
            int q = (int) Math.round(v * 32767.0);
            if (q < -32767) q = -32767;
            if (q > 32767) q = 32767;
            return (short) q;
        }

        private static int qI8(float vN11) {
            float v = MathHelper.clamp(vN11, -1.0f, 1.0f);
            int q = (int) Math.round(v * 127.0);
            if (q < -127) q = -127;
            if (q > 127) q = 127;
            return q & 255;
        }

        private static void putI16(byte[] a, int o, short v) {
            a[o] = (byte) (v & 255);
            a[o + 1] = (byte) ((v >>> 8) & 255);
        }

        private static short getI16(byte[] a, int o) {
            int lo = a[o] & 255;
            int hi = a[o + 1] & 255;
            return (short) ((hi << 8) | lo);
        }

        private static float dqU8(int u) {
            return (u & 255) / 255.0f;
        }

        private static float dqI16(short s) {
            return MathHelper.clamp(s / 32767.0f, -1.0f, 1.0f);
        }

        private static float dqI8(byte b) {
            return MathHelper.clamp(((int) b) / 127.0f, -1.0f, 1.0f);
        }

        private void distillFromMem(int steps) {
            if (mem == null || memLen < SAMPLE_BYTES) return;
            int n = memLen / SAMPLE_BYTES;
            if (n <= 0) return;

            float[] x = net.tmpIn();
            float[] t = net.tmpTarget();

            int it = Math.min(steps, Math.min(n, 1800));
            for (int k = 0; k < it; k++) {
                int idx = ThreadLocalRandom.current().nextInt(n);
                int o = idx * SAMPLE_BYTES;

                float distN = dqU8(mem[o++] & 255);
                float spN = dqU8(mem[o++] & 255);
                int flags = mem[o++] & 255;

                boolean air = (flags & 1) != 0;
                boolean mode = (flags & 2) != 0;
                int side = (flags >>> 2) & 3;
                if (side > 2) side = 1;

                x[0] = distN;
                x[1] = spN;
                x[2] = air ? 1.0f : 0.0f;
                x[3] = mode ? 1.0f : 0.0f;
                x[4] = (side == 0) ? 1.0f : 0.0f;
                x[5] = (side == 1) ? 1.0f : 0.0f;
                x[6] = (side == 2) ? 1.0f : 0.0f;

                short dy = getI16(mem, o); o += 2;
                short dp = getI16(mem, o); o += 2;

                t[0] = dqI16(dy);
                t[1] = dqI16(dp);

                t[2] = dqU8(mem[o++] & 255);
                t[3] = dqU8(mem[o++] & 255);
                t[4] = dqU8(mem[o++] & 255);

                t[5] = dqU8(mem[o++] & 255);
                t[6] = dqU8(mem[o++] & 255);

                t[7] = dqI8(mem[o++]);
                t[8] = dqI8(mem[o++]);

                t[9] = dqU8(mem[o++] & 255);
                t[10] = dqU8(mem[o++] & 255);

                t[11] = dqU8(mem[o++] & 255);
                t[12] = dqU8(mem[o++] & 255);

                net.train(x, t, 0.0019f, 0.00008f);
            }
        }

        public Prediction predict(LivingEntity target) {
            return predict(target, true);
        }

        public Prediction predict(LivingEntity target, boolean attackSample) {
            float[] x = net.tmpIn();
            fillFeatures(x, target, attackSample);

            float[] out = net.forward(x);

            float dyN = MathHelper.clamp(out[0], -1.0f, 1.0f);
            float dpN = MathHelper.clamp(out[1], -1.0f, 1.0f);

            float px = MathHelper.clamp(out[2], 0.0f, 1.0f);
            float py = MathHelper.clamp(out[3], 0.0f, 1.0f);
            float pz = MathHelper.clamp(out[4], 0.0f, 1.0f);

            float ySp = MathHelper.clamp(out[5], 0.0f, 1.0f);
            float pSp = MathHelper.clamp(out[6], 0.0f, 1.0f);

            float yJ = MathHelper.clamp(out[7], -1.0f, 1.0f);
            float pJ = MathHelper.clamp(out[8], -1.0f, 1.0f);

            float gY = MathHelper.clamp(out[9], 0.0f, 1.0f);
            float gP = MathHelper.clamp(out[10], 0.0f, 1.0f);

            float aN = MathHelper.clamp(out[11], 0.0f, 1.0f);
            float errN = MathHelper.clamp(out[12], 0.0f, 1.0f);

            int idx = idxFor(target, attackSample);
            float c = 0.0f;
            if (idx >= 0) c = count[idx];

            float base = 1.0f - errN;
            base = MathHelper.clamp(base, 0.0f, 1.0f);

            float cGate = MathHelper.clamp(c / 34.0f, 0.0f, 1.0f);
            if (net.step() < 24) cGate *= MathHelper.clamp(net.step() / 24.0f, 0.0f, 1.0f);

            float conf = base * base * cGate;
            conf = MathHelper.clamp(conf, 0.0f, 1.0f);

            float pc = base * MathHelper.clamp(c / 50.0f, 0.0f, 1.0f);
            pc = MathHelper.clamp(pc, 0.0f, 1.0f);
            int pointConf = (int) (pc * 100.0f);

            Prediction pr = new Prediction();
            pr.yawDeltaDeg = dyN * AIM_MAX_YAW_DELTA;
            pr.pitchDeltaDeg = dpN * AIM_MAX_PITCH_DELTA;
            pr.confidence = conf;

            pr.px = px;
            pr.py = py;
            pr.pz = pz;
            pr.pointConfidence = pointConf;

            pr.yawSpeed = ySp * 240.0f;
            pr.pitchSpeed = pSp * 240.0f;

            pr.yawJitter = yJ * AIM_MAX_YAW_DELTA;
            pr.pitchJitter = pJ * AIM_MAX_PITCH_DELTA;

            if (gY <= 0.001f) pr.gcdYaw = 0.0f;
            else pr.gcdYaw = MathHelper.clamp(gY * GCD_MAX, GCD_MIN, GCD_MAX);

            if (gP <= 0.001f) pr.gcdPitch = 0.0f;
            else pr.gcdPitch = MathHelper.clamp(gP * GCD_MAX, GCD_MIN, GCD_MAX);

            pr.amp = aN * 50.0f;

            return pr;
        }

        FallbackPrediction fallbackPredict(LivingEntity target, boolean attackSample) {
            MinecraftClient mc = MinecraftClient.getInstance();

            float dist = 3.0f;
            float sp = 0.0f;
            boolean air = false;
            int side = 1;

            if (mc.player != null && target != null) {
                dist = mc.player.distanceTo(target);
                Vec3d v = target.getVelocity();
                sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
                air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
                side = sideBin(v, mc.player.getYaw());
            }

            int ab = air ? 1 : 0;
            int mode = attackSample ? 1 : 0;

            float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
            float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

            float fDb = distN * (DIST_BINS - 1);
            float fSb = speedN * (SPEED_BINS - 1);

            int db0 = MathHelper.clamp((int) Math.floor(fDb), 0, DIST_BINS - 1);
            int sb0 = MathHelper.clamp((int) Math.floor(fSb), 0, SPEED_BINS - 1);

            int db1 = Math.min(DIST_BINS - 1, db0 + 1);
            int sb1 = Math.min(SPEED_BINS - 1, sb0 + 1);

            float dFrac = MathHelper.clamp(fDb - db0, 0.0f, 1.0f);
            float sFrac = MathHelper.clamp(fSb - sb0, 0.0f, 1.0f);

            FbSample p00 = fbSample(db0, sb0, ab, mode, side);
            FbSample p10 = fbSample(db1, sb0, ab, mode, side);
            FbSample p01 = fbSample(db0, sb1, ab, mode, side);
            FbSample p11 = fbSample(db1, sb1, ab, mode, side);

            float w00 = (1.0f - dFrac) * (1.0f - sFrac);
            float w10 = dFrac * (1.0f - sFrac);
            float w01 = (1.0f - dFrac) * sFrac;
            float w11 = dFrac * sFrac;

            float yaw = p00.yaw * w00 + p10.yaw * w10 + p01.yaw * w01 + p11.yaw * w11;
            float pitch = p00.pitch * w00 + p10.pitch * w10 + p01.pitch * w01 + p11.pitch * w11;

            float err = p00.err * w00 + p10.err * w10 + p01.err * w01 + p11.err * w11;
            float c = p00.c * w00 + p10.c * w10 + p01.c * w01 + p11.c * w11;

            float conf = MathHelper.clamp((float) Math.exp(-err * 0.10f) * MathHelper.clamp(c / 18.0f, 0.0f, 1.0f), 0.0f, 1.0f);
            if (c < 1.0f) conf = 0.0f;

            FallbackPrediction out = new FallbackPrediction();
            out.yawDeltaDeg = yaw;
            out.pitchDeltaDeg = pitch;
            out.confidence = conf;
            return out;
        }

        Vec3d pointFromPrediction(LivingEntity target, Prediction pr) {
            if (target == null || pr == null) return null;
            Box hb = target.getBoundingBox();
            double x = hb.minX + (hb.maxX - hb.minX) * pr.px;
            double y = hb.minY + (hb.maxY - hb.minY) * pr.py;
            double z = hb.minZ + (hb.maxZ - hb.minZ) * pr.pz;
            return new Vec3d(x, y, z);
        }

        AngleDeg angleDegFromTurns(Turns turns) {
            AngleDeg a = new AngleDeg();
            if (turns == null) {
                a.yaw = 0.0f;
                a.pitch = 0.0f;
                return a;
            }
            Vec3d v = turns.toVector();
            double x = v.x;
            double y = v.y;
            double z = v.z;

            float yaw = (float) (Math.toDegrees(Math.atan2(z, x)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(y, Math.sqrt(x * x + z * z))));

            a.yaw = yaw;
            a.pitch = pitch;
            return a;
        }

        Turns turnsFromAngleDeg(float yaw, float pitch) {
            return new Turns(yaw, pitch);
        }

        private FbSample fbSample(int d, int s, int ab, int mb, int side) {
            int idx = idxBins(d, s, ab, mb, side);

            FbSample out = new FbSample();
            out.yaw = fbYawAvg[idx];
            out.pitch = fbPitchAvg[idx];
            out.err = fbErrEma[idx];
            out.c = fbCount[idx];
            return out;
        }

        void load() throws Exception {
            MinecraftClient mc = MinecraftClient.getInstance();
            File f = filePath(mc).toFile();
            if (!f.exists()) return;

            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);

            int v = findJsonIntValue(json, "v", 0);

            String b64 = findJsonStringValue(json, "b64");
            if (b64 == null) b64 = findJsonStringValue(json, "data");
            if (b64 == null) b64 = findJsonStringValue(json, "blob");
            if (b64 == null || b64.isEmpty()) return;

            byte[] data;
            try {
                data = Base64.getDecoder().decode(b64);
            } catch (Exception ignored) {
                return;
            }

            if (data.length < 16) return;

            ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

            if (v >= 7) {
                int magic = bb.getInt();
                int ver = bb.getInt();
                if (magic != MAGIC || (ver != 7 && ver != STORE_VER)) {
                    loadLegacy(bb);
                    return;
                }

                net.read(bb);

                lossEma = (bb.remaining() >= 4) ? bb.getFloat() : 0.0f;

                readIntArray(bb, count);

                readFloatArray(bb, fbYawAvg);
                readFloatArray(bb, fbPitchAvg);
                readFloatArray(bb, fbErrEma);
                readIntArray(bb, fbCount);

                mem = null;
                memLen = 0;
                memCount = 0;

                if (ver >= STORE_VER && bb.remaining() >= 8) {
                    int mCount = bb.getInt();
                    int mLen = bb.getInt();
                    if (mLen > 0 && mLen <= bb.remaining() && (mLen % SAMPLE_BYTES) == 0) {
                        mem = new byte[mLen];
                        bb.get(mem);
                        memLen = mLen;
                        memCount = Math.max(0, mCount);
                        distillFromMem(520);
                    }
                }

                return;
            }

            loadLegacy(bb);
        }

        private void loadLegacy(ByteBuffer bb) {
            float[] yawDeltaAvg = new float[SIZE];
            float[] pitchDeltaAvg = new float[SIZE];
            float[] errEma = new float[SIZE];
            int[] cnt = new int[SIZE];

            float[] pXAvg = new float[SIZE];
            float[] pYAvg = new float[SIZE];
            float[] pZAvg = new float[SIZE];
            float[] pErrEma = new float[SIZE];

            float[] yawSpeedAvg = new float[SIZE];
            float[] pitchSpeedAvg = new float[SIZE];
            float[] yawJitterAvg = new float[SIZE];
            float[] pitchJitterAvg = new float[SIZE];
            float[] gcdYawAvg = new float[SIZE];
            float[] gcdPitchAvg = new float[SIZE];
            float[] ampAvg = new float[SIZE];

            readFloatArray(bb, yawDeltaAvg);
            readFloatArray(bb, pitchDeltaAvg);
            readFloatArray(bb, errEma);
            readIntArray(bb, cnt);

            readFloatArray(bb, pXAvg);
            readFloatArray(bb, pYAvg);
            readFloatArray(bb, pZAvg);
            readFloatArray(bb, pErrEma);

            if (bb.remaining() >= SIZE * 7 * 4) {
                readFloatArray(bb, yawSpeedAvg);
                readFloatArray(bb, pitchSpeedAvg);
                readFloatArray(bb, yawJitterAvg);
                readFloatArray(bb, pitchJitterAvg);
                readFloatArray(bb, gcdYawAvg);
                readFloatArray(bb, gcdPitchAvg);
                readFloatArray(bb, ampAvg);
            }

            if (bb.remaining() >= SIZE * 7 * 4) {
                float[] skip = new float[SIZE * 7];
                readFloatArray(bb, skip);
            }

            if (bb.remaining() >= SIZE * 4 * 4) {
                float[] skip2 = new float[SIZE * 4];
                readFloatArray(bb, skip2);
            }

            if (bb.remaining() >= fbYawAvg.length * 4 + fbPitchAvg.length * 4 + fbErrEma.length * 4 + fbCount.length * 4) {
                readFloatArray(bb, fbYawAvg);
                readFloatArray(bb, fbPitchAvg);
                readFloatArray(bb, fbErrEma);
                readIntArray(bb, fbCount);
            } else {
                for (int i = 0; i < SIZE; i++) {
                    fbYawAvg[i] = yawDeltaAvg[i];
                    fbPitchAvg[i] = pitchDeltaAvg[i];
                    fbErrEma[i] = errEma[i];
                    fbCount[i] = Math.min(cnt[i], 160);
                }
            }

            for (int i = 0; i < SIZE; i++) count[i] = cnt[i];

            distillFromLegacy(yawDeltaAvg, pitchDeltaAvg, pXAvg, pYAvg, pZAvg, yawSpeedAvg, pitchSpeedAvg, yawJitterAvg, pitchJitterAvg, gcdYawAvg, gcdPitchAvg, ampAvg, cnt);

            mem = null;
            memLen = 0;
            memCount = 0;
        }

        private void distillFromLegacy(float[] yawDeltaAvg,
                                       float[] pitchDeltaAvg,
                                       float[] pXAvg,
                                       float[] pYAvg,
                                       float[] pZAvg,
                                       float[] yawSpeedAvg,
                                       float[] pitchSpeedAvg,
                                       float[] yawJitterAvg,
                                       float[] pitchJitterAvg,
                                       float[] gcdYawAvg,
                                       float[] gcdPitchAvg,
                                       float[] ampAvg,
                                       int[] cnt) {
            int steps = 900;
            for (int it = 0; it < steps; it++) {
                int idx = ThreadLocalRandom.current().nextInt(SIZE);

                int w = cnt[idx];
                if (w <= 0 && ThreadLocalRandom.current().nextFloat() < 0.70f) continue;

                int tmp = idx;

                int side = tmp % SIDE_BINS;
                tmp /= SIDE_BINS;

                int mode = tmp % MODE_BINS;
                tmp /= MODE_BINS;

                int ab = tmp % AIR_BINS;
                tmp /= AIR_BINS;

                int sb = tmp % SPEED_BINS;
                tmp /= SPEED_BINS;

                int db = tmp % DIST_BINS;

                float distN = (db + 0.5f) / (float) (DIST_BINS - 1);
                float speedN = (sb + 0.5f) / (float) (SPEED_BINS - 1);

                float[] x = net.tmpIn();
                x[0] = MathHelper.clamp(distN, 0.0f, 1.0f);
                x[1] = MathHelper.clamp(speedN, 0.0f, 1.0f);
                x[2] = (ab == 1) ? 1.0f : 0.0f;
                x[3] = (mode == 1) ? 1.0f : 0.0f;
                x[4] = (side == 0) ? 1.0f : 0.0f;
                x[5] = (side == 1) ? 1.0f : 0.0f;
                x[6] = (side == 2) ? 1.0f : 0.0f;

                float dy = MathHelper.clamp(yawDeltaAvg[idx], -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
                float dp = MathHelper.clamp(pitchDeltaAvg[idx], -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

                float px = pXAvg[idx];
                float py = pYAvg[idx];
                float pz = pZAvg[idx];

                if (px == 0.0f) px = 0.5f;
                if (py == 0.0f) py = 0.5f;
                if (pz == 0.0f) pz = 0.5f;

                float ySp = yawSpeedAvg[idx];
                float pSp = pitchSpeedAvg[idx];

                float yJ = yawJitterAvg[idx];
                float pJ = pitchJitterAvg[idx];

                float gY = gcdYawAvg[idx];
                float gP = gcdPitchAvg[idx];

                float amp = ampAvg[idx];

                float err = (Math.abs(dy) + Math.abs(dp)) * 0.5f;

                float[] t = net.tmpTarget();
                t[0] = MathHelper.clamp(dy / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
                t[1] = MathHelper.clamp(dp / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);
                t[2] = MathHelper.clamp(px, 0.0f, 1.0f);
                t[3] = MathHelper.clamp(py, 0.0f, 1.0f);
                t[4] = MathHelper.clamp(pz, 0.0f, 1.0f);
                t[5] = MathHelper.clamp(Math.abs(ySp) / 240.0f, 0.0f, 1.0f);
                t[6] = MathHelper.clamp(Math.abs(pSp) / 240.0f, 0.0f, 1.0f);
                t[7] = MathHelper.clamp(yJ / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
                t[8] = MathHelper.clamp(pJ / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);
                t[9] = (gY == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gY) / GCD_MAX, 0.0f, 1.0f);
                t[10] = (gP == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gP) / GCD_MAX, 0.0f, 1.0f);
                t[11] = MathHelper.clamp(amp / 50.0f, 0.0f, 1.0f);
                t[12] = MathHelper.clamp(err / 20.0f, 0.0f, 1.0f);

                float lr = 0.0038f;
                if (w > 0) lr *= MathHelper.clamp((float) Math.sqrt(Math.min(w, 3000)) / 20.0f, 0.55f, 1.35f);

                net.train(x, t, lr, 0.00010f);
            }
        }

        void save() throws Exception {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            int memBytes = (mem != null && memLen > 0) ? memLen : 0;

            int bytes =
                    8
                            + net.bytes()
                            + 4
                            + count.length * 4
                            + (fbYawAvg.length + fbPitchAvg.length + fbErrEma.length) * 4
                            + fbCount.length * 4
                            + 8
                            + memBytes;

            ByteBuffer bb = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);

            bb.putInt(MAGIC);
            bb.putInt(STORE_VER);

            net.write(bb);

            bb.putFloat(lossEma);

            writeIntArray(bb, count);

            writeFloatArray(bb, fbYawAvg);
            writeFloatArray(bb, fbPitchAvg);
            writeFloatArray(bb, fbErrEma);
            writeIntArray(bb, fbCount);

            bb.putInt(memCount);
            bb.putInt(memBytes);
            if (memBytes > 0) bb.put(mem, 0, memBytes);

            byte[] data = new byte[bb.position()];
            bb.rewind();
            bb.get(data);

            String b64 = Base64.getEncoder().encodeToString(data);
            String json = "{\"v\":" + STORE_VER + ",\"b64\":\"" + b64 + "\"}";

            File f = filePath(mc).toFile();
            Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
        }

        private static void readFloatArray(ByteBuffer bb, float[] arr) {
            for (int i = 0; i < arr.length; i++) {
                if (bb.remaining() < 4) return;
                arr[i] = bb.getFloat();
            }
        }

        private static void readIntArray(ByteBuffer bb, int[] arr) {
            for (int i = 0; i < arr.length; i++) {
                if (bb.remaining() < 4) return;
                arr[i] = bb.getInt();
            }
        }

        private static void writeFloatArray(ByteBuffer bb, float[] arr) {
            for (float v : arr) bb.putFloat(v);
        }

        private static void writeIntArray(ByteBuffer bb, int[] arr) {
            for (int v : arr) bb.putInt(v);
        }

        private static String findJsonStringValue(String json, String key) {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) return null;
            int colon = json.indexOf(":", k);
            if (colon < 0) return null;
            int q1 = json.indexOf("\"", colon + 1);
            if (q1 < 0) return null;
            int q2 = json.indexOf("\"", q1 + 1);
            if (q2 < 0) return null;
            String s = json.substring(q1 + 1, q2).trim();
            return s.isEmpty() ? null : s;
        }

        private static int findJsonIntValue(String json, String key, int def) {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) return def;
            int colon = json.indexOf(":", k);
            if (colon < 0) return def;
            int i = colon + 1;
            while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) i++;
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
            try {
                return Integer.parseInt(json.substring(s, i));
            } catch (Exception ignored) {
            }
            return def;
        }

        static final class FbSample {
            float yaw;
            float pitch;
            float err;
            float c;
        }

        static final class AngleDeg {
            float yaw;
            float pitch;
        }

        public static final class Prediction {
            float yawDeltaDeg;
            float pitchDeltaDeg;
            float confidence;

            float px;
            float py;
            float pz;

            int pointConfidence;

            float yawSpeed;
            float pitchSpeed;
            float yawJitter;
            float pitchJitter;
            float gcdYaw;
            float gcdPitch;
            float amp;
        }

        static final class FallbackPrediction {
            float yawDeltaDeg;
            float pitchDeltaDeg;
            float confidence;
        }

        static final class NeuralNet {
            static final float B1 = 0.90f;
            static final float B2 = 0.999f;
            static final float EPS = 1.0e-8f;

            final int in, h1, h2, out;

            final float[] w1;
            final float[] b1;
            final float[] w2;
            final float[] b2;
            final float[] w3;
            final float[] b3;

            final float[] mw1;
            final float[] vw1;
            final float[] mb1;
            final float[] vb1;

            final float[] mw2;
            final float[] vw2;
            final float[] mb2;
            final float[] vb2;

            final float[] mw3;
            final float[] vw3;
            final float[] mb3;
            final float[] vb3;

            int step = 0;
            float b1Pow = 1.0f;
            float b2Pow = 1.0f;

            final float[] z1;
            final float[] a1;
            final float[] z2;
            final float[] a2;
            final float[] z3;
            final float[] outAct;

            final float[] inTmp;
            final float[] tTmp;

            final float[] dz3;
            final float[] da2;
            final float[] dz2;
            final float[] da1;
            final float[] dz1;

            NeuralNet(int in, int h1, int h2, int out) {
                this.in = in;
                this.h1 = h1;
                this.h2 = h2;
                this.out = out;

                this.w1 = new float[h1 * in];
                this.b1 = new float[h1];
                this.w2 = new float[h2 * h1];
                this.b2 = new float[h2];
                this.w3 = new float[out * h2];
                this.b3 = new float[out];

                this.mw1 = new float[w1.length];
                this.vw1 = new float[w1.length];
                this.mb1 = new float[b1.length];
                this.vb1 = new float[b1.length];

                this.mw2 = new float[w2.length];
                this.vw2 = new float[w2.length];
                this.mb2 = new float[b2.length];
                this.vb2 = new float[b2.length];

                this.mw3 = new float[w3.length];
                this.vw3 = new float[w3.length];
                this.mb3 = new float[b3.length];
                this.vb3 = new float[b3.length];

                this.z1 = new float[h1];
                this.a1 = new float[h1];
                this.z2 = new float[h2];
                this.a2 = new float[h2];
                this.z3 = new float[out];
                this.outAct = new float[out];

                this.inTmp = new float[in];
                this.tTmp = new float[out];

                this.dz3 = new float[out];
                this.da2 = new float[h2];
                this.dz2 = new float[h2];
                this.da1 = new float[h1];
                this.dz1 = new float[h1];

                init();
            }

            int step() {
                return step;
            }

            float[] tmpIn() {
                return inTmp;
            }

            float[] tmpTarget() {
                return tTmp;
            }

            void init() {
                Random r = new Random(System.nanoTime() ^ 0x5F3759DF);
                initMat(w1, in, h1, r);
                initMat(w2, h1, h2, r);
                initMat(w3, h2, out, r);
                for (int i = 0; i < b1.length; i++) b1[i] = 0.0f;
                for (int i = 0; i < b2.length; i++) b2[i] = 0.0f;
                for (int i = 0; i < b3.length; i++) b3[i] = 0.0f;
                step = 0;
                b1Pow = 1.0f;
                b2Pow = 1.0f;
                clearOpt();
            }

            void clearOpt() {
                for (int i = 0; i < mw1.length; i++) {
                    mw1[i] = 0.0f;
                    vw1[i] = 0.0f;
                }
                for (int i = 0; i < mb1.length; i++) {
                    mb1[i] = 0.0f;
                    vb1[i] = 0.0f;
                }
                for (int i = 0; i < mw2.length; i++) {
                    mw2[i] = 0.0f;
                    vw2[i] = 0.0f;
                }
                for (int i = 0; i < mb2.length; i++) {
                    mb2[i] = 0.0f;
                    vb2[i] = 0.0f;
                }
                for (int i = 0; i < mw3.length; i++) {
                    mw3[i] = 0.0f;
                    vw3[i] = 0.0f;
                }
                for (int i = 0; i < mb3.length; i++) {
                    mb3[i] = 0.0f;
                    vb3[i] = 0.0f;
                }
            }

            static void initMat(float[] w, int fanIn, int fanOut, Random r) {
                float scale = (float) Math.sqrt(2.0 / Math.max(1, fanIn));
                for (int i = 0; i < w.length; i++) {
                    float u = (r.nextFloat() * 2.0f - 1.0f);
                    w[i] = u * scale;
                }
            }

            float[] forward(float[] x) {
                for (int i = 0; i < h1; i++) {
                    float s = b1[i];
                    int row = i * in;
                    for (int j = 0; j < in; j++) s += w1[row + j] * x[j];
                    z1[i] = s;
                    a1[i] = (s > 0.0f) ? s : 0.0f;
                }

                for (int i = 0; i < h2; i++) {
                    float s = b2[i];
                    int row = i * h1;
                    for (int j = 0; j < h1; j++) s += w2[row + j] * a1[j];
                    z2[i] = s;
                    a2[i] = (s > 0.0f) ? s : 0.0f;
                }

                for (int i = 0; i < out; i++) {
                    float s = b3[i];
                    int row = i * h2;
                    for (int j = 0; j < h2; j++) s += w3[row + j] * a2[j];
                    z3[i] = s;
                }

                outAct[0] = tanh(z3[0]);
                outAct[1] = tanh(z3[1]);

                outAct[2] = sigmoid(z3[2]);
                outAct[3] = sigmoid(z3[3]);
                outAct[4] = sigmoid(z3[4]);

                outAct[5] = sigmoid(z3[5]);
                outAct[6] = sigmoid(z3[6]);

                outAct[7] = tanh(z3[7]);
                outAct[8] = tanh(z3[8]);

                outAct[9] = sigmoid(z3[9]);
                outAct[10] = sigmoid(z3[10]);

                outAct[11] = sigmoid(z3[11]);
                outAct[12] = sigmoid(z3[12]);

                return outAct;
            }

            float train(float[] x, float[] t, float lr, float l2) {
                forward(x);

                for (int i = 0; i < out; i++) dz3[i] = 0.0f;

                sqGradTanh(0, t[0], 1.00f);
                sqGradTanh(1, t[1], 1.00f);

                sqGradSig(2, t[2], 0.80f);
                sqGradSig(3, t[3], 0.80f);
                sqGradSig(4, t[4], 0.80f);

                sqGradSig(5, t[5], 0.15f);
                sqGradSig(6, t[6], 0.15f);

                sqGradTanh(7, t[7], 0.22f);
                sqGradTanh(8, t[8], 0.22f);

                sqGradSig(9, t[9], 0.03f);
                sqGradSig(10, t[10], 0.03f);

                sqGradSig(11, t[11], 0.18f);
                sqGradSig(12, t[12], 0.35f);

                for (int j = 0; j < h2; j++) da2[j] = 0.0f;
                for (int i = 0; i < out; i++) {
                    int row = i * h2;
                    float g = dz3[i];
                    for (int j = 0; j < h2; j++) da2[j] += w3[row + j] * g;
                }

                for (int j = 0; j < h2; j++) dz2[j] = (z2[j] > 0.0f) ? da2[j] : 0.0f;

                for (int j = 0; j < h1; j++) da1[j] = 0.0f;
                for (int i = 0; i < h2; i++) {
                    int row = i * h1;
                    float g = dz2[i];
                    for (int j = 0; j < h1; j++) da1[j] += w2[row + j] * g;
                }

                for (int j = 0; j < h1; j++) dz1[j] = (z1[j] > 0.0f) ? da1[j] : 0.0f;

                step++;
                b1Pow *= B1;
                b2Pow *= B2;

                float corr1 = 1.0f - b1Pow;
                float corr2 = 1.0f - b2Pow;
                if (corr1 < 1.0e-6f) corr1 = 1.0e-6f;
                if (corr2 < 1.0e-6f) corr2 = 1.0e-6f;

                for (int i = 0; i < out; i++) {
                    int row = i * h2;
                    float gb = dz3[i];
                    updateAdamScalar(b3, mb3, vb3, i, gb, lr, l2, corr1, corr2);

                    for (int j = 0; j < h2; j++) {
                        float g = dz3[i] * a2[j];
                        int wi = row + j;
                        updateAdamScalar(w3, mw3, vw3, wi, g, lr, l2, corr1, corr2);
                    }
                }

                for (int i = 0; i < h2; i++) {
                    int row = i * h1;
                    float gb = dz2[i];
                    updateAdamScalar(b2, mb2, vb2, i, gb, lr, l2, corr1, corr2);

                    for (int j = 0; j < h1; j++) {
                        float g = dz2[i] * a1[j];
                        int wi = row + j;
                        updateAdamScalar(w2, mw2, vw2, wi, g, lr, l2, corr1, corr2);
                    }
                }

                for (int i = 0; i < h1; i++) {
                    int row = i * in;
                    float gb = dz1[i];
                    updateAdamScalar(b1, mb1, vb1, i, gb, lr, l2, corr1, corr2);

                    for (int j = 0; j < in; j++) {
                        float g = dz1[i] * x[j];
                        int wi = row + j;
                        updateAdamScalar(w1, mw1, vw1, wi, g, lr, l2, corr1, corr2);
                    }
                }

                return 0.0f;
            }

            void sqGradTanh(int idx, float target, float w) {
                float y = outAct[idx];
                float e = (y - target);
                float we = e * w;
                float d = (1.0f - y * y);
                dz3[idx] = we * d;
            }

            void sqGradSig(int idx, float target, float w) {
                float y = outAct[idx];
                float e = (y - target);
                float we = e * w;
                float d = y * (1.0f - y);
                dz3[idx] = we * d;
            }

            static void updateAdamScalar(float[] p, float[] m, float[] v, int i, float g, float lr, float l2, float corr1, float corr2) {
                float pi = p[i];
                float grad = g + l2 * pi;

                float mi = m[i] = B1 * m[i] + (1.0f - B1) * grad;
                float vi = v[i] = B2 * v[i] + (1.0f - B2) * grad * grad;

                float mHat = mi / corr1;
                float vHat = vi / corr2;

                float upd = lr * (mHat / ((float) Math.sqrt(vHat) + EPS));
                p[i] = pi - upd;
            }

            static float tanh(float x) {
                if (x > 6.0f) return 1.0f;
                if (x < -6.0f) return -1.0f;
                return (float) Math.tanh(x);
            }

            static float sigmoid(float x) {
                if (x > 12.0f) return 0.999994f;
                if (x < -12.0f) return 0.000006f;
                return 1.0f / (1.0f + (float) Math.exp(-x));
            }

            int bytes() {
                int f = 4 * 4;
                int w = (w1.length + b1.length + w2.length + b2.length + w3.length + b3.length) * 4;
                int m = (mw1.length + vw1.length + mb1.length + vb1.length
                        + mw2.length + vw2.length + mb2.length + vb2.length
                        + mw3.length + vw3.length + mb3.length + vb3.length) * 4;
                return f + w + m;
            }

            void write(ByteBuffer bb) {
                bb.putInt(step);
                bb.putFloat(b1Pow);
                bb.putFloat(b2Pow);

                writeFloatArray(bb, w1);
                writeFloatArray(bb, b1);
                writeFloatArray(bb, w2);
                writeFloatArray(bb, b2);
                writeFloatArray(bb, w3);
                writeFloatArray(bb, b3);

                writeFloatArray(bb, mw1);
                writeFloatArray(bb, vw1);
                writeFloatArray(bb, mb1);
                writeFloatArray(bb, vb1);

                writeFloatArray(bb, mw2);
                writeFloatArray(bb, vw2);
                writeFloatArray(bb, mb2);
                writeFloatArray(bb, vb2);

                writeFloatArray(bb, mw3);
                writeFloatArray(bb, vw3);
                writeFloatArray(bb, mb3);
                writeFloatArray(bb, vb3);
            }

            void read(ByteBuffer bb) {
                if (bb.remaining() < 4) return;
                step = bb.getInt();
                if (bb.remaining() >= 8) {
                    b1Pow = bb.getFloat();
                    b2Pow = bb.getFloat();
                } else {
                    b1Pow = 1.0f;
                    b2Pow = 1.0f;
                }

                readFloatArray(bb, w1);
                readFloatArray(bb, b1);
                readFloatArray(bb, w2);
                readFloatArray(bb, b2);
                readFloatArray(bb, w3);
                readFloatArray(bb, b3);

                readFloatArray(bb, mw1);
                readFloatArray(bb, vw1);
                readFloatArray(bb, mb1);
                readFloatArray(bb, vb1);

                readFloatArray(bb, mw2);
                readFloatArray(bb, vw2);
                readFloatArray(bb, mb2);
                readFloatArray(bb, vb2);

                readFloatArray(bb, mw3);
                readFloatArray(bb, vw3);
                readFloatArray(bb, mb3);
                readFloatArray(bb, vb3);
            }

            private static void readFloatArray(ByteBuffer bb, float[] arr) {
                for (int i = 0; i < arr.length; i++) {
                    if (bb.remaining() < 4) return;
                    arr[i] = bb.getFloat();
                }
            }

            private static void writeFloatArray(ByteBuffer bb, float[] arr) {
                for (float v : arr) bb.putFloat(v);
            }
        }
    }

    static final class PerlinNoise {
        private final int[] p = new int[512];

        PerlinNoise(int seed) {
            int[] perm = new int[256];
            for (int i = 0; i < 256; i++) perm[i] = i;

            Random r = new Random(seed);
            for (int i = 255; i > 0; i--) {
                int j = r.nextInt(i + 1);
                int t = perm[i];
                perm[i] = perm[j];
                perm[j] = t;
            }

            for (int i = 0; i < 256; i++) {
                int v = perm[i] & 255;
                p[i] = v;
                p[i + 256] = v;
            }
        }

        private static float fade(float t) {
            return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
        }

        private static float lerp(float t, float a, float b) {
            return a + t * (b - a);
        }

        private static float grad(int hash, float x, float y, float z) {
            int h = hash & 15;
            float u = h < 8 ? x : y;
            float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }

        float noise(float x, float y) {
            float z = 0.0f;

            int X = ((int) Math.floor(x)) & 255;
            int Y = ((int) Math.floor(y)) & 255;
            int Z = ((int) Math.floor(z)) & 255;

            x = (float) (x - Math.floor(x));
            y = (float) (y - Math.floor(y));
            z = (float) (z - Math.floor(z));

            float u = fade(x);
            float v = fade(y);
            float w = fade(z);

            int A = p[X] + Y;
            int AA = p[A] + Z;
            int AB = p[A + 1] + Z;
            int B = p[X + 1] + Y;
            int BA = p[B] + Z;
            int BB = p[B + 1] + Z;

            return lerp(w,
                    lerp(v,
                            lerp(u, grad(p[AA], x, y, z), grad(p[BA], x - 1.0f, y, z)),
                            lerp(u, grad(p[AB], x, y - 1.0f, z), grad(p[BB], x - 1.0f, y - 1.0f, z))
                    ),
                    lerp(v,
                            lerp(u, grad(p[AA + 1], x, y, z - 1.0f), grad(p[BA + 1], x - 1.0f, y, z - 1.0f)),
                            lerp(u, grad(p[AB + 1], x, y - 1.0f, z - 1.0f), grad(p[BB + 1], x - 1.0f, y - 1.0f, z - 1.0f))
                    )
            );
        }

        float fbm(float x, float y, int octaves, float lacunarity, float gain) {
            float sum = 0.0f;
            float amp = 0.5f;
            float fx = x;
            float fy = y;
            for (int i = 0; i < octaves; i++) {
                sum += noise(fx, fy) * amp;
                fx *= lacunarity;
                fy *= lacunarity;
                amp *= gain;
            }
            return sum;
        }
    }
}