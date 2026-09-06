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
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Real native AE2 orders against item keys whose visible data and deep custom data must remain exact. */
public final class ExactComponentFixture {
    public static final boolean ENABLED = "exact_components".equals(System.getProperty("ae2lo.probe.chain"));
    private static final long ORDER = 3000;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> runs = new ArrayList<>();
    private static final List<Map<String, Object>> executions = new ArrayList<>();
    private static ServerLevel level;
    private static IGrid grid;
    private static CraftingRipperBlockEntity ripper;
    private static CraftingCPUCluster cpu;
    private static Future<ICraftingPlan> future;
    private static ICraftingLink link;
    private static ItemStack sourceA, sourceB, partialC, outputA, outputB, outputC;
    private static AEItemKey keyA, keyB, keyC, target;
    private static Path output;
    private static int stage, round = 1, ripCalls, cpuCalls;
    private static long stageTick, submitTick, executeTick;
    private static double energyBefore, measuredFee;
    private static boolean inExecute, completed, finished, passed;
    private static Map<AEKey, Long> beforeStock;

    private ExactComponentFixture() {}

    public static void begin(ServerLevel world, IGrid network, CraftingRipperBlockEntity block, Path evidence) {
        if (level != null) return;
        level = world; grid = network; ripper = block;
        output = evidence.resolve("exact-components-report.json");
        try {
            require(level.getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-"), "Isolated probe world required");
            report.put("scenario", "Wrong full-components and deep partial-data books rejected; two real orders of 3000 exact book clones");
            report.put("minecraft", "26.1.2");
            report.put("orderPerRun", ORDER);
            report.put("boundary", "Native recipe matches/assemble, real network stock, native AE2 planning/submission/link/CPU execution. Detached extraction checks are separately labelled.");
            report.put("runs", runs);
            report.put("executions", executions);
            sourceA = book("A", 9_007_199_254_740_993L, false);
            sourceB = book("B", 9_007_199_254_740_997L, false);
            partialC = sourceA.copy();
            partialC.set(DataComponents.CUSTOM_DATA, payload(9_007_199_254_740_994L, true));
            keyA = AEItemKey.of(sourceA); keyB = AEItemKey.of(sourceB); keyC = AEItemKey.of(partialC);
            require(!keyA.equals(keyB) && !keyA.equals(keyC) && !keyB.equals(keyC), "Exact witnesses collapsed to one key");
            require(Objects.equals(sourceA.get(DataComponents.CUSTOM_NAME), partialC.get(DataComponents.CUSTOM_NAME))
                    && Objects.equals(sourceA.get(DataComponents.WRITTEN_BOOK_CONTENT), partialC.get(DataComponents.WRITTEN_BOOK_CONTENT)),
                    "Partial-data witness must differ only in deep CUSTOM_DATA");
            var frame = frame(sourceA);
            var input = CraftingInput.of(3, 3, Arrays.asList(frame));
            var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            require(holder.value().matches(input, level), "Live book-copy recipe rejected A");
            outputA = holder.value().assemble(input);
            outputB = holder.value().assemble(CraftingInput.of(3, 3, Arrays.asList(frame(sourceB))));
            outputC = holder.value().assemble(CraftingInput.of(3, 3, Arrays.asList(frame(partialC))));
            require(outputA.is(Items.WRITTEN_BOOK) && outputA.getCount() == 1 && !outputB.isEmpty() && !outputC.isEmpty(), "Unexpected book-copy recipe");
            target = AEItemKey.of(outputA);
            require(!target.equals(keyA) && !target.equals(AEItemKey.of(outputB)) && !target.equals(AEItemKey.of(outputC)), "Derived book output lost exact source distinctions");
            require(Objects.equals(outputA.get(DataComponents.CUSTOM_DATA), sourceA.get(DataComponents.CUSTOM_DATA))
                    && Objects.equals(outputA.get(DataComponents.CUSTOM_NAME), sourceA.get(DataComponents.CUSTOM_NAME)),
                    "Native book-copy recipe does not preserve source custom data");
            var encoded = PatternDetailsHelper.encodeCraftingPattern(holder, frame, outputA, false, false);
            var expectedPattern = PatternDetailsHelper.decodePattern(encoded, level);
            require(expectedPattern != null && target.equals(expectedPattern.getPrimaryOutput().what()), "Native pattern round trip changed target");
            report.put("recipeId", holder.id().identifier().toString());
            report.put("sourceA", describe(sourceA));
            report.put("wrongFullComponentsB", describe(sourceB));
            report.put("wrongDeepPartialDataC", describe(partialC));
            report.put("expectedOutputA", describe(outputA));
            report.put("CasePrecise", true);
            ripper.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            for (int slot = 0; slot < ripper.getLogic().getPatternInv().size(); slot++) ripper.getLogic().getPatternInv().setItemDirect(slot, ItemStack.EMPTY);
            ripper.getLogic().getPatternInv().setItemDirect(0, encoded);
            insert(keyB, 1); insert(keyC, 1); insert(AEItemKey.of(Items.WRITABLE_BOOK), ORDER);
            require(stock().getOrDefault(keyA, 0L) == 0, "A must be absent from negative case");
            transition(1, "exact_manual_publication_wait");
            write();
        } catch (Throwable failure) { fail(failure); }
    }

    public static void tick() {
        if (level == null || finished) return;
        try {
            if (stage == 1 && elapsed() >= 40) {
                inspectPublished("manual");
                cpu = idleCpu();
                future = calculate();
                transition(2, "exact_missing_A_planning");
            } else if (stage == 2 && ready()) {
                rejectMissing(future.get());
                insert(keyA, 1);
                transition(3, "exact_A_inserted_settling");
            } else if (stage == 3 && elapsed() >= 30) {
                future = calculate();
                transition(4, "exact_manual_planning");
            } else if (stage == 4 && ready()) {
                submit(future.get());
            } else if (stage == 5) {
                require(!link.isCanceled(), "Real exact-component order was canceled");
                // Standalone AE2 links have no nexus and do not become isDone after native finishJob.
                if (!cpu.craftingLogic.hasJob() && !cpu.isBusy()
                        && (link.isStandalone() || link.isDone())) transition(6, "exact_order_return_settling");
                else require(elapsed() < 400, "Exact-component native order timed out");
            } else if (stage == 6 && elapsed() >= 10) {
                verifyRun();
                if (round == 1) {
                    long removed = grid.getStorageService().getInventory().extract(target, ORDER, Actionable.MODULATE, new MachineSource(ripper));
                    require(removed == ORDER, "Could not isolate second order from first order outputs");
                    report.put("firstRunOutputsRemovedOnlyForIndependentSecondOrder", removed);
                    for (int slot = 0; slot < ripper.getLogic().getPatternInv().size(); slot++) ripper.getLogic().getPatternInv().setItemDirect(slot, ItemStack.EMPTY);
                    ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
                    insert(AEItemKey.of(Items.WRITABLE_BOOK), ORDER);
                    round = 2;
                    transition(7, "exact_loop_card_catalog_wait");
                } else {
                    passed = true; finished = true;
                    transition(9, "exact_components_pass");
                }
                write();
            } else if (stage == 7 && elapsed() >= 120) {
                require(ripper.isLoopCardInstalled(), "Loop Card did not install");
                for (var item : ripper.getLogic().getPatternInv()) require(item.isEmpty(), "Manual pattern remains in automatic-mode test");
                inspectPublished("loop_card");
                require(!grid.getCraftingService().getCraftingFor(AEItemKey.of(outputB)).isEmpty()
                        && !grid.getCraftingService().getCraftingFor(AEItemKey.of(outputC)).isEmpty(), "Automatic catalog did not preserve B/C output variants");
                report.put("loopCardPublishesDistinctA_B_C_Outputs", true);
                future = calculate();
                transition(4, "exact_loop_card_planning");
                write();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    private static void inspectPublished(String mode) {
        var patterns = grid.getCraftingService().getCraftingFor(target);
        require(!patterns.isEmpty(), mode + " did not publish exact A output");
        var checks = new ArrayList<Map<String, Object>>();
        for (var pattern : patterns) {
            require(target.equals(pattern.getPrimaryOutput().what()), "Native output lookup returned a different component key");
            for (var wrong : List.of(sourceB, partialC)) {
                var local = new ListCraftingInventory(ignored -> {});
                local.insert(AEItemKey.of(wrong), 1, Actionable.MODULATE);
                local.insert(AEItemKey.of(Items.WRITABLE_BOOK), 8, Actionable.MODULATE);
                var selected = CraftingCpuHelper.extractPatternInputs(pattern, local, level, new KeyCounter(), new KeyCounter());
                checks.add(Map.of("patternClass", pattern.getClass().getName(), "wrongInput", describe(wrong),
                        "detachedSelectionRejected", selected == null));
                require(selected == null, mode + " actual published pattern accepted unsafe full/partial component substitution");
            }
        }
        report.put(mode + "ActualPublishedExactSelectionChecks", checks);
        report.put(mode + "PublishedPatternCount", patterns.size());
    }

    private static Future<ICraftingPlan> calculate() {
        return grid.getCraftingService().beginCraftingCalculation(level, () -> new MachineSource(ripper), target, ORDER,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    private static boolean ready() {
        if (future.isDone()) return true;
        require(elapsed() < 400, "Native exact-component planning timed out");
        return false;
    }

    private static void rejectMissing(ICraftingPlan plan) {
        require(plan != null && plan.missingItems().get(keyA) > 0, "Only wrong B/C were stored, but native planner did not report precise A missing");
        var before = stock();
        double beforeEnergy = available(grid.getEnergyService());
        int callsBefore = ripCalls;
        boolean rejected = false;
        String rejection;
        try {
            var result = grid.getCraftingService().submitJob(plan, null, cpu, false, new MachineSource(ripper));
            rejected = !result.successful();
            if (!rejected) link = cpu.craftingLogic.getLastLink();
            rejection = String.valueOf(result.errorCode());
        } catch (RuntimeException nativeRefusal) {
            rejected = true;
            rejection = nativeRefusal.getClass().getSimpleName() + ": " + nativeRefusal.getMessage();
        }
        double afterEnergy = available(grid.getEnergyService());
        require(rejected && !cpu.isBusy(), "Native API accepted the missing exact-component order");
        require(before.equals(stock()) && ripCalls == callsBefore && Math.abs(beforeEnergy - afterEnergy) < 0.00001,
                "Rejected exact-component order changed materials or execution energy");
        report.put("missingAOnlyWrongB_CStored", Map.of("nativeMissingA", plan.missingItems().get(keyA),
                "missing", describe(plan.missingItems()), "simulation", plan.simulation(), "nativeSubmitRejected", true,
                "rejection", rejection, "stockUnchanged", true, "executionCalls", ripCalls - callsBefore,
                "immediateEnergyDeltaAE", beforeEnergy - afterEnergy));
        report.put("partialDataUnsafeSelectionRejected", true);
    }

    private static void submit(ICraftingPlan plan) {
        require(plan != null && !plan.simulation() && plan.missingItems().isEmpty(), "Correct A stock still produced missing materials");
        require(plan.bytes() <= cpu.getAvailableStorage(), "Physical CPU too small for exact-component order");
        require(plan.usedItems().get(keyB) == 0 && plan.usedItems().get(keyC) == 0, "Native planner selected a wrong component variant");
        beforeStock = stock();
        require(beforeStock.getOrDefault(keyA, 0L) == 1 && beforeStock.getOrDefault(keyB, 0L) == 1
                && beforeStock.getOrDefault(keyC, 0L) == 1 && beforeStock.getOrDefault(target, 0L) == 0
                && beforeStock.getOrDefault(AEItemKey.of(Items.WRITABLE_BOOK), 0L) == ORDER, "Wrong finite pre-order inventory");
        var planReport = new LinkedHashMap<String, Object>();
        planReport.put("mode", round == 1 ? "manual" : "loop_card");
        planReport.put("bytes", plan.bytes());
        planReport.put("used", describe(plan.usedItems()));
        planReport.put("patterns", plan.patternTimes().entrySet().stream().map(entry -> Map.of(
                "class", entry.getKey().getClass().getName(), "operations", entry.getValue(),
                "output", entry.getKey().getPrimaryOutput().toString())).toList());
        report.put("plan" + round, planReport);
        ripCalls = 0; cpuCalls = 0; measuredFee = 0; completed = false;
        submitTick = level.getGameTime();
        transition(5, "exact_native_cpu_executing");
        var submitted = grid.getCraftingService().submitJob(plan, null, cpu, false, new MachineSource(ripper));
        require(submitted.successful(), "Native exact-component submit rejected: " + submitted.errorCode() + " " + submitted.errorDetail());
        link = cpu.craftingLogic.getLastLink();
        require(link != null, "Native submission did not create a real link");
        writeUnchecked();
    }

    public static void onCpuTick(CraftingCpuLogic logic) {
        if (ENABLED && stage == 5 && cpu != null && logic == cpu.craftingLogic && logic.hasJob()) cpuCalls++;
    }

    public static void beforeRipper(IGrid executingGrid, IEnergyService energy, ScheduledCraftingJob job, Level world) {
        if (!ENABLED || executingGrid != grid || stage != 5) return;
        inExecute = true;
        ripCalls++;
        energyBefore = available(energy);
        executeTick = world.getGameTime();
    }

    public static void afterRipper(IGrid executingGrid, IEnergyService energy, ListCraftingInventory inventory,
            ScheduledCraftingJob job, Level world, CraftingRipperExecutor.Result result) {
        if (!ENABLED || executingGrid != grid || !inExecute) return;
        inExecute = false;
        double after = available(energy);
        measuredFee += energyBefore - after;
        completed = result == CraftingRipperExecutor.Result.COMPLETED;
        executions.add(Map.of("round", round, "tickBefore", executeTick, "tickAfter", world.getGameTime(),
                "result", result.name(), "energyBeforeAE", energyBefore, "energyAfterAE", after,
                "isolatedExecutionFeeAE", energyBefore - after, "nativeContinuation", job.ae2lightoptimizer$isNativeRipperJob(),
                "cpuStockAfterExecute", describe(inventory.list)));
        if (executeTick != world.getGameTime()) fail(new IllegalStateException("Exact-component execution crossed a tick"));
    }

    private static void verifyRun() {
        var expected = new LinkedHashMap<>(beforeStock);
        expected.remove(AEItemKey.of(Items.WRITABLE_BOOK));
        expected.merge(target, ORDER, Math::addExact);
        require(expected.equals(stock()), "Full exact-key final inventory differs: wrong NBT selected or unexpected output");
        require(completed && ripCalls == 1 && cpuCalls == 1 && executeTick - submitTick <= 1,
                "Exact-component order did not finish in exactly one CPU/ripper call within one tick");
        require(Math.abs(measuredFee - 50) < 0.00001, "Exact-component order execution fee differs from 50 AE");
        require((link.isStandalone() || link.isDone()) && !link.isCanceled() && !cpu.craftingLogic.hasJob()
                && !cpu.isBusy() && cpu.craftingLogic.getInventory().list.isEmpty()
                && cpu.craftingLogic.getWaitingFor(target) == 0, "Native CPU did not finish and return the exact output");
        var waiting = new HashSet<AEKey>();
        cpu.craftingLogic.getAllWaitingFor(waiting);
        require(waiting.isEmpty(), "Native CPU still waits for other outputs after the exact-component order");
        var row = new LinkedHashMap<String, Object>();
        row.put("mode", round == 1 ? "manual" : "loop_card");
        row.put("passed", true); row.put("outputFullKeyCount", stock().get(target));
        row.put("sourceASeed", stock().get(keyA)); row.put("wrongBUnconsumed", stock().get(keyB));
        row.put("partialCUnconsumed", stock().get(keyC)); row.put("ripperCalls", ripCalls);
        row.put("nativeCpuCalls", cpuCalls); row.put("executionFeeAE", measuredFee);
        row.put("submitTick", submitTick); row.put("executionTick", executeTick);
        row.put("nativeLinkDoneFlag", link.isDone()); row.put("nativeLinkStandalone", link.isStandalone());
        row.put("nativeLinkCanceled", link.isCanceled()); row.put("nativeCpuHasJob", cpu.craftingLogic.hasJob());
        row.put("nativeCpuFinished", true); row.put("fullInventoryExact", true);
        runs.add(row);
    }

    private static CraftingCPUCluster idleCpu() {
        return grid.getCraftingService().getCpus().stream().filter(candidate -> !candidate.isBusy())
                .filter(CraftingCPUCluster.class::isInstance).map(CraftingCPUCluster.class::cast).findFirst().orElseThrow();
    }

    private static ItemStack book(String name, long exact, boolean partial) {
        var item = new ItemStack(Items.WRITTEN_BOOK);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("AE2LO CasePrecise " + name));
        item.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("CasePrecise " + name),
                "AE2LO-" + name, 0, List.of(Filterable.passThrough(Component.literal("Component " + name + " exact clone"))), true));
        item.set(DataComponents.CUSTOM_DATA, payload(exact, partial));
        return item;
    }

    private static CustomData payload(long exact, boolean partial) {
        var data = new CompoundTag();
        var nested = new CompoundTag();
        var deepest = new CompoundTag();
        deepest.putLong("ExactLongBeyondDouble", exact);
        deepest.putString("CasePrecise", "AbC_Keep_CASE");
        deepest.putIntArray("Vector", new int[]{3, 1, 4, 1, 5});
        if (!partial) deepest.putString("RequiredField", "must_exist");
        nested.put("deepest", deepest);
        data.put("payload", nested);
        data.putString("owner", "ae2lo-exact-components");
        return CustomData.of(data);
    }

    private static ItemStack[] frame(ItemStack source) {
        var items = new ItemStack[9];
        Arrays.fill(items, ItemStack.EMPTY);
        items[0] = source.copy(); items[1] = new ItemStack(Items.WRITABLE_BOOK);
        return items;
    }

    private static void insert(AEItemKey key, long amount) {
        require(grid.getStorageService().getInventory().insert(key, amount, Actionable.MODULATE, new MachineSource(ripper)) == amount,
                "Finite exact-key insertion was truncated");
    }

    private static Map<AEKey, Long> stock() {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var entry : grid.getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getLongValue() != 0) result.put(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    private static List<Map<String, Object>> describe(KeyCounter values) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : values) if (entry.getLongValue() != 0) result.add(Map.of("key",
                entry.getKey() instanceof AEItemKey item ? describe(item.toStack()) : entry.getKey().toString(), "amount", entry.getLongValue()));
        return result;
    }

    private static Map<String, Object> describe(ItemStack stack) {
        return Map.of("item", stack.getItem().toString(), "count", stack.getCount(), "components", stack.getComponentsPatch().toString(),
                "customData", stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().toString());
    }

    private static double available(IEnergyService energy) { return energy.extractAEPower(1_000_000_000, Actionable.SIMULATE, PowerMultiplier.ONE); }
    private static long elapsed() { return level.getGameTime() - stageTick; }
    private static void transition(int next, String status) {
        stage = next; stageTick = level.getGameTime(); ProbeState.phase = "exact_components"; ProbeState.status = status;
    }
    public static boolean isFinished() { return finished; }
    public static boolean passed() { return passed; }
    public static Map<String, Object> summary() {
        return Map.of("passed", passed, "finished", finished, "runs", runs,
                "partialDataUnsafeSelectionRejected", Boolean.TRUE.equals(report.get("partialDataUnsafeSelectionRejected")),
                "failure", report.getOrDefault("failure", ""));
    }
    private static void write() throws java.io.IOException {
        report.put("passed", passed); report.put("finished", finished);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void writeUnchecked() {
        try { write(); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static void fail(Throwable failure) {
        finished = true; passed = false;
        report.put("failure", failure.getClass().getName() + ": " + failure.getMessage());
        try {
            if (grid != null) report.put("failureStock", describe(grid.getStorageService().getInventory().getAvailableStacks()));
            if (cpu != null) report.put("failureCpu", Map.of("hasJob", cpu.craftingLogic.hasJob(),
                    "busy", cpu.isBusy(), "inventory", describe(cpu.craftingLogic.getInventory().list),
                    "targetWaiting", target == null ? 0 : cpu.craftingLogic.getWaitingFor(target)));
            if (link != null) report.put("failureLink", Map.of("standalone", link.isStandalone(),
                    "doneFlag", link.isDone(), "canceled", link.isCanceled()));
            write();
            if (cpu != null && cpu.craftingLogic.hasJob() && link != null && !link.isDone()
                    && !link.isCanceled() && cpu.craftingLogic.getLastLink() == link) {
                cpu.cancelJob();
                report.put("cleanupCanceledOwnProbeJob", true);
                write();
            }
        } catch (Exception recordingFailure) { report.put("recordingFailure", recordingFailure.toString()); }
        ProbeState.status = "exact_components_failed: " + failure;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
