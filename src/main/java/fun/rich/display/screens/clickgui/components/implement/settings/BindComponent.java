package fun.rich.display.screens.clickgui.components.implement.settings;

import fun.rich.features.module.setting.implement.BindSetting;
import fun.rich.utils.client.chat.StringHelper;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

import static fun.rich.utils.display.font.Fonts.Type.DEFAULT;
import static fun.rich.utils.display.font.Fonts.Type.GUIICONS;
import static fun.rich.utils.display.font.Fonts.Type.SEMI;

public class BindComponent extends AbstractSettingComponent {
    private final BindSetting setting;
    private boolean binding;

    private static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    private static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);
    private static final Color TEXT_MUTED = new Color(108, 114, 128, 255);
    private static final Color CHIP_BG = new Color(20, 24, 32, 255);
    private static final Color CHIP_STROKE = new Color(255, 255, 255, 14);
    private static final Color ICON_BG = new Color(20, 24, 32, 255);
    private static final Color ICON_STROKE = new Color(255, 255, 255, 14);

    public BindComponent(BindSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        height = 20;

        float iconBoxX = x + 9;
        float iconBoxY = y + 4;
        float iconBoxW = 12;
        float iconBoxH = 12;

        rectangle.render(ShapeProperties.create(matrix, iconBoxX, iconBoxY, iconBoxW, iconBoxH)
                .round(4f)
                .thickness(1f)
                .outlineColor(ICON_STROKE.getRGB())
                .color(ICON_BG.getRGB())
                .build());

        Fonts.getSize(13, GUIICONS).drawString(matrix, "L", iconBoxX + 3.0f, y + 12.8f, TEXT_MUTED.getRGB());
        Fonts.getSize(12, DEFAULT).drawString(matrix, setting.getName(), x + 27, y + 13.0f, TEXT_PRIMARY.getRGB());

        String bindName = StringHelper.getBindName(setting.getKey());
        String name = binding ? "..." : bindName;
        float textWidth = setting.getKey() < 0 && !binding ? 10 : Fonts.getSize(11, SEMI).getStringWidth(name);
        float boxWidth = Math.max(20f, textWidth + 10f);
        float boxX = x + width - boxWidth - 9f;
        float boxY = y + 3f;

        rectangle.render(ShapeProperties.create(matrix, boxX, boxY, boxWidth, 14f)
                .round(5f)
                .thickness(1f)
                .outlineColor(binding ? getAccent(40) : CHIP_STROKE.getRGB())
                .color(binding ? blend(CHIP_BG, new Color(getAccent(18), true), 0.8).getRGB() : CHIP_BG.getRGB())
                .build());

        if (setting.getKey() < 0 && !binding) {
            Fonts.getSize(16, GUIICONS).drawString(matrix, "G", boxX + 4f, boxY + 9.8f, TEXT_MUTED.getRGB());
        } else {
            Fonts.getSize(11, SEMI).drawCenteredString(matrix, name, boxX + boxWidth / 2f, boxY + 9.7f,
                    binding ? blend(TEXT_PRIMARY, new Color(getAccent(255), true), 0.15).getRGB() : TEXT_SECONDARY.getRGB());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float bindNameWidth = setting.getKey() < 0 && !binding
                ? 10
                : Fonts.getSize(11, SEMI).getStringWidth(binding ? "..." : StringHelper.getBindName(setting.getKey()));
        float boxWidth = Math.max(20f, bindNameWidth + 10f);
        float boxX = x + width - boxWidth - 9f;
        float boxY = y + 3f;

        if (button == 0) {
            if (Calculate.isHovered(mouseX, mouseY, boxX, boxY, boxWidth, 14f)) {
                binding = !binding;
                return true;
            } else {
                binding = false;
            }
        }

        if (binding && button > 1) {
            setting.setKey(button);
            binding = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode == GLFW.GLFW_KEY_DELETE ? -1 : keyCode;
        if (binding) {
            setting.setKey(key);
            binding = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static int getAccent(int alpha) {
        int color = new Color(132, 112, 255, 255).getRGB();
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static Color blend(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped);
        return new Color(r, g, bl, al);
    }
}