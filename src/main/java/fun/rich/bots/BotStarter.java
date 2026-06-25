package fun.rich.bots;

import com.mojang.authlib.GameProfile;
import fun.rich.bots.connection.BotClientConfigurationNetHandler;
import fun.rich.bots.connection.BotClientLoginNetHandler;
import fun.rich.bots.connection.BotClientPlayNetHandler;
import fun.rich.bots.connection.BotNetwork;
import io.netty.channel.ChannelFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.network.state.LoginStates;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public final class BotStarter {
    private static final long LOGIN_TIMEOUT_MS = 20000L;
    private static final long LOOP_SLEEP_MS = 50L;

    private BotStarter() {
    }

    public static void run(String name, String ip) {
        run(name, ip, 25565);
    }

    public static void run(String name, String ip, int port) {
        String safeName = sanitizeName(name);
        String safeIp = ip == null ? "" : ip.trim();
        int safePort = clampPort(port);

        if (safeName.isEmpty() || safeIp.isEmpty()) {
            return;
        }

        Bot pending = BotManager.createPending(safeName, safeIp, safePort);
        pending.setStateMessage("Подготовка подключения");
        pending.touch();

        Thread thread = new Thread(() -> connectInternal(pending, safeName, safeIp, safePort), "bot-connect-" + safeName);
        thread.setDaemon(true);
        thread.start();
    }

    private static void connectInternal(Bot pending, String name, String ip, int port) {
        BotNetwork network = null;

        try {
            pending.setName(name);
            pending.setTargetHost(ip);
            pending.setTargetPort(port);
            pending.markConnecting();
            pending.setStateMessage("Подключение к " + ip + ":" + port);
            pending.touch();

            GameProfile profile = new GameProfile(stableOfflineUuid(name), name);

            network = BotNetwork.createVanillaClientConnection();
            pending.attachNetwork(network);
            pending.setStateMessage("ClientConnection создан");
            pending.touch();

            InetSocketAddress socketAddress = resolve(ip, port);
            if (socketAddress == null) {
                pending.markFailed("Не удалось разрешить адрес");
                safeClose(network);
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            ChannelFuture future = network.connectVanilla(socketAddress, mc.options.shouldUseNativeTransport());
            pending.setStateMessage("Socket connect...");
            pending.touch();

            future.syncUninterruptibly();

            if (!future.isSuccess()) {
                Throwable cause = future.cause();
                pending.markFailed(cause == null ? "Не удалось открыть канал" : shortError(cause));
                safeClose(network);
                return;
            }

            pending.setStateMessage("Сокет открыт");
            pending.touch();

            BotClientLoginNetHandler loginHandler = new BotClientLoginNetHandler(
                    network,
                    mc,
                    null,
                    createStatusConsumer(pending),
                    pending,
                    profile,
                    ip,
                    port
            );

            network.beginVanillaLogin(
                    socketAddress.getHostName(),
                    socketAddress.getPort(),
                    LoginStates.C2S,
                    LoginStates.S2C,
                    loginHandler,
                    false
            );

            pending.markLogin();
            pending.setStateMessage("Custom login handler установлен");
            pending.touch();

            rawConnection(network).send(new LoginHelloC2SPacket(profile.getName(), profile.getId()));
            pending.setStateMessage("Login hello отправлен");
            pending.touch();

            long startedAt = System.currentTimeMillis();

            while (!Thread.currentThread().isInterrupted()) {
                if (!network.isOpen()) {
                    String closeReason = network.getLastCloseReason();
                    if (closeReason == null || closeReason.isBlank()) {
                        closeReason = readPendingStateFromVanilla(network);
                    }
                    if (closeReason == null || closeReason.isBlank()) {
                        closeReason = pending.getStateMessage();
                    }
                    if (closeReason == null || closeReason.isBlank()) {
                        closeReason = "Соединение закрыто";
                    }

                    if (!pending.isTerminal()) {
                        pending.markDisconnected();
                        pending.setStateMessage(closeReason);
                        pending.touch();
                    }

                    safeClose(network);
                    return;
                }

                network.tick();

                Object listener = currentListener(network);
                String listenerName = listener == null ? "" : listener.getClass().getName();

                if (listener instanceof BotClientPlayNetHandler playHandler) {
                    pending.attachNetwork(network);
                    pending.attachConnection(playHandler);
                    playHandler.setBotOwner(pending);
                    pending.refreshRuntimeState();
                    pending.markPlay();

                    if (pending.getStateMessage() == null || pending.getStateMessage().isBlank() || pending.getStateMessage().contains("configuration")) {
                        pending.setStateMessage("Custom play phase");
                    }

                    pending.touch();
                    BotManager.mergeIntoPending(pending);
                    return;
                }

                if (listener instanceof BotClientConfigurationNetHandler) {
                    pending.markLogin();
                    pending.setStateMessage("Custom configuration phase");
                    pending.touch();
                } else if (listener instanceof BotClientLoginNetHandler) {
                    pending.markLogin();
                    pending.setStateMessage("Custom login phase");
                    pending.touch();
                } else if (listenerName.contains("ClientPlayNetworkHandler")) {
                    pending.markLogin();
                    pending.setStateMessage("Обнаружен vanilla play listener");
                    pending.touch();
                } else if (listenerName.contains("ClientConfigurationNetworkHandler")) {
                    pending.markLogin();
                    pending.setStateMessage("Обнаружен vanilla configuration listener");
                    pending.touch();
                }

                long now = System.currentTimeMillis();
                if (now - startedAt >= LOGIN_TIMEOUT_MS) {
                    pending.markFailed("Таймаут входа");
                    pending.touch();
                    safeClose(network);
                    return;
                }

                try {
                    Thread.sleep(LOOP_SLEEP_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    safeClose(network);
                    return;
                }
            }
        } catch (Throwable t) {
            pending.markFailed(shortError(t));
            pending.touch();
            t.printStackTrace();
            safeClose(network);
        }
    }

    private static Consumer<Text> createStatusConsumer(Bot pending) {
        return status -> {
            if (pending == null || status == null || pending.isTerminal()) {
                return;
            }

            try {
                String text = status.getString();
                if (text == null || text.isBlank()) {
                    text = "Login status update";
                }

                pending.markLogin();
                pending.setStateMessage(text);
                pending.touch();
            } catch (Throwable ignored) {
            }
        };
    }

    private static Object currentListener(BotNetwork network) {
        try {
            Object raw = network.unwrap();
            Object listener = getFieldRecursive(raw, "packetListener");
            if (listener == null) {
                listener = getFieldRecursive(raw, "listener");
            }
            if (listener == null) {
                listener = invokeNoArgs(raw, "getPacketListener");
            }
            if (listener == null) {
                listener = invokeNoArgs(raw, "getListener");
            }
            return listener;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String readPendingStateFromVanilla(BotNetwork network) {
        try {
            Object raw = network.unwrap();
            Object info = getFieldRecursive(raw, "disconnectionInfo");
            if (info == null) {
                info = getFieldRecursive(raw, "disconnectInfo");
            }
            if (info == null) {
                info = getFieldRecursive(raw, "disconnectionReason");
            }
            if (info == null) {
                info = getFieldRecursive(raw, "reason");
            }
            if (info == null) {
                return "";
            }

            Object reason = invokeNoArgs(info, "reason");
            if (reason == null) {
                reason = invokeNoArgs(info, "getReason");
            }
            if (reason == null) {
                reason = getFieldRecursive(info, "reason");
            }
            if (reason == null) {
                reason = info;
            }

            if (reason instanceof Text text) {
                return text.getString();
            }

            Object stringValue = invokeNoArgs(reason, "getString");
            if (stringValue instanceof String s && !s.isBlank()) {
                return s;
            }

            return String.valueOf(reason);
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static net.minecraft.network.ClientConnection rawConnection(BotNetwork network) {
        Object raw = network.unwrap();
        if (raw instanceof net.minecraft.network.ClientConnection connection) {
            return connection;
        }
        throw new IllegalStateException("BotNetwork не содержит ClientConnection");
    }

    private static InetSocketAddress resolve(String ip, int port) {
        try {
            return new InetSocketAddress(ip, port);
        } catch (Throwable ignored) {
        }

        try {
            return new InetSocketAddress(java.net.InetAddress.getByName(ip), port);
        } catch (UnknownHostException ignored) {
        }

        return null;
    }

    private static void safeClose(BotNetwork network) {
        try {
            if (network != null) {
                network.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getFieldRecursive(Object target, String name) {
        if (target == null || name == null) {
            return null;
        }

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }

        try {
            for (Method method : target.getClass().getMethods()) {
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
                for (Method method : current.getDeclaredMethods()) {
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

    private static UUID stableOfflineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String out = name.trim();
        if (out.length() > 16) {
            out = out.substring(0, 16);
        }
        return out;
    }

    private static int clampPort(int port) {
        return port < 1 || port > 65535 ? 25565 : port;
    }

    private static String shortError(Throwable t) {
        if (t == null) {
            return "unknown error";
        }

        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }

        msg = msg.trim();
        if (msg.length() > 120) {
            msg = msg.substring(0, 120);
        }
        return t.getClass().getSimpleName() + ": " + msg;
    }
}