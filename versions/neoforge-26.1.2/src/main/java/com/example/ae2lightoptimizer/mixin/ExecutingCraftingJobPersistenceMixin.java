package com.example.ae2lightoptimizer.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.example.ae2lightoptimizer.integration.RipperMapSerialization;
import com.example.ae2lightoptimizer.integration.CraftingExecutionScheduleCodec;
import com.example.ae2lightoptimizer.integration.ScheduledCraftingJob;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ExecutingCraftingJob.class, remap = false)
abstract class ExecutingCraftingJobPersistenceMixin {
    @Shadow private GenericStack finalOutput;

    @Inject(method = "<init>(Lnet/minecraft/world/level/storage/ValueInput;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$restoreMapTarget(ValueInput data, @Coerce Object listener,
            CraftingCpuLogic cpu, CallbackInfo callback) {
        var markers = data.read("ae2loFinalOutputMap", CompoundTag.CODEC).orElse(null);
        if (finalOutput == null || markers == null) return;
        try {
            finalOutput = new GenericStack(RipperMapSerialization.restoreKey(finalOutput.what(), markers), finalOutput.amount());
        } catch (RuntimeException invalidMarker) {
            // The native caller cancels a job with a missing final output and returns its physical stock.
            finalOutput = null;
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/world/level/storage/ValueInput;Lappeng/crafting/execution/ExecutingCraftingJob$CraftingDifferenceListener;Lappeng/crafting/execution/CraftingCpuLogic;)V",
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEItemKey;fromTag(Lnet/minecraft/world/level/storage/ValueInput;)Lappeng/api/stacks/AEItemKey;"),
            require = 1, expect = 1)
    private AEItemKey ae2lightoptimizer$readMapTask(ValueInput data) {
        var key = AEItemKey.fromTag(data);
        try {
            return (AEItemKey) RipperMapSerialization.restoreKey(key,
                    data.read(RipperMapSerialization.TAG_NAME, CompoundTag.CODEC).orElseGet(CompoundTag::new));
        } catch (RuntimeException invalidMarker) { return null; }
    }

    @Redirect(method = "writeToNBT", at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEItemKey;toTag(Lnet/minecraft/world/level/storage/ValueOutput;)V"),
            require = 1, expect = 1)
    private void ae2lightoptimizer$writeMapTask(AEItemKey key, ValueOutput data) {
        key.toTag(data);
        var job = (ScheduledCraftingJob) this;
        if (job.ae2lightoptimizer$isNativeRipperJob() || job.ae2lightoptimizer$isRipperRequested()) {
            var markers = RipperMapSerialization.markers(key);
            if (!markers.isEmpty()) data.store(RipperMapSerialization.TAG_NAME, CompoundTag.CODEC, markers);
        }
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1, expect = 1)
    private void ae2lightoptimizer$writeSchedule(ValueOutput output, CallbackInfo callback) {
        var scheduledJob = (ScheduledCraftingJob) this;
        if (finalOutput != null && (scheduledJob.ae2lightoptimizer$isNativeRipperJob()
                || scheduledJob.ae2lightoptimizer$isRipperRequested())) {
            var markers = RipperMapSerialization.markers(finalOutput.what());
            if (!markers.isEmpty()) output.store("ae2loFinalOutputMap", CompoundTag.CODEC, markers);
        }
        if (scheduledJob.ae2lightoptimizer$getNativeState() != null) {
            com.example.ae2lightoptimizer.integration.RipperNativeCrafting.write(
                    output, scheduledJob.ae2lightoptimizer$getNativeState());
        }
        if (scheduledJob.ae2lightoptimizer$isNativeRipperJob()) {
            output.putBoolean("ae2loNativeRipper", true);
        }
        if (scheduledJob.ae2lightoptimizer$isRipperRequested()) {
            output.putBoolean("ae2loRipperRequested", true);
        }
        if (scheduledJob.ae2lightoptimizer$isRipped()) {
            output.putBoolean("ae2loRipped", true);
        }
        if (scheduledJob.ae2lightoptimizer$hasSchedule()) {
            CraftingExecutionScheduleCodec.write(output, scheduledJob);
        }
    }
}
