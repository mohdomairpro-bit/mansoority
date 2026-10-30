package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * after it dies: a thin, many-legged thing skitters out of the dark, picks up the skull and carries it home to the
 * altar in its tower. it can't be hurt and it doesn't stop. then it digs itself into the floor and is gone.
 */
public class Scavenger extends PathfinderMob {
    private static final EntityDataAccessor<Boolean> CARRY = SynchedEntityData.defineId(Scavenger.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SINK = SynchedEntityData.defineId(Scavenger.class, EntityDataSerializers.INT);
    public BlockPos from, to;
    private int life;
    /** client: walk cycle */
    public float stride, strideO;

    public Scavenger(EntityType<? extends PathfinderMob> t, Level l) {
        super(t, l);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 40D).add(Attributes.MOVEMENT_SPEED, 0.3D);
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); this.entityData.define(CARRY, false); this.entityData.define(SINK, 0); }
    public boolean carrying() { return this.entityData.get(CARRY); }
    public int sink() { return this.entityData.get(SINK); }
    @Override public boolean hurt(DamageSource s, float a) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        this.noPhysics = true; this.setNoGravity(true); this.setDeltaMovement(Vec3.ZERO);
        if (this.level().isClientSide) {
            strideO = stride;
            double moved = Math.hypot(this.getX() - this.xo, this.getZ() - this.zo);
            stride += (float) moved * 2.2F;
            return;
        }
        ServerLevel sl = (ServerLevel) this.level();
        life++;
        if (from == null || to == null || life > 20 * 120) { this.discard(); return; }
        if (sink() > 0) {                                           // digging itself in
            this.entityData.set(SINK, sink() + 1);
            this.setPos(this.getX(), this.getY() - 0.06, this.getZ());
            if (sink() % 4 == 0) sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.END_STONE.defaultBlockState()), this.getX(), this.getY() + 0.2, this.getZ(), 12, 0.5, 0.1, 0.5, 0.1);
            if (sink() > 40) this.discard();
            return;
        }
        Vec3 goal = Vec3.atBottomCenterOf(carrying() ? to : from);
        Vec3 d = goal.subtract(this.position());
        double h = Math.hypot(d.x, d.z);
        if (h > 0.6) {
            double sp = Math.min(h, carrying() ? 0.32 : 0.42);
            double nx = this.getX() + d.x / h * sp, nz = this.getZ() + d.z / h * sp;
            BlockPos g = ShadowEntity.findStand(sl, nx, this.getY() + 1, nz, 6);
            double ny = g != null ? this.getY() + (g.getY() - this.getY()) * 0.4 : this.getY() + (goal.y - this.getY()) * 0.1;
            this.setPos(nx, ny, nz);
            float yaw = (float) (Mth.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
            this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
            if (life % 5 == 0) sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.SPIDER_STEP, SoundSource.HOSTILE, 0.8F, 1.6F);
            return;
        }
        if (!carrying()) {                                          // takes the skull
            if (sl.getBlockState(from).getBlock() instanceof SkullBlock) sl.removeBlock(from, false);
            this.entityData.set(CARRY, true);
            sl.playSound(null, from, ShadowRegistry.CRACK.get(), SoundSource.HOSTILE, 2F, 1.3F);
            return;
        }
        // home: up onto the altar it goes
        BlockPos at = to;
        sl.setBlock(at, ShadowRegistry.SKULL_BLOCK.get().defaultBlockState(), 3);
        BossData d0 = BossData.get(sl.getServer());
        d0.skull = at; d0.setDirty();
        this.entityData.set(CARRY, false);
        this.entityData.set(SINK, 1);
        sl.playSound(null, at, ShadowRegistry.WHISPER.get(), SoundSource.HOSTILE, 2F, 0.7F);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) { super.addAdditionalSaveData(t); }
}
