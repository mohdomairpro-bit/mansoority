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
    private static final java.util.Map<String, net.minecraft.resources.ResourceLocation> HD = java.util.Map.of(
            "shadow_blade", new net.minecraft.resources.ResourceLocation(com.mansoority.shadow.TheShadow.MODID, "textures/entity/shadow_blade_hd.png"),
            "pale_ward", new net.minecraft.resources.ResourceLocation(com.mansoority.shadow.TheShadow.MODID, "textures/entity/pale_ward_hd.png"));

    public static void draw(String mesh, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        RigidMesh m = RigidMesh.load(mesh);
        if (m == null) return;
        m.reset(); m.pose();
        var tex = HD.getOrDefault(mesh, RigidMesh.PALETTE);          // the blade and the shield have their own baked 2048px textures
        m.draw(pose, buf, RenderType.entityCutoutNoCull(tex), RenderType.eyes(tex), light, overlay, 1F, 1F, 1F, 1F);
    }

    /**
     * the blade's aura: layered shells around it that flicker and lick upward like flames - white at the core,
     * violet, then a deep purple haze. additive, so it glows in the dark. strength 0..1
     */
    public static void aura(PoseStack pose, MultiBufferSource buf, float strength) {
        RigidMesh m = RigidMesh.load("shadow_blade");
        if (m == null || strength <= 0F) return;
        m.reset(); m.pose();
        float t = (net.minecraft.Util.getMillis() % 100000L) / 1000F;
        VertexConsumer vc = buf.getBuffer(ShadowRenderer.Types.GLOW);
        float[][] layers = {{0.010F, 0.55F, 0.48F, 0.70F}, {0.026F, 0.36F, 0.16F, 0.62F}, {0.048F, 0.17F, 0.05F, 0.36F}, {0.075F, 0.07F, 0.02F, 0.16F}};
        for (float[] L : layers)
            for (RigidMesh.Part p : m.parts) m.drawShell(pose, p, vc, L[0], t, L[1] * strength, L[2] * strength, L[3] * strength);
    }

    /** the blade, its middle at the origin, lying along x (for clamping it crossways in the teeth) */
    public static void bladeCrossways(PoseStack pose, MultiBufferSource buf, int light, float size) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(-90F));
        pose.scale(size, size, size);
        pose.translate(0F, -0.75F, 0F);
        draw("shadow_blade", pose, buf, light, OverlayTexture.NO_OVERLAY);
        aura(pose, buf, 0.8F);
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
