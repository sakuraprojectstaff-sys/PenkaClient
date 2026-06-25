package fun.rich.common.guard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RuntimeGuard {
    private static final RuntimeGuard INSTANCE = new RuntimeGuard();

    private final GuardState state = GuardState.getInstance();

    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong checkPeriodMs = new AtomicLong(8000L);
    private final AtomicLong lastCheckAt = new AtomicLong(0L);
    private final AtomicLong lastOkAt = new AtomicLong(0L);

    private final Map<String, String> expectedClassHashes = new ConcurrentHashMap<>();
    private final Map<String, String> expectedResourceHashes = new ConcurrentHashMap<>();
    private final LinkedHashSet<String> requiredClasses = new LinkedHashSet<>();
    private final LinkedHashSet<String> forbiddenClasses = new LinkedHashSet<>();

    private RuntimeGuard() {
        requiredClasses.add("fun.rich.Rich");
        requiredClasses.add("fun.rich.mixins.client.MinecraftClientMixin");
        requiredClasses.add("fun.rich.features.impl.misc.TelegramBot");
    }

    public static RuntimeGuard getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public long getCheckPeriodMs() {
        return checkPeriodMs.get();
    }

    public void setCheckPeriodMs(long ms) {
        if (ms < 1000L) ms = 1000L;
        checkPeriodMs.set(ms);
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public long getLastCheckAt() {
        return lastCheckAt.get();
    }

    public long getLastOkAt() {
        return lastOkAt.get();
    }

    public void init() {
        if (!initialized.compareAndSet(false, true)) return;
        state.markInitialized();
        forceCheck();
    }

    public void tick() {
        if (!enabled.get()) return;
        if (state.isCompromised()) return;

        long now = System.currentTimeMillis();
        long last = lastCheckAt.get();
        long period = checkPeriodMs.get();

        if (now - last < period) return;
        forceCheck();
    }

    public boolean forceCheck() {
        if (!enabled.get()) return false;
        if (state.isCompromised()) return false;

        long now = System.currentTimeMillis();
        lastCheckAt.set(now);

        try {
            if (!checkRequiredClasses()) return false;
            if (!checkForbiddenClasses()) return false;

            lastOkAt.set(now);
            return true;
        } catch (Throwable t) {
            compromise("RuntimeGuard", "exception:" + t.getClass().getSimpleName());
            return false;
        }
    }

    public boolean compromise(String source, String reason) {
        return state.compromise(source, reason);
    }

    public GuardState getState() {
        return state;
    }

    public void addRequiredClass(String className) {
        String n = safe(className);
        if (n.isEmpty()) return;
        synchronized (requiredClasses) {
            requiredClasses.add(n);
        }
    }

    public void removeRequiredClass(String className) {
        String n = safe(className);
        if (n.isEmpty()) return;
        synchronized (requiredClasses) {
            requiredClasses.remove(n);
        }
    }

    public List<String> getRequiredClasses() {
        synchronized (requiredClasses) {
            return new ArrayList<>(requiredClasses);
        }
    }

    public void addForbiddenClass(String className) {
        String n = safe(className);
        if (n.isEmpty()) return;
        synchronized (forbiddenClasses) {
            forbiddenClasses.add(n);
        }
    }

    public void removeForbiddenClass(String className) {
        String n = safe(className);
        if (n.isEmpty()) return;
        synchronized (forbiddenClasses) {
            forbiddenClasses.remove(n);
        }
    }

    public List<String> getForbiddenClasses() {
        synchronized (forbiddenClasses) {
            return new ArrayList<>(forbiddenClasses);
        }
    }

    public void setExpectedClassHash(String className, String sha256Hex) {
        String cls = safe(className);
        String hex = IntegrityUtil.normalizeHex(sha256Hex);
        if (cls.isEmpty() || !IntegrityUtil.looksLikeSha256(hex)) return;
        expectedClassHashes.put(cls, hex);
    }

    public void removeExpectedClassHash(String className) {
        String cls = safe(className);
        if (cls.isEmpty()) return;
        expectedClassHashes.remove(cls);
    }

    public Map<String, String> getExpectedClassHashes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(expectedClassHashes));
    }

    public void setExpectedResourceHash(String resourcePath, String sha256Hex) {
        String path = safe(resourcePath);
        String hex = IntegrityUtil.normalizeHex(sha256Hex);
        if (path.isEmpty() || !IntegrityUtil.looksLikeSha256(hex)) return;
        expectedResourceHashes.put(path, hex);
    }

    public void removeExpectedResourceHash(String resourcePath) {
        String path = safe(resourcePath);
        if (path.isEmpty()) return;
        expectedResourceHashes.remove(path);
    }

    public Map<String, String> getExpectedResourceHashes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(expectedResourceHashes));
    }

    public int loadHashesFromResource(String resourcePath) {
        expectedClassHashes.clear();
        expectedResourceHashes.clear();
        return 0;
    }

    public String dumpStatus() {
        return "RuntimeGuard{enabled=" + enabled.get()
                + ", initialized=" + initialized.get()
                + ", compromised=" + state.isCompromised()
                + ", lastCheckAt=" + lastCheckAt.get()
                + ", lastOkAt=" + lastOkAt.get()
                + ", expectedClassHashes=" + expectedClassHashes.size()
                + ", expectedResourceHashes=" + expectedResourceHashes.size()
                + "}";
    }

    private boolean checkRequiredClasses() {
        List<String> copy;
        synchronized (requiredClasses) {
            copy = new ArrayList<>(requiredClasses);
        }

        for (String className : copy) {
            if (className.isEmpty()) continue;
            byte[] b = IntegrityUtil.readClassBytes(className);
            if (b == null || b.length == 0) {
                compromise("RuntimeGuard", "required_class_missing:" + className);
                return false;
            }
        }
        return true;
    }

    private boolean checkForbiddenClasses() {
        List<String> copy;
        synchronized (forbiddenClasses) {
            copy = new ArrayList<>(forbiddenClasses);
        }

        if (copy.isEmpty()) return true;

        ClassLoader cl = RuntimeGuard.class.getClassLoader();
        for (String className : copy) {
            if (className.isEmpty()) continue;
            try {
                Class<?> hit = Class.forName(className, false, cl);
                if (isAllowedJdkAttachClass(className, hit)) {
                    continue;
                }
                compromise("RuntimeGuard", "forbidden_class_present:" + className);
                return false;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                if (isBenignForbiddenCheckThrowable(t)) {
                    continue;
                }
                compromise("RuntimeGuard", "forbidden_check_error:" + className + ":" + t.getClass().getSimpleName());
                return false;
            }
        }

        return true;
    }

    private boolean checkClassHashes() {
        return true;
    }

    private boolean checkResourceHashes() {
        return true;
    }

    private static boolean isAllowedJdkAttachClass(String className, Class<?> cls) {
        String n = safe(className);
        if (n.isEmpty() || cls == null) return false;

        boolean attachName = n.equals("com.sun.tools.attach.VirtualMachine")
                || n.startsWith("com.sun.tools.attach.")
                || n.startsWith("sun.tools.attach.");

        if (!attachName) return false;

        String moduleName = "";
        try {
            Module m = cls.getModule();
            if (m != null && m.isNamed()) moduleName = safe(m.getName());
        } catch (Throwable ignored) {
        }

        if ("jdk.attach".equals(moduleName)) return true;

        try {
            ClassLoader loader = cls.getClassLoader();
            if (loader == null) return true;
            ClassLoader platform = ClassLoader.getPlatformClassLoader();
            return loader == platform;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBenignForbiddenCheckThrowable(Throwable t) {
        if (t == null) return false;
        String n = t.getClass().getName();
        if (n == null) return false;
        return n.endsWith("UnsupportedClassVersionError")
                || n.endsWith("NoClassDefFoundError")
                || n.endsWith("LinkageError")
                || n.endsWith("IncompatibleClassChangeError");
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}