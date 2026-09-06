package com.example.ae2lightoptimizer.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import com.example.ae2lightoptimizer.crafting.CompressedBatchCursor;
import com.example.ae2lightoptimizer.integration.CraftingExecutionSchedule;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobMixin implements ScheduledCraftingJob {
    @Shadow @Final private CraftingLink link;
    @Shadow @Final private ListCraftingInventory waitingFor;
    @Shadow @Final private Map<IPatternDetails, ?> tasks;
    @Shadow @Final private ElapsedTimeTracker timeTracker;
    @Shadow private GenericStack finalOutput;
    @Shadow private long remainingAmount;

    @Unique
    @Nullable
    private CraftingExecutionSchedule ae2lightoptimizer$schedule;

    @Unique
    @Nullable
    private CompressedBatchCursor ae2lightoptimizer$cursor;

    @Unique
    private boolean ae2lightoptimizer$ripped;

    @Unique
    private boolean ae2lightoptimizer$ripperRequested;

    @Unique
    private boolean ae2lightoptimizer$nativeRipper;

    @Unique
    private com.example.ae2lightoptimizer.integration.RipperNativeCrafting.State ae2lightoptimizer$nativeState;

    @Override
    public com.example.ae2lightoptimizer.integration.RipperNativeCrafting.State ae2lightoptimizer$getNativeState() {
        return ae2lightoptimizer$nativeState;
    }

    @Override
    public void ae2lightoptimizer$setNativeState(com.example.ae2lightoptimizer.integration.RipperNativeCrafting.State state) {
        ae2lightoptimizer$nativeState = state;
        ae2lightoptimizer$nativeRipper = true;
    }

    @Override
    public boolean ae2lightoptimizer$isNativeRipperJob() { return ae2lightoptimizer$nativeRipper; }

    @Override
    public void ae2lightoptimizer$useNativeRipper() { ae2lightoptimizer$nativeRipper = true; }

    @Override
    public boolean ae2lightoptimizer$isRipperRequested() {
        return ae2lightoptimizer$ripperRequested;
    }

    @Override
    public void ae2lightoptimizer$requestRipper() {
        ae2lightoptimizer$ripperRequested = true;
    }

    @Override
    public boolean ae2lightoptimizer$isRipped() {
        return ae2lightoptimizer$ripped;
    }

    @Override
    public void ae2lightoptimizer$markRipped() {
        ae2lightoptimizer$ripperRequested = true;
        ae2lightoptimizer$ripped = true;
        tasks.clear();
        if (ae2lightoptimizer$hasSchedule()) {
            int count = ae2lightoptimizer$schedule.batches().size();
            ae2lightoptimizer$configure(ae2lightoptimizer$schedule, count, 0);
        }
    }

    @Override
    public boolean ae2lightoptimizer$isStandalone() {
        return link.isStandalone();
    }

    @Override
    public Map<IPatternDetails, Long> ae2lightoptimizer$getRemainingTasks() {
        Map<IPatternDetails, Long> result = new LinkedHashMap<>();
        tasks.forEach((pattern, progress) -> {
            long remaining = ((CraftingTaskProgressAccessor) progress).ae2lightoptimizer$getRemaining();
            if (remaining > 0) {
                result.put(pattern, remaining);
            }
        });
        return result;
    }

    @Override
    public boolean ae2lightoptimizer$hasWaitingItems() {
        return !waitingFor.list.isEmpty();
    }

    @Override
    public void ae2lightoptimizer$configure(
            CraftingExecutionSchedule schedule, int batchIndex, long remainingInBatch) {
        List<Long> sizes = schedule.batches().stream()
                .map(CraftingExecutionSchedule.Batch::repetitions)
                .toList();
        this.ae2lightoptimizer$schedule = schedule;
        this.ae2lightoptimizer$cursor = new CompressedBatchCursor(sizes, batchIndex, remainingInBatch);
    }

    @Override
    public boolean ae2lightoptimizer$hasSchedule() {
        return ae2lightoptimizer$schedule != null && ae2lightoptimizer$cursor != null;
    }

    @Override
    @Nullable
    public IPatternDetails ae2lightoptimizer$currentPattern() {
        if (!ae2lightoptimizer$hasSchedule() || ae2lightoptimizer$cursor.complete()) {
            return null;
        }
        return ae2lightoptimizer$schedule.batches()
                .get(ae2lightoptimizer$cursor.batchIndex()).pattern();
    }

    @Override
    public int ae2lightoptimizer$limitOperations(int requestedOperations) {
        return ae2lightoptimizer$hasSchedule()
                ? ae2lightoptimizer$cursor.limit(requestedOperations)
                : requestedOperations;
    }

    @Override
    public void ae2lightoptimizer$advance(long completedOperations) {
        if (ae2lightoptimizer$hasSchedule() && completedOperations > 0) {
            ae2lightoptimizer$cursor.advance(completedOperations);
        }
    }

    @Override
    public CraftingExecutionSchedule ae2lightoptimizer$getSchedule() {
        return ae2lightoptimizer$schedule;
    }

    @Override
    public int ae2lightoptimizer$getBatchIndex() {
        return ae2lightoptimizer$cursor.batchIndex();
    }

    @Override
    public long ae2lightoptimizer$getRemainingInBatch() {
        return ae2lightoptimizer$cursor.remainingInBatch();
    }

    @Override
    public boolean ae2lightoptimizer$isRingSchedule() {
        return ae2lightoptimizer$hasSchedule()
                && ae2lightoptimizer$schedule.owner() == CraftingExecutionSchedule.Owner.RING_TERMINAL;
    }

    @Override
    public boolean ae2lightoptimizer$isScheduleComplete() {
        return ae2lightoptimizer$hasSchedule() && ae2lightoptimizer$cursor.complete();
    }

    @Override
    public long ae2lightoptimizer$getFinalOutputReserve() {
        return ae2lightoptimizer$isRingSchedule()
                ? ae2lightoptimizer$schedule.finalOutputReserve() : 0;
    }

    @Override
    public boolean ae2lightoptimizer$matchesFinalOutput(AEKey what) {
        return finalOutput != null && what.matches(finalOutput);
    }

    @Override
    @Nullable
    public AEKey ae2lightoptimizer$getFinalOutputKey() {
        return finalOutput == null ? null : finalOutput.what();
    }

    @Override
    public long ae2lightoptimizer$getRemainingRequest() {
        return remainingAmount;
    }

    @Override
    public void ae2lightoptimizer$decrementRemainingRequest(long amount) {
        remainingAmount = Math.max(0, remainingAmount - amount);
    }

    @Override
    public long ae2lightoptimizer$getWaitingFor(AEKey what, long amount) {
        return waitingFor.extract(what, amount, Actionable.SIMULATE);
    }

    @Override
    public void ae2lightoptimizer$consumeWaitingFor(AEKey what, long amount) {
        ((ElapsedTimeTrackerAccessor) (Object) timeTracker)
                .ae2lightoptimizer$decrementItems(amount, what.getType());
        waitingFor.extract(what, amount, Actionable.MODULATE);
    }

    @Override
    public long ae2lightoptimizer$deliver(AEKey what, long amount, Actionable mode) {
        return link.insert(what, amount, mode);
    }
}
