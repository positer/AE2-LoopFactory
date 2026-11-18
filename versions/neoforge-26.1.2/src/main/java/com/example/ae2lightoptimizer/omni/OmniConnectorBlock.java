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
    public OmniConnectorBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
        super(properties);
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
    @Override public boolean propagatesSkylightDown(BlockState state) { return true; }
    @Override protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
            BlockPos pos, Direction side, BlockPos neighborPos, BlockState neighbor, net.minecraft.util.RandomSource random) {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, level, ticks, pos, side, neighborPos, neighbor, random);
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
            net.minecraft.world.level.redstone.Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        if (level.getBlockEntity(pos) instanceof OmniConnectorBlockEntity endpoint) endpoint.refreshFactoryLink();
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(WIRELESS)) {
            // The ring lies in the plane perpendicular to the base face, so a side-mounted connector
            // orbits around its own axis instead of always spinning horizontally.
            var axis = state.getValue(BlockStateProperties.FACING).getOpposite();
            var ring = OmniOrbitMath.basis(axis);
            double angle = Math.floorMod(level.getGameTime() * 6L + pos.asLong(), 360L) * Math.PI / 180.0;
            double radius = 0.30 + random.nextDouble() * 0.06;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double x = pos.getX() + 0.5 + (ring.u().x() * cos + ring.v().x() * sin) * radius;
            double y = pos.getY() + 0.5 + (ring.u().y() * cos + ring.v().y() * sin) * radius;
            double z = pos.getZ() + 0.5 + (ring.u().z() * cos + ring.v().z() * sin) * radius;
            double speed = 0.02;
            level.addParticle(OmniContent.ORBIT.get(), x, y, z,
                    (-ring.u().x() * sin + ring.v().x() * cos) * speed,
                    (-ring.u().y() * sin + ring.v().y() * cos) * speed,
                    (-ring.u().z() * sin + ring.v().z() * cos) * speed);
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
    /** Copies the selected link into an AE2 memory card, or applies the card's link to this connector. */
    @Override
    protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
            BlockState state, Level level, BlockPos pos, Player player,
            net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (stack.is(appeng.core.definitions.AEItems.MEMORY_CARD.asItem())
                && level.getBlockEntity(pos) instanceof OmniConnectorBlockEntity connector) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player.isShiftKeyDown()) {
                OmniMemoryCard.write(stack, connector.linkId());
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.ae2lpt.omni.link_copied", connector.linkId()));
            } else {
                String copied = OmniMemoryCard.read(stack);
                connector.selectLink(copied);
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.ae2lpt.omni.link_pasted", copied));
            }
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
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