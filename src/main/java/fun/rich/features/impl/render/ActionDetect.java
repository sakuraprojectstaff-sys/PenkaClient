package fun.rich.features.impl.render;

import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.DrawEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.chat.ChatMessage;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayDeque;
import java.util.Deque;

public class ActionDetect extends Module {

    private final MultiSelectSetting detect = new MultiSelectSetting("Детект", "Какие действия ловить")
            .value("ПКМ блок", "ПКМ предмет", "Ломание", "Слот", "Выброс", "Удар")
            .selected("ПКМ блок", "ПКМ предмет", "Ломание", "Выброс", "Удар");

    private final BooleanSetting chat = new BooleanSetting("Чат", "Писать в чат").setValue(true);

    private final BooleanSetting overlay = new BooleanSetting("Оверлей", "Показывать сверху слева").setValue(true);

    private final BooleanSetting marker = new BooleanSetting("Маркер", "Показывать 3D маркер действия").setValue(true);

    private final SliderSettings keepSec = new SliderSettings("Время", "Сколько держать событие (сек)")
            .setValue(2.2f).range(0.3f, 8.0f);

    private final SliderSettings maxLines = new SliderSettings("Строк", "Макс строк в оверлее")
            .setValue(6.0f).range(1.0f, 12.0f);

    private final SliderSettings markerWidth = new SliderSettings("Толщина", "Толщина линий маркера")
            .setValue(2.0f).range(1.0f, 4.0f);

    private final Deque<Line> lines = new ArrayDeque<>();
    private final Deque<Mark> marks = new ArrayDeque<>();

    public ActionDetect() {
        super("ActionDetect", "ActionDetect", ModuleCategory.RENDER);
        setup(detect, chat, overlay, marker, keepSec, maxLines, markerWidth);
    }

    @Override
    public void activate() {
        super.activate();
        lines.clear();
        marks.clear();
    }

    @Override
    public void deactivate() {
        lines.clear();
        marks.clear();
        super.deactivate();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (!isState()) return;
        if (e == null) return;
        if (!e.isSend()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        Packet<?> p = e.getPacket();
        if (p == null) return;

        String n = p.getClass().getSimpleName();

        if (n.equals("PlayerInteractBlockC2SPacket")) {
            if (!detect.isSelected("ПКМ блок")) return;
            BlockPos pos = extractBlockPosFromInteractBlock(p);
            String msg = pos != null
                    ? "§aПКМ §7блок §8• §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    : "§aПКМ §7блок";
            push(msg, 0xFF55FF55, pos != null ? new Box(pos).expand(0.002) : null, 0x6655FF55);
            return;
        }

        if (n.equals("PlayerInteractItemC2SPacket")) {
            if (!detect.isSelected("ПКМ предмет")) return;
            String msg = "§aПКМ §7предмет";
            push(msg, 0xFF55FF55, null, 0);
            return;
        }

        if (n.equals("PlayerActionC2SPacket")) {
            handlePlayerActionPacket(p);
            return;
        }

        if (n.equals("UpdateSelectedSlotC2SPacket")) {
            if (!detect.isSelected("Слот")) return;
            int slot = extractIntNoArgs(p, "getSelectedSlot");
            if (slot < 0) slot = extractIntNoArgs(p, "getSlot");
            String msg = slot >= 0 ? "§eСлот §8• §f" + (slot + 1) : "§eСлот";
            push(msg, 0xFFFFD255, null, 0);
            return;
        }

        if (n.equals("HandSwingC2SPacket")) {
            if (!detect.isSelected("Удар")) return;
            push("§cУдар", 0xFFFF5555, null, 0);
        }
    }

    private void handlePlayerActionPacket(Object p) {
        Object action = invokeNoArgs(p, "getAction");
        String a = action == null ? "" : action.toString();

        if ((a.contains("START_DESTROY_BLOCK") || a.contains("STOP_DESTROY_BLOCK")) && detect.isSelected("Ломание")) {
            BlockPos pos = extractBlockPosNoArgs(p, "getPos");
            String msg = pos != null
                    ? "§cЛомание §8• §f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    : "§cЛомание";
            push(msg, 0xFFFF5555, pos != null ? new Box(pos).expand(0.002) : null, 0x66FF5555);
            return;
        }

        if ((a.contains("DROP_ITEM") || a.contains("DROP_ALL_ITEMS")) && detect.isSelected("Выброс")) {
            boolean all = a.contains("DROP_ALL");
            String msg = all ? "§6Выброс §8• §fCtrl+Q" : "§6Выброс §8• §fQ";
            push(msg, 0xFFFFAA00, null, 0);
        }
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        long now = System.currentTimeMillis();
        long keep = keepMs();

        while (!lines.isEmpty()) {
            Line l = lines.peekFirst();
            if (l == null || now - l.timeMs > keep) lines.pollFirst();
            else break;
        }

        while (!marks.isEmpty()) {
            Mark m = marks.peekFirst();
            if (m == null || now - m.timeMs > keep) marks.pollFirst();
            else break;
        }

        int max = (int) maxLines.getValue();
        if (max < 1) max = 1;
        while (lines.size() > max) lines.pollLast();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!isState()) return;
        if (!marker.isValue()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        if (marks.isEmpty()) return;

        int lw = (int) markerWidth.getValue();
        if (lw < 1) lw = 1;
        if (lw > 6) lw = 6;

        for (Mark m : marks) {
            if (m == null || m.box == null) continue;
            int outline = (m.color & 0x00FFFFFF) | 0xFF000000;
            Render3D.drawBox(m.box, m.color, lw, true, true, true);
            Render3D.drawBox(m.box, outline, lw, true, true, true);
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!isState()) return;
        if (!overlay.isValue()) return;
        if (mc == null || mc.textRenderer == null) return;
        if (lines.isEmpty()) return;

        DrawContext ctx = e.getDrawContext();
        int x = 6;
        int y = 6;
        int dy = mc.textRenderer.fontHeight + 2;

        for (Line l : lines) {
            if (l == null) continue;
            ctx.drawText(mc.textRenderer, Text.literal(stripColors(l.text)), x, y, l.rgb, true);
            y += dy;
        }
    }

    private void push(String text, int rgb, Box markerBox, int markerColor) {
        long now = System.currentTimeMillis();

        lines.addLast(new Line(now, text, rgb));

        if (chat.isValue()) {
            ChatMessage.brandmessage(text);
        }

        if (marker.isValue() && markerBox != null) {
            marks.addLast(new Mark(now, markerBox, markerColor != 0 ? markerColor : withAlpha(ColorAssist.getClientColor(), 100)));
        }
    }

    private long keepMs() {
        float s = keepSec.getValue();
        if (s < 0.1f) s = 0.1f;
        return (long) (s * 1000.0f);
    }

    private BlockPos extractBlockPosFromInteractBlock(Object packet) {
        try {
            Object hr = invokeNoArgs(packet, "getBlockHitResult");
            if (hr == null) hr = invokeNoArgs(packet, "getHitResult");
            if (hr instanceof BlockHitResult hit) return hit.getBlockPos();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private BlockPos extractBlockPosNoArgs(Object packet, String method) {
        try {
            Object r = invokeNoArgs(packet, method);
            if (r instanceof BlockPos p) return p;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int extractIntNoArgs(Object obj, String method) {
        try {
            Object r = invokeNoArgs(obj, method);
            if (r instanceof Integer i) return i;
            if (r instanceof Number n) return n.intValue();
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private Object invokeNoArgs(Object obj, String name) {
        if (obj == null) return null;
        try {
            return obj.getClass().getMethod(name).invoke(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int withAlpha(int rgb, int a) {
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (rgb & 0x00FFFFFF) | (a << 24);
    }

    private String stripColors(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private record Line(long timeMs, String text, int rgb) {
    }

    private record Mark(long timeMs, Box box, int color) {
    }
}