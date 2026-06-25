package fun.rich.mixins.misc;

import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.features.impl.misc.Cape;
import fun.rich.features.impl.render.cape.CapeHolder;
import fun.rich.features.impl.render.cape.math.Vector2;
import fun.rich.features.impl.render.cape.math.Vector3;
import fun.rich.features.impl.render.cape.sim.StickSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class MixinAbstractClientPlayerEntity implements CapeHolder {

    @Unique
    private static final int rich$partCount = 16;

    @Unique
    private final StickSimulation rich$simulation = new StickSimulation();

    @Override
    public StickSimulation getSimulation() {
        return this.rich$simulation;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void rich$onTick(CallbackInfo ci) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (!rich$shouldSimulate(player)) return;

        boolean dirty = this.rich$simulation.init(rich$partCount);
        if (dirty) {
            this.rich$simulation.applyMovement(new Vector3(1.0f, 1.0f, 0.0f));
            for (int i = 0; i < 5; i++) {
                rich$simulate(player);
            }
            return;
        }

        rich$simulate(player);
    }

    @Unique
    private boolean rich$shouldSimulate(AbstractClientPlayerEntity player) {
        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        if (player == mc.player) return true;
        return cape.friendsEnabled() && FriendUtils.isFriend(player);
    }

    @Unique
    private void rich$simulate(AbstractClientPlayerEntity player) {
        StickSimulation simulation = this.rich$simulation;
        if (simulation.empty()) return;

        double d = rich$getDouble(player, "capeX", player.getX()) - player.getX();
        double m = rich$getDouble(player, "capeZ", player.getZ()) - player.getZ();

        float bodyYaw = rich$getBodyYaw(player);
        double o = MathHelper.sin(bodyYaw * 0.017453292f);
        double p = -MathHelper.cos(bodyYaw * 0.017453292f);

        float heightMul = 5.0f;
        float strafeMul = 5.0f;

        if (rich$canSwim(player)) {
            heightMul *= 2.0f;
        }

        double prevY = rich$getDouble(player, "prevY", player.getY());
        double prevX = rich$getDouble(player, "prevX", player.getX());
        double prevZ = rich$getDouble(player, "prevZ", player.getZ());

        double fallHack = MathHelper.clamp((prevY - player.getY()) * 10.0, 0.0, 1.0);

        if (rich$canSwim(player)) {
            simulation.setGravity(2.5f);
        } else {
            simulation.setGravity(25.0f);
        }

        Vector3 gravity = new Vector3(0.0f, -1.0f, 0.0f);
        Vector2 strafe = new Vector2((float) (player.getX() - prevX), (float) (player.getZ() - prevZ));
        strafe.rotateDegrees(-player.getYaw());

        boolean sneaking = player.isInSneakingPose();

        double changeX = d * o + m * p + fallHack + (sneaking && !simulation.isSneaking() ? 3.0 : 0.0);
        double changeY = (player.getY() - prevY) * heightMul + (sneaking && !simulation.isSneaking() ? 1.0 : 0.0);
        double changeZ = -strafe.x * strafeMul;

        simulation.setSneaking(sneaking);

        Vector3 change = new Vector3((float) changeX, (float) changeY, (float) changeZ);

        if (player.isSwimming()) {
            float rotation = player.getPitch() + 90.0f;
            gravity.rotateDegrees(rotation);
            change.rotateDegrees(rotation);
        }

        simulation.setGravityDirection(gravity);
        simulation.applyMovement(change);
        simulation.simulate();
    }

    @Unique
    private boolean rich$canSwim(AbstractClientPlayerEntity player) {
        return player.isTouchingWater() || player.isSwimming();
    }

    @Unique
    private float rich$getBodyYaw(AbstractClientPlayerEntity player) {
        Object value = rich$invokeNoArgs(player, "getBodyYaw");
        if (value instanceof Number number) return number.floatValue();

        value = rich$readField(player, "bodyYaw");
        if (value instanceof Number number) return number.floatValue();

        return player.getYaw();
    }

    @Unique
    private double rich$getDouble(Object target, String field, double fallback) {
        Object value = rich$readField(target, field);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    @Unique
    private Object rich$invokeNoArgs(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private Object rich$readField(Object target, String name) {
        Class<?> current = target.getClass();

        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }
}