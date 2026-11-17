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
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,
            net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.phys.shapes.CollisionContext context) {
        var shape = net.minecraft.world.phys.shapes.Shapes.empty();
        for (double[] box : new double[][] {{4,4,2,12,12,4},{5,5,1,11,11,2},{6,6,0,10,10,1},{6,6,4,10,10,5}}) {
            double[] a = rotate(box[0], box[1], box[2], state.getValue(FACING));
            double[] b = rotate(box[3], box[4], box[5], state.getValue(FACING));
            shape = net.minecraft.world.phys.shapes.Shapes.or(shape, Block.box(
                    Math.min(a[0],b[0]), Math.min(a[1],b[1]), Math.min(a[2],b[2]),
                    Math.max(a[0],b[0]), Math.max(a[1],b[1]), Math.max(a[2],b[2])));
        }
        return shape;
    }

    private static double[] rotate(double x, double y, double z, net.minecraft.core.Direction facing) {
        return switch(facing) {
            case NORTH -> new double[]{x,y,z};
            case SOUTH -> new double[]{16-x,y,16-z};
            case EAST -> new double[]{16-z,y,x};
            case WEST -> new double[]{z,y,16-x};
            case UP -> new double[]{x,16-z,y};
            case DOWN -> new double[]{x,z,16-y};
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }
}