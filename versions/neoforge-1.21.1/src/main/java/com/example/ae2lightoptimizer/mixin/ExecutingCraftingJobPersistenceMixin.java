package com.example.ae2lightoptimizer.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import com.example.ae2lightoptimizer.integration.CraftingExecutionScheduleCodec;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobPersistenceMixin {
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$writeSchedule(
            HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> callback) {
        var scheduledJob = (ScheduledCraftingJob) this;
        if (scheduledJob.ae2lightoptimizer$hasSchedule()) {
            CraftingExecutionScheduleCodec.write(callback.getReturnValue(), registries, scheduledJob);
        }
    }
}
