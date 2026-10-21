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
                sp.serverLevel().setWeatherParameters(6000, 0, false, false);
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
        if (t == 2400) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            ShadowEntity e = ShadowEntity.spawnAround(sp, 1, 0, 14);
            com.mansoority.shadow.ShadowCinematic.stop(sp);
            if (e != null) { e.scripted = true; e.reveal(); e.startVanish(); }
            log("portal: " + (e != null));
        });
        if (t == 2402) lookNear(mc, 0F);
        if (t == 2410) shot(mc, "16_portal_open");
        if (t == 2420) shot(mc, "17_portal_hands");
        if (t == 2429) shot(mc, "18_portal_close");
        if (t == 560) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            sp.serverLevel().setDayTime(17000);
            com.mansoority.shadow.ShadowCinematic.start(sp);
            log("cinematic started");
        });
        int c = t - 560;
        int[] at = {40, 120, 230, 300, 420, 470, 560, 640, 700, 780, 860, 960, 1060, 1200, 1300, 1420, 1500};
        for (int i = 0; i < at.length; i++) if (c == at[i]) shot(mc, String.format("c%02d_cinematic", i));
        // ---- new features
        if (t == 2440) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().getEntities(ShadowRegistry.SHADOW.get(), x -> true).forEach(Entity::discard);
            sp.serverLevel().setDayTime(18000);
            ShadowEntity e = ShadowEntity.grab(sp, ShadowEntity.GRAB_LICK);
            log("grab: " + (e != null));
        });
        if (t == 2500) shot(mc, "20_grab_lift");
        if (t == 2540) shot(mc, "21_grab_lick");
        if (t == 2620) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            sp.serverLevel().setDayTime(6000);
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_HELMET));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE));
            sp.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(ShadowRegistry.SHADOW_BLADE.get()));
            ShadowEntity e = ShadowEntity.mimicFor(sp);
            log("mimic: " + (e != null));
        });
        if (t == 2640) lookNear(mc, 0F);
        if (t == 2650) shot(mc, "22_mimic_gear");
        if (t == 2660) { ClientFx.handle(FxPacket.of(FxPacket.DEATHMSG)); ClientFx.handle(new FxPacket(FxPacket.MARK, 600, 0, 0, 0, 1, 1));
            ClientFx.handle(new FxPacket(FxPacket.MARK_OTHER, 600, 0, 0, 0, mc.player.getId(), 1)); }
        if (t == 2680) shot(mc, "23_deathmsg_mark");
        if (t == 2682) { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(mc.player.getYRot() + 180F); }
        if (t == 2690) shot(mc, "23b_handprint_back");
        if (t == 2692) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (t == 2700) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            net.minecraft.server.level.ServerLevel end = server.getLevel(net.minecraft.world.level.Level.END);
            if (end != null) {
                end.getChunk(0, 2); end.getChunk(0, 3);
                net.minecraft.core.BlockPos st = ShadowEntity.findStand(end, 0.5, 70, 40.5, 40);
                double y = st != null ? st.getY() : 70;
                sp.teleportTo(end, 0.5, y, 40.5, 180F, 10F); sp.fallDistance = 0; log("to the end at y=" + y);
            }
        });
        if (t == 2800) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            var b = com.mansoority.shadow.ShadowEvents.endBossCheck(sp, true);
            log("boss: " + (b != null));
        });
        if (t == 2830) lookNear(mc, 0F);
        if (t == 2840) shot(mc, "24_boss_emerge");
        if (t == 2900) lookNear(mc, 0F);
        if (t == 2910) shot(mc, "25_boss_fight");
        if (t == 3000) { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); }
        if (t == 3010) shot(mc, "26_boss_shades");
        if (t > 2800 && t < 3100 && t % 40 == 0) server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayers().get(0);
            log("boss fight hp=" + sp.getHealth() + " alive=" + sp.isAlive());
        });
        if (t == 3100) mc.setScreen(new ShadowConfigScreen(null));
        if (t == 3110) shot(mc, "27_config_screen");
        if (t == 3115) { mc.setScreen(null); log("done"); mc.stop(); }
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
