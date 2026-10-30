package com.mansoority.shadow.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/** shared bits for the mobs drawn from rigid meshes: facing, the red hurt flash, falling over when they die */
public abstract class RigidMobRenderer<T extends LivingEntity> extends EntityRenderer<T> {
    private final String meshName;
    protected float scale = 1F;

    protected RigidMobRenderer(EntityRendererProvider.Context ctx, String mesh, float shadow) {
        super(ctx);
        this.meshName = mesh;
        this.shadowRadius = shadow;
    }

    protected String mesh(T e) { return meshName; }

    /** pose the parts for this frame (they're reset first) */
    protected abstract void animate(RigidMesh m, T e, float pt, float age);

    /** extra transform before drawing (upside down on a ceiling, etc.) */
    protected void place(T e, PoseStack pose, float pt) {}

    @Override
    public void render(T e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
        RigidMesh m = RigidMesh.load(mesh(e));
        if (m == null) return;
        m.reset();
        float age = e.tickCount + pt;
        animate(m, e, pt, age);
        m.pose();
        pose.pushPose();
        float body = Mth.rotLerp(pt, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(-body));
        if (e.deathTime > 0) {                                   // keels over
            float k = Math.min(1F, (e.deathTime + pt - 1F) / 20F * 1.6F);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sqrt(k) * 90F));
        }
        place(e, pose, pt);
        pose.scale(scale, scale, scale);
        int overlay = OverlayTexture.pack(OverlayTexture.u(0F), OverlayTexture.v(e.hurtTime > 0 || e.deathTime > 0));
        m.draw(pose, buf, RenderType.entityCutoutNoCull(RigidMesh.PALETTE), RenderType.eyes(RigidMesh.PALETTE), light, overlay, 1F, 1F, 1F, 1F);
        pose.popPose();
        super.render(e, yaw, pt, pose, buf, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T e) { return RigidMesh.PALETTE; }
}
