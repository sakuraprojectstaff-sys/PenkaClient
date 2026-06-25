package fun.rich.common.guard;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GuardBootstrap {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean COMPROMISE_LOGGED = new AtomicBoolean(false);

    private GuardBootstrap() {
    }

    public static void init() {
        if (!STARTED.compareAndSet(false, true)) return;

        RuntimeGuard guard = RuntimeGuard.getInstance();
        guard.setEnabled(true);
        guard.setCheckPeriodMs(8000L);

        guard.addRequiredClass("fun.rich.Rich");
        guard.addRequiredClass("fun.rich.mixins.client.MinecraftClientMixin");
        guard.addRequiredClass("fun.rich.features.impl.misc.TelegramBot");

        guard.addRequiredClass("fun.rich.features.impl.combat.Aura");
        guard.addRequiredClass("fun.rich.features.module.Module");

        guard.addForbiddenClass("org.jd.gui.api.API");
        guard.addForbiddenClass("com.sun.tools.attach.VirtualMachine");
        guard.addForbiddenClass("javassist.Loader");

        int loaded = 0;
        loaded += tryLoadHashes("/rich/guard.hashes");
        loaded += tryLoadHashes("/assets/rich/guard.hashes");
        loaded += tryLoadHashes("/guard.hashes");

        guard.init();

        logInfo("Guard init | hashes=" + loaded + " | " + guard.dumpStatus());
    }

    public static void tick() {
        if (!STARTED.get()) return;

        RuntimeGuard guard = RuntimeGuard.getInstance();
        guard.tick();

        if (guard.getState().isCompromised() && COMPROMISE_LOGGED.compareAndSet(false, true)) {
            logError("Guard compromised | " + guard.getState().summary());
        }
    }

    public static boolean isStarted() {
        return STARTED.get();
    }

    public static boolean isCompromised() {
        return RuntimeGuard.getInstance().getState().isCompromised();
    }

    public static String status() {
        return RuntimeGuard.getInstance().dumpStatus() + " | " + RuntimeGuard.getInstance().getState().summary();
    }

    private static int tryLoadHashes(String path) {
        try {
            return RuntimeGuard.getInstance().loadHashesFromResource(path);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void logInfo(String msg) {
        if (!tryLogger("info", msg)) {
            System.out.println("[Guard] " + safe(msg));
        }
    }

    private static void logError(String msg) {
        if (!tryLogger("error", msg)) {
            System.err.println("[Guard] " + safe(msg));
        }
    }

    private static boolean tryLogger(String methodName, String msg) {
        try {
            Class<?> loggerClass = Class.forName("fun.rich.utils.client.logs.Logger");
            Method m = loggerClass.getMethod(methodName, String.class);
            m.invoke(null, "[Guard] " + safe(msg));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}