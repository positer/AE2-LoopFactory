package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RingOutputLockTest {
    @Test
    void executesOneThousandTemplateGrowthOperationsWithoutLosingTheLockedSeed() {
        long templatesInCpu = 1;
        long operationsRemaining = 1_000;
        long requestRemaining = 1_000;
        long delivered = 0;

        while (operationsRemaining > 0) {
            long dispatched = Math.min(templatesInCpu, operationsRemaining);
            templatesInCpu -= dispatched;
            operationsRemaining -= dispatched;

            templatesInCpu += Math.multiplyExact(dispatched, 2L);
            long release = RingOutputLock.releasable(
                    templatesInCpu, 1, requestRemaining, operationsRemaining == 0);
            templatesInCpu -= release;
            requestRemaining -= release;
            delivered += release;
        }

        assertEquals(1_000, delivered);
        assertEquals(0, requestRemaining);
        assertEquals(1, templatesInCpu);
    }

    @Test
    void locksTheSeedAcrossAThreeNodeIrreducibleRing() {
        Map<String, Long> cpu = new LinkedHashMap<>();
        cpu.put("seed", 1L);
        int rounds = 500;

        for (int round = 0; round < rounds; round++) {
            apply(cpu, Map.of("seed", 1L), Map.of("alpha", 2L));
            apply(cpu, Map.of("alpha", 2L), Map.of("beta", 1L));
            apply(cpu, Map.of("beta", 1L), Map.of("seed", 3L));
            assertEquals(0, RingOutputLock.releasable(
                    cpu.get("seed"), 1, 1_000, false));
        }

        long released = RingOutputLock.releasable(cpu.get("seed"), 1, 1_000, true);
        cpu.merge("seed", -released, Math::addExact);
        assertEquals(1_000, released);
        assertEquals(1, cpu.get("seed"));
        assertEquals(0, cpu.getOrDefault("alpha", 0L));
        assertEquals(0, cpu.getOrDefault("beta", 0L));
    }

    @Test
    void releasesACompressedPetaScaleBacklogInConstantState() {
        long peta = 1_000_000_000_000_000L;
        assertEquals(0, RingOutputLock.releasable(peta + 1, 1, peta, false));
        assertEquals(peta, RingOutputLock.releasable(peta + 1, 1, peta, true));
    }

    @Test
    void standaloneTerminalJobCompletesWhenCraftingLinkRoutesNothing() {
        long storedInCpu = 1_001;
        long remainingRequest = 1_000;
        long routedToRequester = 0; // Craft-confirm submissions pass requester == null.

        long satisfied = RingOutputLock.releasable(
                storedInCpu, 1, remainingRequest, true);
        storedInCpu -= routedToRequester;
        remainingRequest -= satisfied;

        assertEquals(1_000, satisfied);
        assertEquals(0, remainingRequest);
        assertEquals(1_001, storedInCpu,
                "AE2 finishJob/storeItems must return both net output and seed to the network");
    }

    private static void apply(Map<String, Long> stock, Map<String, Long> inputs,
                              Map<String, Long> outputs) {
        inputs.forEach((key, amount) -> {
            long available = stock.getOrDefault(key, 0L);
            if (available < amount) {
                throw new AssertionError("Attempted to skip a blocked ring node: " + key);
            }
            stock.put(key, available - amount);
        });
        outputs.forEach((key, amount) -> stock.merge(key, amount, Math::addExact));
    }
}
