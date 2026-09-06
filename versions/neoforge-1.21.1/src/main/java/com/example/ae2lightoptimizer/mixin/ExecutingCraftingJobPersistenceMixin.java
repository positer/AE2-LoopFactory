package com.example.ae2lightoptimizer.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.example.ae2lightoptimizer.integration.RipperMapSerialization;
import com.example.ae2lightoptimizer.integration.CraftingExecutionScheduleCodec;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobPersistenceMixin {
    @Shadow private GenericStack finalOutput;

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$restoreMapTarget(CompoundTag data, HolderLookup.Provider registries,
            @Coerce Object listener, CraftingCpuLogic cpu, CallbackInfo callback) {
        if (finalOutput == null || !data.contains("ae2loFinalOutputMap")) return;
        try {
            finalOutput = new GenericStack(RipperMapSerialization.restoreKey(finalOutput.what(),
                    data.getCompound("ae2loFinalOutputMap")), finalOutput.amount());
        } catch (RuntimeException invalidMarker) {
            // The native caller cancels a job with a missing final output and returns its physical stock.
            finalOutput = null;
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEItemKey;fromTag(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)Lappeng/api/stacks/AEItemKey;"),
            require = 1, expect = 1)
    private AEItemKey ae2lightoptimizer$readMapTask(HolderLookup.Provider registries, CompoundTag data) {
        var key = AEItemKey.fromTag(registries, data);
        try {
            return (AEItemKey) RipperMapSerialization.restoreKey(key, data.getCompound(RipperMapSerialization.TAG_NAME));
        } catch (RuntimeException invalidMarker) { return null; }
    }

    @Redirect(method = "writeToNBT", at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEItemKey;toTag(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"),
            require = 1, expect = 1)
    private CompoundTag ae2lightoptimizer$writeMapTask(AEItemKey key, HolderLookup.Provider registries) {
        var data = key.toTag(registries);
        var job = (ScheduledCraftingJob) this;
        if (job.ae2lightoptimizer$isNativeRipperJob() || job.ae2lightoptimizer$isRipperRequested()) {
            var markers = RipperMapSerialization.markers(key);
            if (!markers.isEmpty()) data.put(RipperMapSerialization.TAG_NAME, markers);
        }
        return data;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$writeSchedule(
            HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> callback) {
        var scheduledJob = (ScheduledCraftingJob) this;
        if (finalOutput != null && (scheduledJob.ae2lightoptimizer$isNativeRipperJob()
                || scheduledJob.ae2lightoptimizer$isRipperRequested())) {
            var markers = RipperMapSerialization.markers(finalOutput.what());
            if (!markers.isEmpty()) callback.getReturnValue().put("ae2loFinalOutputMap", markers);
        }
        if (scheduledJob.ae2lightoptimizer$getNativeState() != null) {
            com.example.ae2lightoptimizer.integration.RipperNativeCrafting.write(
                    callback.getReturnValue(), registries, scheduledJob.ae2lightoptimizer$getNativeState());
        }
        if (scheduledJob.ae2lightoptimizer$isNativeRipperJob()) {
            callback.getReturnValue().putBoolean("ae2loNativeRipper", true);
        }
        if (scheduledJob.ae2lightoptimizer$isRipperRequested()) {
            callback.getReturnValue().putBoolean("ae2loRipperRequested", true);
        }
        if (scheduledJob.ae2lightoptimizer$isRipped()) {
            callback.getReturnValue().putBoolean("ae2loRipped", true);
        }
        if (scheduledJob.ae2lightoptimizer$hasSchedule()) {
            CraftingExecutionScheduleCodec.write(callback.getReturnValue(), registries, scheduledJob);
        }
    }
}
