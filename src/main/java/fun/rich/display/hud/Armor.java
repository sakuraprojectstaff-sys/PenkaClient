package fun.rich.display.hud;

import com.google.common.collect.Lists;
import fun.rich.features.impl.render.Hud;
import fun.rich.utils.client.managers.api.draggable.AbstractDraggable;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.shape.ShapeProperties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.awt.Color;
import java.util.List;

public class Armor extends AbstractDraggable {
    private static final int SLOT_COUNT = 4;
    private static final float SLOT_SIZE = 18F;
    private static final float SLOT_GAP = 2.5F;
    private static final float PADDING_X = 2.5F;
    private static final float PADDING_Y = 2F;
    private static final float ITEM_SCALE = 0.84F;
    private static final float WIDTH = PADDING_X * 2F + SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
    private static final float HEIGHT = PADDING_Y * 2F + SLOT_SIZE;

    public Armor() {
        super("Armor", 0, 0, (int) WIDTH, (int) HEIGHT, false);
    }

    @Override
    public boolean visible() {
        return true;
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;

        if (!isDragging()) {
            int scaledWidth = context.getScaledWindowWidth();
            int scaledHeight = context.getScaledWindowHeight();
            setX(scaledWidth / 2 + 94);
            setY(scaledHeight - 27);
        }

        float x = getX();
        float y = getY();
        int hudColor = Hud.getInstance() != null ? Hud.getInstance().colorSetting.getColor() : ColorAssist.rgba(255, 255, 255, 255);

        blur.render(ShapeProperties.create(context.getMatrices(), x - 0.5F, y - 0.5F, getWidth() + 1F, getHeight() + 1F)
                .quality(5)
                .round(5F)
                .softness(1F)
                .thickness(2F)
                .outlineColor(ColorAssist.getOutline(0))
                .color(ColorAssist.getRect(0.6F))
                .build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), x, y, getWidth(), getHeight())
                .round(5F)
                .thickness(1F)
                .outlineColor(ColorAssist.getOutline(0))
                .color(
                        ColorAssist.getRect(0.68F),
                        ColorAssist.getRect(0.68F),
                        ColorAssist.getRect(0.58F),
                        ColorAssist.getRect(0.58F)
                )
                .build());

        List<ItemStack> armorList = Lists.reverse(mc.player.getInventory().armor);

        for (int i = 0; i < SLOT_COUNT; i++) {
            float slotX = x + PADDING_X + i * (SLOT_SIZE + SLOT_GAP);
            float slotY = y + PADDING_Y;

            ItemStack stack = i < armorList.size() ? armorList.get(i) : ItemStack.EMPTY;
            boolean filled = !stack.isEmpty();

            int slotOutline = filled ? ColorAssist.multAlpha(hudColor, 0.75F) : ColorAssist.getOutline(0);
            int slotFill = filled ? ColorAssist.multAlpha(hudColor, 0.08F) : ColorAssist.getRect(0.52F);

            rectangle.render(ShapeProperties.create(context.getMatrices(), slotX, slotY, SLOT_SIZE, SLOT_SIZE)
                    .round(4F)
                    .thickness(1.4F)
                    .outlineColor(slotOutline)
                    .color(slotFill)
                    .build());

            if (filled) {
                float itemSize = 16F * ITEM_SCALE;
                float itemX = slotX + (SLOT_SIZE - itemSize) / 2F - 1F;
                float itemY = slotY + (SLOT_SIZE - itemSize) / 2F - 1F;
                Render2D.defaultDrawStack(context, stack, itemX, itemY, false, false, ITEM_SCALE);

                if (stack.isDamageable()) {
                    float ratio = 1F - (float) stack.getDamage() / (float) stack.getMaxDamage();
                    ratio = Math.max(0F, Math.min(1F, ratio));

                    float barX = slotX + 2F;
                    float barY = slotY + SLOT_SIZE - 2.8F;
                    float barW = SLOT_SIZE - 4F;

                    rectangle.render(ShapeProperties.create(context.getMatrices(), barX, barY, barW, 1.8F)
                            .round(1F)
                            .color(ColorAssist.rgba(0, 0, 0, 120))
                            .build());

                    rectangle.render(ShapeProperties.create(context.getMatrices(), barX, barY, barW * ratio, 1.8F)
                            .round(1F)
                            .color(durabilityColor(ratio))
                            .build());
                }
            }
        }
    }

    private int durabilityColor(float ratio) {
        ratio = Math.max(0F, Math.min(1F, ratio));
        if (ratio > 0.66F) {
            float t = (ratio - 0.66F) / 0.34F;
            return lerpColor(new Color(255, 200, 0, 255), new Color(70, 255, 70, 255), t);
        }
        if (ratio > 0.33F) {
            float t = (ratio - 0.33F) / 0.33F;
            return lerpColor(new Color(255, 120, 0, 255), new Color(255, 200, 0, 255), t);
        }
        float t = ratio / 0.33F;
        return lerpColor(new Color(255, 60, 60, 255), new Color(255, 120, 0, 255), t);
    }

    private int lerpColor(Color a, Color b, float t) {
        t = Math.max(0F, Math.min(1F, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return ColorAssist.rgba(r, g, bl, al);
    }
}