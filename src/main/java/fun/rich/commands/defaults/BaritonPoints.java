package fun.rich.commands.defaults;

import fun.rich.features.impl.misc.BaritonPVE;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.api.command.Command;
import fun.rich.utils.client.managers.api.command.argument.IArgConsumer;
import fun.rich.utils.client.managers.api.command.exception.CommandException;
import fun.rich.utils.client.managers.api.command.helpers.TabCompleteHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class BaritonPoints extends Command {

    private static final int SKLAD_POINTS_MAX = 4;

    private BlockPos rawPos1;
    private BlockPos rawPos2;

    private Integer yOffsetDown;
    private Integer yOffsetUp;

    private final BlockPos[] skladPoints = new BlockPos[SKLAD_POINTS_MAX];

    protected BaritonPoints() {
        super("pve");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        args.requireMax(5);

        if (!args.hasAny()) {
            usage();
            return;
        }

        String sub = args.getString();

        if (eq(sub, "pos1", "p1", "1")) {
            handlePosSet(1, args);
            return;
        }

        if (eq(sub, "pos2", "p2", "2")) {
            handlePosSet(2, args);
            return;
        }

        if (eq(sub, "y", "height")) {
            handleHeight(args);
            return;
        }

        if (eq(sub, "sklad", "storage", "chest")) {
            handleSklad(args);
            return;
        }

        if (eq(sub, "clear", "reset", "remove")) {
            rawPos1 = null;
            rawPos2 = null;
            yOffsetDown = null;
            yOffsetUp = null;

            Arrays.fill(skladPoints, null);

            BaritonPVE module = module();
            if (module != null) {
                module.clearArea();
                pushAllSkladPointsToModule(module);
            }

            logDirect("Точки, склад и смещения высоты очищены");
            return;
        }

        if (eq(sub, "help", "?")) {
            usage();
            return;
        }

        usage();
    }

    private void handlePosSet(int index, IArgConsumer args) throws CommandException {
        String action = args.hasAny() ? args.getString() : "set";
        if (!eq(action, "set")) {
            logDirect("Используй: .pve pos" + index + " set", Formatting.RED);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null) {
            logDirect("Игрок не найден", Formatting.RED);
            return;
        }

        if (index == 1) {
            rawPos1 = mc.player.getBlockPos().toImmutable();
            logDirect("Точка 1 сохранена (" + fmt(rawPos1) + ")");
        } else {
            rawPos2 = mc.player.getBlockPos().toImmutable();
            logDirect("Точка 2 сохранена (" + fmt(rawPos2) + ")");
        }

        syncPreviewToModule();

        if (rawPos1 != null && rawPos2 != null && !hasYOffset()) {
            logDirect("Теперь задай смещение высоты: .pve y set <d> <up>");
        }
    }

    private void handleHeight(IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            logDirect("Высота (смещения): .pve y d <off> | .pve y up <off> | .pve y set <d> <up>");
            return;
        }

        String sub = args.getString();

        if (eq(sub, "d", "down")) {
            if (!args.hasAny()) {
                logDirect("Используй: .pve y d <число>", Formatting.RED);
                return;
            }

            Integer value = parseIntSafe(args.getString());
            if (value == null) {
                logDirect("Некорректное число", Formatting.RED);
                return;
            }

            yOffsetDown = value;

            if (yOffsetUp != null && yOffsetDown > yOffsetUp) {
                int t = yOffsetDown;
                yOffsetDown = yOffsetUp;
                yOffsetUp = t;
            }

            if (yOffsetUp == null) {
                logDirect("d = " + yOffsetDown + ", теперь задай up: .pve y up <off>");
            } else {
                logDirect("Смещения высоты [" + yOffsetDown + ".." + yOffsetUp + "]");
            }

            syncPreviewToModule();
            return;
        }

        if (eq(sub, "up")) {
            if (!args.hasAny()) {
                logDirect("Используй: .pve y up <число>", Formatting.RED);
                return;
            }

            Integer value = parseIntSafe(args.getString());
            if (value == null) {
                logDirect("Некорректное число", Formatting.RED);
                return;
            }

            yOffsetUp = value;

            if (yOffsetDown != null && yOffsetDown > yOffsetUp) {
                int t = yOffsetDown;
                yOffsetDown = yOffsetUp;
                yOffsetUp = t;
            }

            if (yOffsetDown == null) {
                logDirect("up = " + yOffsetUp + ", теперь задай d: .pve y d <off>");
            } else {
                logDirect("Смещения высоты [" + yOffsetDown + ".." + yOffsetUp + "]");
            }

            syncPreviewToModule();
            return;
        }

        if (eq(sub, "set")) {
            if (!args.hasAny()) {
                logDirect("Используй: .pve y set <d> <up>", Formatting.RED);
                return;
            }

            Integer d = parseIntSafe(args.getString());
            if (d == null || !args.hasAny()) {
                logDirect("Используй: .pve y set <d> <up>", Formatting.RED);
                return;
            }

            Integer up = parseIntSafe(args.getString());
            if (up == null) {
                logDirect("Некорректные числа", Formatting.RED);
                return;
            }

            if (d > up) {
                int t = d;
                d = up;
                up = t;
            }

            yOffsetDown = d;
            yOffsetUp = up;

            logDirect("Смещения высоты установлены: [" + yOffsetDown + ".." + yOffsetUp + "]");

            syncPreviewToModule();
            return;
        }

        logDirect("Высота (смещения): .pve y d <off> | .pve y up <off> | .pve y set <d> <up>");
    }

    private void handleSklad(IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            printSkladPoints();
            return;
        }

        String sub = args.getString();

        if (eq(sub, "set")) {
            if (!args.hasAny()) {
                logDirect("Используй: .pve sklad set <1-4>", Formatting.RED);
                return;
            }

            Integer idx = parseIntSafe(args.getString());
            if (idx == null || idx < 1 || idx > SKLAD_POINTS_MAX) {
                logDirect("Номер точки склада должен быть от 1 до " + SKLAD_POINTS_MAX, Formatting.RED);
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                logDirect("Игрок не найден", Formatting.RED);
                return;
            }

            BlockPos pos = mc.player.getBlockPos().toImmutable();
            skladPoints[idx - 1] = pos;

            BaritonPVE module = module();
            if (module != null) {
                pushSkladPointToModule(module, idx, pos);
            }

            logDirect("Точка склада " + idx + " сохранена (" + fmt(pos) + ")");
            return;
        }

        if (eq(sub, "clear", "remove", "reset")) {
            if (!args.hasAny()) {
                Arrays.fill(skladPoints, null);

                BaritonPVE module = module();
                if (module != null) {
                    pushAllSkladPointsToModule(module);
                }

                logDirect("Все точки склада очищены");
                return;
            }

            Integer idx = parseIntSafe(args.getString());
            if (idx == null || idx < 1 || idx > SKLAD_POINTS_MAX) {
                logDirect("Номер точки склада должен быть от 1 до " + SKLAD_POINTS_MAX, Formatting.RED);
                return;
            }

            skladPoints[idx - 1] = null;

            BaritonPVE module = module();
            if (module != null) {
                pushSkladPointToModule(module, idx, null);
            }

            logDirect("Точка склада " + idx + " очищена");
            return;
        }

        if (eq(sub, "list", "show")) {
            printSkladPoints();
            return;
        }

        logDirect("Склад: .pve sklad set <1-4> | .pve sklad clear [1-4]");
    }

    private void printSkladPoints() {
        boolean any = false;
        for (int i = 0; i < SKLAD_POINTS_MAX; i++) {
            BlockPos p = skladPoints[i];
            if (p != null) {
                any = true;
                logDirect("Склад " + (i + 1) + ": " + fmt(p));
            }
        }
        if (!any) {
            logDirect("Точки склада не заданы");
        }
    }

    private void syncPreviewToModule() {
        BaritonPVE module = module();
        if (module == null) {
            return;
        }

        if (rawPos1 == null || rawPos2 == null) {
            return;
        }

        BlockPos[] area = buildEffectiveArea(false);
        if (area == null || area.length < 2) {
            return;
        }

        module.setPreviewArea(area[0], area[1]);

        int minX = Math.min(rawPos1.getX(), rawPos2.getX());
        int maxX = Math.max(rawPos1.getX(), rawPos2.getX());
        int minZ = Math.min(rawPos1.getZ(), rawPos2.getZ());
        int maxZ = Math.max(rawPos1.getZ(), rawPos2.getZ());
        int minY = Math.min(area[0].getY(), area[1].getY());
        int maxY = Math.max(area[0].getY(), area[1].getY());

        logDirect("Область обновлена XZ [" + minX + ".." + maxX + " | " + minZ + ".." + maxZ + "], Y [" + minY + ".." + maxY + "]");
    }

    private BlockPos[] buildEffectiveArea(boolean logErrors) {
        if (rawPos1 == null || rawPos2 == null) {
            return null;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) {
            if (logErrors) {
                logDirect("Мир не найден", Formatting.RED);
            }
            return null;
        }

        int rawMinY = Math.min(rawPos1.getY(), rawPos2.getY());
        int rawMaxY = Math.max(rawPos1.getY(), rawPos2.getY());

        int finalMinY;
        int finalMaxY;

        if (hasYOffset()) {
            finalMinY = rawMinY + Math.min(yOffsetDown, yOffsetUp);
            finalMaxY = rawMaxY + Math.max(yOffsetDown, yOffsetUp);
        } else {
            finalMinY = rawMinY;
            finalMaxY = rawMaxY;
        }

        finalMinY = clampY(finalMinY, mc.world.getBottomY(), mc.world.getTopYInclusive());
        finalMaxY = clampY(finalMaxY, mc.world.getBottomY(), mc.world.getTopYInclusive());

        if (finalMinY > finalMaxY) {
            int t = finalMinY;
            finalMinY = finalMaxY;
            finalMaxY = t;
        }

        BlockPos p1;
        BlockPos p2;

        if (rawPos1.getY() < rawPos2.getY()) {
            p1 = new BlockPos(rawPos1.getX(), finalMinY, rawPos1.getZ());
            p2 = new BlockPos(rawPos2.getX(), finalMaxY, rawPos2.getZ());
        } else if (rawPos1.getY() > rawPos2.getY()) {
            p1 = new BlockPos(rawPos1.getX(), finalMaxY, rawPos1.getZ());
            p2 = new BlockPos(rawPos2.getX(), finalMinY, rawPos2.getZ());
        } else {
            p1 = new BlockPos(rawPos1.getX(), finalMinY, rawPos1.getZ());
            p2 = new BlockPos(rawPos2.getX(), finalMaxY, rawPos2.getZ());
        }

        return new BlockPos[]{p1, p2};
    }

    private void pushAllSkladPointsToModule(BaritonPVE module) {
        for (int i = 0; i < SKLAD_POINTS_MAX; i++) {
            pushSkladPointToModule(module, i + 1, skladPoints[i]);
        }
    }

    private void pushSkladPointToModule(BaritonPVE module, int idx, BlockPos pos) {
        if (module == null) {
            return;
        }

        try {
            Method m = module.getClass().getMethod("setSkladPoint", int.class, BlockPos.class);
            m.invoke(module, idx, pos);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method m = module.getClass().getMethod("setStoragePoint", int.class, BlockPos.class);
            m.invoke(module, idx, pos);
        } catch (Throwable ignored) {
        }
    }

    private void usage() {
        logDirect(".pve pos1 set");
        logDirect(".pve pos2 set");
        logDirect(".pve y d <off>");
        logDirect(".pve y up <off>");
        logDirect(".pve y set <d> <up>");
        logDirect(".pve sklad set <1-4>");
        logDirect(".pve sklad clear [1-4]");
        logDirect(".pve clear");
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!args.hasAny()) {
            return Stream.empty();
        }

        String first = args.getString();

        if (!args.hasAny()) {
            return new TabCompleteHelper()
                    .sortAlphabetically()
                    .prepend("pos1", "pos2", "y", "sklad", "clear")
                    .filterPrefix(first)
                    .stream();
        }

        if (eq(first, "pos1", "p1", "1", "pos2", "p2", "2")) {
            String second = args.getString();
            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .prepend("set")
                        .filterPrefix(second)
                        .stream();
            }
        }

        if (eq(first, "y", "height")) {
            String second = args.getString();
            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .prepend("d", "up", "set")
                        .filterPrefix(second)
                        .stream();
            }
        }

        if (eq(first, "sklad", "storage", "chest")) {
            String second = args.getString();

            if (!args.hasAny()) {
                return new TabCompleteHelper()
                        .prepend("set", "clear", "list")
                        .filterPrefix(second)
                        .stream();
            }

            if (eq(second, "set", "clear")) {
                String third = args.getString();
                if (!args.hasAny()) {
                    return new TabCompleteHelper()
                            .prepend("1", "2", "3", "4")
                            .filterPrefix(third)
                            .stream();
                }
            }
        }

        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Точки PVE, смещения высоты и точки склада";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Выделение области для PVE по 2 точкам и смещениям высоты.",
                "Отдельно можно сохранить точки склада (сундуки/маршрут к складу).",
                "",
                "Использование:",
                "> pve pos1 set - Ставит первую точку на текущем блоке.",
                "> pve pos2 set - Ставит вторую точку на текущем блоке.",
                "> pve y d <off> - Нижнее смещение по Y.",
                "> pve y up <off> - Верхнее смещение по Y.",
                "> pve y set <d> <up> - Устанавливает оба смещения.",
                "> pve sklad set <1-4> - Ставит точку склада на текущем блоке.",
                "> pve sklad clear [1-4] - Очищает все/одну точку склада.",
                "> pve clear - Очищает точки, склад и смещения."
        );
    }

    private BaritonPVE module() {
        try {
            return Instance.get(BaritonPVE.class);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean hasYOffset() {
        return yOffsetDown != null && yOffsetUp != null;
    }

    private String fmt(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return null;
        }
    }

    private int clampY(int y, int min, int max) {
        if (y < min) return min;
        if (y > max) return max;
        return y;
    }

    private boolean eq(String value, String... variants) {
        for (String v : variants) {
            if (v.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}