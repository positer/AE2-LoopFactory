package com.example.ae2lightoptimizer.integration;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
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

    public static void write(CompoundTag jobData, HolderLookup.Provider registries,
                             ScheduledCraftingJob job) {
        var data = new CompoundTag();
        data.putString(TAG_OWNER, job.ae2lightoptimizer$getSchedule().owner().name());
        data.putInt(TAG_BATCH_INDEX, job.ae2lightoptimizer$getBatchIndex());
        data.putLong(TAG_REMAINING, job.ae2lightoptimizer$getRemainingInBatch());
        data.putLong(TAG_FINAL_OUTPUT_RESERVE,
                job.ae2lightoptimizer$getSchedule().finalOutputReserve());
        var batches = new ListTag();
        for (var batch : job.ae2lightoptimizer$getSchedule().batches()) {
            var batchData = batch.pattern().getDefinition().toTag(registries);
            batchData.putLong(TAG_REPETITIONS, batch.repetitions());
            batches.add(batchData);
        }
        data.put(TAG_BATCHES, batches);
        jobData.put(TAG_NAME, data);
    }

    @Nullable
    public static RestoredSchedule read(CompoundTag jobData, HolderLookup.Provider registries, Level level) {
        if (!jobData.contains(TAG_NAME, Tag.TAG_COMPOUND)) {
            return null;
        }
        try {
            var data = jobData.getCompound(TAG_NAME);
            var owner = CraftingExecutionSchedule.Owner.valueOf(data.getString(TAG_OWNER));
            var batches = new ArrayList<CraftingExecutionSchedule.Batch>();
            var batchList = data.getList(TAG_BATCHES, Tag.TAG_COMPOUND);
            for (int i = 0; i < batchList.size(); i++) {
                var batchData = batchList.getCompound(i);
                long repetitions = batchData.getLong(TAG_REPETITIONS);
                var definition = AEItemKey.fromTag(registries, batchData);
                var pattern = PatternDetailsHelper.decodePattern(definition, level);
                if (pattern == null) {
                    return null;
                }
                batches.add(new CraftingExecutionSchedule.Batch(pattern, repetitions));
            }
            var schedule = new CraftingExecutionSchedule(
                    owner, batches, data.getLong(TAG_FINAL_OUTPUT_RESERVE));
            return new RestoredSchedule(schedule, data.getInt(TAG_BATCH_INDEX), data.getLong(TAG_REMAINING));
        } catch (IllegalArgumentException invalidData) {
            return null;
        }
    }

    public record RestoredSchedule(CraftingExecutionSchedule schedule,
                                   int batchIndex, long remainingInBatch) {
    }
}
