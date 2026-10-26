package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** a glob of web from a spitter: hurts a little, slows a lot, and leaves the floor sticky where it lands */
public class WebShot extends ThrowableItemProjectile {
    public WebShot(EntityType<? extends WebShot> type, Level level) { super(type, level); }
    public WebShot(Level level, LivingEntity owner) { super(ShadowRegistry.WEB_SHOT.get(), owner, level); }

    @Override protected Item getDefaultItem() { return ShadowRegistry.PALE_SILK.get(); }
    @Override protected float getGravity() { return 0.04F; }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (r.getEntity() instanceof PaleSpider) return;
        r.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 3F);
        if (r.getEntity() instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1), this.getOwner());
        web(r.getEntity().blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        web(r.getDirection() == Direction.UP ? r.getBlockPos().above() : r.getBlockPos().relative(r.getDirection()));
    }

    private void web(BlockPos at) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        for (int dy = 0; dy >= -3; dy--) {                         // settle onto the floor below
            BlockPos p = at.offset(0, dy, 0);
            if (WebFloorBlock.placeTemp(sl, p)) break;
            if (!sl.getBlockState(p).isAir()) break;
        }
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem())), this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.08);
            this.discard();
        }
    }
}
