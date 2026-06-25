package fun.rich.mixins.client.screen.ingame;

import fun.rich.Rich;
import fun.rich.events.render.DrawEvent;
import fun.rich.features.impl.render.CrossHair;
import fun.rich.features.impl.render.Hud;
import fun.rich.features.impl.render.ScoreBoard;
import fun.rich.utils.client.managers.event.EventManager;
import fun.rich.utils.display.geometry.Render2D;
import fun.rich.utils.display.interfaces.QuickImports;
import fun.rich.utils.display.shape.ShapeProperties;
import fun.rich.utils.math.calc.Calculate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ConcurrentModificationException;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin implements QuickImports {
    @Final
    @Shadow
    private MinecraftClient client;

    @Shadow
    protected abstract void renderStatusBars(DrawContext context);

    @Shadow
    protected abstract void renderMountHealth(DrawContext context);

    @Unique
    private boolean rich$scoreboardCustomEnabled;

    @Unique
    private boolean rich$scoreboardCaptureBounds;

    @Unique
    private boolean rich$scoreboardFrameHasBounds;

    @Unique
    private int rich$scoreboardMinX;

    @Unique
    private int rich$scoreboardMinY;

    @Unique
    private int rich$scoreboardMaxX;

    @Unique
    private int rich$scoreboardMaxY;

    @Unique
    private boolean rich$scoreboardCachedBoundsValid;

    @Unique
    private int rich$scoreboardCachedMinX;

    @Unique
    private int rich$scoreboardCachedMinY;

    @Unique
    private int rich$scoreboardCachedMaxX;

    @Unique
    private int rich$scoreboardCachedMaxY;

    @Unique
    private Hud rich$getHudSafe() {
        try {
            return Hud.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private CrossHair rich$getCrossHairSafe() {
        try {
            return CrossHair.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private ScoreBoard rich$getScoreBoardSafe() {
        try {
            return ScoreBoard.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/LayeredDrawer;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
            shift = At.Shift.AFTER))
    public void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        blur.setup();
        DrawEvent event = new DrawEvent(context, drawEngine, tickCounter.getTickDelta(false));
        EventManager.callEvent(event);
        Render2D.onRender(context);

        boolean debugHudVisible = client.getDebugHud().shouldShowDebugHud();

        if (!client.options.hudHidden && !debugHudVisible) {
            Hud hud = rich$getHudSafe();

            context.getMatrices().push();
            context.getMatrices().translate(0.0F, 0.0F, 400.0F);

            if (hud != null) {
                Rich.getInstance().getDraggableRepository().draggable().forEach(draggable -> {
                    if (draggable.canDraw(hud, draggable)) draggable.startAnimation();
                    else draggable.stopAnimation();

                    float scale = draggable.getScaleAnimation().getOutput().floatValue();
                    if (!draggable.isCloseAnimationFinished()) {
                        draggable.validPosition();
                        try {
                            Calculate.setAlpha(scale, () -> draggable.drawDraggable(context));
                        } catch (ConcurrentModificationException ignored) {
                        }
                    }
                });
            }

            context.getMatrices().pop();
        }
    }

    @Inject(method = "renderCrosshair", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/hud/InGameHud;CROSSHAIR_TEXTURE:Lnet/minecraft/util/Identifier;"), cancellable = true)
    public void renderCrosshairHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        CrossHair crossHair = rich$getCrossHairSafe();
        if (crossHair != null && crossHair.isState()) {
            crossHair.onRenderCrossHair();
            ci.cancel();
        }
    }

    @Inject(at = @At(value = "HEAD"), method = "renderStatusEffectOverlay", cancellable = true)
    public void renderStatusEffectOverlayHook(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Hud hud = rich$getHudSafe();
        if (hud != null && hud.isState() && hud.interfaceSettings.isSelected("Potions")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"))
    private void renderScoreboardSidebarHook(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        ScoreBoard scoreBoard = rich$getScoreBoardSafe();

        rich$scoreboardCustomEnabled = scoreBoard != null && scoreBoard.isState();
        rich$scoreboardCaptureBounds = false;
        rich$scoreboardFrameHasBounds = false;

        if (!rich$scoreboardCustomEnabled) return;

        rich$scoreboardCaptureBounds = true;

        if (rich$scoreboardCachedBoundsValid) {
            int x = rich$scoreboardCachedMinX - 2;
            int y = rich$scoreboardCachedMinY - 2;
            int w = (rich$scoreboardCachedMaxX - rich$scoreboardCachedMinX) + 4;
            int h = (rich$scoreboardCachedMaxY - rich$scoreboardCachedMinY) + 4;

            rich$drawScoreboardPanel(context, x, y, w, h);
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("TAIL"))
    private void renderScoreboardSidebarHookTail(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (rich$scoreboardCustomEnabled && rich$scoreboardFrameHasBounds) {
            rich$scoreboardCachedMinX = rich$scoreboardMinX;
            rich$scoreboardCachedMinY = rich$scoreboardMinY;
            rich$scoreboardCachedMaxX = rich$scoreboardMaxX;
            rich$scoreboardCachedMaxY = rich$scoreboardMaxY;
            rich$scoreboardCachedBoundsValid = true;
        }

        rich$scoreboardCaptureBounds = false;
        rich$scoreboardFrameHasBounds = false;
        rich$scoreboardCustomEnabled = false;
    }

    @Redirect(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V")
    )
    private void rich$redirectScoreboardFill(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        if (!rich$scoreboardCaptureBounds) {
            context.fill(x1, y1, x2, y2, color);
            return;
        }

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);

        if (!rich$scoreboardFrameHasBounds) {
            rich$scoreboardMinX = minX;
            rich$scoreboardMinY = minY;
            rich$scoreboardMaxX = maxX;
            rich$scoreboardMaxY = maxY;
            rich$scoreboardFrameHasBounds = true;
        } else {
            if (minX < rich$scoreboardMinX) rich$scoreboardMinX = minX;
            if (minY < rich$scoreboardMinY) rich$scoreboardMinY = minY;
            if (maxX > rich$scoreboardMaxX) rich$scoreboardMaxX = maxX;
            if (maxY > rich$scoreboardMaxY) rich$scoreboardMaxY = maxY;
        }

        if (!rich$scoreboardCachedBoundsValid) {
            context.fill(x1, y1, x2, y2, color);
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At(value = "HEAD"), cancellable = true)
    private void renderOverlayMessage(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Hud hud = rich$getHudSafe();
        if (hud != null && hud.isState() && hud.interfaceSettings.isSelected("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderExperienceLevel", at = @At(value = "HEAD"), cancellable = true)
    private void renderExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Hud hud = rich$getHudSafe();
        if (hud != null && hud.isState() && hud.interfaceSettings.isSelected("HotBar")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderMainHud", at = @At(value = "HEAD"), cancellable = true)
    private void renderMainHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Hud hud = rich$getHudSafe();
        if (hud != null && hud.isState() && hud.interfaceSettings.isSelected("HotBar")) {
            context.drawGuiTexture(RenderLayer::getGuiTextured, InGameHud.HOTBAR_ATTACK_INDICATOR_BACKGROUND_TEXTURE, 0, 0, 1, 1);
            if (client.interactionManager != null && client.interactionManager.hasStatusBars()) {
                renderStatusBars(context);
            }
            this.renderMountHealth(context);
            ci.cancel();
        }
    }

    @Unique
    private void rich$drawScoreboardPanel(DrawContext context, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;

        MatrixStack matrix = context.getMatrices();

        blur.render(ShapeProperties.create(matrix, x, y, w, h).round(6.0F).color(0x30000000).build());
        rectangle.render(ShapeProperties.create(matrix, x, y, w, h).round(6.0F).color(0x78000000).build());

        rich$drawRoundedOutline(context, x, y, w, h, 6, 0x14000000);
    }

    @Unique
    private void rich$drawRoundedOutline(DrawContext context, int x, int y, int w, int h, int r, int color) {
        if (w <= 1 || h <= 1) return;

        int rr = Math.max(1, Math.min(r, Math.min(w, h) / 2));

        context.fill(x + rr, y, x + w - rr, y + 1, color);
        context.fill(x + rr, y + h - 1, x + w - rr, y + h, color);
        context.fill(x, y + rr, x + 1, y + h - rr, color);
        context.fill(x + w - 1, y + rr, x + w, y + h - rr, color);

        for (int i = 0; i < rr; i++) {
            context.fill(x + i, y + rr - i - 1, x + i + 1, y + rr - i, color);
            context.fill(x + w - i - 1, y + rr - i - 1, x + w - i, y + rr - i, color);
            context.fill(x + i, y + h - rr + i, x + i + 1, y + h - rr + i + 1, color);
            context.fill(x + w - i - 1, y + h - rr + i, x + w - i, y + h - rr + i + 1, color);
        }
    }
}