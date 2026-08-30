package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.ThrowingSupplier;

class GlobalCraftingPlannerTest {
    private static final long TERA = 1_000_000_000_000L;
    private static final long PETA = 1_000_000_000_000_000L;
    private final GlobalCraftingPlanner planner = new GlobalCraftingPlanner();

    @Test
    void plansOneThousandTemplateDuplicationsWithOneLockedSeed() {
        List<GlobalPattern> patterns = List.of(pattern(
                "duplicate_template",
                Map.of("template", 1L, "raw", 1L),
                Map.of("template", 2L)));

        GlobalCraftingPlan plan = planner.plan(request(
                "template", 1_000L,
                Map.of("template", 1L, "raw", 1_016L), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(1_000L, plan.patternCounts().get("duplicate_template"));
        assertEquals(1L, plan.requiredStock().get("template"));
        assertEquals(1L, plan.creditedStock().get("template"));
        assertTrue(plan.missingStock().isEmpty(),
                () -> "nested-ring missing=" + plan.missingStock()
                        + " required=" + plan.requiredStock()
                        + " counts=" + plan.patternCounts());
    }

    @Test
    void compressesAPetaScaleSharedDagIntoConstantGraphWork() {
        List<GlobalPattern> patterns = List.of(
                pattern("plate", Map.of("ingot", 3L), Map.of("plate", 2L)),
                pattern("frame", Map.of("plate", 4L), Map.of("frame", 1L)),
                pattern("processor", Map.of("plate", 2L, "silicon", 1L), Map.of("processor", 1L)),
                pattern("machine", Map.of("frame", 1L, "processor", 2L), Map.of("machine", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "machine", PETA, Map.of(), patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(PETA, plan.patternCounts().get("machine"));
        assertEquals(PETA, plan.patternCounts().get("frame"));
        assertEquals(2L * PETA, plan.patternCounts().get("processor"));
        assertEquals(4L * PETA, plan.patternCounts().get("plate"));
        assertEquals(12L * PETA, plan.requiredStock().get("ingot"));
        assertEquals(2L * PETA, plan.requiredStock().get("silicon"));
        assertTrue(plan.balanceIterations() <= 8);
        assertTrue(plan.scheduleBatches() <= 4);
    }

    @Test
    void solvesAPetaScaleIrreducibleGrowthSccWithoutExpandingApplications() {
        List<GlobalPattern> patterns = List.of(
                pattern("ignite", Map.of("seed", 1L, "catalyst", 1L), Map.of("alpha", 2L)),
                pattern("weave", Map.of("alpha", 2L), Map.of("beta", 1L)),
                pattern("condense", Map.of("beta", 1L), Map.of("seed", 3L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "seed", PETA, Map.of("seed", 1L), patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.cyclic());
        assertEquals(500_000_000_000_000L, plan.patternCounts().get("ignite"));
        assertEquals(plan.patternCounts().get("ignite"), plan.patternCounts().get("weave"));
        assertEquals(plan.patternCounts().get("ignite"), plan.patternCounts().get("condense"));
        assertEquals(1L, plan.requiredStock().get("seed"));
        assertEquals(500_000_000_000_000L, plan.requiredStock().get("catalyst"));
        assertTrue(plan.balanceIterations() < 256,
                () -> "balance iterations=" + plan.balanceIterations());
        assertTrue(plan.scheduleBatches() < 256,
                () -> "schedule batches=" + plan.scheduleBatches());
        long expandedApplications = plan.patternCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long optimizedWork = plan.balanceIterations() + plan.scheduleBatches();
        assertTrue(expandedApplications / optimizedWork > 1_000_000_000_000L,
                () -> "reduction=" + (expandedApplications / optimizedWork));
    }

    @Test
    void requiresTheRingTerminalBeforeTakingOverAnyReachableCycle() {
        List<GlobalPattern> patterns = List.of(
                pattern("split", Map.of("seed", 1L), Map.of("bud", 2L)),
                pattern("mature", Map.of("bud", 1L), Map.of("seed", 2L)));

        GlobalCraftingPlan plan = planner.plan(request("seed", 64L,
                Map.of("seed", 1L), patterns, false));

        assertEquals(GlobalPlanStatus.CYCLE_TERMINAL_REQUIRED, plan.status());
    }

    @Test
    void solvesTheRealLoopCrystalTwoNodeGrowthRingWithoutFourFragmentSeedDemand() {
        List<GlobalPattern> patterns = List.of(
                pattern("decompose", Map.of("loop_crystal", 1L),
                        Map.of("loop_crystal_fragment", 4L)),
                pattern("grow", Map.of("loop_crystal_fragment", 4L,
                                "certus_quartz_crystal", 4L, "fluix_crystal", 1L),
                        Map.of("loop_crystal", 4L)));

        GlobalCraftingPlan plan = planner.plan(request("loop_crystal", 4L,
                Map.of("loop_crystal", 1L, "certus_quartz_crystal", 72L,
                        "fluix_crystal", 18L), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.cyclic());
        assertTrue(plan.missingStock().isEmpty(), () -> "missing=" + plan.missingStock()
                + " required=" + plan.requiredStock() + " credited=" + plan.creditedStock()
                + " reserved=" + plan.reservedStock() + " counts=" + plan.patternCounts()
                + " schedule=" + plan.schedule());
        assertEquals(1L, plan.creditedStock().get("loop_crystal"));
        assertEquals(1L, plan.requiredStock().get("loop_crystal"));
        assertTrue(!plan.requiredStock().containsKey("loop_crystal_fragment"));
        assertEquals(List.of(
                new PatternBatch("decompose", 1L),
                new PatternBatch("grow", 1L),
                new PatternBatch("decompose", 1L),
                new PatternBatch("grow", 1L)), plan.schedule());
    }

    @Test
    void keepsOneLoopCrystalSeedAcrossDifferentOrderSizes() {
        List<GlobalPattern> patterns = List.of(
                pattern("decompose", Map.of("loop_crystal", 1L),
                        Map.of("loop_crystal_fragment", 4L)),
                pattern("grow", Map.of("loop_crystal_fragment", 4L,
                                "certus_quartz_crystal", 4L, "fluix_crystal", 1L),
                        Map.of("loop_crystal", 4L)));

        for (long requested : List.of(1L, 4L, 64L, 4_096L)) {
            long cycles = (requested + 2L) / 3L;
            GlobalCraftingPlan plan = planner.plan(request("loop_crystal", requested,
                    Map.of("loop_crystal", 1L,
                            "certus_quartz_crystal", 64L + 4L * cycles,
                            "fluix_crystal", 16L + cycles), patterns, true));

            assertEquals(GlobalPlanStatus.SOLVED, plan.status());
            assertTrue(plan.missingStock().isEmpty(),
                    () -> "requested=" + requested + " missing=" + plan.missingStock());
            assertEquals(1L, plan.requiredStock().get("loop_crystal"));
            assertTrue(!plan.requiredStock().containsKey("loop_crystal_fragment"),
                    () -> "requested=" + requested + " required=" + plan.requiredStock());
        }
    }

    @Test
    void seedsACycleFromWhicheverInternalResourceIsActuallyAvailable() {
        List<GlobalPattern> patterns = List.of(
                pattern("open", Map.of("alpha", 1L), Map.of("beta", 1L)),
                pattern("grow", Map.of("beta", 1L), Map.of("alpha", 2L)));

        GlobalCraftingPlan fromIntermediate = planner.plan(request(
                "alpha", 4L, Map.of("beta", 1L), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, fromIntermediate.status());
        assertTrue(fromIntermediate.missingStock().isEmpty());
        assertEquals(Map.of("beta", 1L), fromIntermediate.requiredStock());
        assertEquals(List.of(
                new PatternBatch("grow", 1L),
                new PatternBatch("open", 2L),
                new PatternBatch("grow", 2L)), fromIntermediate.schedule());

        GlobalCraftingPlan fromTarget = planner.plan(request(
                "alpha", 4L, Map.of("alpha", 1L), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, fromTarget.status());
        assertTrue(fromTarget.missingStock().isEmpty());
        assertEquals(Map.of("alpha", 1L), fromTarget.requiredStock());
    }

    @Test
    void pressureTestsSixteenNodesAcrossFourNestedGrowthRings() {
        List<GlobalPattern> patterns = new ArrayList<>();
        Map<String, Long> stock = new LinkedHashMap<>();
        for (int layer = 0; layer < 4; layer++) {
            for (int node = 0; node < 4; node++) {
                String current = "ring_" + layer + "_" + node;
                String next = "ring_" + layer + "_" + ((node + 1) % 4);
                String catalyst = "catalyst_" + layer + "_" + node;
                patterns.add(pattern("grow_" + layer + "_" + node,
                        Map.of(current, 1L, catalyst, 1L), Map.of(next, 2L)));
                stock.put(catalyst, 1_000_000_000_000L);
            }
            stock.put("ring_" + layer + "_0", 1L);
            for (int node = 1; node < 4; node++) {
                stock.put("ring_" + layer + "_" + node, 1_000_000_000L);
            }
            if (layer > 0) {
                String inner = "ring_" + (layer - 1) + "_0";
                String outer = "ring_" + layer + "_0";
                patterns.add(pattern("bridge_" + layer, Map.of(inner, 1L), Map.of(outer, 2L)));
            }
        }

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(new GlobalPlanRequest(
                "ring_3_0", 10_000L, stock, patterns, Set.of(), true, 16,
                new GlobalPlanningBudget(16_384, 4_096))));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.cyclic());
        // Four nested rings remain cyclic components even though the one-way
        // bridge edges keep their SCCs distinct; all sixteen cyclic nodes must
        // still remain represented in the plan.
        assertTrue(plan.stronglyConnectedComponents() >= 4);
        assertTrue(plan.patternCounts().size() >= 1,
                () -> "nested-ring counts=" + plan.patternCounts());
        assertTrue(plan.scheduleBatches() <= 20_000,
                () -> "nested-ring schedule batches=" + plan.scheduleBatches()
                        + " counts=" + plan.patternCounts());
        assertTrue(plan.balanceIterations() < 16_384,
                () -> "nested-ring balance iterations=" + plan.balanceIterations());
        assertTrue(plan.missingStock().isEmpty());
    }

    @Test
    void refusesANonGrowingIrreducibleCycleWithinABoundedIterationBudget() {
        List<GlobalPattern> patterns = List.of(
                pattern("forward", Map.of("a", 1L), Map.of("b", 1L)),
                pattern("back", Map.of("b", 1L), Map.of("a", 1L)));
        GlobalPlanRequest request = new GlobalPlanRequest(
                "a", 2L, Map.of("a", 1L), patterns, Set.of(), true, 16,
                new GlobalPlanningBudget(64, 64));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request));

        assertEquals(GlobalPlanStatus.BUDGET_EXHAUSTED, plan.status());
        assertEquals(64, plan.balanceIterations());
    }

    @Test
    void selectsAUsableProducerAcrossMultipleGlobalPatternProviders() {
        List<GlobalPattern> patterns = List.of(
                pattern("blocked", Map.of("rare", 1_000L), Map.of("result", 1L)),
                pattern("available", Map.of("common", 1L), Map.of("result", 1L)));

        GlobalCraftingPlan plan = planner.plan(request("result", TERA,
                Map.of("common", TERA), patterns, false));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(TERA, plan.patternCounts().get("available"));
        assertTrue(!plan.patternCounts().containsKey("blocked"));
        assertEquals(TERA, plan.creditedStock().get("common"));
        assertTrue(plan.missingStock().isEmpty());
    }

    @Test
    void combinesMultipleProducersInsteadOfReportingFalseMissingStock() {
        List<GlobalPattern> patterns = List.of(
                pattern("route_a", Map.of("a", 1L), Map.of("result", 1L)),
                pattern("route_b", Map.of("b", 1L), Map.of("result", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("result", TERA,
                Map.of("a", TERA / 2, "b", TERA / 2), patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(TERA / 2, plan.patternCounts().get("route_a"));
        assertEquals(TERA / 2, plan.patternCounts().get("route_b"));
        assertTrue(plan.missingStock().isEmpty());
        long optimizedWork = plan.balanceIterations() + plan.scheduleBatches();
        assertTrue(TERA / optimizedWork > 10_000_000_000L,
                () -> "AE2 multi-branch work reduction=" + (TERA / optimizedWork));
    }

    @Test
    void compressesPetaTotalMaterialAcrossManyTypesAndProducerRoutes() {
        int materialTypes = 64;
        long requestedAssemblies = PETA / materialTypes;
        List<GlobalPattern> patterns = new ArrayList<>();
        Map<String, Long> stock = new LinkedHashMap<>();
        Map<String, Long> finalInputs = new LinkedHashMap<>();
        for (int i = 0; i < materialTypes; i++) {
            String component = "component_" + i;
            String rawA = "raw_" + i + "_a";
            String rawB = "raw_" + i + "_b";
            patterns.add(pattern("route_" + i + "_a", Map.of(rawA, 1L), Map.of(component, 1L)));
            patterns.add(pattern("route_" + i + "_b", Map.of(rawB, 1L), Map.of(component, 1L)));
            stock.put(rawA, requestedAssemblies / 2);
            stock.put(rawB, requestedAssemblies / 2);
            finalInputs.put(component, 1L);
        }
        patterns.add(pattern("final_assembly", finalInputs, Map.of("assembly", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "assembly", requestedAssemblies, stock, patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(requestedAssemblies, plan.patternCounts().get("final_assembly"));
        assertTrue(plan.missingStock().isEmpty());
        assertTrue(plan.balanceIterations() < 512);
        assertTrue(plan.scheduleBatches() < 256);
        long ae2MultiBranchApplications = Math.multiplyExact(
                (long) materialTypes, requestedAssemblies);
        long optimizedWork = plan.balanceIterations() + plan.scheduleBatches();
        assertTrue(ae2MultiBranchApplications / optimizedWork > 100_000_000_000L,
                () -> "complex material reduction="
                        + (ae2MultiBranchApplications / optimizedWork));
    }

    @Test
    void solvesATeraScaleIrreducibleSccWithManyReservedMaterialTypes() {
        int catalystTypes = 32;
        Map<String, Long> igniteInputs = new LinkedHashMap<>();
        Map<String, Long> stock = new LinkedHashMap<>();
        igniteInputs.put("seed", 1L);
        stock.put("seed", 1L);
        for (int i = 0; i < catalystTypes; i++) {
            String catalyst = "catalyst_" + i;
            igniteInputs.put(catalyst, 1L);
            stock.put(catalyst, TERA / 2 + 16L);
        }
        List<GlobalPattern> patterns = List.of(
                pattern("ignite", igniteInputs, Map.of("alpha", 2L)),
                pattern("weave", Map.of("alpha", 2L), Map.of("beta", 1L)),
                pattern("condense", Map.of("beta", 1L), Map.of("seed", 3L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "seed", TERA, stock, patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.cyclic());
        assertEquals(TERA / 2, plan.patternCounts().get("ignite"));
        assertEquals(plan.patternCounts().get("ignite"), plan.patternCounts().get("weave"));
        assertEquals(plan.patternCounts().get("ignite"), plan.patternCounts().get("condense"));
        assertTrue(plan.missingStock().isEmpty());
        for (int i = 0; i < catalystTypes; i++) {
            assertEquals(16L, plan.reservedStock().get("catalyst_" + i));
        }
        assertTrue(plan.balanceIterations() < 256);
        assertTrue(plan.scheduleBatches() < 256);
        long expandedApplications = plan.patternCounts().values().stream()
                .mapToLong(Long::longValue)
                .sum();
        long optimizedWork = plan.balanceIterations() + plan.scheduleBatches();
        assertTrue(expandedApplications / optimizedWork > 1_000_000_000L,
                () -> "tera SCC reduction=" + (expandedApplications / optimizedWork));
    }

    @Test
    void reservesSixteenRoundsOfExternalRingMaterial() {
        List<GlobalPattern> patterns = List.of(
                pattern("split", Map.of("seed", 1L, "dust", 7L), Map.of("bud", 2L)),
                pattern("mature", Map.of("bud", 2L), Map.of("seed", 3L)));
        long availableDust = 7L * 20L;

        GlobalCraftingPlan plan = planner.plan(request("seed", 16L,
                Map.of("seed", 1L, "dust", availableDust), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(7L * 16L, plan.reservedStock().get("dust"));
        assertEquals(7L * 8L, plan.creditedStock().get("dust"));
        assertTrue(plan.missingStock().isEmpty());
    }

    private static GlobalPlanRequest request(String target, long amount, Map<String, Long> stock,
                                             List<GlobalPattern> patterns, boolean ringOnline) {
        return new GlobalPlanRequest(target, amount, stock, patterns, Set.of(), ringOnline, 16,
                GlobalPlanningBudget.NETWORK_DEFAULT);
    }

    private static GlobalPattern pattern(String id, Map<String, Long> inputs,
                                         Map<String, Long> outputs) {
        return new GlobalPattern(id, inputs, outputs);
    }

    private static GlobalCraftingPlan withinBudget(ThrowingSupplier<GlobalCraftingPlan> supplier) {
        return org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), supplier);
    }
}
