package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class TapeMouse extends Module {
    private final BooleanSetting wallsRayTrace = new BooleanSetting("Проверка на видимость", "Кликает только если прицел на энтити")
            .setValue(false);

    private final SliderSettings delay = new SliderSettings("Задержка", "Задержка (мс)")
            .setValue(1000).range(100F, 5000F);

    private final SelectSetting button = new SelectSetting("Кнопка", "Какая кнопка кликается")
            .value("ЛКМ", "ПКМ")
            .selected("ЛКМ");

    private long lastClickMs;

    public TapeMouse() {
        super("TapeMouse", "TapeMouse", ModuleCategory.MISC);
        setup(wallsRayTrace, delay, button);
    }

    @Override
    public void activate() {
        super.activate();
        lastClickMs = 0L;
    }

    @Override
    public void deactivate() {
        super.deactivate();
        lastClickMs = 0L;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (wallsRayTrace.isValue()) {
            if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY) return;
        }

        long now = System.currentTimeMillis();
        long d = (long) delay.getValue();
        if (d < 1L) d = 1L;

        if (lastClickMs == 0L) lastClickMs = now;

        if (now - lastClickMs >= d) {
            if (button.isSelected("ЛКМ")) {
                mc.doAttack();
            } else {
                doRightClick();
            }
            lastClickMs = now;
        }
    }

    private void doRightClick() {
        if (mc.crosshairTarget instanceof BlockHitResult bhr) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}