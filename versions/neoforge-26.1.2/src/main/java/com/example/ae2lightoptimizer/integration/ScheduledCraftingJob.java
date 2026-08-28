package com.example.ae2lightoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

public interface ScheduledCraftingJob {
    void ae2lightoptimizer$configure(CraftingExecutionSchedule schedule, int batchIndex, long remainingInBatch);

    boolean ae2lightoptimizer$hasSchedule();

    @Nullable
    IPatternDetails ae2lightoptimizer$currentPattern();

    int ae2lightoptimizer$limitOperations(int requestedOperations);

    void ae2lightoptimizer$advance(long completedOperations);

    CraftingExecutionSchedule ae2lightoptimizer$getSchedule();

    int ae2lightoptimizer$getBatchIndex();

    long ae2lightoptimizer$getRemainingInBatch();

    boolean ae2lightoptimizer$isRingSchedule();

    boolean ae2lightoptimizer$isScheduleComplete();

    long ae2lightoptimizer$getFinalOutputReserve();

    boolean ae2lightoptimizer$matchesFinalOutput(AEKey what);

    @Nullable
    AEKey ae2lightoptimizer$getFinalOutputKey();

    long ae2lightoptimizer$getRemainingRequest();

    void ae2lightoptimizer$decrementRemainingRequest(long amount);

    long ae2lightoptimizer$getWaitingFor(AEKey what, long amount);

    void ae2lightoptimizer$consumeWaitingFor(AEKey what, long amount);

    long ae2lightoptimizer$deliver(AEKey what, long amount, Actionable mode);
}
