package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.DrawEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.math.calc.Calculate;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormats.POSITION_TEXTURE_COLOR;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Arrows extends Module {

    Identifier legacyIconId = Identifier.of("textures/features/arrows/arrow.png");
    Identifier arrowsIconId = Identifier.of("mre", "textures/arrows.png");
    Identifier triangleIconId = Identifier.of("mre", "textures/triangle.png");
    Identifier triangle2IconId = Identifier.of("mre", "textures/triangle2.png");

    Animation radiusAnim = new Decelerate().setMs(150).setValue(6);

    SliderSettings radiusSetting = new SliderSettings("Радиус", "Радиус на экране")
            .setValue(50).range(30, 140);

    SliderSettings sizeSetting = new SliderSettings("Размер", "Размер стрелок")
            .setValue(10).range(8, 20);

    SliderSettings distSetting = new SliderSettings("Растояние", "Макс дистанция (блоки)")
            .setValue(128).range(5, 150);

    SelectSetting skinSetting = new SelectSetting("Скин", "Выбор стрелочки")
            .value("Стандарт", "Arrows", "Triangle", "Triangle2")
            .selected("Стандарт");

    ColorSetting colorSetting = new ColorSetting("Цвет", "Цвет стрелок")
            .setColor(0xFFFFFFFF);

    MultiSelectSetting targets = new MultiSelectSetting("Сущности", "Фильтр целей")
            .value("Друзья", "Игроки", "Голые игроки", "Мобы", "Предметы", "Боты", "За спиной")
            .selected("Друзья", "Игроки", "За спиной");

    BooleanSetting animations = new BooleanSetting("Анимации", "Плавный радиус")
            .setValue(true);

    BooleanSetting ignoreThirdPerson = new BooleanSetting("Игнорировать 3-е лицо", "Не рисовать в 3-м лице")
            .setValue(true);

    @NonFinal
    boolean autoColorApplied = false;

    public Arrows() {
        super("Arrows", "Arrows", ModuleCategory.RENDER);
        setup(radiusSetting, sizeSetting, distSetting, skinSetting, colorSetting, targets, animations, ignoreThirdPerson);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc == null || mc.player == null) return;

        if (!autoColorApplied) {
            autoColorApplied = true;
            int c = safeClientColor();
            if (colorSetting.getColor() == 0xFFFFFFFF && c != 0xFFFFFFFF) {
                colorSetting.setColor(c);
            }
        }

        double vx = mc.player.getVelocity().x;
        double vz = mc.player.getVelocity().z;
        boolean moving = mc.player.isSprinting() || (vx * vx + vz * vz) > 0.001;

        radiusAnim.setDirection((bool(animations) && moving) ? Direction.FORWARDS : Direction.BACKWARDS);
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc == null || mc.player == null || mc.world == null) return;
        if (mc.options == null || mc.options.hudHidden) return;

        if (bool(ignoreThirdPerson) && !mc.options.getPerspective().equals(Perspective.FIRST_PERSON)) return;

        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(() -> onDraw(e));
            return;
        }

        float middleW = mc.getWindow().getScaledWidth() / 2f;
        float middleH = mc.getWindow().getScaledHeight() / 2f;

        float radius = radiusSetting.getValue() + (bool(animations) ? radiusAnim.getOutput().floatValue() : 0f);
        float size = sizeSetting.getValue();

        float posY = middleH - radius;
        posY = MathHelper.clamp(posY, 6f, mc.getWindow().getScaledHeight() - (size + 6f));

        float maxDist = distSetting.getValue();
        boolean behind = targets.isSelected("За спиной");

        boolean any = false;
        for (Entity ent : getEntitiesSafe()) {
            if (isTarget(ent, maxDist, behind)) {
                any = true;
                break;
            }
        }
        if (!any) return;

        MatrixStack matrices = e.getDrawContext().getMatrices();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);

        RenderSystem.setShaderTexture(0, getSelectedIcon());
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(QUADS, POSITION_TEXTURE_COLOR);

        for (Entity ent : getEntitiesSafe()) {
            if (!isTarget(ent, maxDist, behind)) continue;

            int base = getBaseColor(ent);

            float dist = mc.player.distanceTo(ent);
            float fade = 1f - MathHelper.clamp(dist / Math.max(1f, maxDist), 0f, 1f);
            float alphaMul = 0.25f + fade * 0.75f;

            int colorTop = ColorAssist.multAlpha(ColorAssist.multDark(base, 0.4F), 0.5F * alphaMul);
            int colorBot = ColorAssist.multAlpha(base, alphaMul);

            float yaw = getRotations(ent) - mc.player.getYaw();

            matrices.push();
            matrices.translate(middleW, middleH, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(yaw));
            matrices.translate(-middleW, -middleH, 0.0F);

            Matrix4f mm = matrices.peek().getPositionMatrix();

            buffer.vertex(mm, middleW - (size / 2f), posY + size, 0).texture(0f, 1f).color(colorTop);
            buffer.vertex(mm, middleW + (size / 2f), posY + size, 0).texture(1f, 1f).color(colorTop);
            buffer.vertex(mm, middleW + (size / 2f), posY, 0).texture(1f, 0f).color(colorBot);
            buffer.vertex(mm, middleW - (size / 2f), posY, 0).texture(0f, 0f).color(colorBot);

            matrices.pop();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private Identifier getSelectedIcon() {
        String v = skinSetting.getSelected();
        if ("Arrows".equals(v)) return arrowsIconId;
        if ("Triangle".equals(v)) return triangleIconId;
        if ("Triangle2".equals(v)) return triangle2IconId;
        return legacyIconId;
    }

    private int getBaseColor(Entity ent) {
        int chosen = colorSetting.getColor();
        if (ent instanceof AbstractClientPlayerEntity p && safeIsFriend(p)) {
            int fc = safeFriendColor();
            return fc != 0xFFFFFFFF ? fc : chosen;
        }
        return chosen;
    }

    private boolean isTarget(Entity ent, float maxDist, boolean behind) {
        if (ent == null) return false;
        if (mc == null || mc.player == null) return false;
        if (ent == mc.player) return false;

        if (ent instanceof LivingEntity living) {
            if (!living.isAlive()) return false;
            if (living.getHealth() <= 0) return false;
        }

        float dist = mc.player.distanceTo(ent);
        if (dist > maxDist) return false;

        if (!behind) {
            float yawDiff = MathHelper.wrapDegrees(getRotations(ent) - mc.player.getYaw());
            if (Math.abs(yawDiff) > 90f) return false;
        }

        if (ent instanceof AbstractClientPlayerEntity p) {
            boolean ghost = isGhostPlayer(p);
            if (ghost) return targets.isSelected("Боты");

            boolean isFriend = safeIsFriend(p);
            if (isFriend && targets.isSelected("Друзья")) return true;

            if (!targets.isSelected("Игроки")) return false;

            int armor = p.getArmor();
            boolean nakedAllowed = targets.isSelected("Голые игроки");
            return armor != 0 || nakedAllowed;
        }

        if (ent instanceof ItemEntity) return targets.isSelected("Предметы");

        if (ent instanceof AnimalEntity) return targets.isSelected("Мобы");
        if (ent instanceof MobEntity) return targets.isSelected("Мобы");

        if (ent instanceof ProjectileEntity) return false;

        return false;
    }

    private Iterable<Entity> getEntitiesSafe() {
        try {
            Object v = mc.world.getClass().getMethod("getEntities").invoke(mc.world);
            if (v instanceof Iterable<?> it) return (Iterable<Entity>) it;
        } catch (Throwable ignored) {
        }
        try {
            Object v = mc.world.getClass().getMethod("iterateEntities").invoke(mc.world);
            if (v instanceof Iterable<?> it) return (Iterable<Entity>) it;
        } catch (Throwable ignored) {
        }
        return (Iterable<Entity>) (Iterable<?>) mc.world.getPlayers();
    }

    private boolean isGhostPlayer(AbstractClientPlayerEntity player) {
        if (player == null) return true;
        if (player.getCustomName() != null) {
            String name = player.getCustomName().getString();
            if (name != null && name.startsWith("Ghost_")) return true;
        }
        return player.getClass().getSimpleName().equals("OtherClientPlayerEntity") && player.getPitch() == -30.0f;
    }

    public static float getRotations(Entity entity) {
        double x = Calculate.interpolate(entity.getX(), entity.getX()) - Calculate.interpolate(mc.player.getX(), mc.player.getX());
        double z = Calculate.interpolate(entity.getZ(), entity.getZ()) - Calculate.interpolate(mc.player.getZ(), mc.player.getZ());
        return (float) -(Math.atan2(x, z) * (180 / Math.PI));
    }

    private static int safeClientColor() {
        try {
            return ColorAssist.getClientColor();
        } catch (Throwable ignored) {
            return 0xFFFFFFFF;
        }
    }

    private static int safeFriendColor() {
        try {
            return ColorAssist.getFriendColor();
        } catch (Throwable ignored) {
            return 0xFFFFFFFF;
        }
    }

    private static boolean safeIsFriend(AbstractClientPlayerEntity p) {
        try {
            return FriendUtils.isFriend(p);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean bool(Object setting) {
        if (setting == null) return false;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isEnabled");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isState");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Exception ignored) {
        }

        for (String fn : new String[]{"value", "state", "enabled"}) {
            try {
                Field f = setting.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(setting);
                if (v instanceof Boolean b) return b;
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}
