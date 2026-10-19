package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
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
import org.joml.Quaternionf;

public class ShadowRenderer extends EntityRenderer<ShadowEntity> {
    private static final ResourceLocation WHITE = new ResourceLocation(TheShadow.MODID, "textures/entity/white.png");
    /** blender metres -> blocks. the model is ~2.6 m tall, so this makes it ~8 blocks */
    public static final float SCALE = ShadowEntity.MODEL_SCALE;

    public ShadowRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowEntity entity) {
        return WHITE;
    }

    @Override
    public void render(ShadowEntity e, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ShadowMesh mesh = ShadowMesh.get();
        if (mesh == null) return;
        float age = e.tickCount + partialTick;
        ShadowPose.apply(mesh, e, age, partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        float grow = 1F + Math.min(1F, e.getStare() / (float) ShadowEntity.STARE_LIMIT) * 0.12F;
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-bodyYaw));
        pose.scale(SCALE * grow, SCALE * grow, SCALE * grow);
        VertexConsumer solid = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        for (ShadowMesh.Part root : mesh.roots) draw(root, pose, solid, light, false);
        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(WHITE));
        for (ShadowMesh.Part root : mesh.roots) draw(root, pose, glow, 0xF000F0, true);
        pose.popPose();
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    private static void draw(ShadowMesh.Part p, PoseStack pose, VertexConsumer vc, int light, boolean glowPass) {
        pose.pushPose();
        if (p.xRot != 0F || p.yRot != 0F || p.zRot != 0F) {
            pose.translate(p.px, p.py, p.pz);
            pose.mulPose(new Quaternionf().rotationZYX(p.zRot, p.yRot, p.xRot));
            pose.translate(-p.px, -p.py, -p.pz);
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int tris = p.triangles();
        for (int t = 0; t < tris; t++) {
            if (p.glow[t] != glowPass) continue;
            for (int k = 0; k < 4; k++) {
                int v = t * 3 + Math.min(k, 2);      // triangle sent as a quad with the last corner repeated
                int c = p.rgb[v];
                vc.vertex(m, p.pos[v * 3], p.pos[v * 3 + 1], p.pos[v * 3 + 2])
                        .color((c >> 16) & 255, (c >> 8) & 255, c & 255, 255)
                        .uv(0.5F, 0.5F)
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(light)
                        .normal(n, p.nrm[v * 3], p.nrm[v * 3 + 1], p.nrm[v * 3 + 2])
                        .endVertex();
            }
        }
        for (ShadowMesh.Part c : p.children) draw(c, pose, vc, light, glowPass);
        pose.popPose();
    }
}
