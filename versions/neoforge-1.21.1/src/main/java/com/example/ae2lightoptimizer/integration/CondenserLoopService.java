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
 * Drives the Loop Card on a Matter Condenser. Every five ticks it drains the marked keys from the
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
        // The condenser has no upgrade inventory of its own yet; the card is looked up in whatever
        // inventory it exposes so the driver works the moment an insertion path exists.

        IGrid grid = condenser.mainNode().getGrid();
        if (grid == null) return;

        ItemStack cell = condenser.getInternalInventory().getStackInSlot(0);
        if (cell.isEmpty() || !(cell.getItem() instanceof ICellWorkbenchItem workbench)) return;
        var config = workbench.getConfigInventory(cell);
        if (config.keySet().isEmpty()) return; // Unmarked cell: no behaviour at all.

        boolean inverted = workbench.getUpgrades(cell).isInstalled(AEItems.INVERTER_CARD);
        var builder = IPartitionList.builder();
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
            if (!partition.matchesFilter(key, mode)) continue;
            long extracted = storage.extract(key, entry.getLongValue(), Actionable.MODULATE, source);
            if (extracted > 0) {
                condenser.addPower(extracted * (double) key.getAmountPerOperation());
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
            if (stored > 0) condenser.addPower(-stored);
            return;
        }
        double required = condenser.getRequiredPower();
        if (required <= 0) return;
        long produced = (long) (stored / required);
        if (produced <= 0) return;
        double spent = produced * required;
        condenser.addPower(-spent);
        var item = output == CondenserOutput.SINGULARITY ? AEItems.SINGULARITY : AEItems.MATTER_BALL;
        AEItemKey key = AEItemKey.of(item.asItem());
        if (key != null) {
            storage.insert(key, produced, Actionable.MODULATE, source);
        }
    }
}