package com.mansoority.shadow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Shadow. It can't hurt anyone, can't be hurt, never drops anything and is never saved to the world.
 * It only moves while nobody is looking at it.
 */
public class ShadowEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_STARE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VANISH = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RUSH = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_HIDDEN = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_MIMIC = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_MIMIC_NAME = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_GRAB = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_GRAB_MODE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SCUTTLE = SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);
    public static final int STAGE_MIMIC = -2, STAGE_GRAB = -3, STAGE_FLEE = -4;
    public static final int GRAB_LICK = 0, GRAB_DRAG = 1, VANISH_LEN = 34;
    /** players currently in its hand: nothing can hurt them while it holds them */
    public static final Set<UUID> GRABBED = new HashSet<>();
    private Vec3 grabHome, fleeDir;
    private int lastTurn = -9999;
    private boolean seenPeek;
    private int peek, hiddenFor, mimicStare;
    private final java.util.List<Vec3> path = new java.util.ArrayList<>();
    /** client-side smoothed pose drivers (no snapping between states) */
    public float smoothStare, smoothRush, tint = 1F;
    /** client: last frame time, for per-frame smoothing */
    public long lastFrameMs;
    /** client: animation playback state */
    public Object animState;
    /** where it's gliding to (it walks now instead of blinking from spot to spot) */
    private Vec3 glide;
    private Vec3 lastVictimPos, prevVictimPos;
    /** driven by the /shadow cinematic script instead of its own brain */
    public boolean scripted, noHarm;
    public void scriptStare(int v) { this.entityData.set(DATA_STARE, v); }
    public void scriptVariant(int v) { this.entityData.set(DATA_VARIANT, v); }
    /** cinematic: the mimic drops its disguise */
    public void reveal(ServerPlayer near) { if (isMimic()) unmask(near); }
    public void reveal() { this.entityData.set(DATA_HIDDEN, false); }

    public static final int STARE_LIMIT = 60;
    /** Blender metres -> blocks for the mesh model (about 8 blocks tall) */
    public static final float MODEL_SCALE = 4.6F;   // ~12 blocks tall
    /** head centre in model metres (from the Blender export log) */
    private static final double HEAD_UP = 1.905, HEAD_FWD = 0.61;
    private UUID targetId;
    private int stage;
    private int life;
    private int unseen;
    private boolean seenThisStage;
    /** the black ring: everyone inside it is hunted too */
    public static final double ZONE_RADIUS = 14.0;
    private final Set<UUID> zone = new HashSet<>();

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        // EntityCulling (in the pack) hid it behind leaves/walls: opt out of occlusion culling.
        // the renderer still does its own frustum check, so it costs nothing while it's behind you
        this.noCulling = true;
        this.setInvulnerable(true);
        this.setInvisible(true);      // minimaps/radars (Xaero's etc.) skip invisible mobs; our own renderer draws it anyway
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 96.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_STARE, 0);
        this.entityData.define(DATA_TARGET, -1);
        this.entityData.define(DATA_VANISH, 0);
        this.entityData.define(DATA_RUSH, 0);
        this.entityData.define(DATA_HIDDEN, false);
        this.entityData.define(DATA_STAGE, 0);
        this.entityData.define(DATA_MIMIC, "");
        this.entityData.define(DATA_GRAB, 0);
        this.entityData.define(DATA_GRAB_MODE, 0);
        this.entityData.define(DATA_SCUTTLE, 0);
        this.entityData.define(DATA_VARIANT, 0);
        this.entityData.define(DATA_MIMIC_NAME, "");
    }

    // ---------------------------------------------------------------- mimic: wears a player's face and walks around
    public boolean isMimic() { return !this.entityData.get(DATA_MIMIC).isEmpty(); }
    public UUID getMimicId() { try { return UUID.fromString(this.entityData.get(DATA_MIMIC)); } catch (Exception ex) { return new UUID(0, 0); } }
    public String getMimicName() { return this.entityData.get(DATA_MIMIC_NAME); }
    @Override
    public AABB getBoundingBoxForCulling() {
        if (getStage() == 0) return this.getBoundingBox().inflate(60.0, 8.0, 60.0);
        if (ringActive()) return this.getBoundingBox().inflate(ZONE_RADIUS + 40.0, 30.0, ZONE_RADIUS + 40.0);   // the black wall around the victim
        return this.getBoundingBox().inflate(5.0 + (getVanish() > 0 ? 6.0 : 0.0), 2.0, 5.0 + (getVanish() > 0 ? 6.0 : 0.0));
    }

    public int getGrab() { return this.entityData.get(DATA_GRAB); }
    public int getGrabMode() { return this.entityData.get(DATA_GRAB_MODE); }
    public int getScuttle() { return this.entityData.get(DATA_SCUTTLE); }
    /** how it reacts to being stared at: 0 arms open + face splits, 1 head upside-down crawl, 2 copies your steps, 3 neck stretches */
    public int getVariant() { return this.entityData.get(DATA_VARIANT); }

    // ---------------------------------------------------------------- the grab: picks you up, then licks you or drags you off
    public static ShadowEntity grab(ServerPlayer p, int mode) {
        ShadowEntity e = spawnAround(p, STAGE_GRAB, 180, 4.5);
        if (e == null) return null;
        e.entityData.set(DATA_GRAB_MODE, mode);
        e.reveal();
        e.noPhysics = true; e.setNoGravity(true);
        if (mode == GRAB_DRAG) {
            double a = Math.toRadians(p.getYRot() + 180 + (e.random.nextDouble() - 0.5) * 90);
            e.fleeDir = new Vec3(-Math.sin(a), 0, Math.cos(a));
        }
        return e;
    }

    /** where the victim hangs: in its hand, in front of it. same maths on both sides so the client sees it smoothly */
    public Vec3 grabOffset() {
        int g = getGrab();
        double fwd, up;
        if (getGrabMode() == GRAB_LICK) {
            double lift = g < 22 ? 0 : g < 55 ? (g - 22) / 33.0 : g < 125 ? 1 : Math.max(0, 1 - (g - 125) / 25.0);
            lift = lift * lift * (3 - 2 * lift);
            fwd = 2.4 + lift * 0.6;
            up = 1.0 + lift * 6.4 + (g >= 55 && g < 125 ? Math.sin(g * 0.25) * 0.15 : 0);
        } else { fwd = 3.4; up = 0.5 + (g > 22 ? Math.abs(Math.sin(g * 0.45)) * 0.35 : 0); }
        double yaw = Math.toRadians(this.getYRot());
        return new Vec3(-Math.sin(yaw) * fwd, up, Math.cos(yaw) * fwd);
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (getGrab() > 0) {
            Vec3 o = grabOffset();
            move.accept(passenger, this.getX() + o.x, this.getY() + o.y, this.getZ() + o.z);
        } else super.positionRider(passenger, move);
    }

    @Override public boolean shouldRiderSit() { return false; }

    private void grabTick(ServerPlayer p) {
        int g = getGrab() + 1;
        this.entityData.set(DATA_GRAB, g);
        int mode = getGrabMode();
        int end = mode == GRAB_LICK ? 150 : 170;
        if (mode == GRAB_DRAG && g > 30 && fleeDir != null) {            // scuttles off with you
            this.entityData.set(DATA_SCUTTLE, g);
            double nx = this.getX() + fleeDir.x * 0.26, nz = this.getZ() + fleeDir.z * 0.26;
            BlockPos s = findStand((ServerLevel) this.level(), nx, this.getY(), nz, 4);
            double ny = s != null ? this.getY() + (s.getY() - this.getY()) * 0.3 : this.getY();
            this.setPos(nx, ny, nz);
            float yaw = (float) (Mth.atan2(fleeDir.z, fleeDir.x) * (180F / Math.PI)) - 90F;
            this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
            if (g % 3 == 0) soundAround(ShadowRegistry.SNAP, 2.0F, 1.2F + this.random.nextFloat() * 0.4F);
        } else faceTowards(p);
        if (g == 1) { turnHead(p); ui(p, ShadowRegistry.BREATH, 1F, 0.6F); }
        if (g == 22) {
            grabHome = p.position();
            GRABBED.add(p.getUUID());
            p.startRiding(this, true);
            ui(p, ShadowRegistry.HIT, 1F, 0.8F);
            ui(p, ShadowRegistry.STINGER, 1F, 0.8F);
        }
        if (g > 22 && g < end && !p.isPassenger()) p.startRiding(this, true);   // you can't wriggle free
        if (mode == GRAB_LICK && g >= 60 && g < 122 && (g - 60) % 15 == 0) {
            this.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.6F, 0.55F);
            this.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.HOSTILE, 1.4F, 0.5F);
            ShadowNet.send(p, FxPacket.of(FxPacket.LICK));
            ((ServerLevel) this.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER, p.getX(), p.getY() + 1.2, p.getZ(), 14, 0.4, 0.5, 0.4, 0);
        }
        if (mode == GRAB_LICK && g == 58) ui(p, ShadowRegistry.BREATH, 1F, 0.45F);
        if (g >= end) {
            p.stopRiding();
            Vec3 drop = mode == GRAB_LICK && grabHome != null ? grabHome : p.position();
            Vec3 safe = safeSpot((ServerLevel) this.level(), drop);
            p.teleportTo(safe.x, safe.y, safe.z);
            p.fallDistance = 0;
            watchStuck(p);
            GRABBED.remove(p.getUUID());
            ShadowAdvancements.award(p, mode == GRAB_LICK ? "mostly" : "dragged");
            ShadowMark.mark(p);
            if (mode == GRAB_LICK && this.random.nextFloat() < 0.5F) ShadowNet.send(p, FxPacket.of(FxPacket.DEATHMSG));
            if (mode == GRAB_DRAG) ui(p, ShadowRegistry.STATIC, 1F, 0.7F);
            startVanish();
        }
    }

    /** you weren't looking: a hand takes your head and turns it toward it */
    public void turnHead(ServerPlayer p) {
        lastTurn = life;
        ShadowNet.send(p, new FxPacket(FxPacket.TURN, 0, 0, 0, 0, this.getId(), 0));
        ui(p, ShadowRegistry.SNAP, 1F, 0.8F);
    }

    /** a mimic you walked up to: drops the act, turns back into itself and runs */
    private void unmask(ServerPlayer near) {
        ShadowAdvancements.award(near, "friends");
        this.entityData.set(DATA_MIMIC, "");
        this.entityData.set(DATA_MIMIC_NAME, "");
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) this.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
        this.refreshDimensions();
        this.getNavigation().stop();
        stage = STAGE_FLEE; this.entityData.set(DATA_STAGE, stage);
        this.noPhysics = true; this.setNoGravity(true);
        Vec3 away = this.position().subtract(near.position()).multiply(1, 0, 1);
        fleeDir = away.lengthSqr() < 0.01 ? new Vec3(1, 0, 0) : away.normalize();
        soundAround(ShadowRegistry.SCREAM, 3.0F, 0.9F);
        soundAround(ShadowRegistry.SNAP, 3.0F, 0.5F);
        ((ServerLevel) this.level()).sendParticles(ShadowRegistry.WISP.get(), this.getX(), this.getY() + 1, this.getZ(), 30, 0.6, 1, 0.6, 0);
        life = 0;
    }

    private void fleeTick() {
        this.entityData.set(DATA_SCUTTLE, life + 8);
        if (life > 90) { startVanish(); return; }
        double sp = Math.min(0.42, 0.12 + life * 0.012);
        double nx = this.getX() + fleeDir.x * sp, nz = this.getZ() + fleeDir.z * sp;
        BlockPos s = findStand((ServerLevel) this.level(), nx, this.getY(), nz, 4);
        double ny = s != null ? this.getY() + (s.getY() - this.getY()) * 0.3 : this.getY();
        this.setPos(nx, ny, nz);
        float yaw = (float) (Mth.atan2(fleeDir.z, fleeDir.x) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
        if (life % 3 == 0) soundAround(ShadowRegistry.SNAP, 2.0F, 1.4F);
    }

    /** client-only dummies (the mirror moment) */
    public void clientStage(int st) { this.entityData.set(DATA_STAGE, st); this.entityData.set(DATA_HIDDEN, false); }

    public int getStage() { return this.entityData.get(DATA_STAGE); }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return isMimic() ? net.minecraft.world.entity.EntityDimensions.scalable(0.6F, 1.8F) : super.getDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_MIMIC.equals(key)) this.refreshDimensions();
    }

    /** someone you know, standing somewhere they can't be. gone the moment anyone gets close */
    public static ShadowEntity mimicFor(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        ServerPlayer copy = p;                                      // default: yourself
        List<ServerPlayer> others = p.server.getPlayerList().getPlayers().stream()
                .filter(o -> o != p && (o.level() != p.level() || o.distanceToSqr(p) > 150 * 150)).toList();
        if (!others.isEmpty() && p.getRandom().nextFloat() < 0.8F) copy = others.get(p.getRandom().nextInt(others.size()));
        BlockPos spot = spotAround(p, (p.getRandom().nextBoolean() ? 1 : -1) * (45 + p.getRandom().nextDouble() * 70), 26 + p.getRandom().nextDouble() * 10);
        if (spot == null) return null;
        ShadowEntity e = ShadowRegistry.SHADOW.get().create(level);
        if (e == null) return null;
        e.entityData.set(DATA_MIMIC, copy.getUUID().toString());
        e.entityData.set(DATA_MIMIC_NAME, copy.getGameProfile().getName());
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            e.setItemSlot(slot, copy.getItemBySlot(slot).copy());
            e.setDropChance(slot, 0F);
        }
        e.refreshDimensions();
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getRandom().nextFloat() * 360F, 0);
        e.setTarget(p, STAGE_MIMIC);
        level.addFreshEntity(e);
        return e;
    }

    private void mimicTick(ServerPlayer p) {
        ServerLevel sl = (ServerLevel) this.level();
        for (ServerPlayer o : sl.players()) {
            if (!o.isSpectator() && o.distanceToSqr(this) < 11 * 11) {   // too close: the act drops
                unmask(o);
                return;
            }
        }
        if (life > 20 * 150 || p.distanceToSqr(this) > 80 * 80) { this.discard(); return; }
        if (mimicStare > 0) {                                     // stops dead. only the head turns. all the way round.
            mimicStare--;
            this.getNavigation().stop();
            double dx = p.getX() - this.getX(), dz = p.getZ() - this.getZ();
            this.yHeadRot = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
            this.setXRot(0F);
            return;
        }
        if (this.getNavigation().isDone() && life % 10 == 0) {
            if (this.random.nextFloat() < 0.3F) { mimicStare = 60 + this.random.nextInt(120); return; }
            double a = this.random.nextDouble() * Math.PI * 2, r = 5 + this.random.nextDouble() * 10;
            BlockPos s = findStand(sl, this.getX() + Math.cos(a) * r, this.getY(), this.getZ() + Math.sin(a) * r, 5);
            if (s != null) this.getNavigation().moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0.42);
        }
    }

    public int getStare() { return this.entityData.get(DATA_STARE); }
    public int getTargetEntityId() { return this.entityData.get(DATA_TARGET); }
    public int getVanish() { return this.entityData.get(DATA_VANISH); }
    public int getRush() { return this.entityData.get(DATA_RUSH); }
    public boolean isHidden() { return this.entityData.get(DATA_HIDDEN); }
    /** the black ring is up once it has started closing in */
    public boolean ringActive() { int st = this.entityData.get(DATA_STAGE); return st < STAGE_BOSS && (getRush() > 0 || st >= 1); }
    public static final int STAGE_BOSS = 7;
    /** subclasses (the End boss) run their own brain */
    protected boolean customTick() { return false; }
    // hooks the boss uses
    protected void setStare(int v) { this.entityData.set(DATA_STARE, v); }
    protected void setVanishTick(int v) { this.entityData.set(DATA_VANISH, v); }
    protected void setScuttle(int v) { this.entityData.set(DATA_SCUTTLE, v); }
    protected void setGrab(int g, int mode) { this.entityData.set(DATA_GRAB, g); this.entityData.set(DATA_GRAB_MODE, mode); }
    protected void setStageData(int st) { this.entityData.set(DATA_STAGE, st); this.entityData.set(DATA_HIDDEN, false); this.stage = st; }
    protected void setTargetId(int id) { this.entityData.set(DATA_TARGET, id); }
    protected void setVariantData(int v) { this.entityData.set(DATA_VARIANT, v); }
    public void sound(RegistryObject<SoundEvent> s, float vol, float pitch) { soundAround(s, vol, pitch); }

    public void setTarget(ServerPlayer player, int stage) {
        this.targetId = player.getUUID();
        this.entityData.set(DATA_TARGET, player.getId());
        this.stage = stage;
        this.entityData.set(DATA_STAGE, stage);
        if (stage == 0) { this.entityData.set(DATA_HIDDEN, true); hiddenFor = 60; }
        this.entityData.set(DATA_VARIANT, this.random.nextInt(4));
    }

    /** walk toward 'glide' a little each tick, following the ground */
    private boolean glideTick(double speed) {
        if (glide == null) return false;
        Vec3 d = glide.subtract(this.position());
        double h = Math.hypot(d.x, d.z);
        if (h < 0.3) { glide = null; return false; }
        double st = Math.min(speed, h);
        double nx = this.getX() + d.x / h * st, nz = this.getZ() + d.z / h * st;
        BlockPos g = findStand((ServerLevel) this.level(), nx, this.getY(), nz, 3);
        double ny = g != null ? this.getY() + (g.getY() - this.getY()) * 0.35 : this.getY();
        this.setPos(nx, ny, nz);
        return true;
    }

    // ---------------------------------------------------------------- harmless & unkillable
    @Override public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && source.getEntity() instanceof Player) startVanish();
        return false;
    }
    @Override public void kill() { this.discard(); }
    @Override public boolean isPushable() { return false; }
    @Override protected void pushEntities() {}
    @Override public void push(Entity entity) {}
    @Override public boolean canBeLeashed(Player player) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override protected boolean shouldDespawnInPeaceful() { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    @Override public boolean canChangeDimensions() { return false; }

    // ---------------------------------------------------------------- brain
    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) { if (!customTick()) serverTick(); }
        else {
            float st = Math.min(1F, getStare() / (float) STARE_LIMIT);
            smoothStare += (st - smoothStare) * (st > smoothStare ? 0.3F : 0.12F);
            smoothRush = getRush();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        java.util.List<Entity> riders = java.util.List.copyOf(this.getPassengers());
        this.ejectPassengers();
        for (Entity pass : riders) {
            GRABBED.remove(pass.getUUID()); pass.fallDistance = 0;
            if (pass instanceof ServerPlayer sp && this.level() instanceof ServerLevel sl) {   // never leave anyone inside the ground
                Vec3 safe = safeSpot(sl, sp.position());
                sp.teleportTo(safe.x, safe.y, safe.z);
                watchStuck(sp);
            }
        }
        if (!this.level().isClientSide && this.level() instanceof ServerLevel sl && reason != RemovalReason.CHANGED_DIMENSION) {
            path.add(this.position());
            ShadowTrails.add(sl, path);
        }
        super.remove(reason);
    }

    private void serverTick() {
        life++;
        if (life % 10 == 0 && !isHidden() && (path.isEmpty() || path.get(path.size() - 1).distanceToSqr(this.position()) > 2.0)) {
            path.add(this.position());
            if (path.size() > 80) path.remove(0);
        }
        int vanish = getVanish();
        if (vanish > 0) {
            this.entityData.set(DATA_VANISH, vanish + 1);
            if (vanish == 1) soundAround(ShadowRegistry.RISER, 2.5F, 0.5F);
            if (vanish % 2 == 0 && vanish < VANISH_LEN - 6 && this.level() instanceof ServerLevel sl) {   // darkness pulled into the hole
                double h = this.getBbHeight(), rad = Math.max(1.3, h * 0.5);
                for (int i = 0; i < 3; i++) {
                    double a = this.random.nextDouble() * Math.PI * 2, d = rad * (1.1 + this.random.nextDouble() * 0.6);
                    double px = Math.cos(a) * d, py = Math.sin(a) * d * 0.9;
                    double yaw = Math.toRadians(this.random.nextDouble() * 360);
                    double ox = px * Math.cos(yaw), oz = px * Math.sin(yaw);
                    sl.sendParticles(ShadowRegistry.WISP.get(), this.getX() + ox, this.getY() + h * 0.5 + py, this.getZ() + oz, 0, -ox * 0.25, -py * 0.25, -oz * 0.25, 1.0);
                }
            }
            if (vanish >= VANISH_LEN) finishVanish();
            return;
        }
        ServerPlayer player = targetId == null ? null : (ServerPlayer) this.level().getPlayerByUUID(targetId);
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != this.level()
                || player.distanceToSqr(this) > 120 * 120) {
            this.discard();
            return;
        }
        prevVictimPos = lastVictimPos;
        lastVictimPos = player.position();
        if (stage == STAGE_GRAB) { grabTick(player); return; }
        if (stage == STAGE_FLEE) { fleeTick(); return; }
        if (scripted) { faceTowards(player); return; }
        if (stage == STAGE_MIMIC) { mimicTick(player); return; }
        faceTowards(player);

        if (getRush() > 0) {
            rushTick(player);
            return;
        }

        if (stage == 0 && stalkTick(player)) return;
        List<ServerPlayer> inside = zoneMembers(player);
        if (stage >= 1) tickZone(player, inside);
        ServerPlayer watcher = null;
        for (ServerPlayer m : inside) if (isWatchedBy(m)) { watcher = m; break; }
        if (watcher != null) {
            this.getNavigation().stop();
            unseen = 0;
            glide = null;
            int stare = getStare() + 1;
            this.entityData.set(DATA_STARE, stare);
            int var = getVariant();
            if (var == 1 && stare > 15 && this.distanceTo(watcher) > 7) {          // crawls at you while you watch
                glide = watcher.position(); glideTick(0.06); glide = null;
            }
            if (var == 2 && prevVictimPos != null && watcher == player && this.distanceTo(watcher) > 6) {   // every step you take, it takes one toward you
                double moved = Math.hypot(watcher.getX() - prevVictimPos.x, watcher.getZ() - prevVictimPos.z);
                if (moved > 0.01) { glide = watcher.position(); glideTick(moved); glide = null; }
            }
            onStare(watcher, stare);
        } else {
            if (getStare() > 0) {
                this.entityData.set(DATA_STARE, 0);
                for (ServerPlayer m : inside) calm(m);
            }
            unseen++;
            glideTick(0.2);
            if (stage >= 1 && unseen > 20 * 7 && life - lastTurn > 20 * 25) turnHead(player);
            creep(player);
        }

        if (life % 160 == 20) soundAround(ShadowRegistry.DRONE, 2.2F, 0.85F);
        if (life % 400 == 200) soundAround(ShadowRegistry.WHISPER, 1.6F, 0.6F);
        if ((stage == 0 && life > 20 * 300) || (stage > 0 && (life > 20 * 420 || unseen > 20 * 60)) || (stage < 0 && life > 20 * 12)) startVanish();
    }

    /**
     * stage 0: for ~5 minutes it only shows itself in brief glimpses - far away, mostly behind you,
     * for a few seconds - then it's gone again. returns true while it's hidden (nothing else to do)
     */
    private boolean stalkTick(ServerPlayer p) {
        if (isHidden()) {
            if (--hiddenFor > 0) return true;
            BlockPos spot = null; int best = -1;
            for (int i = 0; i < 5; i++) {                  // pick the darkest spot with the most cover (trees, walls)
                BlockPos c = spotAround(p, 110 + this.random.nextDouble() * 140, 30 + this.random.nextDouble() * 16);
                if (c == null) continue;
                int sc = coverScore((ServerLevel) this.level(), c);
                if (sc > best) { best = sc; spot = c; }
            }
            if (spot == null) { hiddenFor = 100; return true; }
            this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            faceTowards(p);
            this.entityData.set(DATA_HIDDEN, false);
            peek = 90 + this.random.nextInt(80);
            seenPeek = false;
            if (this.random.nextFloat() < 0.4F) ui(p, ShadowRegistry.HEARTBEAT, 0.35F, 0.8F);
            else if (this.random.nextFloat() < 0.35F) soundAround(ShadowRegistry.FAR, 3.0F, 0.9F + this.random.nextFloat() * 0.2F);
            return false;
        }
        if (getStare() > 0) seenPeek = true;
        if (peek == 1 && !seenPeek && life - lastTurn > 20 * 60 && this.random.nextFloat() < 0.4F) {   // you never noticed it: it makes you
            turnHead(p);
            peek += 70;
        }
        if (getStare() == 0 && --peek <= 0) {        // nobody noticed: slip away quietly
            this.entityData.set(DATA_HIDDEN, true);
            hiddenFor = 220 + this.random.nextInt(380);
            return true;
        }
        return false;
    }

    private static int coverScore(ServerLevel level, BlockPos s) {
        int side = 0, over = 0;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            boolean ring = Math.abs(dx) >= 2 || Math.abs(dz) >= 2;
            for (int dy = 0; dy <= 3; dy++) if (ring && !level.getBlockState(s.offset(dx, dy, dz)).isAir()) side++;
        }
        for (int dy = 2; dy <= 10; dy++) if (!level.getBlockState(s.above(dy)).isAir()) over++;
        return side * 2 - over * 6 + (15 - level.getMaxLocalRawBrightness(s)) * 3;
    }

    /** players inside the ring around the victim (the victim always counts) */
    private List<ServerPlayer> zoneMembers(ServerPlayer target) {
        List<ServerPlayer> list = target.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                target.getBoundingBox().inflate(ZONE_RADIUS, 8, ZONE_RADIUS),
                o -> !o.isSpectator() && o.distanceToSqr(target) <= ZONE_RADIUS * ZONE_RADIUS);
        if (!list.contains(target)) list.add(target);
        return list;
    }

    /** a wall of black smoke in a circle around the victim; walking into it gets you hunted too */
    private void tickZone(ServerPlayer target, List<ServerPlayer> inside) {
        ServerLevel sl = target.serverLevel();
        if (life % 10 == 0) {
            double a = this.random.nextDouble() * Math.PI * 2;
            sl.sendParticles(ShadowRegistry.WISP.get(), target.getX() + Math.cos(a) * ZONE_RADIUS * 0.95, target.getY() + 0.3,
                    target.getZ() + Math.sin(a) * ZONE_RADIUS * 0.95, 2, 0.5, 0.2, 0.5, 0.0);
        }
        for (ServerPlayer m : inside) {
            if (zone.add(m.getUUID()) && m != target) {      // someone just walked in
                ui(m, ShadowRegistry.STINGER, 1F, 0.95F);
                ui(m, ShadowRegistry.HEARTBEAT, 1F, 0.9F);
                m.displayClientMessage(net.minecraft.network.chat.Component.literal("You shouldn't have come here.")
                        .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), true);
            }
            if (life % 140 == 70) ui(m, ShadowRegistry.HEARTBEAT, 0.8F, 0.85F);
        }
    }

    private void faceTowards(Player player) {
        double dx = player.getX() - this.getX();
        double dz = player.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** where the head is in the world (it hangs out in front of the body) */
    public Vec3 headPos() {
        float s = MODEL_SCALE * (1F + Math.min(1F, getStare() / (float) STARE_LIMIT) * 0.12F);
        double fwd = HEAD_FWD * s;
        double rad = Math.toRadians(this.yBodyRot);
        return new Vec3(this.getX() - Math.sin(rad) * fwd, this.getY() + HEAD_UP * s, this.getZ() + Math.cos(rad) * fwd);
    }

    public boolean isWatchedBy(Player player) {
        if (isHidden()) return false;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(96));
        boolean ray = this.getBoundingBox().inflate(0.4).clip(eye, end).isPresent();
        if (!ray) {
            Vec3 head = headPos();
            Vec3 toHead = head.subtract(eye);
            double along = toHead.dot(look);
            if (along > 0) {
                double off = toHead.subtract(look.scale(along)).length();
                ray = off < 1.4;
            }
        }
        if (!ray) return false;
        return clearPath(player, eye, headPos()) || clearPath(player, eye, this.position().add(0, this.getBbHeight() * 0.5, 0));
    }

    private boolean clearPath(Player player, Vec3 from, Vec3 to) {
        return this.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    private void onStare(ServerPlayer p, int s) {
        if (s == 1) {
            if (!seenThisStage) {
                seenThisStage = true;
                ui(p, ShadowRegistry.STINGER, 1F, 1F);
                ui(p, ShadowRegistry.STINGER, 1F, 0.98F);
                ui(p, ShadowRegistry.HIT, 1F, 1F);
                ui(p, ShadowRegistry.HIT, 1F, 0.9F);
            }
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, false));
            ui(p, ShadowRegistry.STATIC, 0.8F, 0.8F);
            soundAround(ShadowRegistry.SNAP, 2.0F, 1.2F);
        }
        if (s == 1 || s == 14 || s == 25 || s == 34 || s == 41 || s == 46 || s == 50 || s == 54 || s == 57) {
            float pitch = 0.8F + s * 0.007F;
            ui(p, ShadowRegistry.HEARTBEAT, 1F, pitch);
            ui(p, ShadowRegistry.HEARTBEAT, 1F, pitch);
        }
        if (s == 22) soundAround(ShadowRegistry.SNAP, 2.0F, 0.9F);
        if (s == 31) soundAround(ShadowRegistry.SNAP, 2.0F, 0.7F);
        if (s == 26) ui(p, ShadowRegistry.STATIC, 1F, 0.6F);
        if (s == 40) ui(p, ShadowRegistry.WHISPER, 1F, 0.6F);
        if (stage >= 1 && (s == 20 || s == 40) && this.distanceTo(p) > 6) lurch(p);
        if (stage < 0 && s >= 10) { startVanish(); return; }   // bedside visit: one look and it's gone
        if (s == 30) ShadowAdvancements.award(p, "looked");
        if (s >= STARE_LIMIT) advance(p);
    }

    /** a sudden jerk one step toward you while you're looking at it */
    private void lurch(ServerPlayer p) {
        Vec3 dir = p.position().subtract(this.position()).multiply(1, 0, 1).normalize();
        BlockPos spot = findStand((ServerLevel) this.level(), this.getX() + dir.x * 1.6, this.getY(), this.getZ() + dir.z * 1.6, 3);
        if (spot != null) {
            this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
            soundAround(ShadowRegistry.SNAP, 2.5F, 0.6F);
            ui(p, ShadowRegistry.HIT, 0.8F, 1.3F);
        }
    }

    private void calm(ServerPlayer p) {
        p.removeEffect(MobEffects.CONFUSION);
    }

    /** only moves while you're NOT looking: blinks a few blocks closer */
    private void creep(ServerPlayer p) {
        if (stage < 1 || unseen < 40 || unseen % 35 != 0) return;
        double dist = Math.sqrt(this.distanceToSqr(p));
        if (dist < 8) return;
        Vec3 dir = p.position().subtract(this.position()).normalize();
        double step = Math.min(4.0, dist - 7);
        BlockPos spot = findStand((ServerLevel) this.level(), this.getX() + dir.x * step, this.getY(), this.getZ() + dir.z * step, 6);
        if (spot != null) glide = new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
    }

    private void advance(ServerPlayer p) {
        calm(p);
        stage++;
        this.entityData.set(DATA_STAGE, stage);
        this.entityData.set(DATA_HIDDEN, false);
        seenThisStage = false;
        this.entityData.set(DATA_STARE, 0);
        burst();
        if (stage >= 3) {
            if (isDaytimeFor(p)) {          // in daylight it gives up: one last look and it's gone
                ui(p, ShadowRegistry.STATIC, 1F, 0.7F);
                startVanish();
                return;
            }
            startRush(p);
            return;
        }
        Vec3 behind = p.position().subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(1.0));
        at(p, ShadowRegistry.BREATH, behind.x, p.getEyeY(), behind.z, 1F, 0.8F);
        BlockPos spot = spotAround(p, 180, stage == 1 ? 13 : 7);
        if (spot == null) { this.discard(); return; }
        this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        unseen = 0;
    }

    /** final stage: it sprints straight at you, through anything, screaming. it never touches you: the scare hits first */
    public static final int RUSH_STILL = 90, RUSH_FOLD = 64, RUSH_GO = RUSH_STILL + RUSH_FOLD;
    public void startRush(ServerPlayer p) {
        // it comes from the side, 90 degrees off where you're looking, far enough that you see it coming
        double side = this.random.nextBoolean() ? 90 : -90;
        BlockPos spot = spotAround(p, side, 30);
        if (spot != null) this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        faceTowards(p);
        this.entityData.set(DATA_HIDDEN, false);
        this.entityData.set(DATA_STARE, 0);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.entityData.set(DATA_RUSH, 1);
        ui(p, ShadowRegistry.STINGER, 1F, 0.7F);
        ui(p, ShadowRegistry.DRONE, 1F, 0.6F);
    }

    private void rushTick(ServerPlayer p) {
        int r = getRush();
        this.entityData.set(DATA_RUSH, r + 1);
        faceTowards(p);
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (r < RUSH_STILL) {                           // 1) it just stands there, far off, looking at you. heartbeat climbs
            int every = Math.max(5, 20 - r / 5);
            if (r % every == 0) ui(p, ShadowRegistry.HEARTBEAT, 1F, 0.8F + r * 0.004F);
            if (r == 20) ui(p, ShadowRegistry.BREATH, 1F, 0.55F);
            if (r == 45) soundAround(ShadowRegistry.WHISPER, 2.5F, 0.5F);
            return;
        }
        if (r < RUSH_GO) {                              // 2) the turn: bones cracking as it folds down
            if (r % 4 == 0) soundAround(ShadowRegistry.SNAP, 2.8F, 0.45F + this.random.nextFloat() * 0.35F);
            if (r == RUSH_STILL) ui(p, ShadowRegistry.RISER, 1F, 0.9F);
            if (r == RUSH_GO - 6) soundAround(ShadowRegistry.SCREAM, 3.2F, 0.6F);
            return;
        }
        int g = r - RUSH_GO;                            // 3) the scuttle
        if (d < 2.4 || g > 220) {
            for (ServerPlayer m : zoneMembers(p)) {      // it gets everyone inside the ring
                ShadowNet.send(m, FxPacket.of(FxPacket.JUMPSCARE_NOW));
                ShadowAdvancements.award(m, "run");
                ShadowMark.mark(m);
                if (m == p ? d < 3.5 : true) bite(m);
            }
            this.discard();
            return;
        }
        boolean freeze = g > 18 && g < 30 && d > 12;    // halfway it stops dead for a moment... then comes faster
        double speed = freeze ? 0 : Math.min(d, Math.min(0.85, 0.2 + g * (g > 30 ? 0.03 : 0.014)));
        double ny = this.getY() + (p.getY() - this.getY()) * 0.25;
        this.setPos(this.getX() + dx / d * speed, ny, this.getZ() + dz / d * speed);
        if (!freeze && g % 3 == 0) soundAround(ShadowRegistry.SNAP, 2.0F, 1.3F + this.random.nextFloat() * 0.4F);
        if (g == 30) soundAround(ShadowRegistry.SCREAM, 3.2F, 0.75F);
    }

    /** a little damage when it catches you. never lethal: always leaves at least one heart */
    private void bite(ServerPlayer p) {
        if (noHarm || isDaytimeFor(p)) return;
        float dmg = ShadowConfig.CATCH_DAMAGE.get().floatValue();
        if (dmg <= 0F || p.isCreative() || p.isSpectator()) return;
        float allowed = Math.min(dmg, p.getHealth() - 2.0F);
        if (allowed <= 0F) return;
        p.hurt(this.damageSources().mobAttack(this), allowed);
    }

    /** used by commands to skip straight to the chase */
    public static ShadowEntity rushFrom(ServerPlayer p, double dist) {
        ShadowEntity e = spawnAround(p, 2, 0, dist);
        if (e != null) { e.stage = 3; e.startRush(p); }
        return e;
    }

    /** daylight with open sky = gentler: no chase, no damage */
    public static boolean isDaytimeFor(ServerPlayer p) {
        return p.level().isDay() && p.level().canSeeSky(p.blockPosition()) && p.getY() > 50;
    }

    public void startVanish() {
        if (getVanish() == 0) this.entityData.set(DATA_VANISH, 1);
    }

    private void finishVanish() {

        soundAround(ShadowRegistry.STATIC, 1.5F, 1.3F);
        this.discard();
    }

    private void burst() {
        if (this.level() instanceof ServerLevel sl) {
            double h = this.getBbHeight();
            sl.sendParticles(ShadowRegistry.WISP.get(), this.getX(), this.getY() + h / 2, this.getZ(), 70, 0.7, h / 3, 0.7, 0.0);
        }
    }

    /** a sound from where it stands. sent to each nearby client so it can be muffled by walls and echo in caves */
    private void soundAround(RegistryObject<SoundEvent> s, float vol, float pitch) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        double r = Math.max(24, 16 * vol);
        for (ServerPlayer p : sl.players())
            if (p.distanceToSqr(this) < r * r) at(p, s, this.getX(), this.getY() + 4, this.getZ(), vol, pitch);
    }

    // ---------------------------------------------------------------- helpers used by events/commands
    public static void ui(ServerPlayer p, RegistryObject<SoundEvent> s, float vol, float pitch) {
        ShadowNet.send(p, new FxPacket(FxPacket.SOUND_UI, ShadowRegistry.indexOf(s), 0, 0, 0, vol, pitch));
    }

    public static void at(ServerPlayer p, RegistryObject<SoundEvent> s, double x, double y, double z, float vol, float pitch) {
        ShadowNet.send(p, new FxPacket(FxPacket.SOUND_AT, ShadowRegistry.indexOf(s), x, y, z, vol, pitch));
    }

    public static void jumpscare(ServerPlayer p, boolean instant) {
        FxPacket pkt = FxPacket.of(instant ? FxPacket.JUMPSCARE_NOW : FxPacket.JUMPSCARE);
        ShadowNet.send(p, pkt);
        for (ServerPlayer other : friendsNear(p)) ShadowNet.send(other, pkt);
    }

    public static List<ServerPlayer> friendsNear(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(ServerPlayer.class, p.getBoundingBox().inflate(12),
                o -> o != p && !o.isSpectator() && !o.isCreative());
    }

    /** spawn the shadow 'dist' blocks from the player at 'angle' degrees from where they look (180 = behind) */
    public static ShadowEntity spawnAround(ServerPlayer p, int stage, double angle, double dist) {
        ServerLevel level = p.serverLevel();
        BlockPos spot = spotAround(p, angle, dist);
        if (spot == null) return null;
        ShadowEntity e = ShadowRegistry.SHADOW.get().create(level);
        if (e == null) return null;
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        e.setTarget(p, stage);
        e.faceTowards(p);
        level.addFreshEntity(e);
        return e;
    }

    public static BlockPos spotAround(ServerPlayer p, double angle, double dist) {
        ServerLevel level = p.serverLevel();
        double[] offsets = {0, -25, 25, -50, 50, -75, 75};
        for (double off : offsets) {
            double a = Math.toRadians(p.getYRot() + angle + off);
            double x = p.getX() - Math.sin(a) * dist;
            double z = p.getZ() + Math.cos(a) * dist;
            BlockPos spot = findStand(level, x, p.getY(), z, 24);   // mountains: the ground can be far above or below you
            if (spot != null) return spot;
        }
        return null;
    }

    /** a solid block to stand on with 2 free blocks above, searching up/down from y */
    public static BlockPos findStand(ServerLevel level, double x, double y, double z, int range) {
        BlockPos base = BlockPos.containing(x, y, z);
        int down = Math.max(range, 40);              // under a forest canopy the ground can be far below
        for (int i = 0; i <= range + down; i++) {
            int dy = i <= range * 2 ? ((i % 2 == 0) ? i / 2 : -(i / 2 + 1)) : -(i - range);
            BlockPos pos = base.above(dy);
            if (!level.hasChunkAt(pos)) return null;
            BlockState below = level.getBlockState(pos.below());
            if (below.isFaceSturdy(level, pos.below(), Direction.UP) && !below.is(net.minecraft.tags.BlockTags.LEAVES)
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    /** getting off its hand: where you are if that's open air, otherwise the nearest open spot above you */
    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        if (this.level() instanceof ServerLevel sl) {
            BlockPos b = BlockPos.containing(passenger.position());
            if (fits(sl, b)) return passenger.position();
            return safeSpot(sl, passenger.position());
        }
        return passenger.position();
    }

    private static boolean fits(ServerLevel l, BlockPos b) {
        return l.getBlockState(b).getCollisionShape(l, b).isEmpty() && l.getBlockState(b.above()).getCollisionShape(l, b.above()).isEmpty()
                && !l.getFluidState(b).is(net.minecraft.tags.FluidTags.LAVA);
    }

    /**
     * somewhere a player can stand. in open air: straight down to the ground. inside the ground: straight UP to the
     * first open spot (never down into a cave). last resort: the surface of that column.
     */
    public static Vec3 safeSpot(ServerLevel l, Vec3 at) {
        BlockPos b = BlockPos.containing(at);
        if (fits(l, b)) {
            for (int i = 0; i < 48 && b.getY() - i > l.getMinBuildHeight(); i++) {
                BlockPos q = b.below(i);
                if (!fits(l, q)) break;
                BlockPos under = q.below();
                if (!l.getBlockState(under).getCollisionShape(l, under).isEmpty() || !l.getFluidState(under).isEmpty())
                    return new Vec3(at.x, q.getY(), at.z);
            }
            return at;           // over the void (the End): leave it to the fall handling
        }
        for (int i = 1; i < 64 && b.getY() + i < l.getMaxBuildHeight(); i++) {
            BlockPos q = b.above(i);
            if (fits(l, q) && !l.getBlockState(q.below()).getCollisionShape(l, q.below()).isEmpty()) return new Vec3(at.x, q.getY(), at.z);
        }
        int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
        return new Vec3(b.getX() + 0.5, top, b.getZ() + 0.5);
    }

    /** for a few seconds after it lets go of someone: if they end up inside a block, pop them out */
    public static final java.util.Map<UUID, Long> STUCK_WATCH = new java.util.HashMap<>();
    public static void watchStuck(ServerPlayer p) { STUCK_WATCH.put(p.getUUID(), p.level().getGameTime() + 100); }
    public static void stuckTick(ServerPlayer p) {
        Long until = STUCK_WATCH.get(p.getUUID());
        if (until == null) return;
        if (p.level().getGameTime() > until) { STUCK_WATCH.remove(p.getUUID()); return; }
        if (p.isPassenger() || !p.isInWall()) return;
        Vec3 safe = safeSpot(p.serverLevel(), p.position());
        p.teleportTo(safe.x, safe.y, safe.z);
        p.fallDistance = 0;
    }

    public static boolean anyNear(ServerPlayer p, double r) {
        AABB box = p.getBoundingBox().inflate(r);
        return !p.serverLevel().getEntitiesOfClass(ShadowEntity.class, box).isEmpty();
    }
}
