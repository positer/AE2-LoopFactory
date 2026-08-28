package com.example.ae2lightoptimizer.crafting;

/** Reports unavoidable graph traversal and reference-storage costs without gating takeover. */
public final class GlobalGraphScaleEstimator {
    private static final long MINIMUM_REFERENCE_BYTES = 16;

    public GlobalGraphScaleAssessment assess(GlobalGraphScaleEnvelope scale) {
        long visits = saturatingAdd(scale.resourceTypes(),
                saturatingAdd(scale.patternNodes(), scale.edges()));
        long minimumBytes = saturatingMultiply(visits, MINIMUM_REFERENCE_BYTES);
        return new GlobalGraphScaleAssessment(visits, minimumBytes);
    }

    private static long saturatingAdd(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingMultiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}
