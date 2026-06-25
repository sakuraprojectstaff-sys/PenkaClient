package fun.rich.features.impl.combat;

import fun.rich.events.player.TickEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.features.aura.rotations.constructor.LinearConstructor;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import fun.rich.utils.features.aura.warp.TurnsConfig;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import fun.rich.utils.math.task.TaskPriority;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Surround extends Module {

    private final SliderSettings blocksPerTick = new SliderSettings("Blocks/Tick", "Сколько блоков ставить за тик")
            .setValue(4.0f).range(1.0f, 12.0f);

    private final SliderSettings placeDelay = new SliderSettings("Delay", "Задержка (тики) между сериями постановки")
            .setValue(1.0f).range(0.0f, 10.0f);

    private final SelectSetting centerMode = new SelectSetting("Center", "Центровка игрока")
            .value("Teleport", "Motion", "Disabled")
            .selected("Motion");

    private final BooleanSetting disableOnJump = new BooleanSetting("Disable on Jump", "Выключать при изменении Y")
            .setValue(true);

    private final BooleanSetting rotate = new BooleanSetting("Rotate", "Поворачивать голову к месту установки")
            .setValue(true);

    private final BooleanSetting render = new BooleanSetting("Render", "Рисовать позиции")
            .setValue(true);

    private final BooleanSetting backSlot = new BooleanSetting("Back Slot", "Возвращать слот после установки")
            .setValue(true);

    private int tickDelay;
    private double lastY;
    private int startSlot = -1;

    private final List<BlockPos> placingPos = new ArrayList<>();
    private final List<BlockPos> completedPos = new ArrayList<>();

    public Surround() {
        super("Surround", ModuleCategory.COMBAT);
        setup(blocksPerTick, placeDelay, centerMode, disableOnJump, rotate, render, backSlot);
    }

    @Override
    public void activate() {
        super.activate();

        tickDelay = 0;
        placingPos.clear();
        completedPos.clear();

        if (mc == null || mc.player == null) return;

        lastY = mc.player.getY();
        startSlot = mc.player.getInventory().selectedSlot;

        int obs = findObsidianSlot();
        if (obs != -1 && obs != mc.player.getInventory().selectedSlot) {
            selectHotbar(obs);
        }

        if (centerMode.isSelected("Teleport")) {
            double x = MathHelper.floor(mc.player.getX()) + 0.5;
            double z = MathHelper.floor(mc.player.getZ()) + 0.5;
            mc.player.updatePosition(x, mc.player.getY(), z);
            try {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        x, mc.player.getY(), z, mc.player.isOnGround(), false
                ));
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void deactivate() {
        if (mc != null && mc.player != null && startSlot != -1) {
            if (mc.player.getInventory().selectedSlot != startSlot) {
                selectHotbar(startSlot);
            }
        }
        startSlot = -1;
        placingPos.clear();
        completedPos.clear();
        tickDelay = 0;
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (disableOnJump.isValue()) {
            double y = mc.player.getY();
            if (Math.abs(y - lastY) > 1.0E-4) {
                setState(false);
                return;
            }
            lastY = y;
        }

        if (centerMode.isSelected("Motion")) doMotionCenter();

        updateTargets();

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        int slot = findObsidianSlot();
        if (slot == -1) return;

        if (mc.player.getInventory().selectedSlot != slot) {
            selectHotbar(slot);
        }

        int limit = (int) blocksPerTick.getValue();
        if (limit < 1) limit = 1;

        int placed = 0;
        for (BlockPos pos : placingPos) {
            if (placed >= limit) break;
            if (pos == null) continue;
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;

            if (placeBlockManual(pos)) placed++;
        }

        tickDelay = (int) placeDelay.getValue();
        if (tickDelay < 0) tickDelay = 0;

        if (backSlot.isValue() && startSlot != -1 && mc.player.getInventory().selectedSlot != startSlot) {
            selectHotbar(startSlot);
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!isState()) return;
        if (!render.isValue()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        int placingFill = 0x66FF0000;
        int placingOutline = 0xFFFF5555;

        int base = ColorAssist.getClientColor();
        int completedFill = (base & 0x00FFFFFF) | (0x55 << 24);
        int completedOutline = (base & 0x00FFFFFF) | (0xFF << 24);

        for (BlockPos pos : placingPos) {
            if (pos == null) continue;
            Box box = new Box(pos).expand(0.002);
            Render3D.drawBox(box, placingFill, 2, true, true, true);
            Render3D.drawBox(box, placingOutline, 2, true, true, true);
        }

        for (BlockPos pos : completedPos) {
            if (pos == null) continue;
            Box box = new Box(pos).expand(0.002);
            Render3D.drawBox(box, completedFill, 2, true, true, true);
            Render3D.drawBox(box, completedOutline, 2, true, true, true);
        }
    }

    private void updateTargets() {
        placingPos.clear();
        completedPos.clear();

        BlockPos base = mc.player.getBlockPos();

        List<BlockPos> targets = List.of(
                base.north(),
                base.south(),
                base.east(),
                base.west()
        );

        for (BlockPos pos : targets) {
            BlockState state = mc.world.getBlockState(pos);

            if (!state.isReplaceable()) {
                if (isSafeBlock(state)) completedPos.add(pos);
                continue;
            }

            placingPos.add(pos);
        }
    }

    private boolean isSafeBlock(BlockState state) {
        if (state == null) return false;
        BlockState s = state;
        return s.getBlock() == Blocks.OBSIDIAN
                || s.getBlock() == Blocks.CRYING_OBSIDIAN
                || s.getBlock() == Blocks.BEDROCK;
    }

    private boolean placeBlockManual(BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (side == Direction.UP) continue;

            BlockPos neighbor = pos.offset(side);
            BlockState ns = mc.world.getBlockState(neighbor);

            if (ns.isReplaceable()) continue;

            Vec3d hitVec = Vec3d.ofCenter(neighbor).add(Vec3d.of(side.getOpposite().getVector()).multiply(0.5));

            if (rotate.isValue()) {
                tryRotate(hitVec);
            }

            try {
                mc.interactionManager.interactBlock(
                        mc.player,
                        Hand.MAIN_HAND,
                        new BlockHitResult(hitVec, side.getOpposite(), neighbor, false)
                );
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private void tryRotate(Vec3d hitVec) {
        try {
            Vec3d eye = mc.player.getEyePos();
            Turns a = MathAngle.fromVec3d(hitVec.subtract(eye));
            Turns.VecRotation vr = new Turns.VecRotation(a, a.toVector());
            TurnsConfig cfg = new TurnsConfig(new LinearConstructor(), false, true);
            TurnsConnection.INSTANCE.rotateTo(vr, mc.player, 40, cfg, TaskPriority.HIGH_IMPORTANCE_1, this);
        } catch (Throwable ignored) {
        }
    }

    private void doMotionCenter() {
        double targetX = MathHelper.floor(mc.player.getX()) + 0.5;
        double targetZ = MathHelper.floor(mc.player.getZ()) + 0.5;

        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();

        if (Math.abs(dx) > 0.1 || Math.abs(dz) > 0.1) {
            mc.player.setVelocity(dx * 0.45, mc.player.getVelocity().y, dz * 0.45);
        }
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.OBSIDIAN)) return i;
            if (mc.player.getInventory().getStack(i).isOf(Items.CRYING_OBSIDIAN)) return i;
        }
        return -1;
    }

    private void selectHotbar(int slot) {
        try {
            if (slot < 0 || slot > 8) return;
            if (mc == null || mc.player == null || mc.player.networkHandler == null) return;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.player.getInventory().selectedSlot = slot;
        } catch (Throwable ignored) {
        }
    }
}