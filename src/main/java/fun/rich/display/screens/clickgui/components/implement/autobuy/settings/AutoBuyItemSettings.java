package fun.rich.display.screens.clickgui.components.implement.autobuy.settings;

import lombok.Getter;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

@Getter
public class AutoBuyItemSettings {
    private int buyBelow;
    private int sellAbove;
    private int minQuantity;
    private final boolean canHaveQuantity;
    private final String itemName;

    public AutoBuyItemSettings(int defaultBuyBelow, Item material, String itemName) {
        this.itemName = itemName;
        this.canHaveQuantity = canItemStack(material);
        setBuyBelow(defaultBuyBelow);
        setSellAbove((int) (defaultBuyBelow * 1.5));
        setMinQuantity(1);
    }

    public void setBuyBelow(int buyBelow) {
        this.buyBelow = clampNonNegative(buyBelow);
    }

    public void setSellAbove(int sellAbove) {
        this.sellAbove = clampNonNegative(sellAbove);
    }

    public void setMinQuantity(int minQuantity) {
        if (!canHaveQuantity) {
            this.minQuantity = 1;
            return;
        }
        this.minQuantity = clampMin(minQuantity, 1);
    }

    private int clampNonNegative(int v) {
        return Math.max(0, v);
    }

    private int clampMin(int v, int min) {
        return Math.max(min, v);
    }

    private boolean canItemStack(Item material) {
        if (material == Items.NETHERITE_HELMET || material == Items.NETHERITE_CHESTPLATE ||
                material == Items.NETHERITE_LEGGINGS || material == Items.NETHERITE_BOOTS ||
                material == Items.NETHERITE_SWORD || material == Items.NETHERITE_PICKAXE ||
                material == Items.CROSSBOW || material == Items.TRIDENT || material == Items.MACE ||
                material == Items.ELYTRA || material == Items.TOTEM_OF_UNDYING) {
            return false;
        }
        return material.getMaxCount() > 1;
    }
}
