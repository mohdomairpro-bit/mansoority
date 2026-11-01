package com.mansoority.shadow.client;

import com.mansoority.shadow.Scavenger;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/** six long legs in two tripods, the neck bobbing; the skull rides in the cradle on its back */
public class ScavengerRenderer extends RigidMobRenderer<Scavenger> {
    public ScavengerRenderer(EntityRendererProvider.Context ctx) { super(ctx, "scavenger", 0.6F); scale = 1.6F; }

    @Override
    protected void place(Scavenger e, PoseStack pose, float pt) {
        if (e.sink() > 0) pose.mulPose(Axis.XP.rotationDegrees(Math.min(60F, (e.sink() + pt) * 3F)));
    }

    @Override
    protected void animate(RigidMesh m, Scavenger e, float pt, float age) {
        float w = Mth.lerp(pt, e.strideO, e.stride);
        for (int k = 0; k < 3; k++) for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "L" : "R";
            float sign = s == 0 ? 1F : -1F;
            // tripod gait: each leg planted and dragging back 60% of the time, then a high quick step forward
            float u = (w / Mth.TWO_PI + (((k + s) % 2 == 0) ? 0F : 0.5F) + k * 0.05F) % 1F; if (u < 0F) u += 1F;
            float fwd, lift;
            if (u < 0.6F) { fwd = Mth.lerp(u / 0.6F, 1F, -1F); lift = 0F; }
            else { float v = (u - 0.6F) / 0.4F; fwd = Mth.lerp(ease(v), -1F, 1F); lift = Mth.sin(v * Mth.PI); }
            RigidMesh.Part a = m.get("leg" + k + side + "_0"), b = m.get("leg" + k + side + "_1");
            if (a == null) continue;
            a.yRot = fwd * 0.35F * sign;
            a.zRot = -lift * 0.45F * sign;
            if (b != null) b.zRot = lift * 0.3F * sign;
        }
        RigidMesh.Part neck = m.get("neck"), head = m.get("head"), body = m.get("body");
        if (body != null) { body.ty = -Math.abs(Mth.cos(w)) * 0.035F; body.zRot = Mth.sin(w) * 0.04F; }
        if (neck != null) neck.xRot = Mth.sin(w * 2F - 0.6F) * 0.09F + Mth.sin(age * 0.07F) * 0.05F;      // bobs a beat behind the steps
        if (head != null) head.yRot = Mth.sin(age * 0.3F) * 0.2F * (Mth.sin(age * 0.05F) > 0.6F ? 1F : 0.2F);
    }

    @Override
    public void render(Scavenger e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        super.render(e, yaw, pt, pose, buf, light);
        if (!e.carrying()) return;
        // the skull in its cradle
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot)));
        pose.translate(0, 1.95 * scale / 1.6F + Math.abs(Mth.sin(Mth.lerp(pt, e.strideO, e.stride))) * 0.05F, 0);
        SkullRenderer.drawSkull(pose, buf, light, 2.2F, 0F);
        pose.popPose();
    }
}
