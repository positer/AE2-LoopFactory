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

    public static final DeferredItem<Item> LOOP_CRYSTAL = ITEMS.register("loop_crystal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> LOOP_CRYSTAL_FRAGMENT = ITEMS.register("loop_crystal_fragment", () -> new Item(new Item.Properties()));
    public static final DeferredItem<BlockItem> LOOP_CRYSTAL_BLOCK = ITEMS.register("loop_crystal_block", () -> new BlockItem(ModBlocks.LOOP_CRYSTAL_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<Item> LOOP_CRYSTAL_POWDER = ITEMS.register("loop_crystal_powder", () -> new Item(new Item.Properties()));

    private ModItems() {
    }
}
