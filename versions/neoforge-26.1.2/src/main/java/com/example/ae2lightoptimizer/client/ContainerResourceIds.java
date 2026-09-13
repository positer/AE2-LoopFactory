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
            var access = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack);
            var fluids = access.getCapability(Capabilities.Fluid.ITEM);
            if (fluids != null) {
                container = true;
                for (int tank = 0; tank < fluids.size(); tank++) {
                    var fluid = fluids.getResource(tank);
                    if (!fluid.isEmpty() && fluids.getAmountAsLong(tank) > 0)
                        ids.add(BuiltInRegistries.FLUID.getKey(fluid.getFluid()).toString());
                }
            }
            var energy = access.getCapability(Capabilities.Energy.ITEM);
            if (energy != null) {
                container = true;
                if (energy.getAmountAsLong() > 0) ids.add("neoforge::fe");
            }
            var items = access.getCapability(Capabilities.Item.ITEM);
            if (items != null) {
                container = true;
                for (int slot = 0; slot < items.size(); slot++) {
                    var item = items.getResource(slot);
                    if (!item.isEmpty() && items.getAmountAsLong(slot) > 0)
                        ids.add(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                }
            }
            var cell = StorageCells.getCellInventory(stack, null);
            if (cell != null) {
                container = true;
                for (var entry : cell.getAvailableStacks()) {
                    if (entry.getLongValue() > 0) ids.add(resourceId(entry.getKey()));
                }
            }

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

    private ContainerResourceIds() {}
}