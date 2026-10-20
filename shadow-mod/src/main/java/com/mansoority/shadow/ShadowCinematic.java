package com.mansoority.shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * /shadow cinematic: a two-and-a-half minute short film starring whoever ran it.
 * every feature, in order, with its own camera work and title cards. nobody gets hurt.
 */
public final class ShadowCinematic {
    public static final int OFF = 0, ORBIT = 1, SHOULDER = 2, WIDE = 3, FACE = 4, CRANE = 5;
    private static final Map<UUID, Run> RUNS = new HashMap<>();

    private static final class Run {
        int t, phase, phaseT;
        ShadowEntity cur;
    }

    public static boolean running(ServerPlayer p) { return RUNS.containsKey(p.getUUID()); }

    public static void start(ServerPlayer p) {
        stop(p);
        RUNS.put(p.getUUID(), new Run());
    }

    public static void stop(ServerPlayer p) {
        Run r = RUNS.remove(p.getUUID());
        if (r != null && r.cur != null && r.cur.isAlive()) r.cur.discard();
        cam(p, OFF, 0, 0, 0, null, -1);
    }

    private static void cam(ServerPlayer p, int shot, double a, double b, double c, ShadowEntity focus, int chapter) {
        ShadowNet.send(p, new FxPacket(FxPacket.CAM, shot, a, b, c, focus == null ? -1 : focus.getId(), chapter));
    }

    private static void next(Run r) { r.phase++; r.phaseT = 0; }

    public static void tick(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        if (r == null) return;
        if (!p.isAlive() || p.hasDisconnected()) { stop(p); return; }
        r.t++;
        int t = r.phaseT++;
        switch (r.phase) {
            case 0 -> {                                   // opening: slow orbit, title card
                if (t == 0) {
                    cam(p, ORBIT, 5.5, 1.7, 0.006, null, 0);
                    ShadowEntity.ui(p, ShadowRegistry.DRONE, 1F, 0.7F);
                }
                if (t == 60) ShadowEntity.ui(p, ShadowRegistry.WHISPER, 0.8F, 0.55F);
                if (t == 110) ShadowEvents.warnAll(p);
                if (t >= 150) next(r);
            }
            case 1 -> {                                   // I. it watches: a glimpse from the treeline
                if (t == 0) {
                    r.cur = ShadowEntity.spawnAround(p, 0, 35, 26);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); }
                    cam(p, SHOULDER, 3.6, 1.1, 1.3, r.cur, 1);
                    ShadowEntity.ui(p, ShadowRegistry.HEARTBEAT, 0.6F, 0.8F);
                }
                if (t == 90) cam(p, WIDE, 9, 2.5, 0, r.cur, -1);
                if (t == 170) { if (r.cur != null) r.cur.discard(); r.cur = null; ShadowEntity.ui(p, ShadowRegistry.STATIC, 0.6F, 1.3F); }
                if (t >= 200) next(r);
            }
            case 2 -> {                                   // II. it wears your friends
                if (t == 0) {
                    r.cur = ShadowEntity.mimicFor(p);
                    cam(p, WIDE, 11, 2.2, 0, r.cur, 2);
                }
                if (t == 110) cam(p, SHOULDER, 4, 1.2, -1.3, r.cur, -1);
                if (t == 190) {
                    if (r.cur != null && r.cur.isAlive()) { ShadowEntity.ui(p, ShadowRegistry.WHISPER, 0.7F, 0.7F); r.cur.discard(); }
                    r.cur = null;
                }
                if (t >= 220) next(r);
            }
            case 3 -> {                                   // III. don't look at it: the stare, the face splits
                if (t == 0) {
                    r.cur = ShadowEntity.spawnAround(p, 1, 0, 12);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); }
                    cam(p, SHOULDER, 3.2, 1.0, 1.1, r.cur, 3);
                }
                if (r.cur != null && t >= 40 && t < 170) r.cur.scriptStare(Math.min(ShadowEntity.STARE_LIMIT - 1, (t - 40) / 2));
                if (t >= 40 && t % Math.max(6, 22 - t / 10) == 0 && t < 170) ShadowEntity.ui(p, ShadowRegistry.HEARTBEAT, 1F, 0.85F + t * 0.002F);
                if (t == 110) cam(p, FACE, 3.6, 0.6, 0, r.cur, -1);
                if (t == 170 && r.cur != null) { r.cur.scriptStare(0); r.cur.startVanish(); }
                if (t >= 210) { r.cur = null; next(r); }
            }
            case 4 -> {                                   // IV. run: the turn, the crouch, the scuttle
                if (t == 0) {
                    r.cur = ShadowEntity.rushFrom(p, 24);
                    if (r.cur != null) r.cur.noHarm = true;
                    cam(p, WIDE, 15, 3.0, 0, r.cur, 4);
                }
                if (t == ShadowEntity.RUSH_STILL) cam(p, FACE, 4.2, 0.4, 0, r.cur, -1);
                if (t == ShadowEntity.RUSH_GO) cam(p, SHOULDER, 3.0, 0.7, 0.9, r.cur, -1);
                if ((r.cur == null || !r.cur.isAlive()) && t > 10) { r.cur = null; next(r); }
                if (t > 600) next(r);
            }
            case 5 -> {                                   // breathe after the jumpscare
                if (t >= 110) next(r);
            }
            case 6 -> {                                   // V. you should sleep: it's right behind you
                if (t == 0) {
                    r.cur = ShadowEntity.spawnAround(p, -1, 170, 2.6);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); }
                    cam(p, FACE, 3.4, 0.3, 0, r.cur, 5);
                    ShadowEntity.ui(p, ShadowRegistry.BREATH, 1F, 0.6F);
                }
                if (t == 70 && r.cur != null) r.cur.scriptStare(40);
                if (t == 100) ShadowEntity.ui(p, ShadowRegistry.WHISPER, 1F, 0.5F);
                if (t == 140 && r.cur != null) { r.cur.scriptStare(0); r.cur.startVanish(); }
                if (t >= 170) { r.cur = null; next(r); }
            }
            case 7 -> {                                   // VI. it wants a taste: lifted up to its face
                if (t == 0) {
                    r.cur = ShadowEntity.grab(p, ShadowEntity.GRAB_LICK);
                    cam(p, WIDE, 9, 2.0, 0, r.cur, 7);
                }
                if (t == 60) cam(p, ORBIT, 9, 6.5, 0.012, null, -1);
                if ((r.cur == null || !r.cur.isAlive()) && t > 10) { r.cur = null; next(r); }
                if (t > 400) next(r);
            }
            case 8 -> {                                   // ending: crane up over the black trails
                if (t == 0) { cam(p, CRANE, 3, 22, 0, null, 6); ShadowEntity.ui(p, ShadowRegistry.DRONE, 1F, 0.55F); }
                if (t >= 200) { stop(p); }
            }
            default -> stop(p);
        }
    }

    private ShadowCinematic() {}
}
