package com.example.ae2lightoptimizer.block;

import appeng.block.AEBaseEntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/** Directional block that accelerates whatever block it faces. */
public final class LoopTickerBlock extends AEBaseEntityBlock<LoopTickerBlockEntity> {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LoopTickerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    /** Shift-right-click with a speed card fills every free upgrade slot at once. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (player.isShiftKeyDown() && stack.is(appeng.core.definitions.AEItems.SPEED_CARD.asItem())
                && level.getBlockEntity(pos) instanceof LoopTickerBlockEntity host) {
            if (!level.isClientSide()) {
                var upgrades = host.getUpgrades();
                for (int slot = 0; slot < upgrades.size() && !stack.isEmpty(); slot++) {
                    if (upgrades.getStackInSlot(slot).isEmpty()) {
                        upgrades.setItemDirect(slot, stack.copyWithCount(1));
                        stack.shrink(1);
                    }
                }
                host.saveRate();
            }
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    public appeng.api.orientation.IOrientationStrategy getOrientationStrategy() {
        return appeng.api.orientation.OrientationStrategies.facing();
    }
}
