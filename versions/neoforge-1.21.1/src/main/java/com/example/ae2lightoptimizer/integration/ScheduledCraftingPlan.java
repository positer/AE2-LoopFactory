package com.example.ae2lightoptimizer.integration;

import org.jetbrains.annotations.Nullable;

public interface ScheduledCraftingPlan {
    void ae2lightoptimizer$setSchedule(CraftingExecutionSchedule schedule);

    @Nullable
    CraftingExecutionSchedule ae2lightoptimizer$getSchedule();
}
