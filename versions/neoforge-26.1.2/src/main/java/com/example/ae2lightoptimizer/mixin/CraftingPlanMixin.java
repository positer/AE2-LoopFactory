package com.example.ae2lightoptimizer.mixin;

import appeng.crafting.CraftingPlan;
import com.example.ae2lightoptimizer.integration.CraftingExecutionSchedule;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingPlan;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = CraftingPlan.class, remap = false)
abstract class CraftingPlanMixin implements ScheduledCraftingPlan {
    @Unique
    @Nullable
    private CraftingExecutionSchedule ae2lightoptimizer$schedule;

    @Override
    public void ae2lightoptimizer$setSchedule(CraftingExecutionSchedule schedule) {
        this.ae2lightoptimizer$schedule = schedule;
    }

    @Override
    @Nullable
    public CraftingExecutionSchedule ae2lightoptimizer$getSchedule() {
        return ae2lightoptimizer$schedule;
    }
}
