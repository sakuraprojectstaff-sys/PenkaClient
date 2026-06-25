package fun.rich.utils.client.window;

import antidaunleak.api.UserProfile;

public class WindowTitleAnimation {
    private static final WindowTitleAnimation INSTANCE = new WindowTitleAnimation();

    private String currentTitle;
    private int animationTick = 0;
    private boolean isRemoving = true;
    private boolean isUserPhase = true;
    private int pauseTicks = 0;
    private final int delayTicks = 1;
    private final int pauseDuration = 100;

    private WindowTitleAnimation() {
        this.currentTitle = safeUserText();
    }

    public static WindowTitleAnimation getInstance() {
        return INSTANCE;
    }

    private static String safeProfile(String key, String fallback) {
        try {
            UserProfile p = UserProfile.getInstance();
            if (p == null) return fallback;
            String v = p.profile(key);
            if (v == null || v.isEmpty()) return fallback;
            return v;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String safeUserText() {
        return "<User: " + safeProfile("username", "unknown") + ">";
    }

    private static String safeUidText() {
        return "<Uid: " + safeProfile("uid", "0") + ">";
    }

    public void updateTitle() {
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }

        if (animationTick >= delayTicks) {
            String newTitle;
            String fullText = isUserPhase ? safeUserText() : safeUidText();

            if (currentTitle == null || currentTitle.isEmpty()) {
                currentTitle = "<";
            }

            if (isRemoving) {
                if (currentTitle.length() > 1) {
                    newTitle = currentTitle.substring(0, currentTitle.length() - 1);
                } else {
                    newTitle = "<";
                    isRemoving = false;
                }
            } else {
                if (currentTitle.length() < fullText.length()) {
                    newTitle = fullText.substring(0, currentTitle.length() + 1);
                } else {
                    newTitle = fullText;
                    isRemoving = true;
                    isUserPhase = !isUserPhase;
                    pauseTicks = pauseDuration;
                }
            }

            currentTitle = newTitle;
            animationTick = 0;
        }

        animationTick++;
    }

    public String getCurrentTitle() {
        if (currentTitle == null || currentTitle.isEmpty()) {
            currentTitle = safeUserText();
        }
        return "Sakura 1.21.4 " + currentTitle;
    }
}