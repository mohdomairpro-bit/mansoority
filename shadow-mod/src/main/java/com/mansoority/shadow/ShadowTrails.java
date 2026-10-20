package com.mansoority.shadow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * after anything happens, something black is left behind: slow wisps creeping along the path it took,
 * pooling on the ground, for a minute and a half. purely particles - nothing is placed in the world.
 */
public class ShadowTrails {
    private static final int LIFE = 20 * 90, MAX = 24;
    private static final List<Trail> TRAILS = new ArrayList<>();

    private record Trail(ServerLevel level, List<Vec3> pts, int[] age) {}

    public static void add(ServerLevel level, List<Vec3> pts) {
        if (pts.isEmpty()) return;
        List<Vec3> copy = new ArrayList<>(pts);
        if (copy.size() == 1) {
            Vec3 c = copy.get(0); RandomSource r = level.random;
            for (int i = 1; i < 6; i++) copy.add(c.add((r.nextDouble() - 0.5) * 6, 0, (r.nextDouble() - 0.5) * 6));
        }
        if (TRAILS.size() >= MAX) TRAILS.remove(0);
        TRAILS.add(new Trail(level, copy, new int[]{0}));
    }

    /** something passed by here: a winding trail from the dark up to just behind the player */
    public static void behind(ServerPlayer p) {
        RandomSource r = p.getRandom();
        Vec3 back = p.getLookAngle().multiply(1, 0, 1).normalize().scale(-1);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        List<Vec3> pts = new ArrayList<>();
        double wob = r.nextDouble() * 6;
        for (int i = 0; i < 14; i++) {
            double t = 24 - i * 1.6;
            Vec3 q = p.position().add(back.scale(t)).add(side.scale(Math.sin(i * 0.7 + wob) * 2.5));
            net.minecraft.core.BlockPos s = ShadowEntity.findStand(p.serverLevel(), q.x, p.getY(), q.z, 6);
            if (s != null) pts.add(new Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5));
        }
        if (!pts.isEmpty()) add(p.serverLevel(), pts);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRAILS.isEmpty()) return;
        Iterator<Trail> it = TRAILS.iterator();
        while (it.hasNext()) {
            Trail t = it.next();
            int a = ++t.age()[0];
            if (a > LIFE) { it.remove(); continue; }
            if (a % 5 != 0) continue;
            if (t.level().players().stream().noneMatch(pl -> pl.distanceToSqr(t.pts().get(0)) < 96 * 96)) continue;
            RandomSource r = t.level().random;
            float fade = 1F - a / (float) LIFE;
            int n = 1 + (int) (3 * fade);
            for (int k = 0; k < n; k++) {
                int i = r.nextInt(Math.max(1, t.pts().size() - 1));
                Vec3 p0 = t.pts().get(i), p1 = t.pts().get(Math.min(i + 1, t.pts().size() - 1));
                double f = r.nextDouble();
                Vec3 q = p0.lerp(p1, f);
                Vec3 dir = p1.subtract(p0);
                double len = Math.max(0.001, dir.length());
                // count 0 = the offsets are a velocity: the wisps creep along the path it took
                t.level().sendParticles(ShadowRegistry.WISP.get(), q.x + (r.nextDouble() - 0.5) * 0.6, q.y + 0.05 + r.nextDouble() * 0.3,
                        q.z + (r.nextDouble() - 0.5) * 0.6, 0, dir.x / len * 0.12, -0.02, dir.z / len * 0.12, 1.0);
            }
        }
    }
}
