package com.mansoority.shadow.client;

import com.mansoority.shadow.FxPacket;
import com.mansoority.shadow.ShadowEntity;
import com.mansoority.shadow.ShadowRegistry;
import java.io.File;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * DEV/CI ONLY (needs -Dtheshadow.autotest=true). Joins a world, spawns the shadow,
 * poses the camera and takes screenshots so the build can be checked visually. Never runs in a normal game.
 */
public final class AutoTest {
    private static int t = -1;
    private static float sideYaw;

    public static boolean enabled() {
        return Boolean.getBoolean("theshadow.autotest");
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        t++;
        MinecraftServer server = mc.getSingleplayerServer();

        if (t == 1) log("in world");
        if (t == 80) {
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.serverLevel().setDayTime(6000);
                sp.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                sp.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);   // no zombies/spiders ruining shots
                sp.serverLevel().setWeatherParameters(6000, 0, false, false);
                // clear the canopy so the stalk shots show it, not a wall of leaves
                net.minecraft.core.BlockPos c = sp.blockPosition();
                for (net.minecraft.core.BlockPos q : net.minecraft.core.BlockPos.betweenClosed(c.offset(-20, -2, -20), c.offset(20, 20, 20)))
                    if (sp.serverLevel().getBlockState(q).is(net.minecraft.tags.BlockTags.LEAVES)) sp.serverLevel().setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                ShadowEntity e = ShadowEntity.spawnAround(sp, 1, 0, 10);
                log("spawned day: " + (e != null));
            });
        }
        if (t == 110) lookNear(mc, 24F);
        if (t == 125) shot(mc, "01_idle_day");
        if (t == 130) lookNear(mc, -18F);
        if (t == 140) shot(mc, "02_idle_day_b");
        if (t == 145) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); lookNear(mc, 14F); }
        if (t == 160) shot(mc, "03_third_person");
        if (t == 165) { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = false; }
        if (t >= 170 && t <= 232) lookNear(mc, 0F);   // stare right at its face
        if (t == 190) shot(mc, "04_stare_20");
        if (t == 212) shot(mc, "05_stare_40");
        if (t == 228) shot(mc, "06_stare_56");
        if (t == 260) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            sp.serverLevel().setDayTime(18000);
            ShadowEntity e = ShadowEntity.spawnAround(sp, 1, 0, 10);
            log("spawned night: " + (e != null));
        });
        if (t == 290) lookNear(mc, 20F);
        if (t == 305) shot(mc, "07_night");
        if (t == 310) { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
        if (t == 320) shot(mc, "08_night_front_cam");
        if (t == 325) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
                sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
                sp.serverLevel().setDayTime(6000);
                ShadowEntity e = ShadowEntity.rushFrom(sp, 22);
                log("rush: " + (e != null));
            });
        }
        if (t > 326 && t < 400) lookNear(mc, 0F);
        if (t == 333) shot(mc, "09_rush_a");
        if (t == 340) shot(mc, "10_rush_b");
        // the rush ends in the jumpscare: catch it
        int js = ClientFx.jumpscareTick();
        if (js == ClientFx.SCARE_AT + 1) shot(mc, "11_scare_lunge");
        if (js == ClientFx.SCARE_AT + 4) shot(mc, "12_scare_face");
        if (js == ClientFx.SCARE_AT + 7) shot(mc, "13_scare_red");
        if (js == ClientFx.SCARE_AT + 20) shot(mc, "14_scare_late");
        if (t == 430) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().setDayTime(13500);
            ShadowEntity e = ShadowEntity.spawnAround(sp, -1, 35, 3.0);
            log("bedside: " + (e != null));
        });
        if (t == 445) lookNear(mc, 28F);
        if (t == 455) shot(mc, "15_bedside");
        if (t == 470) { mc.options.hideGui = false; ClientFx.handle(FxPacket.of(FxPacket.SEQUENCE)); }
        if (t % 200 == 0) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            for (var m : sp.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, sp.getBoundingBox().inflate(96)))
                if (!(m instanceof com.mansoority.shadow.ShadeEntity)) m.discard();
            if (sp.getHealth() < 10) sp.setHealth(20);
        });
        if (t == 3700) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            ShadowEntity e = ShadowEntity.spawnAround(sp, 1, 0, 14);
            com.mansoority.shadow.ShadowCinematic.stop(sp);
            if (e != null) { e.scripted = true; e.reveal(); e.startVanish(); }
            log("portal: " + (e != null));
        });
        if (t == 3702) lookNear(mc, 0F);
        if (t == 3710) shot(mc, "16_portal_open");
        if (t == 3720) shot(mc, "17_portal_hands");
        if (t == 3729) shot(mc, "18_portal_close");
        if (t == 560) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            sp.serverLevel().setDayTime(17000);
            com.mansoority.shadow.ShadowCinematic.start(sp);
            log("cinematic started");
        });
        int c = t - 560;
        int[] at = {40, 120, 250, 330, 420, 520, 620, 700, 820, 980, 1120, 1260, 1400, 1550, 1700, 1850, 2000, 2150, 2300, 2450, 2600, 2750, 2900, 3050};
        for (int i = 0; i < at.length; i++) if (c == at[i]) shot(mc, String.format("c%02d_cinematic", i));
        // ---- new features
        if (t == 3740) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            sp.serverLevel().setDayTime(18000);
            ShadowEntity e = ShadowEntity.grab(sp, ShadowEntity.GRAB_LICK);
            log("grab: " + (e != null));
        });
        if (t == 3800) shot(mc, "20_grab_lift");
        if (t == 3840) shot(mc, "21_grab_lick");
        if (t == 3920) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().setDayTime(6000);
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADOW_BLADE.get()));
            ShadowEntity e = ShadowEntity.mimicFor(sp);
            log("mimic: " + (e != null));
        });
        if (t == 3940) { for (Entity en : mc.level.entitiesForRendering()) if (en instanceof ShadowEntity se && se.isMimic()) {
                Vec3 d = en.position().subtract(mc.player.position()); mc.player.setYRot((float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90F); mc.player.setXRot(5F); log("mimic at " + (int) d.length()); } }
        if (t == 3950) shot(mc, "22_mimic_gear");
        if (t == 3960) { ClientFx.handle(FxPacket.of(FxPacket.DEATHMSG)); ClientFx.handle(new FxPacket(FxPacket.MARK, 600, 0, 0, 0, 1, 1));
            ClientFx.handle(new FxPacket(FxPacket.MARK_OTHER, 600, 0, 0, 0, mc.player.getId(), 1)); }
        if (t == 3980) shot(mc, "23_deathmsg_mark");
        if (t == 3982) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (t == 3990) shot(mc, "23b_handprint_back");
        if (t == 3992) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t == 4000) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            net.minecraft.server.level.ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
            if (end != null) {
                end.getChunk(0, 2); end.getChunk(0, 3);
                net.minecraft.core.BlockPos st = ShadowEntity.findStand(end, 0.5, 70, 40.5, 40);
                double y = st != null ? st.getY() : 70;
                sp.teleportTo(end, 0.5, y, 40.5, 180F, 10F); sp.fallDistance = 0; log("to the end at y=" + y);
            }
        });
        // ---- the boss: intro, then every move on demand
        if (t == 4100) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 10, false, false));
            var b = com.mansoority.shadow.ShadowEvents.endBossCheck(sp, true);
            log("boss: " + (b != null));
        });
        if (t == 4135) shot(mc, "24_boss_intro_wide");
        if (t == 4330) shot(mc, "25_boss_intro_face");
        if (t == 4400) boss(server, "skipintro");
        if (t > 4400 && t < 5700 && t % 7 == 0) lookNear(mc, 0F);
        String[][] seq = {{"4420", "act:" + com.mansoority.shadow.MansoorityBoss.A_SLAM}, {"4520", "act:" + com.mansoority.shadow.MansoorityBoss.A_SPRAY},
                {"4630", "act:" + com.mansoority.shadow.MansoorityBoss.A_SPIKES}, {"4720", "act:" + com.mansoority.shadow.MansoorityBoss.A_BITE},
                {"4830", "phase2"}, {"4930", "act:" + com.mansoority.shadow.MansoorityBoss.A_BLADE}, {"5000", "signature"},
                {"5130", "phase3"}, {"5230", "act:" + com.mansoority.shadow.MansoorityBoss.A_THROW}, {"5330", "flood"}, {"5430", "act:" + com.mansoority.shadow.MansoorityBoss.A_SCREAM}};
        for (String[] q : seq) if (t == Integer.parseInt(q[0])) { boss(server, q[1]); log("boss do " + q[1]); }
        int[][] shots = {{4440, 0}, {4466, 1}, {4478, 2}, {4560, 3}, {4660, 4}, {4740, 5}, {4775, 6}, {4860, 7}, {4947, 8}, {5040, 9}, {5100, 10},
                {5165, 11}, {5240, 12}, {5260, 13}, {5345, 14}, {5442, 15}};
        String[] names = {"26_slam_crouch", "27_slam_land", "28_slam_wave", "29_spray", "30_spikes", "31_bite", "32_chew", "33_phase2_blade", "34_blade_swing",
                "35_signature", "36_signature_smash", "37_phase3_black", "38_throw", "39_blade_flying", "40_flood", "41_scream_knockdown"};
        for (int i = 0; i < shots.length; i++) if (t == shots[i][0]) shot(mc, names[i]);
        if (t == 5500) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (t == 5520) shot(mc, "42_boss_third_person");
        if (t == 5530) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t > 4100 && t < 5700 && t % 60 == 0) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            if (sp.getHealth() < 10) sp.setHealth(20);
            log("boss fight hp=" + sp.getHealth() + " alive=" + sp.isAlive());
        });
        // stage 3: back to the overworld - spiders, Shade armour + ward, the shrine
        if (t == 5540) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            for (var b : sp.serverLevel().getEntitiesOfClass(com.mansoority.shadow.MansoorityBoss.class, sp.getBoundingBox().inflate(200))) b.discard();
            net.minecraft.server.level.ServerLevel ow = server.overworld();
            ow.setDayTime(6000);
            int x = ow.getSharedSpawnPos().getX() + 40, z = ow.getSharedSpawnPos().getZ() + 40;
            int y = ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            sp.teleportTo(ow, x + 0.5, y, z + 0.5, 0F, 10F);
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADE_HELMET.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADE_CHESTPLATE.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADE_LEGGINGS.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADE_BOOTS.get()));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(ShadowRegistry.PALE_WARD.get()));
            log("stage3 at " + x + " " + y + " " + z);
        });
        if (t == 5550) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            net.minecraft.server.level.ServerLevel ow = sp.serverLevel();
            for (int i = 0; i < 2; i++) {
                var type = i == 0 ? ShadowRegistry.PALE_BITER.get() : ShadowRegistry.PALE_SPITTER.get();
                var e = type.create(ow);
                if (e == null) continue;
                double px = sp.getX() + (i == 0 ? -1.6 : 1.8), pz = sp.getZ() + 4.5 + i;
                e.moveTo(px, ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(px), (int) Math.floor(pz)), pz, 180F, 0F);
                e.setNoAi(true); e.setYHeadRot(180F); e.yBodyRot = 180F;
                ow.addFreshEntity(e);
            }
            net.minecraft.core.BlockPos at = sp.blockPosition();
            int k = 0;
            for (var b : ShadowRegistry.PALE_BLOCKS) { if (k > 7) break; ow.setBlock(at.offset(-4 + k++, 0, 8), b.get().defaultBlockState(), 3); }
        });
        if (t == 5560) { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); }
        if (t == 5575) shot(mc, "44_shade_armour");
        if (t == 5580) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t == 5595) shot(mc, "45_pale_spiders");
        if (t == 5600) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            var src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput().withLevel(sp.serverLevel());
            net.minecraft.core.BlockPos sh = sp.blockPosition().offset(0, 0, 40);
            server.getCommands().performPrefixedCommand(src, "place template theshadow:lair " + sh.getX() + " " + (sh.getY() - 1) + " " + sh.getZ());
            sp.teleportTo(sp.serverLevel(), sh.getX() + 10.5, sh.getY() + 16, sh.getZ() - 10, 0F, 40F);
        });
        if (t == 5640) shot(mc, "46_shrine");
        if (t == 5700) mc.setScreen(new ShadowConfigScreen(null));
        if (t == 5710) shot(mc, "43_config_screen");
        if (t == 5715) { mc.setScreen(null); log("done"); mc.stop(); }
    }

    private static void boss(MinecraftServer server, String what) {
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            for (var b : sp.serverLevel().getEntitiesOfClass(com.mansoority.shadow.MansoorityBoss.class, sp.getBoundingBox().inflate(150))) b.debug(what);
        });
    }

    /** aim at the shadow's head, offset sideways by 'offset' degrees */
    private static void lookNear(Minecraft mc, float offset) {
        ShadowEntity target = null;
        for (Entity e : mc.level.entitiesForRendering()) if (e instanceof ShadowEntity s) target = s;
        if (target == null) return;
        Vec3 head = target.headPos();
        Vec3 d = head.subtract(mc.player.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0) + offset;
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        mc.player.setYRot(yaw); mc.player.setXRot(pitch);
        mc.player.yRotO = yaw; mc.player.xRotO = pitch;
        mc.player.setYHeadRot(yaw);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> {});
        log("shot " + name);
    }

    private static void log(String s) {
        System.out.println("[SHADOW-AUTOTEST] " + s);
    }

    private AutoTest() {}
}
