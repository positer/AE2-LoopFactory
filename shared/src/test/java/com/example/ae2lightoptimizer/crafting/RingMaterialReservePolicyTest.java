package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RingMaterialReservePolicyTest {
    private final RingMaterialReservePolicy policy = new RingMaterialReservePolicy();

    @Test
    void ignoresExistingMaterialWhenItCannotCoverSixteenCycles() {
        RingMaterialReservePlan plan = policy.plan(1_000, 4, 60).orElseThrow();

        assertEquals(4_000, plan.totalLoopMaterial());
        assertEquals(60, plan.reservedExistingMaterial());
        assertEquals(0, plan.creditedExistingMaterial());
        assertEquals(4_000, plan.calculatedDemand());
    }

    @Test
    void preservesSixteenCyclesAndCreditsOnlyTheSurplus() {
        RingMaterialReservePlan plan = policy.plan(1_000, 4, 1_064).orElseThrow();

        assertEquals(64, plan.reservedExistingMaterial());
        assertEquals(1_000, plan.creditedExistingMaterial());
        assertEquals(3_000, plan.calculatedDemand());
    }

    @Test
    void handlesPetaScaleDemandWithoutExpandingCycles() {
        long peta = 1_000_000_000_000_000L;
        RingMaterialReservePlan plan = policy.plan(peta, 1, 16).orElseThrow();

        assertEquals(16, plan.reservedExistingMaterial());
        assertEquals(peta, plan.calculatedDemand());
        assertTrue(policy.plan(Long.MAX_VALUE, 2, Long.MAX_VALUE).isEmpty());
    }
}
