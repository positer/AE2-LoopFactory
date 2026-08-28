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
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobMixin implements ScheduledCraftingJob {
    @Shadow @Final private CraftingLink link;
    @Shadow @Final private ListCraftingInventory waitingFor;
    @Shadow @Final private ElapsedTimeTracker timeTracker;
    @Shadow private GenericStack finalOutput;
    @Shadow private long remainingAmount;

    @Unique
    @Nullable
    private CraftingExecutionSchedule ae2lightoptimizer$schedule;

    @Unique
    @Nullable
    private CompressedBatchCursor ae2lightoptimizer$cursor;

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
