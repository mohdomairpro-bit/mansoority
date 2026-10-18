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
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.player instanceof ServerPlayer p)) return;
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
        if (p.getRandom().nextDouble() >= ShadowConfig.CHANCE.get()) return;

        data.putLong(KEY_NEXT, now + cooldown + p.getRandom().nextInt(20 * 60 * 20));
        double roll = p.getRandom().nextDouble();
        if (roll < 0.65) {
            if (ShadowEntity.spawnAround(p, 0, 180, 28) == null) soundEvent(p);
        } else if (roll < 0.93) {
            soundEvent(p);
        } else {
            ShadowEntity.jumpscare(p, false);
        }
    }

    private static boolean eligible(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator() || p.isSleeping()) return false;
        boolean spooky = !p.level().isDay() || p.getY() < 40 || !p.level().canSeeSky(p.blockPosition());
        if (!spooky) return false;
        if (p.tickCount - p.getLastHurtByMobTimestamp() < 1200) return false;  // was hit in the last minute
        if (p.tickCount - p.getLastHurtMobTimestamp() < 1200) return false;    // hit something in the last minute
        if (p.tickCount < 2400) return false;                                  // just joined
        boolean boss = !p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(64),
                e -> e instanceof EnderDragon || e instanceof WitherBoss || e.getType().is(BOSSES)).isEmpty();
        if (boss) return false;
        int players = p.serverLevel().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(32), pl -> !pl.isSpectator()).size();
        if (players > ShadowConfig.MAX_WITNESSES.get()) return false;
        return !ShadowEntity.anyNear(p, 160);
    }

    private static void soundEvent(ServerPlayer p) {
        int id = p.getRandom().nextInt(4);
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
                            for (String a : new String[]{"test", "stalk", "scare", "event", "despawn", "soon", "reset"}) b.suggest(a);
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
            default -> { reply(src, "Use: test, stalk, scare, event, despawn, soon, reset"); return 0; }
        }
        return 1;
    }

    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), true);
    }
}
