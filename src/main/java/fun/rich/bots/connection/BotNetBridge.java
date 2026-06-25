package fun.rich.bots.connection;

import fun.rich.bots.Bot;
import fun.rich.bots.BotManager;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public final class BotNetBridge {
    private static final Set<Object> DISCONNECT_HANDLED = Collections.newSetFromMap(new IdentityHashMap<>());

    private BotNetBridge() {
    }

    public static boolean handlePacket(Object listener, Object packet) {
        if (listener == null || packet == null) {
            return false;
        }

        if (listener instanceof BotClientLoginNetHandler loginHandler) {
            return BotClientLoginNetHandler.dispatchPacket(loginHandler, packet);
        }

        if (listener instanceof BotClientConfigurationNetHandler configurationHandler) {
            return BotClientConfigurationNetHandler.dispatchPacket(configurationHandler, packet);
        }

        if (listener instanceof BotClientPlayNetHandler playHandler) {
            return BotClientPlayNetHandler.dispatchPacket(playHandler, packet);
        }

        return false;
    }

    public static void tickAll() {
    }

    public static void handleDisconnect(Object listener, Object reason) {
        if (listener == null || !isBotListener(listener)) {
            return;
        }

        synchronized (DISCONNECT_HANDLED) {
            if (DISCONNECT_HANDLED.contains(listener)) {
                return;
            }
            DISCONNECT_HANDLED.add(listener);
        }

        try {
            if (listener instanceof BotClientLoginNetHandler loginHandler) {
                loginHandler.onDisconnected(reason);
            } else if (listener instanceof BotClientConfigurationNetHandler configurationHandler) {
                configurationHandler.onDisconnected(reason);
            } else if (listener instanceof BotClientPlayNetHandler playHandler) {
                playHandler.onDisconnected(reason);
            }

            markByListener(listener, reason);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static boolean isBotListener(Object listener) {
        return listener instanceof BotClientLoginNetHandler
                || listener instanceof BotClientConfigurationNetHandler
                || listener instanceof BotClientPlayNetHandler;
    }

    public static void removeByListener(Object listener) {
        if (listener == null) {
            return;
        }

        List<Bot> bots = new ArrayList<>(BotManager.allBots);
        for (Bot bot : bots) {
            if (bot == null) {
                continue;
            }

            try {
                if (listenerMatches(bot, listener)) {
                    disconnectOnly(bot);
                    clearHandled(listener);
                    return;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    public static Object getListener(Bot bot) {
        if (bot == null || bot.getNetworkManager() == null) {
            return null;
        }

        try {
            Object raw = bot.getNetworkManager().unwrap();

            Object listener = getField(raw, "packetListener");
            if (listener != null) {
                return listener;
            }

            listener = getField(raw, "listener");
            if (listener != null) {
                return listener;
            }

            listener = invokeNoArgs(raw, "getPacketListener");
            if (listener != null) {
                return listener;
            }

            return invokeNoArgs(raw, "getListener");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void clearHandled(Object listener) {
        if (listener == null) {
            return;
        }

        synchronized (DISCONNECT_HANDLED) {
            DISCONNECT_HANDLED.remove(listener);
        }
    }

    private static boolean listenerMatches(Bot bot, Object listener) {
        if (bot == null || listener == null) {
            return false;
        }

        if (bot.getConnection() == listener) {
            return true;
        }

        Object current = getListener(bot);
        return current == listener;
    }

    private static void markByListener(Object listener, Object reason) {
        List<Bot> bots = new ArrayList<>(BotManager.allBots);
        for (Bot bot : bots) {
            if (bot == null) {
                continue;
            }

            try {
                if (!listenerMatches(bot, listener)) {
                    continue;
                }

                String msg = extractDisconnectReason(reason);
                String current = bot.getStateMessage();

                if (!bot.isTerminal()) {
                    bot.markDisconnected();
                }

                if (msg != null && !msg.isBlank()) {
                    bot.setStateMessage(msg);
                } else if (current == null || current.isBlank()) {
                    bot.setStateMessage("Disconnect packet получен, но без текста причины");
                }

                bot.touch();
                return;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private static void disconnectOnly(Bot bot) {
        if (bot == null) {
            return;
        }

        try {
            if (bot.getConnection() instanceof BotClientPlayNetHandler playHandler) {
                playHandler.sendQuittingDisconnectingPacket();
            }
        } catch (Throwable ignored) {
        }

        try {
            if (bot.getNetworkManager() != null) {
                bot.getNetworkManager().markClosedReason("Соединение закрыто");
                bot.getNetworkManager().close();
            }
        } catch (Throwable ignored) {
        }

        bot.markDisconnected();
        bot.setStateMessage("Соединение закрыто");
        bot.touch();
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

    private static Object getField(Object target, String fieldName) {
        if (target == null || fieldName == null) {
            return null;
        }

        try {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    Field field = current.getDeclaredField(fieldName);
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

    private static String extractDisconnectReason(Object reason) {
        if (reason == null) {
            return "";
        }

        if (reason instanceof Text text) {
            String s = text.getString();
            return s == null ? "" : s.trim();
        }

        String text = extractTextDeep(reason);
        if (text != null && !text.isBlank()) {
            return text;
        }

        String fallback = String.valueOf(reason);
        return fallback == null ? "" : fallback.trim();
    }

    private static String extractTextDeep(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Text text) {
            String s = text.getString();
            return s == null || s.isBlank() ? null : s.trim();
        }

        if (obj instanceof String s) {
            s = s.trim();
            return s.isEmpty() ? null : s;
        }

        Object value = invokeNoArgs(obj, "getReason");
        String out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = invokeNoArgs(obj, "reason");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = invokeNoArgs(obj, "getText");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = invokeNoArgs(obj, "getMessage");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = invokeNoArgs(obj, "getContent");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = invokeNoArgs(obj, "asString");
        if (value instanceof String s1 && !s1.isBlank()) {
            return s1.trim();
        }

        value = invokeNoArgs(obj, "getString");
        if (value instanceof String s2 && !s2.isBlank()) {
            return s2.trim();
        }

        value = invokeNoArgs(obj, "toPlain");
        if (value instanceof String s3 && !s3.isBlank()) {
            return s3.trim();
        }

        value = getField(obj, "reason");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = getField(obj, "message");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = getField(obj, "content");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        value = getField(obj, "text");
        out = extractTextDeep(value);
        if (out != null && !out.isBlank()) {
            return out;
        }

        return null;
    }
}