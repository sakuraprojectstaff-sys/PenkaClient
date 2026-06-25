package fun.rich.display.screens.clickgui.components.implement.autobuy.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AutoBuySettingsManager {
    private static final Path FILE = Paths.get("config", "rich", "autobuy_settings.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static AutoBuySettingsManager instance;

    private final Map<String, SettingsData> settingsMap = new ConcurrentHashMap<>();

    private AutoBuySettingsManager() {
    }

    public static AutoBuySettingsManager getInstance() {
        if (instance == null) {
            instance = new AutoBuySettingsManager();
        }
        return instance;
    }

    public void saveSettings(String itemName, AutoBuyItemSettings settings) {
        if (itemName == null || settings == null) return;

        String k = norm(itemName);
        settingsMap.put(k, new SettingsData(
                settings.getBuyBelow(),
                settings.getSellAbove(),
                settings.getMinQuantity()
        ));
    }

    public void loadSettings(String itemName, AutoBuyItemSettings settings) {
        if (itemName == null || settings == null) return;

        SettingsData data = settingsMap.get(norm(itemName));
        if (data == null) return;

        settings.setBuyBelow(data.buyBelow);
        settings.setSellAbove(data.sellAbove);
        settings.setMinQuantity(data.minQuantity);
    }

    public boolean hasSettings(String itemName) {
        if (itemName == null) return false;
        return settingsMap.containsKey(norm(itemName));
    }

    public void clear() {
        settingsMap.clear();
    }

    public JsonObject saveToJson() {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, SettingsData> e : settingsMap.entrySet()) {
            SettingsData d = e.getValue();
            JsonObject itemJson = new JsonObject();
            itemJson.addProperty("buyBelow", d.buyBelow);
            itemJson.addProperty("sellAbove", d.sellAbove);
            itemJson.addProperty("minQuantity", d.minQuantity);
            json.add(e.getKey(), itemJson);
        }
        return json;
    }

    public void loadFromJson(JsonObject json) {
        settingsMap.clear();
        if (json == null) return;

        for (Map.Entry<String, JsonElement> e : json.entrySet()) {
            String key = norm(e.getKey());
            JsonElement el = e.getValue();
            if (el == null || !el.isJsonObject()) continue;

            JsonObject o = el.getAsJsonObject();
            int buyBelow = getInt(o, "buyBelow", 0);
            int sellAbove = getInt(o, "sellAbove", 0);
            int minQuantity = getInt(o, "minQuantity", 0);

            settingsMap.put(key, new SettingsData(buyBelow, sellAbove, minQuantity));
        }
    }

    public void saveToFile() {
        try {
            Files.createDirectories(FILE.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(saveToJson(), w);
            }
        } catch (IOException ignored) {
        }
    }

    public void loadFromFile() {
        if (!Files.exists(FILE)) return;

        try (BufferedReader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(r);
            if (root != null && root.isJsonObject()) {
                loadFromJson(root.getAsJsonObject());
            }
        } catch (IOException ignored) {
        } catch (Exception ignored) {
            settingsMap.clear();
        }
    }

    private static int getInt(JsonObject o, String key, int def) {
        try {
            if (o != null && o.has(key) && o.get(key) != null) return o.get(key).getAsInt();
        } catch (Exception ignored) {
        }
        return def;
    }

    private static String norm(String s) {
        return s.trim().toLowerCase();
    }

    private static final class SettingsData {
        final int buyBelow;
        final int sellAbove;
        final int minQuantity;

        SettingsData(int buyBelow, int sellAbove, int minQuantity) {
            this.buyBelow = buyBelow;
            this.sellAbove = sellAbove;
            this.minQuantity = minQuantity;
        }
    }
}
