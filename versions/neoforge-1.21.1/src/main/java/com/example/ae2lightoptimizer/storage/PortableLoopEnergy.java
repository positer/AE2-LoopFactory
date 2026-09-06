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
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Optional FE storage integration through public AEKey IDs; no addon classes are loaded. */
public final class PortableLoopEnergy {
    private PortableLoopEnergy() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var cell : ModItems.PORTABLE_LOOP_STORAGE_CELLS) {
            event.registerItem(Capabilities.EnergyStorage.ITEM,
                    (stack, ignored) -> new Capacitor(stack, cell.get()), cell);
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

    private static final class Capacitor implements IEnergyStorage {
        private final ItemStack stack;
        private final PortableLoopStorageCellItem item;
        private final PoweredItemCapabilities battery;

        private Capacitor(ItemStack stack, PortableLoopStorageCellItem item) {
            this.stack = stack;
            this.item = item;
            this.battery = new PoweredItemCapabilities(stack, item);
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return amount > 0 ? battery.receiveEnergy(amount, simulate) : 0;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return amount > 0 && canExtract()
                    ? (int) extractStoredFe(stack, amount, simulate) : 0;
        }

        @Override
        public int getEnergyStored() {
            return canExtract() ? PortableEnergyMath.saturatedInt(storedFe(stack)) : battery.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return canExtract() ? PortableEnergyMath.saturatedInt(capacityFe(stack, item))
                    : battery.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return item.getAECurrentPower(stack) > 0 && storedFe(stack) > 0;
        }

        @Override
        public boolean canReceive() {
            return battery.canReceive();
        }
    }
}
