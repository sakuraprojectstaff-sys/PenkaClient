package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.packet.PacketEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class TotemAngle extends Module {

    public static TotemAngle getInstance() {
        return Instance.get(TotemAngle.class);
    }

    private final SelectSetting mode = new SelectSetting("Режим", "Выбор эффекта")
            .value("Angel", "Shatter")
            .selected("Angel");

    private final BooleanSetting outline = new BooleanSetting("Обводка", "Рисовать только контур, без заливки")
            .setValue(false);

    private final SliderSettings riseHeight = new SliderSettings("Высота подъема", "Высота подъема призрака")
            .range(0.2f, 5.0f)
            .setValue(4.0f);

    private final SliderSettings duration = new SliderSettings("Время жизни", "Время жизни призрака")
            .range(0.2f, 6.0f)
            .setValue(3.0f);

    private final BooleanSetting showSelf = new BooleanSetting("Показывать на себе", "Показывать эффект на себе")
            .setValue(false);

    private final SliderSettings brightness = new SliderSettings("Яркость", "Усиливает видимость без смены цвета")
            .range(0.2f, 3.0f)
            .setValue(1.0f);

    private final ColorSetting color = new ColorSetting("Цвет", "Цвет эффекта")
            .setColor(new Color(179, 140, 255, 255).getRGB());

    private final Deque<Ghost> ghosts = new ArrayDeque<>();
    private final Deque<Shatter> shatters = new ArrayDeque<>();

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public TotemAngle() {
        super("TotemAngle", "Totem Angel", ModuleCategory.RENDER);
        setup(mode, outline, riseHeight, duration, showSelf, brightness, color);
    }

    @SuppressWarnings("unused")
    public void onDisable() {
        ghosts.clear();
        shatters.clear();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        Object p = packetFrom(e);
        if (!(p instanceof EntityStatusS2CPacket pkt)) return;
        if (!isTotemStatus(pkt)) return;

        if (mc.world == null) return;
        Entity ent = pkt.getEntity(mc.world);
        if (!(ent instanceof LivingEntity le)) return;

        if (!bool(showSelf) && mc.player != null && le.getId() == mc.player.getId()) return;

        if (isShatter()) addShatter(le);
        else addGhost(le);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        boolean sh = isShatter();
        boolean ol = bool(outline);

        if (!sh) {
            if (ghosts.isEmpty()) return;

            long now = System.currentTimeMillis();
            float lifeMs = slider(duration) * 1000.0f;
            float rise = slider(riseHeight);
            float bright = MathHelper.clamp(slider(brightness), 0.2f, 3.0f);

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            Iterator<Ghost> it = ghosts.iterator();
            while (it.hasNext()) {
                Ghost g = it.next();
                float t = (now - g.startTime()) / Math.max(1.0f, lifeMs);
                if (t >= 1.0f) {
                    it.remove();
                    continue;
                }

                float up = rise * (float) ease(MathHelper.clamp(t, 0.0f, 0.75f));
                float a = (float) easeOutAlpha(MathHelper.clamp(t, 0.0f, 1.0f));
                float alphaMul = MathHelper.clamp(a * bright, 0.0f, 0.95f);

                int base = color.getColor();
                int rgb = base & 0x00FFFFFF;
                int ca = MathHelper.clamp((int) (((base >>> 24) & 0xFF) * alphaMul), 0, 255);
                int argb = (ca << 24) | rgb;

                e.getStack().push();
                e.getStack().translate(g.pos().x, g.pos().y + up, g.pos().z);
                e.getStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-g.bodyYaw()));

                renderBodyBoxes(e.getStack(), argb, ol);

                e.getStack().pop();
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            return;
        }

        if (shatters.isEmpty()) return;

        long now = System.currentTimeMillis();
        float lifeMs = slider(duration) * 1000.0f;
        float power = MathHelper.clamp(slider(riseHeight), 0.2f, 5.0f);
        float bright = MathHelper.clamp(slider(brightness), 0.2f, 3.0f);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Iterator<Shatter> it = shatters.iterator();
        while (it.hasNext()) {
            Shatter s = it.next();
            float t = (now - s.startTime) / Math.max(1.0f, lifeMs);
            if (t >= 1.0f) {
                it.remove();
                continue;
            }

            float a = (float) alphaProfile(t);
            float alphaMul = MathHelper.clamp(a * bright, 0.0f, 0.95f);

            int base = color.getColor();
            int rgb = base & 0x00FFFFFF;
            int ca = MathHelper.clamp((int) (((base >>> 24) & 0xFF) * alphaMul), 0, 255);
            int argb = (ca << 24) | rgb;

            float td = t * (lifeMs / 1000.0f);
            float g = 6.2f;

            e.getStack().push();
            e.getStack().translate(s.pos.x, s.pos.y, s.pos.z);
            e.getStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-s.bodyYaw));

            renderPart(e, s, PartId.HEAD, td, power, g, argb, ol);
            renderPart(e, s, PartId.BODY, td, power, g, argb, ol);
            renderPart(e, s, PartId.ARM_L, td, power, g, argb, ol);
            renderPart(e, s, PartId.ARM_R, td, power, g, argb, ol);
            renderPart(e, s, PartId.LEG_L, td, power, g, argb, ol);
            renderPart(e, s, PartId.LEG_R, td, power, g, argb, ol);

            e.getStack().pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private boolean isShatter() {
        try {
            return mode.isSelected("Shatter");
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void addGhost(LivingEntity player) {
        float td = mc.getRenderTickCounter().getTickDelta(false);

        double x = MathHelper.lerp(td, player.prevX, player.getX());
        double y = MathHelper.lerp(td, player.prevY, player.getY());
        double z = MathHelper.lerp(td, player.prevZ, player.getZ());

        float bodyYaw = MathHelper.lerpAngleDegrees(td, player.prevBodyYaw, player.bodyYaw);

        ghosts.addLast(new Ghost(new Vec3d(x, y, z), bodyYaw, System.currentTimeMillis()));
        while (ghosts.size() > 64) ghosts.pollFirst();
    }

    private void addShatter(LivingEntity player) {
        float td = mc.getRenderTickCounter().getTickDelta(false);

        double x = MathHelper.lerp(td, player.prevX, player.getX());
        double y = MathHelper.lerp(td, player.prevY, player.getY());
        double z = MathHelper.lerp(td, player.prevZ, player.getZ());

        float bodyYaw = MathHelper.lerpAngleDegrees(td, player.prevBodyYaw, player.bodyYaw);

        long st = System.currentTimeMillis();
        int seed = mixSeed(player.getId(), st);

        PartMotion[] parts = new PartMotion[6];

        float yawRad = (float) Math.toRadians(bodyYaw);
        float fx = -MathHelper.sin(yawRad);
        float fz = MathHelper.cos(yawRad);
        float rx = fz;
        float rz = -fx;

        parts[PartId.HEAD.ordinal()] = motion(seed, 0, fx * 0.25f, 1.35f, fz * 0.25f, rx * 0.06f, rz * 0.06f);
        parts[PartId.BODY.ordinal()] = motion(seed, 1, fx * 0.10f, 0.55f, fz * 0.10f, rx * 0.03f, rz * 0.03f);

        parts[PartId.ARM_L.ordinal()] = motion(seed, 2, -rx * 0.95f + fx * 0.10f, 0.75f, -rz * 0.95f + fz * 0.10f, -rx * 0.12f, -rz * 0.12f);
        parts[PartId.ARM_R.ordinal()] = motion(seed, 3, rx * 0.95f + fx * 0.10f, 0.75f, rz * 0.95f + fz * 0.10f, rx * 0.12f, rz * 0.12f);

        parts[PartId.LEG_L.ordinal()] = motion(seed, 4, -rx * 0.45f + fx * 0.35f, 0.60f, -rz * 0.45f + fz * 0.35f, -rx * 0.06f, -rz * 0.06f);
        parts[PartId.LEG_R.ordinal()] = motion(seed, 5, rx * 0.45f + fx * 0.35f, 0.60f, rz * 0.45f + fz * 0.35f, rx * 0.06f, rz * 0.06f);

        shatters.addLast(new Shatter(new Vec3d(x, y, z), bodyYaw, st, parts));
        while (shatters.size() > 64) shatters.pollFirst();
    }

    private void renderBodyBoxes(net.minecraft.client.util.math.MatrixStack ms, int argb, boolean outlineOnly) {
        Matrix4f m = ms.peek().getPositionMatrix();

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        if (!outlineOnly) {
            drawBoxFill(bb, m, -0.25f, 1.45f, -0.25f, 0.25f, 1.95f, 0.25f, argb);
            drawBoxFill(bb, m, -0.30f, 0.90f, -0.15f, 0.30f, 1.45f, 0.15f, argb);

            drawBoxFill(bb, m, -0.55f, 0.95f, -0.12f, -0.30f, 1.45f, 0.12f, argb);
            drawBoxFill(bb, m, 0.30f, 0.95f, -0.12f, 0.55f, 1.45f, 0.12f, argb);

            drawBoxFill(bb, m, -0.25f, 0.00f, -0.12f, 0.00f, 0.90f, 0.12f, argb);
            drawBoxFill(bb, m, 0.00f, 0.00f, -0.12f, 0.25f, 0.90f, 0.12f, argb);

            BufferRenderer.drawWithGlobalProgram(bb.end());
            return;
        }

        float t = 0.02f;

        drawBoxOutlineQuads(bb, m, -0.25f, 1.45f, -0.25f, 0.25f, 1.95f, 0.25f, argb, t);
        drawBoxOutlineQuads(bb, m, -0.30f, 0.90f, -0.15f, 0.30f, 1.45f, 0.15f, argb, t);

        drawBoxOutlineQuads(bb, m, -0.55f, 0.95f, -0.12f, -0.30f, 1.45f, 0.12f, argb, t);
        drawBoxOutlineQuads(bb, m, 0.30f, 0.95f, -0.12f, 0.55f, 1.45f, 0.12f, argb, t);

        drawBoxOutlineQuads(bb, m, -0.25f, 0.00f, -0.12f, 0.00f, 0.90f, 0.12f, argb, t);
        drawBoxOutlineQuads(bb, m, 0.00f, 0.00f, -0.12f, 0.25f, 0.90f, 0.12f, argb, t);

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private void renderPart(WorldRenderEvent e, Shatter s, PartId id, float td, float power, float gravity, int argb, boolean outlineOnly) {
        PartMotion m = s.parts[id.ordinal()];

        double ox = m.vx * td * power;
        double oy = m.vy * td * power - 0.5 * gravity * td * td;
        double oz = m.vz * td * power;

        float spin = m.spinDeg * td * 120.0f;
        float spin2 = m.spinDeg2 * td * 95.0f;

        e.getStack().push();
        e.getStack().translate(ox, oy, oz);

        if (id != PartId.BODY) {
            e.getStack().multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            e.getStack().multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin2));
        }

        renderPartBox(e.getStack(), id, argb, outlineOnly);
        e.getStack().pop();
    }

    private void renderPartBox(net.minecraft.client.util.math.MatrixStack ms, PartId id, int argb, boolean outlineOnly) {
        Matrix4f m = ms.peek().getPositionMatrix();

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        if (!outlineOnly) {
            if (id == PartId.HEAD) {
                drawBoxFill(bb, m, -0.25f, 1.45f, -0.25f, 0.25f, 1.95f, 0.25f, argb);
            } else if (id == PartId.BODY) {
                drawBoxFill(bb, m, -0.30f, 0.90f, -0.15f, 0.30f, 1.45f, 0.15f, argb);
            } else if (id == PartId.ARM_L) {
                drawBoxFill(bb, m, -0.55f, 0.95f, -0.12f, -0.30f, 1.45f, 0.12f, argb);
            } else if (id == PartId.ARM_R) {
                drawBoxFill(bb, m, 0.30f, 0.95f, -0.12f, 0.55f, 1.45f, 0.12f, argb);
            } else if (id == PartId.LEG_L) {
                drawBoxFill(bb, m, -0.25f, 0.00f, -0.12f, 0.00f, 0.90f, 0.12f, argb);
            } else if (id == PartId.LEG_R) {
                drawBoxFill(bb, m, 0.00f, 0.00f, -0.12f, 0.25f, 0.90f, 0.12f, argb);
            }

            BufferRenderer.drawWithGlobalProgram(bb.end());
            return;
        }

        float t = 0.02f;

        if (id == PartId.HEAD) {
            drawBoxOutlineQuads(bb, m, -0.25f, 1.45f, -0.25f, 0.25f, 1.95f, 0.25f, argb, t);
        } else if (id == PartId.BODY) {
            drawBoxOutlineQuads(bb, m, -0.30f, 0.90f, -0.15f, 0.30f, 1.45f, 0.15f, argb, t);
        } else if (id == PartId.ARM_L) {
            drawBoxOutlineQuads(bb, m, -0.55f, 0.95f, -0.12f, -0.30f, 1.45f, 0.12f, argb, t);
        } else if (id == PartId.ARM_R) {
            drawBoxOutlineQuads(bb, m, 0.30f, 0.95f, -0.12f, 0.55f, 1.45f, 0.12f, argb, t);
        } else if (id == PartId.LEG_L) {
            drawBoxOutlineQuads(bb, m, -0.25f, 0.00f, -0.12f, 0.00f, 0.90f, 0.12f, argb, t);
        } else if (id == PartId.LEG_R) {
            drawBoxOutlineQuads(bb, m, 0.00f, 0.00f, -0.12f, 0.25f, 0.90f, 0.12f, argb, t);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void drawBoxFill(BufferBuilder bb, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c) {
        bb.vertex(m, x1, y1, z2).color(c);
        bb.vertex(m, x2, y1, z2).color(c);
        bb.vertex(m, x2, y2, z2).color(c);
        bb.vertex(m, x1, y2, z2).color(c);

        bb.vertex(m, x2, y1, z1).color(c);
        bb.vertex(m, x1, y1, z1).color(c);
        bb.vertex(m, x1, y2, z1).color(c);
        bb.vertex(m, x2, y2, z1).color(c);

        bb.vertex(m, x1, y1, z1).color(c);
        bb.vertex(m, x1, y1, z2).color(c);
        bb.vertex(m, x1, y2, z2).color(c);
        bb.vertex(m, x1, y2, z1).color(c);

        bb.vertex(m, x2, y1, z2).color(c);
        bb.vertex(m, x2, y1, z1).color(c);
        bb.vertex(m, x2, y2, z1).color(c);
        bb.vertex(m, x2, y2, z2).color(c);

        bb.vertex(m, x1, y2, z2).color(c);
        bb.vertex(m, x2, y2, z2).color(c);
        bb.vertex(m, x2, y2, z1).color(c);
        bb.vertex(m, x1, y2, z1).color(c);

        bb.vertex(m, x1, y1, z1).color(c);
        bb.vertex(m, x2, y1, z1).color(c);
        bb.vertex(m, x2, y1, z2).color(c);
        bb.vertex(m, x1, y1, z2).color(c);
    }

    private static void drawBoxOutlineQuads(BufferBuilder bb, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c, float t) {
        float xm = (x1 + x2) * 0.5f;
        float ym = (y1 + y2) * 0.5f;
        float zm = (z1 + z2) * 0.5f;

        float ex1 = x1, ex2 = x2, ey1 = y1, ey2 = y2, ez1 = z1, ez2 = z2;

        edgeX(bb, m, ex1, ex2, ey1, ez1, c, t);
        edgeX(bb, m, ex1, ex2, ey1, ez2, c, t);
        edgeX(bb, m, ex1, ex2, ey2, ez1, c, t);
        edgeX(bb, m, ex1, ex2, ey2, ez2, c, t);

        edgeY(bb, m, ey1, ey2, ex1, ez1, c, t);
        edgeY(bb, m, ey1, ey2, ex2, ez1, c, t);
        edgeY(bb, m, ey1, ey2, ex1, ez2, c, t);
        edgeY(bb, m, ey1, ey2, ex2, ez2, c, t);

        edgeZ(bb, m, ez1, ez2, ex1, ey1, c, t);
        edgeZ(bb, m, ez1, ez2, ex2, ey1, c, t);
        edgeZ(bb, m, ez1, ez2, ex1, ey2, c, t);
        edgeZ(bb, m, ez1, ez2, ex2, ey2, c, t);
    }

    private static void edgeX(BufferBuilder bb, Matrix4f m, float x1, float x2, float y, float z, int c, float t) {
        drawBoxFill(bb, m, x1, y - t, z - t, x2, y + t, z + t, c);
    }

    private static void edgeY(BufferBuilder bb, Matrix4f m, float y1, float y2, float x, float z, int c, float t) {
        drawBoxFill(bb, m, x - t, y1, z - t, x + t, y2, z + t, c);
    }

    private static void edgeZ(BufferBuilder bb, Matrix4f m, float z1, float z2, float x, float y, int c, float t) {
        drawBoxFill(bb, m, x - t, y - t, z1, x + t, y + t, z2, c);
    }

    private static PartMotion motion(int seed, int i, float bx, float by, float bz, float jx, float jz) {
        float r1 = rand(seed, i * 11 + 3) * 2.0f - 1.0f;
        float r2 = rand(seed, i * 11 + 7) * 2.0f - 1.0f;
        float r3 = rand(seed, i * 11 + 9) * 2.0f - 1.0f;

        float vx = bx + jx + r1 * 0.12f;
        float vy = by + rand(seed, i * 11 + 5) * 0.35f;
        float vz = bz + jz + r2 * 0.12f;

        float s1 = r3;
        float s2 = rand(seed, i * 13 + 1) * 2.0f - 1.0f;

        float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 0.001f) len = 1.0f;
        vx /= len;
        vy /= len;
        vz /= len;

        return new PartMotion(vx, vy, vz, s1, s2);
    }

    private static double ease(double t) {
        double inv = 1.0D - t;
        return 1.0D - inv * inv * inv;
    }

    private static double easeOutAlpha(double t) {
        double inv = 1.0D - t;
        return 0.75D * (1.0D - inv * inv * inv);
    }

    private static double alphaProfile(double t) {
        double in = MathHelper.clamp(t / 0.10, 0.0, 1.0);
        double out = MathHelper.clamp((t - 0.65) / 0.35, 0.0, 1.0);
        double aIn = 1.0 - Math.pow(1.0 - in, 3.0);
        double aOut = 1.0 - out;
        return 0.85 * aIn * aOut;
    }

    private static int mixSeed(int id, long st) {
        long x = (st ^ (st >>> 33)) * 0xff51afd7ed558ccdL;
        x = (x ^ (x >>> 33)) * 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int) (x ^ (x >>> 32) ^ id * 0x9E3779B9);
    }

    private static float rand(int seed, int k) {
        int x = seed ^ (k * 0x27d4eb2d);
        x ^= (x >>> 15);
        x *= 0x85ebca6b;
        x ^= (x >>> 13);
        x *= 0xc2b2ae35;
        x ^= (x >>> 16);
        return (x & 0x00FFFFFF) / 16777215.0f;
    }

    private record Ghost(Vec3d pos, float bodyYaw, long startTime) {
    }

    private enum PartId {
        HEAD, BODY, ARM_L, ARM_R, LEG_L, LEG_R
    }

    private record PartMotion(float vx, float vy, float vz, float spinDeg, float spinDeg2) {
    }

    private static final class Shatter {
        final Vec3d pos;
        final float bodyYaw;
        final long startTime;
        final PartMotion[] parts;

        Shatter(Vec3d pos, float bodyYaw, long startTime, PartMotion[] parts) {
            this.pos = pos;
            this.bodyYaw = bodyYaw;
            this.startTime = startTime;
            this.parts = parts;
        }
    }

    private static boolean isTotemStatus(EntityStatusS2CPacket pkt) {
        try {
            Method m = pkt.getClass().getMethod("getStatus");
            Object v = m.invoke(pkt);
            if (v instanceof Byte b) return (b & 0xFF) == 35;
            if (v instanceof Number n) return (n.intValue() & 0xFF) == 35;
        } catch (Throwable ignored) {
        }
        try {
            Method m = pkt.getClass().getMethod("getStatusByte");
            Object v = m.invoke(pkt);
            if (v instanceof Byte b) return (b & 0xFF) == 35;
            if (v instanceof Number n) return (n.intValue() & 0xFF) == 35;
        } catch (Throwable ignored) {
        }
        try {
            Method m = pkt.getClass().getMethod("status");
            Object v = m.invoke(pkt);
            if (v instanceof Byte b) return (b & 0xFF) == 35;
            if (v instanceof Number n) return (n.intValue() & 0xFF) == 35;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object packetFrom(PacketEvent e) {
        try {
            Method m = e.getClass().getMethod("getPacket");
            return m.invoke(e);
        } catch (Throwable ignored) {
        }
        try {
            Method m = e.getClass().getMethod("packet");
            return m.invoke(e);
        } catch (Throwable ignored) {
        }
        try {
            Method m = e.getClass().getMethod("get");
            return m.invoke(e);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static float slider(SliderSettings s) {
        try {
            Method m = s.getClass().getMethod("getValueFloat");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = s.getClass().getMethod("getValue");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = s.getClass().getMethod("get");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }

    private static boolean bool(Object setting) {
        if (setting == null) return false;
        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("isEnabled");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            for (String f : new String[]{"value", "state", "enabled"}) {
                java.lang.reflect.Field ff = setting.getClass().getDeclaredField(f);
                ff.setAccessible(true);
                Object v = ff.get(setting);
                if (v instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
