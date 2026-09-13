package com.example.ae2lightoptimizer.item;

import appeng.api.upgrades.Upgrades;
import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.storage.LoopStorageCellItem;
import com.example.ae2lightoptimizer.storage.LoopStorageTier;
import com.example.ae2lightoptimizer.storage.PortableLoopStorageCellItem;
import java.util.List;
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

    public static final DeferredItem<BlockItem> CRAFTING_RIPPER = ITEMS.register(
            "crafting_ripper", () -> new BlockItem(ModBlocks.CRAFTING_RIPPER.get(), new Item.Properties()));
    public static final DeferredItem<com.example.ae2lightoptimizer.factory.FactoryPatternItem> LOOP_FACTORY_PATTERN = ITEMS.register("loop_factory_pattern", () -> new com.example.ae2lightoptimizer.factory.FactoryPatternItem(new Item.Properties()));

    public static final DeferredItem<Item> LOOP_CARD = ITEMS.register(
            "loop_card", () -> Upgrades.createUpgradeCardItem(new Item.Properties()));
    public static final DeferredItem<Item> LOOP_CRYSTAL = ITEMS.register("loop_crystal", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> LOOP_CRYSTAL_FRAGMENT = ITEMS.register("loop_crystal_fragment", () -> new Item(new Item.Properties()));
    public static final DeferredItem<BlockItem> LOOP_CRYSTAL_BLOCK = ITEMS.register("loop_crystal_block", () -> new BlockItem(ModBlocks.LOOP_CRYSTAL_BLOCK.get(), new Item.Properties()));
    public static final DeferredItem<Item> LOOP_CRYSTAL_POWDER = ITEMS.register("loop_crystal_powder", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LOOP_STORAGE_CELL_HOUSING =
            ITEMS.register("loop_storage_cell_housing", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> LOOP_STORAGE_CORE_1K = registerCore("1k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_4K = registerCore("4k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_16K = registerCore("16k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_64K = registerCore("64k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_256K = registerCore("256k");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_1M = registerCore("1m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_4M = registerCore("4m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_16M = registerCore("16m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_64M = registerCore("64m");
    public static final DeferredItem<Item> LOOP_STORAGE_CORE_256M = registerCore("256m");

    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_1K =
            registerCell(LoopStorageTier.SIZE_1K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_4K =
            registerCell(LoopStorageTier.SIZE_4K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_16K =
            registerCell(LoopStorageTier.SIZE_16K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_64K =
            registerCell(LoopStorageTier.SIZE_64K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_256K =
            registerCell(LoopStorageTier.SIZE_256K);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_1M =
            registerCell(LoopStorageTier.SIZE_1M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_4M =
            registerCell(LoopStorageTier.SIZE_4M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_16M =
            registerCell(LoopStorageTier.SIZE_16M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_64M =
            registerCell(LoopStorageTier.SIZE_64M);
    public static final DeferredItem<LoopStorageCellItem> LOOP_STORAGE_CELL_256M =
            registerCell(LoopStorageTier.SIZE_256M);
    public static final DeferredItem<LoopStorageCellItem> INFINITE_LOOP_STORAGE_CELL =
            registerCell(LoopStorageTier.INFINITE);

    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_1K =
            registerPortableCell(LoopStorageTier.SIZE_1K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_4K =
            registerPortableCell(LoopStorageTier.SIZE_4K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_16K =
            registerPortableCell(LoopStorageTier.SIZE_16K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_64K =
            registerPortableCell(LoopStorageTier.SIZE_64K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_256K =
            registerPortableCell(LoopStorageTier.SIZE_256K);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_1M =
            registerPortableCell(LoopStorageTier.SIZE_1M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_4M =
            registerPortableCell(LoopStorageTier.SIZE_4M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_16M =
            registerPortableCell(LoopStorageTier.SIZE_16M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_64M =
            registerPortableCell(LoopStorageTier.SIZE_64M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_LOOP_STORAGE_CELL_256M =
            registerPortableCell(LoopStorageTier.SIZE_256M);
    public static final DeferredItem<PortableLoopStorageCellItem> PORTABLE_INFINITE_LOOP_STORAGE_CELL =
            registerPortableCell(LoopStorageTier.INFINITE);

    public static final List<DeferredItem<PortableLoopStorageCellItem>> PORTABLE_LOOP_STORAGE_CELLS = List.of(
            PORTABLE_LOOP_STORAGE_CELL_1K, PORTABLE_LOOP_STORAGE_CELL_4K,
            PORTABLE_LOOP_STORAGE_CELL_16K, PORTABLE_LOOP_STORAGE_CELL_64K,
            PORTABLE_LOOP_STORAGE_CELL_256K, PORTABLE_LOOP_STORAGE_CELL_1M,
            PORTABLE_LOOP_STORAGE_CELL_4M, PORTABLE_LOOP_STORAGE_CELL_16M,
            PORTABLE_LOOP_STORAGE_CELL_64M, PORTABLE_LOOP_STORAGE_CELL_256M,
            PORTABLE_INFINITE_LOOP_STORAGE_CELL);

    public static final List<DeferredItem<LoopStorageCellItem>> LOOP_STORAGE_CELLS = List.of(
            LOOP_STORAGE_CELL_1K,
            LOOP_STORAGE_CELL_4K,
            LOOP_STORAGE_CELL_16K,
            LOOP_STORAGE_CELL_64K,
            LOOP_STORAGE_CELL_256K,
            LOOP_STORAGE_CELL_1M,
            LOOP_STORAGE_CELL_4M,
            LOOP_STORAGE_CELL_16M,
            LOOP_STORAGE_CELL_64M,
            LOOP_STORAGE_CELL_256M,
            INFINITE_LOOP_STORAGE_CELL);

    public static final List<DeferredItem<? extends Item>> LOOP_STORAGE_CONTENT = List.of(
            LOOP_STORAGE_CELL_HOUSING,
            LOOP_STORAGE_CORE_1K,
            LOOP_STORAGE_CORE_4K,
            LOOP_STORAGE_CORE_16K,
            LOOP_STORAGE_CORE_64K,
            LOOP_STORAGE_CORE_256K,
            LOOP_STORAGE_CORE_1M,
            LOOP_STORAGE_CORE_4M,
            LOOP_STORAGE_CORE_16M,
            LOOP_STORAGE_CORE_64M,
            LOOP_STORAGE_CORE_256M,
            LOOP_STORAGE_CELL_1K,
            LOOP_STORAGE_CELL_4K,
            LOOP_STORAGE_CELL_16K,
            LOOP_STORAGE_CELL_64K,
            LOOP_STORAGE_CELL_256K,
            LOOP_STORAGE_CELL_1M,
            LOOP_STORAGE_CELL_4M,
            LOOP_STORAGE_CELL_16M,
            LOOP_STORAGE_CELL_64M,
            LOOP_STORAGE_CELL_256M,
            INFINITE_LOOP_STORAGE_CELL);

    private static DeferredItem<Item> registerCore(String tier) {
        return ITEMS.register(tier + "_loop_storage_core", () -> new Item(new Item.Properties()));
    }

    private static DeferredItem<LoopStorageCellItem> registerCell(LoopStorageTier tier) {
        return ITEMS.register(
                tier.id() + "_loop_storage_cell",
                () -> new LoopStorageCellItem(tier, new Item.Properties().stacksTo(1)));
    }

    private static DeferredItem<PortableLoopStorageCellItem> registerPortableCell(LoopStorageTier tier) {
        return ITEMS.register(
                "portable_" + tier.id() + "_loop_storage_cell",
                () -> new PortableLoopStorageCellItem(tier, new Item.Properties()));
    }

    private ModItems() {
    }
}
