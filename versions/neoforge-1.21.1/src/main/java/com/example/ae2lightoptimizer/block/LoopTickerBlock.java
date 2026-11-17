package com.example.ae2lightoptimizer.block;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Directional block that accelerates whatever block it faces. */
public final class LoopTickerBlock extends AEBaseEntityBlock<LoopTickerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LoopTickerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
            net.minecraft.core.BlockPos pos, net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof LoopTickerBlockEntity host) {
            appeng.menu.MenuOpener.open(com.example.ae2lightoptimizer.menu.LoopTickerMenu.TYPE.get(), player,
                    appeng.menu.locator.MenuLocators.forBlockEntity(host));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
}