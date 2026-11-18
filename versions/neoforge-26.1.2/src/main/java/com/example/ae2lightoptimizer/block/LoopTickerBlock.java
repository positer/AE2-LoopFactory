package com.example.ae2lightoptimizer.block;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Directional block that accelerates whatever block it faces. */
public final class LoopTickerBlock extends AEBaseEntityBlock<LoopTickerBlockEntity> {
    public static final EnumProperty<net.minecraft.core.Direction> FACING = BlockStateProperties.FACING;

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
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public appeng.api.orientation.IOrientationStrategy getOrientationStrategy() {
        return appeng.api.orientation.OrientationStrategies.facing();
    }
}
