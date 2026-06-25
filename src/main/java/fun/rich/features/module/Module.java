package fun.rich.features.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.utils.client.sound.SoundManager;
import fun.rich.Rich;
import fun.rich.features.module.setting.SettingRepository;
import fun.rich.utils.client.managers.event.EventManager;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.display.hud.Notifications;
import fun.rich.features.impl.render.Hud;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class Module extends SettingRepository implements QuickImports {
    String name;
    String visibleName;
    ModuleCategory category;
    Animation animation = new Decelerate().setMs(175).setValue(1);

    public Module(String name, ModuleCategory category) {
        this.name = name;
        this.category = category;
        this.visibleName = name;
    }

    public Module(String name, String visibleName, ModuleCategory category) {
        this.name = name;
        this.visibleName = visibleName;
        this.category = category;
    }

    @NonFinal
    int key = GLFW.GLFW_KEY_UNKNOWN, type = 1;

    @NonFinal
    public boolean state;

    public void switchState() {
        setState(!state);
    }

    public void setState(boolean state) {
        animation.setDirection(state ? Direction.FORWARDS : Direction.BACKWARDS);
        if (state != this.state) {
            this.state = state;
            handleStateChange();
        }
    }

    private void handleStateChange() {
        MinecraftClient mc = MinecraftClient.getInstance();

        Hud hud = Hud.getInstance();
        float volume = 1.0f;
        boolean notifyModuleSwitch = false;

        if (hud != null) {
            try {
                volume = hud.getModuleVolume();
            } catch (Throwable ignored) {
            }
            try {
                notifyModuleSwitch = hud.notificationSettings != null && hud.notificationSettings.isSelected("Module Switch");
            } catch (Throwable ignored) {
            }
        }

        if (mc.player != null && mc.world != null) {
            if (state) {
                SoundManager.playSound(SoundManager.ENABLE_MODULE, volume, 1.0f);
                if (notifyModuleSwitch) {
                    Notifications n = Notifications.getInstance();
                    if (n != null) n.addList("Feature " + Formatting.GRAY + visibleName + Formatting.RESET + " - enabled!", 2000, null);
                }
                activate();
            } else {
                SoundManager.playSound(SoundManager.DISABLE_MODULE, volume, 1.0f);
                if (notifyModuleSwitch) {
                    Notifications n = Notifications.getInstance();
                    if (n != null) n.addList("Feature " + Formatting.GRAY + visibleName + Formatting.RESET + " - disabled!", 2000, null);
                }
                deactivate();
            }
        }
        toggleSilent(state);
    }

    private void toggleSilent(boolean activate) {
        EventManager eventManager = Rich.getInstance().getEventManager();
        if (activate) {
            eventManager.register(this);
        } else {
            eventManager.unregister(this);
        }
    }

    public void activate() {
    }

    public void deactivate() {
    }
}
