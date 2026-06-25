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
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmBamboo {

    static final long AREA_SCAN_INTERVAL_MS = 320L;
    static final long TARGET_SCAN_INTERVAL_MS = 85L;
    static final long FULL_RESCAN_INTERVAL_MS = 260L;
    static final long ACTION_INTERVAL_MS = 80L;

    static final long PICKUP_FOCUS_MS = 4200L;
    static final long STUCK_TIMEOUT_MS = 700L;
    static final long SWEEP_REPATH_MS = 650L;

    static final double BLOCK_REACH_SQ = 25.0D;
    static final double ITEM_PICKUP_CLOSE_SQ = 1.55D * 1.55D;
    static final double ITEM_PICKUP_GOAL_REACH_SQ = 2.10D * 2.10D;
    static final double ITEM_SEARCH_RADIUS = 10.5D;

    static final int SWEEP_STEP = 2;
    static final int LOCAL_SCAN_RADIUS_XZ = 14;
    static final int LOCAL_SCAN_RADIUS_Y = 5;

    static final int BAMBOO_SCAN_EXTRA_DOWN = 1;
    static final int BAMBOO_SCAN_EXTRA_UP = 10;

    final MinecraftClient mc = MinecraftClient.getInstance();

    long lastAreaScanAt;
    long lastTargetScanAt;
    long lastFullRescanAt;
    long nextActionAt;

    int cachedHarvestableBamboo;

    BlockPos targetBamboo;

    BlockPos pickupFocusBambooPos;
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

        cachedHarvestableBamboo = 0;

        targetBamboo = null;

        pickupFocusBambooPos = null;
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

        if (targetBamboo != null && tryHandleHarvestTarget(ctx, targetBamboo)) {
            return;
        }

        if (trySweepArea(ctx)) {
            return;
        }

        ctx.stopWorkPathing();
        resetTrackedGoal();
    }

    public int getCachedHarvestableBamboo() {
        return cachedHarvestableBamboo;
    }

    public boolean hasAnyCultureToDeposit() {
        return countItemInInventory(Items.BAMBOO) > 0;
    }

    public boolean shouldGoToStorageNow() {
        int bamboo = countItemInInventory(Items.BAMBOO);
        if (bamboo <= 0) {
            return false;
        }

        boolean inventoryTight = getFreeInventorySlots(36) <= 1;
        boolean lotsOfBamboo = bamboo >= 128;
        boolean enoughBamboo = bamboo >= 64;
        boolean noMoreTargetsNow = cachedHarvestableBamboo <= 0;

        if (lotsOfBamboo) {
            return true;
        }

        if (inventoryTight) {
            return true;
        }

        if (enoughBamboo && noMoreTargetsNow) {
            return true;
        }

        return enoughBamboo;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        int bambooSlot = findInventorySlotPreferWhole(Items.BAMBOO);
        if (bambooSlot < 0) {
            return false;
        }

        return throwInventorySlot(bambooSlot, true);
    }

    private void refreshTargets(Context ctx) {
        long now = System.currentTimeMillis();

        if (targetBamboo != null) {
            targetBamboo = canonicalizeHarvestTarget(targetBamboo);
            if (!isValidBambooTarget(ctx, targetBamboo)) {
                targetBamboo = null;
            }
        }

        if (now - lastTargetScanAt < TARGET_SCAN_INTERVAL_MS) {
            return;
        }

        lastTargetScanAt = now;
        scanLocalTargets(ctx);

        if (targetBamboo == null && now - lastFullRescanAt >= FULL_RESCAN_INTERVAL_MS) {
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
        int fromY = Math.max(min.getY() - BAMBOO_SCAN_EXTRA_DOWN, p.getY() - LOCAL_SCAN_RADIUS_Y);
        int toY = Math.min(max.getY() + BAMBOO_SCAN_EXTRA_UP, p.getY() + LOCAL_SCAN_RADIUS_Y + 5);
        int fromZ = Math.max(min.getZ(), p.getZ() - LOCAL_SCAN_RADIUS_XZ);
        int toZ = Math.min(max.getZ(), p.getZ() + LOCAL_SCAN_RADIUS_XZ);

        Vec3d playerPos = mc.player.getPos();
        double best = targetBamboo != null ? playerPos.squaredDistanceTo(getInteractPoint(targetBamboo)) : Double.MAX_VALUE;

        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!isHarvestableBamboo(pos, state)) {
                        continue;
                    }

                    BlockPos candidate = canonicalizeHarvestTarget(pos);
                    if (!isValidBambooTarget(ctx, candidate)) {
                        continue;
                    }

                    double d = playerPos.squaredDistanceTo(getInteractPoint(candidate));
                    if (d < best) {
                        best = d;
                        targetBamboo = candidate.toImmutable();
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

        int fromY = min.getY() - BAMBOO_SCAN_EXTRA_DOWN;
        int toY = max.getY() + BAMBOO_SCAN_EXTRA_UP;

        Vec3d playerPos = mc.player.getPos();
        double best = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (int y = fromY; y <= toY; y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!isHarvestableBamboo(pos, state)) {
                        continue;
                    }

                    BlockPos candidate = canonicalizeHarvestTarget(pos);
                    if (!isValidBambooTarget(ctx, candidate)) {
                        continue;
                    }

                    double d = playerPos.squaredDistanceTo(getInteractPoint(candidate));
                    if (d < best) {
                        best = d;
                        bestPos = candidate.toImmutable();
                    }
                }
            }
        }

        targetBamboo = bestPos;
    }

    private boolean tryHandleHarvestTarget(Context ctx, BlockPos bambooPos) {
        bambooPos = canonicalizeHarvestTarget(bambooPos);

        if (!isValidBambooTarget(ctx, bambooPos)) {
            targetBamboo = null;
            return false;
        }

        if (canReachBlock(bambooPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();

            if (tryHarvestBamboo(bambooPos)) {
                targetBamboo = null;
                pickupFocusBambooPos = bambooPos.toImmutable();
                pickupFocusUntilAt = System.currentTimeMillis() + PICKUP_FOCUS_MS;
                return true;
            }
            return false;
        }

        BlockPos approach = findApproachPosForBamboo(ctx, bambooPos);
        if (approach == null) {
            BlockPos fallback = findFallbackPathGoalForBamboo(ctx, bambooPos);
            pathToGoalTracked(ctx, fallback != null ? fallback : bambooPos);
            return true;
        }

        pathToGoalTracked(ctx, approach);
        return true;
    }

    private boolean tryHandlePickupPhase(Context ctx) {
        if (pickupFocusBambooPos == null || mc.player == null || mc.world == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > pickupFocusUntilAt) {
            pickupFocusBambooPos = null;
            return false;
        }

        ItemEntity drop = findNearestRelevantDropAround(pickupFocusBambooPos, ITEM_SEARCH_RADIUS);
        if (drop == null) {
            drop = findNearestRelevantDropAround(mc.player.getBlockPos(), 8.5D);
        }
        if (drop == null) {
            return false;
        }

        Vec3d itemPos = drop.getPos();
        if (canPickupFromCurrentPosition(itemPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return true;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, itemPos);
        if (pickupGoal != null) {
            if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pickupGoal)) <= ITEM_PICKUP_GOAL_REACH_SQ) {
                ctx.stopWorkPathing();
                resetTrackedGoal();
                return true;
            }
            pathToGoalTracked(ctx, pickupGoal);
            return true;
        }

        BlockPos fallback = findFallbackPickupGoalNearItem(ctx, itemPos);
        if (fallback != null) {
            pathToGoalTracked(ctx, fallback);
            return true;
        }

        return false;
    }

    private boolean tryPickupNearbyDrops(Context ctx) {
        if (mc.player == null) {
            return false;
        }

        ItemEntity drop = findNearestRelevantDropAround(mc.player.getBlockPos(), 7.5D);
        if (drop == null) {
            return false;
        }

        Vec3d itemPos = drop.getPos();
        if (canPickupFromCurrentPosition(itemPos)) {
            return false;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, itemPos);
        if (pickupGoal == null) {
            pickupGoal = findFallbackPickupGoalNearItem(ctx, itemPos);
        }
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

            BlockPos sweepGoal = findSweepStandable(ctx, x, z, min.getY(), max.getY() + BAMBOO_SCAN_EXTRA_UP);
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

        cachedHarvestableBamboo = 0;

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null || mc.world == null) {
            return;
        }

        int fromY = min.getY() - BAMBOO_SCAN_EXTRA_DOWN;
        int toY = max.getY() + BAMBOO_SCAN_EXTRA_UP;

        for (int y = fromY; y <= toY; y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    if (!isHarvestableBamboo(pos, state)) {
                        continue;
                    }

                    BlockPos candidate = canonicalizeHarvestTarget(pos);
                    if (isValidBambooTarget(ctx, candidate)) {
                        cachedHarvestableBamboo++;
                    }
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

            if (!stack.isOf(Items.BAMBOO)) {
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

        BlockPos anchor = BlockPos.ofFloored(itemPos.x, itemPos.y - 0.15D, itemPos.z);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 0; r <= 6; r++) {
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
                        if (!canPickupItemFromStandPos(p, itemPos)) {
                            continue;
                        }

                        double score = Vec3d.ofCenter(p).squaredDistanceTo(itemPos)
                                + mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) * 0.14D;

                        BlockState feet = mc.world.getBlockState(p);
                        BlockState head = mc.world.getBlockState(p.up());

                        if (feet.isAir()) {
                            score -= 0.40D;
                        } else if (feet.isOf(Blocks.BAMBOO)) {
                            score += 0.35D;
                        }

                        if (head.isAir()) {
                            score -= 0.35D;
                        } else if (head.isOf(Blocks.BAMBOO)) {
                            score += 0.25D;
                        }

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

    private BlockPos findFallbackPickupGoalNearItem(Context ctx, Vec3d itemPos) {
        if (ctx == null || itemPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos anchor = BlockPos.ofFloored(itemPos.x, itemPos.y - 0.15D, itemPos.z);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 7; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
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

    private boolean canPickupItemFromStandPos(BlockPos standPos, Vec3d itemPos) {
        if (standPos == null || itemPos == null) {
            return false;
        }

        Vec3d feet = Vec3d.ofCenter(standPos);
        Vec3d eye = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.62D, standPos.getZ() + 0.5D);

        double dFeetSq = feet.squaredDistanceTo(itemPos);
        double dEyeSq = eye.squaredDistanceTo(itemPos);

        return dFeetSq <= (2.15D * 2.15D) || dEyeSq <= (2.45D * 2.45D);
    }

    private boolean canPickupFromCurrentPosition(Vec3d itemPos) {
        if (mc == null || mc.player == null || itemPos == null) {
            return false;
        }
        return mc.player.getPos().squaredDistanceTo(itemPos) <= ITEM_PICKUP_CLOSE_SQ
                || mc.player.getEyePos().squaredDistanceTo(itemPos) <= (2.25D * 2.25D);
    }

    private boolean isValidBambooTarget(Context ctx, BlockPos pos) {
        if (ctx == null || pos == null || mc.world == null) {
            return false;
        }

        BlockPos safe = canonicalizeHarvestTarget(pos);
        if (safe == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(safe);
        if (!isHarvestableBamboo(safe, state)) {
            return false;
        }

        BlockPos base = getBambooColumnBase(safe);
        if (base == null) {
            return false;
        }

        return ctx.isInSelectedArea(base);
    }

    private boolean tryHarvestBamboo(BlockPos bambooPos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) {
            return false;
        }

        BlockPos target = canonicalizeHarvestTarget(bambooPos);
        if (target == null) {
            return false;
        }

        BlockPos base = getBambooColumnBase(target);
        if (base == null) {
            return false;
        }

        BlockPos mustBreak = base.up();
        if (!target.equals(mustBreak)) {
            return false;
        }

        if (!mc.world.getBlockState(base).isOf(Blocks.BAMBOO)) {
            return false;
        }

        if (!mc.world.getBlockState(mustBreak).isOf(Blocks.BAMBOO)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return false;
        }

        ensureSwordMainHandIfPossible();

        if (hasAnySwordInInventory() && !isSwordStack(mc.player.getMainHandStack())) {
            return false;
        }

        lookAtHard(getInteractPoint(mustBreak));

        if (!canReachBlock(mustBreak)) {
            return false;
        }

        attackBambooReliable(mustBreak);

        if (mc.world.getBlockState(base).isOf(Blocks.BAMBOO) && !mc.world.getBlockState(mustBreak).isOf(Blocks.BAMBOO)) {
            mc.player.swingHand(Hand.MAIN_HAND);
            nextActionAt = now + ACTION_INTERVAL_MS;
            return true;
        }

        return false;
    }

    private void attackBambooReliable(BlockPos pos) {
        if (mc == null || mc.interactionManager == null || pos == null) {
            return;
        }

        Direction d = pickBestAttackDirection(pos);

        tryAttack(pos, d);
        tryAttack(pos, Direction.UP);
        tryAttack(pos, Direction.NORTH);
        tryAttack(pos, Direction.SOUTH);
        tryAttack(pos, Direction.WEST);
        tryAttack(pos, Direction.EAST);
    }

    private void tryAttack(BlockPos pos, Direction dir) {
        try {
            mc.interactionManager.attackBlock(pos, dir == null ? Direction.UP : dir);
        } catch (Throwable ignored) {
        }
    }

    private Direction pickBestAttackDirection(BlockPos pos) {
        if (mc == null || mc.player == null || pos == null) {
            return Direction.UP;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(pos);

        double dx = eye.x - c.x;
        double dy = eye.y - c.y;
        double dz = eye.z - c.z;

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ax >= ay && ax >= az) {
            return dx > 0.0D ? Direction.EAST : Direction.WEST;
        }
        if (az >= ax && az >= ay) {
            return dz > 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
        return dy > 0.0D ? Direction.UP : Direction.DOWN;
    }

    private void ensureSwordMainHandIfPossible() {
        if (mc == null || mc.player == null) {
            return;
        }

        int selected = mc.player.getInventory().selectedSlot;
        if (selected < 0 || selected > 8) {
            selected = 0;
        }

        ItemStack current = mc.player.getInventory().getStack(selected);
        if (isSwordStack(current)) {
            return;
        }

        int swordHotbar = -1;
        for (int i = 0; i < 9; i++) {
            if (isSwordStack(mc.player.getInventory().getStack(i))) {
                swordHotbar = i;
                break;
            }
        }

        if (swordHotbar >= 0) {
            mc.player.getInventory().selectedSlot = swordHotbar;
            syncSelectedSlotIfPossible();
            return;
        }

        int swordInv = -1;
        for (int i = 9; i < 36; i++) {
            if (isSwordStack(mc.player.getInventory().getStack(i))) {
                swordInv = i;
                break;
            }
        }

        if (swordInv >= 0 && mc.interactionManager != null) {
            try {
                int syncId = mc.player.currentScreenHandler.syncId;
                int swordSlotId = toScreenHandlerSlotId(swordInv);
                mc.interactionManager.clickSlot(syncId, swordSlotId, selected, SlotActionType.SWAP, mc.player);
                syncSelectedSlotIfPossible();
            } catch (Throwable ignored) {
            }
            return;
        }

        if (isAxeStack(current)) {
            int safeHotbar = findNonAxeHotbarSlot();
            if (safeHotbar >= 0 && safeHotbar != selected) {
                mc.player.getInventory().selectedSlot = safeHotbar;
                syncSelectedSlotIfPossible();
            }
        }
    }

    private void syncSelectedSlotIfPossible() {
        if (mc == null || mc.interactionManager == null) {
            return;
        }

        try {
            for (Method m : mc.interactionManager.getClass().getMethods()) {
                String n = m.getName().toLowerCase();
                if (m.getParameterCount() == 0 && n.contains("sync") && n.contains("slot")) {
                    m.invoke(mc.interactionManager);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean hasAnySwordInInventory() {
        if (mc == null || mc.player == null) {
            return false;
        }

        for (int i = 0; i < 36; i++) {
            if (isSwordStack(mc.player.getInventory().getStack(i))) {
                return true;
            }
        }
        return false;
    }

    private int findNonAxeHotbarSlot() {
        if (mc == null || mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!isAxeStack(s)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isSwordStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        try {
            Object item = stack.getItem();
            if (item == null) {
                return false;
            }
            Class<?> swordCls = Class.forName("net.minecraft.item.SwordItem");
            if (swordCls.isInstance(item)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Item item = stack.getItem();
            if (item == null) {
                return false;
            }
            String n = item.getClass().getSimpleName();
            if (n != null && n.toLowerCase().contains("sword")) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Item item = stack.getItem();
            if (item != null) {
                String key = item.getTranslationKey();
                if (key != null && key.toLowerCase().contains("sword")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean isAxeStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        try {
            Object item = stack.getItem();
            if (item == null) {
                return false;
            }
            Class<?> axeCls = Class.forName("net.minecraft.item.AxeItem");
            if (axeCls.isInstance(item)) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Item item = stack.getItem();
            if (item == null) {
                return false;
            }
            String n = item.getClass().getSimpleName();
            if (n != null && n.toLowerCase().contains("axe")) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Item item = stack.getItem();
            if (item != null) {
                String key = item.getTranslationKey();
                if (key != null && key.toLowerCase().contains("axe")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
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

    private BlockPos findApproachPosForBamboo(Context ctx, BlockPos bambooPos) {
        BlockPos base = getBambooColumnBase(bambooPos);
        BlockPos feetLevelCenter = base != null ? base : bambooPos;
        return findApproachPosForInteract(ctx, feetLevelCenter, bambooPos);
    }

    private BlockPos findFallbackPathGoalForBamboo(Context ctx, BlockPos bambooPos) {
        if (ctx == null || bambooPos == null) {
            return null;
        }

        BlockPos base = getBambooColumnBase(bambooPos);
        if (base == null) {
            return null;
        }

        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }
                        return p.toImmutable();
                    }
                }
            }
        }

        return null;
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

                        BlockState below = mc.world.getBlockState(p.down());
                        if (below.isOf(Blocks.FARMLAND)) {
                            score += 20.0D;
                        }

                        if (mc.world.getBlockState(p).isOf(Blocks.BAMBOO)) {
                            score += 0.55D;
                        }
                        if (mc.world.getBlockState(p.up()).isOf(Blocks.BAMBOO)) {
                            score += 0.35D;
                        }

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

        if (below.isOf(Blocks.FARMLAND)) {
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

        if (isAlwaysPassablePlant(state)) {
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

    private boolean isAlwaysPassablePlant(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.BAMBOO) || state.isOf(Blocks.SUGAR_CANE);
    }

    private boolean isWalkThroughCrop(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.WHEAT)
                || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES)
                || state.isOf(Blocks.BEETROOTS)
                || state.isOf(Blocks.NETHER_WART)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.BAMBOO);
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

        if (state.isOf(Blocks.BAMBOO)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.78D, pos.getZ() + 0.5D);
        }

        if (isWalkThroughCrop(state)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.45D, pos.getZ() + 0.5D);
        }

        return Vec3d.ofCenter(pos);
    }

    private void lookAtHard(Vec3d target) {
        if (mc.player == null || target == null) {
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horiz))));
        pitch = MathHelper.clamp(pitch, -89.0F, 89.0F);

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private BlockPos getBambooColumnBase(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return null;
        }

        BlockPos cur = pos;
        BlockState s = mc.world.getBlockState(cur);
        if (!s.isOf(Blocks.BAMBOO)) {
            return null;
        }

        for (int i = 0; i < 32; i++) {
            BlockPos down = cur.down();
            if (!mc.world.getBlockState(down).isOf(Blocks.BAMBOO)) {
                return cur.toImmutable();
            }
            cur = down;
        }

        return cur.toImmutable();
    }

    private int getBambooColumnHeight(BlockPos base) {
        if (base == null || mc.world == null || !mc.world.getBlockState(base).isOf(Blocks.BAMBOO)) {
            return 0;
        }

        int h = 0;
        for (int i = 0; i < 32; i++) {
            if (!mc.world.getBlockState(base.up(i)).isOf(Blocks.BAMBOO)) {
                break;
            }
            h++;
        }
        return h;
    }

    private BlockPos canonicalizeHarvestTarget(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return null;
        }

        if (!mc.world.getBlockState(pos).isOf(Blocks.BAMBOO)) {
            return null;
        }

        BlockPos base = getBambooColumnBase(pos);
        if (base == null) {
            return null;
        }

        int height = getBambooColumnHeight(base);
        if (height < 2) {
            return null;
        }

        BlockPos breakPos = base.up();

        if (!mc.world.getBlockState(base).isOf(Blocks.BAMBOO)) {
            return null;
        }
        if (!mc.world.getBlockState(breakPos).isOf(Blocks.BAMBOO)) {
            return null;
        }

        return breakPos.toImmutable();
    }

    private boolean isHarvestableBamboo(BlockPos pos, BlockState state) {
        if (pos == null || state == null || mc.world == null) {
            return false;
        }

        if (!state.isOf(Blocks.BAMBOO)) {
            return false;
        }

        BlockPos base = getBambooColumnBase(pos);
        if (base == null) {
            return false;
        }

        int height = getBambooColumnHeight(base);
        if (height < 2) {
            return false;
        }

        BlockPos safe = canonicalizeHarvestTarget(pos);
        return safe != null && safe.equals(base.up());
    }
}