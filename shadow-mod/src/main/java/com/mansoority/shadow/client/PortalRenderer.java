package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** when it leaves, a black-and-white spiral tears open, it's pulled in twisting, and the hole snaps shut */
public final class PortalRenderer {
    private static final ResourceLocation TEX = new ResourceLocation(TheShadow.MODID, "textures/entity/portal.png");

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = null;
        RenderType rt = RenderType.entityTranslucent(TEX);
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof ShadowEntity s) || s.getVanish() <= 0) continue;
            float v = s.getVanish() + pt;
            float open = v < 8F ? v / 8F : v > ShadowEntity.VANISH_LEN - 8 ? Math.max(0F, (ShadowEntity.VANISH_LEN - v) / 8F) : 1F;
            open = open * open * (3F - 2F * open);
            if (open <= 0.01F) continue;
            if (buf == null) buf = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buf.getBuffer(rt);
            Vec3 c = s.getPosition(pt).add(0, s.getBbHeight() * 0.5, 0);
            float rad = Math.max(1.1F, s.getBbHeight() * 0.48F) * open;
            float wob = 1F + Mth.sin(v * 1.7F) * 0.04F;
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            float yaw = (float) Math.toDegrees(Math.atan2(cam.x - c.x, cam.z - c.z));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            for (int layer = 0; layer < 3; layer++) {
                ps.pushPose();
                ps.translate(0, 0, -0.05F * layer);
                ps.mulPose(Axis.ZP.rotationDegrees((layer % 2 == 0 ? 1 : -1) * v * (14F + layer * 9F)));
                float r = rad * (1F - layer * 0.18F) * wob;
                quad(vc, ps.last().pose(), ps.last().normal(), r * 0.72F, r, layer == 0 ? 245 : 170);
                ps.popPose();
            }
            ps.popPose();
        }
        if (buf != null) buf.endBatch(rt);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float hw, float hh, int a) {
        put(vc, m, n, -hw, -hh, 0, 1, a); put(vc, m, n, hw, -hh, 1, 1, a);
        put(vc, m, n, hw, hh, 1, 0, a);  put(vc, m, n, -hw, hh, 0, 0, a);
        put(vc, m, n, -hw, hh, 0, 0, a); put(vc, m, n, hw, hh, 1, 0, a);
        put(vc, m, n, hw, -hh, 1, 1, a); put(vc, m, n, -hw, -hh, 0, 1, a);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int a) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0F, 0F, 1F).endVertex();
    }

    private PortalRenderer() {}
}
