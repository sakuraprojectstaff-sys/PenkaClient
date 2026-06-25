package fun.rich.features.impl.misc;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class HitSound extends Module {

    private static HitSound instance;

    private static final String[] OPTIONS = new String[]{"bell", "crime", "nya", "skeet", "uwu"};

    private final SelectSetting sound;
    private final SliderSettings volume;

    public HitSound() {
        super("HitSound", ModuleCategory.MISC);
        instance = this;

        this.sound = new SelectSetting("Sound", "bell");
        forceSelectSetting(this.sound, OPTIONS, "bell");

        this.volume = new SliderSettings("Volume", "1.0");
        configureSlider(this.volume, 0.1F, 2.0F, 0.01F);

        for (String n : OPTIONS) ensureSoundEvent(Identifier.of("minecraft", n));

        setup(this.sound, this.volume);
    }

    public static HitSound getInstance() {
        return instance;
    }

    public boolean isOn() {
        return isModuleEnabledCompat(this);
    }

    public float getVolumeValue() {
        return readNumber(volume, 1.0F);
    }

    public SoundEvent getSelectedEvent() {
        String name = readSelectedName(sound);
        return ensureSoundEvent(Identifier.of("minecraft", name));
    }

    private static SoundEvent ensureSoundEvent(Identifier id) {
        try {
            if (Registries.SOUND_EVENT.containsId(id)) return Registries.SOUND_EVENT.get(id);
        } catch (Throwable ignored) {
        }
        SoundEvent ev = SoundEvent.of(id);
        try {
            return Registry.register(Registries.SOUND_EVENT, id, ev);
        } catch (Throwable ignored) {
        }
        return ev;
    }

    private static String readSelectedName(Object setting) {
        String s = readString(setting);
        if (s != null) {
            String n = normalize(s);
            if (n != null) return n;
        }
        for (String o : OPTIONS) {
            if (isSelected(setting, o)) return o;
        }
        return "bell";
    }

    private static String normalize(String v) {
        String s = v.trim().toLowerCase();
        for (String o : OPTIONS) {
            if (o.equals(s)) return o;
        }
        return null;
    }

    private static void forceSelectSetting(Object setting, String[] options, String def) {
        ArrayList<String> list = new ArrayList<>();
        for (String o : options) list.add(o);

        setListAny(setting, list);
        setArrayAny(setting, options);

        setStringAny(setting, "selected", def);
        setStringAny(setting, "value", def);
        setStringAny(setting, "current", def);

        invokeString(setting, "setSelected", def);
        invokeString(setting, "setValue", def);
        invokeString(setting, "set", def);
        invokeString(setting, "select", def);

        String got = readString(setting);
        if (got == null || got.isEmpty()) {
            setStringAny(setting, "selected", def);
            setStringAny(setting, "value", def);
            setStringAny(setting, "current", def);
        }

        Object gotList = getListAny(setting);
        if (gotList == null) {
            setListAny(setting, list);
            setArrayAny(setting, options);
        }
    }

    private static void invokeString(Object obj, String method, String v) {
        if (obj == null) return;
        try {
            Method m = obj.getClass().getMethod(method, String.class);
            m.invoke(obj, v);
        } catch (Throwable ignored) {
        }
    }

    private static Object getListAny(Object setting) {
        if (setting == null) return null;

        try {
            Method m = setting.getClass().getMethod("getList");
            return m.invoke(setting);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("list");
            f.setAccessible(true);
            return f.get(setting);
        } catch (Throwable ignored) {
        }
        try {
            for (Field f : setting.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                return f.get(setting);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setListAny(Object setting, List<String> list) {
        if (setting == null) return;

        try {
            Method m = setting.getClass().getMethod("setList", List.class);
            m.invoke(setting, list);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("setValues", List.class);
            m.invoke(setting, list);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("values", List.class);
            m.invoke(setting, list);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("list");
            f.setAccessible(true);
            f.set(setting, list);
            return;
        } catch (Throwable ignored) {
        }
        try {
            for (Field f : setting.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                f.set(setting, list);
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setArrayAny(Object setting, String[] options) {
        if (setting == null) return;

        try {
            Field f = setting.getClass().getDeclaredField("values");
            f.setAccessible(true);
            if (f.getType() == String[].class) f.set(setting, options);
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("modes");
            f.setAccessible(true);
            if (f.getType() == String[].class) f.set(setting, options);
        } catch (Throwable ignored) {
        }
    }

    private static void setStringAny(Object obj, String field, String v) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == String.class) f.set(obj, v);
        } catch (Throwable ignored) {
        }
    }

    private static void configureSlider(Object setting, float min, float max, float inc) {
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

    private static void invokeFloat(Object obj, String method, float v) {
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

    private static void setFloatAny(Object obj, String field, float v) {
        if (obj == null) return;
        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == float.class) f.setFloat(obj, v);
            else if (f.getType() == double.class) f.setDouble(obj, v);
        } catch (Throwable ignored) {
        }
    }

    private static float readNumber(Object setting, float def) {
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

    private static float parseFloatSafe(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s.trim());
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static String readString(Object setting) {
        if (setting == null) return null;

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
            Method m = setting.getClass().getMethod("getSelected");
            Object v = m.invoke(setting);
            if (v instanceof String) return (String) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = setting.getClass().getDeclaredField("selected");
            f.setAccessible(true);
            Object v = f.get(setting);
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
        return null;
    }

    private static boolean isSelected(Object setting, String option) {
        if (setting == null) return false;

        try {
            Method m = setting.getClass().getMethod("isSelected", String.class);
            Object v = m.invoke(setting, option);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("is", String.class);
            Object v = m.invoke(setting, option);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static boolean isModuleEnabledCompat(Object module) {
        if (module == null) return false;

        try {
            Method m = module.getClass().getMethod("isEnabled");
            Object v = m.invoke(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = module.getClass().getMethod("isToggled");
            Object v = m.invoke(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = module.getClass().getMethod("isState");
            Object v = m.invoke(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = module.getClass().getDeclaredField("enabled");
            f.setAccessible(true);
            Object v = f.get(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = module.getClass().getDeclaredField("toggled");
            f.setAccessible(true);
            Object v = f.get(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = module.getClass().getDeclaredField("state");
            f.setAccessible(true);
            Object v = f.get(module);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }

        return true;
    }
}
