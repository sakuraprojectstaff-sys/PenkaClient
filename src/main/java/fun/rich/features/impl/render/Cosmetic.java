package fun.rich.features.impl.render;

import fun.rich.display.hud.Notifications;
import fun.rich.events.keyboard.KeyEvent;
import fun.rich.events.render.WorldRenderEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ColorSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.client.sound.SoundManager;
import fun.rich.utils.display.color.ColorAssist;
import lombok.experimental.NonFinal;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Cosmetic extends Module {

    final Identifier defaultTexture = Identifier.of("textures/features/particles/bloom.png");

    final BooleanSetting spheresEnabled = new BooleanSetting("Сферы", "Включить сферы").setValue(false);
    final BooleanSetting haloEnabled = new BooleanSetting("Нимб", "Включить Нимб").setValue(false);
    final BooleanSetting bodyTrailEnabled = new BooleanSetting("Линия", "Включить Линию тела").setValue(false);

    final BooleanSetting petEnabled = new BooleanSetting("Питомец", "Питомец").setValue(false);

    final BooleanSetting narutoEnabled = new BooleanSetting("Naruto", "Наруто бег").setValue(false);

    final BooleanSetting playerAnimEnabled = new BooleanSetting("Анимации", "Анимации игрока").setValue(false);

    final SelectSetting playerAnimType = new SelectSetting("Анимация", "Выбор анимации")
            .value("Теневые клоны")
            .selected("Теневые клоны")
            .visible(this::playerAnimOn);

    final BindSetting playerAnimBind = new BindSetting("Бинд анимации", "Запуск анимации")
            .visible(this::playerAnimOn);

    @NonFinal boolean playerAnimRequested = false;

    final SelectSetting petSkin = new SelectSetting("Тип питомца", "Выбор питомца")
            .value(
                    "Летучая мышь",
                    "Попугай",
                    "Ворон",
                    "Фея",
                    "Пчела",
                    "Векс",
                    "Лисичка",
                    "Свинья",
                    "Лягушка",
                    "Иглобрюх",
                    "Слайм"
            )
            .selected("Попугай")
            .visible(this::petOn);

    final BooleanSetting petFirstPerson = new BooleanSetting("Питомец от 1-го лица", "Рисовать питомца от первого лица")
            .setValue(true)
            .visible(this::petOn);

    final BindSetting ravenSceneBind = new BindSetting("Техника Вороньего Клонирования", "Карасу Буншин но Дзюцу")
            .visible(this::ravenUiOn);

    @NonFinal boolean ravenSceneRequested = false;

    final BooleanSetting haloOthers = new BooleanSetting("Нимб на других", "Рисовать Нимб на других игроках").setValue(false).visible(this::haloOn);
    final BooleanSetting haloFirstPerson = new BooleanSetting("Нимб от 1-го лица", "Рисовать Нимб от первого лица").setValue(true).visible(this::haloOn);

    final SliderSettings haloDistance = new SliderSettings("Дистанция Нимба", "Макс. дистанция отрисовки Нимба")
            .setValue(72).range(8, 192)
            .visible(this::haloOn);

    final SelectSetting haloQuality = new SelectSetting("Качество Нимба", "Детализация Нимба")
            .value("Низкое", "Среднее", "Высокое", "Ультра")
            .selected("Среднее")
            .visible(this::haloOn);

    final ColorSetting haloColor = new ColorSetting("Цвет Нимба", "Цвет Нимба")
            .value(ColorAssist.getColor(255, 255, 255, 255))
            .visible(this::haloOn);

    final BooleanSetting haloAnimate = new BooleanSetting("Анимация Нимба", "Плавное покачивание Нимба")
            .setValue(true)
            .visible(this::haloOn);

    final SliderSettings haloRadius = new SliderSettings("Радиус Нимба", "Большой радиус Нимба")
            .setValue(0.36f).range(0.18f, 0.90f)
            .visible(this::haloOn);

    final SliderSettings haloThickness = new SliderSettings("Толщина Нимба", "Толщина трубки Нимба")
            .setValue(0.06f).range(0.02f, 0.16f)
            .visible(this::haloOn);

    final SliderSettings haloYOffset = new SliderSettings("Смещение Нимба по Y", "Смещение Нимба по высоте")
            .setValue(0.10f).range(-0.60f, 0.60f)
            .visible(this::haloOn);

    final SelectSetting mode = new SelectSetting("Режим", "Режим движения сфер")
            .value("Статичный", "Орбита", "Спираль", "Спираль V2", "Волны")
            .selected("Статичный")
            .visible(this::spheresOn);

    final SelectSetting effect = new SelectSetting("Эффект", "Доп. эффект")
            .value("Нет", "След", "Огненный след")
            .selected("След")
            .visible(this::spheresOn);

    final ColorSetting exort = new ColorSetting("Экзорт", "Цвет сферы")
            .value(ColorAssist.getColor(255, 200, 100, 200))
            .visible(this::spheresOn);

    final ColorSetting wex = new ColorSetting("Векс", "Цвет сферы")
            .value(ColorAssist.getColor(255, 150, 255, 200))
            .visible(this::spheresOn);

    final ColorSetting quas = new ColorSetting("Квас", "Цвет сферы")
            .value(ColorAssist.getColor(150, 150, 255, 200))
            .visible(this::spheresOn);

    final SliderSettings size = new SliderSettings("Размер", "Размер сферы")
            .setValue(0.12f).range(0.05f, 0.22f)
            .visible(this::spheresOn);

    final SliderSettings radius = new SliderSettings("Радиус", "Радиус орбиты сфер")
            .setValue(0.42f).range(0.18f, 1.25f)
            .visible(this::spheresOn);

    final SliderSettings height = new SliderSettings("Высота", "Смещение по Y")
            .setValue(0.05f).range(-1.20f, 3.50f)
            .visible(this::spheresOn);

    final SliderSettings speed = new SliderSettings("Скорость", "Скорость анимации")
            .setValue(1.85f).range(0.2f, 4.0f)
            .visible(() -> spheresOn() && !mode.isSelected("Статичный"));

    final SliderSettings breathe = new SliderSettings("Пульсация", "Пульсация радиуса")
            .setValue(0.08f).range(0.0f, 0.30f)
            .visible(this::spheresOn);

    final SliderSettings spiralHeight = new SliderSettings("Высота спирали", "Размах спирали по Y")
            .setValue(1.25f).range(0.2f, 6.0f)
            .visible(() -> spheresOn() && (mode.isSelected("Спираль") || mode.isSelected("Спираль V2")));

    final SliderSettings waveAmp = new SliderSettings("Амплитуда волн", "Амплитуда волн по Y")
            .setValue(0.22f).range(0.0f, 1.2f)
            .visible(() -> spheresOn() && mode.isSelected("Волны"));

    final SliderSettings waveFreq = new SliderSettings("Частота волн", "Частота волн")
            .setValue(2.4f).range(0.6f, 8.0f)
            .visible(() -> spheresOn() && mode.isSelected("Волны"));

    final SelectSetting sphereEditor = new SelectSetting("Редактор", "Какую сферу редактировать")
            .value("Экзорт", "Векс", "Квас", "Все")
            .selected("Все")
            .visible(this::spheresOn);

    final SliderSettings editLR = new SliderSettings("Смещение Лево/Право", "Смещение по правой оси")
            .setValue(0.0f).range(-1.50f, 1.50f)
            .visible(this::spheresOn);

    final SliderSettings editFB = new SliderSettings("Смещение Вперёд/Назад", "Смещение по оси вперёд/назад")
            .setValue(0.0f).range(-1.50f, 1.50f)
            .visible(this::spheresOn);

    final SliderSettings trailLen = new SliderSettings("Длина следа", "Количество точек")
            .setValue(18).range(6, 48)
            .visible(() -> spheresOn() && !effect.isSelected("Нет"));

    final SliderSettings trailPower = new SliderSettings("Сила свечения", "Интенсивность свечения")
            .setValue(1.10f).range(0.2f, 2.2f)
            .visible(() -> spheresOn() && !effect.isSelected("Нет"));

    final BooleanSetting self = new BooleanSetting("На себя", "Рисовать на себе").setValue(true).visible(this::spheresOn);
    final BooleanSetting others = new BooleanSetting("На других", "Рисовать на других").setValue(false).visible(this::spheresOn);
    final BooleanSetting firstPerson = new BooleanSetting("От 1-го лица", "Рисовать от 1-го лица").setValue(true).visible(this::spheresOn);

    final SelectSetting bodyTrailMode = new SelectSetting("Режим Линияа", "Стиль Линияа")
            .value("Статичный", "Градиент")
            .selected("Статичный")
            .visible(this::bodyTrailOn);

    final ColorSetting bodyTrailColorA = new ColorSetting("Цвет Линияа", "Цвет Линияа")
            .value(ColorAssist.getColor(255, 255, 255, 200))
            .visible(this::bodyTrailOn);

    final ColorSetting bodyTrailColorB = new ColorSetting("Цвет Линияа 2", "Второй цвет градиента")
            .value(ColorAssist.getColor(255, 140, 255, 200))
            .visible(() -> bodyTrailOn() && bodyTrailMode.isSelected("Градиент"));

    final SliderSettings bodyTrailPoints = new SliderSettings("Точки Линияа", "Количество точек")
            .setValue(24).range(6, 80)
            .visible(this::bodyTrailOn);

    final SliderSettings bodyTrailWidth = new SliderSettings("Ширина Линияа", "Толщина Линияа")
            .setValue(0.22f).range(0.06f, 0.60f)
            .visible(this::bodyTrailOn);

    final SliderSettings bodyTrailAlpha = new SliderSettings("Прозрачность Линияа", "Множитель альфы")
            .setValue(1.0f).range(0.1f, 1.6f)
            .visible(this::bodyTrailOn);

    final SliderSettings bodyTrailStepMs = new SliderSettings("Шаг (мс)", "Интервал добавления точки (мс)")
            .setValue(18).range(6, 80)
            .visible(this::bodyTrailOn);

    final BooleanSetting bodyTrailSelf = new BooleanSetting("Линия на себя", "Рисовать на себе").setValue(true).visible(this::bodyTrailOn);
    final BooleanSetting bodyTrailOthers = new BooleanSetting("Линия на других", "Рисовать на других").setValue(false).visible(this::bodyTrailOn);
    final BooleanSetting bodyTrailFirstPerson = new BooleanSetting("Линия от 1-го лица", "Рисовать от 1-го лица").setValue(true).visible(this::bodyTrailOn);

    final SliderSettings petRadius = new SliderSettings("Радиус", "Радиус вокруг игрока")
            .setValue(0.65f).range(0.15f, 2.5f)
            .visible(this::petOn);

    final SliderSettings petHeight = new SliderSettings("Высота", "Смещение по Y")
            .setValue(0.95f).range(-0.5f, 3.0f)
            .visible(this::petOn);

    final SliderSettings petSpeed = new SliderSettings("Скорость", "Скорость движения")
            .setValue(1.15f).range(0.05f, 5.0f)
            .visible(this::petOn);

    final SliderSettings petScale = new SliderSettings("Размер", "Масштаб модели")
            .setValue(0.55f).range(0.15f, 2.0f)
            .visible(this::petOn);

    final SliderSettings ravenAlpha = new SliderSettings("Raven Alpha", "Прозрачность воронов/эффектов")
            .setValue(210).range(0, 255)
            .visible(this::ravenUiOn);

    final ColorSetting ravenColor = new ColorSetting("Raven Color", "Цвет глоу/тумана (вороны)")
            .value(ColorAssist.getColor(20, 20, 20, 255))
            .visible(this::ravenUiOn);

    final SliderSettings ravenGlow = new SliderSettings("Raven Glow", "Интенсивность свечения/тумана")
            .setValue(65).range(0, 100)
            .visible(this::ravenUiOn);

    final Nimb nimb;
    final Spheres spheres;
    final Trail trail;
    final Raven raven;
    final Naruto naruto;
    final PlayerAnimations playerAnimations;

    public Cosmetic() {
        super("Cosmetic", "Cosmetic", ModuleCategory.RENDER);

        setup(
                spheresEnabled, haloEnabled, bodyTrailEnabled, petEnabled,

                narutoEnabled,

                playerAnimEnabled, playerAnimType, playerAnimBind,

                mode, effect,
                exort, wex, quas,
                size, radius, height,
                speed, breathe,
                spiralHeight, waveAmp, waveFreq,
                sphereEditor, editLR, editFB,
                trailLen, trailPower,
                self, others, firstPerson,

                haloOthers, haloFirstPerson,
                haloDistance, haloQuality,
                haloColor, haloAnimate,
                haloRadius, haloThickness, haloYOffset,

                bodyTrailMode,
                bodyTrailColorA, bodyTrailColorB,
                bodyTrailPoints, bodyTrailWidth, bodyTrailAlpha, bodyTrailStepMs,
                bodyTrailSelf, bodyTrailOthers, bodyTrailFirstPerson,

                petSkin, petFirstPerson, petRadius, petHeight, petSpeed, petScale,

                ravenAlpha, ravenColor, ravenGlow,

                ravenSceneBind
        );

        this.nimb = new Nimb(this);
        this.spheres = new Spheres(this);
        this.trail = new Trail(this);
        this.raven = new Raven(this);
        this.naruto = new Naruto(this);
        this.playerAnimations = new PlayerAnimations(this);
    }

    @Override
    public void deactivate() {
        super.deactivate();
        ravenSceneRequested = false;
        playerAnimRequested = false;
        if (spheres != null) spheres.deactivate();
        if (nimb != null) nimb.deactivate();
        if (trail != null) trail.deactivate();
        if (raven != null) raven.deactivate();
        if (naruto != null) naruto.onDisable();
        if (playerAnimations != null) playerAnimations.deactivate();
    }

    @EventHandler
    public void onWorldRender(WorldRenderEvent e) {
        if (mc.world == null || mc.player == null) return;

        boolean s = spheresOn();
        boolean h = haloOn();
        boolean t = bodyTrailOn();
        boolean r = petOn();
        boolean a = playerAnimOn();

        if (!s && !h && !t && !r && !a) return;

        if (s) spheres.onWorldRender(e);
        if (h) nimb.onWorldRender(e);
        if (t) trail.onWorldRender(e);

        if (r) {
            boolean camFirst = mc.options.getPerspective().isFirstPerson();
            if (!(camFirst && !bool(petFirstPerson))) raven.onWorldRender(e);
        }

        if (a && playerAnimations != null) playerAnimations.onWorldRender(e);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (mc.world == null || mc.player == null) return;
        if (!isState()) return;

        if (playerAnimOn() && e.isKeyDown(playerAnimBind.getKey())) {
            playerAnimRequested = true;
            float volume = Hud.getInstance().getModuleVolume();
            Notifications.getInstance().addList("Теневые клоны", 1500, null);
            SoundManager.playSound(SoundManager.ENABLE_MODULE, volume, 1.0f);
        }

        if (ravenUiOn() && e.isKeyDown(ravenSceneBind.getKey())) {
            ravenSceneRequested = true;
            float volume = Hud.getInstance().getModuleVolume();
            Notifications.getInstance().addList("Raven Scene", 1500, null);
            SoundManager.playSound(SoundManager.ENABLE_MODULE, volume, 1.0f);
        }
    }

    boolean pollRavenScene() {
        if (!ravenSceneRequested) return false;
        ravenSceneRequested = false;
        return true;
    }

    boolean pollPlayerAnim() {
        if (!playerAnimRequested) return false;
        playerAnimRequested = false;
        return true;
    }

    boolean spheresOn() {
        return bool(spheresEnabled);
    }

    boolean haloOn() {
        return bool(haloEnabled);
    }

    boolean bodyTrailOn() {
        return bool(bodyTrailEnabled);
    }

    boolean petOn() {
        return bool(petEnabled);
    }

    boolean narutoOn() {
        return bool(narutoEnabled);
    }

    boolean playerAnimOn() {
        return bool(playerAnimEnabled);
    }

    boolean petIsBat() {
        return petSkin != null && petSkin.isSelected("Летучая мышь");
    }

    boolean petIsParrot() {
        return petSkin != null && petSkin.isSelected("Попугай");
    }

    boolean petIsRaven() {
        return petSkin != null && petSkin.isSelected("Ворон");
    }

    boolean petIsFairy() {
        return petSkin != null && petSkin.isSelected("Фея");
    }

    boolean petIsBee() {
        return petSkin != null && petSkin.isSelected("Пчела");
    }

    boolean petIsVex() {
        return petSkin != null && petSkin.isSelected("Векс");
    }

    boolean petIsFox() {
        return petSkin != null && petSkin.isSelected("Лисичка");
    }

    boolean petIsPig() {
        return petSkin != null && petSkin.isSelected("Свинья");
    }

    boolean petIsFrog() {
        return petSkin != null && petSkin.isSelected("Лягушка");
    }

    boolean petIsPufferfish() {
        return petSkin != null && petSkin.isSelected("Иглобрюх");
    }

    boolean petIsSlime() {
        return petSkin != null && petSkin.isSelected("Слайм");
    }

    boolean ravenUiOn() {
        return petOn() && petIsRaven();
    }

    public static boolean bool(Object setting) {
        if (setting == null) return false;

        try {
            Method m = setting.getClass().getMethod("getValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isValue");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isEnabled");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("isState");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignored) {
        }

        try {
            Method m = setting.getClass().getMethod("get");
            Object v = m.invoke(setting);
            if (v instanceof Boolean) return (Boolean) v;
        } catch (Exception ignored) {
        }

        for (String fn : new String[]{"value", "state", "enabled"}) {
            try {
                Field f = setting.getClass().getDeclaredField(fn);
                f.setAccessible(true);
                Object v = f.get(setting);
                if (v instanceof Boolean) return (Boolean) v;
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}
