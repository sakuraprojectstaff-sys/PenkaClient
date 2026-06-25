package fun.rich.display.screens.clickgui.components.implement.category;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import fun.rich.Rich;
import fun.rich.common.animation.Animation;
import fun.rich.common.animation.Direction;
import fun.rich.common.animation.implement.Decelerate;
import fun.rich.display.screens.clickgui.MenuScreen;
import fun.rich.display.screens.clickgui.components.AbstractComponent;
import fun.rich.display.screens.clickgui.components.implement.module.ModuleComponent;
import fun.rich.features.module.Module;
import fun.rich.features.module.ModuleCategory;
import fun.rich.utils.display.font.Fonts;
import fun.rich.utils.display.scissor.ScissorAssist;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryComponent extends AbstractComponent {
    private final List<ModuleComponent> moduleComponents = new ArrayList<>();
    private static final Set<ModuleComponent> globalModuleComponents = new HashSet<>();
    private final ModuleCategory category;
    private final Animation selectAnimation = new Decelerate().setMs(220).setValue(1);
    private final Animation hoverAnimation = new Decelerate().setMs(180).setValue(1);
    private boolean initializedAnimations = false;
    private float scroll = 0;
    private float smoothedScroll = 0;

    private static final Color SIDEBAR_ITEM = new Color(15, 18, 24, 0);
    private static final Color SIDEBAR_ITEM_HOVER = new Color(255, 255, 255, 12);
    private static final Color SIDEBAR_ITEM_ACTIVE = new Color(255, 255, 255, 18);
    private static final Color TEXT_ACTIVE = new Color(236, 239, 248, 255);
    private static final Color TEXT_IDLE = new Color(138, 145, 160, 255);
    private static final Color TEXT_HOVER = new Color(200, 206, 219, 255);

    public CategoryComponent(ModuleCategory category) {
        this.category = category;
        initializeModules();
    }

    private void initializeModules() {
        List<Module> modules = Rich.getInstance().getModuleRepository().modules();
        for (Module module : modules) {
            ModuleComponent newComponent = new ModuleComponent(module);
            if (globalModuleComponents.add(newComponent)) {
                moduleComponents.add(newComponent);
            }
        }
    }

    public void postInitialize() {
        if (!initializedAnimations) {
            if (MenuScreen.INSTANCE.getCategory().equals(category)) {
                selectAnimation.setDirection(Direction.FORWARDS);
                hoverAnimation.setDirection(Direction.BACKWARDS);
                selectAnimation.reset();
                hoverAnimation.reset();
                selectAnimation.setMs(0);
                hoverAnimation.setMs(0);
            } else {
                selectAnimation.setDirection(Direction.BACKWARDS);
                hoverAnimation.setDirection(Direction.BACKWARDS);
                selectAnimation.reset();
                hoverAnimation.reset();
                selectAnimation.setMs(0);
                hoverAnimation.setMs(0);
            }
            initializedAnimations = true;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        postInitialize();
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        globalModuleComponents.clear();

        Matrix4f positionMatrix = context.getMatrices().peek().getPositionMatrix();
        ScissorAssist scissorManager = Rich.getInstance().getScissorManager();

        drawCategoryTab(context, context.getMatrices(), mouseX, mouseY);

        int[] offsets = calculateOffsets();
        int columnWidth = 142;
        int column = 0;
        int maxScroll = 0;
        float offsetX = 35f;
        float offsetY = 14f;

        scissorManager.push(positionMatrix,
                menuScreen.x + offsetX - 75,
                menuScreen.y + offsetY + 15,
                menuScreen.width - offsetX + 150,
                menuScreen.height - offsetY - 15);

        for (int i = moduleComponents.size() - 1; i >= 0; i--) {
            ModuleComponent component = moduleComponents.get(i);
            if (shouldRenderComponent(component)) {
                int componentHeight = component.getComponentHeight() + 10;
                component.x = menuScreen.x + 32 + (column * (columnWidth + 48));
                component.y = (float) (menuScreen.y + 37 + offsets[column] - componentHeight + smoothedScroll);
                component.width = columnWidth + 40;

                if (component.y > menuScreen.y - componentHeight && menuScreen.y + menuScreen.height + 15 > component.y) {
                    component.render(context, mouseX, mouseY, delta);
                }

                offsets[column] -= componentHeight;
                maxScroll = Math.max(maxScroll, offsets[column]);
                column = (column + 1) % 2;
            }
        }
        scissorManager.pop();

        int clamped = MathHelper.clamp(maxScroll - (menuScreen.height / 2 + 35), 0, maxScroll);
        scroll = MathHelper.clamp(scroll, -clamped, 0);
        smoothedScroll = Calculate.interpolateSmooth(2, smoothedScroll, scroll);

        if (clamped > 0) {
            float scrollbarWidth = 4;
            float scrollbarX = menuScreen.x + menuScreen.width - offsetX - scrollbarWidth + 50;
            float scrollbarY = menuScreen.y + offsetY + 22;
            float scrollbarHeight = menuScreen.height - offsetY * 2 - 14;

            rectangle.render(ShapeProperties.create(context.getMatrices(), scrollbarX, scrollbarY, scrollbarWidth, scrollbarHeight)
                    .round(2F)
                    .color(
                            new Color(255, 255, 255, 10).getRGB(),
                            new Color(255, 255, 255, 14).getRGB(),
                            new Color(255, 255, 255, 14).getRGB(),
                            new Color(255, 255, 255, 10).getRGB())
                    .build());

            float contentHeight = clamped;
            float viewHeight = menuScreen.height - offsetY * 2;
            float handleHeight = Math.max(20, viewHeight * (viewHeight / (contentHeight + viewHeight)));
            float scrollRatio = (float) (-smoothedScroll) / contentHeight;
            float handleY = scrollbarY + (scrollbarHeight - handleHeight) * scrollRatio;

            rectangle.render(ShapeProperties.create(context.getMatrices(), scrollbarX, handleY, scrollbarWidth, handleHeight)
                    .round(2F)
                    .color(
                            new Color(255, 255, 255, 48).getRGB(),
                            new Color(255, 255, 255, 62).getRGB(),
                            new Color(255, 255, 255, 62).getRGB(),
                            new Color(255, 255, 255, 48).getRGB())
                    .build());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;

        float hoverX = x + 3f;
        float hoverY = y - 1f;
        float hoverW = 30f;
        float hoverH = 22f;

        if (Calculate.isHovered(mouseX, mouseY, hoverX, hoverY, hoverW, hoverH) && button == 0) {
            MenuScreen.INSTANCE.setCategory(category);
            selectAnimation.setMs(220);
            hoverAnimation.setMs(180);
            selectAnimation.setDirection(Direction.FORWARDS);
            return true;
        }

        float offsetX = 35f;
        float offsetY = 14f;
        if (Calculate.isHovered(mouseX, mouseY,
                menuScreen.x + offsetX - 75,
                menuScreen.y + offsetY,
                menuScreen.width - offsetX + 150,
                menuScreen.height - offsetY + 15)) {
            for (int i = 0; i < moduleComponents.size(); i++) {
                ModuleComponent moduleComponent = moduleComponents.get(i);
                if (shouldRenderComponent(moduleComponent) && moduleComponent.isHover(mouseX, mouseY)) {
                    return moduleComponent.mouseClicked(mouseX, mouseY, button);
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isHover(double mouseX, double mouseY) {
        boolean isHovered = Calculate.isHovered(mouseX, mouseY, x + 3f, y - 1f, 30f, 22f);
        if (isHovered) {
            return true;
        }

        moduleComponents.forEach(moduleComponent -> moduleComponent.isHover(mouseX, mouseY));
        for (ModuleComponent moduleComponent : moduleComponents) {
            if (moduleComponent.isHover(mouseX, mouseY)) {
                return true;
            }
        }
        return super.isHover(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        moduleComponents.forEach(moduleComponent -> moduleComponent.mouseReleased(mouseX, mouseY, button));
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        float offsetX = 35;
        float offsetY = 13;
        if (Calculate.isHovered(mouseX, mouseY, menuScreen.x + offsetX, menuScreen.y + offsetY, menuScreen.width - offsetX + 7, menuScreen.height - offsetY + 15)) {
            scroll += amount * 20;
        }
        moduleComponents.forEach(moduleComponent -> {
            if (shouldRenderComponent(moduleComponent)) {
                moduleComponent.mouseScrolled(mouseX, mouseY, amount);
            }
        });
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        moduleComponents.forEach(moduleComponent -> {
            if (shouldRenderComponent(moduleComponent)) {
                moduleComponent.keyPressed(keyCode, scanCode, modifiers);
            }
        });
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        moduleComponents.forEach(moduleComponent -> {
            if (shouldRenderComponent(moduleComponent)) {
                moduleComponent.charTyped(chr, modifiers);
            }
        });
        return super.charTyped(chr, modifiers);
    }

    private void drawCategoryTab(DrawContext context, MatrixStack matrix, int mouseX, int mouseY) {
        boolean selected = MenuScreen.INSTANCE.getCategory().equals(category);
        boolean hovered = Calculate.isHovered(mouseX, mouseY, x + 3f, y - 1f, 30f, 22f);

        selectAnimation.setDirection(selected ? Direction.FORWARDS : Direction.BACKWARDS);
        hoverAnimation.setDirection(!selected && hovered ? Direction.FORWARDS : Direction.BACKWARDS);

        float selectedAnim = selectAnimation.getOutput().floatValue();
        float hoverAnim = hoverAnimation.getOutput().floatValue();

        float tabX = x + 3f;
        float tabY = y - 1f;
        float tabW = 30f;
        float tabH = 22f;

        if (hoverAnim > 0.01f) {
            rectangle.render(ShapeProperties.create(matrix, tabX, tabY, tabW, tabH)
                    .round(6F)
                    .color(
                            applyAlpha(SIDEBAR_ITEM_HOVER, 0.75f * hoverAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_HOVER, 1f * hoverAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_HOVER, 1f * hoverAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_HOVER, 0.75f * hoverAnim).getRGB())
                    .build());
        }

        if (selectedAnim > 0.01f) {
            rectangle.render(ShapeProperties.create(matrix, tabX, tabY, tabW, tabH)
                    .round(6F)
                    .thickness(1f)
                    .outlineColor(new Color(255, 255, 255, (int) (16 * selectedAnim)).getRGB())
                    .color(
                            applyAlpha(SIDEBAR_ITEM_ACTIVE, 0.95f * selectedAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_ACTIVE, 1f * selectedAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_ACTIVE, 1f * selectedAnim).getRGB(),
                            applyAlpha(SIDEBAR_ITEM_ACTIVE, 0.95f * selectedAnim).getRGB())
                    .build());

            rectangle.render(ShapeProperties.create(matrix, tabX + 1.5f, tabY + 5f, 2f, tabH - 10f)
                    .round(1f)
                    .color(getAccent(255)).build());
        }

        int iconColor = selected
                ? mix(TEXT_ACTIVE, new Color(getAccent(255), true), 0.15f).getRGB()
                : (hovered ? TEXT_HOVER.getRGB() : TEXT_IDLE.getRGB());

        if (ModuleCategory.COMBAT.equals(category)) {
            Fonts.getSize(21, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "A", x + 18f, y + 7.8f, iconColor);
        }
        if (ModuleCategory.MOVEMENT.equals(category)) {
            Fonts.getSize(23, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "B", x + 17f, y + 7.1f, iconColor);
        }
        if (ModuleCategory.RENDER.equals(category)) {
            Fonts.getSize(21, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "C", x + 17f, y + 7.2f, iconColor);
        }
        if (ModuleCategory.PLAYER.equals(category)) {
            Fonts.getSize(23, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "D", x + 17f, y + 7.2f, iconColor);
        }
        if (ModuleCategory.MISC.equals(category)) {
            Fonts.getSize(21, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "E", x + 17.5f, y + 7.2f, iconColor);
        }
        if (ModuleCategory.CONFIGS.equals(category)) {
            Fonts.getSize(21, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "F", x + 17.5f, y + 7.2f, iconColor);
        }
        if (ModuleCategory.AUTOBUY.equals(category)) {
            Fonts.getSize(33, Fonts.Type.ICONSCATEGORY).drawCenteredString(context.getMatrices(), "H", x + 17.5f, y + 3.8f, iconColor);
        }
    }

    private int[] calculateOffsets() {
        int[] offsets = new int[2];
        int column = 0;
        for (int i = moduleComponents.size() - 1; i >= 0; i--) {
            ModuleComponent component = moduleComponents.get(i);
            if (shouldRenderComponent(component)) {
                int componentHeight = component.getComponentHeight() + 10;
                offsets[column] += componentHeight;
                column = (column + 1) % 2;
            }
        }
        return offsets;
    }

    private boolean shouldRenderComponent(ModuleComponent component) {
        MenuScreen menuScreen = MenuScreen.INSTANCE;
        ModuleCategory moduleCategory = component.getModule().getCategory();
        String text = menuScreen.getSearchComponent().getText().toLowerCase();
        String moduleName = component.getModule().getVisibleName().toLowerCase();
        return (text.equalsIgnoreCase("") ? moduleCategory.equals(menuScreen.getCategory()) : moduleName.contains(text));
    }

    private static int getAccent(int alpha) {
        int color = new Color(132, 112, 255, 255).getRGB();
        return (clamp255(alpha) << 24) | (color & 0x00FFFFFF);
    }

    private static int clamp255(int v) {
        if (v < 0) return 0;
        return Math.min(v, 255);
    }

    private static Color applyAlpha(Color color, float mul) {
        int a = clamp255((int) (color.getAlpha() * mul));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), a);
    }

    private static Color mix(Color a, Color b, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        int r = (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped);
        int g = (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped);
        int bl = (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped);
        int al = (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * clamped);
        return new Color(r, g, bl, al);
    }
}