package fun.rich.display.hud;

import fun.rich.features.impl.render.Hud;
import fun.rich.utils.client.managers.api.draggable.AbstractDraggable;
import fun.rich.utils.display.color.ColorAssist;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;

import java.util.stream.IntStream;

public class HotBar extends AbstractDraggable {
    private float selectItemX;

    public HotBar() {
        super("HotBar", 0, 50, 182, 22, false);
    }

    @Override
    public void drawDraggable(DrawContext context) {
        if (mc.player == null) return;

        MatrixStack matrix = context.getMatrices();
        PlayerInventory inventory = mc.player.getInventory();
        ItemStack offHand = mc.player.getOffHandStack();

        selectItemX = Calculate.interpolateSmooth(1, selectItemX, inventory.selectedSlot * 20);
        setX((mc.getWindow().getScaledWidth() - getWidth()) / 2);
        setY(mc.getWindow().getScaledHeight() - 27);

        blur.render(ShapeProperties.create(matrix, getX() - 0.5F, getY() - 0.5F, getWidth() + 1, 23F)
                .quality(5)
                .round(4)
                .softness(1)
                .thickness(2)
                .outlineColor(ColorAssist.getOutline(0))
                .color(ColorAssist.getRect(0.6F))
                .build());

        blur.render(ShapeProperties.create(matrix, getX() + selectItemX + 1, getY() + 1, 20, 20)
                .quality(5)
                .round(4.5F)
                .softness(1)
                .thickness(2)
                .outlineColor(ColorAssist.getRect(0.15F))
                .color(ColorAssist.getRect(0.15F))
                .build());

        IntStream.range(0, 9).forEach(i -> drawStack(context, inventory.main.get(i), getX() + i * 20 + 2, getY() + 2, false));

        float selectedX = getX() + selectItemX + 1;
        float selectedY = getY() + 1;
        int hudColor = Hud.getInstance() != null ? Hud.getInstance().colorSetting.getColor() : ColorAssist.rgba(255, 255, 255, 255);

        rectangle.render(ShapeProperties.create(matrix, selectedX, selectedY, 20, 20)
                .round(4.5F)
                .thickness(2.2F)
                .outlineColor(ColorAssist.multAlpha(hudColor, 0.95F))
                .color(ColorAssist.multAlpha(hudColor, 0.18F))
                .build());

        rectangle.render(ShapeProperties.create(matrix, selectedX + 1, selectedY + 1, 18, 18)
                .round(3.8F)
                .thickness(1.2F)
                .outlineColor(ColorAssist.multAlpha(hudColor, 0.45F))
                .color(ColorAssist.rgba(0, 0, 0, 0))
                .build());

        if (!offHand.isEmpty()) {
            float offX = getX() + (mc.player.getMainArm().equals(Arm.RIGHT) ? -28 : 198);
            drawStack(context, offHand, offX, getY() + 2, true);
        }

        if (!mc.player.isSpectator() && !mc.player.isCreative()) {
            drawExperienceBar(matrix);
        }

        drawOverlayInfo(matrix);
    }

    public void drawExperienceBar(MatrixStack matrix) {
        if (mc.player == null) return;

        FontRenderer font = Fonts.getSize(16, Fonts.Type.DEFAULT);
        String level = String.valueOf(mc.player.experienceLevel);
        float x = mc.getWindow().getScaledWidth() / 2F - font.getStringWidth(level) / 2F;
        font.drawString(matrix, level, x, getY() - 9.5F, ColorAssist.rgba(120, 255, 120, 255));
    }

    public void drawOverlayInfo(MatrixStack matrix) {
        float scaledWidth = mc.getWindow().getScaledWidth() / 2F;
        float heightStart = mc.getWindow().getScaledHeight() - 75;
        float paddingX = 4F;
        float paddingY = 3F;
        FontRenderer font = Fonts.getSize(14, Fonts.Type.DEFAULT);

        if (mc.inGameHud.heldItemTooltipFade > 0 && mc.inGameHud.currentStack != null) {
            float alpha = ((float) mc.inGameHud.heldItemTooltipFade * 256.0F / 10.0F) / 255.0F;
            Text text = mc.inGameHud.currentStack.getName();
            String textStr = text.getString();
            float width = font.getStringWidth(textStr);
            float textHeight = font.getStringHeight(textStr);
            int x = (int) (scaledWidth - width / 2F);

            blur.render(ShapeProperties.create(matrix, x - paddingX, heightStart - paddingY, width + paddingX * 2F, textHeight / 2.15F + paddingY * 2F)
                    .round(2.5F)
                    .color(ColorAssist.multAlpha(ColorAssist.getRect(0.7F), alpha))
                    .build());

            font.drawString(matrix, textStr, x, heightStart + 2.5F, ColorAssist.multAlpha(ColorAssist.getText(), alpha));
        }

        if (mc.inGameHud.overlayRemaining > 0 && mc.inGameHud.overlayMessage != null && !mc.inGameHud.overlayMessage.getString().isEmpty()) {
            float alpha = ((float) mc.inGameHud.overlayRemaining * 256.0F / 10.0F) / 255.0F;
            Text text = mc.inGameHud.overlayMessage;
            String textStr = text.getString();
            float width = font.getStringWidth(textStr);
            float textHeight = font.getStringHeight(textStr);
            int x = (int) (scaledWidth - width / 2F);

            blur.render(ShapeProperties.create(matrix, x - paddingX, heightStart - paddingY - 17, width + paddingX * 2F, textHeight / 2.15F + paddingY * 2F)
                    .round(2.5F)
                    .color(ColorAssist.multAlpha(ColorAssist.getRect(0.7F), alpha))
                    .build());

            font.drawString(matrix, textStr, x, heightStart - 14.5F, ColorAssist.multAlpha(ColorAssist.getText(), alpha));
        }
    }

    public void drawStack(DrawContext context, ItemStack stack, float x, float y, boolean offHand) {
        if (offHand) {
            blur.render(ShapeProperties.create(context.getMatrices(), x - 2.5F, y - 2.5F, 23, 23)
                    .round(3)
                    .thickness(2)
                    .softness(1)
                    .outlineColor(ColorAssist.getOutline(0))
                    .color(ColorAssist.getRect(0.7F))
                    .build());
        }
        Render2D.defaultDrawStack(context, stack, x, y, false, true, 1);
    }
}