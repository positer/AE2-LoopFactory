package com.example.ae2lightoptimizer.crafting;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/** Sparse repeated recipe transition. Intermediate seeds are required even when net use is zero. */
public final class InstantCraftingBatch {
    private InstantCraftingBatch() {
    }

    public static <K> long executableRepetitions(
            Map<K, Long> stock, Map<K, Long> inputs, Map<K, Long> outputs, long requested) {
        if (requested <= 0 || inputs.isEmpty() || outputs.isEmpty()) {
            throw new IllegalArgumentException("A batch needs positive work, inputs and outputs");
        }
        validate(stock, false);
        validate(inputs, true);
        validate(outputs, true);
        long result = requested;
        for (var input : inputs.entrySet()) {
            long available = stock.getOrDefault(input.getKey(), 0L);
            long consumed = input.getValue();
            if (available < consumed) {
                return 0;
            }
            long returned = outputs.getOrDefault(input.getKey(), 0L);
            if (consumed > returned) {
                long extra = (available - consumed) / (consumed - returned);
                // Avoid overflowing extra + 1 for a Long.MAX_VALUE one-item stock.
                result = Math.min(result, extra == Long.MAX_VALUE ? extra : extra + 1);
            }
        }
        return result;
    }

    /** Returns a new stock map only after every key and every intermediate boundary passes. */
    public static <K> Map<K, Long> apply(
            Map<K, Long> stock, Map<K, Long> inputs, Map<K, Long> outputs, long repetitions) {
        if (executableRepetitions(stock, inputs, outputs, repetitions) != repetitions) {
            throw new IllegalArgumentException("Insufficient intermediate recipe inputs");
        }
        Map<K, Long> result = new LinkedHashMap<>(stock);
        var touched = new LinkedHashSet<>(inputs.keySet());
        touched.addAll(outputs.keySet());
        for (K key : touched) {
            long delta = Math.subtractExact(outputs.getOrDefault(key, 0L), inputs.getOrDefault(key, 0L));
            long balance = Math.addExact(stock.getOrDefault(key, 0L), Math.multiplyExact(delta, repetitions));
            if (balance < 0) {
                throw new IllegalArgumentException("Negative recipe balance");
            }
            if (balance == 0) {
                result.remove(key);
            } else {
                result.put(key, balance);
            }
        }
        return result;
    }

    private static <K> void validate(Map<K, Long> values, boolean positive) {
        for (var entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null
                    || (positive ? entry.getValue() <= 0 : entry.getValue() < 0)) {
                throw new IllegalArgumentException("Invalid material amount");
            }
        }
    }
}
