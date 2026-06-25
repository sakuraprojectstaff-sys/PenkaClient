package fun.rich.features.impl.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Naruto {

    private static volatile Naruto INSTANCE;

    private final Cosmetic p;

    public Naruto(Cosmetic p) {
        this.p = p;
        INSTANCE = this;
    }

    public void onDisable() {
    }

    public static boolean enabled() {
        Naruto i = INSTANCE;
        if (i == null) return true;
        return i.isEnabled();
    }

    private boolean isEnabled() {
        if (p == null) return false;

        try {
            Method m = p.getClass().getDeclaredMethod("narutoOn");
            m.setAccessible(true);
            Object v = m.invoke(p);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Throwable ignored) {
        }

        for (String fn : new String[]{"narutoEnabled", "narutoRunEnabled", "naruto", "narutoRun"}) {
            try {
                Field f = p.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object setting = f.get(p);
                return Cosmetic.bool(setting);
            } catch (Throwable ignored) {
            }
        }

        return false;
    }
}
