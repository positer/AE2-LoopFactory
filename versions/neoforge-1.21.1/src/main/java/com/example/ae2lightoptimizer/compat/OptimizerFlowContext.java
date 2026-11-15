package com.example.ae2lightoptimizer.compat;

/**
 * Carries this network's capacity decision from Thunderbolt's max-flow entry point into its static
 * supply hook. Thunderbolt asks {@code HighCapacityChannelSupport.supplyPerController} for a global
 * per-controller supply without a grid argument, and only calls it from {@code BorrowedCapacityCalculator.solve},
 * which does have the grid. Marking at the head of that solve is therefore exact; a thread-local keeps
 * concurrent pathing calculations from reading each other's decision.
 */
public final class OptimizerFlowContext {
    private static final ThreadLocal<Boolean> DOUBLED = new ThreadLocal<>();
    public static void mark(boolean doubled) { DOUBLED.set(doubled); }
    public static boolean doubled() { return Boolean.TRUE.equals(DOUBLED.get()); }
    private OptimizerFlowContext() {}
}
