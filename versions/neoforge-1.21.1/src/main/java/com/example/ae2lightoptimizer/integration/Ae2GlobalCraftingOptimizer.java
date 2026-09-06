package com.example.ae2lightoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.inv.NetworkCraftingSimulationState;
import com.example.ae2lightoptimizer.block.RecipeRingSolverTerminalBlockEntity;
import com.example.ae2lightoptimizer.block.SupercomputingCraftingOptimizerInterfaceBlockEntity;
import com.example.ae2lightoptimizer.crafting.GlobalCraftingPlanner;
import com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy;
import com.example.ae2lightoptimizer.crafting.CraftingTakeoverPolicy.GraphKind;
import com.example.ae2lightoptimizer.crafting.GlobalPattern;
import com.example.ae2lightoptimizer.crafting.GlobalPlanRequest;
import com.example.ae2lightoptimizer.crafting.GlobalPlanningBudget;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;
import org.jetbrains.annotations.Nullable;

/** Bridges the compressed shared planner into AE2's network-wide crafting calculation. */
public final class Ae2GlobalCraftingOptimizer {
    private static final GlobalCraftingPlanner PLANNER = new GlobalCraftingPlanner();
    private static final long RESERVED_CYCLE_ROUNDS = 16;

    private Ae2GlobalCraftingOptimizer() {
    }

    public static OptimizationAttempt tryPlan(
            IGrid grid, NetworkCraftingSimulationState networkInventory,
            CraftingCalculation calculation, AEKey requestedKey, long requestedAmount,
            boolean simulate, ObjLongConsumer<AEKey> missingSink,
            Consumer<Boolean> multiplePathsSink, Consumer<Boolean> simulationSink) {
        if (grid == null || requestedAmount <= 0) {
            return OptimizationAttempt.notHandled();
        }

        try {
            boolean optimizerOnline = !grid.getActiveMachines(
                    SupercomputingCraftingOptimizerInterfaceBlockEntity.class).isEmpty();
            boolean ringTerminalOnline = !grid.getActiveMachines(
                    RecipeRingSolverTerminalBlockEntity.class).isEmpty();
            var takeoverPolicy = new CraftingTakeoverPolicy(ringTerminalOnline, optimizerOnline);
            if (!takeoverPolicy.hasActiveService()) {
                return OptimizationAttempt.notHandled();
            }
            GraphAdapter adapter = collectReachableGraph(grid, networkInventory, requestedKey);
            if (adapter.patterns().isEmpty()) {
                return OptimizationAttempt.notHandled();
            }

            var plan = PLANNER.plan(new GlobalPlanRequest(
                    adapter.idFor(requestedKey),
                    requestedAmount,
                    adapter.availableStock(),
                    adapter.patterns(),
                    adapter.emittableResources(),
                    ringTerminalOnline,
                    RESERVED_CYCLE_ROUNDS,
                    GlobalPlanningBudget.forReachableGraph(
                            adapter.resourceIds().size(), adapter.patterns().size())));
            if (!plan.solved()) {
                return OptimizationAttempt.notHandled();
            }
            GraphKind graphKind = plan.cyclic() ? GraphKind.CYCLIC : GraphKind.ACYCLIC;
            if (!takeoverPolicy.accepts(graphKind)) {
                return OptimizationAttempt.notHandled();
            }

            simulationSink.accept(simulate);
            ChildCraftingSimulationState inventory = new ChildCraftingSimulationState(networkInventory);
            String targetId = adapter.idFor(requestedKey);
            if (!plan.creditedStock().containsKey(targetId)) {
                inventory.ignore(requestedKey);
            }
            inventory.addStackBytes(requestedKey, 1, requestedAmount);

            for (var entry : plan.requiredStock().entrySet()) {
                AEKey key = adapter.keyFor(entry.getKey());
                inventory.addStackBytes(key, entry.getValue(), 1);
            }
            for (var entry : plan.creditedStock().entrySet()) {
                AEKey key = adapter.keyFor(entry.getKey());
                long extracted = inventory.extract(key, entry.getValue(), Actionable.MODULATE);
                if (extracted != entry.getValue()) {
                    return OptimizationAttempt.notHandled();
                }
            }
            if (!plan.missingStock().isEmpty()) {
                if (!simulate) {
                    return OptimizationAttempt.handled(null);
                }
                plan.missingStock().forEach((id, amount) -> missingSink.accept(adapter.keyFor(id), amount));
            }
            plan.emittedStock().forEach((id, amount) -> {
                AEKey key = adapter.keyFor(id);
                inventory.addStackBytes(key, amount, 1);
                inventory.emitItems(key, amount);
            });
            plan.patternCounts().forEach((id, repetitions) -> {
                inventory.addCrafting(adapter.patternFor(id), repetitions);
                inventory.addBytes(repetitions);
            });
            inventory.addBytes((long) plan.stronglyConnectedComponents() * 8L);

            multiplePathsSink.accept(adapter.hasMultiplePaths());
            CraftingPlan ae2Plan = CraftingSimulationState.buildCraftingPlan(
                    inventory, calculation, requestedAmount);
            if (!plan.schedule().isEmpty()) {
                var executionBatches = plan.schedule().stream()
                        .map(batch -> new CraftingExecutionSchedule.Batch(
                                adapter.patternFor(batch.patternId()), batch.repetitions()))
                        .toList();
                var owner = plan.cyclic()
                        ? CraftingExecutionSchedule.Owner.RING_TERMINAL
                        : CraftingExecutionSchedule.Owner.OPTIMIZER_INTERFACE;
                long finalOutputReserve = plan.cyclic()
                        ? plan.requiredStock().getOrDefault(targetId, 0L)
                        : 0L;
                ((ScheduledCraftingPlan) (Object) ae2Plan).ae2lightoptimizer$setSchedule(
                        new CraftingExecutionSchedule(owner, executionBatches, finalOutputReserve));
            }
            return OptimizationAttempt.handled(ae2Plan);
        } catch (ArithmeticException | IllegalArgumentException unsupported) {
            return OptimizationAttempt.notHandled();
        }
    }

    private static GraphAdapter collectReachableGraph(
            IGrid grid, NetworkCraftingSimulationState networkInventory, AEKey target) {
        Map<AEKey, String> resourceIds = new LinkedHashMap<>();
        Map<String, AEKey> resources = new LinkedHashMap<>();
        IdentityHashMap<IPatternDetails, String> patternIds = new IdentityHashMap<>();
        Map<String, IPatternDetails> patternDetails = new LinkedHashMap<>();
        List<GlobalPattern> patterns = new ArrayList<>();
        Set<AEKey> visitedResources = new LinkedHashSet<>();
        ArrayDeque<AEKey> pending = new ArrayDeque<>();
        idFor(target, resourceIds, resources);
        pending.add(target);

        while (!pending.isEmpty()) {
            AEKey outputKey = pending.removeFirst();
            if (!visitedResources.add(outputKey)) {
                continue;
            }
            for (IPatternDetails details : grid.getCraftingService().getCraftingFor(outputKey)) {
                if (patternIds.containsKey(details)) {
                    continue;
                }
                String patternId = "p" + patternIds.size();
                PatternConversion converted = convertPattern(
                        grid, networkInventory, details, resourceIds, resources);
                if (converted == null) {
                    continue;
                }
                patternIds.put(details, patternId);
                patternDetails.put(patternId, details);
                patterns.add(new GlobalPattern(patternId, converted.inputs(), converted.outputs()));
                converted.inputKeys().forEach(pending::addLast);
            }
        }

        Map<String, Long> available = new LinkedHashMap<>();
        Set<String> emittable = new LinkedHashSet<>();
        resources.forEach((id, key) -> {
            long amount = networkInventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            if (amount > 0) {
                available.put(id, amount);
            }
            if (grid.getCraftingService().canEmitFor(key)) {
                emittable.add(id);
            }
        });
        Map<String, Integer> producerCounts = new LinkedHashMap<>();
        patterns.forEach(pattern -> pattern.outputs().keySet().forEach(
                id -> producerCounts.merge(id, 1, Integer::sum)));
        boolean hasMultiplePaths = producerCounts.values().stream().anyMatch(count -> count > 1);
        return new GraphAdapter(resourceIds, resources, patterns, patternDetails,
                available, emittable, hasMultiplePaths);
    }

    @Nullable
    private static PatternConversion convertPattern(
            IGrid grid, NetworkCraftingSimulationState networkInventory, IPatternDetails details,
            Map<AEKey, String> resourceIds, Map<String, AEKey> resources) {
        var level = grid.getPivot().getLevel();
        if (CraftingRipperPatterns.requiresNativeExecution(details, level)) return null;
        var knifeTemplates = quartzKnifeTemplates(details, level);
        if (!knifeTemplates.isEmpty()) {
            var actualKnives = new LinkedHashSet<AEKey>();
            for (var template : knifeTemplates) {
                for (var candidate : networkInventory.findFuzzyTemplates(template)) {
                    if (networkInventory.extract(candidate, 1, Actionable.SIMULATE) > 0) actualKnives.add(candidate);
                }
            }
            // Native selection can choose a component-bearing knife absent from getPossibleInputs().
            // Decline before reading any remainder; the final submitted plan is classified from usedItems.
            if (CraftingRipperPatterns.requiresNativeExecution(details, actualKnives, level)) return null;
        }
        Map<String, Long> inputs = new LinkedHashMap<>();
        Map<String, Long> outputs = new LinkedHashMap<>();
        List<AEKey> inputKeys = new ArrayList<>();

        for (IPatternDetails.IInput input : details.getInputs()) {
            var possible = input.getPossibleInputs();
            if (possible.length == 0 || input.getMultiplier() <= 0) {
                return null;
            }
            int selected = selectInput(grid, networkInventory, input, possible);
            if (selected < 0) return null;
            var stack = possible[selected];
            if (stack.amount() <= 0) {
                return null;
            }
            AEKey key = stack.what();
            long amount = Math.multiplyExact(stack.amount(), input.getMultiplier());
            inputs.merge(idFor(key, resourceIds, resources), amount, Math::addExact);
            inputKeys.add(key);

        }
        if (!knifeTemplates.isEmpty()
                && CraftingRipperPatterns.requiresNativeExecution(details, inputKeys, level)) return null;
        for (int index = 0; index < inputKeys.size(); index++) {
            var input = details.getInputs()[index];
            AEKey remaining = input.getRemainingKey(inputKeys.get(index));
            if (remaining != null) {
                outputs.merge(idFor(remaining, resourceIds, resources), input.getMultiplier(), Math::addExact);
            }
        }
        for (var output : details.getOutputs()) {
            if (output.amount() <= 0) {
                return null;
            }
            outputs.merge(idFor(output.what(), resourceIds, resources), output.amount(), Math::addExact);
        }
        return outputs.isEmpty() ? null : new PatternConversion(inputs, outputs, inputKeys);
    }

    private static List<AEKey> quartzKnifeTemplates(IPatternDetails pattern, net.minecraft.world.level.Level level) {
        var encoded = pattern.getDefinition().get(appeng.api.ids.AEComponents.ENCODED_CRAFTING_PATTERN);
        if (encoded == null) return List.of();
        var holder = level.getRecipeManager().byKey(encoded.recipeId()).orElse(null);
        if (holder == null || !(holder.value() instanceof appeng.recipes.quartzcutting.QuartzCuttingRecipe)) {
            return List.of();
        }
        var templates = new ArrayList<AEKey>();
        for (var input : encoded.inputs()) {
            if (input.is(appeng.datagen.providers.tags.ConventionTags.QUARTZ_KNIFE)) templates.add(appeng.api.stacks.AEItemKey.of(input));
        }
        return templates;
    }

    private static int selectInput(IGrid grid, NetworkCraftingSimulationState inventory,
                                   IPatternDetails.IInput input,
                                   appeng.api.stacks.GenericStack[] possible) {
        int best = -1;
        long bestCoverage = -1;
        for (int i = 0; i < possible.length; i++) {
            if (possible[i].amount() <= 0
                    || !input.isValid(possible[i].what(), grid.getPivot().getLevel())) {
                continue;
            }
            long stored = inventory.extract(possible[i].what(), Long.MAX_VALUE, Actionable.SIMULATE);
            long coverage = stored / possible[i].amount();
            if (coverage > bestCoverage
                    || (coverage == bestCoverage
                    && grid.getCraftingService().isCraftable(possible[i].what())
                    && !grid.getCraftingService().isCraftable(possible[best].what()))) {
                best = i;
                bestCoverage = coverage;
            }
        }
        return best;
    }

    private static String idFor(AEKey key, Map<AEKey, String> ids, Map<String, AEKey> resources) {
        return ids.computeIfAbsent(key, ignored -> {
            String id = "k" + ids.size();
            resources.put(id, key);
            return id;
        });
    }

    private record PatternConversion(Map<String, Long> inputs, Map<String, Long> outputs,
                                     List<AEKey> inputKeys) {
    }

    private record GraphAdapter(Map<AEKey, String> resourceIds, Map<String, AEKey> resources,
                                List<GlobalPattern> patterns,
                                Map<String, IPatternDetails> patternDetails,
                                Map<String, Long> availableStock,
                                Set<String> emittableResources,
                                boolean hasMultiplePaths) {
        String idFor(AEKey key) {
            return resourceIds.get(key);
        }

        AEKey keyFor(String id) {
            return resources.get(id);
        }

        IPatternDetails patternFor(String id) {
            return patternDetails.get(id);
        }
    }

    public record OptimizationAttempt(boolean handled, @Nullable CraftingPlan plan) {
        public static OptimizationAttempt notHandled() {
            return new OptimizationAttempt(false, null);
        }

        public static OptimizationAttempt handled(@Nullable CraftingPlan plan) {
            return new OptimizationAttempt(true, plan);
        }
    }
}
