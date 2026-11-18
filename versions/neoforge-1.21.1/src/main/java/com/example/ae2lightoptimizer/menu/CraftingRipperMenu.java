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
            DeferredRegister.create(Registries.MENU, Ae2LightOptimizer.NAMESPACE);
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingRipperMenu>> TYPE = MENUS.register(
            "crafting_ripper", () -> MenuTypeBuilder.create(CraftingRipperMenu::new, CraftingRipperBlockEntity.class)
                    .buildUnregistered(ResourceLocation.fromNamespaceAndPath(Ae2LightOptimizer.NAMESPACE, "crafting_ripper")));
    private final CraftingRipperBlockEntity host;
    @GuiSync(8) public boolean loopCardInstalled;
    @GuiSync(9) public int patternRows = 1;
    @GuiSync(10) public int firstPatternRow;
    @GuiSync(11) public int scrollAcknowledgement;
    @GuiSync(12) public boolean networkAvailable;
    @GuiSync(13) public boolean importingPatterns;
    @GuiSync(14) public long importedPatterns;
    private com.example.ae2lightoptimizer.block.RipperPatternImporter importer;
    private RipperPatternViewport viewport;
    private int requestedSequence;
    private int requestedRow;
    private boolean fullPatternSync;
    public record ScrollRequest(int row, int sequence) {}

    /** Called by the narrow native-constructor adapter before the subclass constructor body. */
    public appeng.api.inventories.InternalInventory createPatternViewport(appeng.helpers.patternprovider.PatternProviderLogic logic) {
        viewport = new RipperPatternViewport((com.example.ae2lightoptimizer.block.CraftingRipperLogic)logic);
        return viewport;
    }


    public CraftingRipperMenu(MenuType<? extends CraftingRipperMenu> type, int id, Inventory inventory,
            CraftingRipperBlockEntity host) {
        super(type, id, inventory, host);
        this.host = host;
        if (viewport == null) throw new IllegalStateException("Missing Ripper pattern viewport adapter");
        if (isServerSide()) patternRows = host.getLogic().getPatternInv().size() / 9;
        updateVisibleSlots();
        registerClientAction("ripperScroll", ScrollRequest.class, this::scroll);
        registerClientAction("ripperImportPatterns", this::importPatterns);

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

    public void importPatterns() {
        if (isClientSide()) { sendClientAction("ripperImportPatterns"); return; }
        if (importer != null || getPlayer().containerMenu != this || !stillValid(getPlayer())
                || !host.getMainNode().isActive()) return;
        importer = new com.example.ae2lightoptimizer.block.RipperPatternImporter(host, getPlayer(), getActionSource());
        importedPatterns = 0;
        importingPatterns = !importer.finished();
        if (!importingPatterns) importer = null;
    }
    @Override public void removed(net.minecraft.world.entity.player.Player player) {
        importer = null;
        super.removed(player);
    }

    public appeng.api.upgrades.IUpgradeInventory getRipperUpgrades() {
        return host.getUpgrades();
    }

    public boolean isScrollPending() { return isClientSide() && scrollAcknowledgement != requestedSequence; }
    public int requestedRow() { return isScrollPending() ? requestedRow : firstPatternRow; }
    public void requestPatternRow(int row) {
        row = com.example.ae2lightoptimizer.crafting.RipperPatternLayout.clampRow(row, patternRows);
        if (!isClientSide() || row == requestedRow()) return;
        requestedRow = row;
        sendClientAction("ripperScroll", new ScrollRequest(row, ++requestedSequence));
    }
    private void scroll(ScrollRequest request) {
        if (!isServerSide() || !stillValid(getPlayer())) return;
        firstPatternRow = com.example.ae2lightoptimizer.crafting.RipperPatternLayout.clampRow(
                request.row(), host.getLogic().getPatternInv().size() / 9);
        scrollAcknowledgement = request.sequence();
        fullPatternSync = true;
        broadcastChanges();
    }
    private void updateVisibleSlots() {
        viewport.moveTo(firstPatternRow, patternRows);
        var patterns = getSlots(SlotSemantics.ENCODED_PATTERN);
        for (int i = 0; i < patterns.size(); i++)
            ((AppEngSlot)patterns.get(i)).setSlotEnabled(viewport.isVisibleSlot(i));
    }
    @Override public void onServerDataSync(it.unimi.dsi.fastutil.shorts.ShortSet fields) {
        super.onServerDataSync(fields);
        updateVisibleSlots();
    }
    @Override public void broadcastChanges() {
        if (isServerSide()) {
            // Importing needs a reachable, powered ME network. A zero-power ripper stays active on its own
            // ad-hoc grid, so activity alone would report a usable network that has nothing to import from.
            networkAvailable = !host.isRemoved() && host.getMainNode().isActive() && host.getMainNode().isPowered();
            if (importer != null) {
                if (!stillValid(getPlayer()) || getPlayer().containerMenu != this) importer = null;
                else {
                    importer.tick();
                    importedPatterns = importer.moved();
                    if (importer.finished()) importer = null;
                }
            }
            importingPatterns = importer != null;
            loopCardInstalled = host.isLoopCardInstalled();
            int rows = host.getLogic().getPatternInv().size() / 9;
            // Keep physical slot identities stable when another viewer removes the tail.
            // The client clamps its scrollbar and requests the new window with the normal ack guard.
            int row = firstPatternRow;
            fullPatternSync |= rows != patternRows || row != firstPatternRow;
            patternRows = rows;
            firstPatternRow = row;
            updateVisibleSlots();
            // AEBaseMenu sends GuiSync before vanilla slot diffs. Send full contents first, then the ack.
            if (fullPatternSync) { broadcastFullState(); fullPatternSync = false; }
        }
        super.broadcastChanges();
    }
    @Override public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        if (isClientSide()) return net.minecraft.world.item.ItemStack.EMPTY;
        if (index >= 0 && index < slots.size()) {
            var source = slots.get(index);
            var semantic = getSlotSemantic(source);
            if ((semantic == SlotSemantics.PLAYER_INVENTORY || semantic == SlotSemantics.PLAYER_HOTBAR)
                    && source.mayPickup(player) && host.getLogic().acceptsPattern(source.getItem())) {
                var offered = source.getItem().copy();
                var remaining = host.getLogic().getPatternInv().addItems(offered.copy());
                int moved = offered.getCount() - remaining.getCount();
                if (moved > 0) source.remove(moved);
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
        }
        return super.quickMoveStack(player, index);
    }
}
