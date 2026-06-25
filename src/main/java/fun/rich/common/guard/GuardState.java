package fun.rich.common.guard;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class GuardState {
    private static final GuardState INSTANCE = new GuardState();

    private final AtomicBoolean compromised = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicLong compromisedAt = new AtomicLong(0L);
    private final AtomicReference<String> reason = new AtomicReference<>("");
    private final AtomicReference<String> source = new AtomicReference<>("");

    private GuardState() {
    }

    public static GuardState getInstance() {
        return INSTANCE;
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public void markInitialized() {
        initialized.set(true);
    }

    public boolean isCompromised() {
        return compromised.get();
    }

    public long getCompromisedAt() {
        return compromisedAt.get();
    }

    public String getReason() {
        String v = reason.get();
        return v == null ? "" : v;
    }

    public String getSource() {
        String v = source.get();
        return v == null ? "" : v;
    }

    public boolean compromise(String source, String reason) {
        if (!compromised.compareAndSet(false, true)) {
            return false;
        }
        this.source.set(safe(source));
        this.reason.set(safe(reason));
        this.compromisedAt.set(System.currentTimeMillis());
        return true;
    }

    public void updateReason(String source, String reason) {
        this.source.set(safe(source));
        this.reason.set(safe(reason));
        if (compromisedAt.get() <= 0L) {
            compromisedAt.set(System.currentTimeMillis());
        }
    }

    public void resetForDebug() {
        compromised.set(false);
        initialized.set(false);
        compromisedAt.set(0L);
        source.set("");
        reason.set("");
    }

    public String summary() {
        if (!isCompromised()) {
            return "GuardState{ok}";
        }
        return "GuardState{compromised=true, source='" + getSource() + "', reason='" + getReason() + "', at=" + getCompromisedAt() + "}";
    }

    private static String safe(String s) {
        if (s == null) return "";
        String v = s.trim();
        return v.length() > 256 ? v.substring(0, 256) : v;
    }
}