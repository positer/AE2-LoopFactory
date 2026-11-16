package com.example.ae2lightoptimizer.omni;

import appeng.block.AEBaseEntityBlock;
import appeng.block.networking.WirelessAccessPointBlock;
import appeng.core.definitions.AEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Native wireless-access-point placement and shapes, with its own zero-power network endpoint. */
public final class OmniConnectorBlock extends AEBaseEntityBlock<OmniConnectorBlockEntity> implements SimpleWaterloggedBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty ONLINE = net.minecraft.world.level.block.state.properties.BooleanProperty.create("online");
    /** True while the connector bridges a formed Loop Factory structure below it. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty WIRELESS = net.minecraft.world.level.block.state.properties.BooleanProperty.create("wireless");
    public OmniConnectorBlock() {
        super(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(AEBlocks.WIRELESS_ACCESS_POINT.block()));
        registerDefaultState(defaultBlockState().setValue(WirelessAccessPointBlock.STATE, WirelessAccessPointBlock.State.OFF)
                .setValue(BlockStateProperties.WATERLOGGED, false).setValue(ONLINE, false).setValue(WIRELESS, false));
    }
    @Override public appeng.api.orientation.IOrientationStrategy getOrientationStrategy() { return appeng.api.orientation.OrientationStrategies.facing(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(WirelessAccessPointBlock.STATE, BlockStateProperties.WATERLOGGED, ONLINE, WIRELESS);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var nativeState = AEBlocks.WIRELESS_ACCESS_POINT.block().getStateForPlacement(context);
        return nativeState == null ? null : defaultBlockState().setValue(BlockStateProperties.FACING, nativeState.getValue(BlockStateProperties.FACING))
                .setValue(BlockStateProperties.WATERLOGGED, nativeState.getValue(BlockStateProperties.WATERLOGGED));
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return nativeState(state).getShape(level, pos, context);
    }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return nativeState(state).getCollisionShape(level, pos, context);
    }
    private BlockState nativeState(BlockState state) {
        return AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState()
                .setValue(BlockStateProperties.FACING, state.getValue(BlockStateProperties.FACING))
                .setValue(BlockStateProperties.WATERLOGGED, state.getValue(BlockStateProperties.WATERLOGGED));
    }
    @Override public FluidState getFluidState(BlockState state) {
        return state.getValue(BlockStateProperties.WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
    @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) { return true; }
    @Override public BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, side, neighbor, level, pos, neighborPos);
    }
    @Override protected BlockState updateBlockStateFromBlockEntity(BlockState current, OmniConnectorBlockEntity endpoint) {
        // A broken block is already air while its block entity is being removed; deriving a state there would write the block back.
        if (endpoint.getLevel() == null || !endpoint.getLevel().getBlockState(endpoint.getBlockPos()).is(this)) return current;
        if (endpoint.getLevel().isClientSide()) return current;
        return current.setValue(ONLINE, endpoint.linked() && endpoint.getMainNode().isActive())
                .setValue(WIRELESS, endpoint.factoryLinked())
                .setValue(WirelessAccessPointBlock.STATE, endpoint.linked() ? WirelessAccessPointBlock.State.HAS_CHANNEL
                : endpoint.linkId().isEmpty() ? WirelessAccessPointBlock.State.OFF : WirelessAccessPointBlock.State.ON);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
            BlockPos fromPos, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof OmniConnectorBlockEntity endpoint) endpoint.refreshFactoryLink();
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(WIRELESS)) {
            // One ring particle per tick orbiting the connector centre inside its own block.
            double angle = Math.floorMod(level.getGameTime() * 6L + pos.asLong(), 360L) * Math.PI / 180.0;
            double radius = 0.30 + random.nextDouble() * 0.06;
            double x = pos.getX() + 0.5 + Math.cos(angle) * radius;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * radius;
            double y = pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.12;
            double speed = 0.02;
            level.addParticle(OmniContent.ORBIT.get(), x, y, z, -Math.sin(angle) * speed, 0.0, Math.cos(angle) * speed);
        }
        if (!state.getValue(ONLINE) || state.getValue(WirelessAccessPointBlock.STATE) != WirelessAccessPointBlock.State.HAS_CHANNEL) return;
        double dx = random.nextDouble() * 2 - 1, dy = random.nextDouble() * 2 - 1, dz = random.nextDouble() * 2 - 1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) return;
        double speed = 0.006 + random.nextDouble() * 0.002;
        dx /= length; dy /= length; dz /= length;
        level.addParticle(OmniContent.PULSE.get(), pos.getX() + 0.5 + dx * 0.06,
                pos.getY() + 0.5 + dy * 0.06, pos.getZ() + 0.5 + dz * 0.06, dx * speed, dy * speed, dz * speed);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return super.useWithoutItem(state, level, pos, player, hit);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof OmniConnectorBlockEntity endpoint) {
            if (!appeng.util.Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(level, pos), player)) return InteractionResult.FAIL;
            endpoint.open(player);
        }
        return InteractionResult.SUCCESS;
    }
}
