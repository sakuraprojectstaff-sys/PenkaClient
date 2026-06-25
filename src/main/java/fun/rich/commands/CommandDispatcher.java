/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package fun.rich.commands;

import fun.rich.Rich;
import fun.rich.commands.argument.ArgConsumer;
import fun.rich.commands.argument.CommandArguments;
import fun.rich.commands.manager.CommandRepository;
import fun.rich.events.chat.ChatEvent;
import fun.rich.events.chat.TabCompleteEvent;
import fun.rich.utils.client.managers.api.command.argument.ICommandArgument;
import fun.rich.utils.client.managers.api.command.exception.CommandNotEnoughArgumentsException;
import fun.rich.utils.client.managers.api.command.exception.CommandNotFoundException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;
import fun.rich.utils.client.managers.api.command.manager.ICommandManager;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.managers.event.EventManager;
import fun.rich.utils.display.interfaces.QuickLogger;
import net.minecraft.util.Pair;

import java.util.List;
import java.util.stream.Stream;

import static fun.rich.utils.client.managers.api.command.IBaritoneChatControl.FORCE_COMMAND_PREFIX;

public class CommandDispatcher implements QuickLogger {
    private final ICommandManager manager;
    public static String prefix = ".";

    public CommandDispatcher(EventManager eventManager) {
        this.manager = Rich.getInstance().getCommandRepository();
        eventManager.register(this);
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        String msg = event.getMessage();
        if (msg == null) return;

        if (msg.startsWith("/")) {
            return;
        }

        boolean forceRun = msg.startsWith(FORCE_COMMAND_PREFIX);
        if ((msg.startsWith(prefix)) || forceRun) {
            event.cancel();
            String commandStr = msg.substring(forceRun ? FORCE_COMMAND_PREFIX.length() : prefix.length());
            if (!runCommand(commandStr) && !commandStr.trim().isEmpty()) {
                new CommandNotFoundException(CommandRepository.expand(commandStr).getLeft()).handle(null, null);
            }
        }
    }

    public boolean runCommand(String msg) {
        if (msg == null) return false;
        if (msg.isEmpty()) {
            return this.runCommand("help");
        }

        Pair<String, List<ICommandArgument>> pair = CommandRepository.expand(msg);
        ArgConsumer argc = new ArgConsumer(this.manager, pair.getRight());
        return this.manager.execute(pair);
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        String eventPrefix = event.prefix;
        if (eventPrefix == null) return;

        if (eventPrefix.startsWith("/")) {
            return;
        }

        if (!eventPrefix.startsWith(prefix)) {
            return;
        }

        String msg = eventPrefix.substring(prefix.length());
        List<ICommandArgument> args = CommandArguments.from(msg, true);
        Stream<String> stream = tabComplete(msg);
        if (args.size() == 1) {
            stream = stream.map(x -> prefix + x);
        }
        event.completions = stream.toArray(String[]::new);
    }

    public Stream<String> tabComplete(String msg) {
        try {
            List<ICommandArgument> args = CommandArguments.from(msg, true);
            ArgConsumer argc = new ArgConsumer(this.manager, args);
            if (argc.hasAtMost(2)) {
                if (argc.hasExactly(1)) {
                    return new TabCompleteHelper()
                            .addCommands(this.manager)
                            .filterPrefix(argc.getString())
                            .stream();
                }
            }
            return this.manager.tabComplete(msg);
        } catch (CommandNotEnoughArgumentsException ignored) {
            return Stream.empty();
        }
    }
}
