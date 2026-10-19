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
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
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
        if (!eligible(p)) return;
        double chance = ShadowConfig.CHANCE.get();
        if (ShadowEntity.isDaytimeFor(p)) chance *= 0.35;          // daytime: much rarer
        if (p.getRandom().nextDouble() >= chance) return;

        data.putLong(KEY_NEXT, now + cooldown + p.getRandom().nextInt(20 * 60 * 20));
        double roll = p.getRandom().nextDouble();
        warnAll(p);
        boolean day = ShadowEntity.isDaytimeFor(p);
        if (roll < 0.50) {                                          // stalked (only ends in a chase at night)
            if (ShadowEntity.spawnAround(p, 0, 180, 28) == null) soundEvent(p);
        } else if (day || roll < 0.96) {                            // just sounds / something wrong
            soundEvent(p);
        } else {                                                    // very rare: straight jumpscare, night only
            ShadowEntity.jumpscare(p, false);
        }
    }

    /** rare: you wake up and it's standing over your bed */
    @SubscribeEvent
    public void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.wakeImmediately()) return;
        if (!ShadowConfig.ENABLED.get() || p.isCreative() || p.isSpectator() || isAfk(p)) return;
        CompoundTag data = p.getPersistentData();
        long now = p.level().getGameTime();
        if (data.contains(KEY_NEXT) && now < data.getLong(KEY_NEXT)) return;
        if (p.getRandom().nextFloat() > 0.04F || ShadowEntity.anyNear(p, 160)) return;
        p.server.execute(() -> {
            ShadowEntity e = ShadowEntity.spawnAround(p, -1, p.getRandom().nextBoolean() ? 60 : -60, 3.5);
            if (e != null) warnAll(p);
            if (e != null) data.putLong(KEY_NEXT, now + 20L * 60L * ShadowConfig.MIN_MINUTES.get());
        });
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

    private static boolean eligible(ServerPlayer p) {
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
        int id = p.getRandom().nextInt(7);
        FxPacket pkt = new FxPacket(FxPacket.SEQUENCE, id, 0, 0, 0, 1, 1);
        ShadowNet.send(p, pkt);
        for (ServerPlayer o : ShadowEntity.friendsNear(p)) ShadowNet.send(o, pkt);
    }

    // ------------------------------------------------------------------ admin / test commands
    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("shadow").requires(s -> s.hasPermission(2))
                .then(Commands.argument("action", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String a : new String[]{"test", "stalk", "rush", "bed", "scare", "event", "despawn", "soon", "reset"}) b.suggest(a);
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
            case "despawn" -> {
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
