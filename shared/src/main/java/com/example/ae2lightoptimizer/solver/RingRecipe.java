package com.example.ae2lightoptimizer.solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record RingRecipe(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
    public RingRecipe {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Recipe id must not be blank");
        }
        inputs = validatedCopy(inputs, "inputs");
        outputs = validatedCopy(outputs, "outputs");
        if (inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("Ring recipes require at least one input and output");
        }
    }

    private static Map<String, Long> validatedCopy(Map<String, Long> amounts, String label) {
        Objects.requireNonNull(amounts, label);
        Map<String, Long> copy = new LinkedHashMap<>();
        amounts.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value <= 0) {
                throw new IllegalArgumentException(label + " must contain named, positive amounts");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
