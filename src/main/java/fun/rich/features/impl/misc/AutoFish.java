package fun.rich.features.impl.misc;

import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ItemBooleanSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class AutoFish extends Module {

    private static final Identifier SPLASH_ID = Identifier.of("minecraft", "entity.fishing_bobber.splash");

    private static final long CAST_COOLDOWN_MS = 650L;
    private static final long REEL_COOLDOWN_MS = 350L;

    private static final long IGNORE_AFTER_CAST_MS = 950L;

    private static final long REEL_DELAY_MIN_MS = 85L;
    private static final long REEL_DELAY_MAX_MS = 140L;

    private static final long RECAST_MIN_MS = 280L;
    private static final long RECAST_MAX_WAIT_MS = 2200L;

    private static final long DROP_JUNK_DELAY_MS = 520L;
    private static final long DROP_JUNK_COOLDOWN_MS = 1200L;

    private static Field playerBobberField;
    private static boolean playerBobberFieldSearched;

    private static Field bobberCaughtFishField;
    private static boolean bobberCaughtFishFieldSearched;

    private static Field bobberHookCountdownField;
    private static boolean bobberHookCountdownFieldSearched;

    private static TrackedData<Boolean> bobberCaughtFishTracked;
    private static boolean bobberCaughtFishTrackedSearched;

    private final BooleanSetting dropJunkEnabled = new BooleanSetting("Выбрасывать мусор", "Выбрасывать мусор из инвентаря после вылова").setValue(true);

    private final ItemBooleanSetting dropBowl = new ItemBooleanSetting("Миска", "Выбрасывать миски", Items.BOWL).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropStick = new ItemBooleanSetting("Палка", "Выбрасывать палки", Items.STICK).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropString = new ItemBooleanSetting("Нить", "Выбрасывать нити", Items.STRING).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropRottenFlesh = new ItemBooleanSetting("Гнилая плоть", "Выбрасывать гнилую плоть", Items.ROTTEN_FLESH).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropLeather = new ItemBooleanSetting("Кожа", "Выбрасывать кожу", Items.LEATHER).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropLeatherBoots = new ItemBooleanSetting("Кожаные ботинки", "Выбрасывать кожаные ботинки", Items.LEATHER_BOOTS).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropBone = new ItemBooleanSetting("Кость", "Выбрасывать кости", Items.BONE).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropInkSac = new ItemBooleanSetting("Чернильный мешок", "Выбрасывать чернильные мешки", Items.INK_SAC).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropTripwireHook = new ItemBooleanSetting("Крючок", "Выбрасывать крючки", Items.TRIPWIRE_HOOK).setValue(true).visible(dropJunkEnabled::isValue);

    private final ItemBooleanSetting dropLilyPad = new ItemBooleanSetting("Кувшинка", "Выбрасывать кувшинки", Items.LILY_PAD).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropSaddle = new ItemBooleanSetting("Седло", "Выбрасывать седла", Items.SADDLE).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropNameTag = new ItemBooleanSetting("Бирка", "Выбрасывать бирки", Items.NAME_TAG).setValue(true).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropDeadBush = new ItemBooleanSetting("Мертвый куст", "Выбрасывать мертвые кусты", Items.DEAD_BUSH).setValue(true).visible(dropJunkEnabled::isValue);

    private final ItemBooleanSetting dropEndCrystal = new ItemBooleanSetting("Кристалл края", "Выбрасывать кристаллы края", Items.END_CRYSTAL).setValue(false).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropNautilusShell = new ItemBooleanSetting("Раковина наутилуса", "Выбрасывать раковины наутилуса", Items.NAUTILUS_SHELL).setValue(false).visible(dropJunkEnabled::isValue);

    private final ItemBooleanSetting dropBow = new ItemBooleanSetting("Лук", "Выбрасывать луки", Items.BOW).setValue(false).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropFishingRod = new ItemBooleanSetting("Удочка", "Выбрасывать удочки", Items.FISHING_ROD).setValue(false).visible(dropJunkEnabled::isValue);
    private final ItemBooleanSetting dropEnchantedBook = new ItemBooleanSetting("Зачарованная книга", "Выбрасывать зачарованные книги", Items.ENCHANTED_BOOK).setValue(false).visible(dropJunkEnabled::isValue);

    private final Map<Item, BooleanSetting> junkMap = new HashMap<>();
    private final Map<Item, Integer> baselineCounts = new HashMap<>();
    private final Map<Item, Boolean> lastJunkToggles = new HashMap<>();
    private boolean baselineCaptured;

    private long lastUseTime;
    private long lastReelTime;
    private boolean isCasting;

    private long ignoreUntil;

    private boolean pendingReel;
    private long reelAt;

    private boolean waitingBobberGone;
    private long recastEarliestAt;
    private long recastDeadlineAt;

    private boolean pendingDropJunk;
    private long dropJunkAt;
    private long lastDropJunk;

    private long lastBiteHintAt;

    private FishingBobberEntity cachedBobber;
    private Hand rodHand = Hand.MAIN_HAND;

    public AutoFish() {
        super("AutoFish", "Auto Fish", ModuleCategory.MISC);

        setup(
                dropJunkEnabled,

                dropBowl, dropStick, dropString, dropRottenFlesh, dropLeather, dropLeatherBoots, dropBone, dropInkSac, dropTripwireHook,

                dropLilyPad, dropSaddle, dropNameTag, dropDeadBush,

                dropEndCrystal, dropNautilusShell,

                dropBow, dropFishingRod, dropEnchantedBook
        );

        junkMap.put(Items.BOWL, dropBowl);
        junkMap.put(Items.STICK, dropStick);
        junkMap.put(Items.STRING, dropString);
        junkMap.put(Items.ROTTEN_FLESH, dropRottenFlesh);
        junkMap.put(Items.LEATHER, dropLeather);
        junkMap.put(Items.LEATHER_BOOTS, dropLeatherBoots);
        junkMap.put(Items.BONE, dropBone);
        junkMap.put(Items.INK_SAC, dropInkSac);
        junkMap.put(Items.TRIPWIRE_HOOK, dropTripwireHook);

        junkMap.put(Items.LILY_PAD, dropLilyPad);
        junkMap.put(Items.SADDLE, dropSaddle);
        junkMap.put(Items.NAME_TAG, dropNameTag);
        junkMap.put(Items.DEAD_BUSH, dropDeadBush);

        junkMap.put(Items.END_CRYSTAL, dropEndCrystal);
        junkMap.put(Items.NAUTILUS_SHELL, dropNautilusShell);

        junkMap.put(Items.BOW, dropBow);
        junkMap.put(Items.FISHING_ROD, dropFishingRod);
        junkMap.put(Items.ENCHANTED_BOOK, dropEnchantedBook);

        for (Map.Entry<Item, BooleanSetting> en : junkMap.entrySet()) {
            lastJunkToggles.put(en.getKey(), isTrue(en.getValue()));
        }
    }

    @Override
    public void activate() {
        super.activate();
        resetState();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        resetState();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Hand hand = findRodHand(player);
        if (hand == null) return;
        rodHand = hand;

        if (!baselineCaptured) {
            captureBaseline(player);
            baselineCaptured = true;
        } else {
            updateBaselineOnToggle(player);
        }

        long now = System.currentTimeMillis();

        if (pendingDropJunk && now >= dropJunkAt) {
            dropJunk(mc, player);
            pendingDropJunk = false;
        }

        FishingBobberEntity bobber = getFishingBobber(player);
        cachedBobber = bobber;

        if (waitingBobberGone) {
            if ((bobber == null && now >= recastEarliestAt) || now >= recastDeadlineAt) {
                useRod(mc, now);
                isCasting = true;
                ignoreUntil = now + IGNORE_AFTER_CAST_MS;
                waitingBobberGone = false;
            }
            return;
        }

        if (bobber == null) {
            pendingReel = false;
            if (!isCasting && (now - lastUseTime) > CAST_COOLDOWN_MS) {
                useRod(mc, now);
                isCasting = true;
                ignoreUntil = now + IGNORE_AFTER_CAST_MS;
            }
            return;
        }

        isCasting = false;

        if (pendingReel) {
            if (now >= reelAt && (now - lastReelTime) > REEL_COOLDOWN_MS) {
                reelAndPlanRecast(mc, now);
            }
            return;
        }

        if (now < ignoreUntil) return;

        if (isCatchable(bobber)) {
            armReel(now);
            return;
        }

        if (lastBiteHintAt != 0L && (now - lastBiteHintAt) <= 1400L) {
            if (isCatchable(bobber)) armReel(now);
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        Hand hand = findRodHand(player);
        if (hand == null) return;
        rodHand = hand;

        FishingBobberEntity bobber = cachedBobber != null ? cachedBobber : getFishingBobber(player);
        if (bobber == null) return;

        long now = System.currentTimeMillis();
        if (now < ignoreUntil) return;

        Packet<?> p = extractPacket(e);
        if (p == null) return;

        if (p instanceof PlaySoundS2CPacket sp) {
            Identifier sid = getSoundId(sp);
            if (sid == null || !sid.equals(SPLASH_ID)) return;

            Vec3d soundPos = new Vec3d(sp.getX(), sp.getY(), sp.getZ());
            if (soundPos.squaredDistanceTo(bobber.getPos()) > (2.4 * 2.4)) return;

            lastBiteHintAt = now;
            return;
        }

        if (p instanceof EntityStatusS2CPacket st) {
            Entity ent = null;
            try {
                ent = st.getEntity(player.getWorld());
            } catch (Throwable ignored) {
            }
            if (ent != bobber) return;

            byte status;
            try {
                status = st.getStatus();
            } catch (Throwable t) {
                status = 0;
            }

            if (status == 31) {
                lastBiteHintAt = now;
            }
        }
    }

    private void armReel(long now) {
        pendingReel = true;
        long d = ThreadLocalRandom.current().nextLong(REEL_DELAY_MIN_MS, REEL_DELAY_MAX_MS + 1L);
        reelAt = now + d;
    }

    private void reelAndPlanRecast(MinecraftClient mc, long now) {
        useRod(mc, now);
        lastReelTime = now;

        pendingReel = false;
        lastBiteHintAt = 0L;

        waitingBobberGone = true;
        recastEarliestAt = now + RECAST_MIN_MS;
        recastDeadlineAt = now + RECAST_MAX_WAIT_MS;

        if (isTrue(dropJunkEnabled) && (now - lastDropJunk) > DROP_JUNK_COOLDOWN_MS) {
            pendingDropJunk = true;
            dropJunkAt = now + DROP_JUNK_DELAY_MS;
            lastDropJunk = now;
        }
    }

    private boolean isCatchable(FishingBobberEntity bobber) {
        try {
            Entity hooked = getHookedEntity(bobber);
            if (hooked != null) return true;
        } catch (Throwable ignored) {
        }

        Boolean tracked = getCaughtFishTracked(bobber);
        if (tracked != null && tracked) return true;

        Boolean caughtField = getCaughtFishField(bobber);
        if (caughtField != null && caughtField) return true;

        Integer hookCd = getHookCountdown(bobber);
        if (hookCd != null && hookCd > 0) return true;

        return false;
    }

    private static Entity getHookedEntity(FishingBobberEntity bobber) {
        try {
            Method m = bobber.getClass().getMethod("getHookedEntity");
            Object v = m.invoke(bobber);
            if (v instanceof Entity) return (Entity) v;
        } catch (Throwable ignored) {
        }
        Object v = getFieldByNameHierarchy(bobber, "hookedEntity", Entity.class);
        if (v instanceof Entity) return (Entity) v;
        return null;
    }

    private static Boolean getCaughtFishTracked(FishingBobberEntity bobber) {
        TrackedData<Boolean> td = resolveCaughtFishTracked(bobber.getClass());
        if (td == null) return null;
        try {
            DataTracker dt = bobber.getDataTracker();
            Object v = dt.get(td);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static TrackedData<Boolean> resolveCaughtFishTracked(Class<?> bobberClass) {
        if (bobberCaughtFishTrackedSearched) return bobberCaughtFishTracked;
        bobberCaughtFishTrackedSearched = true;

        Class<?> c = bobberClass;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!TrackedData.class.isAssignableFrom(f.getType())) continue;
                String n = f.getName().toLowerCase();
                if (!(n.contains("caught") || n.contains("fish"))) continue;
                try {
                    f.setAccessible(true);
                    Object tdObj = f.get(null);
                    if (!(tdObj instanceof TrackedData)) continue;
                    @SuppressWarnings("unchecked")
                    TrackedData<Boolean> cast = (TrackedData<Boolean>) tdObj;
                    bobberCaughtFishTracked = cast;
                    return bobberCaughtFishTracked;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private static Boolean getCaughtFishField(FishingBobberEntity bobber) {
        Field f = resolveCaughtFishField(bobber.getClass());
        if (f == null) return null;
        try {
            if (f.getType() == boolean.class) return f.getBoolean(bobber);
            Object v = f.get(bobber);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field resolveCaughtFishField(Class<?> bobberClass) {
        if (bobberCaughtFishFieldSearched) return bobberCaughtFishField;
        bobberCaughtFishFieldSearched = true;

        Class<?> c = bobberClass;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != boolean.class && f.getType() != Boolean.class) continue;
                String n = f.getName().toLowerCase();
                if (!(n.contains("caught") || (n.contains("fish") && n.contains("caught")))) continue;
                try {
                    f.setAccessible(true);
                    bobberCaughtFishField = f;
                    return bobberCaughtFishField;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private static Integer getHookCountdown(FishingBobberEntity bobber) {
        Field f = resolveHookCountdownField(bobber.getClass());
        if (f == null) return null;
        try {
            if (f.getType() == int.class) return f.getInt(bobber);
            Object v = f.get(bobber);
            if (v instanceof Integer) return (Integer) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field resolveHookCountdownField(Class<?> bobberClass) {
        if (bobberHookCountdownFieldSearched) return bobberHookCountdownField;
        bobberHookCountdownFieldSearched = true;

        Class<?> c = bobberClass;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getType() != int.class && f.getType() != Integer.class) continue;
                String n = f.getName().toLowerCase();
                if (!(n.contains("hook") && n.contains("count"))) continue;
                try {
                    f.setAccessible(true);
                    bobberHookCountdownField = f;
                    return bobberHookCountdownField;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private void useRod(MinecraftClient mc, long now) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) return;
        mc.interactionManager.interactItem(player, rodHand);
        lastUseTime = now;
    }

    private void dropJunk(MinecraftClient mc, ClientPlayerEntity player) {
        if (!isTrue(dropJunkEnabled)) return;
        if (mc.interactionManager == null) return;
        if (player.currentScreenHandler == null) return;

        PlayerInventory inv = player.getInventory();
        ScreenHandler handler = player.currentScreenHandler;

        Map<Item, Integer> excessMap = new HashMap<>();
        for (Map.Entry<Item, BooleanSetting> en : junkMap.entrySet()) {
            Item it = en.getKey();
            if (!isTrue(en.getValue())) continue;
            int base = baselineCounts.getOrDefault(it, 0);
            int cur = countItem(inv, it);
            int ex = cur - base;
            if (ex > 0) excessMap.put(it, ex);
        }

        if (excessMap.isEmpty()) return;

        int droppedClicks = 0;

        for (Map.Entry<Item, Integer> en : excessMap.entrySet()) {
            if (droppedClicks >= 6) break;
            Item it = en.getKey();
            int ex = en.getValue();
            if (ex <= 0) continue;

            int max = it.getMaxCount();
            if (max > 1) continue;

            for (int k = 0; k < ex && droppedClicks < 6; k++) {
                int bestDropIndex = findWorstNonStackIndex(inv, it);
                if (bestDropIndex < 0) break;

                int slotId = findHandlerSlotId(handler, inv, bestDropIndex);
                if (slotId < 0) break;

                try {
                    mc.interactionManager.clickSlot(handler.syncId, slotId, 1, SlotActionType.THROW, player);
                    droppedClicks++;
                } catch (Throwable ignored) {
                }
            }
        }

        if (droppedClicks >= 6) return;

        for (int invIndex = 0; invIndex < inv.size(); invIndex++) {
            if (droppedClicks >= 6) break;
            if (invIndex == inv.selectedSlot) continue;
            if (invIndex >= 36) continue;

            ItemStack st = inv.getStack(invIndex);
            if (st == null || st.isEmpty()) continue;

            Integer ex = excessMap.get(st.getItem());
            if (ex == null || ex <= 0) continue;

            int slotId = findHandlerSlotId(handler, inv, invIndex);
            if (slotId < 0) continue;

            int stackCount = st.getCount();
            int max = st.getMaxCount();

            if (max <= 1) continue;

            if (stackCount <= ex) {
                try {
                    mc.interactionManager.clickSlot(handler.syncId, slotId, 1, SlotActionType.THROW, player);
                    excessMap.put(st.getItem(), ex - stackCount);
                    droppedClicks++;
                } catch (Throwable ignored) {
                }
            } else {
                int toDrop = Math.min(ex, 6 - droppedClicks);
                for (int i = 0; i < toDrop && droppedClicks < 6; i++) {
                    try {
                        mc.interactionManager.clickSlot(handler.syncId, slotId, 0, SlotActionType.THROW, player);
                        droppedClicks++;
                    } catch (Throwable ignored) {
                        break;
                    }
                }
                excessMap.put(st.getItem(), ex - toDrop);
            }
        }
    }

    private static int findWorstNonStackIndex(PlayerInventory inv, Item it) {
        int bestIdx = -1;
        int bestScore = Integer.MAX_VALUE;

        for (int invIndex = 0; invIndex < inv.size(); invIndex++) {
            if (invIndex == inv.selectedSlot) continue;
            if (invIndex >= 36) continue;

            ItemStack st = inv.getStack(invIndex);
            if (st == null || st.isEmpty()) continue;
            if (st.getItem() != it) continue;

            int score = scoreStackForKeep(st);
            if (score < bestScore) {
                bestScore = score;
                bestIdx = invIndex;
            }
        }

        return bestIdx;
    }

    private static int scoreStackForKeep(ItemStack st) {
        int ench = st.hasEnchantments() ? 1000000 : 0;
        int dur = 0;
        try {
            if (st.isDamageable()) {
                int max = st.getMaxDamage();
                int dmg = st.getDamage();
                dur = Math.max(0, max - dmg);
            }
        } catch (Throwable ignored) {
        }
        return ench + dur;
    }

    private static int countItem(PlayerInventory inv, Item it) {
        int c = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st == null || st.isEmpty()) continue;
            if (st.getItem() != it) continue;
            c += st.getCount();
        }
        return c;
    }

    private void captureBaseline(ClientPlayerEntity player) {
        baselineCounts.clear();
        for (Item it : junkMap.keySet()) {
            baselineCounts.put(it, 0);
        }

        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st == null || st.isEmpty()) continue;
            Item it = st.getItem();
            if (!baselineCounts.containsKey(it)) continue;
            baselineCounts.put(it, baselineCounts.get(it) + st.getCount());
        }

        for (Map.Entry<Item, BooleanSetting> en : junkMap.entrySet()) {
            lastJunkToggles.put(en.getKey(), isTrue(en.getValue()));
        }
    }

    private void updateBaselineOnToggle(ClientPlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        for (Map.Entry<Item, BooleanSetting> en : junkMap.entrySet()) {
            Item it = en.getKey();
            boolean cur = isTrue(en.getValue());
            boolean prev = lastJunkToggles.getOrDefault(it, false);
            if (cur && !prev) {
                baselineCounts.put(it, countItem(inv, it));
            }
            lastJunkToggles.put(it, cur);
        }
    }

    private static int findHandlerSlotId(ScreenHandler handler, PlayerInventory inv, int invIndex) {
        for (int i = 0; i < handler.slots.size(); i++) {
            Slot s = handler.slots.get(i);
            if (s == null) continue;
            if (s.inventory == inv && s.getIndex() == invIndex) return i;
        }
        return -1;
    }

    private static Hand findRodHand(ClientPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main != null && main.getItem() == Items.FISHING_ROD) return Hand.MAIN_HAND;

        ItemStack off = player.getOffHandStack();
        if (off != null && off.getItem() == Items.FISHING_ROD) return Hand.OFF_HAND;

        return null;
    }

    private void resetState() {
        lastUseTime = 0L;
        lastReelTime = 0L;
        isCasting = false;

        ignoreUntil = 0L;

        pendingReel = false;
        reelAt = 0L;

        waitingBobberGone = false;
        recastEarliestAt = 0L;
        recastDeadlineAt = 0L;

        pendingDropJunk = false;
        dropJunkAt = 0L;
        lastDropJunk = 0L;

        lastBiteHintAt = 0L;

        cachedBobber = null;
        rodHand = Hand.MAIN_HAND;

        baselineCaptured = false;
        baselineCounts.clear();
    }

    private static Packet<?> extractPacket(PacketEvent e) {
        try {
            Method m = e.getClass().getMethod("getPacket");
            Object v = m.invoke(e);
            if (v instanceof Packet) return (Packet<?>) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = e.getClass().getDeclaredField("packet");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof Packet) return (Packet<?>) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = e.getClass().getMethod("get");
            Object v = m.invoke(e);
            if (v instanceof Packet) return (Packet<?>) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Identifier getSoundId(PlaySoundS2CPacket sp) {
        try {
            Object entry = sp.getSound();
            if (entry != null) {
                try {
                    Object se = entry.getClass().getMethod("value").invoke(entry);
                    if (se instanceof SoundEvent ev) return Registries.SOUND_EVENT.getId(ev);
                } catch (Throwable ignored) {
                }
                try {
                    Object se = entry.getClass().getMethod("valueOrNull").invoke(entry);
                    if (se instanceof SoundEvent ev) return Registries.SOUND_EVENT.getId(ev);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = PlaySoundS2CPacket.class.getDeclaredField("sound");
            f.setAccessible(true);
            Object entry = f.get(sp);
            if (entry != null) {
                try {
                    Object se = entry.getClass().getMethod("value").invoke(entry);
                    if (se instanceof SoundEvent ev) return Registries.SOUND_EVENT.getId(ev);
                } catch (Throwable ignored2) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static FishingBobberEntity getFishingBobber(ClientPlayerEntity player) {
        FishingBobberEntity direct = tryNamedBobber(player);
        if (direct != null) return direct;

        Field f = resolvePlayerBobberField(player.getClass());
        if (f == null) return null;

        try {
            Object v = f.get(player);
            if (v instanceof FishingBobberEntity) return (FishingBobberEntity) v;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static FishingBobberEntity tryNamedBobber(ClientPlayerEntity player) {
        FishingBobberEntity v = (FishingBobberEntity) getFieldByNameHierarchy(player, "fishHook", FishingBobberEntity.class);
        if (v != null) return v;
        v = (FishingBobberEntity) getFieldByNameHierarchy(player, "fishingBobber", FishingBobberEntity.class);
        if (v != null) return v;
        v = (FishingBobberEntity) getFieldByNameHierarchy(player, "bobber", FishingBobberEntity.class);
        return v;
    }

    private static Object getFieldByNameHierarchy(Object obj, String name, Class<?> type) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (type.isInstance(v)) return v;
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Field resolvePlayerBobberField(Class<?> cls) {
        if (playerBobberFieldSearched) return playerBobberField;
        playerBobberFieldSearched = true;

        Class<?> c = cls;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!FishingBobberEntity.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    playerBobberField = f;
                    return playerBobberField;
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private static boolean isTrue(Object setting) {
        if (setting == null) return false;
        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Boolean) return (Boolean) v;
            if (f.getType() == boolean.class) return f.getBoolean(setting);
        } catch (Throwable ignored) {
        }
        return false;
    }
}