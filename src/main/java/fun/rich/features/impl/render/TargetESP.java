package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.Rich;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.events.player.RotationUpdateEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.impl.combat.Aura;
import fun.rich.features.impl.combat.TriggerBot;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.managers.event.types.EventType;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.math.calc.CalcVector;
import fun.rich.utils.math.time.StopWatch;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.PufferfishEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4i;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TargetESP extends Module {

    public static TargetESP getInstance() {
        return Instance.get(TargetESP.class);
    }

    Animation esp_anim = new Decelerate().setMs(400).setValue(1);

    SelectSetting targetEspType = new SelectSetting("Отображения таргета", "Выбирает тип цели esp")
            .value(
                    "Cube",
                    "Circle",
                    "Circle V2",
                    "Ghosts",
                    "Ghost V2",
                    "Crystals",
                    "Garland",
                    "Atom",
                    "Pig",
                    "Летучая мышь",
                    "Попугай",
                    "Фея",
                    "Пчела",
                    "Векс",
                    "Лисичка",
                    "Лягушка",
                    "Иглобрюх",
                    "Слайм"
            )
            .selected("Circle");

    SelectSetting cubeType = new SelectSetting("Картинка для куба", "Выбирает тип куба")
            .value("1", "2", "3", "4", "5")
            .visible(() -> targetEspType.isSelected("Cube"));

    SelectSetting atomParticles = new SelectSetting("Частицы Atom", "Количество частиц для Atom")
            .value("3", "4", "5", "6", "7", "8", "9", "10", "11", "12")
            .visible(() -> targetEspType.isSelected("Atom"))
            .selected("4");

    final SliderSettings cubeSpeed = new SliderSettings("Cube Speed", "Скорость Cube")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Cube"));

    final SliderSettings circleSpeed = new SliderSettings("Circle Speed", "Скорость Circle")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Circle"));

    final SliderSettings circleV2Speed = new SliderSettings("Circle V2 Speed", "Скорость Circle V2")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Circle V2"));

    final SliderSettings ghostsSpeed = new SliderSettings("Ghosts Speed", "Скорость Ghosts")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Ghosts"));

    final SliderSettings ghostV2Speed = new SliderSettings("Ghost V2 Speed", "Скорость Ghost V2")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Ghost V2"));

    final SliderSettings crystalsSpeed = new SliderSettings("Crystals Speed", "Скорость Crystals")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Crystals"));

    final SliderSettings garlandSpeed = new SliderSettings("Garland Speed", "Скорость Garland")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Garland"));

    final SliderSettings atomSpeed = new SliderSettings("Atom Speed", "Скорость Atom")
            .range(0.5f, 3.0f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Atom"));

    final SliderSettings pigSpeed = new SliderSettings("Pig Speed", "Скорость Pig")
            .range(0.5f, 3.0f)
            .setValue(1.5f)
            .visible(() -> targetEspType.isSelected("Pig"));

    final SliderSettings pigSize = new SliderSettings("Pig Size", "Размер Pig")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Pig"));

    final SliderSettings batSpeed = new SliderSettings("Bat Speed", "Скорость Летучей мыши")
            .range(0.5f, 3.0f)
            .setValue(1.3f)
            .visible(() -> targetEspType.isSelected("Летучая мышь"));

    final SliderSettings batSize = new SliderSettings("Bat Size", "Размер Летучей мыши")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Летучая мышь"));

    final SliderSettings parrotSpeed = new SliderSettings("Parrot Speed", "Скорость Попугая")
            .range(0.5f, 3.0f)
            .setValue(1.3f)
            .visible(() -> targetEspType.isSelected("Попугай"));

    final SliderSettings parrotSize = new SliderSettings("Parrot Size", "Размер Попугая")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Попугай"));

    final SliderSettings fairySpeed = new SliderSettings("Fairy Speed", "Скорость Феи")
            .range(0.5f, 3.0f)
            .setValue(1.2f)
            .visible(() -> targetEspType.isSelected("Фея"));

    final SliderSettings fairySize = new SliderSettings("Fairy Size", "Размер Феи")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Фея"));

    final SliderSettings beeSpeed = new SliderSettings("Bee Speed", "Скорость Пчелы")
            .range(0.5f, 3.0f)
            .setValue(1.35f)
            .visible(() -> targetEspType.isSelected("Пчела"));

    final SliderSettings beeSize = new SliderSettings("Bee Size", "Размер Пчелы")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Пчела"));

    final SliderSettings vexSpeed = new SliderSettings("Vex Speed", "Скорость Векса")
            .range(0.5f, 3.0f)
            .setValue(1.25f)
            .visible(() -> targetEspType.isSelected("Векс"));

    final SliderSettings vexSize = new SliderSettings("Vex Size", "Размер Векса")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Векс"));

    final SliderSettings foxSpeed = new SliderSettings("Fox Speed", "Скорость Лисички")
            .range(0.5f, 3.0f)
            .setValue(1.15f)
            .visible(() -> targetEspType.isSelected("Лисичка"));

    final SliderSettings foxSize = new SliderSettings("Fox Size", "Размер Лисички")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Лисичка"));

    final SliderSettings frogSpeed = new SliderSettings("Frog Speed", "Скорость Лягушки")
            .range(0.5f, 3.0f)
            .setValue(1.15f)
            .visible(() -> targetEspType.isSelected("Лягушка"));

    final SliderSettings frogSize = new SliderSettings("Frog Size", "Размер Лягушки")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Лягушка"));

    final SliderSettings pufferSpeed = new SliderSettings("Pufferfish Speed", "Скорость Иглобрюха")
            .range(0.5f, 3.0f)
            .setValue(1.15f)
            .visible(() -> targetEspType.isSelected("Иглобрюх"));

    final SliderSettings pufferSize = new SliderSettings("Pufferfish Size", "Размер Иглобрюха")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Иглобрюх"));

    final SliderSettings slimeSpeed = new SliderSettings("Slime Speed", "Скорость Слайма")
            .range(0.5f, 3.0f)
            .setValue(1.2f)
            .visible(() -> targetEspType.isSelected("Слайм"));

    final SliderSettings slimeSize = new SliderSettings("Slime Size", "Размер Слайма")
            .range(0.4f, 2.2f)
            .setValue(1.0f)
            .visible(() -> targetEspType.isSelected("Слайм"));

    public ColorSetting colorSetting = new ColorSetting("Цвет", "Выберите цвет для esp")
            .setColor(new Color(255, 101, 57, 255).getRGB());

    float animationProgress = 0.0f;
    LivingEntity target = null;
    LivingEntity lastTarget = null;

    double kolcoStep = 0.0;
    final double ring2SpeedBase = 0.035;

    final Identifier glowTexture = Identifier.of("textures/features/particles/bloom.png");

    final Toggle throughWalls = new Toggle(false);
    final Toggle damageRed = new Toggle(true);

    long lastDamageTime = 0L;
    static final long DAMAGE_FLASH_DURATION = 450L;
    float damageFlashIntensity = 0.0f;

    private Entity lastRenderedTarget = null;
    private final List<Crystal> crystalList = new ArrayList<>();
    private float rotationAngle = 0;
    private static final Identifier ESP_BLOOM_TEX = Identifier.of("textures/features/particles/bloom.png");

    net.minecraft.world.World petWorld = null;

    PigEntity pig = null;
    BatEntity bat = null;
    ParrotEntity parrot = null;
    AllayEntity fairy = null;
    BeeEntity bee = null;
    VexEntity vex = null;
    FoxEntity fox = null;
    FrogEntity frog = null;
    PufferfishEntity pufferfish = null;
    SlimeEntity slime = null;

    public TargetESP() {
        super("TargetEsp", "Target Esp", ModuleCategory.RENDER);
        setup(
                targetEspType,
                cubeType,
                atomParticles,
                cubeSpeed,
                circleSpeed,
                circleV2Speed,
                ghostsSpeed,
                ghostV2Speed,
                crystalsSpeed,
                garlandSpeed,
                atomSpeed,
                pigSpeed,
                pigSize,
                batSpeed,
                batSize,
                parrotSpeed,
                parrotSize,
                fairySpeed,
                fairySize,
                beeSpeed,
                beeSize,
                vexSpeed,
                vexSize,
                foxSpeed,
                foxSize,
                frogSpeed,
                frogSize,
                pufferSpeed,
                pufferSize,
                slimeSpeed,
                slimeSize,
                colorSetting
        );
    }

    @EventHandler
    public void onRotationUpdate(RotationUpdateEvent e) {
        if (e.getType() == EventType.POST) {
            Render3D.updateTargetEsp();
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        StrikeManager attackHandler = Rich.getInstance().getAttackPerpetrator().getAttackHandler();
        StopWatch attackTimer = attackHandler.getAttackTimer();

        LivingEntity currentTarget = null;
        LivingEntity lastTargetLocal = null;

        if (Aura.getInstance().isState()) {
            currentTarget = Aura.getInstance().getTarget();
            lastTargetLocal = Aura.getInstance().getLastTarget();
        } else if (TriggerBot.getInstance().isState()) {
            currentTarget = TriggerBot.getInstance().target;
            lastTargetLocal = TriggerBot.getInstance().target;
        }

        float fadeMul = speedForSelected();
        int ms = (int) MathHelper.clamp(400.0f / Math.max(0.05f, fadeMul), 60.0f, 2400.0f);
        esp_anim.setMs(ms);

        esp_anim.setDirection(currentTarget != null ? Direction.FORWARDS : Direction.BACKWARDS);
        float anim = esp_anim.getOutput().floatValue();

        animationProgress = anim;
        target = currentTarget;
        lastTarget = lastTargetLocal;

        kolcoStep += ring2SpeedBase * slider(circleV2Speed);

        if (lastTargetLocal != null && lastTargetLocal.hurtTime > 0) {
            lastDamageTime = System.currentTimeMillis();
        }

        if (lastTargetLocal != null && !esp_anim.isFinished(Direction.BACKWARDS)) {
            float red = MathHelper.clamp((lastTargetLocal.hurtTime - tickCounter.getTickDelta(false)) / 20, 0, 1);
            switch (targetEspType.getSelected()) {
                case "Cube" -> Render3D.drawCube(lastTargetLocal, anim, red, cubeType.getSelected());
                case "Circle" -> Render3D.drawCircle(e.getStack(), lastTargetLocal, anim, red);
                case "Circle V2" -> renderKolco2(new EventRender3D(e.getStack()), anim);
                case "Ghosts" -> Render3D.drawGhosts(lastTargetLocal, anim, red, 0.62F);
                case "Ghost V2" -> renderDoubleHelix(e.getStack(), lastTargetLocal, anim, red);
                case "Crystals" -> {
                    if (crystalList.isEmpty() || lastTargetLocal != lastRenderedTarget) {
                        createCrystals(lastTargetLocal);
                        lastRenderedTarget = lastTargetLocal;
                    }
                    renderCrystals(e.getStack(), lastTargetLocal, anim, red);
                }
                case "Garland" -> renderGarland(e.getStack(), lastTargetLocal, anim);
                case "Atom" -> renderAtom(e.getStack(), lastTargetLocal, anim, red);
                case "Pig" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.PIG, slider(pigSpeed), slider(pigSize));
                case "Летучая мышь" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.BAT, slider(batSpeed), slider(batSize));
                case "Попугай" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.PARROT, slider(parrotSpeed), slider(parrotSize));
                case "Фея" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.FAIRY, slider(fairySpeed), slider(fairySize));
                case "Пчела" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.BEE, slider(beeSpeed), slider(beeSize));
                case "Векс" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.VEX, slider(vexSpeed), slider(vexSize));
                case "Лисичка" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.FOX, slider(foxSpeed), slider(foxSize));
                case "Лягушка" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.FROG, slider(frogSpeed), slider(frogSize));
                case "Иглобрюх" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.PUFFERFISH, slider(pufferSpeed), slider(pufferSize));
                case "Слайм" -> renderPet(e.getStack(), lastTargetLocal, anim, PetKind.SLIME, slider(slimeSpeed), slider(slimeSize));
            }
        }
    }

    private float speedForSelected() {
        String s = targetEspType.getSelected();
        return switch (s) {
            case "Cube" -> slider(cubeSpeed);
            case "Circle" -> slider(circleSpeed);
            case "Circle V2" -> slider(circleV2Speed);
            case "Ghosts" -> slider(ghostsSpeed);
            case "Ghost V2" -> slider(ghostV2Speed);
            case "Crystals" -> slider(crystalsSpeed);
            case "Garland" -> slider(garlandSpeed);
            case "Atom" -> slider(atomSpeed);
            case "Pig" -> slider(pigSpeed);
            case "Летучая мышь" -> slider(batSpeed);
            case "Попугай" -> slider(parrotSpeed);
            case "Фея" -> slider(fairySpeed);
            case "Пчела" -> slider(beeSpeed);
            case "Векс" -> slider(vexSpeed);
            case "Лисичка" -> slider(foxSpeed);
            case "Лягушка" -> slider(frogSpeed);
            case "Иглобрюх" -> slider(pufferSpeed);
            case "Слайм" -> slider(slimeSpeed);
            default -> 1.0f;
        };
    }

    private float slider(SliderSettings s) {
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

    private enum PetKind {
        PIG,
        BAT,
        PARROT,
        FAIRY,
        BEE,
        VEX,
        FOX,
        FROG,
        PUFFERFISH,
        SLIME
    }

    private void ensurePetWorld() {
        if (mc == null || mc.world == null) return;
        if (petWorld != mc.world) {
            petWorld = mc.world;
            pig = null;
            bat = null;
            parrot = null;
            fairy = null;
            bee = null;
            vex = null;
            fox = null;
            frog = null;
            pufferfish = null;
            slime = null;
        }
    }

    private static void tryInvoke(Object o, String name, Class<?>[] sig, Object[] args) {
        try {
            Method m = o.getClass().getMethod(name, sig);
            m.invoke(o, args);
        } catch (Throwable ignored) {
        }
    }

    private static void tryInvokeAny(Object o, String name, Object arg) {
        try {
            for (Method m : o.getClass().getMethods()) {
                if (!m.getName().equals(name)) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 1 && (p[0] == boolean.class || p[0] == Boolean.class)) {
                    m.invoke(o, arg);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Entity getPet(PetKind kind) {
        if (mc == null || mc.world == null) return null;
        ensurePetWorld();

        switch (kind) {
            case PIG -> {
                if (pig == null) {
                    PigEntity p = new PigEntity(EntityType.PIG, mc.world);
                    p.setNoGravity(true);
                    p.setSilent(true);
                    p.setInvulnerable(true);
                    tryInvokeAny(p, "setAiDisabled", true);
                    try {
                        p.setBreedingAge(-24000);
                    } catch (Throwable ignored) {
                    }
                    pig = p;
                }
                return pig;
            }
            case BAT -> {
                if (bat == null) {
                    BatEntity b = new BatEntity(EntityType.BAT, mc.world);
                    b.setNoGravity(true);
                    b.setSilent(true);
                    b.setInvulnerable(true);
                    tryInvokeAny(b, "setAiDisabled", true);
                    tryInvokeAny(b, "setRoosting", false);
                    tryInvokeAny(b, "setHanging", false);
                    bat = b;
                }
                return bat;
            }
            case PARROT -> {
                if (parrot == null) {
                    ParrotEntity p = new ParrotEntity(EntityType.PARROT, mc.world);
                    p.setNoGravity(true);
                    p.setSilent(true);
                    p.setInvulnerable(true);
                    tryInvokeAny(p, "setAiDisabled", true);
                    parrot = p;
                }
                return parrot;
            }
            case FAIRY -> {
                if (fairy == null) {
                    AllayEntity a = new AllayEntity(EntityType.ALLAY, mc.world);
                    a.setNoGravity(true);
                    a.setSilent(true);
                    a.setInvulnerable(true);
                    tryInvokeAny(a, "setAiDisabled", true);
                    fairy = a;
                }
                return fairy;
            }
            case BEE -> {
                if (bee == null) {
                    BeeEntity b = new BeeEntity(EntityType.BEE, mc.world);
                    b.setNoGravity(true);
                    b.setSilent(true);
                    b.setInvulnerable(true);
                    tryInvokeAny(b, "setAiDisabled", true);
                    bee = b;
                }
                return bee;
            }
            case VEX -> {
                if (vex == null) {
                    VexEntity v = new VexEntity(EntityType.VEX, mc.world);
                    v.setNoGravity(true);
                    v.setSilent(true);
                    v.setInvulnerable(true);
                    tryInvokeAny(v, "setAiDisabled", true);
                    vex = v;
                }
                return vex;
            }
            case FOX -> {
                if (fox == null) {
                    FoxEntity f = new FoxEntity(EntityType.FOX, mc.world);
                    f.setNoGravity(true);
                    f.setSilent(true);
                    f.setInvulnerable(true);
                    tryInvokeAny(f, "setAiDisabled", true);
                    fox = f;
                }
                return fox;
            }
            case FROG -> {
                if (frog == null) {
                    FrogEntity f = new FrogEntity(EntityType.FROG, mc.world);
                    f.setNoGravity(true);
                    f.setSilent(true);
                    f.setInvulnerable(true);
                    tryInvokeAny(f, "setAiDisabled", true);
                    frog = f;
                }
                return frog;
            }
            case PUFFERFISH -> {
                if (pufferfish == null) {
                    PufferfishEntity p = new PufferfishEntity(EntityType.PUFFERFISH, mc.world);
                    p.setNoGravity(true);
                    p.setSilent(true);
                    p.setInvulnerable(true);
                    tryInvokeAny(p, "setAiDisabled", true);
                    pufferfish = p;
                }
                return pufferfish;
            }
            case SLIME -> {
                if (slime == null) {
                    SlimeEntity s = new SlimeEntity(EntityType.SLIME, mc.world);
                    s.setNoGravity(true);
                    s.setSilent(true);
                    s.setInvulnerable(true);
                    tryInvokeAny(s, "setAiDisabled", true);
                    tryInvoke(s, "setSize", new Class[]{int.class, boolean.class}, new Object[]{1, true});
                    tryInvoke(s, "setSize", new Class[]{int.class}, new Object[]{1});
                    slime = s;
                }
                return slime;
            }
        }
        return null;
    }

    private float petRadiusMul(PetKind k) {
        return switch (k) {
            case PIG -> 0.75f;
            case BAT -> 0.82f;
            case PARROT -> 0.78f;
            case FAIRY -> 0.88f;
            case BEE -> 0.90f;
            case VEX -> 0.86f;
            case FOX -> 0.80f;
            case FROG -> 0.80f;
            case PUFFERFISH -> 0.86f;
            case SLIME -> 0.84f;
        };
    }

    private float petYBaseMul(PetKind k) {
        return switch (k) {
            case PIG -> 0.55f;
            case BAT -> 0.72f;
            case PARROT -> 0.66f;
            case FAIRY -> 0.70f;
            case BEE -> 0.68f;
            case VEX -> 0.70f;
            case FOX -> 0.56f;
            case FROG -> 0.56f;
            case PUFFERFISH -> 0.64f;
            case SLIME -> 0.58f;
        };
    }

    private float petCoreYAdd(PetKind k) {
        return switch (k) {
            case PIG -> 0.55f;
            case BAT -> 0.75f;
            case PARROT -> 0.65f;
            case FAIRY -> 0.70f;
            case BEE -> 0.62f;
            case VEX -> 0.70f;
            case FOX -> 0.52f;
            case FROG -> 0.52f;
            case PUFFERFISH -> 0.62f;
            case SLIME -> 0.60f;
        };
    }

    private float petOrbitScale(PetKind k) {
        return switch (k) {
            case PIG -> 0.30f;
            case BAT -> 0.26f;
            case PARROT -> 0.28f;
            case FAIRY -> 0.25f;
            case BEE -> 0.24f;
            case VEX -> 0.25f;
            case FOX -> 0.22f;
            case FROG -> 0.22f;
            case PUFFERFISH -> 0.24f;
            case SLIME -> 0.28f;
        };
    }

    private float petCoreScale(PetKind k) {
        return switch (k) {
            case PIG -> 0.42f;
            case BAT -> 0.36f;
            case PARROT -> 0.38f;
            case FAIRY -> 0.34f;
            case BEE -> 0.34f;
            case VEX -> 0.35f;
            case FOX -> 0.30f;
            case FROG -> 0.30f;
            case PUFFERFISH -> 0.34f;
            case SLIME -> 0.40f;
        };
    }

    private void renderEntityCompat(EntityRenderDispatcher dispatcher, Entity entity, MatrixStack ms, VertexConsumerProvider vcp, int light, float tickDelta) {
        try {
            Method m = null;
            for (Method mm : dispatcher.getClass().getMethods()) {
                if (!mm.getName().equals("render")) continue;
                Class<?>[] p = mm.getParameterTypes();
                if (p.length == 9
                        && Entity.class.isAssignableFrom(p[0])
                        && p[1] == double.class && p[2] == double.class && p[3] == double.class
                        && p[4] == float.class && p[5] == float.class
                        && MatrixStack.class.isAssignableFrom(p[6])
                        && VertexConsumerProvider.class.isAssignableFrom(p[7])
                        && p[8] == int.class) {
                    m = mm;
                    break;
                }
            }
            if (m != null) {
                m.invoke(dispatcher, entity, 0.0, 0.0, 0.0, 0.0f, tickDelta, ms, vcp, light);
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            Method m = null;
            for (Method mm : dispatcher.getClass().getMethods()) {
                if (!mm.getName().equals("render")) continue;
                Class<?>[] p = mm.getParameterTypes();
                if (p.length == 8
                        && Entity.class.isAssignableFrom(p[0])
                        && p[1] == double.class && p[2] == double.class && p[3] == double.class
                        && p[4] == float.class
                        && MatrixStack.class.isAssignableFrom(p[5])
                        && VertexConsumerProvider.class.isAssignableFrom(p[6])
                        && p[7] == int.class) {
                    m = mm;
                    break;
                }
            }
            if (m != null) {
                m.invoke(dispatcher, entity, 0.0, 0.0, 0.0, 0.0f, ms, vcp, light);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class PuffCompat {
        private static Method mSetPuffState;
        private static Field fPuffState;
        private static boolean inited;

        private static void init(PufferfishEntity p) {
            if (inited) return;
            inited = true;

            try {
                for (Method m : p.getClass().getMethods()) {
                    if (!m.getName().equals("setPuffState")) continue;
                    Class<?>[] t = m.getParameterTypes();
                    if (t.length == 1 && t[0] == int.class) {
                        mSetPuffState = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            if (mSetPuffState == null) {
                Class<?> k = p.getClass();
                while (k != null) {
                    try {
                        Field f = k.getDeclaredField("puffState");
                        f.setAccessible(true);
                        fPuffState = f;
                        break;
                    } catch (Throwable ignored) {
                    }
                    k = k.getSuperclass();
                }
            }
        }

        static void set(PufferfishEntity p, int state) {
            init(p);
            int s = MathHelper.clamp(state, 0, 2);
            try {
                if (mSetPuffState != null) {
                    mSetPuffState.invoke(p, s);
                    return;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (fPuffState != null) fPuffState.setInt(p, s);
            } catch (Throwable ignored) {
            }
        }
    }

    private static final class BatAnimCompat {
        private static boolean inited;
        private static Method mSetRoosting;
        private static Method mSetHanging;
        private static Field wingDeviation;
        private static Field prevWingDeviation;
        private static Field wingAngle;
        private static Field prevWingAngle;
        private static Field flapProgress;
        private static Field prevFlapProgress;

        private static void init(BatEntity b) {
            if (inited) return;
            inited = true;

            try {
                for (Method m : b.getClass().getMethods()) {
                    if (m.getParameterCount() != 1) continue;
                    Class<?>[] p = m.getParameterTypes();
                    if (p[0] != boolean.class && p[0] != Boolean.class) continue;
                    if (m.getName().equals("setRoosting")) mSetRoosting = m;
                    if (m.getName().equals("setHanging")) mSetHanging = m;
                }
            } catch (Throwable ignored) {
            }

            Class<?> k = b.getClass();
            while (k != null) {
                try {
                    for (Field f : k.getDeclaredFields()) {
                        if (f.getType() != float.class) continue;
                        String n = f.getName().toLowerCase();
                        if (wingDeviation == null && n.contains("wing") && n.contains("deviation") && !n.contains("prev")) {
                            f.setAccessible(true);
                            wingDeviation = f;
                            continue;
                        }
                        if (prevWingDeviation == null && n.contains("wing") && n.contains("deviation") && n.contains("prev")) {
                            f.setAccessible(true);
                            prevWingDeviation = f;
                            continue;
                        }
                        if (wingAngle == null && n.contains("wing") && n.contains("angle") && !n.contains("prev")) {
                            f.setAccessible(true);
                            wingAngle = f;
                            continue;
                        }
                        if (prevWingAngle == null && n.contains("wing") && n.contains("angle") && n.contains("prev")) {
                            f.setAccessible(true);
                            prevWingAngle = f;
                            continue;
                        }
                        if (flapProgress == null && (n.contains("flap") || n.contains("wing")) && n.contains("progress") && !n.contains("prev")) {
                            f.setAccessible(true);
                            flapProgress = f;
                            continue;
                        }
                        if (prevFlapProgress == null && (n.contains("flap") || n.contains("wing")) && n.contains("progress") && n.contains("prev")) {
                            f.setAccessible(true);
                            prevFlapProgress = f;
                        }
                    }
                } catch (Throwable ignored) {
                }
                k = k.getSuperclass();
            }
        }

        static void apply(BatEntity b, float spMul) {
            init(b);

            try {
                if (mSetRoosting != null) mSetRoosting.invoke(b, false);
            } catch (Throwable ignored) {
            }
            try {
                if (mSetHanging != null) mSetHanging.invoke(b, false);
            } catch (Throwable ignored) {
            }

            float t = (System.currentTimeMillis() % 1_000_000L) * 0.00165f * MathHelper.clamp(spMul, 0.5f, 3.0f);
            float flap = 0.5f + 0.5f * (float) Math.sin(t * 12.0f);
            float dev = 0.2f + 0.8f * flap;
            float ang = (float) Math.cos(t * 12.0f) * 0.8f;
            float prog = t * 0.45f;

            try {
                if (prevWingDeviation != null && wingDeviation != null) prevWingDeviation.setFloat(b, wingDeviation.getFloat(b));
            } catch (Throwable ignored) {
            }
            try {
                if (wingDeviation != null) wingDeviation.setFloat(b, dev);
            } catch (Throwable ignored) {
            }

            try {
                if (prevWingAngle != null && wingAngle != null) prevWingAngle.setFloat(b, wingAngle.getFloat(b));
            } catch (Throwable ignored) {
            }
            try {
                if (wingAngle != null) wingAngle.setFloat(b, ang);
            } catch (Throwable ignored) {
            }

            try {
                if (prevFlapProgress != null && flapProgress != null) prevFlapProgress.setFloat(b, flapProgress.getFloat(b));
            } catch (Throwable ignored) {
            }
            try {
                if (flapProgress != null) flapProgress.setFloat(b, prog);
            } catch (Throwable ignored) {
            }
        }
    }

    private void renderPet(MatrixStack ms, LivingEntity entity, float anim, PetKind kind, float spMul, float sizeMul) {
        if (entity == null || anim <= 0.0F) return;
        if (mc == null || mc.world == null) return;

        Entity pet = getPet(kind);
        if (pet == null) return;

        if (kind == PetKind.BAT && pet instanceof BatEntity b) {
            BatAnimCompat.apply(b, spMul);
        }

        float td = mc.getRenderTickCounter().getTickDelta(false);
        Vec3d pos = CalcVector.lerpPosition(entity);

        float radius = Math.max(0.45f, entity.getWidth() * petRadiusMul(kind));
        float yBase = Math.max(0.35f, entity.getHeight() * petYBaseMul(kind));

        float speed = 0.00025f * spMul;
        float time = -(System.currentTimeMillis() % 1_000_000L) * speed;

        double[] px = new double[8];
        double[] py = new double[8];
        double[] pz = new double[8];

        float aoe = time * 360f;

        for (int i = 0; i < 8; i++) {
            float ta = aoe + (i / 8.0f) * 360f;
            double rad = Math.toRadians(ta);
            float yOff = (i % 2 == 0) ? 0.10f : -0.10f;
            double ox = Math.cos(rad) * radius;
            double oz = Math.sin(rad) * radius;
            px[i] = pos.x + ox;
            py[i] = pos.y + yBase + yOff - 0.20f;
            pz[i] = pos.z + oz;
        }

        double coreX = pos.x;
        double coreY = pos.y + Math.max(1.15f, entity.getHeight() + petCoreYAdd(kind));
        double coreZ = pos.z;

        float t2 = (System.currentTimeMillis() % 1_000_000L) * spMul * 0.00100f;
        float yaw2 = t2 * 180f;
        float pitch2 = (float) (Math.sin(t2 * 1.5) * 120f);
        float roll2 = (float) (Math.cos(t2 * 1.2) * 90f);

        float puffLerp = 0.0f;
        if (kind == PetKind.PUFFERFISH && pet instanceof PufferfishEntity pf) {
            float ph = (System.currentTimeMillis() % 1_000_000L) * 0.0011f * spMul;
            float s = 0.5f + 0.5f * (float) Math.sin(ph);
            puffLerp = MathHelper.clamp(s, 0.0f, 1.0f);
            int state = puffLerp < 0.33f ? 0 : (puffLerp < 0.70f ? 1 : 2);
            PuffCompat.set(pf, state);
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        VertexConsumerProvider.Immediate vcp;
        try {
            vcp = mc.getBufferBuilders().getEntityVertexConsumers();
        } catch (Throwable ignored) {
            return;
        }
        int light = 0xF000F0;

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        for (int i = 0; i < 9; i++) {
            double wx, wy, wz;
            double nx, ny, nz;

            if (i < 8) {
                wx = px[i];
                wy = py[i];
                wz = pz[i];
                int ni = (i + 1) % 8;
                nx = px[ni];
                ny = py[ni];
                nz = pz[ni];
            } else {
                wx = coreX;
                wy = coreY;
                wz = coreZ;
                nx = px[0];
                ny = py[0];
                nz = pz[0];
            }

            ms.push();
            ms.translate(wx, wy, wz);

            float localPhase = (float) i * 0.55f;
            float walkT = ((System.currentTimeMillis() % 1_000_000L) * 0.001f) * (1.2f * spMul) + localPhase;

            float bob = (float) Math.sin(walkT * 3.0f) * 0.045f * MathHelper.clamp(spMul, 0.5f, 3.0f);
            ms.translate(0.0, bob, 0.0);

            if (kind == PetKind.BAT) {
                float flutter = (float) Math.sin(walkT * 10.5f) * 0.030f;
                ms.translate(0.0, flutter, 0.0);
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.sin(walkT * 10.5f) * 10.0f));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.cos(walkT * 10.5f) * 8.0f));
            }

            if (i == 8) {
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw2));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch2));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll2));
            } else {
                double lx = nx - wx;
                double lz = nz - wz;
                float yaw = (float) Math.toDegrees(Math.atan2(-lz, lx)) - 95f;
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(walkT * 2.2f) * 8.0f));
            }

            float base = (i == 8 ? petCoreScale(kind) : petOrbitScale(kind));
            float s = base * easeOutCubic(anim) * MathHelper.clamp(sizeMul, 0.05f, 8.0f);

            if (kind == PetKind.PUFFERFISH) {
                float extra = 1.0f + 0.38f * puffLerp;
                s *= extra;
            }

            ms.scale(s, s, s);

            renderEntityCompat(dispatcher, pet, ms, vcp, light, td);

            ms.pop();
        }

        try {
            vcp.draw();
        } catch (Throwable ignored) {
        }

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static class Crystal {
        private final Entity entity;
        private final Vec3d position;
        private final Vec3d rotation;
        private final float size;
        private final float rotationSpeed;

        public Crystal(Entity entity, Vec3d position, Vec3d rotation) {
            this.entity = entity;
            this.position = position;
            this.rotation = rotation;
            this.size = 0.05f;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
        }

        public void render(MatrixStack ms, float anim, float red, Camera camera, float speedMul) {
            ms.push();
            ms.translate(position.x, position.y, position.z);

            float t = (System.currentTimeMillis() / 500.0f) * speedMul;
            float pulsation = 1.0f + (float) (Math.sin(t) * 0.1f);
            ms.scale(pulsation, pulsation, pulsation);

            float selfRotation = ((System.currentTimeMillis() % 36000) / 100.0f) * rotationSpeed * speedMul;

            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation.x));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation.y + selfRotation));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation.z));

            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
            int baseColor = ColorAssist.interpolateColor(TargetESP.getInstance().colorSetting.getColor(), new Color(255, 0, 0).getRGB(), red);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            drawCrystal(ms, baseColor, 0.2f, true, anim);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            drawCrystal(ms, baseColor, 0.3f, true, anim);
            drawCrystal(ms, baseColor, 0.8f, false, anim);

            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            ms.push();
            ms.scale(1.2f, 1.2f, 1.2f);
            drawCrystal(ms, baseColor, 0.3f, true, anim);
            ms.pop();

            drawBloomSphere(ms, baseColor, anim, camera);

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            ms.pop();
        }

        private void drawBloomSphere(MatrixStack ms, int baseColor, float anim, Camera camera) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, Identifier.of("textures/features/particles/bloom.png"));
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.depthMask(false);

            int bloomColor = ColorAssist.setAlpha(baseColor, (int) (0.4f * 25 * anim));
            float bloomSize = size * 13.0f;
            float pitch = camera.getPitch();
            float yaw = camera.getYaw();
            int segments = 6;

            for (int i = 0; i < segments; i++) {
                ms.push();
                float angle = (360.0f / segments) * i;
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
                Matrix4f matrix = ms.peek().getPositionMatrix();
                BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                bufferBuilder.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bufferBuilder.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bufferBuilder.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bufferBuilder.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);
                BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                ms.pop();
            }

            for (int i = 0; i < segments; i++) {
                ms.push();
                float angle = (360.0f / segments) * i;
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
                Matrix4f matrix = ms.peek().getPositionMatrix();
                BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                bufferBuilder.vertex(matrix, -bloomSize / 2, -bloomSize / 2, 0).texture(0, 1).color(bloomColor);
                bufferBuilder.vertex(matrix, bloomSize / 2, -bloomSize / 2, 0).texture(1, 1).color(bloomColor);
                bufferBuilder.vertex(matrix, bloomSize / 2, bloomSize / 2, 0).texture(1, 0).color(bloomColor);
                bufferBuilder.vertex(matrix, -bloomSize / 2, bloomSize / 2, 0).texture(0, 0).color(bloomColor);
                BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
                ms.pop();
            }

            RenderSystem.depthMask(true);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        }

        private void drawCrystal(MatrixStack ms, int baseColor, float alpha, boolean filled, float anim) {
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(
                    filled ? VertexFormat.DrawMode.TRIANGLES : VertexFormat.DrawMode.DEBUG_LINES,
                    VertexFormats.POSITION_COLOR
            );

            float s = size;
            float h_prism = size * 1f;
            float h_pyramid = size * 1.5f;
            int numSides = 8;

            List<Vec3d> topVertices = new ArrayList<>();
            List<Vec3d> bottomVertices = new ArrayList<>();

            for (int i = 0; i < numSides; i++) {
                float angle = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(angle));
                float z = (float) (s * Math.sin(angle));
                topVertices.add(new Vec3d(x, h_prism / 2, z));
                bottomVertices.add(new Vec3d(x, -h_prism / 2, z));
            }

            Vec3d vTop = new Vec3d(0, h_prism / 2 + h_pyramid, 0);
            Vec3d vBottom = new Vec3d(0, -h_prism / 2 - h_pyramid, 0);

            int finalColor = ColorAssist.setAlpha(baseColor, (int) (alpha * 255 * anim));

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                Vec3d v3 = topVertices.get((i + 1) % numSides);
                Vec3d v4 = topVertices.get(i);
                drawQuad(ms, bufferBuilder, v1, v2, v3, v4, finalColor, filled);
            }

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = topVertices.get(i);
                Vec3d v2 = topVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vTop, v1, v2, finalColor, filled);
            }

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vBottom, v2, v1, finalColor, filled);
            }

            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }

        private void drawTriangle(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, int color, boolean filled) {
            Matrix4f m = ms.peek().getPositionMatrix();
            if (filled) {
                bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
            } else {
                bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
            }
        }

        private void drawQuad(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, Vec3d v4, int color, boolean filled) {
            if (filled) {
                drawTriangle(ms, bb, v1, v2, v3, color, true);
                drawTriangle(ms, bb, v1, v3, v4, color, true);
            } else {
                Matrix4f m = ms.peek().getPositionMatrix();
                bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
                bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
                bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
                bb.vertex(m, (float) v4.x, (float) v4.y, (float) v4.z).color(color);
                bb.vertex(m, (float) v4.x, (float) v4.y, (float) v4.z).color(color);
                bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
            }
        }
    }

    private void createCrystals(Entity target) {
        crystalList.clear();
        crystalList.add(new Crystal(target, new Vec3d(0, 0.85, 0.8), new Vec3d(-49, 0, 40)));
        crystalList.add(new Crystal(target, new Vec3d(0.2, 0.85, -0.675), new Vec3d(35, 0, -30)));
        crystalList.add(new Crystal(target, new Vec3d(0.6, 1.35, 0.6), new Vec3d(-30, 0, 35)));
        crystalList.add(new Crystal(target, new Vec3d(-0.74, 1.05, 0.4), new Vec3d(-25, 0, -30)));
        crystalList.add(new Crystal(target, new Vec3d(0.74, 0.95, -0.4), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(-0.475, 0.85, -0.375), new Vec3d(30, 0, -25)));
        crystalList.add(new Crystal(target, new Vec3d(0, 1.35, -0.6), new Vec3d(45, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(0.85, 0.7, 0.1), new Vec3d(-30, 0, 30)));
        crystalList.add(new Crystal(target, new Vec3d(-0.7, 1.35, -0.3), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(-0.3, 1.35, 0.55), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(-0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(0.5, 0.7, 0.7), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(-0.7, 0.75, 0), new Vec3d(0, 0, 0)));
        crystalList.add(new Crystal(target, new Vec3d(-0.2, 0.65, -0.7), new Vec3d(0, 0, 0)));
    }

    private void renderCrystals(MatrixStack ms, Entity target, float anim, float red) {
        if (target == null || crystalList.isEmpty()) return;

        float sp = slider(crystalsSpeed);

        RenderSystem.enableDepthTest();
        Vec3d targetPos = CalcVector.lerpPosition(target);
        rotationAngle = (rotationAngle + 0.5f * sp) % 360.0f;

        ms.push();
        ms.translate(targetPos.x, targetPos.y, targetPos.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationAngle));
        Camera camera = mc.gameRenderer.getCamera();
        for (Crystal crystal : crystalList) {
            crystal.render(ms, anim, red, camera, sp);
        }
        ms.pop();

        RenderSystem.enableDepthTest();
    }

    private void renderDoubleHelix(MatrixStack ms, LivingEntity entity, float anim, float red) {
        if (entity == null || anim <= 0.0F) return;

        float eased = easeOutCubic(anim);

        Camera camera = mc.gameRenderer.getCamera();
        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        Vec3d targetPos = CalcVector.lerpPosition(entity);

        float radius = Math.max(0.22f, entity.getWidth() * 0.75f);
        float height = Math.max(0.35f, entity.getHeight());

        float spMul = slider(ghostV2Speed);
        float sp = 3.0f * spMul;
        double time = (double) System.currentTimeMillis() / (500.0 / (double) sp);

        int baseColor = ColorAssist.interpolateColor(colorSetting.getColor(), new Color(255, 0, 0).getRGB(), red);

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, ESP_BLOOM_TEX);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        ms.push();
        ms.translate(targetPos.x, targetPos.y, targetPos.z);

        BufferBuilder quads = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int steps = 40;
        for (int j = 0; j < steps; j++) {
            float k = (float) j / (float) steps;
            float fade = 1.0f - k;
            float a = eased * fade * 0.9f;

            double tt = time - (double) j * 0.12;
            float sn = (float) Math.sin(tt);
            float cs = (float) Math.cos(tt);

            float y = height * 0.55f + (float) Math.sin(tt) * 0.26f;

            float x1 = cs * radius;
            float z1 = sn * radius;

            float x2 = -x1;
            float z2 = -z1;

            float s = (0.22f * fade + 0.06f) * eased;

            int c = multAlpha(baseColor, a);

            drawBillboard(quads, ms, x1, y, z1, s, yaw, pitch, c);
            drawBillboard(quads, ms, x2, y, z2, s, yaw, pitch, c);
        }

        BufferRenderer.drawWithGlobalProgram(quads.end());

        ms.pop();

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderGarland(MatrixStack ms, LivingEntity entity, float anim) {
        if (entity == null || anim <= 0.0F) return;

        float sp = slider(garlandSpeed);

        Camera camera = mc.gameRenderer.getCamera();

        float height = entity.getHeight();
        float width = entity.getWidth();
        float radius = width * 1.2f;

        float period = 4000f / Math.max(0.05f, sp);
        float time = (System.currentTimeMillis() % (long) period) / period;
        float offset = time * 360f;

        int lightsCount = 30;
        int spirals = 3;

        Vec3d targetPos = CalcVector.lerpPosition(entity);

        ms.push();
        ms.translate(targetPos.x, targetPos.y, targetPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder wire = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        int wireColor = multAlpha(0xFF0B3B0B, anim);
        Matrix4f pm = ms.peek().getPositionMatrix();

        for (int i = 0; i <= lightsCount; i++) {
            float progress = (float) i / (float) lightsCount;
            float angle = (float) Math.toRadians(offset + (progress * 360f * spirals));
            float currentRadius = radius * (1.0f - (progress * 0.6f));
            float x = (float) Math.cos(angle) * currentRadius;
            float z = (float) Math.sin(angle) * currentRadius;
            float y = progress * height;
            wire.vertex(pm, x, y, z).color(wireColor);
        }

        BufferRenderer.drawWithGlobalProgram(wire.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, Identifier.of("textures/features/particles/bloom.png"));
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        BufferBuilder bulbs = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float pitch = camera.getPitch();
        float yaw = camera.getYaw();

        for (int i = 0; i <= lightsCount; i++) {
            float progress = (float) i / (float) lightsCount;
            float angle = (float) Math.toRadians(offset + (progress * 360f * spirals));
            float currentRadius = radius * (1.0f - (progress * 0.6f));
            float x = (float) Math.cos(angle) * currentRadius;
            float z = (float) Math.sin(angle) * currentRadius;
            float y = progress * height;

            float size = 0.15f * easeOutCubic(anim);
            float twinkle = (float) Math.sin(((System.currentTimeMillis() / 100.0) * sp) + i) * 0.2f + 0.8f;
            float alpha = anim * twinkle;

            int color = multAlpha(getFestiveColor(i), alpha);
            drawBillboard(bulbs, ms, x, y, z, size, yaw, pitch, color);
        }

        BufferRenderer.drawWithGlobalProgram(bulbs.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        ms.pop();
    }

    private void renderAtom(MatrixStack ms, LivingEntity entity, float anim, float red) {
        if (entity == null || anim <= 0.0F) return;

        float sp = slider(atomSpeed);

        Vec3d pos = CalcVector.lerpPosition(entity);
        float height = entity.getHeight();

        float td = mc.getRenderTickCounter().getTickDelta(false);
        float time = mc.world != null ? (float) ((mc.world.getTime() + td) / 20.0) : (System.nanoTime() / 1_000_000_000.0f);
        time *= sp;

        int c = ColorAssist.interpolateColor(colorSetting.getColor(), new Color(255, 0, 0).getRGB(), red);
        shedevrotargetespAtoms(ms, pos, height, time, anim, c, c);
    }

    private int getAtomParticleCount() {
        try {
            int v = Integer.parseInt(atomParticles.getSelected());
            if (v < 1) return 1;
            if (v > 32) return 32;
            return v;
        } catch (Exception e) {
            return 4;
        }
    }

    private void shedevrotargetespAtoms(MatrixStack ms, Vec3d pos, float height, float time, float anim, int color1, int color2) {
        ms.push();
        ms.translate(pos.x, pos.y + height / 2f, pos.z);

        float scale = 0.7f * anim;
        ms.scale(scale, scale, scale);

        Camera camera = mc.gameRenderer.getCamera();
        float yaw = camera.getYaw();
        float pitch = camera.getPitch();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        float radius = 1.0f;
        int segments = 160;

        float ringSpin = time * 78f;

        int totalElectrons = getAtomParticleCount();
        int basePerRing = totalElectrons / 3;
        int rem = totalElectrons % 3;
        if (basePerRing < 1) {
            basePerRing = 1;
            rem = 0;
        }

        for (int ring = 0; ring < 3; ring++) {
            ms.push();

            float wobble = (float) Math.sin(time * 1.35f + ring * 0.9f) * 7.5f;
            float ringOffset = ring * 60f + wobble;

            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(ringOffset));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ringSpin + ring * 120f));

            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
            Matrix4f matrix = ms.peek().getPositionMatrix();

            for (int i = 0; i <= segments; i++) {
                float ang = (float) (2 * Math.PI * i / segments);
                float x = (float) Math.cos(ang) * radius;
                float z = (float) Math.sin(ang) * radius;

                float p = (float) i / (float) segments;
                int segColor = ColorAssist.interpolateColor(color1, color2, p);
                segColor = ColorAssist.setAlpha(segColor, (int) (165 * anim));

                bb.vertex(matrix, x, 0, z).color(segColor);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());

            int eCount = basePerRing;
            if (rem == 1) {
                if (ring == 1) eCount++;
            } else if (rem == 2) {
                if (ring == 0 || ring == 2) eCount++;
            }
            float speed = 2.15f + ring * 0.35f;

            for (int p = 0; p < eCount; p++) {
                float base = (float) (2 * Math.PI * p / eCount);
                float theta = time * speed + base + ring * 0.82f;

                float px = (float) Math.cos(theta) * radius;
                float pz = (float) Math.sin(theta) * radius;

                int pBaseColor = ColorAssist.interpolateColor(color1, color2, (float) p / (float) eCount);
                int pc = ColorAssist.setAlpha(pBaseColor, (int) (230 * anim));

                float pulse = 0.92f + 0.08f * (float) Math.sin(time * 6.6f + ring * 1.7f + p * 2.1f);
                float s = 0.082f * anim * pulse;

                ms.push();
                ms.translate(px, 0, pz);
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 190f + ring * 77f + p * 101f));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(time * 160f + ring * 53f + p * 89f));

                drawSmallSphere(ms, s * 1.25f, ColorAssist.setAlpha(pc, (int) (110 * anim)));
                drawSmallSphere(ms, s * 0.78f, ColorAssist.setAlpha(pc, (int) (210 * anim)));

                RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
                RenderSystem.setShaderTexture(0, ESP_BLOOM_TEX);

                BufferBuilder glow = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                drawBillboard(glow, ms, 0, 0, 0, s * 2.0f, yaw, pitch, ColorAssist.setAlpha(pc, (int) (90 * anim)));
                BufferRenderer.drawWithGlobalProgram(glow.end());

                ms.pop();
            }

            ms.pop();
        }

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, ESP_BLOOM_TEX);

        BufferBuilder core = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        drawBillboard(core, ms, 0, 0, 0, 0.20f * anim, yaw, pitch, ColorAssist.setAlpha(color1, (int) (220 * anim)));
        drawBillboard(core, ms, 0, 0, 0, 0.34f * anim, yaw, pitch, ColorAssist.setAlpha(color1, (int) (90 * anim)));
        BufferRenderer.drawWithGlobalProgram(core.end());

        renderGlow(ms, color1, anim * 0.5f);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();

        ms.pop();
    }

    private void drawSmallSphere(MatrixStack ms, float size, int color) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f m = ms.peek().getPositionMatrix();

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        bb.vertex(m, -size, -size, 0).color(color);
        bb.vertex(m, -size, size, 0).color(color);
        bb.vertex(m, size, size, 0).color(color);
        bb.vertex(m, size, -size, 0).color(color);

        bb.vertex(m, -size, 0, -size).color(color);
        bb.vertex(m, -size, 0, size).color(color);
        bb.vertex(m, size, 0, size).color(color);
        bb.vertex(m, size, 0, -size).color(color);

        bb.vertex(m, 0, -size, -size).color(color);
        bb.vertex(m, 0, size, -size).color(color);
        bb.vertex(m, 0, size, size).color(color);
        bb.vertex(m, 0, -size, size).color(color);

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private void renderGlow(MatrixStack ms, int color, float alpha) {
        float a = MathHelper.clamp(alpha, 0.0f, 1.0f);
        int c = ColorAssist.setAlpha(color, (int) (140 * a));

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        int segments = 64;
        float r = 1.25f;

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        Matrix4f m = ms.peek().getPositionMatrix();

        for (int i = 0; i <= segments; i++) {
            float ang = (float) (2 * Math.PI * i / segments);
            float x = (float) Math.cos(ang) * r;
            float z = (float) Math.sin(ang) * r;
            bb.vertex(m, x, 0, z).color(c);
        }

        BufferRenderer.drawWithGlobalProgram(bb.end());
    }

    private static void drawBillboard(BufferBuilder buffer, MatrixStack ms, float x, float y, float z, float scale, float yaw, float pitch, int color) {
        ms.push();
        ms.translate(x, y, z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        Matrix4f m = ms.peek().getPositionMatrix();

        buffer.vertex(m, -scale, -scale, 0).texture(0, 0).color(color);
        buffer.vertex(m, -scale, scale, 0).texture(0, 1).color(color);
        buffer.vertex(m, scale, scale, 0).texture(1, 1).color(color);
        buffer.vertex(m, scale, -scale, 0).texture(1, 0).color(color);

        ms.pop();
    }

    private static int getFestiveColor(int index) {
        int type = index & 3;
        return switch (type) {
            case 0 -> 0xFFFF0000;
            case 1 -> 0xFFFFD700;
            case 2 -> 0xFF00FF00;
            default -> 0xFF00BFFF;
        };
    }

    private static int multAlpha(int argb, float alphaMul) {
        int a = (argb >>> 24) & 0xFF;
        int na = MathHelper.clamp((int) (a * alphaMul), 0, 255);
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    private static float easeOutCubic(float t) {
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static final class Toggle {
        boolean enabled;

        Toggle(boolean enabled) {
            this.enabled = enabled;
        }

        boolean isEnabled() {
            return enabled;
        }
    }

    private static final class EventRender3D {
        private final MatrixStack matrix;

        EventRender3D(MatrixStack matrix) {
            this.matrix = matrix;
        }

        public MatrixStack getMatrix() {
            return matrix;
        }
    }

    private static final class MathUtil {
        static Vec3d interpolate(Entity entity) {
            float td = net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
            return new Vec3d(
                    MathHelper.lerp(td, entity.prevX, entity.getX()),
                    MathHelper.lerp(td, entity.prevY, entity.getY()),
                    MathHelper.lerp(td, entity.prevZ, entity.getZ())
            );
        }

        static double interpolate(double from, double to) {
            float td = net.minecraft.client.MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
            return from + (to - from) * td;
        }

        static double absSinAnimation(double value) {
            return Math.abs(Math.sin(value));
        }
    }

    private static final class ColorUtil {
        static int multAlpha(int argb, float alphaMul) {
            int a = (argb >>> 24) & 0xFF;
            int na = MathHelper.clamp((int) (a * alphaMul), 0, 255);
            return (na << 24) | (argb & 0x00FFFFFF);
        }
    }

    private static final class Render3DUtil {
        static void drawGlowTexture(MatrixStack.Entry entry, Identifier texture, float x, float y, float w, float h, Vector4i colors, boolean useDepth) {
            if (useDepth) {
                RenderSystem.enableDepthTest();
            } else {
                RenderSystem.disableDepthTest();
            }
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            Matrix4f m = entry.getPositionMatrix();
            int c = colors.x;
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            bb.vertex(m, x, y + h, 0).texture(0, 1).color(c);
            bb.vertex(m, x + w, y + h, 0).texture(1, 1).color(c);
            bb.vertex(m, x + w, y, 0).texture(1, 0).color(c);
            bb.vertex(m, x, y, 0).texture(0, 0).color(c);
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }
    }

    private void renderKolco2(EventRender3D event, float interpolatedAlpha) {
        float alpha = Math.max(this.animationProgress, interpolatedAlpha);
        if (alpha <= 0.0F) return;

        LivingEntity targetEntity = this.target != null ? this.target : this.lastTarget;
        if (targetEntity == null) return;

        if (mc == null || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d renderPos = MathUtil.interpolate(targetEntity);

        float entityWidth = targetEntity.getWidth() * 0.9f;
        float entityHeight = targetEntity.getHeight();
        float animationAlpha = easeOutCubic(interpolatedAlpha);

        boolean canSee = Objects.requireNonNull(mc.player).canSee(targetEntity);
        boolean useDepth = !throughWalls.isEnabled() && canSee;

        if (!throughWalls.isEnabled() && !canSee) return;

        MatrixStack matrices = event.getMatrix();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();

        if (useDepth) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }

        double ringSp = slider(circleV2Speed);
        double currentStep = MathUtil.interpolate(kolcoStep - (ring2SpeedBase * ringSp), kolcoStep);

        double golovkaY = MathUtil.absSinAnimation(currentStep) * entityHeight;
        double tailBaseY = MathUtil.absSinAnimation(currentStep - 0.4) * entityHeight;

        float golovkaSize = 0.12f;
        float tailSize = 0.08f;

        int totalPoints = 138;
        int tailSegments = 16;

        for (int i = 0; i < totalPoints; i++) {
            double angleRadians = 2 * Math.PI * i / totalPoints;

            float xOffset = (float) (Math.cos(angleRadians) * entityWidth);
            float zOffset = (float) (Math.sin(angleRadians) * entityWidth);

            int baseColor = applyDamageFlash(colorSetting.getColor());

            matrices.push();
            matrices.translate(renderPos.x + xOffset, renderPos.y + golovkaY, renderPos.z + zOffset);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

            MatrixStack.Entry golovkaEntry = matrices.peek().copy();

            int coreColor = ColorUtil.multAlpha(baseColor, animationAlpha * 0.9f);
            Vector4i coreVec = new Vector4i(coreColor, coreColor, coreColor, coreColor);
            Render3DUtil.drawGlowTexture(golovkaEntry, glowTexture, -golovkaSize / 2, -golovkaSize / 2, golovkaSize, golovkaSize, coreVec, useDepth);

            matrices.pop();

            for (int t = 1; t <= tailSegments; t++) {
                float tailProgress = (float) t / (tailSegments + 1);

                double currentTailY = golovkaY + (tailBaseY - golovkaY) * tailProgress;
                float currentTailAlpha = animationAlpha * (1f - tailProgress) * 0.6f;

                matrices.push();
                matrices.translate(renderPos.x + xOffset, renderPos.y + currentTailY, renderPos.z + zOffset);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

                MatrixStack.Entry tailEntry = matrices.peek().copy();

                int tailCoreColor = ColorUtil.multAlpha(baseColor, currentTailAlpha);
                Vector4i tailCoreVec = new Vector4i(tailCoreColor, tailCoreColor, tailCoreColor, tailCoreColor);
                Render3DUtil.drawGlowTexture(tailEntry, glowTexture, -tailSize / 2, -tailSize / 2, tailSize, tailSize, tailCoreVec, useDepth);

                matrices.pop();
            }
        }

        if (useDepth) RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private int applyDamageFlash(int color) {
        if (!damageRed.isEnabled()) return color;

        float targetIntensity = 0f;
        long timeSinceDamage = System.currentTimeMillis() - lastDamageTime;

        if (timeSinceDamage < DAMAGE_FLASH_DURATION) {
            float progress = (float) timeSinceDamage / DAMAGE_FLASH_DURATION;
            targetIntensity = 1.0f - easeOutCubic(progress);
        }

        damageFlashIntensity = MathHelper.lerp(1f, damageFlashIntensity, targetIntensity);

        if (damageFlashIntensity < 0.05f) return color;

        int alpha = (color >> 24) & 0xFF;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        int finalRed = (int) MathHelper.lerp(damageFlashIntensity, red, 255);
        int finalGreen = (int) MathHelper.lerp(damageFlashIntensity, green, 50);
        int finalBlue = (int) MathHelper.lerp(damageFlashIntensity, blue, 50);

        return (alpha << 24) | (finalRed << 16) | (finalGreen << 8) | finalBlue;
    }
}
