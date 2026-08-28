package com.example.ae2lightoptimizer.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import com.example.ae2lightoptimizer.integration.CraftingExecutionScheduleCodec;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobPersistenceMixin {
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$writeSchedule(ValueOutput output, CallbackInfo callback) {
        var scheduledJob = (ScheduledCraftingJob) this;
        if (scheduledJob.ae2lightoptimizer$hasSchedule()) {
            CraftingExecutionScheduleCodec.write(output, scheduledJob);
        }
    }
}
