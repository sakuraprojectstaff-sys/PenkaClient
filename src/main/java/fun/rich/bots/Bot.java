package fun.rich.bots;

import com.mojang.authlib.GameProfile;
import fun.rich.bots.connection.BotClientPlayNetHandler;
import fun.rich.bots.connection.BotNetwork;
import fun.rich.bots.impl.BotTapeMouse;
import fun.rich.bots.player.BotController;
import fun.rich.bots.player.BotPlayer;
import fun.rich.bots.world.BotWorld;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Getter
@Setter
public class Bot {
    private BotNetwork networkManager;
    private BotWorld botWorld;
    private BotPlayer botPlayer;
    private BotController botController;
    private BotClientPlayNetHandler connection;
    private BotTapeMouse tapeMouse;

    private String targetHost = "";
    private int targetPort = 25565;

    private volatile State state = State.CREATED;
    private volatile String stateMessage = "";
    private volatile boolean collected;
    private volatile boolean codesCollected;
    private volatile long lastTimeCollected;
    private volatile long createdAt = System.currentTimeMillis();
    private volatile long connectStartedAt;
    private volatile long connectedAt;
    private volatile long lastUpdateAt = System.currentTimeMillis();

    public Bot() {
    }

    public Bot(String name, String targetHost, int targetPort) {
        this.botPlayer = new BotPlayer(createProfile(name));
        this.targetHost = targetHost == null ? "" : targetHost.trim();
        this.targetPort = normalizePort(targetPort);
        this.state = State.CONNECTING;
        this.connectStartedAt = System.currentTimeMillis();
        this.lastUpdateAt = this.connectStartedAt;
    }

    public Bot(BotNetwork networkManager, BotWorld botWorld, BotPlayer botPlayer, BotController botController, BotClientPlayNetHandler connection) {
        this.networkManager = networkManager;
        this.botWorld = botWorld;
        this.botPlayer = botPlayer;
        this.botController = botController;
        this.connection = connection;
        this.state = State.PLAY;
        this.connectedAt = System.currentTimeMillis();
        this.lastUpdateAt = this.connectedAt;
        if (this.connection != null) {
            this.connection.setBotOwner(this);
        }
    }

    public Bot(BotNetwork networkManager, BotWorld botWorld, BotPlayer botPlayer, BotController botController, BotClientPlayNetHandler connection, String targetHost, int targetPort) {
        this.networkManager = networkManager;
        this.botWorld = botWorld;
        this.botPlayer = botPlayer;
        this.botController = botController;
        this.connection = connection;
        this.targetHost = targetHost == null ? "" : targetHost.trim();
        this.targetPort = normalizePort(targetPort);
        this.state = State.PLAY;
        this.connectedAt = System.currentTimeMillis();
        this.lastUpdateAt = this.connectedAt;
        if (this.connection != null) {
            this.connection.setBotOwner(this);
        }
    }

    public String getNameString() {
        if (botPlayer != null) {
            String name = botPlayer.getNameString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }

        if (connection != null && connection.getBot() != null) {
            String name = connection.getBot().getNameString();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }

        return "";
    }

    public String getNameClear() {
        return getNameString();
    }

    public void setName(String name) {
        this.botPlayer = new BotPlayer(createProfile(name));
        if (this.connection != null) {
            this.connection.setBot(this.botPlayer);
        }
        touch();
    }

    public void markCreated() {
        this.state = State.CREATED;
        this.createdAt = System.currentTimeMillis();
        this.lastUpdateAt = this.createdAt;
    }

    public void markConnecting() {
        if (isTerminal()) {
            return;
        }
        this.state = State.CONNECTING;
        this.connectStartedAt = System.currentTimeMillis();
        this.lastUpdateAt = this.connectStartedAt;
    }

    public void markLogin() {
        if (isTerminal()) {
            return;
        }
        refreshRuntimeState();
        if (this.state == State.PLAY) {
            return;
        }
        this.state = State.LOGIN;
        touch();
    }

    public void markPlay() {
        if (isTerminal()) {
            return;
        }
        this.state = State.PLAY;
        long now = System.currentTimeMillis();
        if (this.connectedAt <= 0L) {
            this.connectedAt = now;
        }
        this.lastUpdateAt = now;
    }

    public void markDisconnected() {
        this.state = State.DISCONNECTED;
        touch();
    }

    public void markFailed(String message) {
        this.state = State.FAILED;
        this.stateMessage = message == null ? "" : message;
        touch();
    }

    public void markState(State state) {
        this.state = state == null ? State.CREATED : state;
        touch();
    }

    public boolean isPending() {
        refreshRuntimeState();
        return state == State.CREATED || state == State.CONNECTING || state == State.LOGIN;
    }

    public boolean isOnlineLike() {
        refreshRuntimeState();
        return state == State.CONNECTING || state == State.LOGIN || state == State.PLAY;
    }

    public boolean isReadyForControl() {
        refreshRuntimeState();
        if (state != State.PLAY) {
            return false;
        }
        if (connection == null) {
            return false;
        }
        if (!connection.isConnectionOpen()) {
            return false;
        }
        if (networkManager == null) {
            return false;
        }
        return botPlayer != null;
    }

    public boolean isInPlay() {
        refreshRuntimeState();
        return state == State.PLAY;
    }

    public boolean isTerminal() {
        return state == State.DISCONNECTED || state == State.FAILED;
    }

    public String getDisplayState() {
        refreshRuntimeState();
        return switch (state) {
            case CREATED -> "CREATED";
            case CONNECTING -> "CONNECTING";
            case LOGIN -> "LOGIN";
            case PLAY -> "PLAY";
            case DISCONNECTED -> "DISCONNECTED";
            case FAILED -> "FAILED";
        };
    }

    public String getDisplayLine() {
        refreshRuntimeState();
        StringBuilder sb = new StringBuilder();
        sb.append(getNameString().isBlank() ? "<unnamed>" : getNameString());
        sb.append(" [").append(getDisplayState()).append("]");
        if (!targetHost.isBlank()) {
            sb.append(" ").append(targetHost).append(":").append(targetPort);
        }
        if (stateMessage != null && !stateMessage.isBlank()) {
            sb.append(" - ").append(stateMessage);
        }
        return sb.toString();
    }

    public void touch() {
        this.lastUpdateAt = System.currentTimeMillis();
    }

    public void attachNetwork(BotNetwork networkManager) {
        this.networkManager = networkManager;
        touch();
    }

    public void attachWorld(BotWorld botWorld) {
        this.botWorld = botWorld;
        touch();
    }

    public void attachPlayer(BotPlayer botPlayer) {
        this.botPlayer = botPlayer;
        if (this.connection != null && this.connection.getBot() != botPlayer) {
            this.connection.setBot(botPlayer);
        }
        touch();
    }

    public void attachController(BotController botController) {
        this.botController = botController;
        if (this.connection != null && this.connection.getBotController() != botController) {
            this.connection.setBotController(botController);
        }
        touch();
    }

    public void attachConnection(BotClientPlayNetHandler connection) {
        this.connection = connection;
        if (connection != null && connection.getBotOwner() != this) {
            connection.setBotOwner(this);
        }
        touch();
    }

    public void attachTapeMouse(BotTapeMouse tapeMouse) {
        this.tapeMouse = tapeMouse;
        touch();
    }

    public void bindPlayObjects(BotNetwork networkManager, BotWorld botWorld, BotPlayer botPlayer, BotController botController, BotClientPlayNetHandler connection) {
        attachNetwork(networkManager);
        attachWorld(botWorld);
        attachPlayer(botPlayer);
        attachController(botController);
        attachConnection(connection);
        refreshRuntimeState();
        if (!isTerminal()) {
            markPlay();
        }
    }

    public void refreshRuntimeState() {
        if (isTerminal()) {
            return;
        }

        if (connection != null) {
            if (connection.getBotOwner() != this) {
                connection.setBotOwner(this);
            }
            if (networkManager == null && connection.getNetwork() != null) {
                networkManager = connection.getNetwork();
            }
            if (botWorld == null && connection.getWorld() != null) {
                botWorld = connection.getWorld();
            }
            if (botPlayer == null && connection.getBot() != null) {
                botPlayer = connection.getBot();
            }
            if (botController == null && connection.getBotController() != null) {
                botController = connection.getBotController();
            }
        }

        boolean open = connection != null && connection.isConnectionOpen();
        boolean readyRefs = networkManager != null && botPlayer != null;

        if (open && readyRefs) {
            if (state != State.PLAY) {
                markPlay();
                if (stateMessage == null || stateMessage.isBlank() || stateMessage.startsWith("Vanilla login success") || stateMessage.startsWith("configuration")) {
                    stateMessage = "Play phase";
                }
            }
            return;
        }

        if (open) {
            if (state == State.CREATED || state == State.CONNECTING) {
                state = State.LOGIN;
                touch();
            }
        }
    }

    public long getAliveForMs() {
        return Math.max(0L, System.currentTimeMillis() - createdAt);
    }

    public long getConnectedForMs() {
        if (connectedAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - connectedAt);
    }

    public enum State {
        CREATED,
        CONNECTING,
        LOGIN,
        PLAY,
        DISCONNECTED,
        FAILED
    }

    private static GameProfile createProfile(String name) {
        String safeName = nullSafeName(name);
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + safeName).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(uuid, safeName);
    }

    private static String nullSafeName(String name) {
        if (name == null) {
            return "Bot";
        }
        String out = name.trim();
        if (out.isEmpty()) {
            return "Bot";
        }
        if (out.length() > 16) {
            out = out.substring(0, 16);
        }
        return out;
    }

    private static int normalizePort(int port) {
        return port < 1 || port > 65535 ? 25565 : port;
    }
}