package com.example.ae2lightoptimizer.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Condenser block whose block entity can also join the ME network. */
public final class LoopCondenserBlock extends appeng.block.misc.CondenserBlock {
    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LoopCondenserBlockEntity(pos, state);
    }
}
