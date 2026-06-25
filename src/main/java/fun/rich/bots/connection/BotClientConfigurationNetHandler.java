package fun.rich.bots.connection;

import com.mojang.authlib.GameProfile;
import fun.rich.bots.Bot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.NetworkPhase;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.s2c.config.DynamicRegistriesS2CPacket;
import net.minecraft.network.packet.s2c.config.FeaturesS2CPacket;
import net.minecraft.network.packet.s2c.config.ReadyS2CPacket;
import net.minecraft.network.packet.s2c.config.SelectKnownPacksS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public final class BotClientConfigurationNetHandler {
    private final BotNetwork connection;
    private final MinecraftClient mc;
    private final Screen parentScreen;
    private final Consumer<?> statusConsumer;
    private final Bot pendingBot;
    private final GameProfile profile;
    private final String targetHost;
    private final int targetPort;

    private boolean finishSent;
    private boolean switchedToPlay;
    private boolean terminalStateReached;

    private long lastFinishAttemptAt;
    private int finishAttempts;

    private final long enteredAt;

    public BotClientConfigurationNetHandler(BotNetwork connection, MinecraftClient mc, Screen parentScreen, Consumer<?> statusConsumer, Bot pendingBot, GameProfile profile, String targetHost, int targetPort) {
        this.connection = connection;
        this.mc = mc;
        this.parentScreen = parentScreen;
        this.statusConsumer = statusConsumer;
        this.pendingBot = pendingBot;
        this.profile = profile;
        this.targetHost = targetHost == null ? "" : targetHost;
        this.targetPort = targetPort;
        this.enteredAt = System.currentTimeMillis();
        updateState("CONFIG", "Вход в configuration phase");
        sendFinishIfNeeded();
    }

    public NetworkPhase getPhase() {
        return NetworkPhase.CONFIGURATION;
    }

    public NetworkSide getSide() {
        return NetworkSide.CLIENTBOUND;
    }

    public boolean isConnectionOpen() {
        return !terminalStateReached && connection != null && connection.isOpen();
    }

    public void addCustomCrashReportInfo(CrashReport report, CrashReportSection section) {
        if (section != null) {
            section.add("Server type", () -> "BOT");
            section.add("Phase", () -> "CONFIGURATION");
            section.add("Bot Target", () -> targetHost + ":" + targetPort);
        }
    }

    public void onDisconnected(DisconnectionInfo info) {
        Object reason = null;
        try {
            if (info != null) {
                reason = info.reason();
            }
        } catch (Throwable ignored) {
        }
        onDisconnectedInternal(reason);
    }

    public void onReady(ReadyS2CPacket packet) {
        onFinishConfiguration(packet);
    }

    public void onDynamicRegistries(DynamicRegistriesS2CPacket packet) {
        onDynamicRegistriesInternal(packet);
    }

    public void onFeatures(FeaturesS2CPacket packet) {
        onFeaturesInternal(packet);
    }

    public void onSelectKnownPacks(SelectKnownPacksS2CPacket packet) {
        onSelectKnownPacksInternal(packet);
    }

    public void onDisconnected(Object reason) {
        onDisconnectedInternal(reason);
    }

    public void onDisconnect(Object reason) {
        onDisconnectedInternal(reason);
    }

    public void onPacket(Object packet) {
        dispatchPacket(this, packet);
    }

    public void handlePacket(Object packet) {
        dispatchPacket(this, packet);
    }

    public void accept(Object packet) {
        dispatchPacket(this, packet);
    }

    public void tick() {
        if (terminalStateReached) {
            return;
        }

        if (!connection.isOpen()) {
            String r = connection.getLastCloseReason();
            onDisconnectedInternal(r == null || r.isBlank() ? Text.literal("Соединение закрыто") : Text.literal(r));
            return;
        }

        long now = System.currentTimeMillis();

        if (!finishSent) {
            if (now - lastFinishAttemptAt > 1500L && now - enteredAt > 900L) {
                sendFinishIfNeeded();
            }
        }
    }

    public static boolean dispatchPacket(BotClientConfigurationNetHandler handler, Object packet) {
        if (handler == null || packet == null || handler.terminalStateReached) {
            return false;
        }

        String simple = packet.getClass().getSimpleName();
        String lower = simple.toLowerCase();
        String full = packet.getClass().getName().toLowerCase();

        try {
            handler.updateState("CONFIG", "Config packet: " + simple);

            if (lower.contains("disconnect") || full.contains("disconnect")) {
                handler.onDisconnectedInternal(packet);
                return true;
            }

            if (lower.contains("dynamicregistries") || lower.contains("dynamic_registry") || full.contains("dynamicregistries")) {
                handler.onDynamicRegistriesInternal(packet);
                return true;
            }

            if (lower.contains("features") || full.contains("features")) {
                handler.onFeaturesInternal(packet);
                return true;
            }

            if (lower.contains("selectknownpacks") || lower.contains("knownpacks")) {
                handler.onSelectKnownPacksInternal(packet);
                return true;
            }

            if (lower.contains("cookierequest") || (lower.contains("cookie") && lower.contains("request"))) {
                handler.onCookieRequest(packet);
                return true;
            }

            if (lower.contains("resourcepacksend") || (lower.contains("resourcepack") && lower.contains("send"))) {
                handler.onResourcePackSend(packet);
                return true;
            }

            if (lower.contains("keepalive") || (lower.contains("ping") && full.contains(".common."))) {
                handler.onKeepAlive(packet);
                return true;
            }

            if ((lower.contains("ready") && (full.contains(".config.") || full.contains(".configuration."))) ||
                    (lower.contains("finishconfiguration") && full.contains(".s2c."))) {
                handler.onFinishConfiguration(packet);
                return true;
            }

            handler.sendFinishIfNeeded();
            return true;
        } catch (Throwable t) {
            handler.failAndClose(shortError(t));
            return false;
        }
    }

    private void onDynamicRegistriesInternal(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            updateState("CONFIG", "Получен DynamicRegistries");
            sendFinishIfNeeded();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void onFeaturesInternal(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            updateState("CONFIG", "Получен Features");
            sendFinishIfNeeded();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void onSelectKnownPacksInternal(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            Object response = createSelectKnownPacksResponse(packet);
            if (response != null) {
                connection.sendPacket(response);
                updateState("CONFIG", "Отправлен SelectKnownPacksC2SPacket");
            } else {
                updateState("CONFIG", "SelectKnownPacks пропущен");
            }
            sendFinishIfNeeded();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void onCookieRequest(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            Object response = createCookieResponsePacket(packet);
            if (response != null) {
                connection.sendPacket(response);
                updateState("CONFIG", "Отправлен CookieResponse");
            } else {
                updateState("CONFIG", "CookieRequest пропущен");
            }
            sendFinishIfNeeded();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void onResourcePackSend(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            List<Object> responses = createResourcePackStatusPackets(packet);
            if (!responses.isEmpty()) {
                for (Object p : responses) {
                    connection.sendPacket(p);
                }
                updateState("CONFIG", "Отправлен ResourcePackStatus");
            } else {
                updateState("CONFIG", "ResourcePackSend пропущен");
            }
            sendFinishIfNeeded();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void onKeepAlive(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            Object response = createPongPacket(packet);
            if (response != null) {
                connection.sendPacket(response);
            }
        } catch (Throwable ignored) {
        }
    }

    private void onFinishConfiguration(Object packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            sendFinishIfNeeded();
            switchToPlay();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    private void sendFinishIfNeeded() {
        if (finishSent || terminalStateReached) {
            return;
        }

        lastFinishAttemptAt = System.currentTimeMillis();

        if (finishAttempts >= 10) {
            return;
        }
        finishAttempts++;

        Object finish = createSimplePacket(
                "net.minecraft.network.packet.c2s.config.ReadyC2SPacket",
                "net.minecraft.network.packet.c2s.configuration.ReadyC2SPacket",
                "net.minecraft.network.packet.c2s.config.FinishConfigurationC2SPacket",
                "net.minecraft.network.packet.c2s.configuration.FinishConfigurationC2SPacket"
        );

        if (finish != null) {
            connection.sendPacket(finish);
            finishSent = true;
            updateState("CONFIG", "Отправлен finish (ReadyC2S/FinishConfigurationC2S)");
        }
    }

    private void switchToPlay() {
        if (switchedToPlay) {
            return;
        }
        switchedToPlay = true;

        BotClientLoginNetHandler builder = new BotClientLoginNetHandler(connection, mc, parentScreen, statusConsumer, pendingBot, profile, targetHost, targetPort);
        BotClientPlayNetHandler playHandler = builder.createPlayHandler();
        connection.transitionToPlay(playHandler);
        updateState("PLAY", "Успешный вход, play phase");
    }

    private void updateState(String stage, String text) {
        if (pendingBot != null) {
            if ("PLAY".equalsIgnoreCase(stage)) {
                pendingBot.markPlay();
            } else {
                pendingBot.markLogin();
            }
            pendingBot.setStateMessage("[" + stage + "] " + text);
            pendingBot.touch();
        }
    }

    private void failAndClose(String message) {
        if (terminalStateReached) {
            return;
        }
        terminalStateReached = true;

        if (pendingBot != null) {
            pendingBot.markFailed(message);
            pendingBot.touch();
        }

        connection.markClosedReason(message);
        connection.close();
    }

    private void onDisconnectedInternal(Object reason) {
        if (terminalStateReached) {
            return;
        }
        terminalStateReached = true;

        String text = extractDisconnectText(reason);
        if (pendingBot != null) {
            pendingBot.markDisconnected();
            pendingBot.setStateMessage("DISCONNECT [CONFIG] " + text);
            pendingBot.touch();
        }

        connection.markClosedReason(text);
        connection.close();
    }

    private static Object createSelectKnownPacksResponse(Object requestPacket) {
        List<Object> packs = extractKnownPacks(requestPacket);

        for (String cn : new String[]{
                "net.minecraft.network.packet.c2s.config.SelectKnownPacksC2SPacket",
                "net.minecraft.network.packet.c2s.configuration.SelectKnownPacksC2SPacket"
        }) {
            try {
                Class<?> type = Class.forName(cn);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    try {
                        constructor.setAccessible(true);
                        Class<?>[] params = constructor.getParameterTypes();
                        if (params.length == 1 && List.class.isAssignableFrom(params[0])) {
                            return constructor.newInstance(packs);
                        }
                        if (params.length == 1 && params[0].isAssignableFrom(packs.getClass())) {
                            return constructor.newInstance(packs);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static List<Object> extractKnownPacks(Object packet) {
        if (packet == null) {
            return Collections.emptyList();
        }

        Object out = tryCall(packet, "getKnownPacks");
        if (!(out instanceof List<?>)) out = tryCall(packet, "knownPacks");
        if (!(out instanceof List<?>)) out = tryGetField(packet, "knownPacks");
        if (!(out instanceof List<?>)) out = tryGetField(packet, "packs");

        if (out instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        return Collections.emptyList();
    }

    private static Object createCookieResponsePacket(Object requestPacket) {
        Object key = tryCall(requestPacket, "getKey");
        if (key == null) key = tryCall(requestPacket, "key");
        if (key == null) key = tryGetField(requestPacket, "key");

        try {
            Class<?> type = Class.forName("net.minecraft.network.packet.c2s.common.CookieResponseC2SPacket");
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                try {
                    constructor.setAccessible(true);
                    Class<?>[] params = constructor.getParameterTypes();
                    Object[] args = new Object[params.length];
                    boolean ok = true;

                    for (int i = 0; i < params.length; i++) {
                        Class<?> p = params[i];
                        Object value = null;

                        if (key != null && p.isAssignableFrom(key.getClass())) {
                            value = key;
                        } else if (p == byte[].class) {
                            value = null;
                        } else if (p == boolean.class || p == Boolean.class) {
                            value = Boolean.FALSE;
                        } else if (p.isPrimitive()) {
                            value = primitiveDefault(p);
                        }

                        if (value == null && !canBeNull(p)) {
                            ok = false;
                            break;
                        }

                        args[i] = value;
                    }

                    if (ok) {
                        return constructor.newInstance(args);
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static List<Object> createResourcePackStatusPackets(Object requestPacket) {
        if (requestPacket == null) {
            return Collections.emptyList();
        }

        Object id = tryCall(requestPacket, "getId");
        if (id == null) id = tryCall(requestPacket, "id");
        if (id == null) id = tryGetField(requestPacket, "id");
        if (id == null) id = tryGetField(requestPacket, "packId");

        if (id == null) {
            return Collections.emptyList();
        }

        Object accepted = createResourcePackStatusPacket(id, "ACCEPTED");
        Object loaded = createResourcePackStatusPacket(id, "SUCCESSFULLY_LOADED");

        List<Object> out = new ArrayList<>();
        if (accepted != null) out.add(accepted);
        if (loaded != null) out.add(loaded);
        return out;
    }

    private static Object createResourcePackStatusPacket(Object id, String statusName) {
        try {
            Class<?> type = Class.forName("net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket");

            Class<?> statusType = null;
            for (Class<?> c : type.getDeclaredClasses()) {
                if (c.isEnum()) {
                    statusType = c;
                    break;
                }
            }
            if (statusType == null) {
                try {
                    statusType = Class.forName("net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket$Status");
                } catch (Throwable ignored) {
                }
            }
            if (statusType == null) {
                return null;
            }

            Object status = enumByName(statusType, statusName);
            if (status == null) {
                return null;
            }

            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                try {
                    constructor.setAccessible(true);
                    Class<?>[] params = constructor.getParameterTypes();
                    if (params.length != 2) {
                        continue;
                    }
                    if (!params[0].isAssignableFrom(id.getClass())) {
                        continue;
                    }
                    if (!params[1].isAssignableFrom(statusType)) {
                        continue;
                    }
                    return constructor.newInstance(id, status);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Object enumByName(Class<?> enumType, String name) {
        if (enumType == null || name == null) {
            return null;
        }
        try {
            Object[] values = enumType.getEnumConstants();
            if (values == null) {
                return null;
            }
            for (Object v : values) {
                if (v == null) {
                    continue;
                }
                try {
                    Method m = v.getClass().getMethod("name");
                    Object n = m.invoke(v);
                    if (n != null && name.equalsIgnoreCase(String.valueOf(n))) {
                        return v;
                    }
                } catch (Throwable ignored) {
                }
                if (name.equalsIgnoreCase(String.valueOf(v))) {
                    return v;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object createPongPacket(Object requestPacket) {
        Long id = tryCallLong(requestPacket, "getParameter");
        if (id == null) id = tryCallLong(requestPacket, "getId");
        if (id == null) id = tryGetLong(requestPacket, "parameter");
        if (id == null) id = tryGetLong(requestPacket, "id");
        if (id == null) return null;

        for (String className : new String[]{
                "net.minecraft.network.packet.c2s.common.CommonPongC2SPacket",
                "net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket"
        }) {
            try {
                Class<?> type = Class.forName(className);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    try {
                        constructor.setAccessible(true);
                        Class<?>[] params = constructor.getParameterTypes();
                        if (params.length == 1 && (params[0] == long.class || params[0] == Long.class)) {
                            return constructor.newInstance(id);
                        }
                        if (params.length == 1 && (params[0] == int.class || params[0] == Integer.class)) {
                            return constructor.newInstance(id.intValue());
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Object createSimplePacket(String... classNames) {
        for (String className : classNames) {
            try {
                Class<?> type = Class.forName(className);
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    try {
                        if (constructor.getParameterCount() == 0) {
                            constructor.setAccessible(true);
                            return constructor.newInstance();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object tryCall(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method m = findMethod(obj.getClass(), methodName);
            if (m == null || m.getParameterCount() != 0) {
                return null;
            }
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Long tryCallLong(Object obj, String methodName) {
        if (obj == null) {
            return null;
        }
        try {
            Method m = findMethod(obj.getClass(), methodName);
            if (m == null || m.getParameterCount() != 0) {
                return null;
            }
            m.setAccessible(true);
            Object v = m.invoke(obj);
            if (v instanceof Long l) return l;
            if (v instanceof Integer i) return (long) i;
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Long tryGetLong(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            Object v = f.get(obj);
            if (v instanceof Long l) return l;
            if (v instanceof Integer i) return (long) i;
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object tryGetField(Object obj, String fieldName) {
        if (obj == null) {
            return null;
        }
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) {
                return null;
            }
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cur = type;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cur = type;
        while (cur != null) {
            Method[] declared = cur.getDeclaredMethods();
            for (Method method : declared) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            cur = cur.getSuperclass();
        }

        Method[] methods = type.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                return method;
            }
        }

        return null;
    }

    private static boolean canBeNull(Class<?> type) {
        return type != null && !type.isPrimitive();
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == char.class) return '\0';
        return null;
    }

    private static String extractDisconnectText(Object reason) {
        if (reason == null) {
            return "Соединение закрыто";
        }

        if (reason instanceof Text text) {
            String s = text.getString();
            return s == null || s.isBlank() ? "Соединение закрыто" : s;
        }

        Object text = tryCall(reason, "getReason");
        if (!(text instanceof String)) text = tryCall(reason, "getString");
        if (!(text instanceof String)) text = tryCall(reason, "asString");
        if (!(text instanceof String)) text = tryGetField(reason, "reason");
        if (!(text instanceof String)) text = tryGetField(reason, "message");

        String out = text == null ? String.valueOf(reason) : String.valueOf(text);
        return out == null || out.isBlank() ? "Соединение закрыто" : out;
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