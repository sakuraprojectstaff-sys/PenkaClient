package fun.rich.display.screens.clickgui.components.implement.other;

import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.display.screens.clickgui.components.AbstractComponent;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;

import java.awt.Color;

import static fun.rich.common.animation.Direction.BACKWARDS;
import static fun.rich.common.animation.Direction.FORWARDS;

@Setter
@Accessors(chain = true)
public class StatusRender extends AbstractComponent {
    private boolean state;
    private Runnable runnable;
    private float alphaMultiplier = 1.0f;

    private final Animation trackAnimation = new Decelerate().setMs(180).setValue(1);
    private final Animation knobAnimation = new Decelerate().setMs(180).setValue(1);
    private final Animation glowAnimation = new Decelerate().setMs(220).setValue(1);

    private static final float TRACK_WIDTH = 22f;
    private static final float TRACK_HEIGHT = 12f;
    private static final float KNOB_SIZE = 8f;
    private static final float KNOB_PADDING = 2f;
    private static final float KNOB_TRAVEL = TRACK_WIDTH - KNOB_SIZE - KNOB_PADDING * 2f;

    private static final Color TRACK_OFF = new Color(29, 33, 42, 255);
    private static final Color TRACK_ON = new Color(132, 112, 255, 255);
    private static final Color TRACK_STROKE_OFF = new Color(255, 255, 255, 16);
    private static final Color TRACK_STROKE_ON = new Color(255, 255, 255, 28);
    private static final Color KNOB = new Color(245, 247, 252, 255);
    private static final Color KNOB_STROKE = new Color(255, 255, 255, 30);

    public StatusRender() {
        trackAnimation.setDirection(state ? FORWARDS : BACKWARDS);
        knobAnimation.setDirection(state ? FORWARDS : BACKWARDS);
        glowAnimation.setDirection(state ? FORWARDS : BACKWARDS);
        trackAnimation.reset();
        knobAnimation.reset();
        glowAnimation.reset();
    }

    @Override
    public StatusRender position(float x, float y) {
        this.x = x - TRACK_WIDTH;
        this.y = y - TRACK_HEIGHT / 2f;
        return this;
    }

    public StatusRender setAlphaMultiplier(float alphaMultiplier) {
        this.alphaMultiplier = alphaMultiplier;
        return this;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();

        trackAnimation.setDirection(state ? FORWARDS : BACKWARDS);
        knobAnimation.setDirection(state ? FORWARDS : BACKWARDS);
        glowAnimation.setDirection(state ? FORWARDS : BACKWARDS);

        float trackProgress = trackAnimation.getOutput().floatValue();
        float knobProgress = knobAnimation.getOutput().floatValue();
        float glowProgress = glowAnimation.getOutput().floatValue();

        Color trackColor = blend(TRACK_OFF, TRACK_ON, trackProgress);
        Color strokeColor = blend(TRACK_STROKE_OFF, TRACK_STROKE_ON, trackProgress);

        int trackArgb = applyAlpha(trackColor, alphaMultiplier).getRGB();
        int strokeArgb = applyAlpha(strokeColor, alphaMultiplier).getRGB();
        int knobArgb = applyAlpha(KNOB, alphaMultiplier).getRGB();
        int knobStrokeArgb = applyAlpha(KNOB_STROKE, alphaMultiplier).getRGB();

        if (glowProgress > 0.01f) {
            blur.render(ShapeProperties.create(matrix, x - 1f, y - 1f, TRACK_WIDTH + 2f, TRACK_HEIGHT + 2f)
                    .round(7f)
                    .quality(14)
                    .color(applyAlpha(new Color(TRACK_ON.getRed(), TRACK_ON.getGreen(), TRACK_ON.getBlue(), 36), glowProgress * alphaMultiplier).getRGB())
                    .build());
        }

        rectangle.render(ShapeProperties.create(matrix, x, y, TRACK_WIDTH, TRACK_HEIGHT)
                .round(TRACK_HEIGHT / 2f)
                .thickness(1f)
                .outlineColor(strokeArgb)
                .color(trackArgb)
                .build());

        float knobX = x + KNOB_PADDING + KNOB_TRAVEL * knobProgress;
        float knobY = y + (TRACK_HEIGHT - KNOB_SIZE) / 2f;

        rectangle.render(ShapeProperties.create(matrix, knobX, knobY, KNOB_SIZE, KNOB_SIZE)
                .round(KNOB_SIZE / 2f)
                .thickness(1f)
                .outlineColor(knobStrokeArgb)
                .color(knobArgb)
                .build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Calculate.isHovered(mouseX, mouseY, x, y, TRACK_WIDTH, TRACK_HEIGHT) && button == 0) {
            state = !state;
            if (runnable != null) {
                runnable.run();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static Color blend(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped);
        return new Color(r, g, bl, al);
    }

    private static Color applyAlpha(Color color, float mul) {
        int a = clamp255((int) (color.getAlpha() * mul));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }
}