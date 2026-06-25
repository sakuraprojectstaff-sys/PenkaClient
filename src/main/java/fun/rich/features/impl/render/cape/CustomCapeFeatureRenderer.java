package fun.rich.features.impl.render.cape;

import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.features.impl.misc.Cape;
import fun.rich.features.impl.render.cape.math.Vector3;
import fun.rich.features.impl.render.cape.sim.StickSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CustomCapeFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    private static final int PART_COUNT = 16;

    public CustomCapeFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context, Object entityModels, Object equipmentModelLoader) {
        super(context);
    }

    @Override
    public void render(MatrixStack poseStack, VertexConsumerProvider multiBufferSource, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        Cape cape = Cape.getInstance();
        if (cape == null || !cape.enabledCompat()) return;
        if (!state.capeVisible) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        Entity entity = mc.world.getEntityById(state.id);
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        if (!shouldRenderFor(player, cape, mc)) return;

        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isEmpty() && chest.isOf(Items.ELYTRA)) return;

        if (!(player instanceof CapeHolder holder)) return;
        StickSimulation simulation = holder.getSimulation();
        if (simulation == null || simulation.empty() || simulation.getPoints().size() < PART_COUNT) return;

        Identifier texture = getCapeTexture(state);
        if (texture == null) return;

        VertexConsumer buffer = multiBufferSource.getBuffer(RenderLayer.getEntityCutout(texture));
        renderSmoothCape(poseStack, buffer, player, simulation, 1.0f, light);
    }

    private boolean shouldRenderFor(AbstractClientPlayerEntity player, Cape cape, MinecraftClient mc) {
        if (player == mc.player) return true;
        return cape.friendsEnabled() && FriendUtils.isFriend(player);
    }

    private void renderSmoothCape(MatrixStack poseStack, VertexConsumer buffer, AbstractClientPlayerEntity player, StickSimulation simulation, float delta, int light) {
        Matrix4f oldPositionMatrix = null;

        for (int part = 0; part < PART_COUNT; part++) {
            modifyPoseStackSimulation(poseStack, player, simulation, delta, part);

            MatrixStack.Entry entry = poseStack.peek();
            Matrix4f currentMatrix = new Matrix4f(entry.getPositionMatrix());

            if (oldPositionMatrix == null) {
                oldPositionMatrix = new Matrix4f(currentMatrix);
            }

            if (part == 0) {
                addTopVertex(buffer, entry, currentMatrix, oldPositionMatrix, 0.3f, 0.0f, 0.0f, -0.3f, 0.0f, -0.06f, part, light);
            }

            if (part == PART_COUNT - 1) {
                addBottomVertex(buffer, entry, currentMatrix, currentMatrix, 0.3f, (part + 1) * (0.96f / PART_COUNT), 0.0f, -0.3f, (part + 1) * (0.96f / PART_COUNT), -0.06f, part, light);
            }

            addLeftVertex(buffer, entry, currentMatrix, oldPositionMatrix, -0.3f, (part + 1) * (0.96f / PART_COUNT), 0.0f, -0.3f, part * (0.96f / PART_COUNT), -0.06f, part, light);
            addRightVertex(buffer, entry, currentMatrix, oldPositionMatrix, 0.3f, (part + 1) * (0.96f / PART_COUNT), 0.0f, 0.3f, part * (0.96f / PART_COUNT), -0.06f, part, light);
            addBackVertex(buffer, entry, currentMatrix, oldPositionMatrix, 0.3f, (part + 1) * (0.96f / PART_COUNT), -0.06f, -0.3f, part * (0.96f / PART_COUNT), -0.06f, part, light);
            addFrontVertex(buffer, entry, oldPositionMatrix, currentMatrix, 0.3f, (part + 1) * (0.96f / PART_COUNT), 0.0f, -0.3f, part * (0.96f / PART_COUNT), 0.0f, part, light);

            oldPositionMatrix = new Matrix4f(currentMatrix);
            poseStack.pop();
        }
    }

    private void modifyPoseStackSimulation(MatrixStack poseStack, AbstractClientPlayerEntity player, StickSimulation simulation, float delta, int part) {
        poseStack.push();

        ItemStack itemStack = player.getEquippedStack(EquipmentSlot.CHEST);
        double z1 = !itemStack.isEmpty() ? 0.15 : 0.125;

        poseStack.translate(0.0, 0.0, z1);

        StickSimulation.Point capePoint = simulation.getPoints().get(0);
        float x = simulation.getPoints().get(part).getLerpX(delta) - capePoint.getLerpX(delta);
        if (x > 0.0f) {
            x = 0.0f;
        }

        float y = capePoint.getLerpY(delta) - part - simulation.getPoints().get(part).getLerpY(delta);
        float z = capePoint.getLerpZ(delta) - simulation.getPoints().get(part).getLerpZ(delta);
        float sidewaysRotationOffset = 0.0f;
        float partRotation = getRotation(delta, part, simulation);
        float height = 0.0f;

        if (player.isInSneakingPose()) {
            height += 25.0f;
            poseStack.translate(0.0, 0.15000000596046448, 0.0);
        }

        float naturalWindSwing = getNaturalWindSwing(part, player.isTouchingWater() || player.isSwimming());

        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6.0f + height + naturalWindSwing));
        poseStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sidewaysRotationOffset / 2.0f));
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - sidewaysRotationOffset / 2.0f));
        poseStack.translate(-z / PART_COUNT, y / PART_COUNT, x / PART_COUNT);
        poseStack.translate(0.0, 0.03, -0.03);
        poseStack.translate(0.0, part * 1.0f / PART_COUNT, 0.0);
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-partRotation));
        poseStack.translate(0.0, -part * 1.0f / PART_COUNT, 0.0);
        poseStack.translate(0.0, -0.03, 0.03);
    }

    private float getRotation(float delta, int part, StickSimulation simulation) {
        if (part == PART_COUNT - 1) {
            return getRotation(delta, part - 1, simulation);
        }
        return (float) getAngle(
                simulation.getPoints().get(part).getLerpedPos(delta),
                simulation.getPoints().get(part + 1).getLerpedPos(delta)
        );
    }

    private double getAngle(Vector3 a, Vector3 b) {
        Vector3 angle = b.clone().subtract(a);
        return Math.toDegrees(Math.atan2(angle.x, angle.y)) + 180.0;
    }

    private float getNaturalWindSwing(int part, boolean underwater) {
        long highlightedPart = System.currentTimeMillis() / (underwater ? 9 : 3) % 360L;
        float relativePart = (part + 1) / (float) PART_COUNT;
        return (float) (Math.sin(Math.toRadians(relativePart * 360.0f - highlightedPart)) * 3.0);
    }

    private static void addBackVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        Matrix4f k;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
            k = matrix;
            matrix = oldMatrix;
            oldMatrix = k;
        }

        float minU = 0.015625f;
        float maxU = 0.171875f;
        float minV = 0.03125f;
        float maxV = 0.53125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, oldMatrix, x1, y2, z1, maxU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z1, minU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y1, z2, minU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x1, y1, z2, maxU, maxV, light, 1.0f, 0.0f, 0.0f);
    }

    private static void addFrontVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        Matrix4f k;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
            k = matrix;
            matrix = oldMatrix;
            oldMatrix = k;
        }

        float minU = 0.1875f;
        float maxU = 0.34375f;
        float minV = 0.03125f;
        float maxV = 0.53125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, oldMatrix, x1, y1, z1, maxU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y1, z1, minU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y2, z2, minU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x1, y2, z2, maxU, minV, light, 1.0f, 0.0f, 0.0f);
    }

    private static void addLeftVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = 0.0f;
        float maxU = 0.015625f;
        float minV = 0.03125f;
        float maxV = 0.53125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, matrix, x2, y1, z1, maxU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y1, z2, minU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z2, minU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z1, maxU, minV, light, 1.0f, 0.0f, 0.0f);
    }

    private static void addRightVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = 0.171875f;
        float maxU = 0.1875f;
        float minV = 0.03125f;
        float maxV = 0.53125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, matrix, x2, y1, z2, minU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y1, z1, maxU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z1, maxU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z2, minU, minV, light, 1.0f, 0.0f, 0.0f);
    }

    private static void addBottomVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = 0.171875f;
        float maxU = 0.328125f;
        float minV = 0.0f;
        float maxV = 0.03125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, oldMatrix, x1, y2, z2, maxU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z2, minU, minV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y1, z1, minU, maxV, light, 1.0f, 0.0f, 0.0f);
        vertex(buffer, entry, matrix, x1, y1, z1, maxU, maxV, light, 1.0f, 0.0f, 0.0f);
    }

    private static void addTopVertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, Matrix4f oldMatrix, float x1, float y1, float z1, float x2, float y2, float z2, int part, int light) {
        float i;
        if (x1 < x2) {
            i = x1;
            x1 = x2;
            x2 = i;
        }

        if (y1 < y2) {
            i = y1;
            y1 = y2;
            y2 = i;
        }

        float minU = 0.015625f;
        float maxU = 0.171875f;
        float minV = 0.0f;
        float maxV = 0.03125f;
        float deltaV = maxV - minV;
        float vPerPart = deltaV / PART_COUNT;
        maxV = minV + (vPerPart * (part + 1));
        minV = minV + (vPerPart * part);

        vertex(buffer, entry, oldMatrix, x1, y2, z1, maxU, maxV, light, 0.0f, 1.0f, 0.0f);
        vertex(buffer, entry, oldMatrix, x2, y2, z1, minU, maxV, light, 0.0f, 1.0f, 0.0f);
        vertex(buffer, entry, matrix, x2, y1, z2, minU, minV, light, 0.0f, 1.0f, 0.0f);
        vertex(buffer, entry, matrix, x1, y1, z2, maxU, minV, light, 0.0f, 1.0f, 0.0f);
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f matrix, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz) {
        buffer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    private Identifier getCapeTexture(PlayerEntityRenderState state) {
        Object skinTextures = state.skinTextures;
        if (skinTextures == null) return null;

        Identifier id = invokeIdentifierGetter(skinTextures, "capeTexture");
        if (id != null) return id;

        id = invokeIdentifierGetter(skinTextures, "getCapeTexture");
        if (id != null) return id;

        id = invokeIdentifierGetter(skinTextures, "cape");
        if (id != null) return id;

        id = readIdentifierField(skinTextures, "capeTexture");
        if (id != null) return id;

        return readIdentifierField(skinTextures, "cape");
    }

    private Identifier invokeIdentifierGetter(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Identifier identifier ? identifier : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Identifier readIdentifierField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(target);
            return value instanceof Identifier identifier ? identifier : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}