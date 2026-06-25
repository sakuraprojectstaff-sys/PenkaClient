package fun.rich.features.impl.combat;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PotionTracker extends Module {

    private static final float RADIUS = 64.0f;
    private static final boolean SHOW_HIT_CHANCE = true;
    private static final boolean SHOW_EFFECTS = true;

    private static final long COOLDOWN_PER_PLAYER_MS = 900L;

    private static final long SPLASH_KEEP_MS = 1800L;
    private static final double SPLASH_ATTR_DIST = 7.0;
    private static final double SPLASH_HIT_RADIUS = 4.0;

    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final Map<Integer, PotionData> trackedPotions = new HashMap<>();
    private final Deque<SplashData> recentSplashes = new ArrayDeque<>();

    private final Map<UUID, Map<String, EffectSnap>> lastEffects = new HashMap<>();
    private final Map<String, Long> lastNotifyMs = new HashMap<>();

    public PotionTracker() {
        super("PotionTracker", ModuleCategory.COMBAT);
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        purgeOldSplashes(now);

        double r = RADIUS;
        Box search = new Box(
                mc.player.getX() - r, mc.player.getY() - r, mc.player.getZ() - r,
                mc.player.getX() + r, mc.player.getY() + r, mc.player.getZ() + r
        );

        Set<Integer> current = new HashSet<>();
        List<PotionEntity> potions = mc.world.getEntitiesByClass(PotionEntity.class, search, p -> true);

        for (PotionEntity potion : potions) {
            int id = potion.getId();
            if (mc.player.distanceTo(potion) > RADIUS) continue;

            current.add(id);

            PotionData data = trackedPotions.get(id);
            if (data == null) {
                ItemStack stack = getPotionStack(potion);
                trackedPotions.put(id, new PotionData(stack == null ? ItemStack.EMPTY : stack.copy(), potion.getX(), potion.getY(), potion.getZ()));
            } else {
                data.lastX = potion.getX();
                data.lastY = potion.getY();
                data.lastZ = potion.getZ();
                ItemStack stack = getPotionStack(potion);
                if (stack != null && !stack.isEmpty()) data.stack = stack.copy();
            }
        }

        Set<Integer> removed = new HashSet<>(trackedPotions.keySet());
        removed.removeAll(current);

        for (int id : removed) {
            PotionData data = trackedPotions.remove(id);
            if (data == null) continue;

            ItemStack s = data.stack == null ? ItemStack.EMPTY : data.stack;
            EffectInfo info = splashEffectInfo(s);
            recentSplashes.addLast(new SplashData(s.copy(), data.lastX, data.lastY, data.lastZ, now, info.keys, info.order));
        }

        List<PlayerEntity> players = mc.world.getEntitiesByClass(PlayerEntity.class, search, p -> true);
        for (PlayerEntity p : players) {
            if (p == null) continue;
            if (p.isSpectator()) continue;

            UUID uuid = p.getUuid();
            Map<String, EffectSnap> prev = lastEffects.get(uuid);
            if (prev == null) prev = new HashMap<>();

            List<StatusEffectInstance> currEffects = collectActiveEffects(p);
            Map<String, EffectSnap> curr = new HashMap<>();
            List<EffectEntry> changed = new ArrayList<>();

            for (StatusEffectInstance eff : currEffects) {
                if (eff == null) continue;

                String key = effectKeyCompat(eff);
                String name = effectNameCompat(eff);
                if ((key == null || key.isEmpty()) && name != null) key = name;
                if (key == null) key = "";

                int ampLvl = safeAmpLevel(eff);
                int durSec = safeDurationSec(eff);

                curr.put(key, new EffectSnap(ampLvl, durSec));

                EffectSnap was = prev.get(key);
                if (was == null) {
                    changed.add(new EffectEntry(key, eff));
                } else {
                    if (ampLvl > was.ampLvl) changed.add(new EffectEntry(key, eff));
                    else if (durSec > was.durSec + 5) changed.add(new EffectEntry(key, eff));
                }
            }

            lastEffects.put(uuid, curr);
            if (changed.isEmpty()) continue;

            SplashPick pick = pickBestSplash(p, now, changed);

            List<StatusEffectInstance> outEffects = new ArrayList<>();
            ItemStack potionStack = ItemStack.EMPTY;
            double hitChance = -1.0;
            List<String> preferOrder = List.of();

            if (pick != null) {
                potionStack = pick.splash.stack;
                preferOrder = pick.splash.effectOrder;

                double dx = p.getX() - pick.splash.x;
                double dz = p.getZ() - pick.splash.z;
                double dist = Math.sqrt(dx * dx + dz * dz);

                if (dist <= SPLASH_HIT_RADIUS) {
                    double proximity = Math.max(0.0, 1.0 - dist / SPLASH_HIT_RADIUS);
                    hitChance = Math.max(0.0, Math.min(100.0, proximity * 100.0));
                }

                if (pick.matchCount > 0 && !pick.splash.effectKeys.isEmpty()) {
                    Set<String> keys = pick.splash.effectKeys;
                    for (EffectEntry ce : changed) {
                        if (ce == null || ce.eff == null) continue;
                        if (keys.contains(ce.key)) outEffects.add(ce.eff);
                    }
                }
            }

            if (outEffects.isEmpty()) {
                for (EffectEntry ce : changed) if (ce != null && ce.eff != null) outEffects.add(ce.eff);
            }

            if (!preferOrder.isEmpty()) {
                Map<String, Integer> idx = new HashMap<>();
                for (int i = 0; i < preferOrder.size(); i++) {
                    String k = preferOrder.get(i);
                    if (k != null) idx.put(k, i);
                }
                outEffects.sort(Comparator.comparingInt(a -> {
                    String k = effectKeyCompat(a);
                    String n = effectNameCompat(a);
                    if ((k == null || k.isEmpty()) && n != null) k = n;
                    if (k == null) k = "";
                    Integer v = idx.get(k);
                    return v == null ? 9999 : v;
                }));
            } else {
                outEffects.sort(Comparator.comparing(a -> {
                    String n = effectNameCompat(a);
                    return n == null ? "" : n;
                }));
            }

            StatusEffectInstance main = outEffects.isEmpty() ? null : outEffects.get(0);
            if (main == null) continue;

            String playerName = p.getName().getString();
            if (!shouldNotify(playerName)) continue;

            boolean single = outEffects.size() <= 1;

            String title = buildPotionTitle(potionStack, main, single);
            String potionColor = pickPotionColor(outEffects, title);

            String chancePart = "";
            if (SHOW_HIT_CHANCE && hitChance >= 0.0) {
                chancePart = " §8(§a" + String.format("%.0f%%", hitChance) + "§8)";
            }

            ChatMessage.brandmessage("§f" + playerName + " §7получил " + potionColor + title + chancePart);

            if (SHOW_EFFECTS && !outEffects.isEmpty()) {
                for (StatusEffectInstance eff : outEffects) {
                    if (eff == null) continue;

                    String effectName = effectNameCompat(eff);
                    String key = effectKeyCompat(eff);
                    String c = effectColor(key, effectName);

                    int ampLvl = safeAmpLevel(eff);
                    if (isNonLeveledEffect(key, effectName)) ampLvl = 1;

                    int durationSec = safeDurationSec(eff);
                    String dur = formatDuration(durationSec);

                    StringBuilder line = new StringBuilder();
                    line.append("§8  • ").append(c).append(effectName);
                    if (ampLvl > 1) line.append(" ").append(toRoman(ampLvl));
                    line.append(" §8(").append("§7").append(dur).append("§8").append(")");

                    ChatMessage.brandmessage(line.toString());
                }
            }
        }
    }

    @Override
    public void deactivate() {
        trackedPotions.clear();
        recentSplashes.clear();
        lastEffects.clear();
        lastNotifyMs.clear();
    }

    private void purgeOldSplashes(long now) {
        while (!recentSplashes.isEmpty()) {
            SplashData s = recentSplashes.peekFirst();
            if (now - s.timeMs > SPLASH_KEEP_MS) recentSplashes.removeFirst();
            else break;
        }
    }

    private SplashPick pickBestSplash(PlayerEntity p, long now, List<EffectEntry> changed) {
        if (recentSplashes.isEmpty()) return null;

        SplashData best = null;
        int bestMatch = -1;
        double bestDist = 1e9;
        long bestDt = Long.MAX_VALUE;

        for (SplashData s : recentSplashes) {
            long dt = now - s.timeMs;
            if (dt < 0 || dt > SPLASH_KEEP_MS) continue;

            double dx = p.getX() - s.x;
            double dz = p.getZ() - s.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > SPLASH_ATTR_DIST) continue;

            int match = 0;
            if (!s.effectKeys.isEmpty()) {
                for (EffectEntry ce : changed) {
                    if (ce == null) continue;
                    if (ce.key == null) continue;
                    if (s.effectKeys.contains(ce.key)) match++;
                }
            }

            boolean better = false;
            if (match > bestMatch) better = true;
            else if (match == bestMatch) {
                if (dist < bestDist - 1e-6) better = true;
                else if (Math.abs(dist - bestDist) < 1e-6 && dt < bestDt) better = true;
            }

            if (better) {
                best = s;
                bestMatch = match;
                bestDist = dist;
                bestDt = dt;
            }
        }

        if (best == null) return null;
        return new SplashPick(best, bestMatch);
    }

    private boolean shouldNotify(String playerName) {
        long now = System.currentTimeMillis();
        Long last = lastNotifyMs.get(playerName);
        if (last != null && now - last < COOLDOWN_PER_PLAYER_MS) return false;
        lastNotifyMs.put(playerName, now);
        return true;
    }

    private String buildPotionTitle(ItemStack potionStack, StatusEffectInstance main, boolean single) {
        String rawName = (potionStack != null && !potionStack.isEmpty()) ? potionStack.getName().getString() : "";
        boolean custom = potionStack != null && !potionStack.isEmpty() && hasCustomNameCompat(potionStack);

        String mainKey = effectKeyCompat(main);
        String mainName = effectNameCompat(main);

        if (!rawName.isEmpty()) {
            if (custom) return rawName;

            if (single) {
                String baseOnly = potionBaseTitleOnly(rawName);
                if (baseOnly != null && !baseOnly.isEmpty()) return baseOnly;
            }

            int lvl = safeAmpLevel(main);
            if (isNonLeveledEffect(mainKey, mainName)) lvl = 1;

            String base = normalizePotionName(rawName);
            return appendRomanIfNeeded(base, lvl);
        }

        if (single) return "Зелье";

        int lvl = safeAmpLevel(main);
        if (isNonLeveledEffect(mainKey, mainName)) lvl = 1;

        String effName = mainName == null || mainName.isEmpty() ? "Эффект" : mainName;
        String base = "Зелье " + effName;
        return appendRomanIfNeeded(base, lvl);
    }

    private String potionBaseTitleOnly(String rawName) {
        if (rawName == null) return "";
        String s = rawName.trim();
        if (s.isEmpty()) return "";

        if (startsWithIgnoreCase(s, "Взрывное зелье ")) return "Взрывное зелье";
        if (startsWithIgnoreCase(s, "Оседающее зелье ")) return "Оседающее зелье";
        if (startsWithIgnoreCase(s, "Туманное зелье ")) return "Туманное зелье";
        if (startsWithIgnoreCase(s, "Зелье ")) return "Зелье";

        if (startsWithIgnoreCase(s, "Splash Potion")) return "Взрывное зелье";
        if (startsWithIgnoreCase(s, "Lingering Potion")) return "Оседающее зелье";
        if (startsWithIgnoreCase(s, "Potion")) return "Зелье";

        return "Зелье";
    }

    private boolean hasCustomNameCompat(ItemStack stack) {
        if (stack == null) return false;

        try {
            Method m = stack.getClass().getMethod("getCustomName");
            Object r = m.invoke(stack);
            if (r instanceof Text t) return t != null;
        } catch (Throwable ignored) {
        }

        try {
            Method m = stack.getClass().getMethod("hasCustomName");
            Object r = m.invoke(stack);
            if (r instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private boolean isNonLeveledEffect(String key, String name) {
        String k = key == null ? "" : key.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();

        if (k.contains("fire_resistance") || n.contains("огнестой")) return true;
        if (k.contains("water_breathing") || n.contains("подвод") || n.contains("дыхани")) return true;
        if (k.contains("night_vision") || n.contains("ночн") || n.contains("зрени")) return true;
        if (k.contains("invisibility") || n.contains("невидим")) return true;

        return false;
    }

    private String formatDuration(int sec) {
        if (sec < 0) sec = 0;
        int m = sec / 60;
        int s = sec % 60;
        return m + ":" + String.format("%02d", s);
    }

    private String appendRomanIfNeeded(String potionName, int level) {
        if (potionName == null) potionName = "Зелье";
        if (level <= 1) return potionName;

        String s = potionName.trim();
        if (endsWithRoman(s)) return s;
        return s + " " + toRoman(level);
    }

    private boolean endsWithRoman(String s) {
        if (s == null) return false;
        String t = s.trim().toUpperCase();
        return t.endsWith(" I") || t.endsWith(" II") || t.endsWith(" III") || t.endsWith(" IV") || t.endsWith(" V")
                || t.endsWith(" VI") || t.endsWith(" VII") || t.endsWith(" VIII") || t.endsWith(" IX") || t.endsWith(" X");
    }

    private String pickPotionColor(List<StatusEffectInstance> effects, String potionName) {
        if (effects != null && !effects.isEmpty()) {
            StatusEffectInstance eff = effects.get(0);
            String key = effectKeyCompat(eff);
            String name = effectNameCompat(eff);
            String c = effectColor(key, name);
            if (c != null && !c.isEmpty()) return c;
        }

        String byName = potionColorFromPotionName(potionName);
        if (byName != null && !byName.isEmpty()) return byName;

        return "§d";
    }

    private String potionColorFromPotionName(String potionName) {
        if (potionName == null) return "§d";
        String n = potionName.toLowerCase();

        if (n.contains("скорост") || n.contains("стремител")) return "§b";
        if (n.contains("сил")) return "§c";
        if (n.contains("огнестой")) return "§6";
        if (n.contains("регенерац")) return "§d";
        if (n.contains("исцел")) return "§a";
        if (n.contains("невидим")) return "§7";
        if (n.contains("ночн") || n.contains("зрени")) return "§9";
        if (n.contains("подвод") || n.contains("дыхани")) return "§3";
        if (n.contains("сопротив")) return "§e";
        if (n.contains("прыж") || n.contains("прыгуч")) return "§a";
        if (n.contains("спешк") || n.contains("провор") || n.contains("ускорен")) return "§e";
        if (n.contains("поглощ")) return "§6";

        if (n.contains("отрав") || n.contains("яд")) return "§2";
        if (n.contains("иссуш") || n.contains("увядан") || n.contains("wither")) return "§0";
        if (n.contains("слабост")) return "§7";
        if (n.contains("медлен") || n.contains("замедл")) return "§8";
        if (n.contains("вред") || n.contains("урон")) return "§4";

        return "§d";
    }

    private String normalizePotionName(String name) {
        if (name == null) return "Зелье";
        String s = name.trim();

        s = stripPrefixIgnoreCase(s, "Взрывное зелье ");
        s = stripPrefixIgnoreCase(s, "Оседающее зелье ");
        s = stripPrefixIgnoreCase(s, "Туманное зелье ");
        s = stripPrefixIgnoreCase(s, "Зелье ");

        s = stripPrefixIgnoreCase(s, "Взрывное ");
        s = stripPrefixIgnoreCase(s, "Оседающее ");
        s = stripPrefixIgnoreCase(s, "Туманное ");

        s = stripPrefixIgnoreCase(s, "Splash Potion of ");
        s = stripPrefixIgnoreCase(s, "Lingering Potion of ");
        s = stripPrefixIgnoreCase(s, "Potion of ");

        if (s.isEmpty()) return "Зелье";
        return "Зелье " + s;
    }

    private boolean startsWithIgnoreCase(String s, String pref) {
        if (s == null || pref == null) return false;
        if (s.length() < pref.length()) return false;
        return s.regionMatches(true, 0, pref, 0, pref.length());
    }

    private String stripPrefixIgnoreCase(String s, String pref) {
        if (!startsWithIgnoreCase(s, pref)) return s;
        return s.substring(pref.length()).trim();
    }

    private ItemStack getPotionStack(PotionEntity potion) {
        if (potion == null) return ItemStack.EMPTY;

        try {
            Method m = potion.getClass().getMethod("getStack");
            Object o = m.invoke(potion);
            if (o instanceof ItemStack stack) return stack;
        } catch (Throwable ignored) {
        }

        try {
            Method m = potion.getClass().getMethod("getItem");
            Object o = m.invoke(potion);
            if (o instanceof ItemStack stack) return stack;
        } catch (Throwable ignored) {
        }

        return ItemStack.EMPTY;
    }

    private List<StatusEffectInstance> collectActiveEffects(LivingEntity entity) {
        if (entity == null) return List.of();

        try {
            Collection<StatusEffectInstance> c = entity.getStatusEffects();
            if (c != null && !c.isEmpty()) return new ArrayList<>(c);
        } catch (Throwable ignored) {
        }

        Object out = invokeNoArgs(entity, "getStatusEffects");
        if (out == null) out = invokeNoArgs(entity, "getActiveStatusEffects");
        if (out == null) out = invokeNoArgs(entity, "getActiveEffects");

        List<StatusEffectInstance> res = new ArrayList<>();

        if (out instanceof Map<?, ?> map) {
            for (Object v : map.values()) if (v instanceof StatusEffectInstance sei) res.add(sei);
            return res;
        }

        if (out instanceof Iterable<?> it) {
            for (Object v : it) if (v instanceof StatusEffectInstance sei) res.add(sei);
            return res;
        }

        if (out instanceof Object[] arr) {
            for (Object v : arr) if (v instanceof StatusEffectInstance sei) res.add(sei);
            return res;
        }

        return res;
    }

    private EffectInfo splashEffectInfo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new EffectInfo(Set.of(), List.of());

        List<StatusEffectInstance> effects = getPotionEffectsCompat(stack);
        if (effects == null || effects.isEmpty()) return new EffectInfo(Set.of(), List.of());

        Set<String> keys = new HashSet<>();
        List<String> order = new ArrayList<>();

        for (StatusEffectInstance e : effects) {
            if (e == null) continue;
            String k = effectKeyCompat(e);
            String n = effectNameCompat(e);
            if ((k == null || k.isEmpty()) && n != null) k = n;
            if (k == null) k = "";
            if (keys.add(k)) order.add(k);
        }

        return new EffectInfo(keys, order);
    }

    private List<StatusEffectInstance> getPotionEffectsCompat(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        List<StatusEffectInstance> a = getPotionEffectsFromUtil("net.minecraft.potion.PotionUtil", stack);
        if (a != null && !a.isEmpty()) return a;

        List<StatusEffectInstance> b = getPotionEffectsFromUtil("net.minecraft.potion.PotionUtils", stack);
        if (b != null && !b.isEmpty()) return b;

        return List.of();
    }

    private List<StatusEffectInstance> getPotionEffectsFromUtil(String className, ItemStack stack) {
        try {
            Class<?> c = Class.forName(className);

            List<StatusEffectInstance> r = tryInvokeEffectsMethod(c, "getPotionEffects", stack);
            if (r != null) return r;

            r = tryInvokeEffectsMethod(c, "getEffectsFromStack", stack);
            if (r != null) return r;

            r = tryInvokeEffectsMethod(c, "getCustomPotionEffects", stack);
            if (r != null) return r;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private List<StatusEffectInstance> tryInvokeEffectsMethod(Class<?> c, String name, ItemStack stack) {
        try {
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p.length < 1) continue;
                if (p[0] != ItemStack.class) continue;

                Object out = invokeEffects(m, stack);
                if (out instanceof List<?> list) return castEffects(list);
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (!Modifier.isStatic(m.getModifiers())) continue;
                if (!List.class.isAssignableFrom(m.getReturnType())) continue;

                Class<?>[] p = m.getParameterTypes();
                if (p.length < 1) continue;
                if (p[0] != ItemStack.class) continue;

                m.setAccessible(true);
                Object out = invokeEffects(m, stack);
                if (out instanceof List<?> list) return castEffects(list);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private Object invokeEffects(Method m, ItemStack stack) {
        try {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1) return m.invoke(null, stack);

            if (p.length == 2) {
                if (p[1] == boolean.class || p[1] == Boolean.class) return m.invoke(null, stack, false);
                if (LivingEntity.class.isAssignableFrom(p[1])) return m.invoke(null, stack, mc != null ? mc.player : null);
                return m.invoke(null, stack, null);
            }

            if (p.length == 3) {
                Object a1 = stack;
                Object a2 = null;
                Object a3 = null;

                if (p[1] == boolean.class || p[1] == Boolean.class) a2 = false;
                else if (LivingEntity.class.isAssignableFrom(p[1])) a2 = mc != null ? mc.player : null;

                if (p[2] == boolean.class || p[2] == Boolean.class) a3 = false;
                else if (LivingEntity.class.isAssignableFrom(p[2])) a3 = mc != null ? mc.player : null;

                return m.invoke(null, a1, a2, a3);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String effectNameCompat(StatusEffectInstance eff) {
        try {
            Object type = eff.getEffectType();

            Object v = invokeNoArgs(type, "value");
            if (v != null) type = v;

            Object key = invokeNoArgs(type, "getTranslationKey");
            if (key instanceof String k) return Text.translatable(k).getString();

            Object name = invokeNoArgs(type, "getName");
            if (name instanceof Text t) return t.getString();
            if (name instanceof String s) return s;

            Object display = invokeNoArgs(type, "getDisplayName");
            if (display instanceof Text t) return t.getString();
            if (display instanceof String s) return s;
        } catch (Throwable ignored) {
        }
        return "Эффект";
    }

    private String effectKeyCompat(StatusEffectInstance eff) {
        try {
            Object type = eff.getEffectType();

            Object v = invokeNoArgs(type, "value");
            if (v != null) type = v;

            Object key = invokeNoArgs(type, "getTranslationKey");
            if (key instanceof String k) return k;

            Object id = invokeNoArgs(type, "toString");
            if (id instanceof String s) return s;
        } catch (Throwable ignored) {
        }
        return "";
    }

    private String effectColor(String key, String name) {
        String k = key == null ? "" : key.toLowerCase();
        String n = name == null ? "" : name.toLowerCase();

        if (k.contains("speed") || n.contains("скорост") || n.contains("стремител")) return "§b";
        if (k.contains("strength") || n.contains("сил")) return "§c";
        if (k.contains("fire_resistance") || n.contains("огнестой")) return "§6";
        if (k.contains("regeneration") || n.contains("регенерац")) return "§d";
        if (k.contains("instant_health") || k.contains("healing") || n.contains("исцел")) return "§a";
        if (k.contains("invisibility") || n.contains("невидим")) return "§7";
        if (k.contains("night_vision") || n.contains("ночн") || n.contains("зрени")) return "§9";
        if (k.contains("water_breathing") || n.contains("подвод") || n.contains("дыхани")) return "§3";
        if (k.contains("resistance") || n.contains("сопротив")) return "§e";
        if (k.contains("jump_boost") || n.contains("прыж") || n.contains("прыгуч")) return "§a";
        if (k.contains("haste") || n.contains("спешк") || n.contains("ускорен") || n.contains("провор")) return "§e";
        if (k.contains("absorption") || n.contains("поглощ")) return "§6";

        if (k.contains("slowness") || n.contains("медлен") || n.contains("замедл")) return "§8";
        if (k.contains("weakness") || n.contains("слабост")) return "§7";
        if (k.contains("poison") || n.contains("отрав") || n.contains("яд")) return "§2";
        if (k.contains("wither") || n.contains("иссуш") || n.contains("увядан")) return "§0";
        if (k.contains("harm") || k.contains("damage") || n.contains("вред") || n.contains("урон")) return "§4";

        return "§d";
    }

    private Object invokeNoArgs(Object o, String name) {
        if (o == null) return null;
        try {
            Method m = o.getClass().getMethod(name);
            return m.invoke(o);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int safeAmpLevel(StatusEffectInstance eff) {
        int amp = 0;

        try {
            amp = eff.getAmplifier();
        } catch (Throwable ignored) {
            try {
                Method m = eff.getClass().getMethod("getAmplifier");
                Object r = m.invoke(eff);
                if (r instanceof Number n) amp = n.intValue();
            } catch (Throwable ignored2) {
            }
        }

        if (amp < 0) amp = 0;

        int level = amp + 1;
        if (level < 1) level = 1;
        return level;
    }

    private int safeDurationSec(StatusEffectInstance eff) {
        try {
            return Math.max(0, eff.getDuration() / 20);
        } catch (Throwable ignored) {
        }
        return 0;
    }

    @SuppressWarnings("unchecked")
    private List<StatusEffectInstance> castEffects(List<?> list) {
        try {
            return (List<StatusEffectInstance>) list;
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    private String toRoman(int number) {
        return switch (number) {
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
            default -> String.valueOf(number);
        };
    }

    private static final class EffectSnap {
        final int ampLvl;
        final int durSec;

        EffectSnap(int ampLvl, int durSec) {
            this.ampLvl = ampLvl;
            this.durSec = durSec;
        }
    }

    private static final class EffectEntry {
        final String key;
        final StatusEffectInstance eff;

        EffectEntry(String key, StatusEffectInstance eff) {
            this.key = key == null ? "" : key;
            this.eff = eff;
        }
    }

    private static final class EffectInfo {
        final Set<String> keys;
        final List<String> order;

        EffectInfo(Set<String> keys, List<String> order) {
            this.keys = keys == null ? Set.of() : keys;
            this.order = order == null ? List.of() : order;
        }
    }

    private static final class SplashPick {
        final SplashData splash;
        final int matchCount;

        SplashPick(SplashData splash, int matchCount) {
            this.splash = splash;
            this.matchCount = matchCount;
        }
    }

    private static final class SplashData {
        final ItemStack stack;
        final double x;
        final double y;
        final double z;
        final long timeMs;
        final Set<String> effectKeys;
        final List<String> effectOrder;

        SplashData(ItemStack stack, double x, double y, double z, long timeMs, Set<String> effectKeys, List<String> effectOrder) {
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.x = x;
            this.y = y;
            this.z = z;
            this.timeMs = timeMs;
            this.effectKeys = effectKeys == null ? Set.of() : effectKeys;
            this.effectOrder = effectOrder == null ? List.of() : effectOrder;
        }
    }

    private static final class PotionData {
        ItemStack stack;
        double lastX;
        double lastY;
        double lastZ;

        PotionData(ItemStack stack, double x, double y, double z) {
            this.stack = stack == null ? ItemStack.EMPTY : stack;
            this.lastX = x;
            this.lastY = y;
            this.lastZ = z;
        }
    }
}