package com.example.ae2lightoptimizer.crafting;

/** Determines when a ring-owned crafting job can safely enter AE2's finished state. */
public final class RingCompletionGate {
    private RingCompletionGate() {
    }

    public static boolean canFinish(boolean scheduleComplete, long remainingRequest,
                                    long pendingFinalOutput) {
        if (remainingRequest < 0 || pendingFinalOutput < 0) {
            throw new IllegalArgumentException("Ring completion amounts cannot be negative");
        }
        return scheduleComplete && remainingRequest == 0 && pendingFinalOutput == 0;
    }
}
