package fun.rich.mixins.misc;

import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.features.impl.misc.Cape;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeFeatureRenderer.class)
public class MixinVanillaCapeFeatureRenderer {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rich$cancelVanillaCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return;
        if (!state.capeVisible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Entity entity = mc.world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;

        if (player != mc.player) {
            if (!cape.friendsEnabled()) return;
            if (!FriendUtils.isFriend(player)) return;
        }

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.isOf(Items.ELYTRA)) return;

        ci.cancel();
    }
}