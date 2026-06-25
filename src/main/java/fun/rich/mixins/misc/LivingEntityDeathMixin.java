package fun.rich.mixins.misc;

import fun.rich.events.player.EntityDeathEvent;
import fun.rich.utils.client.managers.event.EventManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityDeathMixin {

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void funrich$onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld() == null || !self.getWorld().isClient) return;
        EventManager.callEvent(new EntityDeathEvent(self, source));
    }
}