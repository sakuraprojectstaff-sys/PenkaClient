package fun.rich.features.impl.misc;

import fun.rich.events.player.TickEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Cape extends Module {

    @NonFinal static Cape instance;

    static final Identifier CAPE = Identifier.of("minecraft", "textures/cape/cape.png");
    static final Identifier CAPE_PREM = Identifier.of("minecraft", "textures/cape/cape_prem.png");

    SelectSetting selfCape = new SelectSetting("Плащ (Себе)", "Обычный");
    BooleanSetting friendsEnabled = new BooleanSetting("Плащ друзьям", "false");
    SelectSetting friendsCape = new SelectSetting("Плащ (Друзьям)", "Обычный");

    public Cape() {
        super("Cape", "Cape", ModuleCategory.MISC);
        instance = this;

        String[] list = buildCapeList();

        ensureSelectOptions(selfCape, list);
        ensureSelectDefault(selfCape, "Обычный", 0);

        ensureSelectOptions(friendsCape, list);
        ensureSelectDefault(friendsCape, "Обычный", 0);

        applyVisibleCompat(friendsCape);

        setup(selfCape, friendsEnabled, friendsCape);
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (!enabledCompat()) return;
        CapeTextureCache.tick();
    }

    public static Cape getInstance() {
        return instance;
    }

    public boolean enabledCompat() {
        return this.state;
    }

    public boolean friendsEnabled() {
        return readBool(friendsEnabled, false);
    }

    public Identifier selfCapeId() {
        return pickCapeId(readString(selfCape, "Обычный"));
    }

    public Identifier friendsCapeId() {
        return pickCapeId(readString(friendsCape, "Обычный"));
    }

    static Identifier pickCapeId(String sel) {
        if (eq(sel, "Премиум")) return CAPE_PREM;
        if (eq(sel, "Обычный")) return CAPE;

        int idx = parseCapeIndex(sel);
        if (idx >= 1 && idx <= 20) return CapeTextureCache.resolveCape(idx, CAPE);

        return CAPE;
    }

    static int parseCapeIndex(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.isEmpty()) return -1;
        String low = t.toLowerCase();
        if (!low.startsWith("cape")) return -1;
        int sp = t.indexOf(' ');
        if (sp < 0 || sp + 1 >= t.length()) return -1;
        String n = t.substring(sp + 1).trim();
        try {
            return Integer.parseInt(n);
        } catch (Throwable ignored) {
        }
        return -1;
    }

    static String[] buildCapeList() {
        List<String> out = new ArrayList<>();
        out.add("Обычный");
        out.add("Премиум");
        for (int i = 1; i <= 20; i++) out.add("Cape " + i);
        return out.toArray(new String[0]);
    }

    static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }

    void applyVisibleCompat(Object setting) {
        if (setting == null) return;

        try {
            Method m = setting.getClass().getMethod("visible", java.util.function.Supplier.class);
            m.invoke(setting, (java.util.function.Supplier<Boolean>) this::friendsEnabled);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("visible", java.util.function.BooleanSupplier.class);
            m.invoke(setting, (java.util.function.BooleanSupplier) this::friendsEnabled);
        } catch (Throwable ignored) {
        }
    }

    static boolean readBool(Object setting, boolean def) {
        if (setting == null) return def;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return parseBool((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return parseBool((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return parseBool((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("state");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("enabled");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }

        return def;
    }

    static boolean parseBool(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim().toLowerCase();
        if (t.isEmpty()) return def;
        if (t.equals("true") || t.equals("1") || t.equals("on") || t.equals("yes") || t.equals("enable") || t.equals("enabled") || t.equals("вкл")) return true;
        if (t.equals("false") || t.equals("0") || t.equals("off") || t.equals("no") || t.equals("disable") || t.equals("disabled") || t.equals("выкл")) return false;
        return def;
    }

    static String readString(Object setting, String def) {
        if (setting == null) return def;

        try {
            Method m = setting.getClass().getMethod("getSelected");
            Object v = m.invoke(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("current");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }

        return def;
    }

    static void ensureSelectOptions(Object setting, String[] values) {
        if (setting == null) return;
        if (values == null) values = new String[0];

        if (tryInvokeStringArray(setting, "setup", values)) return;
        if (tryInvokeStringArray(setting, "setValues", values)) return;
        if (tryInvokeStringArray(setting, "setModes", values)) return;
        if (tryInvokeStringArray(setting, "setOptions", values)) return;
        if (tryInvokeStringArray(setting, "setList", values)) return;

        List<String> list = new ArrayList<>();
        for (String s : values) list.add(s);

        if (tryInvokeList(setting, "setup", list)) return;
        if (tryInvokeList(setting, "setValues", list)) return;
        if (tryInvokeList(setting, "setModes", list)) return;
        if (tryInvokeList(setting, "setOptions", list)) return;

        trySetField(setting, "values", values, list);
        trySetField(setting, "modes", values, list);
        trySetField(setting, "options", values, list);
        trySetField(setting, "list", values, list);
        trySetField(setting, "strings", values, list);
    }

    static void ensureSelectDefault(Object setting, String def, int defIndex) {
        if (setting == null) return;
        if (def == null) def = "";
        if (defIndex < 0) defIndex = 0;

        if (tryInvokeString(setting, "setSelected", def)) return;
        if (tryInvokeString(setting, "setCurrent", def)) return;
        if (tryInvokeString(setting, "setValue", def)) return;
        if (tryInvokeString(setting, "set", def)) return;
        if (tryInvokeString(setting, "select", def)) return;
        if (tryInvokeString(setting, "setMode", def)) return;
        if (tryInvokeString(setting, "setOption", def)) return;
        if (tryInvokeString(setting, "setDefault", def)) return;
        if (tryInvokeString(setting, "setSelectedValue", def)) return;

        if (tryInvokeInt(setting, "setIndex", defIndex)) return;
        if (tryInvokeInt(setting, "setSelectedIndex", defIndex)) return;
        if (tryInvokeInt(setting, "setCurrentIndex", defIndex)) return;
        if (tryInvokeInt(setting, "selectIndex", defIndex)) return;
        if (tryInvokeInt(setting, "setModeIndex", defIndex)) return;

        trySetStringField(setting, def, "current", "value", "selected", "mode", "selectedValue", "option", "selectedOption", "defaultValue");
        trySetIntField(setting, defIndex, "index", "selectedIndex", "currentIndex", "modeIndex");
    }

    static boolean tryInvokeString(Object obj, String name, String v) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == String.class) {
                    m.invoke(obj, v);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == String.class) {
                    m.setAccessible(true);
                    m.invoke(obj, v);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean tryInvokeInt(Object obj, String name, int v) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == int.class || m.getParameterTypes()[0] == Integer.class) {
                    m.invoke(obj, v);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == int.class || m.getParameterTypes()[0] == Integer.class) {
                    m.setAccessible(true);
                    m.invoke(obj, v);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean tryInvokeStringArray(Object obj, String name, String[] arr) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == String[].class) {
                    m.invoke(obj, (Object) arr);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] == String[].class) {
                    m.setAccessible(true);
                    m.invoke(obj, (Object) arr);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static boolean tryInvokeList(Object obj, String name, List<String> list) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (List.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    m.invoke(obj, list);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            for (Method m : obj.getClass().getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                if (List.class.isAssignableFrom(m.getParameterTypes()[0])) {
                    m.setAccessible(true);
                    m.invoke(obj, list);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    static void trySetField(Object obj, String field, String[] arr, List<String> list) {
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == String[].class) {
                f.set(obj, arr);
                return;
            }
            if (List.class.isAssignableFrom(f.getType())) {
                f.set(obj, list);
            }
        } catch (Throwable ignored) {
        }
    }

    static void trySetStringField(Object obj, String v, String... names) {
        if (obj == null || names == null) return;
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            try {
                Field f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                if (f.getType() == String.class) {
                    f.set(obj, v);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }

    static void trySetIntField(Object obj, int v, String... names) {
        if (obj == null || names == null) return;
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            try {
                Field f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    f.setInt(obj, v);
                    return;
                }
                if (f.getType() == Integer.class) {
                    f.set(obj, v);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
