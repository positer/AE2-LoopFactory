package com.example.ae2lightoptimizer.block;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Accelerates the block it faces: every server tick it runs that block's ticker and random ticks
 * {@link #rate()} times. Speed cards raise the cap; the player's chosen rate is remembered.
 */
public final class LoopTickerBlockEntity extends AENetworkedBlockEntity
        implements IUpgradeableObject, com.example.ae2lightoptimizer.api.LoopTickerHost {
    public static final int MAX_CARDS = 4;
    private static final int BASE_CAP = 4;

    private final IUpgradeInventory upgrades;
    /** 0 means "follow the maximum"; a stored value is the player's explicit pick. */
    private double selectedBaseRate;

    public LoopTickerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOP_TICKER.get(), pos, state);
        getMainNode().setFlags(appeng.api.networking.GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0);
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

    @Override
    public void addAdditionalDrops(net.minecraft.world.level.Level level, BlockPos pos,
                                   java.util.List<net.minecraft.world.item.ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var stack : upgrades) if (!stack.isEmpty()) drops.add(stack.copy());
    }

    @Override
    public void clearContent() { super.clearContent(); upgrades.clear(); }

    public int cardMultiplier() {
        return 1 << Math.min(MAX_CARDS, upgrades.getInstalledUpgrades(appeng.core.definitions.AEItems.SPEED_CARD));
    }

    @Override
    public void saveRate() {
        saveChanges();
    }

    public int maxRate() { return BASE_CAP * cardMultiplier(); }

    public int rate() {
        return selectedBaseRate <= 0 ? maxRate()
                : Math.max(1, Math.min(maxRate(), (int) Math.round(selectedBaseRate * cardMultiplier())));
    }

    public void setRate(int value) {
        selectedBaseRate = Math.max(1, Math.min(value, maxRate())) / (double) cardMultiplier();
        saveChanges();
    }

    @Override
    public void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putDouble("ae2lfBaseRate", selectedBaseRate);
        upgrades.writeToNBT(out, "upgrades");
    }

    @Override
    public void loadTag(ValueInput in) {
        super.loadTag(in);
        upgrades.readFromNBT(in, "upgrades");
        selectedBaseRate = in.getDoubleOr("ae2lfBaseRate", in.getInt("ae2lfRate").orElse(0) / (double) cardMultiplier());
    }

    public Direction facing() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING) : Direction.NORTH;
    }

    /** Additive ticks preserve the world clock and other accelerators' schedules. */
    public void accelerate() {
        if (!(level instanceof ServerLevel server) || !getMainNode().isActive()) return;
        BlockPos target = getBlockPos().relative(facing());
        com.example.ae2lightoptimizer.integration.LoopTickerTarget.accelerate(this, server, target, rate());
    }
}
