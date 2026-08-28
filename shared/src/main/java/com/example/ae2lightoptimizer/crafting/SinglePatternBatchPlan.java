package com.example.ae2lightoptimizer.crafting;

public record SinglePatternBatchPlan(
        BatchOptimizationMode mode,
        long repetitions,
        long seedRequired,
        long producedTarget,
        long optimizedOperations,
        long ae2EquivalentOperations) {
    public SinglePatternBatchPlan {
        if (repetitions <= 0 || seedRequired < 0 || producedTarget <= 0
                || optimizedOperations <= 0 || ae2EquivalentOperations < optimizedOperations) {
            throw new IllegalArgumentException("Invalid single-pattern batch plan");
        }
    }
}
