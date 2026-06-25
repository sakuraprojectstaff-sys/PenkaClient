package fun.rich.commands.defaults;

import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;
import net.minecraft.client.MinecraftClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class TelegramID extends Command {

    private static final String DEFAULT_CHAT_ID = "";

    public TelegramID() {
        super("tg");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            args.requireMax(0);
            String cur = readSavedChatId();
            if (cur.isEmpty()) cur = DEFAULT_CHAT_ID;
            logDirect("Текущий Chat ID: " + cur);
            logDirect("Использование: " + label + " <chatId> | " + label + " reset");
            return;
        }

        String a0 = args.getString().trim();
        args.requireMax(1);

        if (a0.equalsIgnoreCase("reset") || a0.equalsIgnoreCase("default")) {
            writeSavedChatId(DEFAULT_CHAT_ID);
            logDirect("Chat ID установлен: " + DEFAULT_CHAT_ID);
            return;
        }

        if (!isValidChatId(a0)) {
            logDirect("Неверный Chat ID. Пример: " + label + " 5514730234");
            return;
        }

        writeSavedChatId(a0);
        logDirect("Chat ID установлен: " + a0);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) return Stream.empty();
        String a0 = args.getString();

        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend(DEFAULT_CHAT_ID, "reset")
                    .filterPrefix(a0)
                    .stream();
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Установить Chat ID для TelegramBot";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Команда для установки Chat ID, который использует TelegramBot.",
                "",
                "Использование:",
                "> .tg 5514999234",
                "> .tg 1544477366",
                "> .tg reset"
        );
    }

    private static boolean isValidChatId(String s) {
        if (s == null) return false;
        String v = s.trim();
        if (v.isEmpty()) return false;

        int i = 0;
        if (v.charAt(0) == '-') {
            if (v.length() == 1) return false;
            i = 1;
        }

        for (; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < '0' || c > '9') return false;
        }

        return v.length() <= 32;
    }

    private static Path chatIdPath() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dir = mc.runDirectory.toPath().resolve("rich");
        return dir.resolve("telegram_chat_id.txt");
    }

    private static String readSavedChatId() {
        try {
            Path p = chatIdPath();
            if (!Files.exists(p)) return "";
            String s = Files.readString(p, StandardCharsets.UTF_8);
            return s == null ? "" : s.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void writeSavedChatId(String id) {
        try {
            Path p = chatIdPath();
            Files.createDirectories(p.getParent());
            String v = id == null ? "" : id.trim();
            Files.writeString(p, v, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable ignored) {
        }
    }
}