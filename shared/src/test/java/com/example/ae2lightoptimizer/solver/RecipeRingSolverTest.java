package com.example.ae2lightoptimizer.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipeRingSolverTest {
    private final RecipeRingSolver solver = new RecipeRingSolver();

    @Test
    void solvesSmithingTemplateStyleSingleRecipeWithClosedFormPlan() {
        RingRecipe duplicate = new RingRecipe(
                "duplicate_template",
                Map.of("template", 1L, "diamond", 7L),
                Map.of("template", 2L));

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "template", 4L,
                Map.of("template", 1L, "diamond", 21L),
                List.of(duplicate),
                new RingSolveBudget(128, 10_000)));

        assertEquals(RingSolveStatus.SOLVED, result.status());
        assertEquals(List.of(new RecipeApplication("duplicate_template", 3L)), result.applications());
        assertEquals(4L, result.finalStock().get("template"));
        assertEquals(0L, result.finalStock().get("diamond"));
    }

    @Test
    void solvesNestedGrowthAcrossMultipleRecipes() {
        RingRecipe split = new RingRecipe("split", Map.of("seed", 1L), Map.of("bud", 2L));
        RingRecipe mature = new RingRecipe("mature", Map.of("bud", 1L), Map.of("seed", 2L));

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "seed", 4L, Map.of("seed", 1L), List.of(split, mature),
                new RingSolveBudget(8, 128)));

        assertEquals(RingSolveStatus.SOLVED, result.status());
        assertEquals(List.of(
                new RecipeApplication("split", 1L),
                new RecipeApplication("mature", 2L)), result.applications());
        assertEquals(4L, result.finalStock().get("seed"));
    }

    @Test
    void rejectsAClosedCycleThatCannotIncreaseTheTarget() {
        RingRecipe forward = new RingRecipe("forward", Map.of("a", 1L), Map.of("b", 1L));
        RingRecipe backward = new RingRecipe("backward", Map.of("b", 1L), Map.of("a", 1L));

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "a", 2L, Map.of("a", 1L), List.of(forward, backward),
                new RingSolveBudget(16, 128)));

        assertEquals(RingSolveStatus.NO_GROWTH_PATH, result.status());
    }

    @Test
    void reportsBudgetExhaustionSeparatelyFromAnImpossibleRing() {
        RingRecipe one = new RingRecipe("one", Map.of("a", 1L), Map.of("b", 1L));
        RingRecipe two = new RingRecipe("two", Map.of("b", 1L), Map.of("c", 1L));
        RingRecipe three = new RingRecipe("three", Map.of("c", 1L), Map.of("a", 2L));

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "a", 8L, Map.of("a", 1L), List.of(one, two, three),
                new RingSolveBudget(1, 2)));

        assertEquals(RingSolveStatus.BUDGET_EXHAUSTED, result.status());
    }

    @Test
    void rejectsRecipesWithNonPositiveAmounts() {
        assertThrows(IllegalArgumentException.class, () ->
                new RingRecipe("invalid", Map.of("seed", 0L), Map.of("seed", 2L)));
    }

    @Test
    void solvesPetaScaleSingleRecipeInOneExploredState() {
        long peta = 1_000_000_000_000_000L;
        long repetitions = peta - 1L;
        RingRecipe duplicate = new RingRecipe(
                "duplicate_template",
                Map.of("template", 1L, "bulk_material", 7L),
                Map.of("template", 2L));

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "template", peta,
                Map.of("template", 1L, "bulk_material", Math.multiplyExact(7L, repetitions)),
                List.of(duplicate),
                new RingSolveBudget(peta, 4)));

        assertEquals(RingSolveStatus.SOLVED, result.status());
        assertEquals(List.of(new RecipeApplication("duplicate_template", repetitions)),
                result.applications());
        assertEquals(peta, result.finalStock().get("template"));
        assertEquals(0L, result.finalStock().get("bulk_material"));
        assertEquals(1, result.exploredStates());
    }

    @Test
    void solvesTeraInputAndPetaOutputInsideAComplexRecipeGraph() {
        long tera = 1_000_000_000_000L;
        long peta = 1_000_000_000_000_000L;
        List<RingRecipe> recipes = new ArrayList<>();
        recipes.add(new RingRecipe("open", Map.of("seed", 1L), Map.of("gate", 1L)));
        recipes.add(new RingRecipe("compress",
                Map.of("gate", 1L, "bulk_material", tera), Map.of("core", 1L)));
        recipes.add(new RingRecipe("release", Map.of("core", 1L), Map.of("seed", peta)));
        for (int i = 0; i < 256; i++) {
            recipes.add(new RingRecipe("bulk_dead_enter_" + i,
                    Map.of("seed", 1L), Map.of("bulk_dead_" + i + "_a", tera)));
            recipes.add(new RingRecipe("bulk_dead_forward_" + i,
                    Map.of("bulk_dead_" + i + "_a", tera),
                    Map.of("bulk_dead_" + i + "_b", tera)));
            recipes.add(new RingRecipe("bulk_dead_back_" + i,
                    Map.of("bulk_dead_" + i + "_b", tera),
                    Map.of("bulk_dead_" + i + "_a", tera)));
        }

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "seed", peta, Map.of("seed", 1L, "bulk_material", tera), recipes,
                new RingSolveBudget(16, 64)));

        assertEquals(RingSolveStatus.SOLVED, result.status());
        assertEquals(List.of(
                new RecipeApplication("open", 1L),
                new RecipeApplication("compress", 1L),
                new RecipeApplication("release", 1L)), result.applications());
        assertEquals(peta, replayPlan(
                Map.of("seed", 1L, "bulk_material", tera), recipes, result.applications()).get("seed"));
        assertTrue(result.exploredStates() <= 4,
                () -> "Irrelevant SCC filtering regressed: " + result.exploredStates());
    }

    @Test
    void filtersTenThousandIrrelevantRecipesBeforeSearch() {
        List<RingRecipe> recipes = new ArrayList<>();
        recipes.add(new RingRecipe("grow", Map.of("seed", 1L), Map.of("seed", 1_024L)));
        for (int i = 0; i < 10_000; i++) {
            recipes.add(new RingRecipe("irrelevant_" + i,
                    Map.of("unrelated_input_" + i, 1L),
                    Map.of("unrelated_output_" + i, 1L)));
        }

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "seed", 1_024L, Map.of("seed", 1L), recipes,
                new RingSolveBudget(2_000, 32)));

        assertEquals(RingSolveStatus.SOLVED, result.status(),
                () -> "states=" + result.exploredStates() + " final=" + result.finalStock());
        assertEquals(1L, result.applications().getFirst().times());
        assertTrue(result.exploredStates() <= 2);
    }

    @Test
    void stressSolvesAnIrreducibleGrowthComponentAmongDeadCycles() {
        List<RingRecipe> recipes = new ArrayList<>();
        recipes.add(new RingRecipe("ignite", Map.of("seed", 1L), Map.of("alpha", 2L)));
        recipes.add(new RingRecipe("weave", Map.of("alpha", 2L), Map.of("beta", 1L)));
        recipes.add(new RingRecipe("condense", Map.of("beta", 1L), Map.of("seed", 3L)));
        for (int i = 0; i < 24; i++) {
            recipes.add(new RingRecipe("dead_enter_" + i,
                    Map.of("seed", 1L), Map.of("dead_" + i + "_a", 1L)));
            recipes.add(new RingRecipe("dead_forward_" + i,
                    Map.of("dead_" + i + "_a", 1L), Map.of("dead_" + i + "_b", 1L)));
            recipes.add(new RingRecipe("dead_back_" + i,
                    Map.of("dead_" + i + "_b", 1L), Map.of("dead_" + i + "_a", 1L)));
        }

        RingSolveResult result = solver.solve(new RingSolveRequest(
                "seed", 33L, Map.of("seed", 1L), recipes,
                new RingSolveBudget(128, 5_000)));

        assertEquals(RingSolveStatus.SOLVED, result.status());
        assertEquals(33L, result.finalStock().get("seed"));
        assertTrue(result.applications().stream()
                .allMatch(step -> !step.recipeId().startsWith("dead_")));
        Map<String, Long> replayedStock = replayPlan(Map.of("seed", 1L), recipes, result.applications());
        assertEquals(33L, replayedStock.get("seed"));
        assertEquals(48L, result.applications().stream()
                .mapToLong(RecipeApplication::times)
                .sum());
        assertTrue(result.exploredStates() < 5_000,
                () -> "Dominance pruning regressed: " + result.exploredStates());
    }

    private static Map<String, Long> replayPlan(Map<String, Long> initialStock,
                                                 List<RingRecipe> recipes,
                                                 List<RecipeApplication> applications) {
        Map<String, RingRecipe> recipesById = new HashMap<>();
        recipes.forEach(recipe -> recipesById.put(recipe.id(), recipe));
        Map<String, Long> stock = new HashMap<>(initialStock);

        for (RecipeApplication application : applications) {
            RingRecipe recipe = recipesById.get(application.recipeId());
            assertTrue(recipe != null, () -> "Unknown recipe in plan: " + application.recipeId());
            for (long repetition = 0; repetition < application.times(); repetition++) {
                recipe.inputs().forEach((resource, amount) -> assertTrue(
                        stock.getOrDefault(resource, 0L) >= amount,
                        () -> "Plan consumes unavailable " + resource + " in " + recipe.id()));
                recipe.inputs().forEach((resource, amount) ->
                        stock.merge(resource, -amount, Long::sum));
                recipe.outputs().forEach((resource, amount) ->
                        stock.merge(resource, amount, Long::sum));
            }
        }
        return stock;
    }
}
