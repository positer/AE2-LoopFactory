package com.example.ae2lightoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.config.CondenserOutput;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.blockentity.misc.CondenserBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.BaseActionSource;
import appeng.util.prioritylist.IPartitionList;
import com.example.ae2lightoptimizer.item.ModItems;
import com.example.ae2lightoptimizer.block.LoopCondenserBlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives a marked loop storage cell on the loop condenser. Every server tick it drains the marked keys from the
 * network into the condenser's own power balance and settles the output directly, so the native
 * output slot never throttles the conversion.
 */
public final class CondenserLoopService {
    private static final Set<LoopCondenserBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private CondenserLoopService() {
    }

    public static void register(LoopCondenserBlockEntity condenser) {
        LOADED.add(condenser);
    }

    public static void unregister(LoopCondenserBlockEntity condenser) {
        LOADED.remove(condenser);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (LOADED.isEmpty()) {
            return;
        }
        for (LoopCondenserBlockEntity condenser : new ArrayList<>(LOADED)) {
            if (condenser.isRemoved()) {
                LOADED.remove(condenser);
                continue;
            }
            try {
                drive(condenser);
            } catch (RuntimeException ignored) {
                // A broken cell or grid must never crash the server tick.
            }
        }
    }

    
    private static void drive(LoopCondenserBlockEntity condenser) {
        if (condenser.getLevel() == null || condenser.getLevel().isClientSide()) return;
        if (!condenser.mainNode().isActive()) return;
        if (!condenser.getLevel().shouldTickBlocksAt(condenser.getBlockPos())) return;
        IGrid grid = condenser.mainNode().getGrid();
        if (grid == null) return;
        ItemStack cell = ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) condenser)
                .ae2lf$storageSlot().getStackInSlot(0);
        if (!(cell.getItem() instanceof com.example.ae2lightoptimizer.storage.LoopStorageCellItem workbench)) return;
        var config = workbench.getConfigInventory(cell);
        var keys = config.keySet();
        if (keys.isEmpty()) return;
        var upgrades = workbench.getUpgrades(cell);
        boolean inverted = upgrades.isInstalled(AEItems.INVERTER_CARD);
        boolean fuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
        var storage = grid.getStorageService().getInventory();
        var source = new BaseActionSource();

        if (!inverted && !fuzzy) {
            for (AEKey key : keys) {
                if (key instanceof AEItemKey) drain(condenser, storage, source, key, Long.MAX_VALUE);
            }
        } else {
            var builder = IPartitionList.builder();
            if (fuzzy) builder.fuzzyMode(workbench.getFuzzyMode(cell));
            builder.addAll(keys);
            var partition = builder.build();
            var mode = inverted ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;
            var available = new appeng.api.stacks.KeyCounter();
            storage.getAvailableStacks(available);
            for (var entry : available) {
                var key = entry.getKey();
                if (key instanceof AEItemKey && partition.matchesFilter(key, mode)) {
                    drain(condenser, storage, source, key, entry.getLongValue());
                }
            }
        }
        settle(condenser, storage, source);
    }

    private static void drain(CondenserBlockEntity condenser, appeng.api.storage.MEStorage storage,
                              BaseActionSource source, AEKey key, long limit) {
        long extracted = storage.extract(key, limit, Actionable.MODULATE, source);
        if (extracted > 0) {
            double credit = extracted / (double) key.getAmountPerOperation();
            ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) condenser)
                    .ae2lf$setStoredPower(condenser.getStoredPower() + credit);
        }
    }

    /** Converts stored power into the configured output in the same tick and pushes it to the network. */
    private static void settle(CondenserBlockEntity condenser,
                               appeng.api.storage.MEStorage storage,
                               appeng.api.networking.security.IActionSource source) {
        CondenserOutput output = condenser.getConfigManager().getSetting(Settings.CONDENSER_OUTPUT);
        double stored = condenser.getStoredPower();
        if (output == CondenserOutput.TRASH) {
            if (stored > 0) ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) condenser)
                    .ae2lf$setStoredPower(0);
            return;
        }
        double required = condenser.getRequiredPower();
        if (required <= 0) return;
        long produced = (long) (stored / required);
        if (produced <= 0) return;
        var item = output == CondenserOutput.SINGULARITY ? AEItems.SINGULARITY : AEItems.MATTER_BALL;
        AEItemKey key = AEItemKey.of(item.asItem());
        if (key != null) {
            long accepted = storage.insert(key, produced, Actionable.MODULATE, source);
            // Backpressure retains every unaccepted product as matter credit.
            ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) condenser)
                    .ae2lf$setStoredPower(stored - accepted * required);
        }
    }
}