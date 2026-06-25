package fun.rich.features.impl.combat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.keyboard.KeyEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.features.impl.movement.AutoSprint;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.interactions.inv.InventoryTask;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AutoSwap extends Module {

    final SelectSetting modeSetting = new SelectSetting("Режим", "Способ обхода")
            .value("Default", "Legit", "Wheel")
            .selected("Default");

    final BindSetting bind = new BindSetting("Кнопка использования предмета", "Использует элемент при нажатии");

    final SelectSetting firstItem = new SelectSetting("Основной предмет", "Выберите первый предмет для обмена.")
            .value("Totem of Undying", "Player Head", "Shield");

    final SelectSetting secondItem = new SelectSetting("Вторичный предмет", "Выберите второй предмет для обмена.")
            .value("Totem of Undying", "Player Head", "Shield");

    enum SwapPhase { READY, SLOWING_DOWN, WAITING_STOP, SWAP, SPEEDING_UP, FINISHED }

    SwapPhase swapPhase = SwapPhase.READY;
    Slot targetSlot = null;
    long actionStartTime = 0L;
    boolean playerFullyStopped = false;

    boolean wasForwardPressed, wasBackPressed, wasLeftPressed, wasRightPressed, wasJumpPressed;
    boolean keysOverridden = false;

    long wheelCooldownUntilMs = 0L;

    public AutoSwap() {
        super("AutoSwap", "Auto Swap", ModuleCategory.COMBAT);
        setup(modeSetting, firstItem, secondItem, bind);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null) return;
        if (!e.isKeyDown(bind.getKey())) return;

        if ("Wheel".equals(modeSetting.getSelected())) {
            long now = System.currentTimeMillis();
            if (now < wheelCooldownUntilMs) return;

            if (mc.currentScreen instanceof WheelScreen) {
                ((WheelScreen) mc.currentScreen).requestClose();
            } else if (mc.currentScreen == null) {
                mc.setScreen(new WheelScreen(this));
            }
            return;
        }

        if (swapPhase != SwapPhase.READY) return;

        if ("Default".equals(modeSetting.getSelected())) {
            executeDefaultSwap();
        } else {
            Slot hotbarSlot = findValidSlot(s -> s.id >= 36 && s.id <= 44);
            if (hotbarSlot != null) {
                startLegitSwap(hotbarSlot);
            } else {
                Slot inventorySlot = findValidSlot(s -> s.id >= 0 && s.id <= 35);
                if (inventorySlot != null) {
                    startLegitSwap(inventorySlot);
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if ("Legit".equals(modeSetting.getSelected()) && swapPhase != SwapPhase.READY) {
            processLegitSwap();
        }
    }

    private void startLegitSwap(Slot slotToSwap) {
        targetSlot = slotToSwap;
        if (targetSlot == null) return;

        wasForwardPressed = isPhysicalPressed(mc.options.forwardKey);
        wasBackPressed = isPhysicalPressed(mc.options.backKey);
        wasLeftPressed = isPhysicalPressed(mc.options.leftKey);
        wasRightPressed = isPhysicalPressed(mc.options.rightKey);
        wasJumpPressed = isPhysicalPressed(mc.options.jumpKey);

        swapPhase = SwapPhase.SLOWING_DOWN;
        actionStartTime = System.currentTimeMillis();
        playerFullyStopped = false;
        keysOverridden = false;
    }

    private void processLegitSwap() {
        if (mc.player == null || mc.currentScreen != null) {
            resetState();
            return;
        }

        long elapsed = System.currentTimeMillis() - actionStartTime;

        switch (swapPhase) {
            case SLOWING_DOWN -> {
                if (mc.player.input != null) {
                    mc.player.input.movementForward = 0;
                    mc.player.input.movementSideways = 0;
                }
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                    AutoSprint.tickStop = 5;
                }
                if (!keysOverridden) {
                    mc.options.forwardKey.setPressed(false);
                    mc.options.backKey.setPressed(false);
                    mc.options.leftKey.setPressed(false);
                    mc.options.rightKey.setPressed(false);
                    mc.options.jumpKey.setPressed(false);
                    keysOverridden = true;
                }
                if (elapsed > 1) {
                    swapPhase = SwapPhase.WAITING_STOP;
                }
            }
            case WAITING_STOP -> {
                if (mc.player.input != null) {
                    mc.player.input.movementForward = 0;
                    mc.player.input.movementSideways = 0;
                }
                double vx = Math.abs(mc.player.getVelocity().x);
                double vz = Math.abs(mc.player.getVelocity().z);
                if ((vx < 0.001 && vz < 0.001) || elapsed > 150) {
                    playerFullyStopped = true;
                    swapPhase = SwapPhase.SWAP;
                }
            }
            case SWAP -> {
                if (playerFullyStopped) {
                    if (targetSlot != null) {
                        InventoryTask.moveItem(targetSlot, 45, false, false);
                    }
                    swapPhase = SwapPhase.SPEEDING_UP;
                    actionStartTime = System.currentTimeMillis();
                    if (keysOverridden) {
                        restoreKeyStates();
                    }
                }
            }
            case SPEEDING_UP -> {
                long speedupElapsed = System.currentTimeMillis() - actionStartTime;
                float progress = Math.min(1.0f, speedupElapsed / 20.0f);
                if (mc.player.input != null) {
                    boolean forward = isPhysicalPressed(mc.options.forwardKey);
                    float targetForward = forward ? 1.0f : 0.0f;
                    mc.player.input.movementForward = lerp(mc.player.input.movementForward, targetForward * progress, 0.4f);
                    if (progress > 0.4f && forward && !mc.player.isSprinting()) {
                        mc.player.setSprinting(true);
                    }
                }
                if (speedupElapsed > 25) {
                    swapPhase = SwapPhase.FINISHED;
                }
            }
            case FINISHED -> resetState();
        }
    }

    private void executeDefaultSwap() {
        if (mc.player == null) return;
        Slot validSlot = findValidSlot(s -> true);
        if (validSlot != null) {
            InventoryTask.swapHand(validSlot, Hand.OFF_HAND, true, true);
        }
    }

    private void executeWheelSwap(Predicate<ItemStack> predicate) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return;
        Slot best = mc.player.currentScreenHandler.slots.stream()
                .filter(s -> s != null && s.id != 45 && s.hasStack())
                .filter(s -> predicate.test(s.getStack()))
                .min(Comparator.comparingInt(this::bestFactorForSwap))
                .orElse(null);
        if (best != null) {
            InventoryTask.swapHand(best, Hand.OFF_HAND, true, true);
        }
    }

    private void restoreKeyStates() {
        boolean currentForward = isPhysicalPressed(mc.options.forwardKey);
        boolean currentBack = isPhysicalPressed(mc.options.backKey);
        boolean currentLeft = isPhysicalPressed(mc.options.leftKey);
        boolean currentRight = isPhysicalPressed(mc.options.rightKey);
        boolean currentJump = isPhysicalPressed(mc.options.jumpKey);

        mc.options.forwardKey.setPressed(wasForwardPressed && currentForward);
        mc.options.backKey.setPressed(wasBackPressed && currentBack);
        mc.options.leftKey.setPressed(wasLeftPressed && currentLeft);
        mc.options.rightKey.setPressed(wasRightPressed && currentRight);
        mc.options.jumpKey.setPressed(wasJumpPressed && currentJump);
        keysOverridden = false;
    }

    private float lerp(float start, float end, float delta) {
        return start + (end - start) * delta;
    }

    private Slot findValidSlot(Predicate<Slot> slotPredicate) {
        if (mc.player == null || mc.player.currentScreenHandler == null) return null;

        Predicate<Slot> combinedSlotPredicate = s -> s != null && s.id != 45 && slotPredicate.test(s);

        Item firstType = getItemByType(firstItem.getSelected());
        Item secondType = getItemByType(secondItem.getSelected());
        Item offHandItem = mc.player.getOffHandStack().getItem();
        String offHandName = mc.player.getOffHandStack().getName().getString();

        Comparator<Slot> byFactor = Comparator.comparingInt(this::bestFactorForSwap);

        if (offHandItem == firstType) {
            return mc.player.currentScreenHandler.slots.stream()
                    .filter(combinedSlotPredicate)
                    .filter(s -> s.hasStack() && s.getStack().getItem() == secondType)
                    .filter(s -> !s.getStack().getName().getString().equals(offHandName))
                    .min(byFactor)
                    .orElse(null);
        }

        if (offHandItem == secondType) {
            return mc.player.currentScreenHandler.slots.stream()
                    .filter(combinedSlotPredicate)
                    .filter(s -> s.hasStack() && s.getStack().getItem() == firstType)
                    .filter(s -> !s.getStack().getName().getString().equals(offHandName))
                    .min(byFactor)
                    .orElse(null);
        }

        Slot first = mc.player.currentScreenHandler.slots.stream()
                .filter(combinedSlotPredicate)
                .filter(s -> s.hasStack() && s.getStack().getItem() == firstType)
                .filter(s -> !s.getStack().getName().getString().equals(offHandName))
                .min(byFactor)
                .orElse(null);

        if (first != null) return first;

        return mc.player.currentScreenHandler.slots.stream()
                .filter(combinedSlotPredicate)
                .filter(s -> s.hasStack() && s.getStack().getItem() == secondType)
                .filter(s -> !s.getStack().getName().getString().equals(offHandName))
                .min(byFactor)
                .orElse(null);
    }

    private int bestFactorForSwap(Slot slot) {
        if (slot == null || !slot.hasStack()) return Integer.MAX_VALUE;
        int f = 0;

        if (slot.id >= 36 && slot.id <= 44) f += 0;
        else f += 10;

        ItemStack st = slot.getStack();
        if (st.hasEnchantments()) f += 2;
        if (hasCustomNameCompat(st)) f += 1;

        int cnt = st.getCount();
        f += Math.max(0, 64 - cnt);

        if (slot.id == 45) f += 99;
        return f;
    }

    private void resetState() {
        if (keysOverridden) {
            restoreKeyStates();
        }
        swapPhase = SwapPhase.READY;
        targetSlot = null;
        playerFullyStopped = false;
    }

    private Item getItemByType(String itemType) {
        return switch (itemType) {
            case "Totem of Undying" -> Items.TOTEM_OF_UNDYING;
            case "Player Head" -> Items.PLAYER_HEAD;
            case "Shield" -> Items.SHIELD;
            default -> Items.AIR;
        };
    }

    private boolean isPhysicalPressed(KeyBinding kb) {
        if (mc == null || mc.getWindow() == null) return false;
        int code = keyCodeCompat(kb);
        if (code < 0) return false;
        return InputUtil.isKeyPressed(mc.getWindow().getHandle(), code);
    }

    private int keyCodeCompat(KeyBinding kb) {
        InputUtil.Key k = getKeyCompat(kb, true);
        if (k == null) k = getKeyCompat(kb, false);
        return k == null ? -1 : k.getCode();
    }

    private InputUtil.Key getKeyCompat(KeyBinding kb, boolean bound) {
        Object v = null;
        try {
            Method m = kb.getClass().getMethod(bound ? "getBoundKey" : "getDefaultKey");
            v = m.invoke(kb);
        } catch (Throwable ignored) {
        }
        if (v == null) {
            try {
                Field f = kb.getClass().getDeclaredField(bound ? "boundKey" : "defaultKey");
                f.setAccessible(true);
                v = f.get(kb);
            } catch (Throwable ignored) {
            }
        }
        return v instanceof InputUtil.Key ? (InputUtil.Key) v : null;
    }

    private boolean hasCustomNameCompat(ItemStack st) {
        try {
            Method m = st.getClass().getMethod("hasCustomName");
            Object r = m.invoke(st);
            if (r instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = st.getClass().getMethod("getCustomName");
            Object r = m.invoke(st);
            return r != null;
        } catch (Throwable ignored) {
        }
        return false;
    }

    static final class WheelScreen extends Screen {

        static final float SEL_R = 0.25f;
        static final float SEL_G = 0.65f;
        static final float SEL_B = 1.0f;

        final AutoSwap p;

        List<Entry> entries = List.of();
        float anim = 0.0f;

        boolean closing = false;
        long lastNs = System.nanoTime();

        float[] hoverMix = new float[0];
        int lastCount = -1;

        int selectedIndex = 0;
        long wheelSelectUntilNs = 0L;

        WheelScreen(AutoSwap p) {
            super(Text.empty());
            this.p = p;
        }

        void requestClose() {
            closing = true;
        }

        @Override
        public boolean shouldPause() {
            return false;
        }

        @Override
        protected void init() {
            entries = buildEntries();
            ensureHoverMix();
            anim = 0.0f;
            closing = false;
            lastNs = System.nanoTime();
            selectedIndex = 0;
            wheelSelectUntilNs = 0L;
        }

        @Override
        public void tick() {
            if (MinecraftClient.getInstance().player == null || MinecraftClient.getInstance().world == null) {
                MinecraftClient.getInstance().setScreen(null);
                return;
            }

            long now = System.nanoTime();
            float dt = (now - lastNs) / 1_000_000_000.0f;
            lastNs = now;
            dt = clamp(dt, 0.0f, 0.05f);

            float target = closing ? 0.0f : 1.0f;
            float speed = closing ? 24.0f : 18.0f;
            anim = approachExp(anim, target, speed, dt);

            if (closing && anim <= 0.02f) {
                p.wheelCooldownUntilMs = System.currentTimeMillis() + 280L;
                MinecraftClient.getInstance().setScreen(null);
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) {
                requestClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 1) {
                requestClose();
                return true;
            }
            if (button != 0) return false;

            if (entries.isEmpty()) {
                requestClose();
                return true;
            }

            int n = entries.size();
            if (selectedIndex < 0) selectedIndex = 0;
            if (selectedIndex >= n) selectedIndex = n - 1;

            long now = System.nanoTime();

            int count = Math.max(3, entries.size());
            float cx = width / 2.0f;
            float cy = height / 2.0f;

            float outerR = 92.0f + Math.max(0, count - 8) * 6.0f;
            float innerR = Math.max(26.0f, outerR - 38.0f);

            float ringScale = easeOutBack(anim) * 0.985f + 0.015f;
            outerR *= ringScale;
            innerR *= ringScale;

            int hover = hoverIndex((float) mouseX, (float) mouseY, cx, cy, innerR, outerR, count);

            int idx;
            if (now < wheelSelectUntilNs) {
                idx = selectedIndex;
            } else {
                idx = (hover >= 0 && hover < entries.size()) ? hover : selectedIndex;
            }

            if (idx >= 0 && idx < entries.size()) {
                Entry e = entries.get(idx);
                p.executeWheelSwap(e.predicate);
            }

            requestClose();
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (entries.isEmpty()) return false;

            int dir = verticalAmount > 0.0 ? -1 : (verticalAmount < 0.0 ? 1 : 0);
            if (dir == 0) return true;

            int n = entries.size();
            selectedIndex = (selectedIndex + dir) % n;
            if (selectedIndex < 0) selectedIndex += n;

            wheelSelectUntilNs = System.nanoTime() + 450_000_000L;
            return true;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            float aIn = easeOutCubic(anim);
            float ringScale = easeOutBack(anim);

            float dim = 0.55f * aIn;
            int dimA = (int) (dim * 255.0f);

            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.defaultBlendFunc();
            context.fill(0, 0, width, height, (dimA << 24));

            int count = Math.max(3, entries.size());
            float cx = width / 2.0f;
            float cy = height / 2.0f;

            float baseOuter = 92.0f + Math.max(0, count - 8) * 6.0f;
            float baseInner = Math.max(26.0f, baseOuter - 38.0f);

            float outerR = baseOuter * (0.86f + 0.14f * ringScale);
            float innerR = baseInner * (0.86f + 0.14f * ringScale);

            int hover = hoverIndex(mouseX, mouseY, cx, cy, innerR, outerR, count);

            int n = entries.size();
            if (n > 0) {
                if (selectedIndex < 0) selectedIndex = 0;
                if (selectedIndex >= n) selectedIndex = n - 1;
            } else {
                selectedIndex = 0;
            }

            long now = System.nanoTime();

            int active = selectedIndex;
            if (now >= wheelSelectUntilNs && hover >= 0 && hover < entries.size()) {
                active = hover;
            }

            int labelIdx = (hover >= 0 && hover < entries.size()) ? hover : selectedIndex;
            if (labelIdx < 0 || labelIdx >= entries.size()) labelIdx = -1;

            float dt = clamp(delta, 0.0f, 1.0f);
            for (int i = 0; i < hoverMix.length; i++) {
                float t = (!entries.isEmpty() && i == active) ? 1.0f : 0.0f;
                hoverMix[i] = approachExp(hoverMix[i], t, 18.0f, dt);
            }

            float glowA = 0.45f * aIn;
            float shadowA = 0.55f * aIn;

            drawRing(context, cx, cy, innerR - 4.0f, outerR + 6.5f, count, 0.10f, 0.10f, 0.12f, 0.75f * shadowA, hoverMix, active, true, 0.28f);
            drawRing(context, cx, cy, innerR - 2.0f, outerR + 2.5f, count, 0.22f, 0.24f, 0.30f, 0.65f * glowA, hoverMix, active, false, 0.48f);
            drawRing(context, cx, cy, innerR, outerR, count, 0.70f, 0.72f, 0.78f, 0.42f * aIn, hoverMix, active, false, 0.68f);

            float ringMid = (innerR + outerR) * 0.5f;

            for (int i = 0; i < count; i++) {
                Entry entry = i < entries.size() ? entries.get(i) : null;
                if (entry == null) continue;

                float start = (float) (-Math.PI / 2.0 + 2.0 * Math.PI * (i / (double) count));
                float end = (float) (-Math.PI / 2.0 + 2.0 * Math.PI * ((i + 1.0) / (double) count));
                float mid = (start + end) * 0.5f;

                float pop = 1.0f + 0.08f * hoverMix[i];
                float ix = cx + (float) Math.cos(mid) * ringMid;
                float iy = cy + (float) Math.sin(mid) * ringMid;

                int px = (int) (ix - 8.0f * pop);
                int py = (int) (iy - 8.0f * pop);

                context.getMatrices().push();
                context.getMatrices().translate(ix, iy, 0.0f);
                context.getMatrices().scale(pop, pop, 1.0f);
                context.getMatrices().translate(-ix, -iy, 0.0f);

                context.drawItem(entry.displayStack, px, py);
                if (entry.totalCount > 1) {
                    context.drawTextWithShadow(mc.textRenderer, String.valueOf(entry.totalCount), (int) (ix + 5.0f), (int) (iy + 3.0f), 0xFFFFFF);
                }

                context.getMatrices().pop();
            }

            if (labelIdx >= 0) {
                Entry entry = entries.get(labelIdx);
                Text name = entry.displayStack.getName();
                int tw = mc.textRenderer.getWidth(name);

                int pad = 5;
                int bgA = (int) (190.0f * aIn);
                int bg = (bgA << 24) | 0x101014;

                int tx = Math.round(cx - tw * 0.5f);

                int ty = Math.round(cy + outerR + 16.0f);
                int minTy = 6;
                int maxTy = height - (9 + pad + 6);
                if (ty < minTy) ty = minTy;
                if (ty > maxTy) ty = maxTy;

                int x0 = tx - pad;
                int y0 = ty - pad;
                int x1 = tx + tw + pad;
                int y1 = ty + 9 + pad;

                context.fill(x0, y0, x1, y1, bg);
                context.drawTextWithShadow(mc.textRenderer, name, tx, ty, 0xFFFFFF);
            }

            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }

        private void ensureHoverMix() {
            int count = Math.max(3, entries.size());
            if (count != lastCount) {
                hoverMix = new float[count];
                lastCount = count;
            }
        }

        private static float approachExp(float v, float target, float speed, float dt) {
            float k = 1.0f - (float) Math.exp(-speed * dt);
            return v + (target - v) * k;
        }

        private static float easeOutCubic(float t) {
            float x = clamp(t, 0.0f, 1.0f);
            float u = 1.0f - x;
            return 1.0f - u * u * u;
        }

        private static float easeOutBack(float t) {
            float x = clamp(t, 0.0f, 1.0f);
            float c1 = 1.70158f;
            float c3 = c1 + 1.0f;
            float u = x - 1.0f;
            return 1.0f + c3 * u * u * u + c1 * u * u;
        }

        private static float clamp(float v, float a, float b) {
            return v < a ? a : (v > b ? b : v);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }

        private static int hoverIndex(float mouseX, float mouseY, float cx, float cy, float innerR, float outerR, int count) {
            float dx = mouseX - cx;
            float dy = mouseY - cy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < innerR || dist > outerR) return -1;

            double ang = Math.atan2(dy, dx);
            ang = ang + Math.PI / 2.0;
            if (ang < 0.0) ang += Math.PI * 2.0;
            if (ang >= Math.PI * 2.0) ang -= Math.PI * 2.0;

            int idx = (int) Math.floor(ang / (Math.PI * 2.0) * count);
            if (idx < 0 || idx >= count) return -1;
            return idx;
        }

        private static void drawRing(DrawContext context, float cx, float cy, float innerR, float outerR, int count,
                                     float rr, float gg, float bb, float aa, float[] hoverMix, int hover, boolean vignette, float selStrength) {

            float aBase = clamp(aa, 0.0f, 1.0f);
            float rBase = clamp(rr, 0.0f, 1.0f);
            float gBase = clamp(gg, 0.0f, 1.0f);
            float bBase = clamp(bb, 0.0f, 1.0f);

            float ringGlow = 0.55f;

            RenderSystem.enableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
            BufferBuilder buffer = RenderSystem.renderThreadTesselator().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            for (int i = 0; i < count; i++) {
                float hm = (hoverMix != null && i < hoverMix.length) ? hoverMix[i] : (i == hover ? 1.0f : 0.0f);
                float hi = 0.85f * hm;

                float blend = clamp(selStrength * hi, 0.0f, 1.0f);
                float r = lerp(rBase, SEL_R, blend);
                float g = lerp(gBase, SEL_G, blend);
                float b = lerp(bBase, SEL_B, blend);

                float a = aBase + ringGlow * hi;

                float start = (float) (-Math.PI / 2.0 + 2.0 * Math.PI * (i / (double) count));
                float end = (float) (-Math.PI / 2.0 + 2.0 * Math.PI * ((i + 1.0) / (double) count));

                float mid = (start + end) * 0.5f;
                float shine = (float) (0.5 + 0.5 * Math.sin(mid * 1.35f));
                float a2 = a * (vignette ? (0.75f + 0.25f * shine) : (0.88f + 0.12f * shine));

                int ir = (int) (clamp(r, 0.0f, 1.0f) * 255.0f);
                int ig = (int) (clamp(g, 0.0f, 1.0f) * 255.0f);
                int ib = (int) (clamp(b, 0.0f, 1.0f) * 255.0f);
                int ia = (int) (clamp(a2, 0.0f, 1.0f) * 255.0f);

                int steps = Math.max(12, (int) (62.0f * (Math.abs(end - start) / ((float) Math.PI * 2.0f))));
                float step = (end - start) / steps;

                for (int s = 0; s < steps; s++) {
                    float a0 = start + step * s;
                    float a1 = start + step * (s + 1);

                    float x0o = cx + (float) Math.cos(a0) * outerR;
                    float y0o = cy + (float) Math.sin(a0) * outerR;
                    float x1o = cx + (float) Math.cos(a1) * outerR;
                    float y1o = cy + (float) Math.sin(a1) * outerR;

                    float x0i = cx + (float) Math.cos(a0) * innerR;
                    float y0i = cy + (float) Math.sin(a0) * innerR;
                    float x1i = cx + (float) Math.cos(a1) * innerR;
                    float y1i = cy + (float) Math.sin(a1) * innerR;

                    buffer.vertex(matrix, x0i, y0i, 0.0f).color(ir, ig, ib, ia);
                    buffer.vertex(matrix, x0o, y0o, 0.0f).color(ir, ig, ib, ia);
                    buffer.vertex(matrix, x1o, y1o, 0.0f).color(ir, ig, ib, ia);

                    buffer.vertex(matrix, x0i, y0i, 0.0f).color(ir, ig, ib, ia);
                    buffer.vertex(matrix, x1o, y1o, 0.0f).color(ir, ig, ib, ia);
                    buffer.vertex(matrix, x1i, y1i, 0.0f).color(ir, ig, ib, ia);
                }
            }

            BufferRenderer.drawWithGlobalProgram((BuiltBuffer) buffer.end());

            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        }

        private List<Entry> buildEntries() {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.currentScreenHandler == null) return List.of();

            List<Item> allowed = List.of(Items.TOTEM_OF_UNDYING, Items.PLAYER_HEAD, Items.SHIELD);
            Map<String, Accum> accum = new LinkedHashMap<>();

            for (Slot slot : mc.player.currentScreenHandler.slots) {
                if (slot == null || !slot.hasStack()) continue;
                ItemStack st = slot.getStack();
                if (st == null || st.isEmpty() || st.getItem() == Items.AIR) continue;
                if (!allowed.contains(st.getItem())) continue;

                boolean ench = st.hasEnchantments();
                boolean custom = hasCustomNameCompatStatic(st);
                String name = st.getName().getString();

                String key = st.getItem().toString() + "|" + (custom ? name : "") + "|" + (ench ? "1" : "0");

                Accum a = accum.get(key);
                if (a == null) {
                    ItemStack display = st.copy();
                    display.setCount(1);

                    Item it = st.getItem();
                    Predicate<ItemStack> predicate = s -> {
                        if (s == null || s.isEmpty()) return false;
                        if (s.getItem() != it) return false;
                        if (ench != s.hasEnchantments()) return false;
                        boolean c2 = hasCustomNameCompatStatic(s);
                        if (custom != c2) return false;
                        if (custom) return s.getName().getString().equals(name);
                        return true;
                    };

                    a = new Accum(display, 0, predicate);
                    accum.put(key, a);
                }
                a.total += st.getCount();
            }

            ArrayList<Entry> out = new ArrayList<>();
            for (Accum a : accum.values()) out.add(new Entry(a.display, a.total, a.predicate));
            out.sort(Comparator.comparingInt(e -> e.displayStack.getItem() == Items.TOTEM_OF_UNDYING ? 0 : 1));
            return out;
        }

        private static boolean hasCustomNameCompatStatic(ItemStack st) {
            try {
                Method m = st.getClass().getMethod("hasCustomName");
                Object r = m.invoke(st);
                if (r instanceof Boolean b) return b;
            } catch (Throwable ignored) {
            }
            try {
                Method m = st.getClass().getMethod("getCustomName");
                Object r = m.invoke(st);
                return r != null;
            } catch (Throwable ignored) {
            }
            return false;
        }

        static final class Accum {
            final ItemStack display;
            int total;
            final Predicate<ItemStack> predicate;

            Accum(ItemStack display, int total, Predicate<ItemStack> predicate) {
                this.display = display;
                this.total = total;
                this.predicate = predicate;
            }
        }

        static final class Entry {
            final ItemStack displayStack;
            final int totalCount;
            final Predicate<ItemStack> predicate;

            Entry(ItemStack displayStack, int totalCount, Predicate<ItemStack> predicate) {
                this.displayStack = displayStack;
                this.totalCount = totalCount;
                this.predicate = predicate;
            }
        }
    }
}