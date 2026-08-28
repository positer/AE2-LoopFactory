package com.example.ae2lightoptimizer.solver;

import java.util.List;
import java.util.Map;

public record RingSolveResult(
        RingSolveStatus status,
        List<RecipeApplication> applications,
        Map<String, Long> finalStock,
        int exploredStates) {
    public RingSolveResult {
        applications = List.copyOf(applications);
        finalStock = Map.copyOf(finalStock);
    }
}
