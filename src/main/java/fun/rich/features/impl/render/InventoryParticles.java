package fun.rich.features.impl.render;

import fun.rich.events.container.HandledScreenEvent;
import fun.rich.events.render.DrawEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.client.managers.event.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class InventoryParticles extends Module {
    private final InventoryParticleManager manager = new InventoryParticleManager();
    private final Map<Integer, Long> nextSpawnAt = new HashMap<>();
    private Screen lastScreen;

    public InventoryParticles() {
        super("InventoryParticles", ModuleCategory.RENDER);
    }

    @EventHandler
    public void onHandledScreen(HandledScreenEvent event) {
        if (!(mc.currentScreen instanceof HandledScreen<?> handled)) {
            return;
        }

        Screen current = mc.currentScreen;
        if (current != lastScreen) {
            lastScreen = current;
            nextSpawnAt.clear();
            manager.clear();
        }

        long now = System.nanoTime();
        manager.update(now);

        int left = (mc.getWindow().getScaledWidth() - event.getBackgroundWidth()) / 2;
        int top = (mc.getWindow().getScaledHeight() - event.getBackgroundHeight()) / 2;
        Slot hovered = event.getSlotHover();

        for (Slot slot : handled.getScreenHandler().slots) {
            if (slot == null) {
                continue;
            }

            ItemStack stack = slot.getStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            boolean hover = hovered == slot;
            trySpawn(slot, stack, left, top, hover, now);
        }

        manager.render(event.getDrawContext());
    }

    @EventHandler
    public void onDraw(DrawEvent event) {
        if (mc.currentScreen instanceof HandledScreen<?>) {
            return;
        }

        if (lastScreen != null || !nextSpawnAt.isEmpty()) {
            lastScreen = null;
            nextSpawnAt.clear();
            manager.clear();
        }
    }

    @Override
    public void deactivate() {
        lastScreen = null;
        nextSpawnAt.clear();
        manager.clear();
    }

    private void trySpawn(Slot slot, ItemStack stack, int left, int top, boolean hover, long now) {
        InventoryParticlePreset preset = InventoryParticlePresetRegistry.resolve(stack);

        long nextAt = nextSpawnAt.getOrDefault(slot.id, 0L);
        if (now < nextAt) {
            return;
        }

        float centerX = left + slot.x + 8.0f;
        float centerY = top + slot.y + 8.0f;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int burst = random.nextInt(preset.minBurst(), preset.maxBurst() + 1);

        if (hover) {
            burst += preset.hoverBurstBonus();
        }
        if (stack.hasGlint()) {
            burst++;
        }
        if (stack.getCount() >= 32) {
            burst++;
        }

        for (int i = 0; i < burst; i++) {
            manager.spawn(centerX, centerY, preset, hover, stack);
        }

        long intervalMs = getIntervalMs(preset, stack, hover, random);
        nextSpawnAt.put(slot.id, now + intervalMs * 1_000_000L);
    }

    private long getIntervalMs(InventoryParticlePreset preset, ItemStack stack, boolean hover, ThreadLocalRandom random) {
        float base = hover ? preset.hoverIntervalMs() : preset.baseIntervalMs();

        if (stack.hasGlint()) {
            base *= 0.84f;
        }

        if (stack.getCount() > 1) {
            base -= Math.min(28.0f, stack.getCount() * 0.55f);
        }

        base += random.nextFloat(0.0f, hover ? 10.0f : 22.0f);
        return Math.max(12L, (long) base);
    }
}