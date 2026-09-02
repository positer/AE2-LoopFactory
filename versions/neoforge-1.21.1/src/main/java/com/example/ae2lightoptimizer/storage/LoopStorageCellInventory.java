package com.example.ae2lightoptimizer.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

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
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import appeng.util.prioritylist.IPartitionList;

/**
 * Universal AE storage inventory whose shared byte budget accepts every registered AE key type.
 */
public final class LoopStorageCellInventory implements StorageCell {
    private final ItemStack cellStack;
    private final LoopStorageTier tier;
    @Nullable
    private final ISaveProvider host;
    private final Object2LongOpenHashMap<AEKey> storedAmounts = new Object2LongOpenHashMap<>();
    private final IPartitionList partitionList;
    private final IncludeExclude partitionMode;
    private final boolean voidUpgrade;
    private final long equalDistributionSlots;
    private boolean dirty;

    public LoopStorageCellInventory(ItemStack cellStack, @Nullable ISaveProvider host) {
        this.cellStack = Objects.requireNonNull(cellStack, "cellStack");
        if (!(cellStack.getItem() instanceof LoopStorageTierProvider cellItem)) {
            throw new IllegalArgumentException("Item stack is not a cyclic storage cell");
        }
        this.tier = cellItem.tier();
        this.host = host;
        this.partitionList = createPartitionList(cellStack);
        this.partitionMode = isInstalled(cellStack, AEItems.INVERTER_CARD)
                ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
        this.voidUpgrade = isInstalled(cellStack, AEItems.VOID_CARD);
        this.equalDistributionSlots = isInstalled(cellStack, AEItems.EQUAL_DISTRIBUTION_CARD)
                ? configTypeSlots(cellStack) : 0;
        load();
    }

    public LoopStorageTier tier() {
        return tier;
    }

    public long storedTypeCount() {
        return storedAmounts.size();
    }

    public long usedBytes() {
        return currentUsage().map(LoopStorageUsage::usedBytes).orElse(tier.capacityBytes());
    }

    private void load() {
        List<GenericStack> storedStacks = cellStack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
        for (GenericStack stack : storedStacks) {
            if (stack.amount() <= 0 || !isRegisteredKeyType(stack.what().getType())) {
                continue;
            }
            storedAmounts.mergeLong(stack.what(), stack.amount(), LoopStorageCellInventory::saturatingAdd);
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        appeng.api.storage.MEStorage.checkPreconditions(what, amount, mode, source);
        if (amount == 0 || !isRegisteredKeyType(what.getType())) {
            return 0;
        }
        if (!canStoreNestedCell(what)) {
            return 0;
        }
        if (partitionList != null && !partitionList.matchesFilter(what, partitionMode)) {
            return 0;
        }

        long currentAmount = storedAmounts.getLong(what);
        long maximumByAmount = Long.MAX_VALUE - currentAmount;
        long requested = Math.min(amount, maximumByAmount);
        if (equalDistributionSlots > 0 && !tier.infinite()) {
            long perTypeBytes = Math.max(1, tier.capacityBytes() / equalDistributionSlots);
            long perTypeCap = capAtLong(perTypeBytes, what.getType().getAmountPerByte());
            requested = Math.min(requested, Math.max(0, perTypeCap - currentAmount));
        }
        if (requested <= 0) {
            return 0;
        }

        long inserted = tier.infinite() ? requested : findMaximumInsert(what, currentAmount, requested);
        if (inserted > 0 && mode == Actionable.MODULATE) {
            storedAmounts.put(what, currentAmount + inserted);
            saveChanges();
        }
        return voidUpgrade ? amount : inserted;
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        if (!isRegisteredKeyType(what.getType()) || !canStoreNestedCell(what)) {
            return false;
        }
        // Advertise only cells that can currently accept this key. This lets key-type-aware
        // routers skip a full cell and continue with the next Loop Storage Cell.
        return insert(what, 1, Actionable.SIMULATE, source) == 1;
    }

    private long findMaximumInsert(AEKey what, long currentAmount, long requested) {
        if (fitsWithAmount(what, currentAmount, requested)) {
            return requested;
        }

        long low = 0;
        long high = requested;
        while (low < high) {
            long middle = high - (high - low) / 2;
            if (fitsWithAmount(what, currentAmount, middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private boolean fitsWithAmount(AEKey what, long currentAmount, long addition) {
        try {
            long updatedAmount = Math.addExact(currentAmount, addition);
            if (currentAmount == 0) {
                storedAmounts.put(what, updatedAmount);
            } else {
                storedAmounts.replace(what, updatedAmount);
            }
            return currentUsage().map(usage -> LoopStorageAccounting.fits(
                    tier, usage.typeCount(), usage.usedBytes())).orElse(false);
        } catch (ArithmeticException overflow) {
            return false;
        } finally {
            if (currentAmount == 0) {
                storedAmounts.removeLong(what);
            } else {
                storedAmounts.put(what, currentAmount);
            }
        }
    }

    private boolean canStoreNestedCell(AEKey what) {
        if (!(what instanceof AEItemKey itemKey)) {
            return true;
        }
        // Preserve the original component-rich item stack when checking recursive cells.
        // Generic wrapped stacks are not themselves registered storage-cell items.
        var nestedCell = StorageCells.getCellInventory(itemKey.toStack(), null);
        return nestedCell == null || nestedCell.canFitInsideCell();
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        appeng.api.storage.MEStorage.checkPreconditions(what, amount, mode, source);
        long currentAmount = storedAmounts.getLong(what);
        long extracted = Math.min(amount, currentAmount);
        if (extracted > 0 && mode == Actionable.MODULATE) {
            long remainder = currentAmount - extracted;
            if (remainder == 0) {
                storedAmounts.removeLong(what);
            } else {
                storedAmounts.put(what, remainder);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(storedAmounts)) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public CellState getStatus() {
        if (storedAmounts.isEmpty()) {
            return CellState.EMPTY;
        }
        if (tier.infinite()) {
            return CellState.NOT_EMPTY;
        }

        var usage = currentUsage();
        if (usage.isEmpty()) {
            return CellState.FULL;
        }
        var current = usage.get();
        if (LoopStorageAccounting.canAddNewType(tier, current)) {
            return CellState.NOT_EMPTY;
        }
        if (LoopStorageAccounting.canGrowExistingType(tier, current)) {
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
        return storedAmounts.isEmpty();
    }

    @Override
    public void persist() {
        if (!dirty) {
            return;
        }

        List<GenericStack> storedStacks = new ArrayList<>(storedAmounts.size());
        for (var entry : Object2LongMaps.fastIterable(storedAmounts)) {
            if (entry.getLongValue() > 0) {
                storedStacks.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }

        if (storedStacks.isEmpty()) {
            cellStack.remove(AEComponents.STORAGE_CELL_INV);
        } else {
            cellStack.set(AEComponents.STORAGE_CELL_INV, List.copyOf(storedStacks));
        }
        dirty = false;
    }

    private void saveChanges() {
        dirty = true;
        persist();
        if (host != null) {
            host.saveChanges();
        }
    }

    private java.util.Optional<LoopStorageUsage> currentUsage() {
        List<LoopStorageKeyUsage<AEKey, Object>> entries = new ArrayList<>(storedAmounts.size());
        for (var entry : Object2LongMaps.fastIterable(storedAmounts)) {
            AEKey what = entry.getKey();
            entries.add(new LoopStorageKeyUsage<>(
                    what,
                    what.getType(),
                    entry.getLongValue(),
                    what.getType().getAmountPerByte()));
        }
        return LoopStorageAccounting.summarize(tier, entries);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
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
}
