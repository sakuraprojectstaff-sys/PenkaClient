package fun.rich.features.impl.misc;

import fun.rich.display.hud.Notifications;
import fun.rich.events.chat.ChatEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class HelperEvent extends Module {

    private final BooleanSetting specRequest;
    private final BooleanSetting soundNotify;
    private final SliderSettings soundPower;

    private String lastMsg = "";
    private long lastMsgMs = 0L;

    public HelperEvent() {
        super("HelperEvent", ModuleCategory.MISC);

        specRequest = new BooleanSetting("Просьба о спеке", "");
        soundNotify = new BooleanSetting("Звук уведомления", "");

        soundPower = createSlider("Сила звука", 80.0f, 10.0f, 100.0f, 5.0f);

        setBool(specRequest, false);
        setBool(soundNotify, false);

        setNumber(soundPower, 80.0f);
        setNumberByNames(soundPower, 10.0f, "setMin", "min");
        setNumberByNames(soundPower, 100.0f, "setMax", "max");
        setNumberByNames(soundPower, 5.0f, "setStep", "step", "setIncrement", "increment");

        setVisible(soundPower, () -> readBool(soundNotify));

        setup(specRequest, soundNotify, soundPower);
    }

    @EventHandler
    private void onChat(ChatEvent e) {
        if (!moduleActive()) return;
        if (mc.world == null || mc.player == null) return;
        if (!readBool(specRequest)) return;

        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) return;

        long now = System.currentTimeMillis();
        if (msg.equals(lastMsg) && now - lastMsgMs < 250L) return;
        lastMsg = msg;
        lastMsgMs = now;

        String lower = msg.toLowerCase(Locale.ROOT);
        int kwPos = keywordPos(lower);
        if (kwPos < 0) return;

        String nick = pickNearestNick(msg, kwPos);

        Notifications hud = Notifications.getInstance();
        if (hud != null) {
            Text t = nick != null
                    ? Text.empty()
                    .append(Text.literal(nick).formatted(Formatting.AQUA))
                    .append(Text.literal(" просит спек!").formatted(Formatting.WHITE))
                    : Text.literal("Просьба о спеке").formatted(Formatting.WHITE);
            hud.addList(t, 7500L);
        }

        if (readBool(soundNotify)) {
            float vol = clamp(readFloat(soundPower) / 100.0f, 0.0f, 1.0f);
            SoundManager.playSound(SoundManager.CATEGORY_CLICK, vol, 1.0f);
        }
    }

    private boolean moduleActive() {
        Object v;
        v = invokeNoArgs(this, "isToggled");
        if (v instanceof Boolean b) return b;
        v = invokeNoArgs(this, "isEnabled");
        if (v instanceof Boolean b) return b;
        v = invokeNoArgs(this, "isActive");
        if (v instanceof Boolean b) return b;
        return true;
    }

    private static int keywordPos(String lower) {
        int p = idx(lower, "spec");
        p = Math.min(p, idx(lower, "спек"));
        p = Math.min(p, idx(lower, "spek"));
        p = Math.min(p, idx(lower, "ызус"));
        p = Math.min(p, idx(lower, "cgtr"));
        return p == Integer.MAX_VALUE ? -1 : p;
    }

    private static int idx(String s, String k) {
        int i = s.indexOf(k);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    private static String pickNearestNick(String msg, int kwPos) {
        String best = null;
        int bestDist = Integer.MAX_VALUE;

        int n = msg.length();
        int i = 0;

        while (i < n) {
            char c = msg.charAt(i);
            if (!isNickChar(c)) {
                i++;
                continue;
            }

            int start = i;
            i++;
            while (i < n && isNickChar(msg.charAt(i))) i++;
            int end = i;

            int len = end - start;
            if (len >= 3 && len <= 16) {
                String token = msg.substring(start, end);
                String tl = token.toLowerCase(Locale.ROOT);
                if (!isKeywordToken(tl)) {
                    int dist = end <= kwPos ? (kwPos - end) : (end - kwPos);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = token;
                    }
                }
            }
        }

        return best;
    }

    private static boolean isKeywordToken(String tl) {
        return tl.equals("spec") || tl.equals("спек") || tl.equals("spek") || tl.equals("ызус") || tl.equals("cgtr");
    }

    private static boolean isNickChar(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || c == '_';
    }

    private static SliderSettings createSlider(String name, float def, float min, float max, float step) {
        SliderSettings s = tryCreateSlider(name, def, min, max, step);
        if (s != null) return s;
        s = tryCreateSliderAlt(name, def, min, max, step);
        if (s != null) return s;
        return (SliderSettings) newInstance(SliderSettings.class,
                new Object[]{name, ""},
                new Object[]{name}
        );
    }

    private static SliderSettings tryCreateSlider(String name, float def, float min, float max, float step) {
        Object o = newInstance(SliderSettings.class,
                new Object[]{name, def, min, max, step},
                new Object[]{name, (double) def, (double) min, (double) max, (double) step}
        );
        return o instanceof SliderSettings s ? s : null;
    }

    private static SliderSettings tryCreateSliderAlt(String name, float def, float min, float max, float step) {
        Object o = newInstance(SliderSettings.class,
                new Object[]{name, "", def, min, max, step},
                new Object[]{name, "", (double) def, (double) min, (double) max, (double) step}
        );
        return o instanceof SliderSettings s ? s : null;
    }

    private static Object newInstance(Class<?> cls, Object[]... variants) {
        for (Object[] args : variants) {
            Object o = tryCreate(cls, args);
            if (o != null) return o;
        }
        return null;
    }

    private static Object tryCreate(Class<?> cls, Object[] args) {
        try {
            for (Constructor<?> ct : cls.getDeclaredConstructors()) {
                Class<?>[] p = ct.getParameterTypes();
                if (p.length != args.length) continue;

                boolean ok = true;
                for (int i = 0; i < p.length; i++) {
                    Object a = args[i];
                    if (a == null) continue;
                    if (!wrap(p[i]).isAssignableFrom(a.getClass())) {
                        ok = false;
                        break;
                    }
                }
                if (!ok) continue;

                ct.setAccessible(true);
                return ct.newInstance(args);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Class<?> wrap(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == boolean.class) return Boolean.class;
        if (c == int.class) return Integer.class;
        if (c == float.class) return Float.class;
        if (c == double.class) return Double.class;
        if (c == long.class) return Long.class;
        if (c == short.class) return Short.class;
        if (c == byte.class) return Byte.class;
        if (c == char.class) return Character.class;
        return c;
    }

    private static void setVisible(Object setting, BooleanSupplier cond) {
        if (setting == null || cond == null) return;

        try {
            Method m = setting.getClass().getMethod("visible", BooleanSupplier.class);
            m.invoke(setting, cond);
            return;
        } catch (Throwable ignored) {
        }

        Supplier<Boolean> s = cond::getAsBoolean;

        try {
            Method m = setting.getClass().getMethod("visible", Supplier.class);
            m.invoke(setting, s);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = setting.getClass().getDeclaredMethod("visible", BooleanSupplier.class);
            m.setAccessible(true);
            m.invoke(setting, cond);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = setting.getClass().getDeclaredMethod("visible", Supplier.class);
            m.setAccessible(true);
            m.invoke(setting, s);
        } catch (Throwable ignored) {
        }
    }

    private static void setBool(Object setting, boolean value) {
        if (setting == null) return;

        if (callBool(setting, "setValue", value)) return;
        if (callBool(setting, "setEnabled", value)) return;
        if (callBool(setting, "setState", value)) return;

        writeBoolField(setting, "value", value);
        writeBoolField(setting, "state", value);
        writeBoolField(setting, "enabled", value);
    }

    private static boolean callBool(Object o, String name, boolean v) {
        try {
            Method m = o.getClass().getMethod(name, boolean.class);
            m.invoke(o, v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getDeclaredMethod(name, boolean.class);
            m.setAccessible(true);
            m.invoke(o, v);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void writeBoolField(Object obj, String name, boolean v) {
        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                if (f.getType() == boolean.class) {
                    f.setBoolean(obj, v);
                    return;
                }
                if (f.getType() == Boolean.class) {
                    f.set(obj, v);
                    return;
                }
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
    }

    private static boolean readBool(Object setting) {
        Object v;

        v = invokeNoArgs(setting, "getValue");
        if (v instanceof Boolean b) return b;

        v = invokeNoArgs(setting, "isValue");
        if (v instanceof Boolean b) return b;

        v = invokeNoArgs(setting, "isEnabled");
        if (v instanceof Boolean b) return b;

        v = invokeNoArgs(setting, "isState");
        if (v instanceof Boolean b) return b;

        v = invokeNoArgs(setting, "get");
        if (v instanceof Boolean b) return b;

        v = readField(setting, "value");
        if (v instanceof Boolean b) return b;

        v = readField(setting, "state");
        if (v instanceof Boolean b) return b;

        v = readField(setting, "enabled");
        if (v instanceof Boolean b) return b;

        return false;
    }

    private static float readFloat(Object setting) {
        Object v;

        v = invokeNoArgs(setting, "getValue");
        if (v instanceof Number n) return n.floatValue();

        v = invokeNoArgs(setting, "get");
        if (v instanceof Number n) return n.floatValue();

        v = readField(setting, "value");
        if (v instanceof Number n) return n.floatValue();

        v = readField(setting, "currentValue");
        if (v instanceof Number n) return n.floatValue();

        return 0.0f;
    }

    private static void setNumber(Object setting, float v) {
        setNumberByNames(setting, v, "setValue", "set", "defaultValue");
    }

    private static void setNumberByNames(Object setting, float v, String... names) {
        if (setting == null) return;

        for (String name : names) {
            if (callNumber(setting, name, v)) return;
        }
    }

    private static boolean callNumber(Object o, String name, float v) {
        try {
            Method m = o.getClass().getMethod(name, float.class);
            m.invoke(o, v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getMethod(name, double.class);
            m.invoke(o, (double) v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getMethod(name, int.class);
            m.invoke(o, (int) v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getDeclaredMethod(name, float.class);
            m.setAccessible(true);
            m.invoke(o, v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getDeclaredMethod(name, double.class);
            m.setAccessible(true);
            m.invoke(o, (double) v);
            return true;
        } catch (Throwable ignored) {
        }
        try {
            Method m = o.getClass().getDeclaredMethod(name, int.class);
            m.setAccessible(true);
            m.invoke(o, (int) v);
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Object invokeNoArgs(Object obj, String name) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(obj);
            } catch (Throwable ignored) {
            }
            try {
                Method m = c.getMethod(name);
                return m.invoke(obj);
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static Object readField(Object obj, String name) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (Throwable ignored) {
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static float clamp(float v, float a, float b) {
        if (v < a) return a;
        if (v > b) return b;
        return v;
    }
}
