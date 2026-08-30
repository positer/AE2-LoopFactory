package com.example.ae2lightoptimizer.block;

import com.example.ae2lightoptimizer.Ae2LightOptimizer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Ae2LightOptimizer.MOD_ID);

    public static final DeferredBlock<Block> RECIPE_RING_SOLVER_TERMINAL = BLOCKS.register(
            "recipe_ring_solver_terminal",
            () -> new RecipeRingSolverTerminalBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE = BLOCKS.register(
            "supercomputing_crafting_optimizer_interface",
            () -> new SupercomputingCraftingOptimizerInterfaceBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL)));

    public static final DeferredBlock<Block> LOOP_CRYSTAL_BLOCK = BLOCKS.register("loop_crystal_block",
            () -> new Block(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.GLASS).lightLevel(s -> 3)));
    private ModBlocks() {
    }
}
