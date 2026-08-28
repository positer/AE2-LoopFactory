package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GlobalGraphScaleEstimatorTest {
    private static final long TERA = 1_000_000_000_000L;
    private static final long PETA = 1_000_000_000_000_000L;
    private final GlobalGraphScaleEstimator estimator = new GlobalGraphScaleEstimator();

    @Test
    void reportsTeraDistinctMaterialAndIrreducibleNodeCostsWithoutRejectingTakeover() {
        GlobalGraphScaleAssessment assessment =
                org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                        Duration.ofMillis(100),
                        () -> estimator.assess(new GlobalGraphScaleEnvelope(
                                TERA, TERA, 3L * TERA, PETA)));

        assertEquals(5L * TERA, assessment.lowerBoundVisits());
        assertEquals(80L * TERA, assessment.minimumReferenceBytes());
        GlobalPlanningBudget scaledBudget = GlobalPlanningBudget.forReachableGraph(100_000, 100_000);
        assertTrue(scaledBudget.maxBalanceIterations()
                > GlobalPlanningBudget.NETWORK_DEFAULT.maxBalanceIterations());
        assertTrue(scaledBudget.maxScheduleBatches()
                > GlobalPlanningBudget.NETWORK_DEFAULT.maxScheduleBatches());
    }

    @Test
    void reportsBoundedGraphCostIndependentlyOfPetaMaterialQuantity() {
        GlobalGraphScaleAssessment assessment = estimator.assess(
                new GlobalGraphScaleEnvelope(1_024, 4_096, 65_536, PETA));

        assertTrue(assessment.lowerBoundVisits() < 100_000);
    }
}
