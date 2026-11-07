package com.mansoority.shadow.client;

import com.mansoority.shadow.MansoorityBoss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

/**
 * two players, one real server (-Dtheshadow.multitest=leader|follower): the leader starts the fight with commands,
 * both log what they see of the boss every 10 server ticks (its action and how far into it they are) and take
 * screenshots on the same server ticks. comparing the two logs shows whether everyone sees the same frame.
 */
public final class MultiTest {
    private static final String ROLE = System.getProperty("theshadow.multitest");
    private static int t;
    private static boolean stopped;

    public static boolean on() { return ROLE != null; }

    static void tick(Minecraft mc) {
        if (ROLE == null || stopped || mc.player == null || mc.level == null || mc.getConnection() == null) return;
        t++;
        long gt = mc.level.getGameTime();
        if (ROLE.equals("leader")) {
            if (t == 200) cmd(mc, "gamerule doDaylightCycle false");
            if (t == 210) cmd(mc, "execute in minecraft:the_end run tp @a 30 80 30");
            if (t == 215) cmd(mc, "effect give @a minecraft:resistance infinite 4 true");
            if (t == 216) cmd(mc, "effect give @a minecraft:slow_falling 40 0 true");
            if (t == 217) cmd(mc, "effect give @a minecraft:saturation infinite 0 true");
            if (t == 218) cmd(mc, "effect give @a minecraft:regeneration infinite 3 true");
            if (t == 400) cmd(mc, "mansoority boss");
            if (t == 460) cmd(mc, "mansoority skipintro");
            if (t == 1400) cmd(mc, "mansoority phase2");
            if (t == 2000) cmd(mc, "mansoority phase3");
        }
        MansoorityBoss b = null;
        for (var e : mc.level.entitiesForRendering()) if (e instanceof MansoorityBoss mb) b = mb;
        if (gt % 10 == 0) {
            if (b != null) {
                float age = HitStop.age(b.getId(), b.tickCount);
                log(String.format("sync gt=%d act=%d el=%.1f phase=%d pos=%.2f,%.2f,%.2f", gt, b.getAction(), b.actionAge(age), b.getPhase(), b.getX(), b.getY(), b.getZ()));
            } else log("sync gt=" + gt + " noboss");
        }
        if (b != null && gt % 100 == 0) {
            mc.options.hideGui = false;
            Screenshot.grab(mc.gameDirectory, ROLE + "_" + gt + ".png", mc.getMainRenderTarget(), m -> {});
        }
        if (t > 2700) { stopped = true; log("done"); mc.stop(); }
    }

    private static void cmd(Minecraft mc, String c) { mc.player.connection.sendCommand(c); log("cmd " + c); }

    private static void log(String s) { com.mansoority.shadow.TheShadow.LOGGER.info("[SHADOW-MULTI " + ROLE + "] " + s); }

    private MultiTest() {}
}
