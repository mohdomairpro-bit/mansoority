package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import com.mansoority.shadow.ThrownBlade;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** the blade it threw: spinning flat through the air, then buried point-first in the ground */
public class ThrownBladeRenderer extends EntityRenderer<ThrownBlade> {
    private ItemStack stack;
    public ThrownBladeRenderer(EntityRendererProvider.Context c) { super(c); }
    @Override public ResourceLocation getTextureLocation(ThrownBlade e) { return TextureAtlas.LOCATION_BLOCKS; }

    @Override
    public void render(ThrownBlade e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        if (stack == null) stack = new ItemStack(ShadowRegistry.SHADOW_BLADE.get());
        pose.pushPose();
        pose.scale(3F, 3F, 3F);
        if (e.stuck()) {
            pose.translate(0, 0.3F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(e.getId() * 37F));
            pose.mulPose(Axis.ZP.rotationDegrees(135F));
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(90F));
            pose.mulPose(Axis.ZP.rotationDegrees((e.tickCount + pt) * 40F));
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, buf, e.level(), e.getId());
        pose.popPose();
    }
}
