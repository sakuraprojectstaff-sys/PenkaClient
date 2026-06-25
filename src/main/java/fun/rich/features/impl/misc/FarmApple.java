package fun.rich.features.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmApple {

    static final long AREA_SCAN_INTERVAL_MS = 320L;
    static final long TARGET_SCAN_INTERVAL_MS = 70L;
    static final long FULL_RESCAN_INTERVAL_MS = 220L;
    static final long ACTION_INTERVAL_MS = 80L;
    static final long INVENTORY_SWAP_INTERVAL_MS = 90L;
    static final long SWEEP_REPATH_MS = 520L;
    static final long STUCK_TIMEOUT_MS = 700L;

    static final double BLOCK_REACH_SQ = 25.0D;
    static final double PICKUP_REACHED_SQ = 0.95D * 0.95D;
    static final double PICKUP_SEARCH_RADIUS = 8.0D;
    static final double PATROL_REACHED_SQ = 2.0D * 2.0D;
    static final double LOG_BREAK_CLOSE_HORIZ_SQ = 1.45D * 1.45D;
    static final double LOG_BREAK_CLOSE_TOTAL_SQ = 3.2D * 3.2D;

    static final int SWEEP_STEP = 2;
    static final int LOCAL_SCAN_RADIUS_XZ = 14;
    static final int LOCAL_SCAN_RADIUS_Y = 6;

    static final int GO_STORAGE_APPLES = 48;
    static final int GO_STORAGE_APPLES_HARD = 96;
    static final int MIN_FREE_SLOTS = 2;

    final MinecraftClient mc = MinecraftClient.getInstance();

    long lastAreaScanAt;
    long lastTargetScanAt;
    long lastFullRescanAt;
    long nextActionAt;
    long nextInventorySwapAt;
    long lastSweepRepathAt;

    int cachedLogs;
    int cachedLeaves;
    int cachedSaplings;
    int cachedPlantable;

    BlockPos targetBreakBlock;
    BlockPos targetBonemealSapling;
    BlockPos targetPlantSoil;

    BlockPos breakingPos;
    long lastBreakSwingAt;

    BlockPos trackedPathGoal;
    Vec3d trackedPathStartPos;
    long trackedPathSinceAt;
    double trackedPathLastGoalDistSq;
    long trackedNoProgressSinceAt;

    BlockPos[] patrolPoints;
    int patrolIndex;
    BlockPos lastAreaMin;
    BlockPos lastAreaMax;

    Mode mode = Mode.IDLE;

    enum Mode {
        IDLE,
        PICKUP,
        BREAK_LOG,
        BREAK_LEAF,
        BONE_MEAL,
        PLANT,
        SWEEP
    }

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
        lastSweepRepathAt = 0L;

        cachedLogs = 0;
        cachedLeaves = 0;
        cachedSaplings = 0;
        cachedPlantable = 0;

        targetBreakBlock = null;
        targetBonemealSapling = null;
        targetPlantSoil = null;

        breakingPos = null;
        lastBreakSwingAt = 0L;

        trackedPathGoal = null;
        trackedPathStartPos = null;
        trackedPathSinceAt = 0L;
        trackedPathLastGoalDistSq = Double.MAX_VALUE;
        trackedNoProgressSinceAt = 0L;

        patrolPoints = null;
        patrolIndex = 0;
        lastAreaMin = null;
        lastAreaMax = null;

        mode = Mode.IDLE;

        cancelBlockBreaking();
    }

    public void tick(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            resetBreakingState(false);
            return;
        }

        if (mc.currentScreen != null) {
            resetBreakingState(false);
            return;
        }

        if (ctx.getMinPoint() == null || ctx.getMaxPoint() == null) {
            resetBreakingState(false);
            return;
        }

        rebuildPatrolPoints(ctx);
        updateAreaCacheIfNeeded(ctx);

        if (tryPickupRelevantDrops(ctx)) {
            resetBreakingState(true);
            return;
        }

        refreshTargets(ctx);

        if (hasTreeWorkPending()) {
            targetBonemealSapling = null;
            targetPlantSoil = null;

            if (targetBreakBlock != null && tryHandleBreakTarget(ctx, targetBreakBlock)) {
                return;
            }

            if (trySweepArea(ctx)) {
                return;
            }

            ctx.stopWorkPathing();
            resetTrackedGoal();
            mode = Mode.IDLE;
            return;
        }

        if (shouldWaitNaturalGrow()) {
            targetBonemealSapling = null;
            targetPlantSoil = null;
            resetBreakingState(true);

            if (trySweepArea(ctx)) {
                return;
            }

            ctx.stopWorkPathing();
            resetTrackedGoal();
            mode = Mode.IDLE;
            return;
        }

        if (targetBonemealSapling != null && tryHandleBonemealTarget(ctx, targetBonemealSapling)) {
            resetBreakingState(true);
            return;
        }

        if (targetPlantSoil != null && tryHandlePlantTarget(ctx, targetPlantSoil)) {
            resetBreakingState(true);
            return;
        }

        resetBreakingState(true);

        if (trySweepArea(ctx)) {
            return;
        }

        ctx.stopWorkPathing();
        resetTrackedGoal();
        mode = Mode.IDLE;
    }

    public boolean shouldGoToStorageNow() {
        int apples = countItemInInventory(Items.APPLE);
        if (apples <= 0) {
            return false;
        }
        if (apples >= GO_STORAGE_APPLES_HARD) {
            return true;
        }
        if (getFreeInventorySlots(36) <= MIN_FREE_SLOTS) {
            return true;
        }
        return apples >= GO_STORAGE_APPLES;
    }

    public boolean hasAnyCultureToDeposit() {
        return countItemInInventory(Items.APPLE) > 0;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }

        int slot = findInventorySlotPreferWhole(Items.APPLE);
        if (slot < 0) {
            return false;
        }

        return throwInventorySlot(slot, true);
    }

    private boolean hasTreeWorkPending() {
        if (targetBreakBlock != null) {
            return true;
        }
        return cachedLogs > 0 || cachedLeaves > 0;
    }

    private boolean shouldWaitNaturalGrow() {
        if (hasTreeWorkPending()) {
            return false;
        }
        if (hasItemInInventory(Items.BONE_MEAL)) {
            return false;
        }
        if (cachedSaplings <= 0) {
            return false;
        }
        if (hasItemInInventory(Items.OAK_SAPLING) && cachedPlantable > 0) {
            return false;
        }
        return true;
    }

    private void refreshTargets(Context ctx) {
        long now = System.currentTimeMillis();

        validateTargets(ctx);

        if (now - lastTargetScanAt < TARGET_SCAN_INTERVAL_MS) {
            if (hasTreeWorkPending()) {
                targetBonemealSapling = null;
                targetPlantSoil = null;
            }
            return;
        }

        lastTargetScanAt = now;

        scanLocalTargets(ctx);

        if ((targetBreakBlock == null || (!hasTreeWorkPending() && (targetBonemealSapling == null || targetPlantSoil == null)))
                && now - lastFullRescanAt >= FULL_RESCAN_INTERVAL_MS) {
            lastFullRescanAt = now;
            scanFullTargets(ctx);
        }

        if (hasTreeWorkPending()) {
            targetBonemealSapling = null;
            targetPlantSoil = null;
        }
    }

    private void validateTargets(Context ctx) {
        if (targetBreakBlock != null && !isValidBreakTarget(ctx, targetBreakBlock)) {
            if (targetBreakBlock.equals(breakingPos)) {
                resetBreakingState(true);
            }
            targetBreakBlock = null;
        }

        if (targetBonemealSapling != null && !hasItemInInventory(Items.BONE_MEAL)) {
            targetBonemealSapling = null;
        }

        if (targetBonemealSapling != null && !isValidBonemealTarget(ctx, targetBonemealSapling)) {
            targetBonemealSapling = null;
        }

        if (targetPlantSoil != null && shouldWaitNaturalGrow()) {
            targetPlantSoil = null;
        }

        if (targetPlantSoil != null && !isValidPlantSoilTarget(ctx, targetPlantSoil)) {
            targetPlantSoil = null;
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

        BlockPos playerBlock = mc.player.getBlockPos();

        int fromX = Math.max(min.getX() - 2, playerBlock.getX() - LOCAL_SCAN_RADIUS_XZ);
        int toX = Math.min(max.getX() + 2, playerBlock.getX() + LOCAL_SCAN_RADIUS_XZ);
        int fromY = Math.max(min.getY() - 2, playerBlock.getY() - LOCAL_SCAN_RADIUS_Y);
        int toY = Math.min(max.getY() + 12, playerBlock.getY() + LOCAL_SCAN_RADIUS_Y + 4);
        int fromZ = Math.max(min.getZ() - 2, playerBlock.getZ() - LOCAL_SCAN_RADIUS_XZ);
        int toZ = Math.min(max.getZ() + 2, playerBlock.getZ() + LOCAL_SCAN_RADIUS_XZ);

        Vec3d playerPos = mc.player.getPos();

        double bestBreak = targetBreakBlock != null ? scoreBreakTarget(targetBreakBlock, playerPos) : Double.MAX_VALUE;
        double bestBone = targetBonemealSapling != null ? playerPos.squaredDistanceTo(getInteractPoint(targetBonemealSapling)) : Double.MAX_VALUE;
        double bestPlant = targetPlantSoil != null ? playerPos.squaredDistanceTo(Vec3d.ofCenter(targetPlantSoil.up())) : Double.MAX_VALUE;

        boolean hasSapling = hasItemInInventory(Items.OAK_SAPLING);
        boolean hasBoneMeal = hasItemInInventory(Items.BONE_MEAL);
        boolean waitNatural = !hasBoneMeal && cachedSaplings > 0 && !(hasSapling && cachedPlantable > 0);

        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (!ctx.isInsideAreaMargin(pos, 3)) {
                        continue;
                    }

                    BlockState state = mc.world.getBlockState(pos);

                    if ((isLogBlock(state) || isAppleLeafBlock(state)) && isValidBreakTarget(ctx, pos)) {
                        double s = scoreBreakTarget(pos, playerPos);
                        if (s < bestBreak) {
                            bestBreak = s;
                            targetBreakBlock = pos.toImmutable();
                        }
                    }

                    if (hasTreeWorkPending()) {
                        continue;
                    }

                    if (hasBoneMeal && isValidBonemealTarget(ctx, pos)) {
                        double d = playerPos.squaredDistanceTo(getInteractPoint(pos));
                        if (d < bestBone) {
                            bestBone = d;
                            targetBonemealSapling = pos.toImmutable();
                        }
                    }

                    if (!waitNatural && hasSapling && isValidPlantSoilTarget(ctx, pos)) {
                        double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(pos.up()));
                        if (d < bestPlant) {
                            bestPlant = d;
                            targetPlantSoil = pos.toImmutable();
                        }
                    }
                }
            }
        }

        if (waitNatural) {
            targetBonemealSapling = null;
            targetPlantSoil = null;
        }
    }

    private void scanFullTargets(Context ctx) {
        if (mc.world == null || mc.player == null) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        Vec3d playerPos = mc.player.getPos();

        BlockPos bestBreakPos = null;
        double bestBreak = Double.MAX_VALUE;

        BlockPos bestBonePos = null;
        double bestBone = Double.MAX_VALUE;

        BlockPos bestPlantPos = null;
        double bestPlant = Double.MAX_VALUE;

        boolean hasSapling = hasItemInInventory(Items.OAK_SAPLING);
        boolean hasBoneMeal = hasItemInInventory(Items.BONE_MEAL);
        boolean waitNatural = !hasBoneMeal && cachedSaplings > 0 && !(hasSapling && cachedPlantable > 0);

        for (int y = min.getY() - 2; y <= max.getY() + 12; y++) {
            for (int x = min.getX() - 2; x <= max.getX() + 2; x++) {
                for (int z = min.getZ() - 2; z <= max.getZ() + 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);

                    if (!ctx.isInsideAreaMargin(pos, 3)) {
                        continue;
                    }

                    BlockState state = mc.world.getBlockState(pos);

                    if ((isLogBlock(state) || isAppleLeafBlock(state)) && isValidBreakTarget(ctx, pos)) {
                        double s = scoreBreakTarget(pos, playerPos);
                        if (s < bestBreak) {
                            bestBreak = s;
                            bestBreakPos = pos.toImmutable();
                        }
                    }

                    if (hasBoneMeal && isValidBonemealTarget(ctx, pos)) {
                        double d = playerPos.squaredDistanceTo(getInteractPoint(pos));
                        if (d < bestBone) {
                            bestBone = d;
                            bestBonePos = pos.toImmutable();
                        }
                    }

                    if (!waitNatural && hasSapling && isValidPlantSoilTarget(ctx, pos)) {
                        double d = playerPos.squaredDistanceTo(Vec3d.ofCenter(pos.up()));
                        if (d < bestPlant) {
                            bestPlant = d;
                            bestPlantPos = pos.toImmutable();
                        }
                    }
                }
            }
        }

        targetBreakBlock = bestBreakPos;

        if (bestBreakPos != null || cachedLogs > 0 || cachedLeaves > 0 || waitNatural) {
            targetBonemealSapling = null;
            targetPlantSoil = null;
        } else {
            targetBonemealSapling = bestBonePos;
            targetPlantSoil = bestPlantPos;
        }
    }

    private double scoreBreakTarget(BlockPos pos, Vec3d playerPos) {
        if (pos == null || playerPos == null || mc == null || mc.world == null) {
            return Double.MAX_VALUE;
        }

        BlockState state = mc.world.getBlockState(pos);
        boolean log = isLogBlock(state);
        boolean leaf = isAppleLeafBlock(state);

        boolean exposed = isExposedForBreaking(pos);
        boolean los = hasLineOfSightToBlock(pos);

        double score = playerPos.squaredDistanceTo(getInteractPoint(pos));

        if (log) {
            score -= 44.0D;
            if (isTightLogStandNear(pos)) {
                score -= 24.0D;
            } else {
                score += 18.0D;
            }

            if (!los) {
                score += 34.0D;
            }

            if (!exposed) {
                score += 26.0D;
            }
        } else if (leaf) {
            score += 4.0D;
            if (los) {
                score -= 8.0D;
            }
            if (exposed) {
                score -= 6.0D;
            }
        }

        score += Math.abs(pos.getY() - mc.player.getBlockY()) * 1.2D;
        return score;
    }

    private boolean tryHandleBreakTarget(Context ctx, BlockPos target) {
        if (ctx == null || target == null || mc.world == null || mc.player == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(target);
        boolean log = isLogBlock(state);
        boolean leaf = isAppleLeafBlock(state);

        if (!log && !leaf) {
            targetBreakBlock = null;
            return false;
        }

        if (leaf && hasProtectedDropOnLeaf(target)) {
            targetBreakBlock = null;
            return false;
        }

        boolean canReach = canReachBlock(target);
        boolean los = hasLineOfSightToBlock(target);
        boolean tightEnoughForLog = isPlayerTightNearBlock(target);

        if ((leaf && canReach && los) || (log && canReach && los && tightEnoughForLog)) {
            mode = log ? Mode.BREAK_LOG : Mode.BREAK_LEAF;
            ctx.stopWorkPathing();
            resetTrackedGoal();

            if (log) {
                selectBestAxeIfAvailable();
            }

            boolean ok = tryBreakBlock(target);

            if (!isValidBreakTarget(ctx, target)) {
                if (target.equals(breakingPos)) {
                    resetBreakingState(false);
                }
                targetBreakBlock = null;
            }

            return ok;
        }

        if (breakingPos != null) {
            resetBreakingState(true);
        }

        BlockPos approach = log ? findTightApproachPosForLog(ctx, target) : null;
        if (approach == null) {
            approach = findApproachPosForBreak(ctx, target);
        }

        if (approach == null && log) {
            BlockPos leafToClear = findLeafToClearForLog(ctx, target);
            if (leafToClear != null) {
                targetBreakBlock = leafToClear.toImmutable();
                return true;
            }
        }

        if (approach == null) {
            approach = findFallbackGoalNearBlock(ctx, target);
        }

        if (approach != null) {
            mode = log ? Mode.BREAK_LOG : Mode.BREAK_LEAF;
            pathToGoalTracked(ctx, approach);
            return true;
        }

        if (leaf) {
            targetBreakBlock = null;
        }

        return false;
    }

    private boolean tryHandleBonemealTarget(Context ctx, BlockPos saplingPos) {
        if (ctx == null || saplingPos == null || mc.player == null || mc.world == null) {
            return false;
        }

        if (hasTreeWorkPending()) {
            targetBonemealSapling = null;
            return false;
        }

        if (!isValidBonemealTarget(ctx, saplingPos)) {
            targetBonemealSapling = null;
            return false;
        }

        if (!selectItemToHand(Items.BONE_MEAL)) {
            targetBonemealSapling = null;
            return false;
        }

        Vec3d aim = getInteractPoint(saplingPos);

        if (canReachBlock(saplingPos) && hasLineOfSightToBlock(saplingPos)) {
            mode = Mode.BONE_MEAL;
            ctx.stopWorkPathing();
            resetTrackedGoal();

            long now = System.currentTimeMillis();
            lookAtSoft(aim);

            if (now < nextActionAt) {
                return true;
            }

            try {
                BlockHitResult hit = new BlockHitResult(aim, Direction.UP, saplingPos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            } catch (Throwable ignored) {
            }

            nextActionAt = now + ACTION_INTERVAL_MS;

            if (!isValidBonemealTarget(ctx, saplingPos)) {
                targetBonemealSapling = null;
            }

            return true;
        }

        BlockPos approach = findApproachPosForInteract(ctx, saplingPos, saplingPos);
        if (approach == null) {
            approach = findFallbackGoalNearBlock(ctx, saplingPos);
        }

        if (approach != null) {
            mode = Mode.BONE_MEAL;
            pathToGoalTracked(ctx, approach);
            return true;
        }

        return false;
    }

    private boolean tryHandlePlantTarget(Context ctx, BlockPos soilPos) {
        if (ctx == null || soilPos == null || mc.player == null || mc.world == null) {
            return false;
        }

        if (hasTreeWorkPending()) {
            targetPlantSoil = null;
            return false;
        }

        if (shouldWaitNaturalGrow()) {
            targetPlantSoil = null;
            return false;
        }

        if (!isValidPlantSoilTarget(ctx, soilPos)) {
            targetPlantSoil = null;
            return false;
        }

        if (!selectItemToHand(Items.OAK_SAPLING)) {
            targetPlantSoil = null;
            return false;
        }

        Vec3d aim = new Vec3d(soilPos.getX() + 0.5D, soilPos.getY() + 1.0D, soilPos.getZ() + 0.5D);

        if (canReachBlockFromPoint(soilPos.up(), aim) && hasLineOfSightToPoint(aim, soilPos)) {
            mode = Mode.PLANT;
            ctx.stopWorkPathing();
            resetTrackedGoal();

            long now = System.currentTimeMillis();
            lookAtSoft(aim);

            if (now < nextActionAt) {
                return true;
            }

            try {
                BlockHitResult hit = new BlockHitResult(aim, Direction.UP, soilPos, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            } catch (Throwable ignored) {
            }

            nextActionAt = now + ACTION_INTERVAL_MS;

            if (!isValidPlantSoilTarget(ctx, soilPos)) {
                targetPlantSoil = null;
            }

            return true;
        }

        BlockPos approach = findApproachPosForInteract(ctx, soilPos, soilPos.up());
        if (approach == null) {
            approach = findFallbackGoalNearBlock(ctx, soilPos);
        }

        if (approach != null) {
            mode = Mode.PLANT;
            pathToGoalTracked(ctx, approach);
            return true;
        }

        return false;
    }

    private boolean tryPickupRelevantDrops(Context ctx) {
        if (ctx == null || mc.player == null || mc.world == null) {
            return false;
        }

        ItemEntity item = findNearestRelevantDropAround(ctx, mc.player.getBlockPos(), PICKUP_SEARCH_RADIUS);
        if (item == null) {
            return false;
        }

        Vec3d pos = item.getPos();
        double distSq = mc.player.getPos().squaredDistanceTo(pos);

        if (distSq <= PICKUP_REACHED_SQ) {
            mode = Mode.PICKUP;
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return true;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, pos);
        if (pickupGoal == null) {
            pickupGoal = findFallbackGoalNearBlock(ctx, item.getBlockPos());
        }
        if (pickupGoal == null) {
            pickupGoal = item.getBlockPos().toImmutable();
        }

        mode = Mode.PICKUP;
        pathToGoalTracked(ctx, pickupGoal);
        return true;
    }

    private ItemEntity findNearestRelevantDropAround(Context ctx, BlockPos center, double radius) {
        if (mc.world == null || mc.player == null || center == null) {
            return null;
        }

        Vec3d c = Vec3d.ofCenter(center);
        Box box = new Box(
                c.x - radius, c.y - 3.0D, c.z - radius,
                c.x + radius, c.y + 4.0D, c.z + radius
        );

        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (ItemEntity it : mc.world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
            if (it == null || !it.isAlive()) {
                continue;
            }

            ItemStack st = it.getStack();
            if (st == null || st.isEmpty()) {
                continue;
            }

            if (!isRelevantDropItem(st.getItem())) {
                continue;
            }

            if (!ctx.isInsideAreaMargin(it.getBlockPos(), 4)) {
                continue;
            }

            double d = mc.player.getPos().squaredDistanceTo(it.getPos());

            if (st.isOf(Items.APPLE)) d -= 3.0D;
            if (st.isOf(Items.OAK_SAPLING)) d -= 2.0D;

            if (d < bestDist) {
                bestDist = d;
                best = it;
            }
        }

        return best;
    }

    private boolean isRelevantDropItem(Item item) {
        if (item == null) {
            return false;
        }
        return item == Items.APPLE
                || item == Items.OAK_SAPLING
                || item == Items.OAK_LOG
                || item == Items.STICK;
    }

    private BlockPos findPickupGoalNearItem(Context ctx, Vec3d itemPos) {
        if (ctx == null || itemPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos anchor = BlockPos.ofFloored(itemPos);
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

                        if (!ctx.isInsideAreaMargin(p, 3)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }

                        Vec3d pc = Vec3d.ofCenter(p);
                        double dItem = pc.squaredDistanceTo(itemPos);
                        double dPlayer = mc.player.getPos().squaredDistanceTo(pc);
                        double score = dItem * 0.95D + dPlayer * 0.12D;

                        if (dItem <= 0.80D * 0.80D) {
                            score -= 20.0D;
                        } else if (dItem <= 1.05D * 1.05D) {
                            score -= 10.0D;
                        } else if (dItem > 2.25D * 2.25D) {
                            score += 8.0D;
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

    private BlockPos findLeafToClearForLog(Context ctx, BlockPos logPos) {
        if (ctx == null || logPos == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -1; dy <= 3; dy++) {
                        BlockPos p = new BlockPos(logPos.getX() + dx, logPos.getY() + dy, logPos.getZ() + dz);

                        if (p.equals(logPos)) {
                            continue;
                        }
                        if (!ctx.isInsideAreaMargin(p, 3)) {
                            continue;
                        }

                        BlockState st = mc.world.getBlockState(p);
                        if (!isAppleLeafBlock(st)) {
                            continue;
                        }

                        if (hasProtectedDropOnLeaf(p)) {
                            continue;
                        }

                        int manhattan = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (manhattan > 4) {
                            continue;
                        }

                        double score = mc.player.getPos().squaredDistanceTo(getInteractPoint(p));

                        if (hasLineOfSightToBlock(p)) {
                            score -= 14.0D;
                        }
                        if (canReachBlock(p)) {
                            score -= 10.0D;
                        }
                        if (isExposedForBreaking(p)) {
                            score -= 6.0D;
                        }

                        if (Math.abs(dx) + Math.abs(dz) <= 1 && dy >= 0) {
                            score -= 10.0D;
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

    private boolean hasProtectedDropOnLeaf(BlockPos leafPos) {
        if (leafPos == null || mc == null || mc.world == null) {
            return false;
        }

        Box checkBox = new Box(
                leafPos.getX() + 0.06D, leafPos.getY() + 0.08D, leafPos.getZ() + 0.06D,
                leafPos.getX() + 0.94D, leafPos.getY() + 1.85D, leafPos.getZ() + 0.94D
        );

        for (ItemEntity it : mc.world.getEntitiesByClass(ItemEntity.class, checkBox, e -> true)) {
            if (it == null || !it.isAlive()) {
                continue;
            }

            ItemStack st = it.getStack();
            if (st == null || st.isEmpty()) {
                continue;
            }

            if (!isRelevantDropItem(st.getItem())) {
                continue;
            }

            return true;
        }

        return false;
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
            mode = Mode.SWEEP;
            return true;
        }

        if (patrolPoints != null && patrolPoints.length > 0) {
            if (patrolIndex < 0 || patrolIndex >= patrolPoints.length) {
                patrolIndex = 0;
            }

            BlockPos p = patrolPoints[patrolIndex];
            if (p != null && mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) <= PATROL_REACHED_SQ) {
                patrolIndex = (patrolIndex + 1) % patrolPoints.length;
                p = patrolPoints[patrolIndex];
            }

            if (p != null) {
                lastSweepRepathAt = now;
                mode = Mode.SWEEP;
                pathToGoalTracked(ctx, p);
                return true;
            }
        }

        int sizeX = Math.max(1, ((max.getX() - min.getX()) / SWEEP_STEP) + 1);
        int sizeZ = Math.max(1, ((max.getZ() - min.getZ()) / SWEEP_STEP) + 1);
        int total = sizeX * sizeZ;

        if (total <= 0) {
            return false;
        }

        for (int tries = 0; tries < total; tries++) {
            int idx = (patrolIndex + tries) % total;
            int row = idx / sizeX;
            int col = idx % sizeX;

            if ((row & 1) == 1) {
                col = sizeX - 1 - col;
            }

            int x = Math.min(max.getX(), min.getX() + col * SWEEP_STEP);
            int z = Math.min(max.getZ(), min.getZ() + row * SWEEP_STEP);

            BlockPos sweepGoal = findSweepStandable(ctx, x, z, min.getY(), max.getY());
            if (sweepGoal == null) {
                continue;
            }

            patrolIndex = (idx + 1) % total;

            if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(sweepGoal)) <= 4.0D) {
                continue;
            }

            lastSweepRepathAt = now;
            mode = Mode.SWEEP;
            pathToGoalTracked(ctx, sweepGoal);
            return true;
        }

        return false;
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

    private void rebuildPatrolPoints(Context ctx) {
        if (ctx == null || mc == null || mc.player == null) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            patrolPoints = null;
            patrolIndex = 0;
            lastAreaMin = null;
            lastAreaMax = null;
            return;
        }

        boolean changed = lastAreaMin == null || lastAreaMax == null || !lastAreaMin.equals(min) || !lastAreaMax.equals(max);
        if (!changed && patrolPoints != null && patrolPoints.length > 0) {
            return;
        }

        lastAreaMin = min.toImmutable();
        lastAreaMax = max.toImmutable();

        int y = MathHelper.clamp(mc.player.getBlockY(), min.getY() - 1, max.getY() + 2);

        BlockPos p1 = new BlockPos(min.getX(), y, min.getZ());
        BlockPos p2 = new BlockPos(max.getX(), y, min.getZ());
        BlockPos p3 = new BlockPos(max.getX(), y, max.getZ());
        BlockPos p4 = new BlockPos(min.getX(), y, max.getZ());
        BlockPos center = new BlockPos((min.getX() + max.getX()) >> 1, y, (min.getZ() + max.getZ()) >> 1);

        patrolPoints = new BlockPos[]{p1, p2, p3, p4, center};
        patrolIndex = 0;
    }

    private void updateAreaCacheIfNeeded(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastAreaScanAt < AREA_SCAN_INTERVAL_MS) {
            return;
        }
        lastAreaScanAt = now;

        cachedLogs = 0;
        cachedLeaves = 0;
        cachedSaplings = 0;
        cachedPlantable = 0;

        if (mc.world == null) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        for (int y = min.getY() - 2; y <= max.getY() + 12; y++) {
            for (int x = min.getX() - 2; x <= max.getX() + 2; x++) {
                for (int z = min.getZ() - 2; z <= max.getZ() + 2; z++) {
                    BlockPos p = new BlockPos(x, y, z);

                    if (!ctx.isInsideAreaMargin(p, 3)) {
                        continue;
                    }

                    BlockState st = mc.world.getBlockState(p);

                    if (isLogBlock(st)) {
                        cachedLogs++;
                    } else if (isAppleLeafBlock(st)) {
                        cachedLeaves++;
                    } else if (isOakSaplingBlock(st)) {
                        cachedSaplings++;
                    }

                    if (isValidPlantSoilTarget(ctx, p)) {
                        cachedPlantable++;
                    }
                }
            }
        }
    }

    private boolean isValidBreakTarget(Context ctx, BlockPos pos) {
        if (ctx == null || pos == null || mc.world == null) {
            return false;
        }

        if (!ctx.isInsideAreaMargin(pos, 3)) {
            return false;
        }

        BlockState st = mc.world.getBlockState(pos);

        if (isLogBlock(st)) {
            return true;
        }

        if (isAppleLeafBlock(st)) {
            return !hasProtectedDropOnLeaf(pos);
        }

        return false;
    }

    private boolean isValidBonemealTarget(Context ctx, BlockPos saplingPos) {
        if (ctx == null || saplingPos == null || mc.world == null) {
            return false;
        }
        if (!ctx.isInsideAreaMargin(saplingPos, 2)) {
            return false;
        }
        return isOakSaplingBlock(mc.world.getBlockState(saplingPos));
    }

    private boolean isValidPlantSoilTarget(Context ctx, BlockPos soilPos) {
        if (ctx == null || soilPos == null || mc.world == null) {
            return false;
        }

        if (!ctx.isInSelectedArea(soilPos)) {
            return false;
        }

        BlockState soil = mc.world.getBlockState(soilPos);
        if (!isPlantableSoil(soil)) {
            return false;
        }

        BlockPos abovePos = soilPos.up();
        if (!ctx.isInsideAreaMargin(abovePos, 1)) {
            return false;
        }

        BlockState above = mc.world.getBlockState(abovePos);

        if (isOakSaplingBlock(above)) {
            return false;
        }

        if (!isReplaceableForSapling(above, abovePos)) {
            return false;
        }

        return true;
    }

    private boolean isPlantableSoil(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.DIRT)
                || state.isOf(Blocks.GRASS_BLOCK)
                || state.isOf(Blocks.COARSE_DIRT)
                || state.isOf(Blocks.PODZOL)
                || state.isOf(Blocks.ROOTED_DIRT)
                || state.isOf(Blocks.MYCELIUM);
    }

    private boolean isReplaceableForSapling(BlockState state, BlockPos pos) {
        if (state == null || pos == null || mc.world == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        if (isOakSaplingBlock(state)) {
            return false;
        }

        if (isLogBlock(state) || isAppleLeafBlock(state)) {
            return false;
        }

        try {
            if (state.getFluidState() != null && !state.getFluidState().isEmpty()) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isLogBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.OAK_LOG) || state.isOf(Blocks.OAK_WOOD);
    }

    private boolean isAppleLeafBlock(BlockState state) {
        return state != null && state.isOf(Blocks.OAK_LEAVES);
    }

    private boolean isOakSaplingBlock(BlockState state) {
        return state != null && state.isOf(Blocks.OAK_SAPLING);
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

    private boolean isPassableForPlayer(BlockState state, BlockPos pos, boolean allowLeavesFeet) {
        if (state == null || pos == null || mc.world == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        if (isAppleLeafBlock(state)) {
            return true;
        }

        if (isOakSaplingBlock(state)) {
            return true;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean canReachBlock(BlockPos pos) {
        if (mc.player == null || pos == null) {
            return false;
        }
        return mc.player.getEyePos().squaredDistanceTo(getInteractPoint(pos)) <= BLOCK_REACH_SQ;
    }

    private boolean canReachBlockFromPoint(BlockPos pos, Vec3d point) {
        if (mc.player == null || point == null) {
            return false;
        }
        return mc.player.getEyePos().squaredDistanceTo(point) <= BLOCK_REACH_SQ;
    }

    private boolean canReachBlockFromStandPos(BlockPos standPos, BlockPos targetPos) {
        if (standPos == null || targetPos == null) {
            return false;
        }
        Vec3d eye = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.62D, standPos.getZ() + 0.5D);
        return eye.squaredDistanceTo(getInteractPoint(targetPos)) <= BLOCK_REACH_SQ;
    }

    private boolean isPlayerTightNearBlock(BlockPos pos) {
        if (mc == null || mc.player == null || pos == null) {
            return false;
        }

        Vec3d player = mc.player.getPos();
        Vec3d c = Vec3d.ofCenter(pos);

        double dx = player.x - c.x;
        double dz = player.z - c.z;
        double horizSq = dx * dx + dz * dz;

        if (horizSq > LOG_BREAK_CLOSE_HORIZ_SQ) {
            return false;
        }

        return mc.player.getEyePos().squaredDistanceTo(getInteractPoint(pos)) <= LOG_BREAK_CLOSE_TOTAL_SQ;
    }

    private boolean isTightLogStandNear(BlockPos logPos) {
        if (mc == null || mc.player == null || mc.world == null || logPos == null) {
            return false;
        }

        int py = mc.player.getBlockY();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = logPos.offset(d).add(0, dy, 0);
                if (Math.abs(p.getY() - py) > 3) {
                    continue;
                }
                if (isStandablePos(p) && canReachBlockFromStandPos(p, logPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasLineOfSightToBlock(BlockPos targetPos) {
        if (targetPos == null || mc == null || mc.world == null || mc.player == null) {
            return false;
        }

        if (hasLineOfSightToPoint(getInteractPoint(targetPos), targetPos)) {
            return true;
        }

        Vec3d c = Vec3d.ofCenter(targetPos);
        Vec3d[] probes = new Vec3d[]{
                new Vec3d(c.x, c.y + 0.20D, c.z),
                new Vec3d(c.x, c.y - 0.20D, c.z),
                new Vec3d(c.x + 0.24D, c.y, c.z),
                new Vec3d(c.x - 0.24D, c.y, c.z),
                new Vec3d(c.x, c.y, c.z + 0.24D),
                new Vec3d(c.x, c.y, c.z - 0.24D)
        };

        for (Vec3d p : probes) {
            if (hasLineOfSightToPoint(p, targetPos)) {
                return true;
            }
        }

        return false;
    }

    private boolean hasLineOfSightFromStandPos(BlockPos standPos, BlockPos targetPos) {
        if (standPos == null || targetPos == null || mc.world == null) {
            return false;
        }

        Vec3d from = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.62D, standPos.getZ() + 0.5D);

        Vec3d[] targets = new Vec3d[]{
                getInteractPoint(targetPos),
                Vec3d.ofCenter(targetPos),
                new Vec3d(targetPos.getX() + 0.5D, targetPos.getY() + 0.25D, targetPos.getZ() + 0.5D),
                new Vec3d(targetPos.getX() + 0.5D, targetPos.getY() + 0.75D, targetPos.getZ() + 0.5D)
        };

        for (Vec3d to : targets) {
            try {
                HitResult hit = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
                if (hit == null || hit.getType() == HitResult.Type.MISS) {
                    return true;
                }
                if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                    BlockPos bp = bhr.getBlockPos();
                    if (bp.equals(targetPos) || bp.equals(targetPos.down())) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean hasLineOfSightToPoint(Vec3d target, BlockPos expectedBlock) {
        if (target == null || mc == null || mc.world == null || mc.player == null) {
            return false;
        }

        Vec3d eye = mc.player.getEyePos();

        try {
            HitResult hit = mc.world.raycast(new RaycastContext(eye, target, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (hit == null || hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                BlockPos bp = bhr.getBlockPos();
                return expectedBlock != null && (bp.equals(expectedBlock) || bp.equals(expectedBlock.down()));
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean isExposedForBreaking(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }

        for (Direction d : Direction.values()) {
            BlockPos p = pos.offset(d);
            BlockState s = mc.world.getBlockState(p);
            if (s.isAir()) {
                return true;
            }
            try {
                if (s.getCollisionShape(mc.world, p).isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean tryBreakBlock(BlockPos pos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null || pos == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (!isLogBlock(state) && !isAppleLeafBlock(state)) {
            if (pos.equals(breakingPos)) {
                resetBreakingState(false);
            }
            return false;
        }

        Direction dir = getBestBreakDirection(pos);
        long now = System.currentTimeMillis();

        try {
            if (breakingPos == null || !breakingPos.equals(pos)) {
                if (now < nextActionAt) {
                    return false;
                }

                cancelBlockBreaking();
                lookAtSoft(getInteractPoint(pos));
                mc.interactionManager.attackBlock(pos, dir);
                mc.player.swingHand(Hand.MAIN_HAND);

                breakingPos = pos.toImmutable();
                lastBreakSwingAt = now;
                nextActionAt = now + ACTION_INTERVAL_MS;
                return true;
            }

            lookAtSoft(getInteractPoint(pos));

            boolean progressed;
            try {
                progressed = mc.interactionManager.updateBlockBreakingProgress(pos, dir);
            } catch (Throwable ignored) {
                progressed = false;
            }

            if (!progressed && now >= nextActionAt) {
                mc.interactionManager.attackBlock(pos, dir);
                progressed = true;
                nextActionAt = now + ACTION_INTERVAL_MS;
            }

            if (progressed && now - lastBreakSwingAt >= 120L) {
                mc.player.swingHand(Hand.MAIN_HAND);
                lastBreakSwingAt = now;
            }

            BlockState after = mc.world.getBlockState(pos);
            if (!isLogBlock(after) && !isAppleLeafBlock(after)) {
                resetBreakingState(false);
            }

            return progressed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void resetBreakingState(boolean cancel) {
        breakingPos = null;
        lastBreakSwingAt = 0L;
        if (cancel) {
            cancelBlockBreaking();
        }
    }

    private void cancelBlockBreaking() {
        try {
            if (mc != null && mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
            }
        } catch (Throwable ignored) {
        }
    }

    private BlockPos findTightApproachPosForLog(Context ctx, BlockPos logPos) {
        if (ctx == null || logPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos p = logPos.offset(d).add(0, dy, 0);

                if (!ctx.isInsideAreaMargin(p, 3)) {
                    continue;
                }
                if (!isStandablePos(p)) {
                    continue;
                }
                if (!canReachBlockFromStandPos(p, logPos)) {
                    continue;
                }

                double score = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));

                if (hasLineOfSightFromStandPos(p, logPos)) {
                    score -= 25.0D;
                }

                if (Math.abs(p.getY() - mc.player.getBlockY()) > 1) {
                    score += 6.0D;
                }

                if (score < bestScore) {
                    bestScore = score;
                    best = p.toImmutable();
                }
            }
        }

        return best;
    }

    private BlockPos findApproachPosForBreak(Context ctx, BlockPos targetPos) {
        if (ctx == null || targetPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int baseY = MathHelper.clamp(targetPos.getY(), mc.player.getBlockY() - 2, mc.player.getBlockY() + 4);

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 3; dy++) {
                        BlockPos p = new BlockPos(targetPos.getX() + dx, baseY + dy, targetPos.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 3)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }
                        if (!canReachBlockFromStandPos(p, targetPos)) {
                            continue;
                        }

                        boolean los = hasLineOfSightFromStandPos(p, targetPos);
                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double dTarget = Vec3d.ofCenter(p).squaredDistanceTo(getInteractPoint(targetPos));

                        double score = dPlayer + dTarget * 0.15D;
                        if (los) {
                            score -= 10.0D;
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

    private BlockPos findApproachPosForInteract(Context ctx, BlockPos feetCenter, BlockPos interactPos) {
        if (ctx == null || feetCenter == null || interactPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int baseY = feetCenter.getY();

        for (int r = 1; r <= 7; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = new BlockPos(feetCenter.getX() + dx, baseY + dy, feetCenter.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 3)) {
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

                        if (hasLineOfSightFromStandPos(p, interactPos)) {
                            score -= 8.0D;
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

    private BlockPos findFallbackGoalNearBlock(Context ctx, BlockPos targetPos) {
        if (ctx == null || targetPos == null || mc.world == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 3; dy++) {
                        BlockPos p = new BlockPos(targetPos.getX() + dx, targetPos.getY() + dy, targetPos.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 3)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }

                        double score = mc.player != null ? mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) : 0.0D;
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

        return targetPos;
    }

    private BlockPos findSweepStandable(Context ctx, int x, int z, int minY, int maxY) {
        if (mc.world == null || mc.player == null) {
            return null;
        }

        int py = mc.player.getBlockY();

        for (int d = 0; d <= Math.max(5, maxY - minY + 2); d++) {
            int y1 = MathHelper.clamp(py + d, minY - 1, maxY + 2);
            BlockPos p1 = new BlockPos(x, y1, z);
            if (ctx.isInsideAreaMargin(p1, 2) && isStandablePos(p1)) {
                return p1.toImmutable();
            }

            if (d == 0) {
                continue;
            }

            int y2 = MathHelper.clamp(py - d, minY - 1, maxY + 2);
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

                    for (int y = minY - 1; y <= maxY + 2; y++) {
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

    private void lookAtSoft(Vec3d target) {
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

        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        float dyaw = MathHelper.wrapDegrees(targetYaw - yaw);
        float dpitch = targetPitch - pitch;

        float yawStep = MathHelper.clamp(2.2F + Math.abs(dyaw) * 0.28F, 2.2F, 10.5F);
        float pitchStep = MathHelper.clamp(1.8F + Math.abs(dpitch) * 0.22F, 1.8F, 7.2F);

        mc.player.setYaw(yaw + MathHelper.clamp(dyaw, -yawStep, yawStep));
        mc.player.setPitch(MathHelper.clamp(pitch + MathHelper.clamp(dpitch, -pitchStep, pitchStep), -89.0F, 89.0F));
    }

    private Vec3d getInteractPoint(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return pos == null ? Vec3d.ZERO : Vec3d.ofCenter(pos);
        }

        BlockState st = mc.world.getBlockState(pos);

        if (isLogBlock(st)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.62D, pos.getZ() + 0.5D);
        }

        if (isAppleLeafBlock(st)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.50D, pos.getZ() + 0.5D);
        }

        if (isOakSaplingBlock(st)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D);
        }

        return Vec3d.ofCenter(pos);
    }

    private Direction getBestBreakDirection(BlockPos pos) {
        if (mc == null || mc.player == null || pos == null) {
            return Direction.UP;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d c = Vec3d.ofCenter(pos);

        double dx = eye.x - c.x;
        double dy = eye.y - c.y;
        double dz = eye.z - c.z;

        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);

        if (ady >= adx && ady >= adz) {
            return dy > 0.0D ? Direction.UP : Direction.DOWN;
        }

        if (adx >= adz) {
            return dx > 0.0D ? Direction.EAST : Direction.WEST;
        }

        return dz > 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private void selectBestAxeIfAvailable() {
        if (mc == null || mc.player == null) {
            return;
        }

        int hotbarAxe = findBestAxeSlot(0, 9);
        if (hotbarAxe >= 0) {
            mc.player.getInventory().selectedSlot = hotbarAxe;
            return;
        }

        if (mc.interactionManager == null) {
            return;
        }

        int invAxe = findBestAxeSlot(9, 36);
        if (invAxe < 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextInventorySwapAt) {
            return;
        }

        int targetHotbar = findBestHotbarSlotForSwap();
        if (targetHotbar < 0) {
            targetHotbar = mc.player.getInventory().selectedSlot;
            if (targetHotbar < 0 || targetHotbar > 8) {
                targetHotbar = 0;
            }
        }

        try {
            int syncId = mc.player.currentScreenHandler.syncId;
            int slotId = toScreenHandlerSlotId(invAxe);
            mc.interactionManager.clickSlot(syncId, slotId, targetHotbar, SlotActionType.SWAP, mc.player);
            nextInventorySwapAt = now + INVENTORY_SWAP_INTERVAL_MS;
            mc.player.getInventory().selectedSlot = targetHotbar;
        } catch (Throwable ignored) {
        }
    }

    private boolean selectItemToHand(Item item) {
        if (mc == null || mc.player == null || item == null) {
            return false;
        }

        int hotbar = findItemSlot(item, 0, 9);
        if (hotbar >= 0) {
            mc.player.getInventory().selectedSlot = hotbar;
            return true;
        }

        if (mc.interactionManager == null) {
            return false;
        }

        int inv = findItemSlot(item, 9, 36);
        if (inv < 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextInventorySwapAt) {
            return false;
        }

        int targetHotbar = findBestHotbarSlotForSwap();
        if (targetHotbar < 0) {
            targetHotbar = mc.player.getInventory().selectedSlot;
            if (targetHotbar < 0 || targetHotbar > 8) {
                targetHotbar = 0;
            }
        }

        try {
            int syncId = mc.player.currentScreenHandler.syncId;
            int slotId = toScreenHandlerSlotId(inv);
            mc.interactionManager.clickSlot(syncId, slotId, targetHotbar, SlotActionType.SWAP, mc.player);
            nextInventorySwapAt = now + INVENTORY_SWAP_INTERVAL_MS;
            mc.player.getInventory().selectedSlot = targetHotbar;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findBestAxeSlot(int fromInclusive, int toExclusive) {
        if (mc.player == null) {
            return -1;
        }

        int from = Math.max(0, fromInclusive);
        int to = Math.min(36, toExclusive);

        int bestSlot = -1;
        int bestScore = Integer.MIN_VALUE;

        for (int i = from; i < to; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) {
                continue;
            }

            int score = getAxeScore(s);
            if (score <= Integer.MIN_VALUE / 2) {
                continue;
            }

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int getAxeScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Integer.MIN_VALUE;
        }

        Item item = stack.getItem();
        if (item == null) {
            return Integer.MIN_VALUE;
        }

        if (item == Items.NETHERITE_AXE) return 700;
        if (item == Items.DIAMOND_AXE) return 600;
        if (item == Items.GOLDEN_AXE) return 500;
        if (item == Items.IRON_AXE) return 400;
        if (item == Items.STONE_AXE) return 300;
        if (item == Items.WOODEN_AXE) return 200;

        return Integer.MIN_VALUE;
    }

    private int findItemSlot(Item item, int fromInclusive, int toExclusive) {
        if (mc.player == null || item == null) {
            return -1;
        }

        int from = Math.max(0, fromInclusive);
        int to = Math.min(36, toExclusive);

        int bestSlot = -1;
        int bestCount = -1;

        for (int i = from; i < to; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty() || !s.isOf(item)) {
                continue;
            }
            if (s.getCount() > bestCount) {
                bestCount = s.getCount();
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private int findBestHotbarSlotForSwap() {
        if (mc.player == null) {
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
            if (s == null || s.isEmpty()) {
                return i;
            }
            if (s.isOf(Items.APPLE) || s.isOf(Items.OAK_SAPLING) || s.isOf(Items.BONE_MEAL)) {
                continue;
            }
            return i;
        }

        return mc.player.getInventory().selectedSlot;
    }

    private boolean hasItemInInventory(Item item) {
        return countItemInInventory(item) > 0;
    }

    private int countItemInInventory(Item item) {
        if (mc.player == null || item == null) {
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

    private int findInventorySlotPreferWhole(Item item) {
        if (mc.player == null || item == null) {
            return -1;
        }

        int bestSlot = -1;
        int bestCount = -1;

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty() || !s.isOf(item)) {
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
}