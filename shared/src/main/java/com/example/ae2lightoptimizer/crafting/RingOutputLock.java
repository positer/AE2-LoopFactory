package com.example.ae2lightoptimizer.crafting;

/** Computes how much completed ring output may leave the crafting CPU. */
public final class RingOutputLock {
    private RingOutputLock() {
    }

    public static long releasable(long stored, long seedReserve, long remainingRequest,
                                  boolean scheduleComplete) {
        if (stored < 0 || seedReserve < 0 || remainingRequest < 0) {
            throw new IllegalArgumentException("Ring output amounts cannot be negative");
        }
        if (!scheduleComplete || stored <= seedReserve || remainingRequest == 0) {
            return 0;
        }
        return Math.min(stored - seedReserve, remainingRequest);
    }
}
