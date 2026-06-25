package fun.rich.utils.client.sound;

import fun.rich.features.impl.misc.ClientSound;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.utils.interactions.interact.PlayerInteractionHelper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

@Setter
@Getter
@UtilityClass
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SoundManager implements QuickImports {

    public SoundEvent OPEN_GUI = SoundEvent.of(Identifier.of("minecraft:gui_open"));
    public SoundEvent CLOSE_GUI = SoundEvent.of(Identifier.of("minecraft:gui_close"));
    public SoundEvent ENABLE_MODULE = SoundEvent.of(Identifier.of("minecraft:module_enable"));
    public SoundEvent DISABLE_MODULE = SoundEvent.of(Identifier.of("minecraft:module_disable"));
    public SoundEvent CATEGORY_CLICK = SoundEvent.of(Identifier.of("minecraft:guicategory_select"));
    public SoundEvent ORTHODOX = SoundEvent.of(Identifier.of("minecraft:kolokolnia_kill"));

    public void init() {
        reg(OPEN_GUI);
        reg(CLOSE_GUI);
        reg(ENABLE_MODULE);
        reg(DISABLE_MODULE);
        reg(CATEGORY_CLICK);
        reg(ORTHODOX);
    }

    private void reg(SoundEvent e) {
        try {
            Identifier id = e.id();
            if (Registries.SOUND_EVENT.containsId(id)) return;
            Registry.register(Registries.SOUND_EVENT, id, e);
        } catch (Throwable ignored) {
        }
    }

    private SoundEvent resolve(SoundEvent e) {
        if (e == null) return null;
        Identifier id = null;
        try {
            id = e.id();
        } catch (Throwable ignored) {
        }
        if (id == null) return e;

        try {
            if (!Registries.SOUND_EVENT.containsId(id)) reg(e);
        } catch (Throwable ignored) {
        }

        try {
            if (Registries.SOUND_EVENT.containsId(id)) return Registries.SOUND_EVENT.get(id);
        } catch (Throwable ignored) {
        }

        return e;
    }

    private boolean sameId(SoundEvent a, SoundEvent b) {
        if (a == b) return true;
        Identifier ia = null;
        Identifier ib = null;
        try {
            ia = a == null ? null : a.id();
        } catch (Throwable ignored) {
        }
        try {
            ib = b == null ? null : b.id();
        } catch (Throwable ignored) {
        }
        return ia != null && ib != null && ia.equals(ib);
    }

    public void playSound(SoundEvent sound) {
        playSound(sound, 1.0f, 1.0f);
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (sound == null) return;

        ClientSound cs = ClientSound.getInstance();
        if (cs != null && cs.enabledCompat()) {
            if (sameId(sound, ENABLE_MODULE)) {
                sound = cs.enableEvent();
                volume *= cs.volumeMul();
            } else if (sameId(sound, DISABLE_MODULE)) {
                sound = cs.disableEvent();
                volume *= cs.volumeMul();
            }
        }

        sound = resolve(sound);
        if (sound == null) return;

        if (!PlayerInteractionHelper.nullCheck()) {
            mc.world.playSound(mc.player, mc.player.getBlockPos(), sound, SoundCategory.BLOCKS, volume, pitch);
        }
    }
}
