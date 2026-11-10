package com.example.ae2lightoptimizer.integration;

/** Attached nodes grant passive capacity even while AE2 is booting or out of channels. */
public interface OptimizerChannelCapacity {
    boolean ae2lf$hasOptimizer();
    static boolean enabled(appeng.api.networking.IGrid grid) {
        return grid != null && grid.getPathingService() instanceof OptimizerChannelCapacity service && service.ae2lf$hasOptimizer();
    }
}
