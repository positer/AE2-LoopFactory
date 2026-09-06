package com.example.ae2loprobe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.RipperNativeCrafting;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.google.gson.GsonBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;


/** Test-only state persistence round trip inside the real three-map job; this does not restart the server. */
public final class NativeContinuationAssertions {
    private static final double ENERGY_PROBE_LIMIT = 1_000_000_000D;
    private static final double TOLERANCE = 0.00001;
    private static final Map<String, Object> report = new LinkedHashMap<>();
    private static final List<Map<String, Object>> calls = new ArrayList<>();
    private static ScheduledCraftingJob observedJob;
    private static UUID identity;
    private static Path output;
    private static boolean activeCall;
    private static boolean roundTripAttempted;
    private static boolean restored;
    private static boolean cpuReloadPending;
    private static boolean cpuRestored;
    private static boolean finished;
    private static double beforeEnergy;
    private static double totalFee;
    private static long beforeTick;
    private static long beforeCommitted;
    private static boolean beforePaid;
    private static String failure;
    private static Map<String, Object> callBefore = Map.of();

    private NativeContinuationAssertions() {}

    public static void before(IEnergyService energy, ScheduledCraftingJob job, Level level) {
        if (!NativeCatalogFixture.ENABLED || finished || !job.ae2lightoptimizer$isNativeRipperJob()) return;
        if (observedJob == null) {
            if (!(level instanceof ServerLevel server)
                    || !server.getServer().getWorldData().getLevelName().startsWith("AE2LO-Ripper-Probe-")
                    || job.ae2lightoptimizer$getRemainingRequest() != 3
                    || !(job.ae2lightoptimizer$getFinalOutputKey() instanceof AEItemKey target)
                    || !target.is(Items.FILLED_MAP) || target.get(DataComponents.MAP_POST_PROCESSING) == null) return;
            observedJob = job;
            output = Path.of(System.getProperty("ae2lo.probe.reportDir",
                    System.getProperty("ae2lo.probe.output", "ae2lo-probe-evidence")))
                    .toAbsolutePath().resolve("native-continuation-roundtrip.json");
            report.put("boundary", "At ServerTick.Post after operation one, strict production State.write/read then binary NBT round trip through actual CraftingCpuLogic.writeToNBT/readFromNBT. Physical CPU inventory, job, link and State are restored; the same crafting UUID continues operations two and three. No server restart, plan substitution, extra execution or canceled old link.");
            report.put("minecraft", "1.21.1");
            report.put("world", server.getServer().getWorldData().getLevelName());
            report.put("expectedOperations", 3);
            report.put("expectedExecutionFeeAE", 50);
            report.put("calls", calls);
            report.put("roundTripPerformed", false);
            report.put("passed", false);
        }
        if (observedJob != job) return;
        activeCall = true;
        beforeTick = level.getGameTime();
        beforeEnergy = available(energy);
        var state = job.ae2lightoptimizer$getNativeState();
        beforeCommitted = state == null ? 0 : state.committedOperations();
        beforePaid = state != null && state.paid();
    }

    public static void before(IEnergyService energy, ListCraftingInventory inventory, ScheduledCraftingJob job, Level level) {
        before(energy, job, level);
        if (activeCall && observedJob == job) callBefore = describeCall(inventory, job);
    }

    public static void after(IEnergyService energy, ListCraftingInventory inventory, ScheduledCraftingJob job,
            Level level, CraftingRipperExecutor.Result result) {
        if (!activeCall || observedJob != job) return;
        activeCall = false;
        double afterEnergy = available(energy);
        double fee = beforeEnergy - afterEnergy;
        totalFee += fee;
        var state = job.ae2lightoptimizer$getNativeState();
        var call = new LinkedHashMap<String, Object>();
        call.put("index", calls.size() + 1);
        call.put("tickBefore", beforeTick);
        call.put("tickAfter", level.getGameTime());
        call.put("result", result.name());
        call.put("energyBeforeAE", beforeEnergy);
        call.put("energyAfterAE", afterEnergy);
        call.put("executionFeeAE", fee);
        call.put("paidBefore", beforePaid);
        call.put("operationsBefore", beforeCommitted);
        call.put("operationsAfter", state == null ? -1 : state.committedOperations());
        call.put("before", callBefore);
        call.put("after", describeCall(inventory, job));
        if (result == CraftingRipperExecutor.Result.INVALID && state != null && state.remainingTasks().isEmpty()) {
            call.put("detachedDeliveryAllocationDiagnostic", deliveryDiagnostic(state, inventory, job));
        }
        calls.add(call);
        try {
            require(Double.isFinite(beforeEnergy) && Double.isFinite(afterEnergy)
                    && beforeEnergy < ENERGY_PROBE_LIMIT && afterEnergy < ENERGY_PROBE_LIMIT,
                    "Energy measurement reached its probe ceiling");
            require(beforeTick == level.getGameTime(), "Executor call crossed a game tick");
            require(state != null && !state.invalid(), "Missing or invalid production continuation");
            if (identity == null) identity = state.identity();
            require(identity.equals(state.identity()), "Production continuation identity changed");
            long operations = state.committedOperations() - beforeCommitted;
            require(operations >= 0 && operations <= 1, "More than one native operation occurred in one execute call");
            double expectedFee = operations == 1 && !beforePaid ? 50 : 0;
            require(Math.abs(fee - expectedFee) < TOLERANCE, "Wrong call-local fee: " + fee + " expected " + expectedFee);
            if (!roundTripAttempted && state.committedOperations() == 1) {
                roundTripAttempted = true;
                cpuReloadPending = true;
            }
            if (result == CraftingRipperExecutor.Result.INVALID || result == CraftingRipperExecutor.Result.NOT_HANDLED) {
                throw new IllegalStateException("Original native job declined after observation");
            }
            if (result == CraftingRipperExecutor.Result.COMPLETED) {
                finished = true;
                require(restored && cpuRestored, "Job completed without exercising the complete CPU persistence round trip");
                require(state.committedOperations() == 3 && state.paid() && state.remainingTasks().isEmpty()
                        && job.ae2lightoptimizer$getRemainingRequest() == 0, "Native job did not complete exactly three operations");
                require(identity.equals(state.identity()), "Restored job lost its identity before completion");
                require(Math.abs(totalFee - 50) < TOLERANCE, "Whole-job execute fee differs from 50 AE: " + totalFee);
                if (job.ae2lightoptimizer$isStandalone()) {
                    long credited = 0;
                    var mapIds = new java.util.HashSet<Object>();
                    for (var credit : state.credits()) {
                        require(credit.actual() instanceof AEItemKey, "Non-item native map credit");
                        var actual = (AEItemKey) credit.actual();
                        require(actual.is(Items.FILLED_MAP) && actual.get(DataComponents.MAP_POST_PROCESSING) == null
                                && inventory.list.get(actual) >= credit.amount(), "Credit lacks its real completed map stock");
                        require(actual.get(DataComponents.MAP_ID) != null, "Actual map credit has no map identity");
                        mapIds.add(actual.get(DataComponents.MAP_ID));
                        credited = Math.addExact(credited, credit.amount());
                    }
                    require(credited == 3 && mapIds.size() == 3, "Restored job lost or duplicated a real map identity");
                    report.put("physicalCompletedMapCredits", credited);
                    report.put("distinctActualMapIds", mapIds.size());
                }
                report.put("finalState", describe(state));
            }
        } catch (RuntimeException assertionFailure) {
            if (failure == null) failure = assertionFailure.getClass().getName() + ": " + assertionFailure.getMessage();
        }
        report.put("finished", finished);
        report.put("totalExecutionFeeAE", totalFee);
        report.put("roundTripPerformed", restored);
        report.put("roundTripAttempted", roundTripAttempted);
        report.put("cpuRoundTripPerformed", cpuRestored);
        report.put("passed", finished && restored && cpuRestored && failure == null);
        if (failure != null) report.put("failure", failure);
        writeReport();
    }


    /** Runs after the executor has returned, so no production stack frame retains the old job. */
    public static boolean restoreCpuAtTickEnd(appeng.me.cluster.implementations.CraftingCPUCluster cpu,
                                              ServerLevel level) {
        if (!cpuReloadPending || cpuRestored) return false;
        cpuReloadPending = false;
        try {
            require(!activeCall, "Full CPU reload attempted inside active executor");
            var field = appeng.crafting.execution.CraftingCpuLogic.class.getDeclaredField("job");
            field.setAccessible(true);
            var originalJob = (ScheduledCraftingJob) field.get(cpu.craftingLogic);
            require(originalJob == observedJob, "Probe selected another CPU's job");
            var originalState = originalJob.ae2lightoptimizer$getNativeState();
            require(originalState != null && originalState.committedOperations() == 1 && originalState.paid(),
                    "CPU reload missed its first committed operation");
            restoreActualState(originalJob, cpu.craftingLogic.getInventory(), originalState, level);
            originalState = originalJob.ae2lightoptimizer$getNativeState();
            var stockBefore = stock(cpu.craftingLogic.getInventory().list);
            var tasksBefore = new LinkedHashMap<>(originalJob.ae2lightoptimizer$getRemainingTasks());
            var finalBefore = originalJob.ae2lightoptimizer$getFinalOutputKey();
            long requestBefore = originalJob.ae2lightoptimizer$getRemainingRequest();
            boolean scheduled = originalJob.ae2lightoptimizer$hasSchedule();
            int batchBefore = scheduled ? originalJob.ae2lightoptimizer$getBatchIndex() : 0;
            long withinBatch = scheduled ? originalJob.ae2lightoptimizer$getRemainingInBatch() : 0;
            boolean requested = originalJob.ae2lightoptimizer$isRipperRequested();
            boolean ripped = originalJob.ae2lightoptimizer$isRipped();
            var linkBefore = cpu.craftingLogic.getLastLink();
            require(linkBefore != null && linkBefore.isStandalone() && !linkBefore.isCanceled(), "Unexpected live link before reload");
            var originalCraftingId = linkBefore.getCraftingID();
            var waitingBefore = waiting(cpu);
            var savedCpu = encodeCpu(cpu, level);
            var bytes = new java.io.ByteArrayOutputStream();
            try (var binary = new java.io.DataOutputStream(bytes)) {
                net.minecraft.nbt.NbtIo.write(savedCpu, binary);
            }
            var file = output.resolveSibling("native-cpu-first-commit.nbt");
            Files.createDirectories(file.getParent());
            Files.write(file, bytes.toByteArray());
            CompoundTag decodedCpu;
            try (var binary = new java.io.DataInputStream(new java.io.ByteArrayInputStream(Files.readAllBytes(file)))) {
                decodedCpu = net.minecraft.nbt.NbtIo.read(binary, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            require(savedCpu.equals(decodedCpu), "Binary NBT transport changed complete CPU tag");
            var cpuReport = new LinkedHashMap<String, Object>();
            cpuReport.put("tick", level.getGameTime());
            cpuReport.put("binaryEvidence", file.toString());
            cpuReport.put("binaryBytes", bytes.size());
            cpuReport.put("binaryNbtEqual", true);
            cpuReport.put("beforeFinalOutput", describeKey(finalBefore));
            cpuReport.put("craftingId", originalCraftingId.toString());
            cpuReport.put("beforeState", describe(originalState));
            report.put("completeCpuRoundTrip", cpuReport);

            decodeCpu(cpu, decodedCpu, level);
            var recoveredJob = (ScheduledCraftingJob) field.get(cpu.craftingLogic);
            require(recoveredJob != null && recoveredJob != originalJob, "Native CPU did not recreate its job");
            var recoveredState = recoveredJob.ae2lightoptimizer$getNativeState();
            var recoveredLink = cpu.craftingLogic.getLastLink();
            require(recoveredState != null && recoveredState != originalState && !recoveredState.invalid(),
                    "Native CPU did not restore its continuation");
            cpuReport.put("afterFinalOutput", describeKey(recoveredJob.ae2lightoptimizer$getFinalOutputKey()));
            cpuReport.put("afterState", describe(recoveredState));
            cpuReport.put("finalOutputExact", finalBefore.equals(recoveredJob.ae2lightoptimizer$getFinalOutputKey()));
            cpuReport.put("physicalStockExact", stockBefore.equals(stock(cpu.craftingLogic.getInventory().list)));
            cpuReport.put("nativeTasksExact", tasksBefore.equals(recoveredJob.ae2lightoptimizer$getRemainingTasks()));
            cpuReport.put("stateTagExact", encode(originalState, level).equals(encode(recoveredState, level)));
            require(Boolean.TRUE.equals(cpuReport.get("finalOutputExact"))
                    && Boolean.TRUE.equals(cpuReport.get("physicalStockExact"))
                    && Boolean.TRUE.equals(cpuReport.get("nativeTasksExact"))
                    && Boolean.TRUE.equals(cpuReport.get("stateTagExact")),
                    "Complete CPU reload changed target, physical stock, tasks or continuation");
            require(originalState.identity().equals(recoveredState.identity())
                    && originalState.remainingTasks().equals(recoveredState.remainingTasks())
                    && originalState.credits().equals(recoveredState.credits())
                    && recoveredState.committedOperations() == 1 && recoveredState.paid(),
                    "Full reload changed persistent operation identity or credits");
            require(recoveredJob.ae2lightoptimizer$isNativeRipperJob()
                    && recoveredJob.ae2lightoptimizer$isRipperRequested() == requested
                    && recoveredJob.ae2lightoptimizer$isRipped() == ripped
                    && recoveredJob.ae2lightoptimizer$getRemainingRequest() == requestBefore
                    && recoveredJob.ae2lightoptimizer$hasSchedule() == scheduled
                    && (!scheduled || recoveredJob.ae2lightoptimizer$getBatchIndex() == batchBefore
                            && recoveredJob.ae2lightoptimizer$getRemainingInBatch() == withinBatch),
                    "Full reload changed CPU flags, remaining request or scheduled batch cursor");
            require(recoveredLink != null && recoveredLink != linkBefore && recoveredLink.isStandalone()
                    && !recoveredLink.isCanceled() && originalCraftingId.equals(recoveredLink.getCraftingID())
                    && !linkBefore.isCanceled() && waitingBefore.equals(waiting(cpu)),
                    "Full reload changed native link identity or waiting stock");
            observedJob = recoveredJob;
            cpuRestored = true;
            cpuReport.put("nativeJobAndLinkRecreated", true);
            cpuReport.put("originalLinkNeverCanceled", true);
            cpuReport.put("restoredObjectInstalledOnActualCpu", true);
            cpuReport.put("passed", true);
            report.put("cpuRoundTripPerformed", true);
            writeReport();
            return true;
        } catch (Exception reloadFailure) {
            if (failure == null) failure = reloadFailure.toString();
            report.put("failure", failure);
            report.put("cpuRoundTripPerformed", false);
            writeReport();
            throw new IllegalStateException("Complete CPU persistence acceptance failed", reloadFailure);
        }
    }

    private static Map<AEKey, Long> waiting(appeng.me.cluster.implementations.CraftingCPUCluster cpu) {
        var keys = new java.util.HashSet<AEKey>();
        cpu.craftingLogic.getAllWaitingFor(keys);
        var result = new LinkedHashMap<AEKey, Long>();
        for (var key : keys) result.put(key, cpu.craftingLogic.getWaitingFor(key));
        return result;
    }

    private static CompoundTag encodeCpu(appeng.me.cluster.implementations.CraftingCPUCluster cpu, Level level) {
        var tag = new CompoundTag();
        cpu.craftingLogic.writeToNBT(tag, level.registryAccess());
        return tag;
    }

    private static void decodeCpu(appeng.me.cluster.implementations.CraftingCPUCluster cpu, CompoundTag tag, Level level) {
        cpu.craftingLogic.readFromNBT(tag, level.registryAccess());
    }

    private static void restoreActualState(ScheduledCraftingJob job, ListCraftingInventory inventory,
            RipperNativeCrafting.State original, Level level) {
        var stockBefore = stock(inventory.list);
        var tasksBefore = new LinkedHashMap<>(job.ae2lightoptimizer$getRemainingTasks());
        long requestBefore = job.ae2lightoptimizer$getRemainingRequest();
        boolean scheduled = job.ae2lightoptimizer$hasSchedule();
        int batchBefore = scheduled ? job.ae2lightoptimizer$getBatchIndex() : 0;
        long withinBatchBefore = scheduled ? job.ae2lightoptimizer$getRemainingInBatch() : 0;
        var saved = encode(original, level);
        var comparison = new LinkedHashMap<String, Object>();
        comparison.put("tick", level.getGameTime());
        comparison.put("before", describe(original));
        comparison.put("serializedNbt", saved.toString());
        report.put("roundTripComparison", comparison);
        var recovered = decode(saved.copy(), level);
        comparison.put("recoveredPresent", recovered != null);
        if (recovered != null) {
            comparison.put("after", describe(recovered));
            comparison.put("reencodedNbt", encode(recovered, level).toString());
            comparison.put("identityEqual", original.identity().equals(recovered.identity()));
            comparison.put("paidEqual", original.paid() == recovered.paid());
            comparison.put("operationsEqual", original.committedOperations() == recovered.committedOperations());
            comparison.put("remainingTasksEqual", original.remainingTasks().equals(recovered.remainingTasks()));
            comparison.put("creditsEqual", original.credits().equals(recovered.credits()));
            comparison.put("completePersistentTagEqual", saved.equals(encode(recovered, level)));
        }
        require(recovered != null && recovered != original && !recovered.invalid(), "Saved continuation failed to decode");
        require(original.identity().equals(recovered.identity()) && original.paid() == recovered.paid()
                && original.committedOperations() == recovered.committedOperations()
                && original.remainingTasks().equals(recovered.remainingTasks())
                && original.credits().equals(recovered.credits()), "Persistent continuation state changed on read");
        require(saved.equals(encode(recovered, level)), "Persistent continuation NBT changed on round trip");
        require(original.paid() && original.committedOperations() == 1
                && original.remainingTasks().values().stream().mapToLong(Long::longValue).sum() == 2
                && original.credits().stream().mapToLong(RipperNativeCrafting.Credit::amount).sum() == 1,
                "Round trip did not capture the real first committed map with two operations left");
        job.ae2lightoptimizer$setNativeState(recovered);
        require(job.ae2lightoptimizer$getNativeState() == recovered, "Real job did not install the recovered State");
        require(stockBefore.equals(stock(inventory.list)) && tasksBefore.equals(job.ae2lightoptimizer$getRemainingTasks())
                && requestBefore == job.ae2lightoptimizer$getRemainingRequest()
                && scheduled == job.ae2lightoptimizer$hasSchedule()
                && (!scheduled || batchBefore == job.ae2lightoptimizer$getBatchIndex()
                        && withinBatchBefore == job.ae2lightoptimizer$getRemainingInBatch()), "State restoration changed inventory or job progress");
        restored = true;
        report.put("roundTrip", Map.of("tick", level.getGameTime(), "before", describe(original),
                "after", describe(recovered), "serializedNbt", saved.toString(),
                "completePersistentTagEqual", true, "installedOnActualJob", true,
                "inventoryUnchanged", true, "jobProgressUnchanged", true,
                "transientNotificationMetadata", "lastPattern/lastActualOutput are intentionally not persistent; production notification already ran"));
    }

    private static CompoundTag encode(RipperNativeCrafting.State state, Level level) {
        var tag = new CompoundTag();
        RipperNativeCrafting.write(tag, level.registryAccess(), state);
        return tag;
    }

    private static RipperNativeCrafting.State decode(CompoundTag tag, Level level) {
        return RipperNativeCrafting.read(tag, level.registryAccess(), level);
    }

    private static double available(IEnergyService energy) {
        return energy.extractAEPower(ENERGY_PROBE_LIMIT, Actionable.SIMULATE, PowerMultiplier.ONE);
    }

    private static Map<AEKey, Long> stock(KeyCounter inventory) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var entry : inventory) if (entry.getLongValue() != 0) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }

    private static Map<String, Object> describe(RipperNativeCrafting.State state) {
        return Map.of("identity", state.identity().toString(), "paid", state.paid(), "invalid", state.invalid(),
                "committedOperations", state.committedOperations(),
                "remainingTasks", state.remainingTasks().entrySet().stream().map(entry -> Map.of(
                        "definition", entry.getKey().getDefinition().toString(), "operations", entry.getValue(),
                        "patternClass", entry.getKey().getClass().getName(), "patternHash", entry.getKey().hashCode(),
                        "definitionHash", entry.getKey().getDefinition().hashCode(),
                        "primaryOutput", describeKey(entry.getKey().getPrimaryOutput().what()))).toList(),
                "credits", state.credits().stream().map(credit -> Map.of(
                        "planned", describeKey(credit.planned()), "actual", describeKey(credit.actual()), "amount", credit.amount())).toList());
    }

    private static Map<String, Object> describeKey(AEKey key) {
        if (key == null) return Map.of("absent", true);
        Object marker = key instanceof AEItemKey item ? item.get(DataComponents.MAP_POST_PROCESSING) : null;
        Object mapId = key instanceof AEItemKey item ? item.get(DataComponents.MAP_ID) : null;
        return Map.of("value", key.toString(), "class", key.getClass().getName(), "hash", key.hashCode(),
                "mapPostProcessing", marker == null ? "absent" : marker.toString(),
                "mapId", mapId == null ? "absent" : mapId.toString(),
                "components", key instanceof AEItemKey item ? item.toStack().getComponentsPatch().toString() : "not an item");
    }

    private static Map<String, Object> describeCall(ListCraftingInventory inventory, ScheduledCraftingJob job) {
        var result = new LinkedHashMap<String, Object>();
        result.put("physicalStock", stock(inventory.list).entrySet().stream().map(entry -> Map.of(
                "key", describeKey(entry.getKey()), "amount", entry.getValue())).toList());
        result.put("finalTarget", describeKey(job.ae2lightoptimizer$getFinalOutputKey()));
        result.put("remainingRequest", job.ae2lightoptimizer$getRemainingRequest());
        var state = job.ae2lightoptimizer$getNativeState();
        result.put("state", state == null ? Map.of("absent", true) : describe(state));
        if (state != null) result.put("creditPhysicalBacking", state.credits().stream().map(credit -> Map.of(
                "planned", describeKey(credit.planned()), "actual", describeKey(credit.actual()),
                "creditAmount", credit.amount(), "physicalAmount", inventory.list.get(credit.actual()),
                "matchesFinalTarget", credit.planned().equals(job.ae2lightoptimizer$getFinalOutputKey()))).toList());
        return result;
    }

    /** Replays only the pure allocation calculation on detached maps after an INVALID return. */
    private static Map<String, Object> deliveryDiagnostic(RipperNativeCrafting.State state,
            ListCraftingInventory inventory, ScheduledCraftingJob job) {
        try {
            var method = RipperNativeCrafting.class.getDeclaredMethod("allocate", AEKey.class, long.class, Map.class, List.class);
            method.setAccessible(true);
            var allocation = method.invoke(null, job.ae2lightoptimizer$getFinalOutputKey(), job.ae2lightoptimizer$getRemainingRequest(),
                    stock(inventory.list), new ArrayList<>(state.credits()));
            return Map.of("allocationSucceeded", true, "allocation", allocation.toString(), "mutatedRealStock", false);
        } catch (java.lang.reflect.InvocationTargetException thrown) {
            return Map.of("allocationSucceeded", false, "cause", thrown.getCause().toString(), "mutatedRealStock", false);
        } catch (ReflectiveOperationException diagnosticFailure) {
            return Map.of("diagnosticUnavailable", diagnosticFailure.toString(), "mutatedRealStock", false);
        }
    }

    public static boolean passed() { return finished && restored && cpuRestored && failure == null; }

    public static Map<String, Object> summary() {
        return Map.of("observed", observedJob != null, "roundTripPerformed", restored, "finished", finished,
                "cpuRoundTripPerformed", cpuRestored,
                "passed", passed(), "totalExecutionFeeAE", totalFee, "failure", failure == null ? "" : failure);
    }

    private static void writeReport() {
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        } catch (java.io.IOException ioFailure) {
            throw new IllegalStateException("Could not write continuation assertion evidence", ioFailure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
