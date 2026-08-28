package com.example.ae2lightoptimizer.crafting;

import java.util.Optional;

public final class RingMaterialReservePolicy {
    public static final long RESERVED_CYCLES = 16;

    public Optional<RingMaterialReservePlan> plan(
            long repetitions, long materialPerCycle, long availableMaterial) {
        if (repetitions <= 0 || materialPerCycle <= 0 || availableMaterial < 0) {
            return Optional.empty();
        }

        try {
            long total = Math.multiplyExact(repetitions, materialPerCycle);
            long reserveTarget = Math.multiplyExact(RESERVED_CYCLES, materialPerCycle);
            long reserved = Math.min(availableMaterial, reserveTarget);
            long surplus = availableMaterial - reserved;
            long credited = Math.min(total, surplus);
            return Optional.of(new RingMaterialReservePlan(
                    total, reserved, credited, total - credited));
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
    }
}
