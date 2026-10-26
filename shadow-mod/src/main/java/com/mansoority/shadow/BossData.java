package com.mansoority.shadow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * per world: the state of the End fight. whether it's been beaten, how many people it has killed this fight,
 * whether it has sealed itself in its tower (and how hurt it was), the island it broke (put back afterwards).
 */
public class BossData extends SavedData {
    public boolean defeated;
    public long lastSpawn;
    /** players it has killed in the current fight: at 5 it crawls back into its tower */
    public int fightDeaths;
    public boolean sealed;
    public float sealedHp;
    public BlockPos sealedAt = BlockPos.ZERO;
    public int tnt;
    /** where its skull lies after it died (rematch altar), or null */
    public BlockPos skull;
    /** blocks the fight crumbled into the void, restored when the fight ends */
    public final List<CompoundTag> broken = new ArrayList<>();

    public static BossData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BossData::load, BossData::new, "theshadow_boss");
    }

    static BossData load(CompoundTag tag) {
        BossData d = new BossData();
        d.defeated = tag.getBoolean("defeated");
        d.lastSpawn = tag.getLong("lastSpawn");
        d.fightDeaths = tag.getInt("fightDeaths");
        d.sealed = tag.getBoolean("sealed");
        d.sealedHp = tag.getFloat("sealedHp");
        d.sealedAt = NbtUtils.readBlockPos(tag.getCompound("sealedAt"));
        d.tnt = tag.getInt("tnt");
        if (tag.contains("skull")) d.skull = NbtUtils.readBlockPos(tag.getCompound("skull"));
        ListTag l = tag.getList("broken", 10);
        for (int i = 0; i < l.size(); i++) d.broken.add(l.getCompound(i));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("defeated", defeated);
        tag.putLong("lastSpawn", lastSpawn);
        tag.putInt("fightDeaths", fightDeaths);
        tag.putBoolean("sealed", sealed);
        tag.putFloat("sealedHp", sealedHp);
        tag.put("sealedAt", NbtUtils.writeBlockPos(sealedAt));
        tag.putInt("tnt", tnt);
        if (skull != null) tag.put("skull", NbtUtils.writeBlockPos(skull));
        ListTag l = new ListTag(); l.addAll(broken); tag.put("broken", l);
        return tag;
    }

    public void setDefeated() { defeated = true; fightDeaths = 0; setDirty(); }
    public void spawned(long time) { lastSpawn = time; setDirty(); }

    public void remember(BlockPos p, BlockState s) {
        CompoundTag t = new CompoundTag();
        t.put("pos", NbtUtils.writeBlockPos(p)); t.put("state", NbtUtils.writeBlockState(s));
        broken.add(t); setDirty();
    }

    /** put the island back together */
    public void restore(net.minecraft.server.level.ServerLevel end) {
        var blocks = end.holderLookup(net.minecraft.core.registries.Registries.BLOCK);
        for (CompoundTag t : broken) {
            BlockPos p = NbtUtils.readBlockPos(t.getCompound("pos"));
            BlockState s = NbtUtils.readBlockState(blocks, t.getCompound("state"));
            if (end.getBlockState(p).isAir()) end.setBlock(p, s, 3);
        }
        broken.clear(); setDirty();
    }
}
