package com.example.ae2lightoptimizer.solver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RingSolveRequest(
        String target,
        long targetAmount,
        Map<String, Long> initialStock,
        List<RingRecipe> recipes,
        RingSolveBudget budget) {
    public RingSolveRequest {
        if (target == null || target.isBlank() || targetAmount <= 0) {
            throw new IllegalArgumentException("Target and target amount must be positive");
        }
        Objects.requireNonNull(initialStock, "initialStock");
        Map<String, Long> stockCopy = new LinkedHashMap<>();
        initialStock.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value < 0) {
                throw new IllegalArgumentException("Initial stock must contain named, non-negative amounts");
            }
            stockCopy.put(key, value);
        });
        initialStock = Map.copyOf(stockCopy);
        recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
        budget = Objects.requireNonNull(budget, "budget");
        if (recipes.stream().map(RingRecipe::id).distinct().count() != recipes.size()) {
            throw new IllegalArgumentException("Recipe ids must be unique");
        }
    }
}
