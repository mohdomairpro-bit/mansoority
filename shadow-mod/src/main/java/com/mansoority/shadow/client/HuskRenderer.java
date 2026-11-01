package com.mansoority.shadow.client;

import com.mansoority.shadow.HollowHusk;
import com.mansoority.shadow.ShadowRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/**
 * drags itself along: one arm lifts and reaches, slams its claws in, and hauls - the whole body surging forward on
 * each pull, the dead legs dragging behind - then the other. to attack it rears up on its arms and throws itself
 * down jaw-first. it dies by swelling up, shuddering, and bursting.
 */
public class HuskRenderer extends RigidMobRenderer<HollowHusk> {
    public HuskRenderer(EntityRendererProvider.Context ctx) { super(ctx, "hollow_husk", 0.7F); }

    @Override protected boolean ownDeath(HollowHusk e) { return true; }

    @Override
    protected void animate(RigidMesh m, HollowHusk e, float pt, float age) {
        float pos = e.walkAnimation.position(pt) * 1.0F, sp = Math.min(1F, e.walkAnimation.speed(pt) * 1.8F);
        float idle = 1F - sp;
        float a = e.getAttackAnim(pt);
        float rear = a <= 0F ? 0F : a < 0.3F ? back(a / 0.3F) : 1F - ease((a - 0.3F) / 0.25F);     // up on its arms...
        float slam = a <= 0.3F ? 0F : a < 0.5F ? back((a - 0.3F) / 0.2F) : 1F - ease((a - 0.5F) / 0.5F);  // ...and down jaw-first
        float surge = 0F;
        for (int s = 0; s < 2; s++) {
            String side = s == 0 ? "L" : "R";
            float sign = s == 0 ? 1F : -1F;
            float u = (pos / Mth.TWO_PI + s * 0.5F) % 1F; if (u < 0F) u += 1F;
            float xr, fr, out;
            if (u < 0.38F) {                                    // lift and reach far forward
                float v = ease(u / 0.38F);
                xr = Mth.lerp(v, 0.35F, -0.75F); fr = Mth.lerp(v, -0.5F, 0.15F); out = Mth.sin(v * Mth.PI) * 0.25F;
            } else if (u < 0.48F) {                             // slam the claws in
                float v = (u - 0.38F) / 0.1F;
                xr = Mth.lerp(v * v, -0.75F, -0.45F); fr = Mth.lerp(v, 0.15F, 0.3F); out = 0F;
            } else {                                            // haul: the arm drags back under it, the body comes forward
                float v = (u - 0.48F) / 0.52F;
                xr = Mth.lerp(ease(v), -0.45F, 0.35F); fr = Mth.lerp(v, 0.3F, -0.5F); out = 0F;
                surge += Mth.sin(v * Mth.PI);
            }
            xr *= sp; fr *= sp; out *= sp;
            float breathe = idle * Mth.sin(age * 0.06F + s) * 0.05F;
            xr += breathe - rear * 1.05F + slam * 0.55F;       // attacking: both arms planted far forward, then thrown down
            fr += rear * 0.6F - slam * 0.3F;
            RigidMesh.Part arm = m.get("arm" + side), fore = m.get("fore" + side);
            if (arm != null) { arm.xRot = xr; arm.zRot = out * sign + rear * 0.2F * sign; }
            if (fore != null) fore.xRot = fr;
            RigidMesh.Part th = m.get("thigh" + side), sh = m.get("shin" + side);
            if (th != null) { th.yRot = Mth.sin(pos + s * Mth.PI) * 0.15F * sp; th.zRot = Mth.sin(age * 0.11F + s * 3) * 0.04F; }
            if (sh != null) sh.xRot = Mth.sin(age * 0.2F + s * 2F) * 0.06F + sp * 0.1F;     // dead legs, dragging
        }
        surge *= sp;
        RigidMesh.Part pelvis = m.get("pelvis"), chest = m.get("chest"), head = m.get("head"), jaw = m.get("jaw");
        if (pelvis != null) { pelvis.tz = surge * 0.05F; pelvis.yRot = Mth.sin(pos) * 0.1F * sp; }
        if (chest != null) {
            chest.xRot = -surge * 0.08F - rear * 0.55F + slam * 0.35F;
            chest.zRot = Mth.sin(pos) * 0.12F * sp;
            chest.ty = surge * 0.03F;
        }
        if (head != null) {
            float tw = (Mth.sin(age * 1.7F) > 0.93F ? Mth.sin(age * 9F) * 0.25F : 0F);            // sudden jerks
            head.yRot = Mth.sin(age * 0.05F) * 0.25F * (1F - rear) + tw;
            head.zRot = Mth.sin(age * 0.04F) * 0.3F * (1F - rear) + tw * 0.5F;
            head.xRot = -Mth.clamp(e.getXRot(), -30, 30) * Mth.DEG_TO_RAD * 0.6F + surge * 0.12F + rear * 0.35F + slam * 0.3F;   // the head lags each surge
        }
        if (jaw != null) jaw.xRot = 0.25F + Math.max(rear, slam) * 0.85F + Math.max(0F, Mth.sin(age * 0.15F)) * 0.15F;

        // dying: swells up, shudders, bursts
        if (e.deathTime > 0) {
            float t = e.deathTime + pt;
            if (t < 7F) {
                float k = t / 7F, sh = Mth.sin(age * 5F) * 0.12F * k;
                float sw = 1F + 0.4F * k * k;
                for (String n : new String[]{"chest", "head", "pelvis"}) {
                    RigidMesh.Part p = m.get(n);
                    if (p == null) continue;
                    p.sx = sw; p.sy = sw; p.sz = sw;
                    p.zRot += sh; p.xRot += sh * 0.5F;
                }
                if (jaw != null) jaw.xRot = 1.1F * k;
            } else {
                for (RigidMesh.Part p : m.parts) p.visible = false;
                if (!e.burst) {
                    e.burst = true;
                    var l = e.level();
                    for (int i = 0; i < 40; i++) {
                        double vx = (l.random.nextDouble() - 0.5) * 0.5, vy = l.random.nextDouble() * 0.45, vz = (l.random.nextDouble() - 0.5) * 0.5;
                        l.addParticle(ShadowRegistry.BILE.get(), e.getX(), e.getY() + 0.4, e.getZ(), vx, vy, vz);
                        if (i % 3 == 0) l.addParticle(ParticleTypes.SQUID_INK, e.getX(), e.getY() + 0.4, e.getZ(), vx * 0.6, vy * 0.6, vz * 0.6);
                    }
                    l.playLocalSound(e.getX(), e.getY(), e.getZ(), net.minecraft.sounds.SoundEvents.SLIME_DEATH, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.5F, false);
                }
            }
        }
    }
}
