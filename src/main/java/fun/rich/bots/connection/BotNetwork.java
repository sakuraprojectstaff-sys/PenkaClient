package fun.rich.bots.connection;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientPacketListener;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class BotNetwork {
    private static final Unsafe UNSAFE = findUnsafe();

    private final ClientConnection connection;

    private volatile Object delegateListener;
    private volatile String currentPhase = "LOGIN";
    private volatile String lastDebugState = "CREATED";
    private volatile String lastCloseReason = "";
    private volatile boolean closeRequested;
    private volatile boolean disconnectionHandled;
    private volatile boolean notifyingDisconnect;

    private volatile String connectedHost = "";
    private volatile int connectedPort = 0;

    private BotNetwork(ClientConnection connection) {
        this.connection = connection;
    }

    public static BotNetwork wrapExisting(Object connection) {
        if (!(connection instanceof ClientConnection clientConnection)) {
            throw new IllegalArgumentException("connection is not ClientConnection");
        }
        return new BotNetwork(clientConnection);
    }

    public static BotNetwork createVanillaClientConnection() {
        return new BotNetwork(new ClientConnection(NetworkSide.CLIENTBOUND));
    }

    public Object unwrap() {
        return connection;
    }

    public ClientConnection unwrapClientConnection() {
        return connection;
    }

    public String getLastDebugState() {
        return lastDebugState;
    }

    public String getLastCloseReason() {
        return lastCloseReason == null ? "" : lastCloseReason;
    }

    public void markClosedReason(String reason) {
        if (reason != null && !reason.isBlank()) {
            this.lastCloseReason = reason;
        }
    }

    public boolean isOpen() {
        if (closeRequested) {
            return false;
        }

        try {
            return connection.isOpen();
        } catch (Throwable ignored) {
        }

        try {
            Channel channel = getChannel();
            return channel != null && channel.isOpen() && channel.isActive();
        } catch (Throwable ignored) {
        }

        return false;
    }

    public void tick() {
        if (closeRequested) {
            return;
        }

        lastDebugState = "TICK";

        try {
            connection.tick();
        } catch (Throwable ignored) {
        }

        tryInvokeNoArgs(connection, "flush");

        if (!isOpen()) {
            handleDisconnection();
        }
    }

    public void handleDisconnection() {
        if (disconnectionHandled) {
            return;
        }
        disconnectionHandled = true;
        lastDebugState = "HANDLE_DISCONNECTION";

        try {
            connection.handleDisconnection();
        } catch (Throwable ignored) {
        }

        notifyDelegateDisconnected(lastCloseReason == null || lastCloseReason.isBlank() ? "Соединение закрыто" : lastCloseReason);
    }

    public void setupEncryption(Object secretKey) {
        if (secretKey == null || closeRequested) {
            return;
        }

        lastDebugState = "SETUP_ENCRYPTION";

        if (tryInvokeOneArg(connection, new String[]{"setupEncryption", "setupEncryptionKey"}, secretKey)) {
            return;
        }

        for (Method method : connection.getClass().getMethods()) {
            if (!method.getName().equals("setupEncryption") && !method.getName().equals("setupEncryptionKey")) {
                continue;
            }
            if (method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(connection, secretKey);
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    public void setCompressionThreshold(int threshold, boolean rejectsBadPackets) {
        if (closeRequested) {
            return;
        }

        lastDebugState = "SET_COMPRESSION_" + threshold;

        try {
            connection.setCompressionThreshold(threshold, rejectsBadPackets);
            return;
        } catch (Throwable ignored) {
        }

        tryInvoke(connection, "setCompressionThreshold", new Class[]{int.class}, threshold);
    }

    public void transitionToConfiguration(Object listener) {
        installListener(listener, "CONFIGURATION");
    }

    public void transitionToPlay(Object listener) {
        installListener(listener, "PLAY");
    }

    public void sendPacket(Object packet) {
        if (packet == null || closeRequested) {
            return;
        }

        lastDebugState = "SEND:" + packet.getClass().getSimpleName();

        try {
            connection.send((net.minecraft.network.packet.Packet<?>) packet);
            return;
        } catch (Throwable ignored) {
        }

        if (tryInvokeOneArg(connection, new String[]{"send", "sendPacket"}, packet)) {
            return;
        }

        throw new IllegalStateException("Не удалось отправить packet: " + packet.getClass().getName());
    }

    public void close() {
        if (closeRequested && disconnectionHandled) {
            return;
        }

        closeRequested = true;
        lastDebugState = "CLOSE";

        boolean invoked = false;

        try {
            connection.disconnect(net.minecraft.text.Text.literal(lastCloseReason == null || lastCloseReason.isBlank() ? "Соединение закрыто" : lastCloseReason));
            invoked = true;
        } catch (Throwable ignored) {
        }

        if (!invoked) {
            try {
                Channel channel = getChannel();
                if (channel != null) {
                    channel.close();
                    invoked = true;
                }
            } catch (Throwable ignored) {
            }
        }

        if (!invoked && (lastCloseReason == null || lastCloseReason.isBlank())) {
            lastCloseReason = "Соединение закрыто";
        }

        handleDisconnection();
    }

    public ChannelFuture connectVanilla(java.net.InetSocketAddress address, boolean useNativeTransport) {
        if (address == null) {
            throw new IllegalArgumentException("address == null");
        }

        lastDebugState = "VANILLA_CONNECT_SOCKET";
        return ClientConnection.connect(address, useNativeTransport, connection);
    }

    public void beginVanillaLogin(String host, int port, Object c2sState, Object s2cState, Object listener, boolean transferred) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }

        boolean invoked = false;

        for (Method method : ClientConnection.class.getMethods()) {
            if (!method.getName().equals("connect")) {
                continue;
            }

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 6) {
                continue;
            }

            try {
                if (params[0] != String.class || params[1] != int.class || params[5] != boolean.class) {
                    continue;
                }
                if (!params[2].isInstance(c2sState) || !params[3].isInstance(s2cState) || !params[4].isInstance(listener)) {
                    continue;
                }

                method.setAccessible(true);
                method.invoke(connection, host, port, c2sState, s2cState, listener, transferred);
                invoked = true;
                break;
            } catch (Throwable ignored) {
            }
        }

        if (!invoked) {
            for (Method method : ClientConnection.class.getDeclaredMethods()) {
                if (!method.getName().equals("connect")) {
                    continue;
                }

                Class<?>[] params = method.getParameterTypes();
                if (params.length != 6) {
                    continue;
                }

                try {
                    if (params[0] != String.class || params[1] != int.class || params[5] != boolean.class) {
                        continue;
                    }
                    if (!params[2].isInstance(c2sState) || !params[3].isInstance(s2cState) || !params[4].isInstance(listener)) {
                        continue;
                    }

                    method.setAccessible(true);
                    method.invoke(connection, host, port, c2sState, s2cState, listener, transferred);
                    invoked = true;
                    break;
                } catch (Throwable ignored) {
                }
            }
        }

        if (!invoked) {
            throw new IllegalStateException("Не удалось вызвать vanilla ClientConnection.connect(host, port, c2s, s2c, listener, transferred)");
        }

        this.connectedHost = host == null ? "" : host;
        this.connectedPort = port;

        this.delegateListener = listener;
        this.currentPhase = "LOGIN";
        this.lastDebugState = "VANILLA_CONNECT_LOGIN";
        this.closeRequested = false;
        this.disconnectionHandled = false;
    }

    private void installListener(Object listener, String phaseName) {
        if (listener == null) {
            throw new IllegalArgumentException("listener == null");
        }
        if (closeRequested) {
            return;
        }

        String normalized = normalizePhase(phaseName);
        PhaseStates states = resolvePhaseStates(normalized);

        boolean ok = false;

        if (listener instanceof ClientPacketListener clientListener && states.outbound != null && states.inbound != null) {
            ok = trySwitchViaConnect(normalized, clientListener, states.outbound, states.inbound);
        }

        if (!ok) {
            boolean setListenerOk = false;

            setListenerOk |= tryInvokeOneArg(connection, new String[]{"setPacketListener", "setListener"}, listener);
            setListenerOk |= forceSetField(connection, "packetListener", listener);
            setListenerOk |= forceSetField(connection, "listener", listener);

            if (states.any != null) {
                setListenerOk |= applyChannelProtocolAttribute(states.any);
                setListenerOk |= forceSetField(connection, "state", states.any);
                setListenerOk |= forceSetField(connection, "networkState", states.any);
                setListenerOk |= tryInvokeOneArg(connection, new String[]{"setState", "setNetworkState"}, states.any);
            }

            ok = setListenerOk;
        }

        this.delegateListener = listener;
        this.currentPhase = normalized;
        this.lastDebugState = "LISTENER_SET:" + normalized + ":" + listener.getClass().getSimpleName();

        if (!ok) {
            throw new IllegalStateException("Не удалось установить listener/state в ClientConnection");
        }
    }

    private boolean trySwitchViaConnect(String phase, ClientPacketListener listener, Object outbound, Object inbound) {
        String host = connectedHost;
        int port = connectedPort;

        if ((host == null || host.isBlank()) || port <= 0) {
            try {
                Channel ch = getChannel();
                if (ch != null && ch.remoteAddress() instanceof java.net.InetSocketAddress isa) {
                    host = isa.getHostString();
                    port = isa.getPort();
                }
            } catch (Throwable ignored) {
            }
        }

        if (host == null || host.isBlank() || port <= 0) {
            return false;
        }

        boolean invoked = false;

        for (Method method : ClientConnection.class.getMethods()) {
            if (!method.getName().equals("connect")) {
                continue;
            }
            Class<?>[] p = method.getParameterTypes();
            if (p.length != 6) {
                continue;
            }

            try {
                if (p[0] != String.class || p[1] != int.class || p[5] != boolean.class) {
                    continue;
                }
                if (!p[2].isInstance(outbound) || !p[3].isInstance(inbound) || !p[4].isInstance(listener)) {
                    continue;
                }

                method.setAccessible(true);
                method.invoke(connection, host, port, outbound, inbound, listener, false);
                invoked = true;
                break;
            } catch (Throwable ignored) {
            }
        }

        if (!invoked) {
            for (Method method : ClientConnection.class.getDeclaredMethods()) {
                if (!method.getName().equals("connect")) {
                    continue;
                }
                Class<?>[] p = method.getParameterTypes();
                if (p.length != 6) {
                    continue;
                }

                try {
                    if (p[0] != String.class || p[1] != int.class || p[5] != boolean.class) {
                        continue;
                    }
                    if (!p[2].isInstance(outbound) || !p[3].isInstance(inbound) || !p[4].isInstance(listener)) {
                        continue;
                    }

                    method.setAccessible(true);
                    method.invoke(connection, host, port, outbound, inbound, listener, false);
                    invoked = true;
                    break;
                } catch (Throwable ignored) {
                }
            }
        }

        if (invoked) {
            this.connectedHost = host;
            this.connectedPort = port;
            this.lastDebugState = "CONNECT_SWITCH:" + phase;
            return true;
        }

        return false;
    }

    private boolean applyChannelProtocolAttribute(Object stateAny) {
        Channel ch = getChannel();
        if (ch == null || stateAny == null) {
            return false;
        }

        boolean ok = false;

        for (Field f : ClientConnection.class.getDeclaredFields()) {
            try {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (!AttributeKey.class.isAssignableFrom(f.getType())) {
                    continue;
                }
                String name = f.getName().toLowerCase();
                if (!name.contains("protocol") && !name.contains("state") && !name.contains("network")) {
                    continue;
                }
                f.setAccessible(true);
                Object keyObj = f.get(null);
                if (!(keyObj instanceof AttributeKey<?> key)) {
                    continue;
                }

                try {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Attribute raw = (Attribute) ch.attr((AttributeKey) key);
                    raw.set(stateAny);
                    ok = true;
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
        }

        return ok;
    }

    private void notifyDelegateDisconnected(Object reason) {
        Object delegate = delegateListener;
        if (delegate == null || notifyingDisconnect) {
            return;
        }

        notifyingDisconnect = true;
        try {
            if (!tryInvokeOneArg(delegate, new String[]{"onDisconnected", "onDisconnect"}, reason)) {
                tryInvokeOneArg(delegate, new String[]{"onDisconnected", "onDisconnect"}, null);
            }
        } finally {
            notifyingDisconnect = false;
        }
    }

    private Channel getChannel() {
        try {
            Method m = connection.getClass().getMethod("getChannel");
            m.setAccessible(true);
            Object out = m.invoke(connection);
            if (out instanceof Channel ch) {
                return ch;
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = findField(connection.getClass(), "channel");
            if (f != null) {
                f.setAccessible(true);
                Object out = f.get(connection);
                if (out instanceof Channel ch) {
                    return ch;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean tryInvokeNoArgs(Object target, String name) {
        try {
            Method m = findMethod(target.getClass(), new String[]{name});
            if (m == null) {
                return false;
            }
            m.invoke(target);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryInvokeOneArg(Object target, String[] names, Object arg) {
        try {
            for (Method method : target.getClass().getMethods()) {
                boolean okName = false;
                for (String name : names) {
                    if (method.getName().equals(name)) {
                        okName = true;
                        break;
                    }
                }
                if (!okName || method.getParameterCount() != 1) {
                    continue;
                }

                Class<?> p0 = method.getParameterTypes()[0];
                if (arg != null && !p0.isAssignableFrom(arg.getClass()) && !p0.isInstance(arg)) {
                    continue;
                }

                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean tryInvoke(Object target, String name, Class<?>[] exactTypes, Object... args) {
        try {
            Method m = target.getClass().getMethod(name, exactTypes);
            m.setAccessible(true);
            m.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String[] names, Class<?>... exactTypes) {
        try {
            for (String name : names) {
                try {
                    Method m = type.getMethod(name, exactTypes);
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {
                }
            }

            for (Method method : type.getMethods()) {
                boolean nameMatch = false;
                for (String name : names) {
                    if (method.getName().equals(name)) {
                        nameMatch = true;
                        break;
                    }
                }
                if (!nameMatch) {
                    continue;
                }
                if (exactTypes.length != 0 && method.getParameterCount() != exactTypes.length) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static boolean forceSetField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) {
            return false;
        }

        Field field = findField(target.getClass(), fieldName);
        if (field == null) {
            return false;
        }

        try {
            field.setAccessible(true);
            field.set(target, value);
            return true;
        } catch (Throwable ignored) {
        }

        if (UNSAFE == null) {
            return false;
        }

        try {
            long offset = UNSAFE.objectFieldOffset(field);
            UNSAFE.putObject(target, offset, value);
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cur = type;
        while (cur != null) {
            try {
                Field f = cur.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Unsafe findUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            Object out = f.get(null);
            if (out instanceof Unsafe u) {
                return u;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String normalizePhase(String phase) {
        if (phase == null || phase.isBlank()) {
            return "LOGIN";
        }
        String p = phase.trim().toUpperCase();
        if ("CONFIG".equals(p)) {
            return "CONFIGURATION";
        }
        if ("HANDSHAKE".equals(p)) {
            return "HANDSHAKING";
        }
        return p;
    }

    private static final class PhaseStates {
        final Object outbound;
        final Object inbound;
        final Object any;

        PhaseStates(Object outbound, Object inbound, Object any) {
            this.outbound = outbound;
            this.inbound = inbound;
            this.any = any;
        }
    }

    private static PhaseStates resolvePhaseStates(String phase) {
        String p = phase == null ? "" : phase.trim().toUpperCase();

        Object outbound = findStatesFieldValue(p, true);
        Object inbound = findStatesFieldValue(p, false);

        Object any = resolveNetworkStateLegacy(p);

        return new PhaseStates(outbound, inbound, any);
    }

    private static Object findStatesFieldValue(String phaseUpper, boolean wantC2S) {
        String className;
        if ("LOGIN".equals(phaseUpper)) className = "net.minecraft.network.state.LoginStates";
        else if ("CONFIGURATION".equals(phaseUpper)) className = "net.minecraft.network.state.ConfigurationStates";
        else if ("PLAY".equals(phaseUpper)) className = "net.minecraft.network.state.PlayStates";
        else if ("HANDSHAKING".equals(phaseUpper) || "HANDSHAKE".equals(phaseUpper)) className = "net.minecraft.network.state.HandshakeStates";
        else className = null;

        if (className == null) {
            return null;
        }

        try {
            Class<?> c = Class.forName(className);

            Object direct = getStaticField(c, wantC2S ? "C2S" : "S2C");
            if (direct != null) {
                return direct;
            }

            String want = wantC2S ? "c2s" : "s2c";
            for (Field f : c.getDeclaredFields()) {
                try {
                    if (!Modifier.isStatic(f.getModifiers())) continue;
                    String n = f.getName() == null ? "" : f.getName().toLowerCase();
                    if (!n.contains(want)) continue;
                    f.setAccessible(true);
                    Object v = f.get(null);
                    if (v != null) return v;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Object getStaticField(Class<?> type, String name) {
        if (type == null || name == null) return null;
        try {
            Field f = type.getDeclaredField(name);
            if (!Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
        }
        try {
            Field f = type.getField(name);
            if (!Modifier.isStatic(f.getModifiers())) return null;
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object resolveNetworkStateLegacy(String phase) {
        String p = phase == null ? "" : phase.trim().toUpperCase();

        for (String cn : new String[]{
                "net.minecraft.network.state.NetworkState",
                "net.minecraft.network.NetworkState"
        }) {
            try {
                Class<?> c = Class.forName(cn);

                if (c.isEnum()) {
                    for (Object e : c.getEnumConstants()) {
                        if (e == null) continue;
                        String n = String.valueOf(e).toUpperCase();
                        if (p.equals("HANDSHAKING") && n.contains("HANDSHAKE")) return e;
                        if (p.equals("LOGIN") && n.contains("LOGIN")) return e;
                        if (p.equals("CONFIGURATION") && (n.contains("CONFIG") || n.contains("CONFIGURATION"))) return e;
                        if (p.equals("PLAY") && n.contains("PLAY")) return e;
                    }
                }

                for (Field f : c.getDeclaredFields()) {
                    try {
                        f.setAccessible(true);
                        Object v = f.get(null);
                        if (v == null) continue;
                        String n = f.getName().toUpperCase();
                        if (p.equals("HANDSHAKING") && n.contains("HANDSHAKE")) return v;
                        if (p.equals("LOGIN") && n.contains("LOGIN")) return v;
                        if (p.equals("CONFIGURATION") && (n.contains("CONFIG") || n.contains("CONFIGURATION"))) return v;
                        if (p.equals("PLAY") && n.contains("PLAY")) return v;
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }
}