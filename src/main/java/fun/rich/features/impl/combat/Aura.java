package fun.rich.features.impl.combat;

import antidaunleak.api.annotation.Native;
import fun.rich.Rich;
import fun.rich.display.hud.Notifications;
import fun.rich.events.packet.PacketEvent;
import fun.rich.events.player.MotionEvent;
import fun.rich.events.player.RotationUpdateEvent;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.impl.movement.ElytraTarget;
import fun.rich.features.impl.movement.TargetStrafe;
import fun.rich.features.impl.render.Hud;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.managers.event.types.EventType;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.features.aura.point.MultiPoint;
import fun.rich.utils.features.aura.rotations.constructor.LinearConstructor;
import fun.rich.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.features.aura.striking.StrikerConstructor;
import fun.rich.utils.features.aura.target.TargetFinder;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import fun.rich.utils.features.aura.warp.TurnsConfig;
import fun.rich.utils.features.aura.warp.TurnsConnection;
import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import fun.rich.utils.math.task.TaskPriority;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Setter
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Aura extends Module {

    static final float RANGE_MARGIN = 0.253F;

    static final float NEURO_DEFAULT_ATTACK_RANGE = 3.0f;
    static final float NEURO_DEFAULT_LOOK_RANGE = 1.5f;

    private static Method neuroGetMpRvMethod;
    private static Method neuroAdjustPointForExecMethod;

    public static Aura getInstance() {
        return Instance.get(Aura.class);
    }

    final TargetFinder targetSelector = new TargetFinder();
    final MultiPoint pointFinder = new MultiPoint();

    @NonFinal
    LivingEntity target;

    @NonFinal
    LivingEntity lastTarget;

    @NonFinal
    long shiftTapEndTime = 0;

    public static boolean fakeRotate;

    @NonFinal
    @Getter
    public static float legitSprintNeed;

    SelectSetting aimMode = new SelectSetting("Наводка", "Выберите тип наводки")
            .value("Neuro Aura")
            .selected("Neuro Aura");

    SelectSetting neuroMode = new SelectSetting("Нейро режим", "Режим Neuro Aura")
            .value("Запоминающий", "Выполняющий")
            .selected("Запоминающий")
            .visible(() -> aimMode.isSelected("Neuro Aura"));

    SliderSettings neuroSmooth = new SliderSettings("Нейро плавность", "Плавность наводки/доводки/возврата в Neuro Aura")
            .setValue(0.76f).range(0.20f, 0.95f)
            .visible(() -> aimMode.isSelected("Neuro Aura") && neuroMode.isSelected("Выполняющий"));

    BooleanSetting flick = new BooleanSetting("Флик", "После удара уводит камеру влево/вправо и возвращает на таргет (раз в 3-4 удара)")
            .setValue(false)
            .visible(this::allowUserSettings);

    BooleanSetting legitReset = new BooleanSetting("Легит сброс", "Один раз при потере цели делает волнистый сброс головы и отпускает камеру")
            .setValue(false)
            .visible(this::allowUserSettings);

    MultiSelectSetting targetType = new MultiSelectSetting("Тип таргета", "Фильтрует весь список целей по типу")
            .value("Players", "Mobs", "Animals", "Friends", "Armor Stand")
            .selected("Players", "Mobs", "Animals")
            .visible(() -> !neuroEnabled());

    SliderSettings attackRange = new SliderSettings("Дистанция удара", "Дальность атаки до цели")
            .setValue(3).range(1F, 6F)
            .visible(this::allowUserSettings);

    SliderSettings lookRange = new SliderSettings("Дополнительная дистанция поиска", "Диапазон поиска до цели")
            .setValue(1.5f).range(0F, 2F)
            .visible(this::allowUserSettings);

    MultiSelectSetting attackSetting = new MultiSelectSetting("Настройки", "Позволяет настроить работу функции")
            .value("Only Critical", "Break Shield", "UnPress Shield", "No Attack When Eat", "Ignore The Walls", "Fake Lag", "Hit Chance")
            .selected("Only Critical", "Break Shield")
            .visible(this::allowUserSettings);

    SliderSettings hitChance = new SliderSettings("Шанс удара в %", "Шанс удара по цели")
            .setValue(100).range(1F, 100F)
            .visible(() -> allowUserSettings() && attackSetting.isSelected("Hit Chance"));

    SelectSetting correctionType = new SelectSetting("Коррекции движения", "Выбор коррекции движения игрока")
            .value("Free", "Focused", "Focus V2", "Not visible")
            .selected("Free")
            .visible(() -> !neuroEnabled() || neuroExec() || (neuroEnabled() && neuroMode.isSelected("Запоминающий")));

    SelectSetting sprintReset = new SelectSetting("Сброс спринта", "Выбор сброса спринта перед ударом")
            .value("Legit", "Packet").selected("Legit")
            .visible(this::allowUserSettings);

    BooleanSetting smartCrits = new BooleanSetting("Удары на земле", "Криты только при нажатии пробела")
            .setValue(true)
            .visible(() -> allowUserSettings() && attackSetting.isSelected("Only Critical"));

    final CopyOnWriteArrayList<Packet<?>> packets = new CopyOnWriteArrayList<>();
    Box box;
    public static int tickStop = -1;

    @NonFinal
    StrikerConstructor.AttackPerpetratorConfigurable cachedConfig;

    @NonFinal
    Box cachedHitbox;

    @NonFinal
    Vec3d cachedPoint;

    @NonFinal
    int smoothPointTid = Integer.MIN_VALUE;

    @NonFinal
    Vec3d smoothPointCur = null;

    @NonFinal
    long smoothPointLastMs = 0L;

    @NonFinal
    boolean flickActive = false;

    @NonFinal
    long flickOutUntil = 0L;

    @NonFinal
    long flickEndUntil = 0L;

    @NonFinal
    float flickYawDeg = 0.0f;

    @NonFinal
    int flickSide = 1;

    @NonFinal
    int flickHits = 0;

    @NonFinal
    int flickNext = 3;

    @NonFinal
    int flickTargetId = Integer.MIN_VALUE;

    @NonFinal
    boolean legitResetActive = false;

    @NonFinal
    boolean legitResetConsumed = false;

    @NonFinal
    long legitResetStart = 0L;

    @NonFinal
    long legitResetEnd = 0L;

    @NonFinal
    float legitResetBaseYaw = 0.0f;

    @NonFinal
    float legitResetBasePitch = 0.0f;

    @NonFinal
    float legitResetAmpYaw = 0.0f;

    @NonFinal
    float legitResetAmpPitch = 0.0f;

    @NonFinal
    float legitResetCycles = 3.2f;

    @NonFinal
    int legitResetSide = 1;

    final NeuroAuraExec neuroExecEngine = new NeuroAuraExec();
    final NeuroAuraLearn neuroLearnEngine = new NeuroAuraLearn();

    public Aura() {
        super("Aura", ModuleCategory.COMBAT);

        neuroLearnEngine.bindExec(neuroExecEngine);

        setup(
                aimMode,
                neuroMode,
                neuroSmooth,
                flick,
                legitReset,

                correctionType,
                sprintReset,

                targetType,

                attackRange,
                lookRange,
                hitChance,

                attackSetting,
                smartCrits
        );
    }

    boolean neuroEnabled() {
        return aimMode.isSelected("Neuro Aura");
    }

    boolean neuroExec() {
        return neuroEnabled() && neuroMode.isSelected("Выполняющий");
    }

    boolean allowUserSettings() {
        return !neuroEnabled() || neuroExec();
    }

    float neuroSmoothValue() {
        float v = neuroSmooth.getValue();
        if (v < 0.20f) v = 0.20f;
        if (v > 0.95f) v = 0.95f;
        return v;
    }

    @Override
    public void activate() {
        super.activate();

        targetSelector.releaseTarget();
        target = null;
        lastTarget = null;

        packets.clear();
        box = null;

        cachedConfig = null;
        cachedHitbox = null;
        cachedPoint = null;

        smoothPointTid = Integer.MIN_VALUE;
        smoothPointCur = null;
        smoothPointLastMs = 0L;

        resetFlick();
        resetLegitReset();
        releaseCameraLockNow();

        neuroLearnEngine.bindExec(neuroExecEngine);
        neuroExecEngine.onActivate(this);
        neuroLearnEngine.onActivate(this);
    }

    @Override
    public void deactivate() {
        try {
            neuroLearnEngine.onDeactivate(this);
        } catch (Exception ignored) {
        }

        try {
            neuroExecEngine.onDeactivate(this);
        } catch (Exception ignored) {
        }

        resetFlick();
        resetLegitReset();
        releaseCameraLockNow();

        targetSelector.releaseTarget();
        target = null;
        lastTarget = null;

        cachedConfig = null;
        cachedHitbox = null;
        cachedPoint = null;

        smoothPointTid = Integer.MIN_VALUE;
        smoothPointCur = null;
        smoothPointLastMs = 0L;

        packets.forEach(PlayerInteractionHelper::sendPacketWithOutEvent);
        packets.clear();

        super.deactivate();
    }

    @EventHandler
    public void onPacket(PacketEvent e) {
        if (e.getPacket() instanceof EntityStatusS2CPacket status && status.getStatus() == 30) {
            Entity entity = status.getEntity(mc.world);
            if (entity != null && entity.equals(target) && Hud.getInstance().notificationSettings.isSelected("Break Shield")) {
                Notifications.getInstance().addList(Text.literal("Сломали щит игроку - ").append(entity.getDisplayName()), 5000);
            }
        }

        if (attackSetting.isSelected("Fake Lag") && target != null) {
            if (PlayerInteractionHelper.nullCheck()) return;
            switch (e.getPacket()) {
                case PlayerRespawnS2CPacket respawn -> setState(false);
                case GameJoinS2CPacket join -> setState(false);
                case ClientStatusC2SPacket status when status.getMode().equals(ClientStatusC2SPacket.Mode.PERFORM_RESPAWN) -> setState(false);
                default -> {
                    if (e.isSend() && tickStop < 0) {
                        packets.add(e.getPacket());
                        e.cancel();
                    }
                }
            }
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (box != null && attackSetting.isSelected("Fake Lag") && target != null) {
            Render3D.drawBox(box, ColorAssist.getClientColor(), 1);
        }
    }

    @EventHandler
    public void tick(TickEvent e) {
        neuroExecEngine.onTick(this);
        if (PlayerInteractionHelper.nullCheck()) return;

        if (target == null) return;

        tickStop--;
        if (tickStop >= 0 && !packets.isEmpty() && attackSetting.isSelected("Fake Lag")) {
            box = mc.player.getBoundingBox();
            packets.forEach(PlayerInteractionHelper::sendPacketWithOutEvent);
            packets.clear();
        }
        if (mc.player.distanceTo(target) > effectiveAttackRange() && attackSetting.isSelected("Fake Lag")) {
            packets.forEach(PlayerInteractionHelper::sendPacketWithOutEvent);
            packets.clear();
        }
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        switch (e.getType()) {
            case EventType.PRE -> {
                if (neuroExecEngine.handlePreRotation(this)) {
                    cachedConfig = null;
                    cachedHitbox = null;
                    cachedPoint = null;
                    target = null;
                    return;
                }

                target = updateTarget();

                cachedConfig = null;
                cachedHitbox = null;
                cachedPoint = null;

                if (target == null) {
                    stopFlick();

                    if (legitReset.isValue()) {
                        if (!legitResetActive && !legitResetConsumed) startLegitReset();
                        if (legitResetActive) {
                            stepLegitReset();
                            return;
                        }
                        releaseCameraLockNow();
                        return;
                    } else {
                        resetLegitReset();
                    }

                    boolean handled = false;
                    if (neuroExec()) {
                        handled = neuroExecEngine.handleNoTarget(this);
                    } else {
                        releaseCameraLockNow();
                    }
                    if (handled) return;
                    return;
                }

                if (legitResetActive || legitResetConsumed) resetLegitReset();

                lastTarget = target;

                if (neuroExec()) {
                    neuroExecEngine.onTargetSelected(this, target);
                }

                cachedConfig = getConfig();

                if (neuroEnabled() && neuroMode.isSelected("Запоминающий")) {
                    neuroLearnEngine.learnStep(this, false);
                } else {
                    if (handleFlickRotation(cachedConfig)) return;
                    rotateToTarget(cachedConfig);
                }
            }
            case EventType.POST -> {
                if (neuroExecEngine.isBusy(this)) return;
                if (neuroEnabled() && target != null && neuroMode.isSelected("Запоминающий")) {
                    neuroLearnEngine.learnStep(this, true);
                    return;
                }
                if (target != null) {
                    StrikerConstructor.AttackPerpetratorConfigurable cfg = cachedConfig != null ? cachedConfig : getConfig();

                    StrikeManager attackHandler = null;
                    try {
                        attackHandler = Rich.getInstance().getAttackPerpetrator().getAttackHandler();
                    } catch (Exception ignored) {
                    }

                    boolean willAttack = false;
                    try {
                        if (attackHandler != null) willAttack = attackHandler.canAttack(cfg, 5);
                    } catch (Exception ignored) {
                    }

                    Rich.getInstance().getAttackPerpetrator().performAttack(cfg);

                    if (willAttack) onAttackForFlick();
                }
            }
        }
    }

    public static boolean shouldRotate;

    float effectiveAttackRange() {
        if (!neuroEnabled()) return attackRange.getValue();
        return neuroExec() ? attackRange.getValue() : NEURO_DEFAULT_ATTACK_RANGE;
    }

    float effectiveLookRange() {
        if (!neuroEnabled()) return lookRange.getValue();
        return neuroExec() ? lookRange.getValue() : NEURO_DEFAULT_LOOK_RANGE;
    }

    boolean effectiveIgnoreWalls() {
        if (!neuroEnabled()) return attackSetting.isSelected("Ignore The Walls");
        return neuroExec() && attackSetting.isSelected("Ignore The Walls");
    }

    LivingEntity updateTarget() {
        boolean neuro = neuroEnabled();
        TargetFinder.EntityFilter filter = new TargetFinder.EntityFilter(neuro ? List.of("Players") : targetType.getSelected());

        float range = effectiveAttackRange() + RANGE_MARGIN
                + (mc.player.isGliding() && ElytraTarget.getInstance().isState()
                ? ElytraTarget.getInstance().elytraFindRange.getValue()
                : effectiveLookRange());

        float dynamicFov = 360;

        targetSelector.searchTargets(mc.world.getEntities(), range, dynamicFov, effectiveIgnoreWalls());
        targetSelector.validateTarget(filter::isValid);
        return targetSelector.getCurrentTarget();
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    void rotateToTarget(StrikerConstructor.AttackPerpetratorConfigurable config) {
        TurnsConnection controller = TurnsConnection.INSTANCE;
        TurnsConfig rotationConfig = getRotationConfig();

        boolean neuro = neuroEnabled();
        boolean exec = neuroExec();

        if (neuro && !exec) return;
        if (target == null || config == null) return;

        if (neuroExecEngine.tryApplyExecRotation(this, config, Rich.getInstance().getAttackPerpetrator().getAttackHandler(), controller, rotationConfig))
            return;

        Turns effectiveAngle = config.getAngle();
        if (effectiveAngle == null) {
            effectiveAngle = mc.player != null ? new Turns(mc.player.getYaw(), mc.player.getPitch()) : new Turns(0.0f, 0.0f);
        }

        Turns.VecRotation rotation = new Turns.VecRotation(effectiveAngle, effectiveAngle.toVector());

        if (fakeRotate) {
            controller.setFakeRotation(rotation.getAngle());
        }
        fakeRotate = false;

        int speed = 40;
        controller.rotateTo(rotation, target, speed, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, this);

        shouldRotate = true;
    }

    private Vec3d neuroGetMpRvSafe() {
        try {
            Method m = neuroGetMpRvMethod;
            if (m == null) {
                for (Method mm : neuroExecEngine.getClass().getMethods()) {
                    if (!"getMpRv".equals(mm.getName())) continue;
                    int pc = mm.getParameterCount();
                    if (pc == 0) {
                        m = mm;
                        break;
                    }
                    if (pc == 1 && mm.getParameterTypes()[0].isAssignableFrom(Aura.class)) {
                        m = mm;
                        break;
                    }
                }
                if (m == null) {
                    for (Method mm : neuroExecEngine.getClass().getDeclaredMethods()) {
                        if (!"getMpRv".equals(mm.getName())) continue;
                        int pc = mm.getParameterCount();
                        if (pc == 0) {
                            mm.setAccessible(true);
                            m = mm;
                            break;
                        }
                        if (pc == 1 && mm.getParameterTypes()[0].isAssignableFrom(Aura.class)) {
                            mm.setAccessible(true);
                            m = mm;
                            break;
                        }
                    }
                }
                neuroGetMpRvMethod = m;
            }
            if (m != null) {
                Object r = m.getParameterCount() == 0 ? m.invoke(neuroExecEngine) : m.invoke(neuroExecEngine, this);
                if (r instanceof Vec3d v) return v;
            }
        } catch (Exception ignored) {
        }
        return Vec3d.ZERO;
    }

    private Vec3d neuroAdjustPointForExecSafe(LivingEntity t, Box hb, Vec3d point) {
        try {
            Method m = neuroAdjustPointForExecMethod;
            if (m == null) {
                for (Method mm : neuroExecEngine.getClass().getMethods()) {
                    if (!"adjustPointForExec".equals(mm.getName())) continue;
                    if (mm.getParameterCount() != 4) continue;
                    Class<?>[] p = mm.getParameterTypes();
                    if (!p[0].isAssignableFrom(Aura.class)) continue;
                    if (!LivingEntity.class.isAssignableFrom(p[1])) continue;
                    if (!Box.class.isAssignableFrom(p[2])) continue;
                    if (!Vec3d.class.isAssignableFrom(p[3])) continue;
                    m = mm;
                    break;
                }
                if (m == null) {
                    for (Method mm : neuroExecEngine.getClass().getDeclaredMethods()) {
                        if (!"adjustPointForExec".equals(mm.getName())) continue;
                        if (mm.getParameterCount() != 4) continue;
                        Class<?>[] p = mm.getParameterTypes();
                        if (!p[0].isAssignableFrom(Aura.class)) continue;
                        if (!LivingEntity.class.isAssignableFrom(p[1])) continue;
                        if (!Box.class.isAssignableFrom(p[2])) continue;
                        if (!Vec3d.class.isAssignableFrom(p[3])) continue;
                        mm.setAccessible(true);
                        m = mm;
                        break;
                    }
                }
                neuroAdjustPointForExecMethod = m;
            }
            if (m != null) {
                Object r = m.invoke(neuroExecEngine, this, t, hb, point);
                if (r instanceof Vec3d v) return v;
            }
        } catch (Exception ignored) {
        }
        return point;
    }

    public StrikerConstructor.AttackPerpetratorConfigurable getConfig() {
        float baseRange = effectiveAttackRange() + RANGE_MARGIN;

        Vec3d rv = Vec3d.ZERO;
        if (neuroExec()) {
            rv = neuroGetMpRvSafe();
        }

        Turns baseRotForPoint = mc.player != null
                ? new Turns(mc.player.getYaw(), mc.player.getPitch())
                : TurnsConnection.INSTANCE.getRotation();

        Pair<Vec3d, Box> pointData = pointFinder.computeVector(
                target,
                baseRange,
                baseRotForPoint,
                rv,
                effectiveIgnoreWalls()
        );

        Vec3d computedPoint = pointData.getLeft();
        Box hitbox = pointData.getRight();

        long now = System.currentTimeMillis();

        if (neuroExec()) {
            computedPoint = neuroAdjustPointForExecSafe(target, hitbox, computedPoint);
        } else {
            int tid = target != null ? target.getId() : -1;
            computedPoint = smoothPointGeneric(tid, computedPoint, hitbox, now);
        }

        if (mc.player.isGliding() && target != null && target.isGliding()) {
            Vec3d targetVelocity = target.getVelocity();
            double targetSpeed = targetVelocity.horizontalLength();

            float leadTicks = 0;
            if (ElytraTarget.shouldElytraTarget) {
                leadTicks = ElytraTarget.getInstance().elytraForward.getValue();
            }

            if (targetSpeed > 0.35) {
                Vec3d predictedPos = target.getPos().add(targetVelocity.multiply(leadTicks));
                computedPoint = predictedPos.add(0, target.getHeight() / 2, 0);

                hitbox = new Box(
                        predictedPos.x - target.getWidth() / 2,
                        predictedPos.y,
                        predictedPos.z - target.getWidth() / 2,
                        predictedPos.x + target.getWidth() / 2,
                        predictedPos.y + target.getHeight(),
                        predictedPos.z + target.getWidth() / 2
                );

                computedPoint = smoothPointGeneric(target.getId(), computedPoint, hitbox, now);
            }
        }

        cachedPoint = computedPoint;
        cachedHitbox = hitbox;

        Turns angle = MathAngle.fromVec3d(computedPoint.subtract(Objects.requireNonNull(mc.player).getEyePos()));

        return new StrikerConstructor.AttackPerpetratorConfigurable(
                target,
                angle,
                baseRange,
                attackSetting.getSelected(),
                aimMode,
                hitbox
        );
    }

    private Vec3d smoothPointGeneric(int tid, Vec3d desired, Box hb, long now) {
        if (desired == null) return null;

        Vec3d d = desired;
        if (hb != null) {
            d = new Vec3d(
                    clampD(d.x, hb.minX + 1.0E-4, hb.maxX - 1.0E-4),
                    clampD(d.y, hb.minY + 1.0E-4, hb.maxY - 1.0E-4),
                    clampD(d.z, hb.minZ + 1.0E-4, hb.maxZ - 1.0E-4)
            );
        }

        if (tid != smoothPointTid || smoothPointCur == null) {
            smoothPointTid = tid;
            smoothPointCur = d;
            smoothPointLastMs = now;
            return d;
        }

        long dtMs = now - smoothPointLastMs;
        if (dtMs < 1L) dtMs = 1L;
        if (dtMs > 90L) dtMs = 90L;
        smoothPointLastMs = now;

        float s = neuroEnabled() ? (neuroExec() ? neuroSmoothValue() : 0.70f) : 0.70f;

        float tau = MathHelper.lerp(s, 70.0f, 160.0f);
        float a = (float) dtMs / tau;
        if (a < 0.0f) a = 0.0f;
        if (a > 1.0f) a = 1.0f;
        a = a * a * (3.0f - 2.0f * a);

        smoothPointCur = new Vec3d(
                lerpD(smoothPointCur.x, d.x, a),
                lerpD(smoothPointCur.y, d.y, a),
                lerpD(smoothPointCur.z, d.z, a)
        );

        return smoothPointCur;
    }

    private static double lerpD(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    @Native(type = Native.Type.VMProtectBeginMutation)
    public TurnsConfig getRotationConfig() {
        boolean neuro = neuroEnabled();

        boolean visibleCorrection = neuro || !correctionType.isSelected("Not visible");
        boolean freeCorrection;

        if (neuro) {
            freeCorrection = true;
            if (neuroExec() && correctionType.isSelected("Focus V2")) {
                freeCorrection = false;
            } else if (correctionType.isSelected("Focused")) {
                freeCorrection = false;
            } else if (correctionType.isSelected("Free")) {
                freeCorrection = true;
            } else if (correctionType.isSelected("Not visible")) {
                freeCorrection = true;
            }
        } else {
            freeCorrection = correctionType.isSelected("Free");
            if (correctionType.isSelected("Focused") || correctionType.isSelected("Focus V2")) {
                freeCorrection = false;
            }
        }

        if (!neuro && TargetStrafe.getInstance().isState() && TargetStrafe.getInstance().mode.isSelected("Grim") && target != null) {
            freeCorrection = false;
        }

        return new TurnsConfig(getSmoothMode(), visibleCorrection, freeCorrection);
    }

    @EventHandler
    public void onmotion(MotionEvent event) {
    }

    public RotateConstructor getSmoothMode() {
        return new LinearConstructor();
    }

    private void releaseCameraLockNow() {
        try {
            TurnsConnection.INSTANCE.clear();
        } catch (Exception ignored) {
        }
        fakeRotate = false;
        shouldRotate = false;
    }

    private void resetFlick() {
        flickActive = false;
        flickOutUntil = 0L;
        flickEndUntil = 0L;
        flickYawDeg = 0.0f;
        flickTargetId = Integer.MIN_VALUE;
        flickSide = 1;
        flickHits = 0;
        flickNext = 3 + ThreadLocalRandom.current().nextInt(2);
    }

    private void stopFlick() {
        flickActive = false;
        flickOutUntil = 0L;
        flickEndUntil = 0L;
        flickYawDeg = 0.0f;
        flickTargetId = Integer.MIN_VALUE;
    }

    private void onAttackForFlick() {
        if (!flick.isValue()) return;
        if (target == null || mc == null || mc.player == null) return;
        if (flickActive) return;

        flickHits++;
        if (flickHits < flickNext) return;

        flickHits = 0;
        flickNext = 3 + ThreadLocalRandom.current().nextInt(2);
        flickSide = -flickSide;

        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = cachedPoint != null ? cachedPoint : target.getPos().add(0.0, target.getHeight() * 0.62, 0.0);
        Vec3d d = aim.subtract(eye);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        if (horiz < 0.7) horiz = 0.7;

        double blocks = 1.0 + ThreadLocalRandom.current().nextDouble() * 0.5;
        float deg = (float) Math.toDegrees(Math.atan(blocks / horiz));
        deg = MathHelper.clamp(deg, 12.0f, 35.0f);

        long now = System.currentTimeMillis();
        long outMs = 70L + ThreadLocalRandom.current().nextInt(41);
        long backMs = 110L + ThreadLocalRandom.current().nextInt(71);

        flickYawDeg = deg;
        flickOutUntil = now + outMs;
        flickEndUntil = flickOutUntil + backMs;
        flickTargetId = target.getId();
        flickActive = true;
    }

    private boolean handleFlickRotation(StrikerConstructor.AttackPerpetratorConfigurable cfg) {
        if (!flick.isValue()) return false;
        if (!flickActive) return false;
        if (mc == null || mc.player == null || target == null) {
            stopFlick();
            return false;
        }
        if (target.getId() != flickTargetId) {
            stopFlick();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now >= flickEndUntil) {
            stopFlick();
            return false;
        }

        Turns base = cfg != null && cfg.getAngle() != null ? cfg.getAngle() : new Turns(mc.player.getYaw(), mc.player.getPitch());
        float baseYaw = base.getYaw();
        float basePitch = base.getPitch();

        float offYaw = baseYaw + flickYawDeg * (float) flickSide;
        float outYaw;

        if (now <= flickOutUntil) {
            outYaw = offYaw;
        } else {
            long den = Math.max(1L, flickEndUntil - flickOutUntil);
            float t = (float) (now - flickOutUntil) / (float) den;
            t = MathHelper.clamp(t, 0.0f, 1.0f);
            outYaw = blendAngle(offYaw, baseYaw, t);
        }

        Turns out = new Turns(outYaw, basePitch);
        Turns.VecRotation rot = new Turns.VecRotation(out, out.toVector());

        TurnsConnection controller = TurnsConnection.INSTANCE;
        TurnsConfig rotationConfig = getRotationConfig();

        controller.rotateTo(rot, target, 40, rotationConfig, TaskPriority.HIGH_IMPORTANCE_1, this);

        shouldRotate = true;
        fakeRotate = false;
        return true;
    }

    private void resetLegitReset() {
        legitResetActive = false;
        legitResetConsumed = false;
        legitResetStart = 0L;
        legitResetEnd = 0L;
        legitResetAmpYaw = 0.0f;
        legitResetAmpPitch = 0.0f;
        legitResetCycles = 3.2f;
    }

    private void startLegitReset() {
        if (!legitReset.isValue()) return;
        if (mc == null || mc.player == null) return;
        if (legitResetActive || legitResetConsumed) return;
        if (lastTarget == null) return;

        Vec3d eye = mc.player.getEyePos();

        Vec3d aim = cachedPoint;
        if (aim == null) aim = lastTarget.getPos().add(0.0, lastTarget.getHeight() * 0.62, 0.0);

        double horiz = 2.6;
        if (aim != null) {
            Vec3d d = aim.subtract(eye);
            horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            if (horiz < 0.95) horiz = 0.95;
            if (horiz > 3.2) horiz = 3.2;
        }

        double blocks = 4.0;
        float deg = (float) Math.toDegrees(Math.atan(blocks / horiz));
        deg = MathHelper.clamp(deg, 60.0f, 120.0f);

        legitResetSide = -legitResetSide;

        legitResetBaseYaw = mc.player.getYaw();
        legitResetBasePitch = mc.player.getPitch();

        legitResetAmpYaw = deg;
        legitResetAmpPitch = MathHelper.clamp(deg * (0.16f + ThreadLocalRandom.current().nextFloat() * 0.06f), 5.0f, 18.0f);
        legitResetCycles = 3.9f + ThreadLocalRandom.current().nextFloat() * 1.0f;

        long now = System.currentTimeMillis();
        long dur = 420L + ThreadLocalRandom.current().nextInt(201);

        legitResetStart = now;
        legitResetEnd = now + dur;

        legitResetActive = true;
        legitResetConsumed = true;
    }

    private void stepLegitReset() {
        if (!legitResetActive) return;
        if (!legitReset.isValue() || mc == null || mc.player == null) {
            legitResetActive = false;
            releaseCameraLockNow();
            return;
        }

        long now = System.currentTimeMillis();
        long den = Math.max(1L, legitResetEnd - legitResetStart);
        float t = (float) (now - legitResetStart) / (float) den;

        if (t >= 1.0f) {
            legitResetActive = false;
            legitResetStart = 0L;
            legitResetEnd = 0L;
            legitResetAmpYaw = 0.0f;
            legitResetAmpPitch = 0.0f;
            releaseCameraLockNow();
            return;
        }

        t = MathHelper.clamp(t, 0.0f, 1.0f);

        float ease = t * t * (3.0f - 2.0f * t);
        float decay = 1.0f - ease;

        float phase = legitResetSide > 0 ? 0.0f : (float) Math.PI;
        float w = (float) (Math.PI * 2.0) * legitResetCycles;

        float yawOff = legitResetAmpYaw * decay * (float) Math.sin(w * t + phase);

        float pitchWave = (float) Math.sin(w * t + phase + 1.5707964f);
        float pitchOff = legitResetAmpPitch * decay * pitchWave;

        float jitterY = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.40f * decay;
        float jitterP = (ThreadLocalRandom.current().nextFloat() - 0.5f) * 0.30f * decay;

        float outYaw = legitResetBaseYaw + yawOff + jitterY;
        float outPitch = MathHelper.clamp(legitResetBasePitch + pitchOff + jitterP, -90.0f, 90.0f);

        Turns out = new Turns(outYaw, outPitch);
        Turns.VecRotation rot = new Turns.VecRotation(out, out.toVector());

        TurnsConfig silent = new TurnsConfig(getSmoothMode(), false, true);
        TurnsConnection.INSTANCE.rotateTo(rot, mc.player, 40, silent, TaskPriority.HIGH_IMPORTANCE_1, this);

        fakeRotate = false;
        shouldRotate = true;
    }

    private static float blendAngle(float from, float to, float t) {
        float d = MathHelper.wrapDegrees(to - from);
        return from + d * MathHelper.clamp(t, 0.0f, 1.0f);
    }
}