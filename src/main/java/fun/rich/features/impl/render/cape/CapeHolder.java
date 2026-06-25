package fun.rich.features.impl.render.cape;

import fun.rich.features.impl.render.cape.math.Vector3;
import fun.rich.features.impl.render.cape.sim.StickSimulation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface CapeHolder {
    StickSimulation getSimulation();

    default void updateSimulation(AbstractClientPlayerEntity player, int partCount) {
        StickSimulation simulation = this.getSimulation();
        if (simulation == null) {
            return;
        }

        boolean dirty = simulation.init(partCount);
        if (dirty) {
            simulation.setSneaking(false);
            simulation.setGravityDirection(new Vector3(0.0f, -1.0f, 0.0f));
            simulation.setGravity(WaveyCapesBase.config.gravity);
            for (int i = 0; i < 10; i++) {
                simulation.simulate();
            }
        }
    }

    default void simulate(AbstractClientPlayerEntity player) {
        StickSimulation simulation = this.getSimulation();
        if (simulation == null || simulation.empty()) {
            return;
        }

        Vec3d velocity = player.getVelocity();
        double dx = velocity.x;
        double dy = velocity.y;
        double dz = velocity.z;

        float bodyYaw = getBodyYaw(player);
        double yaw = Math.toRadians(bodyYaw);

        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double rightX = Math.cos(yaw);
        double rightZ = Math.sin(yaw);

        double localForward = dx * forwardX + dz * forwardZ;
        double localStrafe = dx * rightX + dz * rightZ;
        double horizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        float gravity = WaveyCapesBase.config.gravity;
        float heightMul = WaveyCapesBase.config.heightMul;
        float strafeMul = WaveyCapesBase.config.strafeMul;
        float forwardMul = WaveyCapesBase.config.forwardMul;

        if (player.isSprinting()) {
            forwardMul *= 1.15f;
            strafeMul *= 1.1f;
        }

        if (canSwim(player)) {
            gravity *= 0.35f;
            heightMul *= 0.75f;
            strafeMul *= 0.6f;
            forwardMul *= 0.6f;
        }

        boolean sneaking = player.isInSneakingPose();

        double changeX = 0.0;
        double changeY = dy * heightMul;
        double changeZ = -localStrafe * strafeMul;

        if (localForward > 0.0) {
            changeX -= localForward * forwardMul;
        } else if (localForward < 0.0) {
            changeX -= localForward * (forwardMul * 0.2);
        }

        if (horizontalSpeed < 0.005) {
            changeX = 0.0;
            changeZ *= 0.25;
        }

        if (sneaking) {
            changeY += 0.08;
        }

        simulation.setSneaking(sneaking);
        simulation.setGravity(gravity);

        Vector3 gravityDir = new Vector3(0.0f, -1.0f, 0.0f);
        Vector3 change = new Vector3((float) changeX, (float) changeY, (float) changeZ);

        if (isActuallySwimming(player)) {
            float rotation = player.getPitch() + 90.0f;
            gravityDir.rotateDegrees(rotation);
            change.rotateDegrees(rotation);
        }

        simulation.setGravityDirection(gravityDir);
        simulation.applyMovement(change);
        simulation.simulate();
    }

    private static boolean canSwim(AbstractClientPlayerEntity player) {
        return player.isTouchingWater() || player.isSwimming();
    }

    private static boolean isActuallySwimming(AbstractClientPlayerEntity player) {
        return player.isSwimming();
    }

    private static float getBodyYaw(AbstractClientPlayerEntity player) {
        Object value = readField(player, "bodyYaw");
        if (value instanceof Number number) {
            return number.floatValue();
        }

        value = invokeNoArgs(player, "getBodyYaw");
        if (value instanceof Number number) {
            return number.floatValue();
        }

        return player.getYaw();
    }

    private static Object invokeNoArgs(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
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