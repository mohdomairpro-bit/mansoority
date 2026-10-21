package com.mansoority.shadow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** after it has been with you, you carry its mark for a day: black wisps cling to your back, everyone can see them */
public final class ShadowMark {
    private static final String KEY = "theshadow_mark";

    public static void mark(ServerPlayer p) {
        CompoundTag d = p.getPersistentData();
        d.putLong(KEY, p.level().getGameTime() + 24000L);
        ShadowNet.send(p, new FxPacket(FxPacket.MARK, 24000 / 20, 0, 0, 0, 1, 1));
    }

    public static boolean marked(ServerPlayer p) {
        return p.getPersistentData().getLong(KEY) > p.level().getGameTime();
    }

    static void tick(ServerPlayer p) {
        if (!marked(p) || p.isSpectator()) return;
        double yaw = Math.toRadians(p.yBodyRot);
        double bx = Math.sin(yaw) * 0.3, bz = -Math.cos(yaw) * 0.3;          // on your back
        p.serverLevel().sendParticles(ShadowRegistry.WISP.get(), p.getX() + bx, p.getY() + 1.2, p.getZ() + bz, 2, 0.15, 0.25, 0.15, 0.0);
    }

    private ShadowMark() {}
}
