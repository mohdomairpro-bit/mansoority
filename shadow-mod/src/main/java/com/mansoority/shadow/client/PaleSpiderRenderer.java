package com.mansoority.shadow.client;

import com.mansoority.shadow.PaleSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * the pale spiders, from their own model: eight legs walking in two alternating sets of four, tentacles that never
 * stop moving (and shoot out to catch you), fangs that open before a bite, upside down when they hang from a ceiling
 */
public class PaleSpiderRenderer extends RigidMobRenderer<PaleSpider> {
    public PaleSpiderRenderer(EntityRendererProvider.Context ctx) { super(ctx, "pale_biter", 0.9F); }

    @Override protected String mesh(PaleSpider e) { return e.spitter ? "pale_spitter" : "pale_biter"; }

    @Override
    protected void place(PaleSpider e, PoseStack pose, float pt) {
        if (e.clinging()) {                                     // belly up under the ceiling
            pose.translate(0, e.getBbHeight() + 0.05, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
        }
    }

    @Override
    protected void animate(RigidMesh m, PaleSpider e, float pt, float age) {
        float walk = Mth.lerp(pt, e.walkO, e.walk);
        float speed = Mth.clamp((e.walk - e.walkO) * 2.5F, 0F, 1F);
        // legs: sets of four (L0 R1 L2 R3 / R0 L1 R2 L3) take turns; each swings, lifts, plants
        for (int k = 0; k < 4; k++) for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "L" : "R";
            float sign = s == 0 ? -1F : 1F;
            float ph = walk + (((k + s) % 2 == 0) ? 0F : Mth.PI);
            float swing = Mth.sin(ph) * 0.42F * speed;
            float lift = Math.max(0F, Mth.cos(ph)) * 0.45F * speed;
            float idle = Mth.sin(age * 0.08F + k * 1.3F + s * 2F) * 0.04F;
            RigidMesh.Part a = m.get("leg" + k + side + "_0"), b = m.get("leg" + k + side + "_1"), c = m.get("leg" + k + side + "_2");
            if (a == null) continue;
            a.yRot = swing * sign * -1F;
            a.zRot = (lift + idle) * sign * -1F;
            if (b != null) b.zRot = -lift * 0.6F * sign * -1F;
            if (c != null) c.zRot = lift * 0.3F * sign * -1F;
            if (e.clinging()) a.zRot += 0.25F * sign;               // legs gripping the ceiling
        }
        // body: bobs with the steps, the abdomen breathes and sways
        RigidMesh.Part body = m.get("body"), abd = m.get("abdomen");
        if (body != null) { body.ty = Math.abs(Mth.sin(walk)) * 0.03F * speed; body.zRot = Mth.sin(walk) * 0.04F * speed; }
        if (abd != null) {
            float br = 1F + Mth.sin(age * 0.12F) * 0.03F;
            abd.sx = br; abd.sz = br; abd.sy = 1F / br;
            abd.yRot = Mth.sin(age * 0.05F) * 0.06F; abd.xRot = -0.1F + Mth.sin(age * 0.07F) * 0.04F;
        }
        // fangs twitch, open wide on the attack
        float atk = e.getAttackAnim(pt);
        float open = 0.15F + Math.max(atk, 0F) * 0.9F + Math.max(0F, Mth.sin(age * 0.3F)) * 0.05F;
        RigidMesh.Part fl = m.get("fangL"), fr = m.get("fangR");
        if (fl != null) fl.yRot = open; if (fr != null) fr.yRot = -open;
        // tentacles: always writhing. during a yank all four stretch out toward whoever it caught
        int y = e.yank();
        float reach = 0F;
        Entity tgt = y > 0 ? e.level().getEntity(e.yankTarget()) : null;
        if (y > 0) {
            float t = y + pt;
            reach = t < 10 ? t / 10F : t < 14 ? 1F : Math.max(0F, 1F - (t - 14) / 8F);
        }
        float dist = tgt != null ? (float) tgt.distanceTo(e) : 6F;
        for (int k = 0; k < 4; k++) for (int sgi = 0; sgi < 3; sgi++) {
            RigidMesh.Part p = m.get("tent" + k + "_" + sgi);
            if (p == null) continue;
            float w = age * 0.17F + k * 1.7F - sgi * 0.9F;
            p.xRot = Mth.sin(w) * 0.35F * (1 - reach);
            p.zRot = Mth.cos(w * 0.8F) * 0.3F * (1 - reach);
            if (reach > 0F) {
                if (sgi == 0) p.xRot = -1.05F * reach;          // swing forward and out
                float stretch = 1F + reach * (dist / 0.5F) / 3F;
                p.sy = p.sz = p.sx = 1F;
                p.sz = stretch; p.sy = stretch; p.sx = 1F;
            }
        }
    }
}
