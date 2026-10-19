package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The black ring: a wall of black fog around the victim. From outside you can't see in;
 * from inside, thick black fog swallows everything past the wall.
 */
public final class RingRenderer {
    private static final ResourceLocation FOG = new ResourceLocation(TheShadow.MODID, "textures/entity/fogwall.png");
    private static final int SEGMENTS = 72;
    private static boolean inside;

    /** centre of the active ring, or null */
    private static Vec3 ringCentre(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ShadowEntity s && s.ringActive()) {
                Entity t = mc.level.getEntity(s.getTargetEntityId());
                if (t != null) return t.getPosition(pt);
            }
        }
        return null;
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Vec3 c = ringCentre(event.getPartialTick());
        Minecraft mc = Minecraft.getInstance();
        inside = c != null && mc.player != null
                && mc.player.position().subtract(c).multiply(1, 0, 1).lengthSqr() < ShadowEntity.ZONE_RADIUS * ShadowEntity.ZONE_RADIUS;
        if (c == null) return;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityTranslucent(FOG);
        VertexConsumer vc = buf.getBuffer(rt);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float r = (float) ShadowEntity.ZONE_RADIUS;
        float t = (mc.level.getGameTime() + event.getPartialTick()) * 0.0015F;
        float y0 = -12F, y1 = 48F;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / SEGMENTS, a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r;
            float x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float u0 = i / 8F + t, u1 = (i + 1) / 8F + t;
            for (int layer = 0; layer < 3; layer++) {        // three offset layers = no gaps, slow churning
                float off = layer * 0.37F, k = 1F + layer * 0.004F;
                quad(vc, m, n, x0 * k, z0 * k, x1 * k, z1 * k, y0, y1, u0 + off, u1 + off, t * (1 + layer) + off, 0.97F);
            }
        }
        buf.endBatch(rt);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x0, float z0, float x1, float z1,
                             float y0, float y1, float u0, float u1, float v, float alpha) {
        int a = (int) (alpha * 255);
        float vt = v + (y1 - y0) / 16F;
        // front and back so it's solid from both sides
        put(vc, m, n, x0, y0, z0, u0, vt, a); put(vc, m, n, x1, y0, z1, u1, vt, a);
        put(vc, m, n, x1, y1, z1, u1, v, a);  put(vc, m, n, x0, y1, z0, u0, v, a);
        put(vc, m, n, x0, y1, z0, u0, v, a);  put(vc, m, n, x1, y1, z1, u1, v, a);
        put(vc, m, n, x1, y0, z1, u1, vt, a); put(vc, m, n, x0, y0, z0, u0, vt, a);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, int a) {
        vc.vertex(m, x, y, z).color(8, 6, 7, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(n, 0F, 1F, 0F).endVertex();
    }

    /** inside the ring: black fog closes in, you can't see out */
    public static void onFog(ViewportEvent.RenderFog event) {
        if (!inside) return;
        event.setNearPlaneDistance(0F);
        event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), (float) ShadowEntity.ZONE_RADIUS + 3F));
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!inside) return;
        event.setRed(0.01F); event.setGreen(0.008F); event.setBlue(0.01F);
    }

    private RingRenderer() {}
}
