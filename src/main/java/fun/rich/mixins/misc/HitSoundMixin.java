package fun.rich.mixins.misc;

import fun.rich.features.impl.misc.HitSound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class HitSoundMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Entity target, CallbackInfo ci) {
        if (!(target instanceof LivingEntity)) return;

        HitSound m = HitSound.getInstance();
        if (m == null || !m.isOn()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        mc.getSoundManager().play(
                PositionedSoundInstance.master(
                        m.getSelectedEvent(),
                        1.0F,
                        m.getVolumeValue()
                )
        );
    }
}
