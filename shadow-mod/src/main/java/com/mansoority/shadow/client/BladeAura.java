package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** whoever holds the blade: violet-to-white motes stream up off it, with the odd bright spark */
final class BladeAura {
    private static final DustColorTransitionOptions MOTE = new DustColorTransitionOptions(new Vector3f(0.62F, 0.22F, 1.0F), new Vector3f(1.0F, 0.95F, 1.0F), 0.9F);
    private static final DustColorTransitionOptions DEEP = new DustColorTransitionOptions(new Vector3f(0.30F, 0.05F, 0.55F), new Vector3f(0.75F, 0.5F, 1.0F), 1.3F);

    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        var r = mc.level.random;
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > 40 * 40) continue;
            boolean main = p.getMainHandItem().is(ShadowRegistry.SHADOW_BLADE.get()), off = p.getOffhandItem().is(ShadowRegistry.SHADOW_BLADE.get());
            if (!main && !off) continue;
            boolean self = p == mc.player && mc.options.getCameraType().isFirstPerson();
            // roughly where the blade is: out from the hand, up along it
            float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
            int side = (main ^ p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT) ? 1 : -1;
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)).scale(side);
            Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
            Vec3 hand = p.position().add(0, 0.75, 0).add(right.scale(0.38)).add(fwd.scale(self ? 0.55 : 0.25));
            int n = self ? 1 : 2;
            for (int i = 0; i < n; i++) {
                double along = r.nextDouble() * 1.3;
                Vec3 at = hand.add(fwd.scale(along * 0.45)).add(0, along * 0.75, 0).add((r.nextDouble() - 0.5) * 0.25, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.25);
                mc.level.addParticle(r.nextInt(3) == 0 ? DEEP : MOTE, at.x, at.y, at.z, 0, 0.03 + r.nextDouble() * 0.04, 0);
            }
            if (r.nextInt(6) == 0) {
                double along = r.nextDouble() * 1.2;
                Vec3 at = hand.add(fwd.scale(along * 0.45)).add(0, along * 0.75, 0);
                mc.level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 0.02, 0.025, (r.nextDouble() - 0.5) * 0.02);
            }
        }
    }

    private BladeAura() {}
}
