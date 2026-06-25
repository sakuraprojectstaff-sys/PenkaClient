package fun.rich.common.proxy;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

public class Config {
    private static final File CONFIG_DIR = new File(MinecraftClient.getInstance().runDirectory, "Rich/Proxy");
    private static final File CONFIG_FILE = new File(CONFIG_DIR, "Proxyconfig.json");

    public static HashMap<String, Proxy> accounts = new HashMap<>();
    public static String lastPlayerName = "";

    public static boolean safeModeEnabled = false;
    public static String safeLocalHost = "127.0.0.1";
    public static int safeLocalPort = 25566;

    public static void loadConfig() {
        try {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }

            if (!CONFIG_FILE.exists()) {
                if (!CONFIG_FILE.createNewFile()) {
                    System.out.println("Error creating Proxyconfig.json file");
                }
                saveConfig();
                return;
            }

            String configString = FileUtils.readFileToString(CONFIG_FILE, StandardCharsets.UTF_8);

            if (!configString.isEmpty()) {
                JsonObject configJson = JsonParser.parseString(configString).getAsJsonObject();

                if (configJson.has("proxy-enabled")) {
                    ProxyServer.proxyEnabled = configJson.get("proxy-enabled").getAsBoolean();
                }

                if (configJson.has("safe-mode")) {
                    safeModeEnabled = configJson.get("safe-mode").getAsBoolean();
                }

                if (configJson.has("safe-local-host")) {
                    String h = configJson.get("safe-local-host").getAsString();
                    if (h != null && !h.trim().isEmpty()) {
                        safeLocalHost = h.trim();
                    }
                }

                if (configJson.has("safe-local-port")) {
                    int p = configJson.get("safe-local-port").getAsInt();
                    if (p >= 1 && p <= 65535) {
                        safeLocalPort = p;
                    }
                }

                Type type = new TypeToken<HashMap<String, Proxy>>() {}.getType();
                if (configJson.has("accounts")) {
                    accounts = new Gson().fromJson(configJson.get("accounts"), type);
                }

                if (accounts == null) {
                    accounts = new HashMap<>();
                }

                if (accounts.containsKey("")) {
                    ProxyServer.proxy = accounts.get("");
                } else {
                    ProxyServer.proxy = new Proxy();
                }
            }
        } catch (Exception e) {
            System.out.println("Error reading Proxyconfig.json file");
            e.printStackTrace();
        }
    }

    public static void setDefaultProxy(Proxy proxy) {
        accounts.put("", proxy);
    }

    public static void saveConfig() {
        try {
            if (!CONFIG_DIR.exists()) {
                CONFIG_DIR.mkdirs();
            }

            JsonElement accountsJsonObject = new Gson().toJsonTree(accounts);

            JsonObject configJson = new JsonObject();
            configJson.addProperty("proxy-enabled", ProxyServer.proxyEnabled);
            configJson.addProperty("safe-mode", safeModeEnabled);
            configJson.addProperty("safe-local-host", safeLocalHost);
            configJson.addProperty("safe-local-port", safeLocalPort);
            configJson.add("accounts", accountsJsonObject);

            Gson gsonPretty = new GsonBuilder().setPrettyPrinting().create();
            FileUtils.write(CONFIG_FILE, gsonPretty.toJson(configJson), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Error writing Proxyconfig.json file");
            e.printStackTrace();
        }
    }
}