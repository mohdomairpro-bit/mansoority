package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** what's left of it: its skull, jaw hanging open. press the Hollow Heart into it and it comes back for a rematch */
public class SkullBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public SkullBlock() {
        super(Properties.of().strength(50F, 1200F).noOcclusion().sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext c) { return this.defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()); }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SkullBlockEntity(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ShadowRegistry.HOLLOW_HEART.get())) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        if (sl.dimension() != Level.END) {
            player.displayClientMessage(Component.literal("Nothing happens. It wants to be back in the End.").withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC), true);
            return InteractionResult.CONSUME;
        }
        if (!sl.getEntitiesOfClass(MansoorityBoss.class, player.getBoundingBox().inflate(300)).isEmpty()) return InteractionResult.CONSUME;
        if (!(sl.getBlockEntity(pos) instanceof SkullBlockEntity be) || be.revive > 0) return InteractionResult.CONSUME;
        if (!player.isCreative()) held.shrink(1);
        sl.blockEvent(pos, this, 1, 0);                       // starts the revival on both sides
        sl.getServer().getPlayerList().broadcastSystemMessage(Component.literal(player.getScoreboardName() + " gave Mansoority its heart back.")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.ITALIC), false);
        return InteractionResult.CONSUME;
    }

    /** the end of the revival: the skull is gone, it stands there, whole */
    static void rise(ServerLevel sl, BlockPos pos) {
        sl.removeBlock(pos, false);
        BossData d = BossData.get(sl.getServer());
        d.defeated = false; d.skull = null; d.fightDeaths = 0; d.setDirty();
        MansoorityBoss b = ShadowRegistry.BOSS.get().create(sl);
        if (b == null) return;
        net.minecraft.world.entity.player.Player near = sl.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 60, false);
        b.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, near != null ? near.getYRot() + 180F : 0F, 0);
        b.setHealth(MansoorityBoss.MAX_HP);
        sl.addFreshEntity(b);
        b.beginRebuild();
        sl.sendParticles(ShadowRegistry.WISP.get(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 200, 2, 3, 2, 0.08);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return type == ShadowRegistry.SKULL_BE.get() ? (l, p, s, be) -> SkullBlockEntity.tick(l, p, s, (SkullBlockEntity) be) : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }
}
