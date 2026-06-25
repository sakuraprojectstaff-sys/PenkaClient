package fun.rich.features.impl.misc;

import fun.rich.events.render.WorldRenderEvent;
import fun.rich.utils.display.geometry.Render3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmDiamond {

    static final long AREA_SCAN_INTERVAL_MS = 220L;
    static final long IDLE_ORE_PROBE_INTERVAL_MS = 180L;

    static final long TARGET_REPATH_INTERVAL_MS = 700L;
    static final long BREAK_PROGRESS_INTERVAL_MS = 32L;
    static final long ATTACK_START_PULSE_MS = 220L;
    static final long SWING_INTERVAL_MS = 160L;
    static final long AIM_POINT_REFRESH_MS = 165L;
    static final long ATTACK_HOLD_AFTER_ALIGN_MS = 180L;

    static final long BARITONE_FILTER_INTERVAL_MS = 120L;
    static final long BARITONE_RESTART_INTERVAL_MS = 700L;
    static final long BARITONE_STUCK_TIMEOUT_MS = 3200L;
    static final long BARITONE_FALLBACK_HOLD_MS = 2400L;
    static final long RETURN_TO_BARITONE_TRY_MS = 1800L;

    static final long WARP_WAIT_MS = 8000L;
    static final long IDLE_ANTI_AFK_INTERVAL_MS = 60_000L;

    static final long ANTI_AFK_STEP_TIMEOUT_MS = 950L;
    static final long ANTI_AFK_RETURN_TIMEOUT_MS = 950L;
    static final long ANTI_AFK_STAGE_PAUSE_MS = 120L;
    static final double ANTI_AFK_OUT_DIST = 1.65D;
    static final double ANTI_AFK_RETURN_DIST = 0.38D;

    static final long ANTI_AFK_TRASH_SWEEP_INTERVAL_MS = 1200L;

    static final double MINE_REACH_SQ = 5.2D * 5.2D;

    static final double MANUAL_BREAK_CLOSE_DIST = 2.65D;
    static final double MANUAL_BREAK_CLOSE_DIST_SQ = MANUAL_BREAK_CLOSE_DIST * MANUAL_BREAK_CLOSE_DIST;
    static final double MANUAL_BREAK_CLOSE_HORIZ = 2.05D;
    static final double MANUAL_BREAK_CLOSE_HORIZ_SQ = MANUAL_BREAK_CLOSE_HORIZ * MANUAL_BREAK_CLOSE_HORIZ;

    static final double ROT_ALIGN_HOLD_DEG = 16.0D;
    static final double ROT_ALIGN_PULSE_DEG = 10.2D;
    static final double ROT_RELEASE_DEG = 23.0D;

    static final int MAX_SCAN_VOLUME_SOFT = 900_000;
    static final int SOFT_SCAN_RADIUS_XZ = 48;
    static final int SOFT_SCAN_RADIUS_Y = 24;

    static final long RENDER_CACHE_UPDATE_MS = 350L;
    static final int RENDER_SOFT_RADIUS_XZ = 30;
    static final int RENDER_SOFT_RADIUS_Y = 16;
    static final int RENDER_MAX_ORES = 160;

    static final long MANUAL_BREAK_STALL_TIMEOUT_MS = 450L;
    static final long NEAR_UPPER_ORE_CHECK_INTERVAL_MS = 70L;

    static final long POST_WARP_TRASH_DELAY_MS = 1200L;
    static final long POST_WARP_TRASH_RECHECK_MS = 70L;
    static final int POST_WARP_TRASH_DROP_BURST = 8;

    final MinecraftClient mc = MinecraftClient.getInstance();

    BlockPos targetOre;
    BlockPos lastPathGoal;
    long lastAreaScanAt;
    long lastRepathAt;
    long lastBreakProgressAt;
    long lastAttackPulseAt;
    long lastSwingAt;
    long attackHoldUntilAt;

    boolean breaking;
    BlockPos breakingPos;
    Direction breakingSide = Direction.UP;
    Vec3d breakingAimPoint;
    long nextAimPointRefreshAt;
    long breakNoProgressSinceAt;

    enum Mode {
        BARITONE_MINE,
        DIRECT_FALLBACK
    }

    Mode mode = Mode.BARITONE_MINE;
    long forceFallbackUntilAt;
    long lastBaritoneFilterAt;
    long lastBaritoneRestartAt;
    long lastReturnToBaritoneTryAt;
    long lastNearUpperOreCheckAt;
    int baritoneFailStrikes;

    Object cachedBaritonePrimary;
    Object cachedMineProcess;
    boolean baritoneInitTried;
    boolean baritoneUnavailable;

    Vec3d baritoneStuckSamplePos;
    long baritoneStuckSampleAt;
    long baritoneNoProgressSinceAt;

    float smoothYaw;
    float smoothPitch;
    boolean smoothRotInit;
    long lastRotAt;
    float rotSpeedDegPerSec = 210.0F;
    long nextRotSpeedAt;
    float rotNoiseYaw;
    float rotNoisePitch;
    float rotNoiseYawTarget;
    float rotNoisePitchTarget;
    long nextRotNoiseAt;

    enum IdleState {
        NONE,
        WARP_WAIT,
        ANTI_AFK
    }

    IdleState idleState = IdleState.NONE;
    long idleUntilAt;
    long lastOreProbeAt;
    BlockPos idleAreaCenter;

    Vec3d antiAfkPairBase;
    int antiAfkStage;
    long antiAfkStageStartedAt;
    long antiAfkPauseUntilAt;

    boolean postWarpTrashDropPending;
    long postWarpTrashDropReadyAt;
    long nextPostWarpTrashDropAt;

    enum MoveKey {
        FORWARD,
        BACK,
        LEFT,
        RIGHT
    }

    final MoveKey[] antiAfkPattern = new MoveKey[]{
            MoveKey.FORWARD, MoveKey.BACK,
            MoveKey.LEFT, MoveKey.RIGHT,
            MoveKey.RIGHT, MoveKey.LEFT,
            MoveKey.BACK, MoveKey.FORWARD
    };

    final Map<BlockPos, Integer> renderOreCache = new LinkedHashMap<>();
    long lastRenderCacheAt;
    BlockPos lastRenderAnchor;

    public interface Context {
        void pathToWorkGoal(BlockPos goal);
        void stopWorkPathing();
        boolean isInSelectedArea(BlockPos pos);
        boolean isInsideAreaMargin(BlockPos pos, int margin);
        BlockPos getMinPoint();
        BlockPos getMaxPoint();
    }

    public void activate() {
        reset();
        applyBaritoneRestrictions(true);
    }

    public void deactivate() {
        stopBreaking();
        cancelMineProcess();
        stopMovementKeys();
        reset();
    }

    public void reset() {
        targetOre = null;
        lastPathGoal = null;
        lastAreaScanAt = 0L;
        lastRepathAt = 0L;
        lastBreakProgressAt = 0L;
        lastAttackPulseAt = 0L;
        lastSwingAt = 0L;
        attackHoldUntilAt = 0L;

        breaking = false;
        breakingPos = null;
        breakingSide = Direction.UP;
        breakingAimPoint = null;
        nextAimPointRefreshAt = 0L;
        breakNoProgressSinceAt = 0L;

        mode = Mode.BARITONE_MINE;
        forceFallbackUntilAt = 0L;
        lastBaritoneFilterAt = 0L;
        lastBaritoneRestartAt = 0L;
        lastReturnToBaritoneTryAt = 0L;
        lastNearUpperOreCheckAt = 0L;
        baritoneFailStrikes = 0;

        cachedBaritonePrimary = null;
        cachedMineProcess = null;
        baritoneInitTried = false;
        baritoneUnavailable = false;

        baritoneStuckSamplePos = null;
        baritoneStuckSampleAt = 0L;
        baritoneNoProgressSinceAt = 0L;

        smoothRotInit = false;
        smoothYaw = 0.0F;
        smoothPitch = 0.0F;
        lastRotAt = 0L;
        rotSpeedDegPerSec = 210.0F;
        nextRotSpeedAt = 0L;
        rotNoiseYaw = 0.0F;
        rotNoisePitch = 0.0F;
        rotNoiseYawTarget = 0.0F;
        rotNoisePitchTarget = 0.0F;
        nextRotNoiseAt = 0L;

        idleState = IdleState.NONE;
        idleUntilAt = 0L;
        lastOreProbeAt = 0L;
        idleAreaCenter = null;

        antiAfkPairBase = null;
        antiAfkStage = 0;
        antiAfkStageStartedAt = 0L;
        antiAfkPauseUntilAt = 0L;

        postWarpTrashDropPending = false;
        postWarpTrashDropReadyAt = 0L;
        nextPostWarpTrashDropAt = 0L;

        renderOreCache.clear();
        lastRenderCacheAt = 0L;
        lastRenderAnchor = null;

        releaseAttackKey();
        releaseUseKey();
        stopMovementKeys();
    }

    public void tick(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            stopBreaking();
            cancelMineProcess();
            stopMovementKeys();
            return;
        }

        if (!hasArea(ctx)) {
            stopBreaking();
            cancelMineProcess();
            stopMovementKeys();
            targetOre = null;
            lastPathGoal = null;
            idleState = IdleState.NONE;
            renderOreCache.clear();
            return;
        }

        forceNoBlockUse();
        ensurePickaxeInHotbar();

        boolean playerInArea = ctx.isInSelectedArea(mc.player.getBlockPos());
        applyBaritoneRestrictions(playerInArea);

        if (idleState != IdleState.NONE) {
            tickPostWarpTrashDrop();

            if (tryExitIdleIfOreAppeared(ctx)) {
                return;
            }
            tickIdleState(ctx);
            return;
        }

        if (mode == Mode.BARITONE_MINE) {
            if (!tickBaritoneMode(ctx, playerInArea)) {
                tickFallbackMode(ctx, playerInArea);
            }
            return;
        }

        tickFallbackMode(ctx, playerInArea);

        long now = System.currentTimeMillis();
        if (now >= forceFallbackUntilAt && now - lastReturnToBaritoneTryAt >= RETURN_TO_BARITONE_TRY_MS) {
            lastReturnToBaritoneTryAt = now;
            if (baritoneAvailable() && !hasManualTargetInReach() && playerInArea) {
                switchToBaritoneMode();
            }
        }
    }

    public void onWorldRender(WorldRenderEvent e, Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return;
        }

        if (!hasArea(ctx)) {
            renderOreCache.clear();
            return;
        }

        updateRenderCache(ctx);
        renderOres(ctx);
        renderCurrentTarget();
        renderIdleCenter(ctx);
    }

    public boolean shouldGoToStorageNow() {
        return false;
    }

    public boolean hasAnyCultureToDeposit() {
        return false;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        return false;
    }

    private boolean tickBaritoneMode(Context ctx, boolean playerInArea) {
        stopBreaking();
        stopMovementKeys();

        if (!playerInArea) {
            cancelMineProcess();
            switchToFallbackMode(BARITONE_FALLBACK_HOLD_MS);
            return false;
        }

        if (!baritoneAvailable()) {
            switchToFallbackMode(BARITONE_FALLBACK_HOLD_MS);
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - lastNearUpperOreCheckAt >= NEAR_UPPER_ORE_CHECK_INTERVAL_MS) {
            lastNearUpperOreCheckAt = now;

            BlockPos urgentUpper = findPriorityNearbyUpperOre(ctx);
            if (urgentUpper != null && !canMineBlockNow(urgentUpper)) {
                cancelMineProcess();
                switchToFallbackModeWithTarget(urgentUpper, 4500L);
                return false;
            }
        }

        if (now - lastBaritoneRestartAt >= BARITONE_RESTART_INTERVAL_MS) {
            ensureMineProcessRunning();
            lastBaritoneRestartAt = now;
        }

        if (now - lastBaritoneFilterAt >= BARITONE_FILTER_INTERVAL_MS) {
            filterKnownOreLocationsByArea(ctx);
            lastBaritoneFilterAt = now;
        }

        if (isBaritonePillaring()) {
            cancelMineProcess();
            BlockPos urgentUpper = findPriorityNearbyUpperOre(ctx);
            if (urgentUpper != null) {
                switchToFallbackModeWithTarget(urgentUpper, 5000L);
            } else {
                switchToFallbackMode(BARITONE_FALLBACK_HOLD_MS);
            }
            return false;
        }

        if (tickBaritoneStuckMonitor()) {
            cancelMineProcess();
            switchToFallbackMode(BARITONE_FALLBACK_HOLD_MS);
            return false;
        }

        boolean mineActive = isMineProcessActive();
        int known = getKnownOreCount();

        if (!mineActive) {
            if (known > 0) {
                baritoneFailStrikes++;
                if (baritoneFailStrikes >= 3) {
                    switchToFallbackMode(BARITONE_FALLBACK_HOLD_MS);
                    return false;
                }
            } else {
                baritoneFailStrikes = 0;
                BlockPos localUpper = findPriorityNearbyUpperOre(ctx);
                if (localUpper != null) {
                    switchToFallbackModeWithTarget(localUpper, 4500L);
                    return false;
                }
                BlockPos local = findNearestDiamondOre(ctx, false);
                if (local != null) {
                    switchToFallbackMode(1200L);
                    return false;
                }
                startWarpWait(ctx);
                return true;
            }
        } else {
            baritoneFailStrikes = 0;
            if (known == 0) {
                BlockPos localUpper = findPriorityNearbyUpperOre(ctx);
                if (localUpper != null) {
                    cancelMineProcess();
                    switchToFallbackModeWithTarget(localUpper, 4500L);
                    return false;
                }

                BlockPos local = findNearestDiamondOre(ctx, false);
                if (local == null) {
                    cancelMineProcess();
                    startWarpWait(ctx);
                    return true;
                }
            }
        }

        return true;
    }

    private BlockPos findPriorityNearbyUpperOre(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos p = mc.player.getBlockPos();
        Vec3d playerPos = mc.player.getPos();

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        if (targetOre != null && ctx.isInSelectedArea(targetOre) && isDiamondOre(targetOre) && isNearbyUpperOreCandidate(targetOre, p, playerPos)) {
            best = targetOre.toImmutable();
            bestScore = playerPos.squaredDistanceTo(Vec3d.ofCenter(best));
        }

        List<BlockPos> known = getKnownOreLocations();
        if (known != null) {
            for (BlockPos ore : known) {
                if (ore == null || !ctx.isInSelectedArea(ore) || !isDiamondOre(ore)) {
                    continue;
                }
                if (!isNearbyUpperOreCandidate(ore, p, playerPos)) {
                    continue;
                }
                double score = playerPos.squaredDistanceTo(Vec3d.ofCenter(ore));
                if (best == null || score < bestScore) {
                    best = ore.toImmutable();
                    bestScore = score;
                }
            }
        }

        for (int dy = 1; dy <= 4; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos ore = new BlockPos(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                    if (!ctx.isInSelectedArea(ore) || !isDiamondOre(ore)) {
                        continue;
                    }
                    if (!isNearbyUpperOreCandidate(ore, p, playerPos)) {
                        continue;
                    }
                    double score = playerPos.squaredDistanceTo(Vec3d.ofCenter(ore));
                    if (best == null || score < bestScore) {
                        best = ore.toImmutable();
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private boolean isNearbyUpperOreCandidate(BlockPos ore, BlockPos playerBlock, Vec3d playerPos) {
        if (ore == null || playerBlock == null || playerPos == null) {
            return false;
        }

        int dy = ore.getY() - playerBlock.getY();
        if (dy < 1 || dy > 4) {
            return false;
        }

        double dx = (ore.getX() + 0.5D) - playerPos.x;
        double dz = (ore.getZ() + 0.5D) - playerPos.z;
        double horizSq = dx * dx + dz * dz;

        return horizSq <= (3.0D * 3.0D);
    }

    private void tickFallbackMode(Context ctx, boolean playerInArea) {
        stopMovementKeys();

        if (shouldRescanTarget(ctx)) {
            BlockPos upper = findPriorityNearbyUpperOre(ctx);
            targetOre = upper != null ? upper : findNearestDiamondOre(ctx, false);
            lastAreaScanAt = System.currentTimeMillis();
            if (targetOre == null) {
                stopBreaking();
                ctx.stopWorkPathing();
                lastPathGoal = null;
                startWarpWait(ctx);
                return;
            }
        }

        if (targetOre == null) {
            return;
        }

        if (!ctx.isInSelectedArea(targetOre) || !isDiamondOre(targetOre)) {
            targetOre = null;
            stopBreaking();
            return;
        }

        if (!playerInArea) {
            stopBreaking();
            long now = System.currentTimeMillis();
            BlockPos center = getAreaCenter(ctx);
            if (center != null && (lastPathGoal == null || !lastPathGoal.equals(center) || now - lastRepathAt >= TARGET_REPATH_INTERVAL_MS)) {
                lastPathGoal = center.toImmutable();
                lastRepathAt = now;
                ctx.pathToWorkGoal(center);
            }
            return;
        }

        BlockPos desiredBreakPos = resolveFallbackBreakTarget(ctx, targetOre);
        BlockPos pathIntent = desiredBreakPos != null ? desiredBreakPos : targetOre;

        if (!isCloseEnoughForManualBreak(pathIntent)) {
            stopBreaking();
            long now = System.currentTimeMillis();

            BlockPos approach = chooseFallbackPathGoal(ctx, desiredBreakPos, targetOre);
            if (approach == null) {
                targetOre = null;
                lastAreaScanAt = 0L;
                ctx.stopWorkPathing();
                lastPathGoal = null;
                return;
            }

            if (lastPathGoal == null || !lastPathGoal.equals(approach) || now - lastRepathAt >= TARGET_REPATH_INTERVAL_MS) {
                lastPathGoal = approach.toImmutable();
                lastRepathAt = now;
                ctx.pathToWorkGoal(approach);
            }
            return;
        }

        if (desiredBreakPos == null) {
            stopBreaking();

            long now = System.currentTimeMillis();
            BlockPos approach = chooseFallbackPathGoal(ctx, null, targetOre);
            if (approach != null && (lastPathGoal == null || !lastPathGoal.equals(approach) || now - lastRepathAt >= TARGET_REPATH_INTERVAL_MS)) {
                lastPathGoal = approach.toImmutable();
                lastRepathAt = now;
                ctx.pathToWorkGoal(approach);
                return;
            }

            targetOre = null;
            lastAreaScanAt = 0L;
            ctx.stopWorkPathing();
            lastPathGoal = null;
            return;
        }

        ctx.stopWorkPathing();
        lastPathGoal = null;

        if (!mineBlockHumanized(desiredBreakPos)) {
            if (desiredBreakPos.equals(targetOre)) {
                targetOre = null;
            }
            stopBreaking();
        }
    }

    private BlockPos chooseFallbackPathGoal(Context ctx, BlockPos desiredBreakPos, BlockPos ore) {
        if (ctx == null || ore == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos best;

        BlockPos tunnelTarget = desiredBreakPos != null ? desiredBreakPos : ore;
        best = findLinearTunnelApproachGoal(ctx, tunnelTarget);
        if (best != null) {
            return best;
        }

        if (desiredBreakPos != null) {
            best = findBreakApproachGoal(ctx, desiredBreakPos, ore);
            if (best != null) {
                return best;
            }

            best = findLooseApproachGoal(ctx, desiredBreakPos, ore, 1, 6, true);
            if (best != null) {
                return best;
            }
        }

        best = findBreakApproachGoal(ctx, ore, ore);
        if (best != null) {
            return best;
        }

        if (isNearbyUpperOreCandidate(ore, mc.player.getBlockPos(), mc.player.getPos())) {
            best = findLooseApproachGoal(ctx, ore, ore, 1, 7, true);
            if (best != null) {
                return best;
            }
        }

        best = findLooseApproachGoal(ctx, ore, ore, 1, 8, true);
        if (best != null) {
            return best;
        }

        BlockPos center = getAreaCenter(ctx);
        if (center != null && isSimpleStandablePos(center) && ctx.isInsideAreaMargin(center, 1)) {
            return center.toImmutable();
        }

        return null;
    }

    private BlockPos findLooseApproachGoal(Context ctx, BlockPos anchor, BlockPos ore, int minR, int maxR, boolean requireProgress) {
        if (ctx == null || anchor == null || ore == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos playerBlock = mc.player.getBlockPos();
        Vec3d playerPos = mc.player.getPos();
        double playerToOreSq = playerPos.squaredDistanceTo(Vec3d.ofCenter(ore));

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = Math.max(1, minR); r <= Math.max(minR, maxR); r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);

                        if (p.equals(ore) || p.equals(anchor)) {
                            continue;
                        }

                        if (!ctx.isInsideAreaMargin(p, 1)) {
                            continue;
                        }

                        if (!isSimpleStandablePos(p)) {
                            continue;
                        }

                        if (!isTunnelApproachCandidate(p, ore)) {
                            continue;
                        }

                        Vec3d posCenter = Vec3d.ofCenter(p);
                        double toOreSq = posCenter.squaredDistanceTo(Vec3d.ofCenter(ore));

                        if (toOreSq > (6.8D * 6.8D)) {
                            continue;
                        }

                        if (requireProgress && toOreSq > playerToOreSq + 3.0D) {
                            continue;
                        }

                        Vec3d fakeEye = new Vec3d(p.getX() + 0.5D, p.getY() + 1.62D, p.getZ() + 0.5D);
                        BlockHitResult losOre = findVisibleMiningHitFrom(fakeEye, ore);
                        BlockHitResult losAnchor = anchor.equals(ore) ? losOre : findVisibleMiningHitFrom(fakeEye, anchor);

                        if (losOre == null && losAnchor == null) {
                            continue;
                        }

                        double score = 0.0D;

                        score += playerPos.squaredDistanceTo(posCenter) * 0.18D;
                        score += toOreSq * 0.82D;
                        score += Math.abs(p.getY() - playerBlock.getY()) * 0.75D;

                        if (losOre != null) {
                            score -= 14.0D;
                            score += fakeEye.squaredDistanceTo(losOre.getPos()) * 0.05D;
                        }

                        if (losAnchor != null) {
                            score -= 4.0D;
                        }

                        if (ore.getY() > playerBlock.getY() && p.getY() <= ore.getY() - 1) {
                            score -= 6.0D;
                        }

                        if (best == null || score < bestScore) {
                            best = p.toImmutable();
                            bestScore = score;
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

    private BlockPos resolveFallbackBreakTarget(Context ctx, BlockPos ore) {
        if (ctx == null || ore == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        if (!ctx.isInSelectedArea(ore) || !isDiamondOre(ore)) {
            return null;
        }

        if (canMineBlockNow(ore)) {
            return ore;
        }

        BlockPos upperCover = findNearbyUpperOreCoverBlock(ctx, ore);
        if (upperCover != null) {
            return upperCover;
        }

        BlockPos lineBlocker = findLineBlockerToOre(ctx, ore);
        if (lineBlocker != null) {
            return lineBlocker;
        }

        BlockPos adjacentCover = findAdjacentCoverBlockForOre(ctx, ore);
        if (adjacentCover != null) {
            return adjacentCover;
        }

        return null;
    }

    private BlockPos findNearbyUpperOreCoverBlock(Context ctx, BlockPos ore) {
        if (ctx == null || ore == null || mc == null || mc.player == null) {
            return null;
        }

        BlockPos pb = mc.player.getBlockPos();
        int dy = ore.getY() - pb.getY();
        if (dy < 1 || dy > 4) {
            return null;
        }

        double dx = (ore.getX() + 0.5D) - mc.player.getPos().x;
        double dz = (ore.getZ() + 0.5D) - mc.player.getPos().z;
        if (dx * dx + dz * dz > 3.0D * 3.0D) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int i = 1; i <= 3; i++) {
            BlockPos c = ore.down(i);
            if (!ctx.isInSelectedArea(c)) {
                continue;
            }
            if (!canManuallyBreakBlock(c)) {
                continue;
            }
            if (!isWithinMineReach(c)) {
                continue;
            }

            BlockHitResult hit = findVisibleMiningHit(c);
            if (hit == null) {
                continue;
            }

            double score = Vec3d.ofCenter(c).squaredDistanceTo(Vec3d.ofCenter(ore));
            score += mc.player.getEyePos().squaredDistanceTo(hit.getPos()) * 0.09D;

            if (best == null || score < bestScore) {
                best = c.toImmutable();
                bestScore = score;
            }
        }

        return best;
    }

    private BlockPos findAdjacentCoverBlockForOre(Context ctx, BlockPos ore) {
        if (ctx == null || ore == null || mc == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d oreCenter = Vec3d.ofCenter(ore);

        for (Direction d : Direction.values()) {
            BlockPos n = ore.offset(d);
            if (!ctx.isInSelectedArea(n)) {
                continue;
            }
            if (!canManuallyBreakBlock(n)) {
                continue;
            }
            if (!isWithinMineReach(n)) {
                continue;
            }

            Direction.Axis axis = d.getAxis();
            if (axis != Direction.Axis.Y && !isTunnelAlignedBreakCandidate(n, ore)) {
                continue;
            }

            BlockHitResult hit = findVisibleMiningHit(n);
            if (hit == null) {
                continue;
            }

            double score = oreCenter.squaredDistanceTo(Vec3d.ofCenter(n));
            score += mc.player.getEyePos().squaredDistanceTo(hit.getPos()) * 0.18D;

            if (axis != Direction.Axis.Y) {
                score += 12.0D;
            } else {
                score -= 2.5D;
            }

            if (best == null || score < bestScore) {
                best = n.toImmutable();
                bestScore = score;
            }
        }

        return best;
    }

    private BlockPos findLineBlockerToOre(Context ctx, BlockPos ore) {
        if (ctx == null || ore == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        double[] p = new double[]{0.34D, 0.50D, 0.66D};

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = mc.player.getEyePos();
        Vec3d oreCenter = Vec3d.ofCenter(ore);

        for (double ox : p) {
            for (double oy : p) {
                for (double oz : p) {
                    Vec3d probe = new Vec3d(ore.getX() + ox, ore.getY() + oy, ore.getZ() + oz);
                    BlockHitResult hit = raycastBetween(eye, probe);
                    if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                        continue;
                    }

                    BlockPos hp = hit.getBlockPos();
                    if (hp == null || hp.equals(ore)) {
                        continue;
                    }

                    if (!ctx.isInSelectedArea(hp)) {
                        continue;
                    }

                    if (!canManuallyBreakBlock(hp)) {
                        continue;
                    }

                    if (!isWithinMineReach(hit.getPos())) {
                        continue;
                    }

                    if (!isTunnelAlignedBreakCandidate(hp, ore)) {
                        continue;
                    }

                    double score = oreCenter.squaredDistanceTo(Vec3d.ofCenter(hp)) * 1.35D;
                    score += eye.squaredDistanceTo(hit.getPos()) * 0.12D;

                    if (best == null || score < bestScore) {
                        best = hp.toImmutable();
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private BlockPos findBreakApproachGoal(Context ctx, BlockPos block, BlockPos scoreRef) {
        if (ctx == null || block == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d scoreCenter = scoreRef == null ? Vec3d.ofCenter(block) : Vec3d.ofCenter(scoreRef);

        for (int r = 1; r <= 7; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -3; dy <= 3; dy++) {
                        BlockPos p = new BlockPos(block.getX() + dx, block.getY() + dy, block.getZ() + dz);

                        if (p.equals(block)) {
                            continue;
                        }

                        if (!ctx.isInsideAreaMargin(p, 1)) {
                            continue;
                        }

                        if (!isSimpleStandablePos(p)) {
                            continue;
                        }

                        if (!isTunnelApproachCandidate(p, block)) {
                            continue;
                        }

                        Vec3d fakePlayerPos = Vec3d.ofCenter(p);
                        Vec3d fakeEye = new Vec3d(p.getX() + 0.5D, p.getY() + 1.62D, p.getZ() + 0.5D);

                        double dCenterSq = fakePlayerPos.squaredDistanceTo(Vec3d.ofCenter(block));
                        double dxh = fakePlayerPos.x - (block.getX() + 0.5D);
                        double dzh = fakePlayerPos.z - (block.getZ() + 0.5D);
                        double horizSq = dxh * dxh + dzh * dzh;

                        if (dCenterSq > (MANUAL_BREAK_CLOSE_DIST + 0.65D) * (MANUAL_BREAK_CLOSE_DIST + 0.65D)) {
                            continue;
                        }
                        if (horizSq > (MANUAL_BREAK_CLOSE_HORIZ + 1.00D) * (MANUAL_BREAK_CLOSE_HORIZ + 1.00D)) {
                            continue;
                        }

                        BlockHitResult visible = findVisibleMiningHitFrom(fakeEye, block);
                        if (visible == null) {
                            continue;
                        }

                        double score = fakePlayerPos.squaredDistanceTo(scoreCenter) * 0.75D;
                        score += mc.player.getPos().squaredDistanceTo(fakePlayerPos) * 0.10D;
                        score += fakeEye.squaredDistanceTo(visible.getPos()) * 0.08D;
                        score += Math.abs(p.getY() - mc.player.getBlockY()) * 0.60D;

                        int manhattanXZ = Math.abs(p.getX() - block.getX()) + Math.abs(p.getZ() - block.getZ());
                        if (manhattanXZ == 1) {
                            score -= 4.0D;
                        }

                        if (best == null || score < bestScore) {
                            best = p.toImmutable();
                            bestScore = score;
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

    private boolean shouldTunnelAlongX(BlockPos from, BlockPos to) {
        if (from == null || to == null) {
            return true;
        }
        int dx = Math.abs(to.getX() - from.getX());
        int dz = Math.abs(to.getZ() - from.getZ());
        return dx >= dz;
    }

    private boolean isTunnelAlignedBreakCandidate(BlockPos candidate, BlockPos ore) {
        if (candidate == null || ore == null || mc == null || mc.player == null) {
            return false;
        }

        BlockPos playerBlock = mc.player.getBlockPos();
        if (candidate.equals(playerBlock)) {
            return false;
        }

        boolean alongX = shouldTunnelAlongX(playerBlock, ore);

        if (alongX) {
            if (candidate.getZ() != playerBlock.getZ()) {
                return false;
            }

            int dir = Integer.compare(ore.getX(), playerBlock.getX());
            if (dir != 0) {
                int candDir = Integer.compare(candidate.getX(), playerBlock.getX());
                if (candDir != 0 && candDir != dir) {
                    return false;
                }
            }
        } else {
            if (candidate.getX() != playerBlock.getX()) {
                return false;
            }

            int dir = Integer.compare(ore.getZ(), playerBlock.getZ());
            if (dir != 0) {
                int candDir = Integer.compare(candidate.getZ(), playerBlock.getZ());
                if (candDir != 0 && candDir != dir) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isTunnelApproachCandidate(BlockPos pos, BlockPos target) {
        if (pos == null || target == null || mc == null || mc.player == null) {
            return false;
        }

        BlockPos playerBlock = mc.player.getBlockPos();
        boolean alongX = shouldTunnelAlongX(playerBlock, target);

        if (alongX) {
            if (pos.getZ() != playerBlock.getZ()) {
                return false;
            }

            int dir = Integer.compare(target.getX(), playerBlock.getX());
            if (dir != 0) {
                int posDir = Integer.compare(pos.getX(), playerBlock.getX());
                if (posDir != 0 && posDir != dir) {
                    return false;
                }
            }
        } else {
            if (pos.getX() != playerBlock.getX()) {
                return false;
            }

            int dir = Integer.compare(target.getZ(), playerBlock.getZ());
            if (dir != 0) {
                int posDir = Integer.compare(pos.getZ(), playerBlock.getZ());
                if (posDir != 0 && posDir != dir) {
                    return false;
                }
            }
        }

        return true;
    }

    private BlockPos findLinearTunnelApproachGoal(Context ctx, BlockPos target) {
        if (ctx == null || target == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockPos playerBlock = mc.player.getBlockPos();

        boolean alongX = shouldTunnelAlongX(playerBlock, target);
        int dir = alongX
                ? Integer.compare(target.getX(), playerBlock.getX())
                : Integer.compare(target.getZ(), playerBlock.getZ());

        if (dir == 0) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d targetCenter = Vec3d.ofCenter(target);

        for (int step = 1; step <= 8; step++) {
            int baseX = alongX ? playerBlock.getX() + dir * step : playerBlock.getX();
            int baseZ = alongX ? playerBlock.getZ() : playerBlock.getZ() + dir * step;

            for (int dy = -3; dy <= 3; dy++) {
                BlockPos p = new BlockPos(baseX, playerBlock.getY() + dy, baseZ);

                if (!ctx.isInsideAreaMargin(p, 1)) {
                    continue;
                }

                if (!isSimpleStandablePos(p)) {
                    continue;
                }

                double score = 0.0D;
                score += step * 10.0D;
                score += Math.abs(dy) * 3.0D;
                score += Vec3d.ofCenter(p).squaredDistanceTo(targetCenter) * 0.25D;

                if (best == null || score < bestScore) {
                    best = p.toImmutable();
                    bestScore = score;
                }
            }

            if (best != null) {
                return best;
            }
        }

        return null;
    }

    private boolean isCloseEnoughForManualBreak(BlockPos pos) {
        if (pos == null || mc == null || mc.player == null) {
            return false;
        }

        if (!isWithinMineReach(pos)) {
            return false;
        }

        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d playerPos = mc.player.getPos();

        if (playerPos.squaredDistanceTo(center) > MANUAL_BREAK_CLOSE_DIST_SQ) {
            return false;
        }

        double dx = playerPos.x - center.x;
        double dz = playerPos.z - center.z;
        if (dx * dx + dz * dz > MANUAL_BREAK_CLOSE_HORIZ_SQ) {
            return false;
        }

        BlockHitResult hit = findVisibleMiningHit(pos);
        return hit != null && isWithinMineReach(hit.getPos());
    }

    private void startWarpWait(Context ctx) {
        stopBreaking();
        stopMovementKeys();
        cancelMineProcess();
        targetOre = null;
        lastPathGoal = null;

        idleAreaCenter = getAreaCenter(ctx);
        idleState = IdleState.WARP_WAIT;
        long now = System.currentTimeMillis();
        idleUntilAt = now + WARP_WAIT_MS;
        lastOreProbeAt = 0L;

        antiAfkPairBase = null;
        antiAfkStage = 0;
        antiAfkStageStartedAt = 0L;
        antiAfkPauseUntilAt = 0L;

        postWarpTrashDropPending = true;
        postWarpTrashDropReadyAt = now + POST_WARP_TRASH_DELAY_MS;
        nextPostWarpTrashDropAt = 0L;

        sendWarpMineCommand();
    }

    private void tickPostWarpTrashDrop() {
        if (!postWarpTrashDropPending || mc == null || mc.player == null || mc.world == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < postWarpTrashDropReadyAt || now < nextPostWarpTrashDropAt) {
            return;
        }

        int dropped = dropPostWarpTrashBurst(POST_WARP_TRASH_DROP_BURST);
        if (dropped > 0) {
            nextPostWarpTrashDropAt = now + POST_WARP_TRASH_RECHECK_MS;
            return;
        }

        postWarpTrashDropPending = false;
        nextPostWarpTrashDropAt = idleState == IdleState.ANTI_AFK ? (now + ANTI_AFK_TRASH_SWEEP_INTERVAL_MS) : 0L;
    }

    private void tickAntiAfkTrashSweep(long now) {
        if (idleState != IdleState.ANTI_AFK) {
            return;
        }

        if (!postWarpTrashDropPending && now >= nextPostWarpTrashDropAt) {
            postWarpTrashDropPending = true;
            postWarpTrashDropReadyAt = now;
        }

        tickPostWarpTrashDrop();
    }

    private int dropPostWarpTrashBurst(int maxDrops) {
        if (maxDrops <= 0 || mc == null || mc.player == null || mc.interactionManager == null) {
            return 0;
        }

        PlayerInventory inv = mc.player.getInventory();
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (inv == null || handler == null) {
            return 0;
        }

        int dropped = 0;

        for (int invSlot = 9; invSlot < 36 && dropped < maxDrops; invSlot++) {
            if (!isPostWarpTrashStack(inv.getStack(invSlot))) {
                continue;
            }
            if (throwPlayerInventorySlot(invSlot, inv, handler)) {
                dropped++;
            }
        }

        for (int invSlot = 0; invSlot < 9 && dropped < maxDrops; invSlot++) {
            if (invSlot == inv.selectedSlot) {
                continue;
            }
            if (!isPostWarpTrashStack(inv.getStack(invSlot))) {
                continue;
            }
            if (throwPlayerInventorySlot(invSlot, inv, handler)) {
                dropped++;
            }
        }

        return dropped;
    }

    private boolean isPostWarpTrashStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        Item item;
        try {
            item = stack.getItem();
        } catch (Throwable t) {
            return false;
        }

        if (item == null) {
            return false;
        }

        return item == Items.STONE
                || item == Items.COBBLESTONE
                || item == Items.GRANITE
                || item == Items.RAW_IRON
                || item == Items.RAW_GOLD
                || item == Items.IRON_ORE
                || item == Items.DEEPSLATE_IRON_ORE
                || item == Items.GOLD_ORE
                || item == Items.DEEPSLATE_GOLD_ORE
                || item == Items.NETHER_GOLD_ORE;
    }

    private boolean throwPlayerInventorySlot(int invSlot, PlayerInventory inv, ScreenHandler handler) {
        if (mc == null || mc.player == null || mc.interactionManager == null || inv == null || handler == null) {
            return false;
        }

        if (invSlot < 0 || invSlot >= 36) {
            return false;
        }

        if (invSlot == inv.selectedSlot) {
            return false;
        }

        int slotId = findHandlerSlotId(handler, inv, invSlot);
        if (slotId < 0) {
            return false;
        }

        try {
            mc.interactionManager.clickSlot(handler.syncId, slotId, 1, SlotActionType.THROW, mc.player);
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private int findHandlerSlotId(ScreenHandler handler, PlayerInventory inv, int invIndex) {
        if (handler == null || inv == null) {
            return -1;
        }

        try {
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot s = handler.slots.get(i);
                if (s == null) {
                    continue;
                }
                if (s.inventory == inv && s.getIndex() == invIndex) {
                    return i;
                }
            }
        } catch (Throwable ignored) {
        }

        return -1;
    }

    private boolean tryExitIdleIfOreAppeared(Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastOreProbeAt < IDLE_ORE_PROBE_INTERVAL_MS) {
            return false;
        }
        lastOreProbeAt = now;

        BlockPos upper = findPriorityNearbyUpperOre(ctx);
        BlockPos ore = upper != null ? upper : findNearestDiamondOre(ctx, true);
        if (ore == null) {
            return false;
        }

        idleState = IdleState.NONE;
        idleUntilAt = 0L;
        antiAfkPairBase = null;
        antiAfkStage = 0;
        antiAfkStageStartedAt = 0L;
        antiAfkPauseUntilAt = 0L;
        stopMovementKeys();

        targetOre = ore.toImmutable();
        lastAreaScanAt = now;
        lastPathGoal = null;

        if (ctx.isInSelectedArea(mc.player.getBlockPos()) && baritoneAvailable() && upper == null) {
            switchToBaritoneMode();
        } else {
            mode = Mode.DIRECT_FALLBACK;
            forceFallbackUntilAt = now + (upper != null ? 4500L : 2500L);
        }

        return true;
    }

    private void tickIdleState(Context ctx) {
        stopBreaking();
        releaseAttackKey();
        forceNoBlockUse();

        if (idleState == IdleState.WARP_WAIT) {
            long now = System.currentTimeMillis();
            if (now >= idleUntilAt) {
                idleState = IdleState.ANTI_AFK;
                antiAfkStage = 0;
                antiAfkPairBase = mc.player != null ? mc.player.getPos() : Vec3d.ZERO;
                antiAfkStageStartedAt = 0L;
                antiAfkPauseUntilAt = 0L;

                postWarpTrashDropPending = true;
                postWarpTrashDropReadyAt = now + 120L;
                nextPostWarpTrashDropAt = 0L;
            }
            return;
        }

        if (idleState == IdleState.ANTI_AFK) {
            tickAntiAfk(ctx);
        }
    }

    private void tickAntiAfk(Context ctx) {
        if (mc == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        tickAntiAfkTrashSweep(now);

        if (antiAfkPauseUntilAt > now) {
            stopMovementKeys();
            return;
        }

        if (antiAfkPattern.length == 0) {
            stopMovementKeys();
            return;
        }

        if (antiAfkStage < 0 || antiAfkStage >= antiAfkPattern.length) {
            antiAfkStage = 0;
        }

        if (antiAfkStageStartedAt == 0L) {
            antiAfkStageStartedAt = now;
            if ((antiAfkStage & 1) == 0 || antiAfkPairBase == null) {
                antiAfkPairBase = mc.player.getPos();
            }
        }

        MoveKey key = antiAfkPattern[antiAfkStage];
        applyMoveKey(key);

        if ((antiAfkStage & 1) == 0) {
            double moved = horizontalDistance(mc.player.getPos(), antiAfkPairBase);
            if (moved >= ANTI_AFK_OUT_DIST || now - antiAfkStageStartedAt >= ANTI_AFK_STEP_TIMEOUT_MS) {
                stopMovementKeys();
                advanceAntiAfkStage(now);
            }
            return;
        }

        double backDist = horizontalDistance(mc.player.getPos(), antiAfkPairBase);
        if (backDist <= ANTI_AFK_RETURN_DIST || now - antiAfkStageStartedAt >= ANTI_AFK_RETURN_TIMEOUT_MS) {
            stopMovementKeys();
            antiAfkPairBase = mc.player.getPos();
            advanceAntiAfkStage(now);
        }
    }

    private void advanceAntiAfkStage(long now) {
        int next = antiAfkStage + 1;
        if (next >= antiAfkPattern.length) {
            finishAntiAfkBurst(now);
            return;
        }

        antiAfkStage = next;
        antiAfkStageStartedAt = 0L;
        antiAfkPauseUntilAt = now + ANTI_AFK_STAGE_PAUSE_MS;
    }

    private void finishAntiAfkBurst(long now) {
        stopMovementKeys();
        antiAfkStage = 0;
        antiAfkStageStartedAt = 0L;
        antiAfkPauseUntilAt = 0L;
        antiAfkPairBase = null;
        idleState = IdleState.WARP_WAIT;
        idleUntilAt = now + IDLE_ANTI_AFK_INTERVAL_MS;
    }

    private Vec3d getRelativeMoveVector(MoveKey key, float yaw) {
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);

        if (key == MoveKey.FORWARD) return new Vec3d(fx, 0.0D, fz);
        if (key == MoveKey.BACK) return new Vec3d(-fx, 0.0D, -fz);
        if (key == MoveKey.LEFT) return new Vec3d(-fz, 0.0D, fx);
        return new Vec3d(fz, 0.0D, -fx);
    }

    private void applyMoveKey(MoveKey key) {
        stopMovementKeys();
        try {
            if (mc == null || mc.options == null) {
                return;
            }
            if (key == MoveKey.FORWARD) mc.options.forwardKey.setPressed(true);
            else if (key == MoveKey.BACK) mc.options.backKey.setPressed(true);
            else if (key == MoveKey.LEFT) mc.options.leftKey.setPressed(true);
            else if (key == MoveKey.RIGHT) mc.options.rightKey.setPressed(true);
        } catch (Throwable ignored) {
        }
    }

    private void stopMovementKeys() {
        try {
            if (mc == null || mc.options == null) {
                return;
            }
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        } catch (Throwable ignored) {
        }
    }

    private void switchToFallbackMode(long holdMs) {
        mode = Mode.DIRECT_FALLBACK;
        forceFallbackUntilAt = System.currentTimeMillis() + Math.max(400L, holdMs);
        targetOre = null;
        lastAreaScanAt = 0L;
        idleState = IdleState.NONE;
        stopMovementKeys();
    }

    private void switchToFallbackModeWithTarget(BlockPos target, long holdMs) {
        mode = Mode.DIRECT_FALLBACK;
        forceFallbackUntilAt = System.currentTimeMillis() + Math.max(800L, holdMs);
        targetOre = target == null ? null : target.toImmutable();
        lastAreaScanAt = 0L;
        idleState = IdleState.NONE;
        stopBreaking();
        stopMovementKeys();
    }

    private void switchToBaritoneMode() {
        mode = Mode.BARITONE_MINE;
        targetOre = null;
        lastAreaScanAt = 0L;
        baritoneFailStrikes = 0;
        baritoneStuckSamplePos = null;
        baritoneStuckSampleAt = 0L;
        baritoneNoProgressSinceAt = 0L;
        idleState = IdleState.NONE;
        stopBreaking();
        stopMovementKeys();
        ensureMineProcessRunning();
    }

    private boolean hasManualTargetInReach() {
        return targetOre != null && mc != null && mc.player != null && mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(targetOre)) <= MINE_REACH_SQ;
    }

    private boolean shouldRescanTarget(Context ctx) {
        if (targetOre == null) {
            return true;
        }

        if (!ctx.isInSelectedArea(targetOre)) {
            return true;
        }

        if (!isDiamondOre(targetOre)) {
            return true;
        }

        long now = System.currentTimeMillis();
        return now - lastAreaScanAt >= AREA_SCAN_INTERVAL_MS;
    }

    private boolean hasArea(Context ctx) {
        return ctx.getMinPoint() != null && ctx.getMaxPoint() != null;
    }

    private BlockPos getAreaCenter(Context ctx) {
        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return null;
        }

        int y = mc != null && mc.player != null ? mc.player.getBlockY() : min.getY();
        if (y < min.getY()) y = min.getY();
        if (y > max.getY()) y = max.getY();

        return new BlockPos(
                (min.getX() + max.getX()) >> 1,
                y,
                (min.getZ() + max.getZ()) >> 1
        );
    }

    private BlockPos findNearestDiamondOre(Context ctx, boolean idleStrictScan) {
        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();

        if (min == null || max == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();
        int maxX = max.getX();
        int maxY = max.getY();
        int maxZ = max.getZ();

        long volume = (long) (maxX - minX + 1) * (long) (maxY - minY + 1) * (long) (maxZ - minZ + 1);

        if (volume > MAX_SCAN_VOLUME_SOFT && !idleStrictScan) {
            BlockPos p = mc.player.getBlockPos();
            BlockPos center = getAreaCenter(ctx);
            boolean playerNearArea = ctx.isInsideAreaMargin(p, 24);

            BlockPos anchor = playerNearArea ? p : (center == null ? p : center);

            minX = Math.max(minX, anchor.getX() - SOFT_SCAN_RADIUS_XZ);
            maxX = Math.min(maxX, anchor.getX() + SOFT_SCAN_RADIUS_XZ);
            minY = Math.max(minY, anchor.getY() - SOFT_SCAN_RADIUS_Y);
            maxY = Math.min(maxY, anchor.getY() + SOFT_SCAN_RADIUS_Y);
            minZ = Math.max(minZ, anchor.getZ() - SOFT_SCAN_RADIUS_XZ);
            maxZ = Math.min(maxZ, anchor.getZ() + SOFT_SCAN_RADIUS_XZ);
        }

        Vec3d scoreRef = getScoreReference(ctx);
        BlockPos playerPos = mc.player.getBlockPos();

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!ctx.isInSelectedArea(pos)) {
                        continue;
                    }

                    if (!isDiamondOre(pos)) {
                        continue;
                    }

                    double score = scoreRef.squaredDistanceTo(Vec3d.ofCenter(pos));

                    if (isExposed(pos)) {
                        score -= 16.0D;
                    } else {
                        score += 3.0D;
                    }

                    score += Math.abs(y - playerPos.getY()) * 0.25D;

                    if (best == null || score < bestScore) {
                        best = pos.toImmutable();
                        bestScore = score;
                    }
                }
            }
        }

        return best;
    }

    private Vec3d getScoreReference(Context ctx) {
        if (mc == null || mc.player == null) {
            BlockPos c = getAreaCenter(ctx);
            return c == null ? Vec3d.ZERO : Vec3d.ofCenter(c);
        }

        if (ctx.isInsideAreaMargin(mc.player.getBlockPos(), 24)) {
            return mc.player.getPos();
        }

        BlockPos c = getAreaCenter(ctx);
        return c == null ? mc.player.getPos() : Vec3d.ofCenter(c);
    }

    private boolean isDiamondOre(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState s = mc.world.getBlockState(pos);
        return s.isOf(Blocks.DIAMOND_ORE) || s.isOf(Blocks.DEEPSLATE_DIAMOND_ORE);
    }

    private boolean isExposed(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState st = mc.world.getBlockState(n);
            if (st.isAir()) {
                return true;
            }
            try {
                if (st.getCollisionShape(mc.world, n).isEmpty()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean mineBlockHumanized(BlockPos pos) {
        if (pos == null || mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return false;
        }

        if (!canManuallyBreakBlock(pos)) {
            return false;
        }

        if (!isCloseEnoughForManualBreak(pos)) {
            releaseAttackKey();
            return false;
        }

        ensurePickaxeInHotbar();
        forceNoBlockUse();

        boolean newTarget = breakingPos == null || !breakingPos.equals(pos);
        if (newTarget) {
            breakingPos = pos.toImmutable();
            breakingSide = Direction.UP;
            breakingAimPoint = null;
            nextAimPointRefreshAt = 0L;
            lastBreakProgressAt = 0L;
            lastAttackPulseAt = 0L;
            attackHoldUntilAt = 0L;
            breakNoProgressSinceAt = 0L;
        }

        long now = System.currentTimeMillis();

        BlockHitResult visibleHit = findVisibleMiningHit(pos);
        if (visibleHit == null) {
            releaseAttackKey();
            breakingAimPoint = null;
            nextAimPointRefreshAt = 0L;
            return false;
        }

        if (breakingAimPoint == null || now >= nextAimPointRefreshAt) {
            breakingSide = visibleHit.getSide();

            Vec3d aim = buildHumanAimPoint(pos, breakingSide, now);
            BlockHitResult aimHit = raycastTo(aim);
            if (aimHit == null || aimHit.getType() != HitResult.Type.BLOCK || !aimHit.getBlockPos().equals(pos)) {
                aim = visibleHit.getPos();
            }

            breakingAimPoint = aim;
            nextAimPointRefreshAt = now + AIM_POINT_REFRESH_MS + (Math.abs(pos.hashCode()) % 90);
        }

        float[] desired = calcYawPitchTo(breakingAimPoint);
        applySmoothRotation(desired[0], desired[1], true, now);

        double angleErr = getAngleErrorTo(desired[0], desired[1]);

        if (angleErr <= ROT_ALIGN_HOLD_DEG) {
            attackHoldUntilAt = now + ATTACK_HOLD_AFTER_ALIGN_MS;
        }

        if (angleErr > ROT_RELEASE_DEG && now > attackHoldUntilAt) {
            releaseAttackKey();
            return true;
        }

        if (now <= attackHoldUntilAt || angleErr <= ROT_ALIGN_HOLD_DEG) {
            pressAttackKey();
        }

        boolean ok = false;

        if (newTarget || (now - lastAttackPulseAt >= ATTACK_START_PULSE_MS && angleErr <= ROT_ALIGN_PULSE_DEG)) {
            try {
                ok |= mc.interactionManager.attackBlock(pos, breakingSide);
            } catch (Throwable ignored) {
            }
            lastAttackPulseAt = now;
        }

        if (angleErr <= ROT_ALIGN_PULSE_DEG && now - lastBreakProgressAt >= BREAK_PROGRESS_INTERVAL_MS) {
            try {
                ok |= mc.interactionManager.updateBlockBreakingProgress(pos, breakingSide);
            } catch (Throwable ignored) {
                try {
                    Method m = mc.interactionManager.getClass().getMethod("attackBlock", BlockPos.class, Direction.class);
                    Object r = m.invoke(mc.interactionManager, pos, breakingSide);
                    if (r instanceof Boolean b) {
                        ok |= b;
                    } else {
                        ok = true;
                    }
                } catch (Throwable ignored2) {
                }
            }
            lastBreakProgressAt = now;
        }

        if (now - lastSwingAt >= SWING_INTERVAL_MS) {
            try {
                mc.player.swingHand(Hand.MAIN_HAND);
            } catch (Throwable ignored) {
            }
            lastSwingAt = now;
        }

        if (ok) {
            breakNoProgressSinceAt = 0L;
        } else {
            if (breakNoProgressSinceAt == 0L) {
                breakNoProgressSinceAt = now;
            } else if (now - breakNoProgressSinceAt >= MANUAL_BREAK_STALL_TIMEOUT_MS) {
                releaseAttackKey();
                return false;
            }
        }

        breaking = true;
        return ok || canManuallyBreakBlock(pos);
    }

    private Vec3d buildHumanAimPoint(BlockPos pos, Direction side, long now) {
        double[] grid = new double[]{-0.22D, 0.0D, 0.22D};

        long seed = 1469598103934665603L;
        seed ^= pos.asLong();
        seed *= 1099511628211L;
        seed ^= (long) side.ordinal() * 0x9E3779B97F4A7C15L;
        seed *= 1099511628211L;

        int step = (int) (((now / 85L) + (seed & 31L)) % 9L);
        int ix = step % 3;
        int iy = step / 3;

        double u = grid[ix];
        double v = grid[iy];

        double t = now * 0.0065D + ((seed >>> 8) & 255L) * 0.011D;
        double ju = Math.sin(t) * 0.014D + Math.cos(t * 0.53D) * 0.008D;
        double jv = Math.cos(t * 0.91D) * 0.012D + Math.sin(t * 0.41D + 0.7D) * 0.007D;

        u += ju;
        v += jv;

        if (u > 0.29D) u = 0.29D;
        if (u < -0.29D) u = -0.29D;
        if (v > 0.29D) v = 0.29D;
        if (v < -0.29D) v = -0.29D;

        double cx = pos.getX() + 0.5D + side.getOffsetX() * 0.5005D;
        double cy = pos.getY() + 0.5D + side.getOffsetY() * 0.5005D;
        double cz = pos.getZ() + 0.5D + side.getOffsetZ() * 0.5005D;

        if (side.getAxis() == Direction.Axis.X) {
            cy += u;
            cz += v;
        } else if (side.getAxis() == Direction.Axis.Y) {
            cx += u;
            cz += v;
        } else {
            cx += u;
            cy += v;
        }

        return new Vec3d(cx, cy, cz);
    }

    private void applySmoothRotation(float targetYaw, float targetPitch, boolean activeMining, long now) {
        if (mc == null || mc.player == null) {
            return;
        }

        if (!smoothRotInit) {
            smoothYaw = mc.player.getYaw();
            smoothPitch = mc.player.getPitch();
            smoothRotInit = true;
            lastRotAt = now;
        }

        if (lastRotAt == 0L) {
            lastRotAt = now;
        }

        float dt = (now - lastRotAt) / 1000.0F;
        if (dt < 0.0F) dt = 0.0F;
        if (dt > 0.12F) dt = 0.12F;
        lastRotAt = now;

        if (now >= nextRotSpeedAt) {
            float base = activeMining ? 205.0F : 160.0F;
            float spread = activeMining ? 85.0F : 60.0F;
            rotSpeedDegPerSec = base + (float) (Math.random() * spread);
            nextRotSpeedAt = now + 120L + (long) (Math.random() * 170.0D);
        }

        if (now >= nextRotNoiseAt) {
            rotNoiseYawTarget = (float) ((Math.random() - 0.5D) * (activeMining ? 1.5D : 0.7D));
            rotNoisePitchTarget = (float) ((Math.random() - 0.5D) * (activeMining ? 1.0D : 0.45D));
            nextRotNoiseAt = now + 90L + (long) (Math.random() * 130.0D);
        }

        float noiseLerp = Math.min(1.0F, Math.max(0.08F, dt * 8.0F));
        rotNoiseYaw += (rotNoiseYawTarget - rotNoiseYaw) * noiseLerp;
        rotNoisePitch += (rotNoisePitchTarget - rotNoisePitch) * noiseLerp;

        float desiredYaw = wrapDegrees(targetYaw + rotNoiseYaw * 0.34F);
        float desiredPitch = clampPitch(targetPitch + rotNoisePitch * 0.24F);

        float yawDiff = wrapDegrees(desiredYaw - smoothYaw);
        float pitchDiff = desiredPitch - smoothPitch;

        float maxStep = rotSpeedDegPerSec * Math.max(0.004F, dt);

        float yawStep = clamp(yawDiff, -maxStep, maxStep);
        float pitchStep = clamp(pitchDiff, -maxStep * 0.72F, maxStep * 0.72F);

        yawStep *= 0.92F + (float) (Math.random() * 0.10D);
        pitchStep *= 0.90F + (float) (Math.random() * 0.12D);

        smoothYaw = wrapDegrees(smoothYaw + yawStep);
        smoothPitch = clampPitch(smoothPitch + pitchStep);

        mc.player.setYaw(smoothYaw);
        mc.player.setPitch(smoothPitch);
    }

    private float[] calcYawPitchTo(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(1.0E-4D, horiz))));
        pitch = clampPitch(pitch);

        return new float[]{yaw, pitch};
    }

    private double getAngleErrorTo(float yaw, float pitch) {
        if (mc == null || mc.player == null) {
            return 180.0D;
        }

        float dy = wrapDegrees(yaw - mc.player.getYaw());
        float dp = pitch - mc.player.getPitch();
        return Math.sqrt(dy * dy + dp * dp);
    }

    private void stopBreaking() {
        breaking = false;
        breakingPos = null;
        breakingSide = Direction.UP;
        breakingAimPoint = null;
        nextAimPointRefreshAt = 0L;
        lastAttackPulseAt = 0L;
        lastBreakProgressAt = 0L;
        attackHoldUntilAt = 0L;
        breakNoProgressSinceAt = 0L;
        releaseAttackKey();

        try {
            if (mc != null && mc.interactionManager != null) {
                Method m = mc.interactionManager.getClass().getMethod("cancelBlockBreaking");
                m.invoke(mc.interactionManager);
            }
        } catch (Throwable ignored) {
        }
    }

    private void forceNoBlockUse() {
        releaseUseKey();

        try {
            if (mc == null || mc.player == null) {
                return;
            }

            ItemStack main = mc.player.getMainHandStack();
            ItemStack off = mc.player.getOffHandStack();

            boolean blockInMain = main != null && !main.isEmpty() && main.getItem() instanceof BlockItem;
            boolean blockInOff = off != null && !off.isEmpty() && off.getItem() instanceof BlockItem;

            if (blockInMain || blockInOff) {
                releaseUseKey();
                try {
                    if (mc.options != null) {
                        mc.options.useKey.setPressed(false);
                    }
                } catch (Throwable ignored) {
                }
            }

            if ((blockInMain || blockInOff) && mc.player.isUsingItem()) {
                try {
                    mc.player.stopUsingItem();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void pressAttackKey() {
        try {
            if (mc != null && mc.options != null) {
                mc.options.attackKey.setPressed(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseAttackKey() {
        try {
            if (mc != null && mc.options != null) {
                mc.options.attackKey.setPressed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private void releaseUseKey() {
        try {
            if (mc != null && mc.options != null) {
                mc.options.useKey.setPressed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    private Direction pickMiningSide(BlockPos pos) {
        if (pos == null || mc == null || mc.player == null || mc.world == null) {
            return Direction.UP;
        }

        BlockHitResult strictVisible = findVisibleMiningHit(pos);
        if (strictVisible != null && strictVisible.getType() == HitResult.Type.BLOCK && strictVisible.getBlockPos().equals(pos)) {
            return strictVisible.getSide();
        }

        if (breakingPos != null && breakingPos.equals(pos) && breakingAimPoint != null) {
            BlockHitResult cachedHit = raycastTo(breakingAimPoint);
            if (cachedHit != null && cachedHit.getType() == HitResult.Type.BLOCK && cachedHit.getBlockPos().equals(pos)) {
                return cachedHit.getSide();
            }
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;

        BlockHitResult hit = raycastTo(center);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
            return hit.getSide();
        }

        double adx = Math.abs(dx);
        double ady = Math.abs(dy);
        double adz = Math.abs(dz);

        if (ady >= adx && ady >= adz) {
            return dy > 0.0D ? Direction.DOWN : Direction.UP;
        }
        if (adx >= adz) {
            return dx > 0.0D ? Direction.WEST : Direction.EAST;
        }
        return dz > 0.0D ? Direction.NORTH : Direction.SOUTH;
    }

    private BlockHitResult raycastTo(Vec3d target) {
        if (target == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }
        return raycastBetween(mc.player.getEyePos(), target);
    }

    private BlockHitResult raycastBetween(Vec3d from, Vec3d to) {
        if (from == null || to == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        try {
            return mc.world.raycast(new RaycastContext(
                    from,
                    to,
                    RaycastContext.ShapeType.OUTLINE,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean canMineBlockNow(BlockPos pos) {
        if (!canManuallyBreakBlock(pos)) {
            return false;
        }
        if (!isWithinMineReach(pos)) {
            return false;
        }

        BlockHitResult hit = findVisibleMiningHit(pos);
        return hit != null && isWithinMineReach(hit.getPos()) && isCloseEnoughForManualBreak(pos);
    }

    private boolean canManuallyBreakBlock(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);
        if (state == null || state.isAir()) {
            return false;
        }

        if (state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.BARRIER)) {
            return false;
        }

        try {
            if (state.getFluidState() != null && !state.getFluidState().isEmpty()) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (state.getHardness(mc.world, pos) < 0.0F) {
                return false;
            }
        } catch (Throwable ignored) {
        }

        return true;
    }

    private boolean isWithinMineReach(BlockPos pos) {
        if (pos == null || mc == null || mc.player == null) {
            return false;
        }
        return mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= MINE_REACH_SQ;
    }

    private boolean isWithinMineReach(Vec3d point) {
        if (point == null || mc == null || mc.player == null) {
            return false;
        }
        return mc.player.getEyePos().squaredDistanceTo(point) <= (MINE_REACH_SQ + 0.65D);
    }

    private BlockHitResult findVisibleMiningHit(BlockPos pos) {
        if (pos == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }
        return findVisibleMiningHitFrom(mc.player.getEyePos(), pos);
    }

    private BlockHitResult findVisibleMiningHitFrom(Vec3d eye, BlockPos pos) {
        if (eye == null || pos == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        BlockHitResult centerHit = raycastBetween(eye, Vec3d.ofCenter(pos));
        if (centerHit != null && centerHit.getType() == HitResult.Type.BLOCK && centerHit.getBlockPos().equals(pos)) {
            return centerHit;
        }

        for (Direction d : Direction.values()) {
            Vec3d faceCenter = new Vec3d(
                    pos.getX() + 0.5D + d.getOffsetX() * 0.5D,
                    pos.getY() + 0.5D + d.getOffsetY() * 0.5D,
                    pos.getZ() + 0.5D + d.getOffsetZ() * 0.5D
            );
            BlockHitResult h = raycastBetween(eye, faceCenter);
            if (h != null && h.getType() == HitResult.Type.BLOCK && h.getBlockPos().equals(pos)) {
                return h;
            }
        }

        double[] probe = new double[]{0.22D, 0.50D, 0.78D};
        for (double x : probe) {
            for (double y : probe) {
                for (double z : probe) {
                    BlockHitResult h = raycastBetween(eye, new Vec3d(pos.getX() + x, pos.getY() + y, pos.getZ() + z));
                    if (h != null && h.getType() == HitResult.Type.BLOCK && h.getBlockPos().equals(pos)) {
                        return h;
                    }
                }
            }
        }

        return null;
    }

    private boolean isSimpleStandablePos(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState below = mc.world.getBlockState(pos.down());

        if (!isPassableForStand(feet, pos)) {
            return false;
        }
        if (!isPassableForStand(head, pos.up())) {
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

    private boolean isPassableForStand(BlockState state, BlockPos pos) {
        if (state == null || pos == null || mc == null || mc.world == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void ensurePickaxeInHotbar() {
        if (mc == null || mc.player == null) {
            return;
        }

        try {
            ItemStack main = mc.player.getMainHandStack();
            if (main != null && !main.isEmpty() && main.getItem() instanceof PickaxeItem) {
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            PlayerInventory inv = mc.player.getInventory();
            if (inv == null) {
                return;
            }

            for (int i = 0; i < 9; i++) {
                ItemStack s = inv.getStack(i);
                if (s != null && !s.isEmpty() && s.getItem() instanceof PickaxeItem) {
                    inv.selectedSlot = i;
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private boolean baritoneAvailable() {
        if (baritoneUnavailable) {
            return false;
        }
        return getBaritonePrimary() != null && getMineProcess() != null;
    }

    private Object getBaritonePrimary() {
        if (cachedBaritonePrimary != null) {
            return cachedBaritonePrimary;
        }
        if (baritoneInitTried) {
            return null;
        }
        baritoneInitTried = true;

        try {
            Class<?> apiCls = Class.forName("baritone.api.BaritoneAPI");
            Method getProvider = apiCls.getMethod("getProvider");
            Object provider = getProvider.invoke(null);
            if (provider == null) {
                baritoneUnavailable = true;
                return null;
            }

            for (Method m : provider.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && m.getName().equals("getPrimaryBaritone")) {
                    Object primary = m.invoke(provider);
                    if (primary != null) {
                        cachedBaritonePrimary = primary;
                        return primary;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        baritoneUnavailable = true;
        return null;
    }

    private Object getMineProcess() {
        if (cachedMineProcess != null) {
            return cachedMineProcess;
        }

        Object primary = getBaritonePrimary();
        if (primary == null) {
            return null;
        }

        try {
            Method m = primary.getClass().getMethod("getMineProcess");
            Object p = m.invoke(primary);
            if (p != null) {
                cachedMineProcess = p;
                return p;
            }
        } catch (Throwable ignored) {
        }

        for (Method m : primary.getClass().getMethods()) {
            if (m.getParameterCount() == 0 && m.getName().toLowerCase().contains("mine")) {
                try {
                    Object p = m.invoke(primary);
                    if (p != null) {
                        cachedMineProcess = p;
                        return p;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private void ensureMineProcessRunning() {
        Object mine = getMineProcess();
        if (mine == null) {
            return;
        }

        if (isMineProcessActive()) {
            return;
        }

        if (invokeMineStart(mine)) {
            baritoneFailStrikes = 0;
        } else {
            baritoneFailStrikes++;
        }
    }

    private boolean invokeMineStart(Object mine) {
        Block[] targets = new Block[]{Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE};

        for (Method m : mine.getClass().getMethods()) {
            if (!m.getName().equals("mine")) {
                continue;
            }

            Class<?>[] p = m.getParameterTypes();

            try {
                if (p.length == 1) {
                    if (p[0].isArray()) {
                        m.invoke(mine, (Object) targets);
                        return true;
                    }
                    if (List.class.isAssignableFrom(p[0])) {
                        ArrayList<Block> list = new ArrayList<>();
                        list.add(Blocks.DIAMOND_ORE);
                        list.add(Blocks.DEEPSLATE_DIAMOND_ORE);
                        m.invoke(mine, list);
                        return true;
                    }
                }

                if (p.length == 2 && p[0] == int.class && p[1].isArray()) {
                    m.invoke(mine, 0, targets);
                    return true;
                }

                if (p.length == 2 && p[0] == long.class && p[1].isArray()) {
                    m.invoke(mine, 0L, targets);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private void cancelMineProcess() {
        Object mine = getMineProcess();
        if (mine == null) {
            return;
        }

        try {
            Method m = mine.getClass().getMethod("cancel");
            m.invoke(mine);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = mine.getClass().getMethod("onLostControl");
            m.invoke(mine);
        } catch (Throwable ignored) {
        }
    }

    private boolean isMineProcessActive() {
        Object mine = getMineProcess();
        if (mine == null) {
            return false;
        }

        try {
            Method m = mine.getClass().getMethod("isActive");
            Object r = m.invoke(mine);
            return r instanceof Boolean && (Boolean) r;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int getKnownOreCount() {
        List<BlockPos> list = getKnownOreLocations();
        return list == null ? 0 : list.size();
    }

    @SuppressWarnings("unchecked")
    private List<BlockPos> getKnownOreLocations() {
        Object mine = getMineProcess();
        if (mine == null) {
            return null;
        }

        try {
            Field f = findFieldRecursive(mine.getClass(), "knownOreLocations");
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            Object v = f.get(mine);
            if (v instanceof List) {
                return (List<BlockPos>) v;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private void filterKnownOreLocationsByArea(Context ctx) {
        Object mine = getMineProcess();
        if (mine == null || ctx == null) {
            return;
        }

        try {
            Field f = findFieldRecursive(mine.getClass(), "knownOreLocations");
            if (f == null) {
                return;
            }
            f.setAccessible(true);
            Object v = f.get(mine);
            if (!(v instanceof List<?> raw)) {
                return;
            }

            ArrayList<BlockPos> filtered = new ArrayList<>(raw.size());
            for (Object o : raw) {
                if (!(o instanceof BlockPos p)) {
                    continue;
                }
                if (!ctx.isInSelectedArea(p)) {
                    continue;
                }
                if (!isDiamondOre(p)) {
                    continue;
                }
                filtered.add(p.toImmutable());
            }

            f.set(mine, filtered);
        } catch (Throwable ignored) {
        }
    }

    private boolean tickBaritoneStuckMonitor() {
        if (mc == null || mc.player == null) {
            return false;
        }

        if (!isMineProcessActive()) {
            baritoneStuckSamplePos = null;
            baritoneStuckSampleAt = 0L;
            baritoneNoProgressSinceAt = 0L;
            return false;
        }

        long now = System.currentTimeMillis();
        Vec3d pos = mc.player.getPos();

        if (baritoneStuckSamplePos == null) {
            baritoneStuckSamplePos = pos;
            baritoneStuckSampleAt = now;
            baritoneNoProgressSinceAt = now;
            return false;
        }

        if (now - baritoneStuckSampleAt < 220L) {
            return false;
        }

        double movedSq = pos.squaredDistanceTo(baritoneStuckSamplePos);
        baritoneStuckSamplePos = pos;
        baritoneStuckSampleAt = now;

        if (movedSq > 0.05D * 0.05D) {
            baritoneNoProgressSinceAt = now;
            return false;
        }

        if (baritoneNoProgressSinceAt == 0L) {
            baritoneNoProgressSinceAt = now;
            return false;
        }

        return now - baritoneNoProgressSinceAt >= BARITONE_STUCK_TIMEOUT_MS;
    }

    private boolean isBaritonePillaring() {
        Object primary = getBaritonePrimary();
        if (primary == null) {
            return false;
        }

        try {
            Object pathingBehavior = invokeNoArg(primary, "getPathingBehavior");
            if (pathingBehavior == null) {
                return false;
            }

            Object currentExec = invokeNoArg(pathingBehavior, "getCurrent");
            if (currentExec == null) {
                return false;
            }

            Object path = invokeNoArg(currentExec, "getPath");
            if (path == null) {
                return false;
            }

            Object movementsObj = invokeNoArg(path, "movements");
            if (!(movementsObj instanceof List<?> movements) || movements.isEmpty()) {
                return false;
            }

            int idx = 0;
            try {
                Object idxObj = invokeNoArg(currentExec, "getPosition");
                if (idxObj instanceof Integer i) {
                    idx = i;
                }
            } catch (Throwable ignored) {
            }

            if (idx < 0) idx = 0;
            if (idx >= movements.size()) idx = movements.size() - 1;

            for (int k = 0; k < 4; k++) {
                int i = idx + k;
                if (i < 0 || i >= movements.size()) {
                    continue;
                }
                Object mv = movements.get(i);
                if (mv == null) {
                    continue;
                }

                String n = mv.getClass().getName().toLowerCase();

                if (n.contains("pillar")) return true;
                if (n.contains("parkourplace")) return true;
                if (n.contains("place") && (n.contains("up") || n.contains("ascend") || n.contains("pillar") || n.contains("jump"))) return true;
            }

            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyBaritoneRestrictions(boolean allowBreakInsideArea) {
        try {
            Class<?> apiCls = Class.forName("baritone.api.BaritoneAPI");
            Method settingsM = apiCls.getMethod("getSettings");
            Object settings = settingsM.invoke(null);
            if (settings == null) {
                return;
            }

            setBaritoneBooleanSetting(settings, "allowPlace", false);
            setBaritoneBooleanSetting(settings, "allowParkourPlace", false);
            setBaritoneBooleanSetting(settings, "allowPillaring", false);
            setBaritoneBooleanSetting(settings, "allowParkour", false);
            setBaritoneBooleanSetting(settings, "allowParkourAscend", false);
            setBaritoneBooleanSetting(settings, "allowWaterBucketFall", false);
            setBaritoneBooleanSetting(settings, "allowWaterBucketAscend", false);
            setBaritoneBooleanSetting(settings, "allowVines", false);
            setBaritoneBooleanSetting(settings, "allowBreak", allowBreakInsideArea);
        } catch (Throwable ignored) {
        }
    }

    private void setBaritoneBooleanSetting(Object settings, String fieldName, boolean value) {
        try {
            Field f = findFieldRecursive(settings.getClass(), fieldName);
            if (f == null) {
                return;
            }
            f.setAccessible(true);
            Object settingObj = f.get(settings);
            if (settingObj == null) {
                return;
            }

            Field valueField = findFieldRecursive(settingObj.getClass(), "value");
            if (valueField != null) {
                valueField.setAccessible(true);
                if (valueField.getType() == boolean.class || valueField.getType() == Boolean.class) {
                    valueField.set(settingObj, value);
                    return;
                }
            }

            try {
                Method set = settingObj.getClass().getMethod("set", Object.class);
                set.invoke(settingObj, value);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateRenderCache(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return;
        }

        long now = System.currentTimeMillis();

        BlockPos anchor = ctx.isInsideAreaMargin(mc.player.getBlockPos(), 20)
                ? mc.player.getBlockPos()
                : (getAreaCenter(ctx) == null ? mc.player.getBlockPos() : getAreaCenter(ctx));

        boolean movedFar = lastRenderAnchor == null || lastRenderAnchor.getSquaredDistance(anchor) >= 16.0D;
        if (!movedFar && now - lastRenderCacheAt < RENDER_CACHE_UPDATE_MS) {
            return;
        }

        lastRenderAnchor = anchor.toImmutable();
        lastRenderCacheAt = now;
        renderOreCache.clear();

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        int minX = Math.max(min.getX(), anchor.getX() - RENDER_SOFT_RADIUS_XZ);
        int maxX = Math.min(max.getX(), anchor.getX() + RENDER_SOFT_RADIUS_XZ);
        int minY = Math.max(min.getY(), anchor.getY() - RENDER_SOFT_RADIUS_Y);
        int maxY = Math.min(max.getY(), anchor.getY() + RENDER_SOFT_RADIUS_Y);
        int minZ = Math.max(min.getZ(), anchor.getZ() - RENDER_SOFT_RADIUS_XZ);
        int maxZ = Math.min(max.getZ(), anchor.getZ() + RENDER_SOFT_RADIUS_XZ);

        int count = 0;
        for (int y = minY; y <= maxY && count < RENDER_MAX_ORES; y++) {
            for (int x = minX; x <= maxX && count < RENDER_MAX_ORES; x++) {
                for (int z = minZ; z <= maxZ && count < RENDER_MAX_ORES; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!ctx.isInSelectedArea(p)) {
                        continue;
                    }
                    if (!isDiamondOre(p)) {
                        continue;
                    }

                    int color = isExposed(p) ? 0xFF57E8FF : 0xFF3AA2FF;
                    renderOreCache.put(p.toImmutable(), color);
                    count++;
                }
            }
        }
    }

    private void renderOres(Context ctx) {
        if (renderOreCache.isEmpty() || mc == null || mc.player == null) {
            return;
        }

        int shown = 0;
        for (Map.Entry<BlockPos, Integer> entry : renderOreCache.entrySet()) {
            if (shown >= RENDER_MAX_ORES) {
                break;
            }

            BlockPos pos = entry.getKey();
            if (!ctx.isInSelectedArea(pos) || !isDiamondOre(pos)) {
                continue;
            }

            double distSq = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(pos));
            if (distSq > (RENDER_SOFT_RADIUS_XZ * RENDER_SOFT_RADIUS_XZ * 1.6D)) {
                continue;
            }

            int color = entry.getValue();
            boolean isTarget = targetOre != null && targetOre.equals(pos);

            Render3D.drawBox(new Box(pos), color, isTarget ? 2.1f : 1.25f, true, true, false);
            if (isTarget) {
                Render3D.drawBox(new Box(pos).expand(0.04D), 0xFFFFFFFF, 1.0f, true, false, false);
            }

            shown++;
        }
    }

    private void renderCurrentTarget() {
        if (mc == null || mc.player == null || targetOre == null) {
            return;
        }

        if (!isDiamondOre(targetOre)) {
            return;
        }

        Vec3d from = mc.player.getEyePos();
        Vec3d to = Vec3d.ofCenter(targetOre);
        Render3D.drawLine(from, to, 0xAA57E8FF, 1.05f, false);
    }

    private void renderIdleCenter(Context ctx) {
        if (idleState == IdleState.NONE || ctx == null) {
            return;
        }

        BlockPos center = idleAreaCenter != null ? idleAreaCenter : getAreaCenter(ctx);
        if (center == null) {
            return;
        }

        int color = idleState == IdleState.WARP_WAIT ? 0xFFFFC857 : 0xFFB07CFF;
        Render3D.drawBox(new Box(center).expand(0.12D), color, 1.35f, true, false, false);
    }

    private void sendWarpMineCommand() {
        if (mc == null || mc.player == null) {
            return;
        }

        try {
            Method m = mc.player.getClass().getMethod("sendChatMessage", String.class);
            m.invoke(mc.player, "/warp mine");
            return;
        } catch (Throwable ignored) {
        }

        try {
            Object networkHandler = mc.player.getClass().getMethod("networkHandler").invoke(mc.player);
            if (networkHandler != null) {
                Method sendCmd = networkHandler.getClass().getMethod("sendChatCommand", String.class);
                sendCmd.invoke(networkHandler, "warp mine");
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object networkHandler = mc.player.getClass().getField("networkHandler").get(mc.player);
            if (networkHandler != null) {
                try {
                    Method sendMsg = networkHandler.getClass().getMethod("sendChatMessage", String.class);
                    sendMsg.invoke(networkHandler, "/warp mine");
                    return;
                } catch (Throwable ignored2) {
                }
                try {
                    Method sendCmd = networkHandler.getClass().getMethod("sendChatCommand", String.class);
                    sendCmd.invoke(networkHandler, "warp mine");
                } catch (Throwable ignored2) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object invokeNoArg(Object target, String method) {
        if (target == null || method == null) {
            return null;
        }

        try {
            Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Field findFieldRecursive(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private double horizontalDistance(Vec3d a, Vec3d b) {
        if (a == null || b == null) {
            return 0.0D;
        }
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float wrapDegrees(float v) {
        float x = v % 360.0F;
        if (x >= 180.0F) x -= 360.0F;
        if (x < -180.0F) x += 360.0F;
        return x;
    }

    private static float clampPitch(float p) {
        if (p > 90.0F) return 90.0F;
        if (p < -90.0F) return -90.0F;
        return p;
    }

    private static float clamp(float v, float min, float max) {
        if (v < min) return min;
        return Math.min(v, max);
    }
}