package fun.rich.common.proxy;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalProxyManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LocalProxyManager INSTANCE = new LocalProxyManager();

    private static final String BIND_HOST = "127.0.0.1";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int ACCEPT_POLL_TIMEOUT_MS = 1000;
    private static final int HANDSHAKE_READ_TIMEOUT_MS = 15000;
    private static final int TRANSFER_BUFFER_SIZE = 65536;
    private static final int MAX_FIRST_PACKET_SIZE = 2 * 1024 * 1024;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong sessionIds = new AtomicLong(0);

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile Session currentSession;

    private volatile String upstreamHost = "";
    private volatile int upstreamPort = 25565;
    private volatile String handshakeHost = "";
    private volatile int localPort = 25566;

    private LocalProxyManager() {
    }

    public static LocalProxyManager getInstance() {
        return INSTANCE;
    }

    public synchronized void start(String upstreamHost, int upstreamPort, int localPort) throws IOException {
        start(upstreamHost, upstreamPort, upstreamHost, localPort);
    }

    public synchronized void start(String upstreamHost, int upstreamPort, String handshakeHost, int localPort) throws IOException {
        String normalizedUpstreamHost = normalizeHost(upstreamHost);
        String normalizedHandshakeHost = normalizeHost(handshakeHost);

        validatePort(upstreamPort, false);
        validatePort(localPort, false);

        if (normalizedUpstreamHost.isEmpty()) {
            throw new IOException("Upstream host is empty");
        }

        if (running.get() && sameConfiguration(normalizedUpstreamHost, upstreamPort, normalizedHandshakeHost, localPort)) {
            return;
        }

        stop();

        this.upstreamHost = normalizedUpstreamHost;
        this.upstreamPort = upstreamPort;
        this.handshakeHost = normalizedHandshakeHost.isEmpty() ? normalizedUpstreamHost : normalizedHandshakeHost;

        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(BIND_HOST, localPort));
        socket.setSoTimeout(ACCEPT_POLL_TIMEOUT_MS);

        this.serverSocket = socket;
        this.localPort = socket.getLocalPort();
        this.running.set(true);

        Thread thread = new Thread(this::acceptLoop, "Rich-LocalProxy-Acceptor");
        thread.setDaemon(true);
        this.acceptThread = thread;
        thread.start();

        LOGGER.info("Started local proxy {}:{} -> {}:{} (handshake host: {})",
                BIND_HOST, this.localPort, this.upstreamHost, this.upstreamPort, this.handshakeHost);
    }

    public synchronized void ensureRunning(String upstreamHost, int upstreamPort, int localPort) throws IOException {
        start(upstreamHost, upstreamPort, upstreamHost, localPort);
    }

    public synchronized void ensureRunning(String upstreamHost, int upstreamPort, String handshakeHost, int localPort) throws IOException {
        start(upstreamHost, upstreamPort, handshakeHost, localPort);
    }

    public synchronized void stop() {
        boolean wasRunning = running.getAndSet(false);

        Session session = this.currentSession;
        this.currentSession = null;
        if (session != null) {
            session.close();
        }

        ServerSocket socket = this.serverSocket;
        this.serverSocket = null;
        closeQuietly(socket);

        Thread thread = this.acceptThread;
        this.acceptThread = null;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (wasRunning || socket != null) {
            LOGGER.info("Stopped local proxy");
        }
    }

    public boolean isRunning() {
        ServerSocket socket = this.serverSocket;
        return running.get() && socket != null && !socket.isClosed();
    }

    public String getBindHost() {
        return BIND_HOST;
    }

    public String getUpstreamHost() {
        return upstreamHost;
    }

    public int getUpstreamPort() {
        return upstreamPort;
    }

    public String getHandshakeHost() {
        return handshakeHost;
    }

    public int getLocalPort() {
        return localPort;
    }

    public int getConnectedClients() {
        Session session = currentSession;
        return session == null ? 0 : session.getConnectedClients();
    }

    public int getActivePlayer() {
        Session session = currentSession;
        return session == null ? 0 : session.getActivePlayer();
    }

    public boolean setActivePlayer(int index) {
        Session session = currentSession;
        return session != null && session.setActivePlayer(index);
    }

    private void acceptLoop() {
        while (running.get()) {
            ServerSocket socket = this.serverSocket;
            if (socket == null || socket.isClosed()) {
                break;
            }

            try {
                Socket accepted = socket.accept();
                configureSocket(accepted);

                PendingClient pending = readPendingClient(accepted);
                if (pending == null) {
                    closeQuietly(accepted);
                    continue;
                }

                handleAccepted(pending);
            } catch (SocketTimeoutException ignored) {
            } catch (IOException e) {
                if (running.get()) {
                    LOGGER.warn("Local proxy accept failed", e);
                }
            }
        }
    }

    private void handleAccepted(PendingClient pending) {
        Session session;
        synchronized (this) {
            if (!running.get()) {
                closeQuietly(pending.socket);
                return;
            }

            session = currentSession;
            if (session == null || session.isClosed() || session.isEmpty()) {
                session = new Session(sessionIds.incrementAndGet(), upstreamHost, upstreamPort, handshakeHost);
                currentSession = session;
            } else if (!session.canAcceptNewClient()) {
                closeQuietly(pending.socket);
                return;
            }
        }

        if (!session.attachClient(pending)) {
            closeQuietly(pending.socket);
        }
    }

    private PendingClient readPendingClient(Socket socket) throws IOException {
        int previousTimeout = socket.getSoTimeout();
        socket.setSoTimeout(HANDSHAKE_READ_TIMEOUT_MS);
        try {
            byte[] firstFrame = readFrame(socket.getInputStream());
            int nextState = readHandshakeNextState(firstFrame);
            if (nextState != 2) {
                return null;
            }
            return new PendingClient(socket, firstFrame);
        } finally {
            socket.setSoTimeout(previousTimeout);
        }
    }

    private synchronized void clearCurrentSession(Session session) {
        if (this.currentSession == session) {
            this.currentSession = null;
        }
    }

    private boolean sameConfiguration(String upstreamHost, int upstreamPort, String handshakeHost, int localPort) {
        String effectiveHandshakeHost = handshakeHost.isEmpty() ? upstreamHost : handshakeHost;
        return Objects.equals(this.upstreamHost, upstreamHost)
                && this.upstreamPort == upstreamPort
                && Objects.equals(this.handshakeHost, effectiveHandshakeHost)
                && this.localPort == localPort;
    }

    private static void configureSocket(Socket socket) throws IOException {
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    private static void validatePort(int port, boolean allowZero) {
        int min = allowZero ? 0 : 1;
        if (port < min || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }

    private static String normalizeHost(String host) {
        return host == null ? "" : host.trim();
    }

    private static int readHandshakeNextState(byte[] frameData) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(frameData);
        int packetId = MCVarInt.read(in);
        if (packetId != 0) {
            return -1;
        }
        MCVarInt.read(in);
        int hostLength = MCVarInt.read(in);
        skipFully(in, hostLength);
        if (in.read() == -1 || in.read() == -1) {
            throw new EOFException("Missing handshake port");
        }
        return MCVarInt.read(in);
    }

    private static byte[] readFrame(InputStream in) throws IOException {
        int length = MCVarInt.read(in);
        if (length < 0 || length > MAX_FIRST_PACKET_SIZE) {
            throw new IOException("Invalid first packet length: " + length);
        }
        return readExact(in, length);
    }

    private static byte[] readExact(InputStream in, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(data, offset, length - offset);
            if (read == -1) {
                throw new EOFException("Unexpected end of stream");
            }
            offset += read;
        }
        return data;
    }

    private static void skipFully(InputStream in, int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException("Unexpected end of stream");
                }
                skipped = 1;
            }
            remaining -= (int) skipped;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private static final class PendingClient {
        private final Socket socket;
        private final byte[] firstFrame;

        private PendingClient(Socket socket, byte[] firstFrame) {
            this.socket = socket;
            this.firstFrame = firstFrame;
        }
    }

    private final class Session implements Closeable {
        private final long id;
        private final String targetHost;
        private final int targetPort;
        private final String rewriteHost;

        private final Object stateLock = new Object();
        private final Object upstreamWriteLock = new Object();

        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicInteger activePlayer = new AtomicInteger(0);

        private volatile Socket upstreamSocket;
        private volatile Thread upstreamThread;

        private final ClientSlot[] clients = new ClientSlot[2];

        private Session(long id, String targetHost, int targetPort, String rewriteHost) {
            this.id = id;
            this.targetHost = targetHost;
            this.targetPort = targetPort;
            this.rewriteHost = rewriteHost;
        }

        private boolean isClosed() {
            return closed.get();
        }

        private boolean isEmpty() {
            synchronized (stateLock) {
                return clients[0] == null && clients[1] == null;
            }
        }

        private boolean canAcceptNewClient() {
            synchronized (stateLock) {
                return !closed.get() && !started.get() && (clients[0] == null || clients[1] == null);
            }
        }

        private boolean attachClient(PendingClient pending) {
            int slotIndex;
            synchronized (stateLock) {
                if (closed.get() || started.get()) {
                    return false;
                }

                slotIndex = clients[0] == null ? 0 : (clients[1] == null ? 1 : -1);
                if (slotIndex == -1) {
                    return false;
                }

                clients[slotIndex] = new ClientSlot(slotIndex, pending.socket, pending.firstFrame);

                if (slotIndex == 0) {
                    activePlayer.set(0);
                }

                if (clients[0] != null && clients[1] != null && started.compareAndSet(false, true)) {
                    Thread t = new Thread(this::startRelay, "Rich-LocalProxy-Session-" + id);
                    t.setDaemon(true);
                    t.start();
                }
            }
            return true;
        }

        private void startRelay() {
            Socket upstream = connectToServer();
            if (upstream == null) {
                close();
                return;
            }
            upstreamSocket = upstream;

            upstreamThread = new Thread(this::pumpUpstreamToClients, "Rich-LocalProxy-Upstream-" + id);
            upstreamThread.setDaemon(true);
            upstreamThread.start();

            for (int i = 0; i < 2; i++) {
                ClientSlot slot;
                synchronized (stateLock) {
                    slot = clients[i];
                }
                if (slot != null) {
                    Thread t = new Thread(() -> pumpClient(slot), "Rich-LocalProxy-Client-" + id + "-" + (i + 1));
                    t.setDaemon(true);
                    slot.thread = t;
                    t.start();
                }
            }
        }

        private Socket connectToServer() {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(targetHost);
                for (InetAddress address : addresses) {
                    Socket socket = new Socket();
                    try {
                        configureSocket(socket);
                        socket.connect(new InetSocketAddress(address, targetPort), CONNECT_TIMEOUT_MS);
                        return socket;
                    } catch (IOException e) {
                        closeQuietly(socket);
                    }
                }
            } catch (IOException ignored) {
            }
            return null;
        }

        private void pumpUpstreamToClients() {
            try {
                InputStream in = upstreamSocket.getInputStream();
                byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];

                while (!closed.get()) {
                    int read = in.read(buffer);
                    if (read == -1) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }

                    for (int i = 0; i < 2; i++) {
                        ClientSlot slot;
                        synchronized (stateLock) {
                            slot = clients[i];
                        }
                        if (slot == null || slot.closed.get()) {
                            continue;
                        }
                        try {
                            synchronized (slot.writeLock) {
                                OutputStream out = slot.socket.getOutputStream();
                                out.write(buffer, 0, read);
                            }
                        } catch (IOException e) {
                            disconnectClient(i);
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                close();
            }
        }

        private void pumpClient(ClientSlot slot) {
            try {
                InputStream in = slot.socket.getInputStream();

                if (slot.index == 0) {
                    byte[] rewrittenFrame = HandshakeRewriter.rewriteHandshakePacket(slot.firstFrame, rewriteHost, targetPort);
                    byte[] wrappedFrame = HandshakeRewriter.wrapFrame(rewrittenFrame);
                    writeToUpstream(wrappedFrame, 0, wrappedFrame.length);
                }

                byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
                while (!closed.get() && !slot.closed.get()) {
                    int read = in.read(buffer);
                    if (read == -1) {
                        disconnectClient(slot.index);
                        return;
                    }
                    if (read == 0) {
                        continue;
                    }

                    if (slot.index == activePlayer.get()) {
                        writeToUpstream(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                disconnectClient(slot.index);
            }
        }

        private void writeToUpstream(byte[] data, int off, int len) throws IOException {
            synchronized (upstreamWriteLock) {
                OutputStream out = upstreamSocket.getOutputStream();
                out.write(data, off, len);
            }
        }

        private void disconnectClient(int index) {
            ClientSlot removed;
            boolean closeAll = false;

            synchronized (stateLock) {
                removed = clients[index];
                if (removed == null) {
                    return;
                }
                clients[index] = null;
                removed.closed.set(true);

                if (index == activePlayer.get()) {
                    if (clients[0] != null && !clients[0].closed.get()) {
                        activePlayer.set(0);
                    } else if (clients[1] != null && !clients[1].closed.get()) {
                        activePlayer.set(1);
                    }
                }

                closeAll = clients[0] == null && clients[1] == null;
            }

            closeQuietly(removed.socket);

            if (closeAll) {
                close();
            }
        }

        private int getConnectedClients() {
            synchronized (stateLock) {
                int c = 0;
                if (clients[0] != null) c++;
                if (clients[1] != null) c++;
                return c;
            }
        }

        private int getActivePlayer() {
            return activePlayer.get();
        }

        private boolean setActivePlayer(int index) {
            if (index < 0 || index > 1) {
                return false;
            }
            synchronized (stateLock) {
                ClientSlot slot = clients[index];
                if (slot == null || slot.closed.get()) {
                    return false;
                }
                activePlayer.set(index);
                return true;
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            clearCurrentSession(this);

            ClientSlot c0;
            ClientSlot c1;
            Socket upstream;

            synchronized (stateLock) {
                c0 = clients[0];
                c1 = clients[1];
                clients[0] = null;
                clients[1] = null;
                upstream = upstreamSocket;
                upstreamSocket = null;
            }

            if (c0 != null) {
                c0.closed.set(true);
                closeQuietly(c0.socket);
            }
            if (c1 != null) {
                c1.closed.set(true);
                closeQuietly(c1.socket);
            }

            closeQuietly(upstream);
        }

        private final class ClientSlot {
            private final int index;
            private final Socket socket;
            private final byte[] firstFrame;
            private final Object writeLock = new Object();
            private final AtomicBoolean closed = new AtomicBoolean(false);
            private volatile Thread thread;

            private ClientSlot(int index, Socket socket, byte[] firstFrame) {
                this.index = index;
                this.socket = socket;
                this.firstFrame = firstFrame;
            }
        }
    }
}