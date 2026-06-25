package fun.rich.features.impl.render;

import fun.rich.events.keyboard.KeyEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.DrawEvent;
import fun.rich.events.render.WorldLoadEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.math.projection.Projection;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WayPoints extends Module {
    static final float PICK_RANGE = 64.0F;
    static final float RENDER_RANGE = 256.0F;
    static final int DEFAULT_ARROW_COLOR = 0xFFFF4DFF;
    static final Identifier TRIANGLE = Identifier.of("mre", "textures/triangle2.png");

    static final int GLOWING_FLAG_INDEX = 6;
    static final String WAYPOINT_GLOW_TEAM = "rich_waypoints_glow";

    static Method entitySetFlagMethod;
    static Method entityGetFlagMethod;
    static boolean entityFlagMethodsSearched;

    static Method scoreboardGetTeamMethod;
    static Method scoreboardAddTeamMethod;
    static Method scoreboardGetScoreHolderTeamMethod;
    static Method scoreboardAddScoreHolderToTeamMethod;
    static Method scoreboardRemoveScoreHolderFromTeamMethod;
    static Method teamSetColorMethod;
    static Method teamGetNameMethod;
    static boolean scoreboardReflectionSearched;

    BindSetting waypointBind = new BindSetting("Вайпоинт", "Поставить/удалить вайпоинт");
    SliderSettings arrowSize = new SliderSettings("Размер стрелки", "16.0");
    ColorSetting color = new ColorSetting("Цвет", "Цвет метки").value(DEFAULT_ARROW_COLOR);

    @NonFinal boolean latch = false;

    List<Waypoint> waypoints = new ArrayList<>();
    List<SmoothPos> smoothEntityPos = new ArrayList<>();

    Map<Integer, Boolean> originalGlowFlags = new HashMap<>();
    Set<Integer> currentGlowTargets = new HashSet<>();
    Map<Integer, String> originalGlowTeamNames = new HashMap<>();
    Map<Integer, String> glowScoreHolderNames = new HashMap<>();

    public WayPoints() {
        super("WayPoints", "WayPoints", ModuleCategory.RENDER);
        applySliderMeta(arrowSize, 6.0F, 64.0F, 1.0F);
        setup(waypointBind, arrowSize, color);
    }

    @Override
    public void deactivate() {
        restoreAllGlowFlags();
        waypoints.clear();
        smoothEntityPos.clear();
        latch = false;
        super.deactivate();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        restoreAllGlowFlags();
        waypoints.clear();
        smoothEntityPos.clear();
        latch = false;
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.world == null || mc.player == null) {
            restoreAllGlowFlags();
            smoothEntityPos.clear();
            return;
        }

        Iterator<Waypoint> it = waypoints.iterator();
        while (it.hasNext()) {
            Waypoint wp = it.next();
            if (wp.type != Type.ENTITY) continue;

            Entity entity = mc.world.getEntityById(wp.entityId);
            if (entity == null || !entity.isAlive() || entity == mc.player) {
                removeSmoothPos(wp.entityId);
                it.remove();
            }
        }

        for (Waypoint wp : waypoints) {
            if (wp.type != Type.ENTITY) continue;
            if (!eq(wp.dimension, currentDimensionId())) continue;

            Entity entity = mc.world.getEntityById(wp.entityId);
            if (entity == null || entity == mc.player || !entity.isAlive()) continue;

            SmoothPos sp = getSmoothPos(wp.entityId);
            Vec3d target = entity.getPos();

            if (sp == null) {
                smoothEntityPos.add(new SmoothPos(wp.entityId, target, target));
            } else {
                sp.prev = sp.curr;
                sp.curr = lerpVec(sp.curr, target, 0.45);
            }
        }

        updateGlowWaypointTargets();
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.player == null || mc.world == null) return;

        int key = waypointBind.getKey();
        if (key == 0) return;

        boolean down = e.isKeyDown(key);

        if (down) {
            if (latch) return;
            latch = true;

            PickResult pick = pickWaypointTarget(PICK_RANGE);
            if (pick == null) return;

            if (pick.entity != null) {
                if (pick.entity != mc.player) {
                    toggleEntityWaypoint(pick.entity);
                }
                return;
            }

            if (pick.blockPos != null) {
                toggleBlockWaypoint(pick.blockPos);
            }
        } else {
            latch = false;
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (waypoints.isEmpty()) return;

        MatrixStack stack = getStack(e);
        if (stack == null) return;

        Render3D.setLastWorldSpaceMatrix(stack.peek().copy());

        String currentDim = currentDimensionId();
        int renderColor = safeColor();

        for (Waypoint wp : waypoints) {
            if (wp.type != Type.BLOCK) continue;
            if (!eq(wp.dimension, currentDim)) continue;

            VoxelShape shape = mc.world.getBlockState(wp.blockPos).getOutlineShape(mc.world, wp.blockPos);
            if (shape == null || shape.isEmpty()) continue;

            Render3D.drawShapeAlternative(wp.blockPos, shape, renderColor, 2.0f, true, true);
        }

        updateGlowWaypointTargets();
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (waypoints.isEmpty()) return;

        DrawContext context = e.getDrawContext();

        Vec3d camPos = mc.getEntityRenderDispatcher().camera.getPos();
        double maxDistSq = RENDER_RANGE * RENDER_RANGE;
        float pt = getTickDeltaSafe();
        float size = clamp(readNumber(arrowSize, 16.0F), 6.0F, 64.0F);
        String currentDim = currentDimensionId();
        long time = mc.world.getTime();
        int baseColor = safeColor();

        for (Waypoint wp : waypoints) {
            if (!eq(wp.dimension, currentDim)) continue;

            if (wp.type == Type.BLOCK) {
                Vec3d anchor = Vec3d.ofCenter(wp.blockPos).add(0.0, 1.08, 0.0);
                double distSq = camPos.squaredDistanceTo(anchor);
                if (distSq > maxDistSq) continue;
                if (!Projection.canSee(anchor)) continue;

                Vec3d screen = Projection.worldSpaceToScreenSpace(anchor);
                if (!validScreenPoint(screen)) continue;

                float fade = distanceFade((float) Math.sqrt(distSq), RENDER_RANGE);
                if (fade <= 0.001f) continue;

                drawArrow2D((float) screen.x, (float) screen.y, size, fade, baseColor);
                continue;
            }

            Entity entity = mc.world.getEntityById(wp.entityId);
            if (entity == null || !entity.isAlive() || entity == mc.player) continue;

            Vec3d pos = getSmoothLerpedEntityPos(wp.entityId, entity, pt);
            Box box = buildEntityBox(entity, pos);

            double distSq = camPos.squaredDistanceTo(box.getCenter());
            if (distSq > maxDistSq) continue;

            float fade = distanceFade((float) Math.sqrt(distSq), RENDER_RANGE);
            if (fade <= 0.001f) continue;

            double bob = Math.sin((time + pt) * 0.16 + wp.entityId * 0.35) * 0.10;
            Vec3d anchor = new Vec3d(box.getCenter().x, box.maxY + 0.72 + bob, box.getCenter().z);
            if (!Projection.canSee(anchor)) continue;

            Vec3d screen = Projection.worldSpaceToScreenSpace(anchor);
            if (!validScreenPoint(screen)) continue;

            drawArrow2D((float) screen.x, (float) screen.y, size, fade, baseColor);
        }
    }

    void drawArrow2D(float x, float y, float size, float fade, int baseColor) {
        int finalColor = alphaColor(baseColor, 0.98f * fade);

        MatrixStack ms = new MatrixStack();
        ms.translate(x, y, 0.0f);
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));
        Render2D.queueTexture(ms, TRIANGLE, -size * 0.5f, -size * 0.5f, size, size, finalColor);
    }

    void updateGlowWaypointTargets() {
        currentGlowTargets.clear();

        if (mc.world == null || mc.player == null) {
            restoreMissingGlowTargets();
            return;
        }

        Object scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) {
            restoreMissingGlowTargets();
            return;
        }

        syncWaypointGlowTeamColor(scoreboard);

        String currentDim = currentDimensionId();
        Vec3d localPos = mc.player.getPos();
        double maxDistanceSq = RENDER_RANGE * RENDER_RANGE;

        for (Waypoint wp : waypoints) {
            if (wp.type != Type.ENTITY) continue;
            if (!eq(wp.dimension, currentDim)) continue;

            Entity entity = mc.world.getEntityById(wp.entityId);
            if (entity == null || entity == mc.player) continue;
            if (!entity.isAlive()) continue;

            if (localPos.squaredDistanceTo(entity.getPos()) > maxDistanceSq) continue;

            markGlow(scoreboard, entity);
        }

        restoreMissingGlowTargets();
    }

    void markGlow(Object scoreboard, Entity entity) {
        int id = entity.getId();
        currentGlowTargets.add(id);

        originalGlowFlags.putIfAbsent(id, getGlowFlag(entity));
        setGlowFlag(entity, true);

        String scoreHolder = getScoreHolderName(entity);
        if (scoreHolder == null || scoreHolder.isEmpty()) return;

        glowScoreHolderNames.put(id, scoreHolder);
        originalGlowTeamNames.putIfAbsent(id, getScoreHolderTeamName(scoreboard, scoreHolder));

        assignScoreHolderToWaypointGlowTeam(scoreboard, scoreHolder);
    }

    void restoreMissingGlowTargets() {
        if (mc.world == null) {
            originalGlowFlags.clear();
            currentGlowTargets.clear();
            originalGlowTeamNames.clear();
            glowScoreHolderNames.clear();
            return;
        }

        Object scoreboard = mc.world.getScoreboard();

        Iterator<Map.Entry<Integer, Boolean>> iterator = originalGlowFlags.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Boolean> entry = iterator.next();
            int id = entry.getKey();
            if (currentGlowTargets.contains(id)) continue;

            Entity entity = mc.world.getEntityById(id);
            if (entity != null) {
                setGlowFlag(entity, entry.getValue());
            }

            String scoreHolder = glowScoreHolderNames.remove(id);
            if (scoreHolder != null && scoreboard != null) {
                restoreScoreHolderTeam(scoreboard, scoreHolder, originalGlowTeamNames.get(id));
            }
            originalGlowTeamNames.remove(id);

            iterator.remove();
        }
    }

    void restoreAllGlowFlags() {
        if (mc.world != null) {
            Object scoreboard = mc.world.getScoreboard();

            for (Map.Entry<Integer, Boolean> entry : originalGlowFlags.entrySet()) {
                Entity entity = mc.world.getEntityById(entry.getKey());
                if (entity != null) {
                    setGlowFlag(entity, entry.getValue());
                }
            }

            if (scoreboard != null) {
                for (Map.Entry<Integer, String> entry : glowScoreHolderNames.entrySet()) {
                    String holder = entry.getValue();
                    String originalTeam = originalGlowTeamNames.get(entry.getKey());
                    restoreScoreHolderTeam(scoreboard, holder, originalTeam);
                }
            }
        }

        originalGlowFlags.clear();
        currentGlowTargets.clear();
        originalGlowTeamNames.clear();
        glowScoreHolderNames.clear();
    }

    void syncWaypointGlowTeamColor(Object scoreboard) {
        Object team = getOrCreateTeam(scoreboard, WAYPOINT_GLOW_TEAM);
        if (team == null) return;

        Formatting formatting = nearestFormatting(safeColor());
        setTeamColor(team, formatting);
    }

    void assignScoreHolderToWaypointGlowTeam(Object scoreboard, String scoreHolder) {
        Object team = getOrCreateTeam(scoreboard, WAYPOINT_GLOW_TEAM);
        if (team == null) return;

        removeScoreHolderFromTeam(scoreboard, scoreHolder, team);
        addScoreHolderToTeam(scoreboard, scoreHolder, team);
    }

    void restoreScoreHolderTeam(Object scoreboard, String scoreHolder, String originalTeamName) {
        Object glowTeam = getOrCreateTeam(scoreboard, WAYPOINT_GLOW_TEAM);
        if (glowTeam != null) {
            removeScoreHolderFromTeam(scoreboard, scoreHolder, glowTeam);
        }

        if (originalTeamName != null && !originalTeamName.isEmpty()) {
            Object originalTeam = getTeam(scoreboard, originalTeamName);
            if (originalTeam != null) {
                addScoreHolderToTeam(scoreboard, scoreHolder, originalTeam);
            }
        }
    }

    @Nullable
    String getScoreHolderName(Entity entity) {
        try {
            Method m = Entity.class.getMethod("getNameForScoreboard");
            Object v = m.invoke(entity);
            if (v instanceof String s) return s;
        } catch (Throwable ignored) {
        }

        try {
            return entity.getUuidAsString();
        } catch (Throwable ignored) {
        }

        try {
            return entity.getName().getString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    String getScoreHolderTeamName(Object scoreboard, String scoreHolder) {
        ensureScoreboardReflection();
        if (scoreboard == null || scoreHolder == null) return null;
        try {
            if (scoreboardGetScoreHolderTeamMethod != null) {
                Object team = scoreboardGetScoreHolderTeamMethod.invoke(scoreboard, scoreHolder);
                return getTeamName(team);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    Object getTeam(Object scoreboard, String name) {
        ensureScoreboardReflection();
        if (scoreboard == null || name == null) return null;
        try {
            if (scoreboardGetTeamMethod != null) {
                return scoreboardGetTeamMethod.invoke(scoreboard, name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    Object getOrCreateTeam(Object scoreboard, String name) {
        Object team = getTeam(scoreboard, name);
        if (team != null) return team;

        ensureScoreboardReflection();
        try {
            if (scoreboardAddTeamMethod != null) {
                return scoreboardAddTeamMethod.invoke(scoreboard, name);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    void addScoreHolderToTeam(Object scoreboard, String scoreHolder, Object team) {
        ensureScoreboardReflection();
        if (scoreboard == null || scoreHolder == null || team == null) return;
        try {
            if (scoreboardAddScoreHolderToTeamMethod != null) {
                scoreboardAddScoreHolderToTeamMethod.invoke(scoreboard, scoreHolder, team);
            }
        } catch (Throwable ignored) {
        }
    }

    void removeScoreHolderFromTeam(Object scoreboard, String scoreHolder, Object team) {
        ensureScoreboardReflection();
        if (scoreboard == null || scoreHolder == null || team == null) return;
        try {
            if (scoreboardRemoveScoreHolderFromTeamMethod != null) {
                scoreboardRemoveScoreHolderFromTeamMethod.invoke(scoreboard, scoreHolder, team);
            }
        } catch (Throwable ignored) {
        }
    }

    void setTeamColor(Object team, Formatting formatting) {
        ensureScoreboardReflection();
        if (team == null || formatting == null) return;
        try {
            if (teamSetColorMethod != null) {
                teamSetColorMethod.invoke(team, formatting);
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    String getTeamName(Object team) {
        ensureScoreboardReflection();
        if (team == null) return null;
        try {
            if (teamGetNameMethod != null) {
                Object v = teamGetNameMethod.invoke(team);
                if (v instanceof String s) return s;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static void ensureScoreboardReflection() {
        if (scoreboardReflectionSearched) return;
        scoreboardReflectionSearched = true;

        try {
            Class<?> scoreboardClass = Class.forName("net.minecraft.scoreboard.Scoreboard");
            Class<?> teamClass = Class.forName("net.minecraft.scoreboard.Team");

            for (Method m : scoreboardClass.getMethods()) {
                Class<?>[] p = m.getParameterTypes();

                if (m.getReturnType() == teamClass && p.length == 1 && p[0] == String.class && m.getName().toLowerCase(Locale.ROOT).contains("getteam")) {
                    scoreboardGetTeamMethod = m;
                }

                if (m.getReturnType() == teamClass && p.length == 1 && p[0] == String.class && m.getName().toLowerCase(Locale.ROOT).contains("addteam")) {
                    scoreboardAddTeamMethod = m;
                }

                if (m.getReturnType() == teamClass && p.length == 1 && p[0] == String.class && m.getName().toLowerCase(Locale.ROOT).contains("scoreholderteam")) {
                    scoreboardGetScoreHolderTeamMethod = m;
                }

                if (m.getReturnType() == boolean.class && p.length == 2 && p[0] == String.class && p[1] == teamClass && m.getName().toLowerCase(Locale.ROOT).contains("addscoreholder")) {
                    scoreboardAddScoreHolderToTeamMethod = m;
                }

                if (p.length == 2 && p[0] == String.class && p[1] == teamClass && m.getName().toLowerCase(Locale.ROOT).contains("removescoreholder")) {
                    scoreboardRemoveScoreHolderFromTeamMethod = m;
                }
            }

            for (Method m : teamClass.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getReturnType() == void.class && p.length == 1 && p[0] == Formatting.class && m.getName().toLowerCase(Locale.ROOT).contains("setcolor")) {
                    teamSetColorMethod = m;
                }
                if (m.getReturnType() == String.class && p.length == 0 && m.getName().equals("getName")) {
                    teamGetNameMethod = m;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    static boolean getGlowFlag(Entity entity) {
        ensureEntityFlagMethods();
        try {
            if (entityGetFlagMethod != null) {
                Object value = entityGetFlagMethod.invoke(entity, GLOWING_FLAG_INDEX);
                if (value instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {
        }
        return entity.isGlowing();
    }

    static void setGlowFlag(Entity entity, boolean state) {
        ensureEntityFlagMethods();
        try {
            if (entitySetFlagMethod != null) {
                entitySetFlagMethod.invoke(entity, GLOWING_FLAG_INDEX, state);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            entity.setGlowing(state);
        } catch (Throwable ignored) {
        }
    }

    static void ensureEntityFlagMethods() {
        if (entityFlagMethodsSearched) return;
        entityFlagMethodsSearched = true;

        try {
            for (Method method : Entity.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 2 && params[0] == int.class && params[1] == boolean.class && method.getReturnType() == void.class) {
                    entitySetFlagMethod = method;
                    entitySetFlagMethod.setAccessible(true);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Method method : Entity.class.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1 && params[0] == int.class && method.getReturnType() == boolean.class) {
                    entityGetFlagMethod = method;
                    entityGetFlagMethod.setAccessible(true);
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    Formatting nearestFormatting(int color) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;

        Formatting best = Formatting.WHITE;
        double bestDist = Double.MAX_VALUE;

        for (Formatting f : Formatting.values()) {
            Integer rgb = getFormattingRgb(f);
            if (rgb == null) continue;

            int fr = (rgb >> 16) & 255;
            int fg = (rgb >> 8) & 255;
            int fb = rgb & 255;

            int dr = r - fr;
            int dg = g - fg;
            int db = b - fb;
            double dist = dr * dr + dg * dg + db * db;

            if (dist < bestDist) {
                bestDist = dist;
                best = f;
            }
        }

        return best;
    }

    Integer getFormattingRgb(Formatting f) {
        try {
            Method m = Formatting.class.getMethod("getColorValue");
            Object v = m.invoke(f);
            if (v instanceof Integer i) return i;
        } catch (Throwable ignored) {
        }

        return switch (f) {
            case BLACK -> 0x000000;
            case DARK_BLUE -> 0x0000AA;
            case DARK_GREEN -> 0x00AA00;
            case DARK_AQUA -> 0x00AAAA;
            case DARK_RED -> 0xAA0000;
            case DARK_PURPLE -> 0xAA00AA;
            case GOLD -> 0xFFAA00;
            case GRAY -> 0xAAAAAA;
            case DARK_GRAY -> 0x555555;
            case BLUE -> 0x5555FF;
            case GREEN -> 0x55FF55;
            case AQUA -> 0x55FFFF;
            case RED -> 0xFF5555;
            case LIGHT_PURPLE -> 0xFF55FF;
            case YELLOW -> 0xFFFF55;
            case WHITE -> 0xFFFFFF;
            default -> null;
        };
    }

    boolean validScreenPoint(Vec3d p) {
        return p != null
                && Double.isFinite(p.x)
                && Double.isFinite(p.y)
                && Double.isFinite(p.z)
                && p.z > -1.0
                && p.z < 1.0;
    }

    Vec3d getSmoothLerpedEntityPos(int entityId, Entity entity, float pt) {
        SmoothPos sp = getSmoothPos(entityId);
        if (sp != null) {
            return lerpVec(sp.prev, sp.curr, pt);
        }
        return entity.getLerpedPos(pt);
    }

    Box buildEntityBox(Entity entity, Vec3d pos) {
        Box raw = entity.getBoundingBox();
        return new Box(
                raw.minX - entity.getX() + pos.x,
                raw.minY - entity.getY() + pos.y,
                raw.minZ - entity.getZ() + pos.z,
                raw.maxX - entity.getX() + pos.x,
                raw.maxY - entity.getY() + pos.y,
                raw.maxZ - entity.getZ() + pos.z
        );
    }

    int safeColor() {
        try {
            if (color != null) return color.getColor();
        } catch (Throwable ignored) {
        }
        return DEFAULT_ARROW_COLOR;
    }

    PickResult pickWaypointTarget(float range) {
        if (mc.player == null || mc.world == null) return null;

        Vec3d start = mc.player.getCameraPosVec(1.0F);
        Vec3d dir = mc.player.getRotationVec(1.0F);
        Vec3d end = start.add(dir.multiply(range));

        BlockHitResult blockHit = mc.world.raycast(new RaycastContext(
                start,
                end,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        double bestBlockSq = blockHit != null && blockHit.getType() != HitResult.Type.MISS
                ? start.squaredDistanceTo(blockHit.getPos())
                : Double.MAX_VALUE;

        Entity bestEntity = null;
        double bestEntitySq = bestBlockSq;

        Box searchBox = mc.player.getBoundingBox().stretch(dir.multiply(range)).expand(1.5);

        for (Entity entity : mc.world.getOtherEntities(mc.player, searchBox, this::canPickEntity)) {
            Box box = entity.getBoundingBox().expand(0.25);
            Optional<Vec3d> hit = box.raycast(start, end);

            double distSq;
            if (box.contains(start)) {
                distSq = 0.0;
            } else if (hit.isPresent()) {
                distSq = start.squaredDistanceTo(hit.get());
            } else {
                continue;
            }

            if (distSq < bestEntitySq) {
                bestEntitySq = distSq;
                bestEntity = entity;
            }
        }

        if (bestEntity != null) {
            return new PickResult(bestEntity, null);
        }

        if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
            return new PickResult(null, blockHit.getBlockPos());
        }

        return null;
    }

    boolean canPickEntity(Entity entity) {
        return entity != null && entity.isAlive() && entity != mc.player;
    }

    void toggleBlockWaypoint(BlockPos pos) {
        String dim = currentDimensionId();

        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            if (wp.type != Type.BLOCK) continue;
            if (!eq(wp.dimension, dim)) continue;
            if (!wp.blockPos.equals(pos)) continue;

            waypoints.remove(i);
            return;
        }

        waypoints.add(Waypoint.block(pos, dim));
    }

    void toggleEntityWaypoint(Entity entity) {
        String dim = currentDimensionId();

        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            if (wp.type != Type.ENTITY) continue;
            if (!eq(wp.dimension, dim)) continue;
            if (wp.entityId != entity.getId()) continue;

            removeSmoothPos(wp.entityId);
            waypoints.remove(i);
            return;
        }

        waypoints.add(Waypoint.entity(entity.getId(), dim));

        SmoothPos sp = getSmoothPos(entity.getId());
        if (sp == null) {
            Vec3d p = entity.getPos();
            smoothEntityPos.add(new SmoothPos(entity.getId(), p, p));
        }
    }

    SmoothPos getSmoothPos(int entityId) {
        for (SmoothPos sp : smoothEntityPos) {
            if (sp.entityId == entityId) return sp;
        }
        return null;
    }

    void removeSmoothPos(int entityId) {
        smoothEntityPos.removeIf(sp -> sp.entityId == entityId);
    }

    float distanceFade(float dist, float maxDist) {
        float start = 8.0f;
        float end = Math.max(start + 1.0f, maxDist);
        float tt = (dist - start) / (end - start);
        tt = clamp(tt, 0.0f, 1.0f);
        tt = tt * tt * (3.0f - 2.0f * tt);
        return 1.0f - tt;
    }

    float getTickDeltaSafe() {
        try {
            Object rtc = mc.getRenderTickCounter();
            if (rtc == null) return 1.0f;

            for (String name : new String[]{"getTickDelta", "getTickProgress"}) {
                try {
                    Method m = rtc.getClass().getMethod(name, boolean.class);
                    Object v = m.invoke(rtc, true);
                    if (v instanceof Number n) return clamp(n.floatValue(), 0.0f, 1.0f);
                } catch (Throwable ignored) {
                }

                try {
                    Method m = rtc.getClass().getMethod(name);
                    Object v = m.invoke(rtc);
                    if (v instanceof Number n) return clamp(n.floatValue(), 0.0f, 1.0f);
                } catch (Throwable ignored) {
                }
            }

            for (String field : new String[]{"tickDelta", "tickProgress"}) {
                try {
                    Field f = rtc.getClass().getDeclaredField(field);
                    f.setAccessible(true);
                    Object v = f.get(rtc);
                    if (v instanceof Number n) return clamp(n.floatValue(), 0.0f, 1.0f);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }

    MatrixStack getStack(Object e) {
        try {
            Method m = e.getClass().getMethod("getStack");
            Object v = m.invoke(e);
            if (v instanceof MatrixStack ms) return ms;
        } catch (Throwable ignored) {
        }

        try {
            Field f = e.getClass().getDeclaredField("stack");
            f.setAccessible(true);
            Object v = f.get(e);
            if (v instanceof MatrixStack ms) return ms;
        } catch (Throwable ignored) {
        }

        return null;
    }

    String currentDimensionId() {
        try {
            return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        } catch (Throwable ignored) {
        }
        return "";
    }

    static int alphaColor(int color, float alpha) {
        alpha = clamp(alpha, 0.0F, 1.0F);
        int a = (color >>> 24) & 255;
        if (a == 0) a = 255;
        int na = (int) (a * alpha);
        return (color & 0x00FFFFFF) | (na << 24);
    }

    static boolean eq(String a, String b) {
        return Objects.equals(a, b);
    }

    static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    static Vec3d lerpVec(Vec3d a, Vec3d b, double t) {
        t = MathHelper.clamp(t, 0.0, 1.0);
        return new Vec3d(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
    }

    static void applySliderMeta(Object setting, float min, float max, float inc) {
        setFloatAny(setting, "min", min);
        setFloatAny(setting, "max", max);
        setFloatAny(setting, "minValue", min);
        setFloatAny(setting, "maxValue", max);
        setFloatAny(setting, "minimum", min);
        setFloatAny(setting, "maximum", max);

        invokeFloat(setting, "setMin", min);
        invokeFloat(setting, "setMax", max);

        setFloatAny(setting, "inc", inc);
        setFloatAny(setting, "step", inc);
        setFloatAny(setting, "increment", inc);

        invokeFloat(setting, "setInc", inc);
        invokeFloat(setting, "setStep", inc);
        invokeFloat(setting, "setIncrement", inc);
    }

    static void invokeFloat(Object obj, String method, float v) {
        if (obj == null) return;

        try {
            Method m = obj.getClass().getMethod(method, float.class);
            m.invoke(obj, v);
        } catch (Throwable ignored) {
        }

        try {
            Method m = obj.getClass().getMethod(method, double.class);
            m.invoke(obj, (double) v);
        } catch (Throwable ignored) {
        }
    }

    static void setFloatAny(Object obj, String field, float v) {
        if (obj == null) return;

        try {
            Field f = obj.getClass().getDeclaredField(field);
            f.setAccessible(true);
            if (f.getType() == float.class) f.setFloat(obj, v);
            else if (f.getType() == double.class) f.setDouble(obj, v);
        } catch (Throwable ignored) {
        }
    }

    static float readNumber(Object setting, float def) {
        if (setting == null) return def;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

        try {
            Field f = setting.getClass().getDeclaredField("value");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

        try {
            Field f = setting.getClass().getDeclaredField("current");
            f.setAccessible(true);
            Object v = f.get(setting);
            if (v instanceof Number) return ((Number) v).floatValue();
            if (v instanceof String) return parseFloatSafe((String) v, def);
        } catch (Throwable ignored) {
        }

        return def;
    }

    static float parseFloatSafe(String s, float def) {
        if (s == null) return def;
        try {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return def;
            return Float.parseFloat(t);
        } catch (Throwable ignored) {
        }
        return def;
    }

    enum Type {
        BLOCK,
        ENTITY
    }

    record PickResult(Entity entity, BlockPos blockPos) {
    }

    record Waypoint(Type type, BlockPos blockPos, int entityId, String dimension) {
        static Waypoint block(BlockPos pos, String dimension) {
            return new Waypoint(Type.BLOCK, pos, -1, dimension);
        }

        static Waypoint entity(int entityId, String dimension) {
            return new Waypoint(Type.ENTITY, null, entityId, dimension);
        }
    }

    static final class SmoothPos {
        int entityId;
        Vec3d prev;
        Vec3d curr;

        SmoothPos(int entityId, Vec3d prev, Vec3d curr) {
            this.entityId = entityId;
            this.prev = prev;
            this.curr = curr;
        }
    }
}