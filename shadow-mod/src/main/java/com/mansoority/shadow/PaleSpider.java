package com.mansoority.shadow;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * its white spiders. biters run you down and bite (it slows you); spitters hang back and spit web that sticks
 * you to the floor. both climb, both hunt day and night, both drop pale silk.
 */
public class PaleSpider extends Spider implements RangedAttackMob {
    public final boolean spitter;

    public PaleSpider(EntityType<? extends Spider> type, Level level) {
        super(type, level);
        this.spitter = type == ShadowRegistry.PALE_SPITTER.get();
        this.xpReward = spitter ? 8 : 7;
    }

    public static AttributeSupplier.Builder biter() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 22.0D).add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D).add(Attributes.FOLLOW_RANGE, 40.0D).add(Attributes.ARMOR, 2.0D);
    }

    public static AttributeSupplier.Builder spitter() {
        return Spider.createAttributes().add(Attributes.MAX_HEALTH, 16.0D).add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D).add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        if (isSpitterType()) {
            this.goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0D, 34, 48, 14F));
        } else {
            this.goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.42F));
            this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.15D, true));
        }
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12F));
        this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, PaleSpider.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** registerGoals runs inside the super constructor, before `spitter` is set */
    private boolean isSpitterType() { return this.getType() == ShadowRegistry.PALE_SPITTER.get(); }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 0), this);
        return ok;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        WebShot s = new WebShot(this.level(), this);
        double dx = target.getX() - this.getX(), dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3) - s.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        s.shoot(dx, dy + flat * 0.18, dz, 1.1F, 4F);
        this.playSound(ShadowRegistry.SPRAY.get(), 0.7F, 1.7F + this.random.nextFloat() * 0.3F);
        this.level().addFreshEntity(s);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance e) {
        return e.getEffect() != MobEffects.POISON && super.canBeAffected(e);
    }

    @Override
    public void makeStuckInBlock(net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.phys.Vec3 v) {
        if (!(s.getBlock() instanceof WebFloorBlock)) super.makeStuckInBlock(s, v);    // their own web doesn't hold them
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return SoundEvents.SPIDER_AMBIENT; }
    @Override
    public float getVoicePitch() { return (spitter ? 1.35F : 1.15F) + this.random.nextFloat() * 0.2F; }
}
