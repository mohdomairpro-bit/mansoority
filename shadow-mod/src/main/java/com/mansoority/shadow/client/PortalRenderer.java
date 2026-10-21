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
    private static final ResourceLocation DARK = new ResourceLocation(TheShadow.MODID, "textures/entity/portal_dark.png");
    private static final ResourceLocation GLOW = new ResourceLocation(TheShadow.MODID, "textures/entity/portal_glow.png");
    private static final int FRAMES = 24, GX = 4, GY = 6;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = event.getPartialTick();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType dark = RenderType.entityTranslucent(DARK), glow = RenderType.eyes(GLOW);
        boolean any = false;
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof ShadowEntity s) || s.getVanish() <= 0) continue;
            any = true;
            float v = s.getVanish() + pt;
            // tears open fast, holds while it's pulled in, then collapses to a point with a flash
            float open = v < 6F ? v / 6F : v > ShadowEntity.VANISH_LEN - 6 ? Math.max(0F, (ShadowEntity.VANISH_LEN - v) / 6F) : 1F;
            open = 1F - (1F - open) * (1F - open) * (1F - open);
            if (open <= 0.01F) continue;
            Vec3 c = s.getPosition(pt).add(0, s.getBbHeight() * 0.5, 0);
            float rad = Math.max(1.3F, s.getBbHeight() * 0.5F) * open;
            float flicker = 0.8F + 0.2F * Mth.sin(v * 3.1F) + (s.getRandom().nextFloat() < 0.08F ? 0.35F : 0F);
            int frame = (int) (v * 1.4F) % FRAMES;
            float u0 = (frame % GX) / (float) GX, v0 = (frame / GX) / (float) GY, du = 1F / GX, dv = 1F / GY;
            PoseStack ps = event.getPoseStack();
            ps.pushPose();
            ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
            float yaw = (float) Math.toDegrees(Math.atan2(cam.x - c.x, cam.z - c.z));
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
            // 1) the hole itself: pure black, ragged edge, slowly turning
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(v * 6F));
            quad(buf.getBuffer(dark), ps.last().pose(), ps.last().normal(), rad * 0.78F, rad, 0F, 0F, 1F, 1F, 255, 0, 0, 0);
            ps.popPose();
            // 2) glowing white filaments spiralling in, two layers turning against each other
            for (int layer = 0; layer < 2; layer++) {
                ps.pushPose();
                ps.translate(0, 0, 0.02F + layer * 0.02F);
                ps.mulPose(Axis.ZP.rotationDegrees((layer == 0 ? 1 : -1) * v * (layer == 0 ? 9F : 16F)));
                float r2 = rad * (layer == 0 ? 1F : 1.12F);
                int lum = (int) (255 * flicker * (layer == 0 ? 1F : 0.45F));
                quad(buf.getBuffer(glow), ps.last().pose(), ps.last().normal(), r2 * 0.78F, r2, u0, v0, u0 + du, v0 + dv, 255, lum, lum, lum);
                ps.popPose();
            }
            ps.popPose();
        }
        if (any) { buf.endBatch(dark); buf.endBatch(glow); }
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
