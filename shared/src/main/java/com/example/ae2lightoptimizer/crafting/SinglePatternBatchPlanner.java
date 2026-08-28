package com.example.ae2lightoptimizer.crafting;

import java.util.Optional;

public final class SinglePatternBatchPlanner {
    public Optional<SinglePatternBatchPlan> plan(long requestedTarget,
                                                  long outputPerCraft,
                                                  long targetInputPerCraft,
                                                  int otherInputCount,
                                                  boolean supercomputerOnline,
                                                  boolean recipeRingOnline) {
        if (requestedTarget <= 0 || outputPerCraft <= 0 || targetInputPerCraft < 0
                || otherInputCount < 0 || !supercomputerOnline) {
            return Optional.empty();
        }

        if (targetInputPerCraft == 0) {
            long repetitions = ceilDiv(requestedTarget, outputPerCraft);
            long produced = saturatingMultiply(outputPerCraft, repetitions);
            long operations = 6L + 2L * otherInputCount;
            long ae2Operations = operations + 4L + 3L * otherInputCount;
            return Optional.of(new SinglePatternBatchPlan(
                    BatchOptimizationMode.DIRECT,
                    repetitions,
                    0,
                    produced,
                    operations,
                    ae2Operations));
        }

        long netGrowth = outputPerCraft - targetInputPerCraft;
        if (!recipeRingOnline || netGrowth <= 0) {
            return Optional.empty();
        }

        long repetitions = ceilDiv(requestedTarget, netGrowth);
        long produced = saturatingMultiply(netGrowth, repetitions);
        long optimizedOperations = 9L + 2L * otherInputCount;
        long operationsPerAe2Iteration = 8L + 3L * otherInputCount;
        long ae2Operations = saturatingMultiply(repetitions, operationsPerAe2Iteration);
        return Optional.of(new SinglePatternBatchPlan(
                BatchOptimizationMode.CIRCULATING,
                repetitions,
                targetInputPerCraft,
                produced,
                optimizedOperations,
                Math.max(optimizedOperations, ae2Operations)));
    }

    private static long ceilDiv(long numerator, long denominator) {
        return 1 + (numerator - 1) / denominator;
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
