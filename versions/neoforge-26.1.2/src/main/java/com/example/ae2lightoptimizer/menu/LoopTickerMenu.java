package com.example.ae2lightoptimizer.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.api.LoopTickerHost;
import com.example.ae2lightoptimizer.block.LoopTickerBlockEntity;
import com.example.ae2lightoptimizer.part.LoopTickerPart;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shows the accelerator rate and lets the player adjust it. */
public final class LoopTickerMenu extends AEBaseMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<LoopTickerMenu>> PART_TYPE = MENUS.register(
            "loop_ticker_part", () -> MenuTypeBuilder.create(LoopTickerMenu::new, LoopTickerPart.class)
                    .buildUnregistered(Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "loop_ticker_part")));

    public static final DeferredHolder<MenuType<?>, MenuType<LoopTickerMenu>> TYPE = MENUS.register(
            "loop_ticker", () -> MenuTypeBuilder.create(LoopTickerMenu::new, LoopTickerBlockEntity.class)
                    .buildUnregistered(Identifier.fromNamespaceAndPath(Ae2LightOptimizer.MOD_ID, "loop_ticker")));

    private final LoopTickerHost host;
    @GuiSync(1) public int rate;
    @GuiSync(2) public int maxRate;

    public LoopTickerMenu(MenuType<? extends LoopTickerMenu> type, int id, Inventory playerInventory,
                          LoopTickerHost host) {
        super(type, id, playerInventory, host);
        this.host = host;
        for (int slot = 0; slot < LoopTickerHost.MAX_CARDS; slot++) {
            addSlot(new appeng.menu.slot.RestrictedInputSlot(
                    appeng.menu.slot.RestrictedInputSlot.PlacableItemType.UPGRADES, host.getUpgrades(), slot),
                    SlotSemantics.UPGRADE);
        }
        createPlayerInventorySlots(playerInventory);
    }

    public appeng.api.upgrades.IUpgradeInventory getTickerUpgrades() { return host.getUpgrades(); }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            rate = host.rate();
            maxRate = host.maxRate();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (isServerSide()) {
            host.setRate(id);
        }
        return true;
    }
}