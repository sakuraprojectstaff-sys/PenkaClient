package fun.rich.features.impl.misc;

import fun.rich.events.packet.PacketEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.List;
import java.util.regex.Pattern;

public class AutoAuth extends Module {

    private static final Pattern REGISTER_HINT = Pattern.compile(
            "(?:^|\\s)(?:/reg(?:ister)?\\b|/register\\b|register\\b|зарегистрируй(?:тесь|ся)?|регист(?:рация)?)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern LOGIN_HINT = Pattern.compile(
            "(?:^|\\s)(?:/l(?:ogin)?\\b|/login\\b|login\\b|авторизуй(?:тесь|ся)?|войдите|логин|вход)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern MC_FORMATTING = Pattern.compile("§[0-9A-FK-ORa-fk-or]");

    private final SelectSetting mode = new SelectSetting("Режим", "Выберите режим")
            .value("Custom", "Random")
            .selected("Custom");

    private final SliderSettings randomLength = new SliderSettings("Длина пароля", "Длина случайного пароля")
            .setValue(12.0f).range(6.0f, 32.0f)
            .visible(() -> mode.isSelected("Random"));

    private final BooleanSetting autoDisable = new BooleanSetting("Выключать после", "Выключать модуль после успешной команды")
            .setValue(true);

    private String lastHandledKey = "";
    private long lastHandledAtMs = 0L;

    public AutoAuth() {
        super("AutoAuth", ModuleCategory.MISC);
        setup(mode, randomLength, autoDisable);
    }

    @Override
    public void activate() {
        super.activate();
        lastHandledKey = "";
        lastHandledAtMs = 0L;
    }

    @Override
    public void deactivate() {
        lastHandledKey = "";
        lastHandledAtMs = 0L;
        super.deactivate();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e == null) return;
        if (e.isSend()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return;

        Packet<?> p = e.getPacket();
        if (p == null) return;

        String msg = extractChatMessage(p);
        if (msg == null || msg.isEmpty()) return;

        msg = normalize(msg);

        long now = System.currentTimeMillis();
        if (now - lastHandledAtMs < 700L) return;

        if (REGISTER_HINT.matcher(msg).find()) {
            String key = "reg|" + msg;
            if (!key.equals(lastHandledKey)) {
                lastHandledKey = key;
                lastHandledAtMs = now;
                doRegister(msg);
            }
            return;
        }

        if (LOGIN_HINT.matcher(msg).find()) {
            String key = "login|" + msg;
            if (!key.equals(lastHandledKey)) {
                lastHandledKey = key;
                lastHandledAtMs = now;
                doLogin(msg);
            }
        }
    }

    private void doRegister(String serverMsg) {
        String pass = getPasswordForReg();
        if (pass.isEmpty()) {
            printClient("AutoAuth: пароль пустой. Укажи в rich/autoauth.txt (1-я строка пароль).");
            if (autoDisable.isValue()) setState(false);
            return;
        }

        String cmd = serverMsg != null && serverMsg.contains("/register") ? "/register" : "/reg";
        sendChat(cmd + " " + pass + " " + pass);

        if (autoDisable.isValue()) setState(false);
    }

    private void doLogin(String serverMsg) {
        String pass = getPasswordForLogin();
        if (pass.isEmpty()) {
            printClient("AutoAuth: пароль пустой. Укажи в rich/autoauth.txt (1-я строка пароль, 2-я строка логин-пароль опционально).");
            if (autoDisable.isValue()) setState(false);
            return;
        }

        String cmd = "/login";
        if (serverMsg != null && serverMsg.contains("/l ")) cmd = "/l";
        sendChat(cmd + " " + pass);

        if (autoDisable.isValue()) setState(false);
    }

    private String getPasswordForReg() {
        PassPair pp = readOrGeneratePasswords();
        return pp == null ? "" : pp.reg;
    }

    private String getPasswordForLogin() {
        PassPair pp = readOrGeneratePasswords();
        return pp == null ? "" : pp.login;
    }

    private PassPair readOrGeneratePasswords() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return null;

            File dir = new File(mc.runDirectory, "rich");
            dir.mkdirs();

            File f = new File(dir, "autoauth.txt");
            String reg = "";
            String login = "";

            if (f.exists()) {
                List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (!lines.isEmpty()) reg = safeTrim(lines.get(0));
                if (lines.size() > 1) login = safeTrim(lines.get(1));
            }

            if (mode.isSelected("Random")) {
                if (reg.isEmpty()) {
                    int len = clamp((int) randomLength.getValue(), 6, 32);
                    reg = generateRandomPassword(len);
                    Files.write(f.toPath(), List.of(reg), StandardCharsets.UTF_8);
                    printClient("AutoAuth: пароль сгенерен и сохранён в rich/autoauth.txt");
                }
                login = reg;
            } else {
                if (login.isEmpty()) login = reg;
            }

            reg = safeTrim(reg);
            login = safeTrim(login);

            return new PassPair(reg, login);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void sendChat(String text) {
        if (text == null || text.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.getNetworkHandler() == null) return;

        try {
            if (text.startsWith("/")) {
                mc.getNetworkHandler().sendChatCommand(text.substring(1));
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            mc.getNetworkHandler().sendChatMessage(text);
        } catch (Throwable ignored) {
        }
    }

    private void printClient(String message) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.inGameHud != null && mc.inGameHud.getChatHud() != null) {
                mc.inGameHud.getChatHud().addMessage(Text.of(message));
            }
        } catch (Throwable ignored) {
        }
    }

    private static String extractChatMessage(Packet<?> packet) {
        try {
            String name = packet.getClass().getName();

            if (name.endsWith("GameMessageS2CPacket")) {
                Text t = (Text) invokeNoArgs(packet, "content");
                if (t == null) t = (Text) invokeNoArgs(packet, "message");
                if (t != null) return t.getString();
            }

            if (name.endsWith("ChatMessageS2CPacket")) {
                Object unsigned = invokeNoArgs(packet, "unsignedContent");
                if (unsigned instanceof Text ut) return ut.getString();

                Object body = invokeNoArgs(packet, "body");
                if (body != null) {
                    Object content = invokeNoArgs(body, "content");
                    if (content instanceof String s) return s;
                    if (content instanceof Text t) return t.getString();
                }

                Text t = (Text) invokeNoArgs(packet, "message");
                if (t != null) return t.getString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeNoArgs(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            return m.invoke(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalize(String s) {
        s = MC_FORMATTING.matcher(s).replaceAll("");
        s = s.replace('\n', ' ').replace('\r', ' ');
        s = s.trim().toLowerCase();
        while (s.contains("  ")) s = s.replace("  ", " ");
        return s;
    }

    private static String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        return Math.min(v, max);
    }

    private static String generateRandomPassword(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    private static final class PassPair {
        final String reg;
        final String login;

        PassPair(String reg, String login) {
            this.reg = reg;
            this.login = login;
        }
    }
}