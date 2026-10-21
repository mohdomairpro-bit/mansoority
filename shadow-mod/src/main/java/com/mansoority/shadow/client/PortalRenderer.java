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
    private static final ResourceLocation SMOKE = new ResourceLocation(TheShadow.MODID, "textures/entity/portal_smoke.png");
    private static final ResourceLocation HAND = new ResourceLocation(TheShadow.MODID, "textures/entity/portal_hand.png");
    private static final int FRAMES = 24, GX = 4, GY = 6;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType smoke = RenderType.entityTranslucent(SMOKE), hands = RenderType.entityTranslucent(HAND);
        boolean any = false;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof ShadowEntity s) || s.getVanish() <= 0) continue;
            any = true;
            float v = s.getVanish() + pt;
            // a hole of ink-black smoke bleeds open, hands reach out of it, it swallows him, then it bleeds shut
            float open = v < 8F ? v / 8F : v > ShadowEntity.VANISH_LEN - 7 ? Math.max(0F, (ShadowEntity.VANISH_LEN - v) / 7F) : 1F;
            open = open * open * (3F - 2F * open);
            if (open <= 0.01F) continue;
            Vec3 c = s.getPosition(pt).add(0, s.getBbHeight() * 0.5, 0);
            float rad = Math.max(1.4F, s.getBbHeight() * 0.55F) * (0.35F + 0.65F * open);
            int frame = (int) (v * 0.9F) % FRAMES;
            float u0 = (frame % GX) / (float) GX, v0 = (frame / GX) / (float) GY, du = 1F / GX, dv = 1F / GY;
            int alpha = (int) (255 * Math.min(1F, open * 1.4F));
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            float yaw = (float) Math.toDegrees(Math.atan2(cam.x - c.x, cam.z - c.z));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            VertexConsumer sm = buf.getBuffer(smoke);
            for (int layer = 0; layer < 3; layer++) {                 // layered smoke, each turning at its own pace
                ps.pushPose();
                ps.translate(0, 0, 0.03F * layer);
                ps.mulPose(Axis.ZP.rotationDegrees((layer == 1 ? -1 : 1) * v * (2.5F + layer * 2F) + layer * 70F));
                float r2 = rad * (1.25F - layer * 0.2F);
                int fr = (frame + layer * 8) % FRAMES;
                float a0 = (fr % GX) / (float) GX, b0 = (fr / GX) / (float) GY;
                quad(sm, ps.last().pose(), ps.last().normal(), r2 * 0.8F, r2, a0, b0, a0 + du, b0 + dv, layer == 0 ? alpha / 2 : alpha, 255, 255, 255);
                ps.popPose();
            }
            // pale hands creeping out over the edge of the hole, fingers flexing
            float reach = Mth.clamp((v - 5F) / 10F, 0F, 1F) * open;
            if (reach > 0.02F) {
                VertexConsumer hv = buf.getBuffer(hands);
                float[] angs = {20F, 140F, 255F};
                for (int k = 0; k < 3; k++) {
                    float kr = Mth.clamp(reach * 1.3F - k * 0.15F, 0F, 1F);     // one after another
                    if (kr <= 0F) continue;
                    ps.pushPose();
                    ps.translate(0, 0, 0.12F + k * 0.01F);
                    ps.mulPose(Axis.ZP.rotationDegrees(angs[k] + Mth.sin(v * 0.13F + k) * 6F));
                    float hs = rad * 0.6F;
                    float d = rad * (0.3F + 0.32F * kr) + hs * 0.2F + Mth.sin(v * 0.6F + k * 2F) * rad * 0.02F;
                    ps.translate(d, 0, 0);
                    int ha = (int) (230 * kr);
                    quad(hv, ps.last().pose(), ps.last().normal(), hs * 0.5F, hs * 0.5F, 0F, 0F, 1F, 1F, ha, 160, 156, 150);
                    ps.popPose();
                }
            }
            ps.popPose();
        }
        if (any) { buf.endBatch(smoke); buf.endBatch(hands); }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float hw, float hh, float u0, float v0, float u1, float v1, int a, int r, int g, int b) {
        put(vc, m, n, -hw, -hh, u0, v1, a, r, g, b); put(vc, m, n, hw, -hh, u1, v1, a, r, g, b);
        put(vc, m, n, hw, hh, u1, v0, a, r, g, b);  put(vc, m, n, -hw, hh, u0, v0, a, r, g, b);
        put(vc, m, n, -hw, hh, u0, v0, a, r, g, b); put(vc, m, n, hw, hh, u1, v0, a, r, g, b);
        put(vc, m, n, hw, -hh, u1, v1, a, r, g, b); put(vc, m, n, -hw, -hh, u0, v1, a, r, g, b);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v, int a, int r, int g, int b) {
        vc.vertex(m, x, y, 0).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0F, 0F, 1F).endVertex();
    }

    private PortalRenderer() {}
}
