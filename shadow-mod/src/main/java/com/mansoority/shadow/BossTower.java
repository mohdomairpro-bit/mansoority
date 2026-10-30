package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * its tower: a twisted spire of pale bone at the edge of the main island, between two of the obsidian pillars,
 * well clear of anything in the middle. a sealed door at the foot (that's where the TNT goes), a chamber inside
 * with the altar its skull is carried back to. built the first time it shows up.
 */
public final class BossTower {
    public static final int HEIGHT = 44;

    /** build it if this world doesn't have one yet */
    public static void ensure(ServerLevel end) {
        BossData d = BossData.get(end.getServer());
        if (d.tower != null) return;
        BlockPos base = site(end);
        if (base == null) return;
        build(end, base);
        d.tower = base;
        d.setDirty();
    }

    /** the door faces the middle of the island */
    public static Direction doorFacing(BlockPos base) {
        return Direction.getNearest(-base.getX(), 0, -base.getZ());
    }

    public static BlockPos door(BlockPos base) {
        Direction f = doorFacing(base);
        return base.relative(f, 6).above(1);
    }

    public static BlockPos altar(BlockPos base) { return base.above(2); }

    /** between two pillars, as far out as the island allows (but at least 58 blocks from the centre) */
    private static BlockPos site(ServerLevel end) {
        BlockPos best = null; int bestR = 0;
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(i * 36 + 18);
            int found = 0;
            for (int r = 58; r < 110; r += 2) {
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                if (!end.hasChunkAt(new BlockPos(x, 0, z))) break;
                int y = end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                if (y < 40 || y > 90) break;
                // the whole footprint has to be on the island
                boolean ok = true;
                for (int k = 0; k < 8 && ok; k++) {
                    int px = x + (int) Math.round(Math.cos(k * Math.PI / 4) * 8), pz = z + (int) Math.round(Math.sin(k * Math.PI / 4) * 8);
                    if (end.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz) < 40) ok = false;
                }
                if (ok) found = r;
            }
            if (found > bestR) {
                bestR = found;
                int r = Math.min(found, 78);
                int x = (int) Math.round(Math.cos(a) * r), z = (int) Math.round(Math.sin(a) * r);
                best = new BlockPos(x, end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            }
        }
        return best;
    }

    private static void set(ServerLevel l, int x, int y, int z, BlockState s) { l.setBlock(new BlockPos(x, y, z), s, 2); }

    public static void build(ServerLevel l, BlockPos base) {
        BlockState bricks = ShadowRegistry.PALE_BONE_BRICKS.get().defaultBlockState();
        BlockState cracked = ShadowRegistry.CRACKED_PALE_BONE_BRICKS.get().defaultBlockState();
        BlockState tiles = ShadowRegistry.PALE_BONE_TILES.get().defaultBlockState();
        BlockState chis = ShadowRegistry.CHISELED_PALE_BONE.get().defaultBlockState();
        BlockState bone = ShadowRegistry.PALE_BONE.get().defaultBlockState();
        BlockState black = Blocks.BLACKSTONE.defaultBlockState();
        int bx = base.getX(), by = base.getY(), bz = base.getZ();
        var rnd = l.getRandom();
        // a solid foundation down into the island so it never floats
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            if (x * x + z * z > 64) continue;
            for (int y = -1; y >= -6; y--) set(l, bx + x, by + y, bz + z, y == -1 ? tiles : Blocks.END_STONE.defaultBlockState());
        }
        for (int y = 0; y < HEIGHT; y++) {
            double t = y / (double) HEIGHT;
            double r = 6.5 * (1 - t * 0.78) + 0.6 * Math.sin(y * 0.45);
            double twist = y * 0.13;
            for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                double a = Math.atan2(z, x);
                // six bone ribs spiral up the outside
                double rib = Math.cos((a - twist) * 6);
                double rr = r + (rib > 0.86 ? 1.0 : 0);
                if (d > rr + 0.5) continue;
                boolean shell = d > rr - 1.2;
                boolean chamber = y >= 1 && y <= 7 && d < 4.6;
                if (chamber) { set(l, bx + x, by + y, bz + z, Blocks.AIR.defaultBlockState()); continue; }
                if (!shell && y > 8) {            // hollow above the chamber too: dark inside, a few chains
                    set(l, bx + x, by + y, bz + z, Blocks.AIR.defaultBlockState());
                    continue;
                }
                BlockState s;
                if (rib > 0.86 && d > rr - 0.6) s = bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
                else if ((y % 7 == 3) && rnd.nextFloat() < 0.25F) s = Blocks.AIR.defaultBlockState();          // slits of windows
                else s = rnd.nextFloat() < 0.18F ? cracked : rnd.nextFloat() < 0.08F ? black : bricks;
                set(l, bx + x, by + y, bz + z, s);
            }
        }
        // floor and the chamber: tiles, the altar in the middle, soul lanterns
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (x * x + z * z <= 20) set(l, bx + x, by, bz + z, tiles);
        set(l, bx, by + 1, bz, chis);
        set(l, bx + 1, by + 1, bz, ShadowRegistry.PALE_BONE_BRICK_SLAB.get().defaultBlockState());
        set(l, bx - 1, by + 1, bz, ShadowRegistry.PALE_BONE_BRICK_SLAB.get().defaultBlockState());
        set(l, bx, by + 1, bz + 1, ShadowRegistry.PALE_BONE_BRICK_SLAB.get().defaultBlockState());
        set(l, bx, by + 1, bz - 1, ShadowRegistry.PALE_BONE_BRICK_SLAB.get().defaultBlockState());
        for (int[] c : new int[][]{{3, 3}, {-3, 3}, {3, -3}, {-3, -3}}) {
            set(l, bx + c[0], by + 7, bz + c[1], Blocks.CHAIN.defaultBlockState());
            set(l, bx + c[0], by + 6, bz + c[1], Blocks.SOUL_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        // the door: a tall arch on the side facing the middle, bricked shut with cracked bone (blow it open)
        Direction f = doorFacing(base);
        Direction side = f.getClockWise();
        BlockPos dc = base.relative(f, 5);
        for (int out = 0; out <= 2; out++) for (int w = -2; w <= 2; w++) for (int h = 1; h <= 6; h++) {
            BlockPos p = dc.relative(f, out).relative(side, w).above(h);
            boolean arch = Math.abs(w) == 2 || h == 6;
            if (out == 2) { if (arch) l.setBlock(p, chis, 2); continue; }
            l.setBlock(p, arch ? bone : cracked, 2);
        }
        // its face over the door
        l.setBlock(dc.relative(f, 2).above(7), ShadowRegistry.SKULL_BLOCK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, f), 2);
        // spikes at the very top
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            int x = (int) Math.round(Math.cos(a) * 2), z = (int) Math.round(Math.sin(a) * 2);
            for (int y = 0; y < 4 + (k % 2) * 3; y++) set(l, bx + x, by + HEIGHT + y, bz + z, bone);
        }
        for (int y = 0; y < 9; y++) set(l, bx, by + HEIGHT + y, bz, bone);
    }

    /** brick the door shut again (when it seals itself in) */
    public static void seal(ServerLevel l, BlockPos base) {
        Direction f = doorFacing(base), side = f.getClockWise();
        BlockPos dc = base.relative(f, 5);
        for (int out = 0; out <= 1; out++) for (int w = -1; w <= 1; w++) for (int h = 1; h <= 5; h++)
            l.setBlock(dc.relative(f, out).relative(side, w).above(h), ShadowRegistry.CRACKED_PALE_BONE_BRICKS.get().defaultBlockState(), 3);
    }

    private BossTower() {}
}
