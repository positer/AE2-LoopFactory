package com.example.ae2lightoptimizer.block;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RecipeRingSolverTerminalContractTest {
    @Test
    void exposesAStableAe2GridHostAndThe2612ValueIoContract() throws IOException {
        String registration = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/block/ModBlocks.java"));
        String blockEntity = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/block/RecipeRingSolverTerminalBlockEntity.java"));
        String block = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/block/RecipeRingSolverTerminalBlock.java"));

        assertTrue(registration.contains("\"recipe_ring_solver_terminal\""));
        assertTrue(blockEntity.contains("implements IInWorldGridNodeHost"));
        assertTrue(blockEntity.contains("GridFlags.REQUIRE_CHANNEL"));
        assertTrue(blockEntity.contains("setIdlePowerUsage(2.0)"));
        assertTrue(blockEntity.contains("mainNode.serialize(output)"));
        assertTrue(blockEntity.contains("mainNode.deserialize(input)"));
        assertUiFree(block + blockEntity);
    }

    @Test
    void packagesAllBlockResourcesWithoutARecipe() {
        ClassLoader loader = getClass().getClassLoader();
        assertNotNull(loader.getResource("ae2lightoptimizer.png"));
        assertNotNull(loader.getResource(
                "assets/ae2lightoptimizer/blockstates/recipe_ring_solver_terminal.json"));
        assertNotNull(loader.getResource(
                "assets/ae2lightoptimizer/models/block/recipe_ring_solver_terminal.json"));
        assertNotNull(loader.getResource(
                "assets/ae2lightoptimizer/items/recipe_ring_solver_terminal.json"));
        assertNotNull(loader.getResource(
                "assets/ae2lightoptimizer/textures/block/recipe_ring_solver_terminal.png"));
        assertNotNull(loader.getResource(
                "data/ae2lightoptimizer/loot_table/blocks/recipe_ring_solver_terminal.json"));
        assertTrue(loader.getResource(
                "data/ae2lightoptimizer/recipe/recipe_ring_solver_terminal.json") == null);
    }

    @Test
    void usesTheBorderedIsometricRingTerminalAsTheModIcon() throws IOException {
        String metadata = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        assertTrue(metadata.contains("logoFile=\"ae2lightoptimizer.png\""));
        var icon = ImageIO.read(Path.of("src/main/resources/ae2lightoptimizer.png").toFile());
        assertNotNull(icon);
        assertEquals(64, icon.getWidth());
        assertEquals(64, icon.getHeight());
        for (int offset = 0; offset < 64; offset++) {
            assertTrue((icon.getRGB(offset, 0) >>> 24) != 0);
            assertTrue((icon.getRGB(offset, 63) >>> 24) != 0);
            assertTrue((icon.getRGB(0, offset) >>> 24) != 0);
            assertTrue((icon.getRGB(63, offset) >>> 24) != 0);
        }
    }

    @Test
    void independentlyOwnsCyclicGraphsWithoutTakingOverAcyclicGraphs() throws IOException {
        String integration = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/integration/Ae2GlobalCraftingOptimizer.java"));

        assertTrue(integration.contains("ringTerminalOnline"));
        assertTrue(integration.contains("plan.cyclic()"));
        assertTrue(integration.contains("new CraftingTakeoverPolicy(ringTerminalOnline, optimizerOnline)"));
        assertTrue(integration.contains("plan.cyclic() ? GraphKind.CYCLIC : GraphKind.ACYCLIC"));
        assertTrue(integration.contains("if (!takeoverPolicy.accepts(graphKind))"));
        assertTrue(integration.contains("RESERVED_CYCLE_ROUNDS = 16"));
        assertTrue(integration.contains("CraftingExecutionSchedule.Owner.RING_TERMINAL"));
        String cpuMixin = Files.readString(Path.of(
                "src/main/java/com/example/ae2lightoptimizer/mixin/CraftingCpuLogicMixin.java"));
        assertTrue(cpuMixin.contains("ae2lightoptimizer$currentPattern"));
        assertTrue(cpuMixin.contains("singleRemovableEntry"));
        assertTrue(cpuMixin.contains("ae2lightoptimizer$lockRingFinalOutput"));
        assertTrue(cpuMixin.contains("inventory.insert(what, accepted, Actionable.MODULATE)"));
        assertTrue(cpuMixin.contains("RingOutputLock.releasable"));
        assertTrue(cpuMixin.contains("RingCompletionGate.canFinish"));
        assertTrue(cpuMixin.contains("pendingFinalOutput"));
        assertTrue(cpuMixin.contains("ae2lightoptimizer$decrementRemainingRequest(releasable)"));
        assertFalse(cpuMixin.contains("ae2lightoptimizer$decrementRemainingRequest(routed)"));
        assertTrue(integration.contains("plan.requiredStock().getOrDefault(targetId, 0L)"));
    }

    private static void assertUiFree(String source) {
        assertFalse(source.contains("MenuProvider"));
        assertFalse(source.contains("openMenu"));
        assertFalse(source.contains("createMenu"));
        assertFalse(source.contains("AbstractContainerScreen"));
    }
}
