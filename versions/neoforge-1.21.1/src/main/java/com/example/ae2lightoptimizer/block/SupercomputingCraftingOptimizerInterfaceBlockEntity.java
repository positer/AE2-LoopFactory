package com.example.ae2lightoptimizer.block;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class SupercomputingCraftingOptimizerInterfaceBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost {
    private static final IGridNodeListener<SupercomputingCraftingOptimizerInterfaceBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(SupercomputingCraftingOptimizerInterfaceBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }

                @Override
                public void onStateChanged(SupercomputingCraftingOptimizerInterfaceBlockEntity owner,
                                           IGridNode node, State state) {
                    owner.updateConnectedState(node.isActive());
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(8.0)
            .setVisualRepresentation(ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get())
            .setInWorldNode(true)
            .setTagName("mainNode");

    public SupercomputingCraftingOptimizerInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, optimizer ->
                    { optimizer.mainNode.create(optimizer.getLevel(), optimizer.getBlockPos()); com.example.ae2lightoptimizer.factory.UniqueNetworkServices.add(optimizer, optimizer.mainNode, com.example.ae2lightoptimizer.factory.UniqueNetworkServices.Kind.OPTIMIZER, false); });
        }
    }

    @Override
    public void setRemoved() {
        com.example.ae2lightoptimizer.factory.UniqueNetworkServices.remove(this);
        mainNode.destroy();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        mainNode.saveToNBT(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mainNode.loadFromNBT(tag);
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction side) {
        return mainNode.getNode();
    }

    public void setOwningPlayer(Player player) {
        mainNode.setOwningPlayer(player);
    }

    private void updateConnectedState(boolean connected) {
        if (level != null && !level.isClientSide
                && getBlockState().getValue(SupercomputingCraftingOptimizerInterfaceBlock.CONNECTED) != connected) {
            level.setBlock(worldPosition,
                    getBlockState().setValue(SupercomputingCraftingOptimizerInterfaceBlock.CONNECTED, connected),
                    Block.UPDATE_CLIENTS);
        }
    }
}
