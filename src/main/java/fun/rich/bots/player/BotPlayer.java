package fun.rich.bots.player;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

public class BotPlayer {
    private final GameProfile gameProfile;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private float bodyYaw;
    private float headYaw;
    private boolean onGround = true;
    private boolean sprinting;
    private boolean sneaking;
    private int entityId = -1;
    private long lastTickTime;
    private String lastChatMessage = "";

    public BotPlayer(GameProfile gameProfile) {
        if (gameProfile == null) {
            this.gameProfile = new GameProfile(UUID.randomUUID(), "Bot");
        } else {
            this.gameProfile = gameProfile;
        }
    }

    public void tick() {
        lastTickTime = System.currentTimeMillis();
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public String getNameString() {
        return gameProfile.getName() == null ? "" : gameProfile.getName();
    }

    public String getNameClear() {
        return getNameString();
    }

    public UUID getUuid() {
        return gameProfile.getId();
    }

    public int getId() {
        return entityId;
    }

    public void setId(int entityId) {
        this.entityId = entityId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setPos(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setPosition(double x, double y, double z) {
        setPos(x, y, z);
    }

    public void updatePosition(double x, double y, double z) {
        setPos(x, y, z);
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getBodyYaw() {
        return bodyYaw;
    }

    public void setBodyYaw(float bodyYaw) {
        this.bodyYaw = bodyYaw;
    }

    public float getHeadYaw() {
        return headYaw;
    }

    public void setHeadYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    public boolean isOnGround() {
        return onGround;
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public void setSprinting(boolean sprinting) {
        this.sprinting = sprinting;
    }

    public boolean isSneaking() {
        return sneaking;
    }

    public void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;
    }

    public long getLastTickTime() {
        return lastTickTime;
    }

    public String getLastChatMessage() {
        return lastChatMessage;
    }

    public void sendChatMessage(String message) {
        if (message == null) {
            this.lastChatMessage = "";
            return;
        }
        this.lastChatMessage = message;
    }

    public void onGameJoin(Object packet) {
        Integer id = extractInt(packet, "getPlayerEntityId", "playerEntityId", "entityId", "id");
        if (id != null) {
            this.entityId = id;
        }
    }

    public void handleGameJoin(Object packet) {
        onGameJoin(packet);
    }

    public void applyGameJoin(Object packet) {
        onGameJoin(packet);
    }

    public void onRespawn(Object packet) {
    }

    public void handleRespawn(Object packet) {
        onRespawn(packet);
    }

    public void applyRespawn(Object packet) {
        onRespawn(packet);
    }

    public void onAbilities(Object packet) {
    }

    public void handleAbilities(Object packet) {
        onAbilities(packet);
    }

    public void applyAbilities(Object packet) {
        onAbilities(packet);
    }

    public void onInventory(Object packet) {
    }

    public void handleInventory(Object packet) {
        onInventory(packet);
    }

    public void applyInventory(Object packet) {
        onInventory(packet);
    }

    public void onSlotUpdate(Object packet) {
    }

    public void handleSlotUpdate(Object packet) {
        onSlotUpdate(packet);
    }

    public void applySlotUpdate(Object packet) {
        onSlotUpdate(packet);
    }

    public void onGameMessage(Object packet) {
    }

    public void handleGameMessage(Object packet) {
        onGameMessage(packet);
    }

    public void applyGameMessage(Object packet) {
        onGameMessage(packet);
    }

    private static Integer extractInt(Object packet, String... names) {
        if (packet == null || names == null) {
            return null;
        }

        for (String name : names) {
            try {
                var method = findNoArgMethod(packet.getClass(), name);
                if (method != null) {
                    method.setAccessible(true);
                    Object value = method.invoke(packet);
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                var field = findField(packet.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    Object value = field.get(packet);
                    if (value instanceof Number number) {
                        return number.intValue();
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static java.lang.reflect.Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (java.lang.reflect.Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        for (java.lang.reflect.Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }

        return null;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }
}