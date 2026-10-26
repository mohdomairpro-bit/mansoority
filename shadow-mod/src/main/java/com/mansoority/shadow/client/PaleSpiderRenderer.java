package com.mansoority.shadow.client;

import com.mansoority.shadow.PaleSpider;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;

/** the vanilla spider body, bone-white, bigger (biter) or bloated and smaller (spitter) */
public class PaleSpiderRenderer extends SpiderRenderer<PaleSpider> {
    private static final ResourceLocation BITER = new ResourceLocation(TheShadow.MODID, "textures/entity/pale_biter.png");
    private static final ResourceLocation SPITTER = new ResourceLocation(TheShadow.MODID, "textures/entity/pale_spitter.png");

    public PaleSpiderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.9F;
    }

    @Override
    public ResourceLocation getTextureLocation(PaleSpider e) { return e.spitter ? SPITTER : BITER; }

    @Override
    protected void scale(PaleSpider e, PoseStack pose, float pt) {
        float s = e.spitter ? 0.85F : 1.08F;
        pose.scale(s, s, s);
    }
}
