package fun.rich.features.impl.misc;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.chat.ChatMessage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AntiCrashPickaxe extends Module {

    private static final int MIN_REMAINING = 50;
    private static final int SWITCH_TO_SLOT = 1;

    private static final String SOUND_1 = "Звук 1";
    private static final String SOUND_2 = "Звук 2";
    private static final String SOUND_OFF = "Выкл";

    private static final Identifier SOUND_NUCLEAR = Identifier.of("minecraft", "nuclear");
    private static final Identifier SOUND_WINDOWS = Identifier.of("minecraft", "windows");

    public final SelectSetting warnSound = new SelectSetting("Звук", SOUND_1);

    private long lastWarnMs;

    public AntiCrashPickaxe() {
        super("AntiCrashPickaxe", ModuleCategory.MISC);
        ensureSelectOptions(warnSound, SOUND_1, SOUND_2, SOUND_OFF);
        ensureSelectDefault(warnSound, SOUND_1, 0);
        setup(warnSound);
    }

    public static AntiCrashPickaxe getInstance() {
        return Instance.get(AntiCrashPickaxe.class);
    }

    public boolean shouldCancelAttackBlock(BlockPos pos, Direction direction) {
        if (!isState()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return false;

        ItemStack item = mc.player.getMainHandStack();
        if (item == null || item.isEmpty()) return false;
        if (!(item.getItem() instanceof PickaxeItem)) return false;
        if (!item.isDamageable()) return false;

        int max = item.getMaxDamage();
        if (max <= 0) return false;

        int remaining = max - item.getDamage();
        if (remaining >= MIN_REMAINING) return false;

        if (mc.player.getInventory().selectedSlot != SWITCH_TO_SLOT) {
            mc.player.getInventory().selectedSlot = SWITCH_TO_SLOT;
            if (mc.interactionManager != null) mc.interactionManager.syncSelectedSlot();
        }

        warn(remaining);
        return true;
    }

    private void warn(int remaining) {
        long now = System.currentTimeMillis();
        if (now - lastWarnMs < 1500L) return;
        lastWarnMs = now;

        ChatMessage.brandmessage("  §6⚠ §c§lОСТОРОЖНО §6⚠");
        ChatMessage.brandmessage("§fКирка почти сломана §7(§e" + remaining + "§7)");

        playWarnSound();
    }

    private void playWarnSound() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        String v = readString(warnSound, SOUND_1);
        if (v == null || v.isEmpty()) v = SOUND_1;
        if (eq(v, SOUND_OFF)) return;

        Identifier id = eq(v, SOUND_2) ? SOUND_WINDOWS : SOUND_NUCLEAR;

        try {
            SoundEvent ev = SoundEvent.of(id);
            mc.getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f));
        } catch (Throwable ignored) {
        }
    }

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static void ensureSelectDefault(Object setting, String def, int defIndex) {
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

    private static void ensureSelectOptions(Object setting, String... values) {
        if (setting == null || values == null || values.length == 0) return;

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

    private static boolean tryInvokeString(Object obj, String name, String v) {
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

    private static boolean tryInvokeInt(Object obj, String name, int v) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterCount() != 1) continue;
                Class<?> p = m.getParameterTypes()[0];
                if (p == int.class || p == Integer.class) {
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
                Class<?> p = m.getParameterTypes()[0];
                if (p == int.class || p == Integer.class) {
                    m.setAccessible(true);
                    m.invoke(obj, v);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean tryInvokeStringArray(Object obj, String name, String[] arr) {
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

    private static boolean tryInvokeList(Object obj, String name, List<String> list) {
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

    private static void trySetField(Object obj, String field, String[] arr, List<String> list) {
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

    private static void trySetStringField(Object obj, String v, String... names) {
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

    private static void trySetIntField(Object obj, int v, String... names) {
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

    private static String readString(Object setting, String def) {
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
}