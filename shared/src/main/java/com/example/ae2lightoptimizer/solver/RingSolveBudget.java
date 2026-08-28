package com.example.ae2lightoptimizer.solver;

public record RingSolveBudget(long maxApplications, int maxStates) {
    public RingSolveBudget {
        if (maxApplications <= 0 || maxStates <= 0) {
            throw new IllegalArgumentException("Solve budgets must be positive");
        }
    }

    public static RingSolveBudget networkBlockDefault() {
        return new RingSolveBudget(256, 20_000);
    }
}
