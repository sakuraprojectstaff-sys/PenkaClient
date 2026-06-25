package fun.rich.mixins.misc;

import fun.rich.features.impl.render.ArmorDurability;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(EquipmentRenderer.class)
public abstract class EquipmentRendererMixin {
    @Unique
    private static final ThreadLocal<ItemStack> rich$currentArmorStack = new ThreadLocal<>();

    @Unique
    private static final ConcurrentHashMap<Identifier, Identifier> rich$grayCache = new ConcurrentHashMap<>();

    @Unique
    private static final Method rich$getPx;

    @Unique
    private static final Method rich$setPx;

    static {
        rich$getPx = findMethod(NativeImage.class, "getColor", "getPixelColor", "getColorArgb", "getArgb", "getPixelRGBA");
        rich$setPx = findMethod(NativeImage.class, "setColor", "setPixelColor", "setColorArgb", "setArgb", "setPixelRGBA");
        if (rich$getPx != null) rich$getPx.setAccessible(true);
        if (rich$setPx != null) rich$setPx.setAccessible(true);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At("HEAD")
    )
    private void rich$store(EquipmentModel.LayerType layerType, RegistryKey<?> assetKey, Model model, ItemStack stack,
                            net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
                            int light, @Nullable Identifier texture, CallbackInfo ci) {
        rich$currentArmorStack.set(stack);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At("RETURN")
    )
    private void rich$clear(EquipmentModel.LayerType layerType, RegistryKey<?> assetKey, Model model, ItemStack stack,
                            net.minecraft.client.util.math.MatrixStack matrices, net.minecraft.client.render.VertexConsumerProvider vertexConsumers,
                            int light, @Nullable Identifier texture, CallbackInfo ci) {
        rich$currentArmorStack.remove();
    }

    @Inject(method = "getDyeColor", at = @At("HEAD"), cancellable = true)
    private static void rich$getDyeColor(EquipmentModel.Layer layer, int dyeColor, CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = rich$currentArmorStack.get();
        if (!rich$enabled(stack)) return;

        int vanilla = rich$vanillaGate(layer, dyeColor);
        if (vanilla == 0) return;

        cir.setReturnValue(rich$durabilityColor(stack));
    }

    @ModifyArg(
            method = "render(Lnet/minecraft/client/render/entity/equipment/EquipmentModel$LayerType;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/client/model/Model;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;getArmorCutoutNoCull(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"),
            index = 0
    )
    private Identifier rich$swapToGray(Identifier original) {
        ItemStack stack = rich$currentArmorStack.get();
        if (!rich$enabled(stack)) return original;
        if (!rich$looksLikeArmorTex(original)) return original;
        return rich$getGrayTexture(original);
    }

    @Unique
    private static boolean rich$enabled(ItemStack stack) {
        if (stack == null) return false;
        if (ArmorDurability.INSTANCE == null || !ArmorDurability.INSTANCE.isState()) return false;
        if (!(stack.getItem() instanceof ArmorItem)) return false;
        return stack.isDamageable() && stack.getMaxDamage() > 0;
    }

    @Unique
    private static boolean rich$looksLikeArmorTex(Identifier id) {
        String p = id.getPath();
        if (!p.endsWith(".png")) return false;
        if (p.contains("skins/")) return false;
        return p.contains("armor") || p.contains("equipment") || p.contains("entity");
    }

    @Unique
    private static int rich$vanillaGate(EquipmentModel.Layer layer, int dyeColor) {
        Optional<EquipmentModel.Dyeable> dyeable = layer.dyeable();
        if (dyeable.isPresent()) {
            int undyed = dyeable.get().colorWhenUndyed().map(ColorHelper::fullAlpha).orElse(0);
            return dyeColor != 0 ? dyeColor : undyed;
        }
        return -1;
    }

    @Unique
    private static int rich$durabilityColor(ItemStack stack) {
        float broken = stack.getDamage() / (float) stack.getMaxDamage();
        if (broken < 0.0F) broken = 0.0F;
        if (broken > 1.0F) broken = 1.0F;

        broken = (float) Math.pow(broken, 0.45);

        float hue = (1.0F - broken) * 0.33F;
        int rgb = rich$hsvToRgb(hue, 1.0F, 1.0F);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    @Unique
    private static Identifier rich$getGrayTexture(Identifier original) {
        Identifier cached = rich$grayCache.get(original);
        if (cached != null) return cached;

        Identifier made = rich$makeGrayTexture(original);
        if (made == null) return original;

        Identifier prev = rich$grayCache.putIfAbsent(original, made);
        return prev != null ? prev : made;
    }

    @Unique
    private static Identifier rich$makeGrayTexture(Identifier original) {
        if (rich$getPx == null || rich$setPx == null) return null;

        MinecraftClient mc = MinecraftClient.getInstance();
        Optional<Resource> resOpt = mc.getResourceManager().getResource(original);
        if (resOpt.isEmpty()) return null;

        NativeImage src = null;
        NativeImage dst = null;

        try (InputStream in = resOpt.get().getInputStream()) {
            src = NativeImage.read(in);
            if (src == null) return null;

            int w = src.getWidth();
            int h = src.getHeight();
            dst = new NativeImage(w, h, true);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = (int) rich$getPx.invoke(src, x, y);

                    int a = (abgr >>> 24) & 255;
                    int b = (abgr >>> 16) & 255;
                    int g = (abgr >>> 8) & 255;
                    int r = abgr & 255;

                    int lum = (int) (0.2126f * r + 0.7152f * g + 0.0722f * b + 0.5f);
                    int out = (a << 24) | (lum << 16) | (lum << 8) | lum;

                    rich$setPx.invoke(dst, x, y, out);
                }
            }

            NativeImageBackedTexture tex = new NativeImageBackedTexture(dst);
            Identifier id = rich$registerDynamicTexture("armor_dur_" + Math.abs(original.toString().hashCode()), tex);
            if (id == null) {
                tex.close();
                return null;
            }
            return id;
        } catch (Throwable t) {
            return null;
        } finally {
            if (src != null) src.close();
        }
    }

    @Unique
    private static Identifier rich$registerDynamicTexture(String key, NativeImageBackedTexture tex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Object tm = mc.getTextureManager();

        try {
            Method m = tm.getClass().getMethod("registerDynamicTexture", String.class, NativeImageBackedTexture.class);
            Object out = m.invoke(tm, key, tex);
            if (out instanceof Identifier id) return id;
        } catch (Throwable ignored) {
        }

        try {
            Identifier id = Identifier.of("rich", "dynamic/" + key);
            Method m = tm.getClass().getMethod("registerTexture", Identifier.class, AbstractTexture.class);
            m.invoke(tm, id, tex);
            return id;
        } catch (Throwable ignored) {
        }

        return null;
    }

    @Unique
    private static Method findMethod(Class<?> cls, String... names) {
        for (String n : names) {
            try {
                return cls.getDeclaredMethod(n, int.class, int.class);
            } catch (Throwable ignored) {
            }
            try {
                return cls.getDeclaredMethod(n, int.class, int.class, int.class);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    @Unique
    private static int rich$hsvToRgb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        float hh = h * 6.0f;
        int i = (int) hh;
        float f = hh - i;

        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);

        float r;
        float g;
        float b;

        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }

        int ir = (int) (r * 255.0f + 0.5f);
        int ig = (int) (g * 255.0f + 0.5f);
        int ib = (int) (b * 255.0f + 0.5f);

        if (ir < 0) ir = 0;
        if (ir > 255) ir = 255;
        if (ig < 0) ig = 0;
        if (ig > 255) ig = 255;
        if (ib < 0) ib = 0;
        if (ib > 255) ib = 255;

        return (ir << 16) | (ig << 8) | ib;
    }
}