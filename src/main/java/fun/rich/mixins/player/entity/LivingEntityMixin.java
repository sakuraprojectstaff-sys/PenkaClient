package fun.rich.mixins.player.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fun.rich.events.block.PushEvent;
import fun.rich.events.item.SwingDurationEvent;
import fun.rich.events.player.EntityDeathEvent;
import fun.rich.events.player.JumpEvent;
import fun.rich.utils.client.managers.event.EventManager;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow public abstract boolean hasStatusEffect(RegistryEntry<StatusEffect> effect);
    @Shadow @Nullable public abstract StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect);
    @Shadow public float bodyYaw;
    @Shadow public abstract boolean isInSwimmingPose();

    @Unique
    private static MinecraftClient rich$getClient() {
        return MinecraftClient.getInstance();
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    public void isPushable(CallbackInfoReturnable<Boolean> infoReturnable) {
        PushEvent event = new PushEvent(PushEvent.Type.COLLISION);
        EventManager.callEvent(event);
        if (event.isCancelled()) infoReturnable.setReturnValue(false);
    }

    @Redirect(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(LivingEntity instance) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return instance.getPitch();
        }
        var rotationManager = TurnsConnection.INSTANCE;
        var rotation = rotationManager.getRotation();
        var configurable = rotationManager.getCurrentRotationPlan();
        if (rotation == null || configurable == null || !configurable.isMoveCorrection() || configurable.isChangeLook()) {
            return instance.getPitch();
        }
        return rotation.getPitch();
    }

    @Redirect(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d hookModifyFallFlyingRotationVector(LivingEntity original) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return original.getRotationVector();
        }
        var rotationManager = TurnsConnection.INSTANCE;
        var rotation = rotationManager.getRotation();
        var configurable = rotationManager.getCurrentRotationPlan();
        if (rotation == null || configurable == null || !configurable.isMoveCorrection() || configurable.isChangeLook()) {
            return original.getRotationVector();
        }
        return rotation.toVector();
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void jump(CallbackInfo info) {
        if ((Object) this instanceof ClientPlayerEntity player) {
            JumpEvent event = new JumpEvent(player);
            EventManager.callEvent(event);
            if (event.isCancelled()) info.cancel();
        }
    }

    @ModifyExpressionValue(method = "jump", at = @At(value = "NEW", target = "(DDD)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d hookFixRotation(Vec3d original) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return original;
        }
        var moveRotation = TurnsConnection.INSTANCE.getMoveRotation();
        if (moveRotation == null) {
            return original;
        }
        float yaw = moveRotation.getYaw() * 0.017453292F;
        return new Vec3d(-MathHelper.sin(yaw) * 0.2F, 0.0, MathHelper.cos(yaw) * 0.2F);
    }

    @ModifyExpressionValue(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(float original) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return original;
        }
        var moveRotation = TurnsConnection.INSTANCE.getMoveRotation();
        if (moveRotation == null) {
            return original;
        }
        return moveRotation.getPitch();
    }

    @ModifyExpressionValue(method = "calcGlidingVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getRotationVector()Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d hookModifyFallFlyingRotationVector(Vec3d original) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return original;
        }
        var moveRotation = TurnsConnection.INSTANCE.getMoveRotation();
        if (moveRotation == null) {
            return original;
        }
        return moveRotation.toVector();
    }

    @Inject(method = "getHandSwingDuration", at = @At("HEAD"), cancellable = true)
    private void swingProgressHook(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient mc = rich$getClient();
        if (mc == null || mc.player == null || (Object) this != mc.player) {
            return;
        }

        SwingDurationEvent event = new SwingDurationEvent();
        EventManager.callEvent(event);

        if (event.isCancelled()) {
            float animation = event.getAnimation();

            if (StatusEffectUtil.hasHaste(mc.player)) {
                animation *= (6 - (1 + StatusEffectUtil.getHasteAmplifier(mc.player)));
            } else if (hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                StatusEffectInstance fatigue = getStatusEffect(StatusEffects.MINING_FATIGUE);
                int amplifier = fatigue != null ? fatigue.getAmplifier() : 0;
                animation *= (6 + (1 + amplifier) * 2);
            } else {
                animation *= 6;
            }

            cir.setReturnValue((int) animation);
        }
    }

    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        EntityDeathEvent event = new EntityDeathEvent(entity, source);
        EventManager.callEvent(event);
    }

    @Inject(method = "handleStatus", at = @At("HEAD"))
    private void handleStatus(byte status, CallbackInfo ci) {
        if (status == 3) {
            LivingEntity entity = (LivingEntity) (Object) this;
            EntityDeathEvent event = new EntityDeathEvent(entity, null);
            EventManager.callEvent(event);
        }
    }
}