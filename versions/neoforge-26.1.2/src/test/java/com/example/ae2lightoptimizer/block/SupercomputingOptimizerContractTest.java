package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SupercomputingOptimizerContractTest {
    @Test
    void registersAnActiveChannelAwareUiFreeGridMachine() throws IOException {
        String registration = read("src/main/java/com/example/ae2lightoptimizer/block/ModBlocks.java");
        String block = read("src/main/java/com/example/ae2lightoptimizer/block/"
                + "SupercomputingCraftingOptimizerInterfaceBlock.java");
        String blockEntity = read("src/main/java/com/example/ae2lightoptimizer/block/"
                + "SupercomputingCraftingOptimizerInterfaceBlockEntity.java");

        assertTrue(registration.contains("\"supercomputing_crafting_optimizer_interface\""));
        assertTrue(block.contains("BooleanProperty CONNECTED"));
        assertTrue(blockEntity.contains("implements IInWorldGridNodeHost"));
        assertTrue(blockEntity.contains("GridFlags.REQUIRE_CHANNEL"));
        assertTrue(blockEntity.contains("setIdlePowerUsage(8.0)"));
        assertTrue(blockEntity.contains("node.isActive()"));
        assertFalse((block + blockEntity).contains("MenuProvider"));
        assertFalse((block + blockEntity).contains("openMenu"));
    }

    @Test
    void packagesConnectedModelsMixinAndNoRecipe() {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("ae2lightoptimizer.mixins.json"));
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/items/"
                + "supercomputing_crafting_optimizer_interface.json"));
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/models/block/"
                + "supercomputing_crafting_optimizer_interface_connected.json"));
        assertNotNull(loader.getResource("assets/ae2lightoptimizer/textures/block/"
                + "supercomputing_crafting_optimizer_interface_connected.png"));
        assertTrue(loader.getResource("data/ae2lightoptimizer/recipe/"
                + "supercomputing_crafting_optimizer_interface.json") == null);
    }

    @Test
    void hooksNetworkWideDagAndSccPlanningIntoAe2Plans() throws IOException {
        String integration = read("src/main/java/com/example/ae2lightoptimizer/integration/"
                + "Ae2GlobalCraftingOptimizer.java");
        String mixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/CraftingCalculationMixin.java");
        String entrypoint = read("src/main/java/com/example/ae2lightoptimizer/Ae2LightOptimizer.java");

        assertTrue(mixin.contains("runCraftAttempt"));
        assertTrue(mixin.contains("require = 1, expect = 1"));
        assertTrue(entrypoint.contains("verifyTakeoverTargetLoads()"));
        assertTrue(entrypoint.contains("Class.forName(\"appeng.crafting.CraftingCalculation\""));
        assertTrue(integration.contains("GlobalCraftingPlanner"));
        assertTrue(integration.contains("collectReachableGraph"));
        assertTrue(integration.contains("GlobalPlanningBudget.forReachableGraph"));
        assertFalse(integration.contains("MAX_RESOURCE_TYPES"));
        assertFalse(integration.contains("GRAPH_BUDGET_EXCEEDED"));
        assertTrue(integration.contains("getCraftingFor(outputKey)"));
        assertTrue(integration.contains("SupercomputingCraftingOptimizerInterfaceBlockEntity.class"));
        assertTrue(integration.contains("RecipeRingSolverTerminalBlockEntity.class"));
        assertTrue(integration.contains("CraftingTakeoverPolicy"));
        assertTrue(integration.contains("if (!takeoverPolicy.hasActiveService())"));
        assertTrue(integration.contains("if (!takeoverPolicy.accepts(graphKind))"));
        assertTrue(integration.contains("inventory.addCrafting"));
        assertTrue(integration.contains("CraftingExecutionSchedule.Owner.OPTIMIZER_INTERFACE"));
        assertTrue(integration.contains("ae2lightoptimizer$setSchedule"));
        assertTrue(integration.contains("missingSink.accept"));
        assertTrue(integration.contains("inventory.emitItems"));
        assertTrue(integration.contains("multiplePathsSink.accept(adapter.hasMultiplePaths())"));
        assertTrue(mixin.contains("method = \"hasMultiplePaths\""));
        assertTrue(integration.contains("return OptimizationAttempt.notHandled()"));
    }

    @Test
    void installsCompressedDispatchAndPersistentExecutionTakeover() throws IOException {
        String cpuMixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/CraftingCpuLogicMixin.java");
        String jobMixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/ExecutingCraftingJobMixin.java");
        String persistence = read("src/main/java/com/example/ae2lightoptimizer/mixin/"
                + "ExecutingCraftingJobPersistenceMixin.java");
        String mixinConfig = read("src/main/resources/ae2lightoptimizer.mixins.json");

        assertTrue(cpuMixin.contains("method = \"trySubmitJob\""));
        assertTrue(cpuMixin.contains("method = \"executeCrafting\""));
        assertTrue(cpuMixin.contains("ae2lightoptimizer$selectCurrentBatch"));
        assertTrue(cpuMixin.contains("ae2lightoptimizer$limitCurrentBatch"));
        assertTrue(cpuMixin.contains("method = \"readFromNBT\""));
        assertTrue(cpuMixin.contains("require = 1, expect = 1"));
        assertTrue(jobMixin.contains("CompressedBatchCursor"));
        assertTrue(persistence.contains("method = \"writeToNBT\""));
        assertTrue(persistence.contains("require = 1, expect = 1"));
        assertTrue(mixinConfig.contains("CraftingCpuLogicMixin"));
        assertTrue(mixinConfig.contains("ExecutingCraftingJobPersistenceMixin"));
    }

    @Test
    void keepsParallelRingAndOptimizerJobsOwnerIsolated() throws IOException {
        String integration = read("src/main/java/com/example/ae2lightoptimizer/integration/"
                + "Ae2GlobalCraftingOptimizer.java");
        String schedule = read("src/main/java/com/example/ae2lightoptimizer/integration/"
                + "CraftingExecutionSchedule.java");
        String jobMixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/"
                + "ExecutingCraftingJobMixin.java");
        String cpuMixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/"
                + "CraftingCpuLogicMixin.java");

        assertTrue(schedule.contains("RING_TERMINAL"));
        assertTrue(schedule.contains("OPTIMIZER_INTERFACE"));
        assertTrue(integration.contains("plan.cyclic()"));
        assertTrue(jobMixin.contains("private CraftingExecutionSchedule ae2lightoptimizer$schedule"));
        assertTrue(jobMixin.contains("private CompressedBatchCursor ae2lightoptimizer$cursor"));
        assertFalse(jobMixin.contains("static CraftingExecutionSchedule ae2lightoptimizer$schedule"));
        assertTrue(cpuMixin.contains("!scheduledJob.ae2lightoptimizer$isRingSchedule()"));
        assertTrue(cpuMixin.indexOf("!scheduledJob.ae2lightoptimizer$isRingSchedule()")
                < cpuMixin.indexOf("!scheduledJob.ae2lightoptimizer$matchesFinalOutput(what)"));
    }

    @Test
    void declinesBeforeGraphTraversalOrAe2StateMutationWhenNoServiceOwnsTheJob()
            throws IOException {
        String integration = read("src/main/java/com/example/ae2lightoptimizer/integration/"
                + "Ae2GlobalCraftingOptimizer.java");
        String mixin = read("src/main/java/com/example/ae2lightoptimizer/mixin/CraftingCalculationMixin.java");

        int noServiceGate = integration.indexOf("if (!takeoverPolicy.hasActiveService())");
        int graphTraversal = integration.indexOf("collectReachableGraph(grid");
        int ownershipGate = integration.indexOf("if (!takeoverPolicy.accepts(graphKind))");
        int simulationMutation = integration.indexOf("simulationSink.accept(simulate)");
        int childState = integration.indexOf("new ChildCraftingSimulationState(networkInventory)");

        assertTrue(noServiceGate >= 0 && noServiceGate < graphTraversal);
        assertTrue(ownershipGate >= 0 && ownershipGate < simulationMutation);
        assertTrue(simulationMutation >= 0 && simulationMutation < childState);
        assertFalse(mixin.contains("this.simulate = simulate;"));
        assertTrue(mixin.contains("value -> this.simulate = value"));
        assertTrue(mixin.indexOf("if (attempt.handled())")
                < mixin.indexOf("callback.setReturnValue(attempt.plan())"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }
}
