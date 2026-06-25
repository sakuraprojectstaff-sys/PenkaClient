package fun.rich.display.screens.clickgui.components.implement.settings;

import fun.rich.display.screens.clickgui.components.implement.other.CheckComponent;
import fun.rich.features.module.setting.implement.BooleanSetting;
import fun.rich.features.module.setting.implement.ItemBooleanSetting;
import fun.rich.utils.display.font.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

import java.awt.Color;

import static fun.rich.utils.display.font.Fonts.Type.DEFAULT;
import static fun.rich.utils.display.font.Fonts.Type.GUIICONS;

public class CheckboxComponent extends AbstractSettingComponent {
    private final CheckComponent checkComponent = new CheckComponent();
    private final BooleanSetting setting;

    private static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    private static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);
    private static final Color ICON_BG = new Color(20, 24, 32, 255);
    private static final Color ICON_STROKE = new Color(255, 255, 255, 14);

    public CheckboxComponent(BooleanSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack ms = context.getMatrices();
        height = 20;

        boolean itemBoolean = setting instanceof ItemBooleanSetting;
        float iconBoxX = x + 9;
        float iconBoxY = y + 4;
        float iconBoxW = 12;
        float iconBoxH = 12;

        rectangle.render(fun.rich.utils.display.shape.ShapeProperties.create(ms, iconBoxX, iconBoxY, iconBoxW, iconBoxH)
                .round(4f)
                .thickness(1f)
                .outlineColor(ICON_STROKE.getRGB())
                .color(ICON_BG.getRGB())
                .build());

        if (itemBoolean) {
            ItemBooleanSetting ib = (ItemBooleanSetting) setting;
            ms.push();
            ms.translate(iconBoxX + 1.5f, iconBoxY + 1.5f, 0);
            ms.scale(0.62f, 0.62f, 1.0f);
            context.drawItem(new ItemStack(ib.getItem()), 0, 0);
            ms.pop();
        } else {
            Fonts.getSize(16, GUIICONS).drawString(ms, "K", iconBoxX + 2.7f, y + 13.0f, TEXT_SECONDARY.getRGB());
        }

        String name = cleanName(setting.getName());
        Fonts.Type textType = itemBoolean ? resolveItemBooleanTextType() : DEFAULT;
        float textY = itemBoolean ? y + 13.0f : y + 13.0f;

        Fonts.getSize(12, textType).drawString(ms, name, x + 27, textY, TEXT_PRIMARY.getRGB());

        checkComponent.position(x + width - 16, y + 10)
                .setRunnable(() -> setting.setValue(!setting.isValue()))
                .setState(setting.isValue())
                .render(context, mouseX, mouseY, delta);
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.regionMatches(true, 0, "Мусор:", 0, 6)) {
            t = t.substring(6).trim();
        } else if (t.regionMatches(true, 0, "Мусор :", 0, 7)) {
            t = t.substring(7).trim();
        }
        return t;
    }

    private static Fonts.Type resolveItemBooleanTextType() {
        return resolveFontType("BOLD", "SEMI_BOLD", "SEMIBOLD", "MEDIUM", "DEFAULT");
    }

    private static Fonts.Type resolveFontType(String... names) {
        for (String n : names) {
            try {
                return Enum.valueOf(Fonts.Type.class, n);
            } catch (Throwable ignored) {
            }
        }
        return Fonts.Type.DEFAULT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        checkComponent.mouseClicked(mouseX, mouseY, button);
        return super.mouseClicked(mouseX, mouseY, button);
    }
}