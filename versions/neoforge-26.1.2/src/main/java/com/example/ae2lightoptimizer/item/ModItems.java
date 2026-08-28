package com.example.ae2lightoptimizer.item;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Ae2LightOptimizer.MOD_ID);

    public static final DeferredItem<BlockItem> RECIPE_RING_SOLVER_TERMINAL =
            ITEMS.registerSimpleBlockItem(
                    "recipe_ring_solver_terminal",
                    ModBlocks.RECIPE_RING_SOLVER_TERMINAL);

    public static final DeferredItem<BlockItem> SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE =
            ITEMS.registerSimpleBlockItem(
                    "supercomputing_crafting_optimizer_interface",
                    ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE);

    private ModItems() {
    }
}
