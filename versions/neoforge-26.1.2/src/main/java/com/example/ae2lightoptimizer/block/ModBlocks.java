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

    public static final DeferredBlock<Block> RECIPE_RING_SOLVER_TERMINAL = BLOCKS.registerBlock(
            "recipe_ring_solver_terminal",
            RecipeRingSolverTerminalBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL));

    public static final DeferredBlock<Block> SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE = BLOCKS.registerBlock(
            "supercomputing_crafting_optimizer_interface",
            SupercomputingCraftingOptimizerInterfaceBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .strength(3.5F)
                    .sound(SoundType.METAL));

    public static final DeferredBlock<Block> LOOP_CRYSTAL_BLOCK = BLOCKS.registerBlock("loop_crystal_block", Block::new,
            () -> BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.AMETHYST));
    private ModBlocks() {
    }
}
