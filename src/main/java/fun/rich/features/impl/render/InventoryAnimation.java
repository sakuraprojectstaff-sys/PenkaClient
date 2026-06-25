package fun.rich.features.impl.render;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InventoryAnimation extends Module {

    private static final String[] MODES = {
            "Scale",
            "Bounce",
            "SlideUp",
            "SlideDown",
            "SlideLeft",
            "SlideRight",
            "Flip",
            "Warp",
            "Glitch"
    };

    public static InventoryAnimation getInstance() {
        return Instance.get(InventoryAnimation.class);
    }

    public final SelectSetting animationType = new SelectSetting("Анимация", "Тип анимации");
    public final SliderSettings speed = new SliderSettings("Скорость", "Скорость анимации");
    public final BooleanSetting inventoryOnly = new BooleanSetting("Только инвентарь", "Анимация только обычного инвентаря");

    public InventoryAnimation() {
        super("InventoryAnimation", ModuleCategory.RENDER);
        initAnimationType();
        initSpeed();
        initInventoryOnly();
        registerSettings();
    }

    public String getAnimationMode() {
        Object value = callNoArgs(animationType, "getSelected");
        if (value != null) return String.valueOf(value);

        value = callNoArgs(animationType, "getValue");
        if (value != null) return String.valueOf(value);

        value = callNoArgs(animationType, "get");
        if (value != null) return String.valueOf(value);

        value = readField(animationType, "selected");
        if (value != null) return String.valueOf(value);

        value = readField(animationType, "value");
        if (value != null) return String.valueOf(value);

        return "Scale";
    }

    public float getAnimationSpeed() {
        Object value = callNoArgs(speed, "getValue");
        if (value instanceof Number n) return n.floatValue();

        value = callNoArgs(speed, "get");
        if (value instanceof Number n) return n.floatValue();

        value = readField(speed, "value");
        if (value instanceof Number n) return n.floatValue();

        return 10.0f;
    }

    public boolean isInventoryOnly() {
        Object value = callNoArgs(inventoryOnly, "isValue");
        if (value instanceof Boolean b) return b;

        value = callNoArgs(inventoryOnly, "getValue");
        if (value instanceof Boolean b) return b;

        value = callNoArgs(inventoryOnly, "get");
        if (value instanceof Boolean b) return b;

        value = readField(inventoryOnly, "value");
        if (value instanceof Boolean b) return b;

        return false;
    }

    private void initAnimationType() {
        List<String> list = new ArrayList<>(Arrays.asList(MODES));

        writeField(animationType, "list", list);
        writeField(animationType, "values", list);
        writeField(animationType, "modes", list);

        if (callOneArg(animationType, "setList", list) == null) {
            callOneArg(animationType, "list", list);
        }

        if (callOneArg(animationType, "selected", "Scale") == null
                && callOneArg(animationType, "setSelected", "Scale") == null
                && callOneArg(animationType, "setValue", "Scale") == null
                && callOneArg(animationType, "set", "Scale") == null) {
            writeField(animationType, "selected", "Scale");
            writeField(animationType, "value", "Scale");
        }

        Object currentList = callNoArgs(animationType, "getList");
        if (!(currentList instanceof List<?>)) {
            writeField(animationType, "list", new ArrayList<>(Arrays.asList(MODES)));
        }
    }

    private void initSpeed() {
        callOneArg(speed, "min", 1.0f);
        callOneArg(speed, "setMin", 1.0f);
        callOneArg(speed, "max", 30.0f);
        callOneArg(speed, "setMax", 30.0f);
        callOneArg(speed, "increment", 0.1f);
        callOneArg(speed, "setIncrement", 0.1f);
        callOneArg(speed, "step", 0.1f);
        callOneArg(speed, "setStep", 0.1f);
        callOneArg(speed, "value", 10.0f);
        callOneArg(speed, "setValue", 10.0f);
        callOneArg(speed, "set", 10.0f);

        writeField(speed, "min", 1.0f);
        writeField(speed, "max", 30.0f);
        writeField(speed, "increment", 0.1f);
        writeField(speed, "step", 0.1f);
        writeField(speed, "value", 10.0f);
    }

    private void initInventoryOnly() {
        callOneArg(inventoryOnly, "value", false);
        callOneArg(inventoryOnly, "setValue", false);
        callOneArg(inventoryOnly, "set", false);
        writeField(inventoryOnly, "value", false);
    }

    private void registerSettings() {
        Object repository = settings();
        if (repository == null) return;

        addSetting(repository, animationType);
        addSetting(repository, speed);
        addSetting(repository, inventoryOnly);
    }

    private void addSetting(Object repository, Object setting) {
        if (callOneArg(repository, "add", setting) != null) return;
        if (callOneArg(repository, "setting", setting) != null) return;
        if (callOneArg(repository, "register", setting) != null) return;
        callOneArg(repository, "append", setting);
    }

    private Object callNoArgs(Object target, String name) {
        if (target == null) return null;

        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != 0) continue;

            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private Object callOneArg(Object target, String name, Object arg) {
        if (target == null) return null;

        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name)) continue;
            if (method.getParameterCount() != 1) continue;

            Class<?> type = method.getParameterTypes()[0];
            if (!isCompatible(type, arg)) continue;

            try {
                method.setAccessible(true);
                method.invoke(target, adapt(type, arg));
                return Boolean.TRUE;
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private Object adapt(Class<?> type, Object arg) {
        if (!(arg instanceof Number n)) return arg;

        if (type == float.class || type == Float.class) return n.floatValue();
        if (type == double.class || type == Double.class) return n.doubleValue();
        if (type == int.class || type == Integer.class) return n.intValue();
        if (type == long.class || type == Long.class) return n.longValue();
        if (type == short.class || type == Short.class) return n.shortValue();
        if (type == byte.class || type == Byte.class) return n.byteValue();

        return arg;
    }

    private boolean isCompatible(Class<?> type, Object arg) {
        if (arg == null) return !type.isPrimitive();
        if (type.isInstance(arg)) return true;

        if (arg instanceof Number) {
            return type == float.class || type == Float.class
                    || type == double.class || type == Double.class
                    || type == int.class || type == Integer.class
                    || type == long.class || type == Long.class
                    || type == short.class || type == Short.class
                    || type == byte.class || type == Byte.class;
        }

        if (arg instanceof Boolean) {
            return type == boolean.class || type == Boolean.class;
        }

        return false;
    }

    private Object readField(Object target, String name) {
        if (target == null) return null;

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    private void writeField(Object target, String name, Object value) {
        if (target == null) return;

        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);

                Class<?> type = field.getType();
                if (!isCompatible(type, value) && value != null && !type.isAssignableFrom(value.getClass())) {
                    current = current.getSuperclass();
                    continue;
                }

                field.set(target, adapt(type, value));
                return;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
    }
}