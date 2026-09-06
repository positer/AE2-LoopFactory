package com.example.ae2lightoptimizer.storage;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnit;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.items.tools.powered.powersink.PoweredItemCapabilities;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Optional FE storage integration through public AEKey IDs; no addon classes are loaded. */
public final class PortableLoopEnergy {
    private PortableLoopEnergy() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var cell : ModItems.PORTABLE_LOOP_STORAGE_CELLS) {
            event.registerItem(Capabilities.Energy.ITEM,
                    (stack, access) -> new Capacitor(access, cell.get()), cell);
        }
    }

    public static void recharge(ItemStack stack, PortableLoopStorageCellItem item) {
        if (!item.getUpgrades(stack).isInstalled(ModItems.LOOP_CARD)) {
            return;
        }
        double aePerFe = PowerUnit.FE.convertTo(PowerUnit.AE, 1);
        long amount = PortableEnergyMath.chargeAmount(storedFe(stack), item.getAECurrentPower(stack),
                item.getAEMaxPower(stack), item.getChargeRate(stack), aePerFe);
        if (amount == 0) {
            return;
        }
        double charge = amount * aePerFe;
        if (item.injectAEPower(stack, charge, Actionable.SIMULATE) > 0) {
            return;
        }
        // Both writes target this stack on the server thread; only whole FE is consumed.
        extractStoredFe(stack, amount, false);
        item.injectAEPower(stack, charge, Actionable.MODULATE);
    }

    public static long storedFe(ItemStack stack) {
        long amount = 0;
        for (var entry : contents(stack)) {
            if (entry.amount() > 0 && isForgeEnergy(entry.what())) {
                amount = PortableEnergyMath.saturatedAdd(amount, entry.amount());
            }
        }
        return amount;
    }

    public static long extractStoredFe(ItemStack stack, long requested, boolean simulate) {
        if (requested <= 0) {
            return 0;
        }
        var stored = contents(stack).stream()
                .map(entry -> new PortableEnergyMath.StoredAmount<>(entry.what(), entry.amount())).toList();
        var debit = PortableEnergyMath.debit(stored, PortableLoopEnergy::isForgeEnergy, requested);
        if (!simulate && debit.extracted() > 0) {
            if (debit.contents().isEmpty()) {
                stack.remove(AEComponents.STORAGE_CELL_INV);
            } else {
                stack.set(AEComponents.STORAGE_CELL_INV, debit.contents().stream()
                        .map(entry -> new GenericStack(entry.key(), entry.amount())).toList());
            }
        }
        return debit.extracted();
    }

    private static List<GenericStack> contents(ItemStack stack) {
        return stack.getOrDefault(AEComponents.STORAGE_CELL_INV, List.of());
    }

    private static boolean isForgeEnergy(AEKey key) {
        return key.getAmountPerByte() > 0 && AEKeyTypes.getAll().contains(key.getType())
                && PortableEnergyMath.isForgeEnergy(key.getType().getId().toString(), key.getId().toString());
    }

    private static long capacityFe(ItemStack stack, PortableLoopStorageCellItem item) {
        if (item.tier().infinite()) {
            return Long.MAX_VALUE;
        }
        for (var entry : contents(stack)) {
            if (isForgeEnergy(entry.what())) {
                long bytes = item.tier().capacityBytes();
                long perByte = entry.what().getAmountPerByte();
                return bytes > Long.MAX_VALUE / perByte ? Long.MAX_VALUE : bytes * perByte;
            }
        }
        return 0;
    }

    private static final class Capacitor implements EnergyHandler {
        private final ItemAccess access;
        private final PortableLoopStorageCellItem item;
        private final PoweredItemCapabilities battery;

        private Capacitor(ItemAccess access, PortableLoopStorageCellItem item) {
            this.access = access;
            this.item = item;
            this.battery = new PoweredItemCapabilities(access, item, item);
        }

        private ItemStack stack() {
            if (access.getAmount() != 1 || !access.getResource().is(item)) {
                return ItemStack.EMPTY;
            }
            return access.getResource().toStack();
        }

        private boolean active(ItemStack stack) {
            return !stack.isEmpty() && item.getAECurrentPower(stack) > 0 && storedFe(stack) > 0;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return battery.insert(amount, transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            var stack = stack();
            if (amount == 0 || !active(stack)) {
                return 0;
            }
            // Modify a detached stack, then exchange through ItemAccess. An aborted
            // or nested transaction restores every component, including the FE list.
            int extracted = (int) extractStoredFe(stack, amount, false);
            if (extracted == 0) {
                return 0;
            }
            return access.exchange(ItemResource.of(stack), 1, transaction) == 1 ? extracted : 0;
        }

        @Override
        public long getAmountAsLong() {
            var stack = stack();
            return active(stack) ? storedFe(stack) : battery.getAmountAsLong();
        }

        @Override
        public long getCapacityAsLong() {
            var stack = stack();
            return active(stack) ? capacityFe(stack, item) : battery.getCapacityAsLong();
        }
    }
}