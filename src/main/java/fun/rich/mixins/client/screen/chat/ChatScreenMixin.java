package fun.rich.mixins.client.screen.chat;

import fun.rich.Rich;
import fun.rich.features.impl.render.Hud;
import fun.rich.utils.client.managers.api.draggable.AbstractDraggable;
import fun.rich.utils.display.interfaces.QuickImports;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(ChatScreen.class)
public class ChatScreenMixin extends Screen implements QuickImports {

    protected ChatScreenMixin() {
        super(Text.empty());
    }

    @Unique
    private List<AbstractDraggable> getDraggablesSafe() {
        try {
            Rich rich = Rich.getInstance();
            if (rich == null) return Collections.emptyList();
            if (rich.getDraggableRepository() == null) return Collections.emptyList();
            List<AbstractDraggable> list = rich.getDraggableRepository().draggable();
            return list != null ? list : Collections.emptyList();
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Hud hud = Hud.getInstance();
        if (hud == null) return;

        List<AbstractDraggable> draggables = getDraggablesSafe();
        if (draggables.isEmpty()) return;

        AbstractDraggable active = null;
        for (AbstractDraggable draggable : draggables) {
            if (draggable == null) continue;
            if (draggable.canDraw(hud, draggable) && draggable.isDragging()) {
                active = draggable;
            }
        }

        if (active == null) return;

        for (AbstractDraggable draggable : draggables) {
            if (draggable == active) {
                draggable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("TAIL"))
    private void onMouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        for (AbstractDraggable draggable : getDraggablesSafe()) {
            if (draggable != null) {
                draggable.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        for (AbstractDraggable draggable : getDraggablesSafe()) {
            if (draggable != null) {
                draggable.mouseReleased(mouseX, mouseY, button);
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}