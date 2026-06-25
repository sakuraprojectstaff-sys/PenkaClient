package fun.rich.features.impl.movement;

import antidaunleak.api.annotation.Native;
import fun.rich.events.player.TickEvent;
import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Hand;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.managers.event.types.EventType;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.Instance;
import fun.rich.utils.math.script.Script;
import fun.rich.events.item.UsingItemEvent;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NoSlow extends Module {

    public static NoSlow getInstance() {
        return Instance.get(NoSlow.class);
    }

    final Script script = new Script();

    public final SelectSetting itemMode = new SelectSetting("Режим предмета", "Выберите режим обхода")
            .value("Vanilla", "HolyWorld", "Grim Latest", "SpookyTime")
            .selected("Vanilla");

    int grimTicks = 0;
    int spookyTicks = 0;

    boolean spookyWantSprint = false;
    boolean spookyUsingPrev = false;

    public NoSlow() {
        super("NoSlow", "No Slow", ModuleCategory.MOVEMENT);
        setup(itemMode);
    }

    private boolean moving() {
        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    @EventHandler
    public void onUpdate(TickEvent event) {
        if (PlayerInteractionHelper.nullCheck()) {
            grimTicks = 0;
            spookyTicks = 0;
            spookyWantSprint = false;
            spookyUsingPrev = false;
            return;
        }

        if (mc.player.isGliding()) {
            grimTicks = 0;
            spookyTicks = 0;
            spookyWantSprint = false;
            spookyUsingPrev = false;
            return;
        }

        Hand h = mc.player.getActiveHand();
        boolean using = (h == Hand.MAIN_HAND || h == Hand.OFF_HAND) && mc.player.getItemUseTime() > 0;

        if (!using) {
            grimTicks = 0;
            spookyTicks = 0;
            spookyWantSprint = false;
            spookyUsingPrev = false;
            return;
        }

        if (itemMode.isSelected("Grim Latest")) {
            grimTicks++;
        } else {
            grimTicks = 0;
        }

        if (itemMode.isSelected("SpookyTime")) {
            spookyTicks++;

            if (!spookyUsingPrev) {
                spookyWantSprint = mc.player.isSprinting() || mc.options.sprintKey.isPressed();
                spookyUsingPrev = true;
            }

            if (spookyWantSprint && moving() && !mc.options.sneakKey.isPressed()) {
                mc.player.setSprinting(true);
            }
        } else {
            spookyTicks = 0;
            spookyWantSprint = false;
            spookyUsingPrev = false;
        }
    }

    @EventHandler
    @Native(type = Native.Type.VMProtectBeginUltra)
    public void onUsingItem(UsingItemEvent e) {
        if (PlayerInteractionHelper.nullCheck()) return;

        Hand first = mc.player.getActiveHand();
        if (first != Hand.MAIN_HAND && first != Hand.OFF_HAND) return;

        Hand second = first == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND;

        switch (e.getType()) {
            case EventType.ON -> {
                switch (itemMode.getSelected()) {

                    case "Vanilla" -> {
                        e.cancel();
                    }

                    case "HolyWorld" -> {
                        UseAction off = mc.player.getOffHandStack().getUseAction();
                        if ((off == UseAction.BLOCK || off == UseAction.EAT) && first == Hand.MAIN_HAND) {
                            return;
                        }
                        PlayerInteractionHelper.interactItem(first);
                        PlayerInteractionHelper.interactItem(second);
                        e.cancel();
                    }

                    case "Grim Latest" -> {
                        if (grimTicks >= 2 && mc.player.getItemUseTime() > 0) {
                            e.cancel();
                            grimTicks = 0;
                        }
                    }

                    case "SpookyTime" -> {
                        if (mc.player.getItemUseTime() <= 1) return;
                        if (spookyTicks <= 1) return;

                        if ((spookyTicks & 1) == 0) {
                            e.cancel();
                        }
                    }
                }
            }

            case EventType.POST -> {
                while (!script.isFinished()) {
                    script.update();
                }
            }
        }
    }
}
