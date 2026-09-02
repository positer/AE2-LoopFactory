package com.example.ae2lightoptimizer.storage;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LoopStorageKeyUsageTest {
    @Test
    void rejectsInvalidUsageRecordsAtTheBoundary() {
        assertThrows(NullPointerException.class,
                () -> new LoopStorageKeyUsage<>(null, "items", 1, 8));
        assertThrows(NullPointerException.class,
                () -> new LoopStorageKeyUsage<>("iron", null, 1, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new LoopStorageKeyUsage<>("iron", "items", -1, 8));
        assertThrows(IllegalArgumentException.class,
                () -> new LoopStorageKeyUsage<>("iron", "items", 1, 0));
    }
}
