package com.example.ae2lightoptimizer.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class RecipeRingSolver {
    public RingSolveResult solve(RingSolveRequest request) {
        long current = request.initialStock().getOrDefault(request.target(), 0L);
        if (current >= request.targetAmount()) {
            return new RingSolveResult(RingSolveStatus.SOLVED, List.of(), request.initialStock(), 1);
        }
        if (request.recipes().isEmpty()) {
            return failed(RingSolveStatus.NO_GROWTH_PATH, request.initialStock(), 1);
        }
        if (request.recipes().size() == 1) {
            return solveSingle(request, request.recipes().getFirst());
        }
        return solveGraph(request);
    }

    private RingSolveResult solveSingle(RingSolveRequest request, RingRecipe recipe) {
        long consumed = recipe.inputs().getOrDefault(request.target(), 0L);
        long produced = recipe.outputs().getOrDefault(request.target(), 0L);
        long net = produced - consumed;
        long current = request.initialStock().getOrDefault(request.target(), 0L);
        if (net <= 0 || current < consumed) {
            return failed(RingSolveStatus.NO_GROWTH_PATH, request.initialStock(), 1);
        }

        long repetitions = ceilDiv(request.targetAmount() - current, net);
        if (repetitions > request.budget().maxApplications()) {
            return failed(RingSolveStatus.BUDGET_EXHAUSTED, request.initialStock(), 1);
        }
        for (var input : recipe.inputs().entrySet()) {
            if (input.getKey().equals(request.target())) {
                continue;
            }
            long required = saturatingMultiply(input.getValue(), repetitions);
            if (request.initialStock().getOrDefault(input.getKey(), 0L) < required) {
                return failed(RingSolveStatus.NO_GROWTH_PATH, request.initialStock(), 1);
            }
        }

        Map<String, Long> finalStock = new LinkedHashMap<>(request.initialStock());
        applyRepeated(finalStock, recipe, repetitions);
        return new RingSolveResult(RingSolveStatus.SOLVED,
                List.of(new RecipeApplication(recipe.id(), repetitions)), finalStock, 1);
    }

    private RingSolveResult solveGraph(RingSolveRequest request) {
        List<RingRecipe> relevantRecipes = relevantRecipes(request.target(), request.recipes());
        if (relevantRecipes.isEmpty()) {
            return failed(RingSolveStatus.NO_GROWTH_PATH, request.initialStock(), 1);
        }

        Set<String> resourceSet = new HashSet<>();
        for (RingRecipe recipe : relevantRecipes) {
            resourceSet.addAll(recipe.inputs().keySet());
            resourceSet.addAll(recipe.outputs().keySet());
        }
        List<String> resources = resourceSet.stream().sorted(Comparator.naturalOrder()).toList();
        Map<String, Integer> indexes = new HashMap<>();
        for (int i = 0; i < resources.size(); i++) {
            indexes.put(resources.get(i), i);
        }
        int targetIndex = indexes.get(request.target());
        long[] caps = computeCaps(request, relevantRecipes, resources, indexes, targetIndex);
        long[] initial = vector(request.initialStock(), resources, caps);
        List<CompiledRecipe> compiled = relevantRecipes.stream()
                .map(recipe -> compile(recipe, indexes))
                .toList();

        Queue<SearchNode> queue = new ArrayDeque<>();
        queue.add(new SearchNode(initial, null, -1, 0));
        List<long[]> frontier = new ArrayList<>();
        frontier.add(initial);
        int explored = 0;
        boolean depthLimited = false;

        while (!queue.isEmpty()) {
            SearchNode node = queue.remove();
            explored++;
            if (node.stock[targetIndex] >= request.targetAmount()) {
                return solvedFromNode(node, compiled, resources, explored);
            }
            if (explored >= request.budget().maxStates()) {
                return failed(RingSolveStatus.BUDGET_EXHAUSTED, request.initialStock(), explored);
            }
            if (node.depth >= request.budget().maxApplications()) {
                depthLimited = true;
                continue;
            }
            for (int recipeIndex = 0; recipeIndex < compiled.size(); recipeIndex++) {
                CompiledRecipe recipe = compiled.get(recipeIndex);
                if (!canApply(node.stock, recipe)) {
                    continue;
                }
                long[] next = apply(node.stock, recipe, caps);
                if (!acceptIntoFrontier(next, frontier)) {
                    continue;
                }
                queue.add(new SearchNode(next, node, recipeIndex, node.depth + 1));
            }
        }
        return failed(depthLimited ? RingSolveStatus.BUDGET_EXHAUSTED : RingSolveStatus.NO_GROWTH_PATH,
                request.initialStock(), explored);
    }

    private static List<RingRecipe> relevantRecipes(String target, List<RingRecipe> recipes) {
        Map<String, List<RingRecipe>> producersByResource = new HashMap<>();
        for (RingRecipe recipe : recipes) {
            for (String output : recipe.outputs().keySet()) {
                producersByResource.computeIfAbsent(output, ignored -> new ArrayList<>()).add(recipe);
            }
        }
        Set<String> needed = new HashSet<>();
        needed.add(target);
        List<RingRecipe> selected = new ArrayList<>();
        Set<RingRecipe> selectedSet = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(target);
        while (!pending.isEmpty()) {
            String resource = pending.remove();
            for (RingRecipe recipe : producersByResource.getOrDefault(resource, List.of())) {
                if (selectedSet.add(recipe)) {
                    selected.add(recipe);
                    for (String input : recipe.inputs().keySet()) {
                        if (needed.add(input)) {
                            pending.add(input);
                        }
                    }
                }
            }
        }
        return selected;
    }

    private static long[] computeCaps(RingSolveRequest request, List<RingRecipe> recipes,
                                      List<String> resources, Map<String, Integer> indexes,
                                      int targetIndex) {
        long[] caps = new long[resources.size()];
        long[] maxOutputs = new long[resources.size()];
        long[] maxInputs = new long[resources.size()];
        for (RingRecipe recipe : recipes) {
            recipe.inputs().forEach((resource, amount) -> {
                int index = indexes.get(resource);
                maxInputs[index] = Math.max(maxInputs[index], amount);
            });
            recipe.outputs().forEach((resource, amount) -> {
                int index = indexes.get(resource);
                maxOutputs[index] = Math.max(maxOutputs[index], amount);
            });
        }
        for (int i = 0; i < resources.size(); i++) {
            String resource = resources.get(i);
            long initial = request.initialStock().getOrDefault(resource, 0L);
            caps[i] = Math.max(maxInputs[i], saturatingAdd(initial,
                    saturatingMultiply(maxOutputs[i], request.budget().maxApplications())));
        }
        caps[targetIndex] = request.targetAmount();
        return caps;
    }

    private static long[] vector(Map<String, Long> stock, List<String> resources, long[] caps) {
        long[] result = new long[resources.size()];
        for (int i = 0; i < resources.size(); i++) {
            result[i] = Math.min(stock.getOrDefault(resources.get(i), 0L), caps[i]);
        }
        return result;
    }

    private static CompiledRecipe compile(RingRecipe recipe, Map<String, Integer> indexes) {
        int[] inputIndexes = new int[recipe.inputs().size()];
        long[] inputAmounts = new long[recipe.inputs().size()];
        int inputCursor = 0;
        for (var input : recipe.inputs().entrySet()) {
            inputIndexes[inputCursor] = indexes.get(input.getKey());
            inputAmounts[inputCursor] = input.getValue();
            inputCursor++;
        }

        Set<String> touchedResources = new HashSet<>(recipe.inputs().keySet());
        touchedResources.addAll(recipe.outputs().keySet());
        int[] touchedIndexes = new int[touchedResources.size()];
        long[] touchedInputs = new long[touchedResources.size()];
        long[] touchedOutputs = new long[touchedResources.size()];
        int touchedCursor = 0;
        for (String resource : touchedResources) {
            touchedIndexes[touchedCursor] = indexes.get(resource);
            touchedInputs[touchedCursor] = recipe.inputs().getOrDefault(resource, 0L);
            touchedOutputs[touchedCursor] = recipe.outputs().getOrDefault(resource, 0L);
            touchedCursor++;
        }
        return new CompiledRecipe(recipe.id(), inputIndexes, inputAmounts,
                touchedIndexes, touchedInputs, touchedOutputs);
    }

    private static boolean canApply(long[] stock, CompiledRecipe recipe) {
        for (int i = 0; i < recipe.inputIndexes.length; i++) {
            if (stock[recipe.inputIndexes[i]] < recipe.inputAmounts[i]) {
                return false;
            }
        }
        return true;
    }

    private static long[] apply(long[] stock, CompiledRecipe recipe, long[] caps) {
        long[] result = stock.clone();
        for (int i = 0; i < recipe.touchedIndexes.length; i++) {
            int index = recipe.touchedIndexes[i];
            long remaining = result[index] - recipe.touchedInputs[i];
            result[index] = Math.min(caps[index], saturatingAdd(remaining, recipe.touchedOutputs[i]));
        }
        return result;
    }

    private static boolean acceptIntoFrontier(long[] candidate, List<long[]> frontier) {
        ListIterator<long[]> iterator = frontier.listIterator();
        while (iterator.hasNext()) {
            long[] existing = iterator.next();
            boolean existingDominates = true;
            boolean candidateDominates = true;
            for (int i = 0; i < candidate.length; i++) {
                existingDominates &= existing[i] >= candidate[i];
                candidateDominates &= candidate[i] >= existing[i];
                if (!existingDominates && !candidateDominates) {
                    break;
                }
            }
            if (existingDominates) {
                return false;
            }
            if (candidateDominates) {
                iterator.remove();
            }
        }
        frontier.add(candidate);
        return true;
    }

    private static RingSolveResult solvedFromNode(SearchNode node, List<CompiledRecipe> recipes,
                                                   List<String> resources, int explored) {
        List<String> reversed = new ArrayList<>();
        for (SearchNode cursor = node; cursor.previous != null; cursor = cursor.previous) {
            reversed.add(recipes.get(cursor.recipeIndex).id);
        }
        Collections.reverse(reversed);
        List<RecipeApplication> applications = new ArrayList<>();
        for (String recipeId : reversed) {
            if (!applications.isEmpty()
                    && applications.getLast().recipeId().equals(recipeId)) {
                RecipeApplication previous = applications.removeLast();
                applications.add(new RecipeApplication(recipeId, previous.times() + 1));
            } else {
                applications.add(new RecipeApplication(recipeId, 1));
            }
        }
        Map<String, Long> stock = new LinkedHashMap<>();
        for (int i = 0; i < resources.size(); i++) {
            stock.put(resources.get(i), node.stock[i]);
        }
        return new RingSolveResult(RingSolveStatus.SOLVED, applications, stock, explored);
    }

    private static void applyRepeated(Map<String, Long> stock, RingRecipe recipe, long repetitions) {
        Set<String> resources = new HashSet<>(recipe.inputs().keySet());
        resources.addAll(recipe.outputs().keySet());
        for (String resource : resources) {
            long initial = stock.getOrDefault(resource, 0L);
            long consumed = saturatingMultiply(recipe.inputs().getOrDefault(resource, 0L), repetitions);
            long produced = saturatingMultiply(recipe.outputs().getOrDefault(resource, 0L), repetitions);
            stock.put(resource, saturatingAdd(initial - consumed, produced));
        }
    }

    private static RingSolveResult failed(RingSolveStatus status, Map<String, Long> stock, int explored) {
        return new RingSolveResult(status, List.of(), stock, explored);
    }

    private static long ceilDiv(long numerator, long denominator) {
        return 1 + (numerator - 1) / denominator;
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record CompiledRecipe(
            String id,
            int[] inputIndexes,
            long[] inputAmounts,
            int[] touchedIndexes,
            long[] touchedInputs,
            long[] touchedOutputs) {}

    private record SearchNode(long[] stock, SearchNode previous, int recipeIndex, long depth) {
        @Override
        public boolean equals(Object other) {
            return other instanceof SearchNode node && Arrays.equals(stock, node.stock);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(stock);
        }
    }
}
