package com.example.ae2lightoptimizer.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void growsAnIntermediateSeedBeforeTheMillionSmithingRecipesThatConsumeIt() {
        long order = 1_000_000L;
        long raw = 3_000_000_000L;
        List<GlobalPattern> patterns = List.of(
                pattern("z_grow", Map.of("template", 1L, "diamond", 7L, "netherrack", 1L),
                        Map.of("template", 2L)),
                pattern("a_smith", Map.of("template", 1L, "diamond_pickaxe", 1L, "netherite_ingot", 1L),
                        Map.of("netherite_pickaxe", 1L)));
        Map<String, Long> stock = Map.of("template", 1L, "diamond", raw, "netherrack", raw,
                "diamond_pickaxe", order, "netherite_ingot", order);

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "netherite_pickaxe", order, stock, patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("a_smith", order, "z_grow", order - 1), plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty(), () -> "false missing seed=" + plan.missingStock());
        assertEquals(1L, plan.requiredStock().get("template"));
        assertEquals(List.of(new PatternBatch("z_grow", order - 1), new PatternBatch("a_smith", order)),
                plan.schedule());
        assertEquals(Map.of("netherite_pickaxe", order), replaySchedule(plan, patterns, plan.creditedStock()));
        assertEquals(Map.of("netherite_pickaxe", order, "diamond", raw - 7 * (order - 1),
                        "netherrack", raw - (order - 1)), replaySchedule(plan, patterns, stock));
    }

    @Test
    void completesBothPetaScaleGrowthComponentsBeforeTheirSharedConsumer() {
        List<GlobalPattern> patterns = List.of(
                pattern("a_assemble", Map.of("seed_a", 1L, "seed_b", 1L), Map.of("assembly", 1L)),
                pattern("z_grow_a", Map.of("seed_a", 1L, "raw_a", 1L),
                        Map.of("seed_a", 2L, "zz_byproduct", 1L)),
                pattern("z_grow_b", Map.of("seed_b", 1L, "raw_b", 1L), Map.of("seed_b", 2L)));
        Map<String, Long> stock = Map.of("seed_a", 1L, "seed_b", 1L,
                "raw_a", PETA - 1, "raw_b", PETA - 1);

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(
                "assembly", PETA, stock, patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.missingStock().isEmpty(), () -> "false missing seed=" + plan.missingStock());
        assertEquals(1L, plan.requiredStock().get("seed_a"));
        assertEquals(1L, plan.requiredStock().get("seed_b"));
        assertEquals(3, plan.scheduleBatches());
        assertEquals(new PatternBatch("a_assemble", PETA), plan.schedule().getLast());
        assertEquals(Map.of("assembly", PETA, "zz_byproduct", PETA - 1),
                replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void anUnusedReverseRecipeDoesNotHideTheSelectedGrowthDependency() {
        long order = 1_000L;
        List<GlobalPattern> patterns = List.of(
                pattern("a_consume", Map.of("seed", 1L), Map.of("result", 1L)),
                pattern("z_grow", Map.of("seed", 1L, "raw", 1L), Map.of("seed", 2L)),
                pattern("unused_recycle", Map.of("result", 1L, "unavailable", 1L), Map.of("seed", 1L)));

        GlobalCraftingPlan plan = planner.plan(request("result", order,
                Map.of("seed", 1L, "raw", order - 1), patterns, true));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("a_consume", order, "z_grow", order - 1), plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty(), () -> "false missing seed=" + plan.missingStock());
        assertEquals(1L, plan.requiredStock().get("seed"));
        assertEquals(Map.of("result", order), replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void craftsThreeThousandLargestCoresWithTheCatalogsLosslessConversionAlternatives() {
        String[] tiers = {"1k", "4k", "16k", "64k", "256k", "1m", "4m", "16m", "64m", "256m"};
        List<GlobalPattern> patterns = new ArrayList<>();
        patterns.add(pattern("core_1k", Map.of("iron", 4L, "fragment", 4L, "certus", 1L),
                Map.of(tiers[0], 1L)));
        for (int tier = 1; tier < tiers.length; tier++) {
            patterns.add(pattern("core_" + tiers[tier], tier < 5
                    ? Map.of("iron", 4L, tiers[tier - 1], 3L, "certus", 1L, "powder", 1L)
                    : Map.of("netherite", 4L, tiers[tier - 1], 3L, "singularity", 1L, "crystal", 1L),
                    Map.of(tiers[tier], 1L)));
        }
        patterns.add(pattern("grow", Map.of("certus", 4L, "fragment", 4L, "fluix", 1L),
                Map.of("crystal", 4L)));
        patterns.add(pattern("fragment", Map.of("crystal", 1L), Map.of("fragment", 4L)));
        patterns.add(pattern("netherite_ingot", Map.of("scrap", 4L, "gold", 4L),
                Map.of("netherite", 1L)));
        patterns.add(pattern("recombine", Map.of("fragment", 4L), Map.of("crystal", 1L)));
        patterns.add(pattern("crystal_block", Map.of("crystal", 9L), Map.of("crystal_block", 1L)));
        patterns.add(pattern("crystal_unblock", Map.of("crystal_block", 1L), Map.of("crystal", 9L)));
        Map<String, Long> stock = Map.of("crystal", 1L, "iron", 3_000_000_000L,
                "certus", 3_000_000_000L, "powder", 3_000_000_000L, "scrap", 3_000_000_000L,
                "gold", 3_000_000_000L, "singularity", 3_000_000_000L, "fluix", 3_000_000_000L);

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("256m", 3_000L,
                stock, patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        long count = 59_049_000L;
        for (String tier : tiers) {
            assertEquals(count, plan.patternCounts().get("core_" + tier));
            count /= 3;
        }
        assertEquals(19_804_000L, plan.patternCounts().get("grow"));
        assertEquals(78_853_000L, plan.patternCounts().get("fragment"));
        assertEquals(1_452_000L, plan.patternCounts().get("netherite_ingot"));
        assertEquals(13, plan.patternCounts().size());
        assertEquals(Map.of("iron", 352_836_000L, "certus", 167_425_000L, "powder", 29_160_000L,
                "scrap", 5_808_000L, "gold", 5_808_000L, "singularity", 363_000L,
                "fluix", 19_804_000L, "crystal", 1L), plan.requiredStock());
        assertTrue(plan.missingStock().isEmpty());
        assertEquals(Map.of("256m", 3_000L, "crystal", 1L),
                replaySchedule(plan, patterns, plan.creditedStock()));
        assertTrue(plan.balanceIterations() < 512);
        assertTrue(plan.scheduleBatches() < 128);
        assertTrue(plan.cyclic());
        assertEquals(CraftingTakeoverPolicy.TakeoverOwner.RECIPE_RING_TERMINAL,
                new CraftingTakeoverPolicy(true, true).ownerFor(CraftingTakeoverPolicy.GraphKind.CYCLIC));
    }

    @Test
    void combinesPartialCompressedStockWithAnotherProducerWithoutRepackingIt() {
        List<GlobalPattern> patterns = List.of(
                pattern("pack", Map.of("ingot", 9L), Map.of("block", 1L)),
                pattern("unpack", Map.of("block", 1L), Map.of("ingot", 9L)),
                pattern("refine", Map.of("ore", 1L), Map.of("ingot", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("ingot", 1_000L,
                Map.of("block", 50L, "ore", 550L), patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("unpack", 50L, "refine", 550L), plan.patternCounts());
        assertEquals(Map.of("block", 50L, "ore", 550L), plan.creditedStock());
        assertTrue(plan.missingStock().isEmpty());
        assertFalse(plan.cyclic());
        assertTrue(plan.reservedStock().isEmpty());
        assertEquals(Map.of("ingot", 1_000L), replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void canManufactureACompressedCarrierFromAvailableRawMaterial() {
        List<GlobalPattern> patterns = List.of(
                pattern("decompress", Map.of("a", 1L), Map.of("b", 9L)),
                pattern("compress", Map.of("b", 9L), Map.of("a", 1L)),
                pattern("make_a", Map.of("c", 1L), Map.of("a", 1L)),
                pattern("alternate", Map.of("d", 1L), Map.of("b", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("b", 9L,
                Map.of("c", 1L), patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("decompress", 1L, "make_a", 1L), plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty());
        assertFalse(plan.cyclic());
        assertEquals(Map.of("b", 9L), replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void aConversionWithALosslessInverseCanStillParticipateInAGrowingCycle() {
        List<GlobalPattern> patterns = List.of(
                pattern("forward", Map.of("a", 2L), Map.of("b", 3L)),
                pattern("growth_return", Map.of("b", 2L), Map.of("a", 2L)),
                pattern("lossless_return", Map.of("b", 3L), Map.of("a", 2L)),
                pattern("alternate", Map.of("missing_raw", 1L), Map.of("b", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("b", 10L,
                Map.of("b", 2L), patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("forward", 10L, "growth_return", 10L), plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty());
        assertTrue(plan.cyclic());
        assertEquals(Map.of("b", 12L), replaySchedule(plan, patterns, plan.creditedStock()));
        assertEquals(GlobalPlanStatus.CYCLE_TERMINAL_REQUIRED,
                planner.plan(request("b", 10L, Map.of("b", 2L), patterns, false)).status());
    }

    @Test
    void followsMultipleLosslessConversionsToTheirIndependentRawInput() {
        List<GlobalPattern> patterns = List.of(
                pattern("a_to_b", Map.of("a", 1L), Map.of("b", 9L)),
                pattern("b_to_a", Map.of("b", 9L), Map.of("a", 1L)),
                pattern("c_to_a", Map.of("c", 1L), Map.of("a", 4L)),
                pattern("a_to_c", Map.of("a", 4L), Map.of("c", 1L)),
                pattern("d_to_c", Map.of("d", 1L), Map.of("c", 2L)),
                pattern("c_to_d", Map.of("c", 2L), Map.of("d", 1L)),
                pattern("make_d", Map.of("raw", 1L), Map.of("d", 1L)),
                pattern("alternate", Map.of("missing_raw", 1L), Map.of("b", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("b", 72L,
                Map.of("raw", 1L), patterns, false)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("a_to_b", 8L, "c_to_a", 2L, "d_to_c", 1L, "make_d", 1L),
                plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty());
        assertFalse(plan.cyclic());
        assertEquals(Map.of("b", 72L), replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void doesNotMistakeANetGrowthSelfRecipeForAConservativeConversion() {
        List<GlobalPattern> patterns = List.of(
                pattern("grow", Map.of("seed", 2L), Map.of("seed", 3L)),
                pattern("shrink", Map.of("seed", 3L), Map.of("seed", 2L)),
                pattern("alternate", Map.of("missing_raw", 1L), Map.of("seed", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("seed", 10L,
                Map.of("seed", 2L), patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertEquals(Map.of("grow", 10L), plan.patternCounts());
        assertTrue(plan.missingStock().isEmpty());
        assertTrue(plan.cyclic());
        assertEquals(Map.of("seed", 12L), replaySchedule(plan, patterns, plan.creditedStock()));
    }

    @Test
    void retainsFourSelectedGrowthComponentsAtPetaScale() {
        List<GlobalPattern> patterns = new ArrayList<>();
        Map<String, Long> initial = new LinkedHashMap<>();
        Map<String, Long> consumer = new LinkedHashMap<>();
        for (int ring = 0; ring < 4; ring++) {
            String seed = "seed_" + ring;
            String raw = "raw_" + ring;
            String middle = "middle_" + ring;
            patterns.add(pattern("grow_" + ring, Map.of(seed, 1L, raw, 1L), Map.of(middle, 2L)));
            patterns.add(pattern("return_" + ring, Map.of(middle, 2L), Map.of(seed, 2L)));
            initial.put(seed, 1L);
            initial.put(raw, PETA - 1);
            consumer.put(seed, 1L);
        }
        patterns.add(pattern("assemble", consumer, Map.of("assembly", 1L)));

        GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request("assembly", PETA,
                initial, patterns, true)));

        assertEquals(GlobalPlanStatus.SOLVED, plan.status());
        assertTrue(plan.cyclic());
        assertTrue(plan.stronglyConnectedComponents() >= 4);
        assertTrue(plan.missingStock().isEmpty());
        for (int ring = 0; ring < 4; ring++) {
            assertEquals(PETA - 1, plan.patternCounts().get("grow_" + ring));
            assertEquals(PETA - 1, plan.patternCounts().get("return_" + ring));
            assertEquals(16L, plan.reservedStock().get("raw_" + ring));
        }
        assertEquals(Map.of("assembly", PETA), replaySchedule(plan, patterns, plan.creditedStock()));
        assertTrue(plan.balanceIterations() < 512);
        assertTrue(plan.scheduleBatches() < 512);
    }

    @Test
    void leavesOrdinaryCraftingWithUnusedCompressionCyclesToTheSupercomputer() {
        List<GlobalPattern> patterns = List.of(
                pattern("pack", Map.of("ingot", 9L), Map.of("block", 1L)),
                pattern("unpack", Map.of("block", 1L), Map.of("ingot", 9L)),
                pattern("refine", Map.of("ore", 1L), Map.of("ingot", 1L)),
                pattern("machine", Map.of("ingot", 9L), Map.of("machine", 1L)));

        for (String target : List.of("machine", "block")) {
            GlobalCraftingPlan plan = withinBudget(() -> planner.plan(request(target, 100L,
                    Map.of("ore", 900L), patterns, false)));

            assertEquals(GlobalPlanStatus.SOLVED, plan.status());
            assertEquals(Map.of(target.equals("block") ? "pack" : "machine", 100L, "refine", 900L),
                    plan.patternCounts());
            assertFalse(plan.cyclic());
            assertTrue(plan.reservedStock().isEmpty());
            assertTrue(plan.missingStock().isEmpty());
            assertEquals(Map.of(target, 100L), replaySchedule(plan, patterns, plan.creditedStock()));
            for (boolean ringOnline : List.of(false, true)) {
                assertEquals(CraftingTakeoverPolicy.TakeoverOwner.SUPERCOMPUTING_INTERFACE,
                        new CraftingTakeoverPolicy(ringOnline, true).ownerFor(plan.cyclic()
                                ? CraftingTakeoverPolicy.GraphKind.CYCLIC : CraftingTakeoverPolicy.GraphKind.ACYCLIC));
            }
        }
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
    void requiresTheRingTerminalBeforeTakingOverASelectedGrowthCycle() {
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
        // Existing intermediate stock satisfies this order through one edge.
        // Unused growth rings must not change its actual acyclic ownership.
        assertFalse(plan.cyclic());
        assertTrue(plan.reservedStock().isEmpty());
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

    private static Map<String, Long> replaySchedule(GlobalCraftingPlan plan, List<GlobalPattern> patterns,
                                                     Map<String, Long> initialStock) {
        Map<String, GlobalPattern> byId = new LinkedHashMap<>();
        patterns.forEach(pattern -> byId.put(pattern.id(), pattern));
        Map<String, Long> stock = new LinkedHashMap<>(initialStock);
        for (PatternBatch batch : plan.schedule()) {
            GlobalPattern pattern = byId.get(batch.patternId());
            stock = InstantCraftingBatch.apply(stock, pattern.inputs(), pattern.outputs(), batch.repetitions());
        }
        return stock;
    }

    private static GlobalPattern pattern(String id, Map<String, Long> inputs,
                                         Map<String, Long> outputs) {
        return new GlobalPattern(id, inputs, outputs);
    }

    private static GlobalCraftingPlan withinBudget(ThrowingSupplier<GlobalCraftingPlan> supplier) {
        return org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2), supplier);
    }
}
