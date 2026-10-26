package com.mansoority.shadow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * its place: an endless black floor in the dark, a ring of bone, and it - hunting you - for thirty seconds.
 * (theshadow:hollow, a dimension of its own; every visitor gets their own patch of it)
 */
public final class HollowPlace {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(TheShadow.MODID, "hollow"));
    private static final Map<UUID, Integer> HUNTERS = new HashMap<>();

    public static boolean is(Level l) { return l.dimension() == LEVEL; }

    public static void bring(ServerLevel hollow, ServerPlayer p) {
        int slot = Math.floorMod(p.getUUID().hashCode(), 512);
        int x = slot * 300, z = 0;
        build(hollow, x, z);
        p.teleportTo(hollow, x + 0.5, 64, z + 0.5, hollow.random.nextFloat() * 360F, 0F);
        hunt(p);
    }

    /** a ring of bone pillars on the black, built once per patch */
    private static void build(ServerLevel l, int x, int z) {
        if (l.getBlockState(new BlockPos(x, 63, z)).is(Blocks.BONE_BLOCK)) return;
        l.setBlock(new BlockPos(x, 63, z), Blocks.BONE_BLOCK.defaultBlockState(), 2);
        var r = l.random;
        for (int i = 0; i < 26; i++) {
            double a = i / 26.0 * Math.PI * 2 + r.nextDouble() * 0.2, d = 14 + r.nextDouble() * 22;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            int h = 3 + r.nextInt(9);
            for (int y = 0; y < h; y++) l.setBlock(new BlockPos(px, 64 + y, pz), Blocks.BONE_BLOCK.defaultBlockState(), 2);
            if (r.nextBoolean()) l.setBlock(new BlockPos(px, 64 + h, pz), Blocks.COBWEB.defaultBlockState(), 2);
        }
    }

    /** it starts far off in the dark and comes for you */
    public static void hunt(ServerPlayer p) {
        ShadowEntity old = HUNTERS.containsKey(p.getUUID()) && p.serverLevel().getEntity(HUNTERS.get(p.getUUID())) instanceof ShadowEntity s ? s : null;
        if (old != null && old.isAlive()) return;
        ShadowEntity e = ShadowEntity.rushFrom(p, 30);
        if (e != null) { e.hollowHunter = true; HUNTERS.put(p.getUUID(), e.getId()); }
    }

    public static void leave(ServerPlayer p) {
        Integer id = HUNTERS.remove(p.getUUID());
        if (id == null) return;
        for (ServerLevel l : p.server.getAllLevels()) { var e = l.getEntity(id); if (e instanceof ShadowEntity) e.discard(); }
    }

    private HollowPlace() {}
}
