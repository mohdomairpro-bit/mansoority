package com.mansoority.shadow.client;

import com.mansoority.shadow.MansoorityBoss;
import com.mansoority.shadow.ShadowRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** the boss's extras: the glowing weak point, the blade (in its teeth / through its stomach), its health over its head */
final class BossLook {
    private static ItemStack blade;

    /** drawn inside the creature's model space (already scaled), after the body */
    static void inModel(MansoorityBoss b, ShadowMesh m, PoseStack pose, MultiBufferSource buf, int light, float age) {
        // the open face glows when it's open to a hit (double damage + it stops cold)
        if (b.has(MansoorityBoss.F_JAW)) {
            int[] jaw = m.trianglesOf("jaw", "jawR");
            if (jaw.length > 0) {
                int r0 = ShadowRenderer.glowR, g0 = ShadowRenderer.glowG, b0 = ShadowRenderer.glowB;
                float pulse = 0.55F + 0.45F * Mth.sin(age * 0.5F);
                ShadowRenderer.glowR = (int) (255 * pulse); ShadowRenderer.glowG = (int) (60 * pulse); ShadowRenderer.glowB = (int) (40 * pulse);
                ShadowRenderer.emit(m, jaw, pose, buf.getBuffer(ShadowRenderer.Types.GLOW), 0xF000F0, true);
                ShadowRenderer.glowR = r0; ShadowRenderer.glowG = g0; ShadowRenderer.glowB = b0;
            }
        }
        if (blade == null) blade = new ItemStack(ShadowRegistry.SHADOW_BLADE.get());
        var mc = Minecraft.getInstance();
        boolean black = b.has(MansoorityBoss.F_BLACK);
        if (b.has(MansoorityBoss.F_BLADE) && !black) {                // phase two: clamped crossways in its teeth
            ShadowMesh.Part head = m.get("head");
            if (head == null) return;
            pose.pushPose();
            pose.mulPoseMatrix(ShadowSkeleton.matrices(m)[head.index]);
            pose.translate(0F, 1.83F, 0.63F);
            pose.mulPose(Axis.YP.rotationDegrees(90F));
            pose.mulPose(Axis.ZP.rotationDegrees(-45F + 8F * Mth.sin(age * 0.2F)));
            pose.scale(0.75F, 0.75F, 0.75F);
            mc.getItemRenderer().renderStatic(blade, ItemDisplayContext.FIXED, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buf, mc.level, 0);
            pose.popPose();
        }
        if (black && !b.has(MansoorityBoss.F_BLADE_OUT)) {           // phase three: right through its own stomach
            ShadowMesh.Part chest = m.get("chest");
            if (chest == null) return;
            pose.pushPose();
            pose.mulPoseMatrix(ShadowSkeleton.matrices(m)[chest.index]);
            pose.translate(0F, 1.58F, 0.05F);
            pose.mulPose(Axis.XP.rotationDegrees(-80F));
            pose.mulPose(Axis.ZP.rotationDegrees(45F));
            pose.scale(0.9F, 0.9F, 0.9F);
            mc.getItemRenderer().renderStatic(blade, ItemDisplayContext.FIXED, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buf, mc.level, 0);
            pose.popPose();
        }
    }

    /** its health, floating over it */
    static void healthBar(MansoorityBoss b, PoseStack pose, MultiBufferSource buf, Quaternionf camRot, Font font, double dist2) {
        if (dist2 > 80 * 80 || b.has(MansoorityBoss.F_INTRO) || b.has(MansoorityBoss.F_FAKEDEAD) || b.getVanish() > 0) return;
        float hp = Mth.clamp(b.getHealth() / MansoorityBoss.MAX_HP, 0F, 1F);
        pose.pushPose();
        pose.translate(0F, b.getBbHeight() + 1.6F, 0F);
        pose.mulPose(camRot);
        float s = 0.05F;
        pose.scale(-s, -s, s);
        Matrix4f m = pose.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackgroundSeeThrough());
        float w = 60F, h = 4F;
        quad(vc, m, -w - 1, -1, w + 1, h + 1, 0xC0000000);
        quad(vc, m, -w, 0, w, h, 0xFF2A0A0A);
        int col = b.getPhase() == 3 ? 0xFFD0201A : b.getPhase() == 2 ? 0xFF9A3CC8 : 0xFFE8E2D6;
        quad(vc, m, -w, 0, -w + 2 * w * hp, h, col);
        String name = b.getPhase() == 3 ? "M A N S O O R I T Y" : "Mansoority";
        Component c = Component.literal(name);
        float tx = -font.width(c) / 2F;
        font.drawInBatch(c, tx, -11F, b.getPhase() == 3 ? 0xFFFF4A3A : 0xFFE8E2D6, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x60000000, 0xF000F0);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int argb) {
        int a = argb >>> 24, r = argb >> 16 & 255, g = argb >> 8 & 255, bl = argb & 255;
        vc.vertex(m, x0, y0, 0.01F).color(r, g, bl, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x0, y1, 0.01F).color(r, g, bl, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x1, y1, 0.01F).color(r, g, bl, a).uv2(0xF000F0).endVertex();
        vc.vertex(m, x1, y0, 0.01F).color(r, g, bl, a).uv2(0xF000F0).endVertex();
    }

    private BossLook() {}
}
