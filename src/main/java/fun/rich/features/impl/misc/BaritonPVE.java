package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.ItemBooleanSetting;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaritonPVE extends Module implements WheatFarmBot.Context, FarmCarrot.Context, FarmPotato.Context, FarmBeetroot.Context, FarmSugarCane.Context, FarmBamboo.Context, FarmMelon.Context, FarmPumpkin.Context, FarmChorus.Context, FarmGunpowder.Context, FarmApple.Context, FarmDiamond.Context {

    static final long DEBUG_COOLDOWN_MS = 15_000L;
    static final long DROP_INTERVAL_MS = 45L;
    static final int DROP_BURST_PER_TICK = 6;
    static final long BARITONE_GOTO_REFRESH_MS = 1100L;

    static final long STORAGE_PROGRESS_SAMPLE_MS = 220L;
    static final long STORAGE_STUCK_TIMEOUT_MS = 950L;
    static final long STORAGE_TEMP_GOAL_MAX_MS = 2200L;
    static final long STORAGE_REJECT_STAND_MS = 4000L;
    static final long DEPOSIT_SETTLE_MS = 170L;

    static final double STORAGE_REACHED_DIST = 2.35D;
    static final double STORAGE_MIN_REACHED_DIST = 0.95D;
    static final double STORAGE_STAND_REACHED_DIST = 1.75D;
    static final double STORAGE_STAND_KEEP_DIST = 2.30D;
    static final double WORK_GOAL_REACHED_DIST_SQ = 1.5625D;
    static final double RETURN_TO_FARM_REACHED_DIST = 3.25D;
    static final double STORAGE_PROGRESS_MIN_MOVE_SQ = 0.09D * 0.09D;

    static final double AREA_LINE_THICK = 0.10D;
    static final double AREA_LINE_MIN = 0.5D - AREA_LINE_THICK / 2.0D;
    static final double AREA_LINE_MAX = 0.5D + AREA_LINE_THICK / 2.0D;

    static final VoxelShape X_LINE = VoxelShapes.cuboid(0.0D, AREA_LINE_MIN, AREA_LINE_MIN, 1.0D, AREA_LINE_MAX, AREA_LINE_MAX);
    static final VoxelShape Y_LINE = VoxelShapes.cuboid(AREA_LINE_MIN, 0.0D, AREA_LINE_MIN, AREA_LINE_MAX, 1.0D, AREA_LINE_MAX);
    static final VoxelShape Z_LINE = VoxelShapes.cuboid(AREA_LINE_MIN, AREA_LINE_MIN, 0.0D, AREA_LINE_MAX, AREA_LINE_MAX, 1.0D);

    static final double MARKER_LINE_THICK = 0.16D;
    static final double MARKER_LINE_MIN = 0.5D - MARKER_LINE_THICK / 2.0D;
    static final double MARKER_LINE_MAX = 0.5D + MARKER_LINE_THICK / 2.0D;

    static final VoxelShape MX_LINE = VoxelShapes.cuboid(0.0D, MARKER_LINE_MIN, MARKER_LINE_MIN, 1.0D, MARKER_LINE_MAX, MARKER_LINE_MAX);
    static final VoxelShape MY_LINE = VoxelShapes.cuboid(MARKER_LINE_MIN, 0.0D, MARKER_LINE_MIN, MARKER_LINE_MAX, 1.0D, MARKER_LINE_MAX);
    static final VoxelShape MZ_LINE = VoxelShapes.cuboid(MARKER_LINE_MIN, MARKER_LINE_MIN, 0.0D, MARKER_LINE_MAX, AREA_LINE_MAX, 1.0D);
    static final VoxelShape MARKER_CORE = VoxelShapes.cuboid(0.28D, 0.28D, 0.28D, 0.72D, 0.72D, 0.72D);

    static final long EAT_HOLD_MS = 2600L;
    static final long EAT_RETRIGGER_MS = 250L;

    final MinecraftClient mc = MinecraftClient.getInstance();

    final ItemBooleanSetting farmWheat = new ItemBooleanSetting("Фарм пшеницы", "Собирать пшеницу", Items.WHEAT).setValue(false);
    final ItemBooleanSetting farmCarrot = new ItemBooleanSetting("Фарм морковки", "Собирать морковь", Items.CARROT).setValue(false);
    final ItemBooleanSetting farmPotato = new ItemBooleanSetting("Фарм картошки", "Собирать картошку", Items.POTATO).setValue(false);
    final ItemBooleanSetting farmBeetroot = new ItemBooleanSetting("Фарм свеклы", "Собирать свеклу", Items.BEETROOT).setValue(false);
    final ItemBooleanSetting farmSugarCane = new ItemBooleanSetting("Фарм тростника", "Собирать тростник", Items.SUGAR_CANE).setValue(false);
    final ItemBooleanSetting farmBamboo = new ItemBooleanSetting("Фарм бамбука", "Собирать бамбук", Items.BAMBOO).setValue(false);
    final ItemBooleanSetting farmMelon = new ItemBooleanSetting("Фарм арбузов", "Собирать арбузы", Items.MELON_SLICE).setValue(false);
    final ItemBooleanSetting farmPumpkin = new ItemBooleanSetting("Фарм тыквы", "Собирать тыквы", Items.PUMPKIN).setValue(false);
    final ItemBooleanSetting farmChorus = new ItemBooleanSetting("Фарм хорусов", "Собирать хорусы", Items.CHORUS_FRUIT).setValue(false);
    final ItemBooleanSetting farmApple = new ItemBooleanSetting("Фарм яблок", "Собирать яблоки", Items.APPLE).setValue(false);
    final ItemBooleanSetting farmGunpowder = new ItemBooleanSetting("Фарм пороха", "Собирать порох", Items.GUNPOWDER).setValue(false);
    final ItemBooleanSetting farmDiamond = new ItemBooleanSetting("Фарм алмазов", "Собирать алмазы", Items.DIAMOND).setValue(false);

    final WheatFarmBot wheatFarmBot = new WheatFarmBot();
    final FarmCarrot farmCarrotBot = new FarmCarrot();
    final FarmPotato farmPotatoBot = new FarmPotato();
    final FarmBeetroot farmBeetrootBot = new FarmBeetroot();
    final FarmSugarCane farmSugarCaneBot = new FarmSugarCane();
    final FarmBamboo farmBambooBot = new FarmBamboo();
    final FarmMelon farmMelonBot = new FarmMelon();
    final FarmPumpkin farmPumpkinBot = new FarmPumpkin();
    final FarmChorus farmChorusBot = new FarmChorus();
    final FarmApple farmAppleBot = new FarmApple();
    final FarmGunpowder farmGunpowderBot = new FarmGunpowder();
    final FarmDiamond farmDiamondBot = new FarmDiamond();

    BlockPos point1;
    BlockPos point2;
    final BlockPos[] skladPoints = new BlockPos[4];

    long lastDebugAt;
    long nextDropAt;
    long lastGotoRefreshAt;
    long depositReadyAt;

    BlockPos activeStorageTarget;
    BlockPos activeStorageStandPos;
    BlockPos currentPathGoal;

    Vec3d storageSamplePos;
    long storageSampleAt;
    long storageNoProgressSinceAt;
    int storageStuckStrikes;
    BlockPos storageTempGoal;
    long storageTempGoalUntilAt;
    BlockPos rejectedStorageStand;
    long rejectedStorageStandAt;

    Object cachedBaritonePrimary;
    Object cachedBaritoneCommandManager;
    boolean baritoneInitTried;
    boolean baritoneMissingWarned;

    State state = State.FARMING;

    boolean eatLock;
    long eatUntilAt;
    long nextEatRetriggerAt;
    Hand eatHand = Hand.MAIN_HAND;

    enum State {
        FARMING,
        GOING_TO_STORAGE,
        DEPOSITING,
        RETURNING_TO_FARM
    }

    public BaritonPVE() {
        super("BaritonPVE", "Baritone PVE", ModuleCategory.MISC);
        setup(
                farmWheat,
                farmCarrot,
                farmPotato,
                farmBeetroot,
                farmSugarCane,
                farmBamboo,
                farmMelon,
                farmPumpkin,
                farmChorus,
                farmApple,
                farmGunpowder,
                farmDiamond
        );
    }

    @Override
    public void activate() {
        super.activate();
        resetDebugState();
        resetRuntimeState();
        wheatFarmBot.activate();
        farmCarrotBot.activate();
        farmPotatoBot.activate();
        farmBeetrootBot.activate();
        farmSugarCaneBot.activate();
        farmBambooBot.activate();
        farmMelonBot.activate();
        farmPumpkinBot.activate();
        farmChorusBot.activate();
        farmAppleBot.activate();
        farmGunpowderBot.activate();
        farmDiamondBot.activate();
        stopEating(true);
        releaseUseKey();
        baritoneStop();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        resetDebugState();
        wheatFarmBot.deactivate();
        farmCarrotBot.deactivate();
        farmPotatoBot.deactivate();
        farmBeetrootBot.deactivate();
        farmSugarCaneBot.deactivate();
        farmBambooBot.deactivate();
        farmMelonBot.deactivate();
        farmPumpkinBot.deactivate();
        farmChorusBot.deactivate();
        farmAppleBot.deactivate();
        farmGunpowderBot.deactivate();
        farmDiamondBot.deactivate();
        resetRuntimeState();
        stopEating(true);
        releaseUseKey();
        baritoneStop();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!isState()) {
            stopEating(true);
            releaseUseKey();
            return;
        }

        if (mc == null || mc.player == null || mc.world == null) {
            stopEating(true);
            releaseUseKey();
            return;
        }

        if (!hasAnyFarmEnabled()) {
            debugOnce("Включите хотя бы 1 режим фарма");
            return;
        }

        if (!hasSelectedArea()) {
            debugOnce("Сначала выделите местность (.pve pos1 set / .pve pos2 set)");
            return;
        }

        if (!isAreaValid()) {
            debugOnce("Некорректная область: точки совпадают");
            return;
        }

        tickFarmLogic();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!isState()) {
            return;
        }

        if (mc == null || mc.player == null || mc.world == null) {
            return;
        }

        if (hasSelectedArea() && isAreaValid()) {
            renderSelectedArea();
            debugAreaHeightHint();
        }

        if (farmDiamond.isValue()) {
            try {
                farmDiamondBot.onWorldRender(e, this);
            } catch (Throwable ignored) {
            }
        }

        renderSkladMarkers();
    }

    private void tickFarmLogic() {
        if (mc.currentScreen != null) {
            stopEating(true);
            releaseUseKey();
            return;
        }

        if (tickEatingLock()) {
            return;
        }

        if (tryStartEatingIfHungry()) {
            return;
        } else {
            releaseUseKey();
        }

        releaseReachedWorkGoalIfNeeded();

        switch (state) {
            case FARMING -> tickStateFarming();
            case GOING_TO_STORAGE -> tickStateGoingToStorage();
            case DEPOSITING -> tickStateDepositing();
            case RETURNING_TO_FARM -> tickStateReturningToFarm();
        }
    }

    private boolean tickEatingLock() {
        if (!eatLock || mc == null || mc.player == null || mc.options == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now >= eatUntilAt) {
            if (!mc.player.isUsingItem() || !mc.player.canConsume(false)) {
                stopEating(false);
                releaseUseKey();
                return false;
            }
            eatUntilAt = now + 450L;
        }

        baritoneStop();
        currentPathGoal = null;

        boolean usingFood = false;
        try {
            if (mc.player.isUsingItem()) {
                ItemStack active = mc.player.getActiveItem();
                if (isForbiddenAutoEatFood(active)) {
                    stopEating(true);
                    releaseUseKey();
                    try {
                        mc.player.stopUsingItem();
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
                usingFood = isFoodStack(active);
            }
        } catch (Throwable ignored) {
        }

        if (!usingFood && now >= nextEatRetriggerAt) {
            nextEatRetriggerAt = now + EAT_RETRIGGER_MS;
            if (eatHand == Hand.OFF_HAND) {
                try {
                    if (mc.interactionManager != null) {
                        mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        mc.options.useKey.setPressed(true);
        return true;
    }

    private boolean tryStartEatingIfHungry() {
        if (mc == null || mc.player == null || mc.options == null) {
            return false;
        }

        if (!mc.player.canConsume(false)) {
            return false;
        }

        boolean usingFood = false;
        try {
            if (mc.player.isUsingItem()) {
                ItemStack active = mc.player.getActiveItem();
                if (isForbiddenAutoEatFood(active)) {
                    stopEating(true);
                    releaseUseKey();
                    try {
                        mc.player.stopUsingItem();
                    } catch (Throwable ignored) {
                    }
                    return false;
                }
                usingFood = isFoodStack(active);
            }
        } catch (Throwable ignored) {
        }

        if (usingFood) {
            beginEatLock(Hand.MAIN_HAND);
            return true;
        }

        ItemStack main = mc.player.getMainHandStack();
        ItemStack off = mc.player.getOffHandStack();

        if (isFoodStack(main)) {
            beginEatLock(Hand.MAIN_HAND);
            mc.options.useKey.setPressed(true);
            return true;
        }

        if (isFoodStack(off)) {
            beginEatLock(Hand.OFF_HAND);
            mc.options.useKey.setPressed(true);
            try {
                if (mc.interactionManager != null) {
                    mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
                }
            } catch (Throwable ignored) {
            }
            return true;
        }

        return false;
    }

    private void beginEatLock(Hand hand) {
        long now = System.currentTimeMillis();
        eatLock = true;
        eatHand = hand == null ? Hand.MAIN_HAND : hand;
        eatUntilAt = now + EAT_HOLD_MS;
        nextEatRetriggerAt = now + 10L;
        baritoneStop();
        currentPathGoal = null;
    }

    private void stopEating(boolean force) {
        if (!eatLock && !force) {
            return;
        }
        eatLock = false;
        eatUntilAt = 0L;
        nextEatRetriggerAt = 0L;
        eatHand = Hand.MAIN_HAND;
    }

    private void releaseReachedWorkGoalIfNeeded() {
        if (state != State.FARMING || currentPathGoal == null || mc == null || mc.player == null) {
            return;
        }
        if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(currentPathGoal)) <= WORK_GOAL_REACHED_DIST_SQ) {
            baritoneStop();
            currentPathGoal = null;
        }
    }

    private boolean hasImplementedFarmModeEnabled() {
        return farmWheat.isValue()
                || farmCarrot.isValue()
                || farmPotato.isValue()
                || farmBeetroot.isValue()
                || farmSugarCane.isValue()
                || farmBamboo.isValue()
                || farmMelon.isValue()
                || farmPumpkin.isValue()
                || farmChorus.isValue()
                || farmApple.isValue()
                || farmGunpowder.isValue()
                || farmDiamond.isValue();
    }

    private void tickSelectedFarmBot() {
        if (farmWheat.isValue()) {
            wheatFarmBot.tick(this);
            return;
        }

        if (farmCarrot.isValue()) {
            farmCarrotBot.tick(this);
            return;
        }

        if (farmPotato.isValue()) {
            farmPotatoBot.tick(this);
            return;
        }

        if (farmBeetroot.isValue()) {
            farmBeetrootBot.tick(this);
            return;
        }

        if (farmSugarCane.isValue()) {
            farmSugarCaneBot.tick(this);
            return;
        }

        if (farmBamboo.isValue()) {
            farmBambooBot.tick(this);
            return;
        }

        if (farmMelon.isValue()) {
            farmMelonBot.tick(this);
            return;
        }

        if (farmPumpkin.isValue()) {
            farmPumpkinBot.tick(this);
            return;
        }

        if (farmChorus.isValue()) {
            farmChorusBot.tick(this);
            return;
        }

        if (farmApple.isValue()) {
            farmAppleBot.tick(this);
            return;
        }

        if (farmGunpowder.isValue()) {
            farmGunpowderBot.tick(this);
            return;
        }

        if (farmDiamond.isValue()) {
            farmDiamondBot.tick(this);
        }
    }

    private boolean selectedBotShouldGoToStorageNow() {
        if (farmWheat.isValue()) {
            return wheatFarmBot.shouldGoToStorageNow();
        }

        if (farmCarrot.isValue()) {
            return farmCarrotBot.shouldGoToStorageNow();
        }

        if (farmPotato.isValue()) {
            return farmPotatoBot.shouldGoToStorageNow();
        }

        if (farmBeetroot.isValue()) {
            return farmBeetrootBot.shouldGoToStorageNow();
        }

        if (farmSugarCane.isValue()) {
            return farmSugarCaneBot.shouldGoToStorageNow();
        }

        if (farmBamboo.isValue()) {
            return farmBambooBot.shouldGoToStorageNow();
        }

        if (farmMelon.isValue()) {
            return farmMelonBot.shouldGoToStorageNow();
        }

        if (farmPumpkin.isValue()) {
            return farmPumpkinBot.shouldGoToStorageNow();
        }

        if (farmChorus.isValue()) {
            return farmChorusBot.shouldGoToStorageNow();
        }

        if (farmApple.isValue()) {
            return farmAppleBot.shouldGoToStorageNow();
        }

        if (farmGunpowder.isValue()) {
            return farmGunpowderBot.shouldGoToStorageNow();
        }

        if (farmDiamond.isValue()) {
            return farmDiamondBot.shouldGoToStorageNow();
        }

        return false;
    }

    private boolean selectedBotHasAnyCultureToDeposit() {
        if (farmWheat.isValue()) {
            return wheatFarmBot.hasAnyCultureToDeposit();
        }

        if (farmCarrot.isValue()) {
            return farmCarrotBot.hasAnyCultureToDeposit();
        }

        if (farmPotato.isValue()) {
            return farmPotatoBot.hasAnyCultureToDeposit();
        }

        if (farmBeetroot.isValue()) {
            return farmBeetrootBot.hasAnyCultureToDeposit();
        }

        if (farmSugarCane.isValue()) {
            return farmSugarCaneBot.hasAnyCultureToDeposit();
        }

        if (farmBamboo.isValue()) {
            return farmBambooBot.hasAnyCultureToDeposit();
        }

        if (farmMelon.isValue()) {
            return farmMelonBot.hasAnyCultureToDeposit();
        }

        if (farmPumpkin.isValue()) {
            return farmPumpkinBot.hasAnyCultureToDeposit();
        }

        if (farmChorus.isValue()) {
            return farmChorusBot.hasAnyCultureToDeposit();
        }

        if (farmApple.isValue()) {
            return farmAppleBot.hasAnyCultureToDeposit();
        }

        if (farmGunpowder.isValue()) {
            return farmGunpowderBot.hasAnyCultureToDeposit();
        }

        if (farmDiamond.isValue()) {
            return farmDiamondBot.hasAnyCultureToDeposit();
        }

        return false;
    }

    private boolean selectedBotDropNextDepositableCultureFromWholeInventory() {
        if (farmWheat.isValue()) {
            return wheatFarmBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmCarrot.isValue()) {
            return farmCarrotBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmPotato.isValue()) {
            return farmPotatoBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmBeetroot.isValue()) {
            return farmBeetrootBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmSugarCane.isValue()) {
            return farmSugarCaneBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmBamboo.isValue()) {
            return farmBambooBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmMelon.isValue()) {
            return farmMelonBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmPumpkin.isValue()) {
            return farmPumpkinBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmChorus.isValue()) {
            return farmChorusBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmApple.isValue()) {
            return farmAppleBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmGunpowder.isValue()) {
            return farmGunpowderBot.dropNextDepositableCultureFromWholeInventory();
        }

        if (farmDiamond.isValue()) {
            return farmDiamondBot.dropNextDepositableCultureFromWholeInventory();
        }

        return false;
    }

    private void tickStateFarming() {
        if (!hasImplementedFarmModeEnabled()) {
            debugOnce("Пока реализованы только 'Фарм пшеницы', 'Фарм морковки', 'Фарм картошки', 'Фарм свеклы', 'Фарм тростника', 'Фарм бамбука', 'Фарм арбузов', 'Фарм тыквы', 'Фарм хорусов', 'Фарм яблок', 'Фарм пороха' и 'Фарм алмазов'");
            stopWorkPathing();
            return;
        }

        tickSelectedFarmBot();

        if (selectedBotShouldGoToStorageNow()) {
            BlockPos nearest = getNearestStoragePoint();
            if (nearest == null) {
                debugOnce("Для сдачи урожая поставьте точку склада (.pve sklad 1 set)");
            } else {
                startGoingToStorage(nearest);
                return;
            }
        }

        if (currentPathGoal != null) {
            return;
        }
    }

    private void tickStateGoingToStorage() {
        if (activeStorageTarget == null) {
            state = State.RETURNING_TO_FARM;
            return;
        }

        BlockPos resolvedStorage = resolveStorageTarget(activeStorageTarget);
        if (resolvedStorage == null) {
            debugOnce("Точка склада должна стоять на hopper/chest/barrel (или рядом/над ним)");
            activeStorageTarget = null;
            activeStorageStandPos = null;
            currentPathGoal = null;
            baritoneStop();
            state = State.RETURNING_TO_FARM;
            return;
        }

        if (!resolvedStorage.equals(activeStorageTarget)) {
            activeStorageTarget = resolvedStorage.toImmutable();
            activeStorageStandPos = null;
        }

        if (!selectedBotHasAnyCultureToDeposit()) {
            state = State.RETURNING_TO_FARM;
            return;
        }

        refreshStorageStandPosIfNeeded();

        if (canDepositFromCurrentPosition()) {
            baritoneStop();
            currentPathGoal = null;
            nextDropAt = 0L;
            depositReadyAt = System.currentTimeMillis() + DEPOSIT_SETTLE_MS;
            state = State.DEPOSITING;
            return;
        }

        BlockPos pathGoal = getStoragePathGoal();
        if (pathGoal == null) {
            state = State.RETURNING_TO_FARM;
            return;
        }

        double distToStand = mc.player.getPos().distanceTo(Vec3d.ofCenter(pathGoal));
        if (distToStand <= STORAGE_STAND_REACHED_DIST && activeStorageStandPos != null) {
            refreshStorageStandPosIfNeeded();
            if (canDepositFromCurrentPosition()) {
                baritoneStop();
                currentPathGoal = null;
                nextDropAt = 0L;
                depositReadyAt = System.currentTimeMillis() + DEPOSIT_SETTLE_MS;
                state = State.DEPOSITING;
                return;
            }
        }

        if (tickStorageSelfRecovery(pathGoal)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGotoRefreshAt >= BARITONE_GOTO_REFRESH_MS || currentPathGoal == null || !currentPathGoal.equals(pathGoal)) {
            currentPathGoal = pathGoal.toImmutable();
            baritoneGoto(pathGoal);
        }
    }

    private void tickStateDepositing() {
        if (activeStorageTarget == null) {
            state = State.RETURNING_TO_FARM;
            return;
        }

        BlockPos resolvedStorage = resolveStorageTarget(activeStorageTarget);
        if (resolvedStorage == null) {
            debugOnce("Точка склада должна стоять на hopper/chest/barrel (или рядом/над ним)");
            activeStorageTarget = null;
            activeStorageStandPos = null;
            currentPathGoal = null;
            baritoneStop();
            state = State.RETURNING_TO_FARM;
            return;
        }

        if (!resolvedStorage.equals(activeStorageTarget)) {
            activeStorageTarget = resolvedStorage.toImmutable();
            activeStorageStandPos = null;
        }

        refreshStorageStandPosIfNeeded();

        if (!canDepositFromCurrentPosition()) {
            state = State.GOING_TO_STORAGE;
            return;
        }

        baritoneStop();
        currentPathGoal = null;

        long now = System.currentTimeMillis();

        faceStorageDropPoint();

        if (now < depositReadyAt) {
            return;
        }

        try {
            Vec3d vel = mc.player != null ? mc.player.getVelocity() : Vec3d.ZERO;
            if (vel != null && vel.horizontalLengthSquared() > 0.01D && now < depositReadyAt + 350L) {
                return;
            }
        } catch (Throwable ignored) {
        }

        if (now < nextDropAt) {
            return;
        }

        int dropped = 0;
        for (int i = 0; i < DROP_BURST_PER_TICK; i++) {
            faceStorageDropPoint();
            if (!selectedBotDropNextDepositableCultureFromWholeInventory()) {
                break;
            }
            dropped++;
        }

        if (dropped > 0) {
            nextDropAt = now + DROP_INTERVAL_MS;
            return;
        }

        state = State.RETURNING_TO_FARM;
    }

    private void tickStateReturningToFarm() {
        if (selectedBotShouldGoToStorageNow() && selectedBotHasAnyCultureToDeposit()) {
            BlockPos nearest = getNearestStoragePoint();
            if (nearest != null) {
                startGoingToStorage(nearest);
                return;
            }
        }

        BlockPos returnGoal = getFarmReturnGoal();
        if (returnGoal != null) {
            double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(returnGoal));
            if (dist > RETURN_TO_FARM_REACHED_DIST) {
                long now = System.currentTimeMillis();
                if (now - lastGotoRefreshAt >= BARITONE_GOTO_REFRESH_MS || currentPathGoal == null || !currentPathGoal.equals(returnGoal)) {
                    currentPathGoal = returnGoal.toImmutable();
                    baritoneGoto(returnGoal);
                }
                return;
            }
        }

        currentPathGoal = null;
        state = State.FARMING;
    }

    private void startGoingToStorage(BlockPos storagePoint) {
        if (storagePoint == null) {
            return;
        }

        BlockPos resolved = resolveStorageTarget(storagePoint);
        if (resolved == null) {
            debugOnce("Точка склада должна стоять на hopper/chest/barrel (или рядом/над ним)");
            return;
        }

        activeStorageTarget = resolved.toImmutable();
        activeStorageStandPos = findStorageStandPos(activeStorageTarget);
        currentPathGoal = null;
        depositReadyAt = 0L;
        resetStorageRecoveryState();
        baritoneStop();

        BlockPos goal = getStoragePathGoal();
        if (goal != null) {
            baritoneGoto(goal);
            currentPathGoal = goal.toImmutable();
        }

        state = State.GOING_TO_STORAGE;
    }

    private void refreshStorageStandPosIfNeeded() {
        if (activeStorageTarget == null) {
            activeStorageStandPos = null;
            return;
        }

        BlockPos resolved = resolveStorageTarget(activeStorageTarget);
        if (resolved == null) {
            activeStorageStandPos = null;
            return;
        }

        if (!resolved.equals(activeStorageTarget)) {
            activeStorageTarget = resolved.toImmutable();
            activeStorageStandPos = null;
        }

        boolean hopper = false;
        try {
            hopper = mc != null && mc.world != null && mc.world.getBlockState(activeStorageTarget).isOf(Blocks.HOPPER);
        } catch (Throwable ignored) {
        }

        if (activeStorageStandPos != null && isStorageStandablePos(activeStorageStandPos) && isStorageDropLineClear(activeStorageStandPos)) {
            if (!hopper) {
                return;
            }

            if (isTightHopperStand(activeStorageStandPos, activeStorageTarget)) {
                return;
            }
        }

        activeStorageStandPos = findStorageStandPos(activeStorageTarget);
    }

    private boolean isTightHopperStand(BlockPos standPos, BlockPos hopperPos) {
        if (standPos == null || hopperPos == null) {
            return false;
        }

        int ax = Math.abs(standPos.getX() - hopperPos.getX());
        int az = Math.abs(standPos.getZ() - hopperPos.getZ());
        int ay = Math.abs(standPos.getY() - hopperPos.getY());
        int manhattanXZ = ax + az;

        if (ax == 1 && az == 1) {
            return false;
        }

        return manhattanXZ == 1 && ay <= 1;
    }

    private BlockPos getStoragePathGoal() {
        if (activeStorageStandPos != null) {
            return activeStorageStandPos;
        }

        BlockPos approach = findStorageApproachPos(activeStorageTarget);
        if (approach != null) {
            return approach;
        }

        return null;
    }

    private boolean canDepositFromCurrentPosition() {
        if (mc == null || mc.player == null || mc.world == null || activeStorageTarget == null) {
            return false;
        }

        if (!isSupportedStorageTarget(activeStorageTarget)) {
            return false;
        }

        Vec3d dropPoint = getStorageDropPoint();
        double distToStorage = mc.player.getPos().distanceTo(dropPoint);

        BlockState state = mc.world.getBlockState(activeStorageTarget);
        boolean hopper = state.isOf(Blocks.HOPPER);

        double maxDist = hopper ? 1.68D : STORAGE_REACHED_DIST;
        double minDist = hopper ? 0.35D : STORAGE_MIN_REACHED_DIST;

        if (distToStorage > maxDist) {
            return false;
        }

        if (distToStorage < minDist) {
            return false;
        }

        if (hopper) {
            Vec3d c = Vec3d.ofCenter(activeStorageTarget);
            double dx = Math.abs(mc.player.getPos().x - c.x);
            double dz = Math.abs(mc.player.getPos().z - c.z);

            if (dx > 1.35D || dz > 1.35D) {
                return false;
            }
        }

        return isStorageDropLineClearFromPlayer();
    }

    private boolean tickStorageSelfRecovery(BlockPos mainGoal) {
        if (mc == null || mc.player == null || mainGoal == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        Vec3d playerPos = mc.player.getPos();

        if (storageTempGoal != null) {
            if (now >= storageTempGoalUntilAt || playerPos.squaredDistanceTo(Vec3d.ofCenter(storageTempGoal)) <= 2.0D) {
                storageTempGoal = null;
                storageTempGoalUntilAt = 0L;
                storageSamplePos = playerPos;
                storageSampleAt = now;
                storageNoProgressSinceAt = now;
                return false;
            }

            if (currentPathGoal == null || !currentPathGoal.equals(storageTempGoal) || now - lastGotoRefreshAt >= Math.max(350L, BARITONE_GOTO_REFRESH_MS / 2L)) {
                currentPathGoal = storageTempGoal.toImmutable();
                baritoneGoto(storageTempGoal);
            }
            return true;
        }

        if (storageSamplePos == null) {
            storageSamplePos = playerPos;
            storageSampleAt = now;
            storageNoProgressSinceAt = now;
            return false;
        }

        if (now - storageSampleAt >= STORAGE_PROGRESS_SAMPLE_MS) {
            double movedSq = playerPos.squaredDistanceTo(storageSamplePos);
            storageSamplePos = playerPos;
            storageSampleAt = now;

            if (movedSq >= STORAGE_PROGRESS_MIN_MOVE_SQ) {
                storageNoProgressSinceAt = now;
                if (storageStuckStrikes > 0) {
                    storageStuckStrikes--;
                }
            }
        }

        if (currentPathGoal != null && playerPos.squaredDistanceTo(Vec3d.ofCenter(currentPathGoal)) <= 2.25D) {
            storageNoProgressSinceAt = now;
            return false;
        }

        if (now - storageNoProgressSinceAt < STORAGE_STUCK_TIMEOUT_MS) {
            return false;
        }

        storageNoProgressSinceAt = now;
        storageStuckStrikes++;

        if (activeStorageStandPos != null) {
            rejectStorageStand(activeStorageStandPos);
            activeStorageStandPos = null;
        }

        BlockPos retreat = findStorageRetreatPos();
        if (retreat != null) {
            storageTempGoal = retreat.toImmutable();
            storageTempGoalUntilAt = now + STORAGE_TEMP_GOAL_MAX_MS + Math.min(900L, storageStuckStrikes * 180L);
            currentPathGoal = null;
            baritoneStop();
            currentPathGoal = storageTempGoal;
            baritoneGoto(storageTempGoal);
            return true;
        }

        baritoneStop();
        currentPathGoal = null;
        lastGotoRefreshAt = 0L;
        return false;
    }

    private BlockPos findStorageRetreatPos() {
        if (mc == null || mc.player == null || mc.world == null || activeStorageTarget == null) {
            return null;
        }

        Vec3d playerPos = mc.player.getPos();
        Vec3d storageCenter = Vec3d.ofCenter(activeStorageTarget);

        double currentStorageDistSq = playerPos.squaredDistanceTo(storageCenter);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos anchor = mc.player.getBlockPos();

        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }

                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos p = new BlockPos(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);

                        if (!isStorageStandablePos(p)) {
                            continue;
                        }

                        Vec3d c = Vec3d.ofCenter(p);
                        double dPlayerSq = playerPos.squaredDistanceTo(c);
                        double dStorageSq = storageCenter.squaredDistanceTo(c);

                        if (dPlayerSq < 1.1D * 1.1D || dPlayerSq > 6.8D * 6.8D) {
                            continue;
                        }

                        if (dStorageSq <= currentStorageDistSq + 0.9D) {
                            continue;
                        }

                        double score = dPlayerSq * 0.55D - dStorageSq * 0.65D;

                        if (activeStorageStandPos != null) {
                            score += c.squaredDistanceTo(Vec3d.ofCenter(activeStorageStandPos)) * 0.08D;
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

    private void resetStorageRecoveryState() {
        storageSamplePos = null;
        storageSampleAt = 0L;
        storageNoProgressSinceAt = 0L;
        storageStuckStrikes = 0;
        storageTempGoal = null;
        storageTempGoalUntilAt = 0L;
        rejectedStorageStand = null;
        rejectedStorageStandAt = 0L;
    }

    private void rejectStorageStand(BlockPos pos) {
        if (pos == null) {
            return;
        }
        rejectedStorageStand = pos.toImmutable();
        rejectedStorageStandAt = System.currentTimeMillis();
    }

    private boolean isRejectedStorageStand(BlockPos pos) {
        if (pos == null || rejectedStorageStand == null) {
            return false;
        }

        if (System.currentTimeMillis() - rejectedStorageStandAt > STORAGE_REJECT_STAND_MS) {
            return false;
        }

        int dx = Math.abs(pos.getX() - rejectedStorageStand.getX());
        int dy = Math.abs(pos.getY() - rejectedStorageStand.getY());
        int dz = Math.abs(pos.getZ() - rejectedStorageStand.getZ());
        return dx <= 1 && dy <= 1 && dz <= 1;
    }

    private BlockPos findStorageStandPos(BlockPos storagePos) {
        if (storagePos == null || mc == null || mc.world == null) {
            return null;
        }

        boolean hopperTarget = mc.world.getBlockState(storagePos).isOf(Blocks.HOPPER);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int pass = 0; pass < 4; pass++) {
            double maxDistToStorage = pass == 0 ? 2.2D : (pass == 1 ? 3.2D : (pass == 2 ? 4.8D : 6.2D));
            double maxDistSq = maxDistToStorage * maxDistToStorage;
            best = null;
            bestScore = Double.MAX_VALUE;

            for (int r = 0; r <= 8; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r != 0 && Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }

                        for (int dy = -3; dy <= 4; dy++) {
                            BlockPos p = new BlockPos(storagePos.getX() + dx, storagePos.getY() + dy, storagePos.getZ() + dz);

                            if (isRejectedStorageStand(p)) {
                                continue;
                            }

                            if (!isStorageStandablePos(p)) {
                                continue;
                            }

                            int ax = Math.abs(p.getX() - storagePos.getX());
                            int az = Math.abs(p.getZ() - storagePos.getZ());
                            int ay = Math.abs(p.getY() - storagePos.getY());
                            int manhattanXZ = ax + az;

                            if (hopperTarget) {
                                if (pass <= 1) {
                                    if (ax == 1 && az == 1) {
                                        continue;
                                    }
                                    if (manhattanXZ != 1 || ay > 1) {
                                        continue;
                                    }
                                } else if (pass == 2) {
                                    if (manhattanXZ > 2 || ay > 2) {
                                        continue;
                                    }
                                }
                            }

                            double dStorage = Vec3d.ofCenter(p).squaredDistanceTo(Vec3d.ofCenter(storagePos));
                            if (dStorage > maxDistSq) {
                                continue;
                            }

                            if (!isStorageDropLineClear(p)) {
                                continue;
                            }

                            double score = dStorage;

                            if (mc.player != null) {
                                score += mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) * 0.07D;
                            }

                            BlockState below = mc.world.getBlockState(p.down());
                            if (below.isOf(Blocks.FARMLAND)) {
                                score += 50.0D;
                            }

                            if (p.getY() < storagePos.getY() - 1 || p.getY() > storagePos.getY() + 2) {
                                score += 4.0D;
                            }

                            double playerToStorageSq = mc.player != null ? mc.player.getPos().squaredDistanceTo(getStorageDropPoint()) : 0.0D;
                            if (playerToStorageSq > 0.0D && dStorage < 1.15D * 1.15D) {
                                score += 6.0D;
                            }

                            if (hopperTarget) {
                                if (manhattanXZ == 1 && ay == 0) {
                                    score -= 18.0D;
                                } else if (manhattanXZ == 1 && ay <= 1) {
                                    score -= 10.0D;
                                }

                                if (ax == 1 && az == 1) {
                                    score += 20.0D;
                                }

                                if (manhattanXZ == 0) {
                                    score += 18.0D;
                                }

                                if (manhattanXZ >= 2) {
                                    score += 14.0D;
                                }

                                if (ay > 1) {
                                    score += 10.0D;
                                }
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
        }

        return null;
    }

    private BlockPos findStorageApproachPos(BlockPos storagePos) {
        if (storagePos == null || mc == null || mc.world == null) {
            return null;
        }

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int pass = 0; pass < 2; pass++) {
            best = null;
            bestScore = Double.MAX_VALUE;

            for (int r = 1; r <= 12; r++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }

                        for (int dy = -4; dy <= 5; dy++) {
                            BlockPos p = new BlockPos(storagePos.getX() + dx, storagePos.getY() + dy, storagePos.getZ() + dz);

                            if (isRejectedStorageStand(p)) {
                                continue;
                            }

                            if (!isStorageStandablePos(p)) {
                                continue;
                            }

                            double dStorage = Vec3d.ofCenter(p).squaredDistanceTo(Vec3d.ofCenter(storagePos));
                            if (dStorage > (12.5D * 12.5D)) {
                                continue;
                            }

                            boolean clearDropLine = isStorageDropLineClear(p);

                            if (pass == 0 && !clearDropLine) {
                                continue;
                            }

                            if (!clearDropLine && dStorage < (3.2D * 3.2D)) {
                                continue;
                            }

                            double score = 0.0D;

                            if (mc.player != null) {
                                score += mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) * (clearDropLine ? 0.22D : 0.35D);
                            }

                            score += dStorage * (clearDropLine ? 0.55D : 0.95D);

                            BlockState below = mc.world.getBlockState(p.down());
                            if (below.isOf(Blocks.FARMLAND)) {
                                score += 120.0D;
                            }

                            if (p.getY() < storagePos.getY() - 1 || p.getY() > storagePos.getY() + 2) {
                                score += 8.0D;
                            }

                            if (!clearDropLine) {
                                score += 28.0D;
                            } else {
                                score -= 14.0D;
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
        }

        return null;
    }

    private boolean isStorageStandablePos(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState below = mc.world.getBlockState(pos.down());

        if (!isPassableForStorage(feet, pos)) {
            return false;
        }
        if (!isPassableForStorage(head, pos.up())) {
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

    private boolean isPassableForStorage(BlockState state, BlockPos pos) {
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

    private boolean isStorageDropLineClearFromPlayer() {
        if (mc == null || mc.player == null || activeStorageTarget == null) {
            return false;
        }
        return isStorageDropLineClearFrom(mc.player.getEyePos(), mc.player.getBlockPos());
    }

    private boolean isStorageDropLineClear(BlockPos standPos) {
        if (standPos == null || activeStorageTarget == null || mc == null || mc.world == null) {
            return false;
        }
        Vec3d from = new Vec3d(standPos.getX() + 0.5D, standPos.getY() + 1.55D, standPos.getZ() + 0.5D);
        return isStorageDropLineClearFrom(from, standPos);
    }

    private boolean isStorageDropLineClearFrom(Vec3d from, BlockPos standPos) {
        if (from == null || activeStorageTarget == null || mc == null || mc.world == null) {
            return false;
        }

        Vec3d to = getStorageDropPoint();

        int steps = 32;
        for (int i = 1; i < steps; i++) {
            double t = i / (double) steps;
            Vec3d p = from.lerp(to, t);
            BlockPos bp = BlockPos.ofFloored(p);

            if (bp.equals(activeStorageTarget)) {
                continue;
            }

            if (standPos != null && (bp.equals(standPos) || bp.equals(standPos.up()))) {
                continue;
            }

            BlockState state = mc.world.getBlockState(bp);
            if (!isPassableForDropLine(state, bp)) {
                return false;
            }
        }

        return true;
    }

    private boolean isPassableForDropLine(BlockState state, BlockPos pos) {
        if (state == null || pos == null || mc == null || mc.world == null) {
            return false;
        }

        if (state.isAir()) {
            return true;
        }

        if (state.isOf(Blocks.WHEAT)
                || state.isOf(Blocks.CARROTS)
                || state.isOf(Blocks.POTATOES)
                || state.isOf(Blocks.BEETROOTS)
                || state.isOf(Blocks.NETHER_WART)
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.BAMBOO)) {
            return true;
        }

        try {
            return state.getCollisionShape(mc.world, pos).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSupportedStorageTarget(BlockPos pos) {
        return resolveStorageTarget(pos) != null;
    }

    private BlockPos resolveStorageTarget(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return null;
        }

        if (isSupportedStorageBlock(pos)) {
            return pos.toImmutable();
        }

        BlockPos down = pos.down();
        if (isSupportedStorageBlock(down)) {
            return down.toImmutable();
        }

        BlockPos up = pos.up();
        if (isSupportedStorageBlock(up)) {
            return up.toImmutable();
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos p = new BlockPos(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (isSupportedStorageBlock(p)) {
                        return p.toImmutable();
                    }
                }
            }
        }

        return null;
    }

    private boolean isSupportedStorageBlock(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.HOPPER)
                || state.isOf(Blocks.CHEST)
                || state.isOf(Blocks.TRAPPED_CHEST)
                || state.isOf(Blocks.BARREL);
    }

    private Vec3d getStorageDropPoint() {
        if (activeStorageTarget == null || mc == null || mc.world == null) {
            return activeStorageTarget == null ? Vec3d.ZERO : Vec3d.ofCenter(activeStorageTarget);
        }

        BlockState state = mc.world.getBlockState(activeStorageTarget);

        if (state.isOf(Blocks.HOPPER)) {
            double x = activeStorageTarget.getX() + 0.5D;
            double y = activeStorageTarget.getY() + 1.18D;
            double z = activeStorageTarget.getZ() + 0.5D;

            Vec3d ref = null;
            if (mc.player != null) {
                ref = mc.player.getEyePos();
            } else if (activeStorageStandPos != null) {
                ref = new Vec3d(activeStorageStandPos.getX() + 0.5D, activeStorageStandPos.getY() + 1.55D, activeStorageStandPos.getZ() + 0.5D);
            }

            if (ref != null) {
                double vx = x - ref.x;
                double vz = z - ref.z;
                double horiz = Math.sqrt(vx * vx + vz * vz);

                if (horiz > 1.0E-4D) {
                    double nx = vx / horiz;
                    double nz = vz / horiz;

                    double push = horiz >= 1.45D ? 0.16D : (horiz >= 1.05D ? 0.12D : 0.07D);

                    x += nx * push;
                    z += nz * push;

                    if (horiz >= 1.35D) {
                        y += 0.03D;
                    }
                }
            }

            return new Vec3d(x, y, z);
        }

        if (state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST) || state.isOf(Blocks.BARREL)) {
            return new Vec3d(activeStorageTarget.getX() + 0.5D, activeStorageTarget.getY() + 0.78D, activeStorageTarget.getZ() + 0.5D);
        }

        return new Vec3d(activeStorageTarget.getX() + 0.5D, activeStorageTarget.getY() + 0.35D, activeStorageTarget.getZ() + 0.5D);
    }

    private void faceStorageDropPoint() {
        if (mc == null || mc.player == null || activeStorageTarget == null) {
            return;
        }

        Vec3d target = getStorageDropPoint();
        Vec3d eye = mc.player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.max(0.0001D, horiz))));

        BlockState state = mc.world != null ? mc.world.getBlockState(activeStorageTarget) : null;

        if (state != null && state.isOf(Blocks.HOPPER)) {
            if (horiz > 1.55D) {
                pitch -= 6.0F;
            } else if (horiz > 1.25D) {
                pitch -= 4.0F;
            } else if (horiz > 1.00D) {
                pitch -= 2.0F;
            }

            if (activeStorageStandPos != null) {
                int ax = Math.abs(activeStorageStandPos.getX() - activeStorageTarget.getX());
                int az = Math.abs(activeStorageStandPos.getZ() - activeStorageTarget.getZ());
                if (ax == 1 && az == 1) {
                    pitch -= 2.0F;
                }
            }

            if (pitch < 16.0F) pitch = 16.0F;
            if (pitch > 74.0F) pitch = 74.0F;
        } else {
            if (pitch < 40.0F) pitch = 40.0F;
            if (pitch > 89.0F) pitch = 89.0F;
        }

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void releaseUseKey() {
        try {
            if (mc != null && mc.options != null) {
                mc.options.useKey.setPressed(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void pathToWorkGoal(BlockPos goal) {
        if (goal == null || mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (currentPathGoal != null && currentPathGoal.equals(goal) && now - lastGotoRefreshAt < BARITONE_GOTO_REFRESH_MS) {
            return;
        }

        currentPathGoal = goal.toImmutable();
        baritoneGoto(currentPathGoal);
    }

    @Override
    public void stopWorkPathing() {
        if (currentPathGoal != null) {
            baritoneStop();
            currentPathGoal = null;
        }
    }

    @Override
    public boolean isInsideAreaMargin(BlockPos pos, int margin) {
        if (pos == null || !hasSelectedArea()) {
            return false;
        }
        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null) {
            return false;
        }
        return pos.getX() >= min.getX() - margin && pos.getX() <= max.getX() + margin
                && pos.getY() >= min.getY() - 2 && pos.getY() <= max.getY() + 2
                && pos.getZ() >= min.getZ() - margin && pos.getZ() <= max.getZ() + margin;
    }

    private void baritoneGoto(BlockPos pos) {
        if (pos == null) {
            return;
        }
        if (executeBaritoneCommand("#goto " + pos.getX() + " " + pos.getY() + " " + pos.getZ())) {
            lastGotoRefreshAt = System.currentTimeMillis();
        }
    }

    private void baritoneStop() {
        executeBaritoneCommand("#stop");
    }

    private boolean executeBaritoneCommand(String commandRaw) {
        if (commandRaw == null || commandRaw.isBlank()) {
            return false;
        }

        try {
            Object primary = getBaritonePrimary();
            if (primary == null) {
                warnBaritoneMissing();
                return false;
            }

            String noPrefix = commandRaw.startsWith("#") ? commandRaw.substring(1) : commandRaw;

            if (tryDirectBaritoneFallback(primary, commandRaw)) return true;
            if (tryDirectBaritoneFallback(primary, noPrefix)) return true;

            Object manager = getBaritoneCommandManager(primary);
            if (manager != null) {
                if (invokeCommandLike(manager, commandRaw)) return true;
                if (invokeCommandLike(manager, noPrefix)) return true;
            }

            if (invokeCommandLike(primary, commandRaw)) return true;
            if (invokeCommandLike(primary, noPrefix)) return true;
        } catch (Throwable ignored) {
        }

        warnBaritoneMissing();
        return false;
    }

    private void warnBaritoneMissing() {
        if (!baritoneMissingWarned) {
            baritoneMissingWarned = true;
            debugOnce("Baritone API не найден/не отвечает. Команды не выполняются");
        }
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

        return null;
    }

    private Object getBaritoneCommandManager(Object primary) {
        if (cachedBaritoneCommandManager != null) {
            return cachedBaritoneCommandManager;
        }
        if (primary == null) {
            return null;
        }

        String[] getters = {"getCommandManager", "getCommandSystem", "getChatControl"};
        for (String name : getters) {
            try {
                Method m = primary.getClass().getMethod(name);
                Object o = m.invoke(primary);
                if (o != null) {
                    cachedBaritoneCommandManager = o;
                    return o;
                }
            } catch (Throwable ignored) {
            }
        }

        for (Method m : primary.getClass().getMethods()) {
            if (m.getParameterCount() == 0) {
                try {
                    Object o = m.invoke(primary);
                    if (o == null) continue;
                    if (hasCommandExecuteMethod(o.getClass())) {
                        cachedBaritoneCommandManager = o;
                        return o;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return null;
    }

    private boolean hasCommandExecuteMethod(Class<?> cls) {
        for (Method m : cls.getMethods()) {
            String n = m.getName().toLowerCase();
            if (!(n.contains("execute") || n.contains("command") || n.contains("run"))) {
                continue;
            }
            if (m.getParameterCount() == 1) {
                Class<?> p = m.getParameterTypes()[0];
                if (p == String.class || p == String[].class || CharSequence.class.isAssignableFrom(p)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean invokeCommandLike(Object target, String command) {
        if (target == null || command == null) {
            return false;
        }

        for (Method m : target.getClass().getMethods()) {
            String name = m.getName().toLowerCase();
            if (!(name.contains("execute") || name.contains("command") || name.contains("run"))) {
                continue;
            }

            try {
                if (m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    if (p == String.class || CharSequence.class.isAssignableFrom(p)) {
                        m.invoke(target, command);
                        return true;
                    }
                    if (p == String[].class) {
                        m.invoke(target, (Object) command.trim().split("\\s+"));
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean tryDirectBaritoneFallback(Object primary, String command) {
        if (primary == null || command == null) {
            return false;
        }

        String c = command.trim();
        if (c.startsWith("#")) {
            c = c.substring(1).trim();
        }
        if (c.isEmpty()) {
            return false;
        }

        String[] a = c.split("\\s+");
        if (a.length == 0) {
            return false;
        }

        if (a[0].equalsIgnoreCase("stop")) {
            boolean ok = false;
            try {
                Object pb = invokeNoArg(primary, "getPathingBehavior");
                if (pb != null && invokeNoArgAny(pb, "cancelEverything", "forceCancel", "cancel", "stop")) {
                    ok = true;
                }
            } catch (Throwable ignored) {
            }

            try {
                Object gp = invokeNoArg(primary, "getCustomGoalProcess");
                if (gp != null) {
                    if (invokeGoalSetter(gp, "setGoal", null)) {
                        ok = true;
                    }
                    if (invokeNoArgAny(gp, "path")) {
                        ok = true;
                    }
                }
            } catch (Throwable ignored) {
            }

            return ok;
        }

        if (a[0].equalsIgnoreCase("goto") && a.length >= 4) {
            try {
                int x = Integer.parseInt(a[1]);
                int y = Integer.parseInt(a[2]);
                int z = Integer.parseInt(a[3]);

                Object process = invokeNoArg(primary, "getCustomGoalProcess");
                if (process == null) {
                    return false;
                }

                Class<?> goalBlockCls = Class.forName("baritone.api.pathing.goals.GoalBlock");
                Object goal = null;

                for (Constructor<?> ctor : goalBlockCls.getConstructors()) {
                    try {
                        Class<?>[] p = ctor.getParameterTypes();
                        if (p.length == 3 && p[0] == int.class && p[1] == int.class && p[2] == int.class) {
                            goal = ctor.newInstance(x, y, z);
                            break;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                if (goal == null) {
                    return false;
                }

                if (invokeGoalSetter(process, "setGoalAndPath", goal)) {
                    return true;
                }

                if (invokeGoalSetter(process, "path", goal)) {
                    return true;
                }

                if (invokeGoalSetter(process, "setGoal", goal)) {
                    invokeNoArgAny(process, "path");
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private boolean invokeGoalSetter(Object target, String methodName, Object arg) {
        if (target == null || methodName == null) {
            return false;
        }

        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(methodName)) {
                continue;
            }

            try {
                if (m.getParameterCount() == 0 && arg == null) {
                    m.invoke(target);
                    return true;
                }

                if (m.getParameterCount() != 1) {
                    continue;
                }

                Class<?> p = m.getParameterTypes()[0];

                if (arg == null) {
                    if (!p.isPrimitive()) {
                        m.invoke(target, new Object[]{null});
                        return true;
                    }
                    continue;
                }

                if (p.isAssignableFrom(arg.getClass())) {
                    m.invoke(target, arg);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private Object invokeNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean invokeNoArgAny(Object target, String... names) {
        for (String n : names) {
            try {
                Method m = target.getClass().getMethod(n);
                m.invoke(target);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private BlockPos getFarmCenter() {
        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null) {
            return null;
        }
        return new BlockPos(
                (min.getX() + max.getX()) >> 1,
                Math.max(min.getY(), Math.min(max.getY(), mc.player != null ? mc.player.getBlockY() : min.getY())),
                (min.getZ() + max.getZ()) >> 1
        );
    }

    private BlockPos getFarmReturnGoal() {
        BlockPos center = getFarmCenter();
        if (center == null || mc == null || mc.world == null) {
            return center;
        }

        if (isReturnStandablePos(center)) {
            return center.toImmutable();
        }

        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null) {
            return center;
        }

        int baseY = mc.player != null ? mc.player.getBlockY() : center.getY();
        int fromY = Math.max(min.getY() - 1, baseY - 3);
        int toY = Math.min(max.getY() + 1, baseY + 3);

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 1; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int y = fromY; y <= toY; y++) {
                        BlockPos p = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                        if (!isInsideAreaMargin(p, 1)) {
                            continue;
                        }
                        if (!isReturnStandablePos(p)) {
                            continue;
                        }

                        double score = Vec3d.ofCenter(p).squaredDistanceTo(Vec3d.ofCenter(center));
                        if (mc.player != null) {
                            score += mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(p)) * 0.08D;
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

        return center;
    }

    private boolean isReturnStandablePos(BlockPos pos) {
        if (pos == null || mc == null || mc.world == null) {
            return false;
        }

        BlockState feet = mc.world.getBlockState(pos);
        BlockState head = mc.world.getBlockState(pos.up());
        BlockState below = mc.world.getBlockState(pos.down());

        if (!isPassableForReturn(feet, pos, true)) {
            return false;
        }
        if (!isPassableForReturn(head, pos.up(), false)) {
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

    private boolean isPassableForReturn(BlockState state, BlockPos pos, boolean allowCropFeet) {
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
                || state.isOf(Blocks.SUGAR_CANE)
                || state.isOf(Blocks.BAMBOO);
    }

    @Override
    public boolean isInSelectedArea(BlockPos pos) {
        if (pos == null || !hasSelectedArea()) {
            return false;
        }
        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null) {
            return false;
        }
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private void resetRuntimeState() {
        nextDropAt = 0L;
        lastGotoRefreshAt = 0L;
        depositReadyAt = 0L;

        activeStorageTarget = null;
        activeStorageStandPos = null;
        currentPathGoal = null;

        state = State.FARMING;

        resetStorageRecoveryState();

        cachedBaritonePrimary = null;
        cachedBaritoneCommandManager = null;
        baritoneInitTried = false;
        baritoneMissingWarned = false;

        wheatFarmBot.reset();
        farmCarrotBot.reset();
        farmPotatoBot.reset();
        farmBeetrootBot.reset();
        farmSugarCaneBot.reset();
        farmBambooBot.reset();
        farmMelonBot.reset();
        farmPumpkinBot.reset();
        farmChorusBot.reset();
        farmAppleBot.reset();
        farmGunpowderBot.reset();
        farmDiamondBot.reset();

        stopEating(true);
    }

    private BlockPos getNearestStoragePoint() {
        if (mc.player == null) {
            for (BlockPos p : skladPoints) {
                if (p != null) {
                    return p;
                }
            }
            return null;
        }

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos p : skladPoints) {
            if (p == null) {
                continue;
            }

            BlockPos resolved = resolveStorageTarget(p);
            if (resolved == null) {
                continue;
            }

            double d = mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(resolved));
            if (d < bestDist) {
                bestDist = d;
                best = resolved;
            }
        }

        return best;
    }

    private void renderSelectedArea() {
        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null) {
            return;
        }

        int minX = min.getX();
        int minY = min.getY();
        int minZ = min.getZ();

        int maxX = max.getX();
        int maxY = max.getY();
        int maxZ = max.getZ();

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }

        int base = ColorAssist.getClientColor();
        int xrayColor = ColorAssist.multAlpha(base, 0.35f);
        int mainColor = ColorAssist.multAlpha(base, 0.95f);

        float xrayWidth = 1.0f;
        float mainWidth = 1.45f;

        drawWireframe(minX, minY, minZ, maxX, maxY, maxZ, xrayColor, xrayWidth, false);
        drawWireframe(minX, minY, minZ, maxX, maxY, maxZ, mainColor, mainWidth, true);
    }

    private void drawWireframe(int minX, int maxX, int y, int z, int color, float width, boolean depth) {
        for (int x = minX; x <= maxX; x++) {
            drawShape(new BlockPos(x, y, z), X_LINE, color, width, depth);
        }
    }

    private void drawWireframe(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int color, float width, boolean depth) {
        drawEdgeX(minX, maxX, minY, minZ, color, width, depth);
        drawEdgeX(minX, maxX, minY, maxZ, color, width, depth);
        drawEdgeX(minX, maxX, maxY, minZ, color, width, depth);
        drawEdgeX(minX, maxX, maxY, maxZ, color, width, depth);

        drawEdgeZ(minZ, maxZ, minX, minY, color, width, depth);
        drawEdgeZ(minZ, maxZ, maxX, minY, color, width, depth);
        drawEdgeZ(minZ, maxZ, minX, maxY, color, width, depth);
        drawEdgeZ(minZ, maxZ, maxX, maxY, color, width, depth);

        drawEdgeY(minY, maxY, minX, minZ, color, width, depth);
        drawEdgeY(minY, maxY, maxX, minZ, color, width, depth);
        drawEdgeY(minY, maxY, minX, maxZ, color, width, depth);
        drawEdgeY(minY, maxY, maxX, maxZ, color, width, depth);
    }

    private void drawEdgeX(int minX, int maxX, int y, int z, int color, float width, boolean depth) {
        for (int x = minX; x <= maxX; x++) {
            drawShape(new BlockPos(x, y, z), X_LINE, color, width, depth);
        }
    }

    private void drawEdgeZ(int minZ, int maxZ, int x, int y, int color, float width, boolean depth) {
        for (int z = minZ; z <= maxZ; z++) {
            drawShape(new BlockPos(x, y, z), Z_LINE, color, width, depth);
        }
    }

    private void drawEdgeY(int minY, int maxY, int x, int z, int color, float width, boolean depth) {
        for (int y = minY; y <= maxY; y++) {
            drawShape(new BlockPos(x, y, z), Y_LINE, color, width, depth);
        }
    }

    private void renderSkladMarkers() {
        for (int i = 0; i < skladPoints.length; i++) {
            BlockPos p = skladPoints[i];
            if (p == null) {
                continue;
            }
            renderSkladMarker(p, i);
        }
    }

    private void renderSkladMarker(BlockPos pos, int index) {
        int base = getSkladColor(index);
        int xray = ColorAssist.multAlpha(base, 0.35f);
        int main = ColorAssist.multAlpha(base, 0.95f);

        float xrayWidth = 1.05f;
        float mainWidth = 1.55f;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        int topY = y + 2 + index;

        drawSkladMarkerPass(x, y, z, topY, xray, xrayWidth, false);
        drawSkladMarkerPass(x, y, z, topY, main, mainWidth, true);
    }

    private void drawSkladMarkerPass(int x, int y, int z, int topY, int color, float width, boolean depth) {
        for (int yy = y; yy <= topY; yy++) {
            drawShape(new BlockPos(x, yy, z), MY_LINE, color, width, depth);
        }

        drawShape(new BlockPos(x, y, z), MX_LINE, color, width, depth);
        drawShape(new BlockPos(x, y, z), MZ_LINE, color, width, depth);
        drawShape(new BlockPos(x, y, z), MARKER_CORE, color, width, depth);

        drawShape(new BlockPos(x, topY, z), MX_LINE, color, width, depth);
        drawShape(new BlockPos(x, topY, z), MZ_LINE, color, width, depth);
        drawShape(new BlockPos(x, topY, z), MARKER_CORE, color, width, depth);

        drawShape(new BlockPos(x + 1, topY, z), MZ_LINE, color, width, depth);
        drawShape(new BlockPos(x - 1, topY, z), MZ_LINE, color, width, depth);
        drawShape(new BlockPos(x, topY, z + 1), MX_LINE, color, width, depth);
        drawShape(new BlockPos(x, topY, z - 1), MX_LINE, color, width, depth);
    }

    private int getSkladColor(int index) {
        if (index == 0) return 0xFFFFD54A;
        if (index == 1) return 0xFF4DD0E1;
        if (index == 2) return 0xFF81C784;
        if (index == 3) return 0xFFBA68C8;
        return 0xFFFFFFFF;
    }

    private void drawShape(BlockPos pos, VoxelShape shape, int color, float width, boolean depth) {
        try {
            Render3D.drawShapeAlternative(pos, shape, color, width, true, depth);
        } catch (Throwable ignored) {
            try {
                Render3D.drawShapeAlternative(pos, shape, color, width, true, true);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void debugAreaHeightHint() {
        BlockPos min = getMinPoint();
        BlockPos max = getMaxPoint();
        if (min == null || max == null || mc.player == null) {
            return;
        }

        int py = mc.player.getBlockY();
        if (py > max.getY() + 12 || py < min.getY() - 12) {
            debugOnce("Область вне вашей высоты: Y [" + min.getY() + ".." + max.getY() + "], вы сейчас на Y " + py);
        }
    }

    private void debugOnce(String message) {
        long now = System.currentTimeMillis();
        if (now - lastDebugAt < DEBUG_COOLDOWN_MS) {
            return;
        }
        lastDebugAt = now;
        ChatMessage.brandmessage("Baritone PVE: " + message);
    }

    private void resetDebugState() {
        lastDebugAt = 0L;
    }

    public void setSkladPoint(int index, BlockPos pos) {
        if (index < 1 || index > skladPoints.length) {
            return;
        }

        BlockPos fixed = pos;
        BlockPos resolved = resolveStorageTarget(pos);
        if (resolved != null) {
            fixed = resolved;
        }

        skladPoints[index - 1] = fixed == null ? null : fixed.toImmutable();
    }

    public void setStoragePoint(int index, BlockPos pos) {
        setSkladPoint(index, pos);
    }

    public BlockPos getSkladPoint(int index) {
        if (index < 1 || index > skladPoints.length) {
            return null;
        }
        return skladPoints[index - 1];
    }

    public boolean hasAnyFarmEnabled() {
        return farmWheat.isValue()
                || farmCarrot.isValue()
                || farmPotato.isValue()
                || farmBeetroot.isValue()
                || farmSugarCane.isValue()
                || farmBamboo.isValue()
                || farmMelon.isValue()
                || farmPumpkin.isValue()
                || farmChorus.isValue()
                || farmApple.isValue()
                || farmGunpowder.isValue()
                || farmDiamond.isValue();
    }

    public boolean hasSelectedArea() {
        return point1 != null && point2 != null;
    }

    public boolean isAreaValid() {
        if (!hasSelectedArea()) {
            return false;
        }
        return !point1.equals(point2);
    }

    public void setPoint1(BlockPos pos) {
        point1 = pos == null ? null : pos.toImmutable();
        resetDebugState();
        resetRuntimeState();
        ChatMessage.brandmessage("Baritone PVE: Точка 1 установлена (" + formatPos(point1) + ")");
    }

    public void setPoint2(BlockPos pos) {
        point2 = pos == null ? null : pos.toImmutable();
        resetDebugState();
        resetRuntimeState();
        ChatMessage.brandmessage("Baritone PVE: Точка 2 установлена (" + formatPos(point2) + ")");
    }

    public void setPreviewArea(BlockPos p1, BlockPos p2) {
        point1 = p1 == null ? null : p1.toImmutable();
        point2 = p2 == null ? null : p2.toImmutable();
        resetDebugState();
        resetRuntimeState();
    }

    public void setPoint1FromPlayer() {
        if (mc == null || mc.player == null) {
            return;
        }
        setPoint1(mc.player.getBlockPos());
    }

    public void setPoint2FromPlayer() {
        if (mc == null || mc.player == null) {
            return;
        }
        setPoint2(mc.player.getBlockPos());
    }

    public void clearArea() {
        point1 = null;
        point2 = null;
        resetDebugState();
        resetRuntimeState();
        stopEating(true);
        releaseUseKey();
        baritoneStop();
        ChatMessage.brandmessage("Baritone PVE: Область фарма очищена");
    }

    public BlockPos getPoint1() {
        return point1;
    }

    public BlockPos getPoint2() {
        return point2;
    }

    @Override
    public BlockPos getMinPoint() {
        if (!hasSelectedArea()) {
            return null;
        }
        return new BlockPos(
                Math.min(point1.getX(), point2.getX()),
                Math.min(point1.getY(), point2.getY()),
                Math.min(point1.getZ(), point2.getZ())
        );
    }

    @Override
    public BlockPos getMaxPoint() {
        if (!hasSelectedArea()) {
            return null;
        }
        return new BlockPos(
                Math.max(point1.getX(), point2.getX()),
                Math.max(point1.getY(), point2.getY()),
                Math.max(point1.getZ(), point2.getZ())
        );
    }

    public boolean isFarmWheat() {
        return farmWheat.isValue();
    }

    public boolean isFarmCarrot() {
        return farmCarrot.isValue();
    }

    public boolean isFarmPotato() {
        return farmPotato.isValue();
    }

    public boolean isFarmBeetroot() {
        return farmBeetroot.isValue();
    }

    public boolean isFarmSugarCane() {
        return farmSugarCane.isValue();
    }

    public boolean isFarmBamboo() {
        return farmBamboo.isValue();
    }

    public boolean isFarmMelon() {
        return farmMelon.isValue();
    }

    public boolean isFarmPumpkin() {
        return farmPumpkin.isValue();
    }

    public boolean isFarmChorus() {
        return farmChorus.isValue();
    }

    public boolean isFarmApple() {
        return farmApple.isValue();
    }

    public boolean isFarmGunpowder() {
        return farmGunpowder.isValue();
    }

    public boolean isFarmDiamond() {
        return farmDiamond.isValue();
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private boolean isForbiddenAutoEatFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return stack.isOf(Items.CHORUS_FRUIT);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isFoodStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (isForbiddenAutoEatFood(stack)) {
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