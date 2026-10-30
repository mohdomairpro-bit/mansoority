package com.mansoority.shadow.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Walking for real. Each hand and foot is locked to the spot in the world where it last landed while the body moves on;
 * when it falls too far behind where it should be, it lifts, arcs forward and lands on whatever the ground is there.
 * Limbs take turns (one at a time when walking, diagonal pairs when it runs), turning on the spot makes it step round,
 * the body tilts with the slope under it and sways toward whichever feet are carrying it.
 */
final class Legs {
    /** per entity */
    static final class State {
        final double[][] plant = new double[4][3], from = new double[4][3], to = new double[4][3];
        final boolean[] has = new boolean[4], stepping = new boolean[4];
        final float[] t = new float[4], dur = new float[4], lift = new float[4];
        float bodyDy, pitch, roll, sway;
        /** 0..1 per limb: how far through its step (for curling toes and fingers) */
        final float[] air = new float[4];
    }

    private static final String[] LOCO = {"walk", "boss_walk", "crawl", "scuttle"};
    private static final Map<String, Vector3f[]> NEUTRAL = new HashMap<>();
    private static final int[] DIAG = {3, 2, 1, 0};          // legL<->armR, legR<->armL
    private static final int[] SIDE = {2, 3, 0, 1};          // legL<->armL, legR<->armR

    static boolean loco(String clip) { for (String c : LOCO) if (c.equals(clip)) return true; return false; }

    /** where each limb sits on average over a walking clip: its "home" under the body */
    static Vector3f[] neutral(ShadowAnim A, ShadowMesh m, String clip) {
        String key = clip + "@" + System.identityHashCode(m);
        Vector3f[] have = NEUTRAL.get(key);
        if (have != null) return have;
        // remember this frame's pose: working this out borrows the mesh
        int np = m.parts.size();
        float[][] save = new float[np][10];
        for (ShadowMesh.Part p : m.parts) {
            float[] s = save[p.index];
            s[0] = p.anim.x; s[1] = p.anim.y; s[2] = p.anim.z; s[3] = p.anim.w; s[4] = p.tx; s[5] = p.ty; s[6] = p.tz; s[7] = p.xRot; s[8] = p.yRot; s[9] = p.zRot;
        }
        Vector3f[] out = compute(A, m, clip);
        for (ShadowMesh.Part p : m.parts) {
            float[] s = save[p.index];
            p.anim.set(s[0], s[1], s[2], s[3]); p.tx = s[4]; p.ty = s[5]; p.tz = s[6]; p.xRot = s[7]; p.yRot = s[8]; p.zRot = s[9];
        }
        NEUTRAL.put(key, out);
        return out;
    }

    private static Vector3f[] compute(ShadowAnim A, ShadowMesh m, String clip) {
        {
            ShadowAnim.Clip c = A.clips.get(clip);
            Vector3f[] sum = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
            if (c == null) return sum;
            float[][] frame = new float[A.parts.length][7];
            int n = c.frames() - (c.loop ? 1 : 0);
            Vector3f f = new Vector3f();
            for (int i = 0; i < n; i++) {
                ShadowAnim.sample(c, i, frame);
                for (ShadowMesh.Part p : m.parts) p.reset();
                for (int b = 0; b < A.parts.length; b++) {
                    ShadowMesh.Part p = m.get(A.parts[b]);
                    if (p == null) continue;
                    float[] v = frame[b];
                    p.anim.set(v[0], v[1], v[2], v[3]).normalize(); p.tx = v[4]; p.ty = v[5]; p.tz = v[6];
                }
                ShadowSkeleton.fk(m);
                for (int l = 0; l < 4; l++) sum[l].add(ShadowSkeleton.endPos(m, l, f));
            }
            for (Vector3f v : sum) v.div(Math.max(1, n));
            return sum;
        }
    }

    private static double[] toModel(double wx, double wy, double wz, double ex, double ey, double ez, float yawDeg, float scale) {
        double a = Math.toRadians(-yawDeg), c = Math.cos(a), s = Math.sin(a);
        double dx = wx - ex, dz = wz - ez;
        return new double[]{(dx * c - dz * s) / scale, (wy - ey) / scale, (dx * s + dz * c) / scale};
    }

    /**
     * place the limbs. the mesh already holds this frame's animated pose. returns a bitmask of limbs that landed this
     * frame (footsteps).
     */
    /**
     * how far above the ground each contact point sits when planted (model metres): the sole of the foot is the contact
     * point itself; a hand stands on its claws, which hang 0.24 below the contact point
     */
    static final float[] SOLE = {0.003F, 0.003F, 0.215F, 0.215F};

    static int update(ShadowMesh m, State st, ShadowAnim A, String clip, int clipContact, Level level,
                      double ex, double ey, double ez, double vx, double vz, float yaw, float scale, float dt, boolean allowBody) {
        if (m.get("shinL") == null) return 0;
        ShadowSkeleton.fk(m);
        boolean walking = loco(clip);
        Vector3f[] home = walking ? neutral(A, m, clip) : null;
        ShadowSkeleton.fk(m);     // neutral() may have disturbed the matrices: rebuild from this frame's pose
        double speed = Math.hypot(vx, vz);                          // blocks per tick
        double spm = speed / scale;                                  // model metres per tick
        float thr = (float) Mth.clamp(0.20 + spm * 3.2, 0.20, 0.42) * scale;      // how far behind a foot may fall (blocks)
        float stepDur = (float) Mth.clamp(11.0 - spm * 60.0, 3.5, 11.0);          // ticks a step takes
        int maxSteps = spm > 0.004 ? 2 : 1;            // walking: up to two in the air, never both on one side
        boolean trot = spm > 0.06;                 // running: diagonal pairs together
        boolean[] free = new boolean[4];
        double[][] want = new double[4][];
        Vector3f f = new Vector3f();
        int touched = 0;
        for (int i = 0; i < 4; i++) {
            ShadowSkeleton.endPos(m, i, f);
            boolean planted = (clipContact >> i & 1) != 0;
            if (!planted && f.y > ShadowSkeleton.END[i][1] + 0.3F) { free[i] = true; st.has[i] = false; st.stepping[i] = false; st.air[i] = 1F; continue; }
            Vector3f d = walking ? new Vector3f(home[i]) : new Vector3f(f);
            double[] w = ShadowSkeleton.toWorld(d, ex, ey, ez, yaw, scale);
            double lead = stepDur * 0.55;                            // aim where the body will be when the foot lands
            w[0] += vx * lead; w[2] += vz * lead;
            float g = ShadowSkeleton.groundAt(level, w[0], ey + (d.y - ShadowSkeleton.END[i][1]) * scale, w[2]);
            w[1] = Float.isNaN(g) ? ey + (d.y - ShadowSkeleton.END[i][1]) * scale : g;
            want[i] = w;
            if (!st.has[i]) { st.plant[i] = w.clone(); st.has[i] = true; continue; }
            double err = Math.hypot(st.plant[i][0] - w[0], st.plant[i][2] - w[2]);
            if (err > thr * 5 || Math.abs(st.plant[i][1] - w[1]) > 3.5) { st.plant[i] = w.clone(); st.stepping[i] = false; }   // teleported: just put it down
        }
        // who steps next: the limb that's furthest behind, if the gait allows it
        int busy = 0; for (boolean b : st.stepping) if (b) busy++;
        for (int round = 0; round < 2 && busy < maxSteps; round++) {
            int best = -1; double bestErr = thr;
            for (int i = 0; i < 4; i++) {
                if (free[i] || st.stepping[i] || want[i] == null) continue;
                if (maxSteps == 1 && busy > 0) continue;
                if (trot && busy > 0) {                                  // running: only the diagonal partner may go with it
                    boolean ok = false;
                    for (int j = 0; j < 4; j++) if (st.stepping[j] && DIAG[j] == i) ok = true;
                    if (!ok) continue;
                }
                if (st.stepping[SIDE[i]] && st.t[SIDE[i]] < 0.5F) continue;
                double err = Math.hypot(st.plant[i][0] - want[i][0], st.plant[i][2] - want[i][2]);
                if (err > bestErr) { bestErr = err; best = i; }
            }
            if (best < 0) break;
            int i = best;
            st.from[i] = st.plant[i].clone(); st.to[i] = want[i].clone();
            st.t[i] = 0F; st.dur[i] = stepDur; st.stepping[i] = true; busy++;
            double dist = Math.hypot(st.to[i][0] - st.from[i][0], st.to[i][2] - st.from[i][2]);
            st.lift[i] = (float) (Math.min(1.6, 0.25 + dist * 0.35) * (scale / 4.6));
        }
        for (int i = 0; i < 4; i++) {
            if (!st.stepping[i]) { st.air[i] = Math.max(0F, st.air[i] - dt * 0.25F); continue; }
            if (want[i] != null) {
                st.to[i][0] += (want[i][0] - st.to[i][0]) * 0.15; st.to[i][2] += (want[i][2] - st.to[i][2]) * 0.15;
                float g = ShadowSkeleton.groundAt(level, st.to[i][0], want[i][1] + 1, st.to[i][2]);
                st.to[i][1] = Float.isNaN(g) ? want[i][1] : g;              // the ground right where it will land
            }
            st.t[i] = Math.min(1F, st.t[i] + dt / st.dur[i]);
            float u = st.t[i];
            float e = u * u * (3 - 2 * u);
            for (int k = 0; k < 3; k++) st.plant[i][k] = Mth.lerp(e, st.from[i][k], st.to[i][k]);
            st.plant[i][1] += Math.sin(u * Math.PI) * st.lift[i] + Math.max(0, st.to[i][1] - st.from[i][1]) * Math.sin(u * Math.PI) * 0.5;
            st.air[i] = (float) Math.sin(u * Math.PI);
            if (u >= 1F) { st.stepping[i] = false; st.plant[i] = st.to[i].clone(); touched |= 1 << i; }
        }
        // the body: sits on its footholds, tilts with the slope, leans toward the feet that are carrying it
        double front = 0, back = 0, left = 0, right = 0; int nf = 0, nb = 0, nl = 0, nr = 0;
        float lowest = Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (free[i] || !st.has[i]) continue;
            double h = (st.stepping[i] ? st.to[i][1] : st.plant[i][1]) - ey;
            lowest = Math.min(lowest, (float) h);
            if (i >= 2) { front += h; nf++; } else { back += h; nb++; }
            if (i % 2 == 0) { left += h; nl++; } else { right += h; nr++; }
        }
        float k = Math.min(1F, dt * 0.3F);
        float body = allowBody && lowest != Float.MAX_VALUE ? Mth.clamp(lowest / scale, -0.45F, 0.35F) : 0F;
        st.bodyDy += (body - st.bodyDy) * k;
        float wantPitch = nf > 0 && nb > 0 ? (float) Math.atan2((front / nf - back / nb), 0.74 * scale) : 0F;
        float wantRoll = nl > 0 && nr > 0 ? (float) Math.atan2((right / nr - left / nl), 0.9 * scale) : 0F;
        st.pitch += (Mth.clamp(wantPitch, -0.5F, 0.5F) - st.pitch) * k;
        st.roll += (Mth.clamp(wantRoll, -0.35F, 0.35F) - st.roll) * k;
        float wantSway = 0F;                                          // lean away from a lifted foot
        for (int i = 0; i < 4; i++) if (st.stepping[i]) wantSway += (i % 2 == 0 ? 1F : -1F) * st.air[i];
        st.sway += (wantSway * 0.035F - st.sway) * Math.min(1F, dt * 0.25F);
        ShadowMesh.Part hips = m.get("hips");
        if (hips != null) {
            hips.ty += st.bodyDy;
            hips.tx += st.sway;
            hips.xRot -= st.pitch * 0.75F;          // nose down when the hands are lower than the feet
            hips.zRot -= st.roll * 0.6F;
        }
        ShadowSkeleton.fk(m);
        for (int i = 0; i < 4; i++) {
            if (free[i] || !st.has[i]) continue;
            double[] mp = toModel(st.plant[i][0], st.plant[i][1], st.plant[i][2], ex, ey, ez, yaw, scale);
            ShadowSkeleton.twoBone(m, i, new Vector3f((float) mp[0], (float) mp[1] + SOLE[i], (float) mp[2]));
        }
        Digits.flex(m, st.air, free);
        return touched;
    }

    private Legs() {}
}
