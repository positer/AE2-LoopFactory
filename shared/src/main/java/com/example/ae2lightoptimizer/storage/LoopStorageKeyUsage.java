package com.example.ae2lightoptimizer.storage;

import java.util.Objects;

/** One stored key entry with an API-free key-type token supplied by a version adapter. */
public record LoopStorageKeyUsage<K, T>(K key, T keyType, long amount, long amountPerByte) {
    public LoopStorageKeyUsage {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(keyType, "keyType");
        if (amount < 0 || amountPerByte <= 0) {
            throw new IllegalArgumentException("Amounts must be non-negative and amountPerByte must be positive");
        }
    }
}
