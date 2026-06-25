package fun.rich.features.impl.render;

import fun.rich.events.player.TickEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BaseFinder extends Module {

    static final int MAX_TRACKED = 2048;

    static final Set<Block> VALUABLE = Set.of(
            Blocks.OBSIDIAN,
            Blocks.CRYING_OBSIDIAN,
            Blocks.ANCIENT_DEBRIS,
            Blocks.RESPAWN_ANCHOR,
            Blocks.REINFORCED_DEEPSLATE,
            Blocks.NETHER_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.LODESTONE,
            Blocks.BEACON,
            Blocks.DRAGON_EGG
    );

    final SliderSettings radius = new SliderSettings("Радиус", "Радиус сканирования (блоки)")
            .setValue(80.0f).range(5.0f, 128.0f);

    final SliderSettings delaySec = new SliderSettings("Таймер", "Пауза между сканами (сек)")
            .setValue(4.0f).range(1.0f, 30.0f);

    final SliderSettings speed = new SliderSettings("Скорость", "Сколько блоков проверять за тик")
            .setValue(20000.0f).range(1000.0f, 60000.0f);

    final SliderSettings lineWidth = new SliderSettings("Толщина", "Толщина линий")
            .setValue(2.0f).range(1.0f, 4.0f);

    final SliderSettings alpha = new SliderSettings("Альфа", "Прозрачность заливки")
            .setValue(0.45f).range(0.0f, 1.0f);

    final BooleanSetting fill = new BooleanSetting("Заливка", "Рисовать заливку").setValue(true);
    final BooleanSetting scanAir = new BooleanSetting("Скан воздуха", "Сканировать и воздух (медленнее)").setValue(false);

    final MultiSelectSetting targets = new MultiSelectSetting("Искать", "Что подсвечивать")
            .value("BlockEntity", "Свет", "Редкие", "Кровати", "Костры", "Свечи", "Верстаки")
            .selected("BlockEntity", "Свет", "Редкие", "Кровати", "Костры", "Свечи", "Верстаки");

    long nextScanAtMs;
    volatile List<BlockPos> detected = Collections.emptyList();

    boolean scanning;

    int minX, maxX, minY, maxY, minZ, maxZ;
    int curX, curY, curZ;

    final BlockPos.Mutable scanPos = new BlockPos.Mutable(0, 0, 0);
    ArrayList<BlockPos> scanOut;
    Set<Long> scanSeen;

    static Method worldGetTopY0;
    static Method worldGetTopY3;
    static Object heightmapWorldSurface;
    static boolean topYReflectionReady;

    public BaseFinder() {
        super("BaseFinder", "BaseFinder", ModuleCategory.RENDER);
        setup(radius, delaySec, speed, lineWidth, alpha, fill, scanAir, targets);
    }

    @Override
    public void activate() {
        super.activate();
        detected = Collections.emptyList();
        scanning = false;
        nextScanAtMs = 0L;
        scanOut = null;
        scanSeen = null;
    }

    @Override
    public void deactivate() {
        detected = Collections.emptyList();
        scanning = false;
        nextScanAtMs = 0L;
        scanOut = null;
        scanSeen = null;
        super.deactivate();
    }

    @EventHandler
    public void onTick(TickEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null) {
            scanning = false;
            detected = Collections.emptyList();
            return;
        }

        long now = System.currentTimeMillis();

        if (!scanning) {
            if (now >= nextScanAtMs) startScan(now);
            return;
        }

        stepScan();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent ignored) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null) return;
        if (detected.isEmpty()) return;

        double r = radius.getValue();
        if (r < 1.0) r = 1.0;
        double r2 = r * r;

        int base = ColorAssist.getClientColor();
        int a = clamp255((int) (alpha.getValue() * 255.0f));

        int lw = (int) lineWidth.getValue();
        if (lw < 1) lw = 1;
        if (lw > 6) lw = 6;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        boolean doFill = fill.isValue() && a > 0;
        int fillColor = (base & 0x00FFFFFF) | (a << 24);
        int outlineColor = (base & 0x00FFFFFF) | (0xFF << 24);

        for (BlockPos pos : detected) {
            if (pos == null) continue;

            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;

            double dx = cx - px;
            double dy = cy - py;
            double dz = cz - pz;

            if (dx * dx + dy * dy + dz * dz > r2) continue;

            Box box = computeBox(pos).expand(0.002);
            if (doFill) Render3D.drawBox(box, fillColor, lw, true, true, true);
            Render3D.drawBox(box, outlineColor, lw, true, true, true);
        }
    }

    void startScan(long now) {
        int r = (int) radius.getValue();
        if (r < 1) r = 1;

        BlockPos p = mc.player.getBlockPos();

        minX = p.getX() - r;
        maxX = p.getX() + r;

        minZ = p.getZ() - r;
        maxZ = p.getZ() + r;

        int bottom = worldBottomY();
        int top = worldTopYInclusive();

        minY = clampI(p.getY() - r, bottom, top);
        maxY = clampI(p.getY() + r, bottom, top);

        curX = minX;
        curY = minY;
        curZ = minZ;

        scanOut = new ArrayList<>(Math.min(256, MAX_TRACKED));
        scanSeen = new HashSet<>(Math.min(1024, MAX_TRACKED * 2));

        scanning = true;
        nextScanAtMs = now + (long) (delaySec.getValue() * 1000.0f);
    }

    void stepScan() {
        if (!scanning) return;
        if (mc == null || mc.world == null) {
            scanning = false;
            return;
        }

        int budget = (int) speed.getValue();
        if (budget < 250) budget = 250;

        boolean includeAir = scanAir.isValue();

        boolean wantBe = targets.isSelected("BlockEntity");
        boolean wantLight = targets.isSelected("Свет");
        boolean wantVal = targets.isSelected("Редкие");
        boolean wantBeds = targets.isSelected("Кровати");
        boolean wantCamp = targets.isSelected("Костры");
        boolean wantCandles = targets.isSelected("Свечи");
        boolean wantTables = targets.isSelected("Верстаки");

        while (budget-- > 0) {
            if (scanOut.size() >= MAX_TRACKED) {
                finishScan();
                return;
            }

            if (curX > maxX) {
                finishScan();
                return;
            }

            scanPos.set(curX, curY, curZ);

            BlockState state = mc.world.getBlockState(scanPos);
            if (!includeAir && (state == null || state.isAir())) {
                advanceCursor();
                continue;
            }

            boolean interesting = false;

            if (wantBe && mc.world.getBlockEntity(scanPos) != null) interesting = true;

            if (!interesting && wantLight) {
                try {
                    if (state.getLuminance() >= 10) interesting = true;
                } catch (Throwable ignored) {
                }
            }

            if (!interesting && wantVal) {
                Block b = state.getBlock();
                if (VALUABLE.contains(b)) interesting = true;
            }

            if (!interesting && (wantBeds || wantCamp || wantCandles)) {
                if (wantBeds && state.isIn(BlockTags.BEDS)) interesting = true;
                else if (wantCamp && state.isIn(BlockTags.CAMPFIRES)) interesting = true;
                else if (wantCandles && state.isIn(BlockTags.CANDLES)) interesting = true;
            }

            if (!interesting && wantTables) {
                Block b = state.getBlock();
                if (b == Blocks.CRAFTING_TABLE
                        || b == Blocks.FLETCHING_TABLE
                        || b == Blocks.SMITHING_TABLE
                        || b == Blocks.CARTOGRAPHY_TABLE
                        || b == Blocks.LOOM) {
                    interesting = true;
                }
            }

            if (interesting) {
                long key = posKey(scanPos.getX(), scanPos.getY(), scanPos.getZ());
                if (scanSeen.add(key)) scanOut.add(new BlockPos(curX, curY, curZ));
            }

            advanceCursor();
        }
    }

    void finishScan() {
        scanning = false;
        if (scanOut == null || scanOut.isEmpty()) detected = Collections.emptyList();
        else detected = List.copyOf(scanOut);
        scanOut = null;
        scanSeen = null;
    }

    void advanceCursor() {
        curZ++;
        if (curZ > maxZ) {
            curZ = minZ;
            curY++;
            if (curY > maxY) {
                curY = minY;
                curX++;
            }
        }
    }

    Box computeBox(BlockPos pos) {
        try {
            BlockState state = mc.world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(mc.world, pos);
            if (shape == null || shape.isEmpty()) return new Box(pos);
            return shape.getBoundingBox().offset(pos);
        } catch (Throwable ignored) {
            return new Box(pos);
        }
    }

    int worldBottomY() {
        try {
            return mc.world.getBottomY();
        } catch (Throwable ignored) {
            return -64;
        }
    }

    int worldTopYInclusive() {
        try {
            return mc.world.getTopYInclusive();
        } catch (Throwable ignored) {
        }

        ensureTopYReflection();

        try {
            if (worldGetTopY0 != null) {
                Object v = worldGetTopY0.invoke(mc.world);
                if (v instanceof Integer i) return i - 1;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (worldGetTopY3 != null && heightmapWorldSurface != null && mc.player != null) {
                BlockPos p = mc.player.getBlockPos();
                Object v = worldGetTopY3.invoke(mc.world, heightmapWorldSurface, p.getX(), p.getZ());
                if (v instanceof Integer i) return i;
            }
        } catch (Throwable ignored) {
        }

        return 319;
    }

    static void ensureTopYReflection() {
        if (topYReflectionReady) return;
        topYReflectionReady = true;

        try {
            worldGetTopY0 = null;
            worldGetTopY3 = null;
            heightmapWorldSurface = null;

            Class<?> worldCls = Class.forName("net.minecraft.world.World");

            try {
                worldGetTopY0 = worldCls.getMethod("getTopY");
            } catch (Throwable ignored) {
            }

            try {
                Class<?> ht = Class.forName("net.minecraft.world.Heightmap$Type");
                Method m = worldCls.getMethod("getTopY", ht, int.class, int.class);
                worldGetTopY3 = m;

                Object[] enums = ht.getEnumConstants();
                if (enums != null) {
                    for (Object o : enums) {
                        if (o != null && "WORLD_SURFACE".equals(o.toString())) {
                            heightmapWorldSurface = o;
                            break;
                        }
                    }
                    if (heightmapWorldSurface == null && enums.length > 0) heightmapWorldSurface = enums[0];
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    static int clampI(int v, int mn, int mx) {
        if (v < mn) return mn;
        return Math.min(v, mx);
    }

    static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    static long posKey(int x, int y, int z) {
        long lx = (x & 0x3FFFFFFL);
        long lz = (z & 0x3FFFFFFL);
        long ly = (y & 0xFFFL);
        return (lx << 38) | (lz << 12) | ly;
    }
}