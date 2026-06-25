package fun.rich.mixins.misc;

import com.mojang.authlib.GameProfile;
import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.features.impl.misc.Cape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PlayerListEntry.class, priority = 2000)
public class MixinCapeFeatureRenderer {

    @Shadow @Final private GameProfile profile;

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return;
        if (!cape.friendsEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (profile == null) return;

        PlayerEntity p = resolve(mc, profile);
        if (p == null) return;
        if (p == mc.player) return;
        if (!FriendUtils.isFriend(p)) return;

        SkinTextures original = cir.getReturnValue();
        if (original == null) return;

        Identifier custom = cape.friendsCapeId();
        cir.setReturnValue(new SkinTextures(
                original.texture(),
                original.textureUrl(),
                custom,
                original.elytraTexture(),
                original.model(),
                original.secure()
        ));
    }

    private static PlayerEntity resolve(MinecraftClient mc, GameProfile gp) {
        if (mc == null || mc.world == null || gp == null) return null;

        try {
            if (gp.getId() != null) {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p != null && gp.getId().equals(p.getUuid())) return p;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            String n = gp.getName();
            if (n != null && !n.isEmpty()) {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    if (p != null && p.getGameProfile() != null && p.getGameProfile().getName() != null) {
                        if (p.getGameProfile().getName().equalsIgnoreCase(n)) return p;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}
