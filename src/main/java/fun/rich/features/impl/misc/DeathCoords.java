package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.util.math.BlockPos;

public class DeathCoords extends Module {

    private final BooleanSetting copyClipboard = new BooleanSetting("Копировать", "Копировать координаты смерти в буфер").setValue(false);
    private final BooleanSetting showDimension = new BooleanSetting("Измерение", "Добавлять измерение (overworld/nether/end)").setValue(true);

    private boolean printed;

    public DeathCoords() {
        super("DeathCoords", ModuleCategory.MISC);
        setup(copyClipboard, showDimension);
    }

    @Override
    public void activate() {
        super.activate();
        printed = false;
    }

    @Override
    public void deactivate() {
        printed = false;
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc == null || mc.player == null) return;

        boolean dead = mc.currentScreen instanceof DeathScreen && mc.player.getHealth() <= 0.0f;

        if (!dead) {
            printed = false;
            return;
        }

        if (printed) return;
        printed = true;

        BlockPos p = mc.player.getBlockPos();
        int x = p.getX();
        int y = p.getY();
        int z = p.getZ();

        String dimPart = "";
        if (showDimension.isValue() && mc.world != null && mc.world.getRegistryKey() != null) {
            String path = mc.world.getRegistryKey().getValue().getPath();
            String dimName = switch (path) {
                case "overworld" -> "§aOverworld";
                case "the_nether" -> "§cNether";
                case "the_end" -> "§dEnd";
                default -> "§7" + path;
            };
            dimPart = " §8(§7" + dimName + "§8)";
        }

        ChatMessage.brandmessage("§c✖ §fСмерть §8• §7X: §f" + x + " §7Y: §f" + y + " §7Z: §f" + z + dimPart);

        if (copyClipboard.isValue()) {
            try {
                mc.keyboard.setClipboard(x + " " + y + " " + z);
                ChatMessage.brandmessage("§8  • §7Скопировано в буфер: §f" + x + " " + y + " " + z);
            } catch (Exception ignored) {
            }
        }
    }
}