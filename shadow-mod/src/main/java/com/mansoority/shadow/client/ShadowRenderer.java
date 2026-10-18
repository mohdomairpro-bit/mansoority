package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public class ShadowRenderer extends MobRenderer<ShadowEntity, ShadowModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(TheShadow.MODID, "textures/entity/shadow.png");
    private static final RenderType GLOW = RenderType.eyes(new ResourceLocation(TheShadow.MODID, "textures/entity/shadow_glow.png"));

    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ShadowModel(ctx.bakeLayer(ShadowModel.LAYER)), 0.0F);
        this.addLayer(new Glow(this));
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return TEXTURE;
    }

    @Override
    protected void scale(ShadowEntity entity, PoseStack pose, float partialTick) {
        float grow = Math.min(1F, entity.getStare() / (float) ShadowEntity.STARE_LIMIT);
        float s = 1.8F + grow * 0.45F;
        pose.scale(s, s, s);
    }

    /** glowing eyes + teeth: visible in pitch black */
    static class Glow extends EyesLayer<ShadowEntity, ShadowModel> {
        Glow(RenderLayerParent<ShadowEntity, ShadowModel> parent) { super(parent); }
        @Override public RenderType renderType() { return GLOW; }
    }
}
