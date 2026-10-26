package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** a sheet of pale web over the floor: wading through it is slow. spat web dries up after a while, placed web stays */
public class WebFloorBlock extends Block {
    public static final BooleanProperty TEMP = BooleanProperty.create("temp");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public WebFloorBlock() {
        super(Properties.of().mapColor(MapColor.WOOL).noCollission().noOcclusion().strength(0.3F).randomTicks()
                .sound(SoundType.WOOL).pushReaction(PushReaction.DESTROY));
        this.registerDefaultState(this.stateDefinition.any().setValue(TEMP, false));
    }

    public static boolean placeTemp(ServerLevel l, BlockPos p) {
        BlockState here = l.getBlockState(p);
        if (!here.canBeReplaced() || here.getBlock() instanceof WebFloorBlock || !here.getFluidState().isEmpty()) return here.getBlock() instanceof WebFloorBlock;
        BlockState s = ShadowRegistry.WEB_FLOOR.get().defaultBlockState().setValue(TEMP, true);
        if (!s.canSurvive(l, p)) return false;
        l.setBlock(p, s, 3);
        return true;
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(TEMP); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        BlockPos b = p.below();
        return l.getBlockState(b).isFaceSturdy(l, b, Direction.UP);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction d, BlockState o, LevelAccessor l, BlockPos p, BlockPos op) {
        return !s.canSurvive(l, p) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(s, d, o, l, p, op);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
        if (e instanceof PaleSpider || e instanceof MansoorityBoss || e instanceof ShadowEntity) return;
        e.makeStuckInBlock(s, new Vec3(0.45D, 0.9D, 0.45D));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        if (s.getValue(TEMP) && r.nextInt(3) == 0) l.removeBlock(p, false);     // spat web lasts a few minutes
    }
}
