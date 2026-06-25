package fun.rich.features.impl.misc;

import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.Setting;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramBot extends Module {

    private static final String BOT_TOKEN = "8104231240:AAHLdoyim_r6ThPEAZPfdbumwXJ0546_u8Y";
    private static final String DEFAULT_CHAT_ID = "";

    private static final String SEP = "━━━━━━━━━━━━━━━━━━━━━━━";
    private static final String SEP_WIDE = "━━━━━━━━━━━━━━━━━━━━━━━━";

    private static final long WARN_COOLDOWN_MS = 30_000L;

    private static final Item FISH_SALMON = Items.SALMON;
    private static final Item FISH_COD = Items.COD;
    private static final Item FISH_PUFFER = Items.PUFFERFISH;
    private static final Item FISH_TROPICAL = Items.TROPICAL_FISH;

    private static final long PRICE_SALMON = 350L;
    private static final long PRICE_COD = 300L;
    private static final long PRICE_PUFFER = 1000L;
    private static final long PRICE_TROPICAL = 450L;

    private static final long EVENT_CMD_TIMEOUT_MS = 7_000L;
    private static final long EVENT_CMD_RETRY_MS = 2_500L;
    private static final long EVENT_CMD_MIN_GAP_MS = 2_500L;
    private static final long EVENT_CONTEXT_TTL_MS = 12_000L;

    private static final Pattern P_DELAY = Pattern.compile("До\\s+следующ(?:его|ей)\\s+(?:ивент|эвент|событи)[^0-9]{0,24}:?\\s*([0-9]{1,3})\\s*мин\\s*([0-9]{1,2})\\s*сек", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_STATUS = Pattern.compile("Начн(?:ё|е)тс[яь]\\s*через\\s*([0-9]{1,3})\\s*(?:мин|минут[аы]?)\\s*([0-9]{1,2})\\s*сек", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_SOON_MIN = Pattern.compile("Появ(?:и|е)тс[яь]\\s*уже\\s*через\\s*([0-9]{1,3})\\s*(?:мин|минут[аы]?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_NOT_ACTIVE = Pattern.compile("(?:ещ[её]\\s+не\\s+активирован|до\\s+активаци[ия])[^0-9\\-]*([0-9]{1,3})\\s*мин\\s*([0-9]{1,2})\\s*сек", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_OPEN = Pattern.compile("до\\s+открыти[яь]\\s*:?\\s*([0-9]{1,3})\\s*мин\\s*([0-9]{1,2})\\s*сек", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CLOSE = Pattern.compile("до\\s+закрыти[яь]\\s*:?\\s*(?:([0-9]{1,3})\\s*мин\\s*)?([0-9]{1,2})\\s*сек", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_LOOT = Pattern.compile("Уровень\\s+лута\\s*:\\s*([^\\n\\r]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_COORDS_ANY = Pattern.compile("(?:Координат[ыа]|координатах)\\s*:?\\s*\\[?\\s*(-?[0-9]{1,9})\\s+(-?[0-9]{1,9})\\s+(-?[0-9]{1,9})\\s*\\]?", Pattern.CASE_INSENSITIVE);

    private static final Pattern P_LIST_NAME = Pattern.compile("\\[[0-9]+]\\s*([^:\\n\\r]+?)(?:\\s*:|\\s*\\.|\\s*$)");
    private static final Pattern P_BRACKET_NAME = Pattern.compile("^\\[([^\\]]+)]");
    private static final Pattern P_WARP = Pattern.compile("/warp\\s*([A-Za-z0-9_\\-]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAB_NAME_PATTERN = Pattern.compile("^\\w{3,16}$");
    private static final List<String> TAB_DONATE_ORDER = Arrays.asList("Герцог", "Князь", "Принц", "Титан", "Элита", "Глава");

    private static final long TAB_SUCCESS_ARM_TTL_MS = 180_000L;
    private static final long TAB_MOD_AFTER_ARM_SLACK_MS = 2500L;

    private final BooleanSetting report = new HookedBooleanSetting("Отчет по AutoFish", "Отправлять отчет по автофишу в Telegram").setValue(true);

    private final SelectSetting interval = new SelectSetting("Интервал", "Частота отчета")
            .value("10 минут", "15 минут", "20 минут")
            .selected("10 минут");

    private final BooleanSetting eventReport = new BooleanSetting("Отчет об ивенте", "Отправлять информацию об ивентах в Telegram").setValue(true);

    private final BooleanSetting tabParserReport = new BooleanSetting("Отчет по Tab Parser", "Отправлять отчет и файл tabparser_results.txt в Telegram").setValue(true);

    private final Setting chatIdSetting;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private long scheduledIntervalMs;
    private long nextReportAt;
    private long lastWarnAt;

    private volatile String pendingSendError;
    private volatile long pendingSendErrorAt;

    private int[] lastReportSnap = new int[4];
    private int[] sessionFishAcc = new int[4];

    private String chatIdCache = DEFAULT_CHAT_ID;
    private long lastChatIdReloadAt;

    private long eventNextQueryAt;
    private boolean eventCmdInFlight;
    private int eventCmdAttempts;
    private long eventCmdTimeoutAt;
    private long eventLastCmdAt;

    private int lastDelayMin;
    private int lastDelaySec;
    private long lastDelayAt;

    private int delayCycleBaseSec;
    private boolean halfScheduledThisCycle;
    private boolean halfTriggeredThisCycle;
    private long halfQueryAt;
    private boolean pendingHalfDelayUpdate;

    private String activeBaseKey;
    private boolean activeNextAttached;
    private String activeNextTextLast;
    private long lastEventSendAt;

    private String lastSoonKey;
    private long lastSoonSendAt;

    private String lastDelayKey;
    private long lastDelaySendAt;

    private String ctxEventName = "";
    private String ctxEventLoot = "";
    private String ctxEventWarp = "";
    private String ctxEventOpenLine = "";
    private Integer ctxEventX;
    private Integer ctxEventY;
    private Integer ctxEventZ;
    private long ctxEventAt;

    private Field settingsListFieldCache;

    private long tabCheckAt;
    private long tabSeenMod;
    private long tabSeenSize;
    private long tabStableSince;
    private long tabLastSentMod;

    private boolean tabSuccessArmed;
    private long tabSuccessArmAt;
    private long tabRunId;
    private long tabArmedRunId;
    private long tabSentRunId;

    public TelegramBot() {
        super("TelegramBot", "Telegram Bot", ModuleCategory.MISC);

        chatIdSetting = tryCreateTextSetting("Chat ID", "ID чата/пользователя", DEFAULT_CHAT_ID);

        if (chatIdSetting != null) setup(report, interval, eventReport, tabParserReport, chatIdSetting);
        else setup(report, interval, eventReport, tabParserReport);

        reloadChatId(true);
        if (chatIdSetting != null) writeTextValue(chatIdSetting, chatIdCache);

        syncIntervalPresence(true);
    }

    @Override
    public void activate() {
        super.activate();
        resetState();

        reloadChatId(true);
        if (chatIdSetting != null) writeTextValue(chatIdSetting, chatIdCache);

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc.player;
        if (player != null) lastReportSnap = snapshotFish(player.getInventory());

        long now = System.currentTimeMillis();
        scheduledIntervalMs = intervalMs();
        nextReportAt = now + scheduledIntervalMs;

        eventNextQueryAt = now + 1200L;

        File f = getTabParserFile(mc);
        if (f != null && f.exists()) {
            tabLastSentMod = f.lastModified();
            tabSeenMod = tabLastSentMod;
            tabSeenSize = f.length();
            tabStableSince = now;
        }

        warnIfBadConfig(mc, now);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        resetState();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        long now = System.currentTimeMillis();

        reloadChatId(false);
        if (chatIdSetting != null && MinecraftClient.getInstance().currentScreen == null) {
            writeTextValue(chatIdSetting, chatIdCache);
        }

        flushAsyncErrorToChat(MinecraftClient.getInstance(), now);

        if (!isState()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        if (tabParserReport.isValue()) tickTabParserReport(mc, now);

        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        if (report.isValue()) tickFishReport(mc, player, now);
        if (eventReport.isValue()) tickEventReport(mc, player, now);
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState()) return;
        if (!eventReport.isValue() && !tabParserReport.isValue()) return;

        Object pkt = readPacketFromEvent(e);
        if (pkt == null) return;

        String cn = pkt.getClass().getName();
        if (cn.contains(".c2s.")) return;
        if (!cn.contains(".s2c.")) return;

        String raw = extractAnyText(pkt);
        if (raw == null || raw.isEmpty()) return;

        raw = stripFormatting(raw);
        if (raw.isEmpty()) return;

        long now = System.currentTimeMillis();
        String[] lines = raw.split("\\r?\\n");

        if (tabParserReport.isValue()) handleTabParserLines(lines, now);
        if (eventReport.isValue()) handleEventLines(lines, now);
    }

    private void handleTabParserLines(String[] lines, long now) {
        if (lines == null || lines.length == 0) return;

        boolean started = false;
        boolean finished = false;

        for (String line : lines) {
            String s = stripFormatting(line);
            if (s.isEmpty()) continue;

            String low = normalizeLow(s);

            if (!started && isTabParserStartLine(low)) started = true;
            if (!finished && isTabParserFinishLine(low)) finished = true;
        }

        if (started) {
            tabRunId++;
            if (tabRunId <= 0L) tabRunId = 1L;
            tabSuccessArmed = false;
            tabSuccessArmAt = 0L;
            tabArmedRunId = 0L;
        }

        if (finished) {
            if (tabRunId <= 0L) tabRunId = 1L;
            tabSuccessArmed = true;
            tabSuccessArmAt = now;
            tabArmedRunId = tabRunId;
        }
    }

    private static boolean isTabParserStartLine(String low) {
        if (low == null || low.isEmpty()) return false;
        if (low.contains("переключаюсь") && low.contains("сервер")) return true;
        if (low.contains("загружено") && (low.contains("запис") || low.contains("ник"))) return true;
        if (low.contains("парсинг") && (low.contains("запущ") || low.contains("начат") || low.contains("старт"))) return true;
        if (low.contains("tabparser") && (low.contains("запущ") || low.contains("start") || low.contains("начат"))) return true;
        return false;
    }

    private static boolean isTabParserFinishLine(String low) {
        if (low == null || low.isEmpty()) return false;
        if (low.contains("успешно") && low.contains("спарсил")) return true;
        if (low.contains("парсинг") && (low.contains("остановлен") || low.contains("заверш") || low.contains("окончен"))) return true;
        return false;
    }

    private void tickTabParserReport(MinecraftClient mc, long now) {
        if (mc == null) return;
        if (!isConfigured()) {
            warnIfBadConfig(mc, now);
            return;
        }

        if (tabSuccessArmed && now - tabSuccessArmAt > TAB_SUCCESS_ARM_TTL_MS) {
            tabSuccessArmed = false;
            tabSuccessArmAt = 0L;
            tabArmedRunId = 0L;
        }

        if (now - tabCheckAt < 1000L) return;
        tabCheckAt = now;

        File f = getTabParserFile(mc);
        if (f == null || !f.exists() || !f.isFile()) return;

        long mod = f.lastModified();
        long size = f.length();

        if (mod <= 0L || size <= 0L) return;
        if (mod <= tabLastSentMod) return;

        if (mod != tabSeenMod || size != tabSeenSize) {
            tabSeenMod = mod;
            tabSeenSize = size;
            tabStableSince = now;
            return;
        }

        if (now - tabStableSince < 1200L) return;
        if (!tabSuccessArmed) return;
        if (tabArmedRunId > 0L && tabSentRunId == tabArmedRunId) return;
        if (tabSuccessArmAt > 0L && mod + TAB_MOD_AFTER_ARM_SLACK_MS < tabSuccessArmAt) return;

        TabStats stats = parseTabParserFile(f);
        sendMessageAsync(buildTabParserReportText(stats));
        sendDocumentAsync(f, "tabparser_results.txt");

        tabLastSentMod = mod;
        tabSuccessArmed = false;
        tabSuccessArmAt = 0L;
        if (tabArmedRunId > 0L) tabSentRunId = tabArmedRunId;
        tabArmedRunId = 0L;
    }

    private static File getTabParserFile(MinecraftClient mc) {
        try {
            return new File(mc.runDirectory, "tabparser_results.txt");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static TabStats parseTabParserFile(File f) {
        TabStats st = new TabStats();
        for (String d : TAB_DONATE_ORDER) st.countByDonate.put(d, 0);

        String currentDonate = null;

        try (BufferedReader r = new BufferedReader(new FileReader(f, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) continue;

                String low = s.toLowerCase();

                if (s.startsWith("====") && low.contains("донат")) {
                    int idx = low.indexOf("донат");
                    String tail = "";
                    if (idx >= 0) tail = s.substring(idx + "донат".length()).replace("====", "").trim();
                    tail = tail.replace("ом", "").replace("ом:", "").replace("ом -", "").replace(":", "").trim();
                    currentDonate = tail.isEmpty() ? null : tail;
                    if (currentDonate != null && !st.countByDonate.containsKey(currentDonate)) st.countByDonate.put(currentDonate, 0);
                    continue;
                }

                if (currentDonate != null && TAB_NAME_PATTERN.matcher(s).matches()) {
                    st.total++;
                    st.countByDonate.put(currentDonate, st.countByDonate.getOrDefault(currentDonate, 0) + 1);
                }
            }
        } catch (Throwable ignored) {
        }

        return st;
    }

    private static String buildTabParserReportText(TabStats st) {
        StringBuilder sb = new StringBuilder(520);

        sb.append(SEP).append('\n');
        sb.append("Отчет по парсу донатеров ✅\n");
        sb.append(SEP).append("\n\n");

        sb.append("• Всего ников: ").append(st.total).append('\n');
        sb.append("• Файл: tabparser_results.txt\n\n");

        sb.append(SEP_WIDE).append('\n');

        for (String d : TAB_DONATE_ORDER) {
            int c = st.countByDonate.getOrDefault(d, 0);
            if ("Глава".equals(d) && c <= 0) continue;
            sb.append("• ").append(d).append(": ").append(c).append('\n');
        }

        sb.append(SEP_WIDE);
        return sb.toString().trim();
    }

    private void sendDocumentAsync(File file, String filename) {
        String token = safeStr(BOT_TOKEN);
        String chat = safeStr(resolveChatId());
        if (token.isEmpty() || token.equals("PUT_TOKEN_HERE")) return;
        if (chat.isEmpty()) return;
        if (file == null || !file.exists() || !file.isFile()) return;

        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length == 0) return;

            String boundary = "----RichBoundary" + System.nanoTime();
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length + 4096);

            writePart(out, boundary, "chat_id", null, chat.getBytes(StandardCharsets.UTF_8), "text/plain; charset=utf-8");

            String fn = filename == null || filename.isEmpty() ? file.getName() : filename;
            String cd = "form-data; name=\"document\"; filename=\"" + fn + "\"";
            writePart(out, boundary, "document", cd, bytes, "text/plain; charset=utf-8");

            out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] body = out.toByteArray();

            String url = "https://api.telegram.org/bot" + token + "/sendDocument";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenAccept(resp -> {
                int code = resp != null ? resp.statusCode() : -1;
                if (code != 200) {
                    String b = resp != null ? resp.body() : "";
                    b = b == null ? "" : b.replace('\n', ' ').replace('\r', ' ');
                    if (b.length() > 160) b = b.substring(0, 160);
                    pendingSendError = "Telegram HTTP " + code + (b.isEmpty() ? "" : (" | " + b));
                    pendingSendErrorAt = System.currentTimeMillis();
                }
            }).exceptionally(ex -> {
                pendingSendError = "Telegram error: " + (ex != null ? ex.getClass().getSimpleName() : "unknown");
                pendingSendErrorAt = System.currentTimeMillis();
                return null;
            });
        } catch (Throwable ignored) {
        }
    }

    private static void writePart(ByteArrayOutputStream out, String boundary, String name, String cdOverride, byte[] data, String ct) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String cd = cdOverride != null ? cdOverride : ("form-data; name=\"" + name + "\"");
        out.write(("Content-Disposition: " + cd + "\r\n").getBytes(StandardCharsets.UTF_8));
        if (ct != null && !ct.isEmpty()) out.write(("Content-Type: " + ct + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void tickFishReport(MinecraftClient mc, ClientPlayerEntity player, long now) {
        long desiredInterval = intervalMs();
        if (desiredInterval != scheduledIntervalMs) {
            scheduledIntervalMs = desiredInterval;
            nextReportAt = now + scheduledIntervalMs;
            lastReportSnap = snapshotFish(player.getInventory());
            return;
        }

        if (now < nextReportAt) return;

        if (!isConfigured()) {
            warnIfBadConfig(mc, now);
            nextReportAt = now + scheduledIntervalMs;
            lastReportSnap = snapshotFish(player.getInventory());
            return;
        }

        int[] cur = snapshotFish(player.getInventory());
        int[] diff = diffPositive(lastReportSnap, cur);

        if (sum(diff) > 0) {
            addAcc(sessionFishAcc, diff);
            sendMessageAsync(buildFishReportText(diff));
        }

        lastReportSnap = cur;
        nextReportAt = now + scheduledIntervalMs;
    }

    private void tickEventReport(MinecraftClient mc, ClientPlayerEntity player, long now) {
        if (!isConfigured()) {
            warnIfBadConfig(mc, now);
            return;
        }

        if (eventCmdInFlight && now > eventCmdTimeoutAt) {
            eventCmdInFlight = false;
            if (eventCmdAttempts < 3) eventNextQueryAt = now + EVENT_CMD_RETRY_MS;
            else {
                eventCmdAttempts = 0;
                eventNextQueryAt = now + 30_000L;
            }
        }

        if (halfQueryAt > 0L && now >= halfQueryAt && !halfTriggeredThisCycle) {
            halfTriggeredThisCycle = true;
            halfQueryAt = 0L;
            pendingHalfDelayUpdate = true;
            eventNextQueryAt = now;
        }

        if (now < eventNextQueryAt) return;
        if (eventCmdInFlight) return;
        if (now - eventLastCmdAt < EVENT_CMD_MIN_GAP_MS) return;

        sendEventDelayCommand(player);
        eventCmdInFlight = true;
        eventCmdTimeoutAt = now + EVENT_CMD_TIMEOUT_MS;
        eventLastCmdAt = now;
        eventCmdAttempts++;
        eventNextQueryAt = now + 60_000L;
    }

    private void handleEventLines(String[] lines, long now) {
        if (lines == null || lines.length == 0) return;
        if (packetHasMysteriousBeacon(lines)) return;

        String name = "";
        String loot = "";
        String warp = "";
        Integer x = null, y = null, z = null;
        String soon = "";
        String openLine = "";

        boolean hasDelay = false;
        int dMin = 0;
        int dSec = 0;

        for (String line : lines) {
            String s = stripFormatting(line);
            if (s.isEmpty()) continue;

            String low = normalizeLow(s);
            if (low.contains("призван игроком")) continue;
            if (isMysteriousBeaconLow(low)) continue;

            Matcher md = P_DELAY.matcher(s);
            if (md.find()) {
                Integer mm = parseIntOrNull(md.group(1));
                Integer ss = parseIntOrNull(md.group(2));
                if (mm != null && ss != null) {
                    hasDelay = true;
                    dMin = mm;
                    dSec = ss;
                }
                continue;
            }

            Matcher mList = P_LIST_NAME.matcher(s);
            if (mList.find()) {
                String n = cleanTail(mList.group(1));
                if (!n.isEmpty()) name = n;
            } else {
                Matcher mb = P_BRACKET_NAME.matcher(s);
                if (mb.find()) {
                    String inside = safeStr(mb.group(1));
                    if (!inside.isEmpty() && !inside.equalsIgnoreCase("Ивенты") && !isNumeric(inside)) name = inside;
                }
            }

            Matcher ml = P_LOOT.matcher(s);
            if (ml.find()) {
                String v = cleanTail(ml.group(1));
                if (!v.isEmpty()) loot = v;
            }

            Matcher mw = P_WARP.matcher(s);
            if (mw.find()) {
                String w = safeStr(mw.group(1));
                if (!w.isEmpty()) warp = "/warp " + w;
            }

            Matcher mo = P_OPEN.matcher(s);
            if (mo.find()) {
                Integer mm = parseIntOrNull(mo.group(1));
                Integer ss = parseIntOrNull(mo.group(2));
                if (mm != null && ss != null) openLine = "До открытия: " + mm + " мин " + ss + " сек";
            } else {
                Matcher mc2 = P_CLOSE.matcher(s);
                if (mc2.find()) {
                    Integer mm = parseIntOrNull(mc2.group(1));
                    Integer ss = parseIntOrNull(mc2.group(2));
                    if (ss != null) {
                        if (mm != null) openLine = "До закрытия: " + mm + " мин " + ss + " сек";
                        else openLine = "До закрытия: " + ss + " сек";
                    }
                }
            }

            String fixed = fixedWarpForEvent(name);
            if (!fixed.isEmpty()) warp = fixed;

            Matcher mc = P_COORDS_ANY.matcher(s);
            if (mc.find()) {
                x = parseIntOrNull(mc.group(1));
                y = parseIntOrNull(mc.group(2));
                z = parseIntOrNull(mc.group(3));
            }

            Matcher ms = P_STATUS.matcher(s);
            if (ms.find()) {
                Integer mm = parseIntOrNull(ms.group(1));
                Integer ss = parseIntOrNull(ms.group(2));
                if (mm != null && ss != null) soon = mm + " мин " + ss + " сек";
            } else {
                Matcher mn = P_SOON_MIN.matcher(s);
                if (mn.find()) {
                    Integer mm = parseIntOrNull(mn.group(1));
                    if (mm != null) soon = mm + " мин";
                } else {
                    Matcher ma = P_NOT_ACTIVE.matcher(s);
                    if (ma.find()) {
                        Integer mm = parseIntOrNull(ma.group(1));
                        Integer ss = parseIntOrNull(ma.group(2));
                        if (mm != null && ss != null) soon = mm + " мин " + ss + " сек";
                    }
                }
            }
        }

        if (hasDelay) {
            eventCmdInFlight = false;
            eventCmdAttempts = 0;

            lastDelayMin = dMin;
            lastDelaySec = dSec;
            lastDelayAt = now;

            int totalSec = Math.max(0, dMin * 60 + dSec);

            if (delayCycleBaseSec == 0) {
                delayCycleBaseSec = totalSec;
                halfScheduledThisCycle = false;
                halfTriggeredThisCycle = false;
            } else if (totalSec > delayCycleBaseSec + 30) {
                delayCycleBaseSec = totalSec;
                halfScheduledThisCycle = false;
                halfTriggeredThisCycle = false;
            }

            if (!halfScheduledThisCycle && !halfTriggeredThisCycle) {
                long remMs = (long) totalSec * 1000L;
                long halfMs = remMs / 2L;
                if (halfMs >= 60_000L) {
                    halfQueryAt = now + halfMs;
                    halfScheduledThisCycle = true;
                }
            }
        }

        if (isMysteriousBeaconLow(normalizeLow(name)) || isMysteriousBeaconLow(normalizeLow(ctxEventName))) return;

        String fixed = fixedWarpForEvent(name);
        if (!fixed.isEmpty()) warp = fixed;

        boolean packetHasEventDetail =
                !safeStr(name).isEmpty()
                        || !safeStr(loot).isEmpty()
                        || !safeStr(warp).isEmpty()
                        || !safeStr(openLine).isEmpty()
                        || !safeStr(soon).isEmpty()
                        || (x != null && y != null && z != null);

        if (packetHasEventDetail) {
            updateEventContext(name, loot, warp, openLine, x, y, z, now);
        }

        if (packetHasEventDetail && isEventContextFresh(now)) {
            if (safeStr(name).isEmpty()) name = ctxEventName;
            if (safeStr(loot).isEmpty()) loot = ctxEventLoot;
            if (safeStr(warp).isEmpty()) warp = ctxEventWarp;
            if (safeStr(openLine).isEmpty()) openLine = ctxEventOpenLine;
            if (x == null && y == null && z == null && ctxEventX != null && ctxEventY != null && ctxEventZ != null) {
                x = ctxEventX;
                y = ctxEventY;
                z = ctxEventZ;
            }
        }

        if (isMysteriousBeaconLow(normalizeLow(name)) || isMysteriousBeaconLow(normalizeLow(ctxEventName))) return;

        boolean hasCoords = x != null && y != null && z != null;
        boolean hasWarp = !safeStr(warp).isEmpty();
        boolean hasSoon = !safeStr(soon).isEmpty();
        String nextText = formatDelayIfFresh(now);

        if (!safeStr(name).isEmpty() && hasSoon) {
            String key = safeStr(name) + "|" + safeStr(loot) + "|" + safeStr(soon);
            if (!key.equals(lastSoonKey) && now - lastSoonSendAt > 3000L) {
                lastSoonKey = key;
                lastSoonSendAt = now;
                sendMessageAsync(buildEventSoonText(name, loot, soon));
            }
            return;
        }

        if (!safeStr(name).isEmpty() && (hasCoords || hasWarp)) {
            String whereKey = hasCoords ? ("xyz:" + x + " " + y + " " + z) : ("warp:" + warp);
            String baseKey = safeStr(name) + "|" + whereKey;

            boolean baseChanged = activeBaseKey == null || !activeBaseKey.equals(baseKey);

            if (baseChanged) {
                activeBaseKey = baseKey;
                activeNextAttached = false;
                activeNextTextLast = "";
                pendingHalfDelayUpdate = false;
                lastEventSendAt = 0L;
            }

            boolean canSend = now - lastEventSendAt > 1200L;

            if (baseChanged && canSend) {
                String whereText = hasCoords ? (x + " " + y + " " + z) : warp;
                sendMessageAsync(buildEventActiveText(name, loot, hasCoords, whereText, openLine, nextText));
                lastEventSendAt = now;
                if (!nextText.isEmpty()) {
                    activeNextAttached = true;
                    activeNextTextLast = nextText;
                }
                return;
            }

            if (!activeNextAttached && !nextText.isEmpty() && canSend) {
                String whereText = hasCoords ? (x + " " + y + " " + z) : warp;
                sendMessageAsync(buildEventActiveText(name, loot, hasCoords, whereText, openLine, nextText));
                lastEventSendAt = now;
                activeNextAttached = true;
                activeNextTextLast = nextText;
                pendingHalfDelayUpdate = false;
                return;
            }

            if (pendingHalfDelayUpdate && !nextText.isEmpty() && canSend) {
                if (!nextText.equals(activeNextTextLast)) {
                    String whereText = hasCoords ? (x + " " + y + " " + z) : warp;
                    sendMessageAsync(buildEventActiveText(name, loot, hasCoords, whereText, openLine, nextText));
                    lastEventSendAt = now;
                    activeNextAttached = true;
                    activeNextTextLast = nextText;
                }
                pendingHalfDelayUpdate = false;
                return;
            }

            if (pendingHalfDelayUpdate && canSend) {
                pendingHalfDelayUpdate = false;
            }

            return;
        }

        if (hasDelay) {
            String delayText = formatDelayIfFresh(now);
            if (!delayText.isEmpty()) {
                String key = delayText;
                if (!key.equals(lastDelayKey) && now - lastDelaySendAt > 120_000L) {
                    lastDelayKey = key;
                    lastDelaySendAt = now;
                    sendMessageAsync(buildEventDelayOnlyText(delayText));
                }
            }
        }
    }

    private void updateEventContext(String name, String loot, String warp, String openLine, Integer x, Integer y, Integer z, long now) {
        if (isMysteriousBeaconLow(normalizeLow(name))) return;

        boolean changed = false;

        if (!safeStr(name).isEmpty()) {
            ctxEventName = safeStr(name);
            changed = true;
        }

        if (!safeStr(loot).isEmpty()) {
            ctxEventLoot = safeStr(loot);
            changed = true;
        }

        if (!safeStr(warp).isEmpty()) {
            ctxEventWarp = safeStr(warp);
            changed = true;
        }

        if (!safeStr(openLine).isEmpty()) {
            ctxEventOpenLine = safeStr(openLine);
            changed = true;
        }

        if (x != null && y != null && z != null) {
            ctxEventX = x;
            ctxEventY = y;
            ctxEventZ = z;
            changed = true;
        }

        if (changed) ctxEventAt = now;
    }

    private boolean isEventContextFresh(long now) {
        return ctxEventAt > 0L && now - ctxEventAt <= EVENT_CONTEXT_TTL_MS;
    }

    private String formatDelayIfFresh(long now) {
        if (lastDelayAt <= 0L) return "";
        if (now - lastDelayAt > 25 * 60_000L) return "";
        return lastDelayMin + " мин " + lastDelaySec + " сек";
    }

    private static String fixedWarpForEvent(String name) {
        String n = safeStr(name).toLowerCase();
        if (n.isEmpty()) return "";
        if (n.equals("сундуки смерти") || n.equals("сундуки смерть")) return "/warp deatharena";
        if (n.equals("бикини боттом") || n.equals("bikini bottom") || n.equals("bikinibottom")) return "/warp BikiniBottom";
        if (n.equals("адская резня") || n.equals("адская резьня")) return "/warp portals";
        return "";
    }

    private static String buildEventActiveText(String name, String loot, boolean coords, String where, String openLine, String nextDelay) {
        StringBuilder sb = new StringBuilder(420);
        sb.append(SEP).append('\n');
        sb.append("Инфо об ивенте: Активен 🟢\n");
        sb.append(SEP).append("\n\n");
        sb.append("• Ивент - ").append(safeStr(name)).append('\n');
        if (coords) sb.append("• Координаты: ").append(safeStr(where)).append('\n');
        else sb.append("• Варп: ").append(safeStr(where)).append('\n');
        if (!safeStr(loot).isEmpty()) sb.append("• Лут: ").append(safeStr(loot)).append('\n');
        if (!safeStr(openLine).isEmpty()) sb.append("• ").append(safeStr(openLine)).append('\n');
        if (!safeStr(nextDelay).isEmpty()) {
            sb.append("\n").append(SEP_WIDE).append('\n');
            sb.append("💬 Следующий: ").append(safeStr(nextDelay)).append('\n');
            sb.append(SEP_WIDE);
        }
        return sb.toString().trim();
    }

    private static String buildEventSoonText(String name, String loot, String startsIn) {
        StringBuilder sb = new StringBuilder(320);
        sb.append(SEP).append('\n');
        sb.append("Инфо об ивенте: Скоро 🟡\n");
        sb.append(SEP).append("\n\n");
        sb.append("• Ивент - ").append(safeStr(name)).append('\n');
        if (!safeStr(loot).isEmpty()) sb.append("• Лут: ").append(safeStr(loot)).append('\n');
        sb.append("\n").append(SEP_WIDE).append('\n');
        sb.append("💬 Старт через: ").append(safeStr(startsIn)).append('\n');
        sb.append(SEP_WIDE);
        return sb.toString().trim();
    }

    private static String buildEventDelayOnlyText(String nextDelay) {
        StringBuilder sb = new StringBuilder(260);
        sb.append(SEP).append('\n');
        sb.append("Инфо об ивенте: Ожидание 🟡\n");
        sb.append(SEP).append("\n\n");
        sb.append("💬 До следующего ивента: ").append(safeStr(nextDelay)).append('\n');
        sb.append(SEP_WIDE);
        return sb.toString().trim();
    }

    private void sendEventDelayCommand(ClientPlayerEntity player) {
        if (player == null) return;
        Object nh;
        try {
            nh = player.networkHandler;
        } catch (Throwable ignored) {
            return;
        }
        if (nh == null) return;

        if (trySendChatCommand(nh, "event delay")) return;
        if (trySendChatCommand(nh, "events delay")) return;
        if (trySendChatMessage(nh, "/event delay")) return;
        if (trySendChatMessage(nh, "/events delay")) return;
        trySendPlayerMessage(player, "/event delay");
    }

    private static boolean trySendChatCommand(Object networkHandler, String cmdNoSlash) {
        try {
            Method m = networkHandler.getClass().getMethod("sendChatCommand", String.class);
            m.invoke(networkHandler, cmdNoSlash);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean trySendChatMessage(Object networkHandler, String msg) {
        try {
            Method m = networkHandler.getClass().getMethod("sendChatMessage", String.class);
            m.invoke(networkHandler, msg);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void trySendPlayerMessage(Object player, String msg) {
        try {
            Method m = player.getClass().getMethod("sendChatMessage", String.class);
            m.invoke(player, msg);
        } catch (Throwable ignored) {
        }
    }

    private void syncIntervalPresence(boolean force) {
        Field f = resolveSettingsListField();
        if (f == null) return;

        try {
            Object v = f.get(this);
            if (!(v instanceof List)) return;

            List<?> old = (List<?>) v;
            boolean has = old.contains(interval);
            boolean want = report.isValue();

            if (!force && want == has) return;
            if (want == has) return;

            ArrayList<Object> copy = new ArrayList<>(old.size() + 1);
            copy.addAll((List<?>) old);

            if (!want) {
                copy.remove(interval);
            } else {
                int idx = copy.indexOf(report);
                if (idx >= 0 && idx + 1 <= copy.size()) copy.add(idx + 1, interval);
                else copy.add(interval);
            }

            f.set(this, copy);
        } catch (Throwable ignored) {
        }
    }

    private Field resolveSettingsListField() {
        if (settingsListFieldCache != null) return settingsListFieldCache;

        Class<?> c = getClass();
        while (c != null && c != Object.class) {
            Field[] fs = c.getDeclaredFields();
            for (Field f : fs) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(this);
                    if (!(v instanceof List)) continue;
                    List<?> l = (List<?>) v;
                    if (l.contains(report) && l.contains(eventReport)) {
                        settingsListFieldCache = f;
                        return f;
                    }
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }

        return null;
    }

    private void resetState() {
        scheduledIntervalMs = 0L;
        nextReportAt = 0L;

        lastWarnAt = 0L;
        pendingSendError = null;
        pendingSendErrorAt = 0L;

        lastReportSnap = new int[4];
        sessionFishAcc = new int[4];

        lastChatIdReloadAt = 0L;

        eventNextQueryAt = 0L;
        eventCmdInFlight = false;
        eventCmdAttempts = 0;
        eventCmdTimeoutAt = 0L;
        eventLastCmdAt = 0L;

        lastDelayMin = 0;
        lastDelaySec = 0;
        lastDelayAt = 0L;

        delayCycleBaseSec = 0;
        halfScheduledThisCycle = false;
        halfTriggeredThisCycle = false;
        halfQueryAt = 0L;
        pendingHalfDelayUpdate = false;

        activeBaseKey = null;
        activeNextAttached = false;
        activeNextTextLast = "";
        lastEventSendAt = 0L;

        lastSoonKey = "";
        lastSoonSendAt = 0L;

        lastDelayKey = "";
        lastDelaySendAt = 0L;

        ctxEventName = "";
        ctxEventLoot = "";
        ctxEventWarp = "";
        ctxEventOpenLine = "";
        ctxEventX = null;
        ctxEventY = null;
        ctxEventZ = null;
        ctxEventAt = 0L;

        tabCheckAt = 0L;
        tabSeenMod = 0L;
        tabSeenSize = 0L;
        tabStableSince = 0L;
        tabLastSentMod = 0L;

        tabSuccessArmed = false;
        tabSuccessArmAt = 0L;
        tabRunId = 0L;
        tabArmedRunId = 0L;
        tabSentRunId = 0L;
    }

    private long intervalMs() {
        int min = 10;
        if (interval.isSelected("10 минут")) min = 10;
        else if (interval.isSelected("15 минут")) min = 15;
        else if (interval.isSelected("20 минут")) min = 20;
        return (long) min * 60_000L;
    }

    private boolean isConfigured() {
        String token = safeStr(BOT_TOKEN);
        if (token.isEmpty() || token.equals("PUT_TOKEN_HERE")) return false;
        return !safeStr(resolveChatId()).isEmpty();
    }

    private String resolveChatId() {
        String c = safeStr(chatIdCache);
        if (!c.isEmpty()) return c;
        return DEFAULT_CHAT_ID;
    }

    private void warnIfBadConfig(MinecraftClient mc, long now) {
        if (mc == null || mc.player == null) return;
        if (now - lastWarnAt < WARN_COOLDOWN_MS) return;
        lastWarnAt = now;

        String token = safeStr(BOT_TOKEN);
        if (token.isEmpty() || token.equals("PUT_TOKEN_HERE")) {
            mc.player.sendMessage(Text.literal("§c[TelegramBot] Впиши BOT_TOKEN в TelegramBot.java"), false);
            return;
        }

        if (safeStr(resolveChatId()).isEmpty()) {
            mc.player.sendMessage(Text.literal("§c[TelegramBot] Укажи Chat ID"), false);
        }
    }

    private void reloadChatId(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastChatIdReloadAt < 500L) return;
        lastChatIdReloadAt = now;

        String saved = safeStr(readSavedChatId());
        if (!saved.isEmpty()) {
            chatIdCache = saved;
            return;
        }

        if (chatIdCache == null || chatIdCache.isEmpty()) chatIdCache = DEFAULT_CHAT_ID;
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

    private static String escapeJson(String s) {
        String v = safeStr(s);
        StringBuilder sb = new StringBuilder(v.length() + 16);
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '\"') sb.append("\\\"");
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else sb.append(ch);
        }
        return sb.toString();
    }

    private void sendMessageAsync(String text) {
        String token = safeStr(BOT_TOKEN);
        String chat = safeStr(resolveChatId());
        if (token.isEmpty() || token.equals("PUT_TOKEN_HERE")) return;
        if (chat.isEmpty()) return;

        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        String body = "{\"chat_id\":\"" + escapeJson(chat) + "\",\"text\":\"" + escapeJson(safeStr(text)) + "\",\"disable_web_page_preview\":true}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        CompletableFuture<HttpResponse<String>> f = http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        f.thenAccept(resp -> {
            int code = resp != null ? resp.statusCode() : -1;
            if (code != 200) {
                String b = resp != null ? resp.body() : "";
                b = b == null ? "" : b.replace('\n', ' ').replace('\r', ' ');
                if (b.length() > 160) b = b.substring(0, 160);
                pendingSendError = "Telegram HTTP " + code + (b.isEmpty() ? "" : (" | " + b));
                pendingSendErrorAt = System.currentTimeMillis();
            }
        }).exceptionally(ex -> {
            pendingSendError = "Telegram error: " + (ex != null ? ex.getClass().getSimpleName() : "unknown");
            pendingSendErrorAt = System.currentTimeMillis();
            return null;
        });
    }

    private void flushAsyncErrorToChat(MinecraftClient mc, long now) {
        String err = pendingSendError;
        if (err == null || err.isEmpty()) return;
        if (now - pendingSendErrorAt > 12_000L) {
            pendingSendError = null;
            return;
        }
        if (now - lastWarnAt < WARN_COOLDOWN_MS) return;
        lastWarnAt = now;

        if (mc != null && mc.player != null) mc.player.sendMessage(Text.literal("§c[TelegramBot] " + err), false);
        pendingSendError = null;
    }

    private String buildFishReportText(int[] periodFish) {
        long minutes = Math.max(1L, scheduledIntervalMs / 60_000L);
        FishSummary p = computeFish(periodFish);

        int salmon = periodFish != null && periodFish.length > 0 ? periodFish[0] : 0;
        int cod = periodFish != null && periodFish.length > 1 ? periodFish[1] : 0;
        int puf = periodFish != null && periodFish.length > 2 ? periodFish[2] : 0;
        int trop = periodFish != null && periodFish.length > 3 ? periodFish[3] : 0;

        StringBuilder sb = new StringBuilder(320);

        sb.append(SEP).append('\n');
        sb.append("  [🔸] Auto Fish Bot [🔸] · ").append(minutes).append(" мин").append('\n');
        sb.append(SEP).append("\n\n");

        sb.append(" 🧾 Твой улов:\n\n");

        if (salmon > 0) sb.append("• 🐟 Лосось x").append(salmon).append('\n');
        if (cod > 0) sb.append("• 🐟 Треска x").append(cod).append('\n');
        if (puf > 0) sb.append("• 🐡 Иглобрюх x").append(puf).append('\n');
        if (trop > 0) sb.append("• 🐠 Тропическая x").append(trop).append('\n');

        sb.append('\n');
        sb.append(SEP_WIDE).append('\n');
        sb.append(" 💰 Прибыль      ").append(formatMoney(p.profit)).append(" $\n");
        sb.append(SEP_WIDE);

        return sb.toString().trim();
    }

    private static int[] snapshotFish(PlayerInventory inv) {
        int[] a = new int[4];
        if (inv == null) return a;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st == null || st.isEmpty()) continue;
            Item it = st.getItem();
            int c = st.getCount();

            if (it == FISH_SALMON) a[0] += c;
            else if (it == FISH_COD) a[1] += c;
            else if (it == FISH_PUFFER) a[2] += c;
            else if (it == FISH_TROPICAL) a[3] += c;
        }

        return a;
    }

    private static int[] diffPositive(int[] before, int[] after) {
        int[] d = new int[4];
        for (int i = 0; i < 4; i++) {
            int b = before != null && i < before.length ? before[i] : 0;
            int a = after != null && i < after.length ? after[i] : 0;
            int inc = a - b;
            d[i] = Math.max(0, inc);
        }
        return d;
    }

    private static void addAcc(int[] acc, int[] add) {
        for (int i = 0; i < 4; i++) {
            int v = add != null && i < add.length ? add[i] : 0;
            if (v > 0) acc[i] += v;
        }
    }

    private static int sum(int[] a) {
        int s = 0;
        if (a == null) return 0;
        for (int v : a) if (v > 0) s += v;
        return s;
    }

    private static FishSummary computeFish(int[] counts) {
        long fish = 0L;
        long profit = 0L;

        int salmon = counts != null && counts.length > 0 ? counts[0] : 0;
        int cod = counts != null && counts.length > 1 ? counts[1] : 0;
        int puf = counts != null && counts.length > 2 ? counts[2] : 0;
        int trop = counts != null && counts.length > 3 ? counts[3] : 0;

        if (salmon > 0) {
            fish += salmon;
            profit += (long) salmon * PRICE_SALMON;
        }
        if (cod > 0) {
            fish += cod;
            profit += (long) cod * PRICE_COD;
        }
        if (puf > 0) {
            fish += puf;
            profit += (long) puf * PRICE_PUFFER;
        }
        if (trop > 0) {
            fish += trop;
            profit += (long) trop * PRICE_TROPICAL;
        }

        return new FishSummary((int) Math.min(Integer.MAX_VALUE, fish), profit);
    }

    private static String formatMoney(long v) {
        boolean neg = v < 0;
        long x = neg ? -v : v;
        String s = Long.toString(x);
        StringBuilder sb = new StringBuilder(s.length() + s.length() / 3 + 2);
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            sb.append(s.charAt(i));
            c++;
            if (c == 3 && i != 0) {
                sb.append(' ');
                c = 0;
            }
        }
        if (neg) sb.append('-');
        return sb.reverse().toString();
    }

    private static final class FishSummary {
        final int fishCount;
        final long profit;

        FishSummary(int fishCount, long profit) {
            this.fishCount = fishCount;
            this.profit = profit;
        }
    }

    private static final class TabStats {
        int total;
        final Map<String, Integer> countByDonate = new HashMap<>();
    }

    private static Setting tryCreateTextSetting(String name, String desc, String def) {
        String[] classes = new String[]{
                "fun.rich.features.module.setting.implement.StringSetting",
                "fun.rich.features.module.setting.implement.TextSetting",
                "fun.rich.features.module.setting.implement.TextBoxSetting",
                "fun.rich.features.module.setting.implement.InputSetting"
        };

        for (String cn : classes) {
            try {
                Class<?> cls = Class.forName(cn);

                Object o;

                o = tryCtor(cls, new Class[]{String.class, String.class, String.class}, new Object[]{name, desc, def});
                if (o instanceof Setting) return (Setting) o;

                o = tryCtor(cls, new Class[]{String.class, String.class}, new Object[]{name, desc});
                if (o instanceof Setting) {
                    writeTextValue(o, def);
                    return (Setting) o;
                }

                o = tryCtor(cls, new Class[]{String.class}, new Object[]{name});
                if (o instanceof Setting) {
                    writeTextValue(o, def);
                    return (Setting) o;
                }

            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Object tryCtor(Class<?> cls, Class<?>[] sig, Object[] args) {
        try {
            Constructor<?> c = cls.getConstructor(sig);
            return c.newInstance(args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryCallNoArgs(Object o, String name) {
        try {
            Method m = o.getClass().getMethod(name);
            return m.invoke(o);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeTextValue(Object setting, String value) {
        if (setting == null) return;
        String v = value == null ? "" : value;

        if (tryCallSet(setting, "setValue", v)) return;
        if (tryCallSet(setting, "setString", v)) return;
        if (tryCallSet(setting, "setText", v)) return;
        if (tryCallSet(setting, "set", v)) return;
        if (tryCallSet(setting, "setInput", v)) return;
        if (tryCallSet(setting, "setCurrent", v)) return;
        if (tryCallSet(setting, "setCurrentText", v)) return;

        writeFieldString(setting, "value", v);
        writeFieldString(setting, "text", v);
        writeFieldString(setting, "string", v);
        writeFieldString(setting, "input", v);
        writeFieldString(setting, "current", v);
        writeFieldString(setting, "currentText", v);
    }

    private static boolean tryCallSet(Object o, String name, String v) {
        try {
            Method m = o.getClass().getMethod(name, String.class);
            m.invoke(o, v);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void writeFieldString(Object obj, String fieldName, String val) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            if (f.getType() == String.class) f.set(obj, val == null ? "" : val);
        } catch (Throwable ignored) {
        }
    }

    private static Object readPacketFromEvent(Object packetEvent) {
        if (packetEvent == null) return null;

        Object p = tryCallNoArgs(packetEvent, "getPacket");
        if (p != null) return p;

        p = tryCallNoArgs(packetEvent, "packet");
        if (p != null) return p;

        try {
            Field f = packetEvent.getClass().getDeclaredField("packet");
            f.setAccessible(true);
            return f.get(packetEvent);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String extractAnyText(Object packet) {
        if (packet == null) return null;

        Object o;

        o = tryCallNoArgs(packet, "content");
        String s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(packet, "message");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(packet, "getContent");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(packet, "getMessage");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(packet, "getText");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        try {
            Field[] fs = packet.getClass().getDeclaredFields();
            for (Field f : fs) {
                f.setAccessible(true);
                Object v = f.get(packet);
                s = unwrapTextOrString(v);
                if (s != null) return s;
                if (v != null) {
                    Object inner = tryCallNoArgs(v, "content");
                    s = unwrapTextOrString(inner);
                    if (s != null) return s;
                    inner = tryCallNoArgs(v, "getContent");
                    s = unwrapTextOrString(inner);
                    if (s != null) return s;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String unwrapTextOrString(Object o) {
        if (o == null) return null;

        if (o instanceof String) {
            String s = safeStr((String) o);
            return s.isEmpty() ? null : s;
        }

        if (o instanceof Text) {
            String s = safeStr(((Text) o).getString());
            return s.isEmpty() ? null : s;
        }

        Object inner = tryCallNoArgs(o, "getString");
        if (inner instanceof String) {
            String s = safeStr((String) inner);
            return s.isEmpty() ? null : s;
        }

        inner = tryCallNoArgs(o, "content");
        if (inner instanceof Text) {
            String s = safeStr(((Text) inner).getString());
            return s.isEmpty() ? null : s;
        }

        inner = tryCallNoArgs(o, "getContent");
        if (inner instanceof Text) {
            String s = safeStr(((Text) inner).getString());
            return s.isEmpty() ? null : s;
        }

        return null;
    }

    private static String stripFormatting(String s) {
        String v = safeStr(s);
        if (v.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '§') {
                i++;
                continue;
            }
            sb.append(ch);
        }
        return sb.toString().trim();
    }

    private static String normalizeLow(String s) {
        if (s == null || s.isEmpty()) return "";
        String v = s.toLowerCase();
        StringBuilder sb = new StringBuilder(v.length());
        boolean pendingSpace = false;

        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch == '\u00A0' || Character.isWhitespace(ch)) {
                pendingSpace = sb.length() > 0;
                continue;
            }
            if (pendingSpace) {
                sb.append(' ');
                pendingSpace = false;
            }
            sb.append(ch);
        }

        return sb.toString().trim();
    }

    private static boolean isMysteriousBeaconLow(String low) {
        String v = low == null ? "" : low;
        if (v.isEmpty()) return false;
        if (v.contains("загадоч") && v.contains("маяк")) return true;
        if (v.contains("myster") && v.contains("beacon")) return true;
        if (v.contains("mystic") && v.contains("beacon")) return true;
        return false;
    }

    private static boolean packetHasMysteriousBeacon(String[] lines) {
        if (lines == null || lines.length == 0) return false;
        for (String line : lines) {
            String s = stripFormatting(line);
            if (s.isEmpty()) continue;
            String low = normalizeLow(s);
            if (isMysteriousBeaconLow(low)) return true;
        }
        return false;
    }

    private static String cleanTail(String s) {
        String v = safeStr(s);
        if (v.isEmpty()) return "";
        while (!v.isEmpty()) {
            char c = v.charAt(v.length() - 1);
            if (c == '!' || c == '.' || c == ',' || c == ';' || c == ':' || c == ')' || c == ']' || c == '»')
                v = v.substring(0, v.length() - 1).trim();
            else break;
        }
        return v;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(safeStr(s));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isNumeric(String s) {
        String v = safeStr(s);
        if (v.isEmpty()) return false;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    private final class HookedBooleanSetting extends BooleanSetting {
        HookedBooleanSetting(String name, String desc) {
            super(name, desc);
        }

        @Override
        public BooleanSetting setValue(boolean value) {
            BooleanSetting r = super.setValue(value);
            syncIntervalPresence(false);
            return r;
        }
    }
}