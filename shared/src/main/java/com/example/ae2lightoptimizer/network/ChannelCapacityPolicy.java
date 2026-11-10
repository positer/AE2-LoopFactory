package com.example.ae2lightoptimizer.network;

/** A network-local, non-stacking capacity multiplier. Infinite and forbidden paths retain native semantics. */
public final class ChannelCapacityPolicy {
    public static int effective(int nativeCapacity, boolean optimizerAttached) {
        if (!optimizerAttached || nativeCapacity <= 0 || nativeCapacity == Integer.MAX_VALUE) return nativeCapacity;
        return (int)Math.min(Integer.MAX_VALUE, (long)nativeCapacity * 2);
    }
    private ChannelCapacityPolicy() {}
}
