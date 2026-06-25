package fun.rich.display.screens.clickgui.components.implement.settings;

import fun.rich.Rich;
import fun.rich.features.module.setting.implement.TextSetting;
import fun.rich.utils.client.chat.StringHelper;
import fun.rich.utils.display.font.FontRenderer;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.scissor.ScissorAssist;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class TextComponent extends AbstractSettingComponent {
    public static boolean typing;
    final TextSetting setting;
    float rectX, rectY, rectWidth, rectHeight;
    boolean dragging;
    int cursorPosition = 0;
    int selectionStart = -1;
    int selectionEnd = -1;
    long lastClickTime = 0;
    float xOffset = 0;
    String text = "";

    static final Color TEXT_PRIMARY = new Color(236, 239, 248, 255);
    static final Color TEXT_SECONDARY = new Color(144, 150, 164, 255);
    static final Color TEXT_MUTED = new Color(108, 114, 128, 255);
    static final Color FIELD_BG = new Color(20, 24, 32, 255);
    static final Color FIELD_STROKE = new Color(255, 255, 255, 14);
    static final Color FIELD_ACTIVE = new Color(132, 112, 255, 60);
    static final Color SELECTION = new Color(132, 112, 255, 90);
    static final Color ICON_BG = new Color(20, 24, 32, 255);
    static final Color ICON_STROKE = new Color(255, 255, 255, 14);

    public TextComponent(TextSetting setting) {
        super(setting);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MatrixStack matrix = context.getMatrices();
        FontRenderer font = Fonts.getSize(12, Fonts.Type.DEFAULT);

        height = 22;

        float iconBoxX = x + 9;
        float iconBoxY = y + 4;
        rectangle.render(ShapeProperties.create(matrix, iconBoxX, iconBoxY, 12, 12)
                .round(4f)
                .thickness(1f)
                .outlineColor(ICON_STROKE.getRGB())
                .color(ICON_BG.getRGB())
                .build());

        Fonts.getSize(12, Fonts.Type.ICONS).drawString(matrix, "U", iconBoxX + 2.7f, y + 12.8f, TEXT_MUTED.getRGB());
        Fonts.getSize(12, Fonts.Type.DEFAULT).drawString(matrix, setting.getName(), x + 27, y + 13.0f, TEXT_PRIMARY.getRGB());

        rectWidth = 66.0F;
        rectHeight = 14.0F;
        rectX = x + width - rectWidth - 9.0F;
        rectY = y + 3.0F;

        rectangle.render(ShapeProperties.create(matrix, rectX, rectY, rectWidth, rectHeight)
                .round(5f)
                .thickness(1f)
                .outlineColor(typing ? FIELD_ACTIVE.getRGB() : FIELD_STROKE.getRGB())
                .color(FIELD_BG.getRGB())
                .build());

        if (text.isEmpty() && !typing) {
            text = setting.getText() == null ? "" : setting.getText();
            cursorPosition = Math.min(cursorPosition, text.length());
        }

        updateXOffset(font, cursorPosition);

        ScissorAssist scissor = Rich.getInstance().getScissorManager();
        scissor.push(matrix.peek().getPositionMatrix(), rectX + 2, rectY + 1, rectWidth - 4, rectHeight - 2);

        if (typing && hasSelection()) {
            int start = Math.max(0, Math.min(getStartOfSelection(), text.length()));
            int end = Math.max(0, Math.min(getEndOfSelection(), text.length()));
            if (start < end) {
                float selectionXStart = rectX + 4 - xOffset + font.getStringWidth(text.substring(0, start));
                float selectionXEnd = rectX + 4 - xOffset + font.getStringWidth(text.substring(0, end));
                rectangle.render(ShapeProperties.create(matrix, selectionXStart, rectY + 3, selectionXEnd - selectionXStart, 8)
                        .round(3f)
                        .color(SELECTION.getRGB())
                        .build());
            }
        }

        if (!typing && text.isEmpty()) {
            font.drawString(matrix, "Введите...", rectX + 4, rectY + 9.7f, TEXT_MUTED.getRGB());
        } else {
            font.drawString(matrix, text, rectX + 4 - xOffset, rectY + 9.7f, typing ? TEXT_PRIMARY.getRGB() : TEXT_SECONDARY.getRGB());
        }

        scissor.pop();

        long currentTime = System.currentTimeMillis();
        boolean focused = typing && currentTime % 1000 < 500;
        if (focused && !hasSelection()) {
            float cursorX = font.getStringWidth(text.substring(0, Math.min(cursorPosition, text.length())));
            rectangle.render(ShapeProperties.create(matrix, rectX + 4 - xOffset + cursorX, rectY + 3, 1f, 8)
                    .round(0.5f)
                    .color(TEXT_PRIMARY.getRGB())
                    .build());
        }

        if (dragging) {
            cursorPosition = getCursorIndexAt(mouseX);
            if (selectionStart == -1) {
                selectionStart = cursorPosition;
            }
            selectionEnd = cursorPosition;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        dragging = typing;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (Calculate.isHovered(mouseX, mouseY, rectX, rectY, rectWidth, rectHeight) && button == 0) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastClickTime < 250) {
                typing = true;
                selectionStart = 0;
                selectionEnd = text.length();
                cursorPosition = text.length();
            } else {
                typing = true;
                dragging = true;
                lastClickTime = currentTime;
                cursorPosition = getCursorIndexAt(mouseX);
                selectionStart = cursorPosition;
                selectionEnd = cursorPosition;
            }
            return true;
        } else if (button == 0) {
            if (typing && text.length() >= setting.getMin() && text.length() <= setting.getMax()) {
                setting.setText(text);
            }
            typing = false;
            dragging = false;
            clearSelection();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (typing && text.length() < setting.getMax()) {
            deleteSelectedText();
            text = text.substring(0, cursorPosition) + chr + text.substring(cursorPosition);
            cursorPosition++;
            clearSelection();
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (typing) {
            if (Screen.hasControlDown()) {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_A -> {
                        selectAllText();
                        return true;
                    }
                    case GLFW.GLFW_KEY_V -> {
                        pasteFromClipboard();
                        return true;
                    }
                    case GLFW.GLFW_KEY_C -> {
                        copyToClipboard();
                        return true;
                    }
                }
            } else {
                switch (keyCode) {
                    case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_ENTER -> {
                        handleTextModification(keyCode);
                        return true;
                    }
                    case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT -> {
                        moveCursor(keyCode);
                        return true;
                    }
                    case GLFW.GLFW_KEY_ESCAPE -> {
                        typing = false;
                        dragging = false;
                        clearSelection();
                        return true;
                    }
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    void pasteFromClipboard() {
        String clipboardText = GLFW.glfwGetClipboardString(window.getHandle());
        if (clipboardText != null) {
            replaceText(cursorPosition, cursorPosition, clipboardText);
        }
    }

    void copyToClipboard() {
        if (hasSelection()) {
            GLFW.glfwSetClipboardString(window.getHandle(), getSelectedText());
        }
    }

    void selectAllText() {
        selectionStart = 0;
        selectionEnd = text.length();
        cursorPosition = text.length();
    }

    void handleTextModification(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                replaceText(getStartOfSelection(), getEndOfSelection(), "");
            } else if (cursorPosition > 0) {
                replaceText(cursorPosition - 1, cursorPosition, "");
            }
        } else if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (text.length() >= setting.getMin() && text.length() <= setting.getMax()) {
                setting.setText(text);
                typing = false;
                dragging = false;
                clearSelection();
            }
        }
    }

    void moveCursor(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_LEFT && cursorPosition > 0) {
            cursorPosition--;
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT && cursorPosition < text.length()) {
            cursorPosition++;
        }
        updateSelectionAfterCursorMove();
    }

    void updateSelectionAfterCursorMove() {
        if (Screen.hasShiftDown()) {
            if (selectionStart == -1) selectionStart = cursorPosition;
            selectionEnd = cursorPosition;
        } else {
            clearSelection();
        }
    }

    void replaceText(int start, int end, String replacement) {
        if (replacement == null) replacement = "";
        if (start < 0) start = 0;
        if (end > text.length()) end = text.length();
        if (start > end) start = end;

        String candidate = text.substring(0, start) + replacement + text.substring(end);
        if (candidate.length() > setting.getMax()) {
            candidate = candidate.substring(0, setting.getMax());
        }

        text = candidate;
        cursorPosition = Math.min(start + replacement.length(), text.length());
        clearSelection();
    }

    boolean hasSelection() {
        return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd;
    }

    String getSelectedText() {
        return text.substring(getStartOfSelection(), getEndOfSelection());
    }

    int getStartOfSelection() {
        return Math.min(selectionStart, selectionEnd);
    }

    int getEndOfSelection() {
        return Math.max(selectionStart, selectionEnd);
    }

    void clearSelection() {
        selectionStart = -1;
        selectionEnd = -1;
    }

    int getCursorIndexAt(double mouseX) {
        FontRenderer font = Fonts.getSize(12, Fonts.Type.DEFAULT);
        float relativeX = (float) mouseX - rectX - 4 + xOffset;
        int position = 0;
        while (position < text.length()) {
            float textWidth = font.getStringWidth(text.substring(0, position + 1));
            if (textWidth > relativeX) break;
            position++;
        }
        return position;
    }

    void updateXOffset(FontRenderer font, int cursorPosition) {
        float cursorX = font.getStringWidth(text.substring(0, Math.min(cursorPosition, text.length())));
        if (cursorX < xOffset) {
            xOffset = cursorX;
        } else if (cursorX - xOffset > rectWidth - 8) {
            xOffset = cursorX - (rectWidth - 8);
        }
    }

    void deleteSelectedText() {
        if (hasSelection()) {
            replaceText(getStartOfSelection(), getEndOfSelection(), "");
        }
    }
}