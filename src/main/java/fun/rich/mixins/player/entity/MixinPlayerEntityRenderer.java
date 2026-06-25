package fun.rich.mixins.player.entity;

import fun.rich.features.impl.render.cape.CustomCapeFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

@Mixin(PlayerEntityRenderer.class)
public abstract class MixinPlayerEntityRenderer {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingEntityRendererAccessor) (Object) this).rich$addFeature(
                new CustomCapeFeatureRenderer(
                        (FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) this,
                        ctx.getEntityModels(),
                        ctx.getEquipmentModelLoader()
                )
        );
    }
}