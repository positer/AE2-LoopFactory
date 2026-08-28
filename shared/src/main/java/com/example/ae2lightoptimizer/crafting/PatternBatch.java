package com.example.ae2lightoptimizer.crafting;

public record PatternBatch(String patternId, long repetitions) {
    public PatternBatch {
        if (patternId == null || patternId.isBlank() || repetitions <= 0) {
            throw new IllegalArgumentException("Invalid pattern batch");
        }
    }
}
