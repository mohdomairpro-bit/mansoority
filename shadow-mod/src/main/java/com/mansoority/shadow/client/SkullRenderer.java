package com.mansoority.shadow.client;

import com.mansoority.shadow.SkullBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** its skull, drawn from its own model: the head and the two halves of the face, hanging a little open */
public class SkullRenderer implements BlockEntityRenderer<SkullBlockEntity> {
    public SkullRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(SkullBlockEntity be, float pt, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        ShadowMesh m = ShadowMesh.get();
        if (m == null) return;
        int[] tris = m.trianglesOf("head", "jaw", "jawR", "eyeL", "eyeR");
        if (tris.length == 0) return;
        float yaw = be.getBlockState().hasProperty(HorizontalDirectionalBlock.FACING) ? -be.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot() : 0F;
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw + 180F));
        pose.mulPose(Axis.ZP.rotationDegrees(14F));                    // lying a little on its side
        float s = 2.6F;
        pose.scale(s, s, s);
        pose.translate(0F, -1.70F, -0.56F);                              // the chin rests on the ground
        VertexConsumer vc = buf.getBuffer(ShadowRenderer.Types.SKIN);
        Matrix4f mat = pose.last().pose(); Matrix3f nm = pose.last().normal();
        float[] P = m.pos, N = m.nrm, UV = m.uv;
        for (int v : tris) {
            vc.vertex(mat, P[v * 3], P[v * 3 + 1], P[v * 3 + 2]).color(235, 230, 222, 255).uv(UV[v * 2], UV[v * 2 + 1])
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nm, N[v * 3], N[v * 3 + 1], N[v * 3 + 2]).endVertex();
        }
        pose.popPose();
    }
}
