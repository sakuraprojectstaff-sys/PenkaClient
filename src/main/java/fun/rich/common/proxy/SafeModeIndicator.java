package fun.rich.common.proxy;

import fun.rich.features.impl.misc.SelfDestruct;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class SafeModeIndicator {
    private static final String INSTANCE_ID = UUID.randomUUID().toString();
    private static final long WRITE_INTERVAL_MS = 500L;
    private static final long TTL_MS = 3000L;

    public static final String DISPLAY_ADDR = "127.0.0.1:25566";

    private static volatile long lastWriteMs = 0L;

    private SafeModeIndicator() {
    }

    public static void tick() {
        if (SelfDestruct.unhooked) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastWriteMs < WRITE_INTERVAL_MS) {
            return;
        }
        lastWriteMs = now;

        Path file = stateFile();
        if (file == null) {
            return;
        }

        if (Config.safeModeEnabled) {
            writeState(file, now);
            return;
        }

        deleteIfOwned(file);
    }

    public static boolean shouldShow() {
        if (SelfDestruct.unhooked) {
            return false;
        }
        if (Config.safeModeEnabled) {
            return true;
        }
        return isRemoteActive();
    }

    public static boolean isRemoteActive() {
        Path file = stateFile();
        if (file == null || !Files.exists(file)) {
            return false;
        }

        long now = System.currentTimeMillis();
        try {
            String s = Files.readString(file, StandardCharsets.UTF_8);
            String[] lines = s.split("\n");
            String owner = "";
            long updated = 0L;

            for (String line : lines) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                if (k.equals("owner")) {
                    owner = v;
                } else if (k.equals("updated")) {
                    try {
                        updated = Long.parseLong(v);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (owner.isEmpty() || updated <= 0L) {
                return false;
            }

            return now - updated <= TTL_MS;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void writeState(Path file, long now) {
        try {
            Files.createDirectories(file.getParent());
            String content = "owner=" + INSTANCE_ID + "\nupdated=" + now + "\n";
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void deleteIfOwned(Path file) {
        try {
            if (!Files.exists(file)) {
                return;
            }
            String s = Files.readString(file, StandardCharsets.UTF_8);
            if (!s.contains("owner=" + INSTANCE_ID)) {
                return;
            }
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static Path stateFile() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.runDirectory == null) {
            return null;
        }
        return mc.runDirectory.toPath()
                .resolve("Rich")
                .resolve("Proxy")
                .resolve("safe_mode_state.json");
    }
}