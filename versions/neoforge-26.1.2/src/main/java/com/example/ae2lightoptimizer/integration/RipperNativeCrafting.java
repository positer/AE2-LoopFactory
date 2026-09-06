package com.example.ae2lightoptimizer.integration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * A CPU-local native crafting continuation. Physical CPU inventory always contains real keys.
 * A credit records only an output this particular job already made; it never changes network key
 * matching. World postprocessing runs once, at commit, after material and energy preflight.
 */
public final class RipperNativeCrafting {
    public static final String TAG_NAME = "ae2loNativeContinuation";

    private RipperNativeCrafting() {}

    /** This check is safe before AE2 takes initial materials and never asks for dynamic remainders. */
    public static boolean acceptsPlan(ICraftingPlan plan, Level level) {
        if (!plan.emittedItems().isEmpty() || !plan.missingItems().isEmpty() || plan.patternTimes().isEmpty()) return false;
        try {
            for (var entry : plan.patternTimes().entrySet()) {
                var pattern = entry.getKey();
                if (entry.getValue() <= 0 || !CraftingRipperPatterns.supportsPattern(pattern, level)) return false;
                var available = new ListCraftingInventory(ignored -> {});
                for (var stock : plan.usedItems()) {
                    if (stock.getLongValue() < 0) return false;
                    if (stock.getLongValue() > 0) available.insert(stock.getKey(), stock.getLongValue(), Actionable.MODULATE);
                }
                var selected = CraftingCpuHelper.extractPatternInputs(withoutRemainders(pattern), available, level,
                        new KeyCounter(), new KeyCounter());
                // Future ingredients (including future saved-map IDs) do not exist yet. Their native
                // recipe definition was checked above; their actual frame is checked before its commit.
                if (selected == null) continue;
                var grid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
                ((IMolecularAssemblerSupportedPattern) pattern).fillCraftingGrid(selected, grid::set);
                for (var counter : selected) {
                    counter.removeZeros();
                    if (!counter.isEmpty()) return false;
                }
                var actual = assemble(pattern, grid, level);
                var expected = pattern.getPrimaryOutput();
                if (actual.isEmpty() || actual.getCount() != expected.amount()
                        || !AEItemKey.of(actual).equals(expected.what())) return false;
            }
            return true;
        } catch (RuntimeException invalidCurrentInput) {
            return false;
        }
    }

    public static State create(ScheduledCraftingJob job, Level level) {
        var state = new State(UUID.randomUUID());
        state.remaining.putAll(job.ae2lightoptimizer$getRemainingTasks());
        if (state.remaining.isEmpty() || state.remaining.entrySet().stream().anyMatch(entry -> entry.getValue() <= 0
                || !CraftingRipperPatterns.supportsPattern(entry.getKey(), level))) state.invalid = true;
        return state;
    }

    /** Caller must keep this State on the same executing job and save it alongside CPU inventory. */
    public static Result execute(State state, ListCraftingInventory inventory, ScheduledCraftingJob job,
            Level level, IEnergyService energy) {
        if (state.invalid) return Result.INVALID;
        if (job.ae2lightoptimizer$hasWaitingItems()) return Result.WAITING;
        if (state.remaining.isEmpty()) return deliver(state, inventory, job);
        if (state.lastCommitTick == level.getGameTime()) return Result.WAITING;
        try {
            // A datapack reload can invalidate any later step, so check the whole remaining task now.
            for (var pattern : state.remaining.keySet()) {
                if (!CraftingRipperPatterns.supportsPattern(pattern, level)) return Result.INVALID;
            }
            Prepared prepared = null;
            for (var pattern : state.remaining.keySet()) {
                if (job.ae2lightoptimizer$hasSchedule()) {
                    var current = job.ae2lightoptimizer$currentPattern();
                    if (current == null || !current.getDefinition().equals(pattern.getDefinition())) continue;
                }
                prepared = prepare(pattern, state, inventory, level);
                if (prepared != null) break;
            }
            if (prepared == null) return Result.INVALID;
            if (!state.paid) {
                double needed = CraftingRipperExecutor.RIP_ENERGY;
                if (energy.extractAEPower(needed, Actionable.SIMULATE, PowerMultiplier.ONE) < needed) return Result.WAITING;
                double charged = energy.extractAEPower(needed, Actionable.MODULATE, PowerMultiplier.ONE);
                if (charged < needed) {
                    if (charged > 0) energy.injectPower(charged, Actionable.MODULATE);
                    return Result.WAITING;
                }
                state.paid = true;
            }
            return commit(prepared, state, inventory, job, level);
        } catch (IllegalArgumentException | ArithmeticException invalidPlan) {
            state.invalid = true;
            return Result.INVALID;
        }
    }

    @Nullable
    private static Prepared prepare(IPatternDetails pattern, State state, ListCraftingInventory inventory, Level level) {
        var physical = snapshot(inventory.list);
        var logicalCreditKeys = state.credits.stream().map(Credit::planned).collect(java.util.stream.Collectors.toSet());
        var projected = new ListCraftingInventory(ignored -> {}) {
            @Override
            public Iterable<AEKey> findFuzzyTemplates(AEKey requestedTemplate) {
                var candidates = new ArrayList<AEKey>();
                for (var candidate : super.findFuzzyTemplates(requestedTemplate)) {
                    // A job-owned logical identity is usable only by the exact declared input.
                    // Fuzzy reuse could feed an already completed map back into the same task.
                    if (candidate.equals(requestedTemplate) || !logicalCreditKeys.contains(candidate)) candidates.add(candidate);
                }
                return candidates;
            }
        };
        physical.forEach((key, amount) -> projected.insert(key, amount, Actionable.MODULATE));
        for (var credit : state.credits) {
            if (projected.extract(credit.actual(), credit.amount(), Actionable.MODULATE) != credit.amount()) {
                throw new IllegalArgumentException("Native output credit exceeds physical CPU stock");
            }
        }
        for (var credit : state.credits) projected.insert(credit.planned(), credit.amount(), Actionable.MODULATE);

        // The native selector normally evaluates getRemainingKey during extraction. A knife remainder
        // can consume world RNG, so input selection deliberately has no remainder side effects.
        var inputs = CraftingCpuHelper.extractPatternInputs(withoutRemainders(pattern), projected, level,
                new KeyCounter(), new KeyCounter());
        if (inputs == null) return null;
        var copies = new KeyCounter[inputs.length];
        var consumed = new LinkedHashMap<AEKey, Long>();
        for (int i = 0; i < inputs.length; i++) {
            copies[i] = new KeyCounter();
            for (var input : inputs[i]) {
                copies[i].add(input.getKey(), input.getLongValue());
                consumed.merge(input.getKey(), input.getLongValue(), Math::addExact);
            }
        }
        var patternGrid = new ArrayList<ItemStack>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        var nativePattern = (IMolecularAssemblerSupportedPattern) pattern;
        nativePattern.fillCraftingGrid(copies, patternGrid::set);
        for (var counter : copies) {
            counter.removeZeros();
            if (!counter.isEmpty()) throw new IllegalArgumentException("Unconsumed selected pattern inputs");
        }

        var credits = new ArrayList<>(state.credits);
        var physicalRemaining = new LinkedHashMap<>(physical);
        var allocations = new LinkedHashMap<AEKey, Deque<Allocation>>();
        for (var entry : consumed.entrySet()) {
            allocations.put(entry.getKey(), allocate(entry.getKey(), entry.getValue(), physicalRemaining, credits));
        }
        var actualGrid = new ArrayList<ItemStack>(patternGrid.size());
        boolean substitutedIdentity = false;
        for (var item : patternGrid) {
            if (item.isEmpty()) {
                actualGrid.add(ItemStack.EMPTY);
                continue;
            }
            var wrapped = GenericStack.unwrapItemStack(item);
            if (wrapped != null) {
                var actual = take(allocations.get(wrapped.what()), wrapped.amount());
                if (!actual.equals(wrapped.what())) throw new IllegalArgumentException("Non-item native identity credit");
                actualGrid.add(item.copy());
            } else {
                var logicalKey = AEItemKey.of(item);
                var actual = take(allocations.get(logicalKey), 1);
                if (!(actual instanceof AEItemKey actualItem)) throw new IllegalArgumentException("Non-item crafting input");
                actualGrid.add(actualItem.toStack());
                substitutedIdentity |= !actual.equals(logicalKey);
            }
        }
        var output = assemble(pattern, actualGrid, level);
        if (output.isEmpty()) return null;
        var plannedOutput = pattern.getPrimaryOutput();
        if (output.getCount() != plannedOutput.amount()) throw new IllegalArgumentException("Recipe output count changed");
        if (!substitutedIdentity && !AEItemKey.of(output).equals(plannedOutput.what())) {
            throw new IllegalArgumentException("Recipe output changed without a job-owned input identity");
        }
        var physicalConsumed = new LinkedHashMap<AEKey, Long>();
        physical.forEach((key, amount) -> {
            long difference = Math.subtractExact(amount, physicalRemaining.getOrDefault(key, 0L));
            if (difference > 0) physicalConsumed.put(key, difference);
        });
        List<ItemStack> plannedRemainders = List.of();
        if (!CraftingRipperPatterns.requiresNativeExecution(pattern, physicalConsumed.keySet(), level)) {
            plannedRemainders = nativePattern.getRemainingItems(CraftingInput.of(3, 3, patternGrid));
        }
        return new Prepared(pattern, physicalConsumed, credits, actualGrid, output, plannedRemainders);
    }

    private static Result commit(Prepared prepared, State state, ListCraftingInventory inventory,
            ScheduledCraftingJob job, Level level) {
        for (var input : prepared.consumed().entrySet()) {
            if (inventory.extract(input.getKey(), input.getValue(), Actionable.SIMULATE) != input.getValue()) {
                state.invalid = true;
                return Result.INVALID;
            }
        }
        for (var input : prepared.consumed().entrySet()) inventory.extract(input.getKey(), input.getValue(), Actionable.MODULATE);
        state.credits.clear();
        state.credits.addAll(prepared.credits());
        var output = prepared.output().copy();
        // Same order as AE2's molecular assembler: output postprocessing, crafted event, remainders.
        // No observer can mistake the pre-postprocessing pattern key for a finished physical item.
        output.onCraftedBySystem(level);
        CraftingEvent.fireAutoCraftingEvent(level, prepared.pattern(), output,
                new SimpleContainer(prepared.grid().toArray(ItemStack[]::new)));
        var remainders = ((IMolecularAssemblerSupportedPattern) prepared.pattern())
                .getRemainingItems(CraftingInput.of(3, 3, prepared.grid()));
        if (!output.isEmpty()) {
            var actual = AEItemKey.of(output);
            inventory.insert(actual, output.getCount(), Actionable.MODULATE);
            addCredit(state.credits, prepared.pattern().getPrimaryOutput().what(), actual, output.getCount());
        }
        for (int slot = 0; slot < remainders.size(); slot++) {
            var remainder = remainders.get(slot);
            if (remainder.isEmpty()) continue;
            var actual = AEItemKey.of(remainder);
            inventory.insert(actual, remainder.getCount(), Actionable.MODULATE);
            if (slot < prepared.plannedRemainders().size()) {
                var planned = prepared.plannedRemainders().get(slot);
                if (!planned.isEmpty() && planned.getCount() == remainder.getCount()) {
                    addCredit(state.credits, AEItemKey.of(planned), actual, remainder.getCount());
                }
            }
        }
        long remaining = state.remaining.get(prepared.pattern()) - 1;
        if (remaining == 0) state.remaining.remove(prepared.pattern());
        else state.remaining.put(prepared.pattern(), remaining);
        job.ae2lightoptimizer$advance(1);
        state.committedOperations = Math.addExact(state.committedOperations, 1);
        state.lastPattern = prepared.pattern();
        state.lastCommitTick = level.getGameTime();
        state.lastActualOutput = output.isEmpty() ? null : AEItemKey.of(output);
        if (output.getCount() != prepared.pattern().getPrimaryOutput().amount()) {
            state.invalid = true;
            return Result.INVALID;
        }
        return state.remaining.isEmpty() ? deliver(state, inventory, job) : Result.PROGRESSED;
    }

    private static ItemStack assemble(IPatternDetails pattern, List<ItemStack> grid, Level level) {
        if (pattern instanceof AECraftingPattern) {
            var encoded = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
            var holder = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().byKey(encoded.recipeId()).orElse(null);
            if (holder == null || holder.value().getType() != RecipeType.CRAFTING
                    || !(holder.value() instanceof CraftingRecipe recipe)) return ItemStack.EMPTY;
            var actual = new ArrayList<>(grid);
            for (int slot = 0; slot < actual.size(); slot++) {
                if (GenericStack.unwrapItemStack(actual.get(slot)) != null) actual.set(slot, encoded.inputs().get(slot).copy());
            }
            var input = CraftingInput.of(3, 3, actual);
            return recipe.matches(input, level) ? recipe.assemble(input) : ItemStack.EMPTY;
        }
        if (pattern instanceof AESmithingTablePattern smithing) {
            var holder = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().byKey(smithing.getRecipeId()).orElse(null);
            if (holder == null || holder.value().getType() != RecipeType.SMITHING
                    || !(holder.value() instanceof SmithingRecipe recipe)) return ItemStack.EMPTY;
            var input = new SmithingRecipeInput(grid.get(3), grid.get(4), grid.get(5));
            return recipe.matches(input, level) ? recipe.assemble(input) : ItemStack.EMPTY;
        }
        if (pattern instanceof CatalogSmithingPattern smithing) {
            var holder = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager()
                    .byKey(smithing.getRecipeId()).orElse(null);
            if (holder == null || holder.value().getType() != RecipeType.SMITHING
                    || !(holder.value() instanceof SmithingRecipe recipe)) return ItemStack.EMPTY;
            var input = new SmithingRecipeInput(grid.get(3), grid.get(4), grid.get(5));
            return recipe.matches(input, level) ? recipe.assemble(input) : ItemStack.EMPTY;
        }
        if (pattern instanceof AEStonecuttingPattern stonecutting) {
            var holder = ((net.minecraft.server.level.ServerLevel) level).getServer().getRecipeManager().byKey(stonecutting.getRecipeId()).orElse(null);
            if (holder == null || holder.value().getType() != RecipeType.STONECUTTING
                    || !(holder.value() instanceof StonecutterRecipe recipe)) return ItemStack.EMPTY;
            var input = new SingleRecipeInput(grid.get(4));
            return recipe.matches(input, level) ? recipe.assemble(input) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    private static Result deliver(State state, ListCraftingInventory inventory, ScheduledCraftingJob job) {
        var planned = job.ae2lightoptimizer$getFinalOutputKey();
        long requested = job.ae2lightoptimizer$getRemainingRequest();
        if (requested == 0) return Result.COMPLETED;
        if (planned == null) return Result.INVALID;
        var physical = snapshot(inventory.list);
        var credits = new ArrayList<>(state.credits);
        Deque<Allocation> outputs;
        try {
            outputs = allocate(planned, requested, physical, credits);
        } catch (IllegalArgumentException insufficientOutput) {
            return Result.INVALID;
        }
        if (job.ae2lightoptimizer$isStandalone()) {
            // Real output and any unused seeds stay private until native finishJob -> storeItems.
            job.ae2lightoptimizer$decrementRemainingRequest(requested);
            return Result.COMPLETED;
        }
        for (var allocation : outputs) {
            long accepted = Math.max(0, Math.min(allocation.amount(),
                    job.ae2lightoptimizer$deliver(allocation.actual(), allocation.amount(), Actionable.SIMULATE)));
            if (accepted == 0) continue;
            accepted = Math.max(0, Math.min(accepted,
                    job.ae2lightoptimizer$deliver(allocation.actual(), accepted, Actionable.MODULATE)));
            if (accepted == 0) continue;
            inventory.extract(allocation.actual(), accepted, Actionable.MODULATE);
            if (!allocation.actual().equals(planned)) consumeCredit(state.credits, planned, allocation.actual(), accepted);
            job.ae2lightoptimizer$decrementRemainingRequest(accepted);
        }
        return job.ae2lightoptimizer$getRemainingRequest() == 0 ? Result.COMPLETED : Result.WAITING;
    }

    private static Deque<Allocation> allocate(AEKey planned, long requested, Map<AEKey, Long> physical,
            List<Credit> credits) {
        var result = new ArrayDeque<Allocation>();
        long reserved = 0;
        for (var credit : credits) if (credit.actual().equals(planned)) reserved = Math.addExact(reserved, credit.amount());
        long free = Math.subtractExact(physical.getOrDefault(planned, 0L), reserved);
        if (free < 0) throw new IllegalArgumentException("Native alias is not physically backed");
        long direct = Math.min(requested, free);
        if (direct > 0) {
            result.add(new Allocation(planned, direct));
            subtract(physical, planned, direct);
            requested -= direct;
        }
        for (int i = 0; i < credits.size() && requested > 0; i++) {
            var credit = credits.get(i);
            if (!credit.planned().equals(planned)) continue;
            long used = Math.min(requested, credit.amount());
            result.add(new Allocation(credit.actual(), used));
            subtract(physical, credit.actual(), used);
            if (used == credit.amount()) credits.remove(i--);
            else credits.set(i, new Credit(credit.planned(), credit.actual(), credit.amount() - used));
            requested -= used;
        }
        if (requested != 0) throw new IllegalArgumentException("Native result identity lacks matching physical stock");
        return result;
    }

    private static AEKey take(Deque<Allocation> allocations, long amount) {
        if (allocations == null || allocations.isEmpty()) throw new IllegalArgumentException("Missing native slot allocation");
        var first = allocations.removeFirst();
        if (first.amount() < amount) throw new IllegalArgumentException("Split native slot allocation");
        if (first.amount() > amount) allocations.addFirst(new Allocation(first.actual(), first.amount() - amount));
        return first.actual();
    }

    private static void subtract(Map<AEKey, Long> values, AEKey key, long amount) {
        long left = Math.subtractExact(values.getOrDefault(key, 0L), amount);
        if (left < 0) throw new IllegalArgumentException("Negative native physical stock");
        if (left == 0) values.remove(key); else values.put(key, left);
    }

    private static void addCredit(List<Credit> credits, AEKey planned, AEKey actual, long amount) {
        if (planned.equals(actual) || amount <= 0) return;
        for (int i = 0; i < credits.size(); i++) {
            var credit = credits.get(i);
            if (credit.planned().equals(planned) && credit.actual().equals(actual)) {
                credits.set(i, new Credit(planned, actual, Math.addExact(credit.amount(), amount)));
                return;
            }
        }
        credits.add(new Credit(planned, actual, amount));
    }

    private static void consumeCredit(List<Credit> credits, AEKey planned, AEKey actual, long amount) {
        for (int i = 0; i < credits.size() && amount > 0; i++) {
            var credit = credits.get(i);
            if (!credit.planned().equals(planned) || !credit.actual().equals(actual)) continue;
            long used = Math.min(amount, credit.amount());
            if (used == credit.amount()) credits.remove(i--);
            else credits.set(i, new Credit(planned, actual, credit.amount() - used));
            amount -= used;
        }
        if (amount != 0) throw new IllegalArgumentException("Native credit already spent");
    }

    private static IPatternDetails withoutRemainders(IPatternDetails pattern) {
        var delegates = pattern.getInputs();
        var inputs = new IPatternDetails.IInput[delegates.length];
        for (int i = 0; i < inputs.length; i++) {
            var delegate = delegates[i];
            inputs[i] = new IPatternDetails.IInput() {
                @Override public GenericStack[] getPossibleInputs() { return delegate.getPossibleInputs(); }
                @Override public long getMultiplier() { return delegate.getMultiplier(); }
                @Override public boolean isValid(AEKey key, Level level) { return delegate.isValid(key, level); }
                @Override public AEKey getRemainingKey(AEKey key) { return null; }
            };
        }
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return pattern.getDefinition(); }
            @Override public IInput[] getInputs() { return inputs; }
            @Override public List<GenericStack> getOutputs() { return pattern.getOutputs(); }
        };
    }

    private static Map<AEKey, Long> snapshot(KeyCounter counter) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var entry : counter) {
            if (entry.getLongValue() < 0) throw new IllegalArgumentException("Negative CPU stock");
            if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    public static void write(ValueOutput jobData, State state) {
        var data = jobData.child(TAG_NAME);
        data.store("identity", UUIDUtil.CODEC, state.identity);
        data.putBoolean("paid", state.paid);
        data.putBoolean("invalid", state.invalid);
        data.putLong("lastCommitTick", state.lastCommitTick);
        data.putLong("committedOperations", state.committedOperations);
        var tasks = data.childrenList("tasks");
        state.remaining.forEach((pattern, count) -> {
            var task = tasks.addChild();
            pattern.getDefinition().toTag(task.child("pattern"));
            task.store("patternMapMarkers", CompoundTag.CODEC, RipperMapSerialization.markers(pattern.getDefinition()));
            task.putLong("remaining", count);
        });
        var credits = data.childrenList("credits");
        for (var credit : state.credits) {
            var record = credits.addChild();
            credit.planned().toTagGeneric(record.child("planned"));
            credit.actual().toTagGeneric(record.child("actual"));
            record.store("plannedMapMarkers", CompoundTag.CODEC, RipperMapSerialization.markers(credit.planned()));
            record.store("actualMapMarkers", CompoundTag.CODEC, RipperMapSerialization.markers(credit.actual()));
            record.putLong("amount", credit.amount());
        }
    }

    @Nullable
    public static State read(ValueInput jobData, Level level) {
        var stored = jobData.child(TAG_NAME);
        if (stored.isEmpty()) return null;
        var data = stored.get();
        var state = new State(data.read("identity", UUIDUtil.CODEC).orElseGet(UUID::randomUUID));
        state.paid = data.getBooleanOr("paid", false);
        state.invalid = data.getBooleanOr("invalid", false);
        state.lastCommitTick = data.getLongOr("lastCommitTick", 0);
        state.committedOperations = data.getLongOr("committedOperations", 0);
        try {
            for (var task : data.childrenListOrEmpty("tasks")) {
                var restoredDefinition = RipperMapSerialization.restoreKey(
                        AEItemKey.fromTag(task.childOrEmpty("pattern")),
                        task.read("patternMapMarkers", CompoundTag.CODEC).orElseGet(CompoundTag::new));
                if (!(restoredDefinition instanceof AEItemKey definition)) {
                    throw new IllegalArgumentException("Invalid saved native pattern definition");
                }
                var pattern = PatternDetailsHelper.decodePattern(definition, level);
                long count = task.getLongOr("remaining", 0);
                if (pattern == null || !CraftingRipperPatterns.supportsPattern(pattern, level)
                        || count <= 0 || state.remaining.put(pattern, count) != null) {
                    throw new IllegalArgumentException("Invalid saved native task");
                }
            }
            for (var record : data.childrenListOrEmpty("credits")) {
                var planned = RipperMapSerialization.restoreKey(
                        AEKey.fromTagGeneric(record.childOrEmpty("planned")),
                        record.read("plannedMapMarkers", CompoundTag.CODEC).orElseGet(CompoundTag::new));
                var actual = RipperMapSerialization.restoreKey(
                        AEKey.fromTagGeneric(record.childOrEmpty("actual")),
                        record.read("actualMapMarkers", CompoundTag.CODEC).orElseGet(CompoundTag::new));
                long amount = record.getLongOr("amount", 0);
                if (planned == null || actual == null || amount <= 0) throw new IllegalArgumentException("Invalid saved native credit");
                addCredit(state.credits, planned, actual, amount);
            }
        } catch (RuntimeException invalidSave) {
            state.invalid = true;
        }
        return state;
    }

    public enum Result { PROGRESSED, WAITING, COMPLETED, INVALID }

    public static final class State {
        private final UUID identity;
        private final LinkedHashMap<IPatternDetails, Long> remaining = new LinkedHashMap<>();
        private final List<Credit> credits = new ArrayList<>();
        private boolean paid;
        private boolean invalid;
        private long lastCommitTick = Long.MIN_VALUE;
        private long committedOperations;
        private AEItemKey lastActualOutput;
        private IPatternDetails lastPattern;

        private State(UUID identity) { this.identity = identity; }
        public UUID identity() { return identity; }
        public boolean paid() { return paid; }
        public boolean invalid() { return invalid; }
        public long committedOperations() { return committedOperations; }
        public List<Credit> credits() { return List.copyOf(credits); }
        public Map<IPatternDetails, Long> remainingTasks() { return Map.copyOf(remaining); }
        @Nullable public AEItemKey lastActualOutput() { return lastActualOutput; }
        @Nullable public IPatternDetails lastPattern() { return lastPattern; }
    }

    public record Credit(AEKey planned, AEKey actual, long amount) {}
    private record Allocation(AEKey actual, long amount) {}
    private record Prepared(IPatternDetails pattern, Map<AEKey, Long> consumed, List<Credit> credits,
            List<ItemStack> grid, ItemStack output, List<ItemStack> plannedRemainders) {}
}
