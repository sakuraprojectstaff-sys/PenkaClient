package fun.rich.mixins.misc;

import fun.rich.features.impl.misc.TotemSound;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(SoundManager.class)
public class LivingEntityTotemSoundMixin {

    private static final Identifier TOTEM_ID = Identifier.of("minecraft", "item.totem.use");
    private static final ThreadLocal<Boolean> GUARD = ThreadLocal.withInitial(() -> false);

    private static volatile Method mPlaySoundNoPlayer;

    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void rich$swapTotemSound(SoundInstance inst, CallbackInfo ci) {
        if (inst == null) return;
        if (Boolean.TRUE.equals(GUARD.get())) return;

        Identifier id;
        try {
            id = inst.getId();
        } catch (Throwable t) {
            return;
        }
        if (id == null || !id.equals(TOTEM_ID)) return;

        TotemSound ts = TotemSound.getInstance();
        if (ts == null || !ts.shouldReplace()) return;

        SoundEvent repl = ts.replacementEvent();
        if (repl == null) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null) return;

        double x, y, z;
        SoundCategory cat;
        try {
            x = inst.getX();
            y = inst.getY();
            z = inst.getZ();
            cat = inst.getCategory();
        } catch (Throwable t) {
            return;
        }

        try {
            GUARD.set(true);
            if (!invokeWorldPlay(mc.world, x, y, z, repl, cat, ts.replacementVolume(), ts.replacementPitch())) return;
            ci.cancel();
        } finally {
            GUARD.set(false);
        }
    }

    private static boolean invokeWorldPlay(World w, double x, double y, double z, SoundEvent e, SoundCategory cat, float vol, float pit) {
        try {
            Method m = mPlaySoundNoPlayer;
            if (m == null) {
                m = findWorldPlay(w.getClass());
                mPlaySoundNoPlayer = m;
            }
            if (m == null) return false;

            if (m.getParameterCount() == 8) {
                m.invoke(w, x, y, z, e, cat, vol, pit, false);
                return true;
            }

            if (m.getParameterCount() == 7) {
                m.invoke(w, x, y, z, e, cat, vol, pit);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Method findWorldPlay(Class<?> wc) {
        try {
            for (Method m : wc.getMethods()) {
                if (!m.getName().equals("playSound")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 8) {
                    if (p[0] == double.class && p[1] == double.class && p[2] == double.class &&
                            SoundEvent.class.isAssignableFrom(p[3]) &&
                            SoundCategory.class.isAssignableFrom(p[4]) &&
                            p[5] == float.class && p[6] == float.class && p[7] == boolean.class) {
                        m.setAccessible(true);
                        return m;
                    }
                }
                if (p.length == 7) {
                    if (p[0] == double.class && p[1] == double.class && p[2] == double.class &&
                            SoundEvent.class.isAssignableFrom(p[3]) &&
                            SoundCategory.class.isAssignableFrom(p[4]) &&
                            p[5] == float.class && p[6] == float.class) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
