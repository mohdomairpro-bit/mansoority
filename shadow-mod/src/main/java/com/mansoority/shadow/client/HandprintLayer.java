package com.mansoority.shadow.client;

import com.mansoority.shadow.TheShadow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** its mark: a black handprint pressed into your back, for everyone to see, for a day */
public class HandprintLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation TEX = new ResourceLocation(TheShadow.MODID, "textures/entity/handprint.png");
    private static final Map<Integer, Long> MARKED = new HashMap<>();

    public HandprintLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    public static void mark(int entityId, int seconds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) MARKED.put(entityId, mc.level.getGameTime() + seconds * 20L);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer p, float limb, float limbAmt,
                       float pt, float age, float headYaw, float headPitch) {
        Long until = MARKED.get(p.getId());
        if (until == null) return;
        if (p.level().getGameTime() > until) { MARKED.remove(p.getId()); return; }
        if (p.isInvisible()) return;
        pose.pushPose();
        this.getParentModel().body.translateAndRotate(pose);
        // the back of the torso is 2 px behind the pivot; the print sits just outside it (and outside a jacket layer)
        pose.translate(0.0F, 6.0F / 16F, 2.35F / 16F);
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = pose.last().pose(); Matrix3f n = pose.last().normal();
        float s = 3.4F / 16F;
        v(vc, m, n, -s, -s, 1, 0, light); v(vc, m, n, s, -s, 0, 0, light);
        v(vc, m, n, s, s, 0, 1, light);  v(vc, m, n, -s, s, 1, 1, light);
        pose.popPose();
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float vv, int light) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 235).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
    }
}
