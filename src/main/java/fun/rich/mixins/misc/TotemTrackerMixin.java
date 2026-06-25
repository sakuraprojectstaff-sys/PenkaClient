package fun.rich.mixins.misc;

import fun.rich.features.impl.misc.TotemTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class TotemTrackerMixin {

    @Inject(method = "onEntityStatus", at = @At("HEAD"))
    private void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        if (packet.getStatus() != 35) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        Entity e = packet.getEntity(mc.world);
        if (!(e instanceof PlayerEntity player)) return;

        ItemStack totemStack = player.getOffHandStack();
        if (totemStack.isEmpty()) totemStack = player.getMainHandStack();

        TotemTracker mod = TotemTracker.getInstance();
        if (mod != null) mod.onTotemPop(player, totemStack.copy());
    }
}