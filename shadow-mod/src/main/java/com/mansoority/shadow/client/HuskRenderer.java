package com.mansoority.shadow.client;

import com.mansoority.shadow.HollowHusk;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;

/** drags itself along: one arm reaches, claws in, pulls; then the other. legs trail. the head never stops twitching */
public class HuskRenderer extends RigidMobRenderer<HollowHusk> {
    public HuskRenderer(EntityRendererProvider.Context ctx) { super(ctx, "hollow_husk", 0.7F); }

    @Override
    protected void animate(RigidMesh m, HollowHusk e, float pt, float age) {
        float pos = e.walkAnimation.position(pt) * 1.4F, sp = Math.min(1F, e.walkAnimation.speed(pt) * 1.8F);
        float idle = 1F - sp;
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "L" : "R";
            float sign = s == 0 ? 1F : -1F;
            float ph = pos + s * Mth.PI;
            float reach = Mth.sin(ph) * sp, up = Math.max(0F, Mth.cos(ph)) * sp;
            RigidMesh.Part arm = m.get("arm" + side), fore = m.get("fore" + side);
            if (arm != null) { arm.xRot = -reach * 0.55F - up * 0.25F + idle * Mth.sin(age * 0.06F + s) * 0.05F; arm.zRot = up * 0.2F * sign; }
            if (fore != null) fore.xRot = reach * 0.35F - up * 0.5F;
            RigidMesh.Part th = m.get("thigh" + side), sh = m.get("shin" + side);
            if (th != null) { th.yRot = Mth.sin(ph * 0.5F) * 0.12F * sp; th.zRot = Mth.sin(age * 0.11F + s * 3) * 0.04F; }
            if (sh != null) sh.xRot = Mth.sin(age * 0.2F + s * 2F) * 0.06F;      // dead legs twitch
        }
        RigidMesh.Part chest = m.get("chest"), head = m.get("head"), jaw = m.get("jaw");
        if (chest != null) { chest.xRot = Mth.sin(pos) * 0.06F * sp; chest.zRot = Mth.sin(pos) * 0.1F * sp; }
        if (head != null) {
            float tw = (Mth.sin(age * 1.7F) > 0.93F ? Mth.sin(age * 9F) * 0.25F : 0F);            // sudden jerks
            head.yRot = Mth.sin(age * 0.05F) * 0.25F + tw;
            head.zRot = Mth.sin(age * 0.04F) * 0.3F;
            head.xRot = -Mth.clamp(e.getXRot(), -30, 30) * Mth.DEG_TO_RAD * 0.6F;
        }
        if (jaw != null) jaw.xRot = 0.25F + e.getAttackAnim(pt) * 0.8F + Math.max(0F, Mth.sin(age * 0.15F)) * 0.15F;
    }
}
