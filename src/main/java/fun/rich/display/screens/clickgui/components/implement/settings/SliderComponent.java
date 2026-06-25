package fun.rich.display.screens.clickgui.components.implement.settings;

import fun.rich.Rich;
import fun.rich.features.module.setting.implement.SliderSettings;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.scissor.ScissorAssist;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static fun.rich.utils.display.font.Fonts.Type.BOLD;
import static fun.rich.utils.display.font.Fonts.Type.DEFAULT;
import static fun.rich.utils.display.font.Fonts.Type.GUIICONS;
import static fun.rich.utils.display.font.Fonts.Type.REGULAR;
import static fun.rich.utils.display.font.Fonts.Type.SEMI;

public class SliderComponent extends AbstractSettingComponent {
    public static final int SLIDER_WIDTH = 72;

    private final SliderSettings setting;
    private boolean dragging;
    private double animation;

    private static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    private static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);
    private static final Color TEXT_MUTED = new Color(108, 114, 128, 255);
    private static final Color CHIP_BG = new Color(20, 24, 32, 255);
    private static final Color CHIP_STROKE = new Color(255, 255, 255, 14);
    private static final Color TRACK_BG = new Color(28, 33, 43, 255);
    private static final Color TRACK_STROKE = new Color(255, 255, 255, 12);
    private static final Color KNOB = new Color(245, 247, 252, 255);
    private static final Color KNOB_STROKE = new Color(255, 255, 255, 28);

    public SliderComponent(SliderSettings setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        height = 22;

        float rowY = y + 11f;
        float valueBoxW = 34f;
        float valueBoxH = 14f;
        float valueBoxX = x + width - valueBoxW - 9f;
        float valueBoxY = y + 4f;

        String valueText = formatValue(setting.getValue());

        rectangle.render(ShapeProperties.create(matrix, valueBoxX, valueBoxY, valueBoxW, valueBoxH)
                .round(5f)
                .thickness(1f)
                .outlineColor(CHIP_STROKE.getRGB())
                .color(CHIP_BG.getRGB())
                .build());

        Fonts.getSize(11, SEMI).drawCenteredString(matrix, valueText, valueBoxX + valueBoxW / 2f, valueBoxY + 9.8f, TEXT_PRIMARY.getRGB());

        Fonts.getSize(16, GUIICONS).drawString(matrix, "H", x + 11f, rowY + 0.5f, TEXT_MUTED.getRGB());

        float nameMaxWidth = width - 120f;
        float nameX = x + 25f;
        if (Fonts.getSize(12, DEFAULT).getStringWidth(setting.getName()) > nameMaxWidth) {
            ScissorAssist scissor = Rich.getInstance().getScissorManager();
            scissor.push(matrix.peek().getPositionMatrix(), nameX, y + 3f, nameMaxWidth, 18f);
            Fonts.getSize(12, DEFAULT).drawStringWithScroll(matrix, setting.getName(), nameX, rowY + 1f, nameMaxWidth - 2f, TEXT_PRIMARY.getRGB());
            scissor.pop();
        } else {
            Fonts.getSize(12, DEFAULT).drawString(matrix, setting.getName(), nameX, rowY + 1f, TEXT_PRIMARY.getRGB());
        }

        changeValue(renderSlider(mouseX, matrix));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        dragging = Calculate.isHovered(mouseX, mouseY, x + width - SLIDER_WIDTH - 9, y + 13, SLIDER_WIDTH, 8) && button == 0;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private float renderSlider(int mouseX, MatrixStack matrix) {
        float sliderX = x + width - SLIDER_WIDTH - 9;
        float sliderY = y + 15;
        float percentValue = SLIDER_WIDTH * (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        float difference = MathHelper.clamp(mouseX - sliderX, 0, SLIDER_WIDTH);

        animation = Calculate.interpolate(animation, percentValue);

        rectangle.render(ShapeProperties.create(matrix, sliderX, sliderY, SLIDER_WIDTH, 3.5f)
                .round(2f)
                .thickness(1f)
                .outlineColor(TRACK_STROKE.getRGB())
                .color(TRACK_BG.getRGB())
                .build());

        rectangle.render(ShapeProperties.create(matrix, sliderX, sliderY, (float) animation, 3.5f)
                .round(2f)
                .color(
                        getAccent(180),
                        getAccent(220),
                        getAccent(220),
                        getAccent(180))
                .build());

        float knobCenterX = sliderX + (float) animation;
        float knobX = MathHelper.clamp(knobCenterX - 4f, sliderX - 1f, sliderX + SLIDER_WIDTH - 7f);

        blur.render(ShapeProperties.create(matrix, knobX - 1f, sliderY - 3f, 10f, 10f)
                .round(5f)
                .quality(12)
                .color(new Color((getAccent(255) >> 16) & 255, (getAccent(255) >> 8) & 255, getAccent(255) & 255, 34).getRGB())
                .build());

        rectangle.render(ShapeProperties.create(matrix, knobX, sliderY - 2f, 8f, 8f)
                .round(4f)
                .thickness(1f)
                .outlineColor(KNOB_STROKE.getRGB())
                .color(KNOB.getRGB())
                .build());

        return difference;
    }

    private void changeValue(float difference) {
        BigDecimal bd = BigDecimal.valueOf((difference / SLIDER_WIDTH) * (setting.getMax() - setting.getMin()) + setting.getMin())
                .setScale(2, RoundingMode.HALF_UP);

        if (dragging) {
            float value = difference == 0 ? setting.getMin() : bd.floatValue();
            if (setting.isInteger()) value = (int) value;
            setting.setValue(value);
        }
    }

    private String formatValue(float value) {
        if (setting.isInteger()) {
            return String.valueOf((int) value);
        }
        BigDecimal bd = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return bd.toPlainString();
    }

    private static int getAccent(int alpha) {
        int color = new Color(132, 112, 255, 255).getRGB();
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }
}