package com.example.ae2lightoptimizer.factory;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;

public final class FactoryBlock extends AEBaseEntityBlock<FactoryBlockEntity> {
    public enum Kind { PROVIDER, TERMINAL, CABLE }
    private final Kind kind;
    public FactoryBlock(BlockBehaviour.Properties properties, Kind kind) { super(properties); this.kind = kind; }
    public Kind kind() { return kind; }
    @Override public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state,
            net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        // onRemove and the AE wrench both obtain the single carrying machine from addAdditionalDrops.
        if(params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof FactoryBlockEntity factory && factory.retainsRecoveryDrop())return new java.util.ArrayList<>();
        return super.getDrops(state,params);
    }
    @Override public appeng.api.orientation.IOrientationStrategy getOrientationStrategy() {
        // Keep the full facing property so worlds saved before the horizontal-only change still load.
        return appeng.api.orientation.OrientationStrategies.facing();
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null || kind != Kind.TERMINAL) return state;
        Direction facing = getOrientationStrategy().getFacing(state);
        if (facing.getAxis().isVertical()) {
            state = getOrientationStrategy().setFacing(state, context.getHorizontalDirection().getOpposite());
        }
        return state;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (kind == Kind.CABLE || player.isShiftKeyDown()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof FactoryBlockEntity factory)
            factory.openMenu(player, MenuLocators.forBlockEntity(factory));
        return InteractionResult.SUCCESS;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, net.minecraft.world.level.redstone.Orientation orientation, boolean moved) {
        if (level.getBlockEntity(pos) instanceof FactoryBlockEntity factory && factory.isProvider()) factory.getLogic().updateRedstoneState();
    }
}
