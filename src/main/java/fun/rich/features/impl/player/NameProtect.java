package fun.rich.features.impl.player;

import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.events.render.TextFactoryEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NameProtect extends Module {

    static final String PROTECTED_NAME = "Protected";

    SelectSetting nickSetting = new SelectSetting("Ник", "Подменяет никнеймы")
            .value("Выкл", "Protected", "zxcnanoMB")
            .selected("Protected");

    BooleanSetting friendsSetting = new BooleanSetting("Друзья", "Скрывает никнеймы друзей").setValue(true);

    SelectSetting prefixSetting = new SelectSetting("Префикс", "Подменяет значение строки Ранг:")
            .value("Выкл", "Хелпер", "Ст.Хелпер", "Модер", "Стажёр")
            .selected("Выкл");

    SelectSetting coinsSetting = new SelectSetting("Монеты", "Подменяет значение строки Монет:")
            .value("Выкл", "0", "10 000", "50 000", "100 000", "250 000", "500 000", "1 000 000", "5 000 000", "50 000 000", "500 000 000")
            .selected("Выкл");

    SelectSetting tokensSetting = new SelectSetting("Токены", "Подменяет значение строки Токенов:")
            .value("Выкл", "5 000", "10 000", "250 000", "500 000", "545 000")
            .selected("Выкл");

    SelectSetting shardsSetting = new SelectSetting("Черепки", "Подменяет значение строки Черепков:")
            .value("Выкл", "0", "10", "25", "50", "100", "135", "250", "500", "1000", "5000", "10000", "50000")
            .selected("Выкл");

    SelectSetting killsSetting = new SelectSetting("Убийства", "Подменяет значение строки Убийств:")
            .value("Выкл", "0", "1", "10", "25", "50", "100", "250", "500", "1000")
            .selected("Выкл");

    SelectSetting deathsSetting = new SelectSetting("Смерти", "Подменяет значение строки Смертей:")
            .value("Выкл", "0", "1", "5", "10", "25", "50", "100", "250", "500", "1000")
            .selected("Выкл");

    SelectSetting hoursSetting = new SelectSetting("Часы", "Подменяет значение строки Наиграно:")
            .value("Выкл", "1", "15", "40", "100", "125")
            .selected("Выкл");

    boolean awaitRankValue;
    long awaitRankAt;

    boolean awaitCoinsValue;
    long awaitCoinsAt;

    boolean awaitTokensValue;
    long awaitTokensAt;

    boolean awaitShardsValue;
    long awaitShardsAt;

    boolean awaitKillsValue;
    long awaitKillsAt;

    boolean awaitDeathsValue;
    long awaitDeathsAt;

    boolean awaitHoursValue;
    long awaitHoursAt;

    public NameProtect() {
        super("NameProtect", "Name Protect", ModuleCategory.PLAYER);
        setup(nickSetting, friendsSetting, prefixSetting, coinsSetting, tokensSetting, shardsSetting, killsSetting, deathsSetting, hoursSetting);
    }

    @EventHandler
    public void onTextFactory(TextFactoryEvent e) {
        String nickReplacement = selectedNickname();

        if (!nickReplacement.isEmpty()) {
            String my = safe(mc.getSession().getUsername());
            if (!my.isEmpty()) e.replaceText(my, nickReplacement);

            if (friendsSetting.isValue()) {
                FriendUtils.getFriends().forEach(friend -> {
                    String fn = safe(friend.getName());
                    if (!fn.isEmpty()) e.replaceText(fn, nickReplacement);
                });
            }
        }

        String raw = readEventText(e);
        if (raw == null || raw.isEmpty()) return;

        String noFmt = stripFormatting(raw);
        if (noFmt.isEmpty()) return;

        long now = System.currentTimeMillis();

        handleSpoof(e, now, raw, noFmt, "Ранг:", "Rank:", selectedPrefix(), 450,
                () -> awaitRankValue, v -> awaitRankValue = v,
                () -> awaitRankAt, v -> awaitRankAt = v,
                ValueMode.TEXT);

        handleSpoof(e, now, raw, noFmt, "Монет:", "Coins:", selectedCoins(), 450,
                () -> awaitCoinsValue, v -> awaitCoinsValue = v,
                () -> awaitCoinsAt, v -> awaitCoinsAt = v,
                ValueMode.NUMBER_PUNCT);

        handleSpoof(e, now, raw, noFmt, "Токенов:", "Tokens:", selectedTokens(), 450,
                () -> awaitTokensValue, v -> awaitTokensValue = v,
                () -> awaitTokensAt, v -> awaitTokensAt = v,
                ValueMode.NUMBER_PUNCT);

        handleSpoof(e, now, raw, noFmt, "Черепков:", "Shards:", selectedShards(), 450,
                () -> awaitShardsValue, v -> awaitShardsValue = v,
                () -> awaitShardsAt, v -> awaitShardsAt = v,
                ValueMode.NUMBER_PUNCT);

        handleSpoof(e, now, raw, noFmt, "Убийств:", "Kills:", selectedKills(), 450,
                () -> awaitKillsValue, v -> awaitKillsValue = v,
                () -> awaitKillsAt, v -> awaitKillsAt = v,
                ValueMode.NUMBER_LEADING);

        handleSpoof(e, now, raw, noFmt, "Смертей:", "Deaths:", selectedDeaths(), 450,
                () -> awaitDeathsValue, v -> awaitDeathsValue = v,
                () -> awaitDeathsAt, v -> awaitDeathsAt = v,
                ValueMode.NUMBER_LEADING);

        handleSpoof(e, now, raw, noFmt, "Наиграно:", "Played:", selectedHours(), 450,
                () -> awaitHoursValue, v -> awaitHoursValue = v,
                () -> awaitHoursAt, v -> awaitHoursAt = v,
                ValueMode.HOURS);
    }

    enum ValueMode {
        TEXT,
        NUMBER_PUNCT,
        NUMBER_LEADING,
        HOURS
    }

    private interface BoolGet { boolean get(); }
    private interface BoolSet { void set(boolean v); }
    private interface LongGet { long get(); }
    private interface LongSet { void set(long v); }

    private void handleSpoof(TextFactoryEvent e,
                             long now,
                             String raw,
                             String noFmt,
                             String ruLabel,
                             String enLabel,
                             String replacement,
                             long ttlMs,
                             BoolGet awaitGet,
                             BoolSet awaitSet,
                             LongGet awaitAtGet,
                             LongSet awaitAtSet,
                             ValueMode mode) {

        String rep = safe(replacement);
        if (rep.isEmpty()) {
            awaitSet.set(false);
            awaitAtSet.set(0L);
            return;
        }

        String repNo = stripFormatting(rep);
        if (repNo.isEmpty()) return;

        if (awaitGet.get()) {
            if (now - awaitAtGet.get() <= ttlMs && isLikelyValue(noFmt, mode)) {
                applyValueReplace(e, raw, noFmt, rep, repNo, mode);
                awaitSet.set(false);
                awaitAtSet.set(0L);
                return;
            }
            awaitSet.set(false);
            awaitAtSet.set(0L);
        }

        int idx = indexOfIgnoreCase(noFmt, ruLabel);
        int labelLen = ruLabel.length();
        if (idx < 0) {
            idx = indexOfIgnoreCase(noFmt, enLabel);
            labelLen = enLabel.length();
        }
        if (idx < 0) return;

        int p = idx + labelLen;
        while (p < noFmt.length()) {
            char ch = noFmt.charAt(p);
            if (ch == ' ' || ch == '\t') p++;
            else break;
        }

        String tail = p <= noFmt.length() ? noFmt.substring(p) : "";
        tail = safe(tail);

        if (tail.isEmpty()) {
            awaitSet.set(true);
            awaitAtSet.set(now);
            return;
        }

        applyValueReplace(e, tail, stripFormatting(tail), rep, repNo, mode);
    }

    private static void applyValueReplace(TextFactoryEvent e, String rawPart, String noFmtPart, String rep, String repNo, ValueMode mode) {
        if (e == null) return;
        if (rawPart == null || rawPart.isEmpty()) return;
        if (noFmtPart == null || noFmtPart.isEmpty()) return;

        if (mode == ValueMode.NUMBER_PUNCT) {
            if (isNumericLike(noFmtPart)) {
                String normTail = normalizeNumber(noFmtPart);
                String normRep = normalizeNumber(repNo);
                if (!normTail.isEmpty() && normTail.equals(normRep)) return;
                String repFmt = formatAsOriginalNumber(repNo, noFmtPart);
                if (!stripFormatting(rawPart).equals(stripFormatting(repFmt))) e.replaceText(rawPart, repFmt);
                return;
            }
            return;
        }

        if (mode == ValueMode.NUMBER_LEADING) {
            String lead = leadingDigits(noFmtPart);
            if (lead.isEmpty()) return;
            String repLead = normalizeNumber(repNo);
            if (repLead.isEmpty()) repLead = repNo;
            if (normalizeNumber(lead).equals(normalizeNumber(repLead))) return;
            e.replaceText(lead, repLead);
            return;
        }

        if (mode == ValueMode.HOURS) {
            String tailH = normalizeHours(noFmtPart);
            String repH = normalizeHours(repNo);
            if (!tailH.isEmpty() && tailH.equals(repH)) return;
            String repFmt = formatAsOriginalHours(repNo, noFmtPart);
            if (!stripFormatting(rawPart).equals(stripFormatting(repFmt))) e.replaceText(rawPart, repFmt);
            return;
        }

        if (mode == ValueMode.TEXT) {
            if (stripFormatting(rawPart).equals(stripFormatting(rep))) return;
            e.replaceText(rawPart, rep);
        }
    }

    private String selectedNickname() {
        if (nickSetting.isSelected("Выкл")) return "";
        if (nickSetting.isSelected("Protected")) return PROTECTED_NAME;
        if (nickSetting.isSelected("zxcnanoMB")) return "zxcnanoMB";
        return "";
    }

    private String selectedPrefix() {
        if (prefixSetting.isSelected("Выкл")) return "";
        if (prefixSetting.isSelected("Хелпер")) return "§aХелпер§r";
        if (prefixSetting.isSelected("Ст.Хелпер")) return "§aСт.Хелпер§r";
        if (prefixSetting.isSelected("Модер")) return "§1Модер§r";
        if (prefixSetting.isSelected("Стажёр")) return "§aСтажёр§r";
        return "";
    }

    private String selectedCoins() {
        if (coinsSetting.isSelected("Выкл")) return "";
        if (coinsSetting.isSelected("0")) return "0";
        if (coinsSetting.isSelected("10 000")) return "10,000";
        if (coinsSetting.isSelected("50 000")) return "50,000";
        if (coinsSetting.isSelected("100 000")) return "100,000";
        if (coinsSetting.isSelected("250 000")) return "250,000";
        if (coinsSetting.isSelected("500 000")) return "500,000";
        if (coinsSetting.isSelected("1 000 000")) return "1,000,000";
        if (coinsSetting.isSelected("5 000 000")) return "5,000,000";
        if (coinsSetting.isSelected("50 000 000")) return "50,000,000";
        if (coinsSetting.isSelected("500 000 000")) return "500,000,000";
        return "";
    }

    private String selectedTokens() {
        if (tokensSetting.isSelected("Выкл")) return "";
        if (tokensSetting.isSelected("5 000")) return "5,000";
        if (tokensSetting.isSelected("10 000")) return "10,000";
        if (tokensSetting.isSelected("250 000")) return "250,000";
        if (tokensSetting.isSelected("500 000")) return "500,000";
        if (tokensSetting.isSelected("545 000")) return "545,000";
        return "";
    }

    private String selectedShards() {
        if (shardsSetting.isSelected("Выкл")) return "";
        if (shardsSetting.isSelected("0")) return "0";
        if (shardsSetting.isSelected("10")) return "10";
        if (shardsSetting.isSelected("25")) return "25";
        if (shardsSetting.isSelected("50")) return "50";
        if (shardsSetting.isSelected("100")) return "100";
        if (shardsSetting.isSelected("135")) return "135";
        if (shardsSetting.isSelected("250")) return "250";
        if (shardsSetting.isSelected("500")) return "500";
        if (shardsSetting.isSelected("1000")) return "1,000";
        if (shardsSetting.isSelected("5000")) return "5,000";
        if (shardsSetting.isSelected("10000")) return "10,000";
        if (shardsSetting.isSelected("50000")) return "50,000";
        return "";
    }

    private String selectedKills() {
        if (killsSetting.isSelected("Выкл")) return "";
        if (killsSetting.isSelected("0")) return "0";
        if (killsSetting.isSelected("1")) return "1";
        if (killsSetting.isSelected("10")) return "10";
        if (killsSetting.isSelected("25")) return "25";
        if (killsSetting.isSelected("50")) return "50";
        if (killsSetting.isSelected("100")) return "100";
        if (killsSetting.isSelected("250")) return "250";
        if (killsSetting.isSelected("500")) return "500";
        if (killsSetting.isSelected("1000")) return "1000";
        return "";
    }

    private String selectedDeaths() {
        if (deathsSetting.isSelected("Выкл")) return "";
        if (deathsSetting.isSelected("0")) return "0";
        if (deathsSetting.isSelected("1")) return "1";
        if (deathsSetting.isSelected("5")) return "5";
        if (deathsSetting.isSelected("10")) return "10";
        if (deathsSetting.isSelected("25")) return "25";
        if (deathsSetting.isSelected("50")) return "50";
        if (deathsSetting.isSelected("100")) return "100";
        if (deathsSetting.isSelected("250")) return "250";
        if (deathsSetting.isSelected("500")) return "500";
        if (deathsSetting.isSelected("1000")) return "1000";
        return "";
    }

    private String selectedHours() {
        if (hoursSetting.isSelected("Выкл")) return "";
        if (hoursSetting.isSelected("1")) return "1ч";
        if (hoursSetting.isSelected("15")) return "15ч";
        if (hoursSetting.isSelected("40")) return "40ч";
        if (hoursSetting.isSelected("100")) return "100ч";
        if (hoursSetting.isSelected("125")) return "125ч";
        return "";
    }

    private static boolean isLikelyValue(String s, ValueMode mode) {
        String v = safe(s);
        if (v.isEmpty()) return false;
        if (v.indexOf(':') >= 0) return false;
        if (v.length() > 64) return false;

        if (mode == ValueMode.TEXT) {
            if (containsIgnoreCase(v, "ранг")) return false;
            if (containsIgnoreCase(v, "rank")) return false;
            return true;
        }

        if (mode == ValueMode.NUMBER_PUNCT) return isNumericLike(v);

        if (mode == ValueMode.NUMBER_LEADING) return !leadingDigits(v).isEmpty();

        if (mode == ValueMode.HOURS) return !normalizeHours(v).isEmpty();

        return true;
    }

    private static String leadingDigits(String v) {
        if (v == null) return "";
        int i = 0;
        while (i < v.length()) {
            char ch = v.charAt(i);
            if (ch >= '0' && ch <= '9') i++;
            else break;
        }
        return i > 0 ? v.substring(0, i) : "";
    }

    private static boolean isNumericLike(String v) {
        if (v == null) return false;
        boolean hasDigit = false;
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch >= '0' && ch <= '9') hasDigit = true;
            else if (ch == ',' || ch == '.' || ch == ' ' || ch == '_') {
            } else return false;
        }
        return hasDigit;
    }

    private static String normalizeNumber(String v) {
        if (v == null) return "";
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        String s = sb.toString();
        int k = 0;
        while (k + 1 < s.length() && s.charAt(k) == '0') k++;
        return s.substring(k);
    }

    private static String formatAsOriginalNumber(String repNo, String originalTailNo) {
        String digits = normalizeNumber(repNo);
        if (digits.isEmpty()) return repNo;

        boolean usesComma = originalTailNo != null && originalTailNo.indexOf(',') >= 0;
        boolean usesSpace = originalTailNo != null && originalTailNo.indexOf(' ') >= 0;

        if (!usesComma && !usesSpace) return digits;

        StringBuilder sb = new StringBuilder(digits.length() + digits.length() / 3 + 2);
        int c = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            sb.append(digits.charAt(i));
            c++;
            if (c == 3 && i != 0) {
                sb.append(usesSpace ? ' ' : ',');
                c = 0;
            }
        }
        return sb.reverse().toString();
    }

    private static String normalizeHours(String v) {
        String s = safe(v);
        if (s.isEmpty()) return "";
        s = s.replace("часов", "").replace("час", "").replace("ч", "").replace("h", "").trim();
        return normalizeNumber(s);
    }

    private static String formatAsOriginalHours(String repNo, String originalTailNo) {
        String digits = normalizeHours(repNo);
        if (digits.isEmpty()) return repNo;

        String orig = safe(originalTailNo);
        boolean hasH = containsIgnoreCase(orig, "h");
        return hasH ? (digits + "h") : (digits + "ч");
    }

    private static boolean containsIgnoreCase(String s, String sub) {
        if (s == null || sub == null) return false;
        return s.toLowerCase().contains(sub.toLowerCase());
    }

    private static int indexOfIgnoreCase(String s, String needle) {
        if (s == null || needle == null) return -1;
        return s.toLowerCase().indexOf(needle.toLowerCase());
    }

    private static String stripFormatting(String s) {
        String v = s == null ? "" : s;
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

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String readEventText(TextFactoryEvent e) {
        if (e == null) return null;

        Object o;

        o = tryCallNoArgs(e, "getText");
        String s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(e, "getString");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(e, "text");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        o = tryCallNoArgs(e, "string");
        s = unwrapTextOrString(o);
        if (s != null) return s;

        try {
            Field[] fs = e.getClass().getDeclaredFields();
            for (Field f : fs) {
                f.setAccessible(true);
                Object v = f.get(e);
                s = unwrapTextOrString(v);
                if (s != null) return s;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Object tryCallNoArgs(Object o, String name) {
        try {
            Method m = o.getClass().getMethod(name);
            return m.invoke(o);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String unwrapTextOrString(Object o) {
        if (o == null) return null;

        if (o instanceof String) {
            String s = safe((String) o);
            return s.isEmpty() ? null : s;
        }

        if (o instanceof Text) {
            String s = safe(((Text) o).getString());
            return s.isEmpty() ? null : s;
        }

        Object inner = tryCallNoArgs(o, "getString");
        if (inner instanceof String) {
            String s = safe((String) inner);
            return s.isEmpty() ? null : s;
        }

        return null;
    }
}