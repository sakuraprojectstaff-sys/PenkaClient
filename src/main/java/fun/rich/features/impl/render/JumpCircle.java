package fun.rich.features.impl.render;

import fun.rich.events.player.JumpEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.math.Counter;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.Camera;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Vector4i;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class JumpCircle extends Module {

    final Identifier texClient = Identifier.of("textures/circle2.png");

    final Identifier texGalaxy = Identifier.of("mre", "textures/circle3.png");
    final Identifier texSharingan = Identifier.of("mre", "textures/sharingan.png");

    final Identifier texPortal1 = Identifier.of("mre", "textures/portal1.png");
    final Identifier texPortal2 = Identifier.of("mre", "textures/portal2.png");
    final Identifier texPortal3 = Identifier.of("mre", "textures/portal3.png");

    final Identifier texCircle1 = Identifier.of("mre", "textures/circle1.png");
    final Identifier texCircle2 = Identifier.of("mre", "textures/circle2.png");
    final Identifier texCircle4 = Identifier.of("mre", "textures/circle4.png");
    final Identifier texCircle5 = Identifier.of("mre", "textures/circle5.png");
    final Identifier texCircle6 = Identifier.of("mre", "textures/circle6.png");

    final SelectSetting view = new SelectSetting("Вид", "Выбор круга")
            .value(
                    "Клиент",
                    "Галактика",
                    "Шаринган",
                    "Портал 1",
                    "Портал 2",
                    "Портал 3",
                    "Circle 1",
                    "Circle 2",
                    "Circle 4",
                    "Circle 5",
                    "Circle 6",
                    "Волна"
            )
            .selected("Клиент");

    final SliderSettings maxSize = new SliderSettings("Max Size", "Максимальный размер круга").setValue(2.5f).range(1.0f, 5.0f);
    final SliderSettings speed = new SliderSettings("Speed", "Скорость анимации").setValue(1000f).range(500f, 5000f);

    final BooleanSetting portalRotate = new BooleanSetting("Поворот", "Включить поворот портала").setValue(false)
            .visible(this::isPortalView);

    final SliderSettings rotateSpeed = new SliderSettings("Скорость поворота", "Вращение (град/сек)")
            .setValue(1200f).range(0f, 2000f)
            .visible(() -> isGalaxySharinganView() || (isPortalView() && bool(portalRotate)));

    final BooleanSetting dbl = new BooleanSetting("Дабл", "Доп. круги после прыжка").setValue(false)
            .visible(() -> !isWaveView());

    final SliderSettings freqMs = new SliderSettings("Частота", "Интервал появления (мс)")
            .setValue(150f).range(50f, 500f)
            .visible(() -> !isWaveView() && bool(dbl));

    final ColorSetting color = new ColorSetting("Цвет", "Цвет эффекта")
            .value(ColorAssist.getColor(225, 225, 255, 255));

    final SliderSettings waveDurationMs = new SliderSettings("Wave Время", "Время волны (мс)")
            .setValue(1500f).range(500f, 6000f)
            .visible(this::isWaveView);

    final SliderSettings waveRadius = new SliderSettings("Wave Радиус", "Максимальный радиус")
            .setValue(12f).range(4f, 28f)
            .visible(this::isWaveView);

    final SliderSettings waveWidth = new SliderSettings("Wave Толщина", "Толщина (1-2 блока)")
            .setValue(1.35f).range(0.9f, 2.1f)
            .visible(this::isWaveView);

    final SliderSettings waveMaxPerFrame = new SliderSettings("Wave Лимит", "Максимум блоков/кадр")
            .setValue(900f).range(80f, 2600f)
            .visible(this::isWaveView);

    final SliderSettings waveLineBase = new SliderSettings("Wave Линия", "Базовая толщина линии")
            .setValue(1.0f).range(0.3f, 4.0f)
            .visible(this::isWaveView);

    final SliderSettings waveLineBoost = new SliderSettings("Wave Усиление", "Усиление толщины по альфе")
            .setValue(2.2f).range(0.0f, 8.0f)
            .visible(this::isWaveView);

    final SliderSettings waveDetail = new SliderSettings("Wave Детализация", "Сегменты круга")
            .setValue(260f).range(60f, 720f)
            .visible(this::isWaveView);

    final List<Circle> circles = new ArrayList<>();
    final List<WaveEffect> waveEffects = new ArrayList<>();
    final Map<Identifier, Identifier> circleMaskCache = new HashMap<>();

    long lastJumpMs = 0L;
    long nextSpawnMs = 0L;
    String lastJumpMode = "Клиент";
    boolean lastJumpFlat = false;
    long lastJumpLifeMs = 1000L;

    public JumpCircle() {
        super("JumpCircle", "Jump Circle", ModuleCategory.RENDER);
        setup(
                view,
                maxSize,
                speed,
                portalRotate,
                rotateSpeed,
                dbl,
                freqMs,
                color,
                waveDurationMs,
                waveRadius,
                waveWidth,
                waveDetail,
                waveMaxPerFrame,
                waveLineBase,
                waveLineBoost
        );
    }

    @EventHandler
    public void onJump(JumpEvent event) {
        if (mc.player == null || event.getPlayer() != mc.player) return;

        String mode = view.getSelected();

        if (isWaveMode(mode)) {
            BlockPos base = BlockPos.ofFloored(mc.player.getX(), mc.player.getBoundingBox().minY - 0.01, mc.player.getZ());
            addWave(base);
            return;
        }

        boolean flat = isCircleMaskMode(mode);
        long life = flat ? 3000L : (long) speed.getValue();

        double y = mc.player.getBoundingBox().minY + 0.03;
        Vec3d pos = new Vec3d(mc.player.getX(), y, mc.player.getZ());

        circles.add(new Circle(pos, new Counter(), life, mode, flat));

        if (bool(dbl)) {
            long now = System.currentTimeMillis();
            long interval = Math.max(20L, (long) freqMs.getValue());
            lastJumpMs = now;
            nextSpawnMs = now + interval;
            lastJumpMode = mode;
            lastJumpFlat = flat;
            lastJumpLifeMs = life;
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        spawnExtraCircles();
        circles.removeIf(c -> c.timer.passedMs(c.lifeMs));
        cleanupWaves();
        renderCircles();
        renderWaves();
    }

    private void spawnExtraCircles() {
        if (!bool(dbl)) return;
        if (mc.player == null) return;
        if (lastJumpMs <= 0L) return;

        long now = System.currentTimeMillis();
        long interval = Math.max(20L, (long) freqMs.getValue());
        long window = (long) (Math.max(100f, Math.min(2500f, lastJumpLifeMs * 0.75f)));

        if (now - lastJumpMs > window) return;

        while (now >= nextSpawnMs) {
            double y = mc.player.getBoundingBox().minY + 0.03;
            Vec3d pos = new Vec3d(mc.player.getX(), y, mc.player.getZ());
            circles.add(new Circle(pos, new Counter(), lastJumpLifeMs, lastJumpMode, lastJumpFlat));
            nextSpawnMs += interval;
        }
    }

    private void renderCircles() {
        if (circles.isEmpty()) return;
        for (Circle c : circles) renderBillboardCircle(c);
    }

    private boolean renderCutBlackForMode(String mode) {
        return !mode.startsWith("Джамп");
    }

    private void renderBillboardCircle(Circle c) {
        float lifeTime = (float) c.timer.getPassedTimeMs();
        float maxTime = (float) c.lifeMs;
        float progress = Math.min(lifeTime / Math.max(1f, maxTime), 1f);
        if (progress >= 1f) return;

        boolean spinAnim = isGalaxySharinganMode(c.mode) || isPortalMode(c.mode);
        boolean doRotate = isGalaxySharinganMode(c.mode) || (isPortalMode(c.mode) && bool(portalRotate));

        float scale;
        float alpha;
        float spinDeg = 0f;

        if (spinAnim) {
            float tt = progress;
            float k;
            if (tt < 0.5f) k = sineOut(clamp01(tt / 0.5f));
            else k = 1f - backIn(clamp01((tt - 0.5f) / 0.5f));

            scale = k * maxSize.getValue();

            float hold = 0.43f;
            if (tt <= hold) alpha = 1f;
            else alpha = 1f - (tt - hold) / (1f - hold);
            alpha = clamp01(alpha);

            if (doRotate) {
                float degPerSec = rotateSpeed.getValue();
                spinDeg = (lifeTime / 1000.0f) * degPerSec * k;
            }
        } else {
            float easedProgress = bounceOut(progress);
            scale = easedProgress * maxSize.getValue();

            float fadeInDuration = 0.15f;
            float glowStart = 0.65f;
            float fadeOutStart = 0.85f;
            float a;

            if (progress < fadeInDuration) {
                a = progress / fadeInDuration;
            } else if (progress >= fadeOutStart) {
                float fadeOutProgress = (progress - fadeOutStart) / (1f - fadeOutStart);
                a = 1f - fadeOutProgress;

                if (progress > glowStart) {
                    float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
                    float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
                    a += glowPulse * (1f - fadeOutProgress);
                }
            } else if (progress > glowStart) {
                float glowProgress = (progress - glowStart) / (fadeOutStart - glowStart);
                float glowPulse = (float) (Math.sin(glowProgress * Math.PI * 3) * 0.3 + 0.3);
                a = 1f + glowPulse;
            } else {
                a = 1f;
            }

            alpha = clamp01(a);
        }

        if (scale <= 0.0001f || alpha <= 0.001f) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getPos();
        Vec3d circlePos = c.pos;

        MatrixStack ms = new MatrixStack();

        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180.0F));

        ms.translate(circlePos.x - cameraPos.x, circlePos.y - cameraPos.y, circlePos.z - cameraPos.z);

        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        if (doRotate) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinDeg));

        Identifier tex = pickPreparedTexture(c.mode);
        Vector4i colors = pickMainColors(alpha);

        Render3D.drawTexture(ms.peek(), tex, -scale / 2f, -scale / 2f, scale, scale, colors, renderCutBlackForMode(c.mode));
    }

    private void addWave(BlockPos pos) {
        if (mc.world == null) return;
        waveEffects.add(new WaveEffect(pos, System.currentTimeMillis()));
    }

    private void cleanupWaves() {
        if (waveEffects.isEmpty()) return;
        Iterator<WaveEffect> it = waveEffects.iterator();
        while (it.hasNext()) {
            WaveEffect w = it.next();
            if (w.isExpired()) it.remove();
        }
    }

    private void renderWaves() {
        if (mc.world == null) return;
        if (waveEffects.isEmpty()) return;
        for (WaveEffect w : waveEffects) w.render();
    }

    private int pickWaveColor(float alpha) {
        return ColorAssist.multAlpha(color.getColor(), clamp01(alpha));
    }

    private class WaveEffect {
        final BlockPos centerPos;
        final long startTime;

        WaveEffect(BlockPos centerPos, long startTime) {
            this.centerPos = centerPos;
            this.startTime = startTime;
        }

        boolean isExpired() {
            long dur = (long) waveDurationMs.getValue();
            return System.currentTimeMillis() - startTime > Math.max(1L, dur);
        }

        void render() {
            if (mc.world == null) return;

            long dur = (long) waveDurationMs.getValue();
            float maxRad = waveRadius.getValue();
            float width = waveWidth.getValue();

            long elapsed = System.currentTimeMillis() - startTime;
            float progress = (float) elapsed / Math.max(1f, (float) dur);
            if (progress >= 1f) return;

            float pr = sineOut(clamp01(progress));
            float currentRadius = pr * maxRad;

            float globalAlpha = (float) Math.pow(1.0f - progress, 0.65);

            int maxPerFrame = Math.max(1, (int) waveMaxPerFrame.getValue());
            int seg = Math.max(24, (int) waveDetail.getValue());

            boolean twoLayers = width >= 1.15f;

            Set<Long> uniq = new HashSet<>(Math.min(16384, seg * (twoLayers ? 2 : 1)));

            addRingOffsets(uniq, currentRadius, seg);
            if (twoLayers) addRingOffsets(uniq, currentRadius + 1.0f, seg);

            int rendered = 0;
            VoxelShape full = VoxelShapes.fullCube();

            for (long key : uniq) {
                if (rendered >= maxPerFrame) return;

                int ox = (int) (key >> 32);
                int oz = (int) key;

                float dx = ox + 0.5f;
                float dz = oz + 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dz * dz);

                float localAlpha = 1.0f - Math.abs(dist - currentRadius) / Math.max(0.0001f, width);
                localAlpha = Math.max(0, Math.min(1, localAlpha)) * globalAlpha;
                if (localAlpha <= 0.04f) continue;

                BlockPos renderPos = new BlockPos(centerPos.getX() + ox, centerPos.getY(), centerPos.getZ() + oz);

                BlockState state = mc.world.getBlockState(renderPos);
                if (state.isAir()) continue;
                if (state.getCollisionShape(mc.world, renderPos).isEmpty()) continue;

                int base = pickWaveColor(localAlpha);
                int a = (base >>> 24) & 0xFF;
                if (a <= 0) continue;

                float lineWidth = waveLineBase.getValue() + (localAlpha * waveLineBoost.getValue());

                try {
                    Render3D.drawShapeAlternative(
                            renderPos,
                            full,
                            base,
                            lineWidth,
                            true,
                            true
                    );
                } catch (Exception ignored) {
                }

                rendered++;
            }
        }

        void addRingOffsets(Set<Long> out, float radius, int seg) {
            if (radius <= 0.25f) return;

            double twoPi = Math.PI * 2.0;
            int firstX = 0;
            int firstZ = 0;
            int lastX = 0;
            int lastZ = 0;
            boolean hasLast = false;

            for (int i = 0; i <= seg; i++) {
                double a = (i / (double) seg) * twoPi;
                int x = (int) Math.round(Math.cos(a) * radius);
                int z = (int) Math.round(Math.sin(a) * radius);

                if (!hasLast) {
                    firstX = x;
                    firstZ = z;
                    lastX = x;
                    lastZ = z;
                    hasLast = true;
                    put(out, x, z);
                    continue;
                }

                addLine(out, lastX, lastZ, x, z);

                lastX = x;
                lastZ = z;
            }

            addLine(out, lastX, lastZ, firstX, firstZ);
        }

        void addLine(Set<Long> out, int x0, int z0, int x1, int z1) {
            int dx = Math.abs(x1 - x0);
            int dz = Math.abs(z1 - z0);
            int sx = x0 < x1 ? 1 : -1;
            int sz = z0 < z1 ? 1 : -1;
            int err = dx - dz;

            int x = x0;
            int z = z0;

            while (true) {
                put(out, x, z);
                if (x == x1 && z == z1) break;
                int e2 = err << 1;
                if (e2 > -dz) {
                    err -= dz;
                    x += sx;
                }
                if (e2 < dx) {
                    err += dx;
                    z += sz;
                }
            }
        }

        void put(Set<Long> out, int x, int z) {
            out.add((((long) x) << 32) | (z & 0xFFFFFFFFL));
        }
    }

    private boolean isGalaxySharinganView() {
        return view.isSelected("Галактика") || view.isSelected("Шаринган");
    }

    private boolean isPortalView() {
        return view.isSelected("Портал 1") || view.isSelected("Портал 2") || view.isSelected("Портал 3");
    }

    private boolean isWaveView() {
        return view.isSelected("Волна");
    }

    private boolean isWaveMode(String mode) {
        return "Волна".equals(mode);
    }

    private boolean isGalaxySharinganMode(String mode) {
        return "Галактика".equals(mode) || "Шаринган".equals(mode);
    }

    private boolean isPortalMode(String mode) {
        return "Портал 1".equals(mode) || "Портал 2".equals(mode) || "Портал 3".equals(mode);
    }

    private boolean isCircleMaskMode(String mode) {
        return "Circle 1".equals(mode) || "Circle 2".equals(mode) || "Circle 4".equals(mode) || "Circle 5".equals(mode) || "Circle 6".equals(mode);
    }

    private Identifier pickTexture(String mode) {
        if ("Галактика".equals(mode)) return texGalaxy;
        if ("Шаринган".equals(mode)) return texSharingan;

        if ("Портал 1".equals(mode)) return texPortal1;
        if ("Портал 2".equals(mode)) return texPortal2;
        if ("Портал 3".equals(mode)) return texPortal3;

        if ("Circle 1".equals(mode)) return texCircle1;
        if ("Circle 2".equals(mode)) return texCircle2;
        if ("Circle 4".equals(mode)) return texCircle4;
        if ("Circle 5".equals(mode)) return texCircle5;
        if ("Circle 6".equals(mode)) return texCircle6;

        return texClient;
    }

    private boolean isCircleMaskTexture(Identifier id) {
        return texCircle1.equals(id) || texCircle2.equals(id) || texCircle4.equals(id) || texCircle5.equals(id) || texCircle6.equals(id);
    }

    private Identifier buildMaskId(Identifier src) {
        String p = src.getPath();
        if (p.endsWith(".png")) p = p.substring(0, p.length() - 4);
        String outPath = p + "_mask";
        return Identifier.of(src.getNamespace(), outPath);
    }

    private Identifier pickPreparedTexture(String mode) {
        Identifier src = pickTexture(mode);
        if (src == null) return texClient;
        if (!isCircleMaskTexture(src)) return src;

        Identifier cached = circleMaskCache.get(src);
        if (cached != null) return cached;

        Identifier out = buildMaskId(src);

        try {
            var opt = mc.getResourceManager().getResource(src);
            if (opt.isEmpty()) return src;

            NativeImage img;
            try (InputStream in = opt.get().getInputStream()) {
                img = NativeImage.read(in);
            }

            int w = img.getWidth();
            int h = img.getHeight();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int c = img.getColorArgb(x, y);
                    int a = (c >>> 24) & 0xFF;
                    if (a == 0) continue;
                    img.setColorArgb(x, y, (a << 24) | 0x00FFFFFF);
                }
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            mc.getTextureManager().registerTexture(out, tex);
            circleMaskCache.put(src, out);
            return out;
        } catch (Exception ignored) {
            return src;
        }
    }

    private Vector4i pickMainColors(float alpha) {
        int c = ColorAssist.multAlpha(color.getColor(), alpha);
        return new Vector4i(c, c, c, c);
    }

    private float bounceOut(float value) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (value < 1.0f / d1) {
            return n1 * value * value;
        } else if (value < 2.0f / d1) {
            return n1 * (value -= 1.5f / d1) * value + 0.75f;
        } else if (value < 2.5f / d1) {
            return n1 * (value -= 2.25f / d1) * value + 0.9375f;
        } else {
            return n1 * (value -= 2.625f / d1) * value + 0.984375f;
        }
    }

    private float sineOut(float t) {
        return (float) Math.sin((t * Math.PI) * 0.5);
    }

    private float backIn(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        return c3 * t * t * t - c1 * t * t;
    }

    private float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static boolean bool(Object setting) {
        if (setting == null) return false;

        try {
            java.lang.reflect.Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            java.lang.reflect.Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            java.lang.reflect.Method m = setting.getClass().getMethod("isState");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            java.lang.reflect.Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        for (String fn : new String[]{"value", "state", "enabled"}) {
            try {
                java.lang.reflect.Field f = setting.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(setting);
                if (v instanceof Boolean b) return b;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public static final class Circle {
        final Vec3d pos;
        final Counter timer;
        final long lifeMs;
        final String mode;
        final boolean flat;

        public Circle(Vec3d pos, Counter timer, long lifeMs, String mode, boolean flat) {
            this.pos = pos;
            this.timer = timer;
            this.lifeMs = lifeMs;
            this.mode = mode;
            this.flat = flat;
        }
    }
}
