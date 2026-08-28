package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SinglePatternBatchPlannerTest {
    private final SinglePatternBatchPlanner planner = new SinglePatternBatchPlanner();

    @Test
    void plansPetaScaleDirectCraftingWithoutQuantityExpansion() {
        long peta = 1_000_000_000_000_000L;

        SinglePatternBatchPlan plan = planner.plan(peta, 1, 0, 9, true, false).orElseThrow();

        assertEquals(BatchOptimizationMode.DIRECT, plan.mode());
        assertEquals(peta, plan.repetitions());
        assertEquals(0, plan.seedRequired());
        assertTrue(plan.optimizedOperations() <= 24);
    }

    @Test
    void requiresBothBlocksForPetaScaleCirculatingCrafting() {
        long peta = 1_000_000_000_000_000L;

        assertTrue(planner.plan(peta, 2, 1, 1, true, false).isEmpty());
        assertTrue(planner.plan(peta, 2, 1, 1, false, true).isEmpty());

        SinglePatternBatchPlan plan = planner.plan(peta, 2, 1, 1, true, true).orElseThrow();
        assertEquals(BatchOptimizationMode.CIRCULATING, plan.mode());
        assertEquals(peta, plan.repetitions());
        assertEquals(1, plan.seedRequired());
        assertTrue(plan.optimizedOperations() <= 12);
        assertTrue(plan.ae2EquivalentOperations() >= 11_000_000_000_000_000L);
    }

    @Test
    void computesTeraMaterialRequirementsInConstantPlannerTime() {
        long tera = 1_000_000_000_000L;
        long peta = 1_000_000_000_000_000L;

        SinglePatternBatchPlan plan = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> planner.plan(peta, 1_000, 0, 6, true, false).orElseThrow());

        assertEquals(tera, plan.repetitions());
        assertEquals(peta, plan.producedTarget());
        assertTrue(plan.optimizedOperations() <= 18);
    }

    @Test
    void compressesPetaScaleGrowthWithTeraScaleInputs() {
        long tera = 1_000_000_000_000L;
        long peta = 1_000_000_000_000_000L;

        SinglePatternBatchPlan plan = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> planner.plan(peta, 2, 1, 12, true, true).orElseThrow());

        assertEquals(peta, plan.repetitions());
        assertEquals(1, plan.seedRequired());
        assertTrue(Math.multiplyExact(tera, 12) < peta);
        assertTrue(plan.optimizedOperations() <= 33);
        assertTrue(plan.ae2EquivalentOperations() == Long.MAX_VALUE
                || plan.ae2EquivalentOperations() / plan.optimizedOperations() > tera);
    }
}
