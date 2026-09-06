package com.example.ae2lightoptimizer.block;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Ae2LightOptimizer.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecipeRingSolverTerminalBlockEntity>>
            RECIPE_RING_SOLVER_TERMINAL = BLOCK_ENTITIES.register(
                    "recipe_ring_solver_terminal",
                    () -> new BlockEntityType<>(
                            RecipeRingSolverTerminalBlockEntity::new,
                            ModBlocks.RECIPE_RING_SOLVER_TERMINAL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SupercomputingCraftingOptimizerInterfaceBlockEntity>>
            SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE = BLOCK_ENTITIES.register(
                    "supercomputing_crafting_optimizer_interface",
                    () -> new BlockEntityType<>(
                            SupercomputingCraftingOptimizerInterfaceBlockEntity::new,
                            ModBlocks.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingRipperBlockEntity>>
            CRAFTING_RIPPER = BLOCK_ENTITIES.register("crafting_ripper",
                    () -> new BlockEntityType<>(CraftingRipperBlockEntity::new, ModBlocks.CRAFTING_RIPPER.get()));

    private ModBlockEntities() {
    }
}
