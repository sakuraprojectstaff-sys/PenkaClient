package fun.rich.features.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.container.HandledScreenEvent;
import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.features.price.PriceParser;
import fun.rich.utils.math.script.Script;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuctionHelper extends Module {

    static final long PULSE_MS = 900L;
    static final float PULSE_MIN_ALPHA = 0.35f;
    static final float PULSE_MAX_ALPHA = 1.0f;

    static final long RECALC_MIN_MS = 90L;
    static final long RECALC_IDLE_MS = 650L;

    static final int IGNORE_CONTROL_ROW_Y = 104;

    static final int PANEL_W = 260;
    static final int PANEL_PAD = 8;
    static final int PANEL_MAX = 8;
    static final int HEADER_H = 26;
    static final int ENTRY_H = 28;

    static final long BUY_KEEP_MS = 900_000L;
    static final int BUY_MAX = 80;

    static final long SNAP_KEEP_MS = 12_000L;
    static final int SNAP_MAX = 64;

    static final long ICON_RESOLVE_KEEP_MS = 9_000L;
    static final int ICON_PENDING_MAX = 96;

    static final int TEXT_DIM = 0xB0FFFFFF;
    static final int TEXT_DIM2 = 0x90FFFFFF;

    static final int PRICE_NUM = 0xFFFFFFFF;
    static final int PRICE_DOLLAR = 0xFF4BFF4B;

    static final Pattern NUM_PATTERN = Pattern.compile("(\\d{1,3}(?:[\\s,._]\\d{3})+|\\d+)");
    static final Pattern COUNT_X = Pattern.compile("(?i)\\bx\\s*(\\d{1,4})\\b");
    static final Pattern COUNT_PCS = Pattern.compile("(?i)\\b(\\d{1,4})\\s*(шт|штук|pcs)\\b");
    static final Pattern SELF_BUY_PATTERN = Pattern.compile("(?iu)вы\\s+успешно\\s+(?:купили|приобрели)\\s+(.+?)\\s+за\\s*\\$?\\s*([0-9][0-9\\s,._]*)");

    static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    static final Identifier UTYA_GIF = Identifier.of("mre", "textures/utya.gif");

    @NonFinal static boolean mouseWheelInit = false;
    @NonFinal static Field fMouseWheel;
    @NonFinal static Method mMouseWheelGetter;

    @NonFinal static boolean tooltipInit = false;
    @NonFinal static Method mGetTooltip;
    @NonFinal static TooltipArg[] tooltipArgs;

    @NonFinal static Method mDrawItem;

    @NonFinal static boolean texInit = false;
    @NonFinal static Method mDrawTex;
    @NonFinal static int texMode = 0;
    @NonFinal static Method mGuiLayerFactory;
    @NonFinal static Object texFirstArg;
    @NonFinal static Function<Identifier, Object> guiLayerFn;

    PriceParser priceParser = new PriceParser();
    Script script = new Script();

    @NonFinal Slot cheapestSlot;
    @NonFinal Slot costEffectiveSlot;

    @NonFinal int lastSyncId = -1;
    @NonFinal long lastRecalcMs = 0L;
    @NonFinal boolean dirty = false;

    @NonFinal boolean recalcQueued = false;

    ArrayDeque<BuyEntry> buys = new ArrayDeque<>();
    ArrayDeque<SnapMeta> snaps = new ArrayDeque<>();
    ArrayDeque<PendingIcon> pendingIcons = new ArrayDeque<>();

    @NonFinal SnapMeta lastHover;
    @NonFinal SnapMeta lastClick;

    @NonFinal boolean lmbPrev = false;
    @NonFinal int mouseX = 0;
    @NonFinal int mouseY = 0;

    @NonFinal float buyScrollPx = 0f;
    @NonFinal boolean buyDrag = false;
    @NonFinal int buyDragStartY = 0;
    @NonFinal float buyDragStartScroll = 0f;

    @NonFinal long lastIconScanMs = 0L;

    int[] RED_GREEN_COLORS = {0xFF4BFF4B, 0xFFFF4B4B};

    ColorSetting cheapestItemColorSetting = new ColorSetting("Самый дешевый предмет", "Цвет подсветки для предмета с наименьшей ценой.")
            .setColor(0xFF4BFF4B).presets(RED_GREEN_COLORS);

    ColorSetting costEffectiveItemColorSetting = new ColorSetting("Экономичный предмет", "Цвет подсветки для лучшего предмета.")
            .setColor(0xFFFF4B4B).presets(RED_GREEN_COLORS);

    @NonFinal GifAnim utya = new GifAnim(UTYA_GIF);

    public AuctionHelper() {
        super("AuctionHelper", "Auction Helper", ModuleCategory.RENDER);
        setup(cheapestItemColorSetting, costEffectiveItemColorSetting);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onPacket(PacketEvent e) {
        Object p = e.getPacket();

        if (p instanceof ScreenHandlerSlotUpdateS2CPacket) {
            if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
            if (!isAuctionScreen(screen)) return;

            dirty = true;
            if (!recalcQueued) {
                recalcQueued = true;
                script.cleanup().addTickStep(0, () -> {
                    recalcQueued = false;
                    if (mc.currentScreen instanceof GenericContainerScreen s && isAuctionScreen(s)) recalc(s);
                });
            }
            return;
        }

        if (p instanceof GameMessageS2CPacket gm) {
            String msg = gm.content() == null ? "" : gm.content().getString();
            if (msg.isEmpty()) return;

            PurchaseEvent pe = parseSelfBuySuccess(msg);
            if (pe == null) return;

            long now = System.currentTimeMillis();
            pushChatPurchase(pe, now);
        }
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onTick(TickEvent e) {
        script.update();

        if (!utya.loaded) {
            if (mc.player != null && mc.getResourceManager() != null) {
                if (!(mc.currentScreen instanceof GenericContainerScreen s) || !isAuctionScreen(s)) {
                    utya.ensureLoaded(mc);
                }
            }
        }

        long now = System.currentTimeMillis();
        pruneBuys(now);
        pruneSnaps(now);
        prunePendingIcons(now);
        resolvePendingIcons(now);

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            resetCalcState();
            lmbPrev = false;
            buyDrag = false;
            return;
        }
        if (!isAuctionScreen(screen)) {
            resetCalcState();
            lmbPrev = false;
            buyDrag = false;
            return;
        }

        if (!dirty && now - lastRecalcMs >= RECALC_IDLE_MS) recalc(screen);
    }

    @EventHandler
    @SuppressWarnings("unused")
    public void onHandledScreen(HandledScreenEvent e) {
        DrawContext ctx = e.getDrawContext();

        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isAuctionScreen(screen)) {
            resetCalcState();
            lmbPrev = false;
            buyDrag = false;
            return;
        }

        long now = System.currentTimeMillis();

        updateMouse();
        boolean lmb = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;
        boolean clickEdge = lmb && !lmbPrev;
        boolean releaseEdge = !lmb && lmbPrev;

        ensureCalculated(screen, now);

        int guiLeft = (screen.width - e.getBackgroundWidth()) / 2;
        int guiTop = (screen.height - e.getBackgroundHeight()) / 2;

        updateHoverMeta(screen, guiLeft, guiTop, now);

        int cheapColor = pulsing(cheapestItemColorSetting.getColor());
        int effColor = pulsing(costEffectiveItemColorSetting.getColor());

        if (cheapestSlot != null) highlightSlotAbs(ctx, guiLeft, guiTop, cheapestSlot, cheapColor, 0);
        if (costEffectiveSlot != null) highlightSlotAbs(ctx, guiLeft, guiTop, costEffectiveSlot, effColor, 0);

        boolean panelConsumed = drawBoughtPanel(ctx, guiLeft, guiTop, e.getBackgroundWidth(), clickEdge, lmb, releaseEdge);

        if (clickEdge && !panelConsumed) {
            Slot s = findAuctionSlotUnderMouse(screen, guiLeft, guiTop, mouseX, mouseY);
            if (s != null) {
                ItemStack st = s.getStack();
                if (!st.isEmpty()) lastClick = makeSnapFromStack(st, now);
            }
        }

        lmbPrev = lmb;
    }

    private void ensureCalculated(GenericContainerScreen screen, long now) {
        int syncId = screen.getScreenHandler().syncId;
        if (syncId != lastSyncId) {
            lastSyncId = syncId;
            dirty = true;
            recalcQueued = false;
            cheapestSlot = null;
            costEffectiveSlot = null;
            buyScrollPx = 0f;
            buyDrag = false;
            lastHover = null;
            lastClick = null;
            snaps.clear();
        }

        if (dirty && now - lastRecalcMs >= RECALC_MIN_MS) recalc(screen);
    }

    private void recalc(GenericContainerScreen screen) {
        if (mc.player == null) {
            resetCalcState();
            return;
        }

        long now = System.currentTimeMillis();
        List<Slot> slots = screen.getScreenHandler().slots;

        int n = slots.size();
        int[] prices = new int[n];
        int[] counts = new int[n];

        Slot bestCheap = null;
        int bestCheapPrice = Integer.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }

            if (slot.inventory == mc.player.getInventory()) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }
            if (slot.y >= IGNORE_CONTROL_ROW_Y) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }

            int price = getTotalPrice(stack);
            if (price >= 0) pushSnap(makeSnapFromStack(stack, now));
            prices[i] = price;

            int count = Math.max(1, stack.getCount());
            counts[i] = count;

            if (price < 0) continue;

            if (price < bestCheapPrice) {
                bestCheapPrice = price;
                bestCheap = slot;
            }
        }

        Slot bestEff = null;
        double bestEffPpi = Double.POSITIVE_INFINITY;
        int bestEffTotal = Integer.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            int price = prices[i];
            if (price < 0) continue;

            Slot slot = slots.get(i);
            if (slot == bestCheap) continue;

            int count = Math.max(1, counts[i]);
            double ppi = (double) price / (double) count;

            boolean better = ppi < bestEffPpi - 1.0E-9;
            boolean equal = Math.abs(ppi - bestEffPpi) <= 1.0E-9;
            if (better || (equal && price < bestEffTotal)) {
                bestEffPpi = ppi;
                bestEffTotal = price;
                bestEff = slot;
            }
        }

        cheapestSlot = bestCheap;
        costEffectiveSlot = bestEff;

        dirty = false;
        lastRecalcMs = now;
    }

    private void pushSnap(SnapMeta s) {
        if (s == null || s.icon == null || s.icon.isEmpty()) return;

        SnapMeta last = snaps.peekLast();
        if (last != null && last.nameKey.equals(s.nameKey) && s.timeMs - last.timeMs <= 350L) {
            snaps.pollLast();
        }

        snaps.addLast(s);
        while (snaps.size() > SNAP_MAX) snaps.pollFirst();
    }

    private void pruneSnaps(long now) {
        while (!snaps.isEmpty()) {
            SnapMeta f = snaps.peekFirst();
            if (f == null) break;
            if (now - f.timeMs > SNAP_KEEP_MS) snaps.pollFirst();
            else break;
        }
    }

    private void pushChatPurchase(PurchaseEvent pe, long now) {
        SnapMeta snap = matchSnap(pe.itemName, now);

        String key = snap != null ? snap.nameKey : ("chat|" + normalizeName(pe.itemName));
        String name = pe.itemName;
        int price = pe.price;
        int count = pe.count;

        ItemStack icon = null;

        if (snap != null) {
            if (name.isEmpty()) name = snap.displayName;
            if (price < 0) price = snap.price;
            if (count <= 0) count = snap.count;
            if (count <= 0) count = 1;

            icon = snap.icon.copy();
            int max = Math.max(1, icon.getMaxCount());
            icon.setCount(Math.min(Math.max(1, count), max));
        }

        if (count <= 0) count = 1;

        BuyEntry be = new BuyEntry(key, name, icon, count, price, now);
        pushBuy(be);

        if (be.icon == null || be.icon.isEmpty()) {
            String want = normalizeName(name.isEmpty() ? pe.itemName : name);
            if (!want.isEmpty()) pushPendingIcon(new PendingIcon(key, want, count, now));
        }
    }

    private void pushPendingIcon(PendingIcon p) {
        if (p == null) return;
        if (p.key.isEmpty() || p.wantNorm.isEmpty()) return;

        PendingIcon last = pendingIcons.peekLast();
        if (last != null && last.key.equals(p.key) && p.timeMs - last.timeMs <= 1600L) {
            last.wantNorm = p.wantNorm;
            last.count = Math.max(last.count, p.count);
            last.timeMs = p.timeMs;
            return;
        }

        pendingIcons.addLast(p);
        while (pendingIcons.size() > ICON_PENDING_MAX) pendingIcons.pollFirst();
    }

    private void prunePendingIcons(long now) {
        while (!pendingIcons.isEmpty()) {
            PendingIcon f = pendingIcons.peekFirst();
            if (f == null) break;
            if (now - f.timeMs > ICON_RESOLVE_KEEP_MS) pendingIcons.pollFirst();
            else break;
        }
    }

    private void resolvePendingIcons(long now) {
        if (mc.player == null) return;
        if (pendingIcons.isEmpty()) return;
        if (now - lastIconScanMs < 65L) return;
        lastIconScanMs = now;

        int budget = 6;

        Iterator<PendingIcon> it = pendingIcons.iterator();
        while (it.hasNext() && budget-- > 0) {
            PendingIcon p = it.next();
            if (p == null) {
                it.remove();
                continue;
            }
            if (now - p.timeMs > ICON_RESOLVE_KEEP_MS) {
                it.remove();
                continue;
            }

            BuyEntry be = findBuyByKey(p.key);
            if (be == null) {
                it.remove();
                continue;
            }
            if (be.icon != null && !be.icon.isEmpty()) {
                it.remove();
                continue;
            }

            ItemStack found = findInPlayerInvByName(p.wantNorm, Math.max(1, p.count));
            if (found == null || found.isEmpty()) continue;

            ItemStack icon = found.copy();
            int max = Math.max(1, icon.getMaxCount());
            int want = Math.max(1, be.count);
            icon.setCount(Math.min(want, max));
            be.icon = icon;

            it.remove();
        }
    }

    private BuyEntry findBuyByKey(String key) {
        if (key == null || key.isEmpty()) return null;
        if (buys.isEmpty()) return null;

        Iterator<BuyEntry> it = buys.descendingIterator();
        while (it.hasNext()) {
            BuyEntry b = it.next();
            if (b != null && key.equals(b.key)) return b;
        }
        return null;
    }

    private ItemStack findInPlayerInvByName(String wantNorm, int need) {
        if (mc.player == null) return null;
        if (wantNorm == null || wantNorm.isEmpty()) return null;

        ItemStack best = null;
        int bestScore = -1;
        int bestCount = 0;

        int size = mc.player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) continue;

            String nm = normalizeName(safeName(s));
            if (nm.isEmpty()) continue;
            if (!nameMatches(nm, wantNorm)) continue;

            int score = 0;
            if (nm.equals(wantNorm)) score += 8;
            else if (nm.contains(wantNorm) || wantNorm.contains(nm)) score += 6;
            else score += 4;

            int c = Math.max(1, s.getCount());
            if (c >= need) score += 2;
            score += Math.min(3, c);

            if (score > bestScore || (score == bestScore && c > bestCount)) {
                bestScore = score;
                bestCount = c;
                best = s;
            }
        }

        return best;
    }

    private SnapMeta matchSnap(String itemName, long now) {
        String want = normalizeName(itemName);
        if (!want.isEmpty()) {
            if (lastClick != null && now - lastClick.timeMs <= 12_000L) {
                if (nameMatches(lastClick.nameNorm, want)) return lastClick;
            }
            if (lastHover != null && now - lastHover.timeMs <= 8_000L) {
                if (nameMatches(lastHover.nameNorm, want)) return lastHover;
            }

            Iterator<SnapMeta> it = snaps.descendingIterator();
            while (it.hasNext()) {
                SnapMeta s = it.next();
                if (now - s.timeMs > SNAP_KEEP_MS) break;
                if (nameMatches(s.nameNorm, want)) return s;
            }
        }

        if (lastClick != null && now - lastClick.timeMs <= 12_000L) return lastClick;
        if (lastHover != null && now - lastHover.timeMs <= 8_000L) return lastHover;

        Iterator<SnapMeta> it = snaps.descendingIterator();
        while (it.hasNext()) {
            SnapMeta s = it.next();
            if (now - s.timeMs > SNAP_KEEP_MS) break;
            return s;
        }
        return null;
    }

    private boolean nameMatches(String a, String b) {
        if (a == null || b == null) return false;
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.contains(b) || b.contains(a);
    }

    private PurchaseEvent parseSelfBuySuccess(String msg) {
        String clean = stripFormatting(msg).replace('\u00A0', ' ').trim();
        if (clean.isEmpty()) return null;

        String lower = clean.toLowerCase();
        if (!lower.contains("вы") || !(lower.contains("купили") || lower.contains("приобрели"))) return null;
        if (lower.contains("ошибка") || lower.contains("не удалось") || lower.contains("уже куп")) return null;

        Matcher m = SELF_BUY_PATTERN.matcher(clean);
        if (!m.find()) return null;

        String item = m.group(1) == null ? "" : m.group(1).trim();
        String priceRaw = m.group(2) == null ? "" : m.group(2);

        int price = parseNumberOnly(priceRaw);
        if (price < 0) price = parseNumberOnly(clean);

        int count = parseCount(clean);
        if (count <= 0) count = 1;

        item = trimTrailingPunct(item);

        if (item.isEmpty() && price < 0) return null;
        return new PurchaseEvent(item, price, count);
    }

    private int parseNumberOnly(String s) {
        if (s == null || s.isEmpty()) return -1;
        Matcher m = NUM_PATTERN.matcher(s);
        if (!m.find()) return -1;
        long v = parseDigitsToLong(m.group(1));
        if (v <= 0) return -1;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) v;
    }

    private int parseCount(String clean) {
        Matcher m1 = COUNT_X.matcher(clean);
        if (m1.find()) return safeInt(m1.group(1));

        Matcher m2 = COUNT_PCS.matcher(clean);
        if (m2.find()) return safeInt(m2.group(1));

        return -1;
    }

    private int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    private String trimTrailingPunct(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (!t.isEmpty()) {
            char c = t.charAt(t.length() - 1);
            if (c == '!' || c == '.' || c == ',' || c == ';' || c == ':' || c == ')' || c == ']' || c == '»')
                t = t.substring(0, t.length() - 1).trim();
            else break;
        }
        return t;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String t = stripFormatting(s).toLowerCase().trim();
        if (t.isEmpty()) return "";
        StringBuilder out = new StringBuilder(t.length());
        boolean sp = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ws = c <= 32;
            if (ws) {
                if (!sp) out.append(' ');
                sp = true;
                continue;
            }
            sp = false;
            if (c == '!' || c == '.' || c == ',' || c == ';' || c == ':') continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    private long parseDigitsToLong(String raw) {
        long v = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') v = v * 10L + (long) (c - '0');
        }
        return v;
    }

    private void updateMouse() {
        double sx = mc.getWindow().getScaledWidth();
        double sy = mc.getWindow().getScaledHeight();
        double wx = mc.getWindow().getWidth();
        double wy = mc.getWindow().getHeight();
        mouseX = (int) (mc.mouse.getX() * sx / wx);
        mouseY = (int) (mc.mouse.getY() * sy / wy);
    }

    private void updateHoverMeta(GenericContainerScreen screen, int guiLeft, int guiTop, long now) {
        Slot s = findAuctionSlotUnderMouse(screen, guiLeft, guiTop, mouseX, mouseY);
        if (s == null) return;
        ItemStack st = s.getStack();
        if (st.isEmpty()) return;
        lastHover = makeSnapFromStack(st, now);
    }

    private Slot findAuctionSlotUnderMouse(GenericContainerScreen screen, int guiLeft, int guiTop, int mx, int my) {
        if (mc.player == null) return null;
        int lx = mx - guiLeft;
        int ly = my - guiTop;

        List<Slot> slots = screen.getScreenHandler().slots;
        for (Slot s : slots) {
            if (s.inventory == mc.player.getInventory()) continue;
            if (s.y >= IGNORE_CONTROL_ROW_Y) continue;
            int sx = s.x;
            int sy = s.y;
            if (lx >= sx && lx < sx + 16 && ly >= sy && ly < sy + 16) return s;
        }
        return null;
    }

    private SnapMeta makeSnapFromStack(ItemStack st, long now) {
        ItemStack icon = st.copy();
        int price = getTotalPrice(st);
        String name = safeName(st);
        String nameNorm = normalizeName(name);
        String nameKey = st.getItem().toString() + "|" + nameNorm;
        int count = Math.max(1, st.getCount());
        return new SnapMeta(nameKey, name, nameNorm, icon, count, price, now);
    }

    private String safeName(ItemStack s) {
        try {
            return s.getName().getString();
        } catch (Throwable t) {
            return "";
        }
    }

    private int getTotalPrice(ItemStack stack) {
        int p = -1;
        try {
            p = priceParser.getPrice(stack);
        } catch (Throwable ignored) {
        }
        if (p >= 0) return p;
        return extractTotalPriceFallback(stack);
    }

    private int extractTotalPriceFallback(ItemStack stack) {
        try {
            int count = Math.max(1, stack.getCount());
            List<String> lines = collectTooltipLines(stack);
            String blob = String.join(" ", lines);
            return findTotalPriceInText(blob, count);
        } catch (Throwable t) {
            return -1;
        }
    }

    private int findTotalPriceInText(String s, int count) {
        if (s == null || s.isEmpty()) return -1;

        String lower = s.toLowerCase();
        Matcher m = NUM_PATTERN.matcher(s);

        int bestScore = -1;
        long best = -1;

        while (m.find()) {
            int start = m.start(1);
            int end = m.end(1);

            long val = parseDigitsToLong(m.group(1));
            if (val <= 0) continue;

            long mul = readSuffixMultiplier(lower, end);
            if (mul != 1L) val *= mul;

            int cs = Math.max(0, start - 52);
            int ce = Math.min(lower.length(), end + 52);
            String ctx = lower.substring(cs, ce);

            if (!hasPriceKeyword(ctx)) continue;

            boolean per = ctx.contains("за шт") || ctx.contains("/шт") || ctx.contains("шт.") || ctx.contains(" per ")
                    || ctx.contains(" each ") || ctx.contains("за 1") || ctx.contains("за шту");

            boolean total = ctx.contains("всего") || ctx.contains("итого") || ctx.contains("total") || ctx.contains("сумм") || ctx.contains("общ");

            int score = 3;
            if (total) score += 3;
            if (per) score += 1;

            long totalVal = per ? val * (long) count : val;
            if (totalVal <= 0) continue;

            if (score > bestScore || (score == bestScore && totalVal > best)) {
                bestScore = score;
                best = totalVal;
            }
        }

        if (best <= 0) return -1;
        if (best > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) best;
    }

    private boolean hasPriceKeyword(String ctx) {
        return ctx.contains("цена") || ctx.contains("price") || ctx.contains("стоим")
                || ctx.contains("руб") || ctx.contains("монет") || ctx.contains("coins") || ctx.contains("коин")
                || ctx.contains("buy") || ctx.contains("куп") || ctx.contains("$") || ctx.contains("₽");
    }

    private long readSuffixMultiplier(String lower, int end) {
        if (end >= lower.length()) return 1L;
        char c0 = lower.charAt(end);
        char c1 = (end + 1 < lower.length()) ? lower.charAt(end + 1) : 0;
        if (c0 == 'k' || c0 == 'к') return (c1 == 'k' || c1 == 'к') ? 1_000_000L : 1_000L;
        if (c0 == 'm' || c0 == 'м') return 1_000_000L;
        return 1L;
    }

    private List<String> collectTooltipLines(ItemStack stack) {
        ArrayList<String> api = tryCollectTooltipApi(stack);
        if (api != null && !api.isEmpty()) {
            for (int i = 0; i < api.size(); i++) api.set(i, stripFormatting(api.get(i)));
            return api;
        }

        ArrayList<String> out = new ArrayList<>(8);
        out.add(safeName(stack));
        for (int i = 0; i < out.size(); i++) out.set(i, stripFormatting(out.get(i)));
        return out;
    }

    private ArrayList<String> tryCollectTooltipApi(ItemStack stack) {
        try {
            if (!tooltipInit) initTooltipApi(stack);
            if (mGetTooltip == null || tooltipArgs == null) return null;

            Object[] args = new Object[tooltipArgs.length];
            for (int i = 0; i < tooltipArgs.length; i++) args[i] = tooltipArgs[i].value(mc.player);

            Object res = mGetTooltip.invoke(stack, args);
            if (!(res instanceof List<?> list)) return null;

            ArrayList<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Text t) out.add(t.getString());
                else if (o != null) out.add(String.valueOf(o));
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void initTooltipApi(ItemStack stack) {
        tooltipInit = true;
        if (stack == null) return;

        Method[] ms = stack.getClass().getMethods();
        for (Method m : ms) {
            if (!"getTooltip".equals(m.getName())) continue;
            if (!List.class.isAssignableFrom(m.getReturnType())) continue;

            Class<?>[] pts = m.getParameterTypes();
            TooltipArg[] plan = buildTooltipPlan(pts);
            if (plan == null) continue;

            try {
                Object[] args = new Object[plan.length];
                for (int i = 0; i < plan.length; i++) args[i] = plan[i].value(mc.player);
                Object res = m.invoke(stack, args);
                if (res instanceof List<?>) {
                    mGetTooltip = m;
                    tooltipArgs = plan;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        mGetTooltip = null;
        tooltipArgs = null;
    }

    private TooltipArg[] buildTooltipPlan(Class<?>[] pts) {
        if (pts == null) return null;
        TooltipArg[] out = new TooltipArg[pts.length];

        for (int i = 0; i < pts.length; i++) {
            Class<?> pt = pts[i];
            if (pt == null) return null;

            if (mc.player != null && pt.isAssignableFrom(mc.player.getClass())) {
                out[i] = TooltipArg.player();
                continue;
            }

            if (pt == boolean.class || pt == Boolean.class) {
                out[i] = TooltipArg.boolFalse();
                continue;
            }

            if (pt == int.class || pt == Integer.class) {
                out[i] = TooltipArg.intZero();
                continue;
            }

            if (pt.isEnum()) {
                Object pick = pickEnum(pt, "NORMAL", "DEFAULT", "BASIC", "REGULAR");
                out[i] = TooltipArg.fixed(pick);
                continue;
            }

            Object st = pickStatic(pt, "DEFAULT", "NORMAL", "BASIC", "REGULAR", "STANDARD");
            if (st != null) {
                out[i] = TooltipArg.fixed(st);
                continue;
            }

            if (pt.isInterface()) {
                out[i] = TooltipArg.proxy(pt);
                continue;
            }

            out[i] = TooltipArg.fixed(null);
        }

        return out;
    }

    private Object pickStatic(Class<?> type, String... names) {
        try {
            for (String n : names) {
                try {
                    Field f = type.getField(n);
                    if (!type.isAssignableFrom(f.getType())) continue;
                    return f.get(null);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object pickEnum(Class<?> enumType, String... prefer) {
        try {
            Object[] cs = enumType.getEnumConstants();
            if (cs == null || cs.length == 0) return null;

            for (String p : prefer) {
                if (p == null) continue;
                for (Object c : cs) {
                    if (c != null && p.equalsIgnoreCase(String.valueOf(c))) return c;
                }
            }
            return cs[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void pushBuy(BuyEntry e) {
        BuyEntry last = buys.peekLast();
        if (last != null && last.key.equals(e.key) && e.timeMs - last.timeMs <= 1600L) {
            last.count += e.count;
            last.timeMs = e.timeMs;
            if (last.totalPrice < 0 && e.totalPrice >= 0) last.totalPrice = e.totalPrice;
            if ((last.icon == null || last.icon.isEmpty()) && e.icon != null && !e.icon.isEmpty()) last.icon = e.icon;
            if (last.name.isEmpty() && !e.name.isEmpty()) last.name = e.name;
            return;
        }

        buys.addLast(e);
        while (buys.size() > BUY_MAX) buys.pollFirst();
    }

    private void pruneBuys(long now) {
        while (!buys.isEmpty()) {
            BuyEntry f = buys.peekFirst();
            if (f == null) break;
            if (now - f.timeMs > BUY_KEEP_MS) buys.pollFirst();
            else break;
        }
    }

    private boolean drawBoughtPanel(DrawContext ctx, int guiLeft, int guiTop, int bgW, boolean clickEdge, boolean lmb, boolean releaseEdge) {
        TextRenderer tr = mc.textRenderer;

        int leftX = guiLeft - PANEL_W - PANEL_PAD;
        int rightX = guiLeft + bgW + PANEL_PAD;

        int x;
        if (leftX >= 4) x = leftX;
        else if (rightX + PANEL_W <= mc.getWindow().getScaledWidth() - 4) x = rightX;
        else x = 4;

        int y = Math.max(6, guiTop - 14);
        int screenH = mc.getWindow().getScaledHeight();

        int total = buys.size();
        int maxLinesFit = Math.max(1, (screenH - y - 12 - HEADER_H) / ENTRY_H);
        int visibleLines = Math.min(PANEL_MAX, Math.min(Math.max(1, maxLinesFit), total == 0 ? PANEL_MAX : maxLinesFit));
        int h = HEADER_H + visibleLines * ENTRY_H + 8;

        if (y + h > screenH - 6) y = Math.max(6, screenH - 6 - h);

        int listX = x + 8;
        int listY = y + HEADER_H;
        int listW = PANEL_W - 16;
        int listH = visibleLines * ENTRY_H + 2;

        boolean hoverPanel = inside(mouseX, mouseY, x, y, PANEL_W, h);
        boolean hoverList = inside(mouseX, mouseY, listX, listY, listW, listH);

        buyDrag = false;

        double wheel = readWheelDelta(hoverList);
        if (wheel != 0.0) buyScrollPx -= (float) (wheel * ENTRY_H);

        int maxScrollPx = Math.max(0, (total - visibleLines) * ENTRY_H);
        buyScrollPx = MathHelper.clamp(buyScrollPx, 0f, (float) maxScrollPx);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 220);

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, PANEL_W, h)
                .round(9).softness(1)
                .outlineColor(0x40181818).thickness(0.7f)
                .color(0xC0101010)
                .build());

        drawTextAny(ctx, tr, "История покупок", x + 12, y + 8, 0xFFFFFFFF);

        String btn = "Очистить";
        int btnW = tr.getWidth(btn) + 12;
        int btnH = 14;
        int bx = x + PANEL_W - 12 - btnW;
        int by = y + 6;

        boolean hoverBtn = inside(mouseX, mouseY, bx, by, btnW, btnH);
        int btnCol = hoverBtn ? 0x702A2A2A : 0x50202020;

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), bx, by, btnW, btnH)
                .round(6).softness(1)
                .outlineColor(0x40181818).thickness(0.6f)
                .color(btnCol)
                .build());

        drawTextAny(ctx, tr, btn, bx + 6, by + 3, 0xEFFFFFFF);

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x + 10, y + HEADER_H - 2, PANEL_W - 20, 1)
                .color(0x33FFFFFF).build());

        if (clickEdge && hoverBtn) {
            buys.clear();
            buyScrollPx = 0f;
            buyDrag = false;
            ctx.getMatrices().pop();
            return true;
        }

        if (buys.isEmpty()) {
            String t = "пока пусто";
            float sc = 1.25f;
            int tw = tr.getWidth(t);
            int tx = x + (PANEL_W - (int) (tw * sc)) / 2;
            int ty = y + HEADER_H + 18;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(tx, ty, 0);
            ctx.getMatrices().scale(sc, sc, 1f);
            drawTextAny(ctx, tr, t, 0, 0, TEXT_DIM);
            ctx.getMatrices().pop();

            int boxW = PANEL_W - 70;
            int boxH = h - HEADER_H - 54;
            if (boxW < 40) boxW = 40;
            if (boxH < 30) boxH = 30;

            int boxX = x + (PANEL_W - boxW) / 2;
            int boxY = y + HEADER_H + 34;

            drawUtyaGif(ctx, boxX, boxY, boxW, boxH, System.currentTimeMillis());

            ctx.getMatrices().pop();
            return hoverPanel;
        }

        int sbW = 3;
        int sbX = x + PANEL_W - 7;
        int sbY = listY + 2;
        int sbH = Math.max(1, listH - 4);

        rectangle.render(ShapeProperties.create(ctx.getMatrices(), sbX, sbY, sbW, sbH)
                .round(2).softness(1)
                .color(0x30101010)
                .build());

        int thumbH;
        int thumbY;
        if (maxScrollPx <= 0) {
            thumbH = sbH;
            thumbY = sbY;
        } else {
            float ratio = MathHelper.clamp((float) visibleLines / (float) Math.max(1, total), 0f, 1f);
            thumbH = MathHelper.clamp((int) ((float) sbH * ratio), 10, sbH);
            float pos = buyScrollPx / (float) maxScrollPx;
            thumbY = sbY + (int) ((float) (sbH - thumbH) * MathHelper.clamp(pos, 0f, 1f));
        }

        int thumbCol = hoverList ? 0xA0FFFFFF : 0x70FFFFFF;
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), sbX, thumbY, sbW, thumbH)
                .round(2).softness(1)
                .color(thumbCol)
                .build());

        int skip = (int) (buyScrollPx / (float) ENTRY_H);

        int yy = y + HEADER_H + 2;
        int shown = 0;

        Iterator<BuyEntry> it = buys.descendingIterator();
        while (it.hasNext() && skip > 0) {
            it.next();
            skip--;
        }

        while (it.hasNext() && shown < visibleLines) {
            BuyEntry be = it.next();

            int iconX = x + 12;
            int iconY = yy + 5;
            if (be.icon != null && !be.icon.isEmpty()) drawItemAny(ctx, be.icon, iconX, iconY);

            int textX = x + 36;
            int right = x + PANEL_W - 12;

            String nm = be.name.isEmpty() ? be.key : be.name;
            String left = "+" + be.count + " " + nm;

            int priceW = 0;
            String digits = null;
            if (be.totalPrice >= 0) {
                digits = formatNumberWithDots(be.totalPrice);
                priceW = tr.getWidth("$") + tr.getWidth(digits);
            }

            int maxLeftW = Math.max(0, (right - textX) - (priceW > 0 ? priceW + 8 : 0));
            String line1 = trimToWidth(tr, left, maxLeftW);

            drawTextAny(ctx, tr, line1, textX, yy + 10, 0xEFFFFFFF);

            if (be.totalPrice >= 0 && digits != null) {
                int wDollar = tr.getWidth("$");
                int wDigits = tr.getWidth(digits);
                int px = right - (wDollar + wDigits);
                drawTextAny(ctx, tr, "$", px, yy + 4, PRICE_DOLLAR);
                drawTextAny(ctx, tr, digits, px + wDollar, yy + 4, PRICE_NUM);
            }

            String time = formatTime(be.timeMs);
            int timeW = tr.getWidth(time);
            drawTextAny(ctx, tr, time, right - timeW, yy + 15, 0xFFFFFFFF);

            yy += ENTRY_H;
            shown++;
        }

        ctx.getMatrices().pop();
        return hoverPanel || hoverList || hoverBtn;
    }

    private void drawUtyaGif(DrawContext ctx, int boxX, int boxY, int boxW, int boxH, long now) {
        if (boxW <= 6 || boxH <= 6) return;

        utya.ensureLoaded(mc);
        Identifier frame = utya.frame(now);
        if (frame == null) return;

        int iw = Math.max(1, utya.w);
        int ih = Math.max(1, utya.h);

        int pad = 16;
        int availW = Math.max(1, boxW - pad * 2);
        int availH = Math.max(1, boxH - pad * 2);

        float s = Math.min((float) availW / (float) iw, (float) availH / (float) ih);
        s *= 0.72f;

        int dw = Math.max(1, (int) ((float) iw * s));
        int dh = Math.max(1, (int) ((float) ih * s));

        int dx = boxX + (boxW - dw) / 2;
        int dy = boxY + (boxH - dh) / 2;

        float sx = (float) dw / (float) iw;
        float sy = (float) dh / (float) ih;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(dx, dy, 0);
        ctx.getMatrices().scale(sx, sy, 1f);
        drawTexAny(ctx, frame, 0, 0, iw, ih, iw, ih);
        ctx.getMatrices().pop();
    }

    private double readWheelDelta(boolean consume) {
        if (!consume) return 0.0;

        try {
            Object mouse = mc.mouse;
            if (!mouseWheelInit) initMouseWheelAccess(mouse);

            if (mMouseWheelGetter != null) {
                Object v = mMouseWheelGetter.invoke(mouse);
                if (v instanceof Number n) return n.doubleValue();
                return 0.0;
            }

            if (fMouseWheel != null) {
                Object v = fMouseWheel.get(mouse);
                double dv = v instanceof Number n ? n.doubleValue() : 0.0;
                if (dv != 0.0) {
                    Class<?> t = fMouseWheel.getType();
                    if (t == double.class) fMouseWheel.setDouble(mouse, 0.0);
                    else if (t == float.class) fMouseWheel.setFloat(mouse, 0f);
                    else if (t == int.class) fMouseWheel.setInt(mouse, 0);
                }
                return dv;
            }
        } catch (Throwable ignored) {
        }

        return 0.0;
    }

    private void initMouseWheelAccess(Object mouse) {
        mouseWheelInit = true;
        if (mouse == null) return;

        try {
            for (Method m : mouse.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                Class<?> rt = m.getReturnType();
                if (!(rt == double.class || rt == float.class || rt == int.class)) continue;
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (n.contains("wheel") || (n.contains("scroll") && (n.contains("y") || n.contains("delta")))) {
                    mMouseWheelGetter = m;
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> c = mouse.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    Class<?> t = f.getType();
                    if (!(t == double.class || t == float.class || t == int.class)) continue;
                    String n = f.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("eventdeltawheel") || n.contains("deltawheel") || n.contains("wheel") || (n.contains("scroll") && (n.contains("y") || n.contains("delta")))) {
                        f.setAccessible(true);
                        fMouseWheel = f;
                        return;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawItemAny(DrawContext ctx, ItemStack stack, int x, int y) {
        try {
            if (mDrawItem == null) {
                try {
                    mDrawItem = DrawContext.class.getMethod("drawItem", ItemStack.class, int.class, int.class);
                } catch (Throwable ignored) {
                    mDrawItem = null;
                }
            }
            if (mDrawItem != null) mDrawItem.invoke(ctx, stack, x, y);
        } catch (Throwable ignored) {
        }
    }

    private void drawTextAny(DrawContext ctx, TextRenderer tr, String text, int x, int y, int color) {
        try {
            ctx.drawTextWithShadow(tr, text, x, y, color);
        } catch (Throwable ignored) {
            try {
                Method m = ctx.getClass().getMethod("drawTextWithShadow", TextRenderer.class, String.class, int.class, int.class, int.class);
                m.invoke(ctx, tr, text, x, y, color);
            } catch (Throwable ignored2) {
            }
        }
    }

    private void drawTexAny(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int texW, int texH) {
        if (tex == null) return;

        try {
            if (!texInit) initDrawTexture();
            if (mDrawTex == null) return;

            RenderSystem.enableBlend();

            if (texMode == 1) {
                mDrawTex.invoke(ctx, tex, x, y, 0, 0, w, h, texW, texH);
                return;
            }
            if (texMode == 2) {
                mDrawTex.invoke(ctx, tex, x, y, 0f, 0f, w, h, texW, texH);
                return;
            }
            if (texMode == 3) {
                mDrawTex.invoke(ctx, tex, x, y, w, h);
                return;
            }
            if (texMode == 4) {
                Object fn = texFirstArg != null ? texFirstArg : guiLayerFn;
                if (fn == null) return;
                mDrawTex.invoke(ctx, fn, tex, x, y, 0f, 0f, w, h, texW, texH);
                return;
            }
            if (texMode == 5) {
                Object layer = guiLayerFor(tex);
                if (layer == null) return;
                mDrawTex.invoke(ctx, layer, tex, x, y, 0f, 0f, w, h, texW, texH);
            }
        } catch (Throwable ignored) {
        }
    }

    private void initDrawTexture() {
        texInit = true;
        mDrawTex = null;
        texMode = 0;
        texFirstArg = null;
        guiLayerFn = null;
        mGuiLayerFactory = null;

        Method best = null;
        int bestMode = 0;

        try {
            Method[] ms = DrawContext.class.getMethods();
            for (Method m : ms) {
                if (!"drawTexture".equals(m.getName())) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p == null) continue;

                if (p.length == 9 && p[0] == Identifier.class && p[1] == int.class && p[2] == int.class && p[3] == int.class && p[4] == int.class) {
                    best = m;
                    bestMode = 1;
                    break;
                }
                if (p.length == 9 && p[0] == Identifier.class && p[1] == int.class && p[2] == int.class && p[3] == float.class && p[4] == float.class) {
                    best = m;
                    bestMode = 2;
                    break;
                }
                if (p.length == 5 && p[0] == Identifier.class && p[1] == int.class && p[2] == int.class && p[3] == int.class && p[4] == int.class) {
                    if (best == null) {
                        best = m;
                        bestMode = 3;
                    }
                    continue;
                }

                if (p.length == 10 && p[1] == Identifier.class && p[2] == int.class && p[3] == int.class) {
                    if (Function.class.isAssignableFrom(p[0])) {
                        best = m;
                        bestMode = 4;
                        break;
                    }
                    if ("net.minecraft.client.render.RenderLayer".equals(p[0].getName())) {
                        best = m;
                        bestMode = 5;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        if (best != null) {
            mDrawTex = best;
            texMode = bestMode;
        }

        if (texMode == 4) {
            try {
                if (guiLayerFn == null) guiLayerFn = AuctionHelper::guiLayerFor;
                texFirstArg = guiLayerFn;
            } catch (Throwable ignored) {
                texFirstArg = null;
            }
        }
    }

    private static Object guiLayerFor(Identifier tex) {
        try {
            if (mGuiLayerFactory == null) {
                Class<?> rl = Class.forName("net.minecraft.client.render.RenderLayer");
                Method[] ms = rl.getMethods();
                Method pick = null;
                for (Method m : ms) {
                    if (!Modifier.isStatic(m.getModifiers())) continue;
                    if (m.getParameterCount() != 1) continue;
                    if (m.getParameterTypes()[0] != Identifier.class) continue;
                    if (!rl.isAssignableFrom(m.getReturnType())) continue;
                    String n = m.getName().toLowerCase(Locale.ROOT);
                    if (n.contains("gui") && (n.contains("textured") || n.contains("texture"))) {
                        pick = m;
                        break;
                    }
                    if (pick == null && n.contains("gui")) pick = m;
                }
                mGuiLayerFactory = pick;
            }

            if (mGuiLayerFactory == null) return null;
            return mGuiLayerFactory.invoke(null, tex);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int pulsing(int color) {
        long now = System.currentTimeMillis();
        float t = (now % PULSE_MS) / (float) PULSE_MS;
        float wave = 0.5f - 0.5f * MathHelper.cos(t * 6.2831855f);
        float a = MathHelper.clamp(PULSE_MIN_ALPHA + (PULSE_MAX_ALPHA - PULSE_MIN_ALPHA) * wave, 0.0f, 1.0f);
        return ColorAssist.multAlpha(color, a);
    }

    private void highlightSlotAbs(DrawContext ctx, int guiLeft, int guiTop, Slot slot, int color, int pad) {
        int x = guiLeft + slot.x + pad;
        int y = guiTop + slot.y + pad;
        int w = 16 - pad - pad;
        int h = 16 - pad - pad;
        if (w <= 0 || h <= 0) return;
        rectangle.render(ShapeProperties.create(ctx.getMatrices(), x, y, w, h).color(color).build());
    }

    private boolean isAuctionScreen(GenericContainerScreen screen) {
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Поиск");
    }

    private void resetCalcState() {
        cheapestSlot = null;
        costEffectiveSlot = null;
        lastSyncId = -1;
        lastRecalcMs = 0L;
        dirty = false;
        recalcQueued = false;
        script.cleanup();
        buyScrollPx = 0f;
        buyDrag = false;
        lastHover = null;
        lastClick = null;
        snaps.clear();
    }

    private String formatTime(long ms) {
        LocalTime lt = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime();
        return TIME_FMT.format(lt);
    }

    private boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private String stripFormatting(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private String trimToWidth(TextRenderer tr, String s, int maxW) {
        if (s == null) return "";
        if (maxW <= 0) return "";
        if (tr.getWidth(s) <= maxW) return s;
        String dots = "...";
        int dw = tr.getWidth(dots);
        if (dw >= maxW) return dots;
        int len = s.length();
        while (len > 0) {
            String sub = s.substring(0, len);
            if (tr.getWidth(sub) + dw <= maxW) return sub + dots;
            len--;
        }
        return dots;
    }

    private String formatNumberWithDots(int number) {
        if (number > -1000 && number < 1000) return String.valueOf(number);

        long n = number;
        boolean neg = n < 0;
        if (neg) n = -n;

        String s = String.valueOf(n);
        int len = s.length();

        StringBuilder b = new StringBuilder(len + (len / 3));
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) b.append('.');
            b.append(s.charAt(i));
        }

        return neg ? "-" + b : b.toString();
    }

    static class BuyEntry {
        final String key;
        @NonFinal String name;
        @NonFinal ItemStack icon;
        @NonFinal int count;
        @NonFinal int totalPrice;
        @NonFinal long timeMs;

        BuyEntry(String key, String name, ItemStack icon, int count, int totalPrice, long timeMs) {
            this.key = key;
            this.name = name == null ? "" : name;
            this.icon = icon;
            this.count = count;
            this.totalPrice = totalPrice;
            this.timeMs = timeMs;
        }
    }

    static class PendingIcon {
        final String key;
        @NonFinal String wantNorm;
        @NonFinal int count;
        @NonFinal long timeMs;

        PendingIcon(String key, String wantNorm, int count, long timeMs) {
            this.key = key == null ? "" : key;
            this.wantNorm = wantNorm == null ? "" : wantNorm;
            this.count = count;
            this.timeMs = timeMs;
        }
    }

    static class SnapMeta {
        final String nameKey;
        final String displayName;
        final String nameNorm;
        final ItemStack icon;
        final int count;
        final int price;
        final long timeMs;

        SnapMeta(String nameKey, String displayName, String nameNorm, ItemStack icon, int count, int price, long timeMs) {
            this.nameKey = nameKey == null ? "" : nameKey;
            this.displayName = displayName == null ? "" : displayName;
            this.nameNorm = nameNorm == null ? "" : nameNorm;
            this.icon = icon;
            this.count = count;
            this.price = price;
            this.timeMs = timeMs;
        }
    }

    static class PurchaseEvent {
        final String itemName;
        final int price;
        final int count;

        PurchaseEvent(String itemName, int price, int count) {
            this.itemName = itemName == null ? "" : itemName;
            this.price = price;
            this.count = count;
        }
    }

    static class TooltipArg {
        final int kind;
        final Object fixed;
        final Class<?> iface;

        TooltipArg(int kind, Object fixed, Class<?> iface) {
            this.kind = kind;
            this.fixed = fixed;
            this.iface = iface;
        }

        static TooltipArg fixed(Object v) {
            return new TooltipArg(0, v, null);
        }

        static TooltipArg player() {
            return new TooltipArg(1, null, null);
        }

        static TooltipArg boolFalse() {
            return new TooltipArg(2, null, null);
        }

        static TooltipArg intZero() {
            return new TooltipArg(3, null, null);
        }

        static TooltipArg proxy(Class<?> iface) {
            return new TooltipArg(4, null, iface);
        }

        Object value(Object player) {
            if (kind == 1) return player;
            if (kind == 2) return false;
            if (kind == 3) return 0;
            if (kind == 4) return makeProxy(iface);
            return fixed;
        }

        Object makeProxy(Class<?> iface) {
            try {
                return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class || rt == Boolean.class) return false;
                    if (rt == int.class || rt == Integer.class) return 0;
                    if (rt == float.class || rt == Float.class) return 0f;
                    if (rt == double.class || rt == Double.class) return 0.0;
                    return null;
                });
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    static class GifAnim {
        final Identifier src;
        @NonFinal boolean loaded = false;
        @NonFinal int w = 0;
        @NonFinal int h = 0;
        @NonFinal long totalMs = 0L;
        @NonFinal long[] ends;
        @NonFinal Identifier[] frames;

        GifAnim(Identifier src) {
            this.src = src;
        }

        void ensureLoaded(net.minecraft.client.MinecraftClient mc) {
            if (loaded && frames != null && frames.length > 0) return;
            loaded = true;

            try {
                if (mc == null || mc.getResourceManager() == null) return;

                InputStream in = null;
                try {
                    var opt = mc.getResourceManager().getResource(src);
                    if (opt == null || opt.isEmpty()) return;
                    in = opt.get().getInputStream();
                    if (in == null) return;
                    decode(mc, in);
                } finally {
                    try {
                        if (in != null) in.close();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        Identifier frame(long nowMs) {
            if (frames == null || frames.length == 0) return null;
            if (frames.length == 1) return frames[0];
            if (ends == null || totalMs <= 0L) return frames[0];

            long t = nowMs % totalMs;
            int lo = 0;
            int hi = ends.length - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (t < ends[mid]) hi = mid;
                else lo = mid + 1;
            }
            int idx = MathHelper.clamp(lo, 0, frames.length - 1);
            Identifier id = frames[idx];
            if (id == null) return frames[0];
            return id;
        }

        private void decode(net.minecraft.client.MinecraftClient mc, InputStream in) throws Exception {
            ImageInputStream iis = null;
            try {
                iis = ImageIO.createImageInputStream(in);
                if (iis == null) return;

                Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName("gif");
                ImageReader r = null;
                if (it != null && it.hasNext()) r = it.next();
                if (r == null) {
                    BufferedImage one = ImageIO.read(iis);
                    if (one == null) return;

                    int tw = one.getWidth();
                    int th = one.getHeight();
                    int maxSide = Math.max(tw, th);
                    if (maxSide > 256) {
                        float k = 256f / (float) maxSide;
                        tw = Math.max(1, Math.round((float) tw * k));
                        th = Math.max(1, Math.round((float) th * k));
                    }

                    BufferedImage scaled = (one.getWidth() == tw && one.getHeight() == th) ? one : scale(one, tw, th);

                    w = scaled.getWidth();
                    h = scaled.getHeight();
                    frames = new Identifier[]{registerFrame(mc, src, 0, scaled)};
                    if (frames[0] == null) frames[0] = Identifier.of("mre", "gif/fallback");
                    ends = new long[]{1000L};
                    totalMs = 1000L;
                    return;
                }

                r.setInput(iis, false, false);

                int count;
                try {
                    count = r.getNumImages(true);
                } catch (Throwable t) {
                    count = 1;
                }
                if (count <= 0) count = 1;

                int[] logical = readLogicalSize(r);
                BufferedImage firstRaw = r.read(0);
                if (firstRaw == null) return;

                int canvasW = logical != null ? Math.max(1, logical[0]) : Math.max(1, firstRaw.getWidth());
                int canvasH = logical != null ? Math.max(1, logical[1]) : Math.max(1, firstRaw.getHeight());

                int tw = canvasW;
                int th = canvasH;
                int maxSide = Math.max(tw, th);
                if (maxSide > 256) {
                    float k = 256f / (float) maxSide;
                    tw = Math.max(1, Math.round((float) tw * k));
                    th = Math.max(1, Math.round((float) th * k));
                }

                w = tw;
                h = th;

                BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);

                Identifier[] ids = new Identifier[count];
                long[] end = new long[count];

                long acc = 0L;

                int prevDisposal = 0;
                int prevX = 0;
                int prevY = 0;
                int prevW = 0;
                int prevH = 0;
                int[] prevSaved = null;

                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        if (prevDisposal == 1) clearRect(canvas, prevX, prevY, prevW, prevH);
                        else if (prevDisposal == 2 && prevSaved != null) restorePixels(canvas, prevSaved);
                    }

                    FrameInfo info = readFrameInfo(r, i);
                    BufferedImage img = (i == 0) ? firstRaw : r.read(i);
                    if (img == null) img = firstRaw;

                    int ix = info != null ? info.x : 0;
                    int iy = info != null ? info.y : 0;
                    int iw = info != null ? info.w : img.getWidth();
                    int ih = info != null ? info.h : img.getHeight();
                    int disposal = info != null ? info.disposal : 0;

                    int[] savedBefore = (disposal == 2) ? copyPixels(canvas) : null;

                    java.awt.Graphics2D g = canvas.createGraphics();
                    try {
                        g.drawImage(img, ix, iy, null);
                    } finally {
                        g.dispose();
                    }

                    BufferedImage full = copy(canvas);
                    if (full.getWidth() != tw || full.getHeight() != th) full = scale(full, tw, th);

                    Identifier id = registerFrame(mc, src, i, full);
                    if (id == null) id = (i > 0 ? ids[i - 1] : null);
                    ids[i] = id;

                    long delay = readDelayMsSafe(r, i);
                    if (delay <= 0L) delay = 70L;
                    acc += delay;
                    end[i] = acc;

                    prevDisposal = disposal;
                    prevX = ix;
                    prevY = iy;
                    prevW = iw;
                    prevH = ih;
                    prevSaved = savedBefore;
                }

                frames = ids;
                ends = end;
                totalMs = Math.max(1L, acc);
            } finally {
                try {
                    if (iis != null) iis.close();
                } catch (Throwable ignored) {
                }
            }
        }

        private BufferedImage copy(BufferedImage img) {
            if (img == null) return null;
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            try {
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
            return out;
        }

        private int[] copyPixels(BufferedImage img) {
            try {
                int w = img.getWidth();
                int h = img.getHeight();
                int[] px = new int[w * h];
                img.getRGB(0, 0, w, h, px, 0, w);
                return px;
            } catch (Throwable t) {
                return null;
            }
        }

        private void restorePixels(BufferedImage img, int[] px) {
            try {
                if (px == null) return;
                int w = img.getWidth();
                int h = img.getHeight();
                if (px.length < w * h) return;
                img.setRGB(0, 0, w, h, px, 0, w);
            } catch (Throwable ignored) {
            }
        }

        private void clearRect(BufferedImage img, int x, int y, int w, int h) {
            try {
                if (w <= 0 || h <= 0) return;
                int ix = Math.max(0, x);
                int iy = Math.max(0, y);
                int ax = Math.min(img.getWidth(), x + w);
                int ay = Math.min(img.getHeight(), y + h);
                int rw = ax - ix;
                int rh = ay - iy;
                if (rw <= 0 || rh <= 0) return;

                java.awt.Graphics2D g = img.createGraphics();
                try {
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fillRect(ix, iy, rw, rh);
                } finally {
                    g.dispose();
                }
            } catch (Throwable ignored) {
            }
        }

        private int[] readLogicalSize(ImageReader r) {
            try {
                var sm = r.getStreamMetadata();
                if (sm == null) return null;
                String fmt = sm.getNativeMetadataFormatName();
                if (fmt == null) return null;
                org.w3c.dom.Node root = sm.getAsTree(fmt);
                if (root == null) return null;

                org.w3c.dom.Node c = root.getFirstChild();
                while (c != null) {
                    String n = c.getNodeName();
                    if (n != null && n.toLowerCase(Locale.ROOT).contains("logical")) {
                        var a = c.getAttributes();
                        if (a != null) {
                            org.w3c.dom.Node w = a.getNamedItem("logicalScreenWidth");
                            org.w3c.dom.Node h = a.getNamedItem("logicalScreenHeight");
                            if (w != null && h != null) {
                                int iw = Integer.parseInt(w.getNodeValue());
                                int ih = Integer.parseInt(h.getNodeValue());
                                if (iw > 0 && ih > 0) return new int[]{iw, ih};
                            }
                        }
                    }
                    c = c.getNextSibling();
                }

                return null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        static class FrameInfo {
            final int x;
            final int y;
            final int w;
            final int h;
            final int disposal;

            FrameInfo(int x, int y, int w, int h, int disposal) {
                this.x = x;
                this.y = y;
                this.w = w;
                this.h = h;
                this.disposal = disposal;
            }
        }

        private FrameInfo readFrameInfo(ImageReader r, int index) {
            try {
                var meta = r.getImageMetadata(index);
                if (meta == null) return null;

                String fmt = meta.getNativeMetadataFormatName();
                if (fmt == null) return null;

                org.w3c.dom.Node root = meta.getAsTree(fmt);
                if (root == null) return null;

                int x = 0;
                int y = 0;
                int w = 0;
                int h = 0;
                int disp = 0;

                org.w3c.dom.Node c = root.getFirstChild();
                while (c != null) {
                    String name = c.getNodeName();
                    if (name != null) {
                        String low = name.toLowerCase(Locale.ROOT);

                        if (low.contains("imagedescriptor")) {
                            var a = c.getAttributes();
                            if (a != null) {
                                org.w3c.dom.Node nx = a.getNamedItem("imageLeftPosition");
                                org.w3c.dom.Node ny = a.getNamedItem("imageTopPosition");
                                org.w3c.dom.Node nw = a.getNamedItem("imageWidth");
                                org.w3c.dom.Node nh = a.getNamedItem("imageHeight");
                                if (nx != null) x = safeParseInt(nx.getNodeValue());
                                if (ny != null) y = safeParseInt(ny.getNodeValue());
                                if (nw != null) w = safeParseInt(nw.getNodeValue());
                                if (nh != null) h = safeParseInt(nh.getNodeValue());
                            }
                        }

                        if (low.contains("graphiccontrolextension")) {
                            var a = c.getAttributes();
                            if (a != null) {
                                org.w3c.dom.Node dm = a.getNamedItem("disposalMethod");
                                if (dm != null) {
                                    String v = dm.getNodeValue();
                                    if (v != null) {
                                        String vv = v.toLowerCase(Locale.ROOT);
                                        if (vv.contains("restoretobackground")) disp = 1;
                                        else if (vv.contains("restoret previous") || vv.contains("restoret oprevious") || vv.contains("restoret o") || vv.contains("restoretoprevious")) disp = 2;
                                        else if (vv.contains("restoretoprevious")) disp = 2;
                                        else disp = 0;
                                    }
                                }
                            }
                        }
                    }
                    c = c.getNextSibling();
                }

                if (w <= 0 || h <= 0) return new FrameInfo(x, y, Math.max(1, w), Math.max(1, h), disp);
                return new FrameInfo(x, y, w, h, disp);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private int safeParseInt(String s) {
            try {
                if (s == null) return 0;
                return Integer.parseInt(s.trim());
            } catch (Throwable t) {
                return 0;
            }
        }

        private BufferedImage scale(BufferedImage img, int tw, int th) {
            try {
                if (img == null) return null;
                if (tw <= 0 || th <= 0) return img;
                if (img.getWidth() == tw && img.getHeight() == th) return img;

                BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.drawImage(img, 0, 0, tw, th, null);
                } finally {
                    g.dispose();
                }
                return out;
            } catch (Throwable ignored) {
                return img;
            }
        }

        private long readDelayMsSafe(ImageReader r, int index) {
            try {
                var meta = r.getImageMetadata(index);
                if (meta == null) return 0L;
                String fmt = meta.getNativeMetadataFormatName();
                if (fmt == null) return 0L;

                var root = meta.getAsTree(fmt);
                if (root == null) return 0L;

                return scanDelayFromNode(root);
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        private long scanDelayFromNode(org.w3c.dom.Node node) {
            if (node == null) return 0L;

            String name = node.getNodeName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains("graphic")) {
                var attrs = node.getAttributes();
                if (attrs != null) {
                    var a = attrs.getNamedItem("delayTime");
                    if (a != null) {
                        String v = a.getNodeValue();
                        if (v != null) {
                            try {
                                int cs = Integer.parseInt(v.trim());
                                if (cs < 0) cs = 0;
                                return (long) cs * 10L;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            }

            org.w3c.dom.Node c = node.getFirstChild();
            while (c != null) {
                long d = scanDelayFromNode(c);
                if (d > 0L) return d;
                c = c.getNextSibling();
            }
            return 0L;
        }

        private Identifier registerFrame(net.minecraft.client.MinecraftClient mc, Identifier src, int idx, BufferedImage img) {
            try {
                if (img == null) return null;

                NativeImage ni = toNative(img);
                if (ni == null) return null;

                NativeImageBackedTexture tex = new NativeImageBackedTexture(ni);
                Identifier id = Identifier.of("mre", "gif/" + safeBase(src) + "/" + idx);

                mc.getTextureManager().registerTexture(id, tex);
                return id;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private String safeBase(Identifier src) {
            try {
                String p = src.getPath();
                if (p == null) return "gif";
                int s = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
                String b = s >= 0 ? p.substring(s + 1) : p;
                int d = b.lastIndexOf('.');
                if (d > 0) b = b.substring(0, d);
                b = b.replaceAll("[^a-zA-Z0-9_\\-]+", "_");
                if (b.isEmpty()) b = "gif";
                return b;
            } catch (Throwable t) {
                return "gif";
            }
        }

        private NativeImage toNative(BufferedImage img) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(96 * 1024);
                ImageIO.write(img, "png", baos);
                byte[] png = baos.toByteArray();
                if (png == null || png.length == 0) return null;
                return readNative(png);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private NativeImage readNative(byte[] png) {
            if (png == null || png.length == 0) return null;

            try {
                Method m = NativeImage.class.getMethod("read", InputStream.class);
                Object o = m.invoke(null, new ByteArrayInputStream(png));
                if (o instanceof NativeImage ni) return ni;
            } catch (Throwable ignored) {
            }

            try {
                Method m = NativeImage.class.getMethod("read", byte[].class);
                Object o = m.invoke(null, (Object) png);
                if (o instanceof NativeImage ni) return ni;
            } catch (Throwable ignored) {
            }

            try {
                Method m = NativeImage.class.getMethod("read", ByteBuffer.class);
                Object o = m.invoke(null, ByteBuffer.wrap(png));
                if (o instanceof NativeImage ni) return ni;
            } catch (Throwable ignored) {
            }

            return null;
        }
    }
}
