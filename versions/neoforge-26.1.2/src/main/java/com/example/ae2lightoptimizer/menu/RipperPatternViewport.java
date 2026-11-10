package com.example.ae2lightoptimizer.menu;

import appeng.api.inventories.BaseInternalInventory;
import com.example.ae2lightoptimizer.block.CraftingRipperLogic;
import com.example.ae2lightoptimizer.crafting.RipperPatternLayout;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** A per-menu window. Client slot packets never mutate the client block entity's complete library. */
public final class RipperPatternViewport extends BaseInternalInventory {
    private final CraftingRipperLogic logic;
    private final boolean client;
    private final NonNullList<ItemStack> received = NonNullList.withSize(RipperPatternLayout.VISIBLE_SLOTS, ItemStack.EMPTY);
    private int firstRow;
    private int rows = 1;
    public RipperPatternViewport(CraftingRipperLogic logic) { this.logic = logic; client = logic.isClientSide(); }
    public void moveTo(int firstRow, int rows) { this.firstRow = firstRow; this.rows = rows; }
    public boolean isVisibleSlot(int slot) { return RipperPatternLayout.storageSlot(firstRow, slot) / 9 < rows; }
    @Override public int size() { return RipperPatternLayout.VISIBLE_SLOTS; }
    @Override public int getSlotLimit(int slot) { return isVisibleSlot(slot) ? 1 : 0; }
    @Override public ItemStack getStackInSlot(int slot) {
        return client ? received.get(slot) : logic.getPatternInv().getStackInSlot(RipperPatternLayout.storageSlot(firstRow, slot));
    }
    @Override public void setItemDirect(int slot, ItemStack stack) {
        if (client) received.set(slot, stack);
        else logic.getPatternInv().setItemDirect(RipperPatternLayout.storageSlot(firstRow, slot), stack);
    }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return isVisibleSlot(slot) && logic.acceptsPattern(stack);
    }
}
