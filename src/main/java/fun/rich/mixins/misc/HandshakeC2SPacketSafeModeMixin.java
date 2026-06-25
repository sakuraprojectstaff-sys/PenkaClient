package fun.rich.mixins.misc;

import fun.rich.common.proxy.Config;
import fun.rich.common.proxy.LocalProxyManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Locale;

@Mixin(HandshakeC2SPacket.class)
public class HandshakeC2SPacketSafeModeMixin {
    @Mutable
    @Shadow
    @Final
    private String address;

    @Mutable
    @Shadow
    @Final
    private int port;

    @Inject(method = "write(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("HEAD"))
    private void rich$fixHandshakeHost(PacketByteBuf buf, CallbackInfo ci) {
        if (!Config.safeModeEnabled) {
            return;
        }

        int localPort;
        String bindHost;
        try {
            LocalProxyManager lpm = LocalProxyManager.getInstance();
            localPort = lpm.getLocalPort();
            bindHost = lpm.getBindHost();
        } catch (Throwable t) {
            return;
        }

        if (localPort <= 0) {
            return;
        }

        if (!rich$isLocalHost(this.address, bindHost) || this.port != localPort) {
            return;
        }

        String h = (String) rich$getConnectMixinField("RICH_FORCE_HANDSHAKE_HOST", null);
        int p = (int) rich$getConnectMixinField("RICH_FORCE_HANDSHAKE_PORT", -1);

        if (h == null || h.isEmpty() || p <= 0) {
            return;
        }

        this.address = h;
        this.port = p;

        rich_setConnectMixinField("RICH_FORCE_HANDSHAKE_HOST", null);
        rich_setConnectMixinField("RICH_FORCE_HANDSHAKE_PORT", -1);
    }

    @Unique
    private static boolean rich$isLocalHost(String host, String bindHost) {
        if (host == null) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        if (h.equals("127.0.0.1") || h.equals("localhost")) {
            return true;
        }
        if (bindHost == null || bindHost.isEmpty()) {
            return false;
        }
        return h.equals(bindHost.trim().toLowerCase(Locale.ROOT));
    }

    @Unique
    private static Object rich$getConnectMixinField(String name, Object def) {
        try {
            Field f = ConnectScreenMixin.class.getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(null);
            return v != null ? v : def;
        } catch (Throwable t) {
            return def;
        }
    }

    @Unique
    private static void rich_setConnectMixinField(String name, Object value) {
        try {
            Field f = ConnectScreenMixin.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(null, value);
        } catch (Throwable ignored) {
        }
    }
}