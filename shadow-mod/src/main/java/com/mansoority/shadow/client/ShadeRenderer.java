package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadeEntity;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/** small, thin, pitch-black, with eyes that glow in the dark */
public class ShadeRenderer extends HumanoidMobRenderer<ShadeEntity, HumanoidModel<ShadeEntity>> {
    private static final ResourceLocation TEX = new ResourceLocation(TheShadow.MODID, "textures/entity/shade.png");
    private static final RenderType EYES = RenderType.eyes(new ResourceLocation(TheShadow.MODID, "textures/entity/shade_eyes.png"));

    public ShadeRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.35F);
        this.addLayer(new EyesLayer<>(this) {
            @Override public RenderType renderType() { return EYES; }
        });
    }

    @Override
    protected void scale(ShadeEntity e, PoseStack pose, float pt) { pose.scale(0.82F, 0.86F, 0.82F); }

    @Override
    public ResourceLocation getTextureLocation(ShadeEntity e) { return TEX; }
}
