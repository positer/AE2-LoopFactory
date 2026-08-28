package com.example.ae2lightoptimizer.crafting;

public record GlobalPlanningBudget(int maxBalanceIterations, int maxScheduleBatches) {
    public static final GlobalPlanningBudget NETWORK_DEFAULT = new GlobalPlanningBudget(16_384, 4_096);

    public static GlobalPlanningBudget forReachableGraph(int resourceTypes, int patternNodes) {
        if (resourceTypes < 0 || patternNodes < 0) {
            throw new IllegalArgumentException("Graph sizes must be non-negative");
        }
        long graphUnits = Math.addExact((long) resourceTypes, patternNodes);
        int balanceIterations = saturatingInt(Math.max(
                NETWORK_DEFAULT.maxBalanceIterations(), Math.multiplyExact(graphUnits, 4L)));
        int scheduleBatches = saturatingInt(Math.max(
                NETWORK_DEFAULT.maxScheduleBatches(), Math.multiplyExact((long) patternNodes, 4L)));
        return new GlobalPlanningBudget(balanceIterations, scheduleBatches);
    }

    public GlobalPlanningBudget {
        if (maxBalanceIterations <= 0 || maxScheduleBatches <= 0) {
            throw new IllegalArgumentException("Planning budgets must be positive");
        }
    }

    private static int saturatingInt(long value) {
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
