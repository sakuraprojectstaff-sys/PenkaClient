package fun.rich.features.impl.render;

import fun.rich.events.item.HandAnimationEvent;
import fun.rich.events.item.SwingDurationEvent;
import fun.rich.features.impl.combat.Aura;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.client.managers.event.EventHandler;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SwingAnimation extends Module {

    SelectSetting swingType = new SelectSetting("Тип взмаха", "Выберите тип взмаха")
            .value("Swipe", "Down", "Smooth", "Smooth 2", "Power", "Feast", "Twist", "Default");

    SliderSettings hitStrengthSetting = new SliderSettings("Сила взмаха", "Сила анимации взмаха")
            .setValue(1.0F).range(0.5F, 3.0F);

    SliderSettings swingSpeedSetting = new SliderSettings("Длительность взмаха", "Длительность анимации удара")
            .setValue(1.0F).range(0.5F, 4.0F);

    BooleanSetting onlySwing = new BooleanSetting("Только при взмахе", "Показывает анимацию только при взмахе")
            .setValue(false);

    BooleanSetting onlyAura = new BooleanSetting("Только при включенной КиллАуре", "Показывает анимацию только при включенной киллауре")
            .setValue(false);

    BooleanSetting offsetsEnabled = new BooleanSetting("Оффсеты рук", "Сдвиг руки в первом лице")
            .setValue(false);

    SliderSettings rightX = new SliderSettings("Правая X", "Сдвиг правой руки по X")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    SliderSettings rightY = new SliderSettings("Правая Y", "Сдвиг правой руки по Y")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    SliderSettings rightZ = new SliderSettings("Правая Z", "Сдвиг правой руки по Z")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    SliderSettings leftX = new SliderSettings("Левая X", "Сдвиг левой руки по X")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    SliderSettings leftY = new SliderSettings("Левая Y", "Сдвиг левой руки по Y")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    SliderSettings leftZ = new SliderSettings("Левая Z", "Сдвиг левой руки по Z")
            .setValue(0.0F).range(-1.5F, 1.5F).visible(offsetsEnabled::isValue);

    public SwingAnimation() {
        super("SwingAnimation", "Swing Animation", ModuleCategory.RENDER);
        setup(
                swingType,
                hitStrengthSetting,
                swingSpeedSetting,
                onlySwing,
                onlyAura,
                offsetsEnabled,
                rightX, rightY, rightZ,
                leftX, leftY, leftZ
        );
    }

    @EventHandler
    public void onSwingDuration(SwingDurationEvent e) {
        if (!canRun()) return;
        e.setAnimation(swingSpeedSetting.getValue());
        e.cancel();
    }

    @EventHandler
    public void onHandAnimation(HandAnimationEvent e) {
        if (mc.player == null || mc.world == null) return;
        if (e.getHand() != Hand.MAIN_HAND) return;
        if (!canRun()) return;
        if (onlySwing.isValue() && mc.player.handSwingTicks == 0) return;

        MatrixStack matrix = e.getMatrices();
        float swingProgress = e.getSwingProgress();

        int i = mc.player.getMainArm() == Arm.RIGHT ? 1 : -1;

        float sin1 = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float sin2 = MathHelper.sin(MathHelper.sqrt(swingProgress) * (float) Math.PI);
        float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5F);
        float strength = hitStrengthSetting.getValue();

        if (offsetsEnabled.isValue()) {
            float ox;
            float oy;
            float oz;

            if (mc.player.getMainArm() == Arm.RIGHT) {
                ox = rightX.getValue();
                oy = rightY.getValue();
                oz = rightZ.getValue();
            } else {
                ox = leftX.getValue();
                oy = leftY.getValue();
                oz = leftZ.getValue();
            }

            matrix.translate(ox * i, oy, oz);
        }

        switch (swingType.getSelected()) {
            case "Twist" -> {
                matrix.translate(i * 0.56F, -0.36F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -90 * strength));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin1 - sin2) * 60 * i * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-30));
                matrix.translate(0, -0.1F, 0.05F);
            }
            case "Swipe" -> {
                matrix.translate(0.56F * i, -0.32F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(70 * i));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-20 * i));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -120 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-70));
            }
            case "Default" -> {
                matrix.translate(i * 0.56F, -0.52F - (sin2 * 0.5F * strength), -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45 * i));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45 * i));
            }
            case "Down" -> {
                matrix.translate(i * 0.56F, -0.32F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(76 * i));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * -5 * strength));
                matrix.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(sin2 * -100 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -155 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100));
            }
            case "Smooth" -> {
                matrix.translate(i * 0.56F, -0.42F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * (45.0F + sin1 * -20.0F * strength)));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * sin2 * -20.0F * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45.0F));
                matrix.translate(0, -0.1, 0);
            }
            case "Smooth 2" -> {
                matrix.translate(i * 0.56F, -0.42F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -80.0F * strength));
                matrix.translate(0, -0.1, 0);
            }
            case "Power" -> {
                matrix.translate(i * 0.56F, -0.32F, -0.72F);
                matrix.translate((-sinSmooth * sinSmooth * sin1) * i * strength, 0, 0);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(61 * i));
                matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sin2 * strength));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((sin2 * sin1) * -5 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin2 * sin1) * -30 * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-60));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinSmooth * -60 * strength));
            }
            case "Feast" -> {
                matrix.translate(i * 0.56F, -0.32F, -0.72F);
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sin2 * 75 * i * strength));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -45 * strength));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30 * i));
                matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80));
                matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(35 * i));
            }
        }

        e.cancel();
    }

    private boolean canRun() {
        if (!onlyAura.isValue()) return true;
        if (Aura.getInstance() == null) return false;
        return Aura.getInstance().isState() && Aura.getInstance().getTarget() != null;
    }
}
