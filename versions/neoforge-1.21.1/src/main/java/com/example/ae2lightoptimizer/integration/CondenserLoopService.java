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
 * Drives a marked loop storage cell on the loop condenser. Every five ticks it drains the marked keys from the
 * network into the condenser's own power balance and settles the output directly, so the native
 * output slot never throttles the conversion.
 */
public final class CondenserLoopService {
    private static final Set<LoopCondenserBlockEntity> LOADED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final int INTERVAL_TICKS = 5;
    private static int tickCounter;

    private CondenserLoopService() {
    }

    public static void register(LoopCondenserBlockEntity condenser) {
        LOADED.add(condenser);
    }

    public static void unregister(LoopCondenserBlockEntity condenser) {
        LOADED.remove(condenser);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % INTERVAL_TICKS != 0 || LOADED.isEmpty()) {
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
        if (config.keySet().isEmpty()) return; // Unmarked cell: no behaviour at all.

        boolean inverted = workbench.getUpgrades(cell).isInstalled(AEItems.INVERTER_CARD);
        var builder = IPartitionList.builder();
        if (workbench.getUpgrades(cell).isInstalled(AEItems.FUZZY_CARD))
            builder.fuzzyMode(workbench.getFuzzyMode(cell));
        builder.addAll(config.keySet());
        IPartitionList partition = builder.build();
        IncludeExclude mode = inverted ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST;

        var storage = grid.getStorageService().getInventory();
        var source = new BaseActionSource();

        var available = new appeng.api.stacks.KeyCounter();
        storage.getAvailableStacks(available);
        for (var entry : available) {
            AEKey key = entry.getKey();
            if (!(key instanceof AEItemKey) || !partition.matchesFilter(key, mode)) continue;
            long extracted = storage.extract(key, entry.getLongValue(), Actionable.MODULATE, source);
            if (extracted > 0) {
                // AE2 native CondenserMEStorage divides by amount-per-operation.
                double credit = extracted / (double) key.getAmountPerOperation();
                ((com.example.ae2lightoptimizer.mixin.CondenserStorageSlotAccessor) (Object) condenser)
                        .ae2lf$setStoredPower(condenser.getStoredPower() + credit);
            }
        }

        settle(condenser, storage, source);
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