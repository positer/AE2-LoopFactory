package com.example.ae2lightoptimizer.integration;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public final class CraftingExecutionScheduleCodec {
    public static final String TAG_NAME = "ae2lightoptimizerSchedule";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_BATCH_INDEX = "batchIndex";
    private static final String TAG_REMAINING = "remainingInBatch";
    private static final String TAG_FINAL_OUTPUT_RESERVE = "finalOutputReserve";
    private static final String TAG_BATCHES = "batches";
    private static final String TAG_REPETITIONS = "repetitions";

    private CraftingExecutionScheduleCodec() {
    }

    public static void write(ValueOutput jobData, ScheduledCraftingJob job) {
        var data = jobData.child(TAG_NAME);
        data.putString(TAG_OWNER, job.ae2lightoptimizer$getSchedule().owner().name());
        data.putInt(TAG_BATCH_INDEX, job.ae2lightoptimizer$getBatchIndex());
        data.putLong(TAG_REMAINING, job.ae2lightoptimizer$getRemainingInBatch());
        data.putLong(TAG_FINAL_OUTPUT_RESERVE,
                job.ae2lightoptimizer$getSchedule().finalOutputReserve());
        var batches = data.childrenList(TAG_BATCHES);
        for (var batch : job.ae2lightoptimizer$getSchedule().batches()) {
            var batchData = batches.addChild();
            batch.pattern().getDefinition().toTag(batchData);
            batchData.putLong(TAG_REPETITIONS, batch.repetitions());
        }
    }

    @Nullable
    public static RestoredSchedule read(ValueInput jobData, Level level) {
        var data = jobData.childOrEmpty(TAG_NAME);
        var ownerName = data.getString(TAG_OWNER).orElse(null);
        if (ownerName == null) {
            return null;
        }
        try {
            var owner = CraftingExecutionSchedule.Owner.valueOf(ownerName);
            var batches = new ArrayList<CraftingExecutionSchedule.Batch>();
            for (var batchData : data.childrenListOrEmpty(TAG_BATCHES)) {
                long repetitions = batchData.getLongOr(TAG_REPETITIONS, 0);
                var definition = AEItemKey.fromTag(batchData);
                var pattern = PatternDetailsHelper.decodePattern(definition, level);
                if (pattern == null) {
                    return null;
                }
                batches.add(new CraftingExecutionSchedule.Batch(pattern, repetitions));
            }
            var schedule = new CraftingExecutionSchedule(
                    owner, batches, data.getLongOr(TAG_FINAL_OUTPUT_RESERVE, 0));
            return new RestoredSchedule(schedule,
                    data.getIntOr(TAG_BATCH_INDEX, 0), data.getLongOr(TAG_REMAINING, 0));
        } catch (IllegalArgumentException invalidData) {
            return null;
        }
    }

    public record RestoredSchedule(CraftingExecutionSchedule schedule,
                                   int batchIndex, long remainingInBatch) {
    }
}
