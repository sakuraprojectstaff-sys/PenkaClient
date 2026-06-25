package fun.rich.features.impl.render;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.events.item.HandOffsetEvent;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ViewModel extends Module {

    SliderSettings mainHandXSetting = new SliderSettings("Основная рука X", "Смещение X для основной руки")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings mainHandYSetting = new SliderSettings("Основная рука Y", "Смещение Y для основной руки")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings mainHandZSetting = new SliderSettings("Основная рука Z", "Смещение Z для основной руки")
            .setValue(0.0F).range(-3.5F, 3.5F);

    SliderSettings offHandXSetting = new SliderSettings("Второстепенная рука X", "Смещение X для второстепенной руки")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings offHandYSetting = new SliderSettings("Второстепенная рука Y", "Смещение Y для второстепенной руки")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings offHandZSetting = new SliderSettings("Второстепенная рука Z", "Смещение Z для второстепенной руки")
            .setValue(0.0F).range(-3.5F, 3.5F);

    SliderSettings mainDiagXSetting = new SliderSettings("Основная диагональ X", "Диагональное смещение по X (доп.)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings mainDiagYSetting = new SliderSettings("Основная диагональ Y", "Диагональное смещение по Y (доп.)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings mainZoomSetting = new SliderSettings("Основная приближение", "Приближение/отдаление (доп.) по Z")
            .setValue(0.0F).range(-3.5F, 3.5F);

    SliderSettings offDiagXSetting = new SliderSettings("Второстепенная диагональ X", "Диагональное смещение по X (доп.)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings offDiagYSetting = new SliderSettings("Второстепенная диагональ Y", "Диагональное смещение по Y (доп.)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings offZoomSetting = new SliderSettings("Второстепенная приближение", "Приближение/отдаление (доп.) по Z")
            .setValue(0.0F).range(-3.5F, 3.5F);

    SliderSettings mainPitchSetting = new SliderSettings("Основная Pitch", "Поворот по X (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings mainYawSetting = new SliderSettings("Основная Yaw", "Поворот по Y (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings mainRollSetting = new SliderSettings("Основная Roll", "Поворот по Z (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings offPitchSetting = new SliderSettings("Второстепенная Pitch", "Поворот по X (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings offYawSetting = new SliderSettings("Второстепенная Yaw", "Поворот по Y (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings offRollSetting = new SliderSettings("Второстепенная Roll", "Поворот по Z (градусы)")
            .setValue(0.0F).range(-60.0F, 60.0F);

    SliderSettings mainScaleSetting = new SliderSettings("Основная Scale", "Масштаб руки")
            .setValue(1.0F).range(0.25F, 2.25F);

    SliderSettings offScaleSetting = new SliderSettings("Второстепенная Scale", "Масштаб руки")
            .setValue(1.0F).range(0.25F, 2.25F);

    SliderSettings mainUpDownSetting = new SliderSettings("Основная Вверх/Вниз", "Доп. вертикаль (упрощённо)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    SliderSettings offUpDownSetting = new SliderSettings("Второстепенная Вверх/Вниз", "Доп. вертикаль (упрощённо)")
            .setValue(0.0F).range(-1.5F, 1.5F);

    public ViewModel() {
        super("ViewModel", "View Model", ModuleCategory.RENDER);
        setup(
                mainHandXSetting, mainHandYSetting, mainHandZSetting,
                offHandXSetting, offHandYSetting, offHandZSetting,
                mainDiagXSetting, mainDiagYSetting, mainZoomSetting,
                offDiagXSetting, offDiagYSetting, offZoomSetting,
                mainPitchSetting, mainYawSetting, mainRollSetting,
                offPitchSetting, offYawSetting, offRollSetting,
                mainScaleSetting, offScaleSetting,
                mainUpDownSetting, offUpDownSetting
        );
    }

    @EventHandler
    public void onHandOffset(HandOffsetEvent e) {
        Hand hand = e.getHand();
        if (hand.equals(Hand.MAIN_HAND) && e.getStack().getItem() instanceof CrossbowItem) return;

        MatrixStack matrix = e.getMatrices();

        if (hand.equals(Hand.MAIN_HAND)) {
            applyHand(matrix,
                    mainHandXSetting.getValue(), mainHandYSetting.getValue(), mainHandZSetting.getValue(),
                    mainDiagXSetting.getValue(), mainDiagYSetting.getValue(), mainZoomSetting.getValue(),
                    mainPitchSetting.getValue(), mainYawSetting.getValue(), mainRollSetting.getValue(),
                    mainScaleSetting.getValue(),
                    mainUpDownSetting.getValue()
            );
        } else {
            applyHand(matrix,
                    offHandXSetting.getValue(), offHandYSetting.getValue(), offHandZSetting.getValue(),
                    offDiagXSetting.getValue(), offDiagYSetting.getValue(), offZoomSetting.getValue(),
                    offPitchSetting.getValue(), offYawSetting.getValue(), offRollSetting.getValue(),
                    offScaleSetting.getValue(),
                    offUpDownSetting.getValue()
            );
        }
    }

    void applyHand(MatrixStack matrix,
                   float x, float y, float z,
                   float dx, float dy, float dz,
                   float pitchDeg, float yawDeg, float rollDeg,
                   float scale,
                   float upDown) {

        float sc = MathHelper.clamp(scale, 0.01f, 16.0f);

        matrix.translate(x + dx, y + dy + upDown, z + dz);

        if (pitchDeg != 0.0f) matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitchDeg));
        if (yawDeg != 0.0f) matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDeg));
        if (rollDeg != 0.0f) matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rollDeg));

        if (sc != 1.0f) matrix.scale(sc, sc, sc);
    }
}
