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
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> YANK =
            net.minecraft.network.syncher.SynchedEntityData.defineId(PaleSpider.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> YANK_AT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(PaleSpider.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CLING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(PaleSpider.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private int yankCd = 60, clingCd = 200, ceilingY;
    private boolean dropping;
    /** client-side animation state */
    public float walk, walkO;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(YANK, 0); this.entityData.define(YANK_AT, -1); this.entityData.define(CLING, false);
    }
    /** ticks into a tentacle yank (0 = none) and who it's aimed at */
    public int yank() { return this.entityData.get(YANK); }
    public int yankTarget() { return this.entityData.get(YANK_AT); }
    /** hanging upside down from a ceiling or canopy, waiting for someone to walk underneath */
    public boolean clinging() { return this.entityData.get(CLING); }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            walkO = walk;
            walk += (float) Math.hypot(this.getX() - this.xo, this.getZ() - this.zo) * 3.2F;
            return;
        }
        if (yankCd > 0) yankCd--;
        if (clingCd > 0) clingCd--;
        net.minecraft.world.entity.LivingEntity tgt = this.getTarget();
        // 1) the yank: tentacles shoot out, catch you, and drag you in
        int y = yank();
        if (y > 0) {
            this.entityData.set(YANK, y + 1);
            this.getNavigation().stop();
            net.minecraft.world.entity.Entity t = this.level().getEntity(yankTarget());
            if (t != null) this.getLookControl().setLookAt(t, 60F, 60F);
            if (y == 10 && t instanceof net.minecraft.world.entity.LivingEntity le && le.distanceTo(this) < 13 && this.hasLineOfSight(le)) {
                net.minecraft.world.phys.Vec3 d = this.position().subtract(le.position()).multiply(1, 0, 1).normalize();
                le.setDeltaMovement(d.x * 1.15, 0.38, d.z * 1.15);
                le.hurtMarked = true;
                le.hurt(this.damageSources().mobAttack(this), 2F);
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 1), this);
                this.playSound(net.minecraft.sounds.SoundEvents.SLIME_SQUISH, 1.2F, 0.6F);
            }
            if (y >= 22) { this.entityData.set(YANK, 0); this.entityData.set(YANK_AT, -1); }
            return;
        }
        if (!clinging() && tgt != null && yankCd <= 0 && this.onGround()) {
            double d = this.distanceTo(tgt);
            if (d > 3.5 && d < 11 && this.hasLineOfSight(tgt) && this.random.nextInt(10) == 0) {
                yankCd = 140 + this.random.nextInt(60);
                this.entityData.set(YANK, 1); this.entityData.set(YANK_AT, tgt.getId());
                this.playSound(ShadowRegistry.SCREECH.get(), 0.7F, 1.8F);
                return;
            }
        }
        // 2) the ambush: up onto a ceiling (or into the tree tops), still, until someone walks underneath
        if (clinging()) {
            this.getNavigation().stop();
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            this.setPos(this.getX(), ceilingY - this.getBbHeight() - 0.02, this.getZ());
            Player under = this.level().getNearestPlayer(this.getX(), this.getY() - 4, this.getZ(), 3.0, p -> !((Player) p).isCreative() && !p.isSpectator());
            if (under != null && under.getY() < this.getY() && this.getY() - under.getY() < 10) {
                this.entityData.set(CLING, false);
                this.setNoGravity(false);
                this.setDeltaMovement(0, -0.7, 0);
                dropping = true;
                this.setTarget(under);
                this.playSound(ShadowRegistry.SCREECH.get(), 1.0F, 1.5F);
            }
            return;
        }
        if (dropping && this.onGround()) {
            dropping = false;
            for (Player p : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(1.2, 0.5, 1.2))) {
                p.hurt(this.damageSources().mobAttack(this), 5F);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1), this);
                p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0), this);
            }
        }
        if (tgt == null && clingCd <= 0 && this.random.nextInt(40) == 0) {
            clingCd = 300;
            net.minecraft.core.BlockPos b = this.blockPosition();
            for (int dy = 2; dy <= 7; dy++) {
                net.minecraft.core.BlockPos c = b.above(dy);
                var st = this.level().getBlockState(c);
                if (st.isAir()) continue;
                if (st.isFaceSturdy(this.level(), c, net.minecraft.core.Direction.DOWN) || st.is(net.minecraft.tags.BlockTags.LEAVES)) {
                    boolean room = true;
                    for (int k = 1; k < dy; k++) if (!this.level().getBlockState(b.above(k)).getCollisionShape(this.level(), b.above(k)).isEmpty()) room = false;
                    if (room) {
                        ceilingY = c.getY();
                        this.entityData.set(CLING, true);
                        this.setNoGravity(true);
                        this.setPos(this.getX(), ceilingY - this.getBbHeight() - 0.02, this.getZ());
                    }
                }
                break;
            }
        }
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float a) {
        if (clinging() && !this.level().isClientSide) { this.entityData.set(CLING, false); this.setNoGravity(false); dropping = true; }
        return super.hurt(s, a);
    }

    @Override
    public boolean causeFallDamage(float d, float m, net.minecraft.world.damagesource.DamageSource s) { return false; }

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
