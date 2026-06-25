package fun.rich.mixins.misc;

import fun.rich.common.proxy.Config;
import fun.rich.common.proxy.SafeModeIndicator;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenSafeModeOverlayMixin {
    @Shadow protected int width;
    @Shadow protected int height;
    @Shadow public TextRenderer textRenderer;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("TAIL"))
    private void rich$renderSafeModeOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!SafeModeIndicator.shouldShow()) {
            return;
        }

        String title = Config.safeModeEnabled ? "SAFE MODE: ON" : "SAFE MODE: ACTIVE";
        String addr = SafeModeIndicator.DISPLAY_ADDR;

        MatrixStack ms = context.getMatrices();

        ms.push();
        float s1 = 2.0f;
        ms.scale(s1, s1, 1.0f);
        int x1 = (int) ((this.width / 2.0f) / s1);
        int y1 = (int) (10.0f / s1);
        context.drawCenteredTextWithShadow(this.textRenderer, title, x1, y1, 0xFFFF0000);
        ms.pop();

        ms.push();
        float s2 = 1.5f;
        ms.scale(s2, s2, 1.0f);
        int x2 = (int) ((this.width / 2.0f) / s2);
        int y2 = (int) (36.0f / s2);
        context.drawCenteredTextWithShadow(this.textRenderer, addr, x2, y2, 0xFFFF0000);
        ms.pop();
    }
}