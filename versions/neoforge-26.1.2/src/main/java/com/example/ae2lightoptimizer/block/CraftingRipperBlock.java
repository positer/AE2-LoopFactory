package com.example.ae2lightoptimizer.block;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class CraftingRipperBlock extends AEBaseEntityBlock<CraftingRipperBlockEntity> {
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public CraftingRipperBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CONNECTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTED);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState state, CraftingRipperBlockEntity host) {
        return state.setValue(CONNECTED, host.getMainNode().isActive());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        var host = getBlockEntity(level, pos);
        if (host != null) host.getLogic().updateRedstoneState();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CraftingRipperBlockEntity host) {
                host.openMenu(player, MenuLocators.forBlockEntity(host));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
