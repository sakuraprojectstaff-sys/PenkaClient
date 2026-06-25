package fun.rich.commands.defaults;

import fun.rich.features.impl.misc.AutoSell;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class AutoSellParse extends Command {

    protected AutoSellParse() {
        super("autosell");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(2);

        if (!args.hasAny()) {
            usage();
            return;
        }

        String sub = args.getString();

        if (eq(sub, "parse", "pars")) {
            AutoSell module = module();
            if (module == null) {
                logDirect("AutoSell не найден", Formatting.RED);
                return;
            }

            float percent = 100f;

            if (args.hasAny()) {
                Float parsed = parseFloatSafe(args.getString());
                if (parsed == null) {
                    logDirect("Используй: .autosell parse [percent]", Formatting.RED);
                    return;
                }
                percent = parsed;
            }

            logDirect(module.startManualParse(percent));
            return;
        }

        if (eq(sub, "status", "state")) {
            AutoSell module = module();
            if (module == null) {
                logDirect("AutoSell не найден", Formatting.RED);
                return;
            }

            logDirect(module.getParserStatus());
            return;
        }

        if (eq(sub, "stop", "cancel")) {
            AutoSell module = module();
            if (module == null) {
                logDirect("AutoSell не найден", Formatting.RED);
                return;
            }

            logDirect(module.stopManualParse());
            return;
        }

        if (eq(sub, "help", "?")) {
            usage();
            return;
        }

        usage();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            return Stream.empty();
        }

        String first = args.getString();

        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("parse", "pars", "status", "stop")
                    .filterPrefix(first)
                    .stream();
        }

        if (eq(first, "parse", "pars")) {
            String second = args.getString();
            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .prepend("100", "95", "90", "110")
                        .filterPrefix(second)
                        .stream();
            }
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Ручной парсер цен для AutoSell";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Ручной парсер цен книг для AutoSell.",
                "",
                "Использование:",
                "> autosell parse - Запустить парсер с 100% от найденного минимума.",
                "> autosell parse 95 - Поставить цену 95% от найденного минимума.",
                "> autosell pars - Алиас parse.",
                "> autosell status - Показать статус парсера.",
                "> autosell stop - Остановить парсер."
        );
    }

    private void usage() {
        logDirect(".autosell parse [percent]");
        logDirect(".autosell status");
        logDirect(".autosell stop");
    }

    private AutoSell module() {
        try {
            return Instance.get(AutoSell.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private Float parseFloatSafe(String s) {
        try {
            return Float.parseFloat(s.replace(',', '.'));
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean eq(String value, String... variants) {
        for (String v : variants) {
            if (v.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}