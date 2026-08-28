package com.example.ae2lightoptimizer.integration;

import appeng.api.crafting.IPatternDetails;
import java.util.List;

/** Ordered, run-length-compressed dispatch plan attached only to handled AE2 jobs. */
public record CraftingExecutionSchedule(Owner owner, List<Batch> batches, long finalOutputReserve) {
    public CraftingExecutionSchedule {
        if (owner == null || batches == null || batches.isEmpty()) {
            throw new IllegalArgumentException("An execution schedule needs an owner and batches");
        }
        if (finalOutputReserve < 0) {
            throw new IllegalArgumentException("Final output reserve cannot be negative");
        }
        batches = List.copyOf(batches);
    }

    public enum Owner {
        RING_TERMINAL,
        OPTIMIZER_INTERFACE
    }

    public record Batch(IPatternDetails pattern, long repetitions) {
        public Batch {
            if (pattern == null || repetitions <= 0) {
                throw new IllegalArgumentException("Invalid execution batch");
            }
        }
    }
}
