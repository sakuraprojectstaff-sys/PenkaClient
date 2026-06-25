package fun.rich.commands.defaults;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.Formatting;

import fun.rich.Rich;
import fun.rich.display.hud.Notifications;
import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.packet.network.Network;
import fun.rich.utils.display.interfaces.QuickImports;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class RCTCommand extends Command implements QuickImports {

    protected RCTCommand(Rich main) {
        super("rct");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (mc == null || mc.player == null || mc.world == null) {
            Notifications.getInstance().addList("[RCT] Мир/игрок недоступен", 3000);
            return;
        }

        if (safeIsPvp()) {
            Notifications.getInstance().addList("[RCT] Вы находитесь в режиме " + Formatting.RED + "пвп", 3000);
            return;
        }

        int anarchy;
        if (args.hasAny()) {
            args.requireMin(1);
            anarchy = args.getArgs().getFirst().getAs(Integer.class);
        } else {
            Integer parsed = findAnarchyFromScoreboard();
            if (parsed != null && parsed > 0) {
                anarchy = parsed;
            } else {
                anarchy = safeGetNetworkAnarchy();
            }

            if (anarchy <= 0) {
                Notifications.getInstance().addList("[RCT] Не удалось определить " + Formatting.RED + "анархию", 3000);
                return;
            }
        }

        reconnectByChat(anarchy);
        Notifications.getInstance().addList("[RCT] Перезаход на " + Formatting.GREEN + "анархию " + anarchy, 2500);
    }

    private void reconnectByChat(int anarchy) {
        String cmd = "/an" + anarchy;
        sendChat(cmd.startsWith("/") ? "/hub" : "/hub");
        sendChat(cmd);

        Thread t = new Thread(() -> {
            try {
                Thread.sleep(1100L);
            } catch (InterruptedException ignored) {
            }
            runOnClient(() -> sendChat(cmd));
        }, "rct-reconnect");
        t.setDaemon(true);
        t.start();
    }

    private void runOnClient(Runnable runnable) {
        try {
            if (mc != null) {
                mc.execute(runnable);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            runnable.run();
        } catch (Throwable ignored) {
        }
    }

    private void sendChat(String message) {
        if (mc == null || mc.player == null || message == null || message.isEmpty()) return;

        try {
            Method m = mc.player.getClass().getMethod("sendChatMessage", String.class);
            m.setAccessible(true);
            m.invoke(mc.player, message);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Object networkHandler = null;

            try {
                Field f = mc.player.getClass().getField("networkHandler");
                f.setAccessible(true);
                networkHandler = f.get(mc.player);
            } catch (Throwable ignored) {
            }

            if (networkHandler == null) {
                try {
                    Method m = mc.player.getClass().getMethod("getNetworkHandler");
                    m.setAccessible(true);
                    networkHandler = m.invoke(mc.player);
                } catch (Throwable ignored) {
                }
            }

            if (networkHandler != null) {
                String commandNoSlash = message.startsWith("/") ? message.substring(1) : message;

                try {
                    Method m = networkHandler.getClass().getMethod("sendChatCommand", String.class);
                    m.setAccessible(true);
                    m.invoke(networkHandler, commandNoSlash);
                    return;
                } catch (Throwable ignored) {
                }

                try {
                    Method m = networkHandler.getClass().getMethod("sendChatMessage", String.class);
                    m.setAccessible(true);
                    m.invoke(networkHandler, message);
                    return;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Integer findAnarchyFromScoreboard() {
        try {
            Object scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) return null;

            Iterable<?> objectives = getObjectivesIterable(scoreboard);
            if (objectives == null) return null;

            for (Object objective : objectives) {
                String line = getObjectiveDisplayName(objective);
                Integer an = parseAnarchyNumber(line);
                if (an != null) return an;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Iterable<?> getObjectivesIterable(Object scoreboard) {
        try {
            Method m = scoreboard.getClass().getMethod("getScoreObjectives");
            m.setAccessible(true);
            Object v = m.invoke(scoreboard);
            if (v instanceof Iterable<?>) return (Iterable<?>) v;
        } catch (Throwable ignored) {
        }

        try {
            Method m = scoreboard.getClass().getMethod("getObjectives");
            m.setAccessible(true);
            Object v = m.invoke(scoreboard);
            if (v instanceof Iterable<?>) return (Iterable<?>) v;
        } catch (Throwable ignored) {
        }

        return null;
    }

    private String getObjectiveDisplayName(Object objective) {
        if (objective == null) return null;

        try {
            Method m = objective.getClass().getMethod("getDisplayName");
            m.setAccessible(true);
            Object text = m.invoke(objective);
            if (text == null) return null;

            try {
                Method getString = text.getClass().getMethod("getString");
                getString.setAccessible(true);
                Object s = getString.invoke(text);
                return s == null ? null : String.valueOf(s);
            } catch (Throwable ignored) {
            }

            return String.valueOf(text);
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Integer parseAnarchyNumber(String s) {
        if (s == null || s.isEmpty()) return null;

        String lower = s.toLowerCase();
        int idx = lower.indexOf("анарх");
        if (idx == -1) return null;

        int start = -1;
        for (int i = idx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                start = i;
                break;
            }
        }
        if (start == -1) return null;

        int end = start;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;

        try {
            return Integer.parseInt(s.substring(start, end));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int safeGetNetworkAnarchy() {
        try {
            return Network.getAnarchy();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean safeIsPvp() {
        try {
            return Network.isPvp();
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Перезаходит на анархию";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Перезаходит на анархию",
                "",
                "Использование:",
                "> rct <anarchy> - Заходит на <anarchy>",
                "> rct - Перезаходит на текущую анархию"
        );
    }
}