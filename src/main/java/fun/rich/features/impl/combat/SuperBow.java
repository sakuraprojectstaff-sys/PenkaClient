package fun.rich.features.impl.combat;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SuperBow extends Module {

    final MinecraftClient mc = MinecraftClient.getInstance();

    float powerMultiplier = 1.05f;
    int delayTicks = 20;

    long lastShotMs = 0L;
    boolean justShot = false;
    int justShotTries = 0;

    public SuperBow() {
        super("SuperBow", "SuperBow", ModuleCategory.COMBAT);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isUsingItem()) return;

        ItemStack active = mc.player.getActiveItem();
        if (active == null || !active.isOf(Items.BOW)) return;

        int useTicks = active.getMaxUseTime(mc.player) - mc.player.getItemUseTime();
        long now = System.currentTimeMillis();
        long delayMs = (long) delayTicks * 50L;

        if (useTicks >= 20 && now - lastShotMs >= delayMs) {
            if (hasAnyArrow()) {
                mc.player.stopUsingItem();
                lastShotMs = now;
                justShot = true;
                justShotTries = 6;
            }
        }

        if (justShot) {
            PersistentProjectileEntity arrow = findLastArrow(mc.player);
            if (arrow != null) {
                arrow.setCritical(true);
                arrow.setDamage(arrow.getDamage() * 1.5);

                Vec3d vel = arrow.getVelocity();
                double mul = powerMultiplier;
                arrow.setVelocity(vel.x * mul, vel.y * mul - 0.05, vel.z * mul);

                justShot = false;
                justShotTries = 0;
            } else {
                if (--justShotTries <= 0) {
                    justShot = false;
                    justShotTries = 0;
                }
            }
        }
    }

    boolean hasAnyArrow() {
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s == null || s.isEmpty()) continue;
            if (s.isOf(Items.ARROW) || s.isOf(Items.TIPPED_ARROW) || s.isOf(Items.SPECTRAL_ARROW)) return true;
        }
        return false;
    }

    PersistentProjectileEntity findLastArrow(LivingEntity shooter) {
        return mc.world.getEntitiesByClass(
                        PersistentProjectileEntity.class,
                        shooter.getBoundingBox().expand(10.0),
                        a -> a.getOwner() == shooter
                )
                .stream()
                .max(Comparator.comparingInt(Entity::getId))
                .orElse(null);
    }
}