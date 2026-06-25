package fun.rich.features.impl.misc;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientSound extends Module {

    @NonFinal static ClientSound instance;

    SelectSetting mode = new SelectSetting("Звук модулей", "Client 1");
    SliderSettings volume = new SliderSettings("Громкость", "1.0");

    public ClientSound() {
        super("ClientSound", "Client Sound", ModuleCategory.MISC);
        instance = this;
        ensureSelectOptions(mode, "Client 1", "Client 2", "Client 3", "Client 4", "Client 5");
        ensureSelectDefault(mode, "Client 1", 0);
        applySliderMeta(volume, 0.0F, 2.0F, 0.01F);
        setup(mode, volume);
    }

    public static ClientSound getInstance() {
        return instance;
    }

    public boolean enabledCompat() {
        return this.state;
    }

    public float volumeMul() {
        float v = readNumber(volume, 1.0F);
        if (v < 0.0F) v = 0.0F;
        if (v > 2.0F) v = 2.0F;
        return v;
    }

    public SoundEvent enableEvent() {
        String m = readString(mode, "Client 1");

        if (eq(m, "Client 2")) return SoundEvent.of(Identifier.of("minecraft:enable"));
        if (eq(m, "Client 3")) return SoundEvent.of(Identifier.of("minecraft:enable1"));
        if (eq(m, "Client 4")) return SoundEvent.of(Identifier.of("minecraft:enable2"));
        if (eq(m, "Client 5")) return SoundEvent.of(Identifier.of("minecraft:enable3"));

        return SoundEvent.of(Identifier.of("minecraft:module_enable"));
    }

    public SoundEvent disableEvent() {
        String m = readString(mode, "Client 1");

        if (eq(m, "Client 2")) return SoundEvent.of(Identifier.of("minecraft:disable"));
        if (eq(m, "Client 3")) return SoundEvent.of(Identifier.of("minecraft:disable1"));
        if (eq(m, "Client 4")) return SoundEvent.of(Identifier.of("minecraft:disable2"));
        if (eq(m, "Client 5")) return SoundEvent.of(Identifier.of("minecraft:disable3"));

        return SoundEvent.of(Identifier.of("minecraft:module_disable"));
    }

    static boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
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

    static void trySetStringField(Object obj, String v, String... names) {
        if (obj == null) return;
        if (names == null) return;
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
        if (obj == null) return;
        if (names == null) return;
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

    static void ensureSelectOptions(Object setting, String a, String b, String c, String d, String e) {
        if (setting == null) return;

        String[] arr = new String[]{a, b, c, d, e};
        if (tryInvokeStringArray(setting, "setup", arr)) return;
        if (tryInvokeStringArray(setting, "setValues", arr)) return;
        if (tryInvokeStringArray(setting, "setModes", arr)) return;
        if (tryInvokeStringArray(setting, "setOptions", arr)) return;
        if (tryInvokeStringArray(setting, "setList", arr)) return;

        List<String> list = new ArrayList<>();
        list.add(a);
        list.add(b);
        list.add(c);
        list.add(d);
        list.add(e);

        if (tryInvokeList(setting, "setup", list)) return;
        if (tryInvokeList(setting, "setValues", list)) return;
        if (tryInvokeList(setting, "setModes", list)) return;
        if (tryInvokeList(setting, "setOptions", list)) return;

        trySetField(setting, "values", arr, list);
        trySetField(setting, "modes", arr, list);
        trySetField(setting, "options", arr, list);
        trySetField(setting, "list", arr, list);
        trySetField(setting, "strings", arr, list);
    }

    static boolean tryInvokeStringArray(Object obj, String name, String[] arr) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (p == String[].class) {
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
                Class<?> p = m.getParameterTypes()[0];
                if (p == String[].class) {
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
                Class<?> p = m.getParameterTypes()[0];
                if (List.class.isAssignableFrom(p)) {
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
                Class<?> p = m.getParameterTypes()[0];
                if (List.class.isAssignableFrom(p)) {
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

    static void applySliderMeta(Object setting, float min, float max, float inc) {
        setFloatAny(setting, "min", min);
        setFloatAny(setting, "max", max);
        setFloatAny(setting, "minValue", min);
        setFloatAny(setting, "maxValue", max);
        setFloatAny(setting, "minimum", min);
        setFloatAny(setting, "maximum", max);

        invokeFloat(setting, "setMin", min);
        invokeFloat(setting, "setMax", max);

        setFloatAny(setting, "inc", inc);
        setFloatAny(setting, "step", inc);
        setFloatAny(setting, "increment", inc);

        invokeFloat(setting, "setInc", inc);
        invokeFloat(setting, "setStep", inc);
        invokeFloat(setting, "setIncrement", inc);
    }

    static void invokeFloat(Object obj, String method, float v) {
        if (obj == null) return;
        try {
            Method m = obj.getClass().getMethod(method, float.class);
            m.invoke(obj, v);
        } catch (Throwable ignored) {
        }
        try {
            Method m = obj.getClass().getMethod(method, double.class);
            m.invoke(obj, (double) v);
        } catch (Throwable ignored) {
        }
    }

    static void setFloatAny(Object obj, String field, float v) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == float.class) f.setFloat(obj, v);
            else if (f.getType() == double.class) f.setDouble(obj, v);
        } catch (Throwable ignored) {
        }
    }

    static float readNumber(Object setting, float def) {
        if (setting == null) return def;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("current");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

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

    static float parseFloatSafe(String s, float def) {
        if (s == null) return def;
        try {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return def;
            return Float.parseFloat(t);
        } catch (Throwable ignored) {
        }
        return def;
    }
}
