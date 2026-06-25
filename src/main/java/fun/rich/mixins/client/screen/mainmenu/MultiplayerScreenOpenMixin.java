package fun.rich.mixins.client.screen.mainmenu;

import fun.rich.common.proxy.Config;
import fun.rich.common.proxy.GuiProxy;
import fun.rich.common.proxy.Proxy;
import fun.rich.common.proxy.ProxyServer;
import fun.rich.features.impl.misc.SelfDestruct;
import fun.rich.mixins.client.screen.IScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public class MultiplayerScreenOpenMixin {
    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;updateButtonActivationStates()V"))
    public void multiplayerGuiOpen(CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        if (!playerName.equals(Config.lastPlayerName)) {
            Config.lastPlayerName = playerName;
            if (Config.accounts.containsKey(playerName)) {
                ProxyServer.proxy = Config.accounts.get(playerName);
            } else {
                if (Config.accounts.containsKey("")) {
                    ProxyServer.proxy = Config.accounts.get("");
                } else {
                    ProxyServer.proxy = new Proxy();
                }
            }
        }

        MultiplayerScreen ms = (MultiplayerScreen) (Object) this;

        String proxyText = ProxyServer.proxyEnabled && ProxyServer.proxy != null && !ProxyServer.proxy.ipPort.isEmpty()
                ? "Прокси: Активен"
                : "Proxy";

        ButtonWidget proxyButton = ButtonWidget.builder(Text.literal(proxyText), button -> {
            MinecraftClient.getInstance().setScreen(new GuiProxy(ms));
        }).dimensions(5, 5, 100, 20).build();

        ButtonWidget safeModeButton = ButtonWidget.builder(Text.literal(Config.safeModeEnabled ? "Safe: On" : "Safe: Off"), button -> {
            Config.safeModeEnabled = !Config.safeModeEnabled;
            Config.saveConfig();
            button.setMessage(Text.literal(Config.safeModeEnabled ? "Safe: On" : "Safe: Off"));
        }).dimensions(109, 5, 76, 20).build();

        IScreen screen = (IScreen) ms;
        screen.getDrawables().add(proxyButton);
        screen.getSelectables().add(proxyButton);
        screen.getChildren().add(proxyButton);

        screen.getDrawables().add(safeModeButton);
        screen.getSelectables().add(safeModeButton);
        screen.getChildren().add(safeModeButton);

        ProxyServer.proxyMenuButton = proxyButton;
    }
}