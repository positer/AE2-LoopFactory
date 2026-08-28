package com.example.ae2lightoptimizer.item;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Ae2LightOptimizer.MOD_ID);

    public static final DeferredItem<BlockItem> RECIPE_RING_SOLVER_TERMINAL = ITEMS.register(
            "recipe_ring_solver_terminal",
            () -> new BlockItem(ModBlocks.RECIPE_RING_SOLVER_TERMINAL.get(), new Item.Properties()));

    public static final DeferredItem<BlockItem> SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE = ITEMS.register(
            "supercomputing_crafting_optimizer_interface",
            () -> new BlockItem(
                    ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get(),
                    new Item.Properties()));

    private ModItems() {
    }
}
