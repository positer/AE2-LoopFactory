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
    public void clearRemoved() {
        super.clearRemoved();
        scheduleInit();
    }

    @Override
    public void onChunkUnloaded() {
        com.example.ae2lightoptimizer.integration.CondenserLoopService.unregister(this);
        mainNode.destroy();
        super.onChunkUnloaded();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (level != null && !level.isClientSide()) {
            mainNode.create(level, getBlockPos());
            com.example.ae2lightoptimizer.integration.CondenserLoopService.register(this);
        }
    }

    @Override
    public double getStorage() {
        var cell = ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) this)
                .ae2lf$storageSlot().getStackInSlot(0);
        if (cell.getItem() instanceof com.example.ae2lightoptimizer.storage.LoopStorageCellItem loop) {
            return loop.tier().capacityBytes() * 8.0;
        }
        return super.getStorage();
    }

    @Override
    public void setRemoved() {
        com.example.ae2lightoptimizer.integration.CondenserLoopService.unregister(this);
        mainNode.destroy();
        super.setRemoved();
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
    }

    @Override
    public void loadTag(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        mainNode.loadFromNBT(tag);
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