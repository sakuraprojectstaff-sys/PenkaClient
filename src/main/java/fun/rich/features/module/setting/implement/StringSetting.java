package fun.rich.features.module.setting.implement;

import fun.rich.features.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.Locale;

public class StringSetting extends Setting {

    private String value = "";
    private int maxLength = 64;

    public StringSetting(String name, String description) {
        super(name, description);
    }

    public StringSetting setValue(String v) {
        value = v == null ? "" : v;
        if (value.length() > maxLength) value = value.substring(0, maxLength);
        return this;
    }

    public String getValue() {
        return value;
    }

    public StringSetting setMaxLength(int len) {
        maxLength = Math.max(1, len);
        if (value.length() > maxLength) value = value.substring(0, maxLength);
        return this;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public boolean isEmpty() {
        return value == null || value.trim().isEmpty();
    }

    public String getValueLower() {
        return (value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
    }

    public void openEditor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.setScreen(new EditorScreen(this, mc.currentScreen));
    }

    public static final class EditorScreen extends Screen {

        private final StringSetting setting;
        private final Screen parent;
        private TextFieldWidget field;

        private int panelW = 280;
        private int panelH = 90;
        private int px;
        private int py;

        public EditorScreen(StringSetting setting, Screen parent) {
            super(Text.literal(setting.getName()));
            this.setting = setting;
            this.parent = parent;
        }

        @Override
        protected void init() {
            px = (width - panelW) / 2;
            py = (height - panelH) / 2;

            field = new TextFieldWidget(textRenderer, px + 14, py + 40, panelW - 28, 18, Text.literal(setting.getName()));
            field.setMaxLength(setting.getMaxLength());
            field.setText(setting.getValue());
            addSelectableChild(field);
            setInitialFocus(field);
        }

        @Override
        public void close() {
            if (field != null) setting.setValue(field.getText());
            if (client != null) client.setScreen(parent);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) {
                close();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                close();
                return true;
            }
            if (field != null && field.keyPressed(keyCode, scanCode, modifiers)) return true;
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (field != null && field.charTyped(chr, modifiers)) return true;
            return super.charTyped(chr, modifiers);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xA0000000);
            context.fill(px, py, px + panelW, py + panelH, 0xFF111418);
            context.fill(px + 1, py + 1, px + panelW - 1, py + panelH - 1, 0xFF171B21);

            context.drawCenteredTextWithShadow(textRenderer, Text.literal(setting.getName()), px + panelW / 2, py + 14, 0xFFFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal("Введите значение и нажмите Enter"), px + 14, py + 28, 0xFFB8C0CC);

            if (field != null) field.render(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
        }
    }
}