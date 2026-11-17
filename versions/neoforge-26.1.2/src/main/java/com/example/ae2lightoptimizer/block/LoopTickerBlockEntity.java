package com.example.ae2lightoptimizer.block;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Accelerates the block it faces: every server tick it runs that block's ticker and random ticks
 * {@link #rate()} times. Speed cards raise the cap; the player's chosen rate is remembered.
 */
public final class LoopTickerBlockEntity extends AEBaseBlockEntity implements IUpgradeableObject {
    public static final int MAX_CARDS = 4;
    private static final int BASE_CAP = 4;

    private final IUpgradeInventory upgrades;
    /** 0 means "follow the maximum"; a stored value is the player's explicit pick. */
    private int rate;

    public LoopTickerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOP_TICKER.get(), pos, state);
        this.upgrades = UpgradeInventories.forMachine(ModBlocks.LOOP_TICKER.get(), MAX_CARDS, this::saveChanges);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            com.example.ae2lightoptimizer.integration.LoopTickerService.register(this);
        }
    }

    @Override
    public void setRemoved() {
        com.example.ae2lightoptimizer.integration.LoopTickerService.unregister(this);
        super.setRemoved();
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
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("ae2lfRate", rate);
        upgrades.writeToNBT(out, "upgrades");
    }

    @Override
    public void loadTag(ValueInput in) {
        super.loadTag(in);
        upgrades.readFromNBT(in, "upgrades");
        rate = in.getInt("ae2lfRate").orElse(0);
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
    }

    /** Runs the facing block's own ticker and random tick {@link #rate()} times. */
    public void accelerate() {
        if (!(level instanceof ServerLevel server)) return;
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
            targetState.randomTick(server, target, server.getRandom());
            if (ticker != null) {
                ticker.tick(server, target, targetState, targetEntity);
            }
        }
    }
}