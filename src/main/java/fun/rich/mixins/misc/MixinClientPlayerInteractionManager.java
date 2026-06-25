package fun.rich.mixins.misc;

import fun.rich.features.impl.misc.AntiCrashPickaxe;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        AntiCrashPickaxe m = AntiCrashPickaxe.getInstance();
        if (m != null && m.shouldCancelAttackBlock(pos, direction)) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}