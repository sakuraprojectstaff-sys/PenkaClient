package fun.rich.features.impl.misc;

import fun.rich.events.packet.PacketEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

public class LockSlot extends Module {

    private final SelectSetting mode = new SelectSetting("Режим", "Какие слоты защищать")
            .value("Все слоты", "Кастом")
            .selected("Все слоты");

    private final MultiSelectSetting slots = new MultiSelectSetting("Слоты", "Защищаемые хотбар-слоты")
            .value("1", "2", "3", "4", "5", "6", "7", "8", "9")
            .selected("1", "2", "3", "4", "5")
            .visible(() -> mode.isSelected("Кастом"));

    private final BooleanSetting pressedCtrl = new BooleanSetting("Работать с ctrl", "Блокировать и Ctrl+Q (выброс стака)")
            .setValue(false);

    public LockSlot() {
        super("LockSlot", ModuleCategory.MISC);
        setup(mode, slots, pressedCtrl);
    }

    public boolean isSlotProtected(int slotIndex0to8) {
        return isSlotProtected(slotIndex0to8, false);
    }

    public boolean isSlotProtected(int slotIndex0to8, boolean ctrlDrop) {
        if (!isState()) return false;
        if (slotIndex0to8 < 0 || slotIndex0to8 > 8) return false;

        if (ctrlDrop && !pressedCtrl.isValue()) return false;

        if (mode.isSelected("Все слоты")) return true;
        if (!mode.isSelected("Кастом")) return false;

        return slots.isSelected(String.valueOf(slotIndex0to8 + 1));
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState()) return;
        if (e == null || !e.isSend()) return;
        if (mc == null || mc.player == null) return;

        Packet<?> p = e.getPacket();
        if (p == null) return;

        if (p instanceof PlayerActionC2SPacket pa) {
            PlayerActionC2SPacket.Action a = pa.getAction();
            if (a == PlayerActionC2SPacket.Action.DROP_ITEM || a == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS) {
                boolean ctrlDrop = a == PlayerActionC2SPacket.Action.DROP_ALL_ITEMS;
                int slot = mc.player.getInventory().selectedSlot;
                if (isSlotProtected(slot, ctrlDrop)) e.cancel();
            }
            return;
        }

        if (p instanceof ClickSlotC2SPacket cs) {
            if (cs.getActionType() != SlotActionType.THROW) return;

            int hotbar = hotbarIndexFromPacketSlot(cs.getSlot());
            if (hotbar < 0) return;

            boolean ctrlDrop = cs.getButton() == 1;
            if (isSlotProtected(hotbar, ctrlDrop)) e.cancel();
        }
    }

    private int hotbarIndexFromPacketSlot(int slotId) {
        if (mc == null || mc.player == null) return -1;

        ScreenHandler sh = mc.player.currentScreenHandler;
        if (sh == null || sh.slots == null) return -1;

        int total = sh.slots.size();
        int start = total - 9;
        if (start < 0) return -1;

        if (slotId >= start && slotId < total) return slotId - start;
        return -1;
    }
}