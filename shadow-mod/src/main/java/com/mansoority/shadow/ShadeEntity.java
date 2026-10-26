package com.mansoority.shadow;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** its shades: small, fast, black, glowing eyes, black glowing swords. these CAN kill you */
public class ShadeEntity extends Monster {
    public ShadeEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 12;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 22.0D).add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D).add(Attributes.FOLLOW_RANGE, 48.0D).add(Attributes.ARMOR, 4.0D);
    }

    public void arm() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ShadowRegistry.SHADOW_BLADE.get()));
        this.setDropChance(EquipmentSlot.MAINHAND, 0F);          // the blade only ever drops from Mansoority itself
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.9D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 50, 0, false, false));
        return ok;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() { return ShadowRegistry.WHISPER.get(); }
    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource s) { return ShadowRegistry.SNAP.get(); }
    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() { return ShadowRegistry.STATIC.get(); }
    @Override
    protected float getSoundVolume() { return 0.6F; }
    @Override
    public float getVoicePitch() { return 1.3F + this.random.nextFloat() * 0.3F; }
    @Override
    public boolean removeWhenFarAway(double d) { return false; }
}
