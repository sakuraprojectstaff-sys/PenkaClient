package fun.rich.utils.display.item;

import com.mojang.blaze3d.systems.RenderSystem;
import fun.rich.features.impl.render.ArmorDurability;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.utils.display.shape.ShapeProperties;
import lombok.experimental.UtilityClass;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.util.math.ColorHelper;

import java.util.HashMap;

@UtilityClass
public class ItemRender implements QuickImports {
    public final HashMap<ItemStack, CachedSprite> SPRITE_CACHE = new HashMap<>();

    public void drawItem(MatrixStack matrix, ItemStack stack, float x, float y, boolean count, boolean bar) {
        CachedSprite cachedSprite = getSpriteTexture(stack);
        if (cachedSprite != null) {
            int color = getItemColor(stack);
            float r = ((color >> 16) & 255) / 255.0F;
            float g = ((color >> 8) & 255) / 255.0F;
            float b = (color & 255) / 255.0F;
            float a = ((color >> 24) & 255) / 255.0F;

            RenderSystem.setShaderColor(r, g, b, a);
            Render2D.drawSprite(matrix, cachedSprite.sprite, x, y, 16, 16);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        if (count) drawCount(matrix, stack, x, y);
        if (bar) drawBar(matrix, stack, x, y);
    }

    private void drawCount(MatrixStack matrix, ItemStack stack, float x, float y) {
        int count = stack.getCount();
        String text = count > 1 ? count + "" : "";
        if (!text.isEmpty()) {
            FontRenderer font = Fonts.getSize(16);
            font.drawString(matrix, text, x + 16 - font.getStringWidth(text), y + 11, ColorAssist.getText());
        }
    }

    private void drawBar(MatrixStack matrix, ItemStack stack, float x, float y) {
        if (stack.isItemBarVisible()) {
            rectangle.render(ShapeProperties.create(matrix, x + 1.5F, y + 13, 13, 2).color(-16777216).build());
            rectangle.render(ShapeProperties.create(matrix, x + 1.5F, y + 13, stack.getItemBarStep(), 1).color(ColorHelper.fullAlpha(stack.getItemBarColor())).build());
        }
    }

    private int getItemColor(ItemStack stack) {
        if (ArmorDurability.INSTANCE == null || !ArmorDurability.INSTANCE.isState()) return 0xFFFFFFFF;
        if (!(stack.getItem() instanceof ArmorItem)) return 0xFFFFFFFF;
        if (!stack.isDamageable() || stack.getMaxDamage() <= 0) return 0xFFFFFFFF;
        return getArmorDurabilityColor(stack);
    }

    private int getArmorDurabilityColor(ItemStack stack) {
        float broken = stack.getDamage() / (float) stack.getMaxDamage();
        broken = clamp01(broken);
        broken = 1.0F - (1.0F - broken) * (1.0F - broken);

        int r;
        int g;
        int b;

        if (broken <= 0.5F) {
            float t = broken / 0.5F;
            r = lerp(0, 255, t);
            g = 255;
            b = 0;
        } else if (broken <= 0.75F) {
            float t = (broken - 0.5F) / 0.25F;
            r = 255;
            g = lerp(255, 140, t);
            b = 0;
        } else {
            float t = (broken - 0.75F) / 0.25F;
            r = 255;
            g = lerp(140, 0, t);
            b = 0;
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private float clamp01(float v) {
        if (v < 0.0F) return 0.0F;
        if (v > 1.0F) return 1.0F;
        return v;
    }

    private int lerp(int a, int b, float t) {
        if (t < 0.0F) t = 0.0F;
        if (t > 1.0F) t = 1.0F;
        return (int) (a + (b - a) * t);
    }

    private CachedSprite getSpriteTexture(ItemStack stack) {
        return SPRITE_CACHE.computeIfAbsent(stack, key -> {
            ItemRenderState state = new ItemRenderState();
            mc.getItemModelManager().update(state, stack, ModelTransformationMode.GUI, mc.world, null, 0);

            Sprite sprite = getFirstParticleSprite(state);
            if (sprite != null) {
                int glId = mc.getTextureManager().getTexture(sprite.getAtlasId()).getGlId();
                return new CachedSprite(sprite, glId, 0xFFFFFF);
            }

            return null;
        });
    }

    private Sprite getFirstParticleSprite(ItemRenderState state) {
        if (state.layerCount == 0) return null;
        BakedModel model = state.layers[0].model;
        if (model == null) return null;
        return model.getParticleSprite();
    }

    public record CachedSprite(Sprite sprite, int glId, int color) {}
}