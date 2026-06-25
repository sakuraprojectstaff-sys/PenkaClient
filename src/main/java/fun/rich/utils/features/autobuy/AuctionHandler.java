package fun.rich.utils.features.autobuy;

import fun.rich.display.screens.clickgui.components.implement.autobuy.items.AutoBuyableItem;
import fun.rich.display.screens.clickgui.components.implement.autobuy.manager.AutoBuyManager;
import fun.rich.display.screens.clickgui.components.implement.autobuy.util.AuctionUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionHandler {
    private final Set<String> notFoundItems = ConcurrentHashMap.newKeySet();
    private final Set<String> processedItems = ConcurrentHashMap.newKeySet();
    private final Set<String> sentItems = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private int failedCount;

    private final AutoBuyManager autoBuyManager;

    public AuctionHandler(AutoBuyManager autoBuyManager) {
        this.autoBuyManager = autoBuyManager;
    }

    public void clear() {
        notFoundItems.clear();
        processedItems.clear();
        sentItems.clear();
        lastMessageTime.clear();
        failedCount = 0;
    }

    public void handleBuyRequest(MinecraftClient mc, int syncId, List<Slot> slots, BuyRequest request, NetworkManager networkManager) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;
        if (slots == null || request == null) return;

        Slot targetSlot = findSlotByItemAndPrice(slots, request.itemName, request.price);
        if (targetSlot != null) {
            mc.interactionManager.clickSlot(syncId, targetSlot.id, 0, SlotActionType.QUICK_MOVE, mc.player);
            failedCount = 0;
        } else {
            String itemKey = request.itemName + "|" + request.price;
            notFoundItems.add(itemKey);
            failedCount++;
        }
    }

    public boolean shouldUpdate() {
        return failedCount > 3;
    }

    public void updateAuction(MinecraftClient mc, int syncId) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.clickSlot(syncId, 49, 0, SlotActionType.QUICK_MOVE, mc.player);
        notFoundItems.clear();
        failedCount = 0;
    }

    public void handleSuspiciousPrice(MinecraftClient mc, int syncId, List<Slot> slots) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;
        if (slots == null) return;

        Slot confirmSlot = null;
        for (Slot slot : slots) {
            if (slot == null) continue;
            ItemStack st = slot.getStack();
            if (st == null || st.isEmpty()) continue;
            if (st.getItem() == Items.GREEN_STAINED_GLASS_PANE) {
                confirmSlot = slot;
                break;
            }
        }

        if (confirmSlot != null) {
            mc.interactionManager.clickSlot(syncId, confirmSlot.id, 0, SlotActionType.PICKUP, mc.player);
        }
    }

    public List<Slot> findMatchingSlots(List<Slot> slots, List<AutoBuyableItem> cachedEnabledItems) {
        List<Slot> matching = new ArrayList<>();
        if (slots == null || cachedEnabledItems == null || cachedEnabledItems.isEmpty()) return matching;

        int max = Math.min(44, slots.size() - 1);
        for (int i = 0; i <= max; i++) {
            Slot slot = slots.get(i);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            if (AuctionUtils.isArmorItem(stack) && AuctionUtils.hasThornsEnchantment(stack)) {
                continue;
            }

            int price = AuctionUtils.getPrice(stack);
            if (price <= 0) continue;

            for (AutoBuyableItem item : cachedEnabledItems) {
                if (item == null || !item.isEnabled() || item.getSettings() == null) continue;

                int maxPrice = item.getSettings().getBuyBelow();
                if (price > maxPrice) continue;

                if (item.getSettings().isCanHaveQuantity()) {
                    int stackCount = stack.getCount();
                    if (stackCount < item.getSettings().getMinQuantity()) continue;
                }

                if (AuctionUtils.compareItem(stack, item.createItemStack())) {
                    matching.add(slot);
                    break;
                }
            }
        }

        matching.sort(Comparator.comparingInt(s -> AuctionUtils.getPrice(s.getStack())));
        return matching;
    }

    public void processBestSlots(List<Slot> bestSlots, NetworkManager networkManager) {
        if (bestSlots == null || networkManager == null) return;

        Map<String, Integer> itemCounts = new HashMap<>();
        for (Slot bestSlot : bestSlots) {
            if (bestSlot == null) continue;

            ItemStack stack = bestSlot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            String itemName = stack.getName().getString();
            String cleanName = AuctionUtils.funTimePricePattern.matcher(itemName).replaceAll("").trim();
            int price = AuctionUtils.getPrice(stack);
            String itemKey = cleanName + "|" + price;

            itemCounts.put(cleanName, itemCounts.getOrDefault(cleanName, 0) + 1);

            if (!sentItems.contains(itemKey)) {
                sentItems.add(itemKey);
                networkManager.sendBuy(cleanName, price);
            }
        }

        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
            String itemName = entry.getKey();
            Long lastTime = lastMessageTime.get(itemName);
            if (lastTime == null || currentTime - lastTime > 2000) {
                lastMessageTime.put(itemName, currentTime);
            }
        }
    }

    private Slot findSlotByItemAndPrice(List<Slot> slots, String itemName, int expectedPrice) {
        if (slots == null || itemName == null) return null;

        int max = Math.min(44, slots.size() - 1);
        for (int i = 0; i <= max; i++) {
            Slot slot = slots.get(i);
            if (slot == null) continue;

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            if (AuctionUtils.isArmorItem(stack) && AuctionUtils.hasThornsEnchantment(stack)) {
                continue;
            }

            String stackName = stack.getName().getString();
            stackName = AuctionUtils.funTimePricePattern.matcher(stackName).replaceAll("").trim();
            int price = AuctionUtils.getPrice(stack);

            if (stackName.equals(itemName) && price == expectedPrice) {
                return slot;
            }
        }
        return null;
    }
}
