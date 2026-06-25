package fun.rich.utils.client.chat;

import fun.rich.utils.client.text.TextHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public class ChatMessage {

    private static final Style TAG = Style.EMPTY.withColor(TextColor.fromRgb(0xB3A0FC)).withItalic(false);
    private static final Style ARROW = Style.EMPTY.withColor(Formatting.GRAY).withItalic(false);

    private static MutableText tag(String name) {
        return Text.empty()
                .append(Text.literal("[ ").setStyle(TAG))
                .append(Text.literal(name).setStyle(TAG))
                .append(Text.literal(" ]").setStyle(TAG));
    }

    public static MutableText brandmessage() {
        return tag("Sakura");
    }

    public static MutableText blockesp() {
        return tag("Block Esp");
    }

    public static MutableText autobuy() {
        return tag("Auto Buy");
    }

    public static MutableText autobuiparcer() {
        return tag("Parce price");
    }

    public static void brandmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = brandmessage().copy().append(Text.literal(" -> ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ancientmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("Ancient Xray").copy().append(Text.literal(" -> ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void helpmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("Help").copy().append(Text.literal(" -> ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void swapmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("AutoSwap").copy().append(Text.literal(" -> ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ircmessage(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("IRC").copy().append(Text.literal(" ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ircmessageWithGreen(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("IRC").copy().append(Text.literal(" ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message).setStyle(Style.EMPTY.withColor(Formatting.GREEN).withItalic(false)));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static void ircmessageWithRed(String message) {
        if (MinecraftClient.getInstance().player != null) {
            Text prefix = tag("IRC").copy().append(Text.literal(" ").setStyle(ARROW));
            Text formattedMessage = prefix.copy().append(Text.literal(message).setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(false)));
            MinecraftClient.getInstance().player.sendMessage(formattedMessage, false);
        }
    }

    public static Text ircprefixDeveloper(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Developer ", "dark_red_bright_red", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixCurator(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Куратор ", "dark_red", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixYouTube(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("YouTube ", "red_white", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixPikmi(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Пикми ", "purple_bright_pink", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixLabuba(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Лабуба ", "pink_dark_pink", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixZapen(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Запен ", "bright_red", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixBoost(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Буст ", "dark_green_bright_green", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixRich(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Рич ", "red_orange", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixPanda(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Панда ", "white_black", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixSmiley(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("(●'◡'●) ", "turquoise_blue", true);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixBibi(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Биби...! ", "cyan_orange_fade", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixBenena(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Бэнена ", "yellow_cyan", false);
        return prefix.copy().append(Text.literal(message));
    }

    public static Text ircprefixBlyabuba(String message) {
        Text prefix = TextHelper.applyPredefinedGradient("Блябуба ", "purple_red_fade", false);
        return prefix.copy().append(Text.literal(message));
    }
}
