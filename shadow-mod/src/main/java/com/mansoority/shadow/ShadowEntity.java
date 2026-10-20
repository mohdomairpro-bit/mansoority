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
    public static final int STAGE_MIMIC = -2;
    private int peek, hiddenFor, mimicStare;

    public static final int STARE_LIMIT = 60;
    /** Blender metres -> blocks for the mesh model (about 8 blocks tall) */
    public static final float MODEL_SCALE = 4.6F;   // ~12 blocks tall
    /** head centre in model metres (from the Blender export log) */
    private static final double HEAD_UP = 2.145, HEAD_FWD = 0.71;
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
        this.noCulling = false;        // frustum-culled: costs nothing while it's behind you
        this.setInvulnerable(true);
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
        this.entityData.define(DATA_MIMIC_NAME, "");
    }

    // ---------------------------------------------------------------- mimic: wears a player's face and walks around
    public boolean isMimic() { return !this.entityData.get(DATA_MIMIC).isEmpty(); }
    public UUID getMimicId() { try { return UUID.fromString(this.entityData.get(DATA_MIMIC)); } catch (Exception ex) { return new UUID(0, 0); } }
    public String getMimicName() { return this.entityData.get(DATA_MIMIC_NAME); }
    @Override
    public AABB getBoundingBoxForCulling() { return this.getBoundingBox().inflate(5.0, 2.0, 5.0); }

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
        e.refreshDimensions();
        e.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, p.getRandom().nextFloat() * 360F, 0);
        e.setTarget(p, STAGE_MIMIC);
        level.addFreshEntity(e);
        return e;
    }

    private void mimicTick(ServerPlayer p) {
        ServerLevel sl = (ServerLevel) this.level();
        for (ServerPlayer o : sl.players()) {
            if (!o.isSpectator() && o.distanceToSqr(this) < 11 * 11) {   // too close: it's simply not there anymore
                ui(o, ShadowRegistry.WHISPER, 0.45F, 0.7F);
                this.discard();
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
    public boolean ringActive() { return getRush() > 0 || this.entityData.get(DATA_STAGE) >= 1; }

    public void setTarget(ServerPlayer player, int stage) {
        this.targetId = player.getUUID();
        this.entityData.set(DATA_TARGET, player.getId());
        this.stage = stage;
        this.entityData.set(DATA_STAGE, stage);
        if (stage == 0) { this.entityData.set(DATA_HIDDEN, true); hiddenFor = 60; }
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
        if (!this.level().isClientSide) serverTick();
    }

    private void serverTick() {
        life++;
        int vanish = getVanish();
        if (vanish > 0) {
            this.entityData.set(DATA_VANISH, vanish + 1);
            if (vanish >= 12) finishVanish();
            return;
        }
        ServerPlayer player = targetId == null ? null : (ServerPlayer) this.level().getPlayerByUUID(targetId);
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != this.level()
                || player.distanceToSqr(this) > 120 * 120) {
            this.discard();
            return;
        }
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
            int stare = getStare() + 1;
            this.entityData.set(DATA_STARE, stare);
            onStare(watcher, stare);
        } else {
            if (getStare() > 0) {
                this.entityData.set(DATA_STARE, 0);
                for (ServerPlayer m : inside) calm(m);
            }
            unseen++;
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
            peek = 50 + this.random.nextInt(70);
            if (this.random.nextFloat() < 0.4F) ui(p, ShadowRegistry.HEARTBEAT, 0.35F, 0.8F);
            return false;
        }
        if (getStare() == 0 && --peek <= 0) {        // nobody noticed: slip away quietly
            this.entityData.set(DATA_HIDDEN, true);
            hiddenFor = 220 + this.random.nextInt(380);
            return true;
        }
        return false;
    }

    private static int coverScore(ServerLevel level, BlockPos s) {
        int cover = 0;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 1; dy <= 7; dy += 2) {
            BlockPos q = s.offset(dx, dy, dz);
            if (!level.getBlockState(q).isAir()) cover++;
        }
        return cover * 2 + (15 - level.getMaxLocalRawBrightness(s)) * 3;
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
        if (stage >= 0 && (s == 20 || s == 40) && this.distanceTo(p) > 6) lurch(p);
        if (stage < 0 && s >= 10) { startVanish(); return; }   // bedside visit: one look and it's gone
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
        if (stage < 1 || unseen < 30 || unseen % 25 != 0) return;
        double dist = Math.sqrt(this.distanceToSqr(p));
        if (dist < 8) return;
        Vec3 dir = p.position().subtract(this.position()).normalize();
        double step = Math.min(4.0, dist - 7);
        BlockPos spot = findStand((ServerLevel) this.level(), this.getX() + dir.x * step, this.getY(), this.getZ() + dir.z * step, 6);
        if (spot != null) this.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
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
    public void startRush(ServerPlayer p) {
        this.noPhysics = true;
        this.setNoGravity(true);
        this.entityData.set(DATA_RUSH, 1);
        soundAround(ShadowRegistry.SCREAM, 3.0F, 0.65F);
        ui(p, ShadowRegistry.STINGER, 1F, 0.9F);
        ui(p, ShadowRegistry.RISER, 1F, 1.4F);
    }

    private void rushTick(ServerPlayer p) {
        int r = getRush();
        this.entityData.set(DATA_RUSH, r + 1);
        double dx = p.getX() - this.getX();
        double dz = p.getZ() - this.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (r < 16) {                                   // folding down into the crouch: holds still, bones cracking
            if (r % 3 == 0) soundAround(ShadowRegistry.SNAP, 2.6F, 0.5F + this.random.nextFloat() * 0.3F);
            return;
        }
        if (d < 2.4 || r > 96) {
            for (ServerPlayer m : zoneMembers(p)) {      // it gets everyone inside the ring
                ShadowNet.send(m, FxPacket.of(FxPacket.JUMPSCARE_NOW));
                if (m == p ? d < 3.5 : true) bite(m);
            }
            this.discard();
            return;
        }
        double speed = Math.min(d, 0.35 + (r - 16) * 0.06);   // starts slow, gets horribly fast
        double ny = this.getY() + (p.getY() - this.getY()) * 0.25;
        this.setPos(this.getX() + dx / d * speed, ny, this.getZ() + dz / d * speed);
        if (r % 5 == 0) soundAround(ShadowRegistry.SNAP, 2.0F, 1.5F);
        if (r == 12) soundAround(ShadowRegistry.SCREAM, 3.0F, 0.75F);
    }

    /** a little damage when it catches you. never lethal: always leaves at least one heart */
    private void bite(ServerPlayer p) {
        if (isDaytimeFor(p)) return;
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
        burst();
        soundAround(ShadowRegistry.STATIC, 1.5F, 1.3F);
        this.discard();
    }

    private void burst() {
        if (this.level() instanceof ServerLevel sl) {
            double h = this.getBbHeight();
            sl.sendParticles(ShadowRegistry.WISP.get(), this.getX(), this.getY() + h / 2, this.getZ(), 70, 0.7, h / 3, 0.7, 0.0);
        }
    }

    private void soundAround(RegistryObject<SoundEvent> s, float vol, float pitch) {
        this.level().playSound(null, this.getX(), this.getY() + 4, this.getZ(), s.get(), SoundSource.MASTER, vol, pitch);
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
            BlockPos spot = findStand(level, x, p.getY(), z, 12);
            if (spot != null) return spot;
        }
        return null;
    }

    /** a solid block to stand on with 2 free blocks above, searching up/down from y */
    public static BlockPos findStand(ServerLevel level, double x, double y, double z, int range) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int i = 0; i <= range * 2; i++) {
            int dy = (i % 2 == 0) ? i / 2 : -(i / 2 + 1);
            BlockPos pos = base.above(dy);
            if (!level.hasChunkAt(pos)) return null;
            BlockState below = level.getBlockState(pos.below());
            if (below.isFaceSturdy(level, pos.below(), Direction.UP)
                    && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos).isEmpty()) {
                return pos;
            }
        }
        return null;
    }

    public static boolean anyNear(ServerPlayer p, double r) {
        AABB box = p.getBoundingBox().inflate(r);
        return !p.serverLevel().getEntitiesOfClass(ShadowEntity.class, box).isEmpty();
    }
}
