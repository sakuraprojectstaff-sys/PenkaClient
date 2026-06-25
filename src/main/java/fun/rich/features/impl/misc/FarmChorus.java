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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmChorus {

    static final long AREA_SCAN_INTERVAL_MS = 420L;
    static final long TARGET_SCAN_INTERVAL_MS = 95L;
    static final long FULL_RESCAN_INTERVAL_MS = 320L;
    static final long ACTION_INTERVAL_MS = 80L;
    static final long INVENTORY_SWAP_INTERVAL_MS = 90L;

    static final long BOW_PULL_MIN_MS = 1850L;
    static final long BOW_PULL_MAX_MS = 4200L;
    static final long BOW_RETRY_DELAY_MS = 320L;

    static final long PICKUP_FOCUS_MS = 3800L;
    static final long STUCK_TIMEOUT_MS = 700L;
    static final long SWEEP_REPATH_MS = 650L;
    static final long REPLANTED_FLOWER_IGNORE_MS = 9000L;

    static final long TARGET_STICK_MS = 950L;

    static final double BLOCK_REACH_SQ = 25.0D;
    static final double BOW_RANGE_SQ = 42.0D * 42.0D;
    static final double ITEM_PICKUP_CLOSE_SQ = 1.35D * 1.35D;
    static final double ITEM_SEARCH_RADIUS = 10.5D;
    static final double TARGET_FORCE_SWITCH_IMPROVE_SQ = 9.0D;

    static final int SWEEP_STEP = 2;
    static final int LOCAL_SCAN_RADIUS_XZ = 16;
    static final int LOCAL_SCAN_RADIUS_Y = 6;
    static final int MIN_CHORUS_CLUSTER_HEIGHT = 6;

    static final int BOW_MAX_USE_TICKS = 72000;

    final MinecraftClient mc = MinecraftClient.getInstance();

    long lastAreaScanAt;
    long lastTargetScanAt;
    long lastFullRescanAt;
    long nextActionAt;
    long nextInventorySwapAt;

    int cachedHarvestableFlowers;

    BlockPos targetFlower;
    long targetLockUntilAt;

    BlockPos pickupFocusPos;
    long pickupFocusUntilAt;

    BlockPos trackedPathGoal;
    Vec3d trackedPathStartPos;
    long trackedPathSinceAt;
    double trackedPathLastGoalDistSq;
    long trackedNoProgressSinceAt;
    int trackedStuckRetries;

    int sweepIndex;
    long lastSweepRepathAt;

    BlockPos breakingPlantPos;
    long lastBreakSwingAt;

    BlockPos activeClusterTopPos;
    BlockPos activeClusterReplantPos;
    long activeClusterStartedAt;
    Stage stage = Stage.SEARCH_FLOWER;

    BlockPos lastReplantedFlowerPos;
    long lastReplantedFlowerUntilAt;

    boolean bowPulling;
    long bowPullStartedAt;
    long bowRetryAt;
    BlockPos bowTargetFlowerPos;

    BlockPos lastReleasedShotFlowerPos;
    long lastReleasedShotAt;

    BlockPos cachedShootFlowerPos;
    BlockPos cachedShootStandPos;
    long cachedShootStandUntilAt;

    final HashMap<Long, Boolean> minHeightCache = new HashMap<>();
    final HashMap<Long, BlockPos> rootCache = new HashMap<>();
    long clusterCacheUntilAt;

    enum Stage {
        SEARCH_FLOWER,
        SHOOT_FLOWER,
        PICKUP_AFTER_SHOT,
        BREAK_STEMS,
        REPLANT_FLOWER,
        PICKUP_AFTER_REPLANT
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

        cachedHarvestableFlowers = 0;

        targetFlower = null;
        targetLockUntilAt = 0L;

        pickupFocusPos = null;
        pickupFocusUntilAt = 0L;

        trackedPathGoal = null;
        trackedPathStartPos = null;
        trackedPathSinceAt = 0L;
        trackedPathLastGoalDistSq = Double.MAX_VALUE;
        trackedNoProgressSinceAt = 0L;
        trackedStuckRetries = 0;

        sweepIndex = 0;
        lastSweepRepathAt = 0L;

        breakingPlantPos = null;
        lastBreakSwingAt = 0L;

        activeClusterTopPos = null;
        activeClusterReplantPos = null;
        activeClusterStartedAt = 0L;
        stage = Stage.SEARCH_FLOWER;

        lastReplantedFlowerPos = null;
        lastReplantedFlowerUntilAt = 0L;

        bowPulling = false;
        bowPullStartedAt = 0L;
        bowRetryAt = 0L;
        bowTargetFlowerPos = null;

        lastReleasedShotFlowerPos = null;
        lastReleasedShotAt = 0L;

        cachedShootFlowerPos = null;
        cachedShootStandPos = null;
        cachedShootStandUntilAt = 0L;

        minHeightCache.clear();
        rootCache.clear();
        clusterCacheUntilAt = 0L;

        cancelBlockBreaking();
        releaseUseKey();
        stopUsingItemSafe();
    }

    public void tick(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            resetTransientActions();
            return;
        }

        if (mc.currentScreen != null) {
            resetTransientActions();
            return;
        }

        if (ctx.getMinPoint() == null || ctx.getMaxPoint() == null) {
            resetTransientActions();
            return;
        }

        updateAreaCacheIfNeeded(ctx);

        if (processClusterCycle(ctx)) {
            return;
        }

        if (tryHandlePickupPhase(ctx)) {
            resetBreakingState(true);
            return;
        }

        refreshTargets(ctx);

        if (tryPickupNearbyDrops(ctx)) {
            return;
        }

        if (targetFlower != null && tryHandleFlowerTarget(ctx, targetFlower)) {
            return;
        }

        resetBreakingState(true);
        stopBowPull(false);

        if (trySweepArea(ctx)) {
            return;
        }

        ctx.stopWorkPathing();
        resetTrackedGoal();
    }

    public int getCachedHarvestableFlowers() {
        return cachedHarvestableFlowers;
    }

    public boolean hasAnyCultureToDeposit() {
        return countItemInInventory(Items.CHORUS_FRUIT) > 0;
    }

    public boolean shouldGoToStorageNow() {
        int fruits = countItemInInventory(Items.CHORUS_FRUIT);

        if (fruits <= 0) {
            return false;
        }

        boolean inventoryTight = getFreeInventorySlots(36) <= 1;
        boolean enough = fruits >= 64;
        boolean lots = fruits >= 128;
        boolean noTargetsNow = cachedHarvestableFlowers <= 0;

        if (lots) {
            return true;
        }

        if (inventoryTight) {
            return true;
        }

        if (enough && noTargetsNow) {
            return true;
        }

        return enough;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        if (mc.player == null || mc.interactionManager == null) {
            return false;
        }

        int fruitSlot = findInventorySlotPreferWhole(Items.CHORUS_FRUIT);
        if (fruitSlot < 0) {
            return false;
        }

        return throwInventorySlot(fruitSlot, true);
    }

    private boolean processClusterCycle(Context ctx) {
        if (activeClusterTopPos == null || mc.world == null || mc.player == null) {
            return false;
        }

        if (!ctx.isInsideAreaMargin(activeClusterTopPos, 2)) {
            clearClusterCycle();
            return false;
        }

        if (System.currentTimeMillis() - activeClusterStartedAt > 45_000L) {
            clearClusterCycle();
            return false;
        }

        BlockPos replantPos = activeClusterReplantPos != null ? activeClusterReplantPos : activeClusterTopPos;
        BlockPos dropAnchor = activeClusterTopPos != null ? activeClusterTopPos : replantPos;

        switch (stage) {
            case SHOOT_FLOWER -> {
                return tryHandleFlowerTarget(ctx, activeClusterTopPos);
            }
            case PICKUP_AFTER_SHOT -> {
                if (tryFastRetryMissedShot(ctx)) {
                    return true;
                }

                if (tryHandlePickupPhase(ctx)) {
                    return true;
                }

                if (System.currentTimeMillis() < pickupFocusUntilAt) {
                    if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(activeClusterTopPos)) > 4.0D) {
                        BlockPos waitGoal = findPickupGoalNearItem(ctx, Vec3d.ofCenter(activeClusterTopPos));
                        if (waitGoal != null) {
                            pathToGoalTracked(ctx, waitGoal);
                            return true;
                        }
                        pathToGoalTracked(ctx, activeClusterTopPos);
                        return true;
                    }
                    return true;
                }

                if (activeClusterTopPos != null && mc.world.getBlockState(activeClusterTopPos).isOf(Blocks.CHORUS_FLOWER)) {
                    stage = Stage.SHOOT_FLOWER;
                    targetFlower = activeClusterTopPos.toImmutable();
                    return true;
                }

                stage = Stage.BREAK_STEMS;
                return true;
            }
            case BREAK_STEMS -> {
                if (activeClusterTopPos != null && mc.world.getBlockState(activeClusterTopPos).isOf(Blocks.CHORUS_FLOWER)) {
                    resetBreakingState(true);
                    stage = Stage.SHOOT_FLOWER;
                    targetFlower = activeClusterTopPos.toImmutable();
                    return true;
                }

                if (pickupFocusPos != null) {
                    ItemEntity near = findNearestRelevantDropAround(pickupFocusPos, ITEM_SEARCH_RADIUS, false);
                    if (near != null && tryHandlePickupPhase(ctx)) {
                        return true;
                    }
                }

                if (replantPos != null) {
                    BlockState replantState = mc.world.getBlockState(replantPos);
                    BlockState supportState = mc.world.getBlockState(replantPos.down());

                    if (replantState.isAir() && supportState.isOf(Blocks.END_STONE)) {
                        resetBreakingState(true);
                        stage = Stage.REPLANT_FLOWER;
                        return true;
                    }

                    if (isValidStemTarget(ctx, replantPos)) {
                        if (tryHandleStemTarget(ctx, replantPos)) {
                            return true;
                        }
                    }
                }

                BlockPos stem = findNearestClusterStem(ctx, activeClusterTopPos);
                if (stem == null) {
                    resetBreakingState(true);
                    stage = Stage.REPLANT_FLOWER;
                    return true;
                }

                if (tryHandleStemTarget(ctx, stem)) {
                    return true;
                }

                return true;
            }
            case REPLANT_FLOWER -> {
                if (replantPos == null) {
                    clearClusterCycle();
                    return false;
                }

                if (!hasChorusFlowerForReplant() && hasNearbyChorusFlowerDropForReplant(dropAnchor)) {
                    pickupFocusPos = dropAnchor.toImmutable();
                    pickupFocusUntilAt = System.currentTimeMillis() + 1800L;
                    if (tryHandlePickupPhase(ctx)) {
                        return true;
                    }
                    return true;
                }

                if (tryReplantFlowerAtTop(ctx, replantPos)) {
                    return true;
                }

                if (mc.world.getBlockState(replantPos).isOf(Blocks.CHORUS_FLOWER)) {
                    lastReplantedFlowerPos = replantPos.toImmutable();
                    lastReplantedFlowerUntilAt = System.currentTimeMillis() + REPLANTED_FLOWER_IGNORE_MS;

                    pickupFocusPos = replantPos.toImmutable();
                    pickupFocusUntilAt = System.currentTimeMillis() + PICKUP_FOCUS_MS;
                    stage = Stage.PICKUP_AFTER_REPLANT;
                    return true;
                }

                if (!hasChorusFlowerForReplant()) {
                    pickupFocusPos = dropAnchor.toImmutable();
                    pickupFocusUntilAt = System.currentTimeMillis() + 1000L;
                    stage = Stage.PICKUP_AFTER_REPLANT;
                    return true;
                }

                if (findReplantSupportPos(replantPos) == null) {
                    pickupFocusPos = dropAnchor.toImmutable();
                    pickupFocusUntilAt = System.currentTimeMillis() + 800L;
                    stage = Stage.PICKUP_AFTER_REPLANT;
                    return true;
                }

                return true;
            }
            case PICKUP_AFTER_REPLANT -> {
                if (tryHandlePickupPhase(ctx)) {
                    return true;
                }

                if (tryPostReplantRetreat(ctx)) {
                    return true;
                }

                clearClusterCycle();
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private void clearClusterCycle() {
        activeClusterTopPos = null;
        activeClusterReplantPos = null;
        activeClusterStartedAt = 0L;
        stage = Stage.SEARCH_FLOWER;
        targetFlower = null;
        targetLockUntilAt = 0L;
        pickupFocusPos = null;
        pickupFocusUntilAt = 0L;
        stopBowPull(false);
        resetBreakingState(true);
    }

    private void beginClusterCycle(BlockPos topFlowerPos) {
        activeClusterTopPos = topFlowerPos == null ? null : topFlowerPos.toImmutable();
        activeClusterReplantPos = findClusterRootReplantPos(activeClusterTopPos);
        if (activeClusterReplantPos == null) {
            activeClusterReplantPos = activeClusterTopPos;
        }
        activeClusterStartedAt = System.currentTimeMillis();
        stage = Stage.SHOOT_FLOWER;
        setTargetFlowerLocked(activeClusterTopPos, System.currentTimeMillis());
        pickupFocusPos = null;
        pickupFocusUntilAt = 0L;
    }

    private void refreshTargets(Context ctx) {
        long now = System.currentTimeMillis();

        if (targetFlower != null && !isValidFlowerTarget(ctx, targetFlower)) {
            if (targetFlower.equals(bowTargetFlowerPos)) {
                stopBowPull(false);
            }
            targetFlower = null;
            targetLockUntilAt = 0L;
        }

        if (now - lastTargetScanAt < TARGET_SCAN_INTERVAL_MS) {
            return;
        }

        lastTargetScanAt = now;
        scanLocalTargets(ctx);

        if (targetFlower == null && now - lastFullRescanAt >= FULL_RESCAN_INTERVAL_MS) {
            lastFullRescanAt = now;
            scanFullTargets(ctx);
            return;
        }

        if (targetFlower == null || !isFlowerTargetSelectionLocked(ctx, now)) {
            if (now - lastFullRescanAt >= FULL_RESCAN_INTERVAL_MS) {
                lastFullRescanAt = now;
                scanFullTargets(ctx);
            }
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

        long now = System.currentTimeMillis();
        BlockPos p = mc.player.getBlockPos();

        int fromX = Math.max(min.getX(), p.getX() - LOCAL_SCAN_RADIUS_XZ);
        int toX = Math.min(max.getX(), p.getX() + LOCAL_SCAN_RADIUS_XZ);
        int fromY = Math.max(min.getY(), p.getY() - LOCAL_SCAN_RADIUS_Y);
        int toY = Math.min(max.getY(), p.getY() + LOCAL_SCAN_RADIUS_Y);
        int fromZ = Math.max(min.getZ(), p.getZ() - LOCAL_SCAN_RADIUS_XZ);
        int toZ = Math.min(max.getZ(), p.getZ() + LOCAL_SCAN_RADIUS_XZ);

        Vec3d playerPos = mc.player.getPos();

        BlockPos current = targetFlower;
        boolean locked = isFlowerTargetSelectionLocked(ctx, now);

        double currentDist = Double.MAX_VALUE;
        if (current != null) {
            currentDist = playerPos.squaredDistanceTo(getFlowerAimPoint(current));
        }

        BlockPos bestPos = current == null ? null : current.toImmutable();
        double bestDist = currentDist;

        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);

                    if (!isHarvestableFlower(state)) {
                        continue;
                    }

                    if (!isValidFlowerTarget(ctx, pos)) {
                        continue;
                    }

                    double d = playerPos.squaredDistanceTo(getFlowerAimPoint(pos));

                    if (bestPos != null && !bestPos.equals(pos) && locked) {
                        if (d + TARGET_FORCE_SWITCH_IMPROVE_SQ >= bestDist) {
                            continue;
                        }
                    }

                    if (bestPos == null || d < bestDist) {
                        bestDist = d;
                        bestPos = pos.toImmutable();
                    }
                }
            }
        }

        if (bestPos == null) {
            return;
        }

        if (targetFlower == null || !targetFlower.equals(bestPos)) {
            setTargetFlowerLocked(bestPos, now);
        } else {
            targetLockUntilAt = Math.max(targetLockUntilAt, now + 120L);
        }
    }

    private void scanFullTargets(Context ctx) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (isFlowerTargetSelectionLocked(ctx, now)) {
            return;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        Vec3d playerPos = mc.player.getPos();
        double best = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            BlockState state = mc.world.getBlockState(pos);

            if (!isHarvestableFlower(state)) {
                continue;
            }

            if (!isValidFlowerTarget(ctx, pos)) {
                continue;
            }

            double d = playerPos.squaredDistanceTo(getFlowerAimPoint(pos));
            if (d < best) {
                best = d;
                bestPos = pos.toImmutable();
            }
        }

        if (bestPos == null) {
            targetFlower = null;
            targetLockUntilAt = 0L;
            return;
        }

        if (targetFlower == null || !targetFlower.equals(bestPos)) {
            setTargetFlowerLocked(bestPos, now);
        } else {
            targetLockUntilAt = Math.max(targetLockUntilAt, now + 120L);
        }
    }

    private boolean tryHandleFlowerTarget(Context ctx, BlockPos flowerPos) {
        if (!isValidFlowerTarget(ctx, flowerPos)) {
            if (flowerPos.equals(bowTargetFlowerPos)) {
                stopBowPull(false);
            }
            if (activeClusterTopPos != null && activeClusterTopPos.equals(flowerPos)) {
                pickupFocusPos = flowerPos.toImmutable();
                pickupFocusUntilAt = System.currentTimeMillis() + PICKUP_FOCUS_MS;
                stage = Stage.PICKUP_AFTER_SHOT;
                stopBowPull(false);
                targetFlower = null;
                targetLockUntilAt = 0L;
                return true;
            }
            targetFlower = null;
            targetLockUntilAt = 0L;
            return false;
        }

        if (!hasUsableBowAndArrow()) {
            stopBowPull(false);
            return false;
        }

        boolean continuingSamePull = bowPulling && bowTargetFlowerPos != null && bowTargetFlowerPos.equals(flowerPos);

        if (continuingSamePull || canShootFlowerFromCurrentPos(flowerPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return tryShootFlower(flowerPos);
        }

        stopBowPull(true);

        if (trackedNoProgressSinceAt > 0L && System.currentTimeMillis() - trackedNoProgressSinceAt > 1200L) {
            invalidateShootStandCache(flowerPos);
            targetLockUntilAt = 0L;
        }

        BlockPos shootPos = findShootStandPosForFlower(ctx, flowerPos);
        if (shootPos == null) {
            BlockPos fallback = findFallbackGoalNearBlock(ctx, flowerPos);
            if (fallback != null) {
                pathToGoalTracked(ctx, fallback);
            } else {
                pathToGoalTracked(ctx, flowerPos);
            }
            targetLockUntilAt = Math.min(targetLockUntilAt, System.currentTimeMillis() + 140L);
            return true;
        }

        pathToGoalTracked(ctx, shootPos);
        return true;
    }

    private boolean tryShootFlower(BlockPos flowerPos) {
        if (mc.player == null || mc.world == null || flowerPos == null) {
            stopBowPull(false);
            return false;
        }

        if (!isHarvestableFlower(mc.world.getBlockState(flowerPos))) {
            stopBowPull(false);
            return false;
        }

        long now = System.currentTimeMillis();

        if (now < bowRetryAt) {
            Vec3d retryAimRaw = findVisibleFlowerAimPoint(mc.player.getEyePos(), flowerPos, mc.player.getBlockPos());
            Vec3d retryAim = retryAimRaw != null ? getBowReleaseAimPoint(retryAimRaw) : getFlowerAimPoint(flowerPos);
            lookAtSoft(retryAim, false);
            return true;
        }

        if (!selectBestBowIfAvailable()) {
            stopBowPull(false);
            return false;
        }

        if (!hasAnyArrow()) {
            stopBowPull(false);
            return false;
        }

        Vec3d aimPointRaw = findVisibleFlowerAimPoint(mc.player.getEyePos(), flowerPos, mc.player.getBlockPos());
        if (aimPointRaw == null) {
            stopBowPull(true);
            return false;
        }

        Vec3d aimPoint = getBowReleaseAimPoint(aimPointRaw);
        lookAtSoft(aimPoint, false);

        ItemStack main = mc.player.getMainHandStack();
        if (main == null || main.isEmpty() || !isBow(main)) {
            stopBowPull(false);
            return false;
        }

        BlockPos shotFlowerPos = flowerPos.toImmutable();
        bowTargetFlowerPos = shotFlowerPos;

        try {
            setUseKeyPressed(true);

            if (!bowPulling) {
                try {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    }
                } catch (Throwable ignored) {
                }
                bowPulling = true;
                bowPullStartedAt = 0L;
                return true;
            }

            if (!mc.player.isUsingItem()) {
                try {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }

            if (bowPullStartedAt <= 0L) {
                bowPullStartedAt = now;
            }

            int pullTicks = getCurrentBowPullTicks(main);
            long heldMs = now - bowPullStartedAt;

            boolean hasReliableTicks = pullTicks >= 2;
            boolean chargedByTicks = pullTicks >= 24;
            boolean chargedByMsFallback = !hasReliableTicks && heldMs >= BOW_PULL_MIN_MS;

            if (!chargedByTicks && !chargedByMsFallback) {
                return true;
            }

            boolean forceReleaseByTicks = pullTicks >= 30;
            boolean forceReleaseByMs = heldMs >= BOW_PULL_MAX_MS;

            if (!forceReleaseByTicks && !forceReleaseByMs) {
                if (!isCrosshairCloseTo(aimPoint, 5.8F, 6.2F)) {
                    return true;
                }
            }

            releaseBowShotNow();

            bowPulling = false;
            bowPullStartedAt = 0L;
            bowRetryAt = now + BOW_RETRY_DELAY_MS;
            rememberReleasedShot(shotFlowerPos, now);

            if (activeClusterTopPos == null || !activeClusterTopPos.equals(shotFlowerPos)) {
                beginClusterCycle(shotFlowerPos);
            }

            pickupFocusPos = shotFlowerPos;
            pickupFocusUntilAt = now + 1500L;
            stage = Stage.PICKUP_AFTER_SHOT;
            targetFlower = null;
            targetLockUntilAt = 0L;

            return true;
        } catch (Throwable ignored) {
            stopBowPull(false);
            return false;
        }
    }

    private boolean tryFastRetryMissedShot(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return false;
        }

        if (stage != Stage.PICKUP_AFTER_SHOT) {
            return false;
        }

        if (activeClusterTopPos == null || lastReleasedShotFlowerPos == null) {
            return false;
        }

        if (!activeClusterTopPos.equals(lastReleasedShotFlowerPos)) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - lastReleasedShotAt < 520L) {
            return false;
        }

        if (!mc.world.getBlockState(activeClusterTopPos).isOf(Blocks.CHORUS_FLOWER)) {
            return false;
        }

        ItemEntity drop = findNearestRelevantDropAround(activeClusterTopPos, 3.8D, true);
        if (drop != null) {
            return false;
        }

        pickupFocusPos = null;
        pickupFocusUntilAt = 0L;

        stopBowPull(true);
        invalidateShootStandCache(activeClusterTopPos);

        if (bowRetryAt <= now || bowRetryAt > now + 220L) {
            bowRetryAt = now + 70L;
        }

        stage = Stage.SHOOT_FLOWER;
        targetFlower = activeClusterTopPos.toImmutable();
        targetLockUntilAt = now + 120L;

        ctx.stopWorkPathing();
        resetTrackedGoal();
        return true;
    }

    private void rememberReleasedShot(BlockPos flowerPos, long now) {
        lastReleasedShotFlowerPos = flowerPos == null ? null : flowerPos.toImmutable();
        lastReleasedShotAt = now;
    }

    private void invalidateShootStandCache(BlockPos flowerPos) {
        if (flowerPos == null) {
            cachedShootFlowerPos = null;
            cachedShootStandPos = null;
            cachedShootStandUntilAt = 0L;
            return;
        }

        if (cachedShootFlowerPos != null && cachedShootFlowerPos.equals(flowerPos)) {
            cachedShootFlowerPos = null;
            cachedShootStandPos = null;
            cachedShootStandUntilAt = 0L;
        }
    }

    private Vec3d getBowReleaseAimPoint(Vec3d rawAim) {
        if (mc == null || mc.player == null || rawAim == null) {
            return rawAim;
        }

        Vec3d eye = mc.player.getEyePos();

        double dx = rawAim.x - eye.x;
        double dy = rawAim.y - eye.y;
        double dz = rawAim.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (horiz < 5.0D) {
            return rawAim;
        }

        double lift = 0.0028D * horiz * horiz + 0.012D * horiz;

        if (dy > 2.0D) {
            lift += 0.10D;
        } else if (dy < -1.0D) {
            lift -= 0.08D;
        }

        lift = MathHelper.clamp(lift, 0.0D, 2.15D);

        return new Vec3d(rawAim.x, rawAim.y + lift, rawAim.z);
    }

    private void ensureClusterCachesFresh() {
        long now = System.currentTimeMillis();

        if (now >= clusterCacheUntilAt || minHeightCache.size() > 4096 || rootCache.size() > 4096) {
            minHeightCache.clear();
            rootCache.clear();
            clusterCacheUntilAt = now + 1400L;
        }
    }

    private int getCurrentBowPullTicks(ItemStack bowStack) {
        if (mc == null || mc.player == null || bowStack == null || bowStack.isEmpty()) {
            return 0;
        }

        int elapsedByLeft = -1;
        int left = getItemUseTimeLeftSafe();
        if (left >= 0 && left <= BOW_MAX_USE_TICKS) {
            elapsedByLeft = Math.max(0, BOW_MAX_USE_TICKS - left);
        }

        int rawUse = -1;
        try {
            rawUse = mc.player.getItemUseTime();
        } catch (Throwable ignored) {
        }

        if (rawUse >= 0) {
            if (rawUse > 2000) {
                int conv = BOW_MAX_USE_TICKS - rawUse;
                if (conv >= 0 && conv <= BOW_MAX_USE_TICKS) {
                    return conv;
                }
                if (elapsedByLeft >= 0) {
                    return elapsedByLeft;
                }
                return Math.max(0, Math.min(200, conv));
            }

            if (rawUse <= 120) {
                return Math.max(0, rawUse);
            }

            if (elapsedByLeft >= 0) {
                return elapsedByLeft;
            }

            return Math.max(0, Math.min(200, rawUse));
        }

        if (elapsedByLeft >= 0) {
            return elapsedByLeft;
        }

        if (bowPullStartedAt > 0L) {
            long heldMs = System.currentTimeMillis() - bowPullStartedAt;
            return (int) Math.max(0L, heldMs / 50L);
        }

        return 0;
    }

    private int getItemUseTimeLeftSafe() {
        if (mc == null || mc.player == null) {
            return -1;
        }

        try {
            Method m = mc.player.getClass().getMethod("getItemUseTimeLeft");
            Object r = m.invoke(mc.player);
            if (r instanceof Integer) {
                return (Integer) r;
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = mc.player.getClass().getDeclaredField("itemUseTimeLeft");
            f.setAccessible(true);
            Object r = f.get(mc.player);
            if (r instanceof Integer) {
                return (Integer) r;
            }
        } catch (Throwable ignored) {
        }

        return -1;
    }

    private boolean tryHandleStemTarget(Context ctx, BlockPos stemPos) {
        if (!isValidStemTarget(ctx, stemPos)) {
            if (stemPos.equals(breakingPlantPos)) {
                resetBreakingState(true);
            }
            return false;
        }

        if (canReachBlock(stemPos)) {
            ctx.stopWorkPathing();
            resetTrackedGoal();

            boolean handled = tryBreakStem(stemPos);
            if (!handled) {
                return false;
            }

            if (!isValidStemTarget(ctx, stemPos)) {
                pickupFocusPos = (activeClusterTopPos != null ? activeClusterTopPos : stemPos).toImmutable();
                pickupFocusUntilAt = System.currentTimeMillis() + 700L;
                resetBreakingState(false);
            }

            return true;
        }

        if (breakingPlantPos != null) {
            resetBreakingState(true);
        }

        BlockPos approach = findApproachPosForInteract(ctx, stemPos, stemPos);
        if (approach == null) {
            pathToGoalTracked(ctx, findFallbackGoalNearBlock(ctx, stemPos));
            return true;
        }

        pathToGoalTracked(ctx, approach);
        return true;
    }

    private boolean tryReplantFlowerAtTop(Context ctx, BlockPos topPos) {
        if (ctx == null || topPos == null || mc.player == null || mc.interactionManager == null || mc.world == null) {
            return false;
        }

        BlockState topState = mc.world.getBlockState(topPos);
        if (topState.isOf(Blocks.CHORUS_FLOWER)) {
            return false;
        }

        if (!topState.isAir()) {
            return false;
        }

        BlockPos support = findReplantSupportPos(topPos);
        if (support == null) {
            return false;
        }

        Direction face = getReplantSupportFace(topPos, support);
        if (face == null) {
            return false;
        }

        if (!hasChorusFlowerForReplant()) {
            return false;
        }

        if (isPlayerBlockingPlacement(topPos)) {
            BlockPos reposition = findReplantApproachPos(ctx, topPos, support);
            if (reposition != null && mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(reposition)) > 1.2D) {
                pathToGoalTracked(ctx, reposition);
                return true;
            }
        }

        if (!canReachReplantFromCurrentPos(support, face)) {
            BlockPos approach = findReplantApproachPos(ctx, topPos, support);
            if (approach == null) {
                pathToGoalTracked(ctx, findFallbackGoalNearBlock(ctx, topPos));
                return true;
            }
            pathToGoalTracked(ctx, approach);
            return true;
        }

        ctx.stopWorkPathing();
        resetTrackedGoal();

        if (!selectChorusFlowerForPlaceIfAvailable()) {
            return true;
        }

        ItemStack main = mc.player.getMainHandStack();
        if (main == null || main.isEmpty() || !main.isOf(Items.CHORUS_FLOWER)) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionAt) {
            return true;
        }

        Vec3d hitVec = getReplantHitVec(support, face);
        lookAtSoft(hitVec, true);

        try {
            BlockHitResult hit = new BlockHitResult(hitVec, face, support, false);
            ActionResult r = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (r != null && r.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                if (isPlayerBlockingPlacement(topPos)) {
                    BlockPos reposition = findReplantApproachPos(ctx, topPos, support);
                    if (reposition != null && mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(reposition)) > 1.2D) {
                        pathToGoalTracked(ctx, reposition);
                        return true;
                    }
                }
                try {
                    ActionResult r2 = mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    if (r2 != null && r2.isAccepted()) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                } catch (Throwable ignored) {
                }
            }
            nextActionAt = now + ACTION_INTERVAL_MS;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasNearbyChorusFlowerDropForReplant(BlockPos topPos) {
        return findNearestRelevantDropAround(topPos, ITEM_SEARCH_RADIUS, true) != null;
    }

    private BlockPos findReplantSupportPos(BlockPos topPos) {
        if (topPos == null || mc.world == null) {
            return null;
        }

        BlockPos down = topPos.down();
        BlockState downState = mc.world.getBlockState(down);
        if (downState.isOf(Blocks.CHORUS_PLANT) || downState.isOf(Blocks.END_STONE)) {
            return down.toImmutable();
        }

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos side = topPos.offset(d);
            BlockState s = mc.world.getBlockState(side);
            if (s.isOf(Blocks.CHORUS_PLANT)) {
                return side.toImmutable();
            }
        }

        return null;
    }

    private Direction getReplantSupportFace(BlockPos topPos, BlockPos supportPos) {
        if (topPos == null || supportPos == null) {
            return null;
        }

        if (supportPos.equals(topPos.down())) {
            return Direction.UP;
        }

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (supportPos.equals(topPos.offset(d))) {
                return d.getOpposite();
            }
        }

        return null;
    }

    private Vec3d getReplantHitVec(BlockPos support, Direction face) {
        Vec3d c = Vec3d.ofCenter(support);
        return c.add(face.getOffsetX() * 0.48D, face.getOffsetY() * 0.48D, face.getOffsetZ() * 0.48D);
    }

    private boolean canReachReplantFromCurrentPos(BlockPos support, Direction face) {
        if (mc.player == null || support == null || face == null) {
            return false;
        }
        return mc.player.getEyePos().squaredDistanceTo(getReplantHitVec(support, face)) <= BLOCK_REACH_SQ;
    }

    private boolean tryHandlePickupPhase(Context ctx) {
        if (pickupFocusPos == null || mc.player == null || mc.world == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > pickupFocusUntilAt) {
            pickupFocusPos = null;
            return false;
        }

        boolean flowersOnly = stage == Stage.PICKUP_AFTER_SHOT;
        ItemEntity drop = findNearestRelevantDropAround(pickupFocusPos, ITEM_SEARCH_RADIUS, flowersOnly);
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

        ItemEntity drop = findNearestRelevantDropAround(mc.player.getBlockPos(), 7.0D);
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
            trackedStuckRetries = 0;
            ctx.pathToWorkGoal(trackedPathGoal);
            return;
        }

        if (trackedPathStartPos == null) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            trackedStuckRetries = 0;
            ctx.pathToWorkGoal(trackedPathGoal);
            return;
        }

        if (goalDistSq + 0.15D < trackedPathLastGoalDistSq) {
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            trackedStuckRetries = 0;
        }

        double movedSq = mc.player.getPos().squaredDistanceTo(trackedPathStartPos);

        if (movedSq >= 0.35D) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
        }

        boolean stuckByMovement = now - trackedPathSinceAt >= STUCK_TIMEOUT_MS && movedSq < 0.20D;
        boolean stuckByNoProgress = now - trackedNoProgressSinceAt >= STUCK_TIMEOUT_MS && goalDistSq > 4.0D;

        if (stuckByMovement || stuckByNoProgress) {
            trackedStuckRetries++;

            if (trackedStuckRetries >= 3) {
                if (targetFlower != null) {
                    invalidateShootStandCache(targetFlower);
                    targetLockUntilAt = 0L;
                }
                ctx.stopWorkPathing();
                resetTrackedGoal();
                return;
            }

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
        trackedStuckRetries = 0;
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

        for (int d = 0; d <= Math.max(6, maxY - minY + 3); d++) {
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

    private void updateAreaCacheIfNeeded(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastAreaScanAt < AREA_SCAN_INTERVAL_MS) {
            return;
        }
        lastAreaScanAt = now;

        cachedHarvestableFlowers = 0;

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null || mc.world == null) {
            return;
        }

        long t = System.currentTimeMillis();

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            if (!ctx.isInSelectedArea(pos)) {
                continue;
            }
            if (!isHarvestableFlower(mc.world.getBlockState(pos))) {
                continue;
            }
            if (isFlowerBaseOnEndStone(pos)) {
                continue;
            }
            if (isRecentlyIgnoredReplantedFlower(pos, t)) {
                continue;
            }
            if (!hasValidChorusSupportNearFlower(pos)) {
                continue;
            }
            if (!hasMinClusterHeightForHarvest(pos)) {
                continue;
            }

            cachedHarvestableFlowers++;
        }
    }

    private ItemEntity findNearestRelevantDropAround(BlockPos centerPos, double radius) {
        return findNearestRelevantDropAround(centerPos, radius, false);
    }

    private ItemEntity findNearestRelevantDropAround(BlockPos centerPos, double radius, boolean flowersOnly) {
        if (mc.world == null || mc.player == null || centerPos == null) {
            return null;
        }

        Vec3d c = Vec3d.ofCenter(centerPos);
        Box box = new Box(
                c.x - radius, c.y - 3.0D, c.z - radius,
                c.x + radius, c.y + 4.0D, c.z + radius
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

            if (flowersOnly) {
                if (!stack.isOf(Items.CHORUS_FLOWER)) {
                    continue;
                }
            } else {
                if (!isRelevantChorusDrop(stack)) {
                    continue;
                }
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

    private BlockPos findNearestClusterStem(Context ctx, BlockPos topFlowerPos) {
        if (ctx == null || topFlowerPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d playerPos = mc.player.getPos();

        int minY = topFlowerPos.getY() - 18;
        int maxY = topFlowerPos.getY();
        int radiusXZ = 6;

        for (int y = maxY; y >= minY; y--) {
            for (int x = topFlowerPos.getX() - radiusXZ; x <= topFlowerPos.getX() + radiusXZ; x++) {
                for (int z = topFlowerPos.getZ() - radiusXZ; z <= topFlowerPos.getZ() + radiusXZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);

                    if (!ctx.isInsideAreaMargin(p, 2)) {
                        continue;
                    }

                    if (!isValidStemTarget(ctx, p)) {
                        continue;
                    }

                    if (isProtectedStemForCurrentCluster(p)) {
                        continue;
                    }

                    double dPlayer = playerPos.squaredDistanceTo(getInteractPoint(p));
                    double dx = p.getX() - topFlowerPos.getX();
                    double dz = p.getZ() - topFlowerPos.getZ();
                    double dTop = dx * dx + dz * dz + Math.abs(p.getY() - topFlowerPos.getY()) * 0.55D;
                    double score = dPlayer * 0.75D + dTop * 0.25D;

                    if (score < bestScore) {
                        bestScore = score;
                        best = p.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    private boolean isValidFlowerTarget(Context ctx, BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }
        if (!ctx.isInSelectedArea(pos)) {
            return false;
        }
        if (!isHarvestableFlower(mc.world.getBlockState(pos))) {
            return false;
        }
        if (isFlowerBaseOnEndStone(pos)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (isRecentlyIgnoredReplantedFlower(pos, now)) {
            return false;
        }

        if (!hasValidChorusSupportNearFlower(pos)) {
            return false;
        }

        if (!hasMinClusterHeightForHarvest(pos)) {
            return false;
        }

        return true;
    }

    private boolean isValidStemTarget(Context ctx, BlockPos pos) {
        if (pos == null || mc.world == null) {
            return false;
        }
        if (!ctx.isInsideAreaMargin(pos, 2)) {
            return false;
        }
        if (!isHarvestableStem(mc.world.getBlockState(pos))) {
            return false;
        }
        if (isProtectedStemForCurrentCluster(pos)) {
            return false;
        }
        return true;
    }

    private boolean isProtectedStemForCurrentCluster(BlockPos pos) {
        if (pos == null || activeClusterTopPos == null || mc.world == null) {
            return false;
        }

        if (stage == Stage.BREAK_STEMS || stage == Stage.REPLANT_FLOWER || stage == Stage.PICKUP_AFTER_REPLANT) {
            return false;
        }

        BlockState self = mc.world.getBlockState(pos);
        if (!self.isOf(Blocks.CHORUS_PLANT)) {
            return false;
        }

        int topX = activeClusterTopPos.getX();
        int topY = activeClusterTopPos.getY();
        int topZ = activeClusterTopPos.getZ();

        int dxTop = Math.abs(pos.getX() - topX);
        int dzTop = Math.abs(pos.getZ() - topZ);

        if (dxTop <= 1 && dzTop <= 1 && pos.getY() >= topY - 2 && pos.getY() < topY) {
            return true;
        }

        BlockPos[] directSupports = getDirectSupportPlantsForTop(activeClusterTopPos);
        for (BlockPos support : directSupports) {
            if (support == null) {
                continue;
            }

            if (support.equals(pos)) {
                return true;
            }

            if (support.getX() == pos.getX() && support.getZ() == pos.getZ() && pos.getY() <= support.getY()) {
                for (int y = support.getY(); y >= support.getY() - 32; y--) {
                    BlockPos p = new BlockPos(support.getX(), y, support.getZ());
                    BlockState s = mc.world.getBlockState(p);
                    if (!s.isOf(Blocks.CHORUS_PLANT)) {
                        break;
                    }
                    if (p.equals(pos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private BlockPos[] getDirectSupportPlantsForTop(BlockPos topPos) {
        BlockPos[] out = new BlockPos[5];
        if (topPos == null || mc.world == null) {
            return out;
        }

        int i = 0;

        BlockPos down = topPos.down();
        if (mc.world.getBlockState(down).isOf(Blocks.CHORUS_PLANT)) {
            out[i++] = down.toImmutable();
        }

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos side = topPos.offset(d);
            if (mc.world.getBlockState(side).isOf(Blocks.CHORUS_PLANT)) {
                out[i++] = side.toImmutable();
                if (i >= out.length) {
                    break;
                }
            }
        }

        return out;
    }

    private boolean hasValidChorusSupportNearFlower(BlockPos flowerPos) {
        if (mc.world == null || flowerPos == null) {
            return false;
        }

        if (isValidChorusSupportForFlower(mc.world.getBlockState(flowerPos.down()))) {
            return true;
        }

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (isValidChorusSupportForFlower(mc.world.getBlockState(flowerPos.offset(d)))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlowerBaseOnEndStone(BlockPos pos) {
        return pos != null && mc.world != null && mc.world.getBlockState(pos.down()).isOf(Blocks.END_STONE);
    }

    private boolean isRecentlyIgnoredReplantedFlower(BlockPos pos, long now) {
        return pos != null && lastReplantedFlowerPos != null && pos.equals(lastReplantedFlowerPos) && now < lastReplantedFlowerUntilAt;
    }

    private boolean isPlayerBlockingPlacement(BlockPos placePos) {
        if (placePos == null || mc.player == null) {
            return false;
        }
        try {
            Box placeBox = new Box(placePos).expand(-0.01D, -0.01D, -0.01D);
            return mc.player.getBoundingBox().intersects(placeBox);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private BlockPos findClusterRootReplantPos(BlockPos anyFlowerPos) {
        if (anyFlowerPos == null || mc.world == null) {
            return null;
        }

        ensureClusterCachesFresh();

        BlockPos cached = rootCache.get(anyFlowerPos.asLong());
        if (cached != null) {
            return cached.toImmutable();
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();

        queue.add(anyFlowerPos.toImmutable());

        BlockPos best = null;
        int bestY = Integer.MAX_VALUE;
        double bestXZ = Double.MAX_VALUE;

        int minY = anyFlowerPos.getY() - 40;
        int maxY = anyFlowerPos.getY() + 8;

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            long key = p.asLong();

            if (!visited.add(key)) {
                continue;
            }

            if (p.getY() < minY || p.getY() > maxY) {
                continue;
            }

            if (Math.abs(p.getX() - anyFlowerPos.getX()) > 18 || Math.abs(p.getZ() - anyFlowerPos.getZ()) > 18) {
                continue;
            }

            BlockState state = mc.world.getBlockState(p);
            if (!isChorusClusterBlock(state)) {
                continue;
            }

            if (mc.world.getBlockState(p.down()).isOf(Blocks.END_STONE)) {
                double xz = (p.getX() - anyFlowerPos.getX()) * (double) (p.getX() - anyFlowerPos.getX())
                        + (p.getZ() - anyFlowerPos.getZ()) * (double) (p.getZ() - anyFlowerPos.getZ());

                if (best == null || p.getY() < bestY || (p.getY() == bestY && xz < bestXZ)) {
                    best = p.toImmutable();
                    bestY = p.getY();
                    bestXZ = xz;
                }
            }

            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                long nk = n.asLong();
                if (!visited.contains(nk)) {
                    queue.add(n.toImmutable());
                }
            }
        }

        if (best != null) {
            rootCache.put(anyFlowerPos.asLong(), best.toImmutable());
        }

        return best;
    }

    private boolean isChorusClusterBlock(BlockState state) {
        return state != null && (state.isOf(Blocks.CHORUS_PLANT) || state.isOf(Blocks.CHORUS_FLOWER));
    }

    private boolean canShootFlowerFromCurrentPos(BlockPos flowerPos) {
        if (mc.player == null || flowerPos == null) {
            return false;
        }

        Vec3d eye = mc.player.getEyePos();
        return findVisibleFlowerAimPoint(eye, flowerPos, mc.player.getBlockPos()) != null;
    }

    private BlockPos findShootStandPosForFlower(Context ctx, BlockPos flowerPos) {
        if (ctx == null || flowerPos == null || mc.world == null || mc.player == null) {
            return null;
        }

        long now = System.currentTimeMillis();

        if (cachedShootStandPos != null
                && cachedShootFlowerPos != null
                && cachedShootFlowerPos.equals(flowerPos)
                && now < cachedShootStandUntilAt
                && ctx.isInsideAreaMargin(cachedShootStandPos, 3)
                && isStandablePos(cachedShootStandPos)) {

            Vec3d eyeCached = new Vec3d(cachedShootStandPos.getX() + 0.5D, cachedShootStandPos.getY() + 1.62D, cachedShootStandPos.getZ() + 0.5D);
            Vec3d aimCached = findVisibleFlowerAimPoint(eyeCached, flowerPos, cachedShootStandPos);

            if (aimCached != null && eyeCached.squaredDistanceTo(aimCached) <= BOW_RANGE_SQ) {
                return cachedShootStandPos.toImmutable();
            }

            invalidateShootStandCache(flowerPos);
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 14; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -3; dy <= 4; dy++) {
                        BlockPos p = new BlockPos(flowerPos.getX() + dx, flowerPos.getY() + dy, flowerPos.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 3)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }

                        Vec3d eye = new Vec3d(p.getX() + 0.5D, p.getY() + 1.62D, p.getZ() + 0.5D);
                        Vec3d aim = findVisibleFlowerAimPoint(eye, flowerPos, p);
                        if (aim == null) {
                            continue;
                        }

                        double distSq = eye.squaredDistanceTo(aim);
                        if (distSq > BOW_RANGE_SQ) {
                            continue;
                        }

                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double score = dPlayer + distSq * 0.02D;

                        if (trackedPathGoal != null && trackedPathGoal.equals(p) && trackedStuckRetries > 0) {
                            score += 25.0D + trackedStuckRetries * 12.0D;
                        }

                        if (score < bestScore) {
                            bestScore = score;
                            best = p.toImmutable();
                        }
                    }
                }
            }

            if (best != null) {
                cachedShootFlowerPos = flowerPos.toImmutable();
                cachedShootStandPos = best.toImmutable();
                cachedShootStandUntilAt = now + 3500L;
                return best;
            }
        }

        return null;
    }

    private boolean isLinePassableForArrow(Vec3d from, Vec3d to, BlockPos targetFlower, BlockPos standPos) {
        if (from == null || to == null || mc.world == null) {
            return false;
        }

        try {
            RaycastContext ctx = new RaycastContext(
                    from,
                    to,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            );
            HitResult hit = mc.world.raycast(ctx);

            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
                BlockPos hp = bhr.getBlockPos();

                boolean targetHit = targetFlower != null && hp.equals(targetFlower);
                boolean standHit = standPos != null && (hp.equals(standPos) || hp.equals(standPos.up()));

                if (!targetHit && !standHit) {
                    return false;
                }
            }
        } catch (Throwable ignored) {
        }

        int steps = 72;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3d p = from.lerp(to, t);
            BlockPos bp = BlockPos.ofFloored(p);

            if (targetFlower != null && bp.equals(targetFlower)) {
                continue;
            }

            if (standPos != null && (bp.equals(standPos) || bp.equals(standPos.up()))) {
                continue;
            }

            BlockState state = mc.world.getBlockState(bp);
            if (!isPassableForArrowLine(state, bp)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPassableForArrowLine(BlockState state, BlockPos pos) {
        if (state == null || pos == null || mc.world == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        if (isWalkThroughCrop(state)) {
            return true;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Vec3d findVisibleFlowerAimPoint(Vec3d fromEye, BlockPos flowerPos, BlockPos standPos) {
        if (fromEye == null || flowerPos == null) {
            return null;
        }

        Vec3d best = null;
        double bestScore = Double.MAX_VALUE;

        for (Vec3d aim : getFlowerAimCandidates(flowerPos)) {
            if (fromEye.squaredDistanceTo(aim) > BOW_RANGE_SQ) {
                continue;
            }

            if (!isLinePassableForArrow(fromEye, aim, flowerPos, standPos)) {
                continue;
            }

            double score = aim.squaredDistanceTo(getFlowerAimPoint(flowerPos));
            if (score < bestScore) {
                bestScore = score;
                best = aim;
            }
        }

        return best;
    }

    private Vec3d[] getFlowerAimCandidates(BlockPos pos) {
        if (pos == null) {
            return new Vec3d[]{Vec3d.ZERO};
        }

        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();

        ArrayList<Vec3d> list = new ArrayList<>(64);

        list.add(new Vec3d(x + 0.5D, y + 0.72D, z + 0.5D));
        list.add(new Vec3d(x + 0.5D, y + 0.84D, z + 0.5D));
        list.add(new Vec3d(x + 0.5D, y + 0.60D, z + 0.5D));
        list.add(new Vec3d(x + 0.5D, y + 0.94D, z + 0.5D));

        double[] ys = {0.60D, 0.68D, 0.76D, 0.84D, 0.92D};
        double[] o1 = {0.10D, 0.18D, 0.24D};

        for (double yy : ys) {
            for (double o : o1) {
                list.add(new Vec3d(x + 0.5D - o, y + yy, z + 0.5D));
                list.add(new Vec3d(x + 0.5D + o, y + yy, z + 0.5D));
                list.add(new Vec3d(x + 0.5D, y + yy, z + 0.5D - o));
                list.add(new Vec3d(x + 0.5D, y + yy, z + 0.5D + o));
            }
        }

        double[] corners = {0.08D, 0.14D, 0.20D};
        double[] yCorners = {0.66D, 0.78D, 0.88D};

        for (double yy : yCorners) {
            for (double c : corners) {
                list.add(new Vec3d(x + 0.5D - c, y + yy, z + 0.5D - c));
                list.add(new Vec3d(x + 0.5D + c, y + yy, z + 0.5D - c));
                list.add(new Vec3d(x + 0.5D - c, y + yy, z + 0.5D + c));
                list.add(new Vec3d(x + 0.5D + c, y + yy, z + 0.5D + c));
            }
        }

        return list.toArray(new Vec3d[0]);
    }

    private boolean tryBreakStem(BlockPos plantPos) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null || plantPos == null) {
            return false;
        }

        if (!isHarvestableStem(mc.world.getBlockState(plantPos))) {
            if (plantPos.equals(breakingPlantPos)) {
                resetBreakingState(false);
            }
            return false;
        }

        selectBestAxeForPlantIfAvailable();

        Direction dir = getBestBreakDirection(plantPos);
        long now = System.currentTimeMillis();

        try {
            if (breakingPlantPos == null || !breakingPlantPos.equals(plantPos)) {
                if (now < nextActionAt) {
                    return false;
                }

                cancelBlockBreaking();

                lookAtSoft(getInteractPoint(plantPos), false);

                mc.interactionManager.attackBlock(plantPos, dir);
                mc.player.swingHand(Hand.MAIN_HAND);

                breakingPlantPos = plantPos.toImmutable();
                lastBreakSwingAt = now;
                nextActionAt = now + ACTION_INTERVAL_MS;
                return true;
            }

            lookAtSoft(getInteractPoint(plantPos), false);

            boolean progressed;
            try {
                progressed = mc.interactionManager.updateBlockBreakingProgress(plantPos, dir);
            } catch (Throwable ignored) {
                progressed = false;
            }

            if (!progressed) {
                if (now >= nextActionAt) {
                    mc.interactionManager.attackBlock(plantPos, dir);
                    progressed = true;
                    nextActionAt = now + ACTION_INTERVAL_MS;
                }
            }

            if (progressed && now - lastBreakSwingAt >= 120L) {
                mc.player.swingHand(Hand.MAIN_HAND);
                lastBreakSwingAt = now;
            }

            if (!isHarvestableStem(mc.world.getBlockState(plantPos))) {
                resetBreakingState(false);
                pickupFocusPos = (activeClusterTopPos != null ? activeClusterTopPos : plantPos).toImmutable();
                pickupFocusUntilAt = System.currentTimeMillis() + 700L;
            }

            return progressed;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasUsableBowAndArrow() {
        return (findBestBowSlot(0, 9) >= 0 || findBestBowSlot(9, 36) >= 0) && hasAnyArrow();
    }

    private boolean selectBestBowIfAvailable() {
        if (mc == null || mc.player == null) {
            return false;
        }

        int hotbarBow = findBestBowSlot(0, 9);
        if (hotbarBow >= 0) {
            mc.player.getInventory().selectedSlot = hotbarBow;
            ItemStack main = mc.player.getMainHandStack();
            return main != null && !main.isEmpty() && isBow(main);
        }

        if (mc.interactionManager == null) {
            return false;
        }

        int invBow = findBestBowSlot(9, 36);
        if (invBow < 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextInventorySwapAt) {
            return false;
        }

        int targetHotbar = findBestHotbarSlotForBow();
        if (targetHotbar < 0) {
            targetHotbar = mc.player.getInventory().selectedSlot;
            if (targetHotbar < 0 || targetHotbar > 8) {
                targetHotbar = 0;
            }
        }

        try {
            int syncId = mc.player.currentScreenHandler.syncId;
            int slotId = toScreenHandlerSlotId(invBow);
            mc.interactionManager.clickSlot(syncId, slotId, targetHotbar, SlotActionType.SWAP, mc.player);
            nextInventorySwapAt = now + INVENTORY_SWAP_INTERVAL_MS;
            mc.player.getInventory().selectedSlot = targetHotbar;

            ItemStack hotbarStack = mc.player.getInventory().getStack(targetHotbar);
            return hotbarStack != null && !hotbarStack.isEmpty() && isBow(hotbarStack);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean hasChorusFlowerForReplant() {
        return countItemInInventory(Items.CHORUS_FLOWER) > 0;
    }

    private boolean selectChorusFlowerForPlaceIfAvailable() {
        if (mc == null || mc.player == null) {
            return false;
        }

        int hotbar = findSlotByItem(Items.CHORUS_FLOWER, 0, 9);
        if (hotbar >= 0) {
            mc.player.getInventory().selectedSlot = hotbar;
            ItemStack main = mc.player.getMainHandStack();
            return main != null && !main.isEmpty() && main.isOf(Items.CHORUS_FLOWER);
        }

        if (mc.interactionManager == null) {
            return false;
        }

        int inv = findSlotByItem(Items.CHORUS_FLOWER, 9, 36);
        if (inv < 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now < nextInventorySwapAt) {
            return false;
        }

        int targetHotbar = findBestHotbarSlotForChorusFlower();
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

            ItemStack hotbarStack = mc.player.getInventory().getStack(targetHotbar);
            return hotbarStack != null && !hotbarStack.isEmpty() && hotbarStack.isOf(Items.CHORUS_FLOWER);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int findBestBowSlot(int fromInclusive, int toExclusive) {
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
            int score = getBowScore(s);
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

    private int getBowScore(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Integer.MIN_VALUE;
        }

        return isBow(stack) ? 500 : Integer.MIN_VALUE;
    }

    private boolean isBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item == Items.BOW;
    }

    private boolean hasAnyArrow() {
        if (mc.player == null) {
            return false;
        }

        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) {
                continue;
            }

            Item item = s.getItem();
            if (item == Items.ARROW || item == Items.SPECTRAL_ARROW || item == Items.TIPPED_ARROW) {
                return true;
            }
        }

        ItemStack off = mc.player.getOffHandStack();
        if (off != null && !off.isEmpty()) {
            Item item = off.getItem();
            return item == Items.ARROW || item == Items.SPECTRAL_ARROW || item == Items.TIPPED_ARROW;
        }

        return false;
    }

    private void selectBestAxeForPlantIfAvailable() {
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

        int targetHotbar = findBestHotbarSlotForAxe();
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

    private int findBestHotbarSlotForBow() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            if (isBow(s)) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            Item item = s.getItem();
            if (item == Items.CHORUS_FRUIT || item == Items.CHORUS_FLOWER) {
                continue;
            }
            return i;
        }

        return mc.player.getInventory().selectedSlot;
    }

    private int findBestHotbarSlotForAxe() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            if (getAxeScore(s) > Integer.MIN_VALUE / 2) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            Item item = s.getItem();
            if (item == Items.CHORUS_FRUIT || item == Items.CHORUS_FLOWER) {
                continue;
            }
            return i;
        }

        return mc.player.getInventory().selectedSlot;
    }

    private int findBestHotbarSlotForChorusFlower() {
        if (mc.player == null) {
            return -1;
        }

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return i;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isEmpty()) {
                return i;
            }
            if (s.isOf(Items.CHORUS_FRUIT)) {
                continue;
            }
            return i;
        }

        return mc.player.getInventory().selectedSlot;
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

    private int findSlotByItem(Item item, int fromInclusive, int toExclusive) {
        if (mc.player == null) {
            return -1;
        }

        int from = Math.max(0, fromInclusive);
        int to = Math.min(36, toExclusive);

        int best = -1;
        int count = -1;
        for (int i = from; i < to; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty() || !s.isOf(item)) {
                continue;
            }
            if (s.getCount() > count) {
                count = s.getCount();
                best = i;
            }
        }
        return best;
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

    private BlockPos findReplantApproachPos(Context ctx, BlockPos topPos, BlockPos support) {
        if (ctx == null || topPos == null || support == null || mc.world == null || mc.player == null) {
            return null;
        }

        Direction face = getReplantSupportFace(topPos, support);
        Vec3d hitVec = face == null ? null : getReplantHitVec(support, face);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int baseY = topPos.getY();

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = new BlockPos(topPos.getX() + dx, baseY + dy, topPos.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }
                        if (isUnsafeReplantStandPos(p, topPos)) {
                            continue;
                        }

                        Vec3d eye = new Vec3d(p.getX() + 0.5D, p.getY() + 1.62D, p.getZ() + 0.5D);
                        if (hitVec != null && eye.squaredDistanceTo(hitVec) > BLOCK_REACH_SQ) {
                            continue;
                        }

                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double dTarget = Vec3d.ofCenter(p).squaredDistanceTo(Vec3d.ofCenter(topPos));
                        double score = dPlayer + dTarget * 0.14D;

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

    private boolean isUnsafeReplantStandPos(BlockPos standPos, BlockPos placePos) {
        if (standPos == null || placePos == null) {
            return true;
        }

        if (standPos.equals(placePos)) {
            return true;
        }

        if (standPos.getX() == placePos.getX() && standPos.getZ() == placePos.getZ() && Math.abs(standPos.getY() - placePos.getY()) <= 1) {
            return true;
        }

        return false;
    }

    private BlockPos findFallbackGoalNearBlock(Context ctx, BlockPos pos) {
        if (ctx == null || pos == null || mc.world == null) {
            return pos;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos p = pos.offset(d);
            if (!ctx.isInsideAreaMargin(p, 2)) {
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

        return best != null ? best : pos;
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

        if (allowCropFeet && (state.isOf(Blocks.CHORUS_FLOWER) || state.isOf(Blocks.CHORUS_PLANT))) {
            return false;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
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
                || state.isOf(Blocks.SUGAR_CANE);
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

    private Vec3d getFlowerAimPoint(BlockPos pos) {
        if (pos == null) {
            return Vec3d.ZERO;
        }
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.72D, pos.getZ() + 0.5D);
    }

    private Vec3d getInteractPoint(BlockPos pos) {
        if (pos == null || mc.world == null) {
            return pos == null ? Vec3d.ZERO : Vec3d.ofCenter(pos);
        }

        BlockState state = mc.world.getBlockState(pos);

        if (state.isOf(Blocks.CHORUS_FLOWER)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.62D, pos.getZ() + 0.5D);
        }

        if (state.isOf(Blocks.CHORUS_PLANT)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.58D, pos.getZ() + 0.5D);
        }

        if (isWalkThroughCrop(state)) {
            return new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.45D, pos.getZ() + 0.5D);
        }

        return Vec3d.ofCenter(pos);
    }

    private void lookAtSoft(Vec3d target, boolean softer) {
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
        float humanYaw = (float) (Math.sin(phase) * (softer ? 0.22D : 0.42D)) * humanScale;
        float humanPitch = (float) (Math.cos(phase * 0.91D + 1.37D) * (softer ? 0.12D : 0.24D)) * humanScale;

        targetYaw = currentYaw + MathHelper.wrapDegrees((targetYaw + humanYaw) - currentYaw);
        targetPitch = MathHelper.clamp(targetPitch + humanPitch, -89.0F, 89.0F);

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;

        float yawStep = MathHelper.clamp((softer ? 1.8F : 2.6F) + Math.abs(yawDelta) * (softer ? 0.26F : 0.42F), softer ? 1.8F : 2.6F, softer ? 9.5F : 15.5F);
        float pitchStep = MathHelper.clamp((softer ? 1.6F : 2.1F) + Math.abs(pitchDelta) * (softer ? 0.22F : 0.36F), softer ? 1.6F : 2.1F, softer ? 7.5F : 11.0F);

        float newYaw = approachAngle(currentYaw, targetYaw, yawStep);
        float newPitch = approachLinear(currentPitch, targetPitch, pitchStep);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -89.0F, 89.0F));
    }

    private boolean isCrosshairCloseTo(BlockPos pos, float maxYawDiff, float maxPitchDiff) {
        if (mc.player == null || pos == null) {
            return false;
        }
        return isCrosshairCloseTo(getFlowerAimPoint(pos), maxYawDiff, maxPitchDiff);
    }

    private boolean isCrosshairCloseTo(Vec3d target, float maxYawDiff, float maxPitchDiff) {
        if (mc.player == null || target == null) {
            return false;
        }

        Vec3d eye = mc.player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float wantYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float wantPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horiz))));

        float dyaw = Math.abs(MathHelper.wrapDegrees(wantYaw - mc.player.getYaw()));
        float dpitch = Math.abs(wantPitch - mc.player.getPitch());

        return dyaw <= maxYawDiff && dpitch <= maxPitchDiff;
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

    private void resetBreakingState(boolean cancel) {
        breakingPlantPos = null;
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

    private void releaseBowShotNow() {
        setUseKeyPressed(false);
        stopUsingItemSafe();
    }

    private void stopBowPull(boolean keepRetryDelay) {
        boolean wasUsing = false;
        try {
            wasUsing = mc != null && mc.player != null && mc.player.isUsingItem();
        } catch (Throwable ignored) {
        }

        bowPulling = false;
        bowPullStartedAt = 0L;
        bowTargetFlowerPos = null;

        setUseKeyPressed(false);

        if (wasUsing) {
            stopUsingItemSafe();
        }

        if (!keepRetryDelay) {
            long now = System.currentTimeMillis();
            if (bowRetryAt < now) {
                bowRetryAt = now + 60L;
            }
        }
    }

    private void setUseKeyPressed(boolean pressed) {
        try {
            if (mc != null && mc.options != null) {
                mc.options.useKey.setPressed(pressed);
            }
        } catch (Throwable ignored) {
        }
    }

    private void stopUsingItemSafe() {
        try {
            if (mc == null || mc.player == null) {
                return;
            }

            if (mc.interactionManager != null) {
                try {
                    Method m0 = mc.interactionManager.getClass().getMethod("stopUsingItem");
                    m0.invoke(mc.interactionManager);
                    return;
                } catch (Throwable ignored) {
                }

                try {
                    for (Method m : mc.interactionManager.getClass().getMethods()) {
                        if (!"stopUsingItem".equals(m.getName())) continue;
                        Class<?>[] p = m.getParameterTypes();
                        if (p.length == 1 && p[0].isAssignableFrom(mc.player.getClass())) {
                            m.invoke(mc.interactionManager, mc.player);
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            try {
                mc.player.stopUsingItem();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseUseKey() {
        setUseKeyPressed(false);
    }

    private void resetTransientActions() {
        resetBreakingState(true);
        stopBowPull(false);
        resetTrackedGoal();
    }

    private boolean isRelevantChorusDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.isOf(Items.CHORUS_FRUIT) || stack.isOf(Items.CHORUS_FLOWER);
    }

    private boolean isHarvestableFlower(BlockState state) {
        return state != null && state.isOf(Blocks.CHORUS_FLOWER);
    }

    private boolean isHarvestableStem(BlockState state) {
        return state != null && state.isOf(Blocks.CHORUS_PLANT);
    }

    private boolean isValidChorusSupportForFlower(BlockState state) {
        if (state == null) {
            return false;
        }
        return state.isOf(Blocks.CHORUS_PLANT) || state.isOf(Blocks.END_STONE);
    }

    private void setTargetFlowerLocked(BlockPos pos, long now) {
        targetFlower = pos == null ? null : pos.toImmutable();
        targetLockUntilAt = pos == null ? 0L : now + TARGET_STICK_MS;
    }

    private boolean isFlowerTargetSelectionLocked(Context ctx, long now) {
        if (targetFlower == null) {
            return false;
        }
        if (now >= targetLockUntilAt) {
            return false;
        }
        if (ctx == null) {
            return false;
        }
        if (!isValidFlowerTarget(ctx, targetFlower)) {
            return false;
        }
        if (trackedPathGoal != null && targetFlower.equals(trackedPathGoal) && trackedNoProgressSinceAt > 0L) {
            if (now - trackedNoProgressSinceAt >= 900L) {
                return false;
            }
        }
        return true;
    }

    private boolean hasMinClusterHeightForHarvest(BlockPos flowerPos) {
        if (flowerPos == null || mc.world == null) {
            return false;
        }

        ensureClusterCachesFresh();

        long key = flowerPos.asLong();
        Boolean cached = minHeightCache.get(key);
        if (cached != null) {
            return cached;
        }

        BlockPos root = findClusterRootReplantPos(flowerPos);
        boolean ok = false;

        if (root != null) {
            int height = flowerPos.getY() - root.getY() + 1;
            ok = height >= MIN_CHORUS_CLUSTER_HEIGHT;
        }

        minHeightCache.put(key, ok);
        return ok;
    }

    private boolean tryPostReplantRetreat(Context ctx) {
        if (ctx == null || mc.player == null || activeClusterReplantPos == null) {
            return false;
        }

        if (pickupFocusPos == null) {
            return false;
        }

        Vec3d replantCenter = Vec3d.ofCenter(activeClusterReplantPos);
        if (mc.player.getPos().squaredDistanceTo(replantCenter) > (2.45D * 2.45D)) {
            return false;
        }

        BlockPos retreat = findRetreatStandPosNear(ctx, activeClusterReplantPos, 2, 5);
        if (retreat == null) {
            pickupFocusPos = null;
            pickupFocusUntilAt = 0L;
            return false;
        }

        if (trackedPathGoal != null && trackedPathGoal.equals(retreat)) {
            pickupFocusPos = null;
            pickupFocusUntilAt = 0L;
            return false;
        }

        pathToGoalTracked(ctx, retreat);
        pickupFocusPos = null;
        pickupFocusUntilAt = 0L;
        return true;
    }

    private BlockPos findRetreatStandPosNear(Context ctx, BlockPos anchor, int minRing, int maxRing) {
        if (ctx == null || anchor == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int baseY = anchor.getY();

        for (int r = Math.max(1, minRing); r <= Math.max(minRing, maxRing); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, baseY + dy, anchor.getZ() + dz);

                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }
                        if (isUnsafeReplantStandPos(p, anchor)) {
                            continue;
                        }

                        double dAnchor = Vec3d.ofCenter(p).squaredDistanceTo(Vec3d.ofCenter(anchor));
                        if (dAnchor < 3.0D) {
                            continue;
                        }

                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double score = dPlayer + dAnchor * 0.08D;

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
}