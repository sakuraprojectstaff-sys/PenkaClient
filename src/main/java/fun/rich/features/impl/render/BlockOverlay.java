package fun.rich.features.impl.render;

import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockOverlay extends Module {

    final SelectSetting shaderMode = new SelectSetting("Шейдер", "Режим заливки оверлея блока")
            .value("Дефолт", "Портал", "Глубокий портал", "Текстура 1", "Текстура 2")
            .selected("Дефолт");

    public static BlockOverlay getInstance() {
        return Instance.get(BlockOverlay.class);
    }

    public BlockOverlay() {
        super("BlockOverlay", "Block Overlay", ModuleCategory.RENDER);
        settings().add(shaderMode);
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }

        if (!(mc.crosshairTarget instanceof BlockHitResult result) || result.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = result.getBlockPos();
        VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
        if (shape == null || shape.isEmpty()) {
            return;
        }

        int color = ColorAssist.getClientColor();

        switch (shaderMode.getSelected()) {
            case "Портал" -> Render3D.drawShapeAlternativePortal(pos, shape, color, 2.0f, true, true);
            case "Глубокий портал" -> Render3D.drawShapeAlternativeDeepPortal(pos, shape, color, 2.0f, true, true);
            case "Текстура 1" -> Render3D.drawShapeAlternativePortalTex1(pos, shape, color, 2.0f, true, true);
            case "Текстура 2" -> Render3D.drawShapeAlternativePortalTex2(pos, shape, color, 2.0f, true, true);
            default -> Render3D.drawShapeAlternative(pos, shape, color, 2.0f, true, true);
        }
    }
}