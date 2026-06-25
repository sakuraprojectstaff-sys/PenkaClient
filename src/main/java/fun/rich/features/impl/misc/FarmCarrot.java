package fun.rich.features.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmCarrot {

    static final long AREA_SCAN_INTERVAL_MS = 320L;
    static final long TARGET_SCAN_INTERVAL_MS = 85L;
    static final long FULL_RESCAN_INTERVAL_MS = 260L;
    static final long ACTION_INTERVAL_MS = 80L;
    static final long INVENTORY_SWAP_INTERVAL_MS = 90L;

    static final long PICKUP_FOCUS_MS = 3600L;
    static final long STUCK_TIMEOUT_MS = 700L;
    static final long SWEEP_REPATH_MS = 650L;

    static final int CARROT_RESERVE = 64;
    static final int EXTRA_CARROT_DELIVER_THRESHOLD = 128;

    static final double BLOCK_REACH_SQ = 25.0D;
    static final double ITEM_PICKUP_CLOSE_SQ = 1.35D * 1.35D;
    static final double ITEM_SEARCH_RADIUS = 9.0D;

    static final int SWEEP_STEP = 2;
    static final int LOCAL_SCAN_RADIUS_XZ = 14;
    static final int LOCAL_SCAN_RADIUS_Y = 3;

    final MinecraftClient mc = MinecraftClient.getInstance();

    long lastAreaScanAt;
    long lastTargetScanAt;
    long lastFullRescanAt;
    long nextActionAt;
    long nextInventorySwapAt;

    int cachedEmptyFarmland;
    int cachedMatureCarrot;

    BlockPos targetCarrotCrop;
    BlockPos targetCarrotFarmland;

    BlockPos pickupFocusCropPos;
    long pickupFocusUntilAt;

    BlockPos trackedPathGoal;
    Vec3d trackedPathStartPos;
    long trackedPathSinceAt;
    double trackedPathLastGoalDistSq;
    long trackedNoProgressSinceAt;

    int sweepIndex;
    long lastSweepRepathAt;

    public interface Context {
        BlockPos getMinPoint();
        BlockPos getMaxPoint();
        boolean isInSelectedArea(BlockPos pos);
        boolean isInsideAreaMargin(BlockPos pos, int margin);
        void pathToWorkGoal(BlockPos goal);
        void stopWorkPathing();
    }

    public void activate() {
        reset();
    }

    public void deactivate() {
        reset();
    }

    public void reset() {
        lastAreaScanAt = 0L;
        lastTargetScanAt = 0L;
        lastFullRescanAt = 0L;
        nextActionAt = 0L;
        nextInventorySwapAt = 0L;

        cachedEmptyFarmland = 0;
        cachedMatureCarrot = 0;

        targetCarrotCrop = null;
        targetCarrotFarmland = null;

        pickupFocusCropPos = null;
        pickupFocusUntilAt = 0L;

        trackedPathGoal = null;
        trackedPathStartPos = null;
        trackedPathSinceAt = 0L;
        trackedPathLastGoalDistSq = Double.MAX_VALUE;
        trackedNoProgressSinceAt = 0L;

        sweepIndex = 0;
        lastSweepRepathAt = 0L;
    }

    public void tick(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        if (ctx.getMinPoint() == null || ctx.getMaxPoint() == null) {
            return;
        }

        updateAreaCacheIfNeeded(ctx);

        if (tryHandlePickupPhase(ctx)) {
            return;
        }

        refreshTargets(ctx);

        if (tryPickupNearbyDrops(ctx)) {
            return;
        }

        if (!hasCarrotsInHotbar() && hasCarrotsAnywhere()) {
            pullCarrotsToHotbar();
        }

        if (targetCarrotCrop != null && tryHandleHarvestTarget(ctx, targetCarrotCrop)) {
            return;
        }

        if (targetCarrotFarmland != null && hasCarrotsAnywhere() && tryHandlePlantTarget(ctx, targetCarrotFarmland)) {
            return;
        }

        if (trySweepArea(ctx)) {
            return;
        }

        ctx.stopWorkPathing();
        resetTrackedGoal();
    }

    public int getCachedEmptyFarmland() {
        return cachedEmptyFarmland;
    }

    public int getCachedMatureCarrot() {
        return cachedMatureCarrot;
    }

    public boolean hasAnyCultureToDeposit() {
        return countCarrotsOverReserve() > 0;
    }

    public int countCarrotsOverReserve() {
        int reserve = getDynamicCarrotReserve();
        return Math.max(0, countItemInInventory(Items.CARROT) - reserve);
    }

    public boolean shouldGoToStorageNow() {
        if (!hasAnyCultureToDeposit()) {
            return false;
        }

        int carrots = countItemInInventory(Items.CARROT);
        int carrotReserve = getDynamicCarrotReserve();
        int carrotsOverReserve = Math.max(0, carrots - carrotReserve);

        boolean inventoryTight = getFreeInventorySlots(36) <= 1;
        boolean carrotsWorthDelivering = carrotsOverReserve >= 64;
        boolean tooManyCarrots = carrotsOverReserve >= EXTRA_CARROT_DELIVER_THRESHOLD;

        if (cachedEmptyFarmland > 0) {
            if (inventoryTight && carrotsOverReserve > 0) {
                return true;
            }
            return carrotsWorthDelivering && inventoryTight;
        }

        if (carrotsWorthDelivering) {
            return true;
        }

        if (inventoryTight && carrotsOverReserve > 0) {
            return true;
        }

        return tooManyCarrots && cachedMatureCarrot <= 0;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        int carrotReserve = getDynamicCarrotReserve();
        int carrotsTotal = countItemInInventory(Items.CARROT);
        int carrotsOver = Math.max(0, carrotsTotal - carrotReserve);
        if (carrotsOver <= 0) {
            return false;
        }

        int wholeDropCarrotSlot = findInventorySlotPreferWholeAtMost(Items.CARROT, carrotsOver);
        if (wholeDropCarrotSlot >= 0) {
            return throwInventorySlot(wholeDropCarrotSlot, true);
        }

        int carrotSlot = findInventorySlotPreferWhole(Items.CARROT);
        if (carrotSlot < 0) {
            return false;
        }

        return throwInventorySlot(carrotSlot, false);
    }

    private int getDynamicCarrotReserve() {
        int reserve = CARROT_RESERVE;

        if (cachedEmptyFarmland > 0) {
            int safety = 24;
            reserve = Math.max(reserve, cachedEmptyFarmland + safety);

            if (cachedEmptyFarmland >= 96) {
                reserve = Math.max(reserve, cachedEmptyFarmland + 40);
            }
        }

        if (cachedMatureCarrot > 0 && cachedEmptyFarmland <= 0) {
            reserve = Math.max(reserve, 72);
        }

        return Math.min(512, reserve);
    }

    private void refreshTargets(Context ctx) {
        long now = System.currentTimeMillis();

        if (targetCarrotCrop != null && !isValidCarrotCropTarget(ctx, targetCarrotCrop)) {
            targetCarrotCrop = null;
        }
        if (targetCarrotFarmland != null && !isValidCarrotFarmlandTarget(ctx, targetCarrotFarmland)) {
            targetCarrotFarmland = null;
        }

        if (now - lastTargetScanAt < TARGET_SCAN_INTERVAL_MS) {
            return;
        }

        lastTargetScanAt = now;
        scanLocalTargets(ctx);

        boolean needCrop = targetCarrotCrop == null;
        boolean needPlant = hasCarrotsAnywhere() && targetCarrotFarmland == null;

        if ((needCrop || needPlant) && now - lastFullRescanAt >= FULL_RESCAN_INTERVAL_MS) {
            lastFullRescanAt = now;
            scanFullTargets(ctx);
        }
    }

    private void scanLocalTargets(Context ctx) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        BlockPos p = mc.player.getBlockPos();

        int fromX = Math.max(min.getX(), p.getX() - LOCAL_SCAN_RADIUS_XZ);
        int toX = Math.min(max.getX(), p.getX() + LOCAL_SCAN_RADIUS_XZ);
        int fromY = Math.max(min.getY(), p.getY() - LOCAL_SCAN_RADIUS_Y);
        int toY = Math.min(max.getY(), p.getY() + LOCAL_SCAN_RADIUS_Y);
        int fromZ = Math.max(min.getZ(), p.getZ() - LOCAL_SCAN_RADIUS_XZ);
        int toZ = Math.min(max.getZ(), p.getZ() + LOCAL_SCAN_RADIUS_XZ);

        Vec3d playerPos = mc.player.getPos();

        double bestCrop = targetCarrotCrop != null ? playerPos.squaredDistanceTo(getInteractPoint(targetCarrotCrop)) : Double.MAX_VALUE;
        double bestFarmland = targetCarrotFarmland != null ? playerPos.squaredDistanceTo(Vec3d.ofCenter(targetCarrotFarmland.up())) : Double.MAX_VALUE;

        boolean canPlant = hasCarrotsAnywhere();

        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (isMatureCarrot(state)) {
                        double d = playerPos.squaredDistanceTo(getInteractPoint(pos));
                        if (d < bestCrop) {
                            bestCrop = d;
                            targetCarrotCrop = pos.toImmutable();
                        }
                        continue;
                    }

                    if (!canPlant) {
                        continue;
                    }

                    BlockPos farmland = extractPlantFarmlandFromAreaCell(ctx, pos, state);
                    if (farmland == null) {
                        continue;
                    }

                    double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(farmland.up()));
                    if (d < bestFarmland) {
                        bestFarmland = d;
                        targetCarrotFarmland = farmland.toImmutable();
                    }
                }
            }
        }
    }

    private void scanFullTargets(Context ctx) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        double bestCrop = Double.MAX_VALUE;
        double bestFarmland = Double.MAX_VALUE;

        BlockPos bestCropPos = null;
        BlockPos bestFarmlandPos = null;

        boolean canPlant = hasCarrotsAnywhere();

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(pos);

            if (isMatureCarrot(state)) {
                double d = playerPos.squaredDistanceTo(getInteractPoint(pos));
                if (d < bestCrop) {
                    bestCrop = d;
                    bestCropPos = pos.toImmutable();
                }
                continue;
            }

            if (!canPlant) {
                continue;
            }

            BlockPos farmland = extractPlantFarmlandFromAreaCell(ctx, pos, state);
            if (farmland == null) {
                continue;
            }

            double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(farmland.up()));
            if (d < bestFarmland) {
                bestFarmland = d;
                bestFarmlandPos = farmland.toImmutable();
            }
        }

        targetCarrotCrop = bestCropPos;

        if (hasCarrotsAnywhere()) {
            targetCarrotFarmland = bestFarmlandPos;
        } else {
            targetCarrotFarmland = null;
        }
    }

    private BlockPos extractPlantFarmlandFromAreaCell(Context ctx, BlockPos areaCellPos, BlockState areaCellState) {
        if (ctx == null || areaCellPos == null || mc.world == null) {
            return null;
        }

        if (!ctx.isInSelectedArea(areaCellPos)) {
            return null;
        }

        if (areaCellState != null && areaCellState.isAir()) {
            BlockPos down = areaCellPos.down();
            BlockState below = mc.world.getBlockState(down);
            if (below.isOf(Blocks.FARMLAND)) {
                return down.toImmutable();
            }
        }

        if (areaCellState != null && areaCellState.isOf(Blocks.FARMLAND)) {
            BlockPos up = areaCellPos.up();
            if (ctx.isInSelectedArea(up) && mc.world.getBlockState(up).isAir()) {
                return areaCellPos.toImmutable();
            }
        }

        return null;
    }

    private boolean tryHandleHarvestTarget(Context ctx, BlockPos cropPos) {
        if (!isValidCarrotCropTarget(ctx, cropPos)) {
            targetCarrotCrop = null;
            return false;
        }

        if (canReachBlock(cropPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();

            if (tryHarvestCarrot(cropPos)) {
                targetCarrotCrop = null;
                BlockPos farmlandUnder = cropPos.down();
                if (mc.world != null && mc.world.getBlockState(farmlandUnder).isOf(Blocks.FARMLAND)) {
                    targetCarrotFarmland = farmlandUnder.toImmutable();
                }
                pickupFocusCropPos = cropPos.toImmutable();
                pickupFocusUntilAt = System.currentTimeMillis() + PICKUP_FOCUS_MS;
                return true;
            }
            return false;
        }

        BlockPos approach = findApproachPosForCrop(ctx, cropPos);
        if (approach == null) {
            pathToGoalTracked(ctx, cropPos);
            return true;
        }

        pathToGoalTracked(ctx, approach);
        return true;
    }

    private boolean tryHandlePlantTarget(Context ctx, BlockPos farmlandPos) {
        if (!isValidCarrotFarmlandTarget(ctx, farmlandPos)) {
            targetCarrotFarmland = null;
            return false;
        }

        if (!hasCarrotsAnywhere()) {
            targetCarrotFarmland = null;
            return false;
        }

        if (!hasCarrotsInHotbar()) {
            pullCarrotsToHotbar();
        }

        if (canReachBlock(farmlandPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();

            if (tryPlantCarrot(farmlandPos)) {
                targetCarrotFarmland = null;
                return true;
            }
            return false;
        }

        BlockPos approach = findApproachPosForFarmland(ctx, farmlandPos);
        if (approach == null) {
            pathToGoalTracked(ctx, farmlandPos.up());
            return true;
        }

        pathToGoalTracked(ctx, approach);
        return true;
    }

    private boolean tryHandlePickupPhase(Context ctx) {
        if (pickupFocusCropPos == null || mc.player == null || mc.world == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > pickupFocusUntilAt) {
            pickupFocusCropPos = null;
            return false;
        }

        ItemEntity drop = findNearestRelevantDropAround(pickupFocusCropPos, ITEM_SEARCH_RADIUS);
        if (drop == null) {
            return false;
        }

        Vec3d itemPos = drop.getPos();
        if (mc.player.getPos().squaredDistanceTo(itemPos) <= ITEM_PICKUP_CLOSE_SQ) {
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return true;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, itemPos);
        if (pickupGoal != null) {
            pathToGoalTracked(ctx, pickupGoal);
            return true;
        }

        pathToGoalTracked(ctx, BlockPos.ofFloored(itemPos));
        return true;
    }

    private boolean tryPickupNearbyDrops(Context ctx) {
        if (mc.player == null) {
            return false;
        }

        ItemEntity drop = findNearestRelevantDropAround(mc.player.getBlockPos(), 6.5D);
        if (drop == null) {
            return false;
        }

        Vec3d itemPos = drop.getPos();
        if (mc.player.getPos().squaredDistanceTo(itemPos) <= ITEM_PICKUP_CLOSE_SQ) {
            return false;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, itemPos);
        if (pickupGoal == null) {
            return false;
        }

        pathToGoalTracked(ctx, pickupGoal);
        return true;
    }

    private void pathToGoalTracked(Context ctx, BlockPos goal) {
        if (ctx == null || goal == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        double goalDistSq = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(goal));

        if (trackedPathGoal == null || !trackedPathGoal.equals(goal)) {
            trackedPathGoal = goal.toImmutable();
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            ctx.pathToWorkGoal(trackedPathGoal);
            return;
        }

        if (trackedPathStartPos == null) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            ctx.pathToWorkGoal(trackedPathGoal);
            return;
        }

        if (goalDistSq + 0.15D < trackedPathLastGoalDistSq) {
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
        }

        double movedSq = mc.player.getPos().squaredDistanceTo(trackedPathStartPos);

        if (movedSq >= 0.35D) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
        }

        boolean stuckByMovement = now - trackedPathSinceAt >= STUCK_TIMEOUT_MS && movedSq < 0.20D;
        boolean stuckByNoProgress = now - trackedNoProgressSinceAt >= STUCK_TIMEOUT_MS && goalDistSq > 4.0D;

        if (stuckByMovement || stuckByNoProgress) {
            ctx.stopWorkPathing();
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            ctx.pathToWorkGoal(trackedPathGoal);
            return;
        }

        ctx.pathToWorkGoal(trackedPathGoal);
    }

    private void resetTrackedGoal() {
        trackedPathGoal = null;
        trackedPathStartPos = null;
        trackedPathSinceAt = 0L;
        trackedPathLastGoalDistSq = Double.MAX_VALUE;
        trackedNoProgressSinceAt = 0L;
    }

    private boolean trySweepArea(Context ctx) {
        if (mc.player == null || mc.world == null) {
            return false;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return false;
        }

        if (trackedPathGoal != null && mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(trackedPathGoal)) <= 4.0D) {
            resetTrackedGoal();
        }

        long now = System.currentTimeMillis();
        if (trackedPathGoal != null && now - lastSweepRepathAt < SWEEP_REPATH_MS) {
            return true;
        }

        int sizeX = Math.max(1, ((max.getX() - min.getX()) / SWEEP_STEP) + 1);
        int sizeZ = Math.max(1, ((max.getZ() - min.getZ()) / SWEEP_STEP) + 1);
        int total = sizeX * sizeZ;
        if (total <= 0) {
            return false;
        }

        for (int tries = 0; tries < total; tries++) {
            int idx = (sweepIndex + tries) % total;
            int row = idx / sizeX;
            int col = idx % sizeX;

            if ((row & 1) == 1) {
                col = sizeX - 1 - col;
            }

            int x = min.getX() + col * SWEEP_STEP;
            int z = min.getZ() + row * SWEEP_STEP;
            x = Math.min(x, max.getX());
            z = Math.min(z, max.getZ());

            BlockPos sweepGoal = findSweepStandable(ctx, x, z, min.getY(), max.getY());
            if (sweepGoal == null) {
                continue;
            }

            sweepIndex = (idx + 1) % total;

            if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(sweepGoal)) <= 4.0D) {
                continue;
            }

            lastSweepRepathAt = now;
            pathToGoalTracked(ctx, sweepGoal);
            return true;
        }

        return false;
    }

    private BlockPos findSweepStandable(Context ctx, int x, int z, int minY, int maxY) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        int py = mc.player.getBlockY();

        for (int d = 0; d <= Math.max(5, maxY - minY + 2); d++) {
            int y1 = MathHelper.clamp(py + d, minY - 1, maxY + 1);
            BlockPos p1 = new BlockPos(x, y1, z);
            if (ctx.isInsideAreaMargin(p1, 2) && isStandablePos(p1)) {
                return p1.toImmutable();
            }

            if (d == 0) {
                continue;
            }

            int y2 = MathHelper.clamp(py - d, minY - 1, maxY + 1);
            BlockPos p2 = new BlockPos(x, y2, z);
            if (ctx.isInsideAreaMargin(p2, 2) && isStandablePos(p2)) {
                return p2.toImmutable();
            }
        }

        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int y = minY - 1; y <= maxY + 1; y++) {
                        BlockPos p = new BlockPos(x + dx, y, z + dz);
                        if (ctx.isInsideAreaMargin(p, 2) && isStandablePos(p)) {
                            return p.toImmutable();
                        }
                    }
                }
            }
        }

        return null;
    }

    private void updateAreaCacheIfNeeded(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastAreaScanAt < AREA_SCAN_INTERVAL_MS) {
            return;
        }
        lastAreaScanAt = now;

        cachedEmptyFarmland = 0;
        cachedMatureCarrot = 0;

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null || mc.world == null) {
            return;
        }

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(pos);

            if (isMatureCarrot(state)) {
                cachedMatureCarrot++;
                continue;
            }

            if (state.isAir()) {
                BlockPos down = pos.down();
                if (mc.world.getBlockState(down).isOf(Blocks.FARMLAND)) {
                    cachedEmptyFarmland++;
                }
            }
        }
    }

    private ItemEntity findNearestRelevantDropAround(BlockPos centerPos, double radius) {
        if (mc.world == null || mc.player == null || centerPos == null) {
            return null;
        }

        Vec3d c = Vec3d.ofCenter(centerPos);
        Box box = new Box(
                c.x - radius, c.y - 2.5D, c.z - radius,
                c.x + radius, c.y + 3.5D, c.z + radius
        );

        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity e : mc.world.getOtherEntities(null, box, ent -> ent instanceof ItemEntity)) {
            if (!(e instanceof ItemEntity itemEntity)) {
                continue;
            }

            ItemStack stack = itemEntity.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (!stack.isOf(Items.CARROT)) {
                continue;
            }

            double d = mc.player.getPos().squaredDistanceTo(itemEntity.getPos());
            if (d < bestDist) {
                bestDist = d;
                best = itemEntity;
            }
        }

        return best;
    }

    private BlockPos findPickupGoalNearItem(Context ctx, Vec3d itemPos) {
        if (ctx == null || itemPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos anchor = BlockPos.ofFloored(itemPos.x, itemPos.y, itemPos.z);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 0; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }

                        double score = Vec3d.ofCenter(p).squaredDistanceTo(itemPos)
                                + mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) * 0.18D;

                        if (score < bestScore) {
                            bestScore = score;
                            best = p.toImmutable();
                        }
                    }
                }
            }

            if (best != null) {
                return best;
            }
        }

        return null;
    }

    private boolean isValidCarrotCropTarget(Context ctx, BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }
        if (!ctx.isInSelectedArea(pos)) {
            return false;
        }
        return isMatureCarrot(mc.world.getBlockState(pos));
    }

    private boolean isValidCarrotFarmlandTarget(Context ctx, BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.FARMLAND)) {
            return false;
        }

        BlockPos plantPos = pos.up();
        if (!ctx.isInSelectedArea(plantPos)) {
            return false;
        }

        return mc.world.getBlockState(plantPos).isAir();
    }

    private boolean tryHarvestCarrot(BlockPos cropPos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            return false;
        }
        if (!isMatureCarrot(mc.world.getBlockState(cropPos))) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return false;
        }

        lookAt(getInteractPoint(cropPos));

        try {
            mc.interactionManager.attackBlock(cropPos, Direction.UP);
            mc.player.swingHand(Hand.MAIN_HAND);
            nextActionAt = now + ACTION_INTERVAL_MS;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean tryPlantCarrot(BlockPos farmlandPos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            return false;
        }

        if (!mc.world.getBlockState(farmlandPos).isOf(Blocks.FARMLAND)) {
            return false;
        }
        if (!mc.world.getBlockState(farmlandPos.up()).isAir()) {
            return false;
        }

        int carrotSlot = findHotbarSlot(Items.CARROT);
        if (carrotSlot < 0) {
            if (!pullCarrotsToHotbar()) {
                return false;
            }
            carrotSlot = findHotbarSlot(Items.CARROT);
            if (carrotSlot < 0) {
                return false;
            }
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return false;
        }

        mc.player.getInventory().selectedSlot = carrotSlot;

        Vec3d hitPos = new Vec3d(
                farmlandPos.getX() + 0.5D,
                farmlandPos.getY() + 0.98D,
                farmlandPos.getZ() + 0.5D
        );

        lookAt(hitPos);

        try {
            BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, farmlandPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            nextActionAt = now + ACTION_INTERVAL_MS;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasCarrotsInHotbar() {
        return findHotbarSlot(Items.CARROT) >= 0;
    }

    private boolean hasCarrotsAnywhere() {
        return countItemInInventory(Items.CARROT) > 0;
    }

    private boolean pullCarrotsToHotbar() {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        if (findHotbarSlot(Items.CARROT) >= 0) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now < nextInventorySwapAt) {
            return false;
        }

        int carrotInvSlot = findInventorySlot(Items.CARROT, 9, 36);
        if (carrotInvSlot < 0) {
            return false;
        }

        int targetHotbar = findBestHotbarSlotForCarrots();
        if (targetHotbar < 0) {
            targetHotbar = mc.player.getInventory().selectedSlot;
            if (targetHotbar < 0 || targetHotbar > 8) {
                targetHotbar = 0;
            }
        }

        try {
            int syncId = mc.player.currentScreenHandler.syncId;
            int slotId = toScreenHandlerSlotId(carrotInvSlot);
            mc.interactionManager.clickSlot(syncId, slotId, targetHotbar, SlotActionType.SWAP, mc.player);
            nextInventorySwapAt = now + INVENTORY_SWAP_INTERVAL_MS;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findBestHotbarSlotForCarrots() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.CARROT)) {
                return i;
            }
            if (isFoodStack(s)) {
                continue;
            }
            return i;
        }

        return mc.player.getInventory().selectedSlot;
    }

    private int findHotbarSlot(Item item) {
        return findInventorySlot(item, 0, 9);
    }

    private int findInventorySlot(Item item, int fromInclusive, int toExclusive) {
        if (mc.player == null) {
            return -1;
        }

        int from = Math.max(0, fromInclusive);
        int to = Math.min(36, toExclusive);

        for (int i = from; i < to; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.isOf(item)) {
                return i;
            }
        }

        return -1;
    }

    private int findInventorySlotPreferWhole(Item item) {
        if (mc.player == null) {
            return -1;
        }

        int bestSlot = -1;
        int bestCount = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) {
                continue;
            }
            if (s.getCount() > bestCount) {
                bestCount = s.getCount();
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int findInventorySlotPreferWholeAtMost(Item item, int maxCount) {
        if (mc.player == null || maxCount <= 0) {
            return -1;
        }

        int bestSlot = -1;
        int bestCount = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty() || !s.isOf(item)) {
                continue;
            }
            int c = s.getCount();
            if (c > maxCount) {
                continue;
            }
            if (c > bestCount) {
                bestCount = c;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private boolean throwInventorySlot(int inventoryIndex, boolean wholeStack) {
        try {
            if (mc.player == null || mc.interactionManager == null) {
                return false;
            }

            int syncId = mc.player.currentScreenHandler.syncId;
            int slotId = toScreenHandlerSlotId(inventoryIndex);
            int button = wholeStack ? 1 : 0;
            mc.interactionManager.clickSlot(syncId, slotId, button, SlotActionType.THROW, mc.player);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int toScreenHandlerSlotId(int inventoryIndex) {
        if (inventoryIndex >= 0 && inventoryIndex <= 8) {
            return 36 + inventoryIndex;
        }
        return inventoryIndex;
    }

    private int countItemInInventory(Item item) {
        if (mc.player == null) {
            return 0;
        }

        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isEmpty() && s.isOf(item)) {
                count += s.getCount();
            }
        }

        return count;
    }

    private int getFreeInventorySlots(int maxSlots) {
        if (mc.player == null) {
            return 0;
        }

        int free = 0;
        int to = Math.min(36, Math.max(0, maxSlots));

        for (int i = 0; i < to; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                free++;
            }
        }

        return free;
    }

    private BlockPos findApproachPosForCrop(Context ctx, BlockPos cropPos) {
        return findApproachPosForInteract(ctx, cropPos, cropPos);
    }

    private BlockPos findApproachPosForFarmland(Context ctx, BlockPos farmlandPos) {
        return findApproachPosForInteract(ctx, farmlandPos.up(), farmlandPos);
    }

    private BlockPos findApproachPosForInteract(Context ctx, BlockPos feetLevelCenter, BlockPos interactPos) {
        if (ctx == null || feetLevelCenter == null || interactPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int baseY = feetLevelCenter.getY();

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = new BlockPos(feetLevelCenter.getX() + dx, baseY + dy, feetLevelCenter.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }
                        if (!canReachBlockFromStandPos(p, interactPos)) {
                            continue;
                        }

                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double dTarget = Vec3d.ofCenter(p).squaredDistanceTo(getInteractPoint(interactPos));
                        double score = dPlayer + dTarget * 0.12D;

                        if (score < bestScore) {
                            bestScore = score;
                            best = p.toImmutable();
                        }
                    }
                }
            }

            if (best != null) {
                return best;
            }
        }

        return null;
    }

    private boolean isStandablePos(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }

        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState below = mc.world.getBlockState(pos.down());

        if (!isPassableForPlayer(feet, pos, true)) {
            return false;
        }
        if (!isPassableForPlayer(head, pos.up(), false)) {
            return false;
        }

        if (below.isAir()) {
            return false;
        }

        try {
            if (below.getFluidState() != null && !below.getFluidState().isEmpty()) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (below.getCollisionShape(mc.world, pos.down()).isEmpty()) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        if (below.isOf(Blocks.CACTUS) || below.isOf(Blocks.MAGMA_BLOCK) || below.isOf(Blocks.CAMPFIRE) || below.isOf(Blocks.SOUL_CAMPFIRE)) {
            return false;
        }

        return true;
    }

    private boolean isPassableForPlayer(BlockState state, BlockPos pos, boolean allowCropFeet) {
        if (state == null || mc.world == null || pos == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        if (allowCropFeet && isWalkThroughCrop(state)) {
            return true;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isWalkThroughCrop(BlockState state) {
        return state.isOf(Blocks.WHEAT)
                || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES)
                || state.isOf(Blocks.BEETROOTS)
                || state.isOf(Blocks.NETHER_WART);
    }

    private boolean canReachBlockFromStandPos(BlockPos standPos, BlockPos targetPos) {
        Vec3d eye = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.62D, standPos.getZ() + 0.5D);
        return eye.squaredDistanceTo(getInteractPoint(targetPos)) <= BLOCK_REACH_SQ;
    }

    private boolean canReachBlock(BlockPos pos) {
        if (mc.player == null || pos == null) {
            return false;
        }
        return mc.player.getEyePos().squaredDistanceTo(getInteractPoint(pos)) <= BLOCK_REACH_SQ;
    }

    private Vec3d getInteractPoint(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return pos == null ? Vec3d.ZERO : Vec3d.ofCenter(pos);
        }

        BlockState state = mc.world.getBlockState(pos);

        if (state.isOf(Blocks.FARMLAND)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.98D, pos.getZ() + 0.5D);
        }

        if (isWalkThroughCrop(state)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.45D, pos.getZ() + 0.5D);
        }

        return Vec3d.ofCenter(pos);
    }

    private void lookAt(Vec3d target) {
        if (mc.player == null || target == null) {
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horiz))));
        targetPitch = MathHelper.clamp(targetPitch, -89.0F, 89.0F);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDeltaRaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDeltaRaw = targetPitch - currentPitch;

        float closeScaleYaw = MathHelper.clamp(Math.abs(yawDeltaRaw) / 18.0F, 0.0F, 1.0F);
        float closeScalePitch = MathHelper.clamp(Math.abs(pitchDeltaRaw) / 14.0F, 0.0F, 1.0F);
        float humanScale = Math.max(closeScaleYaw, closeScalePitch);

        long t = System.currentTimeMillis();
        double phase = t * 0.0105D + target.x * 0.41D + target.y * 0.23D + target.z * 0.37D;
        float humanYaw = (float) (Math.sin(phase) * 0.42D) * humanScale;
        float humanPitch = (float) (Math.cos(phase * 0.91D + 1.37D) * 0.24D) * humanScale;

        targetYaw = currentYaw + MathHelper.wrapDegrees((targetYaw + humanYaw) - currentYaw);
        targetPitch = MathHelper.clamp(targetPitch + humanPitch, -89.0F, 89.0F);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float yawStep = MathHelper.clamp(2.6F + Math.abs(yawDelta) * 0.42F, 2.6F, 15.5F);
        float pitchStep = MathHelper.clamp(2.1F + Math.abs(pitchDelta) * 0.36F, 2.1F, 11.0F);

        float newYaw = approachAngle(currentYaw, targetYaw, yawStep);
        float newPitch = approachLinear(currentPitch, targetPitch, pitchStep);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -89.0F, 89.0F));
    }

    private float approachAngle(float current, float target, float maxStep) {
        float delta = MathHelper.wrapDegrees(target - current);
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }

    private float approachLinear(float current, float target, float maxStep) {
        float delta = target - current;
        if (delta > maxStep) {
            delta = maxStep;
        } else if (delta < -maxStep) {
            delta = -maxStep;
        }
        return current + delta;
    }

    private boolean isMatureCarrot(BlockState state) {
        if (state == null || !state.isOf(Blocks.CARROTS)) {
            return false;
        }

        try {
            if (state.contains(Properties.AGE_7)) {
                return state.get(Properties.AGE_7) >= 7;
            }
        } catch (Throwable ignored) {
        }

        return true;
    }

    private boolean isFoodStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        try {
            Method m = stack.getClass().getMethod("isFood");
            Object r = m.invoke(stack);
            if (r instanceof Boolean) {
                return (Boolean) r;
            }
        } catch (Throwable ignored) {
        }

        try {
            Item item = stack.getItem();
            if (item != null) {
                Method m = item.getClass().getMethod("isFood");
                Object r = m.invoke(item);
                if (r instanceof Boolean) {
                    return (Boolean) r;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> dctClass = Class.forName("net.minecraft.component.DataComponentTypes");
            Field foodField = dctClass.getField("FOOD");
            Object foodComponent = foodField.get(null);
            if (foodComponent == null) {
                return false;
            }

            for (Method m : stack.getClass().getMethods()) {
                if (!m.getName().equals("contains") || m.getParameterCount() != 1) {
                    continue;
                }
                try {
                    Object r = m.invoke(stack, foodComponent);
                    if (r instanceof Boolean && (Boolean) r) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }

            for (Method m : stack.getClass().getMethods()) {
                if (!m.getName().equals("get") || m.getParameterCount() != 1) {
                    continue;
                }
                try {
                    Object r = m.invoke(stack, foodComponent);
                    if (r != null) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}