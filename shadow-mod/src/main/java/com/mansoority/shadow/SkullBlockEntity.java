package com.mansoority.shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** only there so the skull can be drawn from the creature's own mesh */
public class SkullBlockEntity extends BlockEntity {
    public SkullBlockEntity(BlockPos pos, BlockState state) { super(ShadowRegistry.SKULL_BE.get(), pos, state); }
}
