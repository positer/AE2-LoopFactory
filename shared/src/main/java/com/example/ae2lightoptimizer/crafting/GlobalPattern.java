package com.example.ae2lightoptimizer.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

public record GlobalPattern(String id, Map<String, Long> inputs, Map<String, Long> outputs) {
    public GlobalPattern {
        if (id == null || id.isBlank() || inputs == null || outputs == null || outputs.isEmpty()) {
            throw new IllegalArgumentException("Invalid global pattern");
        }
        inputs = validatedCopy(inputs);
        outputs = validatedCopy(outputs);
    }

    private static Map<String, Long> validatedCopy(Map<String, Long> values) {
        Map<String, Long> copy = new LinkedHashMap<>();
        values.forEach((key, amount) -> {
            if (key == null || key.isBlank() || amount == null || amount <= 0) {
                throw new IllegalArgumentException("Pattern amounts must be positive");
            }
            copy.merge(key, amount, Math::addExact);
        });
        return Map.copyOf(copy);
    }
}
