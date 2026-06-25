package fun.rich.features.impl.misc;

import fun.rich.events.player.MotionEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;

public class AutoClanUpgrade extends Module {
    private static final float DESIRED_PITCH = 89.0F;

    private int previousSlot = -1;
    private long lastWarn;

    public AutoClanUpgrade() {
        super("AutoClanUpgrade", "AutoClanUpgrade", ModuleCategory.MISC);
    }

    @EventHandler
    public void onMotion(MotionEvent e) {
        if (mc.player == null) return;
        e.setYaw(mc.player.getYaw());
        e.setPitch(DESIRED_PITCH);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        int slot = findRequiredSlot();
        if (slot == -1) {
            warnOnce("Нет редстоуна/факелов в хотбаре");
            restoreSlot();
            richDisableSelf();
            return;
        }

        if (!isHoldingRequiredItem()) {
            if (previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = slot;
        }

        BlockPos playerPos = mc.player.getBlockPos();

        if (mc.world.getBlockState(playerPos).isAir()) {
            BlockPos support = playerPos.down();
            if (mc.world.getBlockState(support).isAir()) return;

            Vec3d hit = Vec3d.ofCenter(support).add(0.0, 0.5, 0.0);
            BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, support, false);

            var res = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            if (res != null && res.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        } else {
            mc.interactionManager.updateBlockBreakingProgress(playerPos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private int findRequiredSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            Item it = st.getItem();
            if (it == Items.REDSTONE || it == Items.TORCH) return i;
        }
        return -1;
    }

    private boolean isHoldingRequiredItem() {
        Item it = mc.player.getInventory().getStack(mc.player.getInventory().selectedSlot).getItem();
        return it == Items.REDSTONE || it == Items.TORCH;
    }

    private void restoreSlot() {
        if (mc.player != null && previousSlot != -1) {
            mc.player.getInventory().selectedSlot = previousSlot;
        }
        previousSlot = -1;
    }

    private void warnOnce(String msg) {
        long now = System.currentTimeMillis();
        if (now - lastWarn < 1500L) return;
        lastWarn = now;
        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
    }

    private void richDisableSelf() {
        tryInvokeNoArgs(this, "toggle");
        tryInvokeNoArgs(this, "disable");
        tryInvokeBool(this, "setState", false);
        tryInvokeBool(this, "setEnabled", false);
        tryInvokeBool(this, "setToggled", false);
        tryInvokeBool(this, "setToggle", false);
    }

    private static void tryInvokeNoArgs(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            m.invoke(obj);
        } catch (Throwable ignored) {
        }
    }

    private static void tryInvokeBool(Object obj, String name, boolean v) {
        try {
            Method m = obj.getClass().getMethod(name, boolean.class);
            m.invoke(obj, v);
        } catch (Throwable ignored) {
        }
    }

    public void onDisable() {
        restoreSlot();
    }
}