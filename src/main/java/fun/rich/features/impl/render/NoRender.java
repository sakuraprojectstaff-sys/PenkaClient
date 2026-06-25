package fun.rich.features.impl.render;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffects;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoRender extends Module {

    public static NoRender getInstance() {
        return Instance.get(NoRender.class);
    }

    final MultiSelectSetting modeSetting = new MultiSelectSetting("Элементы", "Выберите элементы для игнорирования")
            .value("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage")
            .selected("Fire", "Bad Effects", "Block Overlay", "Darkness", "Damage");

    public NoRender() {
        super("NoRender", "No Render", ModuleCategory.RENDER);
        setup(modeSetting);
    }

    public boolean has(String key) {
        return isState() && modeSetting.isSelected(key);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        if (has("Bad Effects")) {
            mc.player.removeStatusEffect(StatusEffects.NAUSEA);
        }

        if (has("Darkness")) {
            mc.player.removeStatusEffect(StatusEffects.DARKNESS);
        }
    }
}
