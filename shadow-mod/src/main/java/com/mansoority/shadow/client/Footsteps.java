package com.mansoority.shadow.client;

import com.mansoority.shadow.MansoorityBoss;
import com.mansoority.shadow.ShadowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** weight: every time a hand or foot comes down - a thud, dust off the block it hit, and for the big one, the ground shakes */
final class Footsteps {
    static void step(ShadowEntity e, ShadowMesh m, int touched, double ex, double ey, double ez, float yaw, float scale, double dist2) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        boolean boss = e instanceof MansoorityBoss;
        if (!boss && e.getStage() == 0 && e.getRush() <= 0) return;           // lurking: not a sound
        boolean scuttle = e.getScuttle() > 0 || e.getRush() > ShadowEntity.RUSH_GO;
        double dist = Math.sqrt(dist2);
        Vector3f f = new Vector3f();
        var rnd = mc.level.random;
        for (int i = 0; i < 4; i++) {
            if ((touched >> i & 1) == 0) continue;
            ShadowSkeleton.endPos(m, i, f);
            double[] w = ShadowSkeleton.toWorld(f, ex, ey, ez, yaw, scale);
            BlockPos below = BlockPos.containing(w[0], w[1] - 0.3, w[2]);
            BlockState g = mc.level.getBlockState(below);
            if (g.isAir()) { below = below.below(); g = mc.level.getBlockState(below); }
            if (g.isAir()) continue;
            var st = g.getSoundType();
            float heavy = boss ? 1F : scuttle ? 0.35F : 0.6F;
            mc.level.playLocalSound(w[0], w[1], w[2], st.getStepSound(), SoundSource.HOSTILE, 0.5F + heavy * 0.9F, st.getPitch() * (boss ? 0.45F : 0.6F), false);
            if (boss || (!scuttle && e.getStage() != 0)) mc.level.playLocalSound(w[0], w[1], w[2], com.mansoority.shadow.ShadowRegistry.STEP.get(), SoundSource.HOSTILE,
                    boss ? 1.6F : 0.7F, (boss ? 0.8F : 1.1F) + rnd.nextFloat() * 0.12F, false);
            else if (scuttle) mc.level.playLocalSound(w[0], w[1], w[2], SoundEvents.SKELETON_STEP, SoundSource.HOSTILE, 0.45F, 1.25F + rnd.nextFloat() * 0.3F, false);
            int n = boss ? 14 : scuttle ? 4 : 7;
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, g);
            for (int k = 0; k < n; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.2 + rnd.nextDouble() * (boss ? 1.2 : 0.6);
                mc.level.addParticle(dust, w[0] + Math.cos(a) * r, w[1] + 0.05, w[2] + Math.sin(a) * r,
                        Math.cos(a) * 0.08, 0.06 + rnd.nextDouble() * 0.12, Math.sin(a) * 0.08);
            }
            if (boss) mc.level.addParticle(ParticleTypes.POOF, w[0], w[1] + 0.2, w[2], 0, 0.02, 0);
            float shake = boss ? 2.2F : scuttle ? 0.5F : 0.35F;
            float reach = boss ? 28F : 12F;
            if (dist < reach) ClientFx.kick(shake * (float) (1.0 - dist / reach));
        }
    }

    private Footsteps() {}
}
