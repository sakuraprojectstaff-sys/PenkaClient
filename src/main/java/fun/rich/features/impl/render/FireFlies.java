package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FireFlies extends Module {

    private static final long LIFE_MS = 8000L;
    private static final long FADE_MS = 500L;
    private static final double MAX_DIST = 60.0D;

    private static final Identifier TEX_ONE = Identifier.of("minecraft", "textures/particles/firefly.png");
    private static final Identifier TEX_TWO = Identifier.of("minecraft", "textures/particles/firefly2.png");

    private final List<FireFly> particles = new ArrayList<>();

    private final Object count;
    private final Object speed;
    private final Object radius;
    private final Object trailLength;
    private final Object randomColor;
    private final Object staticColor;
    private final Object textureTwo;

    public FireFlies() {
        super("Fire Flies", "Fire Flies", ModuleCategory.RENDER);

        count = newSlider("Количество", 100.0F, 10.0F, 300.0F, 10.0F);
        speed = newSlider("Скорость", 0.15F, 0.05F, 0.5F, 0.05F);
        radius = newSlider("Радиус спавна", 25.0F, 10.0F, 50.0F, 5.0F);
        trailLength = newSlider("Длина шлейфа", 20.0F, 5.0F, 40.0F, 5.0F);
        randomColor = newBoolean("Рандомный цвет", true);
        staticColor = newColor("Цвет", 0xFFB6FF4A);
        textureTwo = newBoolean("Текстура 2", false);

        setupCompat(count, speed, radius, trailLength, randomColor, staticColor, textureTwo);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        Vec3d playerPos = mc.player.getPos();
        particles.removeIf(p -> p.ageMs() >= LIFE_MS || p.position.distanceTo(playerPos) >= MAX_DIST);

        int wantCount = clampInt(Math.round(f(count, 100.0F)), 1, 300);

        if (particles.size() > wantCount) {
            int kill = particles.size() - wantCount;
            for (int i = 0; i < kill; i++) particles.remove(0);
        }

        int add = wantCount - particles.size();
        if (add > 0) {
            int perFrame = Math.min(add, 32);
            for (int i = 0; i < perFrame; i++) spawnParticle();
        }

        MatrixStack matrix = stackOf(e);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, boolValue(textureTwo, false) ? TEX_TWO : TEX_ONE);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int maxTrail = clampInt(Math.round(f(trailLength, 20.0F)), 1, 80);
        float spd = Math.max(0.01F, f(speed, 0.15F));
        float rad = Math.max(5.0F, f(radius, 25.0F));
        boolean rnd = boolValue(randomColor, true);

        int picked = colorValue(staticColor, 0xFFB6FF4A);

        for (int i = 0; i < particles.size(); i++) {
            FireFly p = particles.get(i);
            p.update(playerPos, spd, rad, maxTrail);

            int baseAlpha = baseAlpha(p.ageMs());
            if (baseAlpha <= 0) continue;

            int col = rnd ? p.color : picked;
            renderTrail(matrix, bb, p, col, baseAlpha);
            renderParticle(matrix, bb, p, col, baseAlpha);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private void spawnParticle() {
        if (mc.player == null) return;

        double rad = Math.max(10.0D, f(radius, 25.0F));
        double dist = random(5.0D, rad);
        double yawRad = Math.toRadians(random(0.0D, 360.0D));
        double xOff = -Math.sin(yawRad) * dist;
        double zOff = Math.cos(yawRad) * dist;
        double yOff = random(-5.0D, 10.0D);

        double spd = Math.max(0.01D, f(speed, 0.15F));
        double velYaw = Math.toRadians(random(0.0D, 360.0D));
        double velPitch = Math.toRadians(random(-30.0D, 30.0D));

        Vec3d vel = new Vec3d(
                -Math.sin(velYaw) * Math.cos(velPitch) * spd,
                Math.sin(velPitch) * spd * 0.5D,
                Math.cos(velYaw) * Math.cos(velPitch) * spd
        );

        int color = hsvHueToRgb((float) ThreadLocalRandom.current().nextDouble(0.0D, 360.0D));
        particles.add(new FireFly(mc.player.getPos().add(xOff, yOff, zOff), vel, particles.size(), color));
    }

    private void renderTrail(MatrixStack matrix, BufferBuilder bb, FireFly p, int color, int baseAlpha) {
        List<Vec3d> trail = p.trail;
        int n = trail.size();
        if (n < 2) return;

        for (int i = 0; i < n; i++) {
            Vec3d pos = trail.get(i);
            float fade = (float) i / (float) (n - 1);
            float size = 0.15F * fade;

            int ta = (int) (baseAlpha * fade * 0.8F);
            if (ta <= 0) continue;

            addQuad(bb, matrix, pos, size, mulAlpha(color, ta));

            if ((i % 3) == 0 && fade > 0.3F) {
                int mini = 2 + ThreadLocalRandom.current().nextInt(3);
                for (int j = 0; j < mini; j++) {
                    Vec3d off = new Vec3d(
                            (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.3D,
                            (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.3D,
                            (ThreadLocalRandom.current().nextDouble() - 0.5D) * 0.3D
                    );
                    float ms = 0.04F + (float) (ThreadLocalRandom.current().nextDouble() * 0.03D);
                    int ma = (int) (ta * 0.6F);
                    if (ma <= 0) continue;
                    addQuad(bb, matrix, pos.add(off), ms, mulAlpha(color, ma));
                }
            }
        }
    }

    private void renderParticle(MatrixStack matrix, BufferBuilder bb, FireFly p, int color, int baseAlpha) {
        int pulseAlpha = pulseAlpha(p.ageMs());
        int finalAlpha = Math.min(baseAlpha, pulseAlpha);
        if (finalAlpha <= 0) return;

        addQuad(bb, matrix, p.position, 0.35F, mulAlpha(color, (int) (finalAlpha * 0.6F)));
        addQuad(bb, matrix, p.position, 0.22F, mulAlpha(color, finalAlpha));
        addQuad(bb, matrix, p.position, 0.10F, mulAlpha(0xFFFFFFFF, finalAlpha));
    }

    private void addQuad(BufferBuilder bb, MatrixStack matrix, Vec3d pos, float size, int color) {
        matrix.push();
        matrix.translate(pos.x, pos.y, pos.z);
        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));

        Matrix4f mat = matrix.peek().getPositionMatrix();

        float r = ((color >> 16) & 255) / 255.0F;
        float g = ((color >> 8) & 255) / 255.0F;
        float b = (color & 255) / 255.0F;
        float a = ((color >>> 24) & 255) / 255.0F;

        bb.vertex(mat, -size, -size, 0.0F).texture(0.0F, 0.0F).color(r, g, b, a);
        bb.vertex(mat, -size, size, 0.0F).texture(0.0F, 1.0F).color(r, g, b, a);
        bb.vertex(mat, size, size, 0.0F).texture(1.0F, 1.0F).color(r, g, b, a);
        bb.vertex(mat, size, -size, 0.0F).texture(1.0F, 0.0F).color(r, g, b, a);

        matrix.pop();
    }

    private static int baseAlpha(long ageMs) {
        float a;
        if (ageMs < FADE_MS) a = easeQuadOut((float) ageMs / (float) FADE_MS);
        else if (ageMs > (LIFE_MS - FADE_MS)) a = easeQuadOut((float) (LIFE_MS - ageMs) / (float) FADE_MS);
        else a = 1.0F;
        return clampInt(Math.round(MathHelper.clamp(a, 0.0F, 1.0F) * 255.0F), 0, 255);
    }

    private static int pulseAlpha(long ageMs) {
        double pulse = (Math.sin((double) ageMs / 300.0D) + 1.0D) * 0.5D;
        return clampInt((int) (pulse * 255.0D), 0, 255);
    }

    private static float easeQuadOut(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t);
    }

    private static int mulAlpha(int argb, int alpha255) {
        int a = clampInt(alpha255, 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int hsvHueToRgb(float hueDeg) {
        float h = ((hueDeg % 360.0F) + 360.0F) % 360.0F;
        float c = 1.0F;
        float x = c * (1.0F - Math.abs(((h / 60.0F) % 2.0F) - 1.0F));
        float m = 0.0F;

        float r1, g1, b1;
        if (h < 60.0F) { r1 = c; g1 = x; b1 = 0.0F; }
        else if (h < 120.0F) { r1 = x; g1 = c; b1 = 0.0F; }
        else if (h < 180.0F) { r1 = 0.0F; g1 = c; b1 = x; }
        else if (h < 240.0F) { r1 = 0.0F; g1 = x; b1 = c; }
        else if (h < 300.0F) { r1 = x; g1 = 0.0F; b1 = c; }
        else { r1 = c; g1 = 0.0F; b1 = x; }

        int r = clampInt((int) ((r1 + m) * 255.0F), 0, 255);
        int g = clampInt((int) ((g1 + m) * 255.0F), 0, 255);
        int b = clampInt((int) ((b1 + m) * 255.0F), 0, 255);
        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    private double random(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static int clampInt(int v, int mn, int mx) {
        return Math.max(mn, Math.min(mx, v));
    }

    private static MatrixStack stackOf(Object e) {
        try {
            Method m = e.getClass().getMethod("getStack");
            Object r = m.invoke(e);
            if (r instanceof MatrixStack ms) return ms;
        } catch (Throwable ignored) {
        }
        try {
            Field f = e.getClass().getDeclaredField("stack");
            f.setAccessible(true);
            Object r = f.get(e);
            if (r instanceof MatrixStack ms) return ms;
        } catch (Throwable ignored) {
        }
        return new MatrixStack();
    }

    private static float f(Object setting, float fallback) {
        if (setting == null) return fallback;
        try {
            for (String n : new String[]{"getValueFloat", "getFloatValue", "getValue", "getCurrent", "get"}) {
                try {
                    Method m = setting.getClass().getMethod(n);
                    Object r = m.invoke(setting);
                    if (r instanceof Number num) return num.floatValue();
                } catch (Throwable ignored) {
                }
            }
            for (String n : new String[]{"value", "current", "val"}) {
                try {
                    Field f = setting.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    Object r = f.get(setting);
                    if (r instanceof Number num) return num.floatValue();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static boolean boolValue(Object setting, boolean fallback) {
        if (setting == null) return fallback;
        try {
            for (String n : new String[]{"getValue", "isValue", "isEnabled", "isState", "get", "value", "enabled", "state"}) {
                try {
                    Method m = setting.getClass().getMethod(n);
                    if (m.getParameterCount() != 0) continue;
                    Object r = m.invoke(setting);
                    Boolean v = asBool(r);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
            for (String n : new String[]{"value", "state", "enabled", "toggled", "on"}) {
                try {
                    Field f = setting.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    Boolean v = asBool(f.get(setting));
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static int colorValue(Object setting, int fallback) {
        if (setting == null) return fallback;
        try {
            for (String n : new String[]{"getColor", "getRGB", "getColorValue", "getValue", "get", "color", "rgb", "value"}) {
                try {
                    Method m = setting.getClass().getMethod(n);
                    if (m.getParameterCount() != 0) continue;
                    int v = asColorInt(m.invoke(setting));
                    if (v != Integer.MIN_VALUE) return v;
                } catch (Throwable ignored) {
                }
            }
            for (String n : new String[]{"color", "rgb", "value", "colorValue", "current", "val", "rgba"}) {
                try {
                    Field f = setting.getClass().getDeclaredField(n);
                    f.setAccessible(true);
                    int v = asColorInt(f.get(setting));
                    if (v != Integer.MIN_VALUE) return v;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    private static Boolean asBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        if (o instanceof String s) {
            String t = s.trim().toLowerCase();
            if (t.equals("true") || t.equals("on") || t.equals("1") || t.equals("yes")) return true;
            if (t.equals("false") || t.equals("off") || t.equals("0") || t.equals("no")) return false;
        }
        return null;
    }

    private static int asColorInt(Object o) {
        if (o == null) return Integer.MIN_VALUE;
        if (o instanceof Number n) return n.intValue();

        Class<?> c = o.getClass();

        if (c.isArray()) {
            int len = Array.getLength(o);
            if (len >= 3) {
                Object a0 = Array.get(o, 0);
                Object a1 = Array.get(o, 1);
                Object a2 = Array.get(o, 2);
                Object a3 = len >= 4 ? Array.get(o, 3) : null;

                Integer r = asByte(a0);
                Integer g = asByte(a1);
                Integer b = asByte(a2);
                Integer a = a3 != null ? asByte(a3) : 255;

                if (r != null && g != null && b != null) {
                    return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
                }

                Float rf = asUnit(a0);
                Float gf = asUnit(a1);
                Float bf = asUnit(a2);
                Float af = a3 != null ? asUnit(a3) : 1.0F;

                if (rf != null && gf != null && bf != null) {
                    int ri = clampInt(Math.round(rf * 255.0F), 0, 255);
                    int gi = clampInt(Math.round(gf * 255.0F), 0, 255);
                    int bi = clampInt(Math.round(bf * 255.0F), 0, 255);
                    int ai = clampInt(Math.round(af * 255.0F), 0, 255);
                    return (ai << 24) | (ri << 16) | (gi << 8) | bi;
                }
            }
        }

        try {
            Method m = c.getMethod("getRGB");
            if (m.getParameterCount() == 0) {
                Object r = m.invoke(o);
                if (r instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            Method mr = c.getMethod("getRed");
            Method mg = c.getMethod("getGreen");
            Method mb = c.getMethod("getBlue");
            Method ma = null;
            try { ma = c.getMethod("getAlpha"); } catch (Throwable ignored) {}

            Object rr = mr.invoke(o);
            Object gg = mg.invoke(o);
            Object bb = mb.invoke(o);
            Object aa = ma != null ? ma.invoke(o) : 255;

            Integer r = asByte(rr);
            Integer g = asByte(gg);
            Integer b = asByte(bb);
            Integer a = asByte(aa);
            if (r != null && g != null && b != null && a != null) {
                return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        } catch (Throwable ignored) {
        }

        try {
            Field fr = c.getDeclaredField("r");
            Field fg = c.getDeclaredField("g");
            Field fb = c.getDeclaredField("b");
            fr.setAccessible(true);
            fg.setAccessible(true);
            fb.setAccessible(true);
            Integer r = asByte(fr.get(o));
            Integer g = asByte(fg.get(o));
            Integer b = asByte(fb.get(o));
            if (r != null && g != null && b != null) return (255 << 24) | (r << 16) | (g << 8) | b;
        } catch (Throwable ignored) {
        }

        try {
            Field fr = c.getDeclaredField("red");
            Field fg = c.getDeclaredField("green");
            Field fb = c.getDeclaredField("blue");
            Field fa = null;
            try { fa = c.getDeclaredField("alpha"); } catch (Throwable ignored) {}
            fr.setAccessible(true);
            fg.setAccessible(true);
            fb.setAccessible(true);
            if (fa != null) fa.setAccessible(true);

            Integer r = asByte(fr.get(o));
            Integer g = asByte(fg.get(o));
            Integer b = asByte(fb.get(o));
            Integer a = fa != null ? asByte(fa.get(o)) : 255;
            if (r != null && g != null && b != null && a != null) {
                return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
            }
        } catch (Throwable ignored) {
        }

        return Integer.MIN_VALUE;
    }

    private static Integer asByte(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            int v = n.intValue();
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            return v;
        }
        if (o instanceof String s) {
            try {
                int v = Integer.parseInt(s.trim());
                if (v < 0) v = 0;
                if (v > 255) v = 255;
                return v;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Float asUnit(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            float v = n.floatValue();
            if (v >= 0.0F && v <= 1.0F) return v;
            if (v >= 0.0F && v <= 255.0F) return v / 255.0F;
            return null;
        }
        if (o instanceof String s) {
            try {
                float v = Float.parseFloat(s.trim().replace(',', '.'));
                if (v >= 0.0F && v <= 1.0F) return v;
                if (v >= 0.0F && v <= 255.0F) return v / 255.0F;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object newSlider(String name, float def, float min, float max, float step) {
        Object o = newSetting("fun.rich.features.module.setting.implement.SliderSettings", name, def, min, max, step);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSetting", name, def, min, max, step);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSettings", name, def);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSetting", name, def);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSettings", name, "");
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSetting", name, "");
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSettings", name);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.SliderSetting", name);
        applySliderBounds(o, def, min, max, step);
        return o;
    }

    private static Object newBoolean(String name, boolean def) {
        Object o = newSetting("fun.rich.features.module.setting.implement.BooleanSetting", name, def);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.BooleanSetting", name, "");
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.BooleanSetting", name);
        applyBooleanDefault(o, def);
        return o;
    }

    private static Object newColor(String name, int def) {
        Object o = newSetting("fun.rich.features.module.setting.implement.ColorSetting", name, def);
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.ColorSetting", name, "");
        if (o == null) o = newSetting("fun.rich.features.module.setting.implement.ColorSetting", name);
        applyColorDefault(o, def);
        return o;
    }

    private static Object newSetting(String fqcn, Object... args) {
        try {
            Class<?> c = Class.forName(fqcn);
            Constructor<?>[] ctors = c.getDeclaredConstructors();
            for (Constructor<?> ctor : ctors) {
                Class<?>[] pt = ctor.getParameterTypes();
                if (pt.length != args.length) continue;
                Object[] packed = new Object[args.length];
                boolean ok = true;
                for (int i = 0; i < pt.length; i++) {
                    Object a = args[i];
                    Class<?> p = pt[i];
                    Object v = adaptArg(p, a);
                    if (v == null && a != null) { ok = false; break; }
                    packed[i] = v;
                }
                if (!ok) continue;
                ctor.setAccessible(true);
                return ctor.newInstance(packed);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object adaptArg(Class<?> p, Object a) {
        if (a == null) return null;
        if (p.isInstance(a)) return a;

        if (p == float.class || p == Float.class) {
            if (a instanceof Number n) return n.floatValue();
        }
        if (p == double.class || p == Double.class) {
            if (a instanceof Number n) return n.doubleValue();
        }
        if (p == int.class || p == Integer.class) {
            if (a instanceof Number n) return n.intValue();
        }
        if (p == long.class || p == Long.class) {
            if (a instanceof Number n) return n.longValue();
        }
        if (p == boolean.class || p == Boolean.class) {
            if (a instanceof Boolean b) return b;
        }
        if (p == String.class) return String.valueOf(a);

        return null;
    }

    private static void applySliderBounds(Object o, float def, float min, float max, float step) {
        if (o == null) return;
        invokeMaybe(o, "setMin", min);
        invokeMaybe(o, "setMax", max);
        invokeMaybe(o, "setStep", step);
        invokeMaybe(o, "setIncrement", step);
        invokeMaybe(o, "setValue", def);
        invokeMaybe(o, "setCurrent", def);
        setFieldMaybe(o, "min", min);
        setFieldMaybe(o, "max", max);
        setFieldMaybe(o, "inc", step);
        setFieldMaybe(o, "step", step);
        setFieldMaybe(o, "value", def);
        setFieldMaybe(o, "current", def);
    }

    private static void applyBooleanDefault(Object o, boolean def) {
        if (o == null) return;
        invokeMaybe(o, "setValue", def);
        invokeMaybe(o, "setEnabled", def);
        invokeMaybe(o, "setState", def);
        setFieldMaybe(o, "value", def);
        setFieldMaybe(o, "enabled", def);
        setFieldMaybe(o, "state", def);
    }

    private static void applyColorDefault(Object o, int def) {
        if (o == null) return;
        invokeMaybe(o, "setColor", def);
        invokeMaybe(o, "setValue", def);
        invokeMaybe(o, "setRGB", def);
        setFieldMaybe(o, "color", def);
        setFieldMaybe(o, "rgb", def);
        setFieldMaybe(o, "value", def);
        setFieldMaybe(o, "colorValue", def);
    }

    private static void invokeMaybe(Object o, String name, Object arg) {
        try {
            for (Method m : o.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                Object v = adaptArg(m.getParameterTypes()[0], arg);
                if (v == null && arg != null) continue;
                m.setAccessible(true);
                m.invoke(o, v);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setFieldMaybe(Object o, String field, Object val) {
        try {
            Field f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            Object v = adaptArg(f.getType(), val);
            if (v == null && val != null) return;
            f.set(o, v);
        } catch (Throwable ignored) {
        }
    }

    private void setupCompat(Object... settings) {
        try {
            Class<?> cls = getClass();
            while (cls != null) {
                for (Method m : cls.getDeclaredMethods()) {
                    if (!m.getName().equals("setup")) continue;
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length != 1) continue;
                    if (!pt[0].isArray()) continue;
                    Class<?> comp = pt[0].getComponentType();
                    Object arr = Array.newInstance(comp, settings.length);
                    for (int i = 0; i < settings.length; i++) {
                        Object s = settings[i];
                        if (s == null || !comp.isAssignableFrom(s.getClass())) continue;
                        Array.set(arr, i, s);
                    }
                    m.setAccessible(true);
                    m.invoke(this, arr);
                    return;
                }
                cls = cls.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class FireFly {
        private final int index;
        private final long bornMs;
        private long lastMs;
        private final int color;

        private Vec3d position;
        private Vec3d velocity;
        private final List<Vec3d> trail = new ArrayList<>();

        private FireFly(Vec3d position, Vec3d velocity, int index, int color) {
            this.position = position;
            this.velocity = velocity;
            this.index = index;
            this.color = color;
            long now = System.currentTimeMillis();
            this.bornMs = now;
            this.lastMs = now;
            this.trail.add(position);
        }

        private long ageMs() {
            return System.currentTimeMillis() - bornMs;
        }

        private void update(Vec3d playerPos, float speedSetting, float spawnRadius, int maxTrail) {
            long now = System.currentTimeMillis();
            long dms = now - lastMs;
            lastMs = now;

            double dt = MathHelper.clamp(dms / 1000.0D, 0.0D, 0.05D);
            double spd = Math.max(0.01D, speedSetting);
            double maxSpeed = spd * 1.5D;

            Vec3d target = playerPos.add(0.0D, 1.2D, 0.0D);
            Vec3d to = target.subtract(position);
            double dist = to.length();

            double rand = spd * 0.35D;
            velocity = velocity.add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5D) * rand * dt,
                    (ThreadLocalRandom.current().nextDouble() - 0.5D) * rand * dt,
                    (ThreadLocalRandom.current().nextDouble() - 0.5D) * rand * dt
            );

            if (dist > spawnRadius * 0.9D && dist > 0.001D) {
                Vec3d pull = to.normalize().multiply(spd * 0.6D * dt);
                velocity = velocity.add(pull);
            }

            double bob = Math.sin((double) (bornMs + now + (index * 97L)) / 900.0D) * (spd * 0.2D) * dt;
            velocity = velocity.add(0.0D, bob, 0.0D);

            velocity = new Vec3d(
                    MathHelper.clamp(velocity.x, -maxSpeed, maxSpeed),
                    MathHelper.clamp(velocity.y, -maxSpeed, maxSpeed),
                    MathHelper.clamp(velocity.z, -maxSpeed, maxSpeed)
            );

            position = position.add(velocity);

            trail.add(position);
            while (trail.size() > maxTrail) trail.remove(0);
        }
    }
}
