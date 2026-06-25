package fun.rich.mixins.misc;

import fun.rich.features.impl.render.ItemPhysic;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(net.minecraft.client.render.entity.ItemEntityRenderer.class)
public abstract class ItemEntityRenderer {
    @Shadow @Final private Random random;

    @Unique private Map<ItemEntityRenderState, Boolean> richGround;
    @Unique private Map<ItemEntityRenderState, Float> richGroundYaw;

    @Unique
    private Map<ItemEntityRenderState, Boolean> richGroundMap() {
        if (richGround == null) {
            richGround = new WeakHashMap<>();
        }
        return richGround;
    }

    @Unique
    private Map<ItemEntityRenderState, Float> richGroundYawMap() {
        if (richGroundYaw == null) {
            richGroundYaw = new WeakHashMap<>();
        }
        return richGroundYaw;
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("HEAD")
    )
    private void richUpdate(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        Map<ItemEntityRenderState, Boolean> ground = richGroundMap();
        Map<ItemEntityRenderState, Float> groundYaw = richGroundYawMap();

        boolean onGroundNow = entity.isOnGround();
        boolean onGroundPrev = Boolean.TRUE.equals(ground.get(state));

        if (onGroundNow && !onGroundPrev) {
            float yaw = computeYawFromVelocity(entity.getVelocity());
            if (Float.isNaN(yaw)) yaw = entity.getYaw();
            groundYaw.put(state, yaw);
        } else if (!onGroundNow) {
            groundYaw.remove(state);
        }

        ground.put(state, onGroundNow);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void richRender(ItemEntityRenderState state, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (!ItemPhysic.enabled()) return;

        if (state == null || state.itemRenderState == null || state.itemRenderState.isEmpty()) {
            ci.cancel();
            return;
        }

        Map<ItemEntityRenderState, Boolean> ground = richGroundMap();
        Map<ItemEntityRenderState, Float> groundYaw = richGroundYawMap();

        boolean onGround = Boolean.TRUE.equals(ground.get(state));

        matrices.push();

        float bob = MathHelper.sin(state.age / 10.0F + state.uniqueOffset) * 0.1F + 0.1F;
        float scaleY = state.itemRenderState.getTransformation().scale.y();

        if (onGround) {
            matrices.translate(0.0F, 0.02F + 0.25F * scaleY, 0.0F);
            float yaw = groundYaw.getOrDefault(state, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
        } else {
            matrices.translate(0.0F, bob + 0.25F * scaleY, 0.0F);
            float rot = ItemEntity.getRotation(state.age, state.uniqueOffset);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot * 300.0F));
        }

        renderItemStack(matrices, consumers, light, state);

        matrices.pop();
        ci.cancel();
    }

    @Unique
    private static float computeYawFromVelocity(Vec3d v) {
        double x = v.x;
        double z = v.z;
        double len2 = x * x + z * z;
        if (len2 < 1.0E-6) return Float.NaN;
        return (float) (MathHelper.atan2(x, z) * 57.2957763671875);
    }

    @Unique
    private void renderItemStack(MatrixStack matrices, VertexConsumerProvider consumers, int light, ItemStackEntityRenderState state) {
        this.random.setSeed(state.seed);

        int amount = state.renderedAmount;
        ItemRenderState item = state.itemRenderState;

        boolean depth = item.hasDepth();
        float sx = item.getTransformation().scale.x();
        float sy = item.getTransformation().scale.y();
        float sz = item.getTransformation().scale.z();

        if (!depth) {
            float oz = -0.09375F * (float) (amount - 1) * 0.5F * sz;
            matrices.translate(0.0F, 0.0F, oz);
        }

        for (int i = 0; i < amount; i++) {
            matrices.push();

            if (i > 0) {
                if (depth) {
                    float ox = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float oy = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float oz = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    matrices.translate(ox, oy, oz);
                } else {
                    float ox = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    float oy = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    matrices.translate(ox, oy, 0.0F);
                }
            }

            item.render(matrices, consumers, light, OverlayTexture.DEFAULT_UV);

            matrices.pop();

            if (!depth) {
                matrices.translate(0.0F * sx, 0.0F * sy, 0.09375F * sz);
            }
        }
    }
}