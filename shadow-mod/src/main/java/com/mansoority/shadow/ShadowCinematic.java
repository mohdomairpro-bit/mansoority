package com.mansoority.shadow;

import java.util.HashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * /shadow cinematic: a two-and-a-half minute short film starring whoever ran it.
 * every feature, in order, with its own camera work and title cards. nobody gets hurt.
 */
public final class ShadowCinematic {
    public static final int OFF = 0, ORBIT = 1, SHOULDER = 2, WIDE = 3, FACE = 4, CRANE = 5, POV = 6;
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
        if (r != null) {
            for (Entity e : EXTRA) if (e.isAlive()) e.discard();
            EXTRA.clear();
            for (ShadeEntity sh : p.serverLevel().getEntitiesOfClass(ShadeEntity.class, p.getBoundingBox().inflate(100))) sh.discard();
        }
        cam(p, OFF, 0, 0, 0, null, -1);
        if (r != null) {
            p.stopRiding();
            ShadowEntity.GRABBED.remove(p.getUUID());
            if (p.isInWall()) { Vec3 s = ShadowEntity.safeSpot(p.serverLevel(), p.position()); p.teleportTo(s.x, s.y, s.z); }
            p.fallDistance = 0;
            ShadowEntity.watchStuck(p);
        }
        if (r != null) p.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
    }

    /** the boss fight's own camera moves (the intro), outside of /shadow cinematic */
    public static void camFor(ServerPlayer p, int shot, double a, double b, double c, ShadowEntity focus, int chapter) { cam(p, shot, a, b, c, focus, chapter); }

    private static void cam(ServerPlayer p, int shot, double a, double b, double c, ShadowEntity focus, int chapter) {
        ShadowNet.send(p, new FxPacket(FxPacket.CAM, shot, a, b, c, focus == null ? -1 : focus.getId(), chapter));
    }

    private static void next(Run r) { r.phase++; r.phaseT = 0; }

    /** mountains, cliffs, water: if the planned spot doesn't work, try every direction and closer distances */
    private static ShadowEntity spawn(ServerPlayer p, int stage, double angle, double dist) {
        for (double f : new double[]{1.0, 0.7, 0.45})
            for (int k = 0; k < 12; k++) {
                double a = angle + (k % 2 == 0 ? 1 : -1) * ((k + 1) / 2) * 30;
                ShadowEntity e = ShadowEntity.spawnAround(p, stage, a, Math.max(2.5, dist * f));
                if (e != null) return e;
            }
        return null;
    }

    private static final int END_CARD = 11;
    private static final List<Entity> EXTRA = new java.util.ArrayList<>();

    public static void tick(ServerPlayer p) {
        Run r = RUNS.get(p.getUUID());
        if (r == null) return;
        if (!p.isAlive() || p.hasDisconnected()) { stop(p); return; }
        r.t++;
        int t = r.phaseT++;
        ServerLevel sl = p.serverLevel();
        // it's a showcase: you should be able to see what's happening, even at night
        if (r.t % 100 == 1) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 260, 0, false, false));
        switch (r.phase) {
            case 0 -> {                                   // opening: slow orbit, title card, the warning
                if (t == 0) { cam(p, ORBIT, 5.5, 1.7, 0.006, null, 0); ShadowEntity.ui(p, ShadowRegistry.DRONE, 1F, 0.7F); }
                if (t == 60) ShadowEntity.ui(p, ShadowRegistry.WHISPER, 0.8F, 0.55F);
                if (t == 110) ShadowEvents.warnAll(p);
                if (t >= 150) next(r);
            }
            case 1 -> {                                   // I. it watches: a glimpse from cover... and the hand that makes you look
                if (t == 0) {
                    r.cur = spawn(p, 0, 35, 26);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); }
                    cam(p, WIDE, 9, 2.5, 0, r.cur, 1);
                    ShadowEntity.ui(p, ShadowRegistry.FAR, 0.9F, 0.9F);
                }
                if (t == 80 && r.cur != null) r.cur.turnHead(p);
                if (t == 90) cam(p, SHOULDER, 3.6, 1.1, 1.3, r.cur, -1);
                if (t == 180) { if (r.cur != null) r.cur.startVanish(); }
                if (t >= 220) { r.cur = null; next(r); }
            }
            case 2 -> {                                   // II. something in the house: lights out, a door, a chest... and behind you
                if (t == 0) { cam(p, ORBIT, 4.5, 1.5, 0.010, null, 2); ShadowNet.send(p, FxPacket.of(FxPacket.POLTER)); }
                if (t == 120) { cam(p, FACE, -3.4, 0.2, 0, null, -1); ShadowNet.send(p, FxPacket.of(FxPacket.MIRROR)); }
                if (t >= 190) next(r);
            }
            case 3 -> {                                   // III. it wears your friends: then you get too close
                if (t == 0) { r.cur = ShadowEntity.mimicFor(p); cam(p, WIDE, 11, 2.2, 0, r.cur, 3); }
                if (t == 100) cam(p, SHOULDER, 4, 1.2, -1.3, r.cur, -1);
                if (t == 150 && r.cur != null && r.cur.isAlive()) { r.cur.reveal(p); cam(p, WIDE, 12, 3, 0, r.cur, -1); }
                if ((t > 160 && (r.cur == null || !r.cur.isAlive())) || t > 330) { if (r.cur != null) r.cur.discard(); r.cur = null; next(r); }
            }
            case 4 -> {                                   // IV. don't look at it: the ring, and every way it reacts to your stare
                if (t == 0) {
                    r.cur = spawn(p, 1, 0, 12);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); r.cur.scriptVariant(0); }
                    cam(p, SHOULDER, 3.2, 1.0, 1.1, r.cur, 4);
                }
                if (r.cur != null) {
                    int seg = (t - 30) / 75, in = (t - 30) % 75;           // four reactions, one after another
                    if (t >= 30 && seg < 4) {
                        if (in == 0) { r.cur.scriptVariant(seg); r.cur.scriptStare(0); }
                        r.cur.scriptStare(Math.min(ShadowEntity.STARE_LIMIT - 1, in));
                        if (in % 16 == 0) ShadowEntity.ui(p, ShadowRegistry.HEARTBEAT, 1F, 0.9F + seg * 0.05F);
                        if (in == 40) cam(p, seg % 2 == 0 ? FACE : WIDE, seg % 2 == 0 ? 3.6 : 10, seg % 2 == 0 ? 0.6 : 2.5, 0, r.cur, -1);
                    }
                    if (t == 30 + 4 * 75) { r.cur.scriptStare(0); r.cur.startVanish(); }
                }
                if (t >= 30 + 4 * 75 + 40) { r.cur = null; next(r); }
            }
            case 5 -> {                                   // V. run: the side-on rush, the fold, the freeze, the scuttle
                if (t == 0) {
                    r.cur = ShadowEntity.rushFrom(p, 24);
                    if (r.cur == null) { r.cur = spawn(p, 2, 0, 24); if (r.cur != null) r.cur.startRush(p); }
                    if (r.cur != null) r.cur.noHarm = true;
                    cam(p, WIDE, 15, 3.0, 0, r.cur, 5);
                }
                if (t == ShadowEntity.RUSH_STILL) cam(p, FACE, 4.2, 0.4, 0, r.cur, -1);
                if (t == ShadowEntity.RUSH_GO) cam(p, SHOULDER, 3.0, 0.7, 0.9, r.cur, -1);
                if ((r.cur == null || !r.cur.isAlive()) && t > 10) { r.cur = null; next(r); }
                if (t > 600) next(r);
            }
            case 6 -> {                                   // aftermath: the black trail, its mark on your back, your own death in chat
                if (t == 30) { ShadowTrails.behind(p); ShadowMark.mark(p); ShadowNet.send(p, FxPacket.of(FxPacket.DEATHMSG)); }
                if (t == 40) cam(p, FACE, 3.0, 0.3, 0, null, -1);        // behind you: the handprint
                if (t >= 170) next(r);
            }
            case 7 -> {                                   // VI. you should sleep
                if (t == 0) {
                    r.cur = spawn(p, -1, 170, 2.6);
                    if (r.cur != null) { r.cur.scripted = true; r.cur.reveal(); }
                    cam(p, FACE, 3.4, 0.3, 0, r.cur, 6);
                    ShadowEntity.ui(p, ShadowRegistry.BREATH, 1F, 0.6F);
                }
                if (t == 70 && r.cur != null) r.cur.scriptStare(40);
                if (t == 100) ShadowEntity.ui(p, ShadowRegistry.WHISPER, 1F, 0.5F);
                if (t == 140 && r.cur != null) { r.cur.scriptStare(0); r.cur.startVanish(); }
                if (t >= 180) { r.cur = null; next(r); }
            }
            case 8, 9 -> {                                // VII. it wants a taste / VIII. taken for a walk
                boolean lick = r.phase == 8;
                if (t == 0) {
                    r.cur = ShadowEntity.grab(p, lick ? ShadowEntity.GRAB_LICK : ShadowEntity.GRAB_DRAG);
                    cam(p, WIDE, 9, 2.0, 0, r.cur, lick ? 7 : 8);
                }
                if (t == 60) cam(p, lick ? ORBIT : WIDE, lick ? 9 : 12, lick ? 6.5 : 2.5, 0.012, lick ? null : r.cur, -1);
                if ((r.cur == null || !r.cur.isAlive()) && t > 10) { r.cur = null; next(r); }
                if (t > 400) next(r);
            }
            case 10 -> {                                  // IX. where it sleeps: its lair rises out of the ground (only on your screen)
                if (t == 0) {
                    Vec3 f = p.getLookAngle().multiply(1, 0, 1).normalize().scale(18);
                    BlockPos at = ShadowEntity.findStand(sl, p.getX() + f.x, p.getY(), p.getZ() + f.z, 20);
                    if (at == null) at = p.blockPosition().offset((int) f.x, 0, (int) f.z);
                    ShadowNet.send(p, new FxPacket(FxPacket.FAKE_LAIR, 20 * 12, at.getX() - 8, at.getY() - 1, at.getZ() - 8, 1, 1));
                    cam(p, CRANE, 6, 12, 0, null, 9);
                    ShadowEntity.ui(p, ShadowRegistry.DRONE, 1F, 0.5F);
                }
                if (t == 120) ShadowNet.send(p, FxPacket.of(FxPacket.POLTER));
                if (t >= 230) next(r);
            }
            case 11 -> {                                  // X. after the dragon: the boss, its shades, their black blades
                if (t == 0) {
                    BlockPos s0 = null;
                    for (int k = 0; k < 12 && s0 == null; k++) {
                        double a = Math.toRadians(p.getYRot() + k * 30), d = k < 6 ? 22 : 14;
                        s0 = ShadowEntity.findStand(sl, p.getX() - Math.sin(a) * d, p.getY(), p.getZ() + Math.cos(a) * d, 24);
                    }
                    MansoorityBoss b = s0 == null ? null : ShadowRegistry.BOSS.get().create(sl);
                    if (b != null) {
                        b.moveTo(s0.getX() + 0.5, s0.getY(), s0.getZ() + 0.5, 0, 0);
                        b.setHealth(MansoorityBoss.MAX_HP);
                        b.showcase = true;
                        sl.addFreshEntity(b);
                        EXTRA.add(b);
                        r.cur = b;
                    }
                    cam(p, WIDE, 16, 4, 0, r.cur, 10);
                }
                if (t == 110) cam(p, SHOULDER, 4.5, 1.2, 1.4, r.cur, -1);
                if (t == 220) cam(p, ORBIT, 10, 4, 0.01, null, -1);
                if (t >= 360) {
                    for (ShadeEntity sh : sl.getEntitiesOfClass(ShadeEntity.class, p.getBoundingBox().inflate(100))) sh.discard();
                    if (r.cur != null && r.cur.isAlive()) r.cur.startVanish();
                    EXTRA.clear();
                    r.cur = null; next(r);
                }
            }
            case 12 -> {                                  // the end: crane up over the black trails
                if (t == 0) { cam(p, CRANE, 3, 22, 0, null, END_CARD); ShadowEntity.ui(p, ShadowRegistry.DRONE, 1F, 0.55F); }
                if (t >= 200) stop(p);
            }
            default -> stop(p);
        }
    }

    private ShadowCinematic() {}
}
