package com.example.ae2lightoptimizer.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.util.prioritylist.IPartitionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class LoopStorageCellInventory implements StorageCell {
    private final ItemStack cellStack;
    @Nullable
    private final ISaveProvider saveProvider;
    private final LoopStorageTier tier;
    private final Map<AEKey, Long> amounts = new LinkedHashMap<>();
    private final IPartitionList partitionList;
    private final IncludeExclude partitionMode;
    private final boolean voidUpgrade;
    private final long equalDistributionSlots;
    private boolean dirty;

    LoopStorageCellInventory(ItemStack cellStack, @Nullable ISaveProvider saveProvider) {
        this.cellStack = Objects.requireNonNull(cellStack, "cellStack");
        this.saveProvider = saveProvider;
        if (!(cellStack.getItem() instanceof LoopStorageTierProvider cellItem)) {
            throw new IllegalArgumentException("Not a loop storage cell");
        }
        this.tier = cellItem.tier();
        this.partitionList = createPartitionList(cellStack);
        this.partitionMode = isInstalled(cellStack, AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.voidUpgrade = isInstalled(cellStack, AEItems.VOID_CARD);
        this.equalDistributionSlots = isInstalled(cellStack, AEItems.EQUAL_DISTRIBUTION_CARD)
                ? configTypeSlots(cellStack) : 0;
        for (var stored : cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.<GenericStack>of())) {
            if (stored != null && stored.amount() > 0 && isRegisteredKeyType(stored.what().getType())) {
                amounts.merge(stored.what(), stored.amount(), LoopStorageCellInventory::saturatingAdd);
            }
        }
    }

    public LoopStorageTier tier() {
        return tier;
    }

    public long storedTypeCount() {
        return amounts.size();
    }

    public long usedBytes() {
        return usage(amounts).map(LoopStorageUsage::usedBytes).orElse(tier.capacityBytes());
    }

    @Override
    public CellState getStatus() {
        if (amounts.isEmpty()) {
            return CellState.EMPTY;
        }
        if (tier.infinite()) {
            return CellState.NOT_EMPTY;
        }
        var current = usage(amounts);
        if (current.isEmpty()) {
            return CellState.FULL;
        }
        var validUsage = current.get();
        if (LoopStorageAccounting.canAddNewType(tier, validUsage)) {
            return CellState.NOT_EMPTY;
        }
        if (LoopStorageAccounting.canGrowExistingType(tier, validUsage)) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    @Override
    public double getIdleDrain() {
        return tier.idleDrain();
    }

    @Override
    public boolean canFitInsideCell() {
        return amounts.isEmpty();
    }

    @Override
    public void persist() {
        if (!dirty) {
            return;
        }
        if (amounts.isEmpty()) {
            cellStack.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            var stored = new ArrayList<GenericStack>(amounts.size());
            amounts.forEach((what, amount) -> {
                if (amount > 0) {
                    stored.add(new GenericStack(what, amount));
                }
            });
            cellStack.set(AEComponents.STORAGE_CELL_INV, List.copyOf(stored));
        }
        dirty = false;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !isRegisteredKeyType(what.getType())) {
            return 0;
        }
        if (what instanceof AEItemKey itemKey) {
            var nested = StorageCells.getCellInventory(itemKey.toStack(), null);
            if (nested != null && !nested.canFitInsideCell()) {
                return 0;
            }
        }
        if (partitionList != null && !partitionList.matchesFilter(what, partitionMode)) {
            return 0;
        }

        long current = amounts.getOrDefault(what, 0L);
        if (equalDistributionSlots > 0 && !tier.infinite()) {
            long perTypeBytes = Math.max(1, tier.capacityBytes() / equalDistributionSlots);
            long perTypeCap = capAtLong(perTypeBytes, what.getType().getAmountPerByte());
            amount = Math.min(amount, Math.max(0, perTypeCap - current));
            if (amount <= 0) {
                return 0;
            }
        }
        if (tier.infinite()) {
            long accepted = Math.min(amount, Long.MAX_VALUE - current);
            if (accepted > 0 && mode == Actionable.MODULATE) {
                amounts.put(what, current + accepted);
                changed();
            }
            return voidUpgrade ? amount : accepted;
        }

        long low = 0;
        long high = Math.min(amount, Long.MAX_VALUE - current);
        while (low < high) {
            long candidate = high - (high - low) / 2;
            if (fitsAfterInsert(what, current + candidate)) {
                low = candidate;
            } else {
                high = candidate - 1;
            }
        }
        if (low > 0 && mode == Actionable.MODULATE) {
            amounts.put(what, current + low);
            changed();
        }
        return voidUpgrade ? amount : low;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!isRegisteredKeyType(what.getType())) {
            return false;
        }
        if (what instanceof AEItemKey itemKey) {
            var nested = StorageCells.getCellInventory(itemKey.toStack(), null);
            if (nested != null && !nested.canFitInsideCell()) {
                return false;
            }
        }
        // Full cells decline preference, allowing the same key type to flow into the
        // next Loop Storage Cell even for integrations that only visit preferred mounts.
        return insert(what, 1, Actionable.SIMULATE, source) == 1;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        MEStorage.checkPreconditions(what, amount, mode, source);
        long current = amounts.getOrDefault(what, 0L);
        long extracted = Math.min(current, amount);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            long remaining = current - extracted;
            if (remaining == 0) {
                amounts.remove(what);
            } else {
                amounts.put(what, remaining);
            }
            changed();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        amounts.forEach(out::add);
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    private boolean fitsAfterInsert(AEKey what, long newAmount) {
        var candidate = new LinkedHashMap<>(amounts);
        candidate.put(what, newAmount);
        return usage(candidate)
                .map(candidateUsage -> LoopStorageAccounting.fits(
                        tier, candidateUsage.typeCount(), candidateUsage.usedBytes()))
                .orElse(false);
    }

    private Optional<LoopStorageUsage> usage(Map<AEKey, Long> contents) {
        var entries = new ArrayList<LoopStorageKeyUsage<AEKey, AEKeyType>>(contents.size());
        contents.forEach((what, amount) -> entries.add(new LoopStorageKeyUsage<>(
                what, what.getType(), amount, what.getType().getAmountPerByte())));
        return LoopStorageAccounting.summarize(tier, entries);
    }

    private void changed() {
        dirty = true;
        persist();
        if (saveProvider != null) {
            saveProvider.saveChanges();
        }
    }

    private static boolean isRegisteredKeyType(AEKeyType keyType) {
        return AEKeyTypes.getAll().contains(keyType) && keyType.getAmountPerByte() > 0;
    }

    private static IPartitionList createPartitionList(ItemStack stack) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem workbench)) {
            return null;
        }
        var builder = IPartitionList.builder();
        builder.fuzzyMode(workbench.getFuzzyMode(stack));
        builder.addAll(workbench.getConfigInventory(stack).keySet());
        return builder.build();
    }

    private static boolean isInstalled(ItemStack stack, net.minecraft.world.level.ItemLike card) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem workbench)) {
            return false;
        }
        return workbench.getUpgrades(stack).isInstalled(card);
    }

    private static long configTypeSlots(ItemStack stack) {
        if (!(stack.getItem() instanceof ICellWorkbenchItem workbench)) {
            return 63;
        }
        var config = workbench.getConfigInventory(stack);
        if (!config.keySet().isEmpty() && !workbench.getUpgrades(stack).isInstalled(AEItems.FUZZY_CARD)) {
            return config.keySet().size();
        }
        return 63;
    }

    private static long capAtLong(long left, long right) {
        if (left <= 0 || right <= 0) {
            return Long.MAX_VALUE;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0) {
            return left;
        }
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
