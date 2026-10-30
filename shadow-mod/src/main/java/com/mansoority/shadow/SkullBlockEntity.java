package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * its skull. press the Hollow Heart into it and the revival plays out here: the heart sinks into the skull and
 * starts beating, black veins crawl out of it, the body pulls itself back together around it... and it stands up.
 */
public class SkullBlockEntity extends BlockEntity {
    public static final int REVIVE_LEN = 150;
    public int revive;          // 0 = resting, else ticks into the revival
    /** set by the client setup: particles while it revives (kept out of this class so servers never load client code) */
    public static java.util.function.Consumer<SkullBlockEntity> CLIENT_TICK = be -> {};

    public SkullBlockEntity(BlockPos pos, BlockState state) { super(ShadowRegistry.SKULL_BE.get(), pos, state); }

    public float reviveProgress(float pt) { return revive <= 0 ? 0F : Math.min(1F, (revive + pt) / REVIVE_LEN); }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) { revive = 1; return true; }
        return super.triggerEvent(id, param);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SkullBlockEntity be) {
        if (be.revive <= 0) return;
        be.revive++;
        if (level.isClientSide) { CLIENT_TICK.accept(be); return; }
        ServerLevel sl = (ServerLevel) level;
        if (be.revive % 20 == 5) sl.playSound(null, pos, ShadowRegistry.HEARTBEAT.get(), net.minecraft.sounds.SoundSource.HOSTILE, 3F, 0.6F + be.revive / (float) REVIVE_LEN * 0.4F);
        if (be.revive == 60) sl.playSound(null, pos, ShadowRegistry.CRACK.get(), net.minecraft.sounds.SoundSource.HOSTILE, 4F, 0.5F);
        if (be.revive == 100) sl.playSound(null, pos, ShadowRegistry.GURGLE.get(), net.minecraft.sounds.SoundSource.HOSTILE, 4F, 0.6F);
        if (be.revive >= REVIVE_LEN) SkullBlock.rise(sl, pos);
    }
}
