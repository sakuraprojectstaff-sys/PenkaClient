package fun.rich.mixins.player.inventory;

import fun.rich.display.screens.clickgui.components.implement.autobuy.manager.AutoBuyManager;
import fun.rich.features.impl.misc.SelfDestruct;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {
    private ButtonWidget takeAllButton;
    private ButtonWidget dropAllButton;
    private ButtonWidget storeAllButton;
    private ButtonWidget autoBuyButton;

    private boolean built;
    private int lastSyncId = Integer.MIN_VALUE;
    private int lastW = -1;
    private int lastH = -1;
    private int lastBgW = -1;
    private int lastBgH = -1;
    private boolean lastAuction;

    private final AutoBuyManager autoBuyManager = AutoBuyManager.getInstance();

    public GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rich$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (SelfDestruct.unhooked) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        String title = this.getTitle() == null ? "" : this.getTitle().getString();
        boolean auction = isAuction(title);
        int sync = this.handler == null ? -1 : this.handler.syncId;

        boolean resizeOrNew = !built
                || sync != lastSyncId
                || this.width != lastW
                || this.height != lastH
                || this.backgroundWidth != lastBgW
                || this.backgroundHeight != lastBgH;

        if (resizeOrNew) {
            lastSyncId = sync;
            lastW = this.width;
            lastH = this.height;
            lastBgW = this.backgroundWidth;
            lastBgH = this.backgroundHeight;
            built = true;
            ensureButtons(mc, auction);
            updatePositions(auction);
            lastAuction = auction;
        } else {
            ensureButtons(mc, auction);
            updatePositions(auction);
            if (auction != lastAuction) {
                setVisibleCompat(autoBuyButton, auction);
                setActiveCompat(autoBuyButton, auction);
                lastAuction = auction;
            }
        }

        if (autoBuyButton != null) {
            boolean state = autoBuyManager.isEnabled();
            autoBuyButton.setMessage(Text.literal("AutoBuy: " + (state ? "§aON" : "§cOFF")));
        }
    }

    private void ensureButtons(MinecraftClient mc, boolean auction) {
        if (dropAllButton == null) {
            dropAllButton = ButtonWidget.builder(Text.literal("Выбросить"), b -> dropAll(mc)).dimensions(0, 0, 80, 20).build();
        }
        if (takeAllButton == null) {
            takeAllButton = ButtonWidget.builder(Text.literal("Взять всё"), b -> takeAll(mc)).dimensions(0, 0, 80, 20).build();
        }
        if (storeAllButton == null) {
            storeAllButton = ButtonWidget.builder(Text.literal("Сложить всё"), b -> storeAll(mc)).dimensions(0, 0, 80, 20).build();
        }

        addIfMissing(dropAllButton);
        addIfMissing(takeAllButton);
        addIfMissing(storeAllButton);

        if (auction && autoBuyButton == null) {
            autoBuyButton = ButtonWidget.builder(
                    Text.literal("AutoBuy: " + (autoBuyManager.isEnabled() ? "§aON" : "§cOFF")),
                    b -> {
                        boolean next = !autoBuyManager.isEnabled();
                        autoBuyManager.setEnabled(next);
                        b.setMessage(Text.literal("AutoBuy: " + (next ? "§aON" : "§cOFF")));
                    }
            ).dimensions(0, 0, 80, 20).build();

            addIfMissing(autoBuyButton);
            setVisibleCompat(autoBuyButton, true);
            setActiveCompat(autoBuyButton, true);
        }

        if (autoBuyButton != null) {
            setVisibleCompat(autoBuyButton, auction);
            setActiveCompat(autoBuyButton, auction);
        }
    }

    private void updatePositions(boolean auction) {
        int left = (this.width - this.backgroundWidth) / 2;
        int top = (this.height - this.backgroundHeight) / 2;
        int right = left + this.backgroundWidth;

        int bx = clamp(right + 4, 2, this.width - 82);
        int by = clamp(top, 2, this.height - 22);

        pos(dropAllButton, bx, by);
        pos(takeAllButton, bx, by + 22);
        pos(storeAllButton, bx, by + 44);

        if (autoBuyButton != null) {
            int ax = clamp(left + this.backgroundWidth / 2 - 40, 2, this.width - 82);
            int ay = clamp(top - 25, 2, this.height - 22);
            pos(autoBuyButton, ax, ay);
            setVisibleCompat(autoBuyButton, auction);
            setActiveCompat(autoBuyButton, auction);
        }
    }

    private void addIfMissing(ButtonWidget w) {
        if (w == null) return;
        if (!this.children().contains(w)) this.addDrawableChild(w);
    }

    @Unique
    private static boolean isAuction(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        return t.contains("аукцион") || t.contains("аукционы") || t.contains("auction");
    }

    @Unique
    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    @Unique
    private static void pos(ButtonWidget w, int x, int y) {
        if (w == null) return;
        try {
            w.setPosition(x, y);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void setVisibleCompat(ButtonWidget w, boolean visible) {
        if (w == null) return;
        try {
            var m = w.getClass().getMethod("setVisible", boolean.class);
            m.invoke(w, visible);
            return;
        } catch (Throwable ignored) {
        }
        try {
            var f = w.getClass().getDeclaredField("visible");
            f.setAccessible(true);
            f.setBoolean(w, visible);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private static void setActiveCompat(ButtonWidget w, boolean active) {
        if (w == null) return;
        try {
            var f = w.getClass().getDeclaredField("active");
            f.setAccessible(true);
            f.setBoolean(w, active);
        } catch (Throwable ignored) {
        }
    }

    private void takeAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null) return;

        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory != player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        0,
                        SlotActionType.QUICK_MOVE,
                        player
                );
            }
        }
    }

    private void dropAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null) return;

        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory != player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        1,
                        SlotActionType.THROW,
                        player
                );
            }
        }
    }

    private void storeAll(MinecraftClient mc) {
        ClientPlayerEntity player = mc.player;
        if (player == null || player.currentScreenHandler == null) return;

        for (Slot slot : player.currentScreenHandler.slots) {
            if (slot.inventory == player.getInventory() && slot.hasStack()) {
                mc.interactionManager.clickSlot(
                        player.currentScreenHandler.syncId,
                        slot.id,
                        0,
                        SlotActionType.QUICK_MOVE,
                        player
                );
            }
        }
    }
}
