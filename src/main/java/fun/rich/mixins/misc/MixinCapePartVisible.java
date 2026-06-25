package fun.rich.mixins.misc;

import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.features.impl.misc.Cape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerEntity.class, priority = 2000)
public class MixinCapePartVisible {

    @Inject(method = "isPartVisible", at = @At("HEAD"), cancellable = true)
    private void onIsPartVisible(PlayerModelPart part, CallbackInfoReturnable<Boolean> cir) {
        if (part != PlayerModelPart.CAPE) return;

        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        PlayerEntity p = (PlayerEntity) (Object) this;

        if (p == mc.player) {
            cir.setReturnValue(true);
            return;
        }

        if (!cape.friendsEnabled()) return;
        if (!FriendUtils.isFriend(p)) return;

        cir.setReturnValue(true);
    }
}
