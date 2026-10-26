package com.mansoority.shadow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.SpikeFeature;
import net.minecraft.world.phys.Vec3;

/**
 * Mansoority in the End. Three phases: (1) itself, (2) with the glowing blade clamped in its teeth, then it dies -
 * or seems to - and (3) something black and screaming tears out of the body with the blade through its own stomach.
 * In this fight, and only here, it can kill you. Kill five of you and it crawls back into its tower.
 */
public class MansoorityBoss extends ShadowEntity {
    public static final float MAX_HP = 1500F;
    /** what the body is doing right now (drives the animation on every client) */
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(MansoorityBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SEQ = SynchedEntityData.defineId(MansoorityBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(MansoorityBoss.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FORM = SynchedEntityData.defineId(MansoorityBoss.class, EntityDataSerializers.INT);

    public static final int A_NONE = 0, A_ROAR = 1, A_SCREAM = 2, A_BITE = 3, A_CHEW = 4, A_SLAM = 5, A_SPRAY = 6, A_SPIKES = 7,
            A_STUN = 8, A_COLLAPSE = 9, A_DEAD = 10, A_RISE = 11, A_BLADE = 12, A_THROW = 13, A_INTRO = 14, A_GRAB = 15,
            A_CHARGE = 16, A_CLIMB = 17, A_SIGNATURE = 18, A_BLINK = 19, A_STEAL = 20, A_RETREAT = 21, A_FETCH = 22;
    /** form flags */
    public static final int F_BLADE = 1, F_BLACK = 2, F_JAW = 4, F_BELLY = 8, F_WALL = 16, F_FAKEDEAD = 32, F_INTRO = 64, F_BLADE_OUT = 128;

    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Mansoority").withStyle(net.minecraft.ChatFormatting.DARK_RED),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);
    private int t, actionT, attackCd = 120, alone, dying = -1, introT = -1, stealT = -1;
    private int floodCd = 300, collapseCd = 200, eyesCd = 240, signatureCd = 900, spikesCd = 300, stunCd = 0, tauntCd = 0;
    private boolean fakeDone;
    private ServerPlayer focus;
    /** cinematic version: same fight, nothing is saved, it leaves when the scene ends */
    public boolean showcase;
    private Vec3 moveFrom, moveTo, chargeDir;
    private int grabbedId = -1;
    private EnderDragon prey;
    private SpikeFeature.EndSpike pillar;
    private int bladeId = -1;
    /** recent damage taken (tick, amount), for the darkness flood */
    private final List<float[]> recent = new ArrayList<>();
    /** crowding: how long each player has been too close */
    private final Map<UUID, Integer> close = new HashMap<>();
    /** shockwave rings currently spreading: centre, radius, who it already hit */
    private final List<Wave> waves = new ArrayList<>();
    private record Wave(Vec3 c, float[] r, float speed, float max, float dmg, java.util.Set<UUID> hit) {}
    /** players pulled into its place, and where to bring them back */
    public static final Map<UUID, Hollow> HOLLOW = new HashMap<>();
    public record Hollow(Vec3 back, long until, int bossId) {}
    private final Map<UUID, Integer> eyeStare = new HashMap<>();
    private final List<Vec3> pillarEyes = new ArrayList<>();
    private int pillarEyesUntil;

    public MansoorityBoss(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(false);
        bar.setDarkenScreen(true);
        bar.setCreateWorldFog(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 128.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, 0);
        this.entityData.define(DATA_ACTION_SEQ, 0);
        this.entityData.define(DATA_PHASE, 1);
        this.entityData.define(DATA_FORM, 0);
    }

    public int getAction() { return this.entityData.get(DATA_ACTION); }
    public int getActionSeq() { return this.entityData.get(DATA_ACTION_SEQ); }
    public int getPhase() { return this.entityData.get(DATA_PHASE); }
    public int getForm() { return this.entityData.get(DATA_FORM); }
    public boolean has(int flag) { return (getForm() & flag) != 0; }
    private void flag(int f, boolean on) { int v = getForm(); this.entityData.set(DATA_FORM, on ? v | f : v & ~f); }
    /** client: animation clock for the current action */
    public float clientActionStart; public int clientSeq = -1;
    /** client: how long since the current action started (ticks) */
    public float actionAge(float age) {
        if (clientSeq != getActionSeq()) { clientSeq = getActionSeq(); clientActionStart = age; }
        return age - clientActionStart;
    }

    private void act(int a) {
        actionT = 0;
        this.entityData.set(DATA_ACTION, a);
        this.entityData.set(DATA_ACTION_SEQ, this.entityData.get(DATA_ACTION_SEQ) + 1);
        flag(F_JAW, false); flag(F_BELLY, false);
    }

    public void beginIntro() { introT = 0; flag(F_INTRO, true); }
    /** came for the dragon: perched on top of a pillar, waiting for the last moment */
    public void beginSteal(EnderDragon d, SpikeFeature.EndSpike sp) {
        prey = d; pillar = sp; stealT = 0; flag(F_INTRO, true); this.setNoGravity(true); this.noPhysics = true;
    }
    public void restoreHp(float hp) { this.setHealth(Math.max(1F, Math.min(MAX_HP, hp))); }

    /** test hooks (CI screenshots and /shadow bossdebug): skip the intro, force a move, jump to a phase */
    public void debug(String what) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        List<ServerPlayer> near = sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(96), pl -> !pl.isSpectator() && pl.isAlive());
        if (near.isEmpty()) return;
        focus = near.get(0);
        switch (what) {
            case "skipintro" -> { introT = -1; flag(F_INTRO, false); act(A_NONE); for (ServerPlayer p : near) ShadowCinematic.camFor(p, ShadowCinematic.OFF, 0, 0, 0, null, -1); }
            case "phase2" -> { this.setHealth(MAX_HP * 0.55F); phaseTwo(sl); }
            case "phase3" -> { fakeDone = true; flag(F_BLADE, false); flag(F_BLACK, true); this.entityData.set(DATA_PHASE, 3); this.setHealth(MAX_HP * 0.4F); act(A_ROAR); }
            case "fakedeath" -> fakeDeath(sl);
            case "flood" -> flood(sl);
            case "signature" -> startSignature(sl);
            case "eyes" -> { eyesCd = 0; pillarEyesUntil = 0; pillarEyes.clear(); }
            default -> {
                if (what.startsWith("act:")) start(sl, Integer.parseInt(what.substring(4)), near);
            }
        }
        attackCd = 400;                                                         // hold still for the camera
    }

    // ================================================================ the brain
    @Override
    protected boolean customTick() {
        ServerLevel sl = (ServerLevel) this.level();
        t++; actionT++;
        setStageData(STAGE_BOSS);
        List<ServerPlayer> near = sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(96), pl -> !pl.isSpectator() && !pl.isCreative() && pl.isAlive());
        for (ServerPlayer pl : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(110))) bar.addPlayer(pl);
        for (ServerPlayer pl : List.copyOf(bar.getPlayers())) if (pl.distanceToSqr(this) > 130 * 130 || pl.level() != sl || !pl.isAlive()) bar.removePlayer(pl);
        bar.setProgress(Math.max(0F, this.getHealth() / MAX_HP));
        if (t % 20 == 0) {
            bar.setName(Component.literal(getPhase() == 3 ? "M A N S O O R I T Y" : "Mansoority").withStyle(net.minecraft.ChatFormatting.DARK_RED));
            bar.setColor(getPhase() == 3 ? BossEvent.BossBarColor.RED : getPhase() == 2 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.WHITE);
        }
        tickWaves(sl);
        tickHollow(sl);
        if (stealT >= 0) { stealTick(sl, near); return true; }
        if (dying >= 0) { dyingTick(sl); return true; }
        if (near.isEmpty()) { if (++alone > 20 * 45) leave(sl, false); return true; }
        alone = 0;
        if (focus == null || !focus.isAlive() || focus.isSpectator() || focus.isCreative() || focus.distanceToSqr(this) > 96 * 96 || (t % 240 == 0 && getAction() == A_NONE))
            focus = near.get(this.random.nextInt(near.size()));
        setTargetId(focus.getId());
        if (introT >= 0) { introTick(sl, near); return true; }
        reflectPearls(sl);
        crowding(near);
        if (tauntCd > 0) tauntCd--;
        if (stunCd > 0) stunCd--;
        if (getPhase() >= 2) pillarEyes(sl, near);
        if (getPhase() == 3 && --collapseCd <= 0) { collapseCd = 140 + this.random.nextInt(100); crumble(sl, near); }
        if (getPhase() == 3 && t % 60 == 0 && getAction() != A_DEAD) sound(ShadowRegistry.SCREECH, 3.5F, 0.7F + this.random.nextFloat() * 0.2F);   // it never stops screaming
        if (t % 100 == 0 && tauntCd <= 0) for (ServerPlayer p : near) if (p.getHealth() < 5F) { tauntCd = 500; sound(ShadowRegistry.V_TASTE, 5F, 1F); line(sl, 5); break; }
        if (getAction() != A_NONE) { actionTick(sl, near); return true; }
        // ------------------------------------------------ between attacks: circle and stalk, always facing its choice
        face(focus);
        flag(F_WALL, false);
        setScuttle(0);
        Vec3 c = centroid(near);
        double ang = t * (0.008 + getPhase() * 0.003) + this.getId();
        double r = 16 + 4 * Math.sin(t * 0.02);
        stepToward(sl, new Vec3(c.x + Math.cos(ang) * r, this.getY(), c.z + Math.sin(ang) * r), 0.11 + getPhase() * 0.035);
        if (floodCd > 0) floodCd--;
        if (signatureCd > 0) signatureCd--;
        if (spikesCd > 0) spikesCd--;
        if (--attackCd <= 0) chooseAttack(sl, near);
        return true;
    }

    private void chooseAttack(ServerLevel sl, List<ServerPlayer> near) {
        int ph = getPhase();
        attackCd = ph == 1 ? 70 : ph == 2 ? 50 : 34;
        double d = focus.distanceTo(this);
        if (ph == 3 && has(F_BLADE_OUT)) { act(A_FETCH); return; }               // first it wants its blade back
        if (signatureCd <= 0 && this.random.nextFloat() < 0.6F) { signatureCd = 1100 - ph * 150; startSignature(sl); return; }
        List<Integer> pool = new ArrayList<>();
        if (d < 11) { pool.add(A_BITE); pool.add(A_BITE); pool.add(A_GRAB); }
        if (d < 10 && ph == 2) { pool.add(A_BLADE); pool.add(A_BLADE); pool.add(A_BLADE); }
        if (d > 6 && d < 26) { pool.add(A_SLAM); pool.add(A_SPRAY); }
        if (d > 12) { pool.add(A_CHARGE); pool.add(A_BLINK); }
        if (ph == 3 && d > 7 && d < 30) { pool.add(A_THROW); pool.add(A_THROW); }
        pool.add(A_SCREAM);
        if (pillarNear(sl) != null && this.random.nextFloat() < 0.5F) pool.add(A_CLIMB);
        int a = pool.get(this.random.nextInt(pool.size()));
        start(sl, a, near);
    }

    private void start(ServerLevel sl, int a, List<ServerPlayer> near) {
        act(a);
        switch (a) {
            case A_BITE -> { sound(ShadowRegistry.BREATH, 3F, 0.5F); flag(F_JAW, true); }
            case A_SCREAM -> { flag(F_JAW, true); sound(ShadowRegistry.BREATH, 3F, 0.4F); }
            case A_SLAM -> { moveTo = focus.position(); telegraph(sl, moveTo, 5F, 44); sound(ShadowRegistry.CRACK, 3F, 0.6F); }
            case A_SPRAY -> sound(ShadowRegistry.GURGLE, 4F, 0.8F);
            case A_CHARGE -> { chargeDir = focus.position().subtract(this.position()).multiply(1, 0, 1).normalize(); sound(ShadowRegistry.SCREECH, 4F, 0.9F); }
            case A_CLIMB -> { pillar = pillarNear(sl); moveTo = null; moveFrom = null; sound(ShadowRegistry.CRACK, 3F, 0.8F); }
            case A_BLINK -> { moveTo = blinkSpot(sl, near); sound(ShadowRegistry.RISER, 4F, 0.6F); }
            case A_THROW, A_BLADE -> sound(ShadowRegistry.BREATH, 3F, 0.6F);
            default -> {}
        }
    }

    private void actionTick(ServerLevel sl, List<ServerPlayer> near) {
        int a = getAction(), k = actionT;
        switch (a) {
            case A_ROAR -> {                                     // phase change: roar, everyone feels it
                face(focus); flag(F_JAW, k > 8 && k < 60);
                if (k == 12) { sound(ShadowRegistry.ROAR, 6F, 0.9F); shake(near, 2.2F); }
                if (k >= 70) act(A_NONE);
            }
            case A_SCREAM -> {
                face(focus);
                if (k == 10) {
                    sound(ShadowRegistry.ROAR, 6F, 1.15F);
                    for (ServerPlayer p : near) {
                        if (p.distanceTo(this) > 22) continue;
                        hurt(p, 2F);
                        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 6, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 70, 3, false, false));
                        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 70, 200, false, false));     // can't jump either
                        ShadowNet.send(p, new FxPacket(FxPacket.KNOCKDOWN, 70, 0, 0, 0, 1, 1));
                    }
                }
                if (k >= 60) act(A_NONE);
            }
            case A_BITE -> biteTick(sl, k);
            case A_CHEW -> chewTick(sl, k);
            case A_SLAM -> slamTick(sl, k, near);
            case A_SPRAY -> sprayTick(sl, k, near);
            case A_SPIKES -> spikesTick(sl, k, near);
            case A_STUN -> { if (k >= 100) act(A_NONE); }
            case A_BLADE -> bladeTick(sl, k, near);
            case A_THROW -> throwTick(sl, k);
            case A_FETCH -> fetchTick(sl, k);
            case A_GRAB -> grabTick2(sl, k);
            case A_CHARGE -> chargeTick(sl, k, near);
            case A_CLIMB -> climbTick(sl, k, near);
            case A_SIGNATURE -> signatureTick(sl, k, near);
            case A_BLINK -> blinkTick(sl, k);
            case A_COLLAPSE -> { if (k >= 50) act(A_DEAD); }
            case A_DEAD -> fakeDeadTick(sl, k);
            case A_RISE -> riseTick(sl, k, near);
            case A_RETREAT -> retreatTick(sl, k);
            default -> act(A_NONE);
        }
    }

    // ================================================================ the attacks
    /** the head draws back, the neck snaps out: if it gets you, it eats you (for a while) */
    private void biteTick(ServerLevel sl, int k) {
        face(focus);
        if (k > 15 && k < 21) stepToward(sl, focus.position(), 0.55);          // the lunge
        if (k == 20) {
            sound(ShadowRegistry.CRUNCH, 4F, 0.8F);
            if (focus.distanceTo(this) < 9.5 && !focus.isPassenger()) {
                grabbedId = focus.getId();
                GRABBED.add(focus.getUUID());
                focus.startRiding(this, true);
                act(A_CHEW); flag(F_JAW, true);
                ShadowEntity.ui(focus, ShadowRegistry.HIT, 1F, 0.7F);
                return;
            }
        }
        if (k >= 46) act(A_NONE);
    }

    private void chewTick(ServerLevel sl, int k) {
        Entity e = sl.getEntity(grabbedId);
        if (!(e instanceof ServerPlayer p) || !p.isAlive()) { release(sl, null); act(A_NONE); return; }
        if (!p.isPassenger()) p.startRiding(this, true);
        flag(F_JAW, true);
        if (k % 20 == 10) {                                                     // three crunches
            hurt(p, 5F);
            sound(ShadowRegistry.CRUNCH, 4F, 0.7F + this.random.nextFloat() * 0.3F);
            sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            sl.sendParticles(ShadowRegistry.DROOL.get(), p.getX(), p.getY() + 1, p.getZ(), 8, 0.3, 0.2, 0.3, 0);
            ShadowNet.send(p, FxPacket.of(FxPacket.LICK));
        }
        if (k >= 66) {                                                          // spat out
            release(sl, p);
            double yaw = Math.toRadians(this.getYRot());
            p.setDeltaMovement(-Math.sin(yaw) * 1.4, 0.7, Math.cos(yaw) * 1.4);
            p.hurtMarked = true;
            act(A_NONE);
        }
    }

    private void release(ServerLevel sl, ServerPlayer p) {
        for (Entity pass : List.copyOf(this.getPassengers())) {
            pass.stopRiding(); GRABBED.remove(pass.getUUID());
            if (pass instanceof ServerPlayer sp) { NOFALL.put(sp.getUUID(), sl.getGameTime() + 100); ShadowEntity.watchStuck(sp); }
        }
        if (p != null) GRABBED.remove(p.getUUID());
        grabbedId = -1;
    }

    /** where someone in its mouth hangs: right in front of the face, so you see all of it */
    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (getAction() == A_CHEW) {
            double yaw = Math.toRadians(this.yBodyRot);
            double fwd = 7.4, up = 5.2 + Math.sin(actionT * 0.5) * 0.3;
            move.accept(passenger, this.getX() - Math.sin(yaw) * fwd, this.getY() + up, this.getZ() + Math.cos(yaw) * fwd);
        } else super.positionRider(passenger, move);
    }

    /** crouch, leap, crash down. then a ring of force rolls out across the ground: jump it */
    private void slamTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (k < 22) { face(focus); if (k == 16) moveTo = focus.position(); return; }
        if (k == 22) { this.noPhysics = true; this.setNoGravity(true); sound(ShadowRegistry.ROAR, 4F, 1.2F); moveFrom = this.position(); }
        if (k >= 22 && k <= 44 && moveFrom != null && moveTo != null) {
            double u = (k - 22) / 22.0;
            Vec3 p = moveFrom.lerp(moveTo, u);
            BlockPos g = findStand(sl, p.x, p.y, p.z, 10);
            double gy = g != null ? g.getY() : moveTo.y;
            this.setPos(p.x, Mth.lerp(u, moveFrom.y, gy) + Math.sin(u * Math.PI) * 9.0, p.z);
            face(focus);
        }
        if (k == 45) impact(sl, near, 6F, 10F, 22F);
        if (k >= 80) act(A_NONE);
    }

    private void impact(ServerLevel sl, List<ServerPlayer> near, float radius, float dmg, float waveMax) {
        this.noPhysics = false; this.setNoGravity(false);
        sound(ShadowRegistry.SLAM, 6F, 0.9F);
        shake(near, 3F);
        for (ServerPlayer p : near) if (p.distanceTo(this) < radius) { hurt(p, dmg); fling(p, 1.2, 0.8); }
        wave(sl, this.position(), 0.5F, waveMax, 6F);
        sl.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 0.5, this.getZ(), 3, 1.5, 0.2, 1.5, 0);
    }

    /** the stomach splits open and empties itself in a cone at you */
    private void sprayTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (k < 70) face(focus);
        flag(F_BELLY, k >= 14 && k < 76);
        if (k == 20) { sound(ShadowRegistry.SPRAY, 5F, 0.9F); for (ServerPlayer p : near) ShadowNet.send(p, new FxPacket(FxPacket.SPRAY, 50, 0, 0, 0, this.getId(), 1)); }
        if (k >= 22 && k < 72 && k % 8 == 0) {
            Vec3 dir = Vec3.directionFromRotation(0, this.getYRot());
            Vec3 from = this.position().add(dir.scale(2.2)).add(0, 6.5, 0);
            for (ServerPlayer p : near) {
                Vec3 to = p.position().add(0, 1, 0).subtract(from);
                if (to.length() > 18) continue;
                if (to.normalize().dot(new Vec3(dir.x, -0.35, dir.z).normalize()) < 0.82) continue;
                hurt(p, 3F);
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, false, true));
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, false));
                ShadowNet.send(p, FxPacket.of(FxPacket.LICK));
            }
        }
        if (k >= 90) act(A_NONE);
    }

    /** hands driven into the ground: lines of bone burst up toward everyone crowding it */
    private void spikesTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        face(focus);
        if (k == 22) { sound(ShadowRegistry.SPIKES, 5F, 0.8F); shake(near, 1.4F); }
        if (k >= 22 && k < 52 && (k - 22) % 3 == 0) {
            int step = (k - 22) / 3;
            for (ServerPlayer p : near) {
                if (p.distanceTo(this) > 26) continue;
                Vec3 dir = p.position().subtract(this.position()).multiply(1, 0, 1);
                double len = dir.length(); if (len < 0.1) continue;
                double at = 3.0 + step * 1.6;
                if (at > len + 2) continue;
                Vec3 s = this.position().add(dir.scale(at / len));
                BlockPos g = findStand(sl, s.x, this.getY(), s.z, 8);
                if (g == null) continue;
                BoneSpike sp = ShadowRegistry.SPIKE.get().create(sl);
                if (sp == null) continue;
                sp.bossId = this.getId();
                sp.moveTo(s.x, g.getY(), s.z, this.random.nextFloat() * 360F, 0);
                sl.addFreshEntity(sp);
            }
        }
        if (k >= 60) act(A_NONE);
    }

    /** phase two: the blade in its teeth, whipped across everything in front of it */
    private void bladeTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (k < 13) face(focus);
        if (k < 14) stepToward(sl, focus.position(), 0.2);
        if (k == 15) sound(ShadowRegistry.SCREECH, 3F, 1.4F);
        if (k == 17) {
            Vec3 dir = Vec3.directionFromRotation(0, this.getYRot());
            for (ServerPlayer p : near) {
                Vec3 to = p.position().subtract(this.position()).multiply(1, 0, 1);
                if (to.length() < 10.5 && to.normalize().dot(dir) > 0.1) {
                    hurt(p, 9F); fling(p, 1.0, 0.45);
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
                    sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2F, 0.6F);
                }
            }
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, this.getX() + dir.x * 5, this.getY() + 4, this.getZ() + dir.z * 5, 6, 2.5, 0.5, 2.5, 0);
        }
        if (k >= 36) act(A_NONE);
    }

    /** phase three: tears the blade out of its own stomach and throws it */
    private void throwTick(ServerLevel sl, int k) {
        face(focus);
        if (k == 6) sound(ShadowRegistry.CRUNCH, 4F, 0.5F);
        if (k == 20) {
            ThrownBlade b = ShadowRegistry.THROWN_BLADE.get().create(sl);
            if (b != null) {
                Vec3 from = this.position().add(0, 5, 0);
                b.bossId = this.getId();
                b.launch(from, focus.position().add(0, 1, 0).subtract(from));
                sl.addFreshEntity(b);
                bladeId = b.getId();
                flag(F_BLADE_OUT, true);
                sound(ShadowRegistry.SCREECH, 4F, 1.2F);
            }
        }
        if (k >= 40) act(A_NONE);
    }

    /** ...and goes to get it back */
    private void fetchTick(ServerLevel sl, int k) {
        Entity b = sl.getEntity(bladeId);
        if (!(b instanceof ThrownBlade tb) || !tb.isAlive()) { flag(F_BLADE_OUT, false); act(A_NONE); return; }
        if (!tb.stuck()) return;
        setScuttle(k + 8);
        Vec3 d = tb.position().subtract(this.position()).multiply(1, 0, 1);
        float yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
        stepToward(sl, tb.position(), 0.42);
        if (d.length() < 3.5) {
            tb.discard(); bladeId = -1; flag(F_BLADE_OUT, false); setScuttle(0);
            sound(ShadowRegistry.CRUNCH, 4F, 0.6F);
            act(A_ROAR);
            return;
        }
        if (k > 400) { tb.discard(); flag(F_BLADE_OUT, false); setScuttle(0); act(A_NONE); }
    }

    /** picks you up to its face... and throws you */
    private void grabTick2(ServerLevel sl, int k) {
        face(focus);
        setGrab(k, GRAB_LICK);
        if (k == 22) {
            if (focus.distanceTo(this) > 11) { setGrab(0, 0); act(A_NONE); return; }
            GRABBED.add(focus.getUUID());
            focus.startRiding(this, true);
            grabbedId = focus.getId();
            ShadowEntity.ui(focus, ShadowRegistry.HIT, 1F, 0.8F);
        }
        if (k > 22 && k < 64 && !focus.isPassenger() && grabbedId == focus.getId()) focus.startRiding(this, true);
        if (k == 64) {
            ServerPlayer f = focus;
            release(sl, f);
            double yaw = Math.toRadians(this.getYRot() + (this.random.nextBoolean() ? 70 : -70));
            f.setDeltaMovement(-Math.sin(yaw) * 1.8, 0.9, Math.cos(yaw) * 1.8);
            f.hurtMarked = true;
            hurt(f, 6F);
            sound(ShadowRegistry.ROAR, 4F, 1.2F);
            setGrab(0, 0);
            act(A_NONE);
        }
    }

    private void chargeTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        setScuttle(k + 8);
        if (k < 20) { face(focus); chargeDir = focus.position().subtract(this.position()).multiply(1, 0, 1).normalize(); return; }
        stepToward(sl, this.position().add(chargeDir.scale(4)), 0.5 + getPhase() * 0.08);
        float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
        boolean hit = false;
        for (ServerPlayer pl : near) if (pl.distanceTo(this) < 4.2) { hurt(pl, 8F + getPhase() * 2F); fling(pl, 1.6, 0.6); hit = true; }
        if (hit || k > 60) { setScuttle(0); act(A_NONE); }
    }

    /** up the side of a pillar, a long wait at the top... then it drops on you */
    private void climbTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (pillar == null) { act(A_NONE); return; }
        Vec3 c = new Vec3(pillar.getCenterX() + 0.5, 0, pillar.getCenterZ() + 0.5);
        Vec3 out = new Vec3(this.getX() - c.x, 0, this.getZ() - c.z);
        if (out.lengthSqr() < 0.01) out = new Vec3(1, 0, 0);
        out = out.normalize();
        Vec3 wall = c.add(out.scale(pillar.getRadius() + 1.6));
        int top = pillar.getHeight();
        if (!has(F_WALL) && moveFrom == null) {                               // 1) run to the foot of the pillar
            setScuttle(k + 8);
            Vec3 d = wall.subtract(this.position()).multiply(1, 0, 1);
            if (d.length() > 1.5) {
                stepToward(sl, new Vec3(wall.x, this.getY(), wall.z), 0.45);
                float yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
                this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
                if (k > 200) act(A_NONE);
                return;
            }
            flag(F_WALL, true); this.noPhysics = true; this.setNoGravity(true); actionT = 0;
            float yaw = (float) (Mth.atan2(-out.z, -out.x) * (180F / Math.PI)) - 90F;   // facing the stone
            this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
            sound(ShadowRegistry.CRACK, 4F, 0.7F);
            return;
        }
        if (has(F_WALL) && moveFrom == null) {
            if (this.getY() < top - 6 && k < 300) {                          // 2) climbing
                setScuttle(k + 8);
                this.setPos(wall.x, this.getY() + 0.32, wall.z);
                if (k % 6 == 0) sound(ShadowRegistry.CRACK, 2.5F, 1.3F);
                return;
            }
            setScuttle(0);
            moveFrom = this.position(); actionT = 0;                          // 3) hanging there, looking at you
            sound(ShadowRegistry.ROAR, 6F, 0.8F);
            telegraph(sl, focus.position(), 5F, 56);
            return;
        }
        if (has(F_WALL)) {
            if (k == 38) moveTo = focus.position();
            if (k < 40) return;
            flag(F_WALL, false); moveFrom = this.position(); actionT = 0;   // 4) the drop
            if (moveTo == null) moveTo = focus.position();
            sound(ShadowRegistry.SCREECH, 5F, 0.9F);
            return;
        }
        double u = Math.min(1.0, k / 16.0);
        Vec3 p = moveFrom.lerp(moveTo, u);
        this.setPos(p.x, Mth.lerp(u * u, moveFrom.y, moveTo.y), p.z);
        face(focus);
        if (u >= 1.0) {
            impact(sl, near, 6.5F, 12F, 18F);
            moveTo = null; moveFrom = null;
            act(A_SLAM); actionT = 46;                                       // finish on the slam's recovery
        }
    }

    /**
     * its signature, "the Unmaking": a roar that drags everyone in, the lights die, and then it is behind whoever it chose
     * and both hands come down. the most damage it can do. there's a circle on the ground: be out of it.
     */
    private void startSignature(ServerLevel sl) {
        act(A_SIGNATURE);
        sound(ShadowRegistry.V_KNEEL, 6F, 1F);
        line(sl, 2);
    }

    private void signatureTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (k < 30) { face(focus); flag(F_JAW, true); }
        if (k == 8) { sound(ShadowRegistry.ROAR, 7F, 0.75F); shake(near, 2.5F); }
        if (k > 8 && k < 30) for (ServerPlayer p : near) {                       // everyone is dragged toward it
            Vec3 pull = this.position().subtract(p.position()).multiply(1, 0, 1);
            if (pull.length() > 5) { p.setDeltaMovement(p.getDeltaMovement().add(pull.normalize().scale(0.09))); p.hurtMarked = true; }
        }
        if (k == 30) {
            flag(F_JAW, false);
            for (ServerPlayer p : near) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            moveTo = focus.position();
            telegraph(sl, moveTo, 5.5F, 36);
            sound(ShadowRegistry.RISER, 5F, 0.5F);
        }
        if (k > 30 && k <= 47) setVanishTick(k - 30);
        if (k == 47) {                                                           // behind them
            Vec3 back = Vec3.directionFromRotation(0, focus.getYRot()).scale(-4.5);
            BlockPos g = findStand(sl, moveTo.x + back.x, moveTo.y, moveTo.z + back.z, 10);
            if (g != null) this.teleportTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            face(focus);
        }
        if (k > 47 && k < 64) setVanishTick(Math.max(0, 17 - (k - 47)));
        if (k == 64) setVanishTick(0);
        if (k == 72) {                                                           // both hands come down
            sound(ShadowRegistry.SLAM, 7F, 0.7F); sound(ShadowRegistry.ROAR, 6F, 0.6F);
            shake(near, 3.5F);
            for (ServerPlayer p : near) if (p.position().distanceTo(moveTo) < 5.5 || p.distanceTo(this) < 5) { hurt(p, 18F); fling(p, 1.5, 1.0); }
            wave(sl, moveTo, 0.6F, 20F, 7F);
        }
        if (k >= 100) act(A_NONE);
    }

    private void blinkTick(ServerLevel sl, int k) {
        setVanishTick(k <= 17 ? k : Math.max(0, 34 - k));
        if (k == 17 && moveTo != null) { this.teleportTo(moveTo.x, moveTo.y, moveTo.z); face(focus); }
        if (k >= 34) { setVanishTick(0); act(A_NONE); summon(sl, getPhase()); }
    }

    private Vec3 blinkSpot(ServerLevel sl, List<ServerPlayer> near) {
        if (near.isEmpty()) return this.position();
        Vec3 c = centroid(near);
        for (int i = 0; i < 10; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            BlockPos s = findStand(sl, c.x + Math.cos(a) * 12, c.y, c.z + Math.sin(a) * 12, 20);
            if (s != null) return new Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        }
        return this.position();
    }

    private void summon(ServerLevel sl, int n) {
        int alive = sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(80)).size();
        for (int i = 0; i < n && alive + i < 6; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            Vec3 c = focus.position();
            BlockPos s = findStand(sl, c.x + Math.cos(a) * 8, c.y, c.z + Math.sin(a) * 8, 10);
            if (s == null) continue;
            ShadeEntity sh = ShadowRegistry.SHADE.get().create(sl);
            if (sh == null) continue;
            sh.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, this.random.nextFloat() * 360F, 0);
            sh.arm();
            sh.setTarget(focus);
            sl.addFreshEntity(sh);
            sl.sendParticles(ShadowRegistry.WISP.get(), sh.getX(), sh.getY() + 1, sh.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
        }
    }

    // ================================================================ arena effects
    private void wave(ServerLevel sl, Vec3 c, float speed, float max, float dmg) {
        waves.add(new Wave(c, new float[]{2F}, speed, max, dmg, new java.util.HashSet<>()));
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(80)))
            ShadowNet.send(p, new FxPacket(FxPacket.RING_WAVE, 0, c.x, c.y, c.z, speed, max));
    }

    private void tickWaves(ServerLevel sl) {
        for (int i = waves.size() - 1; i >= 0; i--) {
            Wave w = waves.get(i);
            w.r()[0] += w.speed();
            float r = w.r()[0];
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(w.c(), w.c()).inflate(r + 2, 4, r + 2))) {
                double d = Math.hypot(p.getX() - w.c().x, p.getZ() - w.c().z);
                if (Math.abs(d - r) < 1.0 && p.onGround() && Math.abs(p.getY() - w.c().y) < 3 && w.hit().add(p.getUUID())) {   // jump over it
                    hurt(p, w.dmg()); fling(p, 0.9, 0.7);
                }
            }
            if (r > w.max()) waves.remove(i);
        }
    }

    private void telegraph(ServerLevel sl, Vec3 at, float radius, int ticks) {
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(90)))
            ShadowNet.send(p, new FxPacket(FxPacket.TELEGRAPH, ticks, at.x, at.y, at.z, radius, 1));
    }

    private void shake(List<ServerPlayer> ps, float amt) {
        for (ServerPlayer p : ps) {
            float k = (float) Math.max(0.15, 1 - p.distanceTo(this) / 50.0);
            ShadowNet.send(p, new FxPacket(FxPacket.SHAKE, 0, 0, 0, 0, amt * k, 1));
        }
    }

    /** one of its lines across everyone's screen (see ClientFx.LINES) */
    public void line(ServerLevel sl, int index) {
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(120)))
            ShadowNet.send(p, new FxPacket(FxPacket.LINE, index, 0, 0, 0, 70, 1));
    }

    /** too many of you in its face, or one of you too long: bone spikes */
    private void crowding(List<ServerPlayer> near) {
        int crowd = 0;
        boolean camper = false;
        for (ServerPlayer p : near) {
            boolean in = p.distanceTo(this) < 9;
            if (in) crowd++;
            int c = in ? close.getOrDefault(p.getUUID(), 0) + 1 : 0;
            close.put(p.getUUID(), c);
            if (c > 20 * 8) camper = true;
        }
        if (getAction() == A_NONE && spikesCd <= 0 && (crowd >= 3 || camper)) {
            spikesCd = 360;
            close.clear();
            act(A_SPIKES);
        }
    }

    /** thrown ender pearls get caught and flung straight back at whoever threw them */
    private void reflectPearls(ServerLevel sl) {
        for (ThrownEnderpearl pearl : sl.getEntitiesOfClass(ThrownEnderpearl.class, this.getBoundingBox().inflate(9))) {
            if (pearl.getTags().contains("theshadow_caught") || !(pearl.getOwner() instanceof ServerPlayer owner)) continue;
            pearl.addTag("theshadow_caught");
            Vec3 back = owner.getEyePosition().subtract(pearl.position());
            pearl.setDeltaMovement(back.normalize().scale(1.7));
            pearl.hurtMarked = true;
            sound(ShadowRegistry.SNAP, 3F, 1.5F);
            if (tauntCd <= 0) { tauntCd = 400; sound(ShadowRegistry.V_SLOW, 5F, 1F); line(sl, 6); }
        }
    }

    /** phase three: chunks of the island crack, crumble and drop into the void (it all comes back after the fight) */
    private void crumble(ServerLevel sl, List<ServerPlayer> near) {
        if (showcase || sl.dimension() != Level.END || !ShadowConfig.COLLAPSE.get()) return;
        for (int tries = 0; tries < 12; tries++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 38 + this.random.nextDouble() * 45;
            int cx = Mth.floor(Math.cos(a) * r), cz = Mth.floor(Math.sin(a) * r);
            boolean safe = true;
            for (ServerPlayer p : near) if (Math.hypot(p.getX() - cx, p.getZ() - cz) < 7) safe = false;
            if (!safe) continue;
            int top = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, cx, cz);
            if (top < 40) continue;
            BossData d = BossData.get(sl.getServer());
            int n = 0;
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 10 + this.random.nextInt(4)) continue;
                for (int dy = 0; dy < 7; dy++) {
                    BlockPos q = new BlockPos(cx + dx, top - 1 - dy, cz + dz);
                    var st = sl.getBlockState(q);
                    if (!st.is(Blocks.END_STONE)) continue;
                    d.remember(q, st);
                    if (dy < 2 && this.random.nextFloat() < 0.5F) {
                        var fb = net.minecraft.world.entity.item.FallingBlockEntity.fall(sl, q, st);
                        fb.dropItem = false;
                    } else sl.setBlock(q, Blocks.AIR.defaultBlockState(), 3);
                    n++;
                }
            }
            if (n > 0) {
                sl.playSound(null, new BlockPos(cx, top, cz), ShadowRegistry.SLAM.get(), SoundSource.HOSTILE, 4F, 0.6F);
                sl.sendParticles(ParticleTypes.EXPLOSION, cx, top, cz, 4, 2, 0.5, 2, 0);
                return;
            }
        }
    }

    /**
     * phase two onward: its eyes open in the pillars. stare into them for three seconds and you go blind,
     * and wake up somewhere else - its place - for thirty seconds, with it.
     */
    private void pillarEyes(ServerLevel sl, List<ServerPlayer> near) {
        if (showcase || sl.dimension() != Level.END) return;
        if (pillarEyes.isEmpty() || t > pillarEyesUntil) {
            pillarEyes.clear();
            if (--eyesCd > 0) return;
            eyesCd = 500 + this.random.nextInt(400);
            List<SpikeFeature.EndSpike> spikes = SpikeFeature.getSpikesForLevel(sl);
            for (int i = 0; i < 3 && !spikes.isEmpty(); i++) {
                SpikeFeature.EndSpike sp = spikes.get(this.random.nextInt(spikes.size()));
                Vec3 c = new Vec3(sp.getCenterX() + 0.5, sp.getHeight() * (0.55 + this.random.nextDouble() * 0.3), sp.getCenterZ() + 0.5);
                Vec3 toMid = new Vec3(-c.x, 0, -c.z);
                toMid = toMid.lengthSqr() < 1 ? new Vec3(1, 0, 0) : toMid.normalize();
                Vec3 eye = c.add(toMid.scale(sp.getRadius() + 0.55));
                pillarEyes.add(eye);
                float yaw = (float) (Mth.atan2(toMid.z, toMid.x) * (180F / Math.PI)) - 90F;
                for (ServerPlayer p : near) ShadowNet.send(p, new FxPacket(FxPacket.PILLAR_EYES, 400, eye.x, eye.y, eye.z, yaw, 1));
            }
            pillarEyesUntil = t + 400;
            eyeStare.clear();
            return;
        }
        for (ServerPlayer p : near) {
            boolean staring = false;
            for (Vec3 eye : pillarEyes) {
                Vec3 to = eye.subtract(p.getEyePosition());
                if (to.length() < 90 && to.normalize().dot(p.getViewVector(1F)) > 0.995) staring = true;
            }
            int s = staring ? eyeStare.getOrDefault(p.getUUID(), 0) + 1 : Math.max(0, eyeStare.getOrDefault(p.getUUID(), 0) - 2);
            eyeStare.put(p.getUUID(), s);
            if (s == 20) ShadowEntity.ui(p, ShadowRegistry.HEARTBEAT, 1F, 1.2F);
            if (s >= 60 && !HOLLOW.containsKey(p.getUUID())) { eyeStare.put(p.getUUID(), 0); takeAway(sl, p); }
        }
    }

    private void takeAway(ServerLevel sl, ServerPlayer p) {
        ServerLevel hollow = sl.getServer().getLevel(HollowPlace.LEVEL);
        if (hollow == null) return;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        ShadowNet.send(p, FxPacket.of(FxPacket.JUMPSCARE_NOW));
        HOLLOW.put(p.getUUID(), new Hollow(p.position(), sl.getGameTime() + 20 * 30, this.getId()));
        HollowPlace.bring(hollow, p);
        ShadowNet.send(p, new FxPacket(FxPacket.HOLLOW, 20 * 30, 0, 0, 0, 1, 1));
    }

    private void tickHollow(ServerLevel sl) {
        if (HOLLOW.isEmpty()) return;
        for (var e : List.copyOf(HOLLOW.entrySet())) {
            if (e.getValue().bossId() != this.getId()) continue;
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(e.getKey());
            if (p == null) { HOLLOW.remove(e.getKey()); continue; }
            if (sl.getGameTime() >= e.getValue().until() || !p.isAlive()) {
                HOLLOW.remove(e.getKey());
                if (p.isAlive() && HollowPlace.in(p)) {
                    Vec3 b = e.getValue().back();
                    p.teleportTo(sl, b.x, b.y, b.z, p.getYRot(), p.getXRot());
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false));
                    ShadowEntity.watchStuck(p);
                    HollowPlace.leave(p);
                }
            }
        }
    }

    // ================================================================ the story beats
    /** perched on a pillar while you fight the dragon. at the last moment it jumps on it and takes the kill */
    private void stealTick(ServerLevel sl, List<ServerPlayer> near) {
        stealT++;
        if (stealT < 60) {                                                    // waiting on top of the pillar
            if (prey == null || !prey.isAlive()) { stealT = -1; this.noPhysics = false; this.setNoGravity(false); beginIntro(); return; }
            if (pillar != null) this.setPos(pillar.getCenterX() + 0.5, pillar.getHeight() + 1, pillar.getCenterZ() + 0.5);
            face(prey);
            if (stealT == 2) for (ServerPlayer p : near) ShadowNet.send(p, new FxPacket(FxPacket.LINE, 7, 0, 0, 0, 60, 1));
            if (prey.getHealth() > prey.getMaxHealth() * 0.06F && stealT == 59) stealT = 58;   // wait for the very end
            return;
        }
        if (stealT == 60) { moveFrom = this.position(); sound(ShadowRegistry.SCREECH, 8F, 0.8F); act(A_SLAM); actionT = 22; }
        if (stealT < 77 && prey != null && prey.isAlive()) {                  // the leap
            double u = (stealT - 60) / 16.0;
            Vec3 p = moveFrom.lerp(prey.position(), u);
            this.setPos(p.x, p.y + Math.sin(u * Math.PI) * 6, p.z);
            face(prey);
            return;
        }
        if (stealT == 77) {                                                   // it bites the dragon's head off
            sound(ShadowRegistry.CRUNCH, 8F, 0.5F);
            sound(ShadowRegistry.ROAR, 8F, 0.7F);
            if (prey != null && prey.isAlive()) {
                prey.hurt(prey.head, this.damageSources().mobAttack(this), 10000F);
                if (prey.isAlive()) prey.setHealth(0F);
            }
            shake(near, 3F);
            this.noPhysics = false; this.setNoGravity(false);
            act(A_NONE);
            return;
        }
        if (stealT > 77 && (this.onGround() || stealT > 300)) {               // landed on the island: the introduction
            stealT = -1;
            sound(ShadowRegistry.SLAM, 7F, 0.7F);
            beginIntro();
        }
    }

    /**
     * the introduction: every camera on it. it walks right up to one of you, puts its face in yours and talks.
     * nobody can be hurt while it does.
     */
    private void introTick(ServerLevel sl, List<ServerPlayer> near) {
        introT++;
        for (ServerPlayer p : near) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 10, false, false));
        if (introT == 1) {
            act(A_ROAR);
            for (ServerPlayer p : near) ShadowCinematic.camFor(p, ShadowCinematic.WIDE, 16, 5, 0, this, -1);
        }
        if (introT < 75) { if (getAction() == A_ROAR) actionTick(sl, near); return; }
        if (getAction() != A_INTRO && getAction() != A_ROAR) {
            if (focus.distanceTo(this) > 8.5 && introT < 220) {                // walks right up to you
                face(focus); stepToward(sl, focus.position(), 0.16);
                return;
            }
            act(A_INTRO);
            for (ServerPlayer p : near) {
                if (p == focus) ShadowCinematic.camFor(p, ShadowCinematic.POV, 0.3, 6.4, 0, this, -1);
                else ShadowCinematic.camFor(p, ShadowCinematic.SHOULDER, 4.5, 1.2, 1.4, this, -1);
            }
            return;
        }
        if (getAction() == A_INTRO) {
            face(focus);
            int k = actionT;
            if (k == 20) { sound(ShadowRegistry.V_DESTROY, 6F, 1F); line(sl, 0); flag(F_JAW, true); }
            if (k == 64) flag(F_JAW, false);
            if (k == 90) { sound(ShadowRegistry.V_MINE, 6F, 1F); line(sl, 1); flag(F_JAW, true); }
            if (k == 150) {
                act(A_ROAR);
                for (ServerPlayer p : near) ShadowCinematic.camFor(p, ShadowCinematic.WIDE, 18, 6, 0, this, -1);
            }
            return;
        }
        actionTick(sl, near);                                                 // the final roar
        if (getAction() == A_NONE) {                                          // the fight begins
            introT = -1; flag(F_INTRO, false); attackCd = 60;
            for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(150))) ShadowCinematic.camFor(p, ShadowCinematic.OFF, 0, 0, 0, null, -1);
        }
    }

    private void phaseTwo(ServerLevel sl) {
        this.entityData.set(DATA_PHASE, 2);
        flag(F_BLADE, true);
        release(sl, null);
        act(A_ROAR);
        sound(ShadowRegistry.V_YOURS, 6F, 1F);
        line(sl, 3);
        summon(sl, 2);
    }

    /** "dies": collapses, the music cuts out... */
    private void fakeDeath(ServerLevel sl) {
        fakeDone = true;
        this.setHealth(1F);
        release(sl, null);
        act(A_COLLAPSE);
        flag(F_FAKEDEAD, true); flag(F_BLADE, false);
        sound(ShadowRegistry.ROAR, 6F, 0.5F);
        for (ShadeEntity sh : sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(100))) sh.discard();
        bar.setVisible(false);
    }

    private void fakeDeadTick(ServerLevel sl, int k) {
        if (k == 1) sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Mansoority has been driven back into the dark. For now.")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
        if (k == 120) sound(ShadowRegistry.CRACK, 5F, 0.6F);
        if (k >= 170) { act(A_RISE); sound(ShadowRegistry.V_AGAIN, 7F, 0.8F); line(sl, 4); }
    }

    /** ...and something black tears its way out of the body, screaming, with the blade through its stomach */
    private void riseTick(ServerLevel sl, int k, List<ServerPlayer> near) {
        if (k == 30) {
            flag(F_BLACK, true); flag(F_FAKEDEAD, false);
            this.entityData.set(DATA_PHASE, 3);
            sl.sendParticles(ShadowRegistry.WISP.get(), this.getX(), this.getY() + 5, this.getZ(), 160, 2, 4, 2, 0.1);
            sl.sendParticles(ShadowRegistry.DROOL.get(), this.getX(), this.getY() + 5, this.getZ(), 60, 2, 3, 2, 0);
            sound(ShadowRegistry.SCREECH, 8F, 0.6F); sound(ShadowRegistry.ROAR, 8F, 0.6F);
            shake(near, 3F);
            this.setHealth(MAX_HP * 0.45F);
            bar.setVisible(true);
        }
        if (k >= 70) act(A_ROAR);
    }

    private static final int[] TAUNT_LINES = {8, 9, 2, 4, 6};

    /** someone died to it. it says something. five of you: it's had enough, back to its tower */
    public void onPlayerKilled(ServerPlayer victim) {
        if (!(this.level() instanceof ServerLevel sl) || showcase) return;
        BossData d = BossData.get(sl.getServer());
        d.fightDeaths++; d.setDirty();
        var lines = List.of(ShadowRegistry.V_STAY, ShadowRegistry.V_LAST, ShadowRegistry.V_KNEEL, ShadowRegistry.V_AGAIN, ShadowRegistry.V_SLOW);
        int i = this.random.nextInt(lines.size());
        sound(lines.get(i), 6F, 1F);
        line(sl, TAUNT_LINES[i]);
        if (d.fightDeaths >= 5 && getAction() != A_RETREAT) { release(sl, null); act(A_RETREAT); pillar = tallestPillar(sl); }
    }

    private void retreatTick(ServerLevel sl, int k) {
        if (pillar == null) { leave(sl, true); return; }
        Vec3 c = new Vec3(pillar.getCenterX() + 0.5, this.getY(), pillar.getCenterZ() + 0.5);
        Vec3 d = c.subtract(this.position()).multiply(1, 0, 1);
        setScuttle(k + 8);
        if (d.length() > pillar.getRadius() + 2.5 && k < 600) {
            float yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
            this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
            stepToward(sl, c, 0.45);
            return;
        }
        leave(sl, true);
    }

    /** gone: either just wandered off, or sealed in its tower until someone wakes it */
    private void leave(ServerLevel sl, boolean seal) {
        if (!showcase) {
            BossData d = BossData.get(sl.getServer());
            d.restore(sl);
            if (seal) {
                d.sealed = true; d.sealedHp = Math.max(this.getHealth(), MAX_HP * 0.3F); d.tnt = 0;
                SpikeFeature.EndSpike sp = pillar != null ? pillar : tallestPillar(sl);
                d.sealedAt = sp != null ? new BlockPos(sp.getCenterX(), sp.getHeight(), sp.getCenterZ()) : this.blockPosition();
                d.fightDeaths = 0;
                d.setDirty();
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Mansoority has crawled back into its tower and sealed itself in. Something will have to wake it.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
                sl.sendParticles(ShadowRegistry.WISP.get(), this.getX(), this.getY() + 4, this.getZ(), 120, 2, 4, 2, 0.05);
            }
        }
        for (ShadeEntity sh : sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(100))) sh.discard();
        Entity b = sl.getEntity(bladeId); if (b != null) b.discard();
        release(sl, null);
        this.discard();
    }

    // ================================================================ damage both ways
    /** its blows are real here: this fight can kill you */
    private void hurt(ServerPlayer pl, float dmg) {
        if (pl.isCreative() || pl.isSpectator() || introT >= 0) return;
        if (showcase) dmg = Math.min(dmg, pl.getHealth() - 1F);          // the cinematic never kills
        if (dmg > 0) pl.hurt(this.damageSources().mobAttack(this), dmg);
    }

    private void fling(ServerPlayer p, double side, double up) {
        Vec3 d = p.position().subtract(this.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-4) d = new Vec3(1, 0, 0);
        d = d.normalize();
        p.setDeltaMovement(d.x * side, up, d.z * side);
        p.hurtMarked = true;
        NOFALL.put(p.getUUID(), p.level().getGameTime() + 60);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || dying >= 0 || introT >= 0 || stealT >= 0) return false;
        int a = getAction();
        if (a == A_COLLAPSE || a == A_DEAD || a == A_RISE || (a == A_BLINK && actionT > 8 && actionT < 26) || has(F_WALL)) return false;
        Entity src = source.getEntity();
        boolean byPlayer = src instanceof Player || (source.getDirectEntity() instanceof Projectile pr && pr.getOwner() instanceof Player);
        if (!byPlayer && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // the weak point: the face. open, it takes double and stops it cold
        boolean head = false;
        Vec3 hp = headPos();
        if (source.getDirectEntity() instanceof Projectile pr) head = pr.position().distanceTo(hp) < 2.6;
        else if (src instanceof Player pl) {
            Vec3 eye = pl.getEyePosition(), look = pl.getViewVector(1F);
            Vec3 to = hp.subtract(eye);
            double along = to.dot(look);
            head = along > 0 && to.subtract(look.scale(along)).length() < 2.0;
        }
        ServerLevel sl = (ServerLevel) this.level();
        if (head && has(F_JAW)) {
            amount *= 2F;
            if (stunCd <= 0 && a != A_CHEW && a != A_SIGNATURE) { stunCd = 300; release(sl, null); setVanishTick(0); act(A_STUN); sound(ShadowRegistry.ROAR, 5F, 1.4F); }
            sl.sendParticles(ParticleTypes.CRIT, hp.x, hp.y, hp.z, 20, 0.6, 0.6, 0.6, 0.4);
        } else if (head) amount *= 1.25F;
        this.setHealth(this.getHealth() - amount);
        recent.add(new float[]{t, amount});
        recent.removeIf(r -> t - r[0] > 100);
        float sum = 0; for (float[] r : recent) sum += r[1];
        if (sum > MAX_HP * 0.08F && floodCd <= 0) { floodCd = 900; flood(sl); recent.clear(); }
        sound(ShadowRegistry.HIT, 3F, 0.7F + this.random.nextFloat() * 0.3F);
        if (getPhase() == 1 && this.getHealth() <= MAX_HP * 0.6F) phaseTwo(sl);
        if (this.getHealth() <= 0F) {
            if (!fakeDone && !showcase) fakeDeath(sl);
            else startDying();
        }
        return true;
    }

    /** hurt it too fast and the lights go out: nothing but its eyes */
    private void flood(ServerLevel sl) {
        sound(ShadowRegistry.FLOOD, 6F, 1F);
        List<ServerPlayer> ps = sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(100));
        for (ServerPlayer p : ps) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
            ShadowNet.send(p, new FxPacket(FxPacket.FLOOD, 120, 0, 0, 0, this.getId(), 1));
        }
        if (getAction() == A_NONE) { moveTo = blinkSpot(sl, ps); act(A_BLINK); }
    }

    private void startDying() {
        dying = 0;
        this.setHealth(1F);
        release((ServerLevel) this.level(), null);
        act(A_COLLAPSE);
        sound(ShadowRegistry.ROAR, 7F, 0.45F);
    }

    private void dyingTick(ServerLevel sl) {
        dying++;
        if (dying == 50) act(A_DEAD);
        if (dying % 6 == 0) sound(ShadowRegistry.CRACK, 3F, 0.4F + this.random.nextFloat() * 0.4F);
        if (dying == 90) setVanishTick(1);
        if (dying > 90) setVanishTick(Math.min(VANISH_LEN - 1, dying - 89));
        if (dying >= 90 + VANISH_LEN - 1) {
            for (ShadeEntity sh : sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(100))) sh.discard();
            Entity b = sl.getEntity(bladeId); if (b != null) b.discard();
            // the blade only ever comes from here. the heart too
            sl.addFreshEntity(new ItemEntity(sl, this.getX(), this.getY() + 2, this.getZ(), new ItemStack(ShadowRegistry.SHADOW_BLADE.get())));
            if (!showcase) sl.addFreshEntity(new ItemEntity(sl, this.getX(), this.getY() + 2.5, this.getZ(), new ItemStack(ShadowRegistry.HOLLOW_HEART.get())));
            ExperienceOrb.award(sl, this.position().add(0, 2, 0), 3000);
            for (ServerPlayer pl : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(128))) ShadowAdvancements.award(pl, "boss");
            if (!showcase) {
                BossData d = BossData.get(sl.getServer());
                d.setDefeated();
                d.restore(sl);
                BlockPos g = findStand(sl, this.getX(), this.getY(), this.getZ(), 10);   // its skull stays where it fell
                if (g != null) { sl.setBlock(g, ShadowRegistry.SKULL_BLOCK.get().defaultBlockState(), 3); d.skull = g; d.setDirty(); }
                sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Mansoority is dead. Its skull is still there.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
            }
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bar.removeAllPlayers();
        super.remove(reason);
    }

    // ================================================================ helpers
    private void face(Entity e) {
        double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
    }

    private void stepToward(ServerLevel sl, Vec3 want, double speed) {
        Vec3 d = want.subtract(this.position());
        double h = Math.hypot(d.x, d.z);
        if (h < 0.2) return;
        double st = Math.min(speed, h);
        double nx = this.getX() + d.x / h * st, nz = this.getZ() + d.z / h * st;
        BlockPos g = findStand(sl, nx, this.getY(), nz, 4);
        if (g == null) return;                                   // never walks off the island
        this.setPos(nx, this.getY() + (g.getY() - this.getY()) * 0.35, nz);
    }

    private static Vec3 centroid(List<ServerPlayer> ps) {
        double x = 0, y = 0, z = 0;
        for (ServerPlayer p : ps) { x += p.getX(); y += p.getY(); z += p.getZ(); }
        return new Vec3(x / ps.size(), y / ps.size(), z / ps.size());
    }

    private SpikeFeature.EndSpike pillarNear(ServerLevel sl) {
        if (showcase || sl.dimension() != Level.END) return null;
        SpikeFeature.EndSpike best = null; double bd = 45;
        for (SpikeFeature.EndSpike sp : SpikeFeature.getSpikesForLevel(sl)) {
            double d = Math.hypot(sp.getCenterX() - this.getX(), sp.getCenterZ() - this.getZ());
            if (d < bd && !sl.getBlockState(new BlockPos(sp.getCenterX(), sp.getHeight() - 1, sp.getCenterZ())).isAir()) { bd = d; best = sp; }
        }
        return best;
    }

    public static SpikeFeature.EndSpike tallestPillar(ServerLevel sl) {
        SpikeFeature.EndSpike best = null;
        for (SpikeFeature.EndSpike sp : SpikeFeature.getSpikesForLevel(sl)) if (best == null || sp.getHeight() > best.getHeight()) best = sp;
        return best;
    }

    /** players it just threw: no fall damage until they land */
    public static final Map<UUID, Long> NOFALL = new HashMap<>();

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
}
