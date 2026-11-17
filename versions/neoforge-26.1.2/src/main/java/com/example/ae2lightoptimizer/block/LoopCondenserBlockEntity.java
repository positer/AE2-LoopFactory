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
            GridHelper.onFirstTick(this, owner ->
                    owner.mainNode.create(owner.getLevel(), owner.getBlockPos()));
        }
    }

    @Override
    public void setRemoved() {
        mainNode.destroy();
        super.setRemoved();
    }

    public IManagedGridNode mainNode() {
        return mainNode;
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction side) {
        return mainNode.getNode();
    }
}
