package com.mansoority.shadow;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

public class ShadowEvents {
    private static final String KEY_NEXT = "theshadow_next";
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(TheShadow.MODID, "bosses"));

    // ------------------------------------------------------------------ random encounters (rare)
    /** AFK detection: when each player last moved or turned */
    private static final java.util.Map<java.util.UUID, double[]> LAST = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> ACTIVE_AT = new java.util.HashMap<>();

    private static void trackActivity(ServerPlayer p) {
        double[] now = {p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()};
        double[] was = LAST.put(p.getUUID(), now);
        if (was == null || Math.abs(now[0] - was[0]) + Math.abs(now[2] - was[2]) > 0.5
                || Math.abs(now[3] - was[3]) > 8 || Math.abs(now[4] - was[4]) > 8) {
            ACTIVE_AT.put(p.getUUID(), p.tickCount);
        }
    }

    public static boolean isAfk(ServerPlayer p) {
        Integer at = ACTIVE_AT.get(p.getUUID());
        return at == null || p.tickCount - at > 20 * 90;      // nothing for 90 seconds = afk
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 20 == 0) trackActivity(p);
        if (p.tickCount % 40 == 0) ShadowMark.tick(p);
        if (p.tickCount % 100 == 50) ShadowEvents.endBossCheck(p, false);
        if (p.tickCount % 40 == 20) lairCheck(p);
        if (p.tickCount % 20 == 5) ShadowGear.tick(p);
        Long nf = MansoorityBoss.NOFALL.get(p.getUUID());
        if (nf != null) { p.fallDistance = 0; if (p.onGround() && p.level().getGameTime() > nf - 90 || p.level().getGameTime() > nf) MansoorityBoss.NOFALL.remove(p.getUUID()); }
        ShadowEntity.stuckTick(p);
        ShadowCinematic.tick(p);
        if (ShadowCinematic.running(p)) return;
        if (p.tickCount % 1200 != 600) return; // once a minute
        if (!ShadowConfig.ENABLED.get()) return;

        CompoundTag data = p.getPersistentData();
        long now = p.level().getGameTime();
        long cooldown = 20L * 60L * ShadowConfig.MIN_MINUTES.get();
        if (!data.contains(KEY_NEXT)) {
            data.putLong(KEY_NEXT, now + cooldown); // grace period for new players
            return;
        }
        if (now < data.getLong(KEY_NEXT)) return;
        if (insomniaVisit(p, data, now)) return;
        if (!eligible(p)) return;
        double chance = ShadowConfig.CHANCE.get();
        if (ShadowEntity.isDaytimeFor(p)) chance *= 0.35;          // daytime: much rarer
        if (p.getRandom().nextDouble() >= chance) return;

        data.putLong(KEY_NEXT, now + cooldown + p.getRandom().nextInt(20 * 60 * 20));
        double roll = p.getRandom().nextDouble();
        warnAll(p);
        boolean day = ShadowEntity.isDaytimeFor(p);
        // a disabled event just becomes a small scare (or nothing, if those are off too)
        if (roll >= 0.55 && roll < 0.63 && !day && ShadowConfig.GRAB.get()) {   // rare, night only: it picks you up
            if (ShadowEntity.grab(p, p.getRandom().nextBoolean() ? ShadowEntity.GRAB_LICK : ShadowEntity.GRAB_DRAG) == null) soundEvent(p);
            return;
        }
        if (roll < 0.14 && ShadowConfig.MIMIC.get()) {              // a familiar face where it shouldn't be
            if (ShadowEntity.mimicFor(p) == null) soundEvent(p);
        } else if (roll < 0.55 && ShadowConfig.STALK.get()) {       // stalked (only ends in a chase at night)
            if (ShadowEntity.spawnAround(p, 0, 180, 28) == null) soundEvent(p);
        } else if (day || roll < 0.96 || !ShadowConfig.JUMPSCARE.get()) {   // just sounds / something wrong
            soundEvent(p);
        } else {                                                    // very rare: straight jumpscare, night only
            ShadowEntity.jumpscare(p, false);
            ShadowTrails.behind(p);
        }
    }

    /** you haven't slept in days (3+ nights): at night, it comes and stands right next to you */
    private static boolean insomniaVisit(ServerPlayer p, CompoundTag data, long now) {
        int awake = p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_REST));
        if (!ShadowConfig.BEDSIDE.get() || awake < 72000 || p.level().isDay() || !eligible(p)) return false;
        if (p.getRandom().nextFloat() > 0.06F) return false;
        ShadowEntity e = ShadowEntity.spawnAround(p, -1, p.getRandom().nextBoolean() ? 150 : -150, 2.6);
        if (e == null) return false;
        ShadowEntity.ui(p, ShadowRegistry.BREATH, 1F, 0.6F);
        ShadowAdvancements.award(p, "sleep");
        p.displayClientMessage(Component.literal("You should sleep.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC), true);
        warnAll(p);
        data.putLong(KEY_NEXT, now + 20L * 60L * ShadowConfig.MIN_MINUTES.get());
        return true;
    }

    /** the End after the dragon: if you're near the main island, it comes for you */
    public static MansoorityBoss endBossCheck(ServerPlayer p, boolean force) {
        net.minecraft.server.level.ServerLevel sl = p.serverLevel();
        if (sl.dimension() != net.minecraft.world.level.Level.END) return null;
        BossData d = BossData.get(p.server);
        if (!force) {
            if (d.defeated || d.sealed || !ShadowConfig.ENABLED.get() || !ShadowConfig.BOSS.get() || p.isCreative() || p.isSpectator()) return null;
            var fight = sl.getDragonFight();
            if (fight == null || !fight.hasPreviouslyKilledDragon()) return null;
            if (!sl.getEntitiesOfClass(EnderDragon.class, p.getBoundingBox().inflate(300)).isEmpty()) return null;
            if (Math.hypot(p.getX(), p.getZ()) > 150) return null;
            if (sl.getGameTime() - d.lastSpawn < 20 * 60 * 5) return null;
        }
        if (!sl.getEntitiesOfClass(MansoorityBoss.class, p.getBoundingBox().inflate(300)).isEmpty()) return null;
        double a = Math.toRadians(p.getYRot());
        net.minecraft.core.BlockPos s = ShadowEntity.findStand(sl, p.getX() - Math.sin(a) * 24, p.getY(), p.getZ() + Math.cos(a) * 24, 30);
        if (s == null) s = ShadowEntity.findStand(sl, 0.5, 70, 0.5 + 20, 40);
        if (s == null) return null;
        MansoorityBoss b = ShadowRegistry.BOSS.get().create(sl);
        if (b == null) return null;
        b.moveTo(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
        b.setHealth(MansoorityBoss.MAX_HP);
        sl.addFreshEntity(b);
        b.beginIntro();
        d.spawned(sl.getGameTime());
        for (ServerPlayer o : sl.players())
            o.displayClientMessage(Component.literal("Something followed you out of the dark.").withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
        return b;
    }

    /** a dragon fight is on: it climbs a pillar, waits, and takes the kill at the very end */
    private static void dragonWatch(net.minecraft.server.level.ServerLevel end) {
        if (!ShadowConfig.ENABLED.get() || !ShadowConfig.BOSS.get() || !ShadowConfig.STEAL.get()) return;
        BossData d = BossData.get(end.getServer());
        if (d.defeated || d.sealed) return;
        var fight = end.getDragonFight();
        if (fight == null) return;
        if (!end.getEntitiesOfClass(MansoorityBoss.class, new net.minecraft.world.phys.AABB(-300, 0, -300, 300, 320, 300)).isEmpty()) return;
        for (EnderDragon dragon : end.getEntitiesOfClass(EnderDragon.class, new net.minecraft.world.phys.AABB(-300, 0, -300, 300, 320, 300))) {
            if (!dragon.isAlive() || dragon.getHealth() > dragon.getMaxHealth() * 0.25F) continue;
            boolean players = !end.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(-150, 0, -150, 150, 320, 150), q -> !q.isCreative() && !q.isSpectator()).isEmpty();
            if (!players) return;
            var spikes = net.minecraft.world.level.levelgen.feature.SpikeFeature.getSpikesForLevel(end);
            if (spikes.isEmpty()) return;
            var sp = spikes.get(end.random.nextInt(spikes.size()));
            MansoorityBoss b = ShadowRegistry.BOSS.get().create(end);
            if (b == null) return;
            b.moveTo(sp.getCenterX() + 0.5, sp.getHeight() + 1, sp.getCenterZ() + 0.5, 0, 0);
            b.setHealth(MansoorityBoss.MAX_HP);
            end.addFreshEntity(b);
            b.beginSteal(dragon, sp);
            d.spawned(end.getGameTime());
            return;
        }
    }

    /** sealed in its tower: five TNT going off at the foot of it wake it up */
    @SubscribeEvent
    public void onExplode(net.minecraftforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) || sl.dimension() != net.minecraft.world.level.Level.END) return;
        BossData d = BossData.get(sl.getServer());
        if (!d.sealed) return;
        var pos = event.getExplosion().getPosition();
        if (Math.hypot(pos.x - d.sealedAt.getX(), pos.z - d.sealedAt.getZ()) > 14) return;
        if (!(event.getExplosion().getDirectSourceEntity() instanceof net.minecraft.world.entity.item.PrimedTnt)) return;
        d.tnt++; d.setDirty();
        for (ServerPlayer p : sl.players()) if (p.distanceToSqr(pos) < 80 * 80)
            p.displayClientMessage(Component.literal(d.tnt >= 5 ? "It's awake." : "Something inside the tower moves. (" + d.tnt + "/5)")
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), true);
        if (d.tnt < 5) return;
        d.sealed = false; d.tnt = 0; d.setDirty();
        MansoorityBoss b = ShadowRegistry.BOSS.get().create(sl);
        if (b == null) return;
        var g = ShadowEntity.findStand(sl, d.sealedAt.getX() + 6.5, d.sealedAt.getY(), d.sealedAt.getZ() + 0.5, 120);
        if (g == null) g = d.sealedAt;
        b.moveTo(g.getX() + 0.5, g.getY(), g.getZ() + 0.5, 0, 0);
        sl.addFreshEntity(b);
        b.restoreHp(d.sealedHp);
        b.beginIntro();
    }

    /** someone died in the fight: it counts */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != net.minecraft.world.level.Level.END && !HollowPlace.in(p)) return;
        net.minecraft.server.level.ServerLevel end = p.server.getLevel(net.minecraft.world.level.Level.END);
        if (end == null) return;
        for (MansoorityBoss b : end.getEntitiesOfClass(MansoorityBoss.class, new net.minecraft.world.phys.AABB(-400, -64, -400, 400, 400, 400))) {
            if (HollowPlace.in(p) || b.distanceToSqr(p) < 120 * 120) { b.onPlayerKilled(p); return; }
        }
    }

    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.level.levelgen.structure.Structure> LAIR =
            net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(TheShadow.MODID, "lair"));

    /** walking into its lair: always ends with it coming for you */
    private static void lairCheck(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || !ShadowConfig.ENABLED.get() || !ShadowConfig.LAIR.get() || !awakened(p.server)) return;
        net.minecraft.server.level.ServerLevel sl = p.serverLevel();
        var reg = sl.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var st = reg.get(LAIR);
        if (st == null) return;
        if (!sl.structureManager().getStructureWithPieceAt(p.blockPosition(), st).isValid()) return;
        CompoundTag d = p.getPersistentData();
        long now = sl.getGameTime();
        if (now - d.getLong("theshadow_lair") < 20L * 60 * 20) return;            // once per visit (20 min)
        if (ShadowEntity.anyNear(p, 160)) return;
        d.putLong("theshadow_lair", now);
        ShadowAdvancements.award(p, "lair");
        warnAll(p);
        ShadowNet.send(p, FxPacket.of(FxPacket.POLTER));                          // its candles go out
        ShadowEntity e = ShadowEntity.spawnAround(p, 2, 180, 7);                    // and it's already behind you
        if (e == null) ShadowEntity.rushFrom(p, 20);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (sl.dimension() == net.minecraft.world.level.Level.END && sl.getGameTime() % 20 == 0) dragonWatch(sl);
    }

    @SubscribeEvent
    public void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && (MansoorityBoss.NOFALL.containsKey(p.getUUID()) || ShadowEntity.GRABBED.contains(p.getUUID()))) event.setCanceled(true);
    }

    /** nothing hurts you while it's holding you (falling, suffocating in a wall, mobs) */
    @SubscribeEvent
    public void onAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && (ShadowEntity.GRABBED.contains(p.getUUID()) || ShadowCinematic.running(p))) { event.setCanceled(true); return; }
        // the Pale Ward: anything shot at you is turned aside
        if (event.getEntity() instanceof ServerPlayer p && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile pr
                && ShadowGear.warded(p, pr)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player)
            event.setAmount(ShadowGear.reduce(event.getEntity(), event.getSource().getEntity(), event.getAmount()));
    }

    private static final String[] WARNINGS = {
            "Something is awake tonight.", "One of you is being watched.", "It has chosen someone.",
            "Don't look at it.", "Someone is not alone right now.", "It's closer than you think.",
            "Check behind you.", "It knows your name.", "Someone just heard it breathing."};

    /** an anonymous warning in everyone's chat: it could be anyone */
    public static void warnAll(ServerPlayer p) {
        String msg = WARNINGS[p.getRandom().nextInt(WARNINGS.length)];
        p.server.getPlayerList().broadcastSystemMessage(
                Component.literal(msg).withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
    }

    /** has anyone on this world killed the Ender Dragon yet? until then it doesn't exist */
    public static boolean awakened(net.minecraft.server.MinecraftServer server) {
        if (!ShadowConfig.AFTER_DRAGON.get()) return true;
        net.minecraft.server.level.ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
        return end != null && end.getDragonFight() != null && end.getDragonFight().hasPreviouslyKilledDragon();
    }

    private static boolean eligible(ServerPlayer p) {
        if (!awakened(p.server)) return false;
        if (p.isCreative() || p.isSpectator() || p.isSleeping()) return false;
        if (isAfk(p)) return false;                                            // you have to actually be playing
        if (p.tickCount < 24000) return false;                                 // first 20 min after joining: nothing
        // big fights only (normal mobs are fine): vanilla bosses, the boss tag, or anything with a huge health pool
        boolean boss = !p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(80),
                e -> !(e instanceof ShadowEntity) && e.isAlive() && (e instanceof EnderDragon || e instanceof WitherBoss
                        || e.getType().is(BOSSES) || e.getMaxHealth() >= 150F)).isEmpty();
        if (boss) return false;
        int players = p.serverLevel().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(32), pl -> !pl.isSpectator()).size();
        if (players > ShadowConfig.MAX_WITNESSES.get()) return false;
        return !ShadowEntity.anyNear(p, 160);
    }

    private static void soundEvent(ServerPlayer p) {
        if (!ShadowConfig.SCARES.get()) return;
        float k = p.getRandom().nextFloat();
        if (k < 0.18F) { ShadowNet.send(p, FxPacket.of(FxPacket.POLTER)); ShadowTrails.behind(p); return; }
        if (k < 0.26F) { ShadowNet.send(p, FxPacket.of(FxPacket.MIRROR)); return; }
        if (k < 0.33F) { ShadowNet.send(p, FxPacket.of(FxPacket.DEATHMSG)); return; }
        int id = p.getRandom().nextInt(7);
        FxPacket pkt = new FxPacket(FxPacket.SEQUENCE, id, 0, 0, 0, 1, 1);
        ShadowTrails.behind(p);
        ShadowNet.send(p, pkt);
        for (ServerPlayer o : ShadowEntity.friendsNear(p)) ShadowNet.send(o, pkt);
    }

    // ------------------------------------------------------------------ admin / test commands
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        for (String root : new String[]{"mansoority", "shadow"})
        d.register(Commands.literal(root).requires(s -> s.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String a : new String[]{"cinematic", "test", "stalk", "mimic", "grab", "lick", "drag", "polter", "mirror", "deathmsg", "mark", "boss", "lair", "rush", "bed", "scare", "event", "despawn", "soon", "reset"}) b.suggest(a);
                            return b.buildFuture();
                        })
                        .executes(c -> run(c, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(c -> run(c, EntityArgument.getPlayer(c, "player"))))));
    }

    private static int run(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
        String action = StringArgumentType.getString(c, "action");
        CommandSourceStack src = c.getSource();
        switch (action) {
            case "test" -> {
                ShadowEntity e = ShadowEntity.spawnAround(p, 1, 0, 9);
                reply(src, e != null ? "It's in front of " + p.getScoreboardName() + ". Stare at it." : "No room to spawn it there.");
            }
            case "stalk" -> {
                ShadowEntity e = ShadowEntity.spawnAround(p, 0, 180, 28);
                reply(src, e != null ? "It's watching " + p.getScoreboardName() + " from behind." : "No room to spawn it there.");
            }
            case "cinematic" -> {
                ShadowCinematic.start(p);
                reply(src, "Lights down. Starring " + p.getScoreboardName() + ".");
            }
            case "grab", "lick", "drag" -> {
                int mode = action.equals("drag") ? ShadowEntity.GRAB_DRAG : action.equals("lick") ? ShadowEntity.GRAB_LICK
                        : (p.getRandom().nextBoolean() ? ShadowEntity.GRAB_LICK : ShadowEntity.GRAB_DRAG);
                ShadowEntity e = ShadowEntity.grab(p, mode);
                reply(src, e != null ? "Don't turn around." : "No room to spawn it there.");
            }
            case "mimic" -> {
                ShadowEntity e = ShadowEntity.mimicFor(p);
                reply(src, e != null ? "Someone's out there." : "No room to spawn it there.");
            }
            case "rush" -> {
                ShadowEntity e = ShadowEntity.rushFrom(p, 24);
                reply(src, e != null ? "Run." : "No room to spawn it there.");
            }
            case "bed" -> {
                ShadowEntity e = ShadowEntity.spawnAround(p, -1, 60, 2.5);
                reply(src, e != null ? "It's right next to " + p.getScoreboardName() + "." : "No room to spawn it there.");
            }
            case "scare" -> { ShadowEntity.jumpscare(p, false); reply(src, "Jumpscare sent."); }
            case "event" -> { soundEvent(p); reply(src, "Sound event sent."); }
            case "polter" -> { ShadowNet.send(p, FxPacket.of(FxPacket.POLTER)); reply(src, "Something moved."); }
            case "mirror" -> { ShadowNet.send(p, FxPacket.of(FxPacket.MIRROR)); reply(src, "Press F5."); }
            case "deathmsg" -> { ShadowNet.send(p, FxPacket.of(FxPacket.DEATHMSG)); reply(src, "Check chat."); }
            case "lair" -> reply(src, "Use: /locate structure theshadow:lair  (it's very rare - forests, taigas, swamps)");
            case "boss" -> {
                MansoorityBoss b = endBossCheck(p, true);
                reply(src, b != null ? "It's here." : "Only in the End (and only one at a time).");
            }
            case "mark" -> { ShadowMark.mark(p); reply(src, p.getScoreboardName() + " has been marked."); }
            case "despawn" -> {
                ShadowCinematic.stop(p);
                p.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), e -> true).forEach(e -> e.discard());
                reply(src, "Gone. For now.");
            }
            case "soon" -> {
                p.getPersistentData().putLong(KEY_NEXT, 0L);
                reply(src, "Cooldown cleared for " + p.getScoreboardName() + ".");
            }
            case "reset" -> {
                p.getPersistentData().putLong(KEY_NEXT, p.level().getGameTime() + 20L * 60L * ShadowConfig.MIN_MINUTES.get());
                reply(src, "Cooldown restarted for " + p.getScoreboardName() + ".");
            }
            default -> { reply(src, "Use: test, stalk, rush, bed, scare, event, despawn, soon, reset"); return 0; }
        }
        return 1;
    }

    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
    }
}
