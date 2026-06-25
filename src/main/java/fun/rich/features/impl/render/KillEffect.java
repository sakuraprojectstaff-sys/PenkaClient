package fun.rich.features.impl.render;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.player.EntityDeathEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.sound.SoundManager;
import fun.rich.utils.display.geometry.Render3D;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class KillEffect extends Module {
    private static final long DURATION_MS = 3000L;
    private static final Random RANDOM = new Random();
    private static final Identifier GLOW_TEX = Identifier.of("textures/features/particles/bloom.png");

    private final SliderSettings volume = new SliderSettings("Volume", "Volume").setValue(100).range(0, 100);
    private final BooleanSetting playSound = new BooleanSetting("Play Sound", "Play Sound").setValue(true);
    private final BooleanSetting mobs = new BooleanSetting("Mobs", "Mobs").setValue(false);

    private final MultiSelectSetting effects = new MultiSelectSetting("Effects", "Effects")
            .value("Cross", "Soul", "Lightning", "Lightning Vanilla", "Particles", "Particles Render")
            .selected("Soul");

    private final SliderSettings vanillaStrikes = new SliderSettings("Strikes", "Strikes")
            .setValue(2)
            .range(1, 5)
            .visible(() -> effects.isSelected("Lightning Vanilla"));

    private final BooleanSetting particleGravity = new BooleanSetting("Gravity", "Gravity")
            .setValue(true)
            .visible(() -> effects.isSelected("Particles") || effects.isSelected("Particles Render"));

    private final ColorSetting colorSetting = new ColorSetting("Color", "Color")
            .setColor(new Color(100, 150, 255, 255).getRGB()).presets(0xFF6C9AFD, 0xFF8C7FFF, 0xFFFFA576, 0xFFFF7B7B);

    private final Map<UUID, EntityRenderData> renderEntities = new ConcurrentHashMap<>();
    private final List<DeathEffect> lightningEffects = new CopyOnWriteArrayList<>();
    private final List<Particle> particles = new CopyOnWriteArrayList<>();

    private static final Method END_VERTEX = findEndVertexMethod();

    private Method mTrail;
    private Method mFlicker;
    private Method mColorInt;
    private Method mColorIntArr;
    private Method mColorFloats;
    private Method mColorFloatArr;

    private Method mLightningSetCosmetic;

    public KillEffect() {
        super("KillEffect", ModuleCategory.RENDER);
        setup(volume, playSound, mobs, effects, vanillaStrikes, particleGravity, colorSetting);
    }

    private static final class EntityRenderData {
        private final long timestamp;
        private final float yaw;
        private final Vec3d startPos;
        private final Entity entity;
        private final OtherClientPlayerEntity fakePlayer;
        private final int fakeId;
        private final boolean cross;
        private final boolean soul;

        EntityRenderData(long timestamp, float yaw, Vec3d startPos, Entity entity, OtherClientPlayerEntity fakePlayer, int fakeId, boolean cross, boolean soul) {
            this.timestamp = timestamp;
            this.yaw = yaw;
            this.startPos = startPos;
            this.entity = entity;
            this.fakePlayer = fakePlayer;
            this.fakeId = fakeId;
            this.cross = cross;
            this.soul = soul;
        }

        long getTimestamp() { return timestamp; }
        float getYaw() { return yaw; }
        Vec3d getStartPos() { return startPos; }
        Entity getEntity() { return entity; }
        OtherClientPlayerEntity getFakePlayer() { return fakePlayer; }
        int getFakeId() { return fakeId; }
        boolean isCross() { return cross; }
        boolean isSoul() { return soul; }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (mc.world == null || mc.player == null) return;

        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity living)) return;
        if (!mobs.isValue() && !(entity instanceof PlayerEntity)) return;
        if (entity == mc.player) return;

        if (playSound.isValue()) {
            mc.world.playSound(mc.player, entity.getBlockPos(), SoundManager.ORTHODOX, SoundCategory.BLOCKS, volume.getValue() / 100f, 1f);
        }

        boolean doCross = effects.isSelected("Cross");
        boolean doSoul = effects.isSelected("Soul");
        boolean doRenderLightning = effects.isSelected("Lightning");
        boolean doVanillaLightning = effects.isSelected("Lightning Vanilla");
        boolean doVanillaParticles = effects.isSelected("Particles");
        boolean doRenderParticles = effects.isSelected("Particles Render");

        int c = getColorArgb();

        if (doCross || doSoul) {
            UUID key = entity.getUuid();
            if (!renderEntities.containsKey(key)) {
                OtherClientPlayerEntity fakePlayer = null;
                int fakeId = 0;

                if (doSoul && entity instanceof PlayerEntity p) {
                    GameProfile gp = p.getGameProfile();
                    fakePlayer = new OtherClientPlayerEntity(mc.world, new GameProfile(gp.getId(), gp.getName()));
                    fakePlayer.setPitch(-30.0f);
                    fakePlayer.setYaw(entity.getYaw());
                    fakePlayer.headYaw = entity.getYaw();
                    fakePlayer.bodyYaw = entity.getYaw();
                    fakePlayer.setCustomNameVisible(false);
                    fakePlayer.setCustomName(Text.literal("Ghost_" + gp.getId()));
                    try {
                        mc.world.addEntity(fakePlayer);
                    } catch (Throwable ignored) {
                    }
                }

                renderEntities.put(key, new EntityRenderData(
                        System.currentTimeMillis(),
                        entity.getYaw(),
                        entity.getPos(),
                        entity,
                        fakePlayer,
                        fakeId,
                        doCross,
                        doSoul
                ));
            }
        }

        if (doRenderLightning) {
            lightningEffects.add(new DeathEffect(living.getPos(), c));
        }

        if (doVanillaLightning) {
            int n = (int) MathHelper.clamp(vanillaStrikes.getValue(), 1.0, 5.0);
            Vec3d p = living.getPos();
            for (int i = 0; i < n; i++) {
                spawnVanillaLightning(p);
            }
        }

        boolean grav = particleGravity.isValue();

        if (doVanillaParticles) {
            fireworkExplode(living.getX(), living.getY() + (living.getHeight() * 0.55), living.getZ(), c, grav);
        }

        if (doRenderParticles) {
            spawnParticles(living, c, grav);
        }
    }

    private void spawnVanillaLightning(Vec3d pos) {
        if (mc.world == null) return;

        LightningEntity l = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
        l.setPos(pos.x, pos.y, pos.z);

        try {
            Method m = getLightningSetCosmetic();
            if (m != null) m.invoke(l, true);
        } catch (Throwable ignored) {
        }

        try {
            mc.world.addEntity(l);
        } catch (Throwable ignored) {
        }
    }

    private Method getLightningSetCosmetic() {
        if (mLightningSetCosmetic != null) return mLightningSetCosmetic;
        mLightningSetCosmetic = findMethodDeep(LightningEntity.class, "setCosmetic", boolean.class);
        return mLightningSetCosmetic;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.world == null || mc.player == null) return;

        MatrixStack stack = e.getStack();
        float tickDelta = e.getPartialTicks();

        if (!renderEntities.isEmpty()) {
            List<UUID> toRemove = new ArrayList<>();
            renderEntities.forEach((uuid, data) -> {
                long dt = System.currentTimeMillis() - data.getTimestamp();
                if (dt > DURATION_MS) {
                    toRemove.add(uuid);
                    if (data.getFakePlayer() != null) {
                        try {
                            mc.world.removeEntity(data.getFakePlayer().getId(), Entity.RemovalReason.DISCARDED);
                        } catch (Throwable ignored) {
                        }
                    }
                    return;
                }

                float t = dt / (float) DURATION_MS;
                float fade = 1.0f - t;

                if (data.isCross()) {
                    float appear = MathHelper.clamp(t / 0.18f, 0.0f, 1.0f);
                    float len = 0.15f + 1.05f * appear;
                    float y = 1.15f + 0.35f * appear;

                    int cc = withAlphaMul(getColorArgb(), 0.85f * fade);

                    float yawRad = (float) Math.toRadians(data.getYaw());
                    float sx = (float) Math.sin(yawRad);
                    float cx = (float) Math.cos(yawRad);

                    Vec3d pos = data.getStartPos().add(0.0, y, 0.0);

                    Vec3d forward = new Vec3d(cx, 0.0, -sx).multiply(len);
                    Vec3d up = new Vec3d(0.0, 1.0, 0.0).multiply(len);

                    Vec3d a1 = pos.subtract(forward).subtract(up);
                    Vec3d b1 = pos.add(forward).add(up);

                    Vec3d a2 = pos.subtract(forward).add(up);
                    Vec3d b2 = pos.add(forward).subtract(up);

                    Render3D.drawLine(a1, b1, cc, 2, true);
                    Render3D.drawLine(a2, b2, cc, 2, true);
                }

                if (data.isSoul()) {
                    float yOffset = t * 3.0f;
                    int alpha = (int) (255 * fade);
                    if (alpha <= 1) return;

                    Vec3d soulPos = data.getStartPos().add(0.0, yOffset, 0.0);

                    Entity renderEntity = data.getEntity();
                    if (data.getFakePlayer() != null) {
                        renderEntity = data.getFakePlayer();
                        renderEntity.setPos(soulPos.x, soulPos.y, soulPos.z);
                    }

                    Render3D.drawEntity(renderEntity, soulPos, data.getYaw(), alpha, stack, tickDelta);
                }
            });
            for (UUID id : toRemove) renderEntities.remove(id);
        }

        if (lightningEffects.isEmpty() && particles.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();

        stack.push();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        RenderSystem.setShaderTexture(0, GLOW_TEX);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder builder = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (DeathEffect effect : lightningEffects) {
            effect.render(builder, stack, camera);
        }

        for (Particle p : particles) {
            if (!p.isDead()) {
                p.update();
                p.render(builder, stack, camera);
            }
        }

        BuiltBuffer built = builder.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }

        particles.removeIf(Particle::isDead);
        lightningEffects.removeIf(DeathEffect::isFinished);

        RenderSystem.depthMask(true);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.disableDepthTest();
        stack.pop();
    }

    private void fireworkExplode(double x, double y, double z, int colorArgb, boolean gravity) {
        if (mc.world == null || mc.particleManager == null) return;

        int amount = 4;
        double size = 0.5;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double gravBias = gravity ? -0.12 : 0.0;

        for (int i = -amount; i <= amount; ++i) {
            for (int j = -amount; j <= amount; ++j) {
                for (int k = -amount; k <= amount; ++k) {
                    double g = j + (random.nextDouble() - random.nextDouble()) * 0.5;
                    double h = i + (random.nextDouble() - random.nextDouble()) * 0.5;
                    double l = k + (random.nextDouble() - random.nextDouble()) * 0.5;

                    double m = Math.sqrt(g * g + h * h + l * l) / size + random.nextGaussian() * 0.05;
                    double vx = g / m;
                    double vy = (h / m) + gravBias;
                    double vz = l / m;

                    addExplosionParticle(x, y, z, vx, vy, vz, colorArgb);

                    if (i != -amount && i != amount && j != -amount && j != amount) {
                        k += amount * 2 - 1;
                    }
                }
            }
        }
    }

    private void addExplosionParticle(double x, double y, double z, double vx, double vy, double vz, int colorArgb) {
        if (mc.particleManager == null) return;

        Object p;
        try {
            p = mc.particleManager.addParticle(ParticleTypes.FIREWORK, x, y, z, vx, vy, vz);
        } catch (Throwable t) {
            return;
        }
        if (p == null) return;

        try {
            Method tr = getTrail(p.getClass());
            if (tr != null) tr.invoke(p, false);
        } catch (Throwable ignored) {
        }

        try {
            Method fl = getFlicker(p.getClass());
            if (fl != null) fl.invoke(p, true);
        } catch (Throwable ignored) {
        }

        int rgb = colorArgb & 0x00FFFFFF;
        float rf = ((rgb >>> 16) & 255) / 255.0f;
        float gf = ((rgb >>> 8) & 255) / 255.0f;
        float bf = (rgb & 255) / 255.0f;

        try {
            Method mi = getColorInt(p.getClass());
            if (mi != null) {
                mi.invoke(p, rgb);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method ma = getColorIntArr(p.getClass());
            if (ma != null) {
                ma.invoke(p, (Object) new int[]{rgb});
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method mf = getColorFloats(p.getClass());
            if (mf != null) {
                mf.invoke(p, rf, gf, bf);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method mfa = getColorFloatArr(p.getClass());
            if (mfa != null) {
                mfa.invoke(p, (Object) new float[]{rf, gf, bf});
            }
        } catch (Throwable ignored) {
        }
    }

    private Method getTrail(Class<?> cls) {
        if (mTrail != null) return mTrail;
        mTrail = findMethodDeep(cls, "setTrail", boolean.class);
        return mTrail;
    }

    private Method getFlicker(Class<?> cls) {
        if (mFlicker != null) return mFlicker;
        mFlicker = findMethodDeep(cls, "setFlicker", boolean.class);
        return mFlicker;
    }

    private Method getColorInt(Class<?> cls) {
        if (mColorInt != null) return mColorInt;
        mColorInt = findMethodDeep(cls, "setColor", int.class);
        return mColorInt;
    }

    private Method getColorIntArr(Class<?> cls) {
        if (mColorIntArr != null) return mColorIntArr;
        mColorIntArr = findMethodDeep(cls, "setColor", int[].class);
        return mColorIntArr;
    }

    private Method getColorFloats(Class<?> cls) {
        if (mColorFloats != null) return mColorFloats;
        mColorFloats = findMethodDeep(cls, "setColor", float.class, float.class, float.class);
        return mColorFloats;
    }

    private Method getColorFloatArr(Class<?> cls) {
        if (mColorFloatArr != null) return mColorFloatArr;
        mColorFloatArr = findMethodDeep(cls, "setColor", float[].class);
        return mColorFloatArr;
    }

    private static Method findMethodDeep(Class<?> c, String name, Class<?>... params) {
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            Method m = findMethod(cur, name, params);
            if (m != null) return m;
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        try {
            Method m = c.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
        }
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getColorArgb() {
        Object v = invoke(colorSetting, "getColor");
        if (v instanceof Integer i) return i;
        if (v instanceof Color c) return c.getRGB();

        v = invoke(colorSetting, "getValue");
        if (v instanceof Integer i) return i;

        v = invoke(colorSetting, "getColorValue");
        if (v instanceof Integer i) return i;

        return 0xFFFFFFFF;
    }

    private static Object invoke(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int withAlphaMul(int argb, float aMul) {
        int a = (argb >>> 24) & 255;
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;
        int na = (int) (a * MathHelper.clamp(aMul, 0.0f, 1.0f));
        if (na < 0) na = 0;
        if (na > 255) na = 255;
        return (na << 24) | (r << 16) | (g << 8) | b;
    }

    private static float easeOutBack(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (t - 1.0f) * (t - 1.0f) * (t - 1.0f) + c1 * (t - 1.0f) * (t - 1.0f);
    }

    private static Method findEndVertexMethod() {
        for (Method m : BufferBuilder.class.getDeclaredMethods()) {
            if (m.getParameterCount() != 0) continue;
            String n = m.getName();
            if (!n.equals("next") && !n.equals("endVertex")) continue;
            m.setAccessible(true);
            return m;
        }
        for (Method m : BufferBuilder.class.getMethods()) {
            if (m.getParameterCount() != 0) continue;
            String n = m.getName();
            if (!n.equals("next") && !n.equals("endVertex")) continue;
            m.setAccessible(true);
            return m;
        }
        return null;
    }

    private static void endVertex(BufferBuilder b) {
        if (END_VERTEX == null) return;
        try {
            END_VERTEX.invoke(b);
        } catch (Throwable ignored) {
        }
    }

    private static void drawBillboardQuad(BufferBuilder builder, MatrixStack ms, float size, int argb) {
        Matrix4f mat = ms.peek().getPositionMatrix();

        int a = (argb >>> 24) & 255;
        int r = (argb >>> 16) & 255;
        int g = (argb >>> 8) & 255;
        int b = argb & 255;

        float hs = size * 0.5f;

        builder.vertex(mat, -hs, -hs, 0.0f);
        builder.texture(0.0f, 0.0f);
        builder.color(r, g, b, a);
        endVertex(builder);

        builder.vertex(mat, hs, -hs, 0.0f);
        builder.texture(1.0f, 0.0f);
        builder.color(r, g, b, a);
        endVertex(builder);

        builder.vertex(mat, hs, hs, 0.0f);
        builder.texture(1.0f, 1.0f);
        builder.color(r, g, b, a);
        endVertex(builder);

        builder.vertex(mat, -hs, hs, 0.0f);
        builder.texture(0.0f, 1.0f);
        builder.color(r, g, b, a);
        endVertex(builder);
    }

    private void spawnParticles(LivingEntity entity, int colorArgb, boolean hasGravity) {
        Vec3d pos = entity.getPos();
        float width = entity.getWidth();
        float height = entity.getHeight();
        float yaw = (float) Math.toRadians(-entity.getYaw() + 90.0f);

        int count = 194;
        float hOffset = height - 0.2f;
        float spread = width * 0.4f;

        spawnSphere(pos.add(0.0, hOffset, 0.0), spread, count / 10, colorArgb, hasGravity);
        spawnBody(pos, width * 0.4f, height * 0.85f, width * 0.2f, yaw, count / 4, colorArgb, hasGravity);

        for (int i = -1; i <= 1; i += 2) {
            Vec3d armPos = new Vec3d(Math.sin(yaw) * width * 0.5 * i, height * 0.75f, Math.cos(yaw) * width * 0.5 * i);
            spawnLimb(pos.add(armPos), width * 0.4f, width * 0.15f, count / 8, colorArgb, hasGravity);

            Vec3d legPos = new Vec3d(Math.sin(yaw) * width * 0.15 * i, height * 0.4f, Math.cos(yaw) * width * 0.15 * i);
            spawnLimb(pos.add(legPos), height * 0.45f, width * 0.15f, count / 6, colorArgb, hasGravity);
        }
    }

    private void spawnSphere(Vec3d pos, float radius, int count, int colorArgb, boolean hasGravity) {
        for (int i = 0; i < count; i++) {
            float angle1 = RANDOM.nextFloat() * (float) Math.PI * 2.0f;
            float angle2 = (float) Math.acos(2.0f * RANDOM.nextFloat() - 1.0f);
            float r = radius * (float) Math.cbrt(RANDOM.nextFloat());

            float ox = r * (float) (Math.sin(angle2) * Math.cos(angle1));
            float oy = r * (float) (Math.sin(angle2) * Math.sin(angle1));
            float oz = r * (float) Math.cos(angle2);

            float speedBase = hasGravity ? 0.02f : 0.008f;
            float vx = (RANDOM.nextFloat() - 0.5f) * speedBase;
            float vy = hasGravity ? 0.03f + RANDOM.nextFloat() * 0.04f : (RANDOM.nextFloat() - 0.5f) * 0.008f;
            float vz = (RANDOM.nextFloat() - 0.5f) * speedBase;

            particles.add(new Particle(pos, ox, oy, oz, vx, vy, vz, colorArgb, hasGravity));
        }
    }

    private void spawnBody(Vec3d pos, float radiusX, float height, float spread, float yaw, int count, int colorArgb, boolean hasGravity) {
        for (int i = 0; i < count; i++) {
            float ox = (RANDOM.nextFloat() - 0.5f) * radiusX * 2.0f;
            float oy = RANDOM.nextFloat() * height;
            float oz = (RANDOM.nextFloat() - 0.5f) * spread * 2.0f;

            float rx = (float) (ox * Math.cos(yaw) - oz * Math.sin(yaw));
            float rz = (float) (ox * Math.sin(yaw) + oz * Math.cos(yaw));

            float speedBase = hasGravity ? 0.025f : 0.01f;
            float vx = (RANDOM.nextFloat() - 0.5f) * speedBase;
            float vy = hasGravity ? 0.04f + RANDOM.nextFloat() * 0.05f : (RANDOM.nextFloat() - 0.5f) * 0.01f;
            float vz = (RANDOM.nextFloat() - 0.5f) * speedBase;

            particles.add(new Particle(pos, rx, oy, rz, vx, vy, vz, colorArgb, hasGravity));
        }
    }

    private void spawnLimb(Vec3d pos, float height, float radius, int count, int colorArgb, boolean hasGravity) {
        for (int i = 0; i < count; i++) {
            float oy = -RANDOM.nextFloat() * height;
            float ox = (RANDOM.nextFloat() - 0.5f) * radius * 2.0f;
            float oz = (RANDOM.nextFloat() - 0.5f) * radius * 2.0f;

            float speedBase = hasGravity ? 0.018f : 0.006f;
            float vx = (RANDOM.nextFloat() - 0.5f) * speedBase;
            float vy = hasGravity ? 0.025f + RANDOM.nextFloat() * 0.035f : (RANDOM.nextFloat() - 0.5f) * 0.006f;
            float vz = (RANDOM.nextFloat() - 0.5f) * speedBase;

            particles.add(new Particle(pos, ox, oy, oz, vx, vy, vz, colorArgb, hasGravity));
        }
    }

    private final class Particle {
        double x, y, z;
        float vx, vy, vz;
        long startTime, lastUpdate;
        float gravityFactor;
        long lifeTime;
        boolean hasGravity;
        float friction;
        int colorArgb;

        Particle(Vec3d origin, float ox, float oy, float oz, float vx, float vy, float vz, int colorArgb, boolean hasGravity) {
            this.x = origin.x + ox;
            this.y = origin.y + oy;
            this.z = origin.z + oz;

            this.vx = vx;
            this.vy = vy;
            this.vz = vz;

            this.colorArgb = colorArgb;
            this.startTime = this.lastUpdate = System.currentTimeMillis();
            this.hasGravity = hasGravity;

            this.gravityFactor = 0.005f + RANDOM.nextFloat() * 0.005f;
            this.lifeTime = 1508L + RANDOM.nextInt(1508);
            this.friction = hasGravity ? 0.999f : 0.995f;
        }

        void update() {
            float bounceFriction = 0.5f;
            long current = System.currentTimeMillis();
            float delta = (float) (current - this.lastUpdate) / 16.67f;
            this.lastUpdate = current;

            if (delta > 5.0f) delta = 5.0f;

            if (this.hasGravity) {
                this.vy -= this.gravityFactor * delta;
            }

            float drag = (float) Math.pow(this.friction, delta);
            this.vx *= drag;
            this.vy *= drag;
            this.vz *= drag;

            double nextX = this.x + (this.vx * delta);
            double nextY = this.y + (this.vy * delta);
            double nextZ = this.z + (this.vz * delta);

            if (this.hasGravity && mc.world != null) {
                if (!mc.world.getBlockState(BlockPos.ofFloored(this.x, nextY - 0.05, this.z)).isAir()) {
                    this.vy = -this.vy * bounceFriction;
                    nextY = this.y;
                }
                if (!mc.world.getBlockState(BlockPos.ofFloored(nextX, this.y, this.z)).isAir()) {
                    this.vx = -this.vx * bounceFriction;
                    nextX = this.x;
                }
                if (!mc.world.getBlockState(BlockPos.ofFloored(this.x, this.y, nextZ)).isAir()) {
                    this.vz = -this.vz * bounceFriction;
                    nextZ = this.z;
                }
            }

            if (Math.abs(this.vy) <= 1.0E-4f) {
                this.vx = 0.0f;
                this.vz = 0.0f;
            }

            this.x = nextX;
            this.y = nextY;
            this.z = nextZ;
        }

        float getAlpha() {
            float p = MathHelper.clamp((float) (System.currentTimeMillis() - this.startTime) / (float) this.lifeTime, 0.0f, 1.0f);
            return 1.0f - p;
        }

        boolean isDead() {
            return System.currentTimeMillis() - this.startTime > this.lifeTime;
        }

        void render(BufferBuilder builder, MatrixStack ms, Camera camera) {
            float alpha = getAlpha();
            if (alpha <= 0.001f) return;

            int c = withAlphaMul(this.colorArgb, 0.9f * alpha);
            float size = 0.11f;

            ms.push();
            ms.translate(this.x, this.y, this.z);
            ms.multiply(camera.getRotation());
            drawBillboardQuad(builder, ms, size, c);
            ms.pop();
        }
    }

    private static final class DeathEffect {
        final Vec3d basePos;
        final int colorArgb;
        final long startTime;
        final List<Vec3d> poses = new ArrayList<>();

        DeathEffect(Vec3d pos, int colorArgb) {
            this.basePos = pos;
            this.colorArgb = colorArgb;
            this.startTime = System.currentTimeMillis();

            Vec3d last = pos;
            for (int i = 0; i < 200; i++) {
                last = last.add(rand(-0.25f, 0.25f), 0.25, rand(-0.25f, 0.25f));
                poses.add(last);
            }
        }

        static float rand(float a, float b) {
            return a + RANDOM.nextFloat() * (b - a);
        }

        boolean isFinished() {
            return System.currentTimeMillis() - startTime >= 750L;
        }

        void render(BufferBuilder builder, MatrixStack ms, Camera camera) {
            long tMs = System.currentTimeMillis() - startTime;

            float appearT = MathHelper.clamp(tMs / 500.0f, 0.0f, 1.0f);
            float appear = easeOutBack(appearT);

            float fadeT = 0.0f;
            if (tMs >= 500L) fadeT = MathHelper.clamp((tMs - 500L) / 250.0f, 0.0f, 1.0f);
            float fade = 1.0f - fadeT;

            float a = appear * fade;
            if (a <= 0.001f) return;

            for (Vec3d p : poses) {
                float size = (float) (0.35 + 1.65 * (p.y - this.basePos.y) / 50.0);
                int c = withAlphaMul(this.colorArgb, a * 0.42f);

                ms.push();
                ms.translate(p.x, p.y, p.z);
                ms.multiply(camera.getRotation());
                drawBillboardQuad(builder, ms, size, c);
                ms.pop();
            }
        }
    }
}