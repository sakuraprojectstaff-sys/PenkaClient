package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class CrystalOptimizer extends Module {

    private final SelectSetting mode = new SelectSetting("Режим", "Как выбирать кристалл")
            .value("Прицел", "Ближайший видимый")
            .selected("Прицел");

    private final BooleanSetting onlyHoldCrystal = new BooleanSetting("Только с кристаллом", "Работать только если кристалл в руке")
            .setValue(true);

    private final BooleanSetting onlyCooled = new BooleanSetting("Только когда готов", "Только если откат атаки 100%")
            .setValue(true);

    private final SliderSettings range = new SliderSettings("Дистанция", "Радиус поиска кристаллов")
            .setValue(6.0f).range(1.0f, 8.0f);

    private final SliderSettings attackDelay = new SliderSettings("Задержка", "Тиков между атаками")
            .setValue(1.0f).range(0.0f, 6.0f);

    private int tickCounter = 0;

    public CrystalOptimizer() {
        super("CrystalOptimizer", ModuleCategory.MISC);
        setup(mode, onlyHoldCrystal, onlyCooled, range, attackDelay);
    }

    @Override
    public void activate() {
        super.activate();
        tickCounter = 0;
    }

    @Override
    public void deactivate() {
        tickCounter = 0;
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (onlyHoldCrystal.isValue() && !isHoldingCrystal()) return;
        if (onlyCooled.isValue() && mc.player.getAttackCooldownProgress(0.0f) < 1.0f) return;

        int delay = (int) attackDelay.getValue();
        if (delay < 0) delay = 0;

        tickCounter++;
        if (tickCounter < Math.max(1, delay)) return;

        EndCrystalEntity crystal = pickCrystal();
        if (crystal == null) return;

        mc.interactionManager.attackEntity(mc.player, crystal);
        mc.player.swingHand(Hand.MAIN_HAND);
        tickCounter = 0;
    }

    private boolean isHoldingCrystal() {
        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();
        return isCrystal(main) || isCrystal(off);
    }

    private boolean isCrystal(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL;
    }

    private EndCrystalEntity pickCrystal() {
        if (mode.isSelected("Прицел")) {
            EndCrystalEntity aimed = aimedCrystal();
            if (aimed != null) return aimed;
        }
        return nearestVisibleCrystal();
    }

    private EndCrystalEntity aimedCrystal() {
        HitResult hr = mc.crosshairTarget;
        if (hr instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e instanceof EndCrystalEntity c) {
                double r = range.getValue();
                if (mc.player.squaredDistanceTo(c) <= r * r && canBeSeen(c, r)) return c;
            }
        }
        return null;
    }

    private EndCrystalEntity nearestVisibleCrystal() {
        double r = range.getValue();

        Box search = new Box(
                mc.player.getX() - r, mc.player.getY() - r, mc.player.getZ() - r,
                mc.player.getX() + r, mc.player.getY() + r, mc.player.getZ() + r
        );

        List<EndCrystalEntity> list = mc.world.getEntitiesByClass(EndCrystalEntity.class, search, e -> true);
        return list.stream()
                .filter(c -> mc.player.squaredDistanceTo(c) <= r * r)
                .filter(c -> canBeSeen(c, r))
                .min(Comparator.comparingDouble(c -> mc.player.squaredDistanceTo(c)))
                .orElse(null);
    }

    private boolean canBeSeen(Entity entity, double reach) {
        if (entity == null || mc == null || mc.player == null || mc.world == null) return false;

        Vec3d eye = mc.player.getEyePos();
        Box bb = entity.getBoundingBox().expand(0.2);

        Vec3d lookEnd = eye.add(mc.player.getRotationVec(1.0f).multiply(reach));
        Optional<Vec3d> hit = bb.raycast(eye, lookEnd);
        if (hit.isPresent() && isRayClear(eye, hit.get())) return true;

        Vec3d c = bb.getCenter();
        Vec3d top = new Vec3d(c.x, bb.maxY, c.z);
        Vec3d bot = new Vec3d(c.x, bb.minY, c.z);

        Vec3d left = new Vec3d(bb.minX, c.y, c.z);
        Vec3d right = new Vec3d(bb.maxX, c.y, c.z);
        Vec3d front = new Vec3d(c.x, c.y, bb.minZ);
        Vec3d back = new Vec3d(c.x, c.y, bb.maxZ);

        Vec3d[] pts = {c, top, bot, left, right, front, back};

        double r2 = reach * reach;
        for (Vec3d p : pts) {
            if (eye.squaredDistanceTo(p) > r2) continue;
            if (isRayClear(eye, p)) return true;
        }

        return false;
    }

    private boolean isRayClear(Vec3d from, Vec3d to) {
        try {
            HitResult res = mc.world.raycast(new RaycastContext(
                    from, to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));
            return res == null || res.getType() == HitResult.Type.MISS;
        } catch (Throwable ignored) {
            return false;
        }
    }
}