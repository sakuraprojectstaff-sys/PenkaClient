package fun.rich.mixins.misc;

import fun.rich.common.proxy.SafeModeIndicator;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientSafeModeIndicatorMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void rich$safeModeIndicatorTick(CallbackInfo ci) {
        SafeModeIndicator.tick();
    }
}