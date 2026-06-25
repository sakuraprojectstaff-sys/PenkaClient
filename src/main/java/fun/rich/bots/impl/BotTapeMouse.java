package fun.rich.bots.impl;

import fun.rich.bots.Bot;
import fun.rich.bots.player.BotController;
import fun.rich.bots.player.BotPlayer;
import fun.rich.bots.world.BotWorld;
import lombok.Getter;

public class BotTapeMouse {
    @Getter
    private final Bot bot;

    public BotTapeMouse(Bot bot) {
        this.bot = bot;
    }

    public void clickBotMouse() {
        if (bot == null) return;

        BotPlayer player = bot.getBotPlayer();
        BotController controller = bot.getBotController();
        Object connection = bot.getConnection();

        if (player == null || controller == null || connection == null) return;

        Object hitResult = invokeNoArgs(connection, "getCrosshairTarget");
        if (hitResult == null) hitResult = getField(connection, "crosshairTarget");
        if (hitResult == null) hitResult = invokeNoArgs(player, "getCrosshairTarget");
        if (hitResult == null) hitResult = getField(player, "crosshairTarget");
        if (hitResult == null) return;

        String type = resolveHitType(hitResult);

        if ("ENTITY".equals(type)) {
            Object entity = invokeNoArgs(hitResult, "getEntity");
            if (entity != null) {
                controller.attackEntity(player, entity);
            }
            return;
        }

        if ("BLOCK".equals(type)) {
            Object blockPos = invokeNoArgs(hitResult, "getBlockPos");
            if (blockPos == null) blockPos = invokeNoArgs(hitResult, "getPos");
            Object side = invokeNoArgs(hitResult, "getSide");
            controller.clickBlock(blockPos, side);
            return;
        }
    }

    public void rightClickBotMouse() {
        if (bot == null) return;

        BotPlayer player = bot.getBotPlayer();
        BotController controller = bot.getBotController();
        BotWorld world = bot.getBotWorld();
        Object connection = bot.getConnection();

        if (player == null || controller == null || connection == null) return;

        Object hitResult = invokeNoArgs(connection, "getCrosshairTarget");
        if (hitResult == null) hitResult = getField(connection, "crosshairTarget");
        if (hitResult == null) hitResult = invokeNoArgs(player, "getCrosshairTarget");
        if (hitResult == null) hitResult = getField(player, "crosshairTarget");

        Object mainHand = resolveHand("MAIN_HAND");
        Object offHand = resolveHand("OFF_HAND");

        if (hitResult != null) {
            String type = resolveHitType(hitResult);

            if ("ENTITY".equals(type)) {
                Object entity = invokeNoArgs(hitResult, "getEntity");
                if (entity != null) {
                    if (mainHand != null) {
                        if (controller.interactWithEntity(player, entity, hitResult, mainHand)) return;
                        if (controller.interactWithEntity(player, entity, mainHand)) return;
                    }
                    if (offHand != null) {
                        if (controller.interactWithEntity(player, entity, hitResult, offHand)) return;
                        controller.interactWithEntity(player, entity, offHand);
                    }
                }
                return;
            }

            if ("BLOCK".equals(type)) {
                if (mainHand != null && controller.interactBlock(player, world, mainHand, hitResult)) return;
                if (offHand != null && controller.interactBlock(player, world, offHand, hitResult)) return;
            }
        }

        if (mainHand != null && controller.processRightClick(player, world, mainHand)) return;
        if (offHand != null) controller.processRightClick(player, world, offHand);
    }

    private static String resolveHitType(Object hitResult) {
        if (hitResult == null) return "";

        Object type = invokeNoArgs(hitResult, "getType");
        if (type == null) type = getField(hitResult, "type");
        return type == null ? "" : String.valueOf(type).toUpperCase();
    }

    private static Object resolveHand(String name) {
        try {
            Class<?> handClass = Class.forName("net.minecraft.util.Hand");
            Object[] values = handClass.getEnumConstants();
            if (values == null) return null;
            for (Object value : values) {
                if (value != null && name.equalsIgnoreCase(String.valueOf(value))) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null || methodName == null) return null;

        try {
            for (var method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 0) continue;
                method.setAccessible(true);
                return method.invoke(target);
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> current = target.getClass();
            while (current != null) {
                for (var method : current.getDeclaredMethods()) {
                    if (!method.getName().equals(methodName) || method.getParameterCount() != 0) continue;
                    method.setAccessible(true);
                    return method.invoke(target);
                }
                current = current.getSuperclass();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static Object getField(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;

        try {
            Class<?> current = target.getClass();
            while (current != null) {
                try {
                    var field = current.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }
}