package com.mansoority.shadow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/** a spike of bone bursting out of the ground: it hits whoever stands on it, then sinks back */
public class BoneSpike extends Entity {
    public static final int LIFE = 46;
    public int bossId = -1;
    public float damage = 7F;

    public BoneSpike(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel sl) {
            if (this.tickCount == 2) {
                AABB box = this.getBoundingBox().inflate(0.9, 1.2, 0.9);
                Entity boss = sl.getEntity(bossId);
                for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, box)) {
                    if (p.isCreative() || p.isSpectator()) continue;
                    p.hurt(boss != null ? this.damageSources().mobAttack((net.minecraft.world.entity.LivingEntity) boss) : this.damageSources().magic(), damage);
                    p.setDeltaMovement(p.getDeltaMovement().x * 0.3, 0.95, p.getDeltaMovement().z * 0.3);
                    p.hurtMarked = true;
                }
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        sl.getBlockState(this.blockPosition().below())), this.getX(), this.getY() + 0.2, this.getZ(), 18, 0.4, 0.2, 0.4, 0.15);
            }
            if (this.tickCount > LIFE) this.discard();
        }
    }
}
