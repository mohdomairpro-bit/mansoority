package com.mansoority.shadow;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** phase three: the blade it tore out of its own stomach, thrown. it flies flat and spinning, then sticks in the ground */
public class ThrownBlade extends Entity {
    private static final EntityDataAccessor<Boolean> STUCK = SynchedEntityData.defineId(ThrownBlade.class, EntityDataSerializers.BOOLEAN);
    public int bossId = -1;
    private Vec3 start;
    private final Set<UUID> hit = new HashSet<>();

    public ThrownBlade(EntityType<?> type, Level level) { super(type, level); this.noPhysics = true; }

    @Override protected void defineSynchedData() { this.entityData.define(STUCK, false); }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    public boolean stuck() { return this.entityData.get(STUCK); }

    public void launch(Vec3 from, Vec3 dir) {
        this.setPos(from); this.start = from;
        this.setDeltaMovement(dir.normalize().scale(1.35));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel sl)) { if (!stuck()) this.setPos(this.position().add(this.getDeltaMovement())); return; }
        if (stuck()) { if (this.tickCount > 20 * 60) this.discard(); return; }
        Vec3 from = this.position(), to = from.add(this.getDeltaMovement());
        HitResult h = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Entity boss = sl.getEntity(bossId);
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().expandTowards(this.getDeltaMovement()).inflate(1.2))) {
            if (p.isCreative() || p.isSpectator() || !hit.add(p.getUUID())) continue;
            p.hurt(boss instanceof LivingEntity le ? this.damageSources().mobAttack(le) : this.damageSources().magic(), 12F);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 80, 0, false, false));
            sl.playSound(null, p.blockPosition(), ShadowRegistry.CRUNCH.get(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 1.3F);
        }
        if (h.getType() != HitResult.Type.MISS || (start != null && from.distanceTo(start) > 34)) {
            if (h.getType() != HitResult.Type.MISS) this.setPos(h.getLocation());
            else {
                net.minecraft.core.BlockPos g = ShadowEntity.findStand(sl, from.x, from.y, from.z, 12);
                if (g != null) this.setPos(from.x, g.getY(), from.z);
            }
            this.entityData.set(STUCK, true);
            this.setDeltaMovement(Vec3.ZERO);
            sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.TRIDENT_HIT_GROUND, net.minecraft.sounds.SoundSource.HOSTILE, 2F, 0.6F);
            return;
        }
        this.setPos(to);
        if (this.tickCount % 3 == 0) sl.playSound(null, this.blockPosition(), net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.6F);
    }
}
