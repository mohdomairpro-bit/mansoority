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
        if (t == 420) { mc.options.hideGui = false; ClientFx.handle(FxPacket.of(FxPacket.SEQUENCE)); }
        if (t == 520) { log("done"); mc.stop(); }
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
