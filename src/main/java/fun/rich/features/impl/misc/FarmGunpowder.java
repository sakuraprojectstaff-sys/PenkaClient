package fun.rich.features.impl.misc;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FarmGunpowder {

    static final long ENTITY_SCAN_MS = 90L;
    static final long LOOT_SCAN_MS = 80L;
    static final long PATH_REFRESH_MS = 350L;
    static final long ATTACK_CD_MS = 520L;
    static final long STUCK_TIMEOUT_MS = 850L;
    static final long SWEEP_REPATH_MS = 700L;

    static final double ATTACK_DIST = 3.35D;
    static final double ATTACK_REACH_SQ = ATTACK_DIST * ATTACK_DIST;
    static final double RETREAT_DIST = 9.0D;
    static final double DANGER_RADIUS = 8.0D;
    static final double LOOT_UNSAFE_RADIUS = 4.25D;
    static final double LOOT_REACHED_SQ = 2.2D * 2.2D;
    static final double PATROL_REACHED_SQ = 2.0D * 2.0D;
    static final double BLOCK_REACH_SQ = 25.0D;

    static final int GO_STORAGE_GUNPOWDER = 128;
    static final int MIN_FREE_SLOTS = 2;
    static final int SWEEP_STEP = 3;

    final MinecraftClient mc = MinecraftClient.getInstance();

    State state = State.SEARCH;

    BlockPos[] patrolPoints;
    int patrolIndex;
    int sweepIndex;
    long lastSweepRepathAt;

    BlockPos lastAreaMin;
    BlockPos lastAreaMax;

    BlockPos lootGoal;
    BlockPos retreatGoal;

    long lastEntityScanAt;
    long lastLootScanAt;
    long lastPathAt;
    long lastAttackAt;

    BlockPos trackedPathGoal;
    Vec3d trackedPathStartPos;
    long trackedPathSinceAt;
    double trackedPathLastGoalDistSq;
    long trackedNoProgressSinceAt;

    enum State {
        SEARCH,
        APPROACH,
        ATTACK,
        RETREAT,
        LOOT,
        PATROL
    }

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
    }

    public void deactivate() {
        reset();
    }

    public void reset() {
        state = State.SEARCH;

        patrolPoints = null;
        patrolIndex = 0;
        sweepIndex = 0;
        lastSweepRepathAt = 0L;

        lastAreaMin = null;
        lastAreaMax = null;

        lootGoal = null;
        retreatGoal = null;

        lastEntityScanAt = 0L;
        lastLootScanAt = 0L;
        lastPathAt = 0L;
        lastAttackAt = 0L;

        resetTrackedGoal();
    }

    public void tick(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            resetTrackedGoal();
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        if (ctx.getMinPoint() == null || ctx.getMaxPoint() == null) {
            resetTrackedGoal();
            return;
        }

        rebuildPatrolPoints(ctx);

        if (shouldGoToStorageNow()) {
            state = State.SEARCH;
            lootGoal = null;
            retreatGoal = null;
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return;
        }

        CreeperEntity danger = findExplodingOrDangerCreeper(ctx);
        if (danger != null) {
            handleRetreat(ctx, danger);
            return;
        }

        ItemEntity loot = findSafeGunpowder(ctx);
        if (loot != null) {
            handleLoot(ctx, loot);
            return;
        }

        CreeperEntity target = findClosestCreeper(ctx);
        if (target != null) {
            handleCombat(ctx, target);
            return;
        }

        handlePatrol(ctx);
    }

    public boolean shouldGoToStorageNow() {
        int gunpowder = countGunpowderInInventory();
        if (gunpowder <= 0) {
            return false;
        }
        if (gunpowder >= GO_STORAGE_GUNPOWDER) {
            return true;
        }
        return countFreeInventorySlots() <= MIN_FREE_SLOTS;
    }

    public boolean hasAnyCultureToDeposit() {
        return countGunpowderInInventory() > 0;
    }

    public boolean dropNextDepositableCultureFromWholeInventory() {
        if (mc == null || mc.player == null || mc.interactionManager == null) {
            return false;
        }

        ScreenHandler handler = mc.player.currentScreenHandler != null ? mc.player.currentScreenHandler : mc.player.playerScreenHandler;
        if (handler == null) {
            return false;
        }

        try {
            List<Slot> slots = handler.slots;
            for (int i = 0; i < slots.size(); i++) {
                Slot slot = slots.get(i);
                if (slot == null) {
                    continue;
                }

                ItemStack stack = slot.getStack();
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                if (!stack.isOf(Items.GUNPOWDER)) {
                    continue;
                }

                mc.interactionManager.clickSlot(handler.syncId, i, 1, SlotActionType.THROW, mc.player);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private void handleRetreat(Context ctx, CreeperEntity creeper) {
        state = State.RETREAT;

        BlockPos goal = computeRetreatGoal(ctx, creeper);
        if (goal == null) {
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return;
        }

        retreatGoal = goal.toImmutable();

        Vec3d look = getBestVisibleCreeperAimPoint(creeper);
        if (look == null) {
            look = Vec3d.ofCenter(retreatGoal);
        }
        lookAtSmooth(look);

        pathToGoalTracked(ctx, retreatGoal);
    }

    private void handleLoot(Context ctx, ItemEntity item) {
        state = State.LOOT;

        lootGoal = item.getBlockPos().toImmutable();

        Vec3d itemLook = item.getPos().add(0.0D, 0.08D, 0.0D);
        if (canSeePoint(itemLook)) {
            lookAtSmooth(itemLook);
        }

        if (mc.player.getPos().squaredDistanceTo(item.getPos()) <= LOOT_REACHED_SQ) {
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return;
        }

        BlockPos pickupGoal = findPickupGoalNearItem(ctx, item.getPos());
        if (pickupGoal != null) {
            pathToGoalTracked(ctx, pickupGoal);
            return;
        }

        pathToGoalTracked(ctx, lootGoal);
    }

    private void handleCombat(Context ctx, CreeperEntity creeper) {
        Vec3d visibleAim = getBestVisibleCreeperAimPoint(creeper);
        if (visibleAim != null) {
            lookAtSmooth(visibleAim);
        }

        boolean canHitNow = canAttackCreeperNow(creeper, visibleAim);
        if (canHitNow) {
            state = State.ATTACK;
            ctx.stopWorkPathing();
            resetTrackedGoal();

            long now = System.currentTimeMillis();
            if (now - lastAttackAt >= ATTACK_CD_MS && attackCooldownReady()) {
                try {
                    mc.interactionManager.attackEntity(mc.player, creeper);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastAttackAt = now;
                } catch (Throwable ignored) {
                }
            }
            return;
        }

        state = State.APPROACH;

        BlockPos approach = findApproachPosForCreeper(ctx, creeper);
        if (approach != null) {
            pathToGoalTracked(ctx, approach);
            return;
        }

        pathToGoalTracked(ctx, creeper.getBlockPos());
    }

    private void handlePatrol(Context ctx) {
        if (trySweepArea(ctx)) {
            state = State.PATROL;
            return;
        }

        if (patrolPoints == null || patrolPoints.length == 0) {
            state = State.SEARCH;
            ctx.stopWorkPathing();
            resetTrackedGoal();
            return;
        }

        if (patrolIndex < 0 || patrolIndex >= patrolPoints.length) {
            patrolIndex = 0;
        }

        BlockPos base = patrolPoints[patrolIndex];
        if (base == null) {
            patrolIndex = (patrolIndex + 1) % patrolPoints.length;
            return;
        }

        BlockPos target = findNearestStandableAround(ctx, base, 4, 2);
        if (target == null) {
            patrolIndex = (patrolIndex + 1) % patrolPoints.length;
            return;
        }

        if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(target)) <= PATROL_REACHED_SQ) {
            patrolIndex = (patrolIndex + 1) % patrolPoints.length;
            return;
        }

        state = State.PATROL;
        if (canSeePoint(Vec3d.ofCenter(target))) {
            lookAtSmooth(Vec3d.ofCenter(target));
        }
        pathToGoalTracked(ctx, target);
    }

    private boolean trySweepArea(Context ctx) {
        if (ctx == null || mc == null || mc.player == null) {
            return false;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (trackedPathGoal != null && now - lastSweepRepathAt < SWEEP_REPATH_MS) {
            return false;
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

            BlockPos candidate = findSweepStandable(ctx, x, z, min.getY(), max.getY());
            if (candidate == null) {
                continue;
            }

            if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(candidate)) <= 4.0D) {
                continue;
            }

            sweepIndex = (idx + 1) % total;
            lastSweepRepathAt = now;
            if (canSeePoint(Vec3d.ofCenter(candidate))) {
                lookAtSmooth(Vec3d.ofCenter(candidate));
            }
            pathToGoalTracked(ctx, candidate);
            return true;
        }

        return false;
    }

    private void pathToGoalTracked(Context ctx, BlockPos goal) {
        if (ctx == null || goal == null || mc == null || mc.player == null) {
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
            lastPathAt = 0L;
        }

        if (trackedPathStartPos == null) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
        }

        if (goalDistSq + 0.12D < trackedPathLastGoalDistSq) {
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
        }

        double movedSq = mc.player.getPos().squaredDistanceTo(trackedPathStartPos);
        if (movedSq >= 0.30D) {
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
        }

        boolean stuckByMove = now - trackedPathSinceAt >= STUCK_TIMEOUT_MS && movedSq < 0.16D;
        boolean stuckByProgress = now - trackedNoProgressSinceAt >= STUCK_TIMEOUT_MS && goalDistSq > 4.0D;

        if ((stuckByMove || stuckByProgress) && now - lastPathAt >= 120L) {
            ctx.stopWorkPathing();
            trackedPathStartPos = mc.player.getPos();
            trackedPathSinceAt = now;
            trackedPathLastGoalDistSq = goalDistSq;
            trackedNoProgressSinceAt = now;
            ctx.pathToWorkGoal(goal);
            lastPathAt = now;
            return;
        }

        if (now - lastPathAt >= PATH_REFRESH_MS) {
            ctx.pathToWorkGoal(goal);
            lastPathAt = now;
        }
    }

    private void resetTrackedGoal() {
        trackedPathGoal = null;
        trackedPathStartPos = null;
        trackedPathSinceAt = 0L;
        trackedPathLastGoalDistSq = Double.MAX_VALUE;
        trackedNoProgressSinceAt = 0L;
    }

    private CreeperEntity findClosestCreeper(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        Box box = areaBox(ctx, 2.0D, 2.0D, 3.0D);
        if (box == null) {
            return null;
        }

        CreeperEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (CreeperEntity c : mc.world.getEntitiesByClass(CreeperEntity.class, box, e -> true)) {
            if (c == null || !c.isAlive()) {
                continue;
            }
            if (!ctx.isInsideAreaMargin(c.getBlockPos(), 2)) {
                continue;
            }
            if (isExplodingLike(c)) {
                continue;
            }

            double distSq = mc.player.getPos().squaredDistanceTo(c.getPos());
            Vec3d aim = getBestVisibleCreeperAimPoint(c);
            double score = distSq + (aim == null ? 10.0D : 0.0D);

            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }

        return best;
    }

    private CreeperEntity findExplodingOrDangerCreeper(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastEntityScanAt < ENTITY_SCAN_MS) {
            return null;
        }
        lastEntityScanAt = now;

        Box box = mc.player.getBoundingBox().expand(DANGER_RADIUS, 3.0D, DANGER_RADIUS);
        CreeperEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (CreeperEntity c : mc.world.getEntitiesByClass(CreeperEntity.class, box, e -> true)) {
            if (c == null || !c.isAlive()) {
                continue;
            }
            if (!ctx.isInsideAreaMargin(c.getBlockPos(), 5)) {
                continue;
            }

            double dist = mc.player.getPos().distanceTo(c.getPos());
            boolean exploding = isExplodingLike(c);
            if (!exploding && dist > 2.6D) {
                continue;
            }

            double score = dist - (exploding ? 100.0D : 0.0D);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }

        return best;
    }

    private ItemEntity findSafeGunpowder(Context ctx) {
        if (ctx == null || mc == null || mc.player == null || mc.world == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastLootScanAt < LOOT_SCAN_MS) {
            return null;
        }
        lastLootScanAt = now;

        Box box = areaBox(ctx, 2.0D, 2.0D, 3.0D);
        if (box == null) {
            return null;
        }

        ItemEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (ItemEntity it : mc.world.getEntitiesByClass(ItemEntity.class, box, e -> true)) {
            if (it == null || !it.isAlive()) {
                continue;
            }

            ItemStack st = it.getStack();
            if (st == null || st.isEmpty() || !st.isOf(Items.GUNPOWDER)) {
                continue;
            }

            if (!ctx.isInsideAreaMargin(it.getBlockPos(), 2)) {
                continue;
            }

            if (!isLootSafe(ctx, it)) {
                continue;
            }

            double d = mc.player.getPos().squaredDistanceTo(it.getPos());
            if (!canSeePoint(it.getPos().add(0.0D, 0.08D, 0.0D))) {
                d += 4.0D;
            }

            if (d < bestScore) {
                bestScore = d;
                best = it;
            }
        }

        return best;
    }

    private boolean isLootSafe(Context ctx, ItemEntity item) {
        if (ctx == null || item == null || mc == null || mc.world == null) {
            return false;
        }

        Box danger = item.getBoundingBox().expand(LOOT_UNSAFE_RADIUS, 2.0D, LOOT_UNSAFE_RADIUS);
        for (CreeperEntity c : mc.world.getEntitiesByClass(CreeperEntity.class, danger, e -> true)) {
            if (c == null || !c.isAlive()) {
                continue;
            }
            if (!ctx.isInsideAreaMargin(c.getBlockPos(), 4)) {
                continue;
            }
            if (isExplodingLike(c)) {
                return false;
            }
            if (item.getPos().distanceTo(c.getPos()) <= LOOT_UNSAFE_RADIUS) {
                return false;
            }
        }

        return true;
    }

    private BlockPos computeRetreatGoal(Context ctx, CreeperEntity creeper) {
        if (ctx == null || creeper == null || mc == null || mc.player == null) {
            return null;
        }

        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return null;
        }

        Vec3d playerPos = mc.player.getPos();
        Vec3d creeperPos = creeper.getPos();

        Vec3d dir = playerPos.subtract(creeperPos);
        dir = new Vec3d(dir.x, 0.0D, dir.z);

        double len = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len < 1.0E-4D) {
            dir = new Vec3d(1.0D, 0.0D, 0.0D);
            len = 1.0D;
        }

        dir = new Vec3d(dir.x / len, 0.0D, dir.z / len);
        Vec3d raw = playerPos.add(dir.multiply(RETREAT_DIST));

        int x = MathHelper.clamp((int) Math.floor(raw.x), min.getX(), max.getX());
        int z = MathHelper.clamp((int) Math.floor(raw.z), min.getZ(), max.getZ());
        int y = MathHelper.clamp(mc.player.getBlockY(), min.getY() - 1, max.getY() + 2);

        BlockPos projected = new BlockPos(x, y, z);
        BlockPos best = findNearestStandableAround(ctx, projected, 6, 2);
        if (best != null) {
            return best.toImmutable();
        }

        return mc.player.getBlockPos().toImmutable();
    }

    private BlockPos findApproachPosForCreeper(Context ctx, CreeperEntity creeper) {
        if (ctx == null || creeper == null || mc == null || mc.world == null || mc.player == null) {
            return null;
        }

        BlockPos anchor = creeper.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 8; r++) {
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
                        if (!canAttackCreeperFromStandPos(p, creeper)) {
                            continue;
                        }

                        double dPlayer = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
                        double dMob = Vec3d.ofCenter(p).squaredDistanceTo(creeper.getPos());
                        double score = dPlayer + dMob * 0.25D;

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

    private boolean canAttackCreeperFromStandPos(BlockPos standPos, CreeperEntity creeper) {
        if (standPos == null || creeper == null) {
            return false;
        }

        Vec3d eye = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.62D, standPos.getZ() + 0.5D);

        for (Vec3d aim : getCreeperAimSamples(creeper)) {
            if (aim == null) {
                continue;
            }
            if (eye.squaredDistanceTo(aim) > BLOCK_REACH_SQ) {
                continue;
            }
            if (isSightLineClear(eye, aim, creeper.getBlockPos(), standPos)) {
                return true;
            }
        }

        return false;
    }

    private boolean canAttackCreeperNow(CreeperEntity creeper, Vec3d visibleAim) {
        if (mc == null || mc.player == null || creeper == null) {
            return false;
        }
        if (visibleAim == null) {
            return false;
        }
        if (mc.player.getEyePos().squaredDistanceTo(visibleAim) > ATTACK_REACH_SQ) {
            return false;
        }
        return true;
    }

    private Vec3d getBestVisibleCreeperAimPoint(CreeperEntity creeper) {
        if (creeper == null || mc == null || mc.player == null) {
            return null;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d best = null;
        double bestDist = Double.MAX_VALUE;

        for (Vec3d aim : getCreeperAimSamples(creeper)) {
            if (aim == null) {
                continue;
            }
            if (!isSightLineClear(eye, aim, creeper.getBlockPos(), mc.player.getBlockPos())) {
                continue;
            }
            double d = eye.squaredDistanceTo(aim);
            if (d < bestDist) {
                bestDist = d;
                best = aim;
            }
        }

        return best;
    }

    private Vec3d[] getCreeperAimSamples(CreeperEntity creeper) {
        if (creeper == null) {
            return new Vec3d[0];
        }

        double h = Math.max(1.0D, creeper.getHeight());
        Vec3d p = creeper.getPos();

        return new Vec3d[]{
                p.add(0.0D, Math.min(h * 0.78D, 1.35D), 0.0D),
                p.add(0.0D, Math.min(h * 0.55D, 1.00D), 0.0D),
                p.add(0.0D, Math.min(h * 0.30D, 0.65D), 0.0D)
        };
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
            sweepIndex = 0;
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
        sweepIndex = 0;
    }

    private Box areaBox(Context ctx, double expandXZ, double expandDown, double expandUp) {
        if (ctx == null) {
            return null;
        }
        BlockPos min = ctx.getMinPoint();
        BlockPos max = ctx.getMaxPoint();
        if (min == null || max == null) {
            return null;
        }
        return new Box(
                min.getX() - expandXZ, min.getY() - expandDown, min.getZ() - expandXZ,
                max.getX() + 1.0D + expandXZ, max.getY() + expandUp, max.getZ() + 1.0D + expandXZ
        );
    }

    private BlockPos findPickupGoalNearItem(Context ctx, Vec3d itemPos) {
        if (ctx == null || itemPos == null || mc == null || mc.world == null || mc.player == null) {
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

    private BlockPos findSweepStandable(Context ctx, int x, int z, int minY, int maxY) {
        if (ctx == null || mc == null || mc.world == null || mc.player == null) {
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

        for (int r = 1; r <= 4; r++) {
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

    private BlockPos findNearestStandableAround(Context ctx, BlockPos center, int radiusXZ, int radiusY) {
        if (ctx == null || center == null || mc == null || mc.player == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 0; r <= radiusXZ; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -radiusY; dy <= radiusY; dy++) {
                        BlockPos p = new BlockPos(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        if (!ctx.isInsideAreaMargin(p, 2)) {
                            continue;
                        }
                        if (!isStandablePos(p)) {
                            continue;
                        }

                        double score = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p));
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
        if (pos == null || mc == null || mc.world == null) {
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
        if (state == null || pos == null || mc == null || mc.world == null) {
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

    private boolean canSeePoint(Vec3d target) {
        if (target == null || mc == null || mc.player == null) {
            return false;
        }
        return isSightLineClear(mc.player.getEyePos(), target, null, mc.player.getBlockPos());
    }

    private boolean isSightLineClear(Vec3d from, Vec3d to, BlockPos targetBlock, BlockPos standPos) {
        if (from == null || to == null || mc == null || mc.world == null) {
            return false;
        }

        int steps = 40;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3d p = from.lerp(to, t);
            BlockPos bp = BlockPos.ofFloored(p);

            if (targetBlock != null) {
                if (bp.equals(targetBlock) || bp.equals(targetBlock.up())) {
                    continue;
                }
            }

            if (standPos != null && (bp.equals(standPos) || bp.equals(standPos.up()))) {
                continue;
            }

            BlockState state = mc.world.getBlockState(bp);
            if (!isPassableForSight(state, bp)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPassableForSight(BlockState state, BlockPos pos) {
        if (state == null || pos == null || mc == null || mc.world == null) {
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

    private void lookAtSmooth(Vec3d target) {
        if (mc == null || mc.player == null || target == null) {
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

    private boolean attackCooldownReady() {
        if (mc == null || mc.player == null) {
            return true;
        }
        try {
            return mc.player.getAttackCooldownProgress(0.0F) >= 0.88F;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean isExplodingLike(CreeperEntity c) {
        if (c == null) {
            return false;
        }

        try {
            Method m = c.getClass().getMethod("isIgnited");
            Object r = m.invoke(c);
            if (r instanceof Boolean && (Boolean) r) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method m = c.getClass().getMethod("getFuseSpeed");
            Object r = m.invoke(c);
            if (r instanceof Integer && (Integer) r > 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method m = c.getClass().getMethod("getCreeperState");
            Object r = m.invoke(c);
            if (r instanceof Integer && (Integer) r > 0) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private int countGunpowderInInventory() {
        if (mc == null || mc.player == null) {
            return 0;
        }

        PlayerInventory inv = mc.player.getInventory();
        if (inv == null) {
            return 0;
        }

        int count = 0;
        try {
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                if (stack.isOf(Items.GUNPOWDER)) {
                    count += stack.getCount();
                }
            }
        } catch (Throwable ignored) {
        }

        return count;
    }

    private int countFreeInventorySlots() {
        if (mc == null || mc.player == null) {
            return 0;
        }

        PlayerInventory inv = mc.player.getInventory();
        if (inv == null) {
            return 0;
        }

        int free = 0;
        try {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.getStack(i);
                if (stack == null || stack.isEmpty()) {
                    free++;
                }
            }
        } catch (Throwable ignored) {
        }

        return free;
    }
}