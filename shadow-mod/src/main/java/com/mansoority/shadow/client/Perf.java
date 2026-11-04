package com.mansoority.shadow.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * /shadowperf (client only): what this mod costs on your machine, measured over the last few seconds -
 * the boss / stalker, the small creatures, and the frame rate - so lag reports come with numbers
 */
public final class Perf {
    public static long mobNanos, mobDraws;
    private static long lastNs, lastBoss, lastBossN, lastMob, lastMobN, lastFrames;
    private static double bossMs, mobMs; private static int bossN, mobN, fps;

    /** every 100 ticks: fold the counters into per-frame averages */
    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.tickCount % 100 != 0) return;
        long b = ShadowRenderer.nanos, bn = ShadowRenderer.draws, m = mobNanos, mn = mobDraws;
        long frames = Math.max(1, mc.getFps() * 5L);
        bossMs = (b - lastBoss) / 1e6 / frames; bossN = (int) ((bn - lastBossN) / frames);
        mobMs = (m - lastMob) / 1e6 / frames; mobN = (int) ((mn - lastMobN) / frames);
        fps = mc.getFps();
        lastBoss = b; lastBossN = bn; lastMob = m; lastMobN = mn;
    }

    static void register(net.minecraftforge.client.event.RegisterClientCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("shadowperf").executes(c -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.displayClientMessage(Component.literal(String.format(
                    "[The Shadow] %d fps | its body: %.2f ms/frame (%d drawn) | small creatures: %.2f ms/frame (%d drawn). At 60 fps a frame is 16.7 ms.",
                    fps, bossMs, bossN, mobMs, mobN)), false);
            return 1;
        }));
    }

    private Perf() {}
}
