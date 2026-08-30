package com.example.ae2lightoptimizer.crafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GlobalCraftingPlanner {
    public GlobalCraftingPlan plan(GlobalPlanRequest request) {
        try {
            Graph graph = buildRelevantGraph(request.target(), request.patterns());
            Components components = findComponents(graph);
            boolean cyclic = !components.cyclicComponents().isEmpty();
            if (cyclic && !request.ringTerminalOnline()) {
                return failed(GlobalPlanStatus.CYCLE_TERMINAL_REQUIRED, cyclic, components.count(), 0, 0);
            }

            Map<String, Long> reserved = cyclic
                    ? computeExternalCycleReserve(graph, components, request.reservedCycles())
                    : Map.of();
            // The reserve is a runtime seed-retention policy, not a deduction from the
            // current order's inventory. Subtracting it here made every finite order
            // falsely report the fixed reserve as missing external inputs.
            Map<String, Long> usableStock = new LinkedHashMap<>(request.availableStock());
            BalanceResult balance = solveBalances(request, graph, usableStock);
            if (balance.status() != GlobalPlanStatus.SOLVED) {
                return failed(balance.status(), cyclic, components.count(), balance.iterations(), 0);
            }

            ScheduleResult schedule = buildCompressedSchedule(
                    graph, balance.patternCounts(), request.target(),
                    request.emittableResources(), usableStock, request.budget());
            if (schedule.status() != GlobalPlanStatus.SOLVED) {
                return failed(schedule.status(), cyclic, components.count(),
                        balance.iterations(), schedule.batches());
            }

            Map<String, Long> credited = new LinkedHashMap<>();
            Map<String, Long> missing = new LinkedHashMap<>();
            for (var entry : schedule.requiredStock().entrySet()) {
                long available = usableStock.getOrDefault(entry.getKey(), 0L);
                long credit = Math.min(available, entry.getValue());
                if (credit > 0) {
                    credited.put(entry.getKey(), credit);
                }
                if (credit < entry.getValue()) {
                    missing.put(entry.getKey(), entry.getValue() - credit);
                }
            }

            return new GlobalCraftingPlan(
                    GlobalPlanStatus.SOLVED,
                    balance.patternCounts(),
                    schedule.schedule(),
                    schedule.requiredStock(),
                    credited,
                    missing,
                    schedule.emittedStock(),
                    reserved,
                    cyclic,
                    components.count(),
                    balance.iterations(),
                    schedule.batches());
        } catch (ArithmeticException overflow) {
            return failed(GlobalPlanStatus.ARITHMETIC_OVERFLOW, false, 0, 0, 0);
        }
    }

    private static BalanceResult solveBalances(GlobalPlanRequest request, Graph graph,
                                                Map<String, Long> usableStock) {
        Map<String, Long> counts = new LinkedHashMap<>();
        long targetFloor = Math.addExact(usableStock.getOrDefault(request.target(), 0L),
                request.requestedAmount());

        for (int iteration = 1; iteration <= request.budget().maxBalanceIterations(); iteration++) {
            Map<String, Long> balance = new LinkedHashMap<>(usableStock);
            for (GlobalPattern pattern : graph.patterns()) {
                long repetitions = counts.getOrDefault(pattern.id(), 0L);
                if (repetitions == 0) {
                    continue;
                }
                for (var input : pattern.inputs().entrySet()) {
                    balance.merge(input.getKey(), -Math.multiplyExact(input.getValue(), repetitions),
                            Math::addExact);
                }
                for (var output : pattern.outputs().entrySet()) {
                    balance.merge(output.getKey(), Math.multiplyExact(output.getValue(), repetitions),
                            Math::addExact);
                }
            }

            String deficitResource = null;
            long deficit = 0;
            for (String resource : graph.resources()) {
                long floor = resource.equals(request.target()) ? targetFloor : 0L;
                long amount = balance.getOrDefault(resource, 0L);
                if (amount < floor
                        && !graph.producers().getOrDefault(resource, List.of()).isEmpty()) {
                    deficitResource = resource;
                    deficit = Math.subtractExact(floor, amount);
                    break;
                }
            }
            if (deficitResource == null) {
                return new BalanceResult(GlobalPlanStatus.SOLVED, counts, iteration);
            }

            GlobalPattern producer = chooseProducer(
                    deficitResource, deficit, balance,
                    graph.producers().get(deficitResource), graph.producers());
            long output = producer.outputs().get(deficitResource);
            long selfInput = producer.inputs().getOrDefault(deficitResource, 0L);
            long directGain = output - selfInput;
            long divisor = directGain > 0 ? directGain : output;
            long increment = ceilDiv(deficit, divisor);
            long stockSupported = stockSupportedIncrement(producer, balance, graph.producers());
            if (stockSupported > 0 && stockSupported < increment) {
                increment = stockSupported;
            }
            counts.merge(producer.id(), increment, Math::addExact);
        }
        return new BalanceResult(GlobalPlanStatus.BUDGET_EXHAUSTED, Map.of(),
                request.budget().maxBalanceIterations());
    }

    private static GlobalPattern chooseProducer(String resource, long deficit,
                                                 Map<String, Long> balance,
                                                 List<GlobalPattern> candidates,
                                                 Map<String, List<GlobalPattern>> producers) {
        return candidates.stream()
                .min(Comparator
                        .comparingLong((GlobalPattern pattern) -> rawPressure(
                                pattern, resource, deficit, balance, producers))
                        .thenComparingLong(pattern -> craftablePressure(
                                pattern, resource, deficit, balance, producers))
                        .thenComparing(Comparator.comparingLong(
                                (GlobalPattern pattern) -> directGain(pattern, resource)).reversed())
                        .thenComparing(GlobalPattern::id))
                .orElseThrow();
    }

    private static long rawPressure(GlobalPattern pattern, String resource, long deficit,
                                    Map<String, Long> balance,
                                    Map<String, List<GlobalPattern>> producers) {
        long repetitions = projectedIncrement(pattern, resource, deficit);
        long pressure = 0;
        for (var input : pattern.inputs().entrySet()) {
            if (!producers.getOrDefault(input.getKey(), List.of()).isEmpty()) {
                continue;
            }
            long required = Math.multiplyExact(input.getValue(), repetitions);
            long shortfall = Math.max(0L, required - Math.max(0L, balance.getOrDefault(input.getKey(), 0L)));
            pressure = saturatingAdd(pressure, shortfall);
        }
        return pressure;
    }

    private static long craftablePressure(GlobalPattern pattern, String resource, long deficit,
                                          Map<String, Long> balance,
                                          Map<String, List<GlobalPattern>> producers) {
        long repetitions = projectedIncrement(pattern, resource, deficit);
        long pressure = 0;
        for (var input : pattern.inputs().entrySet()) {
            if (producers.getOrDefault(input.getKey(), List.of()).isEmpty()) {
                continue;
            }
            long required = Math.multiplyExact(input.getValue(), repetitions);
            long shortfall = Math.max(0L, required - Math.max(0L, balance.getOrDefault(input.getKey(), 0L)));
            pressure = saturatingAdd(pressure, shortfall);
        }
        return pressure;
    }

    private static long projectedIncrement(GlobalPattern pattern, String resource, long deficit) {
        long output = pattern.outputs().get(resource);
        long gain = directGain(pattern, resource);
        return ceilDiv(deficit, gain > 0 ? gain : output);
    }

    private static long directGain(GlobalPattern pattern, String resource) {
        return pattern.outputs().get(resource) - pattern.inputs().getOrDefault(resource, 0L);
    }

    private static long stockSupportedIncrement(
            GlobalPattern pattern, Map<String, Long> balance,
            Map<String, List<GlobalPattern>> producers) {
        long supported = Long.MAX_VALUE;
        boolean hasRawInput = false;
        for (var input : pattern.inputs().entrySet()) {
            if (!producers.getOrDefault(input.getKey(), List.of()).isEmpty()) {
                continue;
            }
            hasRawInput = true;
            long available = Math.max(0L, balance.getOrDefault(input.getKey(), 0L));
            supported = Math.min(supported, available / input.getValue());
        }
        return hasRawInput ? supported : Long.MAX_VALUE;
    }

    private static ScheduleResult buildCompressedSchedule(
            Graph graph, Map<String, Long> patternCounts, String preferredSeedResource,
            Set<String> emittable, Map<String, Long> availableStock,
            GlobalPlanningBudget budget) {
        Map<String, GlobalPattern> byId = new LinkedHashMap<>();
        graph.patterns().forEach(pattern -> byId.put(pattern.id(), pattern));
        Map<String, Long> remaining = new LinkedHashMap<>(patternCounts);
        Map<String, Long> stock = new LinkedHashMap<>();
        Map<String, Long> required = new LinkedHashMap<>();
        Map<String, Long> emitted = new LinkedHashMap<>();
        List<PatternBatch> schedule = new ArrayList<>();

        seedExternalInputs(remaining, byId, stock, required, emitted, emittable);

        while (remaining.values().stream().anyMatch(value -> value > 0)) {
            if (schedule.size() >= budget.maxScheduleBatches()) {
                return new ScheduleResult(GlobalPlanStatus.BUDGET_EXHAUSTED,
                        List.of(), Map.of(), Map.of(), schedule.size());
            }

            GlobalPattern runnable = null;
            long runnableBatch = 0;
            for (var entry : remaining.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                GlobalPattern pattern = byId.get(entry.getKey());
                long batch = feasibleBatch(pattern, entry.getValue(), stock);
                if (batch > 0) {
                    runnable = pattern;
                    runnableBatch = batch;
                    break;
                }
            }

            if (runnable == null) {
                GlobalPattern sourceReady = chooseSourceReadyPattern(remaining, byId);
                GlobalPattern seedPattern = sourceReady != null
                        ? sourceReady : chooseCheapestSeedPattern(
                                remaining, byId, stock, required, availableStock,
                                emittable, preferredSeedResource);
                if (seedPattern == null) {
                    return new ScheduleResult(GlobalPlanStatus.NO_FEASIBLE_PLAN,
                            List.of(), Map.of(), Map.of(), schedule.size());
                }
                Set<String> stillProduced = remainingProducedResources(remaining, byId);
                for (var input : seedPattern.inputs().entrySet()) {
                    long repetitions = sourceReady != null || !stillProduced.contains(input.getKey())
                            ? remaining.get(seedPattern.id()) : 1L;
                    long need = Math.multiplyExact(input.getValue(), repetitions);
                    long missing = Math.max(0L, need - stock.getOrDefault(input.getKey(), 0L));
                    if (missing == 0) {
                        continue;
                    }
                    stock.merge(input.getKey(), missing, Math::addExact);
                    if (emittable.contains(input.getKey())) {
                        emitted.merge(input.getKey(), missing, Math::addExact);
                    } else {
                        required.merge(input.getKey(), missing, Math::addExact);
                    }
                }
                continue;
            }

            applyBatch(stock, runnable, runnableBatch);
            remaining.merge(runnable.id(), -runnableBatch, Math::addExact);
            appendBatch(schedule, runnable.id(), runnableBatch);
        }

        return new ScheduleResult(GlobalPlanStatus.SOLVED, schedule, required, emitted, schedule.size());
    }

    private static void seedExternalInputs(
            Map<String, Long> remaining, Map<String, GlobalPattern> byId,
            Map<String, Long> stock, Map<String, Long> required,
            Map<String, Long> emitted, Set<String> emittable) {
        Set<String> produced = remainingProducedResources(remaining, byId);
        Map<String, Long> totalInputs = new LinkedHashMap<>();
        remaining.forEach((id, repetitions) -> {
            if (repetitions <= 0) {
                return;
            }
            for (var input : byId.get(id).inputs().entrySet()) {
                if (!produced.contains(input.getKey())) {
                    totalInputs.merge(input.getKey(),
                            Math.multiplyExact(input.getValue(), repetitions), Math::addExact);
                }
            }
        });
        totalInputs.forEach((resource, amount) -> {
            stock.put(resource, amount);
            if (emittable.contains(resource)) {
                emitted.put(resource, amount);
            } else {
                required.put(resource, amount);
            }
        });
    }

    private static GlobalPattern chooseSourceReadyPattern(Map<String, Long> remaining,
                                                          Map<String, GlobalPattern> byId) {
        Set<String> stillProduced = remainingProducedResources(remaining, byId);
        for (var entry : remaining.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            GlobalPattern pattern = byId.get(entry.getKey());
            if (pattern.inputs().keySet().stream().noneMatch(stillProduced::contains)) {
                return pattern;
            }
        }
        return null;
    }

    private static Set<String> remainingProducedResources(Map<String, Long> remaining,
                                                          Map<String, GlobalPattern> byId) {
        Set<String> stillProduced = new HashSet<>();
        remaining.forEach((id, count) -> {
            if (count > 0) {
                stillProduced.addAll(byId.get(id).outputs().keySet());
            }
        });
        return stillProduced;
    }

    private static GlobalPattern chooseCheapestSeedPattern(Map<String, Long> remaining,
                                                           Map<String, GlobalPattern> byId,
                                                           Map<String, Long> stock,
                                                           Map<String, Long> required,
                                                           Map<String, Long> availableStock,
                                                           Set<String> emittable,
                                                           String preferredSeedResource) {
        return remaining.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> byId.get(entry.getKey()))
                .min(Comparator
                        .comparingLong((GlobalPattern pattern) -> missingFromAvailableForOne(
                                pattern, stock, required, availableStock, emittable))
                        .thenComparingInt(pattern ->
                                pattern.inputs().containsKey(preferredSeedResource) ? 0 : 1)
                        .thenComparingLong(pattern -> missingForOne(pattern, stock))
                        .thenComparing(GlobalPattern::id))
                .orElse(null);
    }

    private static long missingFromAvailableForOne(
            GlobalPattern pattern, Map<String, Long> stock,
            Map<String, Long> required, Map<String, Long> availableStock,
            Set<String> emittable) {
        long missing = 0;
        for (var input : pattern.inputs().entrySet()) {
            if (emittable.contains(input.getKey())) {
                continue;
            }
            long increment = Math.max(0L,
                    input.getValue() - stock.getOrDefault(input.getKey(), 0L));
            long before = required.getOrDefault(input.getKey(), 0L);
            long after = Math.addExact(before, increment);
            long available = availableStock.getOrDefault(input.getKey(), 0L);
            long newlyMissing = Math.max(0L, after - available)
                    - Math.max(0L, before - available);
            missing = saturatingAdd(missing, newlyMissing);
        }
        return missing;
    }

    private static long missingForOne(GlobalPattern pattern, Map<String, Long> stock) {
        long missing = 0;
        for (var input : pattern.inputs().entrySet()) {
            long shortfall = Math.max(0L, input.getValue() - stock.getOrDefault(input.getKey(), 0L));
            missing = saturatingAdd(missing, shortfall);
        }
        return missing;
    }

    private static long feasibleBatch(GlobalPattern pattern, long remaining,
                                      Map<String, Long> stock) {
        if (pattern.inputs().isEmpty()) {
            return remaining;
        }
        long batch = remaining;
        for (var input : pattern.inputs().entrySet()) {
            batch = Math.min(batch, stock.getOrDefault(input.getKey(), 0L) / input.getValue());
            if (batch == 0) {
                return 0;
            }
        }
        return batch;
    }

    private static void applyBatch(Map<String, Long> stock, GlobalPattern pattern, long repetitions) {
        for (var input : pattern.inputs().entrySet()) {
            stock.merge(input.getKey(), -Math.multiplyExact(input.getValue(), repetitions), Math::addExact);
        }
        for (var output : pattern.outputs().entrySet()) {
            stock.merge(output.getKey(), Math.multiplyExact(output.getValue(), repetitions), Math::addExact);
        }
    }

    private static void appendBatch(List<PatternBatch> schedule, String patternId, long repetitions) {
        if (!schedule.isEmpty() && schedule.getLast().patternId().equals(patternId)) {
            PatternBatch previous = schedule.removeLast();
            schedule.add(new PatternBatch(patternId,
                    Math.addExact(previous.repetitions(), repetitions)));
        } else {
            schedule.add(new PatternBatch(patternId, repetitions));
        }
    }

    private static Map<String, Long> computeExternalCycleReserve(
            Graph graph, Components components, long reservedCycles) {
        if (reservedCycles == 0) {
            return Map.of();
        }
        Map<String, Long> perRound = new LinkedHashMap<>();
        for (GlobalPattern pattern : graph.patterns()) {
            Set<Integer> outputComponents = new HashSet<>();
            for (String output : pattern.outputs().keySet()) {
                int component = components.componentByResource().get(output);
                if (components.cyclicComponents().contains(component)) {
                    outputComponents.add(component);
                }
            }
            for (int component : outputComponents) {
                for (var input : pattern.inputs().entrySet()) {
                    if (components.componentByResource().get(input.getKey()) != component) {
                        perRound.merge(input.getKey(), input.getValue(), Math::addExact);
                    }
                }
            }
        }
        Map<String, Long> reserved = new LinkedHashMap<>();
        perRound.forEach((resource, amount) ->
                reserved.put(resource, Math.multiplyExact(amount, reservedCycles)));
        return reserved;
    }

    private static Graph buildRelevantGraph(String target, List<GlobalPattern> patterns) {
        Map<String, List<GlobalPattern>> allProducers = new LinkedHashMap<>();
        for (GlobalPattern pattern : patterns) {
            for (String output : pattern.outputs().keySet()) {
                allProducers.computeIfAbsent(output, ignored -> new ArrayList<>()).add(pattern);
            }
        }
        Set<String> needed = new LinkedHashSet<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        List<GlobalPattern> selected = new ArrayList<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        needed.add(target);
        pending.add(target);
        while (!pending.isEmpty()) {
            String resource = pending.removeFirst();
            for (GlobalPattern pattern : allProducers.getOrDefault(resource, List.of())) {
                if (selectedIds.add(pattern.id())) {
                    selected.add(pattern);
                    for (String input : pattern.inputs().keySet()) {
                        if (needed.add(input)) {
                            pending.addLast(input);
                        }
                    }
                }
            }
        }
        selected.sort(Comparator.comparing(GlobalPattern::id));
        Map<String, List<GlobalPattern>> producers = new LinkedHashMap<>();
        Set<String> resources = new LinkedHashSet<>();
        resources.add(target);
        for (GlobalPattern pattern : selected) {
            resources.addAll(pattern.inputs().keySet());
            resources.addAll(pattern.outputs().keySet());
            for (String output : pattern.outputs().keySet()) {
                producers.computeIfAbsent(output, ignored -> new ArrayList<>()).add(pattern);
            }
        }
        List<String> sortedResources = resources.stream().sorted().toList();
        return new Graph(List.copyOf(selected), producers, sortedResources);
    }

    private static Components findComponents(Graph graph) {
        Map<String, Set<String>> edges = new LinkedHashMap<>();
        for (String resource : graph.resources()) {
            edges.put(resource, new LinkedHashSet<>());
        }
        for (GlobalPattern pattern : graph.patterns()) {
            for (String output : pattern.outputs().keySet()) {
                edges.get(output).addAll(pattern.inputs().keySet());
            }
        }
        Tarjan tarjan = new Tarjan(edges);
        return tarjan.run();
    }

    private static GlobalCraftingPlan failed(GlobalPlanStatus status, boolean cyclic,
                                             int components, int iterations, int batches) {
        return new GlobalCraftingPlan(status, Map.of(), List.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), cyclic, components, iterations, batches);
    }

    private static long ceilDiv(long numerator, long denominator) {
        if (numerator <= 0 || denominator <= 0) {
            throw new ArithmeticException("Non-positive division");
        }
        return 1L + (numerator - 1L) / denominator;
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private record Graph(List<GlobalPattern> patterns,
                         Map<String, List<GlobalPattern>> producers,
                         List<String> resources) {}

    private record BalanceResult(GlobalPlanStatus status,
                                 Map<String, Long> patternCounts,
                                 int iterations) {}

    private record ScheduleResult(GlobalPlanStatus status,
                                  List<PatternBatch> schedule,
                                  Map<String, Long> requiredStock,
                                  Map<String, Long> emittedStock,
                                  int batches) {}

    private record Components(Map<String, Integer> componentByResource,
                              Set<Integer> cyclicComponents,
                              int count) {}

    private static final class Tarjan {
        private final Map<String, Set<String>> edges;
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final ArrayDeque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final Map<String, Integer> componentByResource = new HashMap<>();
        private final Set<Integer> cyclicComponents = new HashSet<>();
        private int nextIndex;
        private int componentCount;

        private Tarjan(Map<String, Set<String>> edges) {
            this.edges = edges;
        }

        private Components run() {
            for (String resource : edges.keySet()) {
                if (!index.containsKey(resource)) {
                    visit(resource);
                }
            }
            return new Components(Map.copyOf(componentByResource),
                    Set.copyOf(cyclicComponents), componentCount);
        }

        private void visit(String resource) {
            index.put(resource, nextIndex);
            lowLink.put(resource, nextIndex);
            nextIndex++;
            stack.push(resource);
            onStack.add(resource);

            for (String child : edges.getOrDefault(resource, Set.of())) {
                if (!index.containsKey(child)) {
                    visit(child);
                    lowLink.put(resource, Math.min(lowLink.get(resource), lowLink.get(child)));
                } else if (onStack.contains(child)) {
                    lowLink.put(resource, Math.min(lowLink.get(resource), index.get(child)));
                }
            }

            if (!lowLink.get(resource).equals(index.get(resource))) {
                return;
            }
            List<String> members = new ArrayList<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                members.add(member);
                componentByResource.put(member, componentCount);
            } while (!member.equals(resource));
            if (members.size() > 1 || edges.getOrDefault(resource, Set.of()).contains(resource)) {
                cyclicComponents.add(componentCount);
            }
            componentCount++;
        }
    }
}
