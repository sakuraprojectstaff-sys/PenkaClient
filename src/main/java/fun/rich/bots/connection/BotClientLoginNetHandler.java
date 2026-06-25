package fun.rich.bots.connection;

import com.mojang.authlib.GameProfile;
import fun.rich.bots.Bot;
import fun.rich.bots.BotManager;
import fun.rich.bots.player.BotController;
import fun.rich.bots.player.BotPlayer;
import fun.rich.bots.world.BotWorld;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.network.listener.ClientLoginPacketListener;
import net.minecraft.network.packet.c2s.login.LoginQueryResponseC2SPacket;
import net.minecraft.network.packet.s2c.common.CookieRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginCompressionS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginDisconnectS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginQueryRequestS2CPacket;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class BotClientLoginNetHandler implements ClientLoginPacketListener {
    private final BotNetwork connection;
    private final MinecraftClient mc;
    private final Screen parentScreen;
    private final Consumer<?> statusConsumer;
    private final Bot pendingBot;
    private final GameProfile requestedProfile;
    private final String targetHost;
    private final int targetPort;

    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTING);

    private volatile GameProfile profile;
    private volatile String botName = "";
    private volatile boolean terminalStateReached;
    private volatile boolean configurationInstalled;

    public BotClientLoginNetHandler(BotNetwork connection, MinecraftClient mc, Screen parentScreen, Consumer<?> statusConsumer, Bot pendingBot, GameProfile requestedProfile, String targetHost, int targetPort) {
        this.connection = connection;
        this.mc = mc;
        this.parentScreen = parentScreen;
        this.statusConsumer = statusConsumer;
        this.pendingBot = pendingBot;
        this.requestedProfile = requestedProfile;
        this.targetHost = targetHost == null ? "" : targetHost;
        this.targetPort = targetPort;
        this.profile = requestedProfile;
        if (requestedProfile != null && requestedProfile.getName() != null) {
            this.botName = requestedProfile.getName();
        }
    }

    public BotClientLoginNetHandler(BotNetwork connection, MinecraftClient mc, Consumer<?> statusConsumer, Bot pendingBot, GameProfile requestedProfile, String targetHost, int targetPort) {
        this(connection, mc, null, statusConsumer, pendingBot, requestedProfile, targetHost, targetPort);
    }

    public BotClientLoginNetHandler(BotNetwork connection, MinecraftClient mc, Bot pendingBot, GameProfile requestedProfile, String targetHost, int targetPort) {
        this(connection, mc, null, null, pendingBot, requestedProfile, targetHost, targetPort);
    }

    public BotClientLoginNetHandler(BotNetwork connection, MinecraftClient mc, Screen parentScreen, Consumer<?> statusConsumer) {
        this(connection, mc, parentScreen, statusConsumer, null, null, "", 25565);
    }

    public BotClientLoginNetHandler(BotNetwork connection, MinecraftClient mc) {
        this(connection, mc, null, null, null, null, "", 25565);
    }

    public BotClientLoginNetHandler(BotNetwork connection) {
        this(connection, MinecraftClient.getInstance(), null, null, null, null, "", 25565);
    }

    public BotNetwork getConnection() {
        return connection;
    }

    public MinecraftClient getMc() {
        return mc;
    }

    @Override
    public void onHello(LoginHelloS2CPacket packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            switchTo(State.AUTHORIZING);
            updatePendingLoginState("HELLO", "Получен login hello");

            PublicKey publicKey = extractPublicKey(packet);
            if (publicKey == null) {
                updatePendingLoginState("HELLO", "Login hello без public key");
                return;
            }

            SecretKey secretKey = KeyGenerator.getInstance("AES").generateKey();
            connection.setupEncryption(secretKey);

            Object keyPacket = createKeyPacket(packet, secretKey, publicKey, requestedProfile == null ? null : requestedProfile.getId());
            if (keyPacket != null) {
                connection.sendPacket(keyPacket);
                switchTo(State.ENCRYPTING);
                updatePendingLoginState("ENCRYPT", "Отправлен login key");
            } else {
                updatePendingLoginState("HELLO", "Не удалось собрать login key packet");
            }
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    @Override
    public void onSuccess(LoginSuccessS2CPacket packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            GameProfile nextProfile = packet == null ? this.profile : packet.profile();
            this.profile = nextProfile;
            if (nextProfile != null && nextProfile.getName() != null) {
                this.botName = nextProfile.getName();
            }

            switchTo(State.JOINING);
            installConfigurationHandler();
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    @Override
    public void onDisconnected(DisconnectionInfo info) {
        onDisconnectedInternal(info == null ? null : info.reason());
    }

    @Override
    public void onDisconnect(LoginDisconnectS2CPacket packet) {
        onDisconnectedInternal(packet == null ? null : packet.getReason());
    }

    @Override
    public void onCompression(LoginCompressionS2CPacket packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            int threshold = extractCompressionThreshold(packet);
            connection.setCompressionThreshold(threshold, false);
            updatePendingLoginState("COMPRESSION", "Compression " + threshold);
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    @Override
    public void onQueryRequest(LoginQueryRequestS2CPacket packet) {
        if (terminalStateReached) {
            return;
        }

        try {
            Object response = createLoginQueryResponse(packet);
            if (response != null) {
                connection.sendPacket(response);
                updatePendingLoginState("QUERY", "Отправлен query response");
            } else {
                updatePendingLoginState("QUERY", "Query проигнорирован");
            }
        } catch (Throwable t) {
            failAndClose(shortError(t));
        }
    }

    @Override
    public void onCookieRequest(CookieRequestS2CPacket packet) {
        if (terminalStateReached) {
            return;
        }
        updatePendingLoginState("COOKIE", "CookieRequest");
    }

    @Override
    public boolean isConnectionOpen() {
        return !terminalStateReached && connection != null && connection.isOpen();
    }

    @Override
    public void addCustomCrashReportInfo(CrashReport report, CrashReportSection section) {
        section.add("Server type", () -> "BOT");
        section.add("Login phase", () -> state.get().toString());
        section.add("Bot Target", () -> targetHost + ":" + targetPort);
    }

    public void tick() {
        if (terminalStateReached) {
            return;
        }

        if (connection != null && !connection.isOpen()) {
            String closeReason = connection.getLastCloseReason();
            onDisconnectedInternal(closeReason == null || closeReason.isBlank() ? Text.literal("Соединение закрыто") : Text.literal(closeReason));
        }
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

    public static boolean dispatchPacket(BotClientLoginNetHandler handler, Object packet) {
        if (handler == null || packet == null || handler.terminalStateReached) {
            return false;
        }

        try {
            if (packet instanceof LoginDisconnectS2CPacket disconnectPacket) {
                handler.onDisconnect(disconnectPacket);
                return true;
            }

            if (packet instanceof LoginCompressionS2CPacket compressionPacket) {
                handler.onCompression(compressionPacket);
                return true;
            }

            if (packet instanceof LoginSuccessS2CPacket successPacket) {
                handler.onSuccess(successPacket);
                return true;
            }

            if (packet instanceof LoginQueryRequestS2CPacket queryPacket) {
                handler.onQueryRequest(queryPacket);
                return true;
            }

            if (packet instanceof LoginHelloS2CPacket helloPacket) {
                handler.onHello(helloPacket);
                return true;
            }

            if (packet instanceof CookieRequestS2CPacket cookiePacket) {
                handler.onCookieRequest(cookiePacket);
                return true;
            }

            if (handler.pendingBot != null) {
                handler.pendingBot.markLogin();
                handler.pendingBot.setStateMessage("Login packet: " + packet.getClass().getSimpleName());
                handler.pendingBot.touch();
            }
        } catch (Throwable t) {
            handler.failAndClose(shortError(t));
            return false;
        }

        return false;
    }

    public void onDisconnected(Object reason) {
        onDisconnectedInternal(reason);
    }

    public void onDisconnect(Object reason) {
        onDisconnectedInternal(reason);
    }

    public void onLoginHello(Object packet) {
        if (packet instanceof LoginHelloS2CPacket p) {
            onHello(p);
        }
    }

    public void onKey(Object packet) {
        if (packet instanceof LoginHelloS2CPacket p) {
            onHello(p);
        }
    }

    public void onLoginKey(Object packet) {
        if (packet instanceof LoginHelloS2CPacket p) {
            onHello(p);
        }
    }

    public void onLoginCompression(Object packet) {
        if (packet instanceof LoginCompressionS2CPacket p) {
            onCompression(p);
        }
    }

    public void onLoginQueryRequest(Object packet) {
        if (packet instanceof LoginQueryRequestS2CPacket p) {
            onQueryRequest(p);
        }
    }

    public void onLoginSuccess(Object packet) {
        if (packet instanceof LoginSuccessS2CPacket p) {
            onSuccess(p);
        }
    }

    public void installPlayHandler() {
        if (terminalStateReached) {
            return;
        }
        connection.transitionToPlay(createPlayHandler());
    }

    public void installConfigurationHandler() {
        if (terminalStateReached || configurationInstalled) {
            return;
        }

        configurationInstalled = true;
        GameProfile currentProfile = this.profile;
        BotClientConfigurationNetHandler configurationHandler = new BotClientConfigurationNetHandler(
                connection,
                mc,
                parentScreen,
                statusConsumer,
                pendingBot,
                currentProfile,
                targetHost,
                targetPort
        );

        connection.transitionToConfiguration(configurationHandler);

        if (pendingBot != null) {
            pendingBot.markLogin();
            pendingBot.setStateMessage("[CONFIG] Вход в configuration phase");
            pendingBot.touch();
        }
    }

    public BotClientPlayNetHandler createPlayHandler() {
        BotWorld botWorld = new BotWorld();
        GameProfile currentProfile = this.profile;
        BotPlayer botPlayer = createBotPlayer(currentProfile == null ? new GameProfile(UUID.randomUUID(), botName.isEmpty() ? "Bot" : botName) : currentProfile);
        BotController botController = new BotController();
        BotClientPlayNetHandler playHandler = new BotClientPlayNetHandler(connection, mc, botWorld, botPlayer, botController);

        Bot target = pendingBot;
        if (target == null) {
            target = new Bot(connection, botWorld, botPlayer, botController, playHandler, targetHost, targetPort);
        } else {
            target.attachNetwork(connection);
            target.attachWorld(botWorld);
            target.attachPlayer(botPlayer);
            target.attachController(botController);
            target.attachConnection(playHandler);
            target.setTargetHost(targetHost);
            target.setTargetPort(targetPort);
        }

        playHandler.setBotOwner(target);

        target.markPlay();
        target.setStateMessage("Успешный вход, play phase");

        if (pendingBot == null) {
            BotManager.add(target);
        } else {
            BotManager.mergeIntoPending(target);
        }

        return playHandler;
    }

    private void onDisconnectedInternal(Object reason) {
        if (terminalStateReached) {
            return;
        }
        terminalStateReached = true;

        String text = readReason(reason);
        String debug = buildDisconnectDebug(reason);
        String finalText = text == null || text.isBlank() ? debug : text;

        if (pendingBot != null) {
            pendingBot.markDisconnected();
            pendingBot.setStateMessage(finalText);
            pendingBot.touch();
        }

        notifyStatus(Text.literal(finalText));

        if (connection != null) {
            connection.markClosedReason(finalText);
            connection.close();
        }
    }

    private void switchTo(State nextState) {
        State actual = state.updateAndGet(current -> nextState);
        notifyStatus(actual.name);
    }

    private void updatePendingLoginState(String stage, String fallback) {
        if (pendingBot == null || terminalStateReached) {
            return;
        }
        pendingBot.markLogin();
        pendingBot.setStateMessage("[" + stage + "] " + fallback);
        pendingBot.touch();
    }

    private void failPending(String message) {
        if (pendingBot != null) {
            pendingBot.markFailed(message);
            pendingBot.touch();
        }
    }

    private void failAndClose(String message) {
        if (terminalStateReached) {
            return;
        }
        terminalStateReached = true;
        failPending(message);
        if (connection != null) {
            connection.markClosedReason(message);
            connection.close();
        }
    }

    private void notifyStatus(Text status) {
        if (statusConsumer == null || status == null) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Consumer<Text> c = (Consumer<Text>) statusConsumer;
            c.accept(status);
        } catch (Throwable ignored) {
        }
    }

    private static String buildDisconnectDebug(Object reason) {
        String type = reason == null ? "null" : reason.getClass().getSimpleName();
        String text = readReason(reason);
        if (text == null || text.isBlank()) {
            return "DISCONNECT [" + type + "]";
        }
        return "DISCONNECT [" + type + "] " + text;
    }

    private static String readReason(Object reason) {
        if (reason == null) {
            return "Соединение закрыто";
        }
        if (reason instanceof Text text) {
            return text.getString();
        }
        if (reason instanceof DisconnectionInfo info) {
            return info.reason().getString();
        }
        String fallback = String.valueOf(reason);
        return fallback == null || fallback.isBlank() ? "Соединение закрыто" : fallback;
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

    private static BotPlayer createBotPlayer(GameProfile profile) {
        try {
            return BotPlayer.class.getConstructor(GameProfile.class).newInstance(profile);
        } catch (Throwable ignored) {
        }

        try {
            return BotPlayer.class.getConstructor(String.class).newInstance(profile == null ? "Bot" : profile.getName());
        } catch (Throwable ignored) {
        }

        throw new IllegalStateException("Не найден подходящий конструктор BotPlayer");
    }

    private static PublicKey extractPublicKey(LoginHelloS2CPacket packet) {
        if (packet == null) {
            return null;
        }

        try {
            Method method = packet.getClass().getMethod("publicKey");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof PublicKey key) {
                return key;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method method = packet.getClass().getMethod("getPublicKey");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof PublicKey key) {
                return key;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                if (PublicKey.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Object out = field.get(packet);
                    if (out instanceof PublicKey key) {
                        return key;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static int extractCompressionThreshold(LoginCompressionS2CPacket packet) {
        if (packet == null) {
            return 256;
        }

        try {
            Method method = packet.getClass().getMethod("compressionThreshold");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method method = packet.getClass().getMethod("getCompressionThreshold");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type == int.class || type == Integer.class) {
                    field.setAccessible(true);
                    Object out = field.get(packet);
                    if (out instanceof Integer i) {
                        return i;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return 256;
    }

    private static Object createKeyPacket(LoginHelloS2CPacket packet, SecretKey secretKey, PublicKey publicKey, UUID profileId) {
        Class<?> packetClass;
        try {
            packetClass = Class.forName("net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket");
        } catch (Throwable t) {
            return null;
        }

        for (Constructor<?> constructor : packetClass.getDeclaredConstructors()) {
            try {
                constructor.setAccessible(true);
                Class<?>[] params = constructor.getParameterTypes();
                Object[] args = new Object[params.length];
                boolean ok = true;

                for (int i = 0; i < params.length; i++) {
                    Class<?> p = params[i];
                    if (SecretKey.class.isAssignableFrom(p)) {
                        args[i] = secretKey;
                    } else if (PublicKey.class.isAssignableFrom(p)) {
                        args[i] = publicKey;
                    } else if (UUID.class.isAssignableFrom(p)) {
                        args[i] = profileId;
                    } else if (LoginHelloS2CPacket.class.isAssignableFrom(p)) {
                        args[i] = packet;
                    } else if (p == byte[].class) {
                        args[i] = extractNonce(packet);
                    } else {
                        ok = false;
                        break;
                    }
                }

                if (ok) {
                    return constructor.newInstance(args);
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static byte[] extractNonce(LoginHelloS2CPacket packet) {
        if (packet == null) {
            return new byte[0];
        }

        try {
            Method method = packet.getClass().getMethod("nonce");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof byte[] bytes) {
                return bytes;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method method = packet.getClass().getMethod("getNonce");
            method.setAccessible(true);
            Object out = method.invoke(packet);
            if (out instanceof byte[] bytes) {
                return bytes;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                if (field.getType() == byte[].class) {
                    field.setAccessible(true);
                    Object out = field.get(packet);
                    if (out instanceof byte[] bytes) {
                        return bytes;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return new byte[0];
    }

    private static Object createLoginQueryResponse(LoginQueryRequestS2CPacket packet) {
        if (packet == null) {
            return null;
        }

        Integer queryId = extractQueryId(packet);
        if (queryId == null) {
            return null;
        }

        try {
            return new LoginQueryResponseC2SPacket(queryId, null);
        } catch (Throwable ignored) {
        }

        for (Constructor<?> constructor : LoginQueryResponseC2SPacket.class.getDeclaredConstructors()) {
            try {
                constructor.setAccessible(true);
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length != 2) {
                    continue;
                }

                Object[] args = new Object[2];
                boolean ok = true;

                for (int i = 0; i < 2; i++) {
                    Class<?> p = params[i];
                    if (p == int.class || p == Integer.class) {
                        args[i] = queryId;
                    } else {
                        args[i] = null;
                    }
                }

                if (ok) {
                    return constructor.newInstance(args);
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Integer extractQueryId(LoginQueryRequestS2CPacket packet) {
        try {
            Method queryId = packet.getClass().getMethod("queryId");
            queryId.setAccessible(true);
            Object id = queryId.invoke(packet);
            if (id instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method queryId = packet.getClass().getMethod("getQueryId");
            queryId.setAccessible(true);
            Object id = queryId.invoke(packet);
            if (id instanceof Integer i) {
                return i;
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Field field : packet.getClass().getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type == int.class || type == Integer.class) {
                    field.setAccessible(true);
                    Object out = field.get(packet);
                    if (out instanceof Integer i) {
                        return i;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private enum State {
        CONNECTING(Text.translatable("connect.connecting"), Set.of()),
        AUTHORIZING(Text.translatable("connect.authorizing"), Set.of(CONNECTING)),
        ENCRYPTING(Text.translatable("connect.encrypting"), Set.of(AUTHORIZING)),
        JOINING(Text.translatable("connect.joining"), Set.of(ENCRYPTING, CONNECTING, AUTHORIZING));

        final Text name;
        final Set<State> prevStates;

        State(Text name, Set<State> prevStates) {
            this.name = name;
            this.prevStates = prevStates;
        }
    }
}