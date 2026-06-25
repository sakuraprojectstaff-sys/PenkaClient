package fun.rich.mixins.misc;

import fun.rich.common.proxy.Config;
import fun.rich.common.proxy.SafeModeIndicator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

@Mixin(InGameHud.class)
public class InGameHudSafeModeOverlayMixin {
    private static final String SAFE_HOST_1 = "127.0.0.1";
    private static final String SAFE_HOST_2 = "localhost";
    private static final int SAFE_PORT = 25566;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void rich$renderSafeModeHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!SafeModeIndicator.shouldShow()) {
            return;
        }

        InetSocketAddress isa = getConnAddress(mc);
        if (isa == null) {
            return;
        }

        String host = isa.getHostString();
        int port = isa.getPort();
        if (!isSafeHost(host) || port != SAFE_PORT) {
            return;
        }

        String title = Config.safeModeEnabled ? "SAFE MODE: ON" : "SAFE MODE: ACTIVE";
        String addr = host + ":" + port;

        int w = mc.getWindow().getScaledWidth();
        MatrixStack ms = context.getMatrices();

        ms.push();
        float s1 = 2.0f;
        ms.scale(s1, s1, 1.0f);
        int x1 = (int) ((w / 2.0f) / s1);
        int y1 = (int) (10.0f / s1);
        context.drawCenteredTextWithShadow(mc.textRenderer, title, x1, y1, 0xFFFF0000);
        ms.pop();

        ms.push();
        float s2 = 1.5f;
        ms.scale(s2, s2, 1.0f);
        int x2 = (int) ((w / 2.0f) / s2);
        int y2 = (int) (36.0f / s2);
        context.drawCenteredTextWithShadow(mc.textRenderer, addr, x2, y2, 0xFFFF0000);
        ms.pop();
    }

    private static InetSocketAddress getConnAddress(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) {
            return null;
        }

        if (!mc.getNetworkHandler().getConnection().isOpen()) {
            return null;
        }

        SocketAddress a = mc.getNetworkHandler().getConnection().getAddress();
        if (a instanceof InetSocketAddress isa) {
            return isa;
        }

        return null;
    }

    private static boolean isSafeHost(String host) {
        if (host == null) {
            return false;
        }
        return SAFE_HOST_1.equals(host) || SAFE_HOST_2.equalsIgnoreCase(host);
    }
}