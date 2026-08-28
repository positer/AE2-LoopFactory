package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RingCompletionGateTest {
    @Test
    void finishesAfterTheRequestAndFinalOutputTransitAreBothDrained() {
        assertFalse(RingCompletionGate.canFinish(false, 0, 0));
        assertFalse(RingCompletionGate.canFinish(true, 1, 0));
        assertFalse(RingCompletionGate.canFinish(true, 0, 1));
        assertTrue(RingCompletionGate.canFinish(true, 0, 0));
    }

    @Test
    void doesNotDependOnUnrelatedWaitingOutputs() {
        assertTrue(RingCompletionGate.canFinish(true, 0, 0),
                "Unrelated waiting keys must not hold a completed ring job open");
    }

    @Test
    void rejectsCorruptNegativeCounters() {
        assertThrows(IllegalArgumentException.class,
                () -> RingCompletionGate.canFinish(true, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RingCompletionGate.canFinish(true, 0, -1));
    }
}
