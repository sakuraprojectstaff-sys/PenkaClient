package fun.rich.features.impl.render;

import fun.rich.events.player.TickEvent;
import fun.rich.events.render.CameraEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.features.aura.warp.Turns;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class Beautifully extends Module {

    public static Beautifully getInstance() {
        return Instance.get(Beautifully.class);
    }

    final BooleanSetting customF5 = new BooleanSetting("Кастомный F5", "Плавные смены режима F5").setValue(true);

    final BooleanSetting smoothTurn = new BooleanSetting("Поворот F5", "Плавный переход Back/Front").setValue(true)
            .visible(customF5::isValue);

    final SliderSettings distance = new SliderSettings("Дистанция", "Дистанция камеры (F5)")
            .setValue(4f).range(-3f, 6f)
            .visible(customF5::isValue);

    final SliderSettings timeMs = new SliderSettings("Время", "Время сглаживания (мс)")
            .setValue(450f).range(50f, 2000f)
            .visible(customF5::isValue);

    final BooleanSetting effectual = new BooleanSetting("Effectual", "Доп. частицы (фонари/костры/огонь/дыхание/и т.д.)").setValue(true);

    Perspective prevPerspective;
    Perspective keepThird = Perspective.THIRD_PERSON_BACK;
    boolean pendingToFirst;

    float smoothDistance = Float.NaN;
    long lastDistMs;

    float f5T = Float.NaN;
    long lastTurnMs;

    final Random rnd = new Random();

    long fxLastMs;
    int fxCleanupT;

    final Map<Integer, Integer> breathT = new HashMap<>();
    final Map<Integer, Integer> breathS = new HashMap<>();

    final Map<Integer, Integer> lastAir = new HashMap<>();
    final Map<Integer, Integer> bubbleT = new HashMap<>();

    final Map<Integer, Long> lastSub = new HashMap<>();
    final Map<Integer, Integer> lastWetT = new HashMap<>();

    final Map<Integer, Integer> witherT = new HashMap<>();
    final Map<Integer, Integer> stepT = new HashMap<>();

    int caveCheckT;
    boolean inCave;

    int elytraT;
    int ambientScanT;
    int burnSparkT;
    int steamT;

    public Beautifully() {
        super("Beautifully", "Beautifully", ModuleCategory.RENDER);
        setup(customF5, smoothTurn, distance, timeMs, effectual);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        tickEffectualHook();

        if (!enabledPublic() || !customF5.isValue() || mc == null || mc.options == null) {
            if (pendingToFirst && mc != null && mc.options != null) {
                mc.options.setPerspective(Perspective.FIRST_PERSON);
            }
            pendingToFirst = false;
            prevPerspective = null;
            keepThird = Perspective.THIRD_PERSON_BACK;
            smoothDistance = Float.NaN;
            lastDistMs = 0L;
            f5T = Float.NaN;
            lastTurnMs = 0L;
            return;
        }

        Perspective p = mc.options.getPerspective();
        if (p == null) return;

        if (prevPerspective == null) prevPerspective = p;

        if (!pendingToFirst && p.isFirstPerson() && !prevPerspective.isFirstPerson()) {
            pendingToFirst = true;
            keepThird = prevPerspective;
            mc.options.setPerspective(keepThird);
            p = keepThird;
        }

        if (!p.isFirstPerson()) {
            keepThird = p;
        }

        if (prevPerspective.isFirstPerson() && !p.isFirstPerson()) {
            smoothDistance = 0f;
            f5T = 0f;
            lastDistMs = 0L;
            lastTurnMs = 0L;
        }

        prevPerspective = p;
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        tickEffectualHook();

        if (!enabledPublic() || !customF5.isValue() || mc == null || mc.options == null) return;

        Perspective p = mc.options.getPerspective();
        if (p == null) return;

        boolean third = !p.isFirstPerson();
        boolean inverse = p == Perspective.THIRD_PERSON_FRONT;

        long now = System.currentTimeMillis();
        float tMs = timeMs.getValue();
        if (tMs < 1f) tMs = 1f;

        float targetDist = third ? distance.getValue() : 0f;
        float targetT = inverse ? 1f : 0f;

        if (pendingToFirst) {
            targetDist = 0f;
            targetT = 0f;
        }

        if (Float.isNaN(smoothDistance)) smoothDistance = targetDist;
        if (lastDistMs == 0L) lastDistMs = now;
        float aD = (float) (now - lastDistMs) / tMs;
        lastDistMs = now;
        if (aD < 0f) aD = 0f;
        if (aD > 1f) aD = 1f;
        smoothDistance = smoothDistance + (targetDist - smoothDistance) * aD;

        if (Float.isNaN(f5T)) f5T = targetT;
        if (smoothTurn.isValue() || pendingToFirst) {
            if (lastTurnMs == 0L) lastTurnMs = now;
            float aT = (float) (now - lastTurnMs) / tMs;
            lastTurnMs = now;
            if (aT < 0f) aT = 0f;
            if (aT > 1f) aT = 1f;
            f5T = f5T + (targetT - f5T) * aT;
            if (f5T < 0f) f5T = 0f;
            if (f5T > 1f) f5T = 1f;
        } else {
            f5T = targetT;
        }

        if (!third) return;

        Turns base = e.getAngle();
        if (base != null && (smoothTurn.isValue() || pendingToFirst)) {
            float baseYaw = base.getYaw();
            float basePitch = base.getPitch();

            float effYaw = baseYaw + 180f * f5T;
            float effPitch = basePitch * (1f - 2f * f5T);

            float outYaw;
            float outPitch;

            if (!inverse) {
                outYaw = effYaw;
                outPitch = effPitch;
            } else {
                outYaw = effYaw + 180f;
                outPitch = -effPitch;
            }

            base.setYaw(outYaw);
            base.setPitch(outPitch);
        }

        e.setCameraClip(false);
        e.setDistance(smoothDistance);
        e.cancel();

        if (pendingToFirst) {
            if (Math.abs(smoothDistance) <= 0.02f && f5T <= 0.02f) {
                pendingToFirst = false;
                mc.options.setPerspective(Perspective.FIRST_PERSON);
                smoothDistance = 0f;
                f5T = 0f;
                lastDistMs = now;
                lastTurnMs = now;
                prevPerspective = Perspective.FIRST_PERSON;
            }
        }
    }

    private void tickEffectualHook() {
        if (!effectual.isValue()) return;
        if (mc == null || mc.world == null || mc.player == null) return;
        if (isPausedSafe()) return;

        long now = System.currentTimeMillis();
        if (fxLastMs == 0L) fxLastMs = now;
        if (now - fxLastMs < 50L) return;
        fxLastMs = now;

        fxCleanupT++;
        if (fxCleanupT >= 200) {
            fxCleanupT = 0;
            cleanupMaps();
        }

        tickColdBreath();
        tickUnderwaterBreath();
        tickWaterDrips();
        tickStepEffects();
        tickCaveDust();
        tickElytraTrail();
        tickWitherSmoke();
        tickBurningSparks();
        tickAmbientLightsAndFire();
        tickSteamColumns();
    }

    private void tickColdBreath() {
        if (mc.world == null) return;

        for (PlayerEntity p : mc.world.getPlayers()) {
            int id = p.getId();

            if (!shouldColdBreath(p)) {
                breathT.remove(id);
                breathS.remove(id);
                continue;
            }

            int st = movementState(p);
            int prev = breathS.getOrDefault(id, st);

            int t = breathT.getOrDefault(id, 0);
            if (st != prev) t = 0;
            t++;

            int freq;
            if (st == 2) freq = 18 + rnd.nextInt(10);
            else if (st == 1) freq = 34 + rnd.nextInt(12);
            else if (st == 3) freq = 22 + rnd.nextInt(10);
            else freq = 55 + rnd.nextInt(20);

            if (t >= freq) {
                spawnBreath(p, st);
                t = 0;
            }

            breathT.put(id, t);
            breathS.put(id, st);
        }
    }

    private boolean shouldColdBreath(PlayerEntity p) {
        if (p == null || mc.world == null) return false;
        if (isSpectator(p) || isCreative(p)) return false;
        if (isUnderWater(p)) return false;
        if (p.isOnFire()) return false;
        float temp = biomeBaseTemp(p.getBlockPos());
        return temp < 0.15f;
    }

    private void spawnBreath(PlayerEntity p, int movementState) {
        if (mc.world == null) return;

        Vec3d look = getLookVec(p).normalize();
        Vec3d vel = p.getVelocity();
        double eyeH = p.getEyeY() - p.getY();

        Vec3d mouth = p.getPos()
                .add(0.0, eyeH - 0.12, 0.0)
                .add(look.multiply(0.18));

        int count = movementState == 2 ? 7 + rnd.nextInt(3) : 4 + rnd.nextInt(3);
        double forward = movementState == 2 ? 0.032 : 0.022;

        for (int i = 0; i < count; i++) {
            double spread = movementState == 2 ? 0.018 : 0.012;
            double vx = look.x * forward + vel.x * 0.10 + (rnd.nextDouble() - 0.5) * spread;
            double vy = look.y * (forward * 0.6) + vel.y * 0.04 + rnd.nextDouble() * 0.010;
            double vz = look.z * forward + vel.z * 0.10 + (rnd.nextDouble() - 0.5) * spread;
            addParticleSafe(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, vx, vy, vz);
            if (rnd.nextInt(4) == 0) {
                addParticleSafe(ParticleTypes.WHITE_ASH, mouth.x, mouth.y, mouth.z, vx * 0.20, 0.0025, vz * 0.20);
            }
        }
    }

    private void tickUnderwaterBreath() {
        if (mc.world == null) return;

        for (PlayerEntity p : mc.world.getPlayers()) {
            int id = p.getId();

            if (isSpectator(p) || isCreative(p) || !isUnderWater(p)) {
                lastAir.remove(id);
                bubbleT.remove(id);
                continue;
            }

            Integer a = getAirValue(p);
            if (a == null) continue;

            int cur = a;
            int prev = lastAir.getOrDefault(id, cur);
            lastAir.put(id, cur);

            if (cur < prev && cur > 0 && cur % 30 == 0) {
                bubbleT.put(id, 10 + rnd.nextInt(6));
            }

            int t = bubbleT.getOrDefault(id, 0);
            if (t > 0) {
                if (rnd.nextInt(3) != 0) spawnBubble(p);
                bubbleT.put(id, t - 1);
            }
        }
    }

    private void spawnBubble(PlayerEntity p) {
        if (mc.world == null) return;

        Vec3d look = getLookVec(p).normalize();
        Vec3d origin = p.getPos().add(0.0, p.getEyeY() - p.getY() - 0.10, 0.0).add(look.multiply(0.16));
        Vec3d pv = p.getVelocity();

        double vx = look.x * 0.03 + pv.x * 0.35 + (rnd.nextDouble() - 0.5) * 0.018;
        double vy = 0.06 + pv.y * 0.25 + rnd.nextDouble() * 0.018;
        double vz = look.z * 0.03 + pv.z * 0.35 + (rnd.nextDouble() - 0.5) * 0.018;

        addParticleSafe(ParticleTypes.BUBBLE, origin.x, origin.y, origin.z, vx, vy, vz);
        if (rnd.nextInt(4) == 0) {
            addParticleSafe(ParticleTypes.BUBBLE_POP, origin.x, origin.y, origin.z, 0, 0, 0);
        }
    }

    private void tickWaterDrips() {
        if (mc.world == null) return;

        long time;
        try {
            time = mc.world.getTime();
        } catch (Throwable ignored) {
            time = 0L;
        }

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (isSpectator(p) || isCreative(p)) continue;

            int id = p.getId();

            if (isUnderWater(p)) {
                lastSub.put(id, time);
                lastWetT.put(id, 24);
                continue;
            }

            int wet = lastWetT.getOrDefault(id, 0);
            if (wet > 0) {
                wet--;
                lastWetT.put(id, wet);
            }

            long last = lastSub.getOrDefault(id, -200L);
            if (time - last > 100L && wet <= 0) continue;

            if (rnd.nextInt(2) != 0) continue;
            spawnDrips(p, wet > 0);
        }
    }

    private void spawnDrips(PlayerEntity p, boolean freshWet) {
        if (mc.world == null) return;

        int count = freshWet ? 3 + rnd.nextInt(3) : 1 + rnd.nextInt(2);
        float yaw = p.getYaw();
        double ry = Math.toRadians(-yaw);

        for (int i = 0; i < count; i++) {
            double ring = 0.23 + rnd.nextDouble() * 0.14;
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double lx = Math.cos(ang) * ring;
            double lz = Math.sin(ang) * ring;
            double ly = 0.85 + rnd.nextDouble() * 1.00;

            double rx = lx * Math.cos(ry) - lz * Math.sin(ry);
            double rz = lx * Math.sin(ry) + lz * Math.cos(ry);

            double x = p.getX() + rx;
            double y = p.getY() + ly;
            double z = p.getZ() + rz;

            addParticleSafe(ParticleTypes.DRIPPING_WATER, x, y, z, 0, 0, 0);
            if (freshWet && rnd.nextInt(5) == 0) {
                addParticleSafe(ParticleTypes.SPLASH, x, y - 0.12, z, 0, 0, 0);
            }
        }
    }

    private void tickStepEffects() {
        if (mc.world == null) return;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (isSpectator(p) || isCreative(p)) continue;
            if (!p.isOnGround()) continue;
            if (isUnderWater(p) || isInWater(p)) continue;
            if (isSneaking(p)) continue;

            Vec3d vel = p.getVelocity();
            double hs = vel.x * vel.x + vel.z * vel.z;
            if (hs < 0.015) continue;

            int id = p.getId();
            int t = stepT.getOrDefault(id, 0) + 1;
            stepT.put(id, t);

            int freq = p.isSprinting() ? 2 : 4;
            if (t % freq != 0) continue;

            BlockPos feet = p.getBlockPos();
            BlockState atFeet = mc.world.getBlockState(feet);
            BlockState below = mc.world.getBlockState(feet.down());
            BlockState src = !atFeet.isAir() ? atFeet : below;
            if (src == null || src.isAir()) continue;

            String key = safeKey(src.getBlock());
            if (key == null) continue;

            if (key.contains("snow")) {
                spawnSnowStep(p, src, vel);
            } else if (key.contains("sand") || key.contains("red_sand")) {
                spawnSandStep(p, src, vel);
            }
        }
    }

    private void spawnSnowStep(PlayerEntity p, BlockState src, Vec3d vel) {
        double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.55;
        double y = p.getY() + 0.03;
        double z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.55;

        double vx = -vel.x * 0.20 + (rnd.nextDouble() - 0.5) * 0.025;
        double vz = -vel.z * 0.20 + (rnd.nextDouble() - 0.5) * 0.025;

        addParticleSafe(ParticleTypes.SNOWFLAKE, x, y + 0.04, z, vx, 0.02 + rnd.nextDouble() * 0.01, vz);
        if (rnd.nextInt(2) == 0) {
            addParticleSafe(new BlockStateParticleEffect(ParticleTypes.BLOCK, src), x, y, z, vx * 0.6, 0.01, vz * 0.6);
        }
    }

    private void spawnSandStep(PlayerEntity p, BlockState src, Vec3d vel) {
        double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.65;
        double y = p.getY() + 0.02;
        double z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.65;

        double vx = -vel.x * 0.35 + (rnd.nextDouble() - 0.5) * 0.05;
        double vz = -vel.z * 0.35 + (rnd.nextDouble() - 0.5) * 0.05;

        addParticleSafe(new BlockStateParticleEffect(ParticleTypes.BLOCK, src), x, y, z, vx, 0.01, vz);
        if (rnd.nextInt(3) == 0) {
            addParticleSafe(new BlockStateParticleEffect(ParticleTypes.BLOCK, src), x, y + 0.02, z, vx * 0.6, 0.02, vz * 0.6);
        }
    }

    private void tickCaveDust() {
        if (mc.player == null || mc.world == null) return;

        caveCheckT++;
        if (caveCheckT >= 20) {
            caveCheckT = 0;
            updateCaveStatus();
        }

        if (!inCave) return;
        if (rnd.nextInt(3) != 0) return;

        BlockPos center = mc.player.getBlockPos();

        double x = center.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 20.0;
        double z = center.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 20.0;
        double y = center.getY() + 1.0 + rnd.nextDouble() * 5.0;

        BlockPos p = BlockPos.ofFloored(x, y, z);
        if (!mc.world.getBlockState(p).isAir()) return;

        addParticleSafe(ParticleTypes.WHITE_ASH, x, y, z, 0, -0.006 - rnd.nextDouble() * 0.004, 0);
    }

    private void updateCaveStatus() {
        if (mc.player == null || mc.world == null) {
            inCave = false;
            return;
        }

        BlockPos p = mc.player.getBlockPos();
        if (mc.world.isSkyVisible(p.up())) {
            inCave = false;
            return;
        }

        int sky;
        try {
            sky = mc.world.getLightLevel(LightType.SKY, p);
        } catch (Throwable ignored) {
            sky = 0;
        }

        inCave = sky <= 0;
    }

    private void tickElytraTrail() {
        if (mc.world == null) return;

        elytraT++;
        if (elytraT < 2) return;
        elytraT = 0;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (isSpectator(p) || p.isInvisible()) continue;
            if (!isFallFlying(p)) continue;

            Vec3d v = p.getVelocity();
            if (v.lengthSquared() < 0.35) continue;

            Vec3d dir = v.normalize();
            Vec3d side = dir.crossProduct(new Vec3d(0, 1, 0));
            if (side.lengthSquared() < 0.001) side = new Vec3d(1, 0, 0);
            else side = side.normalize();

            for (int i = 0; i < 2; i++) {
                double sideMul = i == 0 ? -0.72 : 0.72;
                double lateralVar = (rnd.nextDouble() - 0.5) * 0.12;
                double depthVar = (rnd.nextDouble() - 0.5) * 0.12;
                double heightVar = (rnd.nextDouble() - 0.5) * 0.14;

                double eyeH = p.getEyeY() - p.getY();

                Vec3d pos = p.getPos()
                        .add(0, eyeH * 0.24 + heightVar, 0)
                        .add(dir.multiply(-0.18 + depthVar))
                        .add(side.multiply(sideMul + lateralVar));

                double speed = v.length();
                Vec3d baseVel = dir.multiply(-speed * (0.08 + rnd.nextDouble() * 0.04));

                addParticleSafe(ParticleTypes.CLOUD,
                        pos.x, pos.y, pos.z,
                        baseVel.x + (rnd.nextDouble() - 0.5) * 0.015,
                        baseVel.y + (rnd.nextDouble() - 0.5) * 0.010,
                        baseVel.z + (rnd.nextDouble() - 0.5) * 0.015);

                if (rnd.nextInt(3) == 0) {
                    addParticleSafe(ParticleTypes.WHITE_ASH,
                            pos.x, pos.y, pos.z,
                            (rnd.nextDouble() - 0.5) * 0.01,
                            0.001,
                            (rnd.nextDouble() - 0.5) * 0.01);
                }
            }
        }
    }

    private void tickWitherSmoke() {
        if (mc.world == null) return;

        for (PlayerEntity p : mc.world.getPlayers()) {
            int id = p.getId();

            if (!p.hasStatusEffect(StatusEffects.WITHER)) {
                witherT.remove(id);
                continue;
            }

            int t = witherT.getOrDefault(id, 0) + 1;
            if (t >= 2) {
                witherT.put(id, 0);
                spawnWither(p);
            } else {
                witherT.put(id, t);
            }
        }
    }

    private void spawnWither(PlayerEntity p) {
        if (mc.world == null) return;

        int n = 2 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            double x = p.getX() + (rnd.nextDouble() - 0.5) * 0.95;
            double y = p.getY() + 0.18 + rnd.nextDouble() * 1.45;
            double z = p.getZ() + (rnd.nextDouble() - 0.5) * 0.95;
            double dy = -0.010 - rnd.nextDouble() * 0.008;
            addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, dy, 0);
        }
    }

    private void tickBurningSparks() {
        if (mc.player == null || mc.world == null) return;

        burnSparkT++;
        if (burnSparkT < 2) return;
        burnSparkT = 0;

        Box box = mc.player.getBoundingBox().expand(14.0);
        for (Entity ent : mc.world.getOtherEntities(mc.player, box, Entity::isOnFire)) {
            if (rnd.nextInt(2) != 0) continue;

            double x = ent.getX() + (rnd.nextDouble() - 0.5) * (ent.getWidth() * 0.7);
            double y = ent.getY() + rnd.nextDouble() * (ent.getHeight() * 0.8);
            double z = ent.getZ() + (rnd.nextDouble() - 0.5) * (ent.getWidth() * 0.7);

            addParticleSafe(ParticleTypes.LAVA, x, y, z, 0, 0.015 + rnd.nextDouble() * 0.02, 0);
            if (rnd.nextInt(3) == 0) {
                addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.008, 0);
            }
        }
    }

    private void tickAmbientLightsAndFire() {
        if (mc.player == null || mc.world == null) return;

        ambientScanT++;
        int samples = (ambientScanT & 1) == 0 ? 24 : 14;

        BlockPos base = mc.player.getBlockPos();
        int r = 12;

        for (int i = 0; i < samples; i++) {
            int dx = rnd.nextInt(r * 2 + 1) - r;
            int dy = rnd.nextInt(8) - 2;
            int dz = rnd.nextInt(r * 2 + 1) - r;

            BlockPos p = base.add(dx, dy, dz);
            BlockState st = mc.world.getBlockState(p);
            if (st == null || st.isAir()) continue;

            String key = safeKey(st.getBlock());
            if (key == null) continue;

            boolean torch = key.contains("torch");
            boolean lantern = key.contains("lantern");
            boolean campfire = key.contains("campfire");
            boolean candle = key.contains("candle");
            boolean fire = key.contains("fire") && !campfire;
            boolean soul = key.contains("soul");

            if (!(torch || lantern || campfire || candle || fire)) continue;
            if (!isProbablyLit(st, key)) continue;

            if (campfire) {
                if (rnd.nextInt(2) == 0) spawnCampfireFx(p, soul);
                continue;
            }

            if (torch) {
                if (rnd.nextInt(4) == 0) spawnTorchFx(p, soul);
                continue;
            }

            if (lantern) {
                if (rnd.nextInt(4) == 0) spawnLanternFx(p, soul, key.contains("hanging"));
                continue;
            }

            if (candle) {
                if (rnd.nextInt(4) == 0) spawnCandleFx(p, st, soul);
                continue;
            }

            if (fire) {
                if (rnd.nextInt(3) == 0) spawnFireFx(p, soul);
            }
        }
    }

    private void spawnCampfireFx(BlockPos p, boolean soul) {
        double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.30;
        double y = p.getY() + 0.48 + rnd.nextDouble() * 0.22;
        double z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.30;

        addParticleSafe(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, 0.035 + rnd.nextDouble() * 0.018, 0);
        if (rnd.nextInt(2) == 0) {
            addParticleSafe(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.006, 0);
        }
    }

    private void spawnTorchFx(BlockPos p, boolean soul) {
        double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.05;
        double y = p.getY() + 0.67 + rnd.nextDouble() * 0.08;
        double z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.05;

        addParticleSafe(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.003, 0);
        if (rnd.nextInt(3) == 0) {
            addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.008, 0);
        }
    }

    private void spawnLanternFx(BlockPos p, boolean soul, boolean hanging) {
        double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.04;
        double y = p.getY() + (hanging ? 0.62 : 0.34) + rnd.nextDouble() * 0.05;
        double z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.04;

        addParticleSafe(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.003, 0);
        if (rnd.nextInt(4) == 0) {
            addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.008, 0);
        }
    }

    private void spawnCandleFx(BlockPos p, BlockState st, boolean soul) {
        int candles = getIntProperty(st, "candles", 1);
        if (candles < 1) candles = 1;
        if (candles > 4) candles = 4;

        double[][] offsets;
        if (candles == 1) {
            offsets = new double[][]{{0.0, 0.0}};
        } else if (candles == 2) {
            offsets = new double[][]{{-0.06, 0.01}, {0.06, -0.01}};
        } else if (candles == 3) {
            offsets = new double[][]{{-0.07, 0.03}, {0.07, 0.02}, {0.00, -0.06}};
        } else {
            offsets = new double[][]{{-0.07, 0.05}, {0.07, 0.05}, {-0.05, -0.06}, {0.05, -0.06}};
        }

        int idx = rnd.nextInt(offsets.length);
        double x = p.getX() + 0.5 + offsets[idx][0] + (rnd.nextDouble() - 0.5) * 0.015;
        double y = p.getY() + 0.54 + rnd.nextDouble() * 0.06;
        double z = p.getZ() + 0.5 + offsets[idx][1] + (rnd.nextDouble() - 0.5) * 0.015;

        addParticleSafe(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.0025, 0);
        if (rnd.nextInt(4) == 0) {
            addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.006, 0);
        }
    }

    private void spawnFireFx(BlockPos p, boolean soul) {
        double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.14;
        double y = p.getY() + 0.10 + rnd.nextDouble() * 0.25;
        double z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.14;

        addParticleSafe(soul ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y, z, 0, 0.005, 0);
        if (rnd.nextInt(3) == 0) {
            addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.010, 0);
        }
    }

    private void tickSteamColumns() {
        if (mc.player == null || mc.world == null) return;

        steamT++;
        if (steamT < 2) return;
        steamT = 0;

        BlockPos base = mc.player.getBlockPos();
        int r = 10;

        for (int i = 0; i < 7; i++) {
            int dx = rnd.nextInt(r * 2 + 1) - r;
            int dz = rnd.nextInt(r * 2 + 1) - r;
            int dy = rnd.nextInt(6) - 2;

            BlockPos p = base.add(dx, dy, dz);
            BlockState st = mc.world.getBlockState(p);
            if (st == null || st.isAir()) continue;

            String key = safeKey(st.getBlock());
            if (key == null) continue;

            boolean hot = key.contains("lava") || key.contains("magma") || key.contains("campfire") || key.contains("fire");
            if (!hot) continue;

            boolean waterNear = false;
            for (int s = 0; s < 6; s++) {
                BlockPos n = p.add((s == 0 ? 1 : s == 1 ? -1 : 0), (s == 2 ? 1 : s == 3 ? -1 : 0), (s == 4 ? 1 : s == 5 ? -1 : 0));
                BlockState ns = mc.world.getBlockState(n);
                if (ns != null && isWaterLike(ns)) {
                    waterNear = true;
                    break;
                }
            }
            if (!waterNear) continue;

            double x = p.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.28;
            double y = p.getY() + 1.02 + rnd.nextDouble() * 0.20;
            double z = p.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.28;

            addParticleSafe(ParticleTypes.CLOUD, x, y, z, 0, 0.045 + rnd.nextDouble() * 0.02, 0);
            if (rnd.nextInt(3) == 0) {
                addParticleSafe(ParticleTypes.SMOKE, x, y, z, 0, 0.018, 0);
            }
        }
    }

    private boolean isProbablyLit(BlockState st, String key) {
        Boolean lit = getBooleanProperty(st, "lit");
        if (lit != null) return lit;
        if (key.contains("fire")) return true;
        if (key.contains("torch")) return true;
        if (key.contains("lantern")) return true;
        if (key.contains("candle")) return true;
        return true;
    }

    private Boolean getBooleanProperty(BlockState st, String propName) {
        try {
            Method m = st.getClass().getMethod("getEntries");
            Object entries = m.invoke(st);
            if (!(entries instanceof Map<?, ?> map)) return null;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object prop = e.getKey();
                Object val = e.getValue();
                if (prop == null) continue;
                Method getName = findNoArgMethod(prop.getClass(), "getName");
                if (getName == null) continue;
                Object nameObj = getName.invoke(prop);
                if (!(nameObj instanceof String name)) continue;
                if (!propName.equalsIgnoreCase(name)) continue;
                if (val instanceof Boolean b) return b;
                return null;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int getIntProperty(BlockState st, String propName, int def) {
        try {
            Method m = st.getClass().getMethod("getEntries");
            Object entries = m.invoke(st);
            if (!(entries instanceof Map<?, ?> map)) return def;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object prop = e.getKey();
                Object val = e.getValue();
                if (prop == null) continue;
                Method getName = findNoArgMethod(prop.getClass(), "getName");
                if (getName == null) continue;
                Object nameObj = getName.invoke(prop);
                if (!(nameObj instanceof String name)) continue;
                if (!propName.equalsIgnoreCase(name)) continue;
                if (val instanceof Integer i) return i;
                if (val instanceof Number n) return n.intValue();
                return def;
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private boolean isWaterLike(BlockState st) {
        try {
            return st.getFluidState() != null && !st.getFluidState().isEmpty() && st.getFluidState().isIn(FluidTags.WATER);
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static String safeKey(Object block) {
        try {
            Method m = block.getClass().getMethod("getTranslationKey");
            Object r = m.invoke(block);
            if (r instanceof String s) return s.toLowerCase();
        } catch (Throwable ignored) {
        }
        try {
            String s = String.valueOf(block);
            if (s != null) return s.toLowerCase();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void addParticleSafe(ParticleEffect fx, double x, double y, double z, double vx, double vy, double vz) {
        if (mc == null || mc.world == null) return;
        try {
            mc.world.addParticle(fx, x, y, z, vx, vy, vz);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Object pm = fieldValue(mc, "particleManager");
            if (pm != null) {
                Method m = null;
                for (Method mm : pm.getClass().getMethods()) {
                    if (!mm.getName().equals("addParticle")) continue;
                    if (mm.getParameterCount() != 7) continue;
                    if (!ParticleEffect.class.isAssignableFrom(mm.getParameterTypes()[0])) continue;
                    m = mm;
                    break;
                }
                if (m != null) m.invoke(pm, fx, x, y, z, vx, vy, vz);
            }
        } catch (Throwable ignored) {
        }
    }

    private int movementState(PlayerEntity p) {
        if (p.isSprinting()) return 2;
        Vec3d v = p.getVelocity();
        if (!p.isOnGround() && v.y > 0.0) return 3;
        double hs = v.x * v.x + v.z * v.z;
        if (hs > 0.02) return 1;
        return 0;
    }

    private boolean isPausedSafe() {
        try {
            return mc.isPaused();
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Vec3d getLookVec(PlayerEntity player) {
        try {
            return player.getRotationVec(1.0f);
        } catch (Throwable ignored) {
        }
        float yaw = player.getYaw() * ((float) Math.PI / 180F);
        float pitch = player.getPitch() * ((float) Math.PI / 180F);
        float cosP = MathHelper.cos(pitch);
        return new Vec3d(-MathHelper.sin(yaw) * cosP, -MathHelper.sin(pitch), MathHelper.cos(yaw) * cosP);
    }

    private float biomeBaseTemp(BlockPos pos) {
        if (mc.world == null || pos == null) return 1.0f;
        try {
            Object entry = mc.world.getBiome(pos);
            Object biome = entry;
            try {
                Method value = entry.getClass().getMethod("value");
                biome = value.invoke(entry);
            } catch (Throwable ignored) {
            }

            Float f;

            f = invokeFloatNoArgs(biome, "getBaseTemperature");
            if (f != null) return f;

            f = invokeFloatNoArgs(biome, "getTemperature");
            if (f != null) return f;

            try {
                Method m = biome.getClass().getMethod("getTemperature", BlockPos.class);
                Object r = m.invoke(biome, pos);
                if (r instanceof Float ff) return ff;
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }

    private static boolean isUnderWater(PlayerEntity p) {
        try {
            return p.isSubmergedInWater();
        } catch (Throwable ignored) {
        }

        Boolean b;

        b = invokeBoolNoArgs(p, "isUnderWater");
        if (b != null) return b;

        b = invokeBoolNoArgs(p, "isSubmergedInWater");
        if (b != null) return b;

        try {
            return p.isTouchingWater() && p.getEyeY() < p.getY() + (p.getEyeY() - p.getY()) * 0.85;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isInWater(PlayerEntity p) {
        try {
            return p.isTouchingWater();
        } catch (Throwable ignored) {
        }

        Boolean b;

        b = invokeBoolNoArgs(p, "isInWater");
        if (b != null) return b;

        b = invokeBoolNoArgs(p, "isTouchingWater");
        if (b != null) return b;

        return false;
    }

    private static boolean isCreative(PlayerEntity p) {
        try {
            return p.getAbilities().creativeMode;
        } catch (Throwable ignored) {
        }
        Boolean b = invokeBoolNoArgs(p, "isCreative");
        return b != null && b;
    }

    private static boolean isSpectator(PlayerEntity p) {
        try {
            return p.isSpectator();
        } catch (Throwable ignored) {
        }
        Boolean b = invokeBoolNoArgs(p, "isSpectator");
        return b != null && b;
    }

    private static boolean isSneaking(PlayerEntity p) {
        Boolean b;

        b = invokeBoolNoArgs(p, "isSneaking");
        if (b != null) return b;

        b = invokeBoolNoArgs(p, "isInSneakingPose");
        if (b != null) return b;

        b = invokeBoolNoArgs(p, "isShiftKeyDown");
        if (b != null) return b;

        return false;
    }

    private static boolean isFallFlying(PlayerEntity p) {
        Boolean b;

        b = invokeBoolNoArgs(p, "isFallFlying");
        if (b != null) return b;

        b = invokeBoolNoArgs(p, "isGliding");
        if (b != null) return b;

        return false;
    }

    private static Integer getAirValue(PlayerEntity p) {
        Integer i;

        i = invokeIntNoArgs(p, "getAir");
        if (i != null) return i;

        i = invokeIntNoArgs(p, "getAirSupply");
        if (i != null) return i;

        i = invokeIntNoArgs(p, "getAirTicks");
        if (i != null) return i;

        return null;
    }

    private void cleanupMaps() {
        if (mc.world == null) return;

        Map<Integer, Boolean> alive = new HashMap<>();
        for (PlayerEntity p : mc.world.getPlayers()) alive.put(p.getId(), Boolean.TRUE);

        cleanupMap(breathT, alive);
        cleanupMap(breathS, alive);
        cleanupMap(lastAir, alive);
        cleanupMap(bubbleT, alive);
        cleanupMap(lastSub, alive);
        cleanupMap(lastWetT, alive);
        cleanupMap(witherT, alive);
        cleanupMap(stepT, alive);
    }

    private static void cleanupMap(Map<Integer, ?> map, Map<Integer, Boolean> alive) {
        Iterator<Integer> it = map.keySet().iterator();
        while (it.hasNext()) {
            Integer k = it.next();
            if (!alive.containsKey(k)) it.remove();
        }
    }

    public boolean enabledPublic() {
        Boolean b;

        b = invokeBoolNoArgs(this, "isEnabled");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "isToggled");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "isState");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "getState");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "getEnabled");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "getToggled");
        if (b != null) return b;

        b = invokeBoolNoArgs(this, "get");
        if (b != null) return b;

        Object v;

        v = fieldValue(this, "enabled");
        if (v instanceof Boolean bb) return bb;

        v = fieldValue(this, "toggled");
        if (v instanceof Boolean bb) return bb;

        v = fieldValue(this, "state");
        if (v instanceof Boolean bb) return bb;

        v = fieldValue(this, "active");
        if (v instanceof Boolean bb) return bb;

        v = fieldValue(this, "on");
        if (v instanceof Boolean bb) return bb;

        return false;
    }

    private static Boolean invokeBoolNoArgs(Object target, String name) {
        try {
            Method m = findNoArgMethod(target.getClass(), name);
            if (m == null) return null;
            if (m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) return null;
            m.setAccessible(true);
            Object r = m.invoke(target);
            if (r instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Integer invokeIntNoArgs(Object target, String name) {
        try {
            Method m = findNoArgMethod(target.getClass(), name);
            if (m == null) return null;
            if (m.getReturnType() != int.class && m.getReturnType() != Integer.class) return null;
            m.setAccessible(true);
            Object r = m.invoke(target);
            if (r instanceof Integer i) return i;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Float invokeFloatNoArgs(Object target, String name) {
        try {
            Method m = findNoArgMethod(target.getClass(), name);
            if (m == null) return null;
            if (m.getReturnType() != float.class && m.getReturnType() != Float.class) return null;
            m.setAccessible(true);
            Object r = m.invoke(target);
            if (r instanceof Float f) return f;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> c, String name) {
        Class<?> k = c;
        while (k != null) {
            try {
                for (Method m : k.getDeclaredMethods()) {
                    if (!m.getName().equals(name)) continue;
                    if (m.getParameterCount() != 0) continue;
                    return m;
                }
            } catch (Throwable ignored) {
            }
            k = k.getSuperclass();
        }
        return null;
    }

    private static Object fieldValue(Object obj, String name) {
        if (obj == null) return null;
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }
}