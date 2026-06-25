package fun.rich.features.impl.misc;

import fun.rich.events.keyboard.HotBarScrollEvent;
import fun.rich.events.player.HotBarUpdateEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.ItemRendererEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.interactions.inv.InventoryTask;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CropBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CarrotAutoFarm extends Module {

    final SliderSettings range = new SliderSettings("Радиус", "Дистанция поиска")
            .setValue(4).range(1, 6);

    final SliderSettings delayMs = new SliderSettings("Задержка (мс)", "Пауза между действиями")
            .setValue(95).range(0, 250);

    long nextScanAtMs;

    final Map<BlockPos, Long> cooldown = new HashMap<>();

    ItemStack renderStack;

    Stage stage = Stage.IDLE;
    ActionType pendingType;
    BlockPos pendingPos;
    Slot pendingSlot;
    boolean pendingNeedSwap;

    public CarrotAutoFarm() {
        super("CarrotAutoFarm", "Carrot AutoFarm", ModuleCategory.MISC);
        setup(range, delayMs);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        cooldown.clear();
        renderStack = null;
        stage = Stage.IDLE;
        pendingType = null;
        pendingPos = null;
        pendingSlot = null;
        pendingNeedSwap = false;
        nextScanAtMs = 0L;
    }

    @EventHandler
    public void onItemRenderer(ItemRendererEvent e) {
        if (renderStack != null && e.getHand().equals(Hand.MAIN_HAND) && Objects.equals(mc.player, e.getPlayer())) {
            e.setStack(renderStack);
        }
    }

    @EventHandler
    public void onHotBarUpdate(HotBarUpdateEvent e) {
        if (stage != Stage.IDLE) e.cancel();
    }

    @EventHandler
    public void onHotBarScroll(HotBarScrollEvent e) {
        if (stage != Stage.IDLE) e.cancel();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || mc.interactionManager == null) return;

        long now = System.currentTimeMillis();
        cleanupCooldown(now);

        if (stage != Stage.IDLE) {
            tickAction(mc, now);
            return;
        }

        long dly = clampL((long) delayMs.getValue(), 0L, 450L);
        if (now < nextScanAtMs) return;

        ClientPlayerEntity p = mc.player;

        int r = clampI((int) range.getValue(), 1, 6);
        BlockPos base = p.getBlockPos();

        Target harvest = null;
        Target bone = null;
        Target plant = null;

        double bestHarvest = Double.POSITIVE_INFINITY;
        double bestBone = Double.POSITIVE_INFINITY;
        double bestPlant = Double.POSITIVE_INFINITY;

        Slot hoeSlot = findHoeSlot();
        Slot carrotSlot = findItemSlot(Items.CARROT);
        Slot boneSlot = findItemSlot(Items.BONE_MEAL);

        boolean haveHoe = hoeSlot != null;
        boolean haveCarrot = carrotSlot != null;
        boolean haveBone = boneSlot != null;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = base.add(dx, dy, dz);
                    if (isOnCooldown(pos, now)) continue;

                    BlockState st = mc.world.getBlockState(pos);
                    Block b = st.getBlock();

                    if (b == Blocks.CARROTS) {
                        int age = cropAge(st);
                        int max = cropMaxAge(st);
                        double d2 = dist2(base, pos);

                        if (age >= max) {
                            if (haveHoe && d2 < bestHarvest) {
                                bestHarvest = d2;
                                harvest = new Target(ActionType.HARVEST, pos);
                            }
                        } else if (haveBone) {
                            if (d2 < bestBone) {
                                bestBone = d2;
                                bone = new Target(ActionType.BONE_MEAL, pos);
                            }
                        }
                        continue;
                    }

                    if (b == Blocks.FARMLAND && haveCarrot) {
                        BlockPos above = pos.up();
                        if (mc.world.getBlockState(above).isAir() && !isOnCooldown(above, now)) {
                            double d2 = dist2(base, pos);
                            if (d2 < bestPlant) {
                                bestPlant = d2;
                                plant = new Target(ActionType.PLANT, pos);
                            }
                        }
                    }
                }
            }
        }

        Target chosen = harvest != null ? harvest : (bone != null ? bone : plant);
        if (chosen == null) {
            nextScanAtMs = now + 60L;
            return;
        }

        Slot useSlot;
        if (chosen.type == ActionType.HARVEST) useSlot = hoeSlot;
        else if (chosen.type == ActionType.BONE_MEAL) useSlot = boneSlot;
        else useSlot = carrotSlot;

        if (useSlot == null) {
            setCooldown(chosen.pos, now, 300L);
            nextScanAtMs = now + 120L;
            return;
        }

        pendingType = chosen.type;
        pendingPos = chosen.pos;
        pendingSlot = useSlot;

        Slot main = InventoryTask.mainHandSlot();
        pendingNeedSwap = main == null || !pendingSlot.equals(main);

        stage = pendingNeedSwap ? Stage.SWAP_IN : Stage.DO_ACTION;

        nextScanAtMs = now + Math.max(0L, dly);
    }

    void tickAction(MinecraftClient mc, long now) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || pendingType == null || pendingPos == null || pendingSlot == null) {
            resetAction();
            return;
        }

        if (pendingType == ActionType.PLANT) {
            if (mc.world == null) {
                resetAction();
                return;
            }
            if (mc.world.getBlockState(pendingPos).getBlock() != Blocks.FARMLAND || !mc.world.getBlockState(pendingPos.up()).isAir()) {
                setCooldown(pendingPos, now, 220L);
                setCooldown(pendingPos.up(), now, 220L);
                resetAction();
                return;
            }
        }

        if (pendingType == ActionType.HARVEST) {
            if (mc.world == null || mc.world.getBlockState(pendingPos).getBlock() != Blocks.CARROTS) {
                setCooldown(pendingPos, now, 180L);
                resetAction();
                return;
            }
        }

        if (pendingType == ActionType.BONE_MEAL) {
            if (mc.world == null || mc.world.getBlockState(pendingPos).getBlock() != Blocks.CARROTS) {
                setCooldown(pendingPos, now, 180L);
                resetAction();
                return;
            }
        }

        if (stage == Stage.SWAP_IN) {
            renderStack = p.getMainHandStack();
            InventoryTask.swapHand(pendingSlot, Hand.MAIN_HAND, true);
            stage = Stage.DO_ACTION;
            return;
        }

        if (stage == Stage.DO_ACTION) {
            if (pendingType == ActionType.HARVEST) {
                mc.interactionManager.attackBlock(pendingPos, Direction.UP);
                p.swingHand(Hand.MAIN_HAND);
                setCooldown(pendingPos, now, 260L);
                setCooldown(pendingPos.up(), now, 240L);
            } else {
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pendingPos), Direction.UP, pendingPos, true);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                p.swingHand(Hand.MAIN_HAND);
                if (pendingType == ActionType.BONE_MEAL) setCooldown(pendingPos, now, 1L);
                else {
                    setCooldown(pendingPos, now, 240L);
                    setCooldown(pendingPos.up(), now, 240L);
                }
            }
            stage = pendingNeedSwap ? Stage.SWAP_BACK : Stage.CLEAR;
            return;
        }

        if (stage == Stage.SWAP_BACK) {
            InventoryTask.swapHand(pendingSlot, Hand.MAIN_HAND, true, true);
            stage = Stage.CLEAR;
            return;
        }

        if (stage == Stage.CLEAR) {
            renderStack = null;
            resetAction();
        }
    }

    void resetAction() {
        stage = Stage.IDLE;
        pendingType = null;
        pendingPos = null;
        pendingSlot = null;
        pendingNeedSwap = false;
    }

    Slot findHoeSlot() {
        return InventoryTask.slots()
                .sorted(Comparator.comparing(s -> s.equals(InventoryTask.mainHandSlot())))
                .filter(s -> {
                    ItemStack st = s.getStack();
                    return st != null && !st.isEmpty() && st.getItem() instanceof HoeItem;
                })
                .findFirst()
                .orElse(null);
    }

    Slot findItemSlot(Item item) {
        if (item == null) return null;
        return InventoryTask.slots()
                .sorted(Comparator.comparing(s -> s.equals(InventoryTask.mainHandSlot())))
                .filter(s -> isItem(s.getStack(), item))
                .findFirst()
                .orElse(null);
    }

    static boolean isItem(ItemStack st, Item it) {
        return st != null && !st.isEmpty() && st.getItem() == it;
    }

    static double dist2(BlockPos a, BlockPos b) {
        double dx = (a.getX() + 0.5) - (b.getX() + 0.5);
        double dy = (a.getY() + 0.5) - (b.getY() + 0.5);
        double dz = (a.getZ() + 0.5) - (b.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz;
    }

    static int cropAge(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof CropBlock cb) return cb.getAge(st);
        return 0;
    }

    static int cropMaxAge(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof CropBlock cb) return cb.getMaxAge();
        return 7;
    }

    void setCooldown(BlockPos pos, long now, long ms) {
        cooldown.put(pos, now + ms);
    }

    boolean isOnCooldown(BlockPos pos, long now) {
        Long t = cooldown.get(pos);
        return t != null && now < t;
    }

    void cleanupCooldown(long now) {
        cooldown.entrySet().removeIf(e -> now >= e.getValue() + 350L);
    }

    static int clampI(int v, int mn, int mx) {
        return Math.max(mn, Math.min(mx, v));
    }

    static long clampL(long v, long mn, long mx) {
        return Math.max(mn, Math.min(mx, v));
    }

    enum ActionType {
        HARVEST,
        BONE_MEAL,
        PLANT
    }

    enum Stage {
        IDLE,
        SWAP_IN,
        DO_ACTION,
        SWAP_BACK,
        CLEAR
    }

    static final class Target {
        final ActionType type;
        final BlockPos pos;

        Target(ActionType type, BlockPos pos) {
            this.type = type;
            this.pos = pos;
        }
    }
}