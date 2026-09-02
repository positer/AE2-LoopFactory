package com.example.ae2lightoptimizer.storage;

/** Checked byte accounting result for a complete cyclic storage cell inventory. */
public record LoopStorageUsage(
        long typeCount,
        long contentBytes,
        long typeBytes,
        long usedBytes,
        boolean hasPartialContentByte) {
    public LoopStorageUsage {
        if (typeCount < 0 || contentBytes < 0 || typeBytes < 0 || usedBytes < 0
                || contentBytes > usedBytes || typeBytes > usedBytes
                || contentBytes > Long.MAX_VALUE - typeBytes
                || contentBytes + typeBytes != usedBytes) {
            throw new IllegalArgumentException("Invalid loop storage usage");
        }
    }
}
