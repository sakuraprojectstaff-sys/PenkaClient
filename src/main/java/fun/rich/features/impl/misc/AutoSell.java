package fun.rich.features.impl.misc;

import fun.rich.events.packet.PacketEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ItemBooleanSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.Packet;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoSell extends Module {
    static volatile AutoSell INSTANCE;
    static volatile boolean WORKER_STARTED;

    static final int INF_PRICE = Integer.MAX_VALUE / 4;
    static final int TRACKED_MAX_LEVEL = 5;

    final ItemBooleanSetting sellUnbreaking5 = new ItemBooleanSetting("Прочность V", "Продавать книги с Прочность V", Items.ENCHANTED_BOOK).setValue(false);
    final ItemBooleanSetting sellSharpness5 = new ItemBooleanSetting("Острота V", "Продавать книги с Острота V", Items.ENCHANTED_BOOK).setValue(false);
    final ItemBooleanSetting sellProtection5 = new ItemBooleanSetting("Защита V", "Продавать книги с Защита V", Items.ENCHANTED_BOOK).setValue(false);
    final ItemBooleanSetting sellSilkTouch = new ItemBooleanSetting("Шелковое касание", "Продавать книги с Шелковое касание", Items.ENCHANTED_BOOK).setValue(false);

    final SliderSettings unbreakingPrice = new SliderSettings("Цена Прочность V", "Цена продажи для Прочность V")
            .setValue(1000f).range(1f, 5000000f)
            .visible(sellUnbreaking5::isValue);

    final SliderSettings sharpnessPrice = new SliderSettings("Цена Острота V", "Цена продажи для Острота V")
            .setValue(1000f).range(1f, 5000000f)
            .visible(sellSharpness5::isValue);

    final SliderSettings protectionPrice = new SliderSettings("Цена Защита V", "Цена продажи для Защита V")
            .setValue(1000f).range(1f, 5000000f)
            .visible(sellProtection5::isValue);

    final SliderSettings silkTouchPrice = new SliderSettings("Цена Шелковое касание", "Цена продажи для Шелковое касание")
            .setValue(1000f).range(1f, 5000000f)
            .visible(sellSilkTouch::isValue);

    final SliderSettings sellDelayMs = new SliderSettings("Задержка (ms)", "Пауза между продажами")
            .setValue(1000f).range(200f, 5000f);

    final BooleanSetting logChat = new BooleanSetting("Логи в чат", "Отладочные сообщения").setValue(false).visible(() -> false);
    final BooleanSetting restoreSlot = new BooleanSetting("Вернуть слот", "Возвращать прошлый слот хотбара").setValue(true).visible(() -> false);

    long nextAllowedAtMs;
    int phase;
    long phaseStartedAtMs;

    int prevSelectedSlot;
    int workHotbarSlot;
    int originalInvSlot;
    boolean swappedFromInventory;

    BookKind pendingKind;

    ParserState parserState = ParserState.IDLE;
    final List<BookKind> parserQueue = new ArrayList<>();
    final int[] parserDirectPrices = new int[TRACKED_MAX_LEVEL + 1];
    int parserIndex;
    BookKind parserKind;
    int parserCurrentPage;
    int parserTotalPages;
    int parserBestPrice;
    int parserWaitAttempts;
    int parserSearchVariant;
    long parserStateStartedAtMs;
    String parserTitleBeforePage = "";
    float parserPercent;
    boolean parserAnnounce;
    int parserScannedKinds;
    int parserUpdatedKinds;

    static final Pattern PAGE_PATTERN = Pattern.compile("\\[(\\d+)/(\\d+)]");
    static final int MAX_PAGES_TO_SCAN = 3;
    static final int MAX_AUCTION_WAIT_ATTEMPTS = 28;

    static Method NH_SEND_CHAT_MESSAGE;
    static Method NH_SEND_CHAT_COMMAND;
    static Method IM_SYNC_SELECTED_SLOT;

    static {
        try {
            Class<?> nh = Class.forName("net.minecraft.client.network.ClientPlayNetworkHandler");
            try {
                NH_SEND_CHAT_MESSAGE = nh.getDeclaredMethod("sendChatMessage", String.class);
                NH_SEND_CHAT_MESSAGE.setAccessible(true);
            } catch (Throwable ignored) {
                NH_SEND_CHAT_MESSAGE = null;
            }
            try {
                NH_SEND_CHAT_COMMAND = nh.getDeclaredMethod("sendChatCommand", String.class);
                NH_SEND_CHAT_COMMAND.setAccessible(true);
            } catch (Throwable ignored) {
                NH_SEND_CHAT_COMMAND = null;
            }
        } catch (Throwable ignored) {
            NH_SEND_CHAT_MESSAGE = null;
            NH_SEND_CHAT_COMMAND = null;
        }

        try {
            Class<?> im = Class.forName("net.minecraft.client.network.ClientPlayerInteractionManager");
            try {
                IM_SYNC_SELECTED_SLOT = im.getDeclaredMethod("syncSelectedSlot");
                IM_SYNC_SELECTED_SLOT.setAccessible(true);
            } catch (Throwable ignored) {
                IM_SYNC_SELECTED_SLOT = null;
            }
        } catch (Throwable ignored) {
            IM_SYNC_SELECTED_SLOT = null;
        }
    }

    public AutoSell() {
        super("AutoSell", "Auto Sell", ModuleCategory.MISC);
        INSTANCE = this;
        setup(
                sellUnbreaking5, sellSharpness5, sellProtection5, sellSilkTouch,
                unbreakingPrice, sharpnessPrice, protectionPrice, silkTouchPrice,
                sellDelayMs,
                logChat, restoreSlot
        );
        resetLogic();
        ensureWorker();
    }

    public static AutoSell getInstance() {
        return INSTANCE;
    }

    public boolean isParsing() {
        return parserState != ParserState.IDLE;
    }

    public String startManualParse(float percent) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) {
            return "Нужно быть в мире";
        }
        if (!anyEnabled()) {
            return "Включи хотя бы одну книгу в AutoSell";
        }
        if (parserState != ParserState.IDLE) {
            return "Парсер уже работает";
        }

        parserPercent = clamp(percent, 10f, 200f);
        parserAnnounce = true;
        parserScannedKinds = 0;
        parserUpdatedKinds = 0;
        startParser(System.currentTimeMillis());

        if (parserState == ParserState.IDLE) {
            return "Не удалось запустить парсер";
        }

        return "Парсер AutoSell запущен";
    }

    public String stopManualParse() {
        if (parserState == ParserState.IDLE) {
            return "Парсер не запущен";
        }
        parserAnnounce = false;
        finishParser(System.currentTimeMillis());
        return "Парсер остановлен";
    }

    public String getParserStatus() {
        if (parserState == ParserState.IDLE) {
            return "Парсер не работает";
        }
        if (parserKind == null) {
            return "Парсер готовится";
        }

        String best = parserBestPrice < INF_PRICE ? ", лучший: " + formatPrice(parserBestPrice) : "";
        return "Сейчас: " + kindLabel(parserKind) + " " + parserCurrentPage + "/" + parserTotalPages + " [" + parserState.name().toLowerCase(Locale.ROOT) + "]" + best;
    }

    void ensureWorker() {
        if (WORKER_STARTED) return;
        WORKER_STARTED = true;

        Thread worker = new Thread(() -> {
            while (true) {
                try {
                    AutoSell self = INSTANCE;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (self != null && mc != null) {
                        mc.execute(() -> {
                            AutoSell s = INSTANCE;
                            if (s != null) {
                                s.workerStep(mc);
                            }
                        });
                    }
                    Thread.sleep(50L);
                } catch (Throwable ignored) {
                    try {
                        Thread.sleep(100L);
                    } catch (InterruptedException ignored2) {
                    }
                }
            }
        }, "AutoSell-Worker");

        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void activate() {
        super.activate();
        resetLogic();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        resetLogic();
    }

    void resetLogic() {
        nextAllowedAtMs = 0L;
        phase = 0;
        phaseStartedAtMs = 0L;
        prevSelectedSlot = 0;
        workHotbarSlot = 0;
        originalInvSlot = -1;
        swappedFromInventory = false;
        pendingKind = null;

        parserState = ParserState.IDLE;
        parserQueue.clear();
        parserIndex = 0;
        parserKind = null;
        parserCurrentPage = 1;
        parserTotalPages = 1;
        parserBestPrice = INF_PRICE;
        parserWaitAttempts = 0;
        parserSearchVariant = 0;
        parserStateStartedAtMs = 0L;
        parserTitleBeforePage = "";
        parserPercent = 100f;
        parserAnnounce = false;
        parserScannedKinds = 0;
        parserUpdatedKinds = 0;
        resetParserDirectPrices();
    }

    void resetParserDirectPrices() {
        Arrays.fill(parserDirectPrices, INF_PRICE);
    }

    void workerStep(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null || mc.interactionManager == null) return;

        boolean parserBusy = parserState != ParserState.IDLE;
        if (!isState() && !parserBusy) return;
        if (!anyEnabled() && !parserBusy) return;

        long now = System.currentTimeMillis();

        if (parserBusy) {
            stepParser(mc, now);
            return;
        }

        if (!isState()) return;
        if (!anyEnabled()) return;

        stepSell(mc, now);
    }

    void stepSell(MinecraftClient mc, long now) {
        if (now < nextAllowedAtMs) return;

        PlayerInventory inv = mc.player.getInventory();
        long delay = Math.max(200L, (long) sellDelayMs.getValue());

        if (phase == 0) {
            FoundBook found = findFirstEnabledBook(mc, inv);
            if (found == null) {
                nextAllowedAtMs = now + 200L;
                return;
            }

            prevSelectedSlot = clampHotbar(inv.selectedSlot);
            workHotbarSlot = prevSelectedSlot;
            originalInvSlot = found.slot;
            swappedFromInventory = false;
            pendingKind = found.kind;

            if (found.slot < 9) {
                workHotbarSlot = found.slot;
                inv.selectedSlot = workHotbarSlot;
                syncSelectedSlot(mc, workHotbarSlot);
            } else {
                int handlerSlot = findHandlerSlotId(mc.player.playerScreenHandler, inv, found.slot);
                if (handlerSlot < 0) {
                    nextAllowedAtMs = now + 300L;
                    return;
                }

                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        handlerSlot,
                        workHotbarSlot,
                        SlotActionType.SWAP,
                        mc.player
                );

                inv.selectedSlot = workHotbarSlot;
                syncSelectedSlot(mc, workHotbarSlot);
                swappedFromInventory = true;
            }

            phase = 1;
            phaseStartedAtMs = now;
            nextAllowedAtMs = now + 150L;
            return;
        }

        if (phase == 1) {
            if (now - phaseStartedAtMs < 150L) return;

            ItemStack held = mc.player.getMainHandStack();
            BookKind heldKind = matchBook(mc, held);

            if (heldKind == null) {
                resetSellOnly();
                nextAllowedAtMs = now + 300L;
                return;
            }

            if (pendingKind != null && heldKind != pendingKind) {
                resetSellOnly();
                nextAllowedAtMs = now + 300L;
                return;
            }

            int sellPrice = getPriceForKind(heldKind);
            if (sellPrice < 1) {
                resetSellOnly();
                nextAllowedAtMs = now + 300L;
                return;
            }

            syncSelectedSlot(mc, workHotbarSlot);
            sendSellCommand(mc, sellPrice);

            phase = swappedFromInventory ? 2 : 3;
            phaseStartedAtMs = now;
            nextAllowedAtMs = now + delay;
            return;
        }

        if (phase == 2) {
            if (now - phaseStartedAtMs < 150L) return;

            if (originalInvSlot >= 9) {
                int handlerSlot = findHandlerSlotId(mc.player.playerScreenHandler, inv, originalInvSlot);
                if (handlerSlot >= 0) {
                    mc.interactionManager.clickSlot(
                            mc.player.playerScreenHandler.syncId,
                            handlerSlot,
                            workHotbarSlot,
                            SlotActionType.SWAP,
                            mc.player
                    );
                }
            }

            phase = 3;
            phaseStartedAtMs = now;
            return;
        }

        if (phase == 3) {
            if (now - phaseStartedAtMs < 100L) return;

            if (restoreSlot.isValue()) {
                inv.selectedSlot = clampHotbar(prevSelectedSlot);
                syncSelectedSlot(mc, inv.selectedSlot);
            }

            resetSellOnly();
            nextAllowedAtMs = now + delay;
        }
    }

    void resetSellOnly() {
        phase = 0;
        phaseStartedAtMs = 0L;
        prevSelectedSlot = 0;
        workHotbarSlot = 0;
        originalInvSlot = -1;
        swappedFromInventory = false;
        pendingKind = null;
    }

    void startParser(long now) {
        parserQueue.clear();
        if (sellUnbreaking5.isValue()) parserQueue.add(BookKind.UNBREAKING5);
        if (sellSharpness5.isValue()) parserQueue.add(BookKind.SHARPNESS5);
        if (sellProtection5.isValue()) parserQueue.add(BookKind.PROTECTION5);
        if (sellSilkTouch.isValue()) parserQueue.add(BookKind.SILK_TOUCH);

        if (parserQueue.isEmpty()) return;

        parserIndex = 0;
        beginParserKind(now);
    }

    void beginParserKind(long now) {
        if (parserIndex < 0 || parserIndex >= parserQueue.size()) {
            finishParser(now);
            return;
        }

        parserKind = parserQueue.get(parserIndex);
        parserCurrentPage = 1;
        parserTotalPages = 1;
        parserBestPrice = INF_PRICE;
        parserWaitAttempts = 0;
        parserSearchVariant = 0;
        parserTitleBeforePage = "";
        resetParserDirectPrices();
        parserState = ParserState.CLOSING_SCREEN;
        parserStateStartedAtMs = now;
    }

    void finishParser(long now) {
        if (parserAnnounce) {
            notifySummary(parserScannedKinds, parserUpdatedKinds);
        }

        parserState = ParserState.IDLE;
        parserQueue.clear();
        parserIndex = 0;
        parserKind = null;
        parserCurrentPage = 1;
        parserTotalPages = 1;
        parserBestPrice = INF_PRICE;
        parserWaitAttempts = 0;
        parserSearchVariant = 0;
        parserStateStartedAtMs = 0L;
        parserTitleBeforePage = "";
        parserPercent = 100f;
        parserAnnounce = false;
        parserScannedKinds = 0;
        parserUpdatedKinds = 0;
        resetParserDirectPrices();
    }

    void stepParser(MinecraftClient mc, long now) {
        if (parserKind == null) {
            finishParser(now);
            return;
        }

        switch (parserState) {
            case CLOSING_SCREEN -> handleParserClosingScreen(mc, now);
            case SENDING_COMMAND -> handleParserSendingCommand(mc, now);
            case WAITING_AUCTION -> handleParserWaitingAuction(mc, now);
            case SCANNING_PAGE -> handleParserScanningPage(mc, now);
            case CLICKING_NEXT_PAGE -> handleParserClickingNext(mc, now);
            case WAITING_PAGE_CHANGE -> handleParserWaitingPageChange(mc, now);
            case APPLYING_RESULT -> handleParserApplyingResult(now);
            case NEXT_KIND -> handleParserNextKind(now);
            case IDLE -> {
            }
        }
    }

    void handleParserClosingScreen(MinecraftClient mc, long now) {
        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            parserStateStartedAtMs = now;
            return;
        }

        if (now - parserStateStartedAtMs >= 200L) {
            parserState = ParserState.SENDING_COMMAND;
            parserStateStartedAtMs = now;
        }
    }

    void handleParserSendingCommand(MinecraftClient mc, long now) {
        if (now - parserStateStartedAtMs < 100L) return;

        String query = getCurrentSearchQuery();
        if (query == null || query.isEmpty()) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
            return;
        }

        sendSearchCommand(mc, query);
        parserState = ParserState.WAITING_AUCTION;
        parserStateStartedAtMs = now;
        parserWaitAttempts = 0;
    }

    void handleParserWaitingAuction(MinecraftClient mc, long now) {
        if (now - parserStateStartedAtMs < 75L) return;
        parserStateStartedAtMs = now;
        parserWaitAttempts++;

        if (parserWaitAttempts > MAX_AUCTION_WAIT_ATTEMPTS) {
            if (!tryNextSearchVariant(now)) {
                parserState = ParserState.APPLYING_RESULT;
                parserStateStartedAtMs = now;
            }
            return;
        }

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            return;
        }

        String title = normalizeTooltipLine(screen.getTitle().getString());

        if (title.contains("не найден") || title.contains("ничего") || title.contains("пусто") || title.contains("нет результатов") || title.contains("товары не найдены") || title.contains("not found")) {
            if (!tryNextSearchVariant(now)) {
                parserState = ParserState.APPLYING_RESULT;
                parserStateStartedAtMs = now;
            }
            return;
        }

        Matcher matcher = PAGE_PATTERN.matcher(screen.getTitle().getString());
        if (matcher.find()) {
            parserCurrentPage = safeParseInt(matcher.group(1), 1);
            parserTotalPages = Math.min(MAX_PAGES_TO_SCAN, safeParseInt(matcher.group(2), 1));
        } else {
            parserCurrentPage = 1;
            parserTotalPages = 1;
        }

        parserState = ParserState.SCANNING_PAGE;
    }

    void handleParserScanningPage(MinecraftClient mc, long now) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
            return;
        }

        int slotsToScan = Math.min(45, screen.getScreenHandler().slots.size());
        for (int i = 0; i < slotsToScan; i++) {
            Slot slot = screen.getScreenHandler().slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) continue;

            ParsedBook parsed = parseTrackedBook(mc, stack);
            if (parsed == null) continue;
            if (parsed.kind != parserKind) continue;
            if (parsed.level < 1 || parsed.level > TRACKED_MAX_LEVEL) continue;

            int price = extractAuctionPrice(stack, mc.player);
            if (price > 0 && price < parserDirectPrices[parsed.level]) {
                parserDirectPrices[parsed.level] = price;
            }
        }

        ParserPlan currentPlan = computeBestPlan(parserKind.lvl);
        parserBestPrice = currentPlan.price;

        if (parserCurrentPage < parserTotalPages) {
            parserTitleBeforePage = screen.getTitle().getString();
            parserState = ParserState.CLICKING_NEXT_PAGE;
            parserStateStartedAtMs = now;
        } else {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
        }
    }

    void handleParserClickingNext(MinecraftClient mc, long now) {
        if (now - parserStateStartedAtMs < 200L) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
            return;
        }

        try {
            mc.interactionManager.clickSlot(screen.getScreenHandler().syncId, 50, 0, SlotActionType.PICKUP, mc.player);
        } catch (Throwable ignored) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
            return;
        }

        parserState = ParserState.WAITING_PAGE_CHANGE;
        parserStateStartedAtMs = now;
    }

    void handleParserWaitingPageChange(MinecraftClient mc, long now) {
        if (now - parserStateStartedAtMs < 100L) return;

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
            return;
        }

        String newTitle = screen.getTitle().getString();
        if (!newTitle.equals(parserTitleBeforePage)) {
            Matcher matcher = PAGE_PATTERN.matcher(newTitle);
            if (matcher.find()) {
                parserCurrentPage = safeParseInt(matcher.group(1), parserCurrentPage + 1);
                parserTotalPages = Math.min(MAX_PAGES_TO_SCAN, safeParseInt(matcher.group(2), parserTotalPages));
            } else {
                parserCurrentPage++;
            }
            parserState = ParserState.SCANNING_PAGE;
            parserStateStartedAtMs = now;
            return;
        }

        if (now - parserStateStartedAtMs > 3000L) {
            parserState = ParserState.APPLYING_RESULT;
            parserStateStartedAtMs = now;
        }
    }

    void handleParserApplyingResult(long now) {
        ParserPlan plan = parserKind == null ? null : computeBestPlan(parserKind.lvl);
        parserBestPrice = plan == null ? INF_PRICE : plan.price;

        if (parserKind != null && parserBestPrice == INF_PRICE && tryNextSearchVariant(now)) {
            return;
        }

        parserScannedKinds++;

        if (parserKind != null && plan != null && plan.price < INF_PRICE) {
            int oldPrice = getPriceForKind(parserKind);
            int newPrice = Math.max(1, Math.round(plan.price * (parserPercent / 100f)));
            if (newPrice != oldPrice) {
                setPriceForKind(parserKind, newPrice);
                parserUpdatedKinds++;
            }
            if (parserAnnounce) {
                notifyParserResult(parserKind, oldPrice, newPrice, plan, parserDirectPrices[parserKind.lvl]);
            }
        } else if (parserAnnounce && parserKind != null) {
            notifyNotFound(parserKind);
        }

        parserState = ParserState.NEXT_KIND;
        parserStateStartedAtMs = now;
    }

    void handleParserNextKind(long now) {
        parserIndex++;
        if (parserIndex >= parserQueue.size()) {
            finishParser(now);
            return;
        }
        beginParserKind(now);
    }

    boolean tryNextSearchVariant(long now) {
        if (parserKind == null) {
            return false;
        }

        String[] queries = getSearchQueries(parserKind);
        if (queries == null || parserSearchVariant + 1 >= queries.length) {
            return false;
        }

        parserSearchVariant++;
        parserCurrentPage = 1;
        parserTotalPages = 1;
        parserWaitAttempts = 0;
        parserTitleBeforePage = "";
        parserState = ParserState.CLOSING_SCREEN;
        parserStateStartedAtMs = now;
        return true;
    }

    String getCurrentSearchQuery() {
        if (parserKind == null) {
            return null;
        }

        String[] queries = getSearchQueries(parserKind);
        if (queries == null || parserSearchVariant < 0 || parserSearchVariant >= queries.length) {
            return null;
        }

        return queries[parserSearchVariant];
    }

    String[] getSearchQueries(BookKind kind) {
        return switch (kind) {
            case UNBREAKING5 -> new String[]{"книга прочность"};
            case SHARPNESS5 -> new String[]{"книга острота"};
            case PROTECTION5 -> new String[]{"книга защита"};
            case SILK_TOUCH -> new String[]{"книга шелковое касание"};
        };
    }

    int extractAuctionPrice(ItemStack stack, PlayerEntity player) {
        List<String> tooltip = getTooltipStrings(stack, player);
        int best = -1;

        for (String line : tooltip) {
            if (line == null || line.isEmpty()) continue;

            String raw = line.toLowerCase(Locale.ROOT);
            String s = normalizeTooltipLine(line);

            boolean looksLikePrice = s.contains("цена")
                    || s.contains("стоимость")
                    || s.contains("продажа")
                    || s.contains("купить")
                    || raw.contains("$")
                    || s.contains("монет")
                    || s.contains("coins");

            if (!looksLikePrice) continue;

            int price = extractDigitsPrice(raw);
            if (price > 0 && (best == -1 || price < best)) {
                best = price;
            }
        }

        return best;
    }

    int extractDigitsPrice(String s) {
        String digits = s.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return -1;
        try {
            return Integer.parseInt(digits);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    ParserPlan computeBestPlan(int level) {
        ParserPlan[] memo = new ParserPlan[TRACKED_MAX_LEVEL + 1];
        return computeBestPlan(level, memo);
    }

    ParserPlan computeBestPlan(int level, ParserPlan[] memo) {
        if (level < 1 || level >= memo.length) {
            return new ParserPlan(INF_PRICE, false, -1);
        }

        ParserPlan cached = memo[level];
        if (cached != null) {
            return cached;
        }

        int direct = parserDirectPrices[level];
        ParserPlan best = new ParserPlan(direct, false, level);

        if (level > 1) {
            ParserPlan lower = computeBestPlan(level - 1, memo);
            if (lower.price < INF_PRICE) {
                long combinedLong = (long) lower.price + (long) lower.price;
                int combined = combinedLong >= INF_PRICE ? INF_PRICE : (int) combinedLong;
                if (combined < best.price) {
                    best = new ParserPlan(combined, true, level - 1);
                }
            }
        }

        memo[level] = best;
        return best;
    }

    int getPriceForKind(BookKind kind) {
        if (kind == BookKind.UNBREAKING5) return Math.max(1, (int) unbreakingPrice.getValue());
        if (kind == BookKind.SHARPNESS5) return Math.max(1, (int) sharpnessPrice.getValue());
        if (kind == BookKind.PROTECTION5) return Math.max(1, (int) protectionPrice.getValue());
        if (kind == BookKind.SILK_TOUCH) return Math.max(1, (int) silkTouchPrice.getValue());
        return 1;
    }

    void setPriceForKind(BookKind kind, int price) {
        float value = Math.max(1f, price);
        if (kind == BookKind.UNBREAKING5) {
            setSliderCurrentValue(unbreakingPrice, value);
            return;
        }
        if (kind == BookKind.SHARPNESS5) {
            setSliderCurrentValue(sharpnessPrice, value);
            return;
        }
        if (kind == BookKind.PROTECTION5) {
            setSliderCurrentValue(protectionPrice, value);
            return;
        }
        if (kind == BookKind.SILK_TOUCH) {
            setSliderCurrentValue(silkTouchPrice, value);
        }
    }

    void setSliderCurrentValue(Object setting, float value) {
        try {
            Method m = setting.getClass().getMethod("setValue", float.class);
            m.setAccessible(true);
            m.invoke(setting, value);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            if (f.getType() == float.class) {
                f.setFloat(setting, value);
                return;
            }
            if (Number.class.isAssignableFrom(f.getType())) {
                f.set(setting, value);
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState() && !isParsing()) return;

        Object packet = extractPacket(e);
        if (!(packet instanceof Packet<?>)) return;

        String msg = extractChatText(packet);
        if (msg == null || msg.isEmpty()) return;

        String lower = msg.toLowerCase(Locale.ROOT);
        if (lower.contains("освободите хранилище") || lower.contains("уберите предметы") || lower.contains("предметы с продажи")) {
            if (isParsing()) {
                parserAnnounce = false;
                finishParser(System.currentTimeMillis());
            }
            if (isState()) {
                setState(false);
            }
        }
    }

    enum BookKind {
        UNBREAKING5("unbreaking", 5),
        SHARPNESS5("sharpness", 5),
        PROTECTION5("protection", 5),
        SILK_TOUCH("silk_touch", 1);

        final String path;
        final int lvl;

        BookKind(String path, int lvl) {
            this.path = path;
            this.lvl = lvl;
        }
    }

    enum ParserState {
        IDLE,
        CLOSING_SCREEN,
        SENDING_COMMAND,
        WAITING_AUCTION,
        SCANNING_PAGE,
        CLICKING_NEXT_PAGE,
        WAITING_PAGE_CHANGE,
        APPLYING_RESULT,
        NEXT_KIND
    }

    static final class FoundBook {
        final int slot;
        final BookKind kind;

        FoundBook(int slot, BookKind kind) {
            this.slot = slot;
            this.kind = kind;
        }
    }

    static final class ParsedBook {
        final BookKind kind;
        final int level;

        ParsedBook(BookKind kind, int level) {
            this.kind = kind;
            this.level = level;
        }
    }

    static final class ParserPlan {
        final int price;
        final boolean combined;
        final int sourceLevel;

        ParserPlan(int price, boolean combined, int sourceLevel) {
            this.price = price;
            this.combined = combined;
            this.sourceLevel = sourceLevel;
        }
    }

    boolean anyEnabled() {
        return sellUnbreaking5.isValue() || sellSharpness5.isValue() || sellProtection5.isValue() || sellSilkTouch.isValue();
    }

    boolean isEnabled(BookKind kind) {
        if (kind == BookKind.UNBREAKING5) return sellUnbreaking5.isValue();
        if (kind == BookKind.SHARPNESS5) return sellSharpness5.isValue();
        if (kind == BookKind.PROTECTION5) return sellProtection5.isValue();
        if (kind == BookKind.SILK_TOUCH) return sellSilkTouch.isValue();
        return false;
    }

    FoundBook findFirstEnabledBook(MinecraftClient mc, PlayerInventory inv) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getStack(i);
            BookKind kind = matchBook(mc, stack);
            if (kind != null && isEnabled(kind)) {
                return new FoundBook(i, kind);
            }
        }
        return null;
    }

    BookKind matchBook(MinecraftClient mc, ItemStack stack) {
        ParsedBook parsed = parseTrackedBook(mc, stack);
        if (parsed == null) return null;
        if (parsed.kind == BookKind.SILK_TOUCH) return BookKind.SILK_TOUCH;
        return parsed.level == 5 ? parsed.kind : null;
    }

    ParsedBook parseTrackedBook(MinecraftClient mc, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() != Items.ENCHANTED_BOOK) return null;

        for (BookKind kind : BookKind.values()) {
            int level = findEnchantLevel(stack, kind.path);
            if (level >= 1 && level <= TRACKED_MAX_LEVEL) {
                if (kind == BookKind.SILK_TOUCH) return new ParsedBook(kind, 1);
                return new ParsedBook(kind, level);
            }
        }

        ParsedBook byName = parseTrackedBookFromText(stack.getName().getString());
        if (byName != null) return byName;

        List<String> tooltip = getTooltipStrings(stack, mc != null ? mc.player : null);
        for (String line : tooltip) {
            ParsedBook parsed = parseTrackedBookFromText(line);
            if (parsed != null) return parsed;
        }

        return null;
    }

    ParsedBook parseTrackedBookFromText(String line) {
        if (line == null || line.isEmpty()) return null;

        String s = normalizeTooltipLine(line);
        if (s.isEmpty()) return null;

        if (containsAnyText(s, "шелковое касание", "silk touch", "silk_touch")) {
            return new ParsedBook(BookKind.SILK_TOUCH, 1);
        }

        int level = extractLevelFromLine(s);
        if (level < 1 || level > TRACKED_MAX_LEVEL) return null;

        if (containsAnyText(s, "защита", "protection")) return new ParsedBook(BookKind.PROTECTION5, level);
        if (containsAnyText(s, "прочность", "unbreaking")) return new ParsedBook(BookKind.UNBREAKING5, level);
        if (containsAnyText(s, "острота", "sharpness")) return new ParsedBook(BookKind.SHARPNESS5, level);

        return null;
    }

    String normalizeTooltipLine(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replace('ё', 'е')
                .replace('\u00A0', ' ')
                .replace("enchantment.minecraft.", "")
                .replace("minecraft:", "")
                .replaceAll("[\\[\\]\\(\\){}:;,.!?/\\\\|]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    boolean containsAnyText(String line, String... variants) {
        String s = normalizeTooltipLine(line);
        for (String v : variants) {
            if (s.contains(normalizeTooltipLine(v))) {
                return true;
            }
        }
        return false;
    }

    int extractLevelFromLine(String line) {
        if (line == null || line.isEmpty()) return -1;

        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) return -1;

        String last = parts[parts.length - 1]
                .replace(".", "")
                .replace(",", "")
                .replace(";", "")
                .replace(":", "")
                .trim();

        if (last.isEmpty()) return -1;

        Integer arabic = tryParseInt(last);
        if (arabic != null) return arabic;

        return romanToInt(last);
    }

    Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable ignored) {
            return null;
        }
    }

    int romanToInt(String s) {
        if (s == null || s.isEmpty()) return -1;

        String v = s.toUpperCase(Locale.ROOT);
        return switch (v) {
            case "I" -> 1;
            case "II" -> 2;
            case "III" -> 3;
            case "IV" -> 4;
            case "V" -> 5;
            case "VI" -> 6;
            case "VII" -> 7;
            case "VIII" -> 8;
            case "IX" -> 9;
            case "X" -> 10;
            default -> -1;
        };
    }

    String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    List<String> getTooltipStrings(ItemStack stack, PlayerEntity player) {
        try {
            for (Method method : stack.getClass().getMethods()) {
                if (!method.getName().equals("getTooltip")) continue;
                if (!List.class.isAssignableFrom(method.getReturnType())) continue;

                Class<?>[] types = method.getParameterTypes();
                Object[] args = new Object[types.length];

                for (int i = 0; i < types.length; i++) {
                    Class<?> type = types[i];
                    String name = type.getName();

                    if (PlayerEntity.class.isAssignableFrom(type)) {
                        args[i] = player;
                        continue;
                    }

                    if (name.contains("TooltipContext")) {
                        args[i] = resolveTooltipContext(type);
                        continue;
                    }

                    if (name.contains("TooltipType") || type.isEnum()) {
                        args[i] = resolveTooltipType(type);
                        continue;
                    }

                    if (type == boolean.class || type == Boolean.class) {
                        args[i] = Boolean.FALSE;
                        continue;
                    }

                    args[i] = null;
                }

                Object result = method.invoke(stack, args);
                if (result instanceof List<?> list) {
                    return list.stream().map(this::tooltipLineToString).toList();
                }
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    Object resolveTooltipContext(Class<?> type) {
        try {
            Field f = type.getField("DEFAULT");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
        }

        try {
            for (Field f : type.getFields()) {
                if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0 && type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    Object resolveTooltipType(Class<?> type) {
        try {
            if (type.isEnum()) {
                Object[] constants = type.getEnumConstants();
                if (constants != null && constants.length > 0) return constants[0];
            }
        } catch (Throwable ignored) {
        }

        try {
            Field f = type.getField("BASIC");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
        }

        try {
            for (Field f : type.getFields()) {
                if ((f.getModifiers() & java.lang.reflect.Modifier.STATIC) != 0 && type.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    String tooltipLineToString(Object line) {
        if (line == null) return "";
        if (line instanceof Text text) {
            try {
                return text.getString();
            } catch (Throwable ignored) {
                return "";
            }
        }

        try {
            Method m = line.getClass().getMethod("getString");
            Object r = m.invoke(line);
            if (r instanceof String s) return s;
        } catch (Throwable ignored) {
        }

        return String.valueOf(line);
    }

    int findEnchantLevel(ItemStack stack, String wantPath) {
        NbtCompound nbt = getStackNbt(stack);
        if (nbt == null) return -1;

        int stored = listFindEnchantLevel(nbt, "StoredEnchantments", wantPath);
        if (stored > 0) return stored;

        return listFindEnchantLevel(nbt, "Enchantments", wantPath);
    }

    int listFindEnchantLevel(NbtCompound nbt, String key, String wantPath) {
        NbtList list;
        try {
            list = nbt.getList(key, 10);
        } catch (Throwable t) {
            return -1;
        }

        if (list == null || list.isEmpty()) return -1;

        for (int i = 0; i < list.size(); i++) {
            NbtCompound enchant = list.getCompound(i);

            String id = "";
            int lvl = 0;

            try {
                id = enchant.getString("id");
            } catch (Throwable ignored) {
            }

            try {
                lvl = enchant.getShort("lvl");
            } catch (Throwable ignored) {
                try {
                    lvl = enchant.getInt("lvl");
                } catch (Throwable ignored2) {
                    lvl = 0;
                }
            }

            if (id == null || id.isEmpty()) continue;

            String low = id.toLowerCase(Locale.ROOT);
            String path = low.contains(":") ? low.substring(low.indexOf(':') + 1) : low;

            if (path.equals(wantPath)) return lvl;
        }

        return -1;
    }

    NbtCompound getStackNbt(ItemStack stack) {
        Object value = invokeObj(stack, "getNbt");
        if (value instanceof NbtCompound compound) return compound;

        value = invokeObj(stack, "getOrCreateNbt");
        if (value instanceof NbtCompound compound) return compound;

        return null;
    }

    int findHandlerSlotId(ScreenHandler handler, PlayerInventory inv, int invIndex) {
        try {
            for (int i = 0; i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                if (slot == null) continue;
                if (slot.inventory == inv && slot.getIndex() == invIndex) return i;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    int clampHotbar(int slot) {
        if (slot < 0) return 0;
        if (slot > 8) return 8;
        return slot;
    }

    float clamp(float value, float min, float max) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    void syncSelectedSlot(MinecraftClient mc, int slot) {
        if (mc == null || mc.player == null || mc.interactionManager == null) return;

        mc.player.getInventory().selectedSlot = clampHotbar(slot);

        try {
            if (IM_SYNC_SELECTED_SLOT != null) {
                IM_SYNC_SELECTED_SLOT.invoke(mc.interactionManager);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method m = mc.interactionManager.getClass().getMethod("syncSelectedSlot");
            m.setAccessible(true);
            m.invoke(mc.interactionManager);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = mc.interactionManager.getClass().getDeclaredMethod("syncSelectedSlot");
            m.setAccessible(true);
            m.invoke(mc.interactionManager);
        } catch (Throwable ignored) {
        }
    }

    boolean sendSellCommand(MinecraftClient mc, int priceValue) {
        return sendChatOrCommand(mc, "/ah sell " + priceValue, "ah sell " + priceValue);
    }

    boolean sendSearchCommand(MinecraftClient mc, String query) {
        return sendChatOrCommand(mc, "/ah search " + query, "ah search " + query);
    }

    boolean sendChatOrCommand(MinecraftClient mc, String withSlash, String noSlash) {
        Object nh = mc.getNetworkHandler();
        if (nh != null) {
            try {
                if (NH_SEND_CHAT_MESSAGE != null) {
                    NH_SEND_CHAT_MESSAGE.invoke(nh, withSlash);
                    return true;
                }
            } catch (Throwable ignored) {
            }

            try {
                if (NH_SEND_CHAT_COMMAND != null) {
                    NH_SEND_CHAT_COMMAND.invoke(nh, noSlash);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }

        try {
            Object player = mc.player;
            Method m = player.getClass().getMethod("sendChatMessage", String.class);
            m.setAccessible(true);
            m.invoke(player, withSlash);
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    int safeParseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    String kindLabel(BookKind kind) {
        if (kind == BookKind.UNBREAKING5) return "Прочность V";
        if (kind == BookKind.SHARPNESS5) return "Острота V";
        if (kind == BookKind.PROTECTION5) return "Защита V";
        if (kind == BookKind.SILK_TOUCH) return "Шелковое касание";
        return "Книга";
    }

    void notifyParserResult(BookKind kind, int oldPrice, int newPrice, ParserPlan plan, int directTargetPrice) {
        String sellPart = buildSellPart(oldPrice, newPrice);

        if (plan.combined) {
            String directPart = directTargetPrice < INF_PRICE ? "§7прямой " + toRoman(kind.lvl) + ": §f" + formatPrice(directTargetPrice) + " §8• " : "";
            ChatMessage.brandmessage(
                    "§6AutoSell §8» §f" + kindLabel(kind) +
                            " §8• " + directPart +
                            "§7сборка: §e2x " + toRoman(plan.sourceLevel) + " §8= §f" + formatPrice(plan.price) +
                            " §8• " + sellPart
            );
            return;
        }

        ChatMessage.brandmessage(
                "§6AutoSell §8» §f" + kindLabel(kind) +
                        " §8• §7мин: §f" + formatPrice(plan.price) +
                        " §8• " + sellPart
        );
    }

    String buildSellPart(int oldPrice, int newPrice) {
        if (oldPrice == newPrice) {
            return "§7продажа: §f" + formatPrice(newPrice);
        }

        String color = newPrice < oldPrice ? "§a" : newPrice > oldPrice ? "§c" : "§f";
        return "§7продажа: §f" + formatPrice(oldPrice) + " §8→ " + color + formatPrice(newPrice);
    }

    void notifyNotFound(BookKind kind) {
        ChatMessage.brandmessage("§6AutoSell §8» §f" + kindLabel(kind) + " §7не найдена на аукционе");
    }

    void notifySummary(int scanned, int updated) {
        ChatMessage.brandmessage("§6AutoSell §8» §7Парсер завершён §8• §7обработано: §f" + scanned + " §8• §7изменено: §a" + updated);
    }

    String formatPrice(int price) {
        if (price <= 0 || price >= INF_PRICE) return "—";
        return String.format(Locale.US, "%,d", price).replace(',', ' ');
    }

    static Object extractPacket(PacketEvent e) {
        try {
            Method m = e.getClass().getMethod("getPacket");
            Object v = m.invoke(e);
            if (v instanceof Packet) return v;
        } catch (Throwable ignored) {
        }

        try {
            Field f = e.getClass().getDeclaredField("packet");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof Packet) return v;
        } catch (Throwable ignored) {
        }

        try {
            Method m = e.getClass().getMethod("get");
            Object v = m.invoke(e);
            if (v instanceof Packet) return v;
        } catch (Throwable ignored) {
        }

        return null;
    }

    String extractChatText(Object packet) {
        Text text = invokeText(packet, "content");
        if (text == null) text = invokeText(packet, "message");
        if (text == null) text = invokeText(packet, "getMessage");
        if (text == null) text = invokeText(packet, "unsignedContent");
        if (text != null) return text.getString();

        Object body = invokeObj(packet, "body");
        if (body != null) {
            Object content = invokeObj(body, "content");
            if (content instanceof String s) return s;
            if (content instanceof Text tx) return tx.getString();
        }

        return null;
    }

    Text invokeText(Object obj, String method) {
        Object value = invokeObj(obj, method);
        return value instanceof Text text ? text : null;
    }

    Object invokeObj(Object obj, String method) {
        if (obj == null) return null;

        try {
            Method m = obj.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {
        }

        try {
            Method m = obj.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {
        }

        return null;
    }
}