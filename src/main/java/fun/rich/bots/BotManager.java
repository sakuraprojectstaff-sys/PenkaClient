package fun.rich.bots;

import fun.rich.bots.impl.BotTapeMouse;

import java.util.ArrayList;
import java.util.List;

public final class BotManager {
    public static final List<Bot> allBots = new ArrayList<>();
    public static final List<Bot> xyesosBots = new ArrayList<>();
    public static final List<BotTapeMouse> tapeMouseBots = new ArrayList<>();

    private BotManager() {
    }

    public static synchronized Bot createPending(String name, String host, int port) {
        Bot existing = findByName(name);
        if (existing != null) {
            if (existing.isTerminal()) {
                disconnect(existing);
                allBots.remove(existing);
                xyesosBots.remove(existing);
                tapeMouseBots.removeIf(t -> t != null && t.getBot() == existing);
            } else {
                existing.setTargetHost(host == null ? "" : host.trim());
                existing.setTargetPort(normalizePort(port));
                existing.markConnecting();
                existing.setStateMessage("Ожидание подключения");
                existing.touch();
                return existing;
            }
        }

        Bot bot = new Bot(name, host, port);
        bot.markConnecting();
        bot.setStateMessage("Ожидание подключения");
        allBots.add(bot);
        return bot;
    }

    public static synchronized void add(Bot bot) {
        if (bot == null) {
            return;
        }

        bot.refreshRuntimeState();

        Bot existing = findByName(bot.getNameString());
        if (existing == bot) {
            return;
        }

        if (existing != null) {
            merge(existing, bot);
            existing.refreshRuntimeState();
            if (existing.getConnection() != null) {
                existing.getConnection().setBotOwner(existing);
            }
            return;
        }

        allBots.add(bot);
    }

    public static synchronized void mergeIntoPending(Bot readyBot) {
        if (readyBot == null) {
            return;
        }

        readyBot.refreshRuntimeState();

        String name = readyBot.getNameString();
        Bot pending = findByName(name);

        if (pending == null) {
            readyBot.markPlay();
            add(readyBot);
            return;
        }

        if (pending != readyBot) {
            merge(pending, readyBot);
            if (readyBot.getConnection() != null) {
                readyBot.getConnection().setBotOwner(pending);
            }
        }

        pending.refreshRuntimeState();
        pending.markPlay();
        if (pending.getStateMessage() == null || pending.getStateMessage().isBlank() || pending.getStateMessage().contains("configuration")) {
            pending.setStateMessage("Play phase");
        }
        pending.touch();
    }

    public static synchronized Bot findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        for (Bot bot : allBots) {
            if (bot == null) {
                continue;
            }

            bot.refreshRuntimeState();

            String botName = bot.getNameString();
            if (botName != null && botName.equalsIgnoreCase(name)) {
                return bot;
            }
        }
        return null;
    }

    public static synchronized boolean hasBot(String name) {
        return findByName(name) != null;
    }

    public static synchronized boolean removeByName(String name) {
        Bot bot = findByName(name);
        if (bot == null) {
            return false;
        }

        disconnect(bot);
        allBots.remove(bot);
        xyesosBots.remove(bot);
        tapeMouseBots.removeIf(t -> t != null && t.getBot() == bot);

        try {
            bot.setTapeMouse(null);
            bot.markDisconnected();
            bot.touch();
        } catch (Throwable ignored) {
        }

        return true;
    }

    public static synchronized void removeAll() {
        List<Bot> copy = new ArrayList<>(allBots);
        for (Bot bot : copy) {
            disconnect(bot);
            try {
                bot.setTapeMouse(null);
                bot.markDisconnected();
                bot.touch();
            } catch (Throwable ignored) {
            }
        }

        allBots.clear();
        xyesosBots.clear();
        tapeMouseBots.clear();
    }

    public static synchronized boolean hasTapeMouse(Bot bot) {
        if (bot == null) {
            return false;
        }

        for (BotTapeMouse tape : tapeMouseBots) {
            if (tape != null && tape.getBot() == bot) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean addTapeMouse(String name) {
        Bot bot = findByName(name);
        if (bot == null || hasTapeMouse(bot)) {
            return false;
        }

        BotTapeMouse tape = new BotTapeMouse(bot);
        bot.setTapeMouse(tape);
        bot.touch();
        tapeMouseBots.add(tape);
        return true;
    }

    public static synchronized boolean removeTapeMouse(String name) {
        Bot bot = findByName(name);
        if (bot == null) {
            return false;
        }

        boolean removed = tapeMouseBots.removeIf(t -> t != null && t.getBot() == bot);
        if (removed) {
            bot.setTapeMouse(null);
            bot.touch();
        }
        return removed;
    }

    public static synchronized List<Bot> listSnapshot() {
        List<Bot> out = new ArrayList<>(allBots);
        for (Bot bot : out) {
            if (bot != null) {
                bot.refreshRuntimeState();
            }
        }
        return out;
    }

    public static synchronized int onlineLikeCount() {
        int count = 0;
        for (Bot bot : allBots) {
            if (bot != null) {
                bot.refreshRuntimeState();
                if (bot.isOnlineLike()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static synchronized int pendingCount() {
        int count = 0;
        for (Bot bot : allBots) {
            if (bot != null) {
                bot.refreshRuntimeState();
                if (bot.isPending()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static synchronized int playCount() {
        int count = 0;
        for (Bot bot : allBots) {
            if (bot != null) {
                bot.refreshRuntimeState();
                if (bot.isInPlay()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static synchronized void markLogin(String name) {
        Bot bot = findByName(name);
        if (bot != null) {
            bot.markLogin();
        }
    }

    public static synchronized void markPlay(String name) {
        Bot bot = findByName(name);
        if (bot != null) {
            bot.refreshRuntimeState();
            bot.markPlay();
        }
    }

    public static synchronized void markDisconnected(String name) {
        Bot bot = findByName(name);
        if (bot != null) {
            bot.markDisconnected();
        }
    }

    public static synchronized void markFailed(String name, String message) {
        Bot bot = findByName(name);
        if (bot != null) {
            bot.markFailed(message);
        }
    }

    private static void merge(Bot target, Bot source) {
        if (target == null || source == null || target == source) {
            return;
        }

        if (source.getNetworkManager() != null) {
            target.attachNetwork(source.getNetworkManager());
        }
        if (source.getBotWorld() != null) {
            target.attachWorld(source.getBotWorld());
        }
        if (source.getBotPlayer() != null) {
            target.attachPlayer(source.getBotPlayer());
        }
        if (source.getBotController() != null) {
            target.attachController(source.getBotController());
        }
        if (source.getConnection() != null) {
            target.attachConnection(source.getConnection());
        }
        if (source.getTapeMouse() != null) {
            target.setTapeMouse(source.getTapeMouse());
        }

        if (source.getTargetHost() != null && !source.getTargetHost().isBlank()) {
            target.setTargetHost(source.getTargetHost());
        }
        if (source.getTargetPort() > 0) {
            target.setTargetPort(source.getTargetPort());
        }

        target.setCollected(source.isCollected());
        target.setCodesCollected(source.isCodesCollected());
        target.setLastTimeCollected(source.getLastTimeCollected());

        if (source.getState() != null) {
            target.setState(source.getState());
        }
        if (source.getStateMessage() != null && !source.getStateMessage().isBlank()) {
            target.setStateMessage(source.getStateMessage());
        }

        if (target.getCreatedAt() <= 0L && source.getCreatedAt() > 0L) {
            target.setCreatedAt(source.getCreatedAt());
        }
        if (target.getConnectStartedAt() <= 0L && source.getConnectStartedAt() > 0L) {
            target.setConnectStartedAt(source.getConnectStartedAt());
        }
        if (target.getConnectedAt() <= 0L && source.getConnectedAt() > 0L) {
            target.setConnectedAt(source.getConnectedAt());
        }

        target.touch();
    }

    private static void disconnect(Bot bot) {
        if (bot == null) {
            return;
        }

        try {
            if (bot.getConnection() != null) {
                bot.getConnection().sendQuittingDisconnectingPacket();
            }
        } catch (Throwable ignored) {
        }

        try {
            if (bot.getNetworkManager() != null) {
                bot.getNetworkManager().markClosedReason("Отключено BotManager");
                bot.getNetworkManager().close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static int normalizePort(int port) {
        return port < 1 || port > 65535 ? 25565 : port;
    }
}