package fun.rich.features.impl.render;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;

public class ArmorDurability extends Module {
    public static ArmorDurability INSTANCE;

    public ArmorDurability() {
        super("ArmorDurability", "Armor Durability", ModuleCategory.RENDER);
        INSTANCE = this;
    }
}