package fun.rich.mixins.misc;

import fun.rich.common.proxy.Config;
import fun.rich.common.proxy.LocalProxyManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.Locale;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {
    @Unique
    private static final int RICH_LOCAL_PROXY_PORT = 25566;

    @Unique
    private static final ThreadLocal<Boolean> RICH_REDIRECT_GUARD = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void rich$connectViaLocalProxy(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, boolean quickPlay, @Nullable CookieStorage cookieStorage, CallbackInfo ci) {
        if (RICH_REDIRECT_GUARD.get()) {
            return;
        }

        String host = address.getAddress();
        int port = address.getPort();

        if (host.isEmpty()) {
            return;
        }

        LocalProxyManager proxy = LocalProxyManager.getInstance();

        boolean isLocal = rich$isLocalAddress(host);
        boolean proxyRunning = proxy.isRunning();
        boolean upstreamMatch = proxyRunning
                && !isLocal
                && host.equalsIgnoreCase(proxy.getUpstreamHost())
                && port == proxy.getUpstreamPort();

        boolean shouldRedirect = Config.safeModeEnabled || upstreamMatch;

        if (!shouldRedirect) {
            return;
        }

        if (isLocal) {
            return;
        }

        try {
            if (Config.safeModeEnabled) {
                proxy.ensureRunning(host, port, host, RICH_LOCAL_PROXY_PORT);
            } else {
                if (!proxyRunning || !upstreamMatch) {
                    return;
                }
            }

            ServerAddress redirected = new ServerAddress(proxy.getBindHost(), proxy.getLocalPort());

            ci.cancel();
            RICH_REDIRECT_GUARD.set(true);
            ConnectScreen.connect(screen, client, redirected, info, quickPlay, cookieStorage);
        } catch (IOException e) {
            ci.cancel();
            client.setScreen(new DisconnectedScreen(
                    screen,
                    ScreenTexts.CONNECT_FAILED,
                    Text.literal("Failed to start local proxy: " + rich$cleanMessage(e))
            ));
        } finally {
            RICH_REDIRECT_GUARD.set(false);
        }
    }

    @Unique
    private static boolean rich$isLocalAddress(String host) {
        String value = host.trim().toLowerCase(Locale.ROOT);
        return value.equals("127.0.0.1")
                || value.equals("localhost")
                || value.equals("0.0.0.0")
                || value.equals("::1")
                || value.equals("[::1]");
    }

    @Unique
    private static String rich$cleanMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}