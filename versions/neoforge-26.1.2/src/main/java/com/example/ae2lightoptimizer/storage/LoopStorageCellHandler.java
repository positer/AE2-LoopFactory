package com.example.ae2lightoptimizer.storage;

import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class LoopStorageCellHandler implements ICellHandler {
    public static final LoopStorageCellHandler INSTANCE = new LoopStorageCellHandler();

    private LoopStorageCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof LoopStorageTierProvider;
    }

    @Override
    @Nullable
    public StorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (!isCell(stack)) {
            return null;
        }
        return new LoopStorageCellInventory(stack, host);
    }

    public void addCellInformationToTooltip(ItemStack stack, Consumer<Component> lines) {
        if (!(getCellInventory(stack, null) instanceof LoopStorageCellInventory inventory)) {
            return;
        }
        var tier = inventory.tier();
        if (tier.infinite()) {
            lines.accept(Component.translatable("tooltip.ae2lightoptimizer.loop_storage_cell.capacity_infinite"));
        } else {
            lines.accept(Component.translatable(
                    "tooltip.ae2lightoptimizer.loop_storage_cell.bytes",
                    inventory.usedBytes(), tier.capacityBytes()));
        }
        if (tier.hasTypeLimit()) {
            lines.accept(Component.translatable(
                    "tooltip.ae2lightoptimizer.loop_storage_cell.types",
                    inventory.storedTypeCount(), tier.maxTypes()));
        }
    }
}
