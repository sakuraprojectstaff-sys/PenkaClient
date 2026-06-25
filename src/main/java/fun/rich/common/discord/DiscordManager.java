package fun.rich.common.discord;

import antidaunleak.api.UserProfile;
import fun.rich.common.discord.utils.DiscordEventHandlers;
import fun.rich.common.discord.utils.DiscordRPC;
import fun.rich.common.discord.utils.DiscordRichPresence;
import fun.rich.common.discord.utils.RPCButton;
import fun.rich.utils.client.discord.Buffer;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.util.Identifier;

@Setter
@Getter
public class DiscordManager {
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final DiscordDaemonThread discordDaemonThread = new DiscordDaemonThread();

    private volatile boolean running = true;
    private DiscordInfo info = new DiscordInfo("Unknown", "", "");
    private long startedAtSeconds = 0L;

    private volatile Identifier avatarId;
    private volatile String avatarUrlToLoad = "";
    private volatile boolean avatarLoadQueued = false;
    private volatile boolean avatarLoading = false;
    private volatile long avatarLastAttemptMs = 0L;

    private String lastDetails = "";
    private String lastState = "";
    private String lastLargeImage = "";
    private String lastLargeHover = "";

    private static final String APP_ID = "1479832621193101402";
    private static final String LOGO_IMAGE = "https://i.postimg.cc/L5D5zbkN/gif.gif";

    private static final String SITE = "https://sakuralient.fun/";
    private static final String BTN_TG = "https://t.me/sakuralientnew";
    private static final String BTN_DS = "https://discord.gg/zYctK14mjZZ";

    public void init() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("linux")) return;

        DiscordEventHandlers handlers = new DiscordEventHandlers.Builder()
                .ready((user) -> {
                    String url = "https://cdn.discordapp.com/avatars/" + user.userId + "/" + user.avatar + ".png";
                    setInfo(new DiscordInfo(user.username, url, user.userId));

                    avatarUrlToLoad = url;
                    avatarLoadQueued = true;

                    if (startedAtSeconds == 0L) startedAtSeconds = System.currentTimeMillis() / 1000L;

                    pushPresence(true);
                })
                .build();

        try {
            DiscordRPC.INSTANCE.Discord_Initialize(APP_ID, handlers, true, "");
        } catch (Throwable ignored) {
            return;
        }

        discordDaemonThread.setDaemon(true);
        discordDaemonThread.start();
    }

    public void stopRPC() {
        running = false;
        try {
            discordDaemonThread.interrupt();
        } catch (Throwable ignored) {
        }
        try {
            DiscordRPC.INSTANCE.Discord_Shutdown();
        } catch (Throwable ignored) {
        }
    }

    public Identifier getAvatarId() {
        if (avatarId == null) {
            tryLoadAvatarLazy();
        }
        return avatarId;
    }

    private void tryLoadAvatarLazy() {
        if (!avatarLoadQueued) return;
        if (avatarLoading) return;

        long now = System.currentTimeMillis();
        if (now - avatarLastAttemptMs < 5000L) return;
        avatarLastAttemptMs = now;

        if (MC == null || MC.player == null || MC.world == null) return;

        String url = avatarUrlToLoad;
        if (url == null || url.isEmpty()) {
            avatarLoadQueued = false;
            return;
        }

        avatarLoading = true;
        avatarLoadQueued = false;

        try {
            MC.execute(() -> {
                try {
                    avatarId = Buffer.registerDynamicTexture("avatar-", Buffer.getHeadFromURL(url));
                } catch (Throwable ignored) {
                    avatarLoadQueued = true;
                } finally {
                    avatarLoading = false;
                }
            });
        } catch (Throwable ignored) {
            avatarLoading = false;
            avatarLoadQueued = true;
        }
    }

    private void pushPresence(boolean force) {
        String username = safeProfile("username", "Unknown");
        String uid = safeProfile("uid", "0");

        boolean playing = isPlaying();
        String server = serverName();

        String details = "User: " + username + " • UID: " + uid;
        String state = playing ? ("Server: " + server + ipSuffix(server)) : "Server: Main Menu";

        String largeImage = LOGO_IMAGE;
        String largeHover = playing ? ("Playing " + server) : SITE;

        if (!force
                && details.equals(lastDetails)
                && state.equals(lastState)
                && largeImage.equals(lastLargeImage)
                && largeHover.equals(lastLargeHover)) {
            return;
        }

        lastDetails = details;
        lastState = state;
        lastLargeImage = largeImage;
        lastLargeHover = largeHover;

        DiscordRichPresence rp = new DiscordRichPresence.Builder()
                .setStartTimestamp(startedAtSeconds == 0L ? (System.currentTimeMillis() / 1000L) : startedAtSeconds)
                .setDetails(details)
                .setState(state)
                .setLargeImage(largeImage, largeHover)
                .setButtons(
                        RPCButton.create("Телеграм", BTN_TG),
                        RPCButton.create("Дискорд", BTN_DS)
                )
                .build();

        try {
            DiscordRPC.INSTANCE.Discord_UpdatePresence(rp);
        } catch (Throwable ignored) {
        }
    }

    private String ipSuffix(String serverName) {
        try {
            if (MC == null) return "";
            if (MC.isInSingleplayer()) return "";
            ServerInfo s = MC.getCurrentServerEntry();
            if (s == null || s.address == null || s.address.isEmpty()) return "";
            String ip = s.address.toLowerCase();

            if ("FunTime".equals(serverName)
                    || "SpookyTime".equals(serverName)
                    || "HolyWorld".equals(serverName)
                    || "ReallyWorld".equals(serverName)
                    || "SkyTime".equals(serverName)
                    || "FunSky".equals(serverName)
                    || "MetaHvH".equals(serverName)) {
                return "";
            }

            return " (" + ip + ")";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private boolean isPlaying() {
        if (MC == null) return false;
        if (MC.player == null || MC.world == null) return false;
        if (MC.isInSingleplayer()) return true;
        ServerInfo s = MC.getCurrentServerEntry();
        return s != null && s.address != null && !s.address.isEmpty();
    }

    private String serverName() {
        if (MC == null) return "Unknown";
        if (MC.isInSingleplayer()) return "Singleplayer";

        ServerInfo s = MC.getCurrentServerEntry();
        String ip = s != null && s.address != null ? s.address.toLowerCase() : "";
        if (ip.isEmpty()) return "Unknown";

        if (ip.contains("funtime")) return "FunTime";
        if (ip.contains("spooky")) return "SpookyTime";
        if (ip.contains("holyworld") || ip.contains("hw")) return "HolyWorld";
        if (ip.contains("reallyworld") || ip.contains("rw")) return "ReallyWorld";
        if (ip.contains("skytime")) return "SkyTime";
        if (ip.contains("funsky")) return "FunSky";
        if (ip.contains("metahvh")) return "MetaHvH";

        return ip;
    }

    private String safeProfile(String key, String def) {
        try {
            String v = String.valueOf(UserProfile.getInstance().profile(key));
            if (v == null || v.isEmpty() || "null".equalsIgnoreCase(v)) return def;
            return v;
        } catch (Throwable ignored) {
            return def;
        }
    }

    private class DiscordDaemonThread extends Thread {
        @Override
        public void run() {
            setName("Discord-RPC");

            long lastUpdateMs = 0L;

            while (running) {
                try {
                    DiscordRPC.INSTANCE.Discord_RunCallbacks();
                } catch (Throwable ignored) {
                }

                long now = System.currentTimeMillis();
                if (now - lastUpdateMs >= 1500L) {
                    lastUpdateMs = now;
                    pushPresence(false);
                }

                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public record DiscordInfo(String userName, String avatarUrl, String userId) {}
}