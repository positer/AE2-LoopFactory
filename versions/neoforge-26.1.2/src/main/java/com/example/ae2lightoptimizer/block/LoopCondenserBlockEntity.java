package com.example.ae2lightoptimizer.block;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Matter Condenser variant that also joins the ME network as a one-channel, zero-power member. Off the
 * network it behaves exactly like the native condenser, because all condenser logic is inherited.
 */
public final class LoopCondenserBlockEntity extends appeng.blockentity.misc.CondenserBlockEntity
        implements IInWorldGridNodeHost {
    private static final IGridNodeListener<LoopCondenserBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(LoopCondenserBlockEntity owner, IGridNode node) {
                    owner.saveChanges();
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(java.util.EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(0.0)
            .setInWorldNode(true)
            .setTagName("mainNode");

    public LoopCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOP_CONDENSER.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            // Creation is deferred to the server tick loop: the block entity is not ticked itself and
            // onLoad runs before the block entity is reachable through the level.
            com.example.ae2lightoptimizer.integration.CondenserLoopService.register(this);
        }
    }

    @Override
    public void setRemoved() {
        com.example.ae2lightoptimizer.integration.CondenserLoopService.unregister(this);
        mainNode.destroy();
        super.setRemoved();
    }

    /** Diagnostic: how often the grid asked this host for its node. */
    public static volatile int gridNodeQueries;

    public IManagedGridNode mainNode() {
        return mainNode;
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction side) {
        gridNodeQueries++;
        return mainNode.getNode();
    }
}