package com.example.ae2lightoptimizer.crafting;

import static com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.GraphKind.ACYCLIC;
import static com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.GraphKind.CYCLIC;
import static com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.TakeoverOwner.ORIGINAL_AE2;
import static com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.TakeoverOwner.RECIPE_RING_TERMINAL;
import static com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.TakeoverOwner.SUPERCOMPUTING_INTERFACE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingTakeoverPolicyTest {
    @Test
    void bothOfflineAlwaysLeaveAe2Untouched() {
        var policy = new CraftingTakeoverPolicy(false, false);

        assertFalse(policy.hasActiveService());
        assertEquals(ORIGINAL_AE2, policy.ownerFor(ACYCLIC));
        assertEquals(ORIGINAL_AE2, policy.ownerFor(CYCLIC));
        assertFalse(policy.accepts(ACYCLIC));
        assertFalse(policy.accepts(CYCLIC));
    }

    @Test
    void ringTerminalAloneOnlyOwnsCyclicGraphs() {
        var policy = new CraftingTakeoverPolicy(true, false);

        assertTrue(policy.hasActiveService());
        assertEquals(ORIGINAL_AE2, policy.ownerFor(ACYCLIC));
        assertEquals(RECIPE_RING_TERMINAL, policy.ownerFor(CYCLIC));
        assertFalse(policy.accepts(ACYCLIC));
        assertTrue(policy.accepts(CYCLIC));
    }

    @Test
    void optimizerAloneOnlyOwnsAcyclicGraphs() {
        var policy = new CraftingTakeoverPolicy(false, true);

        assertTrue(policy.hasActiveService());
        assertEquals(SUPERCOMPUTING_INTERFACE, policy.ownerFor(ACYCLIC));
        assertEquals(ORIGINAL_AE2, policy.ownerFor(CYCLIC));
        assertTrue(policy.accepts(ACYCLIC));
        assertFalse(policy.accepts(CYCLIC));
    }

    @Test
    void bothOnlineRetainSeparateGraphOwnership() {
        var policy = new CraftingTakeoverPolicy(true, true);

        assertTrue(policy.hasActiveService());
        assertEquals(SUPERCOMPUTING_INTERFACE, policy.ownerFor(ACYCLIC));
        assertEquals(RECIPE_RING_TERMINAL, policy.ownerFor(CYCLIC));
        assertTrue(policy.accepts(ACYCLIC));
        assertTrue(policy.accepts(CYCLIC));
    }
}
