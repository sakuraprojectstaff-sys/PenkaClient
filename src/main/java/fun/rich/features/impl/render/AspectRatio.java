package fun.rich.features.impl.render;

import fun.rich.events.render.AspectRatioEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AspectRatio extends Module {

    SelectSetting preset = new SelectSetting("Монитор", "Выберите соотношение сторон")
            .value("21:9", "16:10", "16:9", "4:3", "Свое", "По умолчанию")
            .selected("16:10");

    SliderSettings custom = new SliderSettings("Ширина", "Свое значение соотношения сторон")
            .setValue(1.6F).range(0.6F, 2.5F)
            .visible(() -> preset.isSelected("Свое"));

    public AspectRatio() {
        super("AspectRatio", "AspectRatio", ModuleCategory.RENDER);
        setup(preset, custom);
    }

    @EventHandler
    public void onAspectRatio(AspectRatioEvent e) {
        if (!isState()) return;
        if (mc == null || mc.getWindow() == null) return;

        float ratio;

        if (preset.isSelected("21:9")) ratio = 21.0f / 9.0f;
        else if (preset.isSelected("16:9")) ratio = 16.0f / 9.0f;
        else if (preset.isSelected("16:10")) ratio = 16.0f / 10.0f;
        else if (preset.isSelected("4:3")) ratio = 4.0f / 3.0f;
        else if (preset.isSelected("Свое")) ratio = custom.getValue();
        else {
            int w = mc.getWindow().getFramebufferWidth();
            int h = mc.getWindow().getFramebufferHeight();
            ratio = h <= 0 ? 1.0f : (float) w / (float) h;
        }

        if (ratio < 0.1f) ratio = 0.1f;
        if (ratio > 3.0f) ratio = 3.0f;

        e.setRatio(ratio);
        e.cancel();
    }
}