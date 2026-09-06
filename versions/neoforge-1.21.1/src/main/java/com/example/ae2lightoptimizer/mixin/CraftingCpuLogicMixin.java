package com.example.ae2lightoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.example.ae2lightoptimizer.integration.CraftingExecutionScheduleCodec;
import com.example.ae2lightoptimizer.integration.CraftingRipperExecutor;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingPlan;
import com.example.ae2lightoptimizer.crafting.RingCompletionGate;
import com.example.ae2lightoptimizer.crafting.RingOutputLock;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingCpuLogic.class, remap = false)
abstract class CraftingCpuLogicMixin {
    @Shadow
    @Nullable
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow
    protected abstract void finishJob(boolean success);

    @Shadow
    protected abstract void postChange(AEKey what);

    @Inject(method = "trySubmitJob", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;tryExtractInitialItems(Lappeng/api/networking/crafting/ICraftingPlan;Lappeng/api/networking/IGrid;Lappeng/crafting/inv/ListCraftingInventory;Lappeng/api/networking/security/IActionSource;)Lappeng/api/stacks/GenericStack;"),
            cancellable = true, require = 1, expect = 1)
    private void ae2lightoptimizer$preflightRipperChain(
            IGrid grid, ICraftingPlan plan, IActionSource source,
            @Nullable ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (CraftingRipperExecutor.ownsAny(grid, plan.patternTimes())
                && !CraftingRipperExecutor.acceptsPlan(plan, cluster.getLevel())) {
            callback.setReturnValue(CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    @Inject(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"),
            cancellable = true, require = 1, expect = 1)
    private void ae2lightoptimizer$executeRipperChain(
            IEnergyService energyService, CraftingService craftingService, CallbackInfo callback) {
        if (job == null) {
            return;
        }
        var scheduledJob = (ScheduledCraftingJob) job;
        if (!scheduledJob.ae2lightoptimizer$isRipped()
                && !scheduledJob.ae2lightoptimizer$isRipperRequested()
                && !scheduledJob.ae2lightoptimizer$isNativeRipperJob()
                && !CraftingRipperExecutor.hasActiveRipper(cluster.getGrid())) {
            return;
        }
        var beforeTasks = scheduledJob.ae2lightoptimizer$getRemainingTasks();
        var result = CraftingRipperExecutor.execute(cluster.getGrid(), energyService,
                inventory, scheduledJob, cluster.getLevel());
        if (result != CraftingRipperExecutor.Result.NOT_HANDLED) {
            cluster.markDirty();
            if (scheduledJob.ae2lightoptimizer$isRipped()) {
                beforeTasks.keySet().forEach(pattern -> pattern.getOutputs().forEach(
                        output -> postChange(output.what())));
            }
        }
        if (result == CraftingRipperExecutor.Result.COMPLETED || result == CraftingRipperExecutor.Result.INVALID) {
            finishJob(result == CraftingRipperExecutor.Result.COMPLETED);
            cluster.updateOutput(null);
        } else if (scheduledJob.ae2lightoptimizer$isRipped()) {
            cluster.updateOutput(new GenericStack(scheduledJob.ae2lightoptimizer$getFinalOutputKey(),
                    scheduledJob.ae2lightoptimizer$getRemainingRequest()));
        }
        if (result != CraftingRipperExecutor.Result.NOT_HANDLED) {
            callback.cancel();
        }
    }

    @Inject(method = "trySubmitJob", at = @At(value = "FIELD",
            target = "Lappeng/crafting/execution/CraftingCpuLogic;job:Lappeng/crafting/execution/ExecutingCraftingJob;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 1, expect = 1)
    private void ae2lightoptimizer$attachSubmittedSchedule(
            IGrid grid, ICraftingPlan plan, IActionSource source,
            @Nullable ICraftingRequester requester,
            CallbackInfoReturnable<ICraftingSubmitResult> callback) {
        if (CraftingRipperExecutor.ownsAny(grid, plan.patternTimes())) {
            if (CraftingRipperExecutor.requiresNativePlan(plan, cluster.getLevel())) {
                ((ScheduledCraftingJob) job).ae2lightoptimizer$useNativeRipper();
            } else {
                ((ScheduledCraftingJob) job).ae2lightoptimizer$requestRipper();
            }
        }
        if (!(plan instanceof ScheduledCraftingPlan scheduledPlan)) {
            return;
        }
        var schedule = scheduledPlan.ae2lightoptimizer$getSchedule();
        if (schedule != null) {
            ((ScheduledCraftingJob) job).ae2lightoptimizer$configure(
                    schedule, 0, schedule.batches().getFirst().repetitions());
        }
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$restoreSchedule(
            CompoundTag data, HolderLookup.Provider registries, CallbackInfo callback) {
        if (job == null || !data.contains("job")) {
            return;
        }
        var restored = CraftingExecutionScheduleCodec.read(
                data.getCompound("job"), registries, cluster.getLevel());
        if (restored != null) {
            ((ScheduledCraftingJob) job).ae2lightoptimizer$configure(
                    restored.schedule(), restored.batchIndex(), restored.remainingInBatch());
        }
        if (data.getCompound("job").getBoolean("ae2loRipped")) {
            ((ScheduledCraftingJob) job).ae2lightoptimizer$markRipped();
        }
        if (data.getCompound("job").getBoolean("ae2loRipperRequested")) {
            ((ScheduledCraftingJob) job).ae2lightoptimizer$requestRipper();
        }
        if (data.getCompound("job").getBoolean("ae2loNativeRipper")) {
            ((ScheduledCraftingJob) job).ae2lightoptimizer$useNativeRipper();
        }
        var nativeState = com.example.ae2lightoptimizer.integration.RipperNativeCrafting.read(
                data.getCompound("job"), registries, cluster.getLevel());
        if (nativeState != null) ((ScheduledCraftingJob) job).ae2lightoptimizer$setNativeState(nativeState);
    }

    @ModifyVariable(method = "executeCrafting", at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 1, expect = 1)
    private int ae2lightoptimizer$limitCurrentBatch(int maxPatterns) {
        if (job == null) {
            return maxPatterns;
        }
        return ((ScheduledCraftingJob) job).ae2lightoptimizer$limitOperations(maxPatterns);
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;entrySet()Ljava/util/Set;", ordinal = 0),
            require = 1, expect = 1)
    private Set<?> ae2lightoptimizer$selectCurrentBatch(Map<?, ?> tasks) {
        if (job == null) {
            return tasks.entrySet();
        }
        var scheduledJob = (ScheduledCraftingJob) job;
        if (!scheduledJob.ae2lightoptimizer$hasSchedule()) {
            return tasks.entrySet();
        }
        IPatternDetails current = scheduledJob.ae2lightoptimizer$currentPattern();
        return current == null ? Collections.emptySet() : singleRemovableEntry(tasks, current);
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$advanceCurrentBatch(
            int maxPatterns, CraftingService craftingService, IEnergyService energyService,
            Level level, CallbackInfoReturnable<Integer> callback) {
        if (job != null) {
            var scheduledJob = (ScheduledCraftingJob) job;
            scheduledJob.ae2lightoptimizer$advance(callback.getReturnValue());
            ae2lightoptimizer$flushRingFinalOutput(scheduledJob);
        }
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true, require = 1, expect = 1)
    private void ae2lightoptimizer$lockRingFinalOutput(
            AEKey what, long amount, Actionable type, CallbackInfoReturnable<Long> callback) {
        if (what == null || job == null) {
            return;
        }
        var scheduledJob = (ScheduledCraftingJob) job;
        if (!scheduledJob.ae2lightoptimizer$isRingSchedule()
                || !scheduledJob.ae2lightoptimizer$matchesFinalOutput(what)) {
            return;
        }

        long accepted = scheduledJob.ae2lightoptimizer$getWaitingFor(what, amount);
        if (accepted <= 0) {
            callback.setReturnValue(0L);
            return;
        }
        if (type == Actionable.MODULATE) {
            scheduledJob.ae2lightoptimizer$consumeWaitingFor(what, accepted);
            inventory.insert(what, accepted, Actionable.MODULATE);
            cluster.markDirty();
            ae2lightoptimizer$flushRingFinalOutput(scheduledJob);
        }
        callback.setReturnValue(accepted);
    }

    private void ae2lightoptimizer$flushRingFinalOutput(ScheduledCraftingJob scheduledJob) {
        if (job == null || !scheduledJob.ae2lightoptimizer$isRingSchedule()) {
            return;
        }
        AEKey finalOutput = scheduledJob.ae2lightoptimizer$getFinalOutputKey();
        if (finalOutput == null) {
            return;
        }
        long stored = inventory.extract(finalOutput, Long.MAX_VALUE, Actionable.SIMULATE);
        long releasable = RingOutputLock.releasable(
                stored,
                scheduledJob.ae2lightoptimizer$getFinalOutputReserve(),
                scheduledJob.ae2lightoptimizer$getRemainingRequest(),
                scheduledJob.ae2lightoptimizer$isScheduleComplete());
        if (releasable > 0) {
            long routed = scheduledJob.ae2lightoptimizer$deliver(
                    finalOutput, releasable, Actionable.SIMULATE);
            if (routed > 0) {
                routed = Math.min(routed, scheduledJob.ae2lightoptimizer$deliver(
                        finalOutput, routed, Actionable.MODULATE));
                if (routed > 0) {
                    inventory.extract(finalOutput, routed, Actionable.MODULATE);
                }
            }

            // AE2 terminal submissions are standalone and have no requester, so their
            // CraftingLink accepts zero. The returned final output still satisfies the
            // job; anything not routed directly is dumped to network storage by
            // finishJob -> storeItems together with the retained ring seed.
            scheduledJob.ae2lightoptimizer$decrementRemainingRequest(releasable);
        }

        long remaining = scheduledJob.ae2lightoptimizer$getRemainingRequest();
        long pendingFinalOutput = scheduledJob.ae2lightoptimizer$getWaitingFor(
                finalOutput, Long.MAX_VALUE);
        if (RingCompletionGate.canFinish(
                scheduledJob.ae2lightoptimizer$isScheduleComplete(),
                remaining,
                pendingFinalOutput)) {
            finishJob(true);
            cluster.updateOutput(null);
        } else if (remaining > 0) {
            cluster.updateOutput(new GenericStack(finalOutput, remaining));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Set<?> singleRemovableEntry(Map<?, ?> source, Object key) {
        if (!source.containsKey(key)) {
            return Collections.emptySet();
        }
        Map rawSource = source;
        return new AbstractSet<Map.Entry<?, ?>>() {
            @Override
            public Iterator<Map.Entry<?, ?>> iterator() {
                return new Iterator<>() {
                    private boolean available = true;
                    private boolean removable;

                    @Override
                    public boolean hasNext() {
                        return available;
                    }

                    @Override
                    public Map.Entry<?, ?> next() {
                        if (!available) {
                            throw new NoSuchElementException();
                        }
                        available = false;
                        removable = true;
                        return new AbstractMap.SimpleImmutableEntry<>(key, rawSource.get(key));
                    }

                    @Override
                    public void remove() {
                        if (!removable) {
                            throw new IllegalStateException();
                        }
                        rawSource.remove(key);
                        removable = false;
                    }
                };
            }

            @Override
            public int size() {
                return 1;
            }
        };
    }
}
