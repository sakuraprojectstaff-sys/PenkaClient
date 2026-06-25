package fun.rich.bots.world;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BotWorld {
    private final Map<Integer, BotEntityState> entities = new ConcurrentHashMap<>();
    private final Map<Long, BotChunkState> chunks = new ConcurrentHashMap<>();
    private long worldSeed;
    private int simulationDistance;
    private int viewDistance;
    private boolean hardcore;
    private boolean debugWorld;
    private boolean flatWorld;
    private long totalTime;
    private long lastTickTime;

    public void tick() {
        lastTickTime = System.currentTimeMillis();
        totalTime++;
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public void setWorldSeed(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    public int getSimulationDistance() {
        return simulationDistance;
    }

    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    public boolean isHardcore() {
        return hardcore;
    }

    public void setHardcore(boolean hardcore) {
        this.hardcore = hardcore;
    }

    public boolean isDebugWorld() {
        return debugWorld;
    }

    public void setDebugWorld(boolean debugWorld) {
        this.debugWorld = debugWorld;
    }

    public boolean isFlatWorld() {
        return flatWorld;
    }

    public void setFlatWorld(boolean flatWorld) {
        this.flatWorld = flatWorld;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public long getLastTickTime() {
        return lastTickTime;
    }

    public Collection<BotEntityState> getEntities() {
        return entities.values();
    }

    public BotEntityState getEntity(int id) {
        return entities.get(id);
    }

    public void addEntity(int id, BotEntityState state) {
        if (state == null) return;
        entities.put(id, state);
    }

    public void removeEntity(int id) {
        entities.remove(id);
    }

    public boolean hasChunk(int chunkX, int chunkZ) {
        return chunks.containsKey(packChunk(chunkX, chunkZ));
    }

    public BotChunkState getChunk(int chunkX, int chunkZ) {
        return chunks.get(packChunk(chunkX, chunkZ));
    }

    public void putChunk(int chunkX, int chunkZ, BotChunkState chunk) {
        if (chunk == null) return;
        chunks.put(packChunk(chunkX, chunkZ), chunk);
    }

    public void unloadChunk(int chunkX, int chunkZ) {
        chunks.remove(packChunk(chunkX, chunkZ));
    }

    public void onGameJoin(Object packet) {
        Long seed = extractLong(packet, "getSeed", "seed");
        if (seed != null) {
            this.worldSeed = seed;
        }

        Integer simDist = extractInt(packet, "getSimulationDistance", "simulationDistance");
        if (simDist != null) {
            this.simulationDistance = simDist;
        }

        Integer vd = extractInt(packet, "getViewDistance", "viewDistance");
        if (vd != null) {
            this.viewDistance = vd;
        }

        Boolean hc = extractBoolean(packet, "isHardcore", "hardcore");
        if (hc != null) {
            this.hardcore = hc;
        }

        Boolean dbg = extractBoolean(packet, "isDebugWorld", "debugWorld");
        if (dbg != null) {
            this.debugWorld = dbg;
        }

        Boolean flat = extractBoolean(packet, "isFlatWorld", "flatWorld");
        if (flat != null) {
            this.flatWorld = flat;
        }
    }

    public void handleGameJoin(Object packet) {
        onGameJoin(packet);
    }

    public void applyGameJoin(Object packet) {
        onGameJoin(packet);
    }

    public void onRespawn(Object packet) {
        Boolean dbg = extractBoolean(packet, "isDebugWorld", "debugWorld");
        if (dbg != null) {
            this.debugWorld = dbg;
        }

        Boolean flat = extractBoolean(packet, "isFlatWorld", "flatWorld");
        if (flat != null) {
            this.flatWorld = flat;
        }
    }

    public void handleRespawn(Object packet) {
        onRespawn(packet);
    }

    public void applyRespawn(Object packet) {
        onRespawn(packet);
    }

    public void onChunkData(Object packet) {
        Integer chunkX = extractInt(packet, "getChunkX", "getX", "chunkX", "x");
        Integer chunkZ = extractInt(packet, "getChunkZ", "getZ", "chunkZ", "z");
        if (chunkX == null || chunkZ == null) {
            return;
        }

        BotChunkState chunk = chunks.computeIfAbsent(packChunk(chunkX, chunkZ), k -> new BotChunkState(chunkX, chunkZ));
        chunk.setLoaded(true);
    }

    public void handleChunkData(Object packet) {
        onChunkData(packet);
    }

    public void applyChunkData(Object packet) {
        onChunkData(packet);
    }

    public void onUnloadChunk(Object packet) {
        Integer chunkX = extractInt(packet, "getChunkX", "getX", "chunkX", "x");
        Integer chunkZ = extractInt(packet, "getChunkZ", "getZ", "chunkZ", "z");
        if (chunkX == null || chunkZ == null) {
            return;
        }
        unloadChunk(chunkX, chunkZ);
    }

    public void handleUnloadChunk(Object packet) {
        onUnloadChunk(packet);
    }

    public void applyUnloadChunk(Object packet) {
        onUnloadChunk(packet);
    }

    public void onEntitySpawn(Object packet) {
        Integer id = extractInt(packet, "getId", "getEntityId", "id", "entityId");
        if (id == null) {
            return;
        }

        BotEntityState entity = entities.computeIfAbsent(id, BotEntityState::new);

        Double x = extractDouble(packet, "getX", "x");
        Double y = extractDouble(packet, "getY", "y");
        Double z = extractDouble(packet, "getZ", "z");
        if (x != null && y != null && z != null) {
            entity.setPosition(x, y, z);
        }

        Float yaw = extractFloat(packet, "getYaw", "yaw");
        if (yaw != null) {
            entity.setYaw(yaw);
            entity.setHeadYaw(yaw);
        }

        Float pitch = extractFloat(packet, "getPitch", "pitch");
        if (pitch != null) {
            entity.setPitch(pitch);
        }

        Integer typeId = extractInt(packet, "getEntityTypeId", "entityTypeId", "typeId");
        if (typeId != null) {
            entity.setTypeId(typeId);
        }

        Object uuid = extractObject(packet, "getUuid", "uuid");
        if (uuid != null) {
            entity.setUuidString(String.valueOf(uuid));
        }
    }

    public void handleEntitySpawn(Object packet) {
        onEntitySpawn(packet);
    }

    public void applyEntitySpawn(Object packet) {
        onEntitySpawn(packet);
    }

    public void onEntityTrackerUpdate(Object packet) {
        Integer id = extractInt(packet, "getId", "getEntityId", "id", "entityId");
        if (id == null) {
            return;
        }
        entities.computeIfAbsent(id, BotEntityState::new);
    }

    public void handleEntityTrackerUpdate(Object packet) {
        onEntityTrackerUpdate(packet);
    }

    public void applyEntityTrackerUpdate(Object packet) {
        onEntityTrackerUpdate(packet);
    }

    public void onEntityPosition(Object packet) {
        Integer id = extractInt(packet, "getId", "getEntityId", "id", "entityId");
        if (id == null) {
            return;
        }

        BotEntityState entity = entities.computeIfAbsent(id, BotEntityState::new);

        Double x = extractDouble(packet, "getX", "x");
        Double y = extractDouble(packet, "getY", "y");
        Double z = extractDouble(packet, "getZ", "z");

        if (x != null && y != null && z != null) {
            entity.setPosition(x, y, z);
            return;
        }

        Double dx = extractDouble(packet, "getDeltaX", "deltaX");
        Double dy = extractDouble(packet, "getDeltaY", "deltaY");
        Double dz = extractDouble(packet, "getDeltaZ", "deltaZ");

        if (dx != null) entity.setX(entity.getX() + dx);
        if (dy != null) entity.setY(entity.getY() + dy);
        if (dz != null) entity.setZ(entity.getZ() + dz);
    }

    public void handleEntityPosition(Object packet) {
        onEntityPosition(packet);
    }

    public void applyEntityPosition(Object packet) {
        onEntityPosition(packet);
    }

    public void onEntityVelocity(Object packet) {
        Integer id = extractInt(packet, "getId", "getEntityId", "id", "entityId");
        if (id == null) {
            return;
        }

        BotEntityState entity = entities.computeIfAbsent(id, BotEntityState::new);

        Double vx = extractDouble(packet, "getVelocityX", "velocityX", "getX", "x");
        Double vy = extractDouble(packet, "getVelocityY", "velocityY", "getY", "y");
        Double vz = extractDouble(packet, "getVelocityZ", "velocityZ", "getZ", "z");

        if (vx != null) entity.setVelocityX(vx);
        if (vy != null) entity.setVelocityY(vy);
        if (vz != null) entity.setVelocityZ(vz);
    }

    public void handleEntityVelocity(Object packet) {
        onEntityVelocity(packet);
    }

    public void applyEntityVelocity(Object packet) {
        onEntityVelocity(packet);
    }

    public void onEntityHeadYaw(Object packet) {
        Integer id = extractInt(packet, "getEntityId", "getId", "entityId", "id");
        if (id == null) {
            return;
        }

        BotEntityState entity = entities.computeIfAbsent(id, BotEntityState::new);
        Float yaw = extractFloat(packet, "getHeadYaw", "getYaw", "headYaw", "yaw");
        if (yaw != null) {
            entity.setHeadYaw(yaw);
        }
    }

    public void handleEntityHeadYaw(Object packet) {
        onEntityHeadYaw(packet);
    }

    public void applyEntityHeadYaw(Object packet) {
        onEntityHeadYaw(packet);
    }

    public void onEntityAnimation(Object packet) {
        Integer id = extractInt(packet, "getEntityId", "getId", "entityId", "id");
        if (id == null) {
            return;
        }
        entities.computeIfAbsent(id, BotEntityState::new).setLastAnimationTime(System.currentTimeMillis());
    }

    public void handleEntityAnimation(Object packet) {
        onEntityAnimation(packet);
    }

    public void applyEntityAnimation(Object packet) {
        onEntityAnimation(packet);
    }

    public void onEntityStatus(Object packet) {
        Integer id = extractInt(packet, "getEntityId", "getId", "entityId", "id");
        if (id == null) {
            return;
        }

        BotEntityState entity = entities.computeIfAbsent(id, BotEntityState::new);
        Integer status = extractInt(packet, "getStatus", "status");
        if (status != null) {
            entity.setLastStatus(status);
        }
    }

    public void handleEntityStatus(Object packet) {
        onEntityStatus(packet);
    }

    public void applyEntityStatus(Object packet) {
        onEntityStatus(packet);
    }

    public void onEntitiesDestroy(Object packet) {
        int[] ids = extractIntArray(packet, "getEntityIds", "entityIds");
        if (ids != null) {
            for (int id : ids) {
                entities.remove(id);
            }
            return;
        }

        Integer id = extractInt(packet, "getEntityId", "getId", "entityId", "id");
        if (id != null) {
            entities.remove(id);
        }
    }

    public void handleEntitiesDestroy(Object packet) {
        onEntitiesDestroy(packet);
    }

    public void applyEntitiesDestroy(Object packet) {
        onEntitiesDestroy(packet);
    }

    public void onGameMessage(Object packet) {
    }

    public void handleGameMessage(Object packet) {
        onGameMessage(packet);
    }

    public void applyGameMessage(Object packet) {
        onGameMessage(packet);
    }

    private static long packChunk(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Integer extractInt(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Long extractLong(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof Number n ? n.longValue() : null;
    }

    private static Double extractDouble(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Float extractFloat(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof Number n ? n.floatValue() : null;
    }

    private static Boolean extractBoolean(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof Boolean b ? b : null;
    }

    private static Object extractObject(Object packet, String... names) {
        return extract(packet, names);
    }

    private static int[] extractIntArray(Object packet, String... names) {
        Object value = extract(packet, names);
        return value instanceof int[] arr ? arr : null;
    }

    private static Object extract(Object packet, String... names) {
        if (packet == null || names == null) {
            return null;
        }

        for (String name : names) {
            try {
                Method method = findNoArgMethod(packet.getClass(), name);
                if (method != null) {
                    method.setAccessible(true);
                    return method.invoke(packet);
                }
            } catch (Throwable ignored) {
            }

            try {
                Field field = findField(packet.getClass(), name);
                if (field != null) {
                    field.setAccessible(true);
                    return field.get(packet);
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0) {
                return method;
            }
        }

        return null;
    }

    private static Field findField(Class<?> type, String name) {
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

    public static final class BotEntityState {
        private final int id;
        private int typeId = -1;
        private String uuidString = "";
        private double x;
        private double y;
        private double z;
        private double velocityX;
        private double velocityY;
        private double velocityZ;
        private float yaw;
        private float pitch;
        private float headYaw;
        private int lastStatus;
        private long lastAnimationTime;

        public BotEntityState(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public int getTypeId() {
            return typeId;
        }

        public void setTypeId(int typeId) {
            this.typeId = typeId;
        }

        public String getUuidString() {
            return uuidString;
        }

        public void setUuidString(String uuidString) {
            this.uuidString = uuidString == null ? "" : uuidString;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public void setPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public double getVelocityX() {
            return velocityX;
        }

        public void setVelocityX(double velocityX) {
            this.velocityX = velocityX;
        }

        public double getVelocityY() {
            return velocityY;
        }

        public void setVelocityY(double velocityY) {
            this.velocityY = velocityY;
        }

        public double getVelocityZ() {
            return velocityZ;
        }

        public void setVelocityZ(double velocityZ) {
            this.velocityZ = velocityZ;
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

        public float getHeadYaw() {
            return headYaw;
        }

        public void setHeadYaw(float headYaw) {
            this.headYaw = headYaw;
        }

        public int getLastStatus() {
            return lastStatus;
        }

        public void setLastStatus(int lastStatus) {
            this.lastStatus = lastStatus;
        }

        public long getLastAnimationTime() {
            return lastAnimationTime;
        }

        public void setLastAnimationTime(long lastAnimationTime) {
            this.lastAnimationTime = lastAnimationTime;
        }
    }

    public static final class BotChunkState {
        private final int x;
        private final int z;
        private boolean loaded;

        public BotChunkState(int x, int z) {
            this.x = x;
            this.z = z;
            this.loaded = true;
        }

        public int getX() {
            return x;
        }

        public int getZ() {
            return z;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
        }
    }
}