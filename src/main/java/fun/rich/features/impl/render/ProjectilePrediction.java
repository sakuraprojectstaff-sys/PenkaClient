package fun.rich.features.impl.render;

import fun.rich.events.render.DrawEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.features.aura.utils.RaycastAngle;
import fun.rich.utils.features.aura.warp.Turns;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import fun.rich.utils.math.calc.Calculate;
import fun.rich.utils.math.projection.Projection;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EggEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SnowballItem;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ProjectilePrediction extends Module {

    public static ProjectilePrediction getInstance() {
        return Instance.get(ProjectilePrediction.class);
    }

    final BooleanSetting predictInHand = new BooleanSetting("В руке", "Предикт предметов в руке").setValue(true);
    final BooleanSetting predictWorld = new BooleanSetting("В мире", "Предикт летящих снарядов").setValue(true);

    final BooleanSetting arrows = new BooleanSetting("Стрела", "Лук/арбалет").setValue(true);
    final BooleanSetting pearls = new BooleanSetting("Эндер перл", "Эндер перл").setValue(true);
    final BooleanSetting snowballs = new BooleanSetting("Снежок", "Снежки").setValue(true);
    final BooleanSetting potions = new BooleanSetting("Зелья", "Зелья").setValue(true);
    final BooleanSetting tridents = new BooleanSetting("Трезубец", "Трезубец").setValue(true);
    final BooleanSetting xp = new BooleanSetting("Бутылка опыта", "Бутылка опыта").setValue(true);
    final BooleanSetting eggs = new BooleanSetting("Яйцо", "Яйцо").setValue(true);

    final BooleanSetting markers = new BooleanSetting("Метки", "Крест + круг на точке").setValue(true);
    final BooleanSetting redOnEntity = new BooleanSetting("Красный по энтити", "Краснить при попадании в энтити").setValue(true);

    final SliderSettings lineWidth = new SliderSettings("Толщина", "Толщина линии").range(1.0f, 4.0f).setValue(2.0f);
    final SliderSettings lineAlpha = new SliderSettings("Прозрачность", "Прозрачность линии").range(0.05f, 1.0f).setValue(0.85f);
    final SliderSettings markerSize = new SliderSettings("Маркер размер", "Размер крестика").range(0.15f, 0.6f).setValue(0.30f);
    final SliderSettings markerRadius = new SliderSettings("Маркер радиус", "Радиус круга").range(0.25f, 0.9f).setValue(0.45f);

    final ColorSetting color = new ColorSetting("Цвет", "Цвет траектории").setColor(new Color(179, 140, 255, 255).getRGB());

    final Deque<Point> points = new ArrayDeque<>();

    public ProjectilePrediction() {
        super("ProjectilePrediction", "Projectile Prediction", ModuleCategory.RENDER);
        setup(
                predictInHand, predictWorld,
                arrows, pearls, snowballs, potions, tridents, xp, eggs,
                markers, redOnEntity,
                lineWidth, lineAlpha, markerSize, markerRadius,
                color
        );
    }

    @fun.rich.utils.client.managers.event.EventHandler
    public void onDraw(DrawEvent e) {
        if (points.isEmpty()) return;

        DrawContext context = e.getDrawContext();
        FontRenderer font = Fonts.getSize(13);

        for (Point point : points) {
            if (!Projection.canSee(point.pos())) continue;

            Vec3d vec3d = Projection.worldSpaceToScreenSpace(point.pos());
            int ticks = point.ticks();

            double time = ticks * 50 / 1000.0;
            String text = String.format("%.1f", time) + " сек";

            float textWidth = font.getStringWidth(text);
            float padding = 3;
            float iconSize = 8;

            float posX = (float) (vec3d.getX() + textWidth / 2 - 6);
            float posY = (float) (vec3d.getY() + 4);

            blur.render(ShapeProperties.create(context.getMatrices(),
                            posX - textWidth + iconSize + padding, posY - padding,
                            padding + textWidth + padding, 10)
                    .round(1.5F)
                    .color(ColorAssist.HALF_BLACK)
                    .build());

            font.drawString(context.getMatrices(), text, posX - textWidth + 8 + padding * 2, posY + 0.5F, -1);
            Render2D.defaultDrawStack(context, point.stack(), posX - textWidth - padding + 2, posY - padding, true, false, 0.5F);
        }
    }

    @fun.rich.utils.client.managers.event.EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null || mc.world == null) return;

        points.clear();

        if (bool(predictInHand)) {
            drawPredictionInHand(e.getStack(), mc.player.getHandItems(), TurnsConnection.INSTANCE.getRotation());
        }

        if (!bool(predictWorld)) return;

        float width = MathHelper.clamp(slider(lineWidth), 1.0f, 6.0f);
        float alphaMul = MathHelper.clamp(slider(lineAlpha), 0.02f, 1.0f);

        int base = color.getColor();
        int baseRgb = base & 0x00FFFFFF;
        int darkRgb = darkenRgb(baseRgb, 0.55f);
        int baseA = (base >>> 24) & 0xFF;
        int useA = MathHelper.clamp((int) (baseA * alphaMul), 0, 255);

        List<Entity> ents = PlayerInteractionHelper.streamEntities().toList();

        getProjectiles(ents).forEach(entity -> {
            Vec3d motion = entity.getVelocity();
            Vec3d pos = entity.getPos();
            Vec3d prevPos;

            for (int i = 0; i < 300; i++) {
                prevPos = pos;
                pos = pos.add(motion);
                motion = calculateMotion(entity, prevPos, motion);

                HitResult result = RaycastAngle.raycast(prevPos, pos, RaycastContext.ShapeType.COLLIDER, entity);
                boolean hit = result.getType() != HitResult.Type.MISS;
                Vec3d end = hit ? result.getPos() : pos;

                boolean hitEntity = result.getType() == HitResult.Type.ENTITY;
                int rgb = hitEntity && bool(redOnEntity) ? 0xFF3232 : lerpRgb(baseRgb, darkRgb, MathHelper.clamp(i / 60.0f, 0.0f, 1.0f));
                int a = useA;
                if (i < 6) a = (int) (useA * (i / 6.0f));
                int lineColor = (a << 24) | rgb;

                Render3D.drawLine(prevPos, end, lineColor, width, false);

                if (hit || end.y < -128) {
                    Direction face = impactFace(result, prevPos, end);
                    addPointFromEntity(entity, end, i);
                    if (bool(markers)) renderImpactMarker(e.getStack(), end, face, hitEntity);
                    break;
                }
            }
        });
    }

    public void drawPredictionInHand(MatrixStack matrix, Iterable<ItemStack> stacks, Turns angle) {
        Item activeItem = mc.player.getActiveItem().getItem();

        for (ItemStack stack : stacks) {
            List<HitResult> results = switch (stack.getItem()) {
                case ExperienceBottleItem ignored when bool(xp) -> listOf(checkTrajectory(new ExperienceBottleEntity(mc.world, mc.player, stack), 0.8, angle));
                case SplashPotionItem ignored when bool(potions) -> listOf(checkTrajectory(new PotionEntity(mc.world, mc.player, stack), 0.55, angle));
                case TridentItem ignored when bool(tridents) && stack.getItem().equals(activeItem) && mc.player.getItemUseTime() >= 10 -> listOf(checkTrajectory(new TridentEntity(mc.world, mc.player, stack), 2.5, angle));
                case SnowballItem ignored when bool(snowballs) -> listOf(checkTrajectory(new SnowballEntity(mc.world, mc.player, stack), 1.5, angle));
                case EggItem ignored when bool(eggs) -> listOf(checkTrajectory(new EggEntity(mc.world, mc.player, stack), 1.5, angle));
                case EnderPearlItem ignored when bool(pearls) -> listOf(checkTrajectory(new EnderPearlEntity(mc.world, mc.player, stack), 1.5, angle));
                case BowItem ignored when bool(arrows) && stack.getItem().equals(activeItem) && mc.player.isUsingItem() -> {
                    float vel = 3 * MathHelper.clamp((mc.player.getItemUseTime() + tickCounter.getTickDelta(false)) / 20F, 0F, 1F);
                    yield listOf(checkTrajectory(new ArrowEntity(mc.world, mc.player, stack, stack), vel, angle));
                }
                case CrossbowItem ignored when bool(arrows) && CrossbowItem.isCharged(stack) -> {
                    ChargedProjectilesComponent component = stack.get(DataComponentTypes.CHARGED_PROJECTILES);
                    List<HitResult> list = new ArrayList<>();
                    if (component != null && !component.getProjectiles().isEmpty()) {
                        float velocity = component.getProjectiles().getFirst().isOf(Items.FIREWORK_ROCKET) ? 100 : 3;
                        list.add(checkTrajectory(angle.toVector(), new ArrowEntity(mc.world, mc.player, stack, stack), velocity));

                        if (component.getProjectiles().size() > 2) {
                            float pitchAbs = angle.getPitch() / 90;
                            float delta = pitchAbs * pitchAbs * pitchAbs * pitchAbs * pitchAbs;
                            float yaw = MathHelper.lerp(Math.abs(delta), 10, 90);
                            float pitch = MathHelper.lerp(delta, 0, 10);
                            list.add(checkTrajectory(angle.addYaw(-yaw).addPitch(-pitch).toVector(), new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                            list.add(checkTrajectory(angle.addYaw(yaw * 2).toVector(), new ArrowEntity(mc.world, mc.player, stack, stack), velocity));
                        }
                    }
                    yield list;
                }
                default -> null;
            };

            if (results == null) continue;

            results = results.stream().filter(Objects::nonNull).toList();
            if (!results.isEmpty()) renderProjectileResults(matrix, results);
        }
    }

    public void renderProjectileResults(MatrixStack matrix, List<HitResult> results) {
        for (HitResult result : results) {
            Direction direction = getDirection(result);
            boolean hitEntity = result.getType().equals(HitResult.Type.ENTITY);

            int cBase = color.getColor();
            int rgb = (hitEntity && bool(redOnEntity)) ? 0xFF3232 : (cBase & 0x00FFFFFF);
            int a = (cBase >>> 24) & 0xFF;
            int drawColor = (a << 24) | rgb;

            double width = MathHelper.clamp(slider(markerSize), 0.1f, 1.2f);
            double radius = MathHelper.clamp(slider(markerRadius), 0.15f, 2.0f);

            Quaternionf quaternionf = switch (direction) {
                case WEST, EAST -> RotationAxis.POSITIVE_Z.rotationDegrees(90);
                case SOUTH, NORTH -> RotationAxis.POSITIVE_X.rotationDegrees(90);
                default -> new Quaternionf();
            };

            matrix.push();
            matrix.translate(result.getPos());
            matrix.multiply(quaternionf);

            MatrixStack.Entry entry = matrix.peek().copy();
            for (int i = 0, size = 90; i <= size; i++) {
                Render3D.drawLine(entry, Calculate.cosSin(i, size, radius), Calculate.cosSin(i + 1, size, radius), drawColor, drawColor, 1, false);
            }
            Render3D.drawLine(entry, new Vec3d(0, 0, -width), new Vec3d(0, 0, width), drawColor, drawColor, 1, false);
            Render3D.drawLine(entry, new Vec3d(-width, 0, 0), new Vec3d(width, 0, 0), drawColor, drawColor, 1, false);

            matrix.pop();
        }
    }

    private void renderImpactMarker(MatrixStack matrix, Vec3d pos, Direction face, boolean hitEntity) {
        int cBase = color.getColor();
        int rgb = (hitEntity && bool(redOnEntity)) ? 0xFF3232 : (cBase & 0x00FFFFFF);
        int a = (cBase >>> 24) & 0xFF;
        int drawColor = (a << 24) | rgb;

        double width = MathHelper.clamp(slider(markerSize), 0.1f, 1.2f);
        double radius = MathHelper.clamp(slider(markerRadius), 0.15f, 2.0f);

        Quaternionf quaternionf = switch (face) {
            case WEST, EAST -> RotationAxis.POSITIVE_Z.rotationDegrees(90);
            case SOUTH, NORTH -> RotationAxis.POSITIVE_X.rotationDegrees(90);
            default -> new Quaternionf();
        };

        matrix.push();
        matrix.translate(pos);
        matrix.multiply(quaternionf);

        MatrixStack.Entry entry = matrix.peek().copy();
        for (int i = 0, size = 90; i <= size; i++) {
            Render3D.drawLine(entry, Calculate.cosSin(i, size, radius), Calculate.cosSin(i + 1, size, radius), drawColor, drawColor, 1, false);
        }
        Render3D.drawLine(entry, new Vec3d(0, 0, -width), new Vec3d(0, 0, width), drawColor, drawColor, 1, false);
        Render3D.drawLine(entry, new Vec3d(-width, 0, 0), new Vec3d(width, 0, 0), drawColor, drawColor, 1, false);

        matrix.pop();
    }

    public List<Entity> getProjectiles(List<Entity> ents) {
        return ents.stream().filter(e -> (e instanceof PersistentProjectileEntity || e instanceof ThrownItemEntity || e instanceof ItemEntity) && !visible(e)).toList();
    }

    public HitResult checkTrajectory(ProjectileEntity entity, double velocity, Turns angle) {
        return checkTrajectory(angle.toVector(), entity, velocity);
    }

    public HitResult checkTrajectory(Vec3d lookVec, ProjectileEntity entity, double velocity) {
        float sqrt = MathHelper.sqrt(lookVec.toVector3f().lengthSquared());
        Vec3d motion = switch (entity) {
            case ArrowEntity arrow when arrow.getItemStack().getItem() instanceof CrossbowItem -> Vec3d.ZERO;
            default -> mc.player.getVelocity();
        };
        return traceTrajectory(mc.player.getEyePos().add(Calculate.interpolate(mc.player).subtract(mc.player.getPos())), lookVec.multiply(velocity / sqrt).add(motion), entity);
    }

    public HitResult calcTrajectory(ProjectileEntity e) {
        return traceTrajectory(e.getPos(), e.getVelocity(), e);
    }

    public HitResult traceTrajectory(Vec3d pos, Vec3d motion, ProjectileEntity entity) {
        List<Entity> ents = PlayerInteractionHelper.streamEntities().toList();
        Vec3d prevPos;

        for (int i = 0; i < 300; i++) {
            prevPos = pos;
            pos = pos.add(motion);
            motion = calculateMotion(entity, prevPos, motion);

            HitResult result = RaycastAngle.raycast(prevPos, pos, RaycastContext.ShapeType.COLLIDER, entity);
            if (result.getType() != HitResult.Type.MISS) return result;

            Entity bestEnt = null;
            Vec3d bestHit = null;
            double bestDist = Double.MAX_VALUE;

            for (Entity ent : ents) {
                if (!(ent instanceof LivingEntity living)) continue;
                if (!living.isAlive()) continue;
                if (living == entity.getOwner()) continue;

                var box = living.getBoundingBox().expand(0.3);
                var opt = box.raycast(prevPos, pos);
                if (opt.isEmpty()) continue;

                Vec3d hit = opt.get();
                double d = prevPos.squaredDistanceTo(hit);
                if (d < bestDist) {
                    bestDist = d;
                    bestEnt = living;
                    bestHit = hit;
                }
            }

            if (bestEnt != null && bestHit != null) {
                Vec3d p = bestHit;
                return new HitResult(p) {
                    @Override
                    public Type getType() {
                        return Type.ENTITY;
                    }
                };
            }

            if (pos.y < -128) break;
        }

        return null;
    }

    public Vec3d calculateMotion(Entity entity, Vec3d prevPos, Vec3d motion) {
        boolean isInWater = mc.world.getFluidState(BlockPos.ofFloored(prevPos)).isIn(FluidTags.WATER);
        double multiply = switch (entity) {
            case TridentEntity trident -> 0.99;
            case PersistentProjectileEntity persistent when isInWater -> 0.6;
            default -> isInWater ? 0.8 : 0.99;
        };
        return motion.multiply(multiply).add(0, -entity.getFinalGravity(), 0);
    }

    private void addPointFromEntity(Entity entity, Vec3d pos, int ticks) {
        ItemStack stack = switch (entity) {
            case ItemEntity item -> item.getStack();
            case ThrownItemEntity thrown -> thrown.getStack();
            case PersistentProjectileEntity persistent -> persistent.getItemStack();
            default -> ItemStack.EMPTY;
        };
        if (stack.isEmpty()) return;

        points.addLast(new Point(stack, pos, ticks));
        while (points.size() > 64) points.pollFirst();
    }

    private Direction getDirection(HitResult result) {
        if (result instanceof BlockHitResult blockHitResult) {
            return blockHitResult.getSide();
        }
        return Direction.getFacing(result.getPos().subtract(mc.player.getEyePos()).normalize());
    }

    private Direction impactFace(HitResult result, Vec3d prev, Vec3d now) {
        if (result instanceof BlockHitResult bhr) return bhr.getSide();
        Vec3d d = now.subtract(prev);
        if (d.lengthSquared() < 1.0E-8) return Direction.UP;

        Vec3d n = d.normalize();
        double ax = Math.abs(n.x), ay = Math.abs(n.y), az = Math.abs(n.z);

        if (ax > ay && ax > az) return n.x > 0 ? Direction.EAST : Direction.WEST;
        if (ay > az) return n.y > 0 ? Direction.UP : Direction.DOWN;
        return n.z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private boolean visible(Entity entity) {
        boolean posChange = entity.getX() == entity.prevX && entity.getY() == entity.prevY && entity.getZ() == entity.prevZ;
        boolean itemEntityCheck = entity instanceof ItemEntity && (entity.isOnGround() || PlayerInteractionHelper.isBoxInBlock(entity.getBoundingBox().expand(2), Blocks.WATER));
        return posChange || itemEntityCheck;
    }

    private static List<HitResult> listOf(HitResult r) {
        ArrayList<HitResult> l = new ArrayList<>(1);
        if (r != null) l.add(r);
        return l;
    }

    private static int lerpRgb(int a, int b, float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t);
        int rb = (int) (ab + (bb - ab) * t);
        return (rr << 16) | (rg << 8) | rb;
    }

    private static int darkenRgb(int rgb, float f) {
        f = MathHelper.clamp(f, 0.0f, 1.0f);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r = (int) (r * f);
        g = (int) (g * f);
        b = (int) (b * f);
        return (r << 16) | (g << 8) | b;
    }

    private record Point(ItemStack stack, Vec3d pos, int ticks) {
    }

    private static float slider(SliderSettings s) {
        try {
            Method m = s.getClass().getMethod("getValueFloat");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = s.getClass().getMethod("getValue");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        try {
            Method m = s.getClass().getMethod("get");
            Object v = m.invoke(s);
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
        } catch (Throwable ignored) {
        }
        return 1.0f;
    }

    private static boolean bool(Object setting) {
        if (setting == null) return false;
        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("isEnabled");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean b) return b;
        } catch (Throwable ignored) {
        }
        try {
            for (String f : new String[]{"value", "state", "enabled"}) {
                java.lang.reflect.Field ff = setting.getClass().getDeclaredField(f);
                ff.setAccessible(true);
                Object v = ff.get(setting);
                if (v instanceof Boolean b) return b;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
