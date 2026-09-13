package com.example.ae2lightoptimizer.factory;

import appeng.api.implementations.menuobjects.ItemMenuHost;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** The physical pattern stays in the encoder item across menu close, logout and world saves. */
public final class FactoryEncoderHost extends ItemMenuHost<FactoryEncoderItem> implements FactoryEditorHost {
    private final AppEngInternalInventory patterns;
    public FactoryEncoderHost(FactoryEncoderItem item, Player player, ItemMenuHostLocator locator) {
        super(item, player, locator);
        patterns = new AppEngInternalInventory(1) {
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.getItem() instanceof FactoryPatternItem; }
            @Override protected void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (!isClientSide()) {
                    getItemStack().set(DataComponents.CONTAINER, toItemContainerContents());
                    // A coded pattern replaces the encoder's code area and keeps the encoder binding.
                    var data = FactoryPatternData.get(patterns.getStackInSlot(0));
                    var current = FactoryPatternData.get(getItemStack());
                    if (!data.code().isEmpty() && !data.code().equals(current.code()))
                        getItemStack().set(FactoryPatternData.TYPE.get(),
                                new FactoryPatternData(data.code(), current.factoryId(), ItemStack.EMPTY));
                }
            }
        };
        patterns.fromItemContainerContents(getItemStack().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
    }
    @Override public String editorTitleKey() { return "item.ae2lightoptimizer.handheld_loop_factory_encoder"; }
    @Override public InternalInventory factoryPatterns() { return patterns; }
    @Override public String factoryDraft() { return FactoryPatternData.get(getItemStack()).code(); }
    @Override public void setFactoryDraft(String draft) {
        var old = FactoryPatternData.get(getItemStack());
        getItemStack().set(FactoryPatternData.TYPE.get(), new FactoryPatternData(draft, old.factoryId(), ItemStack.EMPTY));
    }
    @Override public FactoryBlockEntity editingFactory() {
        return FactoryServer.find(getPlayer().level(), FactoryPatternData.get(getItemStack()).factoryId());
    }
    @Override public IGrid editorGrid() { var owner = editingFactory(); return owner == null ? null : owner.factoryGrid(); }
}
