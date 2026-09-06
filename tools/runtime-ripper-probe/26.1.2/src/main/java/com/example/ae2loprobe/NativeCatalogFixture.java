package com.example.ae2loprobe;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.example.ae2lightoptimizer.block.CraftingRipperBlockEntity;
import com.example.ae2lightoptimizer.item.ModItems;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/** Real native map-extension job; successful assembly alone does not satisfy this acceptance test. */
public final class NativeCatalogFixture {
    public static final boolean ENABLED = "native_catalog".equals(System.getProperty("ae2lo.probe.chain"));
    private static final long ORDER = 3;
    private static final boolean EXTERNAL_ASSEMBLER = Boolean.getBoolean("ae2lo.probe.native.assembler");
    private static final Set<Long> observedProducedCounts = new LinkedHashSet<>();
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> observations = new ArrayList<>();
    private static ServerLevel level;
    private static IGrid grid;
    private static CraftingRipperBlockEntity ripper;
    private static MolecularAssemblerBlockEntity assembler;
    private static CraftingCPUCluster cpu;
    private static Future<ICraftingPlan> future;
    private static ICraftingLink link;
    private static ItemStack sourceMap;
    private static AEItemKey target;
    private static String recipeId;
    private static Path output;
    private static long stageTick;
    private static long submittedTick;
    private static int stage;
    private static boolean finished;
    private static boolean passed;

    private NativeCatalogFixture() { }

    public static void begin(ServerLevel serverLevel, IGrid activeGrid, CraftingRipperBlockEntity block, Path evidence) {
        if (level != null) return;
        level = serverLevel;
        grid = activeGrid;
        ripper = block;
        output = evidence.resolve("native-catalog-report.json");
        try {
            if (!level.getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-")) {
                throw new IllegalStateException("Native catalog fixture requires its isolated probe world");
            }
            report.put("scenario", "Three real map extensions through automatic Loop Card catalog and CPU-owned native continuation");
            report.put("world", level.getServer().getWorldData().getLevelName());
            report.put("assertion", "Uncanceled CPU completed, waitingFor empty, and three distinct real map IDs with scale incremented and no MAP_POST_PROCESSING marker; observe one new map per native tick");
            report.put("instantExecutionRequired", false);
            report.put("expectedJobEnergyAE", 50);
            report.put("order", ORDER);
            report.put("externalAssembler", EXTERNAL_ASSEMBLER);
            report.put("observations", observations);
            BlockPos position = ripper.getBlockPos().above();
            if (!level.getBlockState(position).isAir()) throw new IllegalStateException("Probe assembler position occupied: " + position);
            if (EXTERNAL_ASSEMBLER) {
                level.setBlockAndUpdate(position, AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
                assembler = (MolecularAssemblerBlockEntity) level.getBlockEntity(position);
                report.put("assemblerPosition", position.toShortString());
            }
            sourceMap = MapItem.create(level, 0, 0, (byte) 0, true, false);
            var frame = new ItemStack[9];
            Arrays.setAll(frame, ignored -> new ItemStack(Items.PAPER));
            frame[4] = sourceMap.copy();
            var input = CraftingInput.of(3, 3, Arrays.asList(frame));
            var holder = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .orElseThrow(() -> new IllegalStateException("Live map extension recipe missing"));
            var assembled = holder.value().assemble(input);
            if (!assembled.has(DataComponents.MAP_POST_PROCESSING)) {
                throw new IllegalStateException("Fixture selected a recipe without world post-processing");
            }
            target = AEItemKey.of(assembled);
            recipeId = holder.id().identifier().toString();
            // Native encoding is only an independent expected-key oracle; no manual pattern is installed.
            var decoded = PatternDetailsHelper.decodePattern(
                    PatternDetailsHelper.encodeCraftingPattern(holder, frame, assembled, false, false), level);
            if (decoded == null || !target.equals(decoded.getPrimaryOutput().what())) {
                throw new IllegalStateException("Native AE2 pattern round trip changed the expected map key");
            }
            report.put("recipeId", recipeId);
            report.put("sourceMap", describe(sourceMap));
            report.put("expectedPrePostProcessKey", describe(target.toStack()));
            var storage = grid.getStorageService().getInventory();
            var source = new MachineSource(ripper);
            if (storage.insert(AEItemKey.of(sourceMap), ORDER, Actionable.MODULATE, source) != ORDER
                    || storage.insert(AEItemKey.of(Items.PAPER), 8 * ORDER, Actionable.MODULATE, source) != 8 * ORDER) {
                throw new IllegalStateException("Could not insert the finite map-extension inputs");
            }
            for (int i = 0; i < ripper.getLogic().getPatternInv().size(); i++) {
                ripper.getLogic().getPatternInv().setItemDirect(i, ItemStack.EMPTY);
            }
            ripper.getUpgrades().setItemDirect(0, ModItems.LOOP_CARD.get().getDefaultInstance());
            transition(1, "native_catalog_wait");
            snapshot("configured");
            write();
        } catch (Throwable failure) { fail(failure); }
    }

    public static void tick() {
        if (level == null || finished) return;
        try {
            if (stage == 1 && elapsed() >= 120) {
                if (EXTERNAL_ASSEMBLER && (!assembler.getMainNode().isActive() || assembler.getMainNode().getGrid() != grid)) {
                    throw new IllegalStateException("Adjacent real assembler did not join the powered probe grid");
                }
                var candidates = grid.getCraftingService().getCraftingFor(target);
                boolean published = candidates.stream().anyMatch(pattern -> {
                    var encoded = pattern.getDefinition().get(AEComponents.ENCODED_CRAFTING_PATTERN);
                    return encoded != null && recipeId.equals(encoded.recipeId().identifier().toString());
                });
                report.put("automaticCatalogPublishesTarget", published);
                if (!published) throw new IllegalStateException("Automatic catalog did not publish the exact native map-extension output");
                cpu = grid.getCraftingService().getCpus().stream().filter(candidate -> !candidate.isBusy())
                        .filter(CraftingCPUCluster.class::isInstance).map(CraftingCPUCluster.class::cast).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No idle native CPU available"));
                future = grid.getCraftingService().beginCraftingCalculation(level, () -> new MachineSource(ripper),
                        target, ORDER, CalculationStrategy.REPORT_MISSING_ITEMS);
                transition(2, "native_catalog_planning");
            } else if (stage == 2) {
                if (!future.isDone()) {
                    if (elapsed() > 400) throw new IllegalStateException("Native map planning timed out");
                    return;
                }
                var plan = future.get();
                if (plan == null || plan.simulation() || !plan.missingItems().isEmpty()) {
                    throw new IllegalStateException("Native map plan is missing material");
                }
                report.put("plan", Map.of("bytes", plan.bytes(), "patterns", plan.patternTimes().size(),
                        "used", describe(plan.usedItems()), "missing", describe(plan.missingItems())));
                if (plan.patternTimes().size() != 1 || plan.patternTimes().values().stream().mapToLong(Long::longValue).sum() != ORDER) {
                    throw new IllegalStateException("Expected exactly three native map-extension recipe applications");
                }
                submittedTick = level.getGameTime();
                report.put("energyBeforeSubmissionAE", availableEnergy());
                var submitted = grid.getCraftingService().submitJob(plan, null, cpu, false, new MachineSource(ripper));
                report.put("nativeSubmitSuccessful", submitted.successful());
                if (!submitted.successful()) throw new IllegalStateException("Native submit rejected: " + submitted.errorCode());
                link = cpu.craftingLogic.getLastLink();
                if (link == null) throw new IllegalStateException("Successful native CPU submission has no crafting link");
                transition(3, "native_catalog_executing");
                snapshot("submitted");
                write();
            } else if (stage == 3) {
                if (NativeContinuationAssertions.restoreCpuAtTickEnd(cpu, level)) {
                    link = cpu.craftingLogic.getLastLink();
                    if (link == null) throw new IllegalStateException("Restored native CPU lost its crafting link");
                    snapshot("complete_cpu_persistence_restored");
                }
                observeProducedMaps();
                if (elapsed() <= 10 || elapsed() % 20 == 0) snapshot("native_tick");
                if (link.isCanceled()) throw new IllegalStateException("Native map job was canceled before completion");
                if (completed()) {
                    transition(4, "native_catalog_return_settling");
                } else if (elapsed() >= 400) {
                    throw new IllegalStateException("Native map job still waits after 400 ticks; compare waitingFor key with produced map IDs");
                }
            } else if (stage == 4 && elapsed() >= 40) {
                snapshot("completion");
                report.put("nativeLinkDone", link.isDone());
                report.put("standaloneLink", link.isStandalone());
                report.put("completionEvidence", "Uncanceled standalone CPU idle, no waiting items, exact real products. Pinned AE2 markDone only signals a linked nexus.");
                report.put("nativeCpuIdle", !cpu.isBusy());
                report.put("elapsedNativeTicks", level.getGameTime() - submittedTick);
                report.put("energyAfterCompletionAE", availableEnergy());
                long validNewMaps = validCompletedMaps();
                report.put("validNewScaledMapCount", validNewMaps);
                long distinctIds = validMapEntries(grid.getStorageService().getInventory().getAvailableStacks()).keySet().stream()
                        .map(key -> key.get(DataComponents.MAP_ID)).distinct().count();
                var finalStock = grid.getStorageService().getInventory().getAvailableStacks();
                long remainingMaps = finalStock.get(AEItemKey.of(sourceMap));
                long remainingPaper = finalStock.get(AEItemKey.of(Items.PAPER));
                report.put("remainingSourceMaps", remainingMaps);
                report.put("remainingPaper", remainingPaper);
                report.put("distinctNewScaledMapIds", distinctIds);
                report.put("observedProducedCounts", observedProducedCounts);
                boolean multiTick = observedProducedCounts.containsAll(List.of(1L, 2L, 3L));
                report.put("nativeMultiTickProgressObserved", multiTick);
                if (!completed() || validNewMaps != ORDER || distinctIds != ORDER || !multiTick || remainingMaps != 0 || remainingPaper != 0 || cpu.craftingLogic.getWaitingFor(target) != 0) {
                    throw new IllegalStateException("Native map completion contract failed");
                }
                passed = true;
                finished = true;
                transition(5, "native_catalog_pass");
                write();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    public static boolean isFinished() { return finished; }
    private static boolean completed() {
        if (link == null || link.isCanceled() || cpu.isBusy()) return false;
        var waiting = new java.util.HashSet<AEKey>();
        cpu.craftingLogic.getAllWaitingFor(waiting);
        return waiting.isEmpty() && (link.isStandalone() || link.isDone());
    }
    public static boolean passed() { return passed; }
    public static Map<String, Object> summary() {
        var result = new LinkedHashMap<String, Object>();
        for (String key : List.of("passed", "automaticCatalogPublishesTarget", "nativeSubmitSuccessful", "nativeLinkDone",
                "nativeCpuIdle", "validNewScaledMapCount", "distinctNewScaledMapIds", "nativeMultiTickProgressObserved", "elapsedNativeTicks", "failure")) {
            if (report.containsKey(key)) result.put(key, report.get(key));
        }
        return result;
    }

    private static long validCompletedMaps() {
        return validMapEntries(grid.getStorageService().getInventory().getAvailableStacks()).values().stream().mapToLong(Long::longValue).sum();
    }

    private static Map<AEItemKey, Long> validMapEntries(KeyCounter inventory) {
        var result = new LinkedHashMap<AEItemKey, Long>();
        var originalId = sourceMap.get(DataComponents.MAP_ID);
        for (var entry : inventory) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key && key.is(Items.FILLED_MAP)) {
                var stack = key.toStack();
                var data = MapItem.getSavedData(stack, level);
                if (!Objects.equals(stack.get(DataComponents.MAP_ID), originalId)
                        && !stack.has(DataComponents.MAP_POST_PROCESSING) && data != null && data.scale == 1) {
                    result.put(key, entry.getLongValue());
                }
            }
        }
        return result;
    }

    private static void observeProducedMaps() {
        long count = validCompletedMaps();
        count += validMapEntries(cpu.craftingLogic.getInventory().list).values().stream().mapToLong(Long::longValue).sum();
        if (count > 0 && observedProducedCounts.add(count)) {
            observations.add(Map.of("phase", "new_real_map_count", "tick", level.getGameTime(), "count", count));
        }
    }

    private static void snapshot(String phase) {
        var data = new LinkedHashMap<String, Object>();
        data.put("phase", phase);
        data.put("tick", level.getGameTime());
        data.put("networkMaps", maps(grid.getStorageService().getInventory().getAvailableStacks()));
        if (assembler != null) {
            var contents = new KeyCounter();
            for (var stack : assembler.getInternalInventory()) if (!stack.isEmpty()) contents.add(AEItemKey.of(stack), stack.getCount());
            data.put("assemblerContents", describe(contents));
        }
        if (cpu != null) {
            var waiting = new LinkedHashSet<AEKey>();
            cpu.craftingLogic.getAllWaitingFor(waiting);
            var waitingCounts = new KeyCounter();
            for (var key : waiting) waitingCounts.add(key, cpu.craftingLogic.getWaitingFor(key));
            data.put("cpuWaitingFor", describe(waitingCounts));
            data.put("cpuInventory", describe(cpu.craftingLogic.getInventory().list));
            data.put("cpuBusy", cpu.isBusy());
        }
        if (link != null) {
            data.put("linkDone", link.isDone());
            data.put("linkCanceled", link.isCanceled());
        }
        observations.add(data);
    }

    private static List<Map<String, Object>> maps(KeyCounter counter) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : counter) if (entry.getKey() instanceof AEItemKey key && key.is(Items.FILLED_MAP)) {
            var data = new LinkedHashMap<String, Object>(describe(key.toStack()));
            data.put("amount", entry.getLongValue());
            var map = MapItem.getSavedData(key.toStack(), level);
            data.put("savedDataExists", map != null);
            if (map != null) { data.put("scale", map.scale); data.put("locked", map.locked); }
            result.add(data);
        }
        return result;
    }

    private static Map<String, Object> describe(ItemStack stack) {
        return Map.of("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "count", stack.getCount(),
                "components", stack.getComponentsPatch().toString());
    }
    private static List<Map<String, Object>> describe(KeyCounter counter) {
        var result = new ArrayList<Map<String, Object>>();
        for (var entry : counter) if (entry.getLongValue() != 0) result.add(Map.of("key", entry.getKey() instanceof AEItemKey key
                ? describe(key.toStack()) : entry.getKey().toString(), "amount", entry.getLongValue()));
        return result;
    }
    private static double availableEnergy() { return grid.getEnergyService().extractAEPower(1_000_000_000, Actionable.SIMULATE, PowerMultiplier.ONE); }
    private static long elapsed() { return level.getGameTime() - stageTick; }
    private static void transition(int next, String status) {
        stage = next;
        stageTick = level.getGameTime();
        ProbeState.phase = "native_catalog";
        ProbeState.status = status;
    }
    private static void write() throws Exception {
        report.put("passed", passed);
        report.put("finished", finished);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
    private static void fail(Throwable failure) {
        passed = false;
        finished = true;
        report.put("failure", failure.getClass().getName() + ": " + failure.getMessage());
        try {
            if (sourceMap != null) report.put("validNewScaledMapCount", validCompletedMaps());
            if (link != null) report.put("nativeLinkDone", link.isDone());
            if (cpu != null) report.put("nativeCpuIdle", !cpu.isBusy());
            snapshot("failure_before_cleanup");
            write();
            // Cancel only this fixture's submitted order after recording the unmodified failure evidence.
            if (link != null && !link.isDone() && !link.isCanceled() && cpu != null && cpu.craftingLogic.getLastLink() == link) {
                cpu.cancelJob();
                report.put("cleanupCanceledOwnProbeJob", true);
                write();
            }
        } catch (Exception recordingFailure) {
            report.put("recordingFailure", recordingFailure.toString());
        }
        ProbeState.status = "native_catalog_failed: " + failure;
    }
}
