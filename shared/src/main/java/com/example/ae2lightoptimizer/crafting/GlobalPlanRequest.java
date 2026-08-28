package com.example.ae2lightoptimizer.crafting;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record GlobalPlanRequest(
        String target,
        long requestedAmount,
        Map<String, Long> availableStock,
        List<GlobalPattern> patterns,
        Set<String> emittableResources,
        boolean ringTerminalOnline,
        long reservedCycles,
        GlobalPlanningBudget budget) {
    public GlobalPlanRequest {
        if (target == null || target.isBlank() || requestedAmount <= 0
                || availableStock == null || patterns == null || emittableResources == null
                || reservedCycles < 0 || budget == null) {
            throw new IllegalArgumentException("Invalid global plan request");
        }
        availableStock = Map.copyOf(availableStock);
        patterns = List.copyOf(patterns);
        emittableResources = Set.copyOf(emittableResources);
        availableStock.forEach((key, amount) -> {
            if (key == null || key.isBlank() || amount == null || amount < 0) {
                throw new IllegalArgumentException("Available stock must be non-negative");
            }
        });
    }
}
