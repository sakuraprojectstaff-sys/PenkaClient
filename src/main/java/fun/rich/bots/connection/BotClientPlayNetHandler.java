package fun.rich.bots.connection;

import fun.rich.bots.Bot;
import fun.rich.bots.BotManager;
import fun.rich.bots.player.BotController;
import fun.rich.bots.player.BotPlayer;
import fun.rich.bots.world.BotWorld;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class BotClientPlayNetHandler {
    private final BotNetwork connection;
    private final MinecraftClient mc;
    private BotWorld world;
    private BotPlayer bot;
    private BotController botController;
    private Bot owner;

    public BotClientPlayNetHandler(BotNetwork connection, MinecraftClient mc, BotWorld world, BotPlayer bot, BotController botController) {
        this.connection = connection;
        this.mc = mc;
        this.world = world;
        this.bot = bot;
        this.botController = botController;
    }

    public void setBotOwner(Bot owner) {
        this.owner = owner;
        syncOwnerRefs();
        tryPromoteToPlay("Play handler attached");
    }

    public Bot getBotOwner() {
        return owner;
    }

    public BotNetwork getNetwork() {
        return connection;
    }

    public MinecraftClient getMinecraft() {
        return mc;
    }

    public BotWorld getWorld() {
        return world;
    }

    public void setWorld(BotWorld world) {
        this.world = world;
        syncOwnerRefs();
        tryPromoteToPlay("World attached");
    }

    public BotPlayer getBot() {
        return bot;
    }

    public void setBot(BotPlayer bot) {
        this.bot = bot;
        syncOwnerRefs();
        tryPromoteToPlay("Player attached");
    }

    public BotController getBotController() {
        return botController;
    }

    public void setBotController(BotController botController) {
        this.botController = botController;
        syncOwnerRefs();
        tryPromoteToPlay("Controller attached");
    }

    public void tick() {
        safeTick(world);
        safeTick(bot);
        safeTick(botController);

        if (owner != null) {
            owner.touch();
        }

        tryPromoteToPlay("Play phase");
    }

    public boolean isConnectionOpen() {
        return connection != null && connection.isOpen();
    }

    public void sendQuittingDisconnectingPacket() {
        Object packet = createQuitPacket();
        if (packet != null) {
            try {
                connection.sendPacket(packet);
            } catch (Throwable ignored) {
            }
        }
        try {
            connection.close();
        } catch (Throwable ignored) {
        }
        if (owner != null) {
            owner.markDisconnected();
            owner.setStateMessage("Отключён вручную");
        }
    }

    public void onGameJoin(Object packet) {
        tryApplyJoinToWorld(packet);
        tryApplyJoinToBot(packet);
        tryPromoteToPlay("Получен GameJoin");
    }

    public void onPlayerRespawn(Object packet) {
        tryApplyRespawnToWorld(packet);
        tryApplyRespawnToBot(packet);
        tryPromoteToPlay("Respawn");
    }

    public void onPlayerPositionLook(Object packet) {
        tryApplyPositionLook(packet);
        tryPromoteToPlay("Position sync");
    }

    public void onPlayerAbilities(Object packet) {
        tryApplyAbilities(packet);
        tryPromoteToPlay(null);
    }

    public void onInventory(Object packet) {
        tryApplyInventory(packet);
        tryPromoteToPlay(null);
    }

    public void onSlotUpdate(Object packet) {
        tryApplySlot(packet);
        tryPromoteToPlay(null);
    }

    public void onChunkData(Object packet) {
        tryApplyChunk(packet);
        tryPromoteToPlay("Мир загружается");
    }

    public void onUnloadChunk(Object packet) {
        tryApplyUnloadChunk(packet);
    }

    public void onEntitySpawn(Object packet) {
        tryApplyEntitySpawn(packet);
    }

    public void onEntityTrackerUpdate(Object packet) {
        tryApplyEntityTracker(packet);
    }

    public void onEntityPosition(Object packet) {
        tryApplyEntityPosition(packet);
    }

    public void onEntityVelocity(Object packet) {
        tryApplyEntityVelocity(packet);
    }

    public void onEntitySetHeadYaw(Object packet) {
        tryApplyHeadYaw(packet);
    }

    public void onEntityAnimation(Object packet) {
        tryApplyEntityAnimation(packet);
    }

    public void onEntityStatus(Object packet) {
        tryApplyEntityStatus(packet);
    }

    public void onEntitiesDestroy(Object packet) {
        tryApplyEntitiesDestroy(packet);
    }

    public void onRemoveEntity(Object packet) {
        tryApplyEntitiesDestroy(packet);
    }

    public void onGameMessage(Object packet) {
        tryApplyGameMessage(packet);
    }

    public void onChatMessage(Object packet) {
        tryApplyGameMessage(packet);
    }

    public void onDisconnect(Object packet) {
        if (owner != null) {
            owner.markDisconnected();
            owner.setStateMessage(readReason(packet));
        }
        connection.handleDisconnection();
    }

    public void onDisconnected(Object packet) {
        onDisconnect(packet);
    }

    public static boolean dispatchPacket(BotClientPlayNetHandler handler, Object packet) {
        if (handler == null || packet == null) {
            return false;
        }

        String simple = packet.getClass().getSimpleName().toLowerCase();

        try {
            if (simple.contains("gamejoin") || simple.contains("login")) {
                handler.onGameJoin(packet);
                return true;
            }
            if (simple.contains("respawn")) {
                handler.onPlayerRespawn(packet);
                return true;
            }
            if (simple.contains("positionlook") || (simple.contains("position") && simple.contains("player"))) {
                handler.onPlayerPositionLook(packet);
                return true;
            }
            if (simple.contains("abilities")) {
                handler.onPlayerAbilities(packet);
                return true;
            }
            if (simple.contains("inventory")) {
                handler.onInventory(packet);
                return true;
            }
            if (simple.contains("slot")) {
                handler.onSlotUpdate(packet);
                return true;
            }
            if (simple.contains("chunkdata") || (simple.contains("chunk") && simple.contains("data"))) {
                handler.onChunkData(packet);
                return true;
            }
            if (simple.contains("unloadchunk")) {
                handler.onUnloadChunk(packet);
                return true;
            }
            if (simple.contains("spawn") && simple.contains("entity")) {
                handler.onEntitySpawn(packet);
                return true;
            }
            if (simple.contains("tracker")) {
                handler.onEntityTrackerUpdate(packet);
                return true;
            }
            if (simple.contains("velocity")) {
                handler.onEntityVelocity(packet);
                return true;
            }
            if (simple.contains("headyaw")) {
                handler.onEntitySetHeadYaw(packet);
                return true;
            }
            if (simple.contains("animation")) {
                handler.onEntityAnimation(packet);
                return true;
            }
            if (simple.contains("status") && simple.contains("entity")) {
                handler.onEntityStatus(packet);
                return true;
            }
            if (simple.contains("destroyentities") || simple.contains("removeentities") || simple.contains("removeentity")) {
                handler.onEntitiesDestroy(packet);
                return true;
            }
            if (simple.contains("chat") || simple.contains("message")) {
                handler.onGameMessage(packet);
                return true;
            }
            if (simple.contains("disconnect")) {
                handler.onDisconnect(packet);
                return true;
            }
        } catch (Throwable t) {
            if (handler.owner != null) {
                handler.owner.markFailed(shortError(t));
            }
            t.printStackTrace();
            return false;
        }

        return false;
    }

    private void tryPromoteToPlay(String message) {
        if (owner == null) {
            return;
        }

        syncOwnerRefs();

        if (!isConnectionOpen()) {
            return;
        }

        if (owner.getState() == Bot.State.CREATED || owner.getState() == Bot.State.CONNECTING) {
            owner.markLogin();
        }

        if (message != null && !message.isBlank()) {
            owner.setStateMessage(message);
        }

        if (connection != null && bot != null) {
            owner.attachNetwork(connection);
            owner.attachPlayer(bot);
            if (world != null) {
                owner.attachWorld(world);
            }
            if (botController != null) {
                owner.attachController(botController);
            }
            owner.attachConnection(this);
            owner.markPlay();
            if (owner.getStateMessage() == null || owner.getStateMessage().isBlank() || owner.getStateMessage().contains("configuration")) {
                owner.setStateMessage("Play phase");
            }
            BotManager.mergeIntoPending(owner);

            Bot merged = BotManager.findByName(owner.getNameString());
            if (merged != null && merged != owner) {
                owner = merged;
                syncOwnerRefs();
            }
            owner.markPlay();
            owner.touch();
        }
    }

    private void syncOwnerRefs() {
        if (owner == null) {
            return;
        }
        owner.attachNetwork(connection);
        if (world != null) {
            owner.attachWorld(world);
        }
        if (bot != null) {
            owner.attachPlayer(bot);
        }
        if (botController != null) {
            owner.attachController(botController);
        }
        owner.attachConnection(this);
    }

    private void tryApplyJoinToWorld(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onGameJoin", packet)) return;
        if (invokeCompatible(world, "handleGameJoin", packet)) return;
        invokeCompatible(world, "applyGameJoin", packet);
    }

    private void tryApplyJoinToBot(Object packet) {
        if (bot == null || packet == null) return;
        if (invokeCompatible(bot, "onGameJoin", packet)) return;
        if (invokeCompatible(bot, "handleGameJoin", packet)) return;
        invokeCompatible(bot, "applyGameJoin", packet);
    }

    private void tryApplyRespawnToWorld(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onRespawn", packet)) return;
        if (invokeCompatible(world, "handleRespawn", packet)) return;
        invokeCompatible(world, "applyRespawn", packet);
    }

    private void tryApplyRespawnToBot(Object packet) {
        if (bot == null || packet == null) return;
        if (invokeCompatible(bot, "onRespawn", packet)) return;
        if (invokeCompatible(bot, "handleRespawn", packet)) return;
        invokeCompatible(bot, "applyRespawn", packet);
    }

    private void tryApplyPositionLook(Object packet) {
        if (bot == null || packet == null) return;

        if (invokeCompatible(bot, "onPositionLook", packet)) return;
        if (invokeCompatible(bot, "handlePositionLook", packet)) return;

        Double x = extractDouble(packet, "getX", "x");
        Double y = extractDouble(packet, "getY", "y");
        Double z = extractDouble(packet, "getZ", "z");
        Float yaw = extractFloat(packet, "getYaw", "yaw");
        Float pitch = extractFloat(packet, "getPitch", "pitch");

        if (x != null && y != null && z != null) {
            invokePosition(bot, x, y, z);
        }
        if (yaw != null) {
            invokeAngle(bot, "setYaw", yaw);
            invokeAngle(bot, "setBodyYaw", yaw);
            invokeAngle(bot, "setHeadYaw", yaw);
        }
        if (pitch != null) {
            invokeAngle(bot, "setPitch", pitch);
        }
    }

    private void tryApplyAbilities(Object packet) {
        if (bot == null || packet == null) return;
        if (invokeCompatible(bot, "onAbilities", packet)) return;
        if (invokeCompatible(bot, "handleAbilities", packet)) return;
        invokeCompatible(bot, "applyAbilities", packet);
    }

    private void tryApplyInventory(Object packet) {
        if (bot == null || packet == null) return;
        if (invokeCompatible(bot, "onInventory", packet)) return;
        if (invokeCompatible(bot, "handleInventory", packet)) return;
        invokeCompatible(bot, "applyInventory", packet);
    }

    private void tryApplySlot(Object packet) {
        if (bot == null || packet == null) return;
        if (invokeCompatible(bot, "onSlotUpdate", packet)) return;
        if (invokeCompatible(bot, "handleSlotUpdate", packet)) return;
        invokeCompatible(bot, "applySlotUpdate", packet);
    }

    private void tryApplyChunk(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onChunkData", packet)) return;
        if (invokeCompatible(world, "handleChunkData", packet)) return;
        invokeCompatible(world, "applyChunkData", packet);
    }

    private void tryApplyUnloadChunk(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onUnloadChunk", packet)) return;
        if (invokeCompatible(world, "handleUnloadChunk", packet)) return;
        invokeCompatible(world, "applyUnloadChunk", packet);
    }

    private void tryApplyEntitySpawn(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntitySpawn", packet)) return;
        if (invokeCompatible(world, "handleEntitySpawn", packet)) return;
        invokeCompatible(world, "applyEntitySpawn", packet);
    }

    private void tryApplyEntityTracker(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityTrackerUpdate", packet)) return;
        if (invokeCompatible(world, "handleEntityTrackerUpdate", packet)) return;
        invokeCompatible(world, "applyEntityTrackerUpdate", packet);
    }

    private void tryApplyEntityPosition(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityPosition", packet)) return;
        if (invokeCompatible(world, "handleEntityPosition", packet)) return;
        invokeCompatible(world, "applyEntityPosition", packet);
    }

    private void tryApplyEntityVelocity(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityVelocity", packet)) return;
        if (invokeCompatible(world, "handleEntityVelocity", packet)) return;
        invokeCompatible(world, "applyEntityVelocity", packet);
    }

    private void tryApplyHeadYaw(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityHeadYaw", packet)) return;
        if (invokeCompatible(world, "handleEntityHeadYaw", packet)) return;
        invokeCompatible(world, "applyEntityHeadYaw", packet);
    }

    private void tryApplyEntityAnimation(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityAnimation", packet)) return;
        if (invokeCompatible(world, "handleEntityAnimation", packet)) return;
        invokeCompatible(world, "applyEntityAnimation", packet);
    }

    private void tryApplyEntityStatus(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntityStatus", packet)) return;
        if (invokeCompatible(world, "handleEntityStatus", packet)) return;
        invokeCompatible(world, "applyEntityStatus", packet);
    }

    private void tryApplyEntitiesDestroy(Object packet) {
        if (world == null || packet == null) return;
        if (invokeCompatible(world, "onEntitiesDestroy", packet)) return;
        if (invokeCompatible(world, "handleEntitiesDestroy", packet)) return;
        invokeCompatible(world, "applyEntitiesDestroy", packet);
    }

    private void tryApplyGameMessage(Object packet) {
        if (bot != null) {
            if (invokeCompatible(bot, "onGameMessage", packet)) return;
            if (invokeCompatible(bot, "handleGameMessage", packet)) return;
            if (invokeCompatible(bot, "applyGameMessage", packet)) return;
        }
        if (world != null) {
            if (invokeCompatible(world, "onGameMessage", packet)) return;
            if (invokeCompatible(world, "handleGameMessage", packet)) return;
            invokeCompatible(world, "applyGameMessage", packet);
        }
    }

    private void safeTick(Object target) {
        if (target == null) return;
        try {
            Method m = findMethod(target.getClass(), "tick");
            if (m == null || m.getParameterCount() != 0) return;
            m.setAccessible(true);
            m.invoke(target);
        } catch (Throwable ignored) {
        }
    }

    private static boolean invokeCompatible(Object target, String methodName, Object arg) {
        if (target == null || arg == null) return false;

        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != 1) continue;

                Class<?> p0 = method.getParameterTypes()[0];
                if (!p0.isInstance(arg) && !p0.isAssignableFrom(arg.getClass())) continue;

                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            }

            for (Method method : target.getClass().getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != 1) continue;

                Class<?> p0 = method.getParameterTypes()[0];
                if (!p0.isInstance(arg) && !p0.isAssignableFrom(arg.getClass())) continue;

                method.setAccessible(true);
                method.invoke(target, arg);
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static void invokePosition(Object target, double x, double y, double z) {
        tryInvoke(target, "setPos", double.class, double.class, double.class, x, y, z);
        tryInvoke(target, "setPosition", double.class, double.class, double.class, x, y, z);
        tryInvoke(target, "updatePosition", double.class, double.class, double.class, x, y, z);
    }

    private static void invokeAngle(Object target, String methodName, float value) {
        if (tryInvoke(target, methodName, float.class, value)) return;
        tryInvoke(target, methodName, double.class, (double) value);
    }

    private static boolean tryInvoke(Object target, String name, Class<?> p0, Object a0) {
        try {
            Method m = target.getClass().getMethod(name, p0);
            m.setAccessible(true);
            m.invoke(target, a0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean tryInvoke(Object target, String name, Class<?> p0, Class<?> p1, Class<?> p2, Object a0, Object a1, Object a2) {
        try {
            Method m = target.getClass().getMethod(name, p0, p1, p2);
            m.setAccessible(true);
            m.invoke(target, a0, a1, a2);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Double extractDouble(Object packet, String methodName, String fieldName) {
        Object value = tryCall(packet, methodName);
        if (!(value instanceof Number)) value = tryGetField(packet, fieldName);
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private static Float extractFloat(Object packet, String methodName, String fieldName) {
        Object value = tryCall(packet, methodName);
        if (!(value instanceof Number)) value = tryGetField(packet, fieldName);
        return value instanceof Number n ? n.floatValue() : null;
    }

    private static Object tryCall(Object obj, String methodName) {
        try {
            Method m = findMethod(obj.getClass(), methodName);
            if (m == null || m.getParameterCount() != 0) return null;
            m.setAccessible(true);
            return m.invoke(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryGetField(Object obj, String fieldName) {
        try {
            Field f = findField(obj.getClass(), fieldName);
            if (f == null) return null;
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        Class<?> cur = type;
        while (cur != null) {
            for (Method method : cur.getDeclaredMethods()) {
                if (method.getName().equals(name)) {
                    return method;
                }
            }
            cur = cur.getSuperclass();
        }

        for (Method method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> cur = type;
        while (cur != null) {
            try {
                return cur.getDeclaredField(name);
            } catch (Throwable ignored) {
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Object createQuitPacket() {
        for (String name : new String[]{
                "net.minecraft.network.packet.c2s.play.QuitC2SPacket",
                "net.minecraft.network.packet.c2s.common.CommonPongC2SPacket"
        }) {
            try {
                Class<?> c = Class.forName(name);
                for (var ctor : c.getConstructors()) {
                    if (ctor.getParameterCount() == 0) {
                        return ctor.newInstance();
                    }
                    if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == int.class) {
                        return ctor.newInstance(0);
                    }
                    if (ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == long.class) {
                        return ctor.newInstance(0L);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String readReason(Object reason) {
        if (reason == null) {
            return "Соединение закрыто";
        }
        try {
            Object text = tryCall(reason, "getString");
            if (text instanceof String s && !s.isBlank()) {
                return s;
            }
        } catch (Throwable ignored) {
        }
        return String.valueOf(reason);
    }

    private static String shortError(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return t.getClass().getSimpleName();
        }
        msg = msg.trim();
        if (msg.length() > 120) {
            msg = msg.substring(0, 120);
        }
        return t.getClass().getSimpleName() + ": " + msg;
    }
}