package com.example.ae2lightoptimizer.storage;

/** Marks every stationary or portable item backed by the universal loop-storage inventory. */
public interface LoopStorageTierProvider {
    LoopStorageTier tier();
}
