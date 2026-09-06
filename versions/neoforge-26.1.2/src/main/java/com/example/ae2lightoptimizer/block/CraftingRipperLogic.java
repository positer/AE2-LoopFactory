package com.example.ae2lightoptimizer.block;

import java.util.List;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.example.ae2lightoptimizer.integration.CraftingRipperPatterns;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CraftingRipperLogic extends PatternProviderLogic {
    private static final java.util.Set<CraftingRipperLogic> LOADED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        CraftingRipperPatterns.invalidateCatalogs();
        for (var logic : List.copyOf(LOADED)) {
            if (!logic.host.isRemoved()) logic.updatePatterns();
        }
    }

    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        for (var logic : List.copyOf(LOADED)) {
            if (!logic.host.isRemoved() && logic.host.getLevel() != null
                    && logic.host.getLevel().getServer() == event.getServer()
                    && logic.catalogDirty && logic.host.isLoopCardInstalled()
                    && logic.host.getLevel().getGameTime() >= logic.nextCatalogRefresh
                    && logic.host.getMainNode().isActive()) logic.updatePatterns();
        }
    }

    private final CraftingRipperBlockEntity host;
    private final InternalInventory slots;
    private List<IPatternDetails> available = List.of();
    private final java.util.Map<appeng.api.stacks.AEItemKey, ItemStack> observedInputs = new java.util.LinkedHashMap<>();
    private appeng.api.networking.IGrid observedGrid;
    private boolean catalogDirty = true;
    private long nextCatalogRefresh;

    public CraftingRipperLogic(CraftingRipperBlockEntity host) {
        super(host.getMainNode(), host, CraftingRipperBlockEntity.PATTERN_SLOTS);
        this.host = host;
        host.getMainNode().addService(appeng.api.networking.storage.IStorageWatcherNode.class,
                new appeng.api.networking.storage.IStorageWatcherNode() {
                    @Override public void updateWatcher(appeng.api.networking.IStackWatcher watcher) {
                        watcher.reset();
                        watcher.setWatchAll(true);
                        catalogDirty = true;
                    }
                    @Override public void onStackChange(appeng.api.stacks.AEKey key, long amount) {
                        if (amount > 0 && key instanceof appeng.api.stacks.AEItemKey item
                                && !observedInputs.containsKey(item)) catalogDirty = true;
                    }
                });
        LOADED.add(this);
        var delegate = super.getPatternInv();
        slots = new appeng.api.inventories.BaseInternalInventory() {
            @Override public int size() { return delegate.size(); }
            @Override public int getSlotLimit(int slot) { return 1; }
            @Override public ItemStack getStackInSlot(int slot) { return delegate.getStackInSlot(slot); }
            @Override public boolean isItemValid(int slot, ItemStack stack) {
                return !host.isLoopCardInstalled() && acceptsPattern(stack);
            }
            @Override public void setItemDirect(int slot, ItemStack stack) {
                // Native swap and transaction rollback must restore retained patterns even while locked.
                // All user insertion paths call isItemValid before this internal restore primitive.
                delegate.setItemDirect(slot, stack);
            }
        };
    }

    private boolean acceptsPattern(ItemStack stack) {
        var level = host.getLevel();
        if (level == null) return false;
        return CraftingRipperPatterns.supportsPattern(PatternDetailsHelper.decodePattern(stack, level), level);
    }

    @Override public InternalInventory getPatternInv() { return slots; }
    @Override public void updatePatterns() {
        if (host == null || host.getLevel() == null || isClientSide()) return;
        if (host.isLoopCardInstalled()) {
            var grid = host.getMainNode().getGrid();
            if (grid != observedGrid) {
                observedInputs.clear();
                observedGrid = grid;
            }
            if (grid != null) {
                for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
                    if (entry.getLongValue() > 0 && entry.getKey() instanceof appeng.api.stacks.AEItemKey key) {
                        observedInputs.putIfAbsent(key, key.toStack());
                    }
                }
            }
            // Keep witnessed keys until the card or network changes: CPU material reservation
            // must not revoke the exact pattern of an already submitted crafting job.
            available = CraftingRipperPatterns.enumerate(host.getLevel(), List.copyOf(observedInputs.values()));
            catalogDirty = false;
            nextCatalogRefresh = host.getLevel().getGameTime() + 20;
        } else {
            observedInputs.clear();
            observedGrid = null;
            catalogDirty = true;
            var result = new java.util.LinkedHashSet<IPatternDetails>();
            for (var stack : super.getPatternInv()) {
                var pattern = PatternDetailsHelper.decodePattern(stack, host.getLevel());
                if (CraftingRipperPatterns.supportsPattern(pattern, host.getLevel())) result.add(pattern);
            }
            available = List.copyOf(result);
        }
        ICraftingProvider.requestUpdate(host.getMainNode());
    }
    @Override public List<IPatternDetails> getAvailablePatterns() { return available; }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        // CPU-local instant and native continuations own dispatch and real output identity.
        return false;
    }
    @Override public boolean isBusy() {
        return super.isBusy() || getCraftingLockedReason() != appeng.api.config.LockCraftingMode.NONE;
    }

    /** Called once after the CPU atomically commits this ripper's complete crafting chain. */
    public void onRipperCrafted(IPatternDetails pattern) {
        var nativeLogic = (com.example.ae2lightoptimizer.mixin.PatternProviderLogicInvoker) (Object) this;
        nativeLogic.ae2lightoptimizer$onPushPatternSuccess(pattern);
        // The result exists synchronously: native result locks clear, while pulse locks remain pending.
        nativeLogic.ae2lightoptimizer$onStackReturnedToNetwork(pattern.getPrimaryOutput());
        saveChanges();
    }
    @Override public void importSettings(DataComponentMap settings, Player player) {
        if (!host.isLoopCardInstalled()) {
            super.importSettings(settings, player);
            updatePatterns();
        }
    }
}
