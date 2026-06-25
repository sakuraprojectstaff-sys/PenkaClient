package fun.rich.features.impl.player;

import fun.rich.events.keyboard.KeyEvent;
import fun.rich.events.keyboard.MouseRotationEvent;
import fun.rich.events.render.CameraEvent;
import fun.rich.events.render.FovEvent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.utils.client.managers.event.EventHandler;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class FreeLook extends Module {

    static final float SENS = 0.15F;

    static final boolean THIRD_PERSON = true;
    static final boolean NO_CLAMP_PITCH = false;
    static final boolean BACK_VIEW = true;

    Perspective previous;
    Turns angle;
    boolean active;

    public static BindSetting freeLookSetting = new BindSetting("Свободный обзор", "Клавиша для свободного обзора");

    public FreeLook() {
        super("FreeLook", "Free Look", ModuleCategory.RENDER);
        setup(freeLookSetting);
    }

    @EventHandler
    public void onKey(KeyEvent e) {
        if (!e.isKeyDown(freeLookSetting.getKey())) return;
        if (active) return;
        previous = mc.options.getPerspective();
        angle = MathAngle.cameraAngle();
        active = true;
    }

    @EventHandler
    public void onFov(FovEvent e) {
        boolean key = PlayerInteractionHelper.isKey(freeLookSetting);

        if (key) {
            if (!active) {
                previous = mc.options.getPerspective();
                angle = MathAngle.cameraAngle();
                active = true;
            }
            if (THIRD_PERSON && mc.options.getPerspective().isFirstPerson()) {
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            if (angle == null) angle = MathAngle.cameraAngle();
            return;
        }

        if (active) {
            if (previous != null && BACK_VIEW) {
                mc.options.setPerspective(previous);
            }
            previous = null;
            angle = null;
            active = false;
        }
    }

    @EventHandler
    public void onMouseRotation(MouseRotationEvent e) {
        if (!PlayerInteractionHelper.isKey(freeLookSetting)) {
            if (!active) angle = null;
            return;
        }

        if (angle == null) angle = MathAngle.cameraAngle();

        angle.setYaw(angle.getYaw() + e.getCursorDeltaX() * SENS);

        float nextPitch = angle.getPitch() + e.getCursorDeltaY() * SENS;
        if (!NO_CLAMP_PITCH) nextPitch = MathHelper.clamp(nextPitch, -90F, 90F);
        angle.setPitch(nextPitch);

        e.cancel();
    }

    @EventHandler
    public void onCamera(CameraEvent e) {
        if (PlayerInteractionHelper.isKey(freeLookSetting) && angle != null) {
            e.setAngle(angle);
            e.cancel();
        }
    }
}
