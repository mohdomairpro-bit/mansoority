package com.mansoority.shadow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** per world: has the End boss been beaten, and when did it last show up */
public class BossData extends SavedData {
    public boolean defeated;
    public long lastSpawn;

    public static BossData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BossData::load, BossData::new, "theshadow_boss");
    }

    static BossData load(CompoundTag tag) {
        BossData d = new BossData();
        d.defeated = tag.getBoolean("defeated");
        d.lastSpawn = tag.getLong("lastSpawn");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("defeated", defeated);
        tag.putLong("lastSpawn", lastSpawn);
        return tag;
    }

    public void setDefeated() { defeated = true; setDirty(); }
    public void spawned(long time) { lastSpawn = time; setDirty(); }
}
