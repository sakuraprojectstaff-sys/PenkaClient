package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.Registries;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;

public class AutoBrewPotion extends Module {

    private final SelectSetting brew = new SelectSetting("Варить", "Выберите зелье")
            .value("Зелье скорости", "Зелье силы", "Зелье огнестойкости")
            .selected("Зелье скорости");

    private final SliderSettings delayMs = new SliderSettings("Задержка", "Задержка действий (мс)")
            .setValue(120.0f).range(50.0f, 1000.0f);

    private final BooleanSetting loot = new BooleanSetting("Забирать", "Забирать готовые зелья в инвентарь")
            .setValue(true);

    private final BooleanSetting addGunpowder = new BooleanSetting("Порох", "Делать взрывные зелья (splash)")
            .setValue(false);

    private final BooleanSetting improve = new BooleanSetting("Улучшать", "Скорость/сила -> glowstone, огнестойкость -> redstone")
            .setValue(true);

    private final BooleanSetting autoFuel = new BooleanSetting("Топливо", "Автозаправка огненным порошком")
            .setValue(true);

    private long nextActionAtMs;
    private long lastNotifyAtMs;
    private String lastNotifyKey = "";

    public AutoBrewPotion() {
        super("AutoBrewPotion", ModuleCategory.MISC);
        setup(brew, delayMs, loot, addGunpowder, improve, autoFuel);
    }

    @Override
    public void activate() {
        super.activate();
        nextActionAtMs = 0L;
        lastNotifyAtMs = 0L;
        lastNotifyKey = "";
    }

    @Override
    public void deactivate() {
        nextActionAtMs = 0L;
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.interactionManager == null) return;
        if (!(mc.player.currentScreenHandler instanceof BrewingStandScreenHandler)) return;

        long now = System.currentTimeMillis();
        if (now < nextActionAtMs) return;

        if (!cursorEmpty()) return;

        if (autoFuel.isValue() && isFuelEmpty()) {
            int bp = findItemInPlayerInv(Items.BLAZE_POWDER);
            if (bp == -1) {
                notifyOnce("fuel", "§c✖ §7AutoBrew §8• §fНет огненного порошка");
                setState(false);
                return;
            }
            placeOne(bp, 4);
            armDelay(now);
            return;
        }

        if (!ingredientEmpty()) return;

        Batch batch = readBatch();
        if (batch.nonEmptyCount == 0) {
            if (!fillEmptyWith("water", Items.POTION, now)) return;
            return;
        }

        if (batch.nonEmptyCount < 3) {
            if (!fillEmptyWith(batch.potionId, batch.item, now)) return;
            return;
        }

        if (!batch.samePotion || !batch.sameItem) return;

        Recipe r = recipe();
        String finalPotionId = improve.isValue() ? r.improvedPotionId : r.basePotionId;
        Item finalItem = addGunpowder.isValue() ? Items.SPLASH_POTION : Items.POTION;

        if (batch.item == Items.SPLASH_POTION && !addGunpowder.isValue()) {
            if (loot.isValue()) {
                lootAll();
                armDelay(now);
            }
            return;
        }

        if (batch.item == Items.SPLASH_POTION) {
            if (batch.potionId.equals(finalPotionId)) {
                if (loot.isValue()) {
                    lootAll();
                    armDelay(now);
                }
            }
            return;
        }

        if (batch.potionId.equals("water")) {
            if (!tryPlaceIngredient(Items.NETHER_WART, "нарост", now)) return;
            return;
        }

        if (batch.potionId.equals("awkward")) {
            if (!tryPlaceIngredient(r.baseIngredient, r.baseIngredientName, now)) return;
            return;
        }

        if (batch.potionId.equals(r.basePotionId)) {
            if (improve.isValue()) {
                if (!tryPlaceIngredient(r.upgradeIngredient, r.upgradeIngredientName, now)) return;
                return;
            }

            if (addGunpowder.isValue()) {
                if (!tryPlaceIngredient(Items.GUNPOWDER, "порох", now)) return;
                return;
            }

            if (loot.isValue() && finalItem == Items.POTION) {
                lootAll();
                armDelay(now);
            }
            return;
        }

        if (batch.potionId.equals(r.improvedPotionId)) {
            if (addGunpowder.isValue()) {
                if (!tryPlaceIngredient(Items.GUNPOWDER, "порох", now)) return;
                return;
            }

            if (loot.isValue() && finalItem == Items.POTION) {
                lootAll();
                armDelay(now);
            }
            return;
        }

        if (batch.potionId.equals(finalPotionId)) {
            if (addGunpowder.isValue()) {
                if (!tryPlaceIngredient(Items.GUNPOWDER, "порох", now)) return;
                return;
            }

            if (loot.isValue() && finalItem == Items.POTION) {
                lootAll();
                armDelay(now);
            }
        }
    }

    private Recipe recipe() {
        if (brew.isSelected("Зелье силы")) {
            return new Recipe("strength", "strong_strength", Items.BLAZE_POWDER, "огненный порошок", Items.GLOWSTONE_DUST, "светокамень");
        }
        if (brew.isSelected("Зелье огнестойкости")) {
            return new Recipe("fire_resistance", "long_fire_resistance", Items.MAGMA_CREAM, "слизь магмы", Items.REDSTONE, "редстоун");
        }
        return new Recipe("swiftness", "strong_swiftness", Items.SUGAR, "сахар", Items.GLOWSTONE_DUST, "светокамень");
    }

    private boolean fillEmptyWith(String potionId, Item item, long now) {
        for (int i = 0; i < 3; i++) {
            if (!getStack(i).isEmpty()) continue;

            int slot = findPotionInPlayerInv(potionId, item);
            if (slot == -1) {
                String name = potionNameForMsg(potionId, item);
                notifyOnce("fill|" + potionId + "|" + item, "§c✖ §7AutoBrew §8• §fНет в инвентаре: §7" + name);
                setState(false);
                return false;
            }

            swapStack(slot, i);
            armDelay(now);
            return true;
        }
        return false;
    }

    private String potionNameForMsg(String potionId, Item item) {
        String it = item == Items.SPLASH_POTION ? "Splash" : "Potion";
        return it + " " + potionId;
    }

    private boolean tryPlaceIngredient(Item ingredient, String name, long now) {
        int slot = findItemInPlayerInv(ingredient);
        if (slot == -1) {
            notifyOnce("ing|" + ingredient, "§c✖ §7AutoBrew §8• §fНет ингредиента: §7" + name);
            setState(false);
            return false;
        }
        placeOne(slot, 3);
        armDelay(now);
        return true;
    }

    private Batch readBatch() {
        int nonEmpty = 0;
        Item firstItem = null;
        String firstPotion = "";
        boolean sameItem = true;
        boolean samePotion = true;

        for (int i = 0; i < 3; i++) {
            ItemStack s = getStack(i);
            if (s.isEmpty()) continue;

            nonEmpty++;

            Item it = s.getItem();
            String pid = getPotionId(s);

            if (firstItem == null) {
                firstItem = it;
                firstPotion = pid;
            } else {
                if (it != firstItem) sameItem = false;
                if (!pid.equals(firstPotion)) samePotion = false;
            }
        }

        if (firstItem == null) firstItem = Items.POTION;
        if (firstPotion == null) firstPotion = "";

        return new Batch(nonEmpty, firstItem, firstPotion, sameItem, samePotion);
    }

    private String getPotionId(ItemStack stack) {
        try {
            PotionContentsComponent c = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (c == null || c.potion().isEmpty()) return "";
            Potion p = c.potion().get().value();
            Identifier id = Registries.POTION.getId(p);
            if (id == null) return "";
            return id.getPath();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void armDelay(long now) {
        long d = (long) delayMs.getValue();
        if (d < 30L) d = 30L;
        nextActionAtMs = now + d;
    }

    private boolean cursorEmpty() {
        try {
            return mc.player.currentScreenHandler.getCursorStack().isEmpty();
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean ingredientEmpty() {
        return getStack(3).isEmpty();
    }

    private boolean isFuelEmpty() {
        return getStack(4).isEmpty();
    }

    private ItemStack getStack(int slotId) {
        try {
            return mc.player.currentScreenHandler.slots.get(slotId).getStack();
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    private int findItemInPlayerInv(Item item) {
        for (int i = 5; i <= 40; i++) {
            ItemStack s = getStack(i);
            if (!s.isEmpty() && s.getItem() == item) return i;
        }
        return -1;
    }

    private int findPotionInPlayerInv(String potionId, Item itemType) {
        if (potionId == null) potionId = "";
        for (int i = 5; i <= 40; i++) {
            ItemStack s = getStack(i);
            if (s.isEmpty()) continue;
            if (itemType != null && s.getItem() != itemType) continue;

            String pid = getPotionId(s);
            if (potionId.equals(pid)) return i;
        }
        return -1;
    }

    private void placeOne(int from, int to) {
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, to, 1, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private void swapStack(int from, int to) {
        int syncId = mc.player.currentScreenHandler.syncId;
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, to, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(syncId, from, 0, SlotActionType.PICKUP, mc.player);
    }

    private void lootAll() {
        int syncId = mc.player.currentScreenHandler.syncId;
        for (int i = 0; i < 3; i++) {
            mc.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
        }
    }

    private void notifyOnce(String key, String msg) {
        long now = System.currentTimeMillis();
        if (key == null) key = "";
        if (key.equals(lastNotifyKey) && now - lastNotifyAtMs < 2500L) return;
        lastNotifyKey = key;
        lastNotifyAtMs = now;
        ChatMessage.brandmessage(msg);
    }

    private static final class Recipe {
        final String basePotionId;
        final String improvedPotionId;
        final Item baseIngredient;
        final String baseIngredientName;
        final Item upgradeIngredient;
        final String upgradeIngredientName;

        Recipe(String basePotionId, String improvedPotionId, Item baseIngredient, String baseIngredientName, Item upgradeIngredient, String upgradeIngredientName) {
            this.basePotionId = basePotionId;
            this.improvedPotionId = improvedPotionId;
            this.baseIngredient = baseIngredient;
            this.baseIngredientName = baseIngredientName;
            this.upgradeIngredient = upgradeIngredient;
            this.upgradeIngredientName = upgradeIngredientName;
        }
    }

    private static final class Batch {
        final int nonEmptyCount;
        final Item item;
        final String potionId;
        final boolean sameItem;
        final boolean samePotion;

        Batch(int nonEmptyCount, Item item, String potionId, boolean sameItem, boolean samePotion) {
            this.nonEmptyCount = nonEmptyCount;
            this.item = item;
            this.potionId = potionId == null ? "" : potionId;
            this.sameItem = sameItem;
            this.samePotion = samePotion;
        }
    }
}