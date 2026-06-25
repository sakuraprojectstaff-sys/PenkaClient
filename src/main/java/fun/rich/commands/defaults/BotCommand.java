package fun.rich.commands.defaults;

import fun.rich.bots.Bot;
import fun.rich.bots.BotManager;
import fun.rich.bots.BotStarter;
import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;
import net.minecraft.client.MinecraftClient;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class BotCommand extends Command {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static Bot controlledBot;

    public BotCommand() {
        super("bot");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            usage(label);
            return;
        }

        String action = args.getString().toLowerCase(Locale.US);

        switch (action) {
            case "connect" -> {
                if (!args.has(2)) {
                    logDirect("Использование: ." + label + " connect <nick> <ip>");
                    return;
                }

                String nick = args.getString();
                String ip = args.getString();
                args.requireMax(3);

                Bot existing = resolveBot(nick);
                if (existing != null && !existing.isTerminal()) {
                    logDirect("Бот уже существует: " + nick + " [" + existing.getDisplayState() + "]");
                    return;
                }

                Bot pending = BotManager.createPending(nick, ip, 25565);
                pending.setStateMessage("Ожидание подключения");
                pending.touch();

                BotStarter.run(nick, ip);
                logDirect("Бот " + nick + " подключается к " + ip);
            }

            case "connectwithport" -> {
                if (!args.has(3)) {
                    logDirect("Использование: ." + label + " connectWithPort <nick> <ip> <port>");
                    return;
                }

                String nick = args.getString();
                String ip = args.getString();
                int port = parseIntSafe(args.getString(), 25565);
                args.requireMax(4);

                Bot existing = resolveBot(nick);
                if (existing != null && !existing.isTerminal()) {
                    logDirect("Бот уже существует: " + nick + " [" + existing.getDisplayState() + "]");
                    return;
                }

                Bot pending = BotManager.createPending(nick, ip, port);
                pending.setStateMessage("Ожидание подключения");
                pending.touch();

                BotStarter.run(nick, ip, port);
                logDirect("Бот " + nick + " подключается к " + ip + ":" + port);
            }

            case "list" -> {
                args.requireMax(1);

                List<Bot> bots = BotManager.listSnapshot();
                if (bots.isEmpty()) {
                    logDirect("Активных ботов нет");
                    return;
                }

                logDirect("Боты: " + bots.size() + " | pending: " + BotManager.pendingCount() + " | play: " + BotManager.playCount());
                for (Bot bot : bots) {
                    if (bot == null) {
                        continue;
                    }
                    bot.refreshRuntimeState();
                    logDirect(bot.getDisplayLine());
                }
            }

            case "remove" -> {
                if (!args.hasAny()) {
                    logDirect("Использование: ." + label + " remove <nickname>");
                    return;
                }

                String name = args.getString();
                args.requireMax(2);

                Bot bot = resolveBot(name);
                if (bot == null) {
                    logDirect("Бот не найден: " + name);
                    return;
                }

                if (controlledBot != null) {
                    controlledBot = resolveBot(controlledBot.getNameString());
                }

                if (controlledBot == bot) {
                    controlledBot = null;
                    if (mc.player != null) {
                        try {
                            mc.setCameraEntity(mc.player);
                        } catch (Throwable ignored) {
                        }
                    }
                }

                if (BotManager.removeByName(name)) {
                    logDirect("Бот удалён: " + name);
                } else {
                    logDirect("Бот не найден: " + name);
                }
            }

            case "control" -> {
                if (!args.hasAny()) {
                    logDirect("Использование: ." + label + " control <name>");
                    return;
                }

                String name = args.getString();
                args.requireMax(2);

                Bot bot = resolveBot(name);
                if (bot == null) {
                    logDirect("Бот не найден: " + name);
                    return;
                }

                bot.refreshRuntimeState();

                if (!bot.isReadyForControl()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Бот пока недоступен для контроля: ").append(name).append(" [").append(bot.getDisplayState()).append("]");

                    if (bot.getConnection() == null) {
                        sb.append(" noConnection");
                    } else if (!bot.getConnection().isConnectionOpen()) {
                        sb.append(" closedConnection");
                    }

                    if (bot.getNetworkManager() == null) {
                        sb.append(" noNetwork");
                    }
                    if (bot.getBotPlayer() == null) {
                        sb.append(" noPlayer");
                    }
                    if (bot.getBotController() == null) {
                        sb.append(" noController");
                    }

                    logDirect(sb.toString());

                    if (bot.getStateMessage() != null && !bot.getStateMessage().isBlank()) {
                        logDirect("Статус: " + bot.getStateMessage());
                    }
                    return;
                }

                controlledBot = bot;
                logDirect("Контроль переключён на " + name + " [" + bot.getDisplayState() + "]");

                if (bot.getStateMessage() != null && !bot.getStateMessage().isBlank()) {
                    logDirect("Статус: " + bot.getStateMessage());
                }
            }

            case "return" -> {
                args.requireMax(1);
                controlledBot = null;
                if (mc.player != null) {
                    try {
                        mc.setCameraEntity(mc.player);
                    } catch (Throwable ignored) {
                    }
                }
                logDirect("Возврат к основному игроку");
            }

            case "chat" -> {
                if (!args.has(2)) {
                    logDirect("Использование: ." + label + " chat <nick> <message>");
                    return;
                }

                String nick = args.getString();
                String message = args.rawRest().trim();

                if (message.isEmpty()) {
                    logDirect("Использование: ." + label + " chat <nick> <message>");
                    return;
                }

                Bot bot = resolveBot(nick);
                if (bot == null) {
                    logDirect("Бот не найден: " + nick);
                    return;
                }

                bot.refreshRuntimeState();

                if (bot.getBotPlayer() == null) {
                    logDirect("У бота ещё нет player instance: " + nick);
                    return;
                }

                bot.getBotPlayer().sendChatMessage(message);
                bot.touch();
                logDirect("Сообщение отправлено от " + nick);
            }

            case "tapemouse" -> {
                if (!args.hasAny()) {
                    logDirect("Использование: ." + label + " tapemouse <add|remove> <botnick>");
                    return;
                }

                String tmAction = args.getString().toLowerCase(Locale.US);

                if (!args.hasAny()) {
                    logDirect("Использование: ." + label + " tapemouse <add|remove> <botnick>");
                    return;
                }

                String botNick = args.getString();
                args.requireMax(3);

                if (tmAction.equals("add")) {
                    if (BotManager.addTapeMouse(botNick)) {
                        logDirect("TapeMouse добавлен для " + botNick);
                    } else {
                        logDirect("Не удалось добавить TapeMouse для " + botNick);
                    }
                    return;
                }

                if (tmAction.equals("remove")) {
                    if (BotManager.removeTapeMouse(botNick)) {
                        logDirect("TapeMouse удалён у " + botNick);
                    } else {
                        logDirect("Не удалось удалить TapeMouse у " + botNick);
                    }
                    return;
                }

                logDirect("Использование: ." + label + " tapemouse <add|remove> <botnick>");
            }

            default -> usage(label);
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            return Stream.empty();
        }

        String a0 = args.getString();

        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .prepend("connect", "connectWithPort", "list", "remove", "control", "return", "chat", "tapemouse")
                    .filterPrefix(a0)
                    .stream();
        }

        if (a0.equalsIgnoreCase("tapemouse")) {
            String a1 = args.getString();

            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .prepend("add", "remove")
                        .filterPrefix(a1)
                        .stream();
            }

            String a2 = args.getString();

            if (a1.equalsIgnoreCase("add")) {
                String[] names = BotManager.listSnapshot().stream()
                        .filter(bot -> bot != null && !BotManager.hasTapeMouse(bot))
                        .map(Bot::getNameString)
                        .filter(s -> s != null && !s.isBlank())
                        .toArray(String[]::new);

                return new TabCompleteHelper()
                        .prepend(names)
                        .filterPrefix(a2)
                        .stream();
            }

            if (a1.equalsIgnoreCase("remove")) {
                String[] names = BotManager.tapeMouseBots.stream()
                        .filter(t -> t != null && t.getBot() != null)
                        .map(t -> t.getBot().getNameString())
                        .filter(s -> s != null && !s.isBlank())
                        .toArray(String[]::new);

                return new TabCompleteHelper()
                        .prepend(names)
                        .filterPrefix(a2)
                        .stream();
            }

            return Stream.empty();
        }

        if (a0.equalsIgnoreCase("remove") || a0.equalsIgnoreCase("control") || a0.equalsIgnoreCase("chat")) {
            String a1 = args.getString();

            String[] names = BotManager.listSnapshot().stream()
                    .filter(bot -> bot != null)
                    .map(Bot::getNameString)
                    .filter(s -> s != null && !s.isBlank())
                    .toArray(String[]::new);

            return new TabCompleteHelper()
                    .prepend(names)
                    .filterPrefix(a1)
                    .stream();
        }

        if (a0.equalsIgnoreCase("connectwithport")) {
            args.getString();
            if (!args.hasAny()) {
                return Stream.empty();
            }

            args.getString();
            if (!args.hasAny()) {
                return Stream.empty();
            }

            String a3 = args.getString();
            return new TabCompleteHelper()
                    .prepend("25565")
                    .filterPrefix(a3)
                    .stream();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Управление ботами";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Управление ботами.",
                "",
                "Использование:",
                "> .bot connect <nick> <ip>",
                "> .bot connectWithPort <nick> <ip> <port>",
                "> .bot list",
                "> .bot remove <nickname>",
                "> .bot control <name>",
                "> .bot return",
                "> .bot chat <nick> <message>",
                "> .bot tapemouse add <botnick>",
                "> .bot tapemouse remove <botname>"
        );
    }

    private void usage(String label) {
        logDirect("Использование: ." + label + " connect|connectWithPort|list|remove|control|return|chat|tapemouse");
    }

    private static Bot resolveBot(String name) {
        Bot bot = BotManager.findByName(name);
        if (bot != null) {
            bot.refreshRuntimeState();
        }
        return bot;
    }

    private static int parseIntSafe(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }
}