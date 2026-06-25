package fun.rich.display.hud;

import com.google.common.base.Suppliers;
import fun.rich.features.impl.render.Hud;
import fun.rich.utils.client.managers.api.draggable.AbstractDraggable;
import fun.rich.utils.display.atlasfont.msdf.MsdfFont;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.display.systemrender.builders.Builder;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.Color;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.function.Supplier;

public class Watermark extends AbstractDraggable {
    private int fpsCount = 0;
    private int ping = 0;
    private double tps = 20.0;
    private double bps = 0.0;
    private double lastX = Double.NaN;
    private double lastZ = Double.NaN;

    private static final Supplier<MsdfFont> VALERA2 = Suppliers.memoize(() -> MsdfFont.builder().atlas("icons2").data("icons2").build());

    public Watermark() {
        super("Watermark", 10, 10, 120, 50, true);
    }

    public void tick() {
        fpsCount = mc.getCurrentFps();

        if (mc.player != null && mc.getNetworkHandler() != null) {
            var playerInfo = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (playerInfo != null) ping = playerInfo.getLatency();
        }

        if (mc.player != null) {
            if (Double.isNaN(lastX) || Double.isNaN(lastZ)) {
                lastX = mc.player.getX();
                lastZ = mc.player.getZ();
                bps = 0.0;
            } else {
                double deltaX = mc.player.getX() - lastX;
                double deltaZ = mc.player.getZ() - lastZ;
                bps = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * 20;
                lastX = mc.player.getX();
                lastZ = mc.player.getZ();
            }
        } else {
            lastX = Double.NaN;
            lastZ = Double.NaN;
            bps = 0.0;
        }

        tps = 20.0;
    }

    private String getMoscowTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        return sdf.format(new Date());
    }

    @Override
    public void drawDraggable(DrawContext e) {
        MatrixStack matrix = e.getMatrices();

        String iconA = "A";
        String iconB = "X";
        String iconC = "W";
        String iconTime = "V";
        String iconCoords = "F";
        String iconPing = "Q";
        String iconTPS = "$";
        String iconBPS = "@";

        String username = mc.player != null ? mc.player.getName().getString() : "Player";
        String fps = String.valueOf(fpsCount);
        String time = getMoscowTime();
        String coords = String.format("%.0f %.0f %.0f",
                mc.player != null ? mc.player.getX() : 0,
                mc.player != null ? mc.player.getY() : 0,
                mc.player != null ? mc.player.getZ() : 0);

        String pingLabel = "Ping";
        String pingText = String.valueOf(ping);
        String tpsLabel = "Tps";
        String tpsText = String.format("%.1f", tps).replace('.', ',');
        String bpsLabel = "Bps";
        String bpsText = String.format("%.1f", bps).replace('.', ',');

        float startX = getX();
        float startY = getY();
        float padding = 4f;
        float spacing = 3f;
        float rectHeight = 14f;
        float verticalSpacing = 3f;

        int iconSize = 9;
        int iconDrawSize = iconSize - 2;
        int textSize = 12;

        float iconTextPad = 2f;
        float textY = 5.6f;

        int logoDrawSize = iconSize + 13;
        float logoYOffset = 3.20f;
        float logoGroupXOffset = 0.22f;
        float logoScaleX = 1.18f;

        String versionText = "Release";
        float versionGap = 2.0f;
        float versionYOffset = textY;

        int theme = forceAlpha(getHudThemeColor(), 255);
        int textColor = 0xFFFFFFFF;

        Fonts.Type textType = resolveTextType();
        FontRenderer textFont = Fonts.getSize(textSize, textType);
        FontRenderer versionFont = Fonts.getSize(textSize, textType);

        FontRenderer logoFont = Fonts.getSize(logoDrawSize, Fonts.Type.ICONS);
        float iconADrawWidth = logoFont.getStringWidth(iconA);
        float logoEffectiveWidth = iconADrawWidth * logoScaleX;

        float versionWidth = versionFont.getStringWidth(versionText);
        float groupWidth = logoEffectiveWidth + versionGap + versionWidth;

        float rect1Width = Math.max(rectHeight, groupWidth + padding * 2);

        drawRect(matrix, startX, startY, rect1Width, rectHeight);

        float groupStartX = startX + (rect1Width - groupWidth) * 0.5f + logoGroupXOffset;
        float logoX = groupStartX;

        matrix.push();
        matrix.translate(logoX, startY + logoYOffset, 0.0f);
        matrix.scale(logoScaleX, 1.0f, 1.0f);
        logoFont.drawString(matrix, iconA, 0.0f, 0.0f, theme);
        matrix.pop();

        float versionX = groupStartX + logoEffectiveWidth + versionGap;
        versionFont.drawString(matrix, versionText, versionX, startY + versionYOffset, textColor);

        float currentX = startX + rect1Width + spacing;

        float iconBWidth = VALERA2.get().getWidth(iconB, iconDrawSize);
        String fpsLabel = "Fps";
        float fpsLabelWidth = textFont.getStringWidth(fpsLabel);
        float fpsWidth = textFont.getStringWidth(fps);

        float iconCWidth = VALERA2.get().getWidth(iconC, iconDrawSize);
        float usernameWidth = textFont.getStringWidth(username);

        float iconTimeWidth = VALERA2.get().getWidth(iconTime, iconDrawSize);
        float timeWidth = textFont.getStringWidth(time);

        float rect2Width = padding * 2
                + iconBWidth + iconTextPad + fpsLabelWidth + 2 + fpsWidth + 4
                + iconCWidth + iconTextPad + usernameWidth + 4
                + iconTimeWidth + iconTextPad + timeWidth;

        drawRect(matrix, currentX, startY, rect2Width, rectHeight);

        float textX = currentX + padding;

        Builder.text()
                .font(VALERA2.get())
                .text(iconB)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), textX, startY + 3);
        textX += iconBWidth + iconTextPad;

        textFont.drawString(matrix, fpsLabel, textX, startY + textY, textColor);
        textX += fpsLabelWidth + 2;

        textFont.drawString(matrix, fps, textX, startY + textY, textColor);
        textX += fpsWidth + 4;

        Builder.text()
                .font(VALERA2.get())
                .text(iconC)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), textX, startY + 3);
        textX += iconCWidth + iconTextPad;

        textFont.drawString(matrix, username, textX, startY + textY, textColor);
        textX += usernameWidth + 4;

        Builder.text()
                .font(VALERA2.get())
                .text(iconTime)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), textX, startY + 3);
        textX += iconTimeWidth + iconTextPad;

        textFont.drawString(matrix, time, textX, startY + textY, textColor);

        float row2Y = startY + rectHeight + verticalSpacing;

        float iconCoordsWidth = VALERA2.get().getWidth(iconCoords, iconDrawSize);
        float coordsWidth = textFont.getStringWidth(coords);
        float rect3Width = padding * 2 + iconCoordsWidth + iconTextPad + coordsWidth;

        drawRect(matrix, startX, row2Y, rect3Width, rectHeight);

        Builder.text()
                .font(VALERA2.get())
                .text(iconCoords)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), startX + padding, row2Y + 3);

        textFont.drawString(matrix, coords, startX + padding + iconCoordsWidth + iconTextPad, row2Y + textY, textColor);

        currentX = startX + rect3Width + spacing;

        float iconPingWidth = VALERA2.get().getWidth(iconPing, iconDrawSize);
        float pingLabelWidth = textFont.getStringWidth(pingLabel);
        float pingWidth = textFont.getStringWidth(pingText);
        float rect4Width = padding * 2 + iconPingWidth + iconTextPad + pingLabelWidth + 2 + pingWidth;

        drawRect(matrix, currentX, row2Y, rect4Width, rectHeight);

        float pingX = currentX + padding;

        Builder.text()
                .font(VALERA2.get())
                .text(iconPing)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), pingX, row2Y + 3);
        pingX += iconPingWidth + iconTextPad;

        textFont.drawString(matrix, pingLabel, pingX, row2Y + textY, textColor);
        pingX += pingLabelWidth + 2;

        textFont.drawString(matrix, pingText, pingX, row2Y + textY, textColor);

        currentX += rect4Width + spacing;

        float iconTPSWidth = VALERA2.get().getWidth(iconTPS, iconDrawSize);
        float tpsLabelWidth = textFont.getStringWidth(tpsLabel);
        float tpsWidth = textFont.getStringWidth(tpsText);
        float rect5Width = padding * 2 + iconTPSWidth + iconTextPad + tpsLabelWidth + 2 + tpsWidth;

        drawRect(matrix, currentX, row2Y, rect5Width, rectHeight);

        float tpsX = currentX + padding;

        Builder.text()
                .font(VALERA2.get())
                .text(iconTPS)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), tpsX, row2Y + 3);
        tpsX += iconTPSWidth + iconTextPad;

        textFont.drawString(matrix, tpsLabel, tpsX, row2Y + textY, textColor);
        tpsX += tpsLabelWidth + 2;

        textFont.drawString(matrix, tpsText, tpsX, row2Y + textY, textColor);

        currentX += rect5Width + spacing;

        float iconBPSWidth = VALERA2.get().getWidth(iconBPS, iconDrawSize);
        float bpsLabelWidth = textFont.getStringWidth(bpsLabel);
        float bpsWidth = textFont.getStringWidth(bpsText);
        float rect6Width = padding * 2 + iconBPSWidth + iconTextPad + bpsLabelWidth + 2 + bpsWidth;

        drawRect(matrix, currentX, row2Y, rect6Width, rectHeight);

        float bpsX = currentX + padding;

        Builder.text()
                .font(VALERA2.get())
                .text(iconBPS)
                .size(iconDrawSize)
                .color(theme)
                .build()
                .render(matrix.peek().getPositionMatrix(), bpsX, row2Y + 3);
        bpsX += iconBPSWidth + iconTextPad;

        textFont.drawString(matrix, bpsLabel, bpsX, row2Y + textY, textColor);
        bpsX += bpsLabelWidth + 2;

        textFont.drawString(matrix, bpsText, bpsX, row2Y + textY, textColor);

        float totalWidth = Math.max(rect1Width + spacing + rect2Width,
                rect3Width + spacing + rect4Width + spacing + rect5Width + spacing + rect6Width);
        setWidth((int) totalWidth);
        setHeight((int) (rectHeight * 2 + verticalSpacing));
    }

    private void drawRect(MatrixStack matrix, float x, float y, float width, float height) {
        blur.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(4f).quality(12)
                .color(new Color(0, 0, 0, 110).getRGB())
                .build());

        rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                .round(4f)
                .thickness(0.1f)
                .outlineColor(new Color(18, 19, 20, 28).getRGB())
                .color(
                        new Color(18, 19, 20, 56).getRGB(),
                        new Color(0, 2, 5, 56).getRGB(),
                        new Color(0, 2, 5, 56).getRGB(),
                        new Color(18, 19, 20, 56).getRGB())
                .build());
    }

    private static int getHudThemeColor() {
        try {
            Hud hud = Hud.getInstance();
            if (hud != null && hud.colorSetting != null) {
                Integer v = tryInt(hud.colorSetting, "getColor");
                if (v == null) v = tryInt(hud.colorSetting, "getValue");
                if (v == null) v = tryInt(hud.colorSetting, "get");
                if (v == null) v = tryInt(hud.colorSetting, "getRgb");
                if (v != null) return v;
            }
        } catch (Throwable ignored) {
        }
        return new Color(255, 101, 57, 255).getRGB();
    }

    private static Integer tryInt(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            Object r = m.invoke(obj);
            if (r instanceof Integer i) return i;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int forceAlpha(int argb, int a) {
        return (clamp255(a) << 24) | (argb & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static Fonts.Type resolveTextType() {
        return resolveFontType("BOLD", "SEMI_BOLD", "SEMIBOLD", "MEDIUM", "DEFAULT");
    }

    private static Fonts.Type resolveFontType(String... names) {
        for (String n : names) {
            try {
                return Enum.valueOf(Fonts.Type.class, n);
            } catch (Throwable ignored) {
            }
        }
        return Fonts.Type.DEFAULT;
    }
}