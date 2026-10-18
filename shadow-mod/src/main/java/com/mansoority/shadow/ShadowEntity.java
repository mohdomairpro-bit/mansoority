package com.mansoority.shadow;

import java.util.List;
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

    public static final int STARE_LIMIT = 60;
    private UUID targetId;
    private int stage;
    private int life;
    private int unseen;
    private boolean seenThisStage;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.noCulling = true;
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
    }

    public int getStare() { return this.entityData.get(DATA_STARE); }
    public int getTargetEntityId() { return this.entityData.get(DATA_TARGET); }
    public int getVanish() { return this.entityData.get(DATA_VANISH); }
    public int getRush() { return this.entityData.get(DATA_RUSH); }

    public void setTarget(ServerPlayer player, int stage) {
        this.targetId = player.getUUID();
        this.entityData.set(DATA_TARGET, player.getId());
        this.stage = stage;
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
        faceTowards(player);

        if (getRush() > 0) {
            rushTick(player);
            return;
        }

        if (isWatchedBy(player)) {
            this.getNavigation().stop();
            unseen = 0;
            int stare = getStare() + 1;
            this.entityData.set(DATA_STARE, stare);
            onStare(player, stare);
        } else {
            if (getStare() > 0) {
                this.entityData.set(DATA_STARE, 0);
                calm(player);
            }
            unseen++;
            creep(player);
        }

        if (life % 160 == 20) soundAround(ShadowRegistry.DRONE, 2.2F, 0.85F);
        if (life % 400 == 200) soundAround(ShadowRegistry.WHISPER, 1.6F, 0.6F);
        if (life > 20 * 120 || unseen > 20 * 50 || (stage < 0 && life > 20 * 12)) startVanish();
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
        float s = 1.8F + Math.min(1F, getStare() / (float) STARE_LIMIT) * 0.45F;
        double fwd = 19.6 / 16.0 * s;
        double rad = Math.toRadians(this.yBodyRot);
        return new Vec3(this.getX() - Math.sin(rad) * fwd, this.getY() + 51.4 / 16.0 * s, this.getZ() + Math.cos(rad) * fwd);
    }

    public boolean isWatchedBy(Player player) {
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
        if (stage < 0 && s >= 10) { startVanish(); return; }   // bedside visit: one look and it's gone
        if (s >= STARE_LIMIT) advance(p);
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
        seenThisStage = false;
        this.entityData.set(DATA_STARE, 0);
        burst();
        if (stage >= 3) {
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
        if (d < 2.4 || r > 80) {
            jumpscare(p, true);
            if (d < 3.5) bite(p);
            this.discard();
            return;
        }
        double speed = Math.min(d, 0.35 + r * 0.06);   // starts slow, gets horribly fast
        double ny = this.getY() + (p.getY() - this.getY()) * 0.25;
        this.setPos(this.getX() + dx / d * speed, ny, this.getZ() + dz / d * speed);
        if (r % 5 == 0) soundAround(ShadowRegistry.SNAP, 2.0F, 1.5F);
        if (r == 12) soundAround(ShadowRegistry.SCREAM, 3.0F, 0.75F);
    }

    /** a little damage when it catches you. never lethal: always leaves at least one heart */
    private void bite(ServerPlayer p) {
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
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + h / 2, this.getZ(), 60, 0.4, h / 3, 0.4, 0.02);
            sl.sendParticles(ParticleTypes.SQUID_INK, this.getX(), this.getY() + h / 2, this.getZ(), 60, 0.4, h / 3, 0.4, 0.02);
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
