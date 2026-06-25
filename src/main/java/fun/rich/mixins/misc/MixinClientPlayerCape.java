package fun.rich.mixins.misc;

import fun.rich.features.impl.misc.Cape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractClientPlayerEntity.class, priority = 2000)
public class MixinClientPlayerCape {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (self != mc.player) return;

        SkinTextures original = cir.getReturnValue();
        if (original == null) return;

        Identifier custom = cape.selfCapeId();
        cir.setReturnValue(new SkinTextures(
                original.texture(),
                original.textureUrl(),
                custom,
                original.elytraTexture(),
                original.model(),
                original.secure()
        ));
    }
}
