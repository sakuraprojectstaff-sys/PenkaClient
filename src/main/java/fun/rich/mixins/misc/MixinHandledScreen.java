package fun.rich.mixins.misc;

import fun.rich.features.impl.render.InventoryAnimation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class MixinHandledScreen<T extends ScreenHandler> extends Screen {

    @Unique
    private float rich$progress = 0.0f;

    @Unique
    private boolean rich$closing = false;

    @Unique
    private boolean rich$bypassClose = false;

    @Unique
    private boolean rich$scheduledClose = false;

    @Unique
    private boolean rich$pushed = false;

    protected MixinHandledScreen(Text title) {
        super(title);
    }

    @Unique
    private InventoryAnimation rich$getModule() {
        return InventoryAnimation.getInstance();
    }

    @Unique
    private boolean rich$isActive() {
        InventoryAnimation module = rich$getModule();
        if (module == null || !module.isState()) return false;
        return !module.isInventoryOnly() || InventoryScreen.class.isInstance(this);
    }

    @Unique
    private float rich$getSpeed() {
        InventoryAnimation module = rich$getModule();
        return module == null ? 10.0f : module.getAnimationSpeed();
    }

    @Unique
    private String rich$getMode() {
        InventoryAnimation module = rich$getModule();
        return module == null ? "Scale" : module.getAnimationMode();
    }

    @Unique
    private float rich$animate(float current, float target, float speed) {
        float factor = Math.max(0.01f, Math.min(1.0f, speed * 0.065f));
        return current + (target - current) * factor;
    }

    @Unique
    private float rich$clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    @Unique
    private float rich$easeOutCubic(float t) {
        t = rich$clamp01(t);
        return 1.0f - (float) Math.pow(1.0f - t, 3.0);
    }

    @Unique
    private float rich$easeOutBack(float t) {
        t = rich$clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3.0) + c1 * (float) Math.pow(t - 1.0f, 2.0);
    }

    @Unique
    private float rich$applyEasing(float raw) {
        if (rich$closing) return rich$easeOutCubic(raw);
        return "Bounce".equals(rich$getMode()) ? Math.max(0.001f, rich$easeOutBack(raw)) : rich$easeOutCubic(raw);
    }

    @Unique
    private void rich$applyTransform(DrawContext context, float value) {
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;
        String mode = rich$getMode();

        switch (mode) {
            case "Scale", "Bounce" -> {
                context.getMatrices().translate(cx, cy, 0.0f);
                context.getMatrices().scale(Math.max(0.001f, value), Math.max(0.001f, value), 1.0f);
                context.getMatrices().translate(-cx, -cy, 0.0f);
            }
            case "SlideUp" -> context.getMatrices().translate(0.0f, this.height * (1.0f - value), 0.0f);
            case "SlideDown" -> context.getMatrices().translate(0.0f, -this.height * (1.0f - value), 0.0f);
            case "SlideLeft" -> context.getMatrices().translate(this.width * (1.0f - value), 0.0f, 0.0f);
            case "SlideRight" -> context.getMatrices().translate(-this.width * (1.0f - value), 0.0f, 0.0f);
            case "Flip" -> {
                context.getMatrices().translate(cx, cy, 0.0f);
                context.getMatrices().scale(Math.max(0.001f, value), 1.0f, 1.0f);
                context.getMatrices().translate(-cx, -cy, 0.0f);
            }
            case "Warp" -> {
                float scale = rich$closing ? value : (1.15f - 0.15f * value);
                context.getMatrices().translate(cx, cy, 0.0f);
                context.getMatrices().scale(Math.max(0.001f, scale), Math.max(0.001f, scale), 1.0f);
                context.getMatrices().translate(-cx, -cy, 0.0f);
            }
            case "Glitch" -> {
                float shake = (1.0f - value) * 35.0f * (float) Math.sin(value * 28.0f);
                float scale = 0.88f + 0.12f * value;
                context.getMatrices().translate(shake, 0.0f, 0.0f);
                context.getMatrices().translate(cx, cy, 0.0f);
                context.getMatrices().scale(scale, scale, 1.0f);
                context.getMatrices().translate(-cx, -cy, 0.0f);
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void rich$renderHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        rich$pushed = false;

        if (rich$scheduledClose) {
            ci.cancel();
            return;
        }

        if (!rich$isActive()) {
            rich$progress = 1.0f;
            rich$closing = false;
            rich$bypassClose = false;
            return;
        }

        float speed = rich$getSpeed();

        if (rich$closing) {
            rich$progress = rich$animate(rich$progress, 0.0f, speed * 3.0f);

            if (rich$progress < 0.005f) {
                rich$scheduledClose = true;
                rich$bypassClose = true;
                if (this.client != null) {
                    this.client.execute(this::close);
                }
                ci.cancel();
                return;
            }
        } else {
            rich$progress = Math.min(1.0f, rich$animate(rich$progress, 1.0f, speed));
            if (rich$progress >= 0.999f) return;
        }

        context.getMatrices().push();
        rich$pushed = true;
        rich$applyTransform(context, rich$applyEasing(rich$progress));
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void rich$renderTail(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (rich$pushed) {
            context.getMatrices().pop();
            rich$pushed = false;
        }

        if (!rich$isActive() || rich$scheduledClose) return;
        if (rich$progress >= 0.999f && !rich$closing) return;

        float alpha = 1.0f - rich$applyEasing(rich$progress);
        int a = Math.min(255, Math.max(0, (int) (alpha * 255.0f)));
        if (a > 0) {
            context.fill(0, 0, this.width, this.height, a << 24);
        }
    }

    @Inject(method = "close", at = @At("HEAD"), cancellable = true)
    private void rich$close(CallbackInfo ci) {
        if (rich$bypassClose) {
            rich$bypassClose = false;
            rich$scheduledClose = false;
            rich$closing = false;
            rich$progress = 0.0f;
            return;
        }

        if (!rich$isActive()) return;

        if (rich$closing || rich$scheduledClose) {
            ci.cancel();
            return;
        }

        if (rich$progress > 0.05f) {
            rich$closing = true;
            ci.cancel();
        }
    }
}