package com.example.ae2lightoptimizer.block;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.DimensionalBlockPos;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.Platform;
import com.example.ae2lightoptimizer.crafting.RipperPatternLayout;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

/** Menu-owned transfer of existing items; closing the menu cancels only uncommitted work. */
public final class RipperPatternImporter {
    public static final int WORK_PER_TICK = 128;
    private final CraftingRipperBlockEntity host;
    private final IGrid grid;
    private final Player player;
    private final IActionSource action;
    private final List<StoredPattern> stored = new ArrayList<>();
    private final List<Device> devices = new ArrayList<>();
    private int storedIndex, deviceIndex, destinationSlot;
    private long moved, lastTick = Long.MIN_VALUE;
    private boolean finished;

    private static final class StoredPattern {
        final AEItemKey key;
        long remaining;
        StoredPattern(AEItemKey key, long remaining) { this.key = key; this.remaining = remaining; }
    }
    private static final class Device {
        final PatternContainer container;
        final InternalInventory inventory;
        final appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler;
        final InternalInventory assemblerInventory;
        final List<Integer> sparseSlots;
        int cursor;
        Device(PatternContainer container, InternalInventory inventory) {
            this.assembler = null;
            this.assemblerInventory = null;
            this.container = container;
            this.inventory = inventory;
            // Sparse libraries may have a very high last index; visit only occupied entries.
            this.sparseSlots = inventory instanceof RipperPatternInventory ripper ? ripper.occupiedSlots() : null;
        }
        Device(appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler) {
            this.container = null;
            this.assembler = assembler;
            this.assemblerInventory = assembler.getInternalInventory();
            // Both pinned AE2 menus expose the physical pattern at slot 10. Slots 0-9 are materials/output.
            this.inventory = assemblerInventory.getSlotInv(10);
            this.sparseSlots = null;
        }
        long sortOrder() { return container != null ? container.getTerminalSortOrder() : assembler.getBlockPos().asLong(); }
        int size() { return sparseSlots == null ? inventory.size() : sparseSlots.size(); }
        int slot() { return sparseSlots == null ? cursor : sparseSlots.get(cursor); }
    }

    public RipperPatternImporter(CraftingRipperBlockEntity host, Player player, IActionSource action) {
        this.host = host;
        this.grid = host.getMainNode().getGrid();
        this.player = player;
        this.action = action;
        if (!available()) { finished = true; return; }
        // A bounded snapshot prevents a storage bus or replenishing source from feeding an endless import.
        for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey item)
                stored.add(new StoredPattern(item, entry.getLongValue()));
        }
        var seen = Collections.newSetFromMap(new IdentityHashMap<InternalInventory, Boolean>());
        seen.add(host.getLogic().getPatternInv());
        for (var machineClass : grid.getMachineClasses()) {
            if (PatternContainer.class.isAssignableFrom(machineClass)) {
                for (var container : grid.getMachines(machineClass.asSubclass(PatternContainer.class))) {
                    if (container == host || container.getGrid() != grid) continue;
                    var inventory = container.getTerminalPatternInventory();
                    if (seen.add(inventory)) devices.add(new Device(container, inventory));
                }
            } else if (appeng.blockentity.crafting.MolecularAssemblerBlockEntity.class.isAssignableFrom(machineClass)) {
                for (var assembler : grid.getMachines(machineClass.asSubclass(appeng.blockentity.crafting.MolecularAssemblerBlockEntity.class))) {
                    if (seen.add(assembler.getInternalInventory())) devices.add(new Device(assembler));
                }
            }
        }
        devices.sort(java.util.Comparator.comparingLong(Device::sortOrder));
    }

    public boolean available() {
        return !host.isRemoved() && host.getMainNode().isActive() && grid != null
                && host.getMainNode().getGrid() == grid
                && Platform.hasPermissions(new DimensionalBlockPos(host.getLevel(), host.getBlockPos()), player);
    }
    public boolean finished() { return finished; }
    public long moved() { return moved; }

    public void tick() {
        if (finished || lastTick == host.getLevel().getGameTime()) return;
        lastTick = host.getLevel().getGameTime();
        if (!available()) { finished = true; return; }
        host.getLogic().getPatternInv().batch(() -> {
            int budget = WORK_PER_TICK;
            while (!finished && budget > 0 && storedIndex < stored.size()) budget -= pullStored(stored.get(storedIndex), budget);
            while (!finished && budget > 0 && deviceIndex < devices.size()) {
                var device = devices.get(deviceIndex);
                int[] left = { budget };
                Runnable transfer = () -> {
                    while (!finished && left[0] > 0 && device.cursor < device.size()) left[0] -= pullDevice(device, left[0]);
                };
                if (device.inventory instanceof RipperPatternInventory ripper) ripper.batch(transfer);
                else transfer.run();
                budget = left[0];
                if (device.cursor >= device.size()) deviceIndex++;
            }
        });
        finished |= storedIndex == stored.size() && deviceIndex == devices.size();
    }

    private int pullStored(StoredPattern source, int budget) {
        var sample = source.key.toStack();
        if (source.remaining <= 0 || !supports(sample)) { storedIndex++; return 1; }
        int requested = (int) Math.min(source.remaining, budget);
        if (!checkSpace(requested)) return 1;
        long extracted = grid.getStorageService().getInventory().extract(source.key, requested, Actionable.MODULATE, action);
        if (extracted <= 0) { storedIndex++; return 1; }
        if (extracted > requested) throw new IllegalStateException("Storage returned more patterns than requested");
        install(sample.copyWithCount((int) extracted));
        source.remaining -= extracted;
        if (source.remaining == 0) storedIndex++;
        return (int) extracted;
    }

    private int pullDevice(Device source, int budget) {
        if (!deviceAvailable(source)) { source.cursor = source.size(); return 1; }
        int slot = source.slot();
        if (slot >= source.inventory.size()) { source.cursor++; return 1; }
        var sample = source.inventory.getStackInSlot(slot).copy();
        if (sample.isEmpty() || !supports(sample)) { source.cursor++; return 1; }
        int requested = Math.min(sample.getCount(), budget);
        if (!checkSpace(requested)) return 1;
        var extracted = source.inventory.extractItem(slot, requested, false);
        if (extracted.isEmpty()) { source.cursor++; return 1; }
        if (!ItemStack.isSameItemSameComponents(sample, extracted) || extracted.getCount() > requested) {
            // A custom container violated its extraction contract. Keep every returned item physical.
            var remainder = source.inventory.insertItem(slot, extracted, false);
            if (!remainder.isEmpty()) player.getInventory().placeItemBackInInventory(remainder);
            source.cursor++;
            return 1;
        }
        install(extracted);
        if (source.inventory.getStackInSlot(slot).isEmpty()) source.cursor++;
        return extracted.getCount();
    }

    private boolean deviceAvailable(Device source) {
        BlockEntity block;
        if (source.assembler != null) {
            if (source.assembler.getMainNode().getGrid() != grid || source.assembler.getInternalInventory() != source.assemblerInventory) return false;
            block = source.assembler;
        } else {
            if (source.container.getGrid() != grid || source.container.getTerminalPatternInventory() != source.inventory) return false;
            block = source.container instanceof PatternProviderLogicHost provider ? provider.getBlockEntity()
                    : source.container instanceof BlockEntity entity ? entity
                    : source.container instanceof appeng.parts.AEBasePart part ? part.getBlockEntity() : null;
        }
        return block != null && !block.isRemoved() && block.getLevel() != null
                && block.getLevel().hasChunkAt(block.getBlockPos())
                && Platform.hasPermissions(new DimensionalBlockPos(block.getLevel(), block.getBlockPos()), player);
    }

    private boolean supports(ItemStack stack) {
        try { return host.getLogic().acceptsPattern(stack); }
        catch (RuntimeException malformedPattern) { return false; }
    }
    private boolean checkSpace(int amount) {
        // Check actual holes and coordinate arithmetic before extracting physical items.
        int probe = destinationSlot;
        try {
            for (int i = 0; i < amount; i++) {
                while (!host.getLogic().getPatternInv().getStackInSlot(probe).isEmpty()) probe = Math.incrementExact(probe);
                RipperPatternLayout.slotsAfter(probe);
                if (i + 1 < amount) probe = Math.incrementExact(probe);
            }
            return true;
        } catch (ArithmeticException full) { finished = true; return false; }
    }
    private void install(ItemStack stack) {
        var inventory = host.getLogic().getPatternInv();
        for (int i = 0; i < stack.getCount(); i++) {
            while (!inventory.getStackInSlot(destinationSlot).isEmpty()) destinationSlot = Math.incrementExact(destinationSlot);
            inventory.setItemDirect(destinationSlot, stack.copyWithCount(1));
            destinationSlot = Math.incrementExact(destinationSlot);
            moved++;
        }
    }
}
