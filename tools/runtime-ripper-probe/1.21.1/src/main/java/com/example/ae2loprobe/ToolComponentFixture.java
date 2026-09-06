package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEItems;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.recipes.quartzcutting.QuartzCuttingRecipe;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.CraftingRipperPatterns;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

/** Two real native CPU orders; the remainder observer never invokes damage or changes RNG. */
public final class ToolComponentFixture {
    public static final boolean ENABLED = "tool_components".equals(System.getProperty("ae2lo.probe.chain"));
    private static final long OPERATIONS = 3;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> runs = new ArrayList<>(), executions = new ArrayList<>(), callbacks = new ArrayList<>();
    private static ServerLevel level;
    private static IGrid grid;
    private static CraftingRipperBlockEntity ripper;
    private static CraftingCPUCluster cpu;
    private static IPatternDetails pattern;
    private static Future<ICraftingPlan> future;
    private static ICraftingLink link;
    private static ItemStack plainKnife, actualKnife;
    private static AEItemKey target;
    private static Path output;
    private static int stage, round = 1, cpuCalls, ripCalls, executionRemainders, callRemainders;
    private static long stageTick, submitTick, firstExecuteTick, executeTick, lastCommitTick = Long.MIN_VALUE, operationsBefore, committed;
    private static long order;
    private static double energyBefore, fee;
    private static boolean finished, passed, inExecute, completed, sawNative;
    private static Map<AEKey, Long> beforeStock;
    private static final Map<AEKey, Long> callbackToolBalance = new LinkedHashMap<>();
    private static UUID nativeIdentity;

    private ToolComponentFixture() {}

    public static void begin(ServerLevel world, IGrid network, CraftingRipperBlockEntity block, Path evidence) {
        if (level != null) return;
        level = world; grid = network; ripper = block; output = evidence.resolve("tool-components-report.json");
        try {
            require(level.getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-"), "Isolated probe world required");
            report.put("minecraft", "1.21.1"); report.put("operationsPerOrder", OPERATIONS);
            report.put("boundary", "Real recipe, native AE2 calculation/submission and CPU stock. Remainder observations are separated into production execute and outside execute; no extra remainder or RNG calls.");
            report.put("runs", runs); report.put("executions", executions); report.put("remainderCallbacks", callbacks);
            plainKnife = AEItems.CERTUS_QUARTZ_KNIFE.stack();
            require(plainKnife.isDamageableItem() && plainKnife.getMaxDamage() > OPERATIONS, "A real durable quartz knife is required");
            var frame = frame(plainKnife);
            var input = CraftingInput.of(3, 3, Arrays.asList(frame));
            var holder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            require(holder.value() instanceof QuartzCuttingRecipe && holder.value().matches(input, level), "Expected live QuartzCuttingRecipe");
            var assembled = holder.value().assemble(input, level.registryAccess());
            require(!assembled.isEmpty(), "Live quartz output is empty");
            target = AEItemKey.of(assembled); order = Math.multiplyExact(OPERATIONS, assembled.getCount());
            var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, frame, assembled, true, false);
            pattern = PatternDetailsHelper.decodePattern(encoded, level);
            require(pattern != null && target.equals(pattern.getPrimaryOutput().what()), "Native encoding failed");
            report.put("recipeId", holder.id().toString()); report.put("target", describe(assembled)); report.put("orderPerRun", order);
            report.put("encodedPlainKnife", describe(plainKnife)); report.put("allowSubstitute", true);
            ripper.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            for (int slot = 0; slot < ripper.getLogic().getPatternInv().size(); slot++) ripper.getLogic().getPatternInv().setItemDirect(slot, ItemStack.EMPTY);
            ripper.getLogic().getPatternInv().setItemDirect(0, encoded);
            prepareRound();
        } catch (Throwable failure) { fail(failure); }
    }

    private static void prepareRound() {
        actualKnife = plainKnife.copy();
        if (round == 2) actualKnife.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        insert(AEItemKey.of(actualKnife), OPERATIONS); insert(AEItemKey.of(Items.IRON_INGOT), OPERATIONS);
        require(!CraftingRipperPatterns.requiresNativeExecution(pattern, level), "Encoded ordinary knife was incorrectly classified as random");
        require(CraftingRipperPatterns.requiresNativeExecution(pattern, List.of(AEItemKey.of(actualKnife)), level) == (round == 2),
                "Actual selected tool components were classified incorrectly");
        transition(1, "tool_pattern_publication_wait"); writeUnchecked();
    }

    public static void tick() {
        if (level == null || finished) return;
        try {
            if (stage == 1 && elapsed() >= 40) {
                require(!grid.getCraftingService().getCraftingFor(target).isEmpty(), "Tool pattern was not published");
                cpu = grid.getCraftingService().getCpus().stream().filter(candidate -> !candidate.isBusy())
                        .filter(CraftingCPUCluster.class::isInstance).map(CraftingCPUCluster.class::cast).findFirst().orElseThrow();
                future = grid.getCraftingService().beginCraftingCalculation(level, () -> new MachineSource(ripper), target, order,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                transition(2, "tool_native_planning");
            } else if (stage == 2) {
                if (future.isDone()) submit(future.get());
                else require(elapsed() < 400, "Tool native planning timed out");
            } else if (stage == 3) {
                require(!link.isCanceled(), "Tool order canceled");
                if (!cpu.craftingLogic.hasJob() && !cpu.isBusy() && (link.isStandalone() || link.isDone())) transition(4, "tool_return_settling");
                else require(elapsed() < 400, "Tool native order timed out");
            } else if (stage == 4 && elapsed() >= 10) {
                verifyRun();
                if (round == 1) {
                    // Remove only this completed test's output and knives before the independent second order.
                    var cleanup = stock();
                    for (var entry : cleanup.entrySet()) if (entry.getKey().equals(target) || isKnife(entry.getKey())) {
                        require(grid.getStorageService().getInventory().extract(entry.getKey(), entry.getValue(), Actionable.MODULATE,
                                new MachineSource(ripper)) == entry.getValue(), "Could not isolate next tool order");
                    }
                    round = 2; prepareRound();
                } else { passed = true; finished = true; transition(5, "tool_components_pass"); writeUnchecked(); }
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void submit(ICraftingPlan plan) {
        require(plan != null && !plan.simulation() && plan.missingItems().isEmpty(), "Native tool plan reported missing materials");
        require(plan.bytes() <= cpu.getAvailableStorage(), "Physical CPU cannot hold the tool order");
        require(plan.patternTimes().size() == 1 && plan.patternTimes().values().iterator().next() == OPERATIONS, "Tool order must use exactly three real recipe applications");
        beforeStock = stock();
        require(beforeStock.getOrDefault(AEItemKey.of(actualKnife), 0L) == OPERATIONS
                && beforeStock.getOrDefault(AEItemKey.of(Items.IRON_INGOT), 0L) == OPERATIONS
                && beforeStock.getOrDefault(target, 0L) == 0, "Unexpected finite tool stock");
        if (round == 2) require(beforeStock.getOrDefault(AEItemKey.of(plainKnife), 0L) == 0, "Plain tool leaked into enchanted-only case");
        report.put("plan" + round, Map.of("mode", mode(), "bytes", plan.bytes(), "used", describe(plan.usedItems()),
                "actualKnife", describe(actualKnife), "nativeRequired", CraftingRipperExecutor.requiresNativePlan(plan, level)));
        require(CraftingRipperExecutor.requiresNativePlan(plan, level) == (round == 2), "Submitted actual plan picked the wrong execution mode");
        cpuCalls = 0; ripCalls = 0; executionRemainders = 0; committed = 0; fee = 0;
        completed = false; sawNative = false; nativeIdentity = null; lastCommitTick = Long.MIN_VALUE;
        callbackToolBalance.clear(); callbackToolBalance.put(AEItemKey.of(actualKnife), OPERATIONS);
        submitTick = level.getGameTime(); transition(3, "tool_native_cpu_executing");
        var submitted = grid.getCraftingService().submitJob(plan, null, cpu, false, new MachineSource(ripper));
        require(submitted.successful(), "Native tool submit rejected: " + submitted.errorCode() + " " + submitted.errorDetail());
        link = cpu.craftingLogic.getLastLink(); require(link != null, "Tool submit did not create a real link"); writeUnchecked();
    }

    public static void onCpuTick(CraftingCpuLogic logic) {
        if (ENABLED && stage == 3 && cpu != null && logic == cpu.craftingLogic && logic.hasJob()) cpuCalls++;
    }
    public static void beforeRipper(IGrid executingGrid, IEnergyService energy, ScheduledCraftingJob job, Level world) {
        if (!ENABLED || executingGrid != grid || stage != 3) return;
        inExecute = true; callRemainders = 0; ripCalls++; energyBefore = available(energy); executeTick = world.getGameTime();
        if (ripCalls == 1) firstExecuteTick = executeTick;
        var state = job.ae2lightoptimizer$getNativeState(); operationsBefore = state == null ? 0 : state.committedOperations();
    }
    public static void afterRipper(IGrid executingGrid, IEnergyService energy, ListCraftingInventory inventory,
            ScheduledCraftingJob job, Level world, CraftingRipperExecutor.Result result) {
        if (!ENABLED || executingGrid != grid || !inExecute) return;
        inExecute = false;
        try {
            double delta = energyBefore - available(energy); fee += delta;
            boolean nativeMode = job.ae2lightoptimizer$isNativeRipperJob(); sawNative |= nativeMode;
            var state = job.ae2lightoptimizer$getNativeState(); long operationsAfter = state == null ? 0 : state.committedOperations();
            var row = new LinkedHashMap<String, Object>();
            row.put("round", round); row.put("tick", executeTick); row.put("result", result.name()); row.put("feeAE", delta);
            row.put("nativeContinuation", nativeMode); row.put("committedBefore", operationsBefore); row.put("committedAfter", operationsAfter);
            row.put("enchantedRemainderCallbacks", callRemainders); row.put("cpuStock", describe(inventory.list)); executions.add(row);
            require(executeTick == world.getGameTime(), "One production execute crossed a server tick");
            if (round == 2) {
                require(nativeMode && state != null && !state.invalid(), "Enchanted actual tool did not use valid native continuation");
                if (nativeIdentity == null) nativeIdentity = state.identity();
                require(nativeIdentity.equals(state.identity()), "Native tool state identity changed");
                long increment = operationsAfter - operationsBefore;
                require(increment >= 0 && increment <= 1 && callRemainders == increment, "Random remainder was evaluated outside the single committed operation");
                if (increment == 1) {
                    require(lastCommitTick < executeTick, "Random tool operations were batched in one tick"); lastCommitTick = executeTick;
                    var allTools = toolStock(inventory.list);
                    for (var entry : toolStock(grid.getStorageService().getInventory().getAvailableStacks()).entrySet()) add(allTools, entry.getKey(), entry.getValue());
                    require(allTools.equals(callbackToolBalance), "Actual CPU/network tool keys differ from real callback remainders");
                }
                require(Math.abs(delta - (operationsBefore == 0 && increment == 1 ? 50 : 0)) < 0.00001, "Native tool fee was not charged exactly once");
                committed = operationsAfter;
            } else require(!nativeMode && callRemainders == 0, "Ordinary knife was incorrectly sent through random-tool continuation");
            completed |= result == CraftingRipperExecutor.Result.COMPLETED;
            writeUnchecked();
        } catch (Throwable failure) { fail(failure); }
    }

    /** Called only after the real QuartzCuttingRecipe callback has returned. */
    public static void observeRemainder(CraftingInput input, List<ItemStack> remainders) {
        if (!ENABLED || level == null || finished) return;
        try {
            for (int slot = 0; slot < input.size(); slot++) {
                var knife = input.getItem(slot);
                if (!knife.is(plainKnife.getItem()) || !knife.isEnchanted()) continue;
                var remaining = remainders.get(slot);
                callbacks.add(Map.of("round", round, "phase", inExecute ? "production_execute" : "outside_production_execute",
                        "tick", level.getGameTime(), "input", describe(knife), "remainder", describe(remaining)));
                if (inExecute) {
                    callRemainders++; executionRemainders++;
                    require(!remaining.isEmpty() && remaining.getCount() == 1 && remaining.is(knife.getItem()), "Unexpected knife break or duplicate remainder");
                    int damage = remaining.getDamageValue() - knife.getDamageValue();
                    require(damage >= 0 && damage <= 1 && normalized(knife).equals(normalized(remaining)), "Real native damage changed unrelated tool components");
                    add(callbackToolBalance, AEItemKey.of(knife), -1); add(callbackToolBalance, AEItemKey.of(remaining), 1);
                }
                break;
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void verifyRun() {
        var after = stock(); var knives = new LinkedHashMap<AEKey, Long>();
        long quantity = 0, damageTotal = 0;
        for (var entry : after.entrySet()) if (isKnife(entry.getKey())) {
            var item = ((AEItemKey) entry.getKey()).toStack();
            require(normalized(item).equals(normalized(actualKnife)), "Tool remainder lost enchantments or other exact components");
            quantity = Math.addExact(quantity, entry.getValue()); damageTotal += item.getDamageValue() * entry.getValue();
            knives.put(entry.getKey(), entry.getValue());
        }
        require(quantity == OPERATIONS && damageTotal >= 0 && damageTotal <= OPERATIONS, "Tool count or damage exceeded three legal operations");
        var expected = new LinkedHashMap<>(beforeStock);
        expected.remove(AEItemKey.of(actualKnife)); expected.remove(AEItemKey.of(Items.IRON_INGOT));
        expected.put(target, order); expected.putAll(knives);
        require(expected.equals(after), "Full finite inventory differs after tool order");
        require(completed && Math.abs(fee - 50) < 0.00001, "Tool order did not complete for exactly 50 AE");
        if (round == 1) require(!sawNative && ripCalls == 1 && cpuCalls == 1 && firstExecuteTick - submitTick <= 1
                && damageTotal == OPERATIONS, "Ordinary knife failed exact one-tick deterministic damage control");
        else require(sawNative && committed == OPERATIONS && executionRemainders == OPERATIONS
                && lastCommitTick - firstExecuteTick >= OPERATIONS - 1 && knives.equals(callbackToolBalance), "Random tool order bypassed real per-operation remainder callbacks");
        var waiting = new HashSet<AEKey>(); cpu.craftingLogic.getAllWaitingFor(waiting);
        require(!link.isCanceled() && (link.isStandalone() || link.isDone()) && !cpu.isBusy() && !cpu.craftingLogic.hasJob()
                && cpu.craftingLogic.getInventory().list.isEmpty() && waiting.isEmpty(), "Native CPU did not finish cleanly");
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", mode()); row.put("passed", true); row.put("outputCount", after.get(target)); row.put("remainingKnifeCount", quantity);
        row.put("totalActualKnifeDamage", damageTotal); row.put("executionFeeAE", fee); row.put("ripperCalls", ripCalls); row.put("cpuCalls", cpuCalls);
        row.put("nativeContinuation", sawNative); row.put("nativeCommittedOperations", committed); row.put("productionRandomRemainderCallbacks", executionRemainders);
        row.put("firstExecutionTick", firstExecuteTick); row.put("lastCommitTick", lastCommitTick); row.put("fullInventoryExact", true);
        row.put("standalone", link.isStandalone()); row.put("nativeDoneFlag", link.isDone()); row.put("cpuFinished", true); runs.add(row);
    }

    private static ItemStack[] frame(ItemStack knife) {
        var frame = new ItemStack[9]; Arrays.fill(frame, ItemStack.EMPTY); frame[0] = knife.copy(); frame[1] = new ItemStack(Items.IRON_INGOT); return frame;
    }
    private static AEItemKey normalized(ItemStack tool) { var copy = tool.copy(); copy.setDamageValue(0); return AEItemKey.of(copy); }
    private static boolean isKnife(AEKey key) { return key instanceof AEItemKey item && item.toStack().is(plainKnife.getItem()); }
    private static Map<AEKey, Long> toolStock(KeyCounter values) {
        var result = new LinkedHashMap<AEKey, Long>(); for (var entry : values) if (entry.getLongValue() > 0 && isKnife(entry.getKey())) result.put(entry.getKey(), entry.getLongValue()); return result;
    }
    private static void add(Map<AEKey, Long> values, AEKey key, long delta) {
        long value = Math.addExact(values.getOrDefault(key, 0L), delta); require(value >= 0, "Callback consumed an absent actual knife");
        if (value == 0) values.remove(key); else values.put(key, value);
    }
    private static void insert(AEItemKey key, long amount) { require(grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, new MachineSource(ripper)) == amount, "Finite insertion truncated"); }
    private static Map<AEKey, Long> stock() {
        var result = new LinkedHashMap<AEKey, Long>(); for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue()); return result;
    }
    private static List<Map<String, Object>> describe(KeyCounter values) {
        var result = new ArrayList<Map<String, Object>>(); for (var entry : values) if (entry.getLongValue() != 0) result.add(Map.of("key", entry.getKey() instanceof AEItemKey item ? describe(item.toStack()) : entry.getKey().toString(), "amount", entry.getLongValue())); return result;
    }
    private static Map<String, Object> describe(ItemStack stack) { return Map.of("item", stack.getItem().toString(), "count", stack.getCount(), "damage", stack.getDamageValue(), "components", stack.getComponentsPatch().toString()); }
    private static String mode() { return round == 1 ? "ordinary_knife_instant" : "unbreaking_actual_substitute_native"; }
    private static double available(IEnergyService energy) { return energy.extractAEPower(1_000_000_000, Actionable.SIMULATE, PowerMultiplier.ONE); }
    private static long elapsed() { return level.getGameTime() - stageTick; }
    private static void transition(int next, String status) { stage = next; stageTick = level.getGameTime(); ProbeState.phase = "tool_components"; ProbeState.status = status; }
    public static boolean isFinished() { return finished; }
    public static boolean passed() { return passed; }
    public static Map<String, Object> summary() { return Map.of("finished", finished, "passed", passed, "runs", runs, "failure", report.getOrDefault("failure", "")); }
    private static void writeUnchecked() {
        try { report.put("finished", finished); report.put("passed", passed); Files.createDirectories(output.getParent()); Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static void fail(Throwable failure) {
        finished = true; passed = false; report.put("failure", failure.toString());
        try {
            if (grid != null) report.put("failureStock", describe(grid.getStorageService().getInventory().getAvailableStacks()));
            if (cpu != null) report.put("failureCpu", Map.of("hasJob", cpu.craftingLogic.hasJob(), "busy", cpu.isBusy(), "stock", describe(cpu.craftingLogic.getInventory().list)));
            writeUnchecked();
        } catch (Exception recordingFailure) { report.put("recordingFailure", recordingFailure.toString()); }
        ProbeState.status = "tool_components_failed: " + failure;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
