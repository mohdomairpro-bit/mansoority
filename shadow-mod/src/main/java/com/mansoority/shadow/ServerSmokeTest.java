package com.mansoority.shadow;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/** CI only (-Dtheshadow.servertest=true): boots the dedicated server, checks registration, then shuts down. */
public final class ServerSmokeTest {
    private static int ticks;

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ticks++;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (ticks == 40) {
            System.out.println("[SHADOW-SERVERTEST] entity type: " + ShadowRegistry.SHADOW.get());
            System.out.println("[SHADOW-SERVERTEST] sounds: " + ShadowRegistry.SOUND_LIST.size());
            System.out.println("[SHADOW-SERVERTEST] config enabled: " + ShadowConfig.ENABLED.get());
        }
        if (ticks == 60 && server != null) {
            System.out.println("[SHADOW-SERVERTEST] OK, stopping");
            server.halt(false);
        }
    }

    private ServerSmokeTest() {}
}
