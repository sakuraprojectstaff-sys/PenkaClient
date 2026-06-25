package fun.rich.mixins.player.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fun.rich.features.impl.combat.Aura;
import fun.rich.features.impl.render.Naruto;
import fun.rich.features.impl.render.PlayerAnimations;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.entity.player.PlayerEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow
    @Nullable
    protected abstract RenderLayer getRenderLayer(LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline);

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;getRenderLayer(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/render/RenderLayer;"
            )
    )
    private RenderLayer renderHook(LivingEntityRenderer instance, LivingEntityRenderState state, boolean showBody, boolean translucent, boolean showOutline) {
        return this.getRenderLayer(state, showBody, translucent, showOutline);
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;III)V"
            )
    )
    private void renderModelHook(EntityModel<?> instance,
                                 MatrixStack matrixStack,
                                 VertexConsumer vertexConsumer,
                                 int light,
                                 int overlay,
                                 int color,
                                 @Local(ordinal = 0, argsOnly = true) LivingEntityRenderState renderState) {

        MinecraftClient mc = MinecraftClient.getInstance();

        boolean applied = false;
        boolean pushed = false;

        PlayerEntityModel pm = null;

        float rp = 0.0F, lp = 0.0F, rr = 0.0F, lr = 0.0F, ry = 0.0F, ly = 0.0F;
        float hp = 0.0F, hy = 0.0F, hr = 0.0F;
        float bp = 0.0F, br = 0.0F, by = 0.0F;

        if (mc != null && mc.player != null && instance instanceof PlayerEntityModel m && renderState instanceof PlayerEntityRenderState ps) {
            if (ps.id == mc.player.getId()) {

                float k = fun.rich.features.impl.render.PlayerAnimations.querySealsK((PlayerEntity) mc.player);

                if (k > 0.001F && !ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !mc.options.getPerspective().isFirstPerson()) {
                    pm = m;

                    rp = pm.rightArm.pitch;
                    lp = pm.leftArm.pitch;
                    rr = pm.rightArm.roll;
                    lr = pm.leftArm.roll;
                    ry = pm.rightArm.yaw;
                    ly = pm.leftArm.yaw;

                    hp = pm.head.pitch;
                    hy = pm.head.yaw;
                    hr = pm.head.roll;

                    bp = pm.body.pitch;
                    by = pm.body.yaw;
                    br = pm.body.roll;

                    float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
                    float t = (mc.player.age + tickDelta);

                    float s = MathHelper.clamp(k, 0.0F, 1.0F);

                    float pulseA = MathHelper.sin(t * 7.2F) * 0.05F * s;
                    float pulseB = MathHelper.cos(t * 8.0F) * 0.035F * s;

                    pm.body.pitch = pm.body.pitch + 0.16F * s;
                    pm.head.pitch = pm.head.pitch - 0.08F * s + pulseB * 0.20F;

                    float armUp = -0.85F * s;
                    float armIn = 0.78F * s;
                    float armRoll = 0.62F * s;

                    pm.rightArm.pitch = pm.rightArm.pitch + armUp + pulseA;
                    pm.leftArm.pitch = pm.leftArm.pitch + armUp - pulseA;

                    pm.rightArm.yaw = pm.rightArm.yaw - armIn;
                    pm.leftArm.yaw = pm.leftArm.yaw + armIn;

                    pm.rightArm.roll = pm.rightArm.roll + armRoll + pulseB * 0.25F;
                    pm.leftArm.roll = pm.leftArm.roll - armRoll - pulseB * 0.25F;

                    float pivotY = 0.72F;
                    float lean = 0.14F * s;
                    matrixStack.push();
                    pushed = true;
                    matrixStack.translate(0.0F, pivotY, 0.0F);
                    matrixStack.multiply(RotationAxis.POSITIVE_X.rotation(lean));
                    matrixStack.translate(0.0F, -pivotY, 0.0F);

                    applied = true;
                } else if (Naruto.enabled()) {

                    float speed = (float) mc.player.getVelocity().horizontalLength();
                    float run = MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);

                    if (run >= 0.10F && !ps.isSwimming && !ps.isGliding && !ps.isUsingItem && !ps.isInSneakingPose) {
                        pm = m;

                        rp = pm.rightArm.pitch;
                        lp = pm.leftArm.pitch;
                        rr = pm.rightArm.roll;
                        lr = pm.leftArm.roll;
                        ry = pm.rightArm.yaw;
                        ly = pm.leftArm.yaw;

                        hp = pm.head.pitch;
                        hy = pm.head.yaw;
                        hr = pm.head.roll;

                        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
                        float tt = (mc.player.age + tickDelta) * 0.6F;

                        float swing = MathHelper.cos(tt) * 0.20F * run;

                        float armBack = 1.25F + 0.45F * run + swing;
                        float armRoll = 0.55F * run;
                        float headDown = -0.15F * run;

                        float bodyLean = 0.45F * run;

                        pm.rightArm.pitch = armBack;
                        pm.leftArm.pitch = armBack;

                        pm.rightArm.roll = armRoll;
                        pm.leftArm.roll = -armRoll;

                        pm.rightArm.yaw = pm.rightArm.yaw + 0.10F * run;
                        pm.leftArm.yaw = pm.leftArm.yaw - 0.10F * run;

                        pm.head.pitch = pm.head.pitch + headDown;

                        float pivotY = 0.75F;
                        matrixStack.push();
                        pushed = true;
                        matrixStack.translate(0.0F, pivotY, 0.0F);
                        matrixStack.multiply(RotationAxis.POSITIVE_X.rotation(bodyLean));
                        matrixStack.translate(0.0F, -pivotY, 0.0F);

                        applied = true;
                    }
                }
            }
        }

        try {
            instance.render(matrixStack, vertexConsumer, light, overlay, color);
        } finally {
            if (pushed) {
                matrixStack.pop();
            }
            if (applied && pm != null) {
                pm.rightArm.pitch = rp;
                pm.leftArm.pitch = lp;
                pm.rightArm.roll = rr;
                pm.leftArm.roll = lr;
                pm.rightArm.yaw = ry;
                pm.leftArm.yaw = ly;

                pm.head.pitch = hp;
                pm.head.yaw = hy;
                pm.head.roll = hr;

                pm.body.pitch = bp;
                pm.body.yaw = by;
                pm.body.roll = br;
            }
        }
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F")
    )
    private float lerpAngleDegreesHook(
            float original,
            @Local(ordinal = 0, argsOnly = true) LivingEntity entity,
            @Local(ordinal = 0, argsOnly = true) float delta
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;

        TurnsConnection controller = TurnsConnection.INSTANCE;

        if (entity.equals(mc.player)
                && controller.getPreviousRotation().getYaw() != mc.player.getYaw()
                && controller.getFakeRotation().getYaw() != mc.player.getYaw()
                && !(mc.currentScreen instanceof HandledScreen)) {

            boolean isLony = Aura.fakeRotate;

            float prevYaw = isLony ? controller.getFakeRotation().getYaw() : controller.getPreviousRotation().getYaw();
            float currentYaw = isLony ? controller.getFakeRotation().getYaw() : controller.getRotation().getYaw();

            if (Aura.getInstance().getTarget() == null) {
                prevYaw = controller.getPreviousRotation().getYaw();
                currentYaw = controller.getRotation().getYaw();
            }

            return MathHelper.lerp(delta, prevYaw, currentYaw);
        }

        return original;
    }

    @ModifyExpressionValue(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F")
    )
    private float getLerpedPitchHook(
            float original,
            @Local(ordinal = 0, argsOnly = true) LivingEntity entity,
            @Local(ordinal = 0, argsOnly = true) float delta
    ) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) return original;

        TurnsConnection controller = TurnsConnection.INSTANCE;

        if (entity.equals(mc.player)
                && controller.getPreviousRotation().getPitch() != mc.player.getPitch()
                && controller.getFakeRotation().getPitch() != mc.player.getPitch()
                && !(mc.currentScreen instanceof HandledScreen)) {

            boolean isLony = Aura.fakeRotate;

            float prevPitch = isLony ? controller.getFakeRotation().getPitch() : controller.getPreviousRotation().getPitch();
            float currentPitch = isLony ? controller.getFakeRotation().getPitch() : controller.getRotation().getPitch();

            if (Aura.getInstance().getTarget() == null) {
                prevPitch = controller.getPreviousRotation().getPitch();
                currentPitch = controller.getRotation().getPitch();
            }

            return MathHelper.lerp(delta, prevPitch, currentPitch);
        }

        return original;
    }
}
