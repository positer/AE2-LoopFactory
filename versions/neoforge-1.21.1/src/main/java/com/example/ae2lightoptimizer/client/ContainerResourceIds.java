package com.example.ae2lightoptimizer.client;

import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import com.example.ae2lightoptimizer.factory.FactoryResourceSelector;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import java.util.LinkedHashSet;
import java.util.List;

/** Read public container views on a detached stack; clicking never drains or extracts resources. */
public final class ContainerResourceIds {
    public record View(boolean container, List<String> contents) {
        public View { contents = List.copyOf(contents); }
    }
    public static View inspect(ItemStack original) {
        if (original.isEmpty()) return new View(false, List.of());
        var stack = original.copy();
        var ids = new LinkedHashSet<String>();
        boolean container = false;
        try {
            var fluids = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (fluids != null) {
                container = true;
                for (int tank = 0; tank < fluids.getTanks(); tank++) {
                    var fluid = fluids.getFluidInTank(tank);
                    if (!fluid.isEmpty()) ids.add(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
                }
            }
            var energy = stack.getCapability(Capabilities.EnergyStorage.ITEM);
            if (energy != null) {
                container = true;
                if (energy.getEnergyStored() > 0) ids.add("neoforge::fe");
            }
            var items = stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (items != null) {
                container = true;
                for (int slot = 0; slot < items.getSlots(); slot++) {
                    var item = items.getStackInSlot(slot);
                    if (!item.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                }
            }
            var cell = StorageCells.getCellInventory(stack, null);
            if (cell != null) {
                container = true;
                for (var entry : cell.getAvailableStacks()) {
                    if (entry.getLongValue() > 0) ids.add(resourceId(entry.getKey()));
                }
            }
            container |= chemicals(stack, ids);
            return new View(container, List.copyOf(ids));
        } catch (RuntimeException | LinkageError error) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot read factory editor container contents for {}",
                    BuiltInRegistries.ITEM.getKey(original.getItem()), error);
            // A broken optional capability must neither mutate the real cursor stack nor invent contents.
            return new View(true, List.of());
        }
    }
    public static String resourceId(AEKey key) {
        return FactoryResourceSelector.resourceType(key).equals("neoforge::fe") ? "neoforge::fe" : key.getId().toString();
    }
    /** Optional Mekanism item chemistry, using its pinned public capability and registry API. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean chemicals(ItemStack stack, LinkedHashSet<String> ids) {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) return false;
        try {
            var chemicalType = Class.forName("mekanism.common.capabilities.Capabilities").getField("CHEMICAL").get(null);
            var capability = (net.neoforged.neoforge.capabilities.ItemCapability)
                    chemicalType.getClass().getMethod("item").invoke(chemicalType);
            var handler = stack.getCapability(capability);
            if (handler == null) return false;
            var handlerType = Class.forName("mekanism.api.chemical.IChemicalHandler");
            var stackType = Class.forName("mekanism.api.chemical.ChemicalStack");
            var tanks = handlerType.getMethod("getChemicalTanks");
            var inTank = handlerType.getMethod("getChemicalInTank", int.class);
            var amount = stackType.getMethod("getAmount");
            var chemical = stackType.getMethod("getChemical");
            var registry = (net.minecraft.core.Registry) Class.forName("mekanism.api.MekanismAPI")
                    .getField("CHEMICAL_REGISTRY").get(null);
            for (int tank = 0; tank < (int) tanks.invoke(handler); tank++) {
                var content = inTank.invoke(handler, tank);
                if ((long) amount.invoke(content) > 0) ids.add(registry.getKey(chemical.invoke(content)).toString());
            }
            return true;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect the optional Mekanism item chemical capability", error);
        }
    }
    private ContainerResourceIds() {}
}