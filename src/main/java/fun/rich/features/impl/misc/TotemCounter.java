package fun.rich.features.impl.misc;

import fun.rich.events.render.DrawEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class TotemCounter extends Module {

    private static final float ICON_SIZE = 16.0F;
    private static final float ICON_OFF_X = 10.0F;
    private static final float TEXT_GAP = 4.0F;
    private final ItemStack totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);

    public TotemCounter() {
        super("TotemCounter", ModuleCategory.MISC);
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (!isModuleEnabledCompat()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        DrawContext ctx = extractContext(e);
        if (ctx == null) return;

        int count = countTotems(mc);
        float centerX = mc.getWindow().getScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getScaledHeight() / 2.0F;

        float iconX = centerX + ICON_OFF_X;
        float iconY = centerY - (ICON_SIZE / 2.0F);

        float textX = iconX + ICON_SIZE + TEXT_GAP;
        float textY = iconY + (ICON_SIZE / 2.0F) - (mc.textRenderer.fontHeight / 2.0F);

        ctx.drawItem(totemStack, (int) iconX, (int) iconY);
        ctx.drawText(mc.textRenderer, Text.of(String.valueOf(count)), (int) textX, (int) textY, 0xFFFFFFFF, true);
    }

    private static int countTotems(MinecraftClient mc) {
        int count = 0;
        int size = mc.player.getInventory().size();
        for (int i = 0; i < size; i++) {
            ItemStack st = mc.player.getInventory().getStack(i);
            if (st.getItem() == Items.TOTEM_OF_UNDYING) count += st.getCount();
        }
        return count;
    }

    private static DrawContext extractContext(Object e) {
        try {
            Method m = e.getClass().getDeclaredMethod("getContext");
            m.setAccessible(true);
            Object o = m.invoke(e);
            if (o instanceof DrawContext) return (DrawContext) o;
        } catch (Throwable ignored) {
        }
        try {
            Method m = e.getClass().getDeclaredMethod("getDrawContext");
            m.setAccessible(true);
            Object o = m.invoke(e);
            if (o instanceof DrawContext) return (DrawContext) o;
        } catch (Throwable ignored) {
        }
        try {
            Field f = e.getClass().getDeclaredField("context");
            f.setAccessible(true);
            Object o = f.get(e);
            if (o instanceof DrawContext) return (DrawContext) o;
        } catch (Throwable ignored) {
        }
        try {
            Field f = e.getClass().getDeclaredField("drawContext");
            f.setAccessible(true);
            Object o = f.get(e);
            if (o instanceof DrawContext) return (DrawContext) o;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isModuleEnabledCompat() {
        try {
            Method m = Module.class.getDeclaredMethod("isEnabled");
            m.setAccessible(true);
            Object v = m.invoke(this);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Method m = Module.class.getDeclaredMethod("isToggled");
            m.setAccessible(true);
            Object v = m.invoke(this);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = Module.class.getDeclaredField("enabled");
            f.setAccessible(true);
            Object v = f.get(this);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        try {
            Field f = Module.class.getDeclaredField("toggled");
            f.setAccessible(true);
            Object v = f.get(this);
            return v instanceof Boolean && (Boolean) v;
        } catch (Throwable ignored) {
        }
        return true;
    }
}
