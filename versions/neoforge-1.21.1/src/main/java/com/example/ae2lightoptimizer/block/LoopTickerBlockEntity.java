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
 * {@link #rate()} times. Speed cards raise the cap; the player's chosen rate is remembered.
 */
public final class LoopTickerBlockEntity extends AENetworkedBlockEntity implements IUpgradeableObject {
    public static final int MAX_CARDS = 4;
    private static final int BASE_CAP = 4;

    private final IUpgradeInventory upgrades;
    /** 0 means "follow the maximum"; a stored value is the player's explicit pick. */
    private double selectedBaseRate;
    private static final ThreadLocal<java.util.Set<LoopTickerBlockEntity>> RUNNING =
            ThreadLocal.withInitial(() -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>()));

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
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("ae2lfBaseRate", selectedBaseRate);
        upgrades.writeToNBT(tag, "upgrades", registries);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        upgrades.readFromNBT(tag, "upgrades", registries);
        selectedBaseRate = tag.contains("ae2lfBaseRate") ? tag.getDouble("ae2lfBaseRate")
                : tag.getInt("ae2lfRate") / (double) cardMultiplier();
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
        if (!server.hasChunkAt(target) || !server.shouldTickBlocksAt(target)) return;
        var running = RUNNING.get();
        if (!running.add(this)) return;
        try {
            for (int i = 0, times = rate(); i < times; i++) {
                BlockState state = server.getBlockState(target);
                if (state.isAir()) break;
                BlockEntity entity = server.getBlockEntity(target);
                if (entity != null && !entity.isRemoved() && state.getBlock() instanceof EntityBlock block) {
                    @SuppressWarnings("unchecked")
                    var ticker = (BlockEntityTicker<BlockEntity>) block.getTicker(server, state, entity.getType());
                    if (ticker != null) ticker.tick(server, target, state, entity);
                }
                state = server.getBlockState(target);
                if (state.isRandomlyTicking()) state.randomTick(server, target, server.getRandom());
            }
        } finally {
            running.remove(this);
            if (running.isEmpty()) RUNNING.remove();
        }
    }
}
