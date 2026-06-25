package fun.rich.bots.player;

import fun.rich.bots.world.BotWorld;

public class BotController {
    private boolean hittingBlock;
    private boolean creativeMode;
    private int lastAttackedEntityId = -1;
    private int lastInteractedEntityId = -1;
    private int lastBlockX;
    private int lastBlockY;
    private int lastBlockZ;
    private long lastActionTime;

    public void tick() {
    }

    public boolean getIsHittingBlock() {
        return hittingBlock;
    }

    public boolean isHittingBlock() {
        return hittingBlock;
    }

    public void setHittingBlock(boolean hittingBlock) {
        this.hittingBlock = hittingBlock;
        this.lastActionTime = System.currentTimeMillis();
    }

    public boolean isInCreativeMode() {
        return creativeMode;
    }

    public boolean hasCreativeInventory() {
        return creativeMode;
    }

    public void setCreativeMode(boolean creativeMode) {
        this.creativeMode = creativeMode;
    }

    public int getLastAttackedEntityId() {
        return lastAttackedEntityId;
    }

    public int getLastInteractedEntityId() {
        return lastInteractedEntityId;
    }

    public int getLastBlockX() {
        return lastBlockX;
    }

    public int getLastBlockY() {
        return lastBlockY;
    }

    public int getLastBlockZ() {
        return lastBlockZ;
    }

    public long getLastActionTime() {
        return lastActionTime;
    }

    public boolean attackEntity(BotPlayer player, Object entity) {
        this.lastAttackedEntityId = extractEntityId(entity);
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean interactWithEntity(BotPlayer player, Object entity, Object hitResult, Object hand) {
        this.lastInteractedEntityId = extractEntityId(entity);
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean interactWithEntity(BotPlayer player, Object entity, Object hand) {
        this.lastInteractedEntityId = extractEntityId(entity);
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean clickBlock(Object blockPos, Object direction) {
        fillBlockPos(blockPos);
        this.hittingBlock = true;
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean processRightClick(BotPlayer player, BotWorld world, Object hand) {
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean interactBlock(BotPlayer player, BotWorld world, Object hand, Object hitResult) {
        this.lastActionTime = System.currentTimeMillis();
        return true;
    }

    public boolean func_217292_a(BotPlayer player, BotWorld world, Object hand, Object hitResult) {
        return interactBlock(player, world, hand, hitResult);
    }

    public void resetBlockRemoving() {
        this.hittingBlock = false;
    }

    public void stopBreakingBlock() {
        this.hittingBlock = false;
    }

    public void syncSelectedSlot() {
    }

    private static int extractEntityId(Object entity) {
        if (entity == null) return -1;

        try {
            var m = findNoArgMethod(entity.getClass(), "getId");
            if (m != null) {
                m.setAccessible(true);
                Object value = m.invoke(entity);
                if (value instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            var f = findField(entity.getClass(), "id");
            if (f != null) {
                f.setAccessible(true);
                Object value = f.get(entity);
                if (value instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            var f = findField(entity.getClass(), "entityId");
            if (f != null) {
                f.setAccessible(true);
                Object value = f.get(entity);
                if (value instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }

        return -1;
    }

    private void fillBlockPos(Object blockPos) {
        if (blockPos == null) return;

        Integer x = extractInt(blockPos, "getX", "x");
        Integer y = extractInt(blockPos, "getY", "y");
        Integer z = extractInt(blockPos, "getZ", "z");

        if (x != null) this.lastBlockX = x;
        if (y != null) this.lastBlockY = y;
        if (z != null) this.lastBlockZ = z;
    }

    private static Integer extractInt(Object obj, String methodName, String fieldName) {
        try {
            var m = findNoArgMethod(obj.getClass(), methodName);
            if (m != null) {
                m.setAccessible(true);
                Object value = m.invoke(obj);
                if (value instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            var f = findField(obj.getClass(), fieldName);
            if (f != null) {
                f.setAccessible(true);
                Object value = f.get(obj);
                if (value instanceof Number n) return n.intValue();
            }
        } catch (Throwable ignored) {
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