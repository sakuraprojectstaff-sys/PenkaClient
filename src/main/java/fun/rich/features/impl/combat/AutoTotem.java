package fun.rich.features.impl.combat;

import antidaunleak.api.annotation.Native;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.interactions.inv.InventoryResult;
import fun.rich.utils.interactions.inv.InventoryToolkit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class AutoTotem extends Module {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final SelectSetting modeSetting = new SelectSetting("Режим", "Способ свопа")
            .value("Default", "Legit")
            .selected("Default");

    private final SliderSettings healthThreshold = new SliderSettings("Порог здоровья", "Минимальное здоровье для экипировки тотема")
            .setValue(4.5F).range(1F, 20F);

    private final SliderSettings noArmorHealth = new SliderSettings("Без полной брони", "Минимальное здоровье без полной брони")
            .setValue(8.0F).range(1F, 20F);

    private final SliderSettings elytraHealth = new SliderSettings("Здоровье на элитре", "Минимальное здоровье при полете")
            .setValue(8.5F).range(1F, 20F);

    private final BooleanSetting includeAbsorption = new BooleanSetting("Золотые сердца", "Учитывать абсорбцию при проверке здоровья")
            .setValue(true);

    private final BooleanSetting crystalCheck = new BooleanSetting("Кристаллы", "Экипировать тотем если рядом кристалл")
            .setValue(true);

    private final SliderSettings crystalDistance = new SliderSettings("Дистанция до кристалла", "Макс дистанция до кристалла")
            .setValue(4F).range(1F, 8F)
            .visible(crystalCheck::isValue);

    private final BooleanSetting fallCheck = new BooleanSetting("Падение", "Экипировать тотем при опасном падении")
            .setValue(true);

    private final BooleanSetting tntCheck = new BooleanSetting("Динамит", "Экипировать тотем если рядом TNT/вагонетка")
            .setValue(false);

    private final SliderSettings tntRadius = new SliderSettings("Радиус TNT", "Дистанция проверки TNT")
            .setValue(10F).range(5F, 35F)
            .visible(tntCheck::isValue);

    private final BooleanSetting tridentCheck = new BooleanSetting("Трезубец", "Экипировать тотем если летит трезубец в игрока")
            .setValue(false);

    private final SliderSettings tridentRadius = new SliderSettings("Радиус трезубца", "Дистанция проверки трезубца")
            .setValue(12F).range(6F, 30F)
            .visible(tridentCheck::isValue);

    private final BooleanSetting saveTaliks = new BooleanSetting("Сейв таликов", "Использовать обычные тотемы без чар")
            .setValue(true);

    private final BooleanSetting returnItem = new BooleanSetting("Возвращать предмет", "Вернуть предыдущий предмет в хотбар после свопа")
            .setValue(true);

    private int savedSlot = -1;
    private int totemSlot = -1;

    private long actionStartTime = 0L;
    private boolean keysOverridden = false;

    private boolean wasForwardPressed, wasBackPressed, wasLeftPressed, wasRightPressed, wasJumpPressed;

    private Phase phase = Phase.READY;

    private ItemStack previousMainHandStack = ItemStack.EMPTY;
    private int previousMainHandSlot = -1;
    private boolean needsReturn = false;

    private long lastDangerScanMs = 0L;
    private double cachedCrystalDist = Double.MAX_VALUE;
    private boolean cachedTntDanger = false;
    private boolean cachedTridentDanger = false;

    private enum Phase { READY, SLOWING_DOWN, SWAP_TOTEM, AWAIT_SWITCH, RESTORE_SLOT, SPEEDING_UP, FINISH }

    public AutoTotem() {
        super("AutoTotem", ModuleCategory.COMBAT);
        setup(
                modeSetting,
                healthThreshold,
                noArmorHealth,
                elytraHealth,
                includeAbsorption,
                crystalCheck,
                crystalDistance,
                fallCheck,
                tntCheck,
                tntRadius,
                tridentCheck,
                tridentRadius,
                saveTaliks,
                returnItem
        );
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (MC.player == null || MC.world == null) {
            resetState();
            return;
        }

        if (phase != Phase.READY) {
            execute();
            return;
        }

        updateDangerCache();

        float hp = MC.player.getHealth();
        if (includeAbsorption.isValue()) hp += MC.player.getAbsorptionAmount();

        float threshold = MC.player.isGliding()
                ? elytraHealth.getValue()
                : (hasFullArmor() ? healthThreshold.getValue() : noArmorHealth.getValue());

        boolean shouldEquip = false;

        if (hp <= threshold) {
            shouldEquip = true;
        } else if (fallCheck.isValue() && isDangerousFall(hp)) {
            shouldEquip = true;
        } else if (crystalCheck.isValue() && cachedCrystalDist <= crystalDistance.getValue()) {
            shouldEquip = true;
        } else if (tntCheck.isValue() && cachedTntDanger) {
            shouldEquip = true;
        } else if (tridentCheck.isValue() && cachedTridentDanger) {
            shouldEquip = true;
        }

        if (shouldEquip) {
            tryEquipTotem();
        }

        if (phase == Phase.READY && needsReturn && returnItem.isValue() && previousMainHandSlot >= 0 && !previousMainHandStack.isEmpty()) {
            if (MC.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                attemptReturnPreviousItem();
                previousMainHandStack = ItemStack.EMPTY;
                previousMainHandSlot = -1;
                needsReturn = false;
            }
        }
    }

    private void updateDangerCache() {
        long now = System.currentTimeMillis();
        if (now - lastDangerScanMs < 150L) return;
        lastDangerScanMs = now;

        cachedCrystalDist = Double.MAX_VALUE;
        cachedTntDanger = false;
        cachedTridentDanger = false;

        if (MC.player == null || MC.world == null) return;

        if (crystalCheck.isValue()) {
            float r = crystalDistance.getValue();
            Box box = MC.player.getBoundingBox().expand(r);
            List<EndCrystalEntity> crystals = MC.world.getEntitiesByClass(EndCrystalEntity.class, box, c -> true);
            for (EndCrystalEntity c : crystals) {
                double d = MC.player.getPos().distanceTo(c.getPos());
                if (d < cachedCrystalDist) cachedCrystalDist = d;
            }
        }

        if (tntCheck.isValue()) {
            float r = tntRadius.getValue();
            Box box = MC.player.getBoundingBox().expand(r);
            if (!MC.world.getEntitiesByClass(TntEntity.class, box, t -> true).isEmpty()) {
                cachedTntDanger = true;
            } else if (!MC.world.getEntitiesByClass(TntMinecartEntity.class, box, t -> true).isEmpty()) {
                cachedTntDanger = true;
            }
        }

        if (tridentCheck.isValue()) {
            float r = tridentRadius.getValue();
            Box box = MC.player.getBoundingBox().expand(r);
            List<TridentEntity> tridents = MC.world.getEntitiesByClass(TridentEntity.class, box, t -> true);

            Vec3d playerEye = MC.player.getEyePos();
            for (TridentEntity t : tridents) {
                Vec3d vel = t.getVelocity();
                double v2 = vel.lengthSquared();
                if (v2 < 0.01) continue;

                Vec3d toPlayer = playerEye.subtract(t.getPos());
                if (toPlayer.dotProduct(vel) <= 0.0) continue;

                Vec3d cross = toPlayer.crossProduct(vel);
                double distToLine = cross.length() / Math.sqrt(v2);
                if (distToLine <= 2.2) {
                    cachedTridentDanger = true;
                    break;
                }
            }
        }
    }

    private boolean hasFullArmor() {
        if (MC.player == null) return true;
        for (ItemStack s : MC.player.getInventory().armor) {
            if (s == null || s.isEmpty()) return false;
        }
        return true;
    }

    private boolean isDangerousFall(float hp) {
        if (MC.player == null) return false;
        if (MC.player.isTouchingWater() || MC.player.isGliding()) return false;

        float fd = MC.player.fallDistance;
        if (fd <= 6.0f) return false;

        float dmg = (fd - 3.0f) / 2.0f;
        return dmg >= hp - 0.5f;
    }

    @Native(type = Native.Type.VMProtectBeginUltra)
    private void tryEquipTotem() {
        if (phase != Phase.READY) return;
        if (isTotemInOffhand()) return;
        if (MC.currentScreen != null) return;

        savedSlot = MC.player.getInventory().selectedSlot;

        InventoryResult hotbar = InventoryToolkit.findItemInHotBar(Items.TOTEM_OF_UNDYING);
        if (hotbar.found()) {
            totemSlot = hotbar.slot();
            if (modeSetting.isSelected("Legit")) startLegitEquip();
            else executeDefaultSwap();
            return;
        }

        InventoryResult inv = saveTaliks.isValue() ? findTotemWithSaveTalics() : InventoryToolkit.findItemInInventory(Items.TOTEM_OF_UNDYING);
        if (!inv.found()) return;

        totemSlot = inv.slot();
        previousMainHandSlot = MC.player.getInventory().selectedSlot;
        previousMainHandStack = MC.player.getMainHandStack().copy();
        needsReturn = true;

        if (modeSetting.isSelected("Legit")) startLegitEquip();
        else executeDefaultSwap();
    }

    private void executeDefaultSwap() {
        if (totemSlot < 0) {
            resetState();
            return;
        }

        int slotIndex = totemSlot;
        if (slotIndex >= 0 && slotIndex <= 8) slotIndex += 36;

        if (MC.interactionManager != null && MC.player.playerScreenHandler != null) {
            MC.interactionManager.clickSlot(
                    MC.player.playerScreenHandler.syncId,
                    slotIndex,
                    40,
                    SlotActionType.SWAP,
                    MC.player
            );
        }

        if (savedSlot >= 0) {
            MC.player.getInventory().selectedSlot = savedSlot;
        }

        resetState();
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    private void startLegitEquip() {
        long h = MC.getWindow().getHandle();
        wasForwardPressed = InputUtil.isKeyPressed(h, MC.options.forwardKey.getDefaultKey().getCode());
        wasBackPressed = InputUtil.isKeyPressed(h, MC.options.backKey.getDefaultKey().getCode());
        wasLeftPressed = InputUtil.isKeyPressed(h, MC.options.leftKey.getDefaultKey().getCode());
        wasRightPressed = InputUtil.isKeyPressed(h, MC.options.rightKey.getDefaultKey().getCode());
        wasJumpPressed = InputUtil.isKeyPressed(h, MC.options.jumpKey.getDefaultKey().getCode());

        phase = Phase.SLOWING_DOWN;
        actionStartTime = System.currentTimeMillis();
        keysOverridden = false;
    }

    private void execute() {
        if (MC.player == null || MC.currentScreen != null) {
            resetState();
            return;
        }

        long elapsed = System.currentTimeMillis() - actionStartTime;

        switch (phase) {
            case SLOWING_DOWN -> {
                MC.player.input.movementForward = 0;
                MC.player.input.movementSideways = 0;
                if (MC.player.isSprinting()) MC.player.setSprinting(false);

                if (!keysOverridden) {
                    MC.options.forwardKey.setPressed(false);
                    MC.options.backKey.setPressed(false);
                    MC.options.leftKey.setPressed(false);
                    MC.options.rightKey.setPressed(false);
                    MC.options.jumpKey.setPressed(false);
                    keysOverridden = true;
                }

                if (elapsed > 1) {
                    phase = Phase.SWAP_TOTEM;
                    actionStartTime = System.currentTimeMillis();
                }
            }
            case SWAP_TOTEM -> {
                if (elapsed > 25) {
                    if (totemSlot < 0) {
                        resetState();
                        return;
                    }

                    int slotIndex = totemSlot;
                    if (slotIndex >= 0 && slotIndex <= 8) slotIndex += 36;

                    if (MC.interactionManager != null && MC.player.playerScreenHandler != null) {
                        MC.interactionManager.clickSlot(
                                MC.player.playerScreenHandler.syncId,
                                slotIndex,
                                40,
                                SlotActionType.SWAP,
                                MC.player
                        );
                    }

                    phase = Phase.AWAIT_SWITCH;
                    actionStartTime = System.currentTimeMillis();
                }
            }
            case AWAIT_SWITCH -> {
                if (isTotemInOffhand() || elapsed > 70) {
                    phase = Phase.RESTORE_SLOT;
                    actionStartTime = System.currentTimeMillis();
                }
            }
            case RESTORE_SLOT -> {
                if (elapsed > 15) {
                    if (savedSlot >= 0) InventoryToolkit.switchTo(savedSlot);
                    if (keysOverridden) restoreKeyStates();
                    actionStartTime = System.currentTimeMillis();
                    phase = Phase.SPEEDING_UP;
                }
            }
            case SPEEDING_UP -> {
                long speedupElapsed = System.currentTimeMillis() - actionStartTime;
                float t = Math.min(1.0f, speedupElapsed / 20.0f);

                if (MC.player.input != null) {
                    long h = MC.getWindow().getHandle();
                    boolean forwardNow = InputUtil.isKeyPressed(h, MC.options.forwardKey.getDefaultKey().getCode());
                    float targetForward = forwardNow ? 1.0f : 0.0f;

                    MC.player.input.movementForward = lerp(MC.player.input.movementForward, targetForward * t, 0.45f);

                    if (t > 0.5f && forwardNow && !MC.player.isSprinting()) {
                        MC.player.setSprinting(true);
                    }
                }

                if (speedupElapsed > 25) {
                    phase = Phase.FINISH;
                }
            }
            case FINISH -> resetState();
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private InventoryResult findTotemWithSaveTalics() {
        InventoryResult nonEnch = InventoryToolkit.findInInventory(i -> i.getItem() == Items.TOTEM_OF_UNDYING && !i.hasEnchantments());
        if (nonEnch.found()) return nonEnch;
        return InventoryToolkit.findItemInInventory(Items.TOTEM_OF_UNDYING);
    }

    private boolean isTotemInOffhand() {
        ItemStack stack = MC.player.getOffHandStack();
        return stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private void restoreKeyStates() {
        long h = MC.getWindow().getHandle();
        boolean forwardNow = InputUtil.isKeyPressed(h, MC.options.forwardKey.getDefaultKey().getCode());
        boolean backNow = InputUtil.isKeyPressed(h, MC.options.backKey.getDefaultKey().getCode());
        boolean leftNow = InputUtil.isKeyPressed(h, MC.options.leftKey.getDefaultKey().getCode());
        boolean rightNow = InputUtil.isKeyPressed(h, MC.options.rightKey.getDefaultKey().getCode());
        boolean jumpNow = InputUtil.isKeyPressed(h, MC.options.jumpKey.getDefaultKey().getCode());

        MC.options.forwardKey.setPressed(wasForwardPressed && forwardNow);
        MC.options.backKey.setPressed(wasBackPressed && backNow);
        MC.options.leftKey.setPressed(wasLeftPressed && leftNow);
        MC.options.rightKey.setPressed(wasRightPressed && rightNow);
        MC.options.jumpKey.setPressed(wasJumpPressed && jumpNow);

        keysOverridden = false;
    }

    private void attemptReturnPreviousItem() {
        if (!returnItem.isValue()) return;
        if (previousMainHandStack == null || previousMainHandStack.isEmpty()) return;
        if (MC.player == null || MC.player.playerScreenHandler == null || MC.interactionManager == null) return;

        int targetHotbar = previousMainHandSlot;
        if (targetHotbar < 0 || targetHotbar > 8) targetHotbar = savedSlot >= 0 ? savedSlot : -1;
        if (targetHotbar < 0) return;

        ItemStack cur = MC.player.getInventory().getStack(targetHotbar);
        if (!cur.isEmpty() && cur.getItem() == previousMainHandStack.getItem()) {
            InventoryToolkit.switchTo(targetHotbar);
            return;
        }

        InventoryResult found = InventoryToolkit.findInInventory(i -> i.getItem() == previousMainHandStack.getItem());
        if (!found.found()) {
            InventoryToolkit.switchTo(targetHotbar);
            return;
        }

        int fromSlot = found.slot();
        int fromIndex = fromSlot;
        if (fromIndex >= 0 && fromIndex <= 8) fromIndex += 36;

        MC.interactionManager.clickSlot(
                MC.player.playerScreenHandler.syncId,
                fromIndex,
                targetHotbar,
                SlotActionType.SWAP,
                MC.player
        );

        InventoryToolkit.switchTo(targetHotbar);
    }

    private void resetState() {
        if (keysOverridden) restoreKeyStates();
        totemSlot = -1;
        savedSlot = -1;
        actionStartTime = 0L;
        phase = Phase.READY;
    }

    @Override
    public void deactivate() {
        resetState();
        previousMainHandStack = ItemStack.EMPTY;
        previousMainHandSlot = -1;
        needsReturn = false;
        super.deactivate();
    }
}