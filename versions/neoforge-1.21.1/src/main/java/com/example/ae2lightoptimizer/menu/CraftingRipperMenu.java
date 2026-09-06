package com.example.ae2lightoptimizer.menu;

import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.menu.slot.AppEngSlot;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CraftingRipperMenu extends PatternProviderMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingRipperMenu>> TYPE = MENUS.register(
            "crafting_ripper", () -> MenuTypeBuilder.create(CraftingRipperMenu::new, CraftingRipperBlockEntity.class)
                    .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "crafting_ripper")));
    private final CraftingRipperBlockEntity host;
    @GuiSync(8) public boolean loopCardInstalled;

    public CraftingRipperMenu(MenuType<? extends CraftingRipperMenu> type, int id, Inventory inventory,
            CraftingRipperBlockEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        // Keep AE2's return inventory for compatibility, but expose no return slots on either menu side.
        for (var slot : getSlots(SlotSemantics.STORAGE)) {
            if (slot instanceof AppEngSlot aeSlot) aeSlot.setSlotEnabled(false);
        }
        // Extensions may already create slots for this exact host inventory in the native constructor.
        if (getSlots(SlotSemantics.UPGRADE).isEmpty()) {
            addSlot(new RestrictedInputSlot(RestrictedInputSlot.PlacableItemType.UPGRADES,
                    host.getUpgrades(), 0), SlotSemantics.UPGRADE);
        }
    }

    public appeng.api.upgrades.IUpgradeInventory getRipperUpgrades() {
        return host.getUpgrades();
    }

    @Override public void broadcastChanges() {
        if (isServerSide()) loopCardInstalled = host.isLoopCardInstalled();
        super.broadcastChanges();
    }
}
