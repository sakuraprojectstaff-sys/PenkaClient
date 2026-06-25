package fun.rich.features.impl.render;

import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.Instance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ScoreBoard extends Module {
    public static ScoreBoard getInstance() {
        return Instance.get(ScoreBoard.class);
    }

    public ScoreBoard() {
        super("ScoreBoard", ModuleCategory.RENDER);
    }

    public void renderSidebar(DrawContext context, ScoreboardObjective objective) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.textRenderer == null || objective == null) return;

        Text title = objective.getDisplayName();
        List<LineEntry> lines = collectEntries(objective);
        if (lines.isEmpty()) return;

        int lineHeight = 9;
        int titleHeight = 9;
        int titleGap = 1;
        int paddingX = 6;
        int paddingTop = 3;
        int paddingBottom = 3;
        int radius = 6;
        int scoreGap = 6;

        int titleWidth = mc.textRenderer.getWidth(title);
        int contentWidth = titleWidth;

        for (LineEntry line : lines) {
            int nameWidth = mc.textRenderer.getWidth(line.name);
            int scoreWidth = line.scoreText == null || line.scoreText.isEmpty() ? 0 : mc.textRenderer.getWidth(line.scoreText);
            int rowWidth = nameWidth + (scoreWidth > 0 ? (scoreGap + scoreWidth) : 0);
            if (rowWidth > contentWidth) contentWidth = rowWidth;
        }

        int rowsHeight = lines.size() * lineHeight;
        int totalWidth = contentWidth + paddingX * 2;
        int totalHeight = paddingTop + titleHeight + titleGap + rowsHeight + paddingBottom;

        int screenWidth = mc.getWindow().getScaledWidth();
        int screenHeight = mc.getWindow().getScaledHeight();

        int vanillaBottomY = screenHeight / 2 + rowsHeight / 3;
        int x = screenWidth - totalWidth - 3;
        int y = vanillaBottomY - totalHeight;

        fillRoundedRect(context, x, y, totalWidth, totalHeight, radius, 0xB0000000);
        drawRoundedOutline(context, x, y, totalWidth, totalHeight, radius, 0x22FFFFFF);

        int titleX = x + (totalWidth - titleWidth) / 2;
        int titleY = y + paddingTop;
        context.drawText(mc.textRenderer, title, titleX, titleY, 0xFFFFFFFF, false);

        int leftX = x + paddingX;
        int rightX = x + totalWidth - paddingX;

        int rowsBottomY = y + totalHeight - paddingBottom;
        for (int i = 0; i < lines.size(); i++) {
            LineEntry line = lines.get(i);

            int lineY = rowsBottomY - (i + 1) * lineHeight;

            context.drawText(mc.textRenderer, line.name, leftX, lineY, 0xFFFFFFFF, false);

            if (line.scoreText != null && !line.scoreText.isEmpty()) {
                int scoreWidth = mc.textRenderer.getWidth(line.scoreText);
                context.drawText(mc.textRenderer, line.scoreText, rightX - scoreWidth, lineY, 0xFFFF5555, false);
            }
        }
    }

    private List<LineEntry> collectEntries(ScoreboardObjective objective) {
        List<LineEntry> out = new ArrayList<>();
        Scoreboard scoreboard = objective.getScoreboard();
        if (scoreboard == null) return out;

        if (!collectEntriesNewApi(scoreboard, objective, out)) {
            collectEntriesOldApi(scoreboard, objective, out);
        }

        if (out.size() > 15) {
            return new ArrayList<>(out.subList(out.size() - 15, out.size()));
        }
        return out;
    }

    private boolean collectEntriesNewApi(Scoreboard scoreboard, ScoreboardObjective objective, List<LineEntry> out) {
        try {
            Method getEntries = findMethod(scoreboard.getClass(), "getScoreboardEntries", 1);
            if (getEntries == null) return false;

            Object iterable = getEntries.invoke(scoreboard, objective);
            if (!(iterable instanceof Iterable<?> entries)) return false;

            for (Object entry : entries) {
                if (entry == null) continue;

                Object ownerObj = invokeFirstNoArg(entry, "owner", "getOwner");
                String owner = asOwnerString(ownerObj);
                if (owner == null) owner = invokeString(entry, "name", "getName");
                if (owner == null || owner.startsWith("#")) continue;

                Text displayText = extractEntryDisplayText(entry);
                Team team = resolveTeam(scoreboard, ownerObj, owner);

                Text nameText;
                if (displayText != null && !displayText.getString().isEmpty()) {
                    nameText = displayText;
                } else if (team != null) {
                    nameText = Team.decorateName(team, Text.literal(owner));
                } else {
                    nameText = Text.literal(owner);
                }

                if (nameText.getString().isEmpty() && team != null) {
                    nameText = Team.decorateName(team, Text.empty());
                }

                int scoreValue = extractEntryScoreValue(entry);
                out.add(new LineEntry(nameText, Integer.toString(scoreValue)));
            }

            return !out.isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void collectEntriesOldApi(Scoreboard scoreboard, ScoreboardObjective objective, List<LineEntry> out) {
        try {
            Method getScores = findMethod(scoreboard.getClass(), "getAllPlayerScores", 1);
            if (getScores == null) return;

            Object iterable = getScores.invoke(scoreboard, objective);
            if (!(iterable instanceof Iterable<?> scores)) return;

            for (Object score : scores) {
                if (score == null) continue;

                String playerName = invokeString(score, "getPlayerName", "playerName", "getName", "name");
                if (playerName == null || playerName.startsWith("#")) continue;

                Team team = resolveTeam(scoreboard, null, playerName);

                Text nameText = team != null
                        ? Team.decorateName(team, Text.literal(playerName))
                        : Text.literal(playerName);

                int scoreValue = invokeInt(score, "getScore", "score", "getScorePoints", "points", "getValue", "value");
                out.add(new LineEntry(nameText, Integer.toString(scoreValue)));
            }
        } catch (Throwable ignored) {
        }
    }

    private Text extractEntryDisplayText(Object entry) {
        Object displayObj = invokeFirstNoArg(entry, "display", "getDisplay", "formatted", "getFormatted");
        if (displayObj == null) return null;

        if (displayObj instanceof Text t) return t;

        if (displayObj instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value instanceof Text t) return t;
        }

        return null;
    }

    private int extractEntryScoreValue(Object entry) {
        int direct = invokeInt(entry, "value", "getValue", "score", "getScore", "points", "getPoints", "getScorePoints");
        if (direct != 0) return direct;

        Object nested = invokeFirstNoArg(entry, "score", "getScore", "valueData", "getValueData");
        if (nested != null) {
            return invokeInt(nested, "getScore", "score", "getValue", "value", "getScorePoints", "points");
        }

        return 0;
    }

    private Team resolveTeam(Scoreboard scoreboard, Object ownerObj, String ownerString) {
        Object teamObj;

        teamObj = invokeScoreboardTeamGetter(scoreboard, "getScoreHolderTeam", ownerObj, ownerString);
        if (teamObj instanceof Team t) return t;

        teamObj = invokeScoreboardTeamGetter(scoreboard, "getPlayerTeam", ownerObj, ownerString);
        if (teamObj instanceof Team t) return t;

        teamObj = invokeScoreboardTeamGetter(scoreboard, "getPlayersTeam", ownerObj, ownerString);
        if (teamObj instanceof Team t) return t;

        return null;
    }

    private Object invokeScoreboardTeamGetter(Scoreboard scoreboard, String methodName, Object ownerObj, String ownerString) {
        try {
            if (ownerObj != null) {
                Method exact = findExactMethod(scoreboard.getClass(), methodName, ownerObj.getClass());
                if (exact != null) {
                    exact.setAccessible(true);
                    return exact.invoke(scoreboard, ownerObj);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Method strMethod = findExactMethod(scoreboard.getClass(), methodName, String.class);
            if (strMethod != null) {
                strMethod.setAccessible(true);
                return strMethod.invoke(scoreboard, ownerString);
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Method m : scoreboard.getClass().getMethods()) {
                if (!m.getName().equals(methodName) || m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                Class<?> p = m.getParameterTypes()[0];

                if (ownerObj != null && p.isAssignableFrom(ownerObj.getClass())) {
                    try {
                        return m.invoke(scoreboard, ownerObj);
                    } catch (Throwable ignored) {
                    }
                }

                if (ownerString != null && p == String.class) {
                    try {
                        return m.invoke(scoreboard, ownerString);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private String asOwnerString(Object ownerObj) {
        if (ownerObj == null) return null;
        if (ownerObj instanceof String s) return s;

        String v = invokeString(ownerObj, "getName", "name", "asString", "stringValue", "owner", "getOwner");
        if (v != null) return v;

        String fallback = ownerObj.toString();
        return fallback == null || fallback.isEmpty() ? null : fallback;
    }

    private Object invokeFirstNoArg(Object target, String... methods) {
        for (String method : methods) {
            try {
                Method m = findExactMethod(target.getClass(), method);
                if (m == null) continue;
                m.setAccessible(true);
                return m.invoke(target);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private String invokeString(Object target, String... methods) {
        for (String method : methods) {
            try {
                Method m = findExactMethod(target.getClass(), method);
                if (m == null) continue;
                m.setAccessible(true);
                Object value = m.invoke(target);
                if (value instanceof String s) return s;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private int invokeInt(Object target, String... methods) {
        for (String method : methods) {
            try {
                Method m = findExactMethod(target.getClass(), method);
                if (m == null) continue;
                m.setAccessible(true);
                Object value = m.invoke(target);
                if (value instanceof Number n) return n.intValue();
                if (value instanceof String s) return Integer.parseInt(s);
            } catch (Throwable ignored) {
            }
        }
        return 0;
    }

    private Method findMethod(Class<?> cls, String name, int paramCount) {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) return m;
        }
        return null;
    }

    private Method findExactMethod(Class<?> cls, String name, Class<?>... args) {
        try {
            return cls.getMethod(name, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void fillRoundedRect(DrawContext context, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;

        int rr = Math.max(1, Math.min(r, Math.min(w, h) / 2));

        context.fill(x + rr, y, x + w - rr, y + h, color);
        context.fill(x, y + rr, x + w, y + h - rr, color);

        for (int i = 0; i < rr; i++) {
            context.fill(x + i, y + rr - i - 1, x + w - i, y + rr - i, color);
            context.fill(x + i, y + h - rr + i, x + w - i, y + h - rr + i + 1, color);
        }
    }

    private void drawRoundedOutline(DrawContext context, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;

        int rr = Math.max(1, Math.min(r, Math.min(w, h) / 2));

        context.fill(x + rr, y, x + w - rr, y + 1, color);
        context.fill(x + rr, y + h - 1, x + w - rr, y + h, color);
        context.fill(x, y + rr, x + 1, y + h - rr, color);
        context.fill(x + w - 1, y + rr, x + w, y + h - rr, color);

        for (int i = 0; i < rr; i++) {
            context.fill(x + i, y + rr - i - 1, x + i + 1, y + rr - i, color);
            context.fill(x + w - i - 1, y + rr - i - 1, x + w - i, y + rr - i, color);
            context.fill(x + i, y + h - rr + i, x + i + 1, y + h - rr + i + 1, color);
            context.fill(x + w - i - 1, y + h - rr + i, x + w - i, y + h - rr + i + 1, color);
        }
    }

    private static final class LineEntry {
        private final Text name;
        private final String scoreText;

        private LineEntry(Text name, String scoreText) {
            this.name = name;
            this.scoreText = scoreText;
        }
    }
}