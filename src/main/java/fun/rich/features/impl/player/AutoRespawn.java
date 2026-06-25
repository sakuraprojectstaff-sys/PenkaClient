package fun.rich.features.impl.player;

import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.DeathScreenEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.packet.network.Network;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoRespawn extends Module {

    static final double FUN_TIME_X = 1448.0;
    static final double FUN_TIME_Y = 1337.0;
    static final double FUN_TIME_Z = 228.0;

    final SelectSetting modeSetting = new SelectSetting("Режим", "Выберите, что будет использоваться")
            .value("FunTime Back", "Default")
            .selected("Default");

    boolean handled;

    public AutoRespawn() {
        super("AutoRespawn", "AutoRespawn", ModuleCategory.PLAYER);
        setup(modeSetting);
    }

    @Override
    public void activate() {
        super.activate();
        handled = false;
    }

    @Override
    public void deactivate() {
        handled = false;
        super.deactivate();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState()) return;
        if (e == null) return;
        if (e.isSend()) return;
        if (!modeSetting.isSelected("FunTime Back")) return;
        if (mc == null || mc.player == null) return;
        if (!"lobby".equals(Network.getWorldType())) return;
        if (handled) return;

        if (e.getPacket() instanceof DeathMessageS2CPacket) {
            handled = true;
            trySendFunTimeBack();
            tryRespawnAndClose();
        }
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null) return;

        boolean deathScreen = mc.currentScreen instanceof DeathScreen;
        boolean dead = deathScreen && mc.player.getHealth() <= 0.0f;

        if (!dead) {
            handled = false;
            return;
        }

        if (handled) return;

        if (modeSetting.isSelected("FunTime Back")) {
            if ("lobby".equals(Network.getWorldType())) {
                handled = true;
                trySendFunTimeBack();
                tryRespawnAndClose();
            }
            return;
        }

        if (mc.player.deathTime > 5) {
            handled = true;
            tryRespawnAndClose();
        }
    }

    @EventHandler
    public void onDeathScreen(DeathScreenEvent ignored) {
        if (!isState()) return;
        if (!modeSetting.isSelected("Default")) return;
        if (mc == null || mc.player == null) return;

        if (mc.player.getHealth() > 0.0f) {
            handled = false;
            return;
        }

        if (handled) return;

        if (mc.player.deathTime > 5) {
            handled = true;
            tryRespawnAndClose();
        }
    }

    private void trySendFunTimeBack() {
        try {
            if (mc == null || mc.player == null || mc.player.networkHandler == null) return;
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(FUN_TIME_X, FUN_TIME_Y, FUN_TIME_Z, false, false));
        } catch (Throwable ignored) {
        }
    }

    private void tryRespawnAndClose() {
        try {
            if (mc == null || mc.player == null) return;
            mc.player.requestRespawn();
        } catch (Throwable ignored) {
        }
        try {
            if (mc == null || mc.player == null) return;
            mc.player.closeScreen();
        } catch (Throwable ignored) {
        }
        try {
            if (mc == null) return;
            mc.setScreen(null);
        } catch (Throwable ignored) {
        }
    }
}