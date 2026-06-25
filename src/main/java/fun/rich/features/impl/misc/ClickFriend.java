package fun.rich.features.impl.misc;

import antidaunleak.api.annotation.Native;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.events.keyboard.KeyEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClickFriend extends Module {

    static final Identifier TRI_TEX = Identifier.of("minecraft", "textures/particles/triangle.png");

    BindSetting friendBind = new BindSetting("Добавить друга", "Добавить/удалить друга");
    SliderSettings triangleSize = new SliderSettings("Размер треугольника", "0.6");
    SliderSettings addRange = new SliderSettings("Дальность добавления", "6.0");

    @NonFinal boolean latch = false;

    public ClickFriend() {
        super("ClickFriend", "Click Friend", ModuleCategory.MISC);
        applySliderMeta(triangleSize, 0.2F, 2.5F, 0.01F);
        applySliderMeta(addRange, 1.0F, 24.0F, 0.25F);
        setup(friendBind, triangleSize, addRange);
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginMutation)
    public void onKey(KeyEvent e) {
        if (mc.player == null || mc.world == null) return;

        int key = friendBind.getKey();
        if (key == 0) return;

        boolean down = e.isKeyDown(key);

        if (down) {
            if (latch) return;
            latch = true;

            float range = readNumber(addRange, 6.0F);
            if (range < 1.0F) range = 1.0F;
            if (range > 24.0F) range = 24.0F;

            PlayerEntity target = pickPlayerInSight(range);
            if (target == null) return;
            if (target == mc.player) return;

            if (FriendUtils.isFriend(target)) FriendUtils.removeFriend(target);
            else FriendUtils.addFriend(target);
        } else {
            latch = false;
        }
    }

    PlayerEntity pickPlayerInSight(float range) {
        Vec3d start = mc.gameRenderer.getCamera().getPos();
        Vec3d dir = mc.player.getRotationVec(1.0F);
        Vec3d end = start.add(dir.x * range, dir.y * range, dir.z * range);

        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null || p == mc.player) continue;

            Box box = p.getBoundingBox().expand(0.2);
            Optional<Vec3d> hit;
            try {
                hit = box.raycast(start, end);
            } catch (Throwable t) {
                hit = Optional.empty();
            }
            if (hit.isEmpty()) continue;

            Vec3d hp = hit.get();
            double d = hp.squaredDistanceTo(start);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }

        return best;
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack ms = getStack(e);
        if (ms == null) return;

        VertexFormat fmt = vertexFormatPosTexColor();
        if (fmt == null) return;

        float pt = getPartialTicks(e);

        float size = readNumber(triangleSize, 0.6F);
        if (size < 0.2F) size = 0.2F;
        if (size > 2.5F) size = 2.5F;

        float yaw = mc.gameRenderer.getCamera().getYaw();
        float pitch = mc.gameRenderer.getCamera().getPitch();

        double time = (mc.world.getTime() + (double) pt) * 0.14;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        setShaderPositionTexColor();
        RenderSystem.setShaderTexture(0, TRI_TEX);

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null || p == mc.player) continue;
            if (!FriendUtils.isFriend(p)) continue;

            Vec3d pos = p.getLerpedPos(pt);

            float phase = phaseFromUuid(p.getUuid());
            float bob = (float) Math.sin(time + (double) phase) * 0.14F;

            double y = pos.y + p.getHeight() + 0.45 + (double) bob;

            ms.push();
            ms.translate(pos.x, y, pos.z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

            float hs = size * 0.5F;
            Matrix4f mat = ms.peek().getPositionMatrix();

            BufferBuilder buf = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, fmt);

            putVertexGreen(buf, mat, -hs, -hs, 0.0F, 1.0F);
            putVertexGreen(buf, mat, hs, -hs, 1.0F, 1.0F);
            putVertexGreen(buf, mat, hs, hs, 1.0F, 0.0F);
            putVertexGreen(buf, mat, -hs, hs, 0.0F, 0.0F);

            Object built = null;
            try {
                built = buf.end();
                drawWithGlobalProgramCompat(built);
            } finally {
                closeQuiet(built);
            }

            ms.pop();
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    static float phaseFromUuid(UUID id) {
        long v = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        v ^= (v >>> 33);
        v *= 0xff51afd7ed558ccdL;
        v ^= (v >>> 33);
        v *= 0xc4ceb9fe1a85ec53L;
        v ^= (v >>> 33);
        int x = (int) v;
        float f = (x & 0x7fffffff) / 2147483647.0F;
        return f * 6.2831855F;
    }

    static void putVertexGreen(BufferBuilder buf, Matrix4f mat, float x, float y, float u, float v) {
        VertexConsumer vc = buf.vertex(mat, x, y, 0.0F).texture(u, v).color(0, 255, 0, 255);
        endVertexCompat(vc);
    }

    static void endVertexCompat(Object v) {
        if (v == null) return;
        try {
            Method m = v.getClass().getMethod("next");
            m.invoke(v);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Method m = v.getClass().getMethod("endVertex");
            m.invoke(v);
        } catch (Throwable ignored) {
        }
    }

    static VertexFormat vertexFormatPosTexColor() {
        Object a = getStaticField("net.minecraft.client.render.VertexFormats", "POSITION_TEX_COLOR");
        if (a == null) a = getStaticField("net.minecraft.client.render.VertexFormats", "POSITION_TEXTURE_COLOR");
        if (a instanceof VertexFormat) return (VertexFormat) a;
        return null;
    }

    static void setShaderPositionTexColor() {
        Object key = getStaticField("net.minecraft.client.gl.ShaderProgramKeys", "POSITION_TEX_COLOR");
        if (key == null) key = getStaticField("net.minecraft.client.gl.ShaderProgramKeys", "POSITION_TEXTURE_COLOR");
        if (key != null && invokeSetShader(key)) return;

        Supplier<?> sup = gameRendererProgramSupplier("getPositionTexColorProgram");
        if (sup == null) sup = gameRendererProgramSupplier("getPositionTextureColorProgram");
        if (sup != null) invokeSetShader(sup);
    }

    static Object getStaticField(String clazz, String field) {
        try {
            Class<?> c = Class.forName(clazz);
            Field f = c.getField(field);
            return f.get(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Supplier<?> gameRendererProgramSupplier(String method) {
        try {
            Class<?> c = Class.forName("net.minecraft.client.render.GameRenderer");
            Method m = c.getMethod(method);
            return new Supplier<Object>() {
                @Override
                public Object get() {
                    try {
                        return m.invoke(null);
                    } catch (Throwable ignored) {
                    }
                    return null;
                }
            };
        } catch (Throwable ignored) {
        }
        return null;
    }

    static boolean invokeSetShader(Object arg) {
        try {
            for (Method m : RenderSystem.class.getMethods()) {
                if (!m.getName().equals("setShader")) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (!p.isInstance(arg)) continue;
                m.invoke(null, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void drawWithGlobalProgramCompat(Object built) {
        if (built == null) return;
        try {
            for (Method m : BufferRenderer.class.getMethods()) {
                if (!m.getName().equals("drawWithGlobalProgram")) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (!p.isInstance(built)) continue;
                m.invoke(null, built);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    static void closeQuiet(Object o) {
        if (o == null) return;
        if (o instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Throwable ignored) {
            }
            return;
        }
        try {
            Method m = o.getClass().getMethod("close");
            m.invoke(o);
        } catch (Throwable ignored) {
        }
    }

    static MatrixStack getStack(Object e) {
        try {
            Method m = e.getClass().getMethod("getStack");
            Object v = m.invoke(e);
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
        try {
            for (Field f : e.getClass().getDeclaredFields()) {
                if (!f.getType().getName().endsWith("MatrixStack")) continue;
                f.setAccessible(true);
                Object v = f.get(e);
                if (v instanceof MatrixStack) return (MatrixStack) v;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static float getPartialTicks(Object e) {
        try {
            Method m = e.getClass().getMethod("getPartialTicks");
            Object v = m.invoke(e);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Field f = e.getClass().getDeclaredField("partialTicks");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof Number) return ((Number) v).floatValue();
        } catch (Throwable ignored) {
        }
        return 0.0F;
    }

    static void applySliderMeta(Object setting, float min, float max, float inc) {
        setFloatAny(setting, "min", min);
        setFloatAny(setting, "max", max);
        setFloatAny(setting, "minValue", min);
        setFloatAny(setting, "maxValue", max);
        setFloatAny(setting, "minimum", min);
        setFloatAny(setting, "maximum", max);

        invokeFloat(setting, "setMin", min);
        invokeFloat(setting, "setMax", max);

        setFloatAny(setting, "inc", inc);
        setFloatAny(setting, "step", inc);
        setFloatAny(setting, "increment", inc);

        invokeFloat(setting, "setInc", inc);
        invokeFloat(setting, "setStep", inc);
        invokeFloat(setting, "setIncrement", inc);
    }

    static void invokeFloat(Object obj, String method, float v) {
        if (obj == null) return;
        try {
            Method m = obj.getClass().getMethod(method, float.class);
            m.invoke(obj, v);
        } catch (Throwable ignored) {
        }
        try {
            Method m = obj.getClass().getMethod(method, double.class);
            m.invoke(obj, (double) v);
        } catch (Throwable ignored) {
        }
    }

    static void setFloatAny(Object obj, String field, float v) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == float.class) f.setFloat(obj, v);
            else if (f.getType() == double.class) f.setDouble(obj, v);
        } catch (Throwable ignored) {
        }
    }

    static float readNumber(Object setting, float def) {
        if (setting == null) return def;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("current");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

        return def;
    }

    static float parseFloatSafe(String s, float def) {
        if (s == null) return def;
        try {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return def;
            return Float.parseFloat(t);
        } catch (Throwable ignored) {
        }
        return def;
    }
}
