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
 * its place: a black floor hanging in the void far out in the End, a ring of bone, and it - hunting you - for thirty seconds.
 * (a real dimension would make every singleplayer world show the "experimental settings" warning, so it lives out here)
 */
public final class HollowPlace {
    public static final ResourceKey<Level> LEVEL = Level.END;
    private static final int FAR = 2_000_000, Y = 120, R = 46;
    private static final Map<UUID, Integer> HUNTERS = new HashMap<>();

    /** are you in its place right now */
    public static boolean is(Level l) { return false; }
    public static boolean in(net.minecraft.world.entity.Entity e) { return e.level().dimension() == Level.END && e.getX() > FAR - 500; }

    public static void bring(ServerLevel end, ServerPlayer p) {
        int slot = Math.floorMod(p.getUUID().hashCode(), 64);
        int x = FAR + slot * 200, z = FAR;
        build(end, x, z);
        p.teleportTo(end, x + 0.5, Y + 1, z + 0.5, end.random.nextFloat() * 360F, 0F);
        hunt(p);
    }

    /** a disc of black stone in the void with a ring of bone pillars, built once per patch */
    private static void build(ServerLevel l, int x, int z) {
        if (l.getBlockState(new BlockPos(x, Y, z)).is(Blocks.BONE_BLOCK)) return;
        for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            if (dx * dx + dz * dz > R * R) continue;
            l.setBlock(new BlockPos(x + dx, Y, z + dz), Blocks.BLACK_CONCRETE.defaultBlockState(), 2);
            l.setBlock(new BlockPos(x + dx, Y - 1, z + dz), Blocks.BLACKSTONE.defaultBlockState(), 2);
        }
        l.setBlock(new BlockPos(x, Y, z), Blocks.BONE_BLOCK.defaultBlockState(), 2);
        var r = l.random;
        for (int i = 0; i < 26; i++) {
            double a = i / 26.0 * Math.PI * 2 + r.nextDouble() * 0.2, d = 14 + r.nextDouble() * 22;
            int px = x + (int) Math.round(Math.cos(a) * d), pz = z + (int) Math.round(Math.sin(a) * d);
            int h = 3 + r.nextInt(9);
            for (int y = 0; y < h; y++) l.setBlock(new BlockPos(px, Y + 1 + y, pz), Blocks.BONE_BLOCK.defaultBlockState(), 2);
            if (r.nextBoolean()) l.setBlock(new BlockPos(px, Y + 1 + h, pz), Blocks.COBWEB.defaultBlockState(), 2);
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
