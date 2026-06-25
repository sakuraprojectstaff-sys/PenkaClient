package fun.rich.features.impl.render;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ItemPhysic extends Module {

    private static ItemPhysic instance;

    public ItemPhysic() {
        super("ItemPhysic", ModuleCategory.RENDER);
        instance = this;
    }

    public static ItemPhysic getInstance() {
        return instance;
    }

    public static boolean enabled() {
        ItemPhysic m = instance;
        return m != null && m.readEnabled();
    }

    private boolean readEnabled() {
        Boolean v;

        v = asBool(invokeNoArgs(this, "isEnabled"));
        if (v != null) return v;

        v = asBool(invokeNoArgs(this, "isToggled"));
        if (v != null) return v;

        v = asBool(invokeNoArgs(this, "isState"));
        if (v != null) return v;

        v = asBool(invokeNoArgs(this, "getState"));
        if (v != null) return v;

        v = asBool(readField(this, "state"));
        if (v != null) return v;

        v = asBool(readField(this, "enabled"));
        if (v != null) return v;

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

    private static Boolean asBool(Object v) {
        return v instanceof Boolean ? (Boolean) v : null;
    }
}
