package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstantCraftingBatchTest {
    @Test
    void replaysTheExistingPetaRingTerminalScheduleWithExactNetGrowth() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            long requested = 1_000_000_000_000_000L;
            var patterns = List.of(
                    new GlobalPattern("ignite", Map.of("seed", 1L, "raw", 1L), Map.of("alpha", 2L)),
                    new GlobalPattern("weave", Map.of("alpha", 2L), Map.of("beta", 1L)),
                    new GlobalPattern("condense", Map.of("beta", 1L), Map.of("seed", 3L)));
            var plan = new GlobalCraftingPlanner().plan(new GlobalPlanRequest("seed", requested,
                    Map.of("seed", 1L, "raw", requested / 2), patterns, Set.of(), true, 16,
                    GlobalPlanningBudget.NETWORK_DEFAULT));
            assertTrue(plan.solved());
            Map<String, Long> stock = new LinkedHashMap<>(plan.creditedStock());
            Map<String, GlobalPattern> byId = new LinkedHashMap<>();
            patterns.forEach(pattern -> byId.put(pattern.id(), pattern));
            for (var batch : plan.schedule()) {
                var pattern = byId.get(batch.patternId());
                stock = InstantCraftingBatch.apply(stock, pattern.inputs(), pattern.outputs(), batch.repetitions());
            }
            assertTrue(plan.schedule().size() < 200);
            assertEquals(Map.of("seed", requested + 1), stock);
        });
    }

    @Test
    void compressedTransitionsMatchIndependentStepByStepReplay() {
        Random random = new Random(294812);
        for (int fixture = 0; fixture < 5_000; fixture++) {
            var initial = new LinkedHashMap<String, Long>();
            var inputs = new LinkedHashMap<String, Long>();
            var outputs = new LinkedHashMap<String, Long>();
            for (String key : List.of("a", "b", "c")) {
                initial.put(key, (long) random.nextInt(100));
                inputs.put(key, 1L + random.nextInt(8));
                outputs.put(key, 1L + random.nextInt(8));
            }
            long requested = 1L + random.nextInt(100);
            var literal = new LinkedHashMap<>(initial);
            long completed = 0;
            while (completed < requested && inputs.entrySet().stream().allMatch(
                    entry -> literal.get(entry.getKey()) >= entry.getValue())) {
                inputs.forEach((key, amount) -> literal.merge(key, -amount, Long::sum));
                outputs.forEach((key, amount) -> literal.merge(key, amount, Long::sum));
                completed++;
            }
            assertEquals(completed, InstantCraftingBatch.executableRepetitions(initial, inputs, outputs, requested));
            if (completed > 0) {
                literal.values().removeIf(amount -> amount == 0);
                assertEquals(literal, InstantCraftingBatch.apply(initial, inputs, outputs, completed));
            }
        }
    }

    @Test
    void petaGrowthRetainsItsSeedWithoutExpandingApplications() {
        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            long amount = 1_000_000_000_000_000L;
            var result = InstantCraftingBatch.apply(Map.of("template", 1L, "diamond", amount * 7),
                    Map.of("template", 1L, "diamond", 7L), Map.of("template", 2L), amount);
            assertEquals(Map.of("template", amount + 1), result);
        });
    }

    @Test
    void equalInputAndRemainderStillNeedsASeed() {
        assertEquals(0, InstantCraftingBatch.executableRepetitions(Map.of("raw", 100L),
                Map.of("tool", 1L, "raw", 1L), Map.of("tool", 1L, "out", 1L), 100));
        assertEquals(Map.of("tool", 1L, "out", 100L), InstantCraftingBatch.apply(
                Map.of("tool", 1L, "raw", 100L), Map.of("tool", 1L, "raw", 1L),
                Map.of("tool", 1L, "out", 1L), 100));
    }

    @Test
    void finalPositiveBalanceDoesNotExcuseAnUnavailableIntermediateInput() {
        assertThrows(IllegalArgumentException.class, () -> InstantCraftingBatch.apply(
                Map.of("seed", 1L), Map.of("seed", 2L), Map.of("seed", 3L), 500));
        assertEquals(4, InstantCraftingBatch.executableRepetitions(Map.of("seed", 10L),
                Map.of("seed", 4L), Map.of("seed", 2L), 100));
    }

    @Test
    void supportsSignedLongMaximumAndRejectsOverflowWithoutMutation() {
        var original = new LinkedHashMap<>(Map.of("seed", 1L));
        assertEquals(Map.of("seed", Long.MAX_VALUE), InstantCraftingBatch.apply(original,
                Map.of("seed", 1L), Map.of("seed", 2L), Long.MAX_VALUE - 1));
        assertThrows(ArithmeticException.class, () -> InstantCraftingBatch.apply(original,
                Map.of("seed", 1L), Map.of("seed", 2L), Long.MAX_VALUE));
        assertEquals(Map.of("seed", 1L), original);
    }

    @Test
    void compressedChainKeepsBucketsAndOnlyDeliversNetFinalOutput() {
        var stock = InstantCraftingBatch.apply(Map.of("milk", 30L), Map.of("milk", 3L),
                Map.of("cake", 1L, "bucket", 3L), 10);
        stock = InstantCraftingBatch.apply(stock, Map.of("cake", 1L), Map.of("slice", 8L), 10);
        assertEquals(Map.of("bucket", 30L, "slice", 80L), stock);
    }
}
