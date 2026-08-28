package com.example.ae2lightoptimizer.crafting;

public record RingMaterialReservePlan(
        long totalLoopMaterial,
        long reservedExistingMaterial,
        long creditedExistingMaterial,
        long calculatedDemand) {
    public RingMaterialReservePlan {
        if (totalLoopMaterial < 0 || reservedExistingMaterial < 0
                || creditedExistingMaterial < 0 || calculatedDemand < 0
                || creditedExistingMaterial + calculatedDemand != totalLoopMaterial) {
            throw new IllegalArgumentException("Invalid ring material reserve plan");
        }
    }
}
