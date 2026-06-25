package fun.rich.display.screens.clickgui.components.implement.autobuy.items;

import fun.rich.display.screens.clickgui.components.implement.autobuy.settings.AutoBuyItemSettings;
import net.minecraft.item.ItemStack;

public interface AutoBuyableItem {
    String getDisplayName();
    ItemStack createItemStack();
    int getPrice();
    boolean isEnabled();
    void setEnabled(boolean enabled);
    AutoBuyItemSettings getSettings();

    default String key() {
        try {
            ItemStack st = createItemStack();
            if (st != null && !st.isEmpty() && st.getItem() != null) {
                String k = st.getItem().getTranslationKey();
                if (k != null && !k.isEmpty()) return k;
            }
        } catch (Exception ignored) {
        }
        String dn = getDisplayName();
        if (dn == null || dn.isEmpty()) return "unknown";
        return dn.trim().toLowerCase();
    }
}
