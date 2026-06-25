package fun.rich.features.impl.render;

import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.geometry.Render3D;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ContainerESP extends Module {

    private final BooleanSetting all = new BooleanSetting("Показать все", "Показывать все типы").setValue(true);

    private final MultiSelectSetting blocks = new MultiSelectSetting("Отображать", "Какие контейнеры рисовать")
            .value("Сундук", "Запертый сундук", "Бочка", "Печь", "Эндер сундук", "Шалкер", "Спавнер")
            .selected("Сундук", "Шалкер", "Спавнер")
            .visible(() -> !all.isValue());

    private final SliderSettings range = new SliderSettings("Дистанция", "Дальность отображения (блоки)")
            .setValue(128.0f).range(16.0f, 256.0f);

    private final SliderSettings lineWidth = new SliderSettings("Толщина", "Толщина линий")
            .setValue(2.0f).range(1.0f, 4.0f);

    private final SliderSettings alpha = new SliderSettings("Альфа", "Прозрачность бокса")
            .setValue(0.65f).range(0.15f, 1.0f);

    public ContainerESP() {
        super("ContainerESP", "ContainerESP", ModuleCategory.RENDER);
        setup(all, blocks, range, lineWidth, alpha);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (!isState()) return;
        if (mc == null || mc.player == null || mc.world == null) return;

        double r = range.getValue();
        if (r < 1.0) r = 1.0;
        double r2 = r * r;

        int lw = (int) lineWidth.getValue();
        if (lw < 1) lw = 1;
        if (lw > 6) lw = 6;

        int a = (int) (alpha.getValue() * 255.0f);
        if (a < 0) a = 0;
        if (a > 255) a = 255;

        int view = 8;
        try {
            if (mc.options != null && mc.options.getViewDistance() != null) {
                view = mc.options.getViewDistance().getValue();
            }
        } catch (Throwable ignored) {
        }

        int needChunks = (int) Math.ceil(r / 16.0);
        int chunkRadius = Math.min(view + 1, needChunks + 1);

        ChunkPos pc = new ChunkPos(mc.player.getBlockPos());

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        for (int cx = pc.x - chunkRadius; cx <= pc.x + chunkRadius; cx++) {
            for (int cz = pc.z - chunkRadius; cz <= pc.z + chunkRadius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : getBlockEntitiesSafe(chunk, mc.world)) {
                    if (be == null) continue;
                    if (!shouldRender(be)) continue;

                    BlockPos pos = be.getPos();
                    double bx = pos.getX() + 0.5;
                    double by = pos.getY() + 0.5;
                    double bz = pos.getZ() + 0.5;

                    double dx = bx - px;
                    double dy = by - py;
                    double dz = bz - pz;

                    if (dx * dx + dy * dy + dz * dz > r2) continue;

                    Box box = computeBox(mc.world, pos);
                    int base = colorOf(be);
                    int fill = (base & 0x00FFFFFF) | (a << 24);
                    int outline = (base & 0x00FFFFFF) | (0xFF << 24);

                    Render3D.drawBox(box, fill, lw, true, true, true);
                    Render3D.drawBox(box, outline, lw, true, true, true);
                }
            }
        }
    }

    private Iterable<BlockEntity> getBlockEntitiesSafe(WorldChunk chunk, ClientWorld world) {
        try {
            return chunk.getBlockEntities().values();
        } catch (Throwable ignored) {
        }

        try {
            List<BlockEntity> list = new ArrayList<>();
            for (BlockPos p : chunk.getBlockEntityPositions()) {
                BlockEntity be = world.getBlockEntity(p);
                if (be != null) list.add(be);
            }
            return list;
        } catch (Throwable ignored) {
        }

        return List.of();
    }

    private Box computeBox(ClientWorld world, BlockPos pos) {
        try {
            BlockState state = world.getBlockState(pos);
            VoxelShape shape = state.getOutlineShape(world, pos);
            if (shape == null || shape.isEmpty()) return new Box(pos).expand(0.002);
            return shape.getBoundingBox().offset(pos).expand(0.002);
        } catch (Throwable ignored) {
            return new Box(pos).expand(0.002);
        }
    }

    private boolean shouldRender(BlockEntity be) {
        if (all.isValue()) return isSupported(be);

        if (be instanceof ChestBlockEntity) return blocks.isSelected("Сундук");
        if (be instanceof TrappedChestBlockEntity) return blocks.isSelected("Запертый сундук");
        if (be instanceof BarrelBlockEntity) return blocks.isSelected("Бочка");
        if (be instanceof AbstractFurnaceBlockEntity) return blocks.isSelected("Печь");
        if (be instanceof EnderChestBlockEntity) return blocks.isSelected("Эндер сундук");
        if (be instanceof ShulkerBoxBlockEntity) return blocks.isSelected("Шалкер");
        if (be instanceof MobSpawnerBlockEntity) return blocks.isSelected("Спавнер");

        return false;
    }

    private boolean isSupported(BlockEntity be) {
        return be instanceof ChestBlockEntity
                || be instanceof TrappedChestBlockEntity
                || be instanceof BarrelBlockEntity
                || be instanceof AbstractFurnaceBlockEntity
                || be instanceof EnderChestBlockEntity
                || be instanceof ShulkerBoxBlockEntity
                || be instanceof MobSpawnerBlockEntity;
    }

    private int colorOf(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return new Color(255, 215, 0).getRGB();
        if (be instanceof TrappedChestBlockEntity) return new Color(255, 80, 80).getRGB();
        if (be instanceof BarrelBlockEntity) return new Color(205, 133, 63).getRGB();
        if (be instanceof AbstractFurnaceBlockEntity) return new Color(255, 140, 0).getRGB();
        if (be instanceof EnderChestBlockEntity) return new Color(138, 43, 226).getRGB();
        if (be instanceof ShulkerBoxBlockEntity) return new Color(147, 112, 219).getRGB();
        if (be instanceof MobSpawnerBlockEntity) return new Color(64, 224, 208).getRGB();
        return new Color(255, 255, 255).getRGB();
    }
}