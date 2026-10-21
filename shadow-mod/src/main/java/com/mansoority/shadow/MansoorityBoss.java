package com.mansoority.shadow;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * the End, after the dragon: Mansoority comes out of the dark for the people who killed it.
 * its own blows can never take you below half a heart. its shades can kill you.
 */
public class MansoorityBoss extends ShadowEntity {
    public static final float MAX_HP = 700F;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Mansoority").withStyle(net.minecraft.ChatFormatting.DARK_RED),
            BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20);
    private int t, attackCd = 100, summonCd = 160, darkCd = 300, alone, emerge = 80, blink = -1, dying = -1;
    private int charge = -1, grabT = -1, stareT = -1;
    private ServerPlayer focus;
    /** cinematic version: same fight, nothing is saved, it leaves when the scene ends */
    public boolean showcase;
    private Vec3 chargeDir, blinkTo;

    public MansoorityBoss(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(false);
        bar.setDarkenScreen(true);
        bar.setCreateWorldFog(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, MAX_HP).add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 128.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    private float phaseHp() { return this.getHealth() / MAX_HP; }
    private int phase() { float h = phaseHp(); return h > 0.6F ? 1 : h > 0.25F ? 2 : 3; }

    @Override
    protected boolean customTick() {
        ServerLevel sl = (ServerLevel) this.level();
        t++;
        setStageData(STAGE_BOSS);
        List<ServerPlayer> near = sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(96), pl -> !pl.isSpectator() && !pl.isCreative());
        for (ServerPlayer pl : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(96))) bar.addPlayer(pl);
        for (ServerPlayer pl : List.copyOf(bar.getPlayers())) if (pl.distanceToSqr(this) > 110 * 110 || pl.level() != sl) bar.removePlayer(pl);
        bar.setProgress(Math.max(0F, this.getHealth() / MAX_HP));
        if (dying >= 0) { dyingTick(sl); return true; }
        if (near.isEmpty()) { if (++alone > 20 * 40) { this.discard(); } return true; }
        alone = 0;
        if (focus == null || !focus.isAlive() || focus.isSpectator() || focus.distanceToSqr(this) > 96 * 96 || t % 200 == 0)
            focus = near.get(this.random.nextInt(near.size()));
        setTargetId(focus.getId());
        if (emerge > 0) {                                      // stepping out of the portal
            emerge--;
            face(focus);
            if (emerge % 10 == 0) sound(ShadowRegistry.SNAP, 3F, 0.5F);
            if (emerge == 1) { sound(ShadowRegistry.SCREAM, 5F, 0.55F); for (ServerPlayer pl : near) ShadowNet.send(pl, FxPacket.of(FxPacket.JUMPSCARE_NOW)); }
            return true;
        }
        if (blink >= 0) { blinkTick(sl); return true; }
        if (grabT >= 0) { grabTick2(sl); return true; }
        if (charge >= 0) { chargeTick(sl, near); return true; }
        if (stareT >= 0) { stareTick(sl); return true; }

        // otherwise: circle the players, always facing whoever it has chosen
        face(focus);
        setScuttle(0);
        Vec3 c = centroid(near);
        double ang = t * (0.012 + phase() * 0.004) + this.getId();
        double r = 20 + 4 * Math.sin(t * 0.03);
        Vec3 want = new Vec3(c.x + Math.cos(ang) * r, this.getY(), c.z + Math.sin(ang) * r);
        stepToward(sl, want, 0.18 + phase() * 0.07);

        if (--summonCd <= 0) { summon(sl, phase() == 1 ? 2 : phase() == 2 ? 3 : 4); summonCd = phase() == 1 ? 320 : 240; }
        if (phase() >= 2 && --darkCd <= 0) {
            darkCd = 340;
            for (ServerPlayer pl : near) pl.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
            sound(ShadowRegistry.DRONE, 5F, 0.5F);
        }
        if (--attackCd <= 0) {
            attackCd = phase() == 1 ? 90 : phase() == 2 ? 65 : 45;
            float k = this.random.nextFloat();
            if (phase() >= 2 && k < 0.25F) startBlink(sl, near);
            else if (phase() == 3 && k < 0.45F) { stareT = 0; setVariantData(0); }
            else if (focus.distanceTo(this) < 9 && k < 0.7F) startGrab();
            else startCharge();
        }
        return true;
    }

    // ---------------------------------------------------------------- moves
    private void startCharge() {
        charge = 0;
        chargeDir = focus.position().subtract(this.position()).multiply(1, 0, 1).normalize();
        sound(ShadowRegistry.SCREAM, 4F, 0.8F);
    }

    private void chargeTick(ServerLevel sl, List<ServerPlayer> near) {
        charge++;
        setScuttle(charge + 8);
        if (charge < 12) { face(focus); chargeDir = focus.position().subtract(this.position()).multiply(1, 0, 1).normalize(); return; }   // crouch, aim
        double sp = 0.55 + phase() * 0.12;
        stepToward(sl, this.position().add(chargeDir.scale(4)), sp);
        float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
        if (charge % 3 == 0) sound(ShadowRegistry.SNAP, 2.5F, 1.3F);
        for (ServerPlayer pl : near) {
            if (pl.distanceTo(this) < 3.6) {
                hit(pl, 6F + phase() * 2F);
                pl.knockback(1.6, -chargeDir.x, -chargeDir.z);
                pl.hurtMarked = true;
                charge = 999;
            }
        }
        if (charge > 40) { charge = -1; setScuttle(0); }
    }

    private void startGrab() {
        grabT = 0;
        face(focus);
        sound(ShadowRegistry.BREATH, 3F, 0.5F);
    }

    /** picks you up to its face... and throws you */
    private void grabTick2(ServerLevel sl) {
        grabT++;
        face(focus);
        setGrab(grabT, GRAB_LICK);
        if (grabT == 22) {
            if (focus.distanceTo(this) > 10) { grabT = -1; setGrab(0, 0); return; }
            GRABBED.add(focus.getUUID());
            focus.startRiding(this, true);
            ShadowEntity.ui(focus, ShadowRegistry.HIT, 1F, 0.8F);
        }
        if (grabT > 22 && grabT < 58 && !focus.isPassenger()) focus.startRiding(this, true);
        if (grabT == 58) {
            focus.stopRiding();
            GRABBED.remove(focus.getUUID());
            NOFALL.put(focus.getUUID(), sl.getGameTime() + 100);
            double yaw = Math.toRadians(this.getYRot() + (this.random.nextBoolean() ? 70 : -70));
            focus.setDeltaMovement(-Math.sin(yaw) * 1.7, 0.8, Math.cos(yaw) * 1.7);
            focus.hurtMarked = true;
            hit(focus, 5F);
            sound(ShadowRegistry.SCREAM, 4F, 0.9F);
            grabT = -1; setGrab(0, 0);
        }
    }

    private void startBlink(ServerLevel sl, List<ServerPlayer> near) {
        Vec3 c = centroid(near);
        double a = this.random.nextDouble() * Math.PI * 2;
        BlockPos s = findStand(sl, c.x + Math.cos(a) * 14, c.y, c.z + Math.sin(a) * 14, 20);
        if (s == null) return;
        blinkTo = new Vec3(s.getX() + 0.5, s.getY(), s.getZ() + 0.5);
        blink = 0;
        sound(ShadowRegistry.RISER, 4F, 0.6F);
    }

    /** into the portal, out somewhere else */
    private void blinkTick(ServerLevel sl) {
        blink++;
        setVanishTick(blink <= 17 ? blink : 34 - blink);
        if (blink == 17) { this.teleportTo(blinkTo.x, blinkTo.y, blinkTo.z); face(focus); }
        if (blink >= 33) { blink = -1; setVanishTick(0); summon(sl, 1); }
    }

    /** final phase: it makes you look at it. while you do, it hurts */
    private void stareTick(ServerLevel sl) {
        stareT++;
        face(focus);
        setStare(Math.min(STARE_LIMIT - 1, stareT));
        if (stareT == 1) turnHead(focus);
        if (stareT % 20 == 0 && isWatchedBy(focus)) {
            hit(focus, 2F);
            focus.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
            ShadowEntity.ui(focus, ShadowRegistry.STATIC, 1F, 0.8F);
        }
        if (stareT > 70) { stareT = -1; setStare(0); }
    }

    private void summon(ServerLevel sl, int n) {
        int alive = sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(80)).size();
        for (int i = 0; i < n && alive + i < 8; i++) {
            double a = this.random.nextDouble() * Math.PI * 2;
            Vec3 c = focus.position();
            BlockPos s = findStand(sl, c.x + Math.cos(a) * 7, c.y, c.z + Math.sin(a) * 7, 10);
            if (s == null) continue;
            ShadeEntity sh = ShadowRegistry.SHADE.get().create(sl);
            if (sh == null) continue;
            sh.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, this.random.nextFloat() * 360F, 0);
            sh.arm();
            sh.setTarget(focus);
            sl.addFreshEntity(sh);
            sl.sendParticles(ShadowRegistry.WISP.get(), sh.getX(), sh.getY() + 1, sh.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
            sl.playSound(null, sh.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1F, 0.4F);
        }
        sound(ShadowRegistry.WHISPER, 4F, 0.5F);
    }

    // ---------------------------------------------------------------- damage both ways
    /** its own hits: never below half a heart */
    private void hit(ServerPlayer pl, float dmg) {
        if (pl.isCreative() || pl.isSpectator()) return;
        float allowed = Math.min(dmg, pl.getHealth() - 1.0F);
        if (allowed > 0) pl.hurt(this.damageSources().mobAttack(this), allowed);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || dying >= 0 || emerge > 0 || blink >= 0) return false;
        Entity src = source.getEntity();
        boolean byPlayer = src instanceof Player || (source.getDirectEntity() instanceof Projectile pr && pr.getOwner() instanceof Player);
        if (!byPlayer && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        this.setHealth(this.getHealth() - amount);
        sound(ShadowRegistry.HIT, 3F, 0.7F + this.random.nextFloat() * 0.3F);
        if (this.random.nextFloat() < 0.15F) sound(ShadowRegistry.SCREAM, 3F, 1.1F);
        if (this.getHealth() <= 0F) startDying();
        return true;
    }

    private void startDying() {
        dying = 0;
        this.setHealth(1F);
        sound(ShadowRegistry.SCREAM, 6F, 0.5F);
    }

    private void dyingTick(ServerLevel sl) {
        dying++;
        setStare(Math.min(STARE_LIMIT - 1, dying * 2));
        if (dying % 4 == 0) sound(ShadowRegistry.SNAP, 3F, 0.4F + this.random.nextFloat() * 0.4F);
        if (dying == 60) setVanishTick(1);
        if (dying > 60) setVanishTick(Math.min(VANISH_LEN - 1, dying - 59));
        if (dying >= 60 + VANISH_LEN - 1) {
            for (ShadeEntity sh : sl.getEntitiesOfClass(ShadeEntity.class, this.getBoundingBox().inflate(100))) sh.discard();
            sl.addFreshEntity(new ItemEntity(sl, this.getX(), this.getY() + 2, this.getZ(), new ItemStack(ShadowRegistry.SHADOW_BLADE.get())));
            ExperienceOrb.award(sl, this.position().add(0, 2, 0), 1500);
            for (ServerPlayer pl : sl.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(128))) ShadowAdvancements.award(pl, "boss");
            if (!showcase) BossData.get(sl.getServer()).setDefeated();
            sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal("Mansoority has been driven back into the dark. For now.")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        bar.removeAllPlayers();
        super.remove(reason);
    }

    // ---------------------------------------------------------------- helpers
    private void face(Entity e) {
        double dx = e.getX() - this.getX(), dz = e.getZ() - this.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90F;
        this.setYRot(yaw); this.yBodyRot = yaw; this.yHeadRot = yaw;
    }

    private void stepToward(ServerLevel sl, Vec3 want, double speed) {
        Vec3 d = want.subtract(this.position());
        double h = Math.hypot(d.x, d.z);
        if (h < 0.2) return;
        double st = Math.min(speed, h);
        double nx = this.getX() + d.x / h * st, nz = this.getZ() + d.z / h * st;
        BlockPos g = findStand(sl, nx, this.getY(), nz, 4);
        if (g == null) return;                                   // never walks off the island
        this.setPos(nx, this.getY() + (g.getY() - this.getY()) * 0.35, nz);
    }

    private static Vec3 centroid(List<ServerPlayer> ps) {
        double x = 0, y = 0, z = 0;
        for (ServerPlayer p : ps) { x += p.getX(); y += p.getY(); z += p.getZ(); }
        return new Vec3(x / ps.size(), y / ps.size(), z / ps.size());
    }

    /** players it just threw: no fall damage until they land */
    public static final java.util.Map<java.util.UUID, Long> NOFALL = new java.util.HashMap<>();

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
}
