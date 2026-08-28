package com.example.ae2lightoptimizer;

import appeng.api.AECapabilities;
import com.example.ae2lightoptimizer.block.ModBlockEntities;
import com.example.ae2lightoptimizer.block.ModBlocks;
import com.example.ae2lightoptimizer.block.RecipeRingSolverTerminalBlockEntity;
import com.example.ae2lightoptimizer.block.SupercomputingCraftingOptimizerInterfaceBlockEntity;
import com.example.ae2lightoptimizer.item.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(Ae2LightOptimizer.MOD_ID)
public final class Ae2LightOptimizer {
    public static final String MOD_ID = "ae2lightoptimizer";

    public Ae2LightOptimizer(IEventBus modEventBus) {
        verifyTakeoverTargetLoads();
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(Ae2LightOptimizer::registerCapabilities);
        modEventBus.addListener(Ae2LightOptimizer::addCreativeTabContents);
    }

    private static void verifyTakeoverTargetLoads() {
        try {
            // Forces all required calculation and execution targets to transform at startup.
            var loader = Ae2LightOptimizer.class.getClassLoader();
            Class.forName("appeng.crafting.CraftingCalculation", false, loader);
            Class.forName("appeng.crafting.CraftingPlan", false, loader);
            Class.forName("appeng.crafting.execution.ExecutingCraftingJob", false, loader);
            Class.forName("appeng.crafting.execution.CraftingCpuLogic", false, loader);
            Class.forName("appeng.crafting.execution.ElapsedTimeTracker", false, loader);
        } catch (ClassNotFoundException missingAe2Internals) {
            throw new IllegalStateException("Cannot install the AE2 global crafting takeover", missingAe2Internals);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.RECIPE_RING_SOLVER_TERMINAL.get(),
                (terminal, ignoredContext) -> terminal);
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModBlockEntities.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get(),
                (optimizer, ignoredContext) -> optimizer);
    }

    private static void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RECIPE_RING_SOLVER_TERMINAL.get());
            event.accept(ModItems.SUPERCOMPUTING_CRAFTING_OPTIMIZER_INTERFACE.get());
        }
    }
}
