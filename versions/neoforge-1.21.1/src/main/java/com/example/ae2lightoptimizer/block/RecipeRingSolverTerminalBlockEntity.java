package com.example.ae2lightoptimizer.block;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import com.example.ae2lightoptimizer.solver.RecipeRingSolver;
import com.example.ae2lightoptimizer.solver.RingSolveRequest;
import com.example.ae2lightoptimizer.solver.RingSolveResult;
import com.example.ae2lightoptimizer.crafting.BatchOptimizationMode;
import com.example.ae2lightoptimizer.crafting.SinglePatternBatchPlan;
import com.example.ae2lightoptimizer.crafting.SinglePatternBatchPlanner;
import com.example.ae2lightoptimizer.crafting.RingMaterialReservePlan;
import com.example.ae2lightoptimizer.crafting.RingMaterialReservePolicy;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RecipeRingSolverTerminalBlockEntity extends BlockEntity
        implements IInWorldGridNodeHost {
    private static final RecipeRingSolver SOLVER = new RecipeRingSolver();
    private static final SinglePatternBatchPlanner BATCH_PLANNER = new SinglePatternBatchPlanner();
    private static final RingMaterialReservePolicy MATERIAL_RESERVE_POLICY = new RingMaterialReservePolicy();
    private static final IGridNodeListener<RecipeRingSolverTerminalBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(RecipeRingSolverTerminalBlockEntity owner, IGridNode node) {
                    owner.setChanged();
                }

                @Override
                public void onStateChanged(RecipeRingSolverTerminalBlockEntity owner, IGridNode node, State state) {
                    owner.updateConnectedState(node.isActive());
                }
            };

    private final IManagedGridNode mainNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setFlags(GridFlags.REQUIRE_CHANNEL)
            .setExposedOnSides(EnumSet.allOf(Direction.class))
            .setIdlePowerUsage(2.0)
            .setVisualRepresentation(ModBlocks.RECIPE_RING_SOLVER_TERMINAL.get())
            .setInWorldNode(true)
            .setTagName("mainNode");

    public RecipeRingSolverTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECIPE_RING_SOLVER_TERMINAL.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            GridHelper.onFirstTick(this, terminal ->
                    terminal.mainNode.create(terminal.getLevel(), terminal.getBlockPos()));
        }
    }

    @Override
    public void setRemoved() {
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
                && getBlockState().getValue(RecipeRingSolverTerminalBlock.CONNECTED) != connected) {
            level.setBlock(worldPosition,
                    getBlockState().setValue(RecipeRingSolverTerminalBlock.CONNECTED, connected),
                    Block.UPDATE_CLIENTS);
        }
    }

    public boolean isNetworkOnline() {
        return mainNode.isOnline();
    }

    public RingSolveResult solve(RingSolveRequest request) {
        return SOLVER.solve(request);
    }

    public Optional<SinglePatternBatchPlan> solveSinglePatternGrowth(
            long requestedTarget, long outputPerCraft, long targetInputPerCraft, int otherInputCount) {
        return BATCH_PLANNER.plan(
                        requestedTarget, outputPerCraft, targetInputPerCraft, otherInputCount, true, true)
                .filter(plan -> plan.mode() == BatchOptimizationMode.CIRCULATING);
    }

    public Optional<RingMaterialReservePlan> solveMaterialDemand(
            long repetitions, long materialPerCycle, long availableMaterial) {
        return MATERIAL_RESERVE_POLICY.plan(repetitions, materialPerCycle, availableMaterial);
    }
}
