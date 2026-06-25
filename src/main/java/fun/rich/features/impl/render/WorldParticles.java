package fun.rich.features.impl.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.events.player.TickEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.*;
import fun.rich.utils.client.Instance;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render3D;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.*;
import java.util.*;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class WorldParticles extends Module {

    static final float QUAD_FIX_ROT_Z = 90f;
    static final String[] THROW_TEX_MODES = new String[]{"Звезда", "Снежинка", "Корона", "Блум", "Сердце", "Молния"};

    public static WorldParticles getInstance() {
        return Instance.get(WorldParticles.class);
    }

    final MultiSelectSetting types = new MultiSelectSetting("Типы", "Выберите типы частиц")
            .value("Урон", "Мир", "Декоративные", "Бросок")
            .selected("Декоративные");

    final SelectSetting modetype = new SelectSetting("Мод", "Выберите тип партиклов")
            .value("2D", "3D")
            .selected("2D")
            .visible(() -> types.isSelected("Декоративные"));

    final SelectSetting particleType = new SelectSetting("Тип частиц", "Выбор типа")
            .value("Звезды", "Снег", "Блум", "Корона", "Сердце", "Молния")
            .selected("Звезды")
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("2D"));

    final SelectSetting particleType3D = new SelectSetting("Тип 3D", "Выбор эффекта")
            .value("Кристаллы", "Кубы", "Снег")
            .selected("Кристаллы")
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D"));

    final SelectSetting behavior3D = new SelectSetting("Поведение 3D", "Как спавнить и двигать 3D частицы")
            .value("Как Мир", "Статик")
            .selected("Как Мир")
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final BooleanSetting occlusion3D = new BooleanSetting("Окклюзия 3D", "Скрывать 3D за блоками")
            .setValue(true)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SliderSettings lifeTime3D = new SliderSettings("Время 3D", "Время жизни 3D частиц в мс")
            .range(600f, 12000f)
            .setValue(2600f)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег") && behavior3D.isSelected("Как Мир"));

    final SliderSettings drag3D = new SliderSettings("Демпф 3D", "Затухание скорости 3D (чем больше, тем плавнее)")
            .range(0.60f, 0.999f)
            .setValue(0.94f)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег") && behavior3D.isSelected("Как Мир"));

    final SliderSettings rot3D = new SliderSettings("Вращение 3D", "Сила вращения 3D")
            .range(0.0f, 2.0f)
            .setValue(1.0f)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SliderSettings bloom3D = new SliderSettings("Блум 3D", "Сила блум-свечения 3D")
            .range(0.0f, 2.0f)
            .setValue(1.0f)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SelectSetting snowMode = new SelectSetting("Режим снега", "Поведение снега")
            .value("Простой", "Взлет")
            .selected("Простой")
            .visible(this::isDecorSnowSelected);

    final BooleanSetting spawnFromGround = new BooleanSetting("От земли", "Спавн частиц от земли")
            .setValue(true)
            .visible(this::isDecor2DOrSnow3D);

    final BooleanSetting spawnOnlyInView = new BooleanSetting("Только в поле зрения", "Спавнить только в поле зрения камеры")
            .setValue(false)
            .visible(this::isDecor2DOrSnow3D);

    final BooleanSetting collision = new BooleanSetting("Коллизия", "Коллизия частиц")
            .setValue(true)
            .visible(this::isDecor2DOrSnow3D);

    final BooleanSetting scale = new BooleanSetting("Скейл", "Масштабирование по альфе")
            .setValue(true)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings particleCount = new SliderSettings("Количество", "Количество кристаллов/кубов в мире")
            .range(10, 200)
            .setValue(50)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SliderSettings range = new SliderSettings("Дальность", "Дальность спавна 3D от игрока")
            .range(8, 64)
            .setValue(32)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SliderSettings size = new SliderSettings("Размер 3D", "Размер кристаллов")
            .range(0.05F, 0.15F)
            .setValue(0.09F)
            .visible(() -> types.isSelected("Декоративные") && modetype.isSelected("3D") && !particleType3D.isSelected("Снег"));

    final SliderSettings maxParticles = new SliderSettings("Макс количество", "Максимальное количество частиц")
            .range(10, 200)
            .setValue(50)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings spawnRate = new SliderSettings("Спавн/сек", "Количество спавна частиц в секунду")
            .range(10f, 200f)
            .setValue(15f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings spawnHeight = new SliderSettings("Высота спавна", "Высота спавна частиц")
            .range(0.05f, 30f)
            .setValue(10f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings particleGravity = new SliderSettings("Гравитация", "Гравитация частиц")
            .range(-10f, 10f)
            .setValue(0f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings motionPower = new SliderSettings("Сила движения", "Сила движения частиц")
            .range(0.1f, 2f)
            .setValue(1f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings inclineX = new SliderSettings("Наклон X", "Наклон полёта по X")
            .range(-17.5f, 17.5f)
            .setValue(0f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings inclineZ = new SliderSettings("Наклон Z", "Наклон полёта по Z")
            .range(-17.5f, 17.5f)
            .setValue(0f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings particleSize = new SliderSettings("Размер 2D", "Размер частиц")
            .setValue(1.0f)
            .range(0.5f, 2.0f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings lifeTime = new SliderSettings("Время жизни", "Время жизни частиц в мс")
            .setValue(800f)
            .range(250f, 3000f)
            .visible(this::isDecor2DOrSnow3D);

    final SliderSettings spawnRange = new SliderSettings("Радиус спавна", "Радиус спавна")
            .setValue(25f)
            .range(10f, 50f)
            .visible(this::isDecor2DOrSnow3D);

    final ColorSetting particleColor = new ColorSetting("Цвет", "Цвет частиц")
            .value(new Color(255, 255, 255, 55).getRGB())
            .presets(
                    new Color(0, 246, 255, 255).getRGB(),
                    new Color(183, 1, 195, 255).getRGB(),
                    new Color(255, 60, 0, 255).getRGB(),
                    new Color(171, 253, 0, 255).getRGB()
            );

    final SelectSetting textureModeWorld = new SelectSetting("Текстура мира", "Выберите текстуру для мировых частиц")
            .value("Звезда", "Снежинка", "Корона", "Блум", "Сердце", "Молния")
            .selected("Звезда")
            .visible(() -> types.isSelected("Мир"));

    final SelectSetting textureModeDamage = new SelectSetting("Текстура урона", "Выберите текстуру для частиц урона")
            .value("Звезда", "Снежинка", "Корона", "Сердце", "Блум", "Молния")
            .selected("Звезда")
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings countDamage = new SliderSettings("Кол-во при уроне", "Количество частиц при уроне")
            .range(5, 50).setValue(20)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings countWorld = new SliderSettings("Кол-во в мире", "Количество частиц в мире")
            .range(2, 15).setValue(12)
            .visible(() -> types.isSelected("Мир"));

    final SliderSettings sizeDamage = new SliderSettings("Размер при уроне", "Размер частиц при уроне")
            .range(0.1f, 0.6f).setValue(0.3f)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings damageSizeRandom = new SliderSettings("Рандом размера", "Разброс размера частиц при уроне")
            .range(0f, 0.7f).setValue(0.15f)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings sizeWorld = new SliderSettings("Размер в мире", "Размер частиц в мире")
            .range(0.1f, 1.2f).setValue(1.1f)
            .visible(() -> types.isSelected("Мир"));

    final SliderSettings scatterForce = new SliderSettings("Сила разброса", "Сила разброса частиц")
            .range(0.1f, 0.5f).setValue(0.2f)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings particleLifeTime = new SliderSettings("Время жизни", "Время жизни частиц в мс")
            .range(500, 8000).setValue(4000)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings speedMultiplier = new SliderSettings("Множитель скорости", "Множитель скорости частиц")
            .range(0.1f, 3f).setValue(1.2f)
            .visible(() -> types.isSelected("Урон") || types.isSelected("Мир"));

    final SliderSettings damageGravity = new SliderSettings("Гравитация урона", "Гравитация частиц урона (вниз)")
            .range(0f, 2.5f).setValue(0.85f)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings damageDrag = new SliderSettings("Демпфирование урона", "Затухание скорости частиц урона")
            .range(0.60f, 0.995f).setValue(0.92f)
            .visible(() -> types.isSelected("Урон"));

    final BooleanSetting randomRotation = new BooleanSetting("Рандомный поворот", "Случайный поворот частиц")
            .setValue(true)
            .visible(() -> types.isSelected("Урон"));

    final SliderSettings decorCount = new SliderSettings("Кол-во декора", "Количество декоративных частиц (как Мир)")
            .range(2, 15).setValue(10)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorSize = new SliderSettings("Размер декора", "Размер декоративных частиц (как Мир)")
            .range(0.1f, 1.2f).setValue(1.0f)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorSpeed = new SliderSettings("Скорость декора", "Множитель скорости декора (как Мир)")
            .range(0.1f, 3.0f).setValue(1.2f)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorRadiusXZ = new SliderSettings("Радиус декора XZ", "Область спавна декора по XZ (как Мир)")
            .range(16f, 128f).setValue(48f)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorHeightY = new SliderSettings("Высота декора Y", "Область спавна декора по Y (как Мир)")
            .range(8f, 80f).setValue(46f)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorGravity = new SliderSettings("Гравитация декора", "Гравитация декоративных частиц (как Мир)")
            .range(0f, 0.02f).setValue(0.001f)
            .visible(this::isDecor2DNonSnow);

    final SliderSettings decorDrag = new SliderSettings("Демпфирование декора", "Затухание скорости декора (как Мир)")
            .range(0.60f, 0.995f).setValue(0.90f)
            .visible(this::isDecor2DNonSnow);

    final BooleanSetting decorRandomRotation = new BooleanSetting("Рандом поворот декора", "Случайный поворот декора (как Мир)")
            .setValue(true)
            .visible(this::isDecor2DNonSnow);

    final BooleanSetting decorOcclusion = new BooleanSetting("Окклюзия декора", "Скрывать декор за блоками")
            .setValue(false)
            .visible(this::isDecor2DNonSnow);

    final SelectSetting textureModeThrow = new SelectSetting("Текстура броска", "Текстура следа броска")
            .value("Звезда", "Снежинка", "Корона", "Блум", "Сердце", "Молния")
            .selected("Блум")
            .visible(() -> types.isSelected("Бросок"));

    final BooleanSetting throwRandomTexture = new BooleanSetting("Рандом текстура", "Случайная текстура у каждой частицы следа")
            .setValue(false)
            .visible(() -> types.isSelected("Бросок"));

    final BooleanSetting throwRandomColor = new BooleanSetting("Рандом цвет", "Случайный цвет у каждой частицы следа")
            .setValue(false)
            .visible(() -> types.isSelected("Бросок"));

    final SliderSettings throwStep = new SliderSettings("Шаг следа", "Дистанция между точками следа")
            .range(0.04f, 0.60f)
            .setValue(0.16f)
            .visible(() -> types.isSelected("Бросок"));

    final SliderSettings throwDensity = new SliderSettings("Плотность", "Интенсивность следа (меньше 1 = реже)")
            .range(0.05f, 4.0f)
            .setValue(1.0f)
            .visible(() -> types.isSelected("Бросок"));

    final SliderSettings throwSize = new SliderSettings("Размер следа", "Размер следа броска")
            .range(0.06f, 0.80f)
            .setValue(0.22f)
            .visible(() -> types.isSelected("Бросок"));

    final SliderSettings throwLife = new SliderSettings("Жизнь следа", "Время жизни следа в мс")
            .range(120, 2500)
            .setValue(520)
            .visible(() -> types.isSelected("Бросок"));

    final SliderSettings throwRange = new SliderSettings("Дальность броска", "Радиус отслеживания снарядов вокруг игрока")
            .range(12, 160)
            .setValue(72)
            .visible(() -> types.isSelected("Бросок"));

    final BooleanSetting throwOnlyMine = new BooleanSetting("Только мои", "След только от моих бросков")
            .setValue(true)
            .visible(() -> types.isSelected("Бросок"));

    final BooleanSetting throwOcclusion = new BooleanSetting("Окклюзия броска", "Скрывать след за блоками")
            .setValue(false)
            .visible(() -> types.isSelected("Бросок"));

    final BooleanSetting throwRandomRotation = new BooleanSetting("Рандом поворот броска", "Случайный поворот следа")
            .setValue(true)
            .visible(() -> types.isSelected("Бросок"));

    final List<WorldCrystal> crystalList = new ArrayList<>();
    final List<CubeParticle> cubeList = new ArrayList<>();
    final List<SnowFlake> snowList = new ArrayList<>();
    final List<DamageParticle> damageParticles = new ArrayList<>();
    final List<WorldParticle> worldParticles = new ArrayList<>();
    final List<DecorWorldParticle> decorWorldParticles = new ArrayList<>();
    final List<TrailParticle> throwParticles = new ArrayList<>();
    final Map<Integer, Vec3d> projectileLastPos = new HashMap<>();

    final Random random = new Random();

    String last3DMode = "Кристаллы";
    int previousParticleCount;
    long lastSpawnTime;
    long lastSnowSpawnTime;

    long lastDamageSpawnMs;
    int lastHurtEntityId = -1;
    int lastHurtTime = 0;

    long lastFrameNs = System.nanoTime();
    float cachedFrameDt = 1f / 60f;

    public WorldParticles() {
        super("WorldParticles", "World Particles", ModuleCategory.RENDER);
        setup(
                types,
                modetype,
                particleType,
                particleType3D,
                behavior3D,
                occlusion3D,
                lifeTime3D,
                drag3D,
                rot3D,
                bloom3D,
                snowMode,
                spawnFromGround,
                spawnOnlyInView,
                collision,
                scale,
                particleCount,
                range,
                size,
                maxParticles,
                spawnRate,
                spawnHeight,
                particleGravity,
                motionPower,
                inclineX,
                inclineZ,
                particleSize,
                lifeTime,
                spawnRange,
                particleColor,
                textureModeWorld,
                textureModeDamage,
                countDamage,
                countWorld,
                sizeDamage,
                damageSizeRandom,
                sizeWorld,
                scatterForce,
                particleLifeTime,
                speedMultiplier,
                damageGravity,
                damageDrag,
                randomRotation,
                decorCount,
                decorSize,
                decorSpeed,
                decorRadiusXZ,
                decorHeightY,
                decorGravity,
                decorDrag,
                decorRandomRotation,
                decorOcclusion,
                textureModeThrow,
                throwRandomTexture,
                throwRandomColor,
                throwStep,
                throwDensity,
                throwSize,
                throwLife,
                throwRange,
                throwOnlyMine,
                throwOcclusion,
                throwRandomRotation
        );
        previousParticleCount = particleCount.getInt();
        lastSpawnTime = System.currentTimeMillis();
        lastSnowSpawnTime = System.currentTimeMillis();
        lastDamageSpawnMs = 0L;
    }

    @Override
    public void activate() {
        super.activate();
        crystalList.clear();
        cubeList.clear();
        snowList.clear();
        damageParticles.clear();
        worldParticles.clear();
        decorWorldParticles.clear();
        throwParticles.clear();
        projectileLastPos.clear();
        lastSpawnTime = System.currentTimeMillis();
        lastSnowSpawnTime = System.currentTimeMillis();
        lastDamageSpawnMs = 0L;
        lastHurtEntityId = -1;
        lastHurtTime = 0;

        if (types.isSelected("Декоративные") && modetype.getSelected().equals("3D")) {
            String sel = particleType3D.getSelected();
            last3DMode = sel;
            if (!"Снег".equals(sel)) {
                if ("Статик".equals(behavior3D.getSelected())) {
                    if ("Кубы".equals(sel)) generateCubesStatic();
                    else if ("Кристаллы".equals(sel)) generateCrystalsStatic();
                } else {
                    warmup3DWorldLike(sel);
                }
            }
        }

        previousParticleCount = particleCount.getInt();
    }

    @Override
    public void deactivate() {
        super.deactivate();
        crystalList.clear();
        cubeList.clear();
        snowList.clear();
        damageParticles.clear();
        worldParticles.clear();
        decorWorldParticles.clear();
        throwParticles.clear();
        projectileLastPos.clear();
    }

    @EventHandler
    public void onTick(TickEvent e) {
        if (mc.player == null || mc.world == null) return;

        if (types.isSelected("Урон")) {
            handleDamageClickSpawns();
            trySpawnDamageOnHurt();
        }

        if (types.isSelected("Бросок")) {
            tickThrowTrail();
        } else {
            throwParticles.clear();
            projectileLastPos.clear();
        }
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.player == null) return;

        updateFrameDt();

        if (types.isSelected("Декоративные")) {
            if (modetype.getSelected().equals("3D")) {
                String sel = particleType3D.getSelected();

                if (!sel.equals(last3DMode)) {
                    crystalList.clear();
                    cubeList.clear();
                    snowList.clear();
                    decorWorldParticles.clear();
                    previousParticleCount = particleCount.getInt();
                    lastSpawnTime = System.currentTimeMillis();
                    lastSnowSpawnTime = System.currentTimeMillis();

                    if (!"Снег".equals(sel)) {
                        if ("Статик".equals(behavior3D.getSelected())) {
                            if ("Кубы".equals(sel)) generateCubesStatic();
                            else if ("Кристаллы".equals(sel)) generateCrystalsStatic();
                        } else {
                            warmup3DWorldLike(sel);
                        }
                    }

                    last3DMode = sel;
                }

                if ("Снег".equals(sel)) {
                    updateSnow(e.getStack());
                } else {
                    if ("Статик".equals(behavior3D.getSelected())) {
                        if ("Кубы".equals(sel)) {
                            int currentCount = particleCount.getInt();
                            updateCubesStatic();
                            ensureCubeCountStatic(currentCount);
                            previousParticleCount = currentCount;
                            renderCubes(e.getStack());
                        } else {
                            int currentCount = particleCount.getInt();
                            if (currentCount != previousParticleCount) {
                                adjustCrystalCountStatic(currentCount);
                                previousParticleCount = currentCount;
                            }
                            updateCrystalsStatic();
                            renderCrystals(e.getStack());
                        }
                    } else {
                        update3DWorldLike(sel);
                        if ("Кубы".equals(sel)) renderCubes(e.getStack());
                        else renderCrystals(e.getStack());
                    }
                }
            } else {
                if (particleType.getSelected().equals("Снег")) {
                    updateSnow(e.getStack());
                } else {
                    updateDecorAsWorld();
                    renderDecorAsWorld(e.getStack());
                }
            }
        }

        if (types.isSelected("Мир")) {
            Iterator<WorldParticle> worldIterator = worldParticles.iterator();
            while (worldIterator.hasNext()) {
                WorldParticle particle = worldIterator.next();
                if (particle.tick()) worldIterator.remove();
            }

            float con = countWorld.getValue() * 100f;
            while (worldParticles.size() < con) {
                boolean drop = false;
                worldParticles.add(new WorldParticle(
                        (float) (mc.player.getX() + random.nextFloat() * 96f - 48f),
                        (float) (mc.player.getY() + random.nextFloat() * 46f + 2f),
                        (float) (mc.player.getZ() + random.nextFloat() * 96f - 48f),
                        drop ? 0 : random.nextFloat() * 0.8f - 0.4f,
                        drop ? random.nextFloat() * 0.15f - 0.2f : random.nextFloat() * 0.2f - 0.1f,
                        drop ? 0 : random.nextFloat() * 0.8f - 0.4f
                ));
            }

            if (!worldParticles.isEmpty()) {
                renderWorldParticles(e.getStack());
            }
        }

        if (types.isSelected("Урон")) {
            Iterator<DamageParticle> it = damageParticles.iterator();
            while (it.hasNext()) {
                DamageParticle p = it.next();
                if (p.tick()) it.remove();
            }
            if (!damageParticles.isEmpty()) {
                renderDamageParticles(e.getStack());
            }
        }

        if (types.isSelected("Бросок")) {
            if (!throwParticles.isEmpty()) {
                renderThrowParticles(e.getStack());
            }
        }
    }

    private void updateFrameDt() {
        long now = System.nanoTime();
        long d = now - lastFrameNs;
        lastFrameNs = now;
        float dt = d / 1_000_000_000f;
        if (dt < 0.001f) dt = 0.001f;
        if (dt > 0.05f) dt = 0.05f;
        cachedFrameDt = dt;
    }

    private boolean isDecor2DNonSnow() {
        return types.isSelected("Декоративные") && modetype.isSelected("2D") && !particleType.isSelected("Снег");
    }

    private float frameDt() {
        return cachedFrameDt;
    }

    private static float smooth01(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    private void handleDamageClickSpawns() {
        int loops = 0;
        while (mc.options.attackKey.wasPressed() && loops++ < 8) {
            HitResult hr = mc.crosshairTarget;
            if (!(hr instanceof EntityHitResult ehr)) continue;
            Entity ent = ehr.getEntity();
            if (!(ent instanceof LivingEntity le)) continue;

            long now = System.currentTimeMillis();
            if (now - lastDamageSpawnMs < 20L) continue;

            Vec3d p = le.getPos().add(0.0, le.getHeight() * 0.6, 0.0);
            spawnDamageParticlesAtHit(p);
            lastDamageSpawnMs = now;
        }
    }

    private void trySpawnDamageOnHurt() {
        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof EntityHitResult ehr)) {
            lastHurtEntityId = -1;
            lastHurtTime = 0;
            return;
        }

        Entity ent = ehr.getEntity();
        if (!(ent instanceof LivingEntity le)) {
            lastHurtEntityId = -1;
            lastHurtTime = 0;
            return;
        }

        int id = ent.getId();
        int ht = le.hurtTime;

        if (id != lastHurtEntityId) {
            lastHurtEntityId = id;
            lastHurtTime = ht;
            return;
        }

        if (ht > lastHurtTime && ht > 0) {
            long now = System.currentTimeMillis();
            if (now - lastDamageSpawnMs >= 20L) {
                Vec3d p = le.getPos().add(0.0, le.getHeight() * 0.6, 0.0);
                spawnDamageParticlesAtHit(p);
                lastDamageSpawnMs = now;
            }
        }

        lastHurtTime = ht;
    }

    private boolean isDecorSnowSelected() {
        if (!types.isSelected("Декоративные")) return false;
        if (modetype.isSelected("2D")) return particleType.isSelected("Снег");
        return modetype.isSelected("3D") && particleType3D.isSelected("Снег");
    }

    private boolean isSnow3DDecor() {
        return types.isSelected("Декоративные") && modetype.isSelected("3D") && particleType3D.isSelected("Снег");
    }

    private boolean isDecor2DOrSnow3D() {
        return types.isSelected("Декоративные") && (modetype.isSelected("2D") || isSnow3DDecor());
    }

    private void spawnDamageParticlesAtHit(Vec3d hitPos) {
        int count = (int) countDamage.getValue();
        float sizeRnd = damageSizeRandom.getValue();

        for (int i = 0; i < count; i++) {
            float offsetX = (random.nextFloat() - 0.5f) * 0.18f;
            float offsetY = (random.nextFloat() - 0.5f) * 0.22f;
            float offsetZ = (random.nextFloat() - 0.5f) * 0.18f;

            float motionX = (random.nextFloat() * 2f - 1f) * scatterForce.getValue() * speedMultiplier.getValue();
            float motionZ = (random.nextFloat() * 2f - 1f) * scatterForce.getValue() * speedMultiplier.getValue();
            float motionY = (random.nextFloat() * 0.35f) * speedMultiplier.getValue();
            float rotation = randomRotation.isValue() ? random.nextFloat() * 360f : 0f;

            float sizeMul = 1f;
            if (sizeRnd > 0f) sizeMul = Math.max(0.15f, 1f + (random.nextFloat() * 2f - 1f) * sizeRnd);

            float hue = random.nextFloat();
            int color = Color.HSBtoRGB(hue, 0.8f, 1.0f);

            damageParticles.add(new DamageParticle(
                    (float) (hitPos.x + offsetX),
                    (float) (hitPos.y + offsetY),
                    (float) (hitPos.z + offsetZ),
                    motionX, motionY, motionZ,
                    (long) particleLifeTime.getValue(),
                    color, rotation, sizeMul
            ));
        }
    }

    private void tickThrowTrail() {
        long now = System.currentTimeMillis();
        throwParticles.removeIf(p -> p.isDead(now));

        double rr = throwRange.getValue();
        Box box = mc.player.getBoundingBox().expand(rr, rr, rr);

        List<ProjectileEntity> list = mc.world.getEntitiesByClass(ProjectileEntity.class, box, this::isThrowProjectile);
        if (list.isEmpty()) {
            projectileLastPos.clear();
            return;
        }

        Set<Integer> alive = new HashSet<>(list.size() * 2);
        for (ProjectileEntity pe : list) {
            if (throwOnlyMine.isValue()) {
                Entity owner = pe.getOwner();
                if (owner != mc.player) continue;
            }

            int id = pe.getId();
            alive.add(id);

            Vec3d cur = pe.getPos();
            Vec3d last = projectileLastPos.get(id);
            projectileLastPos.put(id, cur);

            if (last == null) continue;

            double dist = cur.distanceTo(last);
            if (dist < 0.001) continue;

            double step = Math.max(0.02, throwStep.getValue());
            Vec3d dir = cur.subtract(last).normalize();

            float dens = (float) throwDensity.getValue();
            if (dens <= 0f) continue;

            for (double d = 0.0; d <= dist; d += step) {
                Vec3d base = last.add(dir.multiply(d));

                int spawn = calcDensityCount(dens);
                if (spawn <= 0) continue;

                for (int k = 0; k < spawn; k++) {
                    double jx = (random.nextDouble() - 0.5) * 0.05;
                    double jy = (random.nextDouble() - 0.5) * 0.05;
                    double jz = (random.nextDouble() - 0.5) * 0.05;
                    float rot = throwRandomRotation.isValue() ? random.nextFloat() * 360f : 0f;
                    throwParticles.add(new TrailParticle(base.add(jx, jy, jz), now, (long) throwLife.getValue(), rot));
                }
            }
        }

        projectileLastPos.keySet().retainAll(alive);
    }

    private int calcDensityCount(float dens) {
        if (dens <= 0f) return 0;
        int whole = (int) Math.floor(dens);
        float frac = dens - whole;
        int add = random.nextFloat() < frac ? 1 : 0;
        return whole + add;
    }

    private boolean isThrowProjectile(ProjectileEntity e) {
        if (e == null) return false;
        if (e.isRemoved()) return false;
        if (e.age < 1) return true;
        Vec3d v = e.getVelocity();
        return v != null && v.lengthSquared() > 0.0008;
    }

    private void renderThrowParticles(MatrixStack stack) {
        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        long now = System.currentTimeMillis();
        float s = throwSize.getValue();

        if (!throwRandomTexture.isValue()) {
            String texturePath = getTexturePath(textureModeThrow.getSelected());
            RenderSystem.setShaderTexture(0, Identifier.of(texturePath));

            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            for (TrailParticle p : throwParticles) {
                if (throwOcclusion.isValue() && isBlockOccluding(p.pos)) continue;

                float t = (now - p.birth) / (float) p.life;
                float a = 1f - smooth01(t);
                if (a <= 0f) continue;

                int col = colorForThrow(p, a);
                stack.push();
                applyBillboard(stack, camera, p.pos, p.rot);

                Matrix4f m = stack.peek().getPositionMatrix();
                float hs = s * 0.5f;

                bb.vertex(m, -hs, -hs, 0).texture(0f, 1f).color(col);
                bb.vertex(m, hs, -hs, 0).texture(1f, 1f).color(col);
                bb.vertex(m, hs, hs, 0).texture(1f, 0f).color(col);
                bb.vertex(m, -hs, hs, 0).texture(0f, 0f).color(col);

                stack.pop();
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
        } else {
            for (int tex = 0; tex < THROW_TEX_MODES.length; tex++) {
                String texturePath = getTexturePath(THROW_TEX_MODES[tex]);
                RenderSystem.setShaderTexture(0, Identifier.of(texturePath));

                BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
                for (TrailParticle p : throwParticles) {
                    if (throwOcclusion.isValue() && isBlockOccluding(p.pos)) continue;

                    float t = (now - p.birth) / (float) p.life;
                    float a = 1f - smooth01(t);
                    if (a <= 0f) continue;

                    if (throwTexIndex(p) != tex) continue;

                    int col = colorForThrow(p, a);
                    stack.push();
                    applyBillboard(stack, camera, p.pos, p.rot);

                    Matrix4f m = stack.peek().getPositionMatrix();
                    float hs = s * 0.5f;

                    bb.vertex(m, -hs, -hs, 0).texture(0f, 1f).color(col);
                    bb.vertex(m, hs, -hs, 0).texture(1f, 1f).color(col);
                    bb.vertex(m, hs, hs, 0).texture(1f, 0f).color(col);
                    bb.vertex(m, -hs, hs, 0).texture(0f, 0f).color(col);

                    stack.pop();
                }
                BufferRenderer.drawWithGlobalProgram(bb.end());
            }
        }

        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private int throwTexIndex(TrailParticle p) {
        int h = hashTrail(p);
        return Math.floorMod(h, THROW_TEX_MODES.length);
    }

    private int colorForThrow(TrailParticle p, float alpha01) {
        int a = Math.max(0, Math.min(255, (int) (255f * alpha01)));

        if (!throwRandomColor.isValue()) {
            return ColorAssist.setAlpha(particleColor.getColor(), a);
        }

        int h = hashTrail(p);
        float hue = (h & 0xFFFF) / 65535f;
        int rgb = Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0x00FFFFFF;
        return (a << 24) | rgb;
    }

    private int hashTrail(TrailParticle p) {
        long x = Double.doubleToLongBits(p.pos.x);
        long y = Double.doubleToLongBits(p.pos.y);
        long z = Double.doubleToLongBits(p.pos.z);
        long h = p.birth * 0x9E3779B97F4A7C15L;
        h ^= x + 0x9E3779B97F4A7C15L + (h << 6) + (h >> 2);
        h ^= y + 0x9E3779B97F4A7C15L + (h << 6) + (h >> 2);
        h ^= z + 0x9E3779B97F4A7C15L + (h << 6) + (h >> 2);
        int v = (int) (h ^ (h >>> 32));
        v ^= v >>> 16;
        v *= 0x7feb352d;
        v ^= v >>> 15;
        v *= 0x846ca68b;
        v ^= v >>> 16;
        return v;
    }

    private void updateDecorAsWorld() {
        Iterator<DecorWorldParticle> it = decorWorldParticles.iterator();
        while (it.hasNext()) {
            DecorWorldParticle p = it.next();
            if (p.tick()) it.remove();
        }

        float target = decorCount.getValue() * 100f;
        while (decorWorldParticles.size() < target) {
            decorWorldParticles.add(spawnDecorWorldParticle());
        }
    }

    private DecorWorldParticle spawnDecorWorldParticle() {
        float rxz = decorRadiusXZ.getValue();
        float ry = decorHeightY.getValue();

        float px = (float) mc.player.getX();
        float py = (float) mc.player.getY();
        float pz = (float) mc.player.getZ();

        float x = px + random.nextFloat() * (rxz * 2f) - rxz;
        float y = py + 2f + random.nextFloat() * ry;
        float z = pz + random.nextFloat() * (rxz * 2f) - rxz;

        float mx = random.nextFloat() * 0.8f - 0.4f;
        float my = random.nextFloat() * 0.2f - 0.1f;
        float mz = random.nextFloat() * 0.8f - 0.4f;

        float rot = decorRandomRotation.isValue() ? random.nextFloat() * 360f : 0f;
        return new DecorWorldParticle(x, y, z, mx, my, mz, rot);
    }

    private void renderDecorAsWorld(MatrixStack stack) {
        if (decorWorldParticles.isEmpty() || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        Identifier tex = Identifier.of(getTexturePath(particleType.getSelected()));
        RenderSystem.setShaderTexture(0, tex);

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (DecorWorldParticle p : decorWorldParticles) {
            p.render(stack, bufferBuilder, camera);
        }

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderWorldParticles(MatrixStack stack) {
        if (worldParticles.isEmpty() || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        String texturePath = getTexturePath(textureModeWorld.getSelected());
        RenderSystem.setShaderTexture(0, Identifier.of(texturePath));

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (WorldParticle particle : worldParticles) {
            particle.render(stack, bufferBuilder, camera);
        }

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderDamageParticles(MatrixStack stack) {
        if (damageParticles.isEmpty() || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        String texturePath = getTexturePath(textureModeDamage.getSelected());
        RenderSystem.setShaderTexture(0, Identifier.of(texturePath));

        BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (DamageParticle particle : damageParticles) {
            particle.render(stack, bufferBuilder, camera);
        }

        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    private String getTexturePath(String mode) {
        switch (mode) {
            case "Снежинка":
            case "Снег":
                return "textures/particles/show1.png";
            case "Блум":
                return "textures/particles/glow.png";
            case "Корона":
                return "textures/particles/core1.png";
            case "Сердце":
                return "textures/particles/heart1.png";
            case "Молния":
                return "textures/particles/light.png";
            case "Звезда":
            case "Звезды":
            default:
                return "textures/particles/star1.png";
        }
    }

    private void updateSnow(MatrixStack stack) {
        long now = System.currentTimeMillis();

        snowList.removeIf(p -> p.isDead(now) || p.pos.squaredDistanceTo(mc.player.getPos()) > (60.0 * 60.0));

        double spawnInterval = 1000.0 / Math.max(1.0, spawnRate.getValue());
        if (now - lastSnowSpawnTime >= spawnInterval && snowList.size() < maxParticles.getInt()) {
            spawnSnow();
            lastSnowSpawnTime = now;
        }

        for (SnowFlake p : snowList) {
            p.update(now);
        }

        renderSnow(stack);
    }

    private void spawnSnow() {
        Vec3d playerPos = mc.player.getPos();

        double distMax = Math.max(5.0, spawnRange.getValue());
        double dist = 5.0 + random.nextDouble() * (distMax - 5.0);
        double yaw = Math.toRadians(random.nextDouble() * 360.0);

        double xOff = -Math.sin(yaw) * dist;
        double zOff = Math.cos(yaw) * dist;

        double yMin;
        double yMax;

        if (snowMode.getSelected().equals("Взлет")) {
            yMin = -5.0;
            yMax = 0.0;
        } else {
            yMin = 3.0;
            yMax = Math.max(4.0, spawnHeight.getValue());
        }

        double yOff = yMin + random.nextDouble() * (yMax - yMin);

        Vec3d pos = new Vec3d(playerPos.x + xOff, playerPos.y + yOff, playerPos.z + zOff);

        if (spawnOnlyInView.isValue() && !isInPlayerView(pos)) return;

        BlockPos bp = BlockPos.ofFloored(pos);
        if (!mc.world.getBlockState(bp).isAir()) return;

        int base = particleColor.getColor();
        snowList.add(new SnowFlake(pos, new Vec3d(0, 0, 0), base));
    }

    private static void applyBillboard(MatrixStack ms, Camera camera, Vec3d worldPos, float rotZDeg) {
        ms.translate(worldPos.x, worldPos.y, worldPos.z);
        ms.multiply(camera.getRotation());
        if (rotZDeg != 0f) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZDeg));
    }

    private void renderSnow(MatrixStack stack) {
        if (snowList.isEmpty() || mc.player == null) return;

        Camera camera = mc.gameRenderer.getCamera();
        Identifier textureId = Identifier.of("textures/particles/show1.png");

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

        for (SnowFlake p : snowList) {
            int alpha = p.getAlpha();
            if (alpha <= 0) continue;

            if (isBlockOccluding(p.pos)) continue;

            float s = 0.22f * particleSize.getValue();
            float big = s * 1.6f;

            int glow = (alpha << 24) | (p.color & 0x00FFFFFF);
            int core = (alpha << 24) | 0x00FFFFFF;

            stack.push();
            applyBillboard(stack, camera, p.pos, QUAD_FIX_ROT_Z);

            Render3D.drawTexture(
                    stack.peek(),
                    textureId,
                    -big / 2.0f,
                    -big / 2.0f,
                    big,
                    big,
                    new org.joml.Vector4i(glow),
                    true
            );

            Render3D.drawTexture(
                    stack.peek(),
                    textureId,
                    -s / 2.0f,
                    -s / 2.0f,
                    s,
                    s,
                    new org.joml.Vector4i(core),
                    true
            );

            stack.pop();
        }

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
    }

    private class SnowFlake {
        Vec3d pos;
        Vec3d delta;
        final int color;
        final long birth;
        int alpha;

        SnowFlake(Vec3d pos, Vec3d delta, int color) {
            this.pos = pos;
            this.delta = delta;
            this.color = color;
            this.birth = System.currentTimeMillis();
            this.alpha = 0;
        }

        boolean isDead(long now) {
            return now - birth >= 5000L;
        }

        int getAlpha() {
            return alpha;
        }

        void update(long now) {
            long age = now - birth;

            int wave = (int) ((((Math.sin(age / 250.0) + 1.0) * 0.5)) * 255.0);
            if (wave < 0) wave = 0;
            if (wave > 255) wave = 255;

            int fadeIn = 150;
            int fadeOut = 150;

            int a1 = 255;
            if (age < fadeIn) a1 = (int) (255.0 * (age / (double) fadeIn));

            long left = 5000L - age;
            int a2 = 255;
            if (left < fadeOut) a2 = (int) (255.0 * (left / (double) fadeOut));

            int a = wave;
            if (a1 < a) a = a1;
            if (a2 < a) a = a2;
            if (a < 0) a = 0;
            if (a > 255) a = 255;
            this.alpha = a;

            double jitter = 0.002 * motionPower.getValue();

            double dx = delta.x + (random.nextDouble() - 0.5) * jitter;
            double dz = delta.z + (random.nextDouble() - 0.5) * jitter;

            double dy;
            if (snowMode.getSelected().equals("Взлет")) {
                dy = 0.03;
            } else {
                dy = delta.y + (random.nextDouble() - 0.5) * (jitter * 0.5) - 0.008;
            }

            dy += (particleGravity.getValue() / 8000.0) * motionPower.getValue();

            double maxSpeed = 0.3;
            dx = net.minecraft.util.math.MathHelper.clamp(dx, -maxSpeed, maxSpeed);
            dy = net.minecraft.util.math.MathHelper.clamp(dy, -maxSpeed, maxSpeed);
            dz = net.minecraft.util.math.MathHelper.clamp(dz, -maxSpeed, maxSpeed);

            Vec3d nd = new Vec3d(dx, dy, dz);

            if (collision.isValue() && mc.world != null) {
                BlockPos bz = BlockPos.ofFloored(pos.x, pos.y, pos.z + nd.z);
                if (!mc.world.getBlockState(bz).isAir()) nd = new Vec3d(nd.x, nd.y, -nd.z * 0.8);

                BlockPos by = BlockPos.ofFloored(pos.x, pos.y + nd.y, pos.z);
                if (!mc.world.getBlockState(by).isAir()) nd = new Vec3d(nd.x * 0.999, -nd.y * 0.7, nd.z * 0.999);

                BlockPos bx = BlockPos.ofFloored(pos.x + nd.x, pos.y, pos.z);
                if (!mc.world.getBlockState(bx).isAir()) nd = new Vec3d(-nd.x * 0.8, nd.y, nd.z);
            }

            this.delta = nd;
            this.pos = this.pos.add(this.delta);
        }
    }

    private boolean isBlockOccluding(Vec3d targetPos) {
        if (mc.world == null || mc.player == null) return false;

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d start = camera.getPos();

        if (start.squaredDistanceTo(targetPos) <= 1.0E-8) return false;

        HitResult hr = mc.world.raycast(new RaycastContext(
                start,
                targetPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (hr.getType() == HitResult.Type.MISS) return false;

        double hitSq = hr.getPos().squaredDistanceTo(start);
        double tgtSq = targetPos.squaredDistanceTo(start);
        return hitSq + 1.0E-6 < tgtSq;
    }

    private Vec3d getCameraLookVec() {
        Camera camera = mc.gameRenderer.getCamera();
        return Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
    }

    private boolean isInPlayerView(Vec3d pos) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        Vec3d look = getCameraLookVec();
        Vec3d toParticle = pos.subtract(camPos).normalize();
        return look.dotProduct(toParticle) > 0.1;
    }

    private void warmup3DWorldLike(String sel) {
        crystalList.clear();
        cubeList.clear();
        lastSpawnTime = System.currentTimeMillis();
        int target = Math.min(particleCount.getInt(), Math.max(1, maxParticles.getInt()));
        int warm = Math.min(target, Math.max(8, target / 3));
        if ("Кубы".equals(sel)) {
            for (int i = 0; i < warm; i++) spawnCubeWorldLike();
        } else {
            for (int i = 0; i < warm; i++) spawnCrystalWorldLike();
        }
    }

    private void update3DWorldLike(String sel) {
        long now = System.currentTimeMillis();

        float dt = frameDt();
        float step = dt * 60f;

        if ("Кубы".equals(sel)) {
            for (int i = cubeList.size() - 1; i >= 0; i--) {
                CubeParticle p = cubeList.get(i);
                p.tickWorldLike(step);
                if (p.isDead()) cubeList.remove(i);
            }
        } else {
            for (int i = crystalList.size() - 1; i >= 0; i--) {
                WorldCrystal c = crystalList.get(i);
                c.tickWorldLike(step);
                if (c.isDead()) crystalList.remove(i);
            }
        }

        int target = Math.min(particleCount.getInt(), Math.max(1, maxParticles.getInt()));
        long interval = (long) Math.max(1.0, 1000.0 / Math.max(1.0, spawnRate.getValue()));

        while (now - lastSpawnTime >= interval) {
            lastSpawnTime += interval;

            if ("Кубы".equals(sel)) {
                if (cubeList.size() < target) spawnCubeWorldLike();
            } else {
                if (crystalList.size() < target) spawnCrystalWorldLike();
            }
        }

        if ("Кубы".equals(sel)) {
            while (cubeList.size() < target) spawnCubeWorldLike();
            while (cubeList.size() > target) cubeList.remove(cubeList.size() - 1);
        } else {
            while (crystalList.size() < target) spawnCrystalWorldLike();
            while (crystalList.size() > target) crystalList.remove(crystalList.size() - 1);
        }
    }

    private Vec3d random3DSpawnPos() {
        Vec3d p = mc.player.getPos();
        double r = Math.max(4.0, range.getValue());
        double a = random.nextDouble() * (Math.PI * 2.0);
        double d = 3.0 + random.nextDouble() * (r - 3.0);
        double x = p.x + Math.cos(a) * d;
        double z = p.z + Math.sin(a) * d;

        double yBase = p.y + 1.0 + random.nextDouble() * Math.max(1.0, spawnHeight.getValue());
        if (spawnFromGround.isValue()) {
            BlockPos top = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, p.y, z));
            yBase = top.getY() + 0.2 + random.nextDouble() * Math.max(0.6, spawnHeight.getValue());
        }

        Vec3d pos = new Vec3d(x, yBase, z);

        if (spawnOnlyInView.isValue() && !isInPlayerView(pos)) {
            int tries = 0;
            while (tries++ < 10) {
                a = random.nextDouble() * (Math.PI * 2.0);
                d = 3.0 + random.nextDouble() * (r - 3.0);
                x = p.x + Math.cos(a) * d;
                z = p.z + Math.sin(a) * d;

                yBase = p.y + 1.0 + random.nextDouble() * Math.max(1.0, spawnHeight.getValue());
                if (spawnFromGround.isValue()) {
                    BlockPos top = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(x, p.y, z));
                    yBase = top.getY() + 0.2 + random.nextDouble() * Math.max(0.6, spawnHeight.getValue());
                }

                pos = new Vec3d(x, yBase, z);
                if (isInPlayerView(pos)) break;
            }
        }

        return pos;
    }

    private Vec3d random3DVelocity() {
        float power = motionPower.getValue();
        float ix = inclineX.getValue();
        float iz = inclineZ.getValue();

        double vx = (random.nextDouble() * 2.0 - 1.0) * 0.045 * power + (ix / 35.0) * 0.02;
        double vy = (random.nextDouble() * 2.0 - 1.0) * 0.02 * power;
        double vz = (random.nextDouble() * 2.0 - 1.0) * 0.045 * power + (iz / 35.0) * 0.02;

        return new Vec3d(vx, vy, vz);
    }

    private void spawnCrystalWorldLike() {
        Vec3d pos = random3DSpawnPos();
        if (!mc.world.getBlockState(BlockPos.ofFloored(pos)).isAir()) return;

        Vec3d vel = random3DVelocity();
        Vec3d rot = new Vec3d(random.nextDouble() * 360.0, random.nextDouble() * 360.0, random.nextDouble() * 360.0);

        WorldCrystal c = new WorldCrystal(pos, vel, rot);
        c.birth = System.currentTimeMillis();
        c.life = (long) lifeTime3D.getValue();
        c.fadeAlpha = 0.0f;
        c.rotationSpeed = (0.5f + random.nextFloat() * 1.5f) * rot3D.getValue();
        c.rotVel = new Vec3d(
                (random.nextDouble() * 2.0 - 1.0) * 0.06 * rot3D.getValue(),
                (random.nextDouble() * 2.0 - 1.0) * 0.06 * rot3D.getValue(),
                (random.nextDouble() * 2.0 - 1.0) * 0.06 * rot3D.getValue()
        );
        crystalList.add(c);
    }

    private void spawnCubeWorldLike() {
        Vec3d pos = random3DSpawnPos();
        if (!mc.world.getBlockState(BlockPos.ofFloored(pos)).isAir()) return;

        Vec3d motion = random3DVelocity().multiply(30.0);
        Vec3d rot = new Vec3d(
                random.nextDouble() * 6.283185307179586,
                random.nextDouble() * 6.283185307179586,
                random.nextDouble() * 6.283185307179586
        );

        Vec3d rotMotion = new Vec3d(
                (random.nextDouble() - 0.5) * 0.08 * rot3D.getValue(),
                (random.nextDouble() - 0.5) * 0.08 * rot3D.getValue(),
                (random.nextDouble() - 0.5) * 0.08 * rot3D.getValue()
        );

        long life = (long) lifeTime3D.getValue();
        float mul = 0.8f + random.nextFloat() * 0.6f;

        cubeList.add(new CubeParticle(pos, rot, motion, rotMotion, life, mul));
    }

    private void renderCrystals(MatrixStack ms) {
        if (mc.player == null || crystalList.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);

        for (WorldCrystal crystal : crystalList) {
            if (crystal.fadeAlpha <= 0) continue;

            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            Vec3d renderPos = crystal.prevPosition.lerp(crystal.position, tickDelta);

            if (occlusion3D.isValue() && isBlockOccluding(renderPos)) continue;

            ms.push();
            ms.translate(renderPos.x, renderPos.y, renderPos.z);

            float pulsation = 1.0f + (float) (Math.sin(System.currentTimeMillis() / 500.0) * 0.1f);
            ms.scale(pulsation, pulsation, pulsation);

            float selfRotation = (System.currentTimeMillis() % 36000) / 100.0f * crystal.rotationSpeed;
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) crystal.rotation.x));
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) crystal.rotation.y + selfRotation));
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) crystal.rotation.z));

            crystal.render(ms, particleColor.getColor(), camera, size.getValue(), (8.0f * bloom3D.getValue()));

            ms.pop();
        }
    }

    private void renderCubes(MatrixStack ms) {
        if (mc.player == null || cubeList.isEmpty()) return;

        Camera camera = mc.gameRenderer.getCamera();
        float tickDelta = mc.getRenderTickCounter().getTickDelta(false);

        int baseColor = particleColor.getColor();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        RenderSystem.setShaderTexture(0, Identifier.of("textures/features/particles/bloom.png"));
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        BufferBuilder bloom = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (CubeParticle p : cubeList) {
            float a = p.alpha();
            if (a <= 0f) continue;

            Vec3d pos = p.prevPos.lerp(p.pos, tickDelta);
            if (occlusion3D.isValue() && isBlockOccluding(pos)) continue;

            float s = (size.getValue() * 6.0f) * p.sizeMul;
            float big = (4.0f * s) * bloom3D.getValue();
            int c = withAlpha(baseColor, a * 0.4f);

            ms.push();
            ms.translate(pos.x, pos.y, pos.z);
            ms.multiply(camera.getRotation());
            drawImage(ms, bloom, -big / 2.0f, -big / 2.0f, big, big, c);
            ms.pop();
        }

        BufferRenderer.drawWithGlobalProgram(bloom.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (CubeParticle p : cubeList) {
            float a = p.alpha();
            if (a <= 0f) continue;

            Vec3d pos = p.prevPos.lerp(p.pos, tickDelta);
            if (occlusion3D.isValue() && isBlockOccluding(pos)) continue;

            Vec3d rot = p.prevRot.lerp(p.rot, tickDelta);

            int diag = withAlpha(baseColor, a * 0.35f);
            int out = withAlpha(baseColor, a * 0.8f);

            ms.push();
            ms.translate(pos.x, pos.y, pos.z);
            ms.multiply(new Quaternionf().rotationXYZ((float) rot.x, (float) rot.y, (float) rot.z));

            float s = (size.getValue() * 6.0f) * p.sizeMul;
            ms.scale(s, s, s);

            Box box = new Box(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);
            renderBoxInternalDiagonals(ms, lines, box, diag);
            renderOutlinedBox(ms, lines, box, out);

            ms.pop();
        }

        BufferRenderer.drawWithGlobalProgram(lines.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static int withAlpha(int rgba, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        return (a << 24) | (rgba & 0x00FFFFFF);
    }

    private static void drawImage(MatrixStack ms, BufferBuilder builder, float x, float y, float w, float h, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();
        builder.vertex(m, x, y + h, 0).texture(0, 1).color(color);
        builder.vertex(m, x + w, y + h, 0).texture(1, 1).color(color);
        builder.vertex(m, x + w, y, 0).texture(1, 0).color(color);
        builder.vertex(m, x, y, 0).texture(0, 0).color(color);
    }

    private static void renderBoxInternalDiagonals(MatrixStack ms, BufferBuilder builder, Box box, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();

        builder.vertex(m, (float) box.minX, (float) box.minY, (float) box.minZ).color(color);
        builder.vertex(m, (float) box.maxX, (float) box.maxY, (float) box.maxZ).color(color);

        builder.vertex(m, (float) box.maxX, (float) box.minY, (float) box.minZ).color(color);
        builder.vertex(m, (float) box.minX, (float) box.maxY, (float) box.maxZ).color(color);

        builder.vertex(m, (float) box.minX, (float) box.minY, (float) box.maxZ).color(color);
        builder.vertex(m, (float) box.maxX, (float) box.maxY, (float) box.minZ).color(color);

        builder.vertex(m, (float) box.maxX, (float) box.minY, (float) box.maxZ).color(color);
        builder.vertex(m, (float) box.minX, (float) box.maxY, (float) box.minZ).color(color);
    }

    private static void renderOutlinedBox(MatrixStack ms, BufferBuilder builder, Box box, int color) {
        Matrix4f m = ms.peek().getPositionMatrix();

        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        builder.vertex(m, x1, y1, z1).color(color);
        builder.vertex(m, x2, y1, z1).color(color);

        builder.vertex(m, x2, y1, z1).color(color);
        builder.vertex(m, x2, y1, z2).color(color);

        builder.vertex(m, x2, y1, z2).color(color);
        builder.vertex(m, x1, y1, z2).color(color);

        builder.vertex(m, x1, y1, z2).color(color);
        builder.vertex(m, x1, y1, z1).color(color);

        builder.vertex(m, x1, y2, z1).color(color);
        builder.vertex(m, x2, y2, z1).color(color);

        builder.vertex(m, x2, y2, z1).color(color);
        builder.vertex(m, x2, y2, z2).color(color);

        builder.vertex(m, x2, y2, z2).color(color);
        builder.vertex(m, x1, y2, z2).color(color);

        builder.vertex(m, x1, y2, z2).color(color);
        builder.vertex(m, x1, y2, z1).color(color);

        builder.vertex(m, x1, y1, z1).color(color);
        builder.vertex(m, x1, y2, z1).color(color);

        builder.vertex(m, x2, y1, z1).color(color);
        builder.vertex(m, x2, y2, z1).color(color);

        builder.vertex(m, x2, y1, z2).color(color);
        builder.vertex(m, x2, y2, z2).color(color);

        builder.vertex(m, x1, y1, z2).color(color);
        builder.vertex(m, x1, y2, z2).color(color);
    }

    private void adjustCrystalCountStatic(int targetCount) {
        int currentSize = crystalList.size();
        if (targetCount > currentSize) addCrystalsStatic(targetCount - currentSize);
        else if (targetCount < currentSize) markCrystalsForRemovalStatic(currentSize - targetCount);
    }

    private void addCrystalsStatic(int count) {
        Vec3d playerPos = mc.player.getPos();
        float rangeValue = range.getValue();
        for (int i = 0; i < count; i++) {
            Vec3d position;
            int attempts = 0;
            do {
                double x = playerPos.x + (random.nextDouble() - 0.5) * 2 * rangeValue;
                double y = playerPos.y + (random.nextDouble() - 0.5) * rangeValue;
                double z = playerPos.z + (random.nextDouble() - 0.5) * 2 * rangeValue;
                position = new Vec3d(x, y, z);
                attempts++;
            } while (!isInPlayerView(position) && attempts < 20);
            Vec3d velocity = new Vec3d(
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02
            );
            Vec3d rotation = new Vec3d(
                    random.nextDouble() * 360,
                    random.nextDouble() * 360,
                    random.nextDouble() * 360
            );
            crystalList.add(new WorldCrystal(position, velocity, rotation));
        }
    }

    private void markCrystalsForRemovalStatic(int count) {
        int marked = 0;
        for (WorldCrystal crystal : crystalList) {
            if (marked >= count) break;
            if (!crystal.markedForDeath && !crystal.isFadingOut) {
                crystal.markedForDeath = true;
                crystal.isFadingOut = true;
                marked++;
            }
        }
    }

    private void generateCrystalsStatic() {
        crystalList.clear();
        Vec3d playerPos = mc.player.getPos();
        int count = particleCount.getInt();
        float rangeValue = range.getValue();
        for (int i = 0; i < count; i++) {
            Vec3d position;
            int attempts = 0;
            do {
                double x = playerPos.x + (random.nextDouble() - 0.5) * 2 * rangeValue;
                double y = playerPos.y + (random.nextFloat() - 0.5) * rangeValue;
                double z = playerPos.z + (random.nextDouble() - 0.5) * 2 * rangeValue;
                position = new Vec3d(x, y, z);
                attempts++;
            } while (!isInPlayerView(position) && attempts < 20);
            Vec3d velocity = new Vec3d(
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02,
                    (random.nextDouble() - 0.5) * 0.02
            );
            Vec3d rotation = new Vec3d(
                    random.nextDouble() * 360,
                    random.nextDouble() * 360,
                    random.nextDouble() * 360
            );
            crystalList.add(new WorldCrystal(position, velocity, rotation));
        }
    }

    private void updateCrystalsStatic() {
        Vec3d playerPos = mc.player.getPos();
        float rangeValue = range.getValue();
        float fadeSpeedValue = 0.05f;

        Iterator<WorldCrystal> iterator = crystalList.iterator();
        while (iterator.hasNext()) {
            WorldCrystal crystal = iterator.next();
            crystal.prevPosition = crystal.position;
            crystal.position = crystal.position.add(crystal.velocity);

            boolean isOccluded = isBlockOccluding(crystal.position);
            boolean inView = isInPlayerView(crystal.position);

            if (crystal.markedForDeath) {
                crystal.fadeAlpha -= fadeSpeedValue;
                if (crystal.fadeAlpha <= 0) {
                    iterator.remove();
                    continue;
                }
            } else {
                if (isOccluded || !inView) {
                    if (!crystal.isFadingOut) crystal.isFadingOut = true;
                } else {
                    if (crystal.isFadingOut) crystal.isFadingOut = false;
                }

                if (crystal.isFadingOut) {
                    crystal.fadeAlpha -= fadeSpeedValue;
                    if (crystal.fadeAlpha <= 0) {
                        crystal.fadeAlpha = 0;
                        Vec3d newPosition;
                        int attempts = 0;
                        do {
                            double x = playerPos.x + (random.nextDouble() - 0.5) * 2 * rangeValue;
                            double y = playerPos.y + (random.nextDouble() - 0.5) * rangeValue;
                            double z = playerPos.z + (random.nextDouble() - 0.5) * 2 * rangeValue;
                            newPosition = new Vec3d(x, y, z);
                            attempts++;
                        } while (!isInPlayerView(newPosition) && attempts < 20);
                        crystal.position = newPosition;
                        crystal.prevPosition = crystal.position;
                        crystal.isFadingOut = false;
                    }
                } else {
                    crystal.fadeAlpha += fadeSpeedValue;
                    if (crystal.fadeAlpha > 1.0f) crystal.fadeAlpha = 1.0f;
                }

                if (crystal.position.distanceTo(playerPos) > rangeValue * 1.5) {
                    Vec3d newPosition;
                    int attempts = 0;
                    do {
                        double x = playerPos.x + (random.nextDouble() - 0.5) * 2 * rangeValue;
                        double y = playerPos.y + (random.nextDouble() - 0.5) * rangeValue;
                        double z = playerPos.z + (random.nextDouble() - 0.5) * 2 * rangeValue;
                        newPosition = new Vec3d(x, y, z);
                        attempts++;
                    } while (!isInPlayerView(newPosition) && attempts < 20);
                    crystal.position = newPosition;
                    crystal.prevPosition = crystal.position;
                    crystal.fadeAlpha = 0;
                    crystal.isFadingOut = false;
                }
            }
        }
    }

    private void generateCubesStatic() {
        cubeList.clear();
        addCubesStatic(particleCount.getInt());
    }

    private void ensureCubeCountStatic(int target) {
        int cur = cubeList.size();
        if (target > cur) {
            addCubesStatic(target - cur);
            return;
        }
        if (target < cur) {
            for (int i = cur - 1; i >= target; i--) cubeList.remove(i);
        }
    }

    private void addCubesStatic(int count) {
        Vec3d playerPos = mc.player.getPos();
        float r = range.getValue();

        for (int i = 0; i < count; i++) {
            double x = playerPos.x + (random.nextDouble() - 0.5) * 2.0 * r;
            double y = playerPos.y + random.nextDouble() * 5.0;
            double z = playerPos.z + (random.nextDouble() - 0.5) * 2.0 * r;

            Vec3d pos = new Vec3d(x, y, z);

            Vec3d motion = new Vec3d(
                    (random.nextDouble() - 0.5) * 2.0,
                    random.nextDouble() * 2.0,
                    (random.nextDouble() - 0.5) * 2.0
            );

            Vec3d rot = new Vec3d(
                    random.nextDouble() * 6.283185307179586,
                    random.nextDouble() * 6.283185307179586,
                    random.nextDouble() * 6.283185307179586
            );

            Vec3d rotMotion = new Vec3d(
                    (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.08,
                    (random.nextDouble() - 0.5) * 0.08
            );

            long life = 1500L + random.nextInt(3000);
            float mul = 0.8f + random.nextFloat() * 0.6f;

            cubeList.add(new CubeParticle(pos, rot, motion, rotMotion, life, mul));
        }
    }

    private void updateCubesStatic() {
        for (int i = cubeList.size() - 1; i >= 0; i--) {
            CubeParticle p = cubeList.get(i);
            p.tickStatic();
            if (p.isDead()) cubeList.remove(i);
        }
    }

    private class CubeParticle {
        Vec3d prevPos;
        Vec3d prevRot;
        Vec3d pos;
        Vec3d rot;
        Vec3d motion;
        Vec3d rotMotion;
        long birth;
        long life;
        float sizeMul;

        CubeParticle(Vec3d pos, Vec3d rot, Vec3d motion, Vec3d rotMotion, long life, float sizeMul) {
            this.pos = pos;
            this.rot = rot;
            this.motion = motion.multiply(0.04);
            this.rotMotion = rotMotion.multiply(0.04);
            this.life = life;
            this.sizeMul = sizeMul;
            this.prevPos = pos;
            this.prevRot = rot;
            this.birth = System.currentTimeMillis();
        }

        void tickStatic() {
            this.prevPos = this.pos;
            this.prevRot = this.rot;
            this.pos = this.pos.add(this.motion);
            this.rot = this.rot.add(this.rotMotion);
            this.motion = this.motion.multiply(0.98);
            this.rotMotion = this.rotMotion.multiply(0.98);
        }

        void tickWorldLike(float step) {
            this.prevPos = this.pos;
            this.prevRot = this.rot;

            Vec3d v = this.motion.multiply(0.001);
            double gy = -(particleGravity.getValue() / 9000.0) * motionPower.getValue();

            v = v.add(0.0, gy * step, 0.0);

            double drag = drag3D.getValue();
            double dragPow = Math.pow(drag, step);
            v = v.multiply(dragPow);

            Vec3d next = this.pos.add(v.multiply(step * 1.35));

            if (collision.isValue()) {
                BlockPos bx = BlockPos.ofFloored(next.x, this.pos.y, this.pos.z);
                if (!mc.world.getBlockState(bx).isAir()) v = new Vec3d(-v.x * 0.75, v.y, v.z);

                BlockPos by = BlockPos.ofFloored(this.pos.x, next.y, this.pos.z);
                if (!mc.world.getBlockState(by).isAir()) v = new Vec3d(v.x * 0.985, -v.y * 0.65, v.z * 0.985);

                BlockPos bz = BlockPos.ofFloored(this.pos.x, this.pos.y, next.z);
                if (!mc.world.getBlockState(bz).isAir()) v = new Vec3d(v.x, v.y, -v.z * 0.75);
            }

            this.pos = this.pos.add(v.multiply(step * 1.35));
            this.motion = v.multiply(1000.0);

            Vec3d rv = this.rotMotion.multiply(0.04 * rot3D.getValue());
            this.rot = this.rot.add(rv.multiply(step));
            this.rotMotion = this.rotMotion.multiply(Math.pow(0.985, step));
        }

        boolean isDead() {
            return System.currentTimeMillis() - birth >= life;
        }

        float alpha() {
            long t = System.currentTimeMillis() - birth;
            long fadeIn = 220L;
            long fadeOut = 260L;

            if (t < 0) return 0f;
            if (t < fadeIn) return Math.min(1f, t / (float) fadeIn);

            long left = life - t;
            if (left < fadeOut) return Math.max(0f, left / (float) fadeOut);

            return 1f;
        }
    }

    private static class WorldCrystal {
        Vec3d position;
        Vec3d prevPosition;
        Vec3d velocity;
        Vec3d rotation;
        float rotationSpeed;
        float fadeAlpha;
        boolean isFadingOut;
        boolean markedForDeath;

        long birth = 0L;
        long life = 0L;
        Vec3d rotVel = new Vec3d(0, 0, 0);

        public WorldCrystal(Vec3d position, Vec3d velocity, Vec3d rotation) {
            this.position = position;
            this.prevPosition = position;
            this.velocity = velocity;
            this.rotation = rotation;
            this.rotationSpeed = 0.5f + (float) (Math.random() * 1.5f);
            this.fadeAlpha = 0.0f;
            this.isFadingOut = false;
            this.markedForDeath = false;
        }

        void tickWorldLike(float step) {
            this.prevPosition = this.position;

            long now = System.currentTimeMillis();
            long age = now - birth;
            float t = life <= 0 ? 0f : Math.max(0f, Math.min(1f, age / (float) life));

            float fadeIn = 0.18f;
            float fadeOut = 0.22f;

            float a = 1f;
            if (t < fadeIn) a = t / fadeIn;
            if (t > 1f - fadeOut) a = Math.min(a, (1f - t) / fadeOut);
            if (a < 0f) a = 0f;
            if (a > 1f) a = 1f;

            this.fadeAlpha = a;

            double dragPow = Math.pow(WorldParticles.getInstance().drag3D.getValue(), step);
            Vec3d vel = this.velocity.multiply(dragPow);

            double gy = -(WorldParticles.getInstance().particleGravity.getValue() / 9000.0) * WorldParticles.getInstance().motionPower.getValue();
            vel = vel.add(0.0, gy * step, 0.0);

            Vec3d next = this.position.add(vel.multiply(step));

            if (WorldParticles.getInstance().collision.isValue() && WorldParticles.getInstance().mc.world != null) {
                BlockPos bx = BlockPos.ofFloored(next.x, this.position.y, this.position.z);
                if (!WorldParticles.getInstance().mc.world.getBlockState(bx).isAir()) vel = new Vec3d(-vel.x * 0.75, vel.y, vel.z);

                BlockPos by = BlockPos.ofFloored(this.position.x, next.y, this.position.z);
                if (!WorldParticles.getInstance().mc.world.getBlockState(by).isAir()) vel = new Vec3d(vel.x * 0.985, -vel.y * 0.65, vel.z * 0.985);

                BlockPos bz = BlockPos.ofFloored(this.position.x, this.position.y, next.z);
                if (!WorldParticles.getInstance().mc.world.getBlockState(bz).isAir()) vel = new Vec3d(vel.x, vel.y, -vel.z * 0.75);
            }

            this.position = this.position.add(vel.multiply(step));
            this.velocity = vel;

            this.rotation = this.rotation.add(this.rotVel.multiply(step));
        }

        boolean isDead() {
            if (life <= 0L) return false;
            long now = System.currentTimeMillis();
            return now - birth >= life;
        }

        public void render(MatrixStack ms, int baseColor, Camera camera, float size, float bloomSizeMultiplier) {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            drawCrystal(ms, baseColor, size);

            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            drawCrystal(ms, baseColor, size);

            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);

            ms.push();
            ms.scale(1.2f, 1.2f, 1.2f);
            drawCrystal(ms, baseColor, size);
            ms.pop();

            drawBloomSphere(ms, baseColor, camera, size, bloomSizeMultiplier);

            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
        }

        private void drawBloomSphere(MatrixStack ms, int baseColor, Camera camera, float size, float bloomSizeMultiplier) {
            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            RenderSystem.setShaderTexture(0, Identifier.of("textures/features/particles/bloom.png"));
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
            RenderSystem.depthMask(false);

            int bloomColor = ColorAssist.setAlpha(baseColor, (int) (15 * fadeAlpha));
            float bloomSize = size * bloomSizeMultiplier;
            int segments = 8;

            for (int i = 0; i < segments; i++) {
                ms.push();
                float angle = (360.0f / segments) * i;
                ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
                ms.multiply(camera.getRotation());

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

        private void drawCrystal(MatrixStack ms, int baseColor, float size) {
            BufferBuilder bufferBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

            float s = size;
            float hPrism = size * 1f;
            float hPyramid = size * 1.5f;
            int numSides = 8;

            List<Vec3d> topVertices = new ArrayList<>();
            List<Vec3d> bottomVertices = new ArrayList<>();

            for (int i = 0; i < numSides; i++) {
                float ang = (float) (2 * Math.PI * i / numSides);
                float x = (float) (s * Math.cos(ang));
                float z = (float) (s * Math.sin(ang));
                topVertices.add(new Vec3d(x, hPrism / 2, z));
                bottomVertices.add(new Vec3d(x, -hPrism / 2, z));
            }

            Vec3d vTop = new Vec3d(0, hPrism / 2 + hPyramid, 0);
            Vec3d vBottom = new Vec3d(0, -hPrism / 2 - hPyramid, 0);

            int finalColor = ColorAssist.setAlpha(baseColor, (int) (55 * fadeAlpha));

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                Vec3d v3 = topVertices.get((i + 1) % numSides);
                Vec3d v4 = topVertices.get(i);
                drawTriangle(ms, bufferBuilder, v1, v2, v3, finalColor);
                drawTriangle(ms, bufferBuilder, v1, v3, v4, finalColor);
            }

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = topVertices.get(i);
                Vec3d v2 = topVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vTop, v1, v2, finalColor);
            }

            for (int i = 0; i < numSides; i++) {
                Vec3d v1 = bottomVertices.get(i);
                Vec3d v2 = bottomVertices.get((i + 1) % numSides);
                drawTriangle(ms, bufferBuilder, vBottom, v2, v1, finalColor);
            }

            BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
        }

        private void drawTriangle(MatrixStack ms, BufferBuilder bb, Vec3d v1, Vec3d v2, Vec3d v3, int color) {
            Matrix4f m = ms.peek().getPositionMatrix();
            bb.vertex(m, (float) v1.x, (float) v1.y, (float) v1.z).color(color);
            bb.vertex(m, (float) v2.x, (float) v2.y, (float) v2.z).color(color);
            bb.vertex(m, (float) v3.x, (float) v3.y, (float) v3.z).color(color);
        }
    }

    class DamageParticle {
        private float prevPosX, prevPosY, prevPosZ;
        private float posX, posY, posZ;
        private float motionX, motionY, motionZ;
        private final long createdTime;
        private final long maxAge;
        private final int color;
        private final float rotation;
        private final float sizeMul;

        public DamageParticle(float posX, float posY, float posZ, float motionX, float motionY, float motionZ,
                              long maxAge, int color, float rotation, float sizeMul) {
            this.posX = this.prevPosX = posX;
            this.posY = this.prevPosY = posY;
            this.posZ = this.prevPosZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.createdTime = System.currentTimeMillis();
            this.maxAge = maxAge;
            this.color = color;
            this.rotation = rotation;
            this.sizeMul = sizeMul;
        }

        public boolean tick() {
            long now = System.currentTimeMillis();
            if (now - createdTime > maxAge) return true;

            float dt = frameDt();
            float step = dt * 60f;

            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;

            posX += motionX * step;
            posY += motionY * step;
            posZ += motionZ * step;

            float drag = damageDrag.getValue();
            float dragPow = (float) Math.pow(drag, step);

            motionX *= dragPow;
            motionY *= dragPow;
            motionZ *= dragPow;

            motionY -= damageGravity.getValue() * dt;

            return false;
        }

        public void render(MatrixStack stack, BufferBuilder bufferBuilder, Camera camera) {
            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            Vec3d worldPos = new Vec3d(
                    prevPosX + (posX - prevPosX) * tickDelta,
                    prevPosY + (posY - prevPosY) * tickDelta,
                    prevPosZ + (posZ - prevPosZ) * tickDelta
            );

            stack.push();
            applyBillboard(stack, camera, worldPos, rotation);

            float ageProgress = (System.currentTimeMillis() - createdTime) / (float) maxAge;
            float inv = 1f - ageProgress;
            float alpha = smooth01(inv);
            int colorWithAlpha = ColorAssist.setAlpha(color, (int) (255 * alpha));
            float s = sizeDamage.getValue() * sizeMul;

            Matrix4f matrix = stack.peek().getPositionMatrix();
            float hs = s * 0.5f;

            bufferBuilder.vertex(matrix, -hs, -hs, 0).texture(0f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, -hs, 0).texture(1f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, hs, 0).texture(1f, 0f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, -hs, hs, 0).texture(0f, 0f).color(colorWithAlpha);

            stack.pop();
        }
    }

    class WorldParticle {
        private float prevPosX, prevPosY, prevPosZ;
        private float posX, posY, posZ;
        private float motionX, motionY, motionZ;
        private int age, maxAge;

        public WorldParticle(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            this.posX = this.prevPosX = posX;
            this.posY = this.prevPosY = posY;
            this.posZ = this.prevPosZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.age = random.nextInt(200) + 100;
            this.maxAge = this.age;
        }

        public boolean tick() {
            if (mc.player.squaredDistanceTo(posX, posY, posZ) > 4096) age -= 8;
            else age--;

            if (age < 0) return true;

            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;

            float dt = frameDt();
            float step = dt * 60f;

            posX += motionX * speedMultiplier.getValue() * step;
            posY += motionY * speedMultiplier.getValue() * step;
            posZ += motionZ * speedMultiplier.getValue() * step;

            float dragPow = (float) Math.pow(0.90f, step);
            motionX *= dragPow;
            motionY *= dragPow;
            motionZ *= dragPow;

            motionY -= 0.06f * dt;

            return false;
        }

        public void render(MatrixStack stack, BufferBuilder bufferBuilder, Camera camera) {
            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            Vec3d worldPos = new Vec3d(
                    prevPosX + (posX - prevPosX) * tickDelta,
                    prevPosY + (posY - prevPosY) * tickDelta,
                    prevPosZ + (posZ - prevPosZ) * tickDelta
            );

            stack.push();
            applyBillboard(stack, camera, worldPos, 0f);

            float ageProgress = (float) age / (float) maxAge;
            float alpha = smooth01(ageProgress);
            int color = particleColor.getColor();
            int colorWithAlpha = ColorAssist.setAlpha(color, (int) (255 * alpha));
            float s = sizeWorld.getValue();
            float hs = s * 0.5f;

            Matrix4f matrix = stack.peek().getPositionMatrix();
            bufferBuilder.vertex(matrix, -hs, -hs, 0).texture(0f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, -hs, 0).texture(1f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, hs, 0).texture(1f, 0f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, -hs, hs, 0).texture(0f, 0f).color(colorWithAlpha);

            stack.pop();
        }
    }

    class DecorWorldParticle {
        private float prevPosX, prevPosY, prevPosZ;
        private float posX, posY, posZ;
        private float motionX, motionY, motionZ;
        private int age, maxAge;
        private final float rotZ;

        public DecorWorldParticle(float posX, float posY, float posZ, float motionX, float motionY, float motionZ, float rotZ) {
            this.posX = this.prevPosX = posX;
            this.posY = this.prevPosY = posY;
            this.posZ = this.prevPosZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.age = random.nextInt(200) + 100;
            this.maxAge = this.age;
            this.rotZ = rotZ;
        }

        public boolean tick() {
            if (mc.player.squaredDistanceTo(posX, posY, posZ) > 4096) age -= 8;
            else age--;

            if (age < 0) return true;

            prevPosX = posX;
            prevPosY = posY;
            prevPosZ = posZ;

            float dt = frameDt();
            float step = dt * 60f;

            posX += motionX * decorSpeed.getValue() * step;
            posY += motionY * decorSpeed.getValue() * step;
            posZ += motionZ * decorSpeed.getValue() * step;

            float drag = decorDrag.getValue();
            float dragPow = (float) Math.pow(drag, step);

            motionX *= dragPow;
            motionY *= dragPow;
            motionZ *= dragPow;

            motionY -= decorGravity.getValue() * step;

            return false;
        }

        public void render(MatrixStack stack, BufferBuilder bufferBuilder, Camera camera) {
            float tickDelta = mc.getRenderTickCounter().getTickDelta(false);
            Vec3d worldPos = new Vec3d(
                    prevPosX + (posX - prevPosX) * tickDelta,
                    prevPosY + (posY - prevPosY) * tickDelta,
                    prevPosZ + (posZ - prevPosZ) * tickDelta
            );

            if (decorOcclusion.isValue() && isBlockOccluding(worldPos)) return;

            stack.push();
            applyBillboard(stack, camera, worldPos, rotZ);

            float ageProgress = (float) age / (float) maxAge;
            float alpha = smooth01(ageProgress);
            int color = particleColor.getColor();
            int colorWithAlpha = ColorAssist.setAlpha(color, (int) (255 * alpha));
            float s = decorSize.getValue();
            float hs = s * 0.5f;

            Matrix4f matrix = stack.peek().getPositionMatrix();
            bufferBuilder.vertex(matrix, -hs, -hs, 0).texture(0f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, -hs, 0).texture(1f, 1f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, hs, hs, 0).texture(1f, 0f).color(colorWithAlpha);
            bufferBuilder.vertex(matrix, -hs, hs, 0).texture(0f, 0f).color(colorWithAlpha);

            stack.pop();
        }
    }

    static class TrailParticle {
        final Vec3d pos;
        final long birth;
        final long life;
        final float rot;

        TrailParticle(Vec3d pos, long birth, long life, float rot) {
            this.pos = pos;
            this.birth = birth;
            this.life = life;
            this.rot = rot;
        }

        boolean isDead(long now) {
            return now - birth >= life;
        }
    }
}