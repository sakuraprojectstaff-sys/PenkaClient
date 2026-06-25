package fun.rich.features.impl.render;

import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import fun.rich.utils.client.Instance;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.*;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.common.repository.friend.FriendUtils;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.client.packet.network.Network;
import fun.rich.utils.math.projection.Projection;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.DrawEvent;
import fun.rich.events.render.WorldLoadEvent;
import fun.rich.features.impl.combat.AntiBot;
import fun.rich.utils.math.calc.Calculate;

import java.awt.*;
import java.lang.Math;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Esp extends Module {
    private static final float DISTANCE = 128.0f;
    private static final int GLOWING_FLAG_INDEX = 6;
    private static final String HUD_GLOW_TEAM = "rich_esp_glow_hud";

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

    public static Esp getInstance() {
        return Instance.get(Esp.class);
    }

    Identifier TEXTURE = Identifier.of("textures/features/esp/container.png");

    List<PlayerEntity> players = new ArrayList<>();
    List<LivingEntity> living = new ArrayList<>();

    Map<RegistryKey<Enchantment>, String> encMap = new HashMap<>();

    Map<Integer, Boolean> originalGlowFlags = new HashMap<>();
    Set<Integer> currentGlowTargets = new HashSet<>();
    Map<Integer, String> originalGlowTeamNames = new HashMap<>();
    Map<Integer, String> glowScoreHolderNames = new HashMap<>();

    public MultiSelectSetting entityType = new MultiSelectSetting("Тип сущности", "Сущности, которые будут отображаться")
            .value("Player", "Entity", "Item", "TNT").selected("Player", "Item");

    MultiSelectSetting playerSetting = new MultiSelectSetting("Настройки игрока", "Настройки для игроков")
            .value("Box", "Armor", "NameTags", "Hand Items").selected("Box", "Armor", "NameTags", "Hand Items").visible(() -> entityType.isSelected("Player"));

    public SelectSetting boxType = new SelectSetting("Тип", "Тип")
            .value("Corner", "Full", "3D Box", "Skeleton", "Glow").selected("3D Box").visible(() -> playerSetting.isSelected("Box"));

    public BooleanSetting flatBoxOutline = new BooleanSetting("Контур", "Контур для плоских боксов").visible(() -> playerSetting.isSelected("Box") && (boxType.isSelected("Corner") || boxType.isSelected("Full")));

    public SliderSettings boxAlpha = new SliderSettings("Прозрачность", "Прозрачность бокса")
            .setValue(1.0F).range(0.1F, 1.0F).visible(() -> boxType.isSelected("3D Box"));

    public SliderSettings skeletonWidth = new SliderSettings("Толщина линий", "Толщина линий скелета")
            .setValue(2.5f).range(2.5f, 4.0f).visible(() -> boxType.isSelected("Skeleton"));

    MultiSelectSetting entitySetting = new MultiSelectSetting("Настройки сущностей", "Настройки для мобов/живых сущностей")
            .value("Box", "NameTags").selected("Box", "NameTags").visible(() -> entityType.isSelected("Entity"));

    SelectSetting entityBoxType = new SelectSetting("Тип", "Тип")
            .value("Corner", "Full", "3D Box", "Glow").selected("3D Box").visible(() -> entitySetting.isSelected("Box") && entityType.isSelected("Entity"));

    BooleanSetting entityFlatOutline = new BooleanSetting("Контур", "Контур для плоских боксов").visible(() -> entitySetting.isSelected("Box") && entityType.isSelected("Entity") && (entityBoxType.isSelected("Corner") || entityBoxType.isSelected("Full")));

    SliderSettings entityBoxAlpha = new SliderSettings("Прозрачность", "Прозрачность бокса")
            .setValue(0.8F).range(0.1F, 1.0F).visible(() -> entitySetting.isSelected("Box") && entityType.isSelected("Entity") && entityBoxType.isSelected("3D Box"));

    public Esp() {
        super("Esp", "Esp", ModuleCategory.RENDER);
        setup(entityType, playerSetting, boxType, flatBoxOutline, boxAlpha, skeletonWidth, entitySetting, entityBoxType, entityFlatOutline, entityBoxAlpha);
    }

    @Override
    public void deactivate() {
        restoreAllGlowFlags();
        super.deactivate();
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        restoreAllGlowFlags();
        players.clear();
        living.clear();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        players.clear();
        living.clear();

        if (mc.world == null || mc.player == null) {
            restoreAllGlowFlags();
            return;
        }

        if (entityType.isSelected("Player")) {
            mc.world.getPlayers().stream()
                    .filter(player -> player != mc.player)
                    .filter(player -> player.getCustomName() == null || !player.getCustomName().getString().startsWith("Ghost_"))
                    .forEach(players::add);
        }

        if (entityType.isSelected("Entity")) {
            PlayerInteractionHelper.streamEntities()
                    .filter(ent -> ent instanceof LivingEntity)
                    .map(ent -> (LivingEntity) ent)
                    .filter(ent -> !(ent instanceof PlayerEntity))
                    .filter(LivingEntity::isAlive)
                    .forEach(living::add);
        }

        updateGlowEspTargets();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        updateGlowEspTargets();

        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);

        if (entityType.isSelected("Player")) {
            for (PlayerEntity player : players) {
                if (player == null) continue;
                if (player == mc.player) continue;
                if (player.getCustomName() != null && player.getCustomName().getString().startsWith("Ghost_")) continue;

                double interpX = MathHelper.lerp(tickDelta, player.prevX, player.getX());
                double interpY = MathHelper.lerp(tickDelta, player.prevY, player.getY());
                double interpZ = MathHelper.lerp(tickDelta, player.prevZ, player.getZ());

                Vec3d interpCenter = new Vec3d(interpX, interpY, interpZ);
                float distance = (float) mc.getEntityRenderDispatcher().camera.getPos().distanceTo(interpCenter);
                if (distance < 1) continue;

                boolean friend = FriendUtils.isFriend(player);
                int baseColor = friend ? ColorAssist.getFriendColor() : ColorAssist.getClientColor();

                if (boxType.isSelected("3D Box")) {
                    int alpha = (int) (boxAlpha.getValue() * 255);
                    int fillColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
                    int outlineColor = baseColor | 0xFF000000;

                    Box interpBox = player.getDimensions(player.getPose()).getBoxAt(interpX, interpY, interpZ);
                    Render3D.drawBox(interpBox, fillColor, 2, true, true, true);
                    Render3D.drawBox(interpBox, outlineColor, 2, true, true, true);
                } else if (boxType.isSelected("Skeleton") && playerSetting.isSelected("Box")) {
                    if (distance > DISTANCE) continue;
                    renderSkeleton(player, tickDelta, baseColor);
                }
            }
        }

        if (entityType.isSelected("Entity") && entitySetting.isSelected("Box") && entityBoxType.isSelected("3D Box")) {
            for (LivingEntity ent : living) {
                if (ent == null) continue;

                double interpX = MathHelper.lerp(tickDelta, ent.prevX, ent.getX());
                double interpY = MathHelper.lerp(tickDelta, ent.prevY, ent.getY());
                double interpZ = MathHelper.lerp(tickDelta, ent.prevZ, ent.getZ());

                Vec3d interpCenter = new Vec3d(interpX, interpY, interpZ);
                float distance = (float) mc.getEntityRenderDispatcher().camera.getPos().distanceTo(interpCenter);
                if (distance < 1 || distance > DISTANCE) continue;

                int baseColor = ColorAssist.getClientColor();
                int alpha = (int) (entityBoxAlpha.getValue() * 255);
                int fillColor = (baseColor & 0x00FFFFFF) | (alpha << 24);
                int outlineColor = baseColor | 0xFF000000;

                Box bb = ent.getBoundingBox();
                Box interpBox = new Box(
                        bb.minX - ent.getX() + interpX,
                        bb.minY - ent.getY() + interpY,
                        bb.minZ - ent.getZ() + interpZ,
                        bb.maxX - ent.getX() + interpX,
                        bb.maxY - ent.getY() + interpY,
                        bb.maxZ - ent.getZ() + interpZ
                );

                Render3D.drawBox(interpBox, fillColor, 2, true, true, true);
                Render3D.drawBox(interpBox, outlineColor, 2, true, true, true);
            }
        }
    }

    @EventHandler
    public void onDraw(DrawEvent e) {
        DrawContext context = e.getDrawContext();
        MatrixStack matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(13, Fonts.Type.SEMI);
        FontRenderer bigFont = Fonts.getSize(13 + 2, Fonts.Type.SEMI);

        if (entityType.isSelected("Player")) {
            for (PlayerEntity player : players) {
                if (player == null) continue;
                if (player == mc.player) continue;
                if (player.getCustomName() != null && player.getCustomName().getString().startsWith("Ghost_")) continue;

                Vector4d vec4d = Projection.getVector4D(player);
                float distance = (float) mc.getEntityRenderDispatcher().camera.getPos().distanceTo(player.getBoundingBox().getCenter());
                boolean friend = FriendUtils.isFriend(player);
                if (distance < 1) continue;
                if (Projection.cantSee(vec4d)) continue;

                if (playerSetting.isSelected("Box") && !boxType.isSelected("Skeleton") && !boxType.isSelected("Glow")) drawBox(friend, vec4d, player);
                if (playerSetting.isSelected("Armor")) drawArmor(context, player, vec4d, font);
                if (playerSetting.isSelected("Hand Items")) drawHands(matrix, player, font, vec4d);

                MutableText text = getTextPlayer(player, friend);

                if (Network.isAresMine()) {
                    float startX = (float) Projection.centerX(vec4d);
                    float startY = (float) (vec4d.y);
                    float width = mc.textRenderer.getWidth(text);
                    float height = mc.textRenderer.fontHeight;
                    float posX = startX - width / 2f;
                    float posY = startY - 11F;

                    blur.render(ShapeProperties.create(matrix,
                                    posX - 2f,
                                    posY - 0.75f,
                                    width + 4f,
                                    height + 1.5f).quality(5).round(4f).softness(1)
                            .round(height / 4f)
                            .color(ColorAssist.HALF_BLACK)
                            .build());
                    context.drawText(mc.textRenderer, text, (int) posX, (int) posY + 1, ColorAssist.getColor(255), false);
                } else {
                    drawText(matrix, text, Projection.centerX(vec4d), vec4d.y - 2, font);
                }
            }
        }

        if (entityType.isSelected("Entity")) {
            for (LivingEntity ent : living) {
                if (ent == null) continue;

                Vector4d vec4d = Projection.getVector4D((Entity) ent);
                float distance = (float) mc.getEntityRenderDispatcher().camera.getPos().distanceTo(ent.getBoundingBox().getCenter());
                if (distance < 1 || distance > DISTANCE) continue;
                if (Projection.cantSee(vec4d)) continue;

                if (entitySetting.isSelected("Box") && !entityBoxType.isSelected("3D Box") && !entityBoxType.isSelected("Glow")) drawEntityBox(vec4d);
                if (entitySetting.isSelected("NameTags")) drawText(matrix, getTextEntity(ent), Projection.centerX(vec4d), vec4d.y - 2, font);
            }
        }

        List<Entity> entities = PlayerInteractionHelper.streamEntities()
                .sorted(Comparator.comparing(ent -> ent instanceof ItemEntity item && item.getStack().getName().getContent().toString().equals("empty")))
                .toList();

        for (Entity entity : entities) {
            if (entity instanceof ItemEntity item && entityType.isSelected("Item")) {
                Vector4d vec4d = Projection.getVector4D(entity);
                ItemStack stack = item.getStack();
                ContainerComponent compoundTag = stack.get(DataComponentTypes.CONTAINER);
                List<ItemStack> list = compoundTag != null ? compoundTag.stream().toList() : List.of();
                if (Projection.cantSee(vec4d)) continue;
                Text text = item.getStack().getName();
                if (stack.getCount() > 1) text = text.copy().append(Formatting.RESET + " [" + Formatting.RED + stack.getCount() + Formatting.GRAY + "x" + Formatting.RESET + "]");
                if (!list.isEmpty()) drawShulkerBox(context, stack, list, vec4d);
                else drawText(matrix, text, Projection.centerX(vec4d), vec4d.y, text.getContent().toString().equals("empty") ? bigFont : font);
            } else if (entity instanceof TntEntity tnt && entityType.isSelected("TNT")) {
                Vector4d vec4d = Projection.getVector4D(entity);
                if (Projection.cantSee(vec4d)) continue;
                drawText(matrix, tnt.getStyledDisplayName(), Projection.centerX(vec4d), vec4d.y, font);
            }
        }
    }

    private void updateGlowEspTargets() {
        currentGlowTargets.clear();

        if (mc.world == null || mc.player == null) {
            restoreMissingGlowTargets();
            return;
        }

        boolean playerGlowMode = entityType.isSelected("Player")
                && playerSetting.isSelected("Box")
                && boxType.isSelected("Glow");

        boolean entityGlowMode = entityType.isSelected("Entity")
                && entitySetting.isSelected("Box")
                && entityBoxType.isSelected("Glow");

        if (!playerGlowMode && !entityGlowMode) {
            restoreMissingGlowTargets();
            return;
        }

        syncHudGlowTeamColor();

        Vec3d localPos = mc.player.getPos();
        double maxDistanceSq = DISTANCE * DISTANCE;

        if (playerGlowMode) {
            for (PlayerEntity player : players) {
                if (player == null || player == mc.player) continue;
                if (!player.isAlive()) continue;
                if (localPos.squaredDistanceTo(player.getPos()) > maxDistanceSq) continue;
                markGlow(player);
            }
        }

        if (entityGlowMode) {
            for (LivingEntity ent : living) {
                if (ent == null) continue;
                if (!ent.isAlive()) continue;
                if (localPos.squaredDistanceTo(ent.getPos()) > maxDistanceSq) continue;
                markGlow(ent);
            }
        }

        restoreMissingGlowTargets();
    }

    private void markGlow(Entity entity) {
        int id = entity.getId();
        currentGlowTargets.add(id);

        originalGlowFlags.putIfAbsent(id, getGlowFlag(entity));
        setGlowFlag(entity, true);

        if (mc.world == null) return;

        String scoreHolder = getScoreHolderName(entity);
        if (scoreHolder == null || scoreHolder.isEmpty()) return;

        glowScoreHolderNames.put(id, scoreHolder);
        originalGlowTeamNames.putIfAbsent(id, getScoreHolderTeamName(mc.world.getScoreboard(), scoreHolder));

        assignScoreHolderToHudGlowTeam(mc.world.getScoreboard(), scoreHolder);
    }

    private void restoreMissingGlowTargets() {
        if (mc.world == null) {
            originalGlowFlags.clear();
            currentGlowTargets.clear();
            originalGlowTeamNames.clear();
            glowScoreHolderNames.clear();
            return;
        }

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
            if (scoreHolder != null) {
                restoreScoreHolderTeam(mc.world.getScoreboard(), scoreHolder, originalGlowTeamNames.get(id));
            }
            originalGlowTeamNames.remove(id);

            iterator.remove();
        }
    }

    private void restoreAllGlowFlags() {
        if (mc.world != null) {
            for (Map.Entry<Integer, Boolean> entry : originalGlowFlags.entrySet()) {
                Entity entity = mc.world.getEntityById(entry.getKey());
                if (entity != null) {
                    setGlowFlag(entity, entry.getValue());
                }
            }

            for (Map.Entry<Integer, String> entry : glowScoreHolderNames.entrySet()) {
                String holder = entry.getValue();
                String originalTeam = originalGlowTeamNames.get(entry.getKey());
                restoreScoreHolderTeam(mc.world.getScoreboard(), holder, originalTeam);
            }
        }

        originalGlowFlags.clear();
        currentGlowTargets.clear();
        originalGlowTeamNames.clear();
        glowScoreHolderNames.clear();
    }

    private void syncHudGlowTeamColor() {
        if (mc.world == null) return;
        Object scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return;

        Object team = getOrCreateTeam(scoreboard, HUD_GLOW_TEAM);
        if (team == null) return;

        int hudColor = getHudColor();
        Formatting formatting = nearestFormatting(hudColor);
        setTeamColor(team, formatting);
    }

    private int getHudColor() {
        try {
            Hud hud = Hud.getInstance();
            if (hud != null && hud.colorSetting != null) {
                return hud.colorSetting.getColor();
            }
        } catch (Throwable ignored) {
        }
        return ColorAssist.getClientColor();
    }

    private void assignScoreHolderToHudGlowTeam(Object scoreboard, String scoreHolder) {
        Object team = getOrCreateTeam(scoreboard, HUD_GLOW_TEAM);
        if (team == null) return;

        removeScoreHolderFromTeam(scoreboard, scoreHolder, team);
        addScoreHolderToTeam(scoreboard, scoreHolder, team);
    }

    private void restoreScoreHolderTeam(Object scoreboard, String scoreHolder, String originalTeamName) {
        Object glowTeam = getOrCreateTeam(scoreboard, HUD_GLOW_TEAM);
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

    private String getScoreHolderName(Entity entity) {
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

    private String getScoreHolderTeamName(Object scoreboard, String scoreHolder) {
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

    private Object getTeam(Object scoreboard, String name) {
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

    private Object getOrCreateTeam(Object scoreboard, String name) {
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

    private void addScoreHolderToTeam(Object scoreboard, String scoreHolder, Object team) {
        ensureScoreboardReflection();
        if (scoreboard == null || scoreHolder == null || team == null) return;
        try {
            if (scoreboardAddScoreHolderToTeamMethod != null) {
                scoreboardAddScoreHolderToTeamMethod.invoke(scoreboard, scoreHolder, team);
            }
        } catch (Throwable ignored) {
        }
    }

    private void removeScoreHolderFromTeam(Object scoreboard, String scoreHolder, Object team) {
        ensureScoreboardReflection();
        if (scoreboard == null || scoreHolder == null || team == null) return;
        try {
            if (scoreboardRemoveScoreHolderFromTeamMethod != null) {
                scoreboardRemoveScoreHolderFromTeamMethod.invoke(scoreboard, scoreHolder, team);
            }
        } catch (Throwable ignored) {
        }
    }

    private void setTeamColor(Object team, Formatting formatting) {
        ensureScoreboardReflection();
        if (team == null || formatting == null) return;
        try {
            if (teamSetColorMethod != null) {
                teamSetColorMethod.invoke(team, formatting);
            }
        } catch (Throwable ignored) {
        }
    }

    private String getTeamName(Object team) {
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

    private static void ensureScoreboardReflection() {
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

    private Formatting nearestFormatting(int color) {
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

    private Integer getFormattingRgb(Formatting f) {
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

    private static boolean getGlowFlag(Entity entity) {
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

    private static void setGlowFlag(Entity entity, boolean state) {
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

    private static void ensureEntityFlagMethods() {
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

    private void renderSkeleton(PlayerEntity player, float partialTicks, int color) {
        Vec3d pos = Calculate.interpolate(player);
        float width = skeletonWidth.getValue();

        float limbSwing = player.limbAnimator.getPos(partialTicks);
        float limbSwingAmount = player.limbAnimator.getSpeed(partialTicks);

        float bodyYaw = MathHelper.lerpAngleDegrees(partialTicks, player.prevBodyYaw, player.bodyYaw);
        float bodyYawRad = (float) Math.toRadians(-bodyYaw + 90);

        boolean isSwimming = player.isSwimming() || player.isGliding();
        float sneakOffset = player.isSneaking() ? 0.2f : 0f;
        float swimOffset = isSwimming ? 0.6f : 0f;

        Vec3d head = pos.add(0, 1.62f - sneakOffset - swimOffset, 0);
        Vec3d neck = pos.add(0, 1.4f - sneakOffset - swimOffset, 0);
        Vec3d body = pos.add(0, 0.9f - sneakOffset - swimOffset, 0);
        Vec3d pelvis = pos.add(0, 0.6f - sneakOffset - swimOffset, 0);

        Render3D.drawLine(head, neck, color, width, false);
        Render3D.drawLine(neck, body, color, width, false);
        Render3D.drawLine(body, pelvis, color, width, false);

        float rightArmSwing = MathHelper.cos(limbSwing * 0.6662f) * limbSwingAmount * 0.5f;
        float leftArmSwing = MathHelper.cos(limbSwing * 0.6662f + (float) Math.PI) * limbSwingAmount * 0.5f;
        float rightLegSwing = MathHelper.cos(limbSwing * 0.6662f + (float) Math.PI) * limbSwingAmount * 0.7f;
        float leftLegSwing = MathHelper.cos(limbSwing * 0.6662f) * limbSwingAmount * 0.7f;

        Vec3d rightShoulder = neck.add(
                Math.sin(bodyYawRad) * 0.3,
                -0.1,
                Math.cos(bodyYawRad) * 0.3
        );

        Vec3d rightElbow = rightShoulder.add(
                Math.sin(bodyYawRad) * 0.05 + Math.sin(bodyYawRad + Math.PI / 2) * rightArmSwing * 0.15,
                -0.25 - Math.abs(rightArmSwing) * 0.1,
                Math.cos(bodyYawRad) * 0.05 + Math.cos(bodyYawRad + Math.PI / 2) * rightArmSwing * 0.15
        );

        Vec3d rightHand = rightElbow.add(
                Math.sin(bodyYawRad + Math.PI / 2) * rightArmSwing * 0.1,
                -0.25 - Math.abs(rightArmSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * rightArmSwing * 0.1
        );

        Render3D.drawLine(rightShoulder, rightElbow, color, width, false);
        Render3D.drawLine(rightElbow, rightHand, color, width, false);

        Vec3d leftShoulder = neck.add(
                -Math.sin(bodyYawRad) * 0.3,
                -0.1,
                -Math.cos(bodyYawRad) * 0.3
        );

        Vec3d leftElbow = leftShoulder.add(
                -Math.sin(bodyYawRad) * 0.05 + Math.sin(bodyYawRad + Math.PI / 2) * leftArmSwing * 0.15,
                -0.25 - Math.abs(leftArmSwing) * 0.1,
                -Math.cos(bodyYawRad) * 0.05 + Math.cos(bodyYawRad + Math.PI / 2) * leftArmSwing * 0.15
        );

        Vec3d leftHand = leftElbow.add(
                Math.sin(bodyYawRad + Math.PI / 2) * leftArmSwing * 0.1,
                -0.25 - Math.abs(leftArmSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * leftArmSwing * 0.1
        );

        Render3D.drawLine(leftShoulder, leftElbow, color, width, false);
        Render3D.drawLine(leftElbow, leftHand, color, width, false);

        Vec3d rightHip = pelvis.add(
                Math.sin(bodyYawRad) * 0.15,
                0,
                Math.cos(bodyYawRad) * 0.15
        );

        Vec3d rightKnee = rightHip.add(
                Math.sin(bodyYawRad + Math.PI / 2) * rightLegSwing * 0.1,
                -0.35 + Math.max(0, rightLegSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * rightLegSwing * 0.1
        );

        Vec3d rightFoot = rightKnee.add(
                Math.sin(bodyYawRad + Math.PI / 2) * rightLegSwing * 0.08,
                -0.35 - Math.max(0, -rightLegSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * rightLegSwing * 0.08
        );

        Render3D.drawLine(rightHip, rightKnee, color, width, false);
        Render3D.drawLine(rightKnee, rightFoot, color, width, false);

        Vec3d leftHip = pelvis.add(
                -Math.sin(bodyYawRad) * 0.15,
                0,
                -Math.cos(bodyYawRad) * 0.15
        );

        Vec3d leftKnee = leftHip.add(
                Math.sin(bodyYawRad + Math.PI / 2) * leftLegSwing * 0.1,
                -0.35 + Math.max(0, leftLegSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * leftLegSwing * 0.1
        );

        Vec3d leftFoot = leftKnee.add(
                Math.sin(bodyYawRad + Math.PI / 2) * leftLegSwing * 0.08,
                -0.35 - Math.max(0, -leftLegSwing) * 0.05,
                Math.cos(bodyYawRad + Math.PI / 2) * leftLegSwing * 0.08
        );

        Render3D.drawLine(leftHip, leftKnee, color, width, false);
        Render3D.drawLine(leftKnee, leftFoot, color, width, false);

        Render3D.drawLine(rightShoulder, leftShoulder, color, width, false);
        Render3D.drawLine(rightHip, leftHip, color, width, false);
    }

    private void drawEntityBox(Vector4d vec) {
        if (entityBoxType.isSelected("3D Box") || entityBoxType.isSelected("Glow")) return;

        int client = ColorAssist.getClientColor();
        int black = ColorAssist.HALF_BLACK;

        float posX = (float) vec.x;
        float posY = (float) vec.y;
        float endPosX = (float) vec.z;
        float endPosY = (float) vec.w;
        float size = (endPosX - posX) / 3;

        if (entityBoxType.isSelected("Corner")) {
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, size, 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, posY, 0.5F, size + 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - 0.5F, size, 0.5F, client);

            Render2D.drawQuad(endPosX - size + 1, posY - 0.5F, size, 0.5F, client);
            Render2D.drawQuad(endPosX + 0.5F, posY, 0.5F, size + 0.5F, client);
            Render2D.drawQuad(endPosX + 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
            Render2D.drawQuad(endPosX - size + 1, endPosY - 0.5F, size, 0.5F, client);

            if (entityFlatOutline.isValue()) {
                Render2D.drawQuad(posX - 1F, posY - 1, size + 1, 1.5F, black);
                Render2D.drawQuad(posX - 1F, posY + 0.5F, 1.5F, size + 0.5F, black);
                Render2D.drawQuad(posX - 1F, endPosY - size - 1, 1.5F, size, black);
                Render2D.drawQuad(posX - 1F, endPosY - 1, size + 1, 1.5F, black);

                Render2D.drawQuad(endPosX - size + 0.5F, posY - 1, size + 1, 1.5F, black);
                Render2D.drawQuad(endPosX, posY + 0.5F, 1.5F, size + 0.5F, black);
                Render2D.drawQuad(endPosX, endPosY - size - 1, 1.5F, size, black);
                Render2D.drawQuad(endPosX - size + 0.5F, endPosY - 1, size + 1, 1.5F, black);
            }
        } else if (entityBoxType.isSelected("Full")) {
            if (entityFlatOutline.isValue()) {
                Render2D.drawQuad(posX - 1F, posY - 1F, endPosX - posX + 2F, 1.5F, black);
                Render2D.drawQuad(posX - 1F, posY - 1F, 1.5F, endPosY - posY + 2F, black);
                Render2D.drawQuad(posX - 1F, endPosY - 1F, endPosX - posX + 2F, 1.5F, black);
                Render2D.drawQuad(endPosX - 0.5F, posY - 1F, 1.5F, endPosY - posY + 2F, black);
            }
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, endPosX - posX + 1F, 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, 0.5F, endPosY - posY + 1F, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - 0.5F, endPosX - posX + 1F, 0.5F, client);
            Render2D.drawQuad(endPosX, posY - 0.5F, 0.5F, endPosY - posY + 1F, client);
        }
    }

    private void drawBox(boolean friend, Vector4d vec, PlayerEntity player) {
        if (boxType.isSelected("3D Box") || boxType.isSelected("Skeleton") || boxType.isSelected("Glow")) {
            return;
        }
        int client = friend ? ColorAssist.getFriendColor() : ColorAssist.getClientColor();
        int black = ColorAssist.HALF_BLACK;
        float posX = (float) vec.x;
        float posY = (float) vec.y;
        float endPosX = (float) vec.z;
        float endPosY = (float) vec.w;
        float size = (endPosX - posX) / 3;
        if (boxType.isSelected("Corner")) {
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, size, 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, posY, 0.5F, size + 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - 0.5F, size, 0.5F, client);
            Render2D.drawQuad(endPosX - size + 1, posY - 0.5F, size, 0.5F, client);
            Render2D.drawQuad(endPosX + 0.5F, posY, 0.5F, size + 0.5F, client);
            Render2D.drawQuad(endPosX + 0.5F, endPosY - size - 0.5F, 0.5F, size, client);
            Render2D.drawQuad(endPosX - size + 1, endPosY - 0.5F, size, 0.5F, client);
            if (flatBoxOutline.isValue()) {
                Render2D.drawQuad(posX - 1F, posY - 1, size + 1, 1.5F, black);
                Render2D.drawQuad(posX - 1F, posY + 0.5F, 1.5F, size + 0.5F, black);
                Render2D.drawQuad(posX - 1F, endPosY - size - 1, 1.5F, size, black);
                Render2D.drawQuad(posX - 1F, endPosY - 1, size + 1, 1.5F, black);
                Render2D.drawQuad(endPosX - size + 0.5F, posY - 1, size + 1, 1.5F, black);
                Render2D.drawQuad(endPosX, posY + 0.5F, 1.5F, size + 0.5F, black);
                Render2D.drawQuad(endPosX, endPosY - size - 1, 1.5F, size, black);
                Render2D.drawQuad(endPosX - size + 0.5F, endPosY - 1, size + 1, 1.5F, black);
            }
        } else if (boxType.isSelected("Full")) {
            if (flatBoxOutline.isValue()) {
                Render2D.drawQuad(posX - 1F, posY - 1F, endPosX - posX + 2F, 1.5F, black);
                Render2D.drawQuad(posX - 1F, posY - 1F, 1.5F, endPosY - posY + 2F, black);
                Render2D.drawQuad(posX - 1F, endPosY - 1F, endPosX - posX + 2F, 1.5F, black);
                Render2D.drawQuad(endPosX - 0.5F, posY - 1F, 1.5F, endPosY - posY + 2F, black);
            }
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, endPosX - posX + 1F, 0.5F, client);
            Render2D.drawQuad(posX - 0.5F, posY - 0.5F, 0.5F, endPosY - posY + 1F, client);
            Render2D.drawQuad(posX - 0.5F, endPosY - 0.5F, endPosX - posX + 1F, 0.5F, client);
            Render2D.drawQuad(endPosX, posY - 0.5F, 0.5F, endPosY - posY + 1F, client);
        }
    }

    private void drawArmor(DrawContext context, PlayerEntity player, Vector4d vec, FontRenderer font) {
        MatrixStack matrix = context.getMatrices();
        List<ItemStack> items = new ArrayList<>();
        player.getEquippedItems().forEach(s -> {
            if (!s.isEmpty()) items.add(s);
        });
        float posX = (float) (Projection.centerX(vec) - items.size() * 5.5);
        float posY = (float) (vec.y - 13 / 1.5 - 15);
        float offset = -11;
        if (!items.isEmpty()) {
            matrix.push();
            matrix.translate(posX, posY, 0);
            for (ItemStack stack : items) {
                offset += 11;
                Render2D.defaultDrawStack(context, stack, offset, 0, false, false, 0.5F);
            }
            matrix.pop();
        }
    }

    private void drawHands(MatrixStack matrix, PlayerEntity player, FontRenderer font, Vector4d vec) {
        double posY = vec.w;
        for (ItemStack stack : player.getHandItems()) {
            if (stack.isEmpty()) continue;
            MutableText text = Text.empty().append(stack.getName());
            if (stack.getCount() > 1)
                text.append(Formatting.RESET + " [" + Formatting.RED + stack.getCount() + Formatting.GRAY + "x" + Formatting.RESET + "]");
            posY += font.getStringHeight(text) / 2 + 3;
            drawText(matrix, text, Projection.centerX(vec), posY, font);
        }
    }

    private void drawShulkerBox(DrawContext context, ItemStack itemStack, List<ItemStack> stacks, Vector4d vec) {
        MatrixStack matrix = context.getMatrices();
        int width = 176;
        int height = 67;
        int color = ColorAssist.multBright(ColorAssist.replAlpha(((BlockItem) itemStack.getItem()).getBlock().getDefaultMapColor().color, 1F), 1);
        matrix.push();
        matrix.translate(Projection.centerX(vec) - (double) width / 4, vec.w + 2, -200 + Math.cos(vec.x));
        matrix.scale(0.5F, 0.5F, 1);
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, 0, 0, 0, 0, width, height, width, height, color);
        int posX = 7;
        int posY = 6;
        for (ItemStack stack : stacks.stream().toList()) {
            Render2D.defaultDrawStack(context, stack, posX, posY, false, true, 1);
            posX += 18;
            if (posX >= 165) {
                posY += 18;
                posX = 7;
            }
        }
        matrix.pop();
    }

    private void drawText(MatrixStack matrix, Text text, double startX, double startY, FontRenderer font) {
        int paddingX = 2;
        float paddingY = 0.75F;
        float height = font.getFont().getSize() / 1.5F;
        float width = font.getStringWidth(text);
        float posX = (float) (startX - width / 2);
        float posY = (float) startY - height;
        rectangle.render(ShapeProperties.create(matrix, posX - paddingX, posY - paddingY, width + paddingX * 2, height + paddingY * 2)
                .round(2f)
                .outlineColor(new Color(33, 33, 33, 0).getRGB())
                .color(ColorAssist.getRect(0.65f))
                .build());
        font.drawText(matrix, text, posX, posY + 3);
    }

    private MutableText getTextPlayer(PlayerEntity player, boolean friend) {
        float health = PlayerInteractionHelper.getHealth(player);
        MutableText text = Text.empty();
        if (friend) text.append("[" + Formatting.GREEN + "F" + Formatting.RESET + "] ");
        if (AntiBot.getInstance().isBot(player)) text.append("[" + Formatting.DARK_RED + "BOT" + Formatting.RESET + "] ");
        if (playerSetting.isSelected("NameTags")) text.append(player.getDisplayName());
        else text.append(player.getName());
        if (player.getOffHandStack().getItem().equals(Items.PLAYER_HEAD) || player.getOffHandStack().getItem().equals(Items.TOTEM_OF_UNDYING))
            text.append(Formatting.RESET + getSphere(player.getOffHandStack()));
        if (health >= 0 && health <= player.getMaxHealth())
            text.append(Formatting.RESET + " [" + Formatting.RED + PlayerInteractionHelper.getHealthString(player) + Formatting.RESET + "]");
        return text;
    }

    private MutableText getTextEntity(LivingEntity ent) {
        MutableText text = Text.empty().append(ent.getDisplayName());
        float hp = ent.getHealth();
        float mx = ent.getMaxHealth();
        if (mx > 0 && hp >= 0 && hp <= mx + 1) {
            String s = String.format(java.util.Locale.US, "%.1f", hp);
            text.append(Formatting.RESET + " [" + Formatting.RED + s + Formatting.RESET + "]");
        }
        return text;
    }

    private String getSphere(ItemStack stack) {
        var component = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (Network.isFunTime() && component != null) {
            NbtCompound compound = component.copyNbt();
            if (compound.getInt("tslevel") != 0) {
                return " [" + Formatting.GOLD + compound.getString("don-item").replace("sphere-", "").toUpperCase() + Formatting.RESET + "]";
            }
        }
        return "";
    }
}