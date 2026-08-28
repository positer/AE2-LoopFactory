package com.example.ae2lightoptimizer.crafting;

import java.util.List;
import java.util.Map;

public record GlobalCraftingPlan(
        GlobalPlanStatus status,
        Map<String, Long> patternCounts,
        List<PatternBatch> schedule,
        Map<String, Long> requiredStock,
        Map<String, Long> creditedStock,
        Map<String, Long> missingStock,
        Map<String, Long> emittedStock,
        Map<String, Long> reservedStock,
        boolean cyclic,
        int stronglyConnectedComponents,
        int balanceIterations,
        int scheduleBatches) {
    public GlobalCraftingPlan {
        patternCounts = Map.copyOf(patternCounts);
        schedule = List.copyOf(schedule);
        requiredStock = Map.copyOf(requiredStock);
        creditedStock = Map.copyOf(creditedStock);
        missingStock = Map.copyOf(missingStock);
        emittedStock = Map.copyOf(emittedStock);
        reservedStock = Map.copyOf(reservedStock);
    }

    public boolean solved() {
        return status == GlobalPlanStatus.SOLVED;
    }
}
