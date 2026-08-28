package com.example.ae2lightoptimizer.crafting;

public record GlobalGraphScaleEnvelope(
        long resourceTypes,
        long patternNodes,
        long edges,
        long totalMaterialAmount) {
    public GlobalGraphScaleEnvelope {
        if (resourceTypes < 0 || patternNodes < 0 || edges < 0 || totalMaterialAmount < 0) {
            throw new IllegalArgumentException("Graph scale values must be non-negative");
        }
    }
}
