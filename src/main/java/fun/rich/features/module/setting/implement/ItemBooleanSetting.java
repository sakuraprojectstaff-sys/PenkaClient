package fun.rich.features.module.setting.implement;

import net.minecraft.item.Item;

import java.util.function.Supplier;

public class ItemBooleanSetting extends BooleanSetting {

    private final Item item;

    public ItemBooleanSetting(String name, String description, Item item) {
        super(name, description);
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    @Override
    public ItemBooleanSetting visible(Supplier<Boolean> visible) {
        super.visible(visible);
        return this;
    }

    @Override
    public ItemBooleanSetting setValue(boolean value) {
        super.setValue(value);
        return this;
    }

    @Override
    public ItemBooleanSetting setKey(int key) {
        super.setKey(key);
        return this;
    }

    @Override
    public ItemBooleanSetting setType(int type) {
        super.setType(type);
        return this;
    }
}