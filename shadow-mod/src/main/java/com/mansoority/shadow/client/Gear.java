package com.mansoority.shadow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** drawing the blade and the shield models anywhere (in hand, in its teeth, through its stomach) */
public final class Gear {
    public static void draw(String mesh, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        RigidMesh m = RigidMesh.load(mesh);
        if (m == null) return;
        m.reset(); m.pose();
        m.draw(pose, buf, RenderType.entityCutoutNoCull(RigidMesh.PALETTE), RenderType.eyes(RigidMesh.PALETTE), light, overlay, 1F, 1F, 1F, 1F);
    }

    /** the blade, its middle at the origin, lying along x (for clamping it crossways in the teeth) */
    public static void bladeCrossways(PoseStack pose, MultiBufferSource buf, int light, float size) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-90F));
        pose.scale(size, size, size);
        pose.translate(0F, -0.75F, 0F);
        draw("shadow_blade", pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    /** a rift: a black disc with a ragged violet rim, opening (k 0..1) */
    public static void rift(PoseStack pose, MultiBufferSource buf, float k, float age) {
        if (k <= 0F) return;
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(RigidMesh.PALETTE));
        int n = 32;
        float r = 0.32F * k;
        for (int i = 0; i < n; i++) {
            float a0 = i * Mth.TWO_PI / n, a1 = (i + 1) * Mth.TWO_PI / n;
            float j0 = 1F + 0.18F * Mth.sin(a0 * 5 + age * 0.4F), j1 = 1F + 0.18F * Mth.sin(a1 * 5 + age * 0.4F);
            // the inside: black (void strip of the palette)
            tri(vc, m, 0, 0, r * j0 * 0.86F * Mth.cos(a0), r * j0 * 0.86F * Mth.sin(a0), r * j1 * 0.86F * Mth.cos(a1), r * j1 * 0.86F * Mth.sin(a1), 12, 0.95F);
            // the rim: violet glow
            quad(vc, m, r * j0 * 0.86F * Mth.cos(a0), r * j0 * 0.86F * Mth.sin(a0), r * j1 * 0.86F * Mth.cos(a1), r * j1 * 0.86F * Mth.sin(a1),
                    r * j1 * 1.05F * Mth.cos(a1), r * j1 * 1.05F * Mth.sin(a1), r * j0 * 1.05F * Mth.cos(a0), r * j0 * 1.05F * Mth.sin(a0), 11, 0.35F);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, int mat, float shade) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv((mat * 16 + 8) / 256F, shade).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(0, 0, 1).endVertex();
    }
    private static void tri(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, int mat, float sh) {
        v(vc, m, x0, y0, mat, sh); v(vc, m, x1, y1, mat, sh); v(vc, m, x2, y2, mat, sh); v(vc, m, x2, y2, mat, sh);
    }
    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int mat, float sh) {
        v(vc, m, x0, y0, mat, sh); v(vc, m, x1, y1, mat, sh); v(vc, m, x2, y2, mat, sh); v(vc, m, x3, y3, mat, sh);
    }

    private Gear() {}
}
