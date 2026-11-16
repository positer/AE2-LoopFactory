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
            if (!logic.host.isRemoved()) { logic.catalogDirty = true; logic.updatePatterns(); }
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
    private final RipperPatternInventory slots;
    private List<IPatternDetails> automatic = List.of();
    private List<IPatternDetails> available = List.of();
    private final java.util.Map<appeng.api.stacks.AEItemKey, ItemStack> observedInputs = new java.util.LinkedHashMap<>();
    private appeng.api.networking.IGrid observedGrid;
    private boolean catalogDirty = true;
    private long nextCatalogRefresh;

    public CraftingRipperLogic(CraftingRipperBlockEntity host) {
        super(host.getMainNode(), host, 0);
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
        slots = new RipperPatternInventory(this::acceptsPattern, () -> {
            if (this.host.getLevel() != null && !isClientSide()) { saveChanges(); updatePatterns(); }
        });
    }

    public boolean acceptsPattern(ItemStack stack) {
        var level = host.getLevel();
        if (level == null) return false;
        return CraftingRipperPatterns.supportsPattern(PatternDetailsHelper.decodePattern(stack, level), level);
    }

    @Override public RipperPatternInventory getPatternInv() { return slots; }
    @Override public void updatePatterns() {
        if (host == null || host.getLevel() == null || isClientSide()) return;
        var manual = new java.util.LinkedHashSet<IPatternDetails>();
        var coveredOutputs = new java.util.LinkedHashSet<appeng.api.stacks.AEKey>();
        for (var stack : slots.nonEmptyStacks()) {
            var declared = declaredOutput(stack);
            if (!declared.isEmpty()) coveredOutputs.add(appeng.api.stacks.AEItemKey.of(declared));
            var pattern = PatternDetailsHelper.decodePattern(stack, host.getLevel());
            if (CraftingRipperPatterns.supportsPattern(pattern, host.getLevel())) {
                manual.add(pattern);
                coveredOutputs.add(pattern.getPrimaryOutput().what());
            }
        }
        if (host.isLoopCardInstalled()) {
            var grid = host.getMainNode().getGrid();
            if (grid != observedGrid) {
                observedInputs.clear();
                observedGrid = grid;
                catalogDirty = true;
            }
            if (catalogDirty) {
                if (grid != null) {
                    for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
                        if (entry.getLongValue() > 0 && entry.getKey() instanceof appeng.api.stacks.AEItemKey key)
                            observedInputs.putIfAbsent(key, key.toStack());
                    }
                }
                // CPU reservations must not revoke a witnessed recipe or its exact components.
                automatic = CraftingRipperPatterns.enumerate(host.getLevel(), List.copyOf(observedInputs.values()));
                catalogDirty = false;
                nextCatalogRefresh = host.getLevel().getGameTime() + 20;
            }
        } else {
            observedInputs.clear();
            observedGrid = null;
            automatic = List.of();
            catalogDirty = true;
        }
        available = com.example.ae2lightoptimizer.crafting.RipperPatternPolicy.select(
                manual, automatic, coveredOutputs, pattern -> pattern.getPrimaryOutput().what());
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
    /** Read the declared target even if a datapack has invalidated the selected recipe. */
    private ItemStack declaredOutput(ItemStack stack) {
        var crafting = stack.get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
        if (crafting != null) return crafting.result();
        var smithing = stack.get(appeng.api.ids.AEComponents.ENCODED_SMITHING_TABLE_PATTERN);
        if (smithing != null) return smithing.resultItem();
        var cutting = stack.get(appeng.api.ids.AEComponents.ENCODED_STONECUTTING_PATTERN);
        return cutting == null ? ItemStack.EMPTY : cutting.output();
    }

    @Override public void addDrops(java.util.List<ItemStack> drops) {
        super.addDrops(drops);
        for (var stack : slots.nonEmptyStacks()) drops.add(stack.copy());
    }
    @Override public void clearContent() {
        super.clearContent();
        slots.clear();
    }
    @Override public void writeToNBT(net.minecraft.world.level.storage.ValueOutput tag) {
        super.writeToNBT(tag);
        slots.write(tag);
    }
    @Override public void readFromNBT(net.minecraft.world.level.storage.ValueInput tag) {
        super.readFromNBT(tag);
        slots.read(tag);
        catalogDirty = true;
    }

    @Override public void exportSettings(DataComponentMap.Builder settings) {
        var contents = slots.nonEmptyStacks();
        // A native memory-card component can encode at most 256 entries. Never truncate a library.
        if (contents.size() <= 256)
            settings.set(appeng.api.ids.AEComponents.EXPORTED_PATTERNS,
                    net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));
    }
    @Override public void importSettings(DataComponentMap settings, Player player) {
        var desired = settings.get(appeng.api.ids.AEComponents.EXPORTED_PATTERNS);
        if (desired == null || player == null || player.level().isClientSide()) return;
        var requested = net.minecraft.core.NonNullList.withSize(desired.getSlots(), ItemStack.EMPTY);
        desired.copyInto(requested);
        slots.batch(() -> {
            if (!player.getAbilities().instabuild) {
                for (var old : slots.nonEmptyStacks()) {
                    var returned = old.is(appeng.core.definitions.AEItems.CRAFTING_PATTERN.asItem())
                            || old.is(appeng.core.definitions.AEItems.PROCESSING_PATTERN.asItem())
                            || old.is(appeng.core.definitions.AEItems.SMITHING_TABLE_PATTERN.asItem())
                            || old.is(appeng.core.definitions.AEItems.STONECUTTING_PATTERN.asItem())
                            ? appeng.core.definitions.AEItems.BLANK_PATTERN.stack(old.getCount()) : old.copy();
                    player.getInventory().placeItemBackInInventory(returned);
                }
            }
            slots.clear();
            for (var stack : requested) {
                if (!acceptsPattern(stack)) continue;
                var blank = appeng.core.definitions.AEItems.BLANK_PATTERN.stack();
                if (!player.getAbilities().instabuild && player.getInventory().countItem(blank.getItem()) == 0) continue;
                var remaining = slots.addItems(stack.copyWithCount(1));
                if (remaining.isEmpty() && !player.getAbilities().instabuild)
                    new appeng.util.inv.PlayerInternalInventory(player.getInventory()).removeItems(1, blank, null);
            }
        });
    }
}
