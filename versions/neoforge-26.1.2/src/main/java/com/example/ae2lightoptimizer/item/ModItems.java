package com.example.ae2lightoptimizer.item;

import java.util.List;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import com.example.ae2lightoptimizer.storage.LoopStorageTier;
import com.example.ae2lightoptimizer.storage.PortableLoopStorageCellItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
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

    public static final DeferredItem<Item> LOOP_CRYSTAL = ITEMS.registerSimpleItem("loop_crystal");
    public static final DeferredItem<Item> LOOP_CRYSTAL_FRAGMENT = ITEMS.registerSimpleItem("loop_crystal_fragment");
    public static final DeferredItem<BlockItem> LOOP_CRYSTAL_BLOCK = ITEMS.registerSimpleBlockItem("loop_crystal_block", ModBlocks.LOOP_CRYSTAL_BLOCK);
    public static final DeferredItem<Item> LOOP_CRYSTAL_POWDER = ITEMS.registerSimpleItem("loop_crystal_powder");

    public static final DeferredItem<Item> LOOP_STORAGE_CELL_HOUSING =
            ITEMS.registerSimpleItem("loop_storage_cell_housing");

    public static final DeferredItem<Item> LOOP_STORAGE_CORE_1K = core("1k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_4K = core("4k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_16K = core("16k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_64K = core("64k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_256K = core("256k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_1M = core("1m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_4M = core("4m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_16M = core("16m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_64M = core("64m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_256M = core("256m");

    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_1K =
            cell(LoopStorageTier.SIZE_1K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_4K =
            cell(LoopStorageTier.SIZE_4K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_16K =
            cell(LoopStorageTier.SIZE_16K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_64K =
            cell(LoopStorageTier.SIZE_64K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_256K =
            cell(LoopStorageTier.SIZE_256K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_1M =
            cell(LoopStorageTier.SIZE_1M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_4M =
            cell(LoopStorageTier.SIZE_4M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_16M =
            cell(LoopStorageTier.SIZE_16M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_64M =
            cell(LoopStorageTier.SIZE_64M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_256M =
            cell(LoopStorageTier.SIZE_256M);
    public static final DeferredItem<LoopStorageCellItem> INFINITE_LOOP_STORAGE_CELL =
            ITEMS.registerItem(
                    "infinite_loop_storage_cell",
                    properties -> new LoopStorageCellItem(properties.stacksTo(1), LoopStorageTier.INFINITE));

    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_1K =
            portableCell(LoopStorageTier.SIZE_1K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_4K =
            portableCell(LoopStorageTier.SIZE_4K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_16K =
            portableCell(LoopStorageTier.SIZE_16K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_64K =
            portableCell(LoopStorageTier.SIZE_64K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_256K =
            portableCell(LoopStorageTier.SIZE_256K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_1M =
            portableCell(LoopStorageTier.SIZE_1M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_4M =
            portableCell(LoopStorageTier.SIZE_4M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_16M =
            portableCell(LoopStorageTier.SIZE_16M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_64M =
            portableCell(LoopStorageTier.SIZE_64M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_256M =
            portableCell(LoopStorageTier.SIZE_256M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_INFINITE_LOOP_STORAGE_CELL =
            portableCell(LoopStorageTier.INFINITE);

    public static final List<DeferredItem<PortableLoopStorageCellItem>> PORTABLE_LOOP_STORAGE_CELLS = List.of(
            PORTABLE_LOOP_STORAGE_CELL_1K, PORTABLE_LOOP_STORAGE_CELL_4K,
            PORTABLE_LOOP_STORAGE_CELL_16K, PORTABLE_LOOP_STORAGE_CELL_64K,
            PORTABLE_LOOP_STORAGE_CELL_256K, PORTABLE_LOOP_STORAGE_CELL_1M,
            PORTABLE_LOOP_STORAGE_CELL_4M, PORTABLE_LOOP_STORAGE_CELL_16M,
            PORTABLE_LOOP_STORAGE_CELL_64M, PORTABLE_LOOP_STORAGE_CELL_256M,
            PORTABLE_INFINITE_LOOP_STORAGE_CELL);

    private static DeferredItem<Item> core(String tier) {
        return ITEMS.registerSimpleItem(tier + "_loop_storage_core");
    }

    private static DeferredItem<LoopStorageCellItem> cell(LoopStorageTier tier) {
        return ITEMS.registerItem(
                tier.id() + "_loop_storage_cell",
                properties -> new LoopStorageCellItem(properties.stacksTo(1), tier));
    }

    private static DeferredItem<PortableLoopStorageCellItem> portableCell(LoopStorageTier tier) {
        return ITEMS.registerItem(
                "portable_" + tier.id() + "_loop_storage_cell",
                properties -> new PortableLoopStorageCellItem(tier, properties));
    }

    public static List<? extends ItemLike> loopStorageItems() {
        return List.of(
                LOOP_STORAGE_CELL_HOUSING,
                LOOP_STORAGE_CORE_1K, LOOP_STORAGE_CORE_4K, LOOP_STORAGE_CORE_16K,
                LOOP_STORAGE_CORE_64K, LOOP_STORAGE_CORE_256K,
                LOOP_STORAGE_CORE_1M, LOOP_STORAGE_CORE_4M, LOOP_STORAGE_CORE_16M,
                LOOP_STORAGE_CORE_64M, LOOP_STORAGE_CORE_256M,
                LOOP_STORAGE_CELL_1K, LOOP_STORAGE_CELL_4K, LOOP_STORAGE_CELL_16K,
                LOOP_STORAGE_CELL_64K, LOOP_STORAGE_CELL_256K,
                LOOP_STORAGE_CELL_1M, LOOP_STORAGE_CELL_4M, LOOP_STORAGE_CELL_16M,
                LOOP_STORAGE_CELL_64M, LOOP_STORAGE_CELL_256M,
                INFINITE_LOOP_STORAGE_CELL);
    }

    private ModItems() {
    }
}
