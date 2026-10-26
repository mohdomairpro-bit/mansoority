package com.mansoority.shadow.client;

import com.mansoority.shadow.BoneSpike;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** a jagged spike of bone: shoots up out of the ground in two ticks, shudders, sinks */
public class SpikeRenderer extends EntityRenderer<BoneSpike> {
    private static final ResourceLocation TEX = new ResourceLocation(TheShadow.MODID, "textures/entity/bone_spike.png");
    public SpikeRenderer(EntityRendererProvider.Context c) { super(c); this.shadowRadius = 0.6F; }
    @Override public ResourceLocation getTextureLocation(BoneSpike e) { return TEX; }

    @Override
    public void render(BoneSpike e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        float rise = t < 3 ? t / 3F : t > BoneSpike.LIFE - 12 ? Math.max(0F, (BoneSpike.LIFE - t) / 12F) : 1F;
        float h = 3.4F * rise * (0.8F + (e.getId() % 5) * 0.08F);
        if (h <= 0.02F) return;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(e.getId()) * 14F + (t < 8 ? Mth.sin(t * 3F) * 3F : 0F)));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = pose.last().pose(); Matrix3f n = pose.last().normal();
        int sides = 6; float r0 = 0.55F;
        for (int i = 0; i < sides; i++) {                       // a six-sided cone, slightly twisted
            double a0 = i * Math.PI * 2 / sides, a1 = (i + 1) * Math.PI * 2 / sides;
            float x0 = (float) Math.cos(a0) * r0, z0 = (float) Math.sin(a0) * r0, x1 = (float) Math.cos(a1) * r0, z1 = (float) Math.sin(a1) * r0;
            float nx = (float) Math.cos((a0 + a1) / 2), nz = (float) Math.sin((a0 + a1) / 2);
            put(vc, m, n, x0, -0.3F, z0, 0, 1, nx, nz, light); put(vc, m, n, x1, -0.3F, z1, 1, 1, nx, nz, light);
            put(vc, m, n, 0.05F, h, 0.02F, 0.5F, 0, nx, nz, light); put(vc, m, n, 0.05F, h, 0.02F, 0.5F, 0, nx, nz, light);
        }
        pose.popPose();
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float nx, float nz, int light) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, 0.3F, nz).endVertex();
    }
}
