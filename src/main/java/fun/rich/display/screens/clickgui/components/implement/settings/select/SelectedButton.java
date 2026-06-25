package fun.rich.display.screens.clickgui.components.implement.settings.select;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import fun.rich.features.module.setting.implement.SelectSetting;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.display.screens.clickgui.components.AbstractComponent;
import fun.rich.utils.math.calc.Calculate;

import java.awt.*;
import java.util.List;

import static fun.rich.utils.display.font.Fonts.Type.SEMI;

public class SelectedButton extends AbstractComponent {
    private final SelectSetting setting;
    private final String text;

    @Setter
    @Accessors(chain = true)
    private float alpha;

    private final Animation alphaAnimation = new Decelerate().setMs(180).setValue(1);

    private static final Color ACTIVE_BG = new Color(132, 112, 255, 42);
    private static final Color HOVER_BG = new Color(255, 255, 255, 8);
    private static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    private static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);

    public SelectedButton(SelectSetting setting, String text) {
        this.setting = setting;
        this.text = text;
        this.alphaAnimation.setDirection(Direction.BACKWARDS);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        boolean selected = setting.isSelected(text);
        boolean hovered = Calculate.isHovered(mouseX, mouseY, x, y, width, height);

        alphaAnimation.setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);

        float selectedAnim = alphaAnimation.getOutput().floatValue();
        float a = (float) Calculate.clamp(alpha, 0f, 1f);

        if (hovered) {
            rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                    .round(getRound(setting.getList(), text))
                    .color(applyAlpha(HOVER_BG, a).getRGB())
                    .build());
        }

        if (!alphaAnimation.isFinished(Direction.BACKWARDS)) {
            rectangle.render(ShapeProperties.create(matrix, x, y, width, height)
                    .round(getRound(setting.getList(), text))
                    .color(applyAlpha(ACTIVE_BG, selectedAnim * a).getRGB())
                    .build());

            rectangle.render(ShapeProperties.create(matrix, x + 4, y + height / 2f - 1f, 2f, 2f)
                    .round(1f)
                    .color(getAccent((int) (255 * selectedAnim * a)))
                    .build());
        }

        Fonts.getSize(11, SEMI).drawString(
                matrix,
                text,
                x + 10,
                y + 8.8f,
                blend(TEXT_SECONDARY, TEXT_PRIMARY, selected ? 0.8 : hovered ? 0.35 : 0.0, a).getRGB()
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Calculate.isHovered(mouseX, mouseY, x, y, width, height) && button == 0) {
            setting.setSelected(text);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public static Vector4f getRound(List<String> list, String text) {
        if (list.size() == 1) return new Vector4f(4f, 4f, 4f, 4f);
        if (list.get(list.size() - 1).equals(text)) return new Vector4f(0f, 0f, 4f, 4f);
        if (list.get(0).equals(text)) return new Vector4f(4f, 4f, 0f, 0f);
        return new Vector4f(0f, 0f, 0f, 0f);
    }

    private static int getAccent(int alpha) {
        int color = new Color(132, 112, 255, 255).getRGB();
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static Color applyAlpha(Color color, double mul) {
        int a = clamp255((int) Math.round(color.getAlpha() * mul));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static Color blend(Color from, Color to, double t, double alphaMul) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped);
        int g = (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped);
        int b = (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped);
        int a = (int) Math.round((from.getAlpha() + (to.getAlpha() - from.getAlpha()) * clamped) * alphaMul);
        return new Color(r, g, b, clamp255(a));
    }
}