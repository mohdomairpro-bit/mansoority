package com.mansoority.shadow.client;

import com.mansoority.shadow.PaleSpider;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * the pale spiders, from their own model. a real gait: each leg plants and drags back along the ground, then snaps
 * up and forward (two sets of four, rippling front to back). close to you it rears up on its back legs, front legs
 * raised and waving, fangs spread - then slams down into the bite. bracing before a tentacle yank, jerking back on
 * the pull. dead, it flips onto its back and its legs curl in, twitching.
 */
public class PaleSpiderRenderer extends RigidMobRenderer<PaleSpider> {
    public PaleSpiderRenderer(EntityRendererProvider.Context ctx) { super(ctx, "pale_biter", 0.9F); }

    @Override protected String mesh(PaleSpider e) { return e.spitter ? "pale_spitter" : "pale_biter"; }
    @Override protected boolean ownDeath(PaleSpider e) { return true; }

    private static float rear(PaleSpider e, float pt) { return ease(Mth.lerp(pt, e.rearO, e.rear)); }

    /** the bite after the rear-up: 0..1 over the vanilla swing */
    private static float strike(PaleSpider e, float pt) {
        float a = e.getAttackAnim(pt);
        return a <= 0F ? 0F : a < 0.25F ? back(a / 0.25F) : 1F - ease((a - 0.25F) / 0.75F);
    }

    private static float yankK(PaleSpider e, float pt) {
        int y = e.yank();
        return y <= 0 ? -1F : y + pt;
    }

    @Override
    protected void place(PaleSpider e, PoseStack pose, float pt) {
        if (e.deathTime > 0) {                                  // flips onto its back
            float k = Math.min(1F, (e.deathTime + pt) / 9F);
            float f = back(k);
            pose.translate(0, Math.sin(k * Math.PI) * 0.45 + f * e.getBbHeight() * 0.55, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(f * 180F));
            return;
        }
        if (e.clinging()) {                                     // belly up under the ceiling
            pose.translate(0, e.getBbHeight() + 0.05, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(180F));
            return;
        }
        // rearing up on its back legs (pivot at the back), then the slam forward into the bite
        float r = rear(e, pt), s = strike(e, pt);
        float y = yankK(e, pt);
        float brace = y < 0 ? 0F : y < 10 ? ease(y / 9F) : y < 13 ? 1F - ease((y - 10) / 3F) : 0F;
        float jerk = y < 10 ? 0F : y < 13 ? back((y - 10) / 3F) : 1F - ease((y - 13) / 9F);
        float pitch = -34F * r * (1F - s) + 14F * s - 12F * brace;
        pose.translate(0, 0, -0.45 - jerk * 0.35);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.translate(0, 0, 0.45 + s * 0.35);
    }

    @Override
    protected void animate(RigidMesh m, PaleSpider e, float pt, float age) {
        float walk = Mth.lerp(pt, e.walkO, e.walk);
        float speed = Mth.clamp(e.pace * 1.4F, 0F, 1F);
        float r = rear(e, pt), s = strike(e, pt);
        float dead = e.deathTime > 0 ? Math.min(1F, (e.deathTime + pt) / 12F) : 0F;
        float y = yankK(e, pt);
        float brace = y < 0 ? 0F : y < 10 ? ease(y / 9F) : y < 13 ? 1F - ease((y - 10) / 3F) : 0F;
        for (int k = 0; k < 4; k++) for (int sd = 0; sd < 2; sd++) {
            String side = sd == 0 ? "L" : "R";
            float sign = sd == 0 ? -1F : 1F;                     // L legs point to -x
            RigidMesh.Part a = m.get("leg" + k + side + "_0"), b = m.get("leg" + k + side + "_1"), c = m.get("leg" + k + side + "_2");
            if (a == null) continue;
            // the step: 62% of the cycle planted and dragging back, then a quick lift and reach forward
            float u = (walk / Mth.TWO_PI + (((k + sd) % 2 == 0) ? 0F : 0.5F) + k * 0.07F) % 1F;
            if (u < 0F) u += 1F;
            float fwd, lift;
            if (u < 0.62F) { fwd = Mth.lerp(u / 0.62F, 1F, -1F); lift = 0F; }
            else { float v = (u - 0.62F) / 0.38F; fwd = Mth.lerp(ease(v), -1F, 1F); lift = Mth.sin(v * Mth.PI); }
            fwd *= speed; lift *= speed;
            float idle = Mth.sin(age * 0.08F + k * 1.3F + sd * 2F) * 0.035F * (1F - speed);
            float up = lift * 0.42F + idle;
            float swing = fwd * 0.36F;
            float bend = -lift * 0.35F;
            // rearing: the front two pairs come up off the ground, clawing at the air; the back pairs brace
            if (k < 2) {
                float wave = Mth.sin(age * 0.45F + k * 1.4F + sd * 2.1F) * 0.22F;
                up += r * (1F - s) * (k == 0 ? 1.15F : 0.6F) + r * (1F - s) * wave * (k == 0 ? 1F : 0.5F);
                swing += r * (k == 0 ? 0.45F : 0.2F) - s * 0.35F;
                bend -= r * 0.5F * (1F - s);
                up -= s * 0.25F;                                 // the strike: front legs stab down
                up += brace * 0.3F;                             // bracing for the yank
            } else {
                up -= r * 0.18F + brace * 0.12F;
                swing -= r * 0.15F;
            }
            // dead: on its back, every leg curling in, twitching less and less
            if (dead > 0F) {
                float tw = Mth.sin(age * 2.1F + k * 1.7F + sd * 2.3F) * 0.3F * (1F - dead) * (1F - dead);
                up = Mth.lerp(dead, up, 0.75F + tw);
                bend = Mth.lerp(dead, bend, -1.3F + tw);
                swing = Mth.lerp(dead, swing, (1.5F - k) * 0.2F);
            }
            a.zRot = up * sign;
            a.yRot = -swing * sign;
            if (b != null) b.zRot = bend * sign;
            if (c != null) c.zRot = (bend * 0.6F - lift * 0.2F - dead * 0.6F) * sign;
            if (e.clinging()) a.zRot -= 0.25F * sign;           // gripping the ceiling
        }
        // body: dips each time a set plants, rolls with the steps
        RigidMesh.Part body = m.get("body"), abd = m.get("abdomen");
        if (body != null) {
            body.ty = -Math.abs(Mth.cos(walk)) * 0.03F * speed;
            body.zRot = Mth.sin(walk) * 0.05F * speed;
        }
        if (abd != null) {
            float br = 1F + Mth.sin(age * 0.12F) * 0.03F + r * 0.05F;
            abd.sx = br; abd.sz = br; abd.sy = 1F / br;
            abd.yRot = Mth.sin(age * 0.05F) * 0.06F - Mth.sin(walk) * 0.08F * speed;    // the abdomen swings against the steps
            abd.xRot = -0.1F + Mth.sin(age * 0.07F) * 0.04F + r * 0.35F;                 // tucks under when it rears
        }
        // fangs: twitching, spread wide while it rears, snapping shut on the bite
        float open = 0.15F + r * 0.75F * (1F - s) + Math.max(0F, Mth.sin(age * 0.3F)) * 0.05F;
        if (s > 0F) open = s > 0.6F ? 1.1F * (1F - s) : 0.9F;
        if (dead > 0F) open = 0.5F * dead;
        RigidMesh.Part fl = m.get("fangL"), fr = m.get("fangR");
        if (fl != null) fl.yRot = open; if (fr != null) fr.yRot = -open;
        // tentacles: always writhing. during a yank all four stretch out toward whoever it caught, then haul back
        float reach = 0F;
        Entity tgt = y > 0 ? e.level().getEntity(e.yankTarget()) : null;
        if (y > 0) reach = y < 8 ? back(y / 8F) : y < 12 ? 1F : Math.max(0F, 1F - ease((y - 12) / 9F));
        float dist = tgt != null ? (float) tgt.distanceTo(e) : 6F;
        for (int k = 0; k < 4; k++) for (int sgi = 0; sgi < 3; sgi++) {
            RigidMesh.Part p = m.get("tent" + k + "_" + sgi);
            if (p == null) continue;
            float w = age * (0.17F + r * 0.2F) + k * 1.7F - sgi * 0.9F;
            float amp = (1F - reach) * (1F + r * 0.6F);
            p.xRot = Mth.sin(w) * 0.35F * amp - r * 0.3F * (sgi == 0 ? 1F : 0F);
            p.zRot = Mth.cos(w * 0.8F) * 0.3F * amp;
            if (dead > 0F) { p.xRot = Mth.lerp(dead, p.xRot, 0.9F); p.zRot *= 1F - dead; }
            if (reach > 0F) {
                if (sgi == 0) p.xRot = -1.05F * reach;          // swing forward and out
                float stretch = 1F + reach * (dist / 0.5F) / 3F;
                p.sz = stretch; p.sy = stretch; p.sx = 1F;
            }
        }
    }
}
