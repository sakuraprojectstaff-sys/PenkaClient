package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class FastExp extends Module {

    private final BooleanSetting onlyPvP = new BooleanSetting("Только PvP", "Работать только если рядом есть игрок").setValue(false);
    private final SliderSettings cooldown = new SliderSettings("Задержка", "Задержка использования (тик)")
            .setValue(1.0f).range(0.0f, 4.0f);

    public FastExp() {
        super("FastExp", ModuleCategory.MISC);
        setup(onlyPvP, cooldown);
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        if (onlyPvP.isValue() && !isPvP()) return;

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        if (isExpBottle(main) || isExpBottle(off)) {
            int v = (int) cooldown.getValue();
            if (v < 0) v = 0;
            if (v > 4) v = 4;
            mc.itemUseCooldown = v;
        }
    }

    private boolean isPvP() {
        if (mc == null || mc.player == null || mc.world == null) return false;

        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == null) continue;
            if (p == mc.player) continue;
            if (p.isSpectator()) continue;
            if (!p.isAlive()) continue;
            if (mc.player.distanceTo(p) <= 6.0f) return true;
        }
        return false;
    }

    private boolean isExpBottle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == Items.EXPERIENCE_BOTTLE;
    }
}