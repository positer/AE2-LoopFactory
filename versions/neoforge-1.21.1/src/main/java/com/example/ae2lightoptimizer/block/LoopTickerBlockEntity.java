package com.example.ae2lightoptimizer.block;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Accelerates the block it faces: every server tick it runs that block's ticker and random ticks
 * {@link #rate()} times. It draws no power of its own, but only runs while the grid is powered and
 * the network has energy left.
 */
public final class LoopTickerBlockEntity extends AENetworkedBlockEntity implements IUpgradeableObject {
    public static final int MAX_CARDS = 4;
    private static final int BASE_CAP = 4;

    private final IUpgradeInventory upgrades;
    /** 0 means "follow the maximum"; a stored value is the player's explicit pick. */
    private int rate;

    public LoopTickerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOP_TICKER.get(), pos, state);
        // No idle draw: the accelerator itself is free, it only needs a live network.
        getMainNode().setIdlePowerUsage(0);
        this.upgrades = UpgradeInventories.forMachine(ModBlocks.LOOP_TICKER.get(), MAX_CARDS, this::saveChanges);
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    public int maxRate() {
        int installed = 0;
        for (int slot = 0; slot < upgrades.size(); slot++) {
            if (!upgrades.getStackInSlot(slot).isEmpty()) {
                installed++;
            }
        }
        return BASE_CAP << Math.min(installed, MAX_CARDS);
    }

    public int rate() {
        return rate <= 0 ? maxRate() : Math.min(rate, maxRate());
    }

    public void setRate(int value) {
        rate = Math.max(1, Math.min(value, maxRate()));
        saveChanges();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ae2lfRate", rate);
        upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        upgrades.readFromNBT(tag, "upgrades", registries);
        rate = tag.getInt("ae2lfRate");
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
    }

    /** True only while the node is online and the grid can still hand out power. */
    public boolean powered() {
        if (!getMainNode().isActive() || getMainNode().getNode() == null) return false;
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var energy = grid.getEnergyService();
        if (energy == null || !energy.isNetworkPowered()) return false;
        // A zero-amount simulation is the accepted availability probe: it never draws power.
        return energy.extractAEPower(0.0, appeng.api.config.Actionable.SIMULATE,
                appeng.api.config.PowerMultiplier.ONE) >= 0;
    }

    /** Runs the facing block's own ticker and random tick {@link #rate()} times. */
    public void accelerate() {
        if (!(level instanceof ServerLevel server)) return;
        if (!powered()) return;
        BlockPos target = getBlockPos().relative(facing());
        BlockState targetState = server.getBlockState(target);
        if (targetState.isAir()) return;
        int times = rate();
        BlockEntity targetEntity = server.getBlockEntity(target);
        BlockEntityTicker<BlockEntity> ticker = null;
        if (targetEntity != null && targetState.getBlock() instanceof EntityBlock entityBlock) {
            @SuppressWarnings("unchecked")
            var found = (BlockEntityTicker<BlockEntity>) entityBlock.getTicker(
                    server, targetState, targetEntity.getType());
            ticker = found;
        }
        for (int i = 0; i < times; i++) {
            targetState.randomTick(server, target, server.random);
            if (ticker != null) {
                ticker.tick(server, target, targetState, targetEntity);
            }
        }
    }
}