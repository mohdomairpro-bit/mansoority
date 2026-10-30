package com.mansoority.shadow;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * a shed skin of him that never stopped moving: drags itself along on its arms, bites, and is easy to put down.
 * when it dies it bursts and leaves a pool of bile that slows anyone standing in it.
 */
public class HollowHusk extends Monster {
    public HollowHusk(EntityType<? extends Monster> t, Level l) {
        super(t, l);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0D).add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.21D).add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 0), this);
        return ok;
    }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (this.level().isClientSide) return;
        AreaEffectCloud c = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
        c.setRadius(2.6F); c.setRadiusOnUse(-0.1F); c.setWaitTime(4); c.setDuration(180);
        c.setRadiusPerTick(-c.getRadius() / 180F);
        c.setParticle(ShadowRegistry.BILE.get());
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        this.level().addFreshEntity(c);
        this.playSound(ShadowRegistry.SPRAY.get(), 1.2F, 1.4F);
    }

    @Override protected SoundEvent getAmbientSound() { return SoundEvents.HUSK_AMBIENT; }
    @Override protected SoundEvent getHurtSound(DamageSource s) { return ShadowRegistry.CRACK.get(); }
    @Override protected SoundEvent getDeathSound() { return ShadowRegistry.GURGLE.get(); }
    @Override public float getVoicePitch() { return 0.55F + this.random.nextFloat() * 0.15F; }
}
