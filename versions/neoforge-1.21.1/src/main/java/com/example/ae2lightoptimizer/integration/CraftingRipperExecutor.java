package com.example.ae2lightoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.block.CraftingRipperLogic;
import com.example.ae2lightoptimizer.crafting.InstantCraftingBatch;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Validates and replays the entire selected job privately before its single 50 AE commit. */
public final class CraftingRipperExecutor {
    public static final double RIP_ENERGY = 50;

    private CraftingRipperExecutor() {
    }

    public static boolean hasActiveRipper(@Nullable IGrid grid) {
        return grid != null && !grid.getActiveMachines(CraftingRipperBlockEntity.class).isEmpty();
    }

    public static boolean ownsAny(IGrid grid, Map<IPatternDetails, Long> tasks) {
        return selectProvider(grid, tasks, false) != null;
    }

    public static boolean requiresNativePlan(Map<IPatternDetails, Long> tasks, Level level) {
        return tasks.keySet().stream().anyMatch(pattern -> CraftingRipperPatterns.requiresNativeExecution(pattern, level));
    }

    /** Initial stock is the exact material selection made by AE2, including full item components. */
    public static boolean requiresNativePlan(ICraftingPlan plan, Level level) {
        return requiresNativePlan(plan.patternTimes(), snapshot(plan.usedItems()).keySet(), level);
    }

    private static boolean requiresNativePlan(Map<IPatternDetails, Long> tasks, Iterable<AEKey> selectedStock, Level level) {
        var candidates = new java.util.LinkedHashSet<AEKey>();
        selectedStock.forEach(candidates::add);
        // A selected upstream recipe may produce the knife used later in the same job.
        for (var pattern : tasks.keySet()) for (var output : pattern.getOutputs()) candidates.add(output.what());
        return tasks.keySet().stream().anyMatch(pattern ->
                CraftingRipperPatterns.requiresNativeExecution(pattern, candidates, level));
    }

    @Nullable
    private static Selection selectProvider(IGrid grid, Map<IPatternDetails, Long> tasks, boolean requireReady) {
        if (!hasActiveRipper(grid) || tasks.isEmpty()) {
            return null;
        }
        // AE2 already selected concrete patterns by priority during planning. Providers sharing an
        // equal pattern use a mutable round-robin iterable; preflight must not traverse or reorder it.
        for (var host : grid.getActiveMachines(CraftingRipperBlockEntity.class)) {
            if (requireReady && host.getLogic().isBusy()) {
                continue;
            }
            for (var pattern : tasks.keySet()) {
                if (host.ownsPattern(pattern)) {
                    return new Selection(host.getLogic(), pattern);
                }
            }
        }
        return null;
    }

    /** Runs before AE2 extracts initial stock, so invalid mixed chains cannot partially execute. */
    public static boolean acceptsPlan(ICraftingPlan plan, Level level) {
        if (!plan.emittedItems().isEmpty() || !plan.missingItems().isEmpty()) {
            return false;
        }
        if (requiresNativePlan(plan, level)) {
            return RipperNativeCrafting.acceptsPlan(plan, level);
        }
        var schedule = plan instanceof ScheduledCraftingPlan scheduled
                ? scheduled.ae2lightoptimizer$getSchedule() : null;
        var batches = schedule == null ? null : schedule.batches();
        long reserve = schedule == null ? 0 : schedule.finalOutputReserve();
        return prepare(plan.patternTimes(), snapshot(plan.usedItems()), batches,
                plan.finalOutput().what(), plan.finalOutput().amount(), reserve, level) != null;
    }

    public static Result execute(IGrid grid, IEnergyService energy, ListCraftingInventory inventory,
                                 ScheduledCraftingJob job, Level level) {
        if (job.ae2lightoptimizer$isNativeRipperJob()) {
            var state = job.ae2lightoptimizer$getNativeState();
            if (state == null) {
                state = RipperNativeCrafting.create(job, level);
                job.ae2lightoptimizer$setNativeState(state);
            }
            if (state.invalid() || state.remainingTasks().keySet().stream().anyMatch(
                    pattern -> !CraftingRipperPatterns.supportsPattern(pattern, level))) return Result.INVALID;
            var selected = state.remainingTasks().isEmpty() ? null
                    : selectProvider(grid, state.remainingTasks(), true);
            if (selected == null && !state.remainingTasks().isEmpty()) return Result.WAITING;
            long before = state.committedOperations();
            var result = RipperNativeCrafting.execute(state, inventory, job, level, energy);
            if (state.committedOperations() > before && selected != null && state.lastPattern() != null) {
                selected.provider().onRipperCrafted(state.lastPattern());
            }
            return switch (result) {
                case COMPLETED -> Result.COMPLETED;
                case INVALID -> Result.INVALID;
                case PROGRESSED, WAITING -> Result.WAITING;
            };
        }
        if (job.ae2lightoptimizer$isRipped()) {
            return deliver(inventory, job);
        }
        Map<IPatternDetails, Long> tasks = job.ae2lightoptimizer$getRemainingTasks();
        // Providers or emitters already in flight must return before an atomic replay can start.
        if (job.ae2lightoptimizer$hasWaitingItems()) {
            return job.ae2lightoptimizer$isRipperRequested() ? Result.WAITING : Result.NOT_HANDLED;
        }
        if (requiresNativePlan(tasks, snapshot(inventory.list).keySet(), level)) {
            // Classify an older job before remainder preflight, without taking over another provider's job.
            if (!job.ae2lightoptimizer$isRipperRequested() && !ownsAny(grid, tasks)) return Result.NOT_HANDLED;
            job.ae2lightoptimizer$useNativeRipper();
            return Result.WAITING;
        }
        if (job.ae2lightoptimizer$isRipperRequested()) {
            if (tasks.isEmpty() || tasks.keySet().stream().anyMatch(
                    pattern -> !CraftingRipperPatterns.supportsPattern(pattern, level))) {
                return Result.INVALID;
            }
        }
        Selection selected = selectProvider(grid, tasks, true);
        if (selected == null) {
            return job.ae2lightoptimizer$isRipperRequested() ? Result.WAITING : Result.NOT_HANDLED;
        }
        var batches = remainingBatches(job);
        Map<AEKey, Long> result = prepare(tasks, snapshot(inventory.list), batches,
                job.ae2lightoptimizer$getFinalOutputKey(), job.ae2lightoptimizer$getRemainingRequest(),
                job.ae2lightoptimizer$getFinalOutputReserve(), level);
        if (result == null) {
            return Result.INVALID;
        }
        if (energy.extractAEPower(RIP_ENERGY, Actionable.SIMULATE,
                PowerMultiplier.ONE) < RIP_ENERGY) {
            return Result.WAITING;
        }

        // Server-thread execution owns this CPU stock. No external storage is mutated during preflight.
        double charged = energy.extractAEPower(RIP_ENERGY, Actionable.MODULATE, PowerMultiplier.ONE);
        if (charged < RIP_ENERGY) {
            if (charged > 0) {
                energy.injectPower(charged, Actionable.MODULATE);
            }
            return Result.WAITING;
        }
        inventory.clear();
        result.forEach((key, amount) -> inventory.insert(key, amount, Actionable.MODULATE));
        job.ae2lightoptimizer$markRipped();
        selected.provider().onRipperCrafted(selected.pattern());
        return deliver(inventory, job);
    }

    private static Result deliver(ListCraftingInventory inventory, ScheduledCraftingJob job) {
        AEKey finalKey = job.ae2lightoptimizer$getFinalOutputKey();
        long requested = job.ae2lightoptimizer$getRemainingRequest();
        long available = inventory.extract(finalKey, requested, Actionable.SIMULATE);
        if (available < requested) {
            return Result.INVALID;
        }
        if (job.ae2lightoptimizer$isStandalone()) {
            job.ae2lightoptimizer$decrementRemainingRequest(requested);
            return Result.COMPLETED;
        }
        long routed = Math.max(0, Math.min(requested,
                job.ae2lightoptimizer$deliver(finalKey, requested, Actionable.SIMULATE)));
        if (routed > 0) {
            routed = Math.max(0, Math.min(routed,
                    job.ae2lightoptimizer$deliver(finalKey, routed, Actionable.MODULATE)));
            if (routed > 0) {
                inventory.extract(finalKey, routed, Actionable.MODULATE);
            }
        }
        // Keep undelivered products private and persist the paid execution state. A full requester
        // retries delivery on following ticks without replaying the recipe or paying another 50 AE.
        job.ae2lightoptimizer$decrementRemainingRequest(routed);
        return job.ae2lightoptimizer$getRemainingRequest() == 0 ? Result.COMPLETED : Result.WAITING;
    }

    @Nullable
    private static List<CraftingExecutionSchedule.Batch> remainingBatches(ScheduledCraftingJob job) {
        if (!job.ae2lightoptimizer$hasSchedule()) {
            return null;
        }
        var schedule = job.ae2lightoptimizer$getSchedule();
        List<CraftingExecutionSchedule.Batch> result = new ArrayList<>();
        int current = job.ae2lightoptimizer$getBatchIndex();
        for (int i = current; i < schedule.batches().size(); i++) {
            var batch = schedule.batches().get(i);
            result.add(new CraftingExecutionSchedule.Batch(batch.pattern(), i == current
                    ? job.ae2lightoptimizer$getRemainingInBatch() : batch.repetitions()));
        }
        return result;
    }

    @Nullable
    private static Map<AEKey, Long> prepare(
            Map<IPatternDetails, Long> tasks, Map<AEKey, Long> initial,
            @Nullable List<CraftingExecutionSchedule.Batch> schedule,
            @Nullable AEKey finalKey, long requested, long reserve, Level level) {
        try {
            if (finalKey == null || requested <= 0 || tasks.isEmpty()) {
                return null;
            }
            for (var entry : tasks.entrySet()) {
                if (entry.getValue() <= 0 || !CraftingRipperPatterns.supportsPattern(entry.getKey(), level)) {
                    return null;
                }
            }
            var simulation = new ListCraftingInventory(key -> { });
            initial.forEach((key, amount) -> simulation.insert(key, amount, Actionable.MODULATE));
            var pending = new LinkedHashMap<>(tasks);
            // Work scales with distinct patterns, input variants and supplied compressed batches.
            // This bound never depends on requested quantities and prevents unscheduled cycle expansion.
            long complexity = Math.addExact(tasks.size(), initial.size() + 1L);
            for (var pattern : tasks.keySet()) {
                for (var input : pattern.getInputs()) {
                    complexity = Math.addExact(complexity, input.getPossibleInputs().length + 1L);
                }
            }
            long workLimit = Math.multiplyExact(Math.multiplyExact(complexity, complexity), 8);
            if (schedule != null) {
                var scheduledCounts = new LinkedHashMap<IPatternDetails, Long>();
                schedule.forEach(batch -> scheduledCounts.merge(batch.pattern(), batch.repetitions(), Math::addExact));
                if (!scheduledCounts.equals(tasks)) {
                    return null;
                }
                workLimit = Math.addExact(workLimit, Math.multiplyExact(schedule.size(), complexity));
                for (var batch : schedule) {
                    long remaining = batch.repetitions();
                    while (remaining > 0) {
                        if (--workLimit < 0) {
                            return null;
                        }
                        long performed = applyAvailableBatch(simulation, batch.pattern(), remaining, level);
                        if (performed == 0) {
                            return null;
                        }
                        remaining -= performed;
                    }
                }
            } else {
                while (!pending.isEmpty()) {
                    boolean progressed = false;
                    var iterator = pending.entrySet().iterator();
                    while (iterator.hasNext()) {
                        if (--workLimit < 0) {
                            return null;
                        }
                        var entry = iterator.next();
                        long performed = applyAvailableBatch(simulation, entry.getKey(), entry.getValue(), level);
                        if (performed > 0) {
                            progressed = true;
                            long remaining = entry.getValue() - performed;
                            if (remaining == 0) {
                                iterator.remove();
                            } else {
                                entry.setValue(remaining);
                            }
                        }
                    }
                    if (!progressed) {
                        return null;
                    }
                }
            }
            Map<AEKey, Long> result = snapshot(simulation.list);
            return result.getOrDefault(finalKey, 0L) >= Math.addExact(requested, reserve) ? result : null;
        } catch (ArithmeticException | IllegalArgumentException unsupported) {
            return null;
        }
    }

    private static long applyAvailableBatch(ListCraftingInventory simulation,
                                            IPatternDetails pattern, long requested, Level level) {
        var output = new KeyCounter();
        var remainder = new KeyCounter();
        var inputs = CraftingCpuHelper.extractPatternInputs(pattern, simulation, level, output, remainder);
        if (inputs == null) {
            return 0;
        }
        if (!CraftingRipperPatterns.validateInputs(pattern, inputs, level)) {
            throw new IllegalArgumentException("Selected recipe input failed live assembly validation");
        }
        Map<AEKey, Long> consumed = new LinkedHashMap<>();
        for (var counter : inputs) {
            merge(consumed, counter);
        }
        Map<AEKey, Long> produced = snapshot(output);
        merge(produced, remainder);
        CraftingCpuHelper.reinjectPatternInputs(simulation, inputs);
        Map<AEKey, Long> before = snapshot(simulation.list);
        long repetitions = InstantCraftingBatch.executableRepetitions(before, consumed, produced, requested);
        var after = InstantCraftingBatch.apply(before, consumed, produced, repetitions);
        simulation.clear();
        after.forEach((key, amount) -> simulation.insert(key, amount, Actionable.MODULATE));
        return repetitions;
    }

    private static Map<AEKey, Long> snapshot(KeyCounter counter) {
        Map<AEKey, Long> result = new LinkedHashMap<>();
        merge(result, counter);
        return result;
    }

    private static void merge(Map<AEKey, Long> destination, KeyCounter counter) {
        for (var entry : counter) {
            if (entry.getLongValue() < 0) {
                throw new IllegalArgumentException("Negative crafting stock");
            }
            if (entry.getLongValue() > 0) {
                destination.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
        }
    }

    public enum Result {
        NOT_HANDLED, WAITING, COMPLETED, INVALID
    }

    private record Selection(CraftingRipperLogic provider, IPatternDetails pattern) {
    }
}
