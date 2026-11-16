package com.example.ae2lightoptimizer.block;

import appeng.api.inventories.BaseInternalInventory;
import com.example.ae2lightoptimizer.crafting.RipperPatternLayout;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Sparse, stable slot identities with one completely empty row after the last occupied row. */
public final class RipperPatternInventory extends BaseInternalInventory {
    private final TreeMap<Integer, ItemStack> stacks = new TreeMap<>();
    private final Predicate<ItemStack> accepts;
    private final Runnable changed;
    private int batching;
    private boolean dirty;

    public RipperPatternInventory(Predicate<ItemStack> accepts, Runnable changed) {
        this.accepts = accepts;
        this.changed = changed;
    }
    @Override public int size() { return RipperPatternLayout.slotsAfter(stacks.isEmpty() ? -1 : stacks.lastKey()); }
    @Override public int getSlotLimit(int slot) { return 1; }
    @Override public ItemStack getStackInSlot(int slot) { return stacks.getOrDefault(slot, ItemStack.EMPTY); }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && !stack.isEmpty() && accepts.test(stack);
    }
    /**
     * Pattern upload and migration from other AE2 addons keep the physical slot index they read. The tail
     * row shrinks when its last pattern leaves, so that index can sit past the current logical size; grow
     * it back instead of failing the platform's range check, and never drop a pattern the ripper accepts.
     */
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || slot < 0 || !accepts.test(stack)) return stack;
        if (!getStackInSlot(slot).isEmpty()) return stack;
        if (simulate) return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        batch(() -> setItemDirect(slot, stack.copyWithCount(1)));
        return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }
    @Override public void setItemDirect(int slot, ItemStack stack) {
        // Rollback can restore a tail slot after removal has shrunk the logical capacity.
        if (slot < 0) throw new IndexOutOfBoundsException(slot);
        RipperPatternLayout.slotsAfter(slot);
        if (stack.isEmpty()) stacks.remove(slot); else stacks.put(slot, stack);
        dirty = true;
        if (batching == 0) { dirty = false; changed.run(); }
    }
    @Override public ItemStack addItems(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!accepts.test(stack)) return stack;
        // Simulation has the same append capacity as execution, even before the empty row grows.
        if (simulate) return ItemStack.EMPTY;
        var remaining = new ItemStack[] { stack };
        batch(() -> remaining[0] = super.addItems(stack, false));
        return remaining[0];
    }
    @Override public void clear() {
        batch(() -> { stacks.clear(); dirty = true; });
    }
    public List<Integer> occupiedSlots() { return List.copyOf(stacks.keySet()); }
    public List<ItemStack> nonEmptyStacks() { return List.copyOf(stacks.values()); }
    public void batch(Runnable operation) {
        batching++;
        try { operation.run(); }
        finally {
            batching--;
            if (batching == 0 && dirty) { dirty = false; changed.run(); }
        }
    }
    public void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        var entries = new net.minecraft.nbt.ListTag();
        for (var entry : stacks.entrySet()) {
            var item = new net.minecraft.nbt.CompoundTag();
            item.putInt("Slot", entry.getKey());
            entries.add(entry.getValue().save(registries, item));
        }
        tag.put("patterns", entries);
    }
    public void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        stacks.clear();
        for (var raw : tag.getList("patterns", 10)) {
            var entry = (net.minecraft.nbt.CompoundTag)raw;
            int slot = entry.getInt("Slot");
            var item = ItemStack.parseOptional(registries, entry);
            if (slot >= 0 && !item.isEmpty()) {
                RipperPatternLayout.slotsAfter(slot);
                stacks.put(slot, item);
            }
        }
    }

}
