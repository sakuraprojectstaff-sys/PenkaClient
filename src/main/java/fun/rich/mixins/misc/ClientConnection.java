package fun.rich.mixins.misc;

import fun.rich.bots.connection.BotNetBridge;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = net.minecraft.network.ClientConnection.class)
public class ClientConnection {
    @Inject(method = "handlePacket", at = @At("HEAD"), cancellable = true)
    private static void rich$handleBotPacket(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        try {
            if (listener != null && BotNetBridge.isBotListener(listener) && BotNetBridge.handlePacket(listener, packet)) {
                ci.cancel();
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void rich$handleBotDisconnection(CallbackInfo ci) {
        try {
            Object listener = rich$getPacketListener();
            if (listener == null || !BotNetBridge.isBotListener(listener)) {
                return;
            }

            Object reason = rich$extractDisconnectReason(this);
            BotNetBridge.handleDisconnect(listener, reason);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Unique
    private Object rich$getPacketListener() {
        Object self = this;

        Object listener = rich$getField(self, "packetListener");
        if (listener != null) {
            return listener;
        }

        listener = rich$getField(self, "listener");
        if (listener != null) {
            return listener;
        }

        listener = rich$invokeNoArgs(self, "getPacketListener");
        if (listener != null) {
            return listener;
        }

        return rich$invokeNoArgs(self, "getListener");
    }

    @Unique
    private static Object rich$extractDisconnectReason(Object connection) {
        if (connection == null) {
            return null;
        }

        Object info = rich$getField(connection, "disconnectionInfo");
        if (info == null) {
            info = rich$getField(connection, "disconnectInfo");
        }
        if (info == null) {
            info = rich$getField(connection, "disconnectionReason");
        }

        if (info instanceof DisconnectionInfo disconnectionInfo) {
            return disconnectionInfo.reason();
        }

        if (info != null) {
            Object reason = rich$invokeNoArgs(info, "reason");
            if (reason != null) {
                return reason;
            }

            reason = rich$invokeNoArgs(info, "getReason");
            if (reason != null) {
                return reason;
            }

            Object fieldReason = rich$getField(info, "reason");
            if (fieldReason != null) {
                return fieldReason;
            }

            return info;
        }

        Object reason = rich$getField(connection, "reason");
        if (reason != null) {
            return reason;
        }

        reason = rich$getField(connection, "disconnectReason");
        if (reason != null) {
            return reason;
        }

        return null;
    }

    @Unique
    private static Object rich$invokeNoArgs(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }

        try {
            for (java.lang.reflect.Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 0) {
                    continue;
                }
                method.setAccessible(true);
                return method.invoke(target);
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> current = target.getClass();
            while (current != null) {
                for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterCount() != 0) {
                        continue;
                    }
                    method.setAccessible(true);
                    return method.invoke(target);
                }
                current = current.getSuperclass();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Unique
    private static Object rich$getField(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }

        try {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}