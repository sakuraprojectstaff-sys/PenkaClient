package fun.rich.display.screens.clickgui.components.implement.settings.multiselect;

import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.display.screens.clickgui.components.implement.settings.AbstractSettingComponent;
import fun.rich.features.module.setting.implement.MultiSelectSetting;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static fun.rich.utils.display.font.Fonts.Type.DEFAULT;
import static fun.rich.utils.display.font.Fonts.Type.GUIICONS;
import static fun.rich.utils.display.font.Fonts.Type.SEMI;

public class MultiSelectComponent extends AbstractSettingComponent {
    private final List<MultiSelectedButton> multiSelectedButtons = new ArrayList<>();
    private final MultiSelectSetting setting;
    private boolean open;
    private float dropdownListX;
    private float dropDownListY;
    private float dropDownListWidth;
    private float dropDownListHeight;
    private final Animation alphaAnimation = new Decelerate().setMs(180).setValue(1);
    private final Animation heightAnimation = new Decelerate().setMs(180).setValue(1);

    private static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    private static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);
    private static final Color TEXT_MUTED = new Color(108, 114, 128, 255);
    private static final Color FIELD_BG = new Color(20, 24, 32, 255);
    private static final Color FIELD_STROKE = new Color(255, 255, 255, 14);
    private static final Color FIELD_ACTIVE = new Color(132, 112, 255, 60);
    private static final Color ICON_BG = new Color(20, 24, 32, 255);
    private static final Color ICON_STROKE = new Color(255, 255, 255, 14);

    public MultiSelectComponent(MultiSelectSetting setting) {
        super(setting);
        this.setting = setting;
        alphaAnimation.setDirection(Direction.BACKWARDS);
        heightAnimation.setDirection(Direction.BACKWARDS);
        for (String s : setting.getList()) {
            multiSelectedButtons.add(new MultiSelectedButton(setting, s));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrices = context.getMatrices();
        float baseHeight = 22f;

        dropDownListWidth = 74f;
        dropdownListX = x + width - dropDownListWidth - 9f;
        dropDownListY = y + 19f;
        dropDownListHeight = setting.getList().size() * 14f;

        if (open) {
            alphaAnimation.setDirection(Direction.FORWARDS);
            heightAnimation.setDirection(Direction.FORWARDS);
        } else {
            alphaAnimation.setDirection(Direction.BACKWARDS);
            heightAnimation.setDirection(Direction.BACKWARDS);
        }

        float animatedHeight = open ? dropDownListHeight : 0f;
        heightAnimation.setValue(animatedHeight);
        height = (int) (baseHeight + heightAnimation.getOutput().floatValue());

        drawLeft(matrices);
        renderSelected(matrices);

        if (!alphaAnimation.isFinished(Direction.BACKWARDS) || open) {
            renderSelectList(context, mouseX, mouseY, delta);
        }
    }

    private void drawLeft(MatrixStack matrices) {
        float iconBoxX = x + 9;
        float iconBoxY = y + 4;

        rectangle.render(ShapeProperties.create(matrices, iconBoxX, iconBoxY, 12, 12)
                .round(4f)
                .thickness(1f)
                .outlineColor(ICON_STROKE.getRGB())
                .color(ICON_BG.getRGB())
                .build());

        Fonts.getSize(13, GUIICONS).drawString(matrices, "I", iconBoxX + 3.0f, y + 12.8f, TEXT_MUTED.getRGB());
        Fonts.getSize(12, DEFAULT).drawString(matrices, setting.getName(), x + 27, y + 13.0f, TEXT_PRIMARY.getRGB());
    }

    private void renderSelected(MatrixStack matrices) {
        float fieldX = x + width - 74f - 9f;
        float fieldY = y + 3f;
        float fieldW = 74f;
        float fieldH = 14f;

        rectangle.render(ShapeProperties.create(matrices, fieldX, fieldY, fieldW, fieldH)
                .round(5f)
                .thickness(1f)
                .outlineColor(open ? FIELD_ACTIVE.getRGB() : FIELD_STROKE.getRGB())
                .color(FIELD_BG.getRGB())
                .build());

        String selectedName = String.join(", ", setting.getSelected());
        if (selectedName.isEmpty()) {
            selectedName = "...";
        }

        Fonts.getSize(11, SEMI).drawString(matrices, selectedName, fieldX + 5, fieldY + 9.5f, TEXT_SECONDARY.getRGB());
        Fonts.getSize(10, GUIICONS).drawString(matrices, open ? "N" : "M", fieldX + fieldW - 11, fieldY + 9.3f, TEXT_MUTED.getRGB());
    }

    private void renderSelectList(DrawContext context, int mouseX, int mouseY, float delta) {
        float opacity = alphaAnimation.getOutput().floatValue();
        float animatedHeight = heightAnimation.getOutput().floatValue();

        if (animatedHeight <= 0.5f) {
            return;
        }

        blur.render(ShapeProperties.create(context.getMatrices(), dropdownListX, dropDownListY, dropDownListWidth, animatedHeight)
                .round(6f)
                .quality(12)
                .color(new Color(0, 0, 0, (int) (40 * opacity)).getRGB())
                .build());

        rectangle.render(ShapeProperties.create(context.getMatrices(), dropdownListX, dropDownListY, dropDownListWidth, animatedHeight)
                .round(6f)
                .thickness(1f)
                .outlineColor(new Color(255, 255, 255, (int) (14 * opacity)).getRGB())
                .color(new Color(16, 20, 27, (int) (245 * opacity)).getRGB())
                .build());

        float offset = dropDownListY;
        for (MultiSelectedButton button : multiSelectedButtons) {
            button.x = dropdownListX + 2;
            button.y = offset + 2;
            button.width = dropDownListWidth - 4;
            button.height = 12;
            button.setAlpha(opacity);
            if (offset - dropDownListY < animatedHeight) {
                button.render(context, mouseX, mouseY, delta);
            }
            offset += 14;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float fieldX = x + width - 74f - 9f;
        float fieldY = y + 3f;

        if (button == 0) {
            if (Calculate.isHovered(mouseX, mouseY, fieldX, fieldY, 74f, 14f)) {
                open = !open;
                return true;
            } else if (open && !isHoveredList(mouseX, mouseY)) {
                open = false;
                return true;
            }

            if (open) {
                for (MultiSelectedButton selectedButton : multiSelectedButtons) {
                    if (selectedButton.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        return open && isHoveredList(mouseX, mouseY);
    }

    private boolean isHoveredList(double mouseX, double mouseY) {
        return Calculate.isHovered(mouseX, mouseY, dropdownListX, dropDownListY, dropDownListWidth, Math.max(dropDownListHeight, heightAnimation.getOutput().floatValue()));
    }
}